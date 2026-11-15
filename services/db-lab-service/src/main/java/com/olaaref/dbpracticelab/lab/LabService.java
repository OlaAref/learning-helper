package com.olaaref.dbpracticelab.lab;

import com.olaaref.dbpracticelab.config.LabProperties;
import com.olaaref.dbpracticelab.content.Model.Step;
import com.olaaref.dbpracticelab.content.Model.Task;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

/**
 * Creates a throwaway database per task attempt, runs the learner's statements in named sessions,
 * grades the result, and crashes / restarts the "crash" server on request.
 */
@Service
public class LabService implements DisposableBean {
  private static final System.Logger LOG = System.getLogger(LabService.class.getName());
  private static final String DB_PREFIX = "dblab_";

  private final LabProperties props;
  private final Map<String, Lab> labs = new ConcurrentHashMap<>();

  public LabService(LabProperties props) {
    this.props = props;
    DriverManager.setLoginTimeout(5);
    dropLeftoverDatabases();
  }

  // ------------------------------------------------------------------ lifecycle

  /** Opens a fresh lab for the task (replacing any earlier lab for the same task) and runs its setup SQL. */
  public synchronized LabView open(String taskKey, Task task) {
    for (Lab old : new ArrayList<>(labs.values())) {
      if (old.taskKey.equals(taskKey)) {
        labs.remove(old.id);
        dispose(old);
      }
    }
    LabProperties.Target target = target(task.target());
    String db = DB_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    try (Connection c = connect(target, null); Statement st = c.createStatement()) {
      st.execute("CREATE DATABASE " + db);
    } catch (SQLException e) {
      throw new LabException(503, "Cannot reach PostgreSQL target '" + task.target() + "' (is docker compose up?): " + e.getMessage());
    }
    Lab lab = new Lab(UUID.randomUUID().toString().substring(0, 8), taskKey, task, task.target(), target, db);
    try {
      runSetup(lab);
    } catch (SQLException e) {
      dropDatabase(lab);
      throw new LabException(500, "Setup SQL of the task failed: " + e.getMessage());
    }
    for (String sid : task.sessions()) lab.sessions.put(sid, new LabSession(sid, () -> connect(lab.target, lab.dbName)));
    labs.put(lab.id, lab);
    return view(lab);
  }

  public LabView view(String labId) {
    return view(lab(labId));
  }

  public synchronized void close(String labId) {
    Lab lab = labs.remove(labId);
    if (lab != null) dispose(lab);
  }

  @Override
  public synchronized void destroy() {
    for (Lab lab : new ArrayList<>(labs.values())) dispose(lab);
    labs.clear();
  }

  // ------------------------------------------------------------------ statements

  /** Submits SQL to a session and waits up to waitMs; a statement blocked on a lock keeps running. */
  public LabView execute(String labId, String sessionId, String sql, long waitMs) {
    Lab lab = lab(labId);
    LabSession s = session(lab, sessionId);
    if (sql == null || sql.isBlank()) throw new LabException(400, "Nothing to run");
    s.submit(sql).await(waitMs);
    return view(lab);
  }

  public LabView cancel(String labId, String sessionId) {
    Lab lab = lab(labId);
    session(lab, sessionId).cancel();
    return view(lab);
  }

  public CheckReport check(String labId) {
    Lab lab = lab(labId);
    return Checker.run(lab, () -> connect(lab.target, lab.dbName));
  }

  // ------------------------------------------------------------------ chaos

  /** action is "kill" (run the kill command) or "start" (run the start command and wait until the server answers). */
  public LabView chaos(String labId, String action) {
    Lab lab = lab(labId);
    if (!"crash".equals(lab.targetName)) throw new LabException(400, "Kill / start is only available for crash tasks");
    LabProperties.Chaos chaos = props.chaos();
    switch (action) {
      case "kill" -> {
        runCommand(chaos.killCommand());
        lab.kills.incrementAndGet();
        sessionsOnTarget(lab.targetName).forEach(LabSession::dropConnection);
      }
      case "start" -> {
        runCommand(chaos.startCommand());
        waitUntilReady(lab.target, chaos.readyTimeoutMs());
        sessionsOnTarget(lab.targetName).forEach(LabSession::dropConnection);
      }
      default -> throw new LabException(400, "Unknown chaos action " + action);
    }
    return view(lab);
  }

  // ------------------------------------------------------------------ solutions (used by tests)

  /** Plays the task's reference solution into the lab and waits until every session is idle. */
  public void replaySolution(String labId, long waitMs) {
    Lab lab = lab(labId);
    if (lab.task.solution() == null) throw new LabException(404, "Task has no solution");
    for (Step step : lab.task.solution().steps()) {
      if (!step.chaos().isEmpty()) chaos(labId, step.chaos());
      else execute(labId, step.session(), step.sql(), waitMs);
    }
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    for (LabSession s : lab.sessions.values()) {
      for (LabSession.Entry e : s.history()) {
        long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (left <= 0 || !e.await(left)) throw new LabException(500, "Statement still running after the solution: " + e.sql());
      }
    }
  }

