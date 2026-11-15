package com.olaaref.dbpracticelab.lab;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.olaaref.dbpracticelab.config.LabProperties;
import com.olaaref.dbpracticelab.content.ContentRepository;
import com.olaaref.dbpracticelab.content.Model.Lecture;
import com.olaaref.dbpracticelab.content.Model.Section;
import com.olaaref.dbpracticelab.content.Model.Task;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Plays every task's reference solution into a real lab and requires that all of the task's checks
 * pass. Needs the compose PostgreSQL servers (docker compose up -d); skipped when they are not running.
 * Crash tasks also need the docker kill/start commands from application.yml to work.
 */
class SolutionsVerificationTest {
  private static final LabProperties PROPS = new LabProperties(
      "../../content",
      "target/test-progress.yml",
      500,
      Map.of(
          "main", new LabProperties.Target(env("LAB_MAIN_URL", "jdbc:postgresql://127.0.0.1:5433/postgres"), "postgres", "postgres"),
          "crash", new LabProperties.Target(env("LAB_CRASH_URL", "jdbc:postgresql://127.0.0.1:5434/postgres"), "postgres", "postgres")),
      new LabProperties.Chaos(
          env("LAB_KILL_COMMAND", "docker kill dblab-crash"), env("LAB_START_COMMAND", "docker start dblab-crash"), 30_000));

  private static String env(String name, String dflt) {
    String v = System.getenv(name);
    return v == null || v.isBlank() ? dflt : v;
  }

  private static boolean reachable(LabProperties.Target t) {
    try (Connection c = DriverManager.getConnection(t.url(), t.user(), t.password())) {
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @TestFactory
  Stream<DynamicTest> referenceSolutionsPassTheirChecks() {
    Assumptions.assumeTrue(reachable(PROPS.targets().get("main")), "PostgreSQL is not running: docker compose up -d");
    ContentRepository content = new ContentRepository(Path.of(PROPS.contentDir()));
    LabService labs = new LabService(PROPS);
    boolean crashUp = reachable(PROPS.targets().get("crash"));
    List<DynamicTest> tests = new ArrayList<>();
    for (Section s : content.sections()) {
      for (Lecture l : s.lectures()) {
        for (Task t : l.tasks()) {
          if (t.kind().equals("crash") && !crashUp) continue;
          String key = s.id() + "/" + l.id() + "/" + t.id();
          tests.add(DynamicTest.dynamicTest(key, () -> {
            LabView lab = labs.open(key, t);
            try {
              labs.replaySolution(lab.id(), 500);
              CheckReport report = labs.check(lab.id());
              assertTrue(report.passed(), key + " -> " + report.items());
            } finally {
              labs.close(lab.id());
            }
          }));
        }
      }
    }
    return tests.stream();
  }
}
