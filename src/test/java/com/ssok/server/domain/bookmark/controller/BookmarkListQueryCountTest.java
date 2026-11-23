package com.ssok.server.domain.bookmark.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.entity.SpaceMember;
import com.ssok.server.domain.space.entity.SpaceRole;
import com.ssok.server.domain.space.entity.SpaceType;
import com.ssok.server.domain.space.repository.SpaceMemberRepository;
import com.ssok.server.domain.space.repository.SpaceRepository;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 북마크 카드 목록이 북마크 수에 비례해 쿼리를 늘리지 않는지(N+1) 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class BookmarkListQueryCountTest {

    private static final int BOOKMARKS = 20;
    private static final long MAX_QUERIES = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private SpaceMemberRepository spaceMemberRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockitoBean
    private AiServerClient aiServerClient;

    private User owner;
    private Space space;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder().email("query-count@ssok.com").password("encoded").name("q").build());
        space = spaceRepository.save(Space.builder().name("쿼리").type(SpaceType.PERSONAL).owner(owner).build());
        spaceMemberRepository.save(SpaceMember.builder().space(space).user(owner).role(SpaceRole.OWNER).build());
        for (int i = 0; i < BOOKMARKS; i++) {
            Bookmark bookmark = Bookmark.builder().url("https://q.example/" + i).title("t" + i)
                    .space(space).savedBy(owner).build();
            bookmark.completeAnalysis("요약", "개발", List.of("A" + i, "B"));
            bookmarkRepository.save(bookmark);
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 스페이스_목록_조회의_쿼리_수는_북마크_수와_무관하다() throws Exception {
        long queries = countQueries(() -> mockMvc.perform(get("/api/bookmarks")
                        .param("spaceId", String.valueOf(space.getId())).param("size", "20").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(BOOKMARKS))
                .andExpect(jsonPath("$.data.content[0].tags.length()").value(2)));

        assertThat(queries).as("queries for %d bookmarks", BOOKMARKS).isLessThanOrEqualTo(MAX_QUERIES);
    }

    @Test
    void 최근_저장_목록의_쿼리_수는_북마크_수와_무관하다() throws Exception {
        long queries = countQueries(() -> mockMvc.perform(get("/api/bookmarks/recent").param("size", "20").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(BOOKMARKS)));

        assertThat(queries).as("queries for %d bookmarks", BOOKMARKS).isLessThanOrEqualTo(MAX_QUERIES);
    }

    private long countQueries(ThrowingRunnable action) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        action.run();
        return statistics.getPrepareStatementCount();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor login() {
        return authentication(new UsernamePasswordAuthenticationToken(owner.getId(), null, List.of()));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
