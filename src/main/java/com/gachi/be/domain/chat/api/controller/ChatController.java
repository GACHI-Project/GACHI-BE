package com.gachi.be.domain.chat.api.controller;

import com.gachi.be.domain.chat.dto.request.ChatMessageRequest;
import com.gachi.be.domain.chat.dto.response.ChatMessageResponse;
import com.gachi.be.domain.chat.service.ChatService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**채팅 컨트롤러. */
@Tag(name = "Chat", description = "AI 챗봇 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    @Operation(
        summary = "채팅 메시지 전송",
        description = """
          AI 챗봇에게 메시지를 전송하고 응답을 받습니다.

          - sessionId가 null이면 새 세션을 생성합니다.
          - sessionId를 응답에서 받아 다음 요청에 재사용하면 대화 맥락이 유지됩니다.
          - 앱을 종료하고 재진입 시 sessionId 없이 요청하면 새 대화가 시작됩니다.
          - chatType: GENERAL(학교 문화/용어 질문), DOCUMENT는 추후 지원 예정
          """)
    @PostMapping("/messages")
    public ApiResponse<ChatMessageResponse> sendMessage(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody ChatMessageRequest request) {
        ChatMessageResponse response = chatService.sendMessage(userId, request);
        return ApiResponse.success(SuccessCode.CHAT_MESSAGE_SUCCESS, response);
    }
}
