package com.olaaref.dbpracticelab.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LabServiceUrlTest {
  @Test
  void replacesTheDatabaseNameAndKeepsParameters() {
    assertEquals("jdbc:postgresql://127.0.0.1:5433/dblab_x", LabService.withDatabase("jdbc:postgresql://127.0.0.1:5433/postgres", "dblab_x"));
    assertEquals("jdbc:postgresql://h:5/dblab_x?ssl=false", LabService.withDatabase("jdbc:postgresql://h:5/postgres?ssl=false", "dblab_x"));
  }
}
