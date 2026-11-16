package com.ssok.server.domain.bookmark.controller;

import com.ssok.server.common.config.AiServerProperties;
import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.bookmark.dto.AiAnalysisResultRequest;
import com.ssok.server.domain.bookmark.service.BookmarkAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 콜백", description = "AI 서버(FastAPI) 전용 콜백 API")
@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkAiCallbackController {

    public static final String CALLBACK_SECRET_HEADER = "X-AI-Callback-Secret";

    private final BookmarkAnalysisService bookmarkAnalysisService;
    private final AiServerProperties aiServerProperties;

    @Operation(summary = "AI 분석 결과 수신", description = "AI 서버가 분석을 마친 뒤 요약/카테고리/태그를 전달")
    @PostMapping("/ai-result")
    public ResponseEntity<ApiResponse<Void>> receiveResult(
            @RequestHeader(value = CALLBACK_SECRET_HEADER, required = false) String secret,
            @RequestBody @Valid AiAnalysisResultRequest request) {
        verifySecret(secret);
        bookmarkAnalysisService.applyResult(request);

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "analysis result applied", null));
    }

    private void verifySecret(String secret) {
        String expected = aiServerProperties.callbackSecret();
        if (expected == null || expected.isBlank()) {
            return;
        }

        if (secret == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthenticatedException("invalid callback secret");
        }
    }
}
