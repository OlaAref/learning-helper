package com.olaaref.dbpracticelab.lab;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * One database connection (a "terminal"). The connection stays in autocommit mode like psql, so
 * transactions exist only when the learner types BEGIN. Statements run on the session's own thread,
 * so a statement that blocks on a lock does not block the HTTP request or the other sessions.
 */
public final class LabSession {
  public interface ConnectionFactory {
    Connection open() throws SQLException;
  }

  /** One submission (possibly several statements). */
  public static final class Entry {
    private final int index;
    private final String sql;
    private final List<StatementResult> results = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Void> done = new CompletableFuture<>();
    private volatile String status = "RUNNING";
    private volatile long millis;

    Entry(int index, String sql) {
      this.index = index;
      this.sql = sql;
    }

    public int index() {
      return index;
    }

    public String sql() {
      return sql;
    }

    public String status() {
      return status;
    }

    public long millis() {
      return millis;
    }

    public List<StatementResult> results() {
      return results;
    }

    /** Waits up to waitMs for the submission to finish; returns false if it is still running. */
    public boolean await(long waitMs) {
      try {
        done.get(waitMs, TimeUnit.MILLISECONDS);
        return true;
      } catch (java.util.concurrent.TimeoutException e) {
        return false;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      } catch (java.util.concurrent.ExecutionException e) {
        return true;
      }
    }

    /** The last result set this submission returned, or null if it returned none or failed. */
    public StatementResult lastResultSet() {
      if (!"OK".equals(status)) return null;
      StatementResult last = null;
      for (StatementResult r : results) if (r.hasRows()) last = r;
      return last;
    }
  }

  public static final int MAX_ROWS = 200;

  private final String id;
  private final ConnectionFactory factory;
  private final ExecutorService executor;
  private final List<Entry> history = new CopyOnWriteArrayList<>();
  private final Object connLock = new Object();
  private Connection conn;
  private volatile boolean broken;
  private volatile Statement running;

  public LabSession(String id, ConnectionFactory factory) {
    this.id = id;
    this.factory = factory;
    this.executor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "lab-session-" + id);
      t.setDaemon(true);
      return t;
    });
  }

  public String id() {
    return id;
  }

  public List<Entry> history() {
    return history;
  }

  public synchronized Entry submit(String sql) {
    Entry e = new Entry(history.size() + 1, sql);
    history.add(e);
    executor.submit(() -> runEntry(e));
    return e;
  }

  /** Asks the server to cancel the statement that is currently running (like Ctrl+C in psql). */
  public void cancel() {
    Statement st = running;
    if (st == null) return;
    try {
      st.cancel();
    } catch (SQLException ignored) {
      // the statement may have finished in the meantime
    }
  }

  /** Forgets the connection (after the server was killed and restarted); the next statement reconnects. */
  public void dropConnection() {
    synchronized (connLock) {
      closeQuietly(conn);
      conn = null;
    }
  }

  public void close() {
    cancel();
    executor.shutdownNow();
    dropConnection();
  }

  // ------------------------------------------------------------------ internals

  private void runEntry(Entry e) {
    long t0 = System.nanoTime();
    boolean failed = false;
    try {
      for (String stmt : SqlSplitter.split(e.sql)) {
        StatementResult r = runStatement(stmt);
        e.results.add(r);
        if (r.error() != null) {
          failed = true;
          break; // like psql with ON_ERROR_STOP: the rest of the submission is skipped
        }
      }
    } catch (RuntimeException ex) {
      e.results.add(StatementResult.failed(e.sql, "XX000", String.valueOf(ex)));
      failed = true;
    }
    e.millis = (System.nanoTime() - t0) / 1_000_000;
    e.status = failed ? "ERROR" : "OK"; // volatile write publishes results
    e.done.complete(null);
  }

  private StatementResult runStatement(String sql) {
    Connection c;
    try {
      c = connection();
    } catch (SQLException ex) {
      return StatementResult.failed(sql, ex.getSQLState(), ex.getMessage());
    }
    try (Statement st = c.createStatement()) {
      running = st;
      if (st.execute(sql)) {
        try (ResultSet rs = st.getResultSet()) {
          return readRows(sql, rs);
        }
      }
      return StatementResult.updated(sql, Math.max(st.getUpdateCount(), 0));
    } catch (SQLException ex) {
      String state = ex.getSQLState();
      if (state == null || state.startsWith("08") || state.startsWith("57P")) broken = true;
      return StatementResult.failed(sql, state == null ? "08006" : state, ex.getMessage());
    } finally {
      running = null;
    }
  }

  private static StatementResult readRows(String sql, ResultSet rs) throws SQLException {
    ResultSetMetaData md = rs.getMetaData();
    int cols = md.getColumnCount();
    List<String> names = new ArrayList<>();
    for (int i = 1; i <= cols; i++) names.add(md.getColumnLabel(i));
    List<List<String>> rows = new ArrayList<>();
    boolean truncated = false;
    while (rs.next()) {
      if (rows.size() >= MAX_ROWS) {
        truncated = true;
        break;
      }
      List<String> row = new ArrayList<>();
      for (int i = 1; i <= cols; i++) row.add(rs.getString(i)); // null stays null: SQL NULL
      rows.add(row);
    }
    return StatementResult.rows(sql, names, rows, truncated);
  }

  private Connection connection() throws SQLException {
    synchronized (connLock) {
      if (conn == null || broken || conn.isClosed()) {
        closeQuietly(conn);
        conn = factory.open();
        broken = false;
      }
      return conn;
    }
  }

  private static void closeQuietly(Connection c) {
    if (c == null) return;
    try {
      c.close();
    } catch (SQLException | RuntimeException ignored) {
      // a connection to a killed server cannot be closed cleanly
    }
  }
}
