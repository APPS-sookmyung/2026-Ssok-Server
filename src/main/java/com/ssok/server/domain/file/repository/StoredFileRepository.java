package com.ssok.server.domain.file.repository;

import com.ssok.server.domain.file.entity.StoredFile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    Optional<StoredFile> findByBookmarkId(Long bookmarkId);
    List<StoredFile> findAllBySpaceId(Long spaceId);
}
