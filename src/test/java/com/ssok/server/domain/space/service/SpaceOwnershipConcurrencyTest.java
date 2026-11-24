package com.ssok.server.domain.space.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssok.server.common.exception.ForbiddenException;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.entity.SpaceMember;
import com.ssok.server.domain.space.entity.SpaceRole;
import com.ssok.server.domain.space.entity.SpaceType;
import com.ssok.server.domain.space.repository.SpaceMemberRepository;
import com.ssok.server.domain.space.repository.SpaceRepository;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SpaceOwnershipConcurrencyTest {
    @Autowired private SpaceRepository spaces;
    @Autowired private SpaceMemberRepository members;
    @Autowired private UserRepository users;
    @Autowired private SpaceMemberService service;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void concurrentTransfersLeaveExactlyOneOwnerMatchingSpaceOwner() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        String suffix = UUID.randomUUID().toString();
        Long[] ids = tx.execute(status -> {
            User owner = user("owner", suffix);
            User first = user("first", suffix);
            User second = user("second", suffix);
            Space space = spaces.save(Space.builder().name("concurrent").type(SpaceType.TEAM).owner(owner).build());
            member(space, owner, SpaceRole.OWNER);
            SpaceMember a = member(space, first, SpaceRole.EDITOR);
            SpaceMember b = member(space, second, SpaceRole.VIEWER);
            return new Long[]{space.getId(), owner.getId(), a.getId(), b.getId(), first.getId(), second.getId()};
        });
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transfer(ids, ids[2], ready, start));
            var second = executor.submit(() -> transfer(ids, ids[3], ready, start));
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            int successes = (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successes).isEqualTo(1);
            tx.executeWithoutResult(status -> {
                var owners = members.findAllBySpaceId(ids[0]).stream()
                        .filter(m -> m.getRole() == SpaceRole.OWNER).toList();
                assertThat(owners).hasSize(1);
                assertThat(spaces.findById(ids[0]).orElseThrow().getOwner().getId())
                        .isEqualTo(owners.getFirst().getUser().getId());
                assertThat(members.findBySpaceIdAndUserId(ids[0], ids[1]).orElseThrow().getRole())
                        .isEqualTo(SpaceRole.EDITOR);
            });
        } finally {
            tx.executeWithoutResult(status -> {
                spaces.deleteById(ids[0]);
                spaces.flush();
                users.deleteById(ids[1]);
                users.deleteById(ids[4]);
                users.deleteById(ids[5]);
            });
        }
    }

    private boolean transfer(Long[] ids, Long target, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("transfer start timed out");
        }
        try {
            service.transferOwnership(ids[1], ids[0], target);
            return true;
        } catch (ForbiddenException expected) {
            return false;
        }
    }

    private User user(String name, String suffix) {
        return users.save(User.builder().name(name).email(name + suffix + "@concurrency.test").password("test-only").build());
    }

    private SpaceMember member(Space space, User user, SpaceRole role) {
        return members.save(SpaceMember.builder().space(space).user(user).role(role).build());
    }
}
