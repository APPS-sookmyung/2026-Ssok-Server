package com.ssok.server.domain.bookmark.service;

import com.ssok.server.common.config.AiServerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI 서버가 콜백을 보내지 못한 경우 북마크가 PENDING에 영원히 머무르지 않도록 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisTimeoutScheduler {

    private final BookmarkAnalysisService bookmarkAnalysisService;
    private final AiServerProperties aiServerProperties;

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void failTimedOutAnalyses() {
        int count = bookmarkAnalysisService.failTimedOutAnalyses(aiServerProperties.analysisTimeout());
        if (count > 0) {
            log.warn("Marked {} timed-out AI analyses as FAILED", count);
        }
    }
}
