package com.ssok.server.domain.conversation.repository;

import com.ssok.server.domain.conversation.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Page<Conversation> findByUserId(Long userId, Pageable pageable);

    /**
     * 제목 또는 메시지 내용에 키워드가 들어간 내 대화. pattern은 '!'로 이스케이프한 %keyword% 형식(소문자).
     */
    @Query(value = """
            select c from Conversation c
            where c.user.id = :userId
              and (lower(c.title) like :pattern escape '!'
                   or exists (select m.id from ConversationMessage m
                              where m.conversation = c and lower(m.content) like :pattern escape '!'))
            """,
            countQuery = """
            select count(c) from Conversation c
            where c.user.id = :userId
              and (lower(c.title) like :pattern escape '!'
                   or exists (select m.id from ConversationMessage m
                              where m.conversation = c and lower(m.content) like :pattern escape '!'))
            """)
    Page<Conversation> search(@Param("userId") Long userId, @Param("pattern") String pattern, Pageable pageable);
}