  // ------------------------------------------------------------------ internals

  private Lab lab(String id) {
    Lab lab = labs.get(id);
    if (lab == null) throw new LabException(404, "This lab is gone (restart the task to get a new one)");
    return lab;
  }

  private static LabSession session(Lab lab, String id) {
    LabSession s = lab.sessions.get(id);
    if (s == null) throw new LabException(404, "Unknown session " + id);
    return s;
  }

  private List<LabSession> sessionsOnTarget(String targetName) {
    List<LabSession> out = new ArrayList<>();
    for (Lab l : labs.values()) if (l.targetName.equals(targetName)) out.addAll(l.sessions.values());
    return out;
  }

  private LabProperties.Target target(String name) {
    LabProperties.Target t = props.targets().get(name);
    if (t == null) throw new LabException(500, "No lab target named '" + name + "' in lab.targets");
    return t;
  }

  private LabView view(Lab lab) {
    List<LabView.SessionView> sessions = new ArrayList<>();
    for (LabSession s : lab.sessions.values()) {
      List<LabView.EntryView> entries = new ArrayList<>();
      for (LabSession.Entry e : s.history()) {
        String status = e.status(); // read first: it publishes the results
        entries.add(new LabView.EntryView(e.index(), e.sql(), status, e.millis(), List.copyOf(e.results())));
      }
      sessions.add(new LabView.SessionView(s.id(), entries));
    }
    return new LabView(lab.id, lab.taskKey, lab.targetName, lab.kills.get(), sessions);
  }

  private void runSetup(Lab lab) throws SQLException {
    if (lab.task.setup().isBlank()) return;
    try (Connection c = connect(lab.target, lab.dbName); Statement st = c.createStatement()) {
      for (String sql : SqlSplitter.split(lab.task.setup())) st.execute(sql);
    }
  }

  private static Connection connect(LabProperties.Target t, String db) throws SQLException {
    Connection c = DriverManager.getConnection(db == null ? t.url() : withDatabase(t.url(), db), t.user(), t.password());
    c.setAutoCommit(true);
    return c;
  }

  /** jdbc:postgresql://host:port/old?x=y  ->  jdbc:postgresql://host:port/db?x=y */
  static String withDatabase(String url, String db) {
    int q = url.indexOf('?');
    String head = q < 0 ? url : url.substring(0, q);
    String tail = q < 0 ? "" : url.substring(q);
    return head.substring(0, head.lastIndexOf('/') + 1) + db + tail;
  }

  private void dispose(Lab lab) {
    lab.sessions.values().forEach(LabSession::close);
    dropDatabase(lab);
  }

  private void dropDatabase(Lab lab) {
    try (Connection c = connect(lab.target, null); Statement st = c.createStatement()) {
      st.execute("DROP DATABASE IF EXISTS " + lab.dbName + " WITH (FORCE)");
    } catch (SQLException e) {
      LOG.log(System.Logger.Level.WARNING, "Could not drop " + lab.dbName + ": " + e.getMessage());
    }
  }

  /** Labs from an earlier run are useless; remove their databases on startup. */
  private void dropLeftoverDatabases() {
    for (Map.Entry<String, LabProperties.Target> t : props.targets().entrySet()) {
      try (Connection c = connect(t.getValue(), null);
          Statement st = c.createStatement();
          ResultSet rs = st.executeQuery("SELECT datname FROM pg_database WHERE datname LIKE 'dblab\\_%'")) {
        List<String> names = new ArrayList<>();
        while (rs.next()) names.add(rs.getString(1));
        for (String n : names) {
          try (Statement drop = c.createStatement()) {
            drop.execute("DROP DATABASE IF EXISTS " + n + " WITH (FORCE)");
          }
        }
      } catch (SQLException e) {
        LOG.log(System.Logger.Level.WARNING, "Target '" + t.getKey() + "' is not reachable yet: " + e.getMessage());
      }
    }
  }

  private static void runCommand(String command) {
    if (command == null || command.isBlank()) throw new LabException(500, "lab.chaos command is not configured");
    try {
      Process p = new ProcessBuilder(command.trim().split("\\s+")).redirectErrorStream(true).start();
      String out = new String(p.getInputStream().readAllBytes());
      if (!p.waitFor(60, TimeUnit.SECONDS)) {
        p.destroyForcibly();
        throw new LabException(500, "Command timed out: " + command);
      }
      if (p.exitValue() != 0) throw new LabException(500, "Command failed (" + command + "): " + out.strip());
    } catch (IOException e) {
      throw new LabException(500, "Cannot run '" + command + "': " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LabException(500, "Interrupted while running " + command);
    }
  }

  private static void waitUntilReady(LabProperties.Target t, long timeoutMs) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
    SQLException last = null;
    while (System.nanoTime() < deadline) {
      try (Connection c = connect(t, null)) {
        return;
      } catch (SQLException e) {
        last = e;
        try {
          Thread.sleep(500);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    throw new LabException(503, "Server did not come back in time" + (last == null ? "" : ": " + last.getMessage()));
  }
}
