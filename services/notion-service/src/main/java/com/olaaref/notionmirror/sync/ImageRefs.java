package com.olaaref.notionmirror.sync;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds local image references in a page body: a Markdown image on its own line whose target is a relative path,
 * such as {@code ![caption](assets/photo.png)}. Pure logic, no Notion calls.
 */
final class ImageRefs {
    static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Pattern STANDALONE = Pattern.compile("^!\\[([^\\]]*)\\]\\(\\s*<?([^)\\s>]+)>?\\s*\\)$");
    private static final Pattern INLINE = Pattern.compile("!\\[[^\\]]*\\]\\(\\s*<?([^)\\s>]+)>?\\s*\\)");
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:.*");
    private static final Pattern WIN_ABS = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    static final String UNMATCHABLE = "";

    /**
     * One local image. anchor is the normalized text of the line it follows (null = it is first in the page,
     * "" = unknown, append at the end); prev is the index of the image it directly follows, or -1;
     * occurrence says which of several identical anchor lines it follows (0-based).
     */
    record Ref(int index, String alt, Path file, String contentType, String anchor, int occurrence, int prev) {}

    record Extraction(String stripped, List<Ref> refs, List<String> problems) {}

    static Extraction extract(Path pageDir, String body) {
        Path base = pageDir.toAbsolutePath().normalize();
        String[] lines = body.split("\n", -1);
        boolean[] drop = new boolean[lines.length];
        List<Ref> refs = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        boolean fence = false;
        String lastText = null;
        int lastOcc = 0;
        int lastRef = -1;

        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].strip();
            if (t.startsWith("```") || t.startsWith("~~~")) {
                fence = !fence;
                lastText = UNMATCHABLE;
                lastRef = -1;
                continue;
            }
            if (fence) {
                lastText = UNMATCHABLE;
                lastRef = -1;
                continue;
            }
            if (t.isEmpty()) continue;
            Matcher m = STANDALONE.matcher(t);
            if (m.matches() && !isRemote(m.group(2))) {
                String target = m.group(2);
                String problem = null;
                Path file = null;
                String ct = null;
                if (WIN_ABS.matcher(target).matches() || target.startsWith("/") || target.startsWith("#")) {
                    problem = "use a path relative to the page folder, not '" + target + "'";
                } else {
                    file = base.resolve(decode(target)).normalize();
                    ct = contentType(file.getFileName().toString());
                    if (!file.startsWith(base)) problem = "'" + target + "' points outside the page folder";
                    else if (!Files.isRegularFile(file)) problem = "file not found: " + target;
                    else if (ct == null) problem = "unsupported image type: " + target;
                    else {
                        try {
                            if (Files.size(file) > MAX_BYTES) problem = target + " is larger than 20 MB";
                        } catch (java.io.IOException e) {
                            problem = "cannot read " + target;
                        }
                    }
                }
                if (problem != null) {
                    problems.add("line " + (i + 1) + ": " + problem);
                    continue;
                }
                String anchor = lastRef >= 0 ? null : lastText;
                refs.add(new Ref(i, m.group(1), file, ct, lastRef >= 0 ? "" : anchor, lastOcc, lastRef));
                drop[i] = true;
                lastRef = refs.size() - 1;
                continue;
            }
            Matcher inl = INLINE.matcher(t);
            while (inl.find()) {
                if (!isRemote(inl.group(1))) {
                    problems.add("line " + (i + 1) + ": an image must be on its own line to be uploaded");
                    break;
                }
            }
            String n = norm(t);
            lastText = n.isEmpty() ? UNMATCHABLE : n;
            lastOcc = n.isEmpty() ? 0 : seen.merge(n, 1, Integer::sum) - 1;
            lastRef = -1;
        }

        boolean[] gone = drop.clone();
        for (int i = 0; i < lines.length; i++) {
            if (!drop[i]) continue;
            if (i + 1 < lines.length && lines[i + 1].isBlank() && !gone[i + 1]) gone[i + 1] = true;
            else if (i > 0 && lines[i - 1].isBlank() && !gone[i - 1]) gone[i - 1] = true;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < lines.length; i++) {
            if (gone[i]) continue;
            if (!first) sb.append('\n');
            sb.append(lines[i]);
            first = false;
        }
        return new Extraction(refs.isEmpty() ? body : sb.toString(), refs, problems);
    }

    static boolean isRemote(String target) {
        return SCHEME.matcher(target).matches() && !WIN_ABS.matcher(target).matches();
    }

    private static String decode(String target) {
        return URLDecoder.decode(target.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    static String contentType(String filename) {
        String n = filename.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        String ext = dot < 0 ? "" : n.substring(dot + 1);
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "heic" -> "image/heic";
            case "tif", "tiff" -> "image/tiff";
            case "ico" -> "image/vnd.microsoft.icon";
            default -> null;
        };
    }

    /** Plain comparable text of a Markdown line or of a Notion block's text. */
    static String norm(String s) {
        String x = s.strip();
        x = x.replaceFirst("^(#{1,6}\\s+|>\\s*|[-*+]\\s+|\\d+[.)]\\s+)", "");
        x = x.replaceFirst("^\\[[ xX]\\]\\s+", "");
        x = x.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        x = x.replaceAll("<[^>]+>", "");
        x = x.replace("**", "").replace("__", "").replace("~~", "").replace("`", "").replace("*", "").replace("\\", "");
        x = x.replaceAll("\\s+", " ").strip();
        return x.toLowerCase(Locale.ROOT);
    }
}
