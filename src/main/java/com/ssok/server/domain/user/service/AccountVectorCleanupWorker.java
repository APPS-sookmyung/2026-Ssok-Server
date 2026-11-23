package com.ssok.server.domain.user.service;

import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.user.repository.AccountVectorDeletionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountVectorCleanupWorker {
    private final AccountVectorDeletionRepository jobs;
    private final AiServerClient ai;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long id) {
        var job = jobs.findLockedById(id).orElse(null);
        if (job == null || job.getNextAttemptAt().isAfter(Instant.now())) return;
        try { ai.deleteSpaceVectors(job.getSpaceId()); jobs.delete(job); }
        catch (RuntimeException e) { job.failed(); log.warn("Account vector cleanup will retry: jobId={}", id); }
    }
}
