package com.olaaref.dbpracticelab.lab;

import java.util.List;

public record CheckReport(boolean passed, List<Item> items) {
  public record Item(String description, boolean passed, String detail) {}
}
