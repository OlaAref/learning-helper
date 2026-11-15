package com.olaaref.dbpracticelab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DbPracticeLabApplication {
  public static void main(String[] args) {
    SpringApplication.run(DbPracticeLabApplication.class, args);
  }
}
