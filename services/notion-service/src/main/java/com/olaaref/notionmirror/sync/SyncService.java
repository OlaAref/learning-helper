package com.olaaref.notionmirror.sync;

import com.olaaref.notionmirror.NotionProperties;
import com.olaaref.notionmirror.SyncProperties;
import com.olaaref.notionmirror.notion.NotionApi;
import com.olaaref.notionmirror.notion.NotionModels.Page;
import com.olaaref.notionmirror.notion.NotionModels.PageMarkdown;
import com.olaaref.notionmirror.notion.NotionModels.SearchResponse;
import com.olaaref.notionmirror.sync.Manifest.Entry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

/**
 * Two-way sync between Notion and local files. Each page is a folder holding index.md; sub-pages are nested folders.
 *
 * Per page: nothing changed = skip; only Notion changed = pull; only the file changed = push;
 * both changed = conflict (the newer edit wins, the older is saved as index.conflict-&lt;timestamp&gt;.md).
 * Nothing is ever deleted: pages that vanish from Notion are moved to .notion-sync/trash.
 * Pushing is off unless sync.push-enabled=true.
 */
@Service
public class SyncService {
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final Pattern RESERVED = Pattern.compile("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final NotionApi notion;
    private final NotionProperties props;
    private final SyncProperties sync;
    private final ManifestStore store;
    private final SyncEvents events;
    private final Assets assets;
    private final LocalImages images;
    private final ReentrantLock lock = new ReentrantLock();

    public SyncService(NotionApi notion, NotionProperties props, SyncProperties sync, ManifestStore store, SyncEvents events, Assets assets, LocalImages images) {
        this.events = events;
        this.images = images;
        this.assets = assets;
        this.notion = notion;
        this.props = props;
        this.sync = sync;
        this.store = store;
    }

    public record Summary(boolean busy, int pulled, int pushed, int created, int conflicts, int skipped, int moved,
                          int trashed, List<String> notes, List<String> errors) {}

    public record TreeNode(String id, String title, boolean partial, List<TreeNode> children) {}

    public record PageDoc(String id, String title, String parentId, String path, String lastEdited, boolean partial, String markdown, List<String> assets) {}

    private static final class Node {
        final Page page;
        final String title;
        final String parentId;
        final List<Node> kids = new ArrayList<>();

        Node(Page page) {
            this.page = page;
            this.title = titleOf(page);
            this.parentId = parentOf(page);
        }
    }

    private static final class Stats {
        int pulled, pushed, created, conflicts, skipped, moved, trashed;
        final List<String> notes = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        Summary summary() {
            return new Summary(false, pulled, pushed, created, conflicts, skipped, moved, trashed, notes, errors);
        }
    }

    // ------------------------------------------------------------------ sync

    public Summary sync() {
        if (!lock.tryLock()) return new Summary(true, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
        try {
            return doSync();
        } finally {
            lock.unlock();
        }
    }

    private Summary doSync() {
        List<Page> pages = fetchAll();
        Map<String, Node> byId = new LinkedHashMap<>();
        for (Page p : pages) byId.put(p.id(), new Node(p));

        List<Node> roots = new ArrayList<>();
        for (Node n : byId.values()) {
            Node parent = n.parentId == null ? null : byId.get(n.parentId);
            if (parent != null && parent != n) parent.kids.add(n);
            else roots.add(n);
        }
        Set<String> scope = scopeIds();
        if (!scope.isEmpty()) {
            roots = byId.values().stream().filter(n -> scope.contains(norm(n.page.id()))).toList();
        }

        Path root = sync.localFolder();
        Manifest manifest = store.load();
        Stats st = new Stats();
        Set<String> visited = new HashSet<>();
        Set<String> used = new HashSet<>();
        for (Node r : sortedByTitle(roots)) visit(r, Path.of(""), used, root, manifest, st, visited);

        if (!byId.isEmpty()) moveMissingToTrash(root, manifest, byId.keySet(), st);
        pushNewPages(root, manifest, st);
        store.save(manifest);
        Summary s = st.summary();
        if (s.pulled() + s.pushed() + s.created() + s.moved() + s.trashed() + s.conflicts() > 0) events.publish("sync");
        log.info("Sync done: pulled={} pushed={} created={} conflicts={} skipped={} moved={} trashed={} errors={}",
                s.pulled(), s.pushed(), s.created(), s.conflicts(), s.skipped(), s.moved(), s.trashed(), s.errors().size());
        return s;
    }

    private void visit(Node n, Path parentRel, Set<String> usedSlugs, Path root, Manifest m, Stats st, Set<String> visited) {
        if (!visited.add(n.page.id())) return;
        String id = n.page.id();
        String slug = uniqueSlug(slug(n.title), id, usedSlugs);
        String relStr = parentRel.resolve(slug).toString().replace('\\', '/');
        try {
            Entry old = m.pages().get(id);
            if (old != null && !old.path().equals(relStr) && Files.exists(root.resolve(old.path()))) {
                Path from = root.resolve(old.path());
                Path to = root.resolve(relStr);
                if (Files.exists(to)) {
                    relStr = relStr + "-" + shortId(id);
                    to = root.resolve(relStr);
                }
                Files.createDirectories(to.getParent());
                Files.move(from, to);
                rewriteDescendantPaths(m, old.path(), relStr);
                st.moved++;
            }
            Path index = root.resolve(relStr).resolve("index.md");
            boolean fileExists = Files.exists(index);
            boolean remoteChanged = old == null || old.lastEdited() == null
                    || !old.lastEdited().equals(n.page.lastEditedTime());
            PageFiles.Parsed local = (old != null && fileExists)
                    ? PageFiles.parse(Files.readString(index, StandardCharsets.UTF_8)) : null;
            boolean bodyChanged = local != null && !PageFiles.matches(local.body(), old.bodyHash());
            String localTitle = local == null ? null : local.meta().get("title");
            boolean titleChanged = localTitle != null && !localTitle.equals(oneLine(old.title()));
            boolean localChanged = bodyChanged || titleChanged;

            if (old == null || !fileExists) {
                pull(n, relStr, index, m, st);
            } else if (!localChanged && !remoteChanged) {
                m.pages().put(id, new Entry(id, n.title, n.parentId, relStr, old.lastEdited(), old.bodyHash(), old.partial()));
                st.skipped++;
                if (assets.anyMissing(index.getParent(), local.body())) backfillAssets(id, index, st);
            } else if (!localChanged) {
                pull(n, relStr, index, m, st);
            } else if (!remoteChanged) {
                push(n, relStr, index, old, local, bodyChanged, titleChanged, false, m, st);
            } else {
                conflict(n, relStr, index, old, local, bodyChanged, titleChanged, m, st);
            }
        } catch (IOException | RuntimeException e) {
            st.errors.add((n.title.isBlank() ? id : n.title) + ": " + e.getMessage());
            log.warn("Sync failed for page {}", id, e);
        }
        Set<String> kidSlugs = new HashSet<>();
        for (Node k : sortedByTitle(n.kids)) visit(k, Path.of(relStr), kidSlugs, root, m, st, visited);
    }

    private void pull(Node n, String relStr, Path index, Manifest m, Stats st) throws IOException {
        String id = n.page.id();
        PageMarkdown md = notion.getMarkdown(id, props.markdownVersion());
        String body = md.markdown() == null ? "" : md.markdown();
        boolean partial = md.truncated();
        PageFiles.writeAtomic(index, PageFiles.render(id, n.title, n.parentId, n.page.lastEditedTime(), partial, body));
        m.pages().put(id, new Entry(id, n.title, n.parentId, relStr, n.page.lastEditedTime(), PageFiles.sha256(body), partial));
        st.notes.addAll(assets.download(index.getParent(), body));
        st.pulled++;
    }

    /** Downloads images that are referenced but missing on disk, without touching index.md. */
    private void backfillAssets(String id, Path index, Stats st) {
        try {
            PageMarkdown md = notion.getMarkdown(id, props.markdownVersion());
            if (md.markdown() != null) st.notes.addAll(assets.download(index.getParent(), md.markdown()));
        } catch (RuntimeException e) {
            st.notes.add("Could not download images for " + id + ": " + e.getMessage());
        }
    }

    /**
     * Sends a local edit to Notion as targeted text replacements (update_content), so blocks the Markdown cannot
     * express faithfully (images, columns, child pages) are never rewritten. Refuses when Notion changed meanwhile.
     */
    private void push(Node n, String relStr, Path index, Entry old, PageFiles.Parsed local, boolean bodyChanged,
                      boolean titleChanged, boolean force, Manifest m, Stats st) throws IOException {
        String id = n.page.id();
        Entry keep = new Entry(id, n.title, n.parentId, relStr, old.lastEdited(), old.bodyHash(), old.partial());
        if (!sync.pushEnabled()) {
            m.pages().put(id, keep);
            st.notes.add("Local edit not pushed (sync.push-enabled is false): " + displayName(n));
            return;
        }
        if (old.partial()) {
            m.pages().put(id, keep);
            st.notes.add("Local edit not pushed because the page was only partly downloaded: " + displayName(n));
            return;
        }
        String writtenBody = local.body();
        List<LocalImages.Uploaded> uploads = List.of();
        if (bodyChanged) {
            ImageRefs.Extraction ex = images.extract(index.getParent(), local.body());
            if (!ex.problems().isEmpty()) {
                m.pages().put(id, keep);
                st.errors.add("Not pushed, fix the image references in " + displayName(n) + ": " + String.join("; ", ex.problems()));
                return;
            }
            String pushBody = ex.stripped();
            PageMarkdown remote = notion.getMarkdown(id, props.markdownVersion());
            if (remote.truncated()) {
                m.pages().put(id, keep);
                st.notes.add("Local edit not pushed because Notion returned only part of the page: " + displayName(n));
                return;
            }
            String remoteBody = remote.markdown() == null ? "" : remote.markdown();
            if (!force && !PageFiles.matches(remoteBody, old.bodyHash())) {
                m.pages().put(id, keep);
                st.notes.add("Notion changed while pushing, will be handled on the next sync: " + displayName(n));
                return;
            }
            Map<String, Object> req;
            if (remoteBody.isBlank()) {
                req = replaceBody(pushBody);
            } else {
                if (BodyDiff.removesSignedLine(remoteBody, pushBody)) {
                    m.pages().put(id, keep);
                    st.errors.add("Not pushed: " + displayName(n) + " no longer contains an image or file that exists in Notion"
                            + " (an <image source=...> line). Put the line back, or delete that image in Notion first.");
                    return;
                }
                List<Map<String, Object>> ops = BodyDiff.operations(remoteBody, pushBody);
                if (ops == null) {
                    m.pages().put(id, keep);
                    st.notes.add("Local edit too large or ambiguous to apply safely, not pushed: " + displayName(n));
                    return;
                }
                req = ops.isEmpty() ? null : updateBody(ops);
            }
            if (!ex.refs().isEmpty()) {
                try {
                    uploads = images.upload(ex.refs());
                } catch (IOException | RuntimeException e) {
                    m.pages().put(id, keep);
                    st.errors.add("Not pushed, image upload failed for " + displayName(n) + ": " + e.getMessage());
                    return;
                }
            }
            if (req == null) {
                writtenBody = remoteBody;
            } else {
                Map<String, Object> res = notion.updateMarkdown(id, req);
                if (res != null && res.get("warnings") instanceof Collection<?> w && !w.isEmpty()) {
                    m.pages().put(id, keep);
                    st.errors.add("Notion warnings for " + displayName(n) + ", edit may be partly applied: " + w);
                    return;
                }
                if (res != null && res.get("markdown") instanceof String s) {
                    writtenBody = s;
                } else {
                    String again = notion.getMarkdown(id, props.markdownVersion()).markdown();
                    writtenBody = again == null ? "" : again;
                }
            }
        }
        if (!uploads.isEmpty()) {
            try {
                st.notes.addAll(images.attach(id, uploads));
                String again = notion.getMarkdown(id, props.markdownVersion()).markdown();
                writtenBody = again == null ? "" : again;
            } catch (RuntimeException e) {
                m.pages().put(id, keep);
                st.errors.add("Text was pushed but attaching images failed for " + displayName(n) + ": " + e.getMessage()
                        + ". Your file was left as it is; the next sync will save a conflict copy.");
                return;
            }
        }
        String newTitle = titleChanged ? local.meta().get("title") : n.title;
        if (titleChanged) notion.updatePage(id, Map.of("properties", titleProperty(newTitle)));
        Page fresh = notion.getPage(id);
        PageFiles.writeAtomic(index, PageFiles.render(id, newTitle, n.parentId, fresh.lastEditedTime(), false, writtenBody));
        m.pages().put(id, new Entry(id, newTitle, n.parentId, relStr, fresh.lastEditedTime(),
                PageFiles.sha256(writtenBody), false));
        st.notes.addAll(assets.download(index.getParent(), writtenBody));
        st.pushed++;
    }

    /** Both sides changed: the newer edit wins, the older one is kept as a conflict file. */
    private void conflict(Node n, String relStr, Path index, Entry old, PageFiles.Parsed local, boolean bodyChanged,
                          boolean titleChanged, Manifest m, Stats st) throws IOException {
        String id = n.page.id();
        if (!sync.pushEnabled()) {
            m.pages().put(id, new Entry(id, n.title, n.parentId, relStr, old.lastEdited(), old.bodyHash(), old.partial()));
            st.notes.add("Both sides changed, left untouched because sync.push-enabled is false: " + displayName(n));
            return;
        }
        String stamp = LocalDateTime.now().format(STAMP);
        long localMs = Files.getLastModifiedTime(index).toMillis();
        long remoteMs = Instant.parse(n.page.lastEditedTime()).toEpochMilli();
        Path conflictFile = index.resolveSibling("index.conflict-" + stamp + ".md");
        if (remoteMs >= localMs) {
            Files.copy(index, conflictFile);
            pull(n, relStr, index, m, st);
            st.notes.add("Conflict on " + displayName(n) + ": Notion was newer. Your file was saved as " + conflictFile.getFileName());
        } else {
            PageMarkdown md = notion.getMarkdown(n.page.id(), props.markdownVersion());
            PageFiles.writeAtomic(conflictFile, PageFiles.render(id, n.title, n.parentId, n.page.lastEditedTime(),
                    md.truncated(), md.markdown() == null ? "" : md.markdown()));
            push(n, relStr, index, old, local, bodyChanged, titleChanged, true, m, st);
            st.notes.add("Conflict on " + displayName(n) + ": your file was newer. Notion's version was saved as " + conflictFile.getFileName());
        }
        st.conflicts++;
    }

    /** New local folders (index.md without a notion_id) become new Notion pages under their parent folder's page. */
    private void pushNewPages(Path root, Manifest m, Stats st) {
        if (!sync.pushEnabled()) return;
        Map<String, Entry> byPath = new HashMap<>();
        for (Entry e : m.pages().values()) byPath.put(e.path(), e);
        List<Path> indexes;
        try (var s = Files.walk(root)) {
            indexes = s.filter(p -> p.getFileName().toString().equals("index.md"))
                    .filter(p -> !root.relativize(p).startsWith(".notion-sync"))
                    .sorted(Comparator.comparingInt(Path::getNameCount)).toList();
        } catch (IOException e) {
            st.errors.add("Scanning for new pages failed: " + e.getMessage());
            return;
        }
        for (Path idx : indexes) {
            String dirRel = root.relativize(idx.getParent()).toString().replace('\\', '/');
            if (dirRel.isEmpty() || byPath.containsKey(dirRel)) continue;
            try {
                PageFiles.Parsed parsed = PageFiles.parse(Files.readString(idx, StandardCharsets.UTF_8));
                if (parsed.meta().get("notion_id") != null) {
                    st.errors.add("Skipped " + dirRel + ": it has a notion_id that is not in the manifest");
                    continue;
                }
                int slash = dirRel.lastIndexOf('/');
                String parentRel = slash < 0 ? "" : dirRel.substring(0, slash);
                String folder = slash < 0 ? dirRel : dirRel.substring(slash + 1);
                String parentId = null;
                if (!parentRel.isEmpty()) {
                    Entry pe = byPath.get(parentRel);
                    if (pe == null) {
                        st.errors.add("Skipped " + dirRel + ": its parent folder is not a mirrored page");
                        continue;
                    }
                    parentId = pe.id();
                }
                String t = parsed.meta().get("title");
                String title = (t == null || t.isBlank()) ? folder : t;
                ImageRefs.Extraction ex = images.extract(idx.getParent(), parsed.body());
                if (!ex.problems().isEmpty()) {
                    st.errors.add("Skipped " + dirRel + ", fix the image references: " + String.join("; ", ex.problems()));
                    continue;
                }
                List<LocalImages.Uploaded> ups = ex.refs().isEmpty() ? List.of() : images.upload(ex.refs());
                Page created = notion.createPage(createBody(parentId, title, ex.stripped()));
                if (!ups.isEmpty()) {
                    try {
                        st.notes.addAll(images.attach(created.id(), ups));
                    } catch (RuntimeException e) {
                        st.errors.add("Page " + dirRel + " was created but attaching images failed: " + e.getMessage());
                    }
                }
                Page fresh = notion.getPage(created.id());
                String body = normalizedBody(fresh.id(), ex.stripped());
                PageFiles.writeAtomic(idx, PageFiles.render(fresh.id(), title, parentId, fresh.lastEditedTime(), false, body));
                Entry e = new Entry(fresh.id(), title, parentId, dirRel, fresh.lastEditedTime(), PageFiles.sha256(body), false);
                st.notes.addAll(assets.download(idx.getParent(), body));
                m.pages().put(fresh.id(), e);
                byPath.put(dirRel, e);
                st.created++;
            } catch (IOException | RuntimeException ex) {
                st.errors.add("Creating " + dirRel + ": " + ex.getMessage());
                log.warn("Creating page from {} failed", dirRel, ex);
            }
        }
    }

    // ------------------------------------------------------------------ writes from the UI

    /** Creates a page in Notion first, then writes the local folder. parentId null means a top-level page. */
    public PageDoc createPage(String parentId, String title, String body) {
        lock.lock();
        try {
            Manifest m = store.load();
            Path parentRel = Path.of("");
            if (parentId != null && !parentId.isBlank()) {
                Entry p = m.pages().get(parentId);
                if (p == null) throw new IllegalArgumentException("Unknown parent page");
                parentRel = Path.of(p.path());
            } else {
                parentId = null;
            }
            String t = (title == null || title.isBlank()) ? "Untitled" : title.strip();
            String text = body == null ? "" : body;
            Page created = notion.createPage(createBody(parentId, t, text));
            Page fresh = notion.getPage(created.id());
            text = normalizedBody(fresh.id(), text);
            String slug = slug(t);
            if (Files.exists(sync.localFolder().resolve(parentRel).resolve(slug))) slug = slug + "-" + shortId(fresh.id());
            String relStr = parentRel.resolve(slug).toString().replace('\\', '/');
            PageFiles.writeAtomic(sync.localFolder().resolve(relStr).resolve("index.md"),
                    PageFiles.render(fresh.id(), t, parentId, fresh.lastEditedTime(), false, text));
            m.pages().put(fresh.id(), new Entry(fresh.id(), t, parentId, relStr, fresh.lastEditedTime(), PageFiles.sha256(text), false));
            assets.download(sync.localFolder().resolve(relStr), text);
            store.save(m);
            events.publish("sync");
            return read(fresh.id()).orElseThrow();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } finally {
            lock.unlock();
        }
    }

    /** Saves an edit from the UI into the local file, then runs a sync so it reaches Notion. */
    public Optional<PageDoc> saveLocal(String id, String title, String markdown) {
        lock.lock();
        try {
            Entry e = store.load().pages().get(id);
            if (e == null) return Optional.empty();
            if (e.partial()) throw new IllegalStateException("This page was only partly downloaded and cannot be edited");
            String newTitle = title == null ? e.title() : title;
            Path index = sync.localFolder().resolve(e.path()).resolve("index.md");
            PageFiles.writeAtomic(index, PageFiles.render(id, newTitle, e.parentId(), e.lastEdited(), false, markdown));
            sync();
            return read(id);
        } catch (IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------ reading

    /** Tree of everything in the manifest, for the UI. */
    public List<TreeNode> tree() {
        Manifest m = store.load();
        Map<String, List<Entry>> kids = new HashMap<>();
        List<Entry> roots = new ArrayList<>();
        for (Entry e : m.pages().values()) {
            if (e.parentId() != null && m.pages().containsKey(e.parentId())) {
                kids.computeIfAbsent(e.parentId(), k -> new ArrayList<>()).add(e);
            } else {
                roots.add(e);
            }
        }
        return build(roots, kids);
    }

    private static List<TreeNode> build(List<Entry> entries, Map<String, List<Entry>> kids) {
        return entries.stream()
                .sorted(Comparator.comparing(e -> e.title() == null ? "" : e.title().toLowerCase()))
                .map(e -> new TreeNode(e.id(), e.title(), e.partial(), build(kids.getOrDefault(e.id(), List.of()), kids)))
                .toList();
    }

    public Optional<PageDoc> read(String id) {
        Entry e = store.load().pages().get(id);
        if (e == null) return Optional.empty();
        try {
            Path index = sync.localFolder().resolve(e.path()).resolve("index.md");
            String body = PageFiles.parse(Files.readString(index, StandardCharsets.UTF_8)).body();
            return Optional.of(new PageDoc(e.id(), e.title(), e.parentId(), e.path(), e.lastEdited(), e.partial(), body,
                    assets.names(sync.localFolder().resolve(e.path()))));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    /** Saves an image pasted or dropped in the UI into the page's assets folder; returns the file name. */
    public String saveImage(String id, String filename, byte[] data) throws IOException {
        Entry e = store.load().pages().get(id);
        if (e == null) throw new IllegalArgumentException("Unknown page");
        if (ImageRefs.contentType(filename == null ? "" : filename) == null) {
            throw new IllegalArgumentException("Unsupported image type");
        }
        if (data.length == 0 || data.length > ImageRefs.MAX_BYTES) {
            throw new IllegalArgumentException("Image must be between 1 byte and 20 MB");
        }
        String base = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.length() > 80) base = base.substring(base.length() - 80);
        if (base.startsWith(".")) base = "_" + base;
        Path dir = sync.localFolder().resolve(e.path()).resolve("assets");
        Files.createDirectories(dir);
        int dot = base.lastIndexOf('.');
        String stem = dot < 0 ? base : base.substring(0, dot);
        String ext = dot < 0 ? "" : base.substring(dot);
        Path f = dir.resolve(base);
        for (int i = 2; Files.exists(f); i++) f = dir.resolve(stem + "-" + i + ext);
        Files.write(f, data);
        return f.getFileName().toString();
    }

    /** Absolute path of a downloaded asset of a page, if the page is known and the file exists. */
    public Optional<Path> assetFile(String id, String name) {
        if (name == null || !name.matches("^[A-Za-z0-9._-]{1,120}$") || name.startsWith(".")) return Optional.empty();
        Entry e = store.load().pages().get(id);
        if (e == null) return Optional.empty();
        Path dir = sync.localFolder().resolve(e.path()).resolve("assets").normalize();
        Path f = dir.resolve(name).normalize();
        if (!f.startsWith(dir) || !Files.isRegularFile(f)) return Optional.empty();
        return Optional.of(f);
    }

    // ------------------------------------------------------------------ helpers

    /** Notion's own rendering of a just-created page, so the local file matches what later syncs will see. */
    private String normalizedBody(String id, String fallback) {
        try {
            PageMarkdown md = notion.getMarkdown(id, props.markdownVersion());
            if (md.markdown() != null && !md.truncated()) return md.markdown();
        } catch (RuntimeException e) {
            log.warn("Could not read back new page {}", id, e);
        }
        return fallback;
    }

    private Map<String, Object> updateBody(List<Map<String, Object>> ops) {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("content_updates", ops);
        inner.put("allow_deleting_content", false);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "update_content");
        body.put("update_content", inner);
        body.put("markdown_version", props.markdownVersion());
        return body;
    }

    private Map<String, Object> replaceBody(String markdown) {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("new_str", markdown);
        inner.put("allow_deleting_content", false);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "replace_content");
        body.put("replace_content", inner);
        body.put("markdown_version", props.markdownVersion());
        return body;
    }

    private Map<String, Object> createBody(String parentId, String title, String markdown) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (parentId != null) body.put("parent", Map.of("type", "page_id", "page_id", parentId));
        body.put("properties", titleProperty(title));
        if (markdown != null && !markdown.isBlank()) {
            body.put("markdown", markdown);
            body.put("markdown_version", props.markdownVersion());
        }
        return body;
    }

    private static Map<String, Object> titleProperty(String title) {
        return Map.of("title", Map.of("title", List.of(Map.of("text", Map.of("content", title)))));
    }

    private void moveMissingToTrash(Path root, Manifest m, Set<String> seen, Stats st) {
        List<Entry> gone = m.pages().values().stream()
                .filter(e -> !seen.contains(e.id()))
                .sorted(Comparator.comparingInt(e -> e.path().length()))
                .toList();
        if (gone.isEmpty()) return;
        String stamp = LocalDateTime.now().format(STAMP);
        for (Entry e : gone) {
            try {
                // Search can lag behind, so ask Notion directly before treating a page as gone.
                try {
                    Page p = notion.getPage(e.id());
                    if (!p.inTrash()) continue;
                } catch (RestClientResponseException ex) {
                    if (ex.getStatusCode().value() != 404) {
                        st.errors.add("Could not check " + e.title() + ": " + ex.getStatusCode());
                        continue;
                    }
                }
                Path from = root.resolve(e.path());
                if (Files.exists(from)) {
                    Path to = root.resolve(".notion-sync").resolve("trash").resolve(stamp).resolve(e.path());
                    Files.createDirectories(to.getParent());
                    Files.move(from, to);
                }
                m.pages().remove(e.id());
                st.trashed++;
            } catch (IOException ex) {
                st.errors.add("trash " + e.title() + ": " + ex.getMessage());
            }
        }
    }

    private static void rewriteDescendantPaths(Manifest m, String oldPath, String newPath) {
        String prefix = oldPath + "/";
        for (Entry e : new ArrayList<>(m.pages().values())) {
            if (e.path().startsWith(prefix)) {
                m.pages().put(e.id(), new Entry(e.id(), e.title(), e.parentId(),
                        newPath + e.path().substring(oldPath.length()), e.lastEdited(), e.bodyHash(), e.partial()));
            }
        }
    }

    private List<Page> fetchAll() {
        List<Page> all = new ArrayList<>();
        String cursor = null;
        do {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("filter", Map.of("property", "object", "value", "page"));
            body.put("page_size", 100);
            if (cursor != null) body.put("start_cursor", cursor);
            SearchResponse r = notion.search(body);
            for (Page p : r.results()) if (!p.inTrash()) all.add(p);
            cursor = r.hasMore() ? r.nextCursor() : null;
        } while (cursor != null);
        return all;
    }

    private Set<String> scopeIds() {
        List<String> s = props.mirrorScope();
        if (s == null || s.isEmpty() || (s.size() == 1 && s.getFirst().equalsIgnoreCase("all"))) return Set.of();
        Set<String> out = new HashSet<>();
        for (String id : s) out.add(norm(id));
        return out;
    }

    private static String displayName(Node n) {
        return n.title.isBlank() ? n.page.id() : n.title;
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replace("\n", " ");
    }

    private static List<Node> sortedByTitle(List<Node> nodes) {
        return nodes.stream().sorted(Comparator.comparing((Node n) -> n.title.toLowerCase())
                .thenComparing(n -> n.page.id())).toList();
    }

    private static String uniqueSlug(String base, String id, Set<String> used) {
        String s = base;
        if (!used.add(s.toLowerCase())) {
            s = base + "-" + shortId(id);
            used.add(s.toLowerCase());
        }
        return s;
    }

    private static String shortId(String id) {
        return norm(id).substring(0, 6);
    }

    private static String norm(String id) {
        return id.replace("-", "").toLowerCase();
    }

    static String slug(String title) {
        String s = title == null ? "" : title.strip();
        s = s.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "-").replaceAll("\\s+", " ");
        while (s.endsWith(".") || s.endsWith(" ")) s = s.substring(0, s.length() - 1);
        if (s.length() > 80) s = s.substring(0, 80).strip();
        if (s.isEmpty()) s = "Untitled";
        if (RESERVED.matcher(s).matches()) s = "_" + s;
        return s;
    }

    private static String titleOf(Page p) {
        if (p.properties() == null) return "";
        for (Object v : p.properties().values()) {
            if (v instanceof Map<?, ?> m && "title".equals(m.get("type")) && m.get("title") instanceof List<?> parts) {
                StringBuilder sb = new StringBuilder();
                for (Object part : parts) {
                    if (part instanceof Map<?, ?> r && r.get("plain_text") instanceof String t) sb.append(t);
                }
                return sb.toString();
            }
        }
        return "";
    }

    private static String parentOf(Page p) {
        Map<String, Object> parent = p.parent();
        if (parent != null && "page_id".equals(parent.get("type")) && parent.get("page_id") instanceof String id) return id;
        return null;
    }
}
