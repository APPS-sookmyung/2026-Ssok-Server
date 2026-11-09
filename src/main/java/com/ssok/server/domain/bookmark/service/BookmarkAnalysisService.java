package com.ssok.server.domain.bookmark.service;

import com.ssok.server.common.exception.BookmarkNotFoundException;
import com.ssok.server.domain.bookmark.dto.AiAnalysisResultRequest;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 서버의 분석 결과를 북마크에 반영한다.
 */
@Service
@RequiredArgsConstructor
public class BookmarkAnalysisService {

    private final BookmarkRepository bookmarkRepository;

    @Transactional
    public void applyResult(AiAnalysisResultRequest request) {
        Bookmark bookmark = bookmarkRepository.findById(request.bookmarkId())
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));

        if (request.isSuccess()) {
            AiAnalysisResultRequest.Analysis analysis = request.analysis();
            bookmark.completeAnalysis(analysis.summary(), analysis.category(), analysis.tags());
        } else {
            bookmark.failAnalysis();
        }
    }

    @Transactional
    public void markFailed(Long bookmarkId) {
        bookmarkRepository.findById(bookmarkId).ifPresent(Bookmark::failAnalysis);
    }
}
