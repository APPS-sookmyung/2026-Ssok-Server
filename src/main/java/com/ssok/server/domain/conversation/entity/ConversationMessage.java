package com.ssok.server.domain.conversation.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "conversation_messages", indexes = @Index(name = "idx_message_conversation",
        columnList = "conversation_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationMessage {

    public static final int MAX_CONTENT_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // AI가 되물은 메시지인지 (ASSISTANT만 해당)
    @Column(nullable = false)
    private boolean clarification;

    // 이 응답에서 보여준 후보 북마크 (유사도순). 카드는 열 때마다 현재 권한으로 다시 조립한다.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "conversation_message_bookmarks", joinColumns = @JoinColumn(name = "message_id"))
    @OrderColumn(name = "rank_order")
    @Column(name = "bookmark_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<Long> bookmarkIds = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ConversationMessage(Conversation conversation, MessageRole role, String content, boolean clarification,
            List<Long> bookmarkIds) {
        this.conversation = conversation;
        this.role = role;
        this.content = content;
        this.clarification = clarification;
        this.bookmarkIds.addAll(bookmarkIds);
    }

    public static ConversationMessage user(Conversation conversation, String content) {
        return new ConversationMessage(conversation, MessageRole.USER, content, false, List.of());
    }

    public static ConversationMessage assistant(Conversation conversation, String content, boolean clarification,
            List<Long> bookmarkIds) {
        return new ConversationMessage(conversation, MessageRole.ASSISTANT, content, clarification, bookmarkIds);
    }
}
