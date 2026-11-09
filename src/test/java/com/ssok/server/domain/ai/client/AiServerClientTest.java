package com.ssok.server.domain.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ssok.server.common.exception.AiServerException;
import com.ssok.server.domain.ai.client.dto.AiSearchResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiServerClientTest {

    private MockRestServiceServer server;
    private AiServerClient aiServerClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-server");
        server = MockRestServiceServer.bindTo(builder).build();
        aiServerClient = new AiServerClient(builder.build());
    }

    @Test
    void 분석_요청은_snake_case_JSON으로_전송된다() {
        server.expect(requestTo("http://ai-server/api/v1/ai/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"bookmark_id": 123, "url": "https://example.com/react-19-vs-18"}
                        """, true))
                .andRespond(withSuccess());

        aiServerClient.requestAnalysis(123L, "https://example.com/react-19-vs-18");

        server.verify();
    }

    @Test
    void 검색_응답을_유사도_순서대로_파싱한다() {
        server.expect(requestTo("http://ai-server/api/v1/ai/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"query": "그 리액트 버전 비교하는 글 뭐였지", "top_k": 5}
                        """, true))
                .andRespond(withSuccess("""
                        {
                          "query": "그 리액트 버전 비교하는 글 뭐였지",
                          "results": [
                            {"bookmark_id": 123, "similarity": 0.895},
                            {"bookmark_id": 74, "similarity": 0.712}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<AiSearchResponse.Result> results = aiServerClient.search("그 리액트 버전 비교하는 글 뭐였지", 5);

        assertThat(results).extracting(AiSearchResponse.Result::bookmarkId).containsExactly(123L, 74L);
        assertThat(results.get(0).similarity()).isEqualTo(0.895);
    }

    @Test
    void AI_서버가_오류를_반환하면_AiServerException을_던진다() {
        server.expect(requestTo("http://ai-server/api/v1/ai/search")).andRespond(withServerError());

        assertThatThrownBy(() -> aiServerClient.search("query", 5)).isInstanceOf(AiServerException.class);
    }
}
