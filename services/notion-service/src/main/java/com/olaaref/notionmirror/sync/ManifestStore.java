package com.olaaref.notionmirror.sync;

import com.olaaref.notionmirror.SyncProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ManifestStore {
    private final Path file;
    private final JsonMapper mapper;

    public ManifestStore(SyncProperties props, JsonMapper mapper) {
        this.file = props.localFolder().resolve(".notion-sync").resolve("state.json");
        this.mapper = mapper;
    }

    public Manifest load() {
        if (!Files.exists(file)) return new Manifest(null);
        return mapper.readValue(file.toFile(), Manifest.class);
    }

    public void save(Manifest manifest) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("state.json.tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), manifest);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
