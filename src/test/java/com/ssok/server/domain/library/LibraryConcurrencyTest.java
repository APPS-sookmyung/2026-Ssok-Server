package com.ssok.server.domain.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssok.server.common.exception.LibraryNotFoundException;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.bookmark.dto.BookmarkSaveRequest;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.bookmark.service.BookmarkService;
import com.ssok.server.domain.library.entity.Library;
import com.ssok.server.domain.library.repository.LibraryRepository;
import com.ssok.server.domain.library.service.LibraryService;
import com.ssok.server.domain.space.entity.*;
import com.ssok.server.domain.space.repository.*;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class LibraryConcurrencyTest {
    @Autowired private LibraryService service;
    @Autowired private BookmarkService bookmarkService;
    @Autowired private LibraryRepository libraries;
    @Autowired private BookmarkRepository bookmarks;
    @Autowired private SpaceRepository spaces;
    @Autowired private SpaceMemberRepository members;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private AiServerClient ai;

    @Test
    void folderDeletionAndConcurrentSaveNeverLeaveDanglingLibraryReferences() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        Long[] ids = tx.execute(status -> {
            User user = users.save(User.builder().name("owner").email(UUID.randomUUID() + "@library-race.test").password("test").build());
            Space space = spaces.save(Space.builder().name("race").type(SpaceType.TEAM).owner(user).build());
            members.save(SpaceMember.builder().space(space).user(user).role(SpaceRole.OWNER).build());
            Library library = libraries.save(new Library(space, "folder"));
            return new Long[]{space.getId(), user.getId(), library.getId()};
        });
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var delete = executor.submit(() -> {
                await(ready, start);
                service.delete(ids[1], ids[0], ids[2]);
                return true;
            });
            var save = executor.submit(() -> {
                await(ready, start);
                try {
                    bookmarkService.save(ids[1], new BookmarkSaveRequest("https://example.com/race", ids[0], ids[2]));
                    return true;
                } catch (LibraryNotFoundException expected) { return false; }
            });
            try { assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); }
            finally { start.countDown(); }
            assertThat(delete.get(20, TimeUnit.SECONDS)).isTrue();
            boolean saved = save.get(20, TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                assertThat(libraries.findById(ids[2])).isEmpty();
                var remaining = bookmarks.findAllBySpaceIdIn(java.util.List.of(ids[0]));
                assertThat(remaining).hasSize(saved ? 1 : 0).allSatisfy(bookmark -> assertThat(bookmark.getLibrary()).isNull());
            });
        } finally {
            tx.executeWithoutResult(status -> {
                spaces.deleteById(ids[0]); spaces.flush(); users.deleteById(ids[1]);
            });
        }
    }

    private void await(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test start timed out");
    }
}
