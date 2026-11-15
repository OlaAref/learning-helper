package com.olaaref.dbpracticelab.progress;

import com.olaaref.dbpracticelab.config.LabProperties;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/** Quiz and task progress kept in a small YAML file (data/progress.yml, git-ignored). */
@Component
public class ProgressStore {
  public record QuizProgress(int attempts, boolean correct) {}

  public record TaskProgress(int attempts, boolean done) {}

  private final Path file;
  private final Map<String, Map<String, Object>> quiz = new LinkedHashMap<>();
  private final Map<String, Map<String, Object>> tasks = new LinkedHashMap<>();

  @Autowired
  public ProgressStore(LabProperties props) {
    this(Path.of(props.progressFile()));
  }

  public ProgressStore(Path file) {
    this.file = file;
    load();
  }

  public synchronized QuizProgress quiz(String key) {
    Map<String, Object> e = quiz.get(key);
    return e == null ? new QuizProgress(0, false) : new QuizProgress(num(e, "attempts"), Boolean.TRUE.equals(e.get("correct")));
  }

  public synchronized TaskProgress task(String key) {
    Map<String, Object> e = tasks.get(key);
    return e == null ? new TaskProgress(0, false) : new TaskProgress(num(e, "attempts"), Boolean.TRUE.equals(e.get("done")));
  }

  public synchronized void recordAnswer(String key, boolean correct) {
    Map<String, Object> e = quiz.computeIfAbsent(key, k -> new LinkedHashMap<>());
    e.put("attempts", num(e, "attempts") + 1);
    e.put("correct", correct);
    e.put("last", Instant.now().toString());
    save();
  }

  /** A failed check counts as an attempt; a passed one marks the task done for good. */
  public synchronized void recordCheck(String key, boolean passed) {
    Map<String, Object> e = tasks.computeIfAbsent(key, k -> new LinkedHashMap<>());
    e.put("attempts", num(e, "attempts") + 1);
    if (passed && !Boolean.TRUE.equals(e.get("done"))) {
      e.put("done", true);
      e.put("completedAt", Instant.now().toString());
    }
    save();
  }

  private static int num(Map<String, Object> e, String k) {
    return e.get(k) instanceof Number n ? n.intValue() : 0;
  }

  @SuppressWarnings("unchecked")
  private void load() {
    if (!Files.exists(file)) return;
    try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      Object root = new Yaml().load(r);
      if (root instanceof Map<?, ?> m) {
        copy((Map<String, Map<String, Object>>) m.get("quiz"), quiz);
        copy((Map<String, Map<String, Object>>) m.get("tasks"), tasks);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + file, e);
    }
  }

  private static void copy(Map<String, Map<String, Object>> from, Map<String, Map<String, Object>> to) {
    if (from != null) from.forEach((k, v) -> to.put(k, new LinkedHashMap<>(v)));
  }

  private void save() {
    try {
      if (file.getParent() != null) Files.createDirectories(file.getParent());
      DumperOptions opts = new DumperOptions();
      opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("quiz", quiz);
      root.put("tasks", tasks);
      try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
        new Yaml(opts).dump(root, w);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot write " + file, e);
    }
  }
}
