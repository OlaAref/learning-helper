package com.olaaref.dbpracticelab.lab;

import com.olaaref.dbpracticelab.content.Model.Check;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Grades a lab. "query" checks run on a fresh connection, so they see only committed data (an open
 * transaction in a session does not count). The other checks look at what happened in the sessions.
 */
final class Checker {
  private Checker() {}

  static CheckReport run(Lab lab, LabSession.ConnectionFactory fresh) {
    List<CheckReport.Item> items = new ArrayList<>();
    boolean all = true;
    for (Check c : lab.task.checks()) {
      CheckReport.Item item;
      try {
        item = switch (c.type()) {
          case "query" -> query(c, fresh);
          case "error" -> error(c, lab);
          case "result" -> result(c, lab);
          case "chaos" -> chaos(c, lab);
          default -> new CheckReport.Item(c.description(), false, "unknown check type " + c.type());
        };
      } catch (SQLException e) {
        item = new CheckReport.Item(c.description(), false, "check query failed: " + e.getMessage());
      }
      all &= item.passed();
      items.add(item);
    }
    return new CheckReport(all, items);
  }

  private static CheckReport.Item query(Check c, LabSession.ConnectionFactory fresh) throws SQLException {
    try (Connection conn = fresh.open(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(c.sql())) {
      int cols = rs.getMetaData().getColumnCount();
      List<List<String>> actual = new ArrayList<>();
      while (rs.next()) {
        List<String> row = new ArrayList<>();
        for (int i = 1; i <= cols; i++) {
          String v = rs.getString(i);
          row.add(v == null ? "NULL" : v);
        }
        actual.add(row);
      }
      return compare(c, actual, "committed data");
    }
  }

  private static CheckReport.Item error(Check c, Lab lab) {
    for (LabSession s : lab.sessions.values()) {
      if (!c.session().isEmpty() && !c.session().equals(s.id())) continue;
      for (LabSession.Entry e : s.history()) {
        for (StatementResult r : e.results()) {
          if (r.error() != null && c.sqlstate().equals(r.error().sqlstate())) {
            return new CheckReport.Item(c.description(), true, "");
          }
        }
      }
    }
    return new CheckReport.Item(c.description(), false, "no statement failed with SQLSTATE " + c.sqlstate() + " yet");
  }

  private static CheckReport.Item result(Check c, Lab lab) {
    LabSession s = lab.sessions.get(c.session());
    if (s == null) return new CheckReport.Item(c.description(), false, "unknown session " + c.session());
    List<StatementResult> sets = new ArrayList<>();
    for (LabSession.Entry e : s.history()) {
      StatementResult r = e.lastResultSet();
      if (r != null) sets.add(r);
    }
    int idx;
    if (c.nth().equals("last")) {
      idx = sets.size();
    } else {
      try {
        idx = Integer.parseInt(c.nth());
      } catch (NumberFormatException ex) {
        return new CheckReport.Item(c.description(), false, "bad nth " + c.nth());
      }
    }
    if (idx < 1 || idx > sets.size()) {
      return new CheckReport.Item(
          c.description(), false, "session " + s.id() + " has returned " + sets.size() + " result set(s); expected at least " + idx);
    }
    List<List<String>> actual = new ArrayList<>();
    for (List<String> row : sets.get(idx - 1).rows()) {
      List<String> cells = new ArrayList<>();
      for (String v : row) cells.add(v == null ? "NULL" : v);
      actual.add(cells);
    }
    return compare(c, actual, "result #" + idx + " of session " + s.id());
  }

  private static CheckReport.Item chaos(Check c, Lab lab) {
    boolean killed = lab.kills.get() > 0;
    return new CheckReport.Item(c.description(), killed == c.killed(), killed == c.killed() ? "" : "the server has not been killed yet");
  }

  private static CheckReport.Item compare(Check c, List<List<String>> actual, String what) {
    boolean ok = actual.equals(c.expect());
    return new CheckReport.Item(c.description(), ok, ok ? "" : "expected " + c.expect() + " but " + what + " is " + actual);
  }
}
