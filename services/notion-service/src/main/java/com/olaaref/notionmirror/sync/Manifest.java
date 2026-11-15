package com.olaaref.notionmirror.sync;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent record of what has been mirrored: Notion page id to local path and sync state. */
public record Manifest(Map<String, Entry> pages) {
    public Manifest {
        pages = pages == null ? new LinkedHashMap<>() : new LinkedHashMap<>(pages);
    }

    /** path is relative to the mirror root, always with forward slashes. */
    public record Entry(
            String id,
            String title,
            String parentId,
            String path,
            String lastEdited,
            String bodyHash,
            boolean partial) {}
}
