package com.gachi.be.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessCode {
    OK(HttpStatus.OK, "OK200", "요청에 성공하였습니다."),
    CREATED(HttpStatus.CREATED, "OK201", "생성에 성공하였습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
