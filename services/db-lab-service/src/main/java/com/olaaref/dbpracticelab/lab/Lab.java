package com.olaaref.dbpracticelab.lab;

import com.olaaref.dbpracticelab.config.LabProperties;
import com.olaaref.dbpracticelab.content.Model.Task;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** A throwaway database plus the sessions (terminals) connected to it for one task attempt. */
final class Lab {
  final String id;
  final String taskKey;
  final Task task;
  final String targetName;
  final LabProperties.Target target;
  final String dbName;
  final Map<String, LabSession> sessions = new LinkedHashMap<>();
  final AtomicInteger kills = new AtomicInteger();

  Lab(String id, String taskKey, Task task, String targetName, LabProperties.Target target, String dbName) {
    this.id = id;
    this.taskKey = taskKey;
    this.task = task;
    this.targetName = targetName;
    this.target = target;
    this.dbName = dbName;
  }
}
