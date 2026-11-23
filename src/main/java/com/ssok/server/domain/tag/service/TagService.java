package com.ssok.server.domain.tag.service;

import com.ssok.server.common.exception.TagNotFoundException;
import com.ssok.server.domain.bookmark.dto.BookmarkSummaryDto;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.space.service.SpaceAccessService;
import com.ssok.server.domain.tag.dto.TagBookmarkListResponse;
import com.ssok.server.domain.tag.dto.TagDto;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final SpaceAccessService spaceAccessService;

    /**
     * 사용자가 속한 스페이스의 북마크에 붙은 태그만 집계한다.
     */
    @Transactional(readOnly = true)
    public List<TagDto> list(Long userId) {
        Set<Long> spaceIds = spaceAccessService.getAccessibleSpaceIds(userId);
        if (spaceIds.isEmpty()) {
            return List.of();
        }

        Map<String, Long> tagCounts = bookmarkRepository.findAllBySpaceIdIn(spaceIds).stream()
                .flatMap(bookmark -> bookmark.getTags().stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        return tagCounts.entrySet().stream()
                .map(entry -> new TagDto(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(TagDto::bookmarkCount).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public TagBookmarkListResponse getBookmarksByTag(Long userId, String tagName, int page, int size) {
        Set<Long> spaceIds = spaceAccessService.getAccessibleSpaceIds(userId);
        if (spaceIds.isEmpty()) {
            throw new TagNotFoundException("tag not found");
        }

        Page<Bookmark> result =
                bookmarkRepository.findByTagsContainingAndSpaceIdIn(tagName, spaceIds, PageRequest.of(page, size));

        // 다른 사람 스페이스에만 있는 태그도 존재 여부를 드러내지 않도록 404로 처리한다.
        if (result.getTotalElements() == 0) {
            throw new TagNotFoundException("tag not found");
        }

        List<BookmarkSummaryDto> content = result.getContent().stream().map(BookmarkSummaryDto::from).toList();

        return new TagBookmarkListResponse(
                tagName, content, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }
}
