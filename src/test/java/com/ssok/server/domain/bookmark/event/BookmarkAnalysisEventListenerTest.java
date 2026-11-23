package com.ssok.server.domain.bookmark.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssok.server.common.config.AiServerProperties;
import com.ssok.server.common.exception.AiServerException;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.ai.client.dto.AiAnalyzeRequest;
import com.ssok.server.domain.bookmark.service.BookmarkAnalysisService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookmarkAnalysisEventListenerTest {

    private static final BookmarkAnalysisRequestedEvent EVENT = new BookmarkAnalysisRequestedEvent(
            1L, "https://spring.io", 7L, LocalDateTime.of(2026, 11, 23, 10, 0), "req-1");

    @Mock
    private AiServerClient aiServerClient;

    @Mock
    private BookmarkAnalysisService bookmarkAnalysisService;

    @Mock
    private AiServerProperties aiServerProperties;

    @InjectMocks
    private BookmarkAnalysisEventListener listener;

    @BeforeEach
    void setUp() {
        given(aiServerProperties.callbackUrl()).willReturn("https://api.ssok.test/api/v1/bookmarks/ai-result");
    }

    @Test
    void 이벤트를_받으면_콜백_주소와_요청_ID를_포함해_AI_서버에_분석을_요청한다() {
        listener.handle(EVENT);

        ArgumentCaptor<AiAnalyzeRequest> captor = ArgumentCaptor.forClass(AiAnalyzeRequest.class);
        verify(aiServerClient).requestAnalysis(captor.capture());
        AiAnalyzeRequest request = captor.getValue();
        assertThat(request.bookmarkId()).isEqualTo(1L);
        assertThat(request.spaceId()).isEqualTo(7L);
        assertThat(request.callbackUrl()).isEqualTo("https://api.ssok.test/api/v1/bookmarks/ai-result");
        assertThat(request.analysisRequestId()).isEqualTo("req-1");
        assertThat(request.savedAt()).startsWith("2026-11-23T10:00:00.000");
        verify(bookmarkAnalysisService, never()).markFailed(any(), any());
    }

    @Test
    void AI_서버_호출이_실패하면_해당_요청을_FAILED로_표시한다() {
        willThrow(new AiServerException("down", null)).given(aiServerClient).requestAnalysis(any());

        listener.handle(EVENT);

        verify(bookmarkAnalysisService).markFailed(1L, "req-1");
    }
}
