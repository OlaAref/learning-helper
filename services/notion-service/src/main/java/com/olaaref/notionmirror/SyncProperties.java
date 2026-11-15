package com.olaaref.notionmirror;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sync")
public record SyncProperties(Path localFolder, Duration interval, String mode, boolean pushEnabled) {}
