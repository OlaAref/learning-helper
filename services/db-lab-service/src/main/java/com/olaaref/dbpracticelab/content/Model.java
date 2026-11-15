package com.olaaref.dbpracticelab.content;

import java.util.List;

/** The content model loaded from content/ (see content/README.md for the file format). */
public final class Model {
  private Model() {}

  public record Section(String id, String course, String title, String notes, List<Lecture> lectures) {}

  public record Lecture(String id, int number, String title, List<Question> questions, List<Task> tasks) {}

  /** kind is "mcq" or "predict"; answer is the 0-based index into options. */
  public record Question(
      String id, String kind, String prompt, String code, List<String> options, int answer, String explanation) {}

  /** kind is "sql", "concurrency" or "crash"; target is the server name from lab.targets. */
  public record Task(
      String id,
      String title,
      String kind,
      String target,
      String difficulty,
      List<String> sessions,
      String statement,
      String setup,
      List<String> hints,
      List<Check> checks,
      Solution solution) {}

  public record Solution(String explanation, List<Step> steps) {}

  /** Either a statement for a session, or a chaos action ("kill" / "start"). */
  public record Step(String session, String sql, String chaos) {}

  /**
   * type = query (expect rows on committed state), error (an sqlstate happened), result (what a
   * session's nth result-producing submission returned) or chaos (the server was killed).
   */
  public record Check(
      String type,
      String description,
      String sql,
      List<List<String>> expect,
      String sqlstate,
      String session,
      String nth,
      boolean killed) {}
}
