package com.ssok.server.domain.file.storage;

import com.ssok.server.common.exception.FileStorageException;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.*;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalObjectStorage implements ObjectStorage {
    private final Path root;

    public LocalObjectStorage(@Value("${file.storage.local-root:./.data/files}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override public String storageId() { return "local:" + root; }

    @Override
    public void put(String key, byte[] content) {
        try {
            Files.createDirectories(root);
            Files.write(resolve(key), content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) { throw new FileStorageException(); }
    }

    @Override
    public byte[] read(String key, int maxBytes) {
        try (var channel = Files.newByteChannel(resolve(key), Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
                var input = Channels.newInputStream(channel)) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) throw new FileStorageException();
            return bytes;
        } catch (IOException e) { throw new FileStorageException(); }
    }

    @Override
    public void delete(String key) {
        try { Files.deleteIfExists(resolve(key)); }
        catch (IOException e) { throw new FileStorageException(); }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new FileStorageException();
        }
        return root.resolve(key);
    }
}
