package com.ssok.server.domain.ai.service;

import com.ssok.server.common.exception.BookmarkNotFoundException;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.ai.client.dto.AiSearchResponse;
import com.ssok.server.domain.ai.dto.SimilarBookmarkResponse;
import com.ssok.server.domain.ai.dto.SummaryResponse;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.event.BookmarkAnalysisRequestedEvent;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.space.service.SpaceAccessService;
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
    private final SpaceAccessService spaceAccessService;

    @Transactional
    public SummaryResponse summarize(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));

        spaceAccessService.requireWrite(userId, bookmark.getSpace().getId());
        bookmark.requestAnalysis();
        eventPublisher.publishEvent(new BookmarkAnalysisRequestedEvent(bookmark.getId(), bookmark.getUrl()));

        return new SummaryResponse(bookmark.getId(), bookmark.getAnalysisStatus());
    }

    @Transactional(readOnly = true)
    public List<SimilarBookmarkResponse> search(Long userId, String query, Long spaceId) {
        List<Long> scope = spaceAccessService.resolveSearchSpaceIds(userId, spaceId);
        if (scope.isEmpty()) {
            return List.of();
        }
        return toResponses(aiServerClient.search(query, TOP_K, scope), scope);
    }

    /**
     * AI 서버에 추천 전용 API가 없으므로, 대상 북마크의 제목과 요약을 검색어로 사용해 유사한 북마크를 찾는다.
     */
    @Transactional(readOnly = true)
    public List<SimilarBookmarkResponse> recommend(Long userId, Long bookmarkId) {
        Bookmark target = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));

        spaceAccessService.requireSearch(userId, target.getSpace().getId());
        if (target.getSummary() == null) {
            return List.of();
        }

        List<Long> scope = spaceAccessService.getSearchableSpaceIds(userId);
        List<AiSearchResponse.Result> results = aiServerClient.search(
                target.getTitle() + " " + target.getSummary(), TOP_K + 1, scope).stream()
                .filter(result -> !target.getId().equals(result.bookmarkId()))
                .limit(TOP_K)
                .toList();

        return toResponses(results, scope);
    }

    // AI 서버가 돌려준 순서(유사도순)를 유지하고, 메인 DB에 없는 북마크는 제외한다.
    private List<SimilarBookmarkResponse> toResponses(List<AiSearchResponse.Result> results, List<Long> scope) {
        if (results.isEmpty()) {
            return List.of();
        }
        List<Long> ids = results.stream().map(AiSearchResponse.Result::bookmarkId).toList();
        Map<Long, Bookmark> bookmarks = bookmarkRepository.findAllByIdInAndSpaceIdIn(ids, scope).stream()
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
