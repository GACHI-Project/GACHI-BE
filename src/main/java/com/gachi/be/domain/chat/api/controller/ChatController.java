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

/** 채팅 컨트롤러. */
@Tag(name = "Chat", description = "AI 챗봇 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat")
public class ChatController {

  private final ChatService chatService;

  @Operation(
      summary = "채팅 메시지 전송",
      description =
          """
          AI 챗봇에게 메시지를 전송하고 응답을 받습니다.

          [공통]
          - sessionId가 null이면 새 세션을 생성합니다.
          - sessionId를 응답에서 받아 다음 요청에 재사용하면 대화 맥락이 유지됩니다.
          - 앱을 종료하고 재진입 시 sessionId 없이 요청하면 새 대화가 시작됩니다.
          - 히스토리 TTL은 1시간입니다.

          [chatType]
          - GENERAL (기본값): 한국 학교 생활/문화에 대한 일반 질문. newsletterId 불필요.
          - DOCUMENT: 특정 가정통신문 한 건에 대한 질의응답. newsletterId 필수.

          [DOCUMENT 모드 주의사항]
          - newsletterId가 없으면 CHAT4002 에러가 발생합니다.
          - 해당 가정통신문이 COMPLETED 상태가 아니면 NL4004 에러가 발생합니다.
          - 하나의 sessionId는 하나의 대화 범위에만 묶입니다.
            GENERAL 세션으로 DOCUMENT 질문을 하거나, A문서 세션으로 B문서를 물으면
            CHAT4003 에러가 발생하므로 문서를 바꿀 때는 sessionId 없이 새로 시작하세요.
          - 답변은 해당 문서 내용을 근거로 하며, 문서에 없는 내용을 보충 설명할 때는
            "이 가정통신문에는 없지만..." 안내와 함께 담임 선생님/학교 문의 안내가 포함됩니다.
          - 문서 본문이 매 요청마다 전송되므로 히스토리는 10턴까지만 유지됩니다.
          """)
  @PostMapping("/messages")
  public ApiResponse<ChatMessageResponse> sendMessage(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ChatMessageRequest request) {
    ChatMessageResponse response = chatService.sendMessage(userId, request);
    return ApiResponse.success(SuccessCode.CHAT_MESSAGE_SUCCESS, response);
  }
}
