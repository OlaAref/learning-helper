package com.olaaref.dbpracticelab.web;

import com.olaaref.dbpracticelab.content.Model.Task;
import java.util.List;

/** JSON shapes of the REST API (the quiz answer and the task solution are never sent unless asked for). */
public final class Views {
  private Views() {}

  public record SectionView(String id, String course, String title, String notes, List<LectureView> lectures) {}

  public record LectureView(
      String id, int number, String title, int quizTotal, int quizCorrect, List<TaskSummary> tasks) {}

  public record TaskSummary(String id, String title, String kind, String difficulty, boolean done, int attempts) {}

  public record QuestionView(
      String id, String kind, String prompt, String code, List<String> options, boolean answered, boolean correct) {}

  public record QuizView(String section, String lecture, String lectureTitle, List<QuestionView> questions) {}

  public record AnswerResult(boolean correct, int correctIndex, String explanation) {}

  public record TaskView(
      String section,
      String lecture,
      String lectureTitle,
      String id,
      String title,
      String kind,
      String target,
      String difficulty,
      List<String> sessions,
      String statement,
      List<String> hints,
      List<String> checks,
      boolean done) {
    static TaskView of(String section, String lecture, String lectureTitle, Task t, boolean done) {
      return new TaskView(
          section, lecture, lectureTitle, t.id(), t.title(), t.kind(), t.target(), t.difficulty(), t.sessions(),
          t.statement(), t.hints(), t.checks().stream().map(c -> c.description()).toList(), done);
    }
  }

  public record SolutionView(String explanation, List<SolutionStep> steps) {}

  public record SolutionStep(String session, String sql, String chaos) {}

  public record AnswerRequest(int choice) {}

  public record ExecuteRequest(String sql) {}

  public record CheckResponse(boolean passed, List<CheckItem> items) {}

  public record CheckItem(String description, boolean passed, String detail) {}
}
