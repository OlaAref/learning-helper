package com.olaaref.notionmirror.notion;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** Minimal Notion API shapes. Unknown fields are ignored. */
public final class NotionModels {
    private NotionModels() {}

    public record Page(
            String id,
            String url,
            @JsonProperty("last_edited_time") String lastEditedTime,
            @JsonProperty("in_trash") boolean inTrash,
            Map<String, Object> parent,
            Map<String, Object> properties) {}

    public record SearchResponse(
            List<Page> results,
            @JsonProperty("has_more") boolean hasMore,
            @JsonProperty("next_cursor") String nextCursor) {}

    public record PageMarkdown(
            String id,
            String markdown,
            boolean truncated,
            @JsonProperty("unknown_block_ids") List<String> unknownBlockIds) {}
}
