package com.ssok.server.domain.file.controller;

import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.file.dto.FileResponse;
import com.ssok.server.domain.file.entity.FilePurpose;
import com.ssok.server.domain.file.service.FileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "파일", description = "비공개 로컬 파일 저장·조회·다운로드·삭제")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    private final FileService files;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileResponse>> upload(Authentication authentication,
            @RequestParam Long spaceId, @RequestParam(required = false) Long libraryId,
            @RequestParam(defaultValue = "BOOKMARK") FilePurpose purpose, @RequestPart MultipartFile file) {
        return ResponseEntity.status(201).body(ApiResponse.success(201, "file uploaded",
                files.upload(userId(authentication), spaceId, libraryId, purpose, file)));
    }

    @GetMapping("/{fileId}")
    public ApiResponse<FileResponse> metadata(Authentication authentication, @PathVariable Long fileId) {
        return ApiResponse.success(200, "file retrieved", files.metadata(userId(authentication), fileId));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<ByteArrayResource> download(Authentication authentication, @PathVariable Long fileId) {
        var download = files.download(userId(authentication), fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.metadata().contentType()))
                .contentLength(download.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.metadata().originalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(new ByteArrayResource(download.bytes()));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Boolean> delete(Authentication authentication, @PathVariable Long fileId) {
        files.delete(userId(authentication), fileId);
        return ApiResponse.success(200, "file deleted", true);
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            throw new UnauthenticatedException("authentication required");
        }
        return id;
    }
}
