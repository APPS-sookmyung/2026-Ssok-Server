package com.ssok.server.domain.bookmark.dto;

import com.ssok.server.common.exception.InvalidRequestException;
import java.util.Arrays;
import org.springframework.data.domain.Sort;

/**
 * 북마크 목록 정렬 기준. 같은 값끼리는 최신 저장순으로 정렬해 페이지 간 순서가 흔들리지 않게 한다.
 */
public enum BookmarkSort {
    LATEST("latest", Sort.by(Sort.Order.desc("createdAt"))),
    OLDEST("oldest", Sort.by(Sort.Order.asc("createdAt"))),
    MOST_VISITED("most-visited", Sort.by(Sort.Order.desc("visitCount"), Sort.Order.desc("createdAt"))),
    RECENTLY_VISITED("recently-visited",
            Sort.by(Sort.Order.desc("lastVisitedAt").nullsLast(), Sort.Order.desc("createdAt"))),
    TITLE("title", Sort.by(Sort.Order.asc("title"), Sort.Order.desc("createdAt")));

    public static final String ALLOWED_VALUES = "latest, oldest, most-visited, recently-visited, title";

    private final String value;
    private final Sort sort;

    BookmarkSort(String value, Sort sort) {
        this.value = value;
        this.sort = sort.and(Sort.by(Sort.Order.desc("id")));
    }

    public Sort toSort() {
        return sort;
    }

    public static BookmarkSort from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        return Arrays.stream(values())
                .filter(sort -> sort.value.equalsIgnoreCase(value.strip()))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("invalid sort. allowed: " + ALLOWED_VALUES));
    }
}
