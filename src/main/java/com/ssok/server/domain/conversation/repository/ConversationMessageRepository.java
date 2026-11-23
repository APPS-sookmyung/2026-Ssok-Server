package com.ssok.server.domain.conversation.repository;

import com.ssok.server.domain.conversation.entity.ConversationMessage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    @EntityGraph(attributePaths = "bookmarkIds")
    List<ConversationMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    // AI에 보낼 최근 메시지 (최신순으로 가져와 뒤집어 쓴다)
    List<ConversationMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    // 보관함 목록에 보여줄 대화별 마지막 메시지
    @Query("""
            select m from ConversationMessage m
            where m.id in (select max(m2.id) from ConversationMessage m2
                           where m2.conversation.id in :conversationIds group by m2.conversation.id)
            """)
    List<ConversationMessage> findLatestByConversationIds(@Param("conversationIds") Collection<Long> conversationIds);
}
