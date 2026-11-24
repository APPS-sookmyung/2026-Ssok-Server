package com.ssok.server.domain.user.service;

import com.ssok.server.domain.user.repository.AccountVectorDeletionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class AccountVectorCleanup {
    public record Requested(Long jobId) {}
    private final AccountVectorDeletionRepository jobs;
    private final AccountVectorCleanupWorker worker;
    @org.springframework.beans.factory.annotation.Value("${account.vector-cleanup.enabled:true}")
    private boolean periodicCleanup;
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(Requested event) { run(event.jobId()); }
    @Scheduled(fixedDelayString = "${account.vector-cleanup.interval-ms:60000}", initialDelayString = "${account.vector-cleanup.interval-ms:60000}")
    public void retry() {
        if (!periodicCleanup) return;
        jobs.findDueIds(Instant.now(), PageRequest.of(0, 50)).forEach(this::run);
    }
    private void run(Long id) {
        try { worker.process(id); }
        catch (RuntimeException e) { log.warn("Account vector cleanup deferred: jobId={}", id); }
    }
}
