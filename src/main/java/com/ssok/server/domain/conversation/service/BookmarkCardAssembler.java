package com.ssok.server.domain.conversation.service;

import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.conversation.dto.BookmarkCardDto;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 북마크 ID 목록을 링크 카드로 조립한다. 삭제됐거나 지금 접근할 수 없는 스페이스의 북마크는 뺀다.
 */
@Component
@RequiredArgsConstructor
class BookmarkCardAssembler {

    private final BookmarkRepository bookmarkRepository;

    /**
     * @return 북마크 ID → 카드 (볼 수 있는 것만)
     */
    Map<Long, BookmarkCardDto> visibleCards(Collection<Long> bookmarkIds, Set<Long> allowedSpaceIds) {
        if (bookmarkIds.isEmpty() || allowedSpaceIds.isEmpty()) {
            return Map.of();
        }
        return bookmarkRepository.findWithSpaceByIdIn(Set.copyOf(bookmarkIds)).stream()
                .filter(bookmark -> allowedSpaceIds.contains(bookmark.getSpace().getId()))
                .map(BookmarkCardDto::from)
                .collect(Collectors.toMap(BookmarkCardDto::bookmarkId, Function.identity()));
    }

    /**
     * 순서를 유지하며 볼 수 있는 카드만 남긴다.
     */
    static List<BookmarkCardDto> inOrder(List<Long> bookmarkIds, Map<Long, BookmarkCardDto> cards) {
        return bookmarkIds.stream().filter(cards::containsKey).map(cards::get).toList();
    }
}
