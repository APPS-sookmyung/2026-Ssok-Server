package com.ssok.server.domain.file.service;

import com.ssok.server.domain.file.entity.FileDeletionJob;
import com.ssok.server.domain.file.entity.StoredFile;
import com.ssok.server.domain.file.repository.*;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FileCleanupService {
    private final StoredFileRepository files;
    private final FileDeletionJobRepository jobs;
    private final ApplicationEventPublisher events;

    @Transactional
    public void forBookmark(Long bookmarkId) {
        files.findByBookmarkId(bookmarkId).ifPresent(this::enqueue);
    }

    @Transactional
    public void forSpace(Long spaceId) {
        files.findAllBySpaceId(spaceId).forEach(this::enqueue);
    }

    @Transactional
    public void enqueue(StoredFile file) {
        FileDeletionJob job = jobs.saveAndFlush(new FileDeletionJob(file.getStorageId(), file.getObjectKey(), Instant.EPOCH));
        events.publishEvent(new FileCleanupRequested(job.getId()));
    }
}
