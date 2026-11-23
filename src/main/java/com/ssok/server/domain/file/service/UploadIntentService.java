package com.ssok.server.domain.file.service;

import com.ssok.server.domain.file.entity.FileDeletionJob;
import com.ssok.server.domain.file.repository.FileDeletionJobRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UploadIntentService {
    private final FileDeletionJobRepository jobs;

    /** 업로드보다 먼저 기록. 외부 트랜잭션이 롤백·중단돼도 정리할 수 있다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long prepare(String storageId, String objectKey) {
        return jobs.saveAndFlush(new FileDeletionJob(storageId, objectKey, Instant.now().plusSeconds(3600))).getId();
    }
}
