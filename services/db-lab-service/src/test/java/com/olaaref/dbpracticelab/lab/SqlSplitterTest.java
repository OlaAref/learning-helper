package com.olaaref.dbpracticelab.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SqlSplitterTest {
  @Test
  void splitsOnSemicolonsAndTrims() {
    assertEquals(List.of("BEGIN", "UPDATE t SET a = 1", "COMMIT"), SqlSplitter.split("BEGIN;\n UPDATE t SET a = 1 ;\nCOMMIT;"));
  }

  @Test
  void ignoresSemicolonsInQuotesCommentsAndDollarBodies() {
    assertEquals(List.of("SELECT 'a;b'"), SqlSplitter.split("SELECT 'a;b';"));
    assertEquals(List.of("SELECT \"x;y\" FROM t"), SqlSplitter.split("SELECT \"x;y\" FROM t"));
    assertEquals(List.of("SELECT 1 -- a; b\n, 2"), SqlSplitter.split("SELECT 1 -- a; b\n, 2;"));
    assertEquals(List.of("SELECT /* a; /* b; */ c; */ 1"), SqlSplitter.split("SELECT /* a; /* b; */ c; */ 1;"));
    assertEquals(
        List.of("DO $$ BEGIN PERFORM 1; END $$", "SELECT 2"), SqlSplitter.split("DO $$ BEGIN PERFORM 1; END $$; SELECT 2"));
  }

  @Test
  void handlesEscapedQuotesAndParameters() {
    assertEquals(List.of("SELECT 'it''s; fine'"), SqlSplitter.split("SELECT 'it''s; fine';"));
    assertEquals(List.of("SELECT $1", "SELECT 2"), SqlSplitter.split("SELECT $1; SELECT 2"));
  }

  @Test
  void blankInputGivesNoStatements() {
    assertEquals(List.of(), SqlSplitter.split("  ;; \n ;"));
  }
}
