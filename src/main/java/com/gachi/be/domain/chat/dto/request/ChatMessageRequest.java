package com.gachi.be.domain.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
    String sessionId,
    @NotBlank(message = "메시지를 입력해주세요.") @Size(max = 1000, message = "메시지는 1000자 이하여야 합니다.")
        String message,
    String chatType,
    // chatType=DOCUMENT일 때 질문 대상이 되는 가정통신문 ID.
    // GENERAL이면 무시. DOCUMENT인데 null이면 CHAT_NEWSLETTER_ID_REQUIRED 에러.
    Long newsletterId) {}
