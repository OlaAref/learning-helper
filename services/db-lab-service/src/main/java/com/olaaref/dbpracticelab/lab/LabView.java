package com.olaaref.dbpracticelab.lab;

import java.util.List;

/** Snapshot of a lab sent to the UI; the UI polls it while any statement is still RUNNING. */
public record LabView(String id, String taskKey, String target, int kills, List<SessionView> sessions) {

  public record SessionView(String id, List<EntryView> entries) {}

  /** status is RUNNING (e.g. waiting on a lock), OK or ERROR. */
  public record EntryView(int index, String sql, String status, long millis, List<StatementResult> statements) {}
}
