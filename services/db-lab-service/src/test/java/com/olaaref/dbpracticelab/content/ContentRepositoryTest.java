package com.olaaref.dbpracticelab.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.olaaref.dbpracticelab.content.Model.Lecture;
import com.olaaref.dbpracticelab.content.Model.Section;
import com.olaaref.dbpracticelab.content.Model.Task;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The shipped content must load and be self-consistent (the loader validates ids, answers and checks). */
class ContentRepositoryTest {
  private final ContentRepository repo = new ContentRepository(Path.of("../../content"));

  @Test
  void acidSectionLoads() {
    Section acid = repo.section("acid").orElseThrow();
    assertEquals(10, acid.lectures().size());
    assertFalse(acid.lectures().stream().mapToInt(l -> l.questions().size()).sum() == 0);
    assertFalse(acid.lectures().stream().mapToInt(l -> l.tasks().size()).sum() == 0);
  }

  @Test
  void everyTaskHasASolutionAndEveryQuestionAValidAnswer() {
    for (Section s : repo.sections()) {
      for (Lecture l : s.lectures()) {
        for (Task t : l.tasks()) {
          assertNotNull(t.solution(), t.id() + " needs a reference solution");
          assertFalse(t.solution().steps().isEmpty(), t.id() + " needs solution steps");
        }
        l.questions().forEach(q -> assertTrue(q.answer() >= 0 && q.answer() < q.options().size()));
      }
    }
  }
}
