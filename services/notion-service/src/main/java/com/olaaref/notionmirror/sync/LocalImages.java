package com.olaaref.notionmirror.sync;

import com.olaaref.notionmirror.NotionProperties;
import com.olaaref.notionmirror.notion.NotionApi;
import com.olaaref.notionmirror.sync.ImageRefs.Ref;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import com.olaaref.notionmirror.notion.NotionModels.Page;
import org.springframework.stereotype.Component;

/** Uploads local images to Notion (File Upload API) and attaches them to a page as image blocks. */
@Component
public class LocalImages {
    private final NotionApi notion;
    private final NotionProperties props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public LocalImages(NotionApi notion, NotionProperties props) {
        this.notion = notion;
        this.props = props;
    }

    /** A local image that has been uploaded to Notion but not yet placed on a page. */
    record Uploaded(Ref ref, String fileUploadId) {}

    ImageRefs.Extraction extract(java.nio.file.Path pageDir, String body) {
        return ImageRefs.extract(pageDir, body);
    }

    /** Step 1: create an upload and send the bytes. Nothing on any page changes yet. */
    List<Uploaded> upload(List<Ref> refs) throws IOException {
        List<Uploaded> out = new ArrayList<>();
        for (Ref r : refs) {
            String name = r.file().getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
            Map<String, Object> created = notion.createFileUpload(Map.of("filename", name, "content_type", r.contentType()));
            Object id = created == null ? null : created.get("id");
            if (!(id instanceof String fid)) throw new IOException("Notion did not return a file upload id for " + name);
            send(fid, name, r.contentType(), Files.readAllBytes(r.file()));
            out.add(new Uploaded(r, fid));
        }
        return out;
    }

    /** Step 2: add image blocks to the page, each after the line it followed locally (end of page if unsure). */
    List<String> attach(String pageId, List<Uploaded> uploads) {
        List<String> notes = new ArrayList<>();
        List<Block> blocks = topLevelBlocks(pageId);
        String[] newIds = new String[uploads.size()];
        for (int i = 0; i < uploads.size(); i++) {
            Uploaded u = uploads.get(i);
            Ref r = u.ref();
            Map<String, Object> position;
            if (r.prev() >= 0 && newIds[r.prev()] != null) {
                position = after(newIds[r.prev()]);
            } else if (r.prev() < 0 && r.anchor() == null) {
                position = Map.of("type", "start");
            } else {
                String id = r.prev() < 0 ? findBlock(blocks, r.anchor(), r.occurrence()) : null;
                if (id == null) {
                    position = Map.of("type", "end");
                    notes.add("Could not find where '" + r.file().getFileName() + "' belongs, added at the end of the page");
                } else {
                    position = after(id);
                }
            }
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("type", "file_upload");
            image.put("file_upload", Map.of("id", u.fileUploadId()));
            if (r.alt() != null && !r.alt().isBlank()) {
                image.put("caption", List.of(Map.of("type", "text", "text", Map.of("content", r.alt()))));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("children", List.of(Map.of("object", "block", "type", "image", "image", image)));
            body.put("position", position);
            Map<String, Object> res = notion.appendChildren(pageId, body);
            if (res != null && res.get("results") instanceof List<?> l && !l.isEmpty()
                    && l.getFirst() instanceof Map<?, ?> b && b.get("id") instanceof String bid) {
                newIds[i] = bid;
            }
        }
        return notes;
    }

    private static Map<String, Object> after(String id) {
        return Map.of("type", "after_block", "after_block", Map.of("id", id));
    }

    record Block(String id, String text) {}

    private List<Block> topLevelBlocks(String pageId) {
        List<Block> out = new ArrayList<>();
        Map<String, Object> r = notion.listChildren(pageId);
        while (r != null) {
            if (r.get("results") instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> b && b.get("id") instanceof String id) out.add(new Block(id, textOf(b)));
                }
            }
            if (Boolean.TRUE.equals(r.get("has_more")) && r.get("next_cursor") instanceof String c) {
                r = notion.listChildrenFrom(pageId, c);
            } else {
                r = null;
            }
        }
        return out;
    }

    static String textOf(Map<?, ?> block) {
        if (!(block.get("type") instanceof String type) || !(block.get(type) instanceof Map<?, ?> body)) return "";
        if (!(body.get("rich_text") instanceof List<?> parts)) return "";
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) {
            if (p instanceof Map<?, ?> m && m.get("plain_text") instanceof String t) sb.append(t);
        }
        return ImageRefs.norm(sb.toString());
    }

    static String findBlock(List<Block> blocks, String anchor, int occurrence) {
        if (anchor == null || anchor.isEmpty()) return null;
        int seen = 0;
        for (Block b : blocks) {
            if (b.text().equals(anchor)) {
                if (seen == occurrence) return b.id();
                seen++;
            }
        }
        return null;
    }

    private void send(String uploadId, String filename, String contentType, byte[] data) throws IOException {
        String boundary = "nm" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream bo = new ByteArrayOutputStream(data.length + 512);
        bo.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bo.write(data);
        bo.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] payload = bo.toByteArray();
        for (int attempt = 0; ; attempt++) {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.notion.com/v1/file_uploads/" + uploadId + "/send"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + props.token())
                    .header("Notion-Version", props.version())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            try {
                long started = System.nanoTime();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                org.slf4j.Logger httpLog = org.slf4j.LoggerFactory.getLogger("com.olaaref.notionmirror.notion.http");
                if (httpLog.isDebugEnabled()) {
                    httpLog.debug("Notion request: POST /v1/file_uploads/{}/send ({} bytes, {})\nNotion response: {} in {} ms\n  body: {}",
                            uploadId, data.length, contentType, res.statusCode(), (System.nanoTime() - started) / 1_000_000,
                            res.body() == null ? "" : res.body().substring(0, Math.min(1000, res.body().length())));
                }
                if (res.statusCode() == 429 && attempt < 3) {
                    Thread.sleep(1000L * (attempt + 1));
                    continue;
                }
                if (res.statusCode() / 100 != 2) {
                    String b = res.body() == null ? "" : res.body();
                    throw new IOException("Notion refused the upload of " + filename + " (" + res.statusCode() + "): "
                            + b.substring(0, Math.min(200, b.length())));
                }
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while uploading " + filename);
            }
        }
    }

    /** Verification run: throwaway page, upload a 1x1 PNG, place it after the first line, read back, trash the page. */
    public Map<String, Object> selfTest() throws IOException {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        java.nio.file.Path tmp = Files.createTempFile("nm-probe", ".png");
        Map<String, Object> out = new LinkedHashMap<>();
        Page page = null;
        try {
            Files.write(tmp, png);
            page = notion.createPage(Map.of("properties",
                    Map.of("title", Map.of("title", List.of(Map.of("text", Map.of("content", "notion-mirror image probe"))))),
                    "markdown", "First line\n\nSecond line", "markdown_version", props.markdownVersion()));
            Ref ref = new Ref(0, "probe image", tmp, "image/png", "first line", 0, -1);
            List<Uploaded> up = upload(List.of(ref));
            out.put("uploaded", up.size());
            out.put("notes", attach(page.id(), up));
            out.put("markdownAfter", notion.getMarkdown(page.id(), props.markdownVersion()).markdown());
            return out;
        } finally {
            Files.deleteIfExists(tmp);
            if (page != null) {
                try {
                    notion.updatePage(page.id(), Map.of("in_trash", true));
                    out.put("trashed", true);
                } catch (RuntimeException e) {
                    out.put("trashed", false);
                }
            }
        }
    }
}
