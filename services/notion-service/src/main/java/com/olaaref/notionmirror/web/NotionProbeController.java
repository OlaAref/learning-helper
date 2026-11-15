package com.olaaref.notionmirror.web;

import com.olaaref.notionmirror.NotionProperties;
import com.olaaref.notionmirror.notion.NotionApi;
import com.olaaref.notionmirror.notion.NotionModels.Page;
import com.olaaref.notionmirror.notion.NotionModels.PageMarkdown;
import com.olaaref.notionmirror.notion.NotionModels.SearchResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;

/**
 * Phase 2 verification endpoints. They answer the open questions from the architecture doc:
 * does the token work, can it create a workspace-level page, and what does the Markdown round trip look like.
 */
@RestController
@RequestMapping("/api/notion")
public class NotionProbeController {
    private final NotionApi notion;
    private final NotionProperties props;
    private final com.olaaref.notionmirror.sync.LocalImages images;

    public NotionProbeController(NotionApi notion, NotionProperties props, com.olaaref.notionmirror.sync.LocalImages images) {
        this.images = images;
        this.notion = notion;
        this.props = props;
    }

    /** Read-only: who am I, and how many pages can the token see. */
    @GetMapping("/probe")
    public ResponseEntity<Map<String, Object>> probe() {
        return run(() -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("me", notion.me());
            SearchResponse s = notion.search(Map.of(
                    "filter", Map.of("property", "object", "value", "page"),
                    "page_size", 10));
            out.put("pagesSample", s.results().stream().map(p -> Map.of(
                    "id", p.id(), "url", String.valueOf(p.url()), "parent", p.parent())).toList());
            out.put("hasMore", s.hasMore());
            return out;
        });
    }

    /** Writes: creates a workspace-level test page, reads it back as Markdown, then moves it to trash. */
    @PostMapping("/probe/roundtrip")
    public ResponseEntity<Map<String, Object>> roundtrip() {
        return run(() -> {
            Map<String, Object> out = new LinkedHashMap<>();
            Page page = notion.createPage(Map.of(
                    "markdown", "# notion-mirror probe\n\nHello **world**\n\n- item one\n- item two\n"));
            out.put("created", Map.of("id", page.id(), "url", String.valueOf(page.url()), "parent", page.parent()));
            try {
                PageMarkdown md = notion.getMarkdown(page.id(), props.markdownVersion());
                out.put("markdownVersion", props.markdownVersion());
                out.put("markdownRead", md.markdown());
                out.put("truncated", md.truncated());
            } finally {
                Page trashed = notion.updatePage(page.id(), Map.of("in_trash", true));
                out.put("trashed", trashed.inTrash());
            }
            return out;
        });
    }

    /**
     * Writes: tests replacing a page's content. Creates a workspace-level test page, replaces its content,
     * reads it back, then moves it to trash. Run this before turning sync.push-enabled on.
     */
    @PostMapping("/probe/push")
    public ResponseEntity<Map<String, Object>> pushProbe() {
        return run(() -> {
            Map<String, Object> out = new LinkedHashMap<>();
            Page page = notion.createPage(Map.of("markdown", "# push probe\n\nOriginal text\n"));
            out.put("created", page.id());
            try {
                Map<String, Object> inner = new LinkedHashMap<>();
                inner.put("new_str", "Replaced **text**\n\n- a\n- b\n\n## Section\n\nMore text");
                inner.put("allow_deleting_content", false);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("type", "replace_content");
                body.put("replace_content", inner);
                body.put("markdown_version", props.markdownVersion());
                out.put("updateResponse", notion.updateMarkdown(page.id(), body));
                out.put("readBack", notion.getMarkdown(page.id(), props.markdownVersion()).markdown());
                out.put("lastEditedAfter", notion.getPage(page.id()).lastEditedTime());
            } finally {
                out.put("trashed", notion.updatePage(page.id(), Map.of("in_trash", true)).inTrash());
            }
            return out;
        });
    }

    /** Verifies image upload: throwaway page, tiny PNG uploaded and attached after the first line, then trashed. */
    @PostMapping("/probe/image")
    public ResponseEntity<Map<String, Object>> imageProbe() {
        return run(() -> {
            try {
                return images.selfTest();
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        });
    }

    private ResponseEntity<Map<String, Object>> run(java.util.function.Supplier<Map<String, Object>> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (RestClientResponseException e) {
            return ResponseEntity.status(HttpStatusCode.valueOf(e.getStatusCode().value()))
                    .body(Map.of("notionStatus", e.getStatusCode().value(), "notionError", e.getResponseBodyAsString()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
