package com.gachi.be.domain.child.api.controller;

import com.gachi.be.domain.child.dto.request.ChildCreateRequest;
import com.gachi.be.domain.child.dto.request.ChildUpdateRequest;
import com.gachi.be.domain.child.dto.response.ChildResponse;
import com.gachi.be.domain.child.service.ChildService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

  @PatchMapping("/{childId}")
  public ApiResponse<Void> updateChild(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long childId,
      @Valid @RequestBody ChildUpdateRequest request) {
    childService.updateChild(authorizationHeader, childId, request);
    return ApiResponse.success(SuccessCode.CHILD_UPDATE_SUCCESS, null);
  }

  @DeleteMapping("/{childId}")
  public ApiResponse<Void> deleteChild(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable Long childId) {
    childService.deleteChild(authorizationHeader, childId);
    return ApiResponse.success(SuccessCode.CHILD_DELETE_SUCCESS, null);
  }
}
