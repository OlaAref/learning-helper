package com.olaaref.dbpracticelab.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under the {@code lab.*} prefix (see application.yml). */
@ConfigurationProperties(prefix = "lab")
public record LabProperties(
    String contentDir,
    String progressFile,
    long executeWaitMs,
    Map<String, Target> targets,
    Chaos chaos) {

  /** A PostgreSQL server the labs run on. The url points at the maintenance database. */
  public record Target(String url, String user, String password) {}

  /** Commands that crash and restart the "crash" target (docker kill / docker start by default). */
  public record Chaos(String killCommand, String startCommand, long readyTimeoutMs) {}
}
