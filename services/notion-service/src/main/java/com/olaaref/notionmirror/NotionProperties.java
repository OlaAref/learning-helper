package com.olaaref.notionmirror;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Notion connection settings. The token comes from the NOTION_TOKEN env var and is never stored on disk. */
@ConfigurationProperties("notion")
public record NotionProperties(String token, String version, String markdownVersion, List<String> mirrorScope) {}
