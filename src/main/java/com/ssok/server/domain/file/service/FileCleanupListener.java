package com.ssok.server.domain.file.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileCleanupListener {
    private final FileCleanupWorker worker;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(FileCleanupRequested event) {
        try { worker.process(event.jobId()); }
        catch (RuntimeException e) { log.warn("File cleanup deferred: jobId={}", event.jobId()); }
    }
}
