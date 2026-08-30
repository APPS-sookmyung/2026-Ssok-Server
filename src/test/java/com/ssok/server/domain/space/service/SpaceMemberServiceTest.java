package com.ssok.server.domain.space.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

import com.ssok.server.common.exception.ForbiddenException;
import com.ssok.server.common.exception.MemberNotFoundException;
import com.ssok.server.domain.space.entity.SpaceMember;
import com.ssok.server.domain.space.entity.SpaceRole;
import com.ssok.server.domain.space.repository.SpaceMemberRepository;
import com.ssok.server.domain.space.repository.SpaceRepository;
import com.ssok.server.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class SpaceMemberServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMemberRepository spaceMemberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SpaceMemberService spaceMemberService;

    @Test
    void MEMBER는_팀원을_삭제할_수_없다() {
        Long requesterId = 2L;
        Long spaceId = 1L;
        Long memberId = 3L;

        SpaceMember requester = SpaceMember.builder()
                .role(SpaceRole.MEMBER)
                .build();

        given(spaceMemberRepository
                .findBySpaceIdAndUserId(spaceId, requesterId))
                .willReturn(Optional.of(requester));

        assertThatThrownBy(() ->
                spaceMemberService.remove(
                        requesterId,
                        spaceId,
                        memberId
                ))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void OWNER는_MEMBER를_삭제할_수_있다() {
        Long requesterId = 1L;
        Long spaceId = 1L;
        Long memberId = 3L;

        SpaceMember requester = SpaceMember.builder()
                .role(SpaceRole.OWNER)
                .build();

        SpaceMember target = SpaceMember.builder()
                .role(SpaceRole.MEMBER)
                .build();

        given(spaceMemberRepository
                .findBySpaceIdAndUserId(spaceId, requesterId))
                .willReturn(Optional.of(requester));

        given(spaceMemberRepository
                .findBySpaceIdAndId(spaceId, memberId))
                .willReturn(Optional.of(target));

        spaceMemberService.remove(
                requesterId,
                spaceId,
                memberId
        );

        verify(spaceMemberRepository).delete(target);
    }

    @Test
    void OWNER는_OWNER를_삭제할_수_없다() {
        Long requesterId = 1L;
        Long spaceId = 1L;
        Long ownerMemberId = 2L;

        SpaceMember requester = SpaceMember.builder()
                .role(SpaceRole.OWNER)
                .build();

        SpaceMember target = SpaceMember.builder()
                .role(SpaceRole.OWNER)
                .build();

        given(spaceMemberRepository
                .findBySpaceIdAndUserId(spaceId, requesterId))
                .willReturn(Optional.of(requester));

        given(spaceMemberRepository
                .findBySpaceIdAndId(spaceId, ownerMemberId))
                .willReturn(Optional.of(target));

        assertThatThrownBy(() ->
                spaceMemberService.remove(
                        requesterId,
                        spaceId,
                        ownerMemberId
                ))
                .isInstanceOf(ForbiddenException.class);

        verify(spaceMemberRepository, never()).delete(target);
    }

    @Test
    void 존재하지_않는_멤버는_삭제할_수_없다() {
        Long requesterId = 1L;
        Long spaceId = 1L;
        Long memberId = 999L;

        SpaceMember requester = SpaceMember.builder()
                .role(SpaceRole.OWNER)
                .build();

        given(spaceMemberRepository
                .findBySpaceIdAndUserId(spaceId, requesterId))
                .willReturn(Optional.of(requester));

        given(spaceMemberRepository
                .findBySpaceIdAndId(spaceId, memberId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                spaceMemberService.remove(
                        requesterId,
                        spaceId,
                        memberId
                ))
                .isInstanceOf(MemberNotFoundException.class);

        verify(spaceMemberRepository, never())
                .delete(any(SpaceMember.class));
    }
}