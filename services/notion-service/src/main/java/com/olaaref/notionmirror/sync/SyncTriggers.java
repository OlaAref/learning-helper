package com.olaaref.notionmirror.sync;

import com.olaaref.notionmirror.NotionProperties;
import com.olaaref.notionmirror.SyncProperties;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Starts a sync on a timer and whenever files under the mirror folder change (1 second debounce). */
@Component
public class SyncTriggers {
    private static final Logger log = LoggerFactory.getLogger(SyncTriggers.class);

    private final SyncService service;
    private final SyncProperties props;
    private final NotionProperties notion;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sync-trigger");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pending;

    public SyncTriggers(SyncService service, SyncProperties props, NotionProperties notion) {
        this.service = service;
        this.props = props;
        this.notion = notion;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!"auto".equalsIgnoreCase(props.mode())) {
            log.info("Sync mode is '{}': no automatic sync", props.mode());
            return;
        }
        if (notion.token() == null || notion.token().isBlank()) {
            log.warn("NOTION_TOKEN is not set: automatic sync not started");
            return;
        }
        long seconds = Math.max(10, props.interval().toSeconds());
        exec.scheduleWithFixedDelay(this::tick, 5, seconds, TimeUnit.SECONDS);
        Thread watcher = new Thread(this::watch, "sync-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void tick() {
        try {
            var s = service.sync();
            if (!s.notes().isEmpty()) s.notes().forEach(n -> log.info("sync note: {}", n));
            if (!s.errors().isEmpty()) s.errors().forEach(e -> log.warn("sync error: {}", e));
        } catch (RuntimeException e) {
            log.warn("Sync failed", e);
        }
    }

    private synchronized void requestSync() {
        if (pending != null) pending.cancel(false);
        pending = exec.schedule(this::tick, 1, TimeUnit.SECONDS);
    }

    private void watch() {
        Path root = props.localFolder();
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            Files.createDirectories(root);
            Map<WatchKey, Path> keys = new HashMap<>();
            registerAll(ws, root, keys);
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = ws.take();
                Path dir = keys.get(key);
                boolean relevant = false;
                for (WatchEvent<?> ev : key.pollEvents()) {
                    if (ev.kind() == StandardWatchEventKinds.OVERFLOW) {
                        relevant = true;
                        continue;
                    }
                    Path child = dir.resolve((Path) ev.context());
                    String name = child.getFileName().toString();
                    if (child.startsWith(root.resolve(".notion-sync")) || name.endsWith(".tmp")) continue;
                    if (ev.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child)) {
                        registerAll(ws, child, keys);
                        relevant = true;
                    } else if (name.equals("index.md") || ev.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                        relevant = true;
                    }
                }
                if (relevant) requestSync();
                if (!key.reset()) keys.remove(key);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            log.warn("File watcher stopped", e);
        }
    }

    private static void registerAll(WatchService ws, Path start, Map<WatchKey, Path> keys) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.getFileName() != null && dir.getFileName().toString().equals(".notion-sync")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                keys.put(dir.register(ws, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE), dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
