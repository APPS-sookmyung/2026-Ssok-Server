package com.ssok.server.domain.conversation.entity;

import com.ssok.server.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 대화 검색 보관함의 대화방. 대화는 개인 소유이며 본인만 볼 수 있다.
 */
@Entity
@Table(name = "conversations", indexes = @Index(name = "idx_conversation_user_updated",
        columnList = "user_id, lastMessageAt"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

    public static final int MAX_TITLE_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    // 첫 질문으로 만든 제목
    @Column(nullable = false, length = MAX_TITLE_LENGTH + 1)
    private String title;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastMessageAt;

    public Conversation(User user, String firstQuestion) {
        this.user = user;
        String trimmed = firstQuestion.strip().replaceAll("\\s+", " ");
        this.title = trimmed.length() > MAX_TITLE_LENGTH ? trimmed.substring(0, MAX_TITLE_LENGTH) + "…" : trimmed;
        this.lastMessageAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    public void touch() {
        this.lastMessageAt = LocalDateTime.now();
    }
}
