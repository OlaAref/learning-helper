package com.olaaref.dbpracticelab.lab;

import java.util.List;

/** What one statement produced: rows, an update count, or an error (never more than one of them). */
public record StatementResult(
    String sql,
    List<String> columns,
    List<List<String>> rows,
    boolean truncated,
    Integer updateCount,
    ErrorInfo error) {

  public record ErrorInfo(String sqlstate, String message) {}

  public boolean hasRows() {
    return columns != null;
  }

  static StatementResult rows(String sql, List<String> columns, List<List<String>> rows, boolean truncated) {
    return new StatementResult(sql, columns, rows, truncated, null, null);
  }

  static StatementResult updated(String sql, int count) {
    return new StatementResult(sql, null, null, false, count, null);
  }

  static StatementResult failed(String sql, String sqlstate, String message) {
    return new StatementResult(sql, null, null, false, null, new ErrorInfo(sqlstate, message));
  }
}
