package com.ssok.server.domain.file;

import static org.assertj.core.api.Assertions.*;
import com.ssok.server.common.exception.FileStorageException;
import com.ssok.server.domain.file.storage.LocalObjectStorage;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalObjectStorageTest {
    @TempDir Path root;

    @Test
    void rejectsTraversalAndNeverOverwritesExistingObject() {
        LocalObjectStorage storage = new LocalObjectStorage(root.toString());
        assertThatThrownBy(() -> storage.put("../escape", new byte[]{1})).isInstanceOf(FileStorageException.class);
        String key = UUID.randomUUID().toString();
        storage.put(key, new byte[]{1, 2});
        assertThatThrownBy(() -> storage.put(key, new byte[]{3})).isInstanceOf(FileStorageException.class);
        assertThat(storage.read(key, 2)).containsExactly(1, 2);
        assertThatThrownBy(() -> storage.read(key, 1)).isInstanceOf(FileStorageException.class);
        storage.delete(key);
        storage.delete(key);
        assertThat(root.resolve(key)).doesNotExist();
    }
}
