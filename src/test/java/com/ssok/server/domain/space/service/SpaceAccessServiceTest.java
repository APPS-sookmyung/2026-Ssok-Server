package com.ssok.server.domain.space.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ssok.server.common.exception.ForbiddenException;
import com.ssok.server.common.exception.SpaceNotFoundException;
import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.domain.space.entity.SpaceMember;
import com.ssok.server.domain.space.entity.SpaceRole;
import com.ssok.server.domain.space.repository.SpaceMemberRepository;
import com.ssok.server.domain.space.repository.SpaceRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpaceAccessServiceTest {
    @Mock private SpaceMemberRepository members;
    @Mock private SpaceRepository spaces;
    @InjectMocks private SpaceAccessService access;

    @ParameterizedTest
    @EnumSource(SpaceRole.class)
    void bothRolesCanAccessTheirSpace(SpaceRole role) {
        SpaceMember member = SpaceMember.builder().role(role).build();
        given(members.findBySpaceIdAndUserId(10L, 1L)).willReturn(Optional.of(member));
        assertThat(access.requireMember(1L, 10L)).isSameAs(member);
    }

    @Test
    void nonMemberIsForbidden() {
        assertThatThrownBy(() -> access.requireMember(1L, 10L))
                .isInstanceOf(ForbiddenException.class).hasMessage("space access denied");
    }

    @Test
    void ownerCanManageSpace() {
        SpaceMember owner = SpaceMember.builder().role(SpaceRole.OWNER).build();
        given(members.findBySpaceIdAndUserId(10L, 1L)).willReturn(Optional.of(owner));
        assertThat(access.requireOwner(1L, 10L)).isSameAs(owner);
    }

    @Test
    void memberCannotManageSpaceAndExistingMessageIsPreserved() {
        given(members.findBySpaceIdAndUserId(10L, 1L))
                .willReturn(Optional.of(SpaceMember.builder().role(SpaceRole.MEMBER).build()));
        assertThatThrownBy(() -> access.requireOwner(1L, 10L, "only owner can delete the space"))
                .isInstanceOf(ForbiddenException.class).hasMessage("only owner can delete the space");
    }

    @Test
    void nonMemberCannotManageSpace() {
        assertThatThrownBy(() -> access.requireOwner(1L, 10L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void bookmarkErrorMessageIsPreserved() {
        assertThatThrownBy(() -> access.requireMember(1L, 10L, "not a member of this space"))
                .isInstanceOf(ForbiddenException.class).hasMessage("not a member of this space");
    }

    @Test
    void existingButUnauthorizedSearchSpaceIsForbidden() {
        given(spaces.existsById(10L)).willReturn(true);
        assertThatThrownBy(() -> access.resolveSearchSpaceIds(1L, 10L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void missingSearchSpaceIsNotFoundBeforeCheckingMembership() {
        assertThatThrownBy(() -> access.resolveSearchSpaceIds(1L, 10L))
                .isInstanceOf(SpaceNotFoundException.class);
        verifyNoInteractions(members);
    }

    @Test
    void missingAuthenticatedUserIsRejectedBeforeQueries() {
        assertAll(
                () -> assertThatThrownBy(() -> access.getAccessibleSpaceIds(null))
                        .isInstanceOf(UnauthenticatedException.class),
                () -> assertThatThrownBy(() -> access.requireMember(null, 10L))
                        .isInstanceOf(UnauthenticatedException.class),
                () -> assertThatThrownBy(() -> access.requireOwner(null, 10L))
                        .isInstanceOf(UnauthenticatedException.class),
                () -> assertThatThrownBy(() -> access.resolveSearchSpaceIds(null, null))
                        .isInstanceOf(UnauthenticatedException.class),
                () -> assertThatThrownBy(() -> access.resolveSearchSpaceIds(null, 10L))
                        .isInstanceOf(UnauthenticatedException.class));
        verifyNoInteractions(members, spaces);
    }
}
