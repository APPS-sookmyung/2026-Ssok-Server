package com.ssok.server.domain.bookmark.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssok.server.common.exception.AiServerException;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.bookmark.service.BookmarkAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookmarkAnalysisEventListenerTest {

    @Mock
    private AiServerClient aiServerClient;

    @Mock
    private BookmarkAnalysisService bookmarkAnalysisService;

    @InjectMocks
    private BookmarkAnalysisEventListener listener;

    @Test
    void 이벤트를_받으면_AI_서버에_분석을_요청한다() {
        listener.handle(new BookmarkAnalysisRequestedEvent(1L, "https://spring.io"));

        verify(aiServerClient).requestAnalysis(1L, "https://spring.io");
        verify(bookmarkAnalysisService, never()).markFailed(anyLong());
    }

    @Test
    void AI_서버_호출이_실패하면_북마크를_FAILED로_표시한다() {
        willThrow(new AiServerException("down", null)).given(aiServerClient).requestAnalysis(anyLong(), any());

        listener.handle(new BookmarkAnalysisRequestedEvent(1L, "https://spring.io"));

        verify(bookmarkAnalysisService).markFailed(1L);
    }
}
