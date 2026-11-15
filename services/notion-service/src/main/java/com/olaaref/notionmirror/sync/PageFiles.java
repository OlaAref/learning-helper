package com.olaaref.notionmirror.sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

/** Reading and writing the index.md files: front matter plus the page body. */
public final class PageFiles {
    private PageFiles() {}

    /** Notion's file links carry a signature that changes on every request; it is not part of the content. */
    private static final Pattern SIGNED_URL =
            Pattern.compile("(https://[^\\s\"'<>)]*?)\\?[^\\s\"'<>)]*X-Amz-[^\\s\"'<>)]*");

    public record Parsed(Map<String, String> meta, String body) {}

    /** The body with signed-URL query strings removed, so two fetches of the same content compare equal. */
    public static String canonical(String s) {
        return SIGNED_URL.matcher(s).replaceAll("$1");
    }

    public static String render(String id, String title, String parentId, String lastEdited, boolean partial, String body) {
        StringBuilder sb = new StringBuilder("---\n");
        sb.append("notion_id: ").append(id).append('\n');
        sb.append("title: ").append(quote(title)).append('\n');
        sb.append("parent_id: ").append(parentId == null ? "null" : parentId).append('\n');
        sb.append("last_edited: ").append(lastEdited).append('\n');
        sb.append("last_synced: ").append(Instant.now()).append('\n');
        if (partial) sb.append("partial: true\n");
        sb.append("---\n\n").append(body.strip()).append('\n');
        return sb.toString();
    }

    public static Parsed parse(String text) {
        String t = text.replace("\r\n", "\n");
        Map<String, String> meta = new HashMap<>();
        if (!t.startsWith("---\n")) return new Parsed(meta, t);
        int end = t.indexOf("\n---\n", 3);
        if (end < 0) return new Parsed(meta, t);
        for (String line : t.substring(4, end).split("\n")) {
            int i = line.indexOf(':');
            if (i > 0) meta.put(line.substring(0, i).trim(), unquote(line.substring(i + 1).trim()));
        }
        return new Parsed(meta, t.substring(end + 5).strip());
    }

    public static void writeAtomic(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Hash of the body as it should be compared: signed-URL signatures ignored. */
    public static String sha256(String s) {
        return digest(canonical(s).strip());
    }

    /** True when the body matches a stored hash, including hashes written before signatures were ignored. */
    public static boolean matches(String body, String storedHash) {
        return sha256(body).equals(storedHash) || digest(body.strip()).equals(storedHash);
    }

    private static String digest(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String quote(String s) {
        return "\"" + (s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")) + "\"";
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            return v.substring(1, v.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return v;
    }
}
