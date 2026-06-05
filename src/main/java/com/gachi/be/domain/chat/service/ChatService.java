package com.gachi.be.domain.chat.service;

import com.gachi.be.domain.chat.dto.request.ChatMessageRequest;
import com.gachi.be.domain.chat.dto.response.ChatMessageResponse;

public interface ChatService {

  /** 채팅 메시지 전송 및 AI 응답 반환. */
  ChatMessageResponse sendMessage(Long userId, ChatMessageRequest request);
}
