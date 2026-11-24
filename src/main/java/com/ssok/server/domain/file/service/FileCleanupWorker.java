package com.ssok.server.domain.file.service;

import com.ssok.server.domain.file.repository.FileDeletionJobRepository;
import com.ssok.server.domain.file.storage.ObjectStorage;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileCleanupWorker {
    private final FileDeletionJobRepository jobs;
    private final ObjectStorage storage;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long id) {
        var job = jobs.findLockedById(id).orElse(null);
        if (job == null || !job.getStorageId().equals(storage.storageId()) || job.getNextAttemptAt().isAfter(Instant.now())) return;
        try {
            storage.delete(job.getObjectKey());
            jobs.delete(job);
        } catch (RuntimeException e) {
            job.failed();
            log.warn("File cleanup will retry: jobId={}", id);
        }
    }
}
