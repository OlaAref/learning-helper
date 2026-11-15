package com.olaaref.dbpracticelab.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small helpers to read typed values out of SnakeYAML's untyped maps with clear errors. */
final class Yml {
  private Yml() {}

  @SuppressWarnings("unchecked")
  static Map<String, Object> map(Object o, String where) {
    if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
    throw new IllegalStateException(where + ": expected a mapping");
  }

  static String str(Map<String, Object> m, String key, String where) {
    Object v = m.get(key);
    if (v == null) throw new IllegalStateException(where + ": missing '" + key + "'");
    return String.valueOf(v).stripTrailing();
  }

  static String str(Map<String, Object> m, String key, String dflt, String where) {
    Object v = m.get(key);
    return v == null ? dflt : String.valueOf(v).stripTrailing();
  }

  static int integer(Map<String, Object> m, String key, String where) {
    Object v = m.get(key);
    if (v instanceof Number n) return n.intValue();
    throw new IllegalStateException(where + ": '" + key + "' must be a number");
  }

  static List<Object> list(Map<String, Object> m, String key, String where) {
    Object v = m.get(key);
    if (v == null) return List.of();
    if (v instanceof List<?> l) return new ArrayList<>(l);
    throw new IllegalStateException(where + ": '" + key + "' must be a list");
  }

  static List<String> strings(Map<String, Object> m, String key, String where) {
    List<String> out = new ArrayList<>();
    for (Object o : list(m, key, where)) out.add(String.valueOf(o).stripTrailing());
    return out;
  }

  /** A cell as the checker compares it: SQL NULL (YAML null) becomes "NULL". */
  static String cell(Object o) {
    return o == null ? "NULL" : String.valueOf(o);
  }

  static List<List<String>> rows(Map<String, Object> m, String key, String where) {
    List<List<String>> out = new ArrayList<>();
    for (Object row : list(m, key, where)) {
      if (!(row instanceof List<?> r)) throw new IllegalStateException(where + ": '" + key + "' rows must be lists");
      List<String> cells = new ArrayList<>();
      for (Object c : r) cells.add(cell(c));
      out.add(cells);
    }
    return out;
  }
}
