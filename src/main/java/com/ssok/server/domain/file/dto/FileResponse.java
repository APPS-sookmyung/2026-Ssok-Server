package com.ssok.server.domain.file.dto;

import com.ssok.server.domain.file.entity.*;
import java.time.Instant;

public record FileResponse(Long fileId, Long spaceId, Long bookmarkId, Long libraryId, Long uploaderId,
        FilePurpose purpose, String originalName, String contentType, long sizeBytes, Instant createdAt, String downloadUrl) {
    public static FileResponse from(StoredFile file) {
        var bookmark = file.getBookmark();
        return new FileResponse(file.getId(), file.getSpace().getId(), bookmark == null ? null : bookmark.getId(),
                bookmark == null || bookmark.getLibrary() == null ? null : bookmark.getLibrary().getId(), file.getUploaderId(),
                file.getPurpose(), file.getOriginalName(), file.getContentType(), file.getSizeBytes(), file.getCreatedAt(),
                "/api/files/" + file.getId() + "/download");
    }
}
