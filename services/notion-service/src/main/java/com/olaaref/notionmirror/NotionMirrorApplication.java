package com.olaaref.notionmirror;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NotionMirrorApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotionMirrorApplication.class, args);
    }
}
