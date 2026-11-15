package com.olaaref.dbpracticelab.content;

import com.olaaref.dbpracticelab.config.LabProperties;
import com.olaaref.dbpracticelab.content.Model.Check;
import com.olaaref.dbpracticelab.content.Model.Lecture;
import com.olaaref.dbpracticelab.content.Model.Question;
import com.olaaref.dbpracticelab.content.Model.Section;
import com.olaaref.dbpracticelab.content.Model.Solution;
import com.olaaref.dbpracticelab.content.Model.Step;
import com.olaaref.dbpracticelab.content.Model.Task;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/** Loads and validates all course content at startup; a malformed file stops the app with its path. */
@Component
public class ContentRepository {
  private static final Set<String> TASK_KINDS = Set.of("sql", "concurrency", "crash");
  private static final Set<String> CHECK_TYPES = Set.of("query", "error", "result", "chaos");

  private final List<Section> sections;

  @Autowired
  public ContentRepository(LabProperties props) {
    this(Path.of(props.contentDir()));
  }

  public ContentRepository(Path root) {
    this.sections = load(root);
  }

  public List<Section> sections() {
    return sections;
  }

  public Optional<Section> section(String id) {
    return sections.stream().filter(s -> s.id().equals(id)).findFirst();
  }

  public Optional<Lecture> lecture(String sectionId, String lectureId) {
    return section(sectionId).flatMap(s -> s.lectures().stream().filter(l -> l.id().equals(lectureId)).findFirst());
  }

  public Optional<Task> task(String sectionId, String lectureId, String taskId) {
    return lecture(sectionId, lectureId).flatMap(l -> l.tasks().stream().filter(t -> t.id().equals(taskId)).findFirst());
  }

  public Optional<Question> question(String sectionId, String lectureId, String questionId) {
    return lecture(sectionId, lectureId)
        .flatMap(l -> l.questions().stream().filter(q -> q.id().equals(questionId)).findFirst());
  }

  // ---------------------------------------------------------------- loading

