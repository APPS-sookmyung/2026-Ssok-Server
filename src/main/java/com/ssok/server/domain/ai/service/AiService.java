package com.ssok.server.domain.ai.service;

import com.ssok.server.common.exception.BookmarkNotFoundException;
import com.ssok.server.domain.ai.dto.SimilarBookmarkResponse;
import com.ssok.server.domain.ai.dto.SummaryResponse;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.bookmark.service.BookmarkAnalysisResult;
import com.ssok.server.domain.bookmark.service.BookmarkAnalysisService;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 임베딩/벡터 검색 대신 단어 겹침(Jaccard) 기반으로 유사도를 계산하는 mock 구현.
 * 추후 실제 임베딩 기반 검색으로 교체 가능하도록 별도 서비스로 분리해둠.
 */
@Service
@RequiredArgsConstructor
public class AiService {

    private static final int TOP_N = 5;

    private final BookmarkRepository bookmarkRepository;
    private final BookmarkAnalysisService bookmarkAnalysisService;

    @Transactional
    public SummaryResponse summarize(Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));

        BookmarkAnalysisResult analysis = bookmarkAnalysisService.analyze(bookmark.getUrl());
        bookmark.updateSummary(analysis.summary());
        bookmarkRepository.saveAndFlush(bookmark);

        return new SummaryResponse(bookmark.getId(), bookmark.getSummary());
    }

    @Transactional(readOnly = true)
    public List<SimilarBookmarkResponse> search(String query) {
        Set<String> queryTokens = tokenize(query);

        return rank(queryTokens, bookmarkRepository.findAll());
    }

    @Transactional(readOnly = true)
    public List<SimilarBookmarkResponse> recommend(Long bookmarkId) {
        Bookmark target = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));

        List<Bookmark> others = bookmarkRepository.findAll().stream()
                .filter(bookmark -> !bookmark.getId().equals(target.getId()))
                .toList();

        return rank(tokensOf(target), others);
    }

    private List<SimilarBookmarkResponse> rank(Set<String> referenceTokens, List<Bookmark> candidates) {
        return candidates.stream()
                .map(bookmark ->
                        new SimilarBookmarkResponse(bookmark.getId(), bookmark.getTitle(),
                                similarity(referenceTokens, tokensOf(bookmark))))
                .filter(dto -> dto.similarity() > 0)
                .sorted(Comparator.comparingDouble(SimilarBookmarkResponse::similarity).reversed())
                .limit(TOP_N)
                .toList();
    }

    private Set<String> tokensOf(Bookmark bookmark) {
        String text = bookmark.getTitle() + " " + bookmark.getSummary() + " " + String.join(" ", bookmark.getTags());
        return tokenize(text);
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9가-힣]+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }

    private double similarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);

        return Math.round((double) intersection.size() / union.size() * 100.0) / 100.0;
    }
}
