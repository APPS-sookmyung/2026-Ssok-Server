package com.ssok.server.domain.library.dto;

import com.ssok.server.domain.library.entity.Library;
import java.time.LocalDateTime;

public record LibraryResponse(Long libraryId, Long spaceId, String name, long bookmarkCount, LocalDateTime createdAt) {
    public static LibraryResponse from(Library library, long count) {
        return new LibraryResponse(library.getId(), library.getSpace().getId(), library.getName(), count, library.getCreatedAt());
    }
}
