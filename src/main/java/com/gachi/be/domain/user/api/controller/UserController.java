package com.gachi.be.domain.user.api.controller;

import com.gachi.be.domain.auth.service.AuthenticatedUserResolver;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.user.dto.request.ChangeLanguageRequest;
import com.gachi.be.domain.user.dto.response.UserMeResponse;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 로그인 사용자 기준 내 정보 조회 API를 제공한다. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
  private final AuthenticatedUserResolver authenticatedUserResolver;
  private final UserRepository userRepository;
  private final NewsletterRepository newsletterRepository;

  @GetMapping("/me")
  public ApiResponse<UserMeResponse> getMyInfo(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);
    return ApiResponse.success(
        SuccessCode.OK,
        new UserMeResponse(
            user.getId(),
            user.getLoginId(),
            user.getEmail(),
            user.getName(),
            user.getPhoneNumber()));
  }

    /** 언어 설정 변경 API */
    @PatchMapping("/me/language")
    @Transactional
    public ApiResponse<Void> changeLanguage(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestBody @Valid ChangeLanguageRequest request) {

        User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);

        String previousLanguage = user.getLanguageCode();
        String newLanguage = request.languageCode();

        user.updateLanguage(newLanguage);
        userRepository.save(user);

        // 진행 중인 파이프라인 FAILED 처리
        int cancelledCount =
            newsletterRepository.cancelInProgressByUserId(
                user.getId(),
                List.of(NewsletterStatus.PENDING, NewsletterStatus.PROCESSING),
                NewsletterStatus.FAILED);

        log.info(
            "[Language] 언어 설정 변경. userId={}, {} -> {}, cancelledPipelines={}",
            user.getId(),
            previousLanguage,
            newLanguage,
            cancelledCount);

        return ApiResponse.success(SuccessCode.USER_LANGUAGE_UPDATED, null);
    }
}
