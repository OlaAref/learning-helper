package com.olaaref.notionmirror.sync;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Images and files that Notion hosts come with links that expire after an hour. They are downloaded into an
 * assets folder next to the page's index.md, and the app serves them from there.
 */
@Component
public class Assets {
    private static final Pattern IMAGE_TAG = Pattern.compile("<(?:image)\\s+source=\"([^\"]+)\"");
    private static final Pattern FILE_TAG = Pattern.compile("<(?:file|pdf)\\s+src=\"([^\"]+)\"");
    private static final Pattern MD_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\((https://[^)\\s]+)\\)");
    private static final long MAX_BYTES = 40L * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** File name for a link, derived from the link's path only (not its signature). The UI computes the same name. */
    static String nameFor(String url) {
        int scheme = url.indexOf("://");
        String rest = scheme >= 0 ? url.substring(scheme + 3) : url;
        int slash = rest.indexOf('/');
        String path = slash >= 0 ? rest.substring(slash) : "/";
        int cut = -1;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '?' || c == '#') {
                cut = i;
                break;
            }
        }
        if (cut >= 0) path = path.substring(0, cut);
        String last = path.substring(path.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        if (last.length() > 60) last = last.substring(last.length() - 60);
        int h = 0x811c9dc5;
        for (int i = 0; i < path.length(); i++) {
            h ^= path.charAt(i);
            h *= 0x01000193;
        }
        return String.format("%08x", h) + "-" + last;
    }

    static boolean isNotionHosted(String url) {
        try {
            URI u = URI.create(url);
            String host = u.getHost();
            return "https".equals(u.getScheme()) && host != null
                    && (host.endsWith(".amazonaws.com") || host.endsWith("notion.so") || host.endsWith("notion.com")
                    || host.endsWith("notionusercontent.com") || host.endsWith("notion-static.com"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Notion-hosted image and file links in the Markdown. */
    static Set<String> notionUrls(String markdown) {
        Set<String> urls = new LinkedHashSet<>();
        for (Pattern p : List.of(IMAGE_TAG, FILE_TAG, MD_IMAGE)) {
            Matcher m = p.matcher(markdown);
            while (m.find()) {
                String url = m.group(1).replace("&amp;", "&");
                if (isNotionHosted(url)) urls.add(url);
            }
        }
        return urls;
    }

    public boolean anyMissing(Path pageDir, String markdown) {
        for (String url : notionUrls(markdown)) {
            if (!Files.exists(pageDir.resolve("assets").resolve(nameFor(url)))) return true;
        }
        return false;
    }

    public List<String> names(Path pageDir) {
        Path dir = pageDir.resolve("assets");
        if (!Files.isDirectory(dir)) return List.of();
        try (var s = Files.list(dir)) {
            return s.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                    .filter(n -> !n.endsWith(".tmp")).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Downloads what is not already there. Returns a message per failure; a failure never stops a sync. */
    public List<String> download(Path pageDir, String markdown) {
        List<String> problems = new ArrayList<>();
        for (String url : notionUrls(markdown)) {
            Path target = pageDir.resolve("assets").resolve(nameFor(url));
            if (Files.exists(target)) continue;
            try {
                Files.createDirectories(target.getParent());
                HttpResponse<InputStream> res = http.send(
                        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = res.body()) {
                    if (res.statusCode() != 200) {
                        problems.add("Image download failed (" + res.statusCode() + "): " + target.getFileName());
                        continue;
                    }
                    Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
                    long copied = 0;
                    try (var out = Files.newOutputStream(tmp)) {
                        copied = in.transferTo(new LimitedOutput(out, MAX_BYTES));
                    }
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException e) {
                problems.add("Image download failed: " + target.getFileName() + " (" + e.getMessage() + ")");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                problems.add("Image download interrupted");
                break;
            }
        }
        return problems;
    }

    /** Output stream that refuses to take more than a limit. */
    private static final class LimitedOutput extends java.io.OutputStream {
        private final java.io.OutputStream out;
        private long left;

        LimitedOutput(java.io.OutputStream out, long limit) {
            this.out = out;
            this.left = limit;
        }

        @Override
        public void write(int b) throws IOException {
            if (--left < 0) throw new IOException("file too large");
            out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            left -= len;
            if (left < 0) throw new IOException("file too large");
            out.write(b, off, len);
        }
    }
}
