package com.olaaref.notionmirror.web;

import com.olaaref.notionmirror.NotionProperties;
import com.olaaref.notionmirror.SyncProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StatusController {
    private final NotionProperties notion;
    private final SyncProperties sync;

    public StatusController(NotionProperties notion, SyncProperties sync) {
        this.notion = notion;
        this.sync = sync;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "connected", notion.token() != null && !notion.token().isBlank(),
            "localFolder", sync.localFolder().toString(),
            "mode", sync.mode(),
            "intervalSeconds", sync.interval().toSeconds(),
            "pushEnabled", sync.pushEnabled());
    }
}
