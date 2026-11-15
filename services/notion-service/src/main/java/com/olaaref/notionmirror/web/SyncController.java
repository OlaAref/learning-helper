package com.olaaref.notionmirror.web;

import com.olaaref.notionmirror.sync.SyncService;
import com.olaaref.notionmirror.sync.SyncService.PageDoc;
import com.olaaref.notionmirror.sync.SyncService.Summary;
import com.olaaref.notionmirror.sync.SyncService.TreeNode;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SyncController {
    private final SyncService service;

    public SyncController(SyncService service) {
        this.service = service;
    }

    public record CreateRequest(String title, String parentId, String markdown) {}

    public record SaveRequest(String title, String markdown) {}

    /** Runs one full sync cycle now. */
    @PostMapping("/sync")
    public Summary sync() {
        return service.sync();
    }

    @GetMapping("/pages")
    public List<TreeNode> pages() {
        return service.tree();
    }

    @GetMapping("/pages/{id}")
    public ResponseEntity<PageDoc> page(@PathVariable String id) {
        return service.read(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/pages")
    public ResponseEntity<?> create(@RequestBody CreateRequest req) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.createPage(req.parentId(), req.title(), req.markdown()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/pages/{id}")
    public ResponseEntity<?> save(@PathVariable String id, @RequestBody SaveRequest req) {
        try {
            return service.saveLocal(id, req.title(), req.markdown() == null ? "" : req.markdown())
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    /** Stores an image from the editor next to the page; the editor then inserts the returned Markdown. */
    @PostMapping("/pages/{id}/images")
    public ResponseEntity<?> addImage(@PathVariable String id, @RequestParam("file") MultipartFile file) throws IOException {
        try {
            String name = service.saveImage(id, file.getOriginalFilename(), file.getBytes());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(java.util.Map.of("name", name, "markdown", "![](assets/" + name + ")"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    /** Serves an image or file that was downloaded next to a page's index.md. */
    @GetMapping("/assets/{id}/{name}")
    public ResponseEntity<?> asset(@PathVariable String id, @PathVariable String name) throws IOException {
        java.util.Optional<Path> f = service.assetFile(id, name);
        if (f.isEmpty()) return ResponseEntity.notFound().build();
        String lower = name.toLowerCase();
        String type = lower.endsWith(".png") ? "image/png"
                : lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "image/jpeg"
                : lower.endsWith(".gif") ? "image/gif"
                : lower.endsWith(".webp") ? "image/webp"
                : lower.endsWith(".pdf") ? "application/pdf"
                : "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePrivate().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .body(new FileSystemResource(f.get()));
    }
}
