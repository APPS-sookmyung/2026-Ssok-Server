package com.ssok.server.domain.conversation.service;

import com.ssok.server.common.exception.ConversationNotFoundException;
import com.ssok.server.common.exception.InvalidRequestException;
import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.util.TimeFormatter;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.ai.client.dto.AiChatRequest;
import com.ssok.server.domain.ai.client.dto.AiChatResponse;
import com.ssok.server.domain.ai.client.dto.AiSearchResponse;
import com.ssok.server.domain.conversation.dto.BookmarkCardDto;
import com.ssok.server.domain.conversation.dto.ChatResponse;
import com.ssok.server.domain.conversation.dto.ConversationDetailResponse;
import com.ssok.server.domain.conversation.dto.ConversationListResponse;
import com.ssok.server.domain.conversation.dto.ConversationSummaryDto;
import com.ssok.server.domain.conversation.dto.MessageResponse;
import com.ssok.server.domain.conversation.entity.Conversation;
import com.ssok.server.domain.conversation.entity.ConversationMessage;
import com.ssok.server.domain.conversation.repository.ConversationMessageRepository;
import com.ssok.server.domain.conversation.repository.ConversationRepository;
import com.ssok.server.domain.space.service.SpaceAccessService;
import com.ssok.server.domain.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 대화 검색과 보관함.
 *
 * <p>Spring은 대화 기록·권한·링크 카드를 맡고, AI는 질문 해석(되묻기)과 후보 검색을 맡는다.
 * AI에는 최근 메시지 최대 10개와 권한을 확인한 space_ids를 보내고, 돌려받은 후보 북마크는
 * 다시 권한을 확인해 카드로 만든다. 지난 대화를 열 때도 카드는 현재 권한으로 다시 조립한다.
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    static final int HISTORY_LIMIT = 10;
    static final int TOP_K = 5;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int PREVIEW_LENGTH = 80;
    static final String NO_SPACE_REPLY = "아직 검색할 수 있는 스페이스가 없어요. 먼저 사이트를 저장해 주세요.";

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SpaceAccessService spaceAccessService;
    private final AiServerClient aiServerClient;
    private final BookmarkCardAssembler cardAssembler;
    private final PlatformTransactionManager transactionManager;

    /**
     * 질문을 보내고 AI 응답을 받는다. conversationId가 없으면 새 대화를 만든다.
     * AI 호출이 실패하면 질문도 저장하지 않으므로 같은 질문으로 다시 시도하면 된다.
     * AI 호출 동안 DB 트랜잭션을 잡고 있지 않도록 조회 → AI 호출 → 저장 순으로 나눠 처리한다.
     */
    public ChatResponse ask(Long userId, Long conversationId, String question, Long spaceId) {
        String content = question.strip();
        List<Long> scope = spaceAccessService.resolveSearchScope(userId, spaceId);
        List<AiChatRequest.Message> history = conversationId != null
                ? loadHistory(userId, conversationId)
                : List.of();

        AiChatResponse aiResponse = scope.isEmpty()
                ? new AiChatResponse(NO_SPACE_REPLY, false, List.of())
                : askAi(history, content, scope);

        Set<Long> allowed = new HashSet<>(scope);
        List<Long> candidateIds = aiResponse.results() == null ? List.of() : aiResponse.results().stream()
                .map(AiSearchResponse.Result::bookmarkId)
                .distinct()
                .toList();
        Map<Long, BookmarkCardDto> cards = cardAssembler.visibleCards(candidateIds, allowed);
        List<Long> shownIds = candidateIds.stream().filter(cards::containsKey).limit(TOP_K).toList();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            Conversation conversation = conversationId != null
                    ? loadOwned(userId, conversationId)
                    : conversationRepository.save(new Conversation(
                            userRepository.findById(userId)
                                    .orElseThrow(() -> new UnauthenticatedException("authentication required")),
                            content));
            ConversationMessage userMessage = messageRepository.save(ConversationMessage.user(conversation, content));
            ConversationMessage answer = messageRepository.save(ConversationMessage.assistant(
                    conversation, aiResponse.reply(), aiResponse.needsClarification(), shownIds));
            conversation.touch();

            return new ChatResponse(conversation.getId(), conversation.getTitle(),
                    toResponse(userMessage, List.of()),
                    toResponse(answer, BookmarkCardAssembler.inOrder(shownIds, cards)));
        });
    }

    /**
     * 보관함 목록. keyword가 있으면 제목이나 메시지 내용에 포함된 대화만 찾는다.
     */
    @Transactional(readOnly = true)
    public ConversationListResponse list(Long userId, String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("lastMessageAt"), Sort.Order.desc("id")));

        Page<Conversation> result = keyword == null || keyword.isBlank()
                ? conversationRepository.findByUserId(userId, pageable)
                : conversationRepository.search(userId, likePattern(keyword), pageable);

        Map<Long, ConversationMessage> latest = result.isEmpty() ? Map.of() : messageRepository
                .findLatestByConversationIds(result.getContent().stream().map(Conversation::getId).toList())
                .stream()
                .collect(Collectors.toMap(message -> message.getConversation().getId(), Function.identity()));

        List<ConversationSummaryDto> content = result.getContent().stream()
                .map(conversation -> new ConversationSummaryDto(
                        conversation.getId(),
                        conversation.getTitle(),
                        preview(latest.get(conversation.getId())),
                        TimeFormatter.format(conversation.getLastMessageAt()),
                        TimeFormatter.format(conversation.getCreatedAt())))
                .toList();

        return new ConversationListResponse(
                content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /**
     * 지난 대화 열기. 링크 카드는 지금 권한으로 다시 조립하므로 팀을 나갔거나 삭제된 북마크는 빠진다.
     */
    @Transactional(readOnly = true)
    public ConversationDetailResponse get(Long userId, Long conversationId) {
        Conversation conversation = loadOwned(userId, conversationId);
        List<ConversationMessage> messages = messageRepository.findByConversationIdOrderByIdAsc(conversationId);

        Set<Long> referenced = messages.stream()
                .flatMap(message -> message.getBookmarkIds().stream())
                .collect(Collectors.toSet());
        Map<Long, BookmarkCardDto> cards =
                cardAssembler.visibleCards(referenced, spaceAccessService.getAccessibleSpaceIds(userId));

        return new ConversationDetailResponse(
                conversation.getId(),
                conversation.getTitle(),
                TimeFormatter.format(conversation.getCreatedAt()),
                messages.stream()
                        .map(message -> toResponse(message, BookmarkCardAssembler.inOrder(message.getBookmarkIds(), cards)))
                        .toList());
    }

    @Transactional
    public void delete(Long userId, Long conversationId) {
        conversationRepository.delete(loadOwned(userId, conversationId));
    }

    private List<AiChatRequest.Message> loadHistory(Long userId, Long conversationId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx.execute(status -> {
            loadOwned(userId, conversationId);
            // 이번 질문까지 합쳐 최대 HISTORY_LIMIT개가 되도록 이전 메시지는 하나 적게 가져온다.
            List<ConversationMessage> recent = new ArrayList<>(messageRepository.findByConversationIdOrderByIdDesc(
                    conversationId, PageRequest.of(0, HISTORY_LIMIT - 1)));
            Collections.reverse(recent);
            return recent.stream()
                    .map(message -> new AiChatRequest.Message(message.getRole().aiValue(), message.getContent()))
                    .toList();
        });
    }

    private AiChatResponse askAi(List<AiChatRequest.Message> history, String question, List<Long> scope) {
        List<AiChatRequest.Message> messages = new ArrayList<>(history);
        messages.add(new AiChatRequest.Message("user", question));
        return aiServerClient.chat(new AiChatRequest(messages, scope, TOP_K));
    }

    // 다른 사람의 대화는 존재 여부도 드러내지 않도록 404로 응답한다.
    private Conversation loadOwned(Long userId, Long conversationId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.isOwnedBy(userId))
                .orElseThrow(() -> new ConversationNotFoundException("conversation not found"));
    }

    private MessageResponse toResponse(ConversationMessage message, List<BookmarkCardDto> cards) {
        return new MessageResponse(message.getId(), message.getRole(), message.getContent(),
                message.isClarification(), cards, TimeFormatter.format(message.getCreatedAt()));
    }

    private String preview(ConversationMessage message) {
        if (message == null) {
            return null;
        }
        String content = message.getContent().replaceAll("\\s+", " ").strip();
        return content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH) + "…" : content;
    }

    private String likePattern(String keyword) {
        String escaped = keyword.strip().toLowerCase()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
