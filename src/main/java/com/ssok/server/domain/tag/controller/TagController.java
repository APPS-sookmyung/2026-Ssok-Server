package com.ssok.server.domain.tag.controller;

import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.tag.dto.TagBookmarkListResponse;
import com.ssok.server.domain.tag.dto.TagDto;
import com.ssok.server.domain.tag.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "태그", description = "태그 관련 API")
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @Operation(summary = "태그 목록", description = "AI가 생성한 태그 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TagDto>>> list() {
        List<TagDto> response = tagService.list();

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "tag list retrieved successfully", response));
    }

    @Operation(summary = "특정 태그의 사이트 조회", description = "선택한 태그가 포함된 저장 사이트 목록 조회")
    @GetMapping("/{tagName}/bookmarks")
    public ResponseEntity<ApiResponse<TagBookmarkListResponse>> getBookmarksByTag(
            @PathVariable String tagName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TagBookmarkListResponse response = tagService.getBookmarksByTag(tagName, page, size);

        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), "bookmarks retrieved successfully", response));
    }
}
