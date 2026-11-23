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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final SpaceAccessService spaceAccessService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SummaryResponse summarize(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));
        spaceAccessService.validateMember(bookmark.getSpace().getId(), userId);

        bookmark.requestAnalysis();
        eventPublisher.publishEvent(BookmarkAnalysisRequestedEvent.from(bookmark));

        return new SummaryResponse(bookmark.getId(), bookmark.getAnalysisStatus());
    }

    /**
     * spaceId를 지정하면 그 스페이스에서만, 생략하면 사용자가 속한 모든 스페이스에서 검색한다.
     * 권한 없는 스페이스를 지정하면 403, 접근 가능한 스페이스가 없으면 AI 호출 없이 0건을 반환한다.
     */
    @Transactional(readOnly = true)
    public List<SimilarBookmarkResponse> search(Long userId, String query, Long spaceId) {
        List<Long> scope = spaceAccessService.resolveSearchScope(userId, spaceId);
        if (scope.isEmpty()) {
            return List.of();
        }

        return toResponses(aiServerClient.search(query, TOP_K, scope), scope);
    }

    /**
     * AI 서버에 추천 전용 API가 없으므로, 대상 북마크의 제목과 요약을 검색어로 사용해 유사한 북마크를 찾는다.
     * 추천 범위는 사용자가 속한 모든 스페이스다.
     */
    @Transactional(readOnly = true)
    public List<SimilarBookmarkResponse> recommend(Long userId, Long bookmarkId) {
        Bookmark target = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));
        spaceAccessService.validateMember(target.getSpace().getId(), userId);

        if (target.getSummary() == null) {
            return List.of();
        }

        List<Long> scope = spaceAccessService.resolveSearchScope(userId, null);
        List<AiSearchResponse.Result> results = aiServerClient.search(
                        target.getTitle() + " " + target.getSummary(), TOP_K + 1, scope).stream()
                .filter(result -> !target.getId().equals(result.bookmarkId()))
                .toList();

        return toResponses(results, scope).stream().limit(TOP_K).toList();
    }

    /**
     * AI 서버가 돌려준 순서(유사도순)를 유지한다.
     * 메인 DB에 없는 북마크와, AI 결과가 범위를 벗어났거나 그 사이 권한이 바뀐 북마크는 제외한다.
     */
    private List<SimilarBookmarkResponse> toResponses(List<AiSearchResponse.Result> results,
            Collection<Long> allowedSpaceIds) {
        Set<Long> allowed = new HashSet<>(allowedSpaceIds);
        List<Long> ids = results.stream().map(AiSearchResponse.Result::bookmarkId).toList();
        Map<Long, Bookmark> bookmarks = bookmarkRepository.findAllById(ids).stream()
                .filter(bookmark -> allowed.contains(bookmark.getSpace().getId()))
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
