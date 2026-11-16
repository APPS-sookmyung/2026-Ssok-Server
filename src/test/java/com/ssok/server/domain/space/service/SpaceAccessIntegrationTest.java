package com.ssok.server.domain.space.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssok.server.common.exception.ForbiddenException;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.entity.SpaceMember;
import com.ssok.server.domain.space.entity.SpaceRole;
import com.ssok.server.domain.space.entity.SpaceType;
import com.ssok.server.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(SpaceAccessService.class)
class SpaceAccessIntegrationTest {
    @Autowired private TestEntityManager entities;
    @Autowired private SpaceAccessService access;

    @Test
    void allSearchIncludesPersonalAndJoinedTeamButExcludesOtherTeams() {
        User user = user("user@test.com");
        User other = user("other@test.com");
        Space personal = space(user, SpaceType.PERSONAL);
        Space joinedTeam = space(other, SpaceType.TEAM);
        Space foreignTeam = space(other, SpaceType.TEAM);
        entities.persist(SpaceMember.builder().space(joinedTeam).user(user).role(SpaceRole.MEMBER).build());
        entities.flush();
        entities.clear();

        assertThat(access.getAccessibleSpaceIds(user.getId()))
                .containsExactlyInAnyOrder(personal.getId(), joinedTeam.getId());
        assertThat(access.resolveSearchSpaceIds(user.getId(), null))
                .containsExactlyInAnyOrder(personal.getId(), joinedTeam.getId());
        assertThat(access.resolveSearchSpaceIds(user.getId(), joinedTeam.getId()))
                .containsExactly(joinedTeam.getId());
        assertThatThrownBy(() -> access.resolveSearchSpaceIds(user.getId(), foreignTeam.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void noMembershipMeansEmptySearchScope() {
        User user = user("user@test.com");
        assertThat(access.getAccessibleSpaceIds(user.getId())).isEmpty();
        assertThat(access.resolveSearchSpaceIds(user.getId(), null)).isEmpty();
    }

    @Test
    void removingMembershipImmediatelyRevokesSearchAndDirectAccess() {
        User user = user("user@test.com");
        User owner = user("owner@test.com");
        Space team = space(owner, SpaceType.TEAM);
        SpaceMember membership = entities.persist(
                SpaceMember.builder().space(team).user(user).role(SpaceRole.MEMBER).build());
        entities.flush();
        assertThat(access.getAccessibleSpaceIds(user.getId())).containsExactly(team.getId());
        entities.remove(membership);
        entities.flush();
        entities.clear();

        assertThat(access.getAccessibleSpaceIds(user.getId())).isEmpty();
        assertThatThrownBy(() -> access.requireMember(user.getId(), team.getId()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> access.resolveSearchSpaceIds(user.getId(), team.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deletedSpaceIsExcludedFromSearchScope() {
        User user = user("user@test.com");
        Space personal = space(user, SpaceType.PERSONAL);
        entities.flush();
        entities.clear();
        assertThat(access.getAccessibleSpaceIds(user.getId())).containsExactly(personal.getId());
        entities.remove(entities.find(Space.class, personal.getId()));
        entities.flush();
        entities.clear();
        assertThat(access.getAccessibleSpaceIds(user.getId())).isEmpty();
    }

    private User user(String email) {
        return entities.persist(User.builder().email(email).password("test-only").name("tester").build());
    }

    private Space space(User owner, SpaceType type) {
        Space space = entities.persist(Space.builder().name("space").type(type).owner(owner).build());
        entities.persist(SpaceMember.builder().space(space).user(owner).role(SpaceRole.OWNER).build());
        return space;
    }
}