  private static List<Section> load(Path root) {
    if (!Files.isDirectory(root)) throw new IllegalStateException("Content directory not found: " + root.toAbsolutePath());
    List<Section> out = new ArrayList<>();
    try (Stream<Path> dirs = Files.list(root)) {
      for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
        if (Files.exists(dir.resolve("section.yml"))) out.add(loadSection(dir));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + root, e);
    }
    Set<String> ids = new HashSet<>();
    for (Section s : out) if (!ids.add(s.id())) throw new IllegalStateException("Duplicate section id " + s.id());
    out.sort(Comparator.comparing(Section::id));
    return List.copyOf(out);
  }

  private static Section loadSection(Path dir) {
    Path file = dir.resolve("section.yml");
    String where = file.toString();
    Map<String, Object> m = Yml.map(read(file), where);
    List<Lecture> lectures = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (Object o : Yml.list(m, "lectures", where)) {
      Map<String, Object> lm = Yml.map(o, where + " lectures[]");
      String id = Yml.str(lm, "id", where);
      if (!ids.add(id)) throw new IllegalStateException(where + ": duplicate lecture id " + id);
      Path ldir = dir.resolve(id);
      lectures.add(new Lecture(
          id,
          Yml.integer(lm, "number", where),
          Yml.str(lm, "title", where),
          loadQuiz(ldir.resolve("quiz.yml")),
          loadTasks(ldir)));
    }
    return new Section(
        Yml.str(m, "id", where),
        Yml.str(m, "course", where),
        Yml.str(m, "title", where),
        Yml.str(m, "notes", "", where),
        List.copyOf(lectures));
  }

  private static List<Question> loadQuiz(Path file) {
    if (!Files.exists(file)) return List.of();
    String where = file.toString();
    Map<String, Object> m = Yml.map(read(file), where);
    List<Question> out = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (Object o : Yml.list(m, "questions", where)) {
      Map<String, Object> q = Yml.map(o, where + " questions[]");
      String id = Yml.str(q, "id", where);
      String w = where + " question " + id;
      if (!ids.add(id)) throw new IllegalStateException(w + ": duplicate id");
      List<String> options = Yml.strings(q, "options", w);
      int answer = Yml.integer(q, "answer", w);
      if (options.size() < 2) throw new IllegalStateException(w + ": needs at least 2 options");
      if (answer < 0 || answer >= options.size()) throw new IllegalStateException(w + ": answer out of range");
      String kind = Yml.str(q, "kind", "mcq", w);
      if (!kind.equals("mcq") && !kind.equals("predict")) throw new IllegalStateException(w + ": bad kind " + kind);
      out.add(new Question(
          id, kind, Yml.str(q, "prompt", w), Yml.str(q, "code", "", w), options, answer, Yml.str(q, "explanation", w)));
    }
    return List.copyOf(out);
  }

  private static List<Task> loadTasks(Path ldir) {
    if (!Files.isDirectory(ldir)) return List.of();
    List<Task> out = new ArrayList<>();
    try (Stream<Path> files = Files.list(ldir)) {
      for (Path f : files.filter(p -> p.getFileName().toString().startsWith("task-")).sorted().toList()) {
        out.add(loadTask(f));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + ldir, e);
    }
    return List.copyOf(out);
  }

  private static Task loadTask(Path file) {
    String where = file.toString();
    Map<String, Object> m = Yml.map(read(file), where);
    String id = Yml.str(m, "id", where);
    String kind = Yml.str(m, "kind", where);
    if (!TASK_KINDS.contains(kind)) throw new IllegalStateException(where + ": bad kind " + kind);
    String target = Yml.str(m, "target", kind.equals("crash") ? "crash" : "main", where);
    List<String> sessions = Yml.strings(m, "sessions", where);
    if (sessions.isEmpty()) sessions = kind.equals("concurrency") ? List.of("A", "B") : List.of("A");

    List<Check> checks = new ArrayList<>();
    for (Object o : Yml.list(m, "checks", where)) {
      Map<String, Object> c = Yml.map(o, where + " checks[]");
      String type = Yml.str(c, "type", where);
      if (!CHECK_TYPES.contains(type)) throw new IllegalStateException(where + ": bad check type " + type);
      Check check = new Check(
          type,
          Yml.str(c, "description", where),
          Yml.str(c, "sql", "", where),
          Yml.rows(c, "expect", where),
          Yml.str(c, "sqlstate", "", where),
          Yml.str(c, "session", "", where),
          Yml.str(c, "nth", "1", where),
          Boolean.TRUE.equals(c.get("killed")));
      if (type.equals("query") && check.sql().isBlank()) throw new IllegalStateException(where + ": query check needs sql");
      if (type.equals("error") && check.sqlstate().isBlank()) throw new IllegalStateException(where + ": error check needs sqlstate");
      if (type.equals("result") && !sessions.contains(check.session())) throw new IllegalStateException(where + ": result check needs a valid session");
      checks.add(check);
    }
    if (checks.isEmpty()) throw new IllegalStateException(where + ": a task needs at least one check");

    Solution solution = null;
    if (m.get("solution") != null) {
      Map<String, Object> s = Yml.map(m.get("solution"), where + " solution");
      List<Step> steps = new ArrayList<>();
      for (Object o : Yml.list(s, "steps", where)) {
        Map<String, Object> st = Yml.map(o, where + " solution.steps[]");
        Step step = new Step(Yml.str(st, "session", "", where), Yml.str(st, "sql", "", where), Yml.str(st, "chaos", "", where));
        boolean isChaos = !step.chaos().isEmpty();
        if (isChaos == !step.sql().isEmpty() || (!isChaos && !sessions.contains(step.session()))) {
          throw new IllegalStateException(where + ": each solution step needs either (session + sql) or chaos");
        }
        steps.add(step);
      }
      solution = new Solution(Yml.str(s, "explanation", "", where), List.copyOf(steps));
    }
    return new Task(
        id,
        Yml.str(m, "title", where),
        kind,
        target,
        Yml.str(m, "difficulty", "medium", where),
        List.copyOf(sessions),
        Yml.str(m, "statement", where),
        Yml.str(m, "setup", "", where),
        Yml.strings(m, "hints", where),
        List.copyOf(checks),
        solution);
  }

  private static Object read(Path file) {
    try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      return new Yaml().load(r);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + file, e);
    }
  }
}
