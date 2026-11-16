package com.ssok.server.domain.ai.service;

import com.ssok.server.common.exception.BookmarkNotFoundException;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.ai.client.dto.AiSearchResponse;
import com.ssok.server.domain.ai.dto.SimilarBookmarkResponse;
import com.ssok.server.domain.ai.dto.SummaryResponse;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.event.BookmarkAnalysisRequestedEvent;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiService {

    private static final int TOP_K = 5;

    private final BookmarkRepository bookmarkRepository;
    private final AiServerClient aiServerClient;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SummaryResponse summarize(Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));

        bookmark.requestAnalysis();
        eventPublisher.publishEvent(new BookmarkAnalysisRequestedEvent(bookmark.getId(), bookmark.getUrl()));

        return new SummaryResponse(bookmark.getId(), bookmark.getAnalysisStatus());
    }

    @Transactional(readOnly = true)
    public List<SimilarBookmarkResponse> search(String query) {
        return toResponses(aiServerClient.search(query, TOP_K));
    }

    /**
     * AI 서버에 추천 전용 API가 없으므로, 대상 북마크의 제목과 요약을 검색어로 사용해 유사한 북마크를 찾는다.
     */
    @Transactional(readOnly = true)
    public List<SimilarBookmarkResponse> recommend(Long bookmarkId) {
        Bookmark target = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));

        if (target.getSummary() == null) {
            return List.of();
        }

        List<AiSearchResponse.Result> results = aiServerClient.search(
                target.getTitle() + " " + target.getSummary(), TOP_K + 1).stream()
                .filter(result -> !target.getId().equals(result.bookmarkId()))
                .limit(TOP_K)
                .toList();

        return toResponses(results);
    }

    // AI 서버가 돌려준 순서(유사도순)를 유지하고, 메인 DB에 없는 북마크는 제외한다.
    private List<SimilarBookmarkResponse> toResponses(List<AiSearchResponse.Result> results) {
        List<Long> ids = results.stream().map(AiSearchResponse.Result::bookmarkId).toList();
        Map<Long, Bookmark> bookmarks = bookmarkRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Bookmark::getId, Function.identity()));

        return results.stream()
                .filter(result -> bookmarks.containsKey(result.bookmarkId()))
                .map(result -> {
                    Bookmark bookmark = bookmarks.get(result.bookmarkId());
                    return new SimilarBookmarkResponse(bookmark.getId(), bookmark.getTitle(), bookmark.getUrl(),
                            bookmark.getSummary(), result.similarity());
                })
                .toList();
    }
}
