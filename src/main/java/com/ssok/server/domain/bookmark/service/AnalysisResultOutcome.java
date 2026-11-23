package com.ssok.server.domain.bookmark.service;

/**
 * AI 콜백 처리 결과. 무시한 경우에도 AI 서버가 재시도하지 않도록 200으로 응답한다.
 */
public enum AnalysisResultOutcome {
    APPLIED,
    /** 이미 삭제된 북마크 */
    IGNORED_DELETED,
    /** 재분석 등으로 더 최신 요청이 있는 경우 */
    IGNORED_STALE,
    /** 이미 처리한 결과 (중복 콜백) */
    IGNORED_DUPLICATE
}
