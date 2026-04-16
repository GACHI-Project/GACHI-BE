package com.gachi.be.domain.child.api.controller;

import com.gachi.be.domain.child.dto.request.ChildCreateRequest;
import com.gachi.be.domain.child.dto.response.ChildResponse;
import com.gachi.be.domain.child.service.ChildService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 자녀 등록 및 내 자녀 목록 조회 API를 제공한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/children")
public class ChildController {
  private final ChildService childService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<ChildResponse> createChild(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @Valid @RequestBody ChildCreateRequest request) {
    return ApiResponse.success(
        SuccessCode.CHILD_CREATE_SUCCESS, childService.createChild(authorizationHeader, request));
  }

  @GetMapping
  public ApiResponse<List<ChildResponse>> getMyChildren(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
    return ApiResponse.success(
        SuccessCode.CHILD_GET_LIST_SUCCESS, childService.getChildren(authorizationHeader));
  }
}
