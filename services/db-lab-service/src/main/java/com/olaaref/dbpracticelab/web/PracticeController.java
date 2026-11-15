package com.olaaref.dbpracticelab.web;

import com.olaaref.dbpracticelab.config.LabProperties;
import com.olaaref.dbpracticelab.content.ContentRepository;
import com.olaaref.dbpracticelab.content.Model.Lecture;
import com.olaaref.dbpracticelab.content.Model.Question;
import com.olaaref.dbpracticelab.content.Model.Section;
import com.olaaref.dbpracticelab.content.Model.Task;
import com.olaaref.dbpracticelab.lab.CheckReport;
import com.olaaref.dbpracticelab.lab.LabService;
import com.olaaref.dbpracticelab.lab.LabView;
import com.olaaref.dbpracticelab.progress.ProgressStore;
import com.olaaref.dbpracticelab.web.Views.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class PracticeController {
  private final ContentRepository content;
  private final ProgressStore progress;
  private final LabService labs;
  private final LabProperties props;

  public PracticeController(ContentRepository content, ProgressStore progress, LabService labs, LabProperties props) {
    this.content = content;
    this.progress = progress;
    this.labs = labs;
    this.props = props;
  }

  // ------------------------------------------------------------------ overview and quizzes

  @GetMapping("/overview")
  public List<SectionView> overview() {
    List<SectionView> out = new ArrayList<>();
    for (Section s : content.sections()) {
      List<LectureView> lectures = new ArrayList<>();
      for (Lecture l : s.lectures()) {
        int correct = 0;
        for (Question q : l.questions()) if (progress.quiz(key(s.id(), l.id(), q.id())).correct()) correct++;
        List<TaskSummary> tasks = new ArrayList<>();
        for (Task t : l.tasks()) {
          ProgressStore.TaskProgress p = progress.task(key(s.id(), l.id(), t.id()));
          tasks.add(new TaskSummary(t.id(), t.title(), t.kind(), t.difficulty(), p.done(), p.attempts()));
        }
        lectures.add(new LectureView(l.id(), l.number(), l.title(), l.questions().size(), correct, tasks));
      }
      out.add(new SectionView(s.id(), s.course(), s.title(), s.notes(), lectures));
    }
    return out;
  }

  @GetMapping("/quiz/{section}/{lecture}")
  public QuizView quiz(@PathVariable String section, @PathVariable String lecture) {
    Lecture l = lecture(section, lecture);
    List<QuestionView> qs = new ArrayList<>();
    for (Question q : l.questions()) {
      ProgressStore.QuizProgress p = progress.quiz(key(section, lecture, q.id()));
      qs.add(new QuestionView(q.id(), q.kind(), q.prompt(), q.code(), q.options(), p.attempts() > 0, p.correct()));
    }
    return new QuizView(section, lecture, l.title(), qs);
  }

  @PostMapping("/quiz/{section}/{lecture}/{question}/answer")
  public AnswerResult answer(
      @PathVariable String section,
      @PathVariable String lecture,
      @PathVariable String question,
      @RequestBody AnswerRequest body) {
    Question q = content.question(section, lecture, question).orElseThrow(() -> notFound("question"));
    boolean correct = body.choice() == q.answer();
    progress.recordAnswer(key(section, lecture, question), correct);
    return new AnswerResult(correct, q.answer(), q.explanation());
  }

  // ------------------------------------------------------------------ tasks

  @GetMapping("/tasks/{section}/{lecture}/{task}")
  public TaskView task(@PathVariable String section, @PathVariable String lecture, @PathVariable String task) {
    Lecture l = lecture(section, lecture);
    Task t = content.task(section, lecture, task).orElseThrow(() -> notFound("task"));
    return TaskView.of(section, lecture, l.title(), t, progress.task(key(section, lecture, task)).done());
  }

  @GetMapping("/tasks/{section}/{lecture}/{task}/solution")
  public SolutionView solution(@PathVariable String section, @PathVariable String lecture, @PathVariable String task) {
    Task t = content.task(section, lecture, task).orElseThrow(() -> notFound("task"));
    if (t.solution() == null) throw notFound("solution");
    return new SolutionView(
        t.solution().explanation(),
        t.solution().steps().stream().map(s -> new SolutionStep(s.session(), s.sql(), s.chaos())).toList());
  }

  /** Starts a fresh lab for the task (also used for "reset"). */
  @PostMapping("/tasks/{section}/{lecture}/{task}/lab")
  public LabView openLab(@PathVariable String section, @PathVariable String lecture, @PathVariable String task) {
    Task t = content.task(section, lecture, task).orElseThrow(() -> notFound("task"));
    return labs.open(key(section, lecture, task), t);
  }

  // ------------------------------------------------------------------ labs

  @GetMapping("/labs/{id}")
  public LabView lab(@PathVariable String id) {
    return labs.view(id);
  }

  @PostMapping("/labs/{id}/sessions/{session}/execute")
  public LabView execute(@PathVariable String id, @PathVariable String session, @RequestBody ExecuteRequest body) {
    return labs.execute(id, session, body.sql(), props.executeWaitMs());
  }

  @PostMapping("/labs/{id}/sessions/{session}/cancel")
  public LabView cancel(@PathVariable String id, @PathVariable String session) {
    return labs.cancel(id, session);
  }

  @PostMapping("/labs/{id}/chaos/{action}")
  public LabView chaos(@PathVariable String id, @PathVariable String action) {
    return labs.chaos(id, action);
  }

  @PostMapping("/labs/{id}/check")
  public CheckResponse check(@PathVariable String id) {
    LabView view = labs.view(id);
    CheckReport report = labs.check(id);
    progress.recordCheck(view.taskKey(), report.passed());
    return new CheckResponse(
        report.passed(), report.items().stream().map(i -> new CheckItem(i.description(), i.passed(), i.detail())).toList());
  }

  @DeleteMapping("/labs/{id}")
  public void close(@PathVariable String id) {
    labs.close(id);
  }

  // ------------------------------------------------------------------ helpers

  private Lecture lecture(String section, String lecture) {
    return content.lecture(section, lecture).orElseThrow(() -> notFound("lecture"));
  }

  private static String key(String section, String lecture, String item) {
    return section + "/" + lecture + "/" + item;
  }

  private static ResponseStatusException notFound(String what) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown " + what);
  }
}
