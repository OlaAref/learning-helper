package com.olaaref.dbpracticelab.lab;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a submission into statements the way psql does: on semicolons that are not inside quotes,
 * comments or dollar-quoted bodies. Each statement is then run on its own, so BEGIN / COMMIT typed by
 * the learner behave exactly as in a psql session.
 */
public final class SqlSplitter {
  private SqlSplitter() {}

  public static List<String> split(String sql) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    int n = sql.length();
    int i = 0;
    while (i < n) {
      char ch = sql.charAt(i);
      if (ch == '\'' || ch == '"') {
        int j = skipQuoted(sql, i, ch);
        cur.append(sql, i, j);
        i = j;
      } else if (ch == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
        int j = sql.indexOf('\n', i);
        if (j < 0) j = n;
        cur.append(sql, i, j);
        i = j;
      } else if (ch == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
        int j = skipBlockComment(sql, i);
        cur.append(sql, i, j);
        i = j;
      } else if (ch == '$') {
        String tag = dollarTag(sql, i);
        if (tag == null) {
          cur.append(ch);
          i++;
        } else {
          int end = sql.indexOf(tag, i + tag.length());
          int j = end < 0 ? n : end + tag.length();
          cur.append(sql, i, j);
          i = j;
        }
      } else if (ch == ';') {
        flush(out, cur);
        i++;
      } else {
        cur.append(ch);
        i++;
      }
    }
    flush(out, cur);
    return out;
  }

  private static void flush(List<String> out, StringBuilder cur) {
    String s = cur.toString().strip();
    if (!s.isEmpty()) out.add(s);
    cur.setLength(0);
  }

  /** Index just past the closing quote; a doubled quote is an escaped quote. */
  private static int skipQuoted(String s, int start, char quote) {
    int i = start + 1;
    while (i < s.length()) {
      if (s.charAt(i) == quote) {
        if (i + 1 < s.length() && s.charAt(i + 1) == quote) {
          i += 2;
          continue;
        }
        return i + 1;
      }
      i++;
    }
    return s.length();
  }

  /** Block comments nest in PostgreSQL. */
  private static int skipBlockComment(String s, int start) {
    int depth = 0;
    int i = start;
    while (i < s.length()) {
      if (s.startsWith("/*", i)) {
        depth++;
        i += 2;
      } else if (s.startsWith("*/", i)) {
        depth--;
        i += 2;
        if (depth == 0) return i;
      } else {
        i++;
      }
    }
    return s.length();
  }

  /** Returns "$tag$" if one opens at index i (not a $1 parameter), otherwise null. */
  private static String dollarTag(String s, int i) {
    int j = i + 1;
    if (j < s.length() && Character.isDigit(s.charAt(j))) return null;
    while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
    return j < s.length() && s.charAt(j) == '$' ? s.substring(i, j + 1) : null;
  }
}
