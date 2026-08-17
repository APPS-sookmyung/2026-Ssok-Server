package com.ssok.server.domain.tag.service;

import com.ssok.server.common.exception.TagNotFoundException;
import com.ssok.server.common.util.TimeFormatter;
import com.ssok.server.domain.bookmark.dto.BookmarkSummaryDto;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.tag.dto.TagBookmarkListResponse;
import com.ssok.server.domain.tag.dto.TagDto;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TagService {

    private final BookmarkRepository bookmarkRepository;

    @Transactional(readOnly = true)
    public List<TagDto> list() {
        Map<String, Long> tagCounts = bookmarkRepository.findAll().stream()
                .flatMap(bookmark -> bookmark.getTags().stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        return tagCounts.entrySet().stream()
                .map(entry -> new TagDto(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(TagDto::bookmarkCount).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public TagBookmarkListResponse getBookmarksByTag(String tagName, int page, int size) {
        Page<Bookmark> result = bookmarkRepository.findByTagsContaining(tagName, PageRequest.of(page, size));

        if (result.isEmpty()) {
            throw new TagNotFoundException("tag not found");
        }

        List<BookmarkSummaryDto> content = result.getContent().stream()
                .map(bookmark -> new BookmarkSummaryDto(
                        bookmark.getId(),
                        bookmark.getTitle(),
                        bookmark.getSummary(),
                        bookmark.getVisitCount(),
                        bookmark.getLastVisitedAt() != null ? TimeFormatter.format(bookmark.getLastVisitedAt()) : null,
                        bookmark.getTags()))
                .toList();

        return new TagBookmarkListResponse(
                tagName, content, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }
}
