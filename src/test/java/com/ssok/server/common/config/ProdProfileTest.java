package com.ssok.server.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * prod 프로필 설정이 필수 값만으로 기동되고, 상태 확인은 공개·Swagger는 비공개인지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "AI_SERVER_URL=http://ai-server:8000",
        "SERVER_PUBLIC_URL=https://api.ssok.test",
        "AI_CALLBACK_SECRET=prod-secret",
        "CORS_ALLOWED_ORIGINS=https://ssok.test"
})
class ProdProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiServerProperties aiServerProperties;

    @Test
    void 상태_확인은_인증_없이_볼_수_있다() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void 운영에서는_Swagger가_꺼져_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    @Test
    void 콜백_주소는_외부_주소로_만든다() {
        org.assertj.core.api.Assertions.assertThat(aiServerProperties.callbackUrl())
                .isEqualTo("https://api.ssok.test/api/v1/bookmarks/ai-result");
    }
}
