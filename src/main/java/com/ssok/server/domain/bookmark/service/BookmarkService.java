package com.ssok.server.domain.bookmark.service;

import com.ssok.server.common.exception.BookmarkNotFoundException;
import com.ssok.server.common.exception.SpaceNotFoundException;
import com.ssok.server.common.util.TimeFormatter;
import com.ssok.server.domain.bookmark.dto.BookmarkDetailResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkListResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkSaveRequest;
import com.ssok.server.domain.bookmark.dto.BookmarkSaveResponse;
import com.ssok.server.domain.bookmark.dto.BookmarkSummaryDto;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.repository.SpaceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final SpaceRepository spaceRepository;
    private final BookmarkAnalysisService bookmarkAnalysisService;

    @Transactional
    public BookmarkSaveResponse save(BookmarkSaveRequest request) {
        Space space = spaceRepository.findById(request.spaceId())
                .orElseThrow(() -> new SpaceNotFoundException("space not found"));

        BookmarkAnalysisResult analysis = bookmarkAnalysisService.analyze(request.url());

        Bookmark bookmark = Bookmark.builder()
                .url(request.url())
                .title(analysis.title())
                .summary(analysis.summary())
                .tags(analysis.tags())
                .space(space)
                .build();

        Bookmark saved = bookmarkRepository.saveAndFlush(bookmark);

        return new BookmarkSaveResponse(
                saved.getId(), saved.getTitle(), saved.getSummary(), saved.getTags(),
                TimeFormatter.format(saved.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public BookmarkListResponse list(Long spaceId, int page, int size) {
        if (!spaceRepository.existsById(spaceId)) {
            throw new SpaceNotFoundException("space not found");
        }

        Page<Bookmark> result = bookmarkRepository.findAllBySpaceId(spaceId, PageRequest.of(page, size));

        List<BookmarkSummaryDto> content = result.getContent().stream()
                .map(bookmark -> new BookmarkSummaryDto(
                        bookmark.getId(),
                        bookmark.getTitle(),
                        bookmark.getSummary(),
                        bookmark.getVisitCount(),
                        bookmark.getLastVisitedAt() != null ? TimeFormatter.format(bookmark.getLastVisitedAt()) : null,
                        bookmark.getTags()))
                .toList();

        return new BookmarkListResponse(
                content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public void delete(Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));

        bookmarkRepository.delete(bookmark);
    }

    @Transactional
    public BookmarkDetailResponse getDetail(Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));

        bookmark.recordVisit();
        bookmarkRepository.saveAndFlush(bookmark);

        return new BookmarkDetailResponse(
                bookmark.getId(),
                bookmark.getTitle(),
                bookmark.getUrl(),
                bookmark.getSummary(),
                bookmark.getVisitCount(),
                TimeFormatter.format(bookmark.getCreatedAt()),
                TimeFormatter.format(bookmark.getLastVisitedAt()),
                bookmark.getTags());
    }
}
