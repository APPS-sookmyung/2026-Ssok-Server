package com.ssok.server.domain.file.service;

import com.ssok.server.domain.file.repository.FileDeletionJobRepository;
import com.ssok.server.domain.file.storage.ObjectStorage;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "file.cleanup.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class FileCleanupScheduler {
    private final FileDeletionJobRepository jobs;
    private final ObjectStorage storage;
    private final FileCleanupWorker worker;

    @Scheduled(fixedDelayString = "${file.cleanup.interval-ms:60000}", initialDelayString = "${file.cleanup.interval-ms:60000}")
    public void retry() {
        for (Long id : jobs.findDueIds(storage.storageId(), Instant.now(), PageRequest.of(0, 50))) {
            try { worker.process(id); }
            catch (RuntimeException e) { log.warn("File cleanup deferred: jobId={}", id); }
        }
    }
}
