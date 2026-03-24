package com.gachi.be.file.api;

import com.gachi.be.file.application.S3FileService;
import com.gachi.be.file.dto.S3UploadResponse;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/files")
public class FileController {
    private final S3FileService s3FileService;

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<S3UploadResponse> uploadImage(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(SuccessCode.OK, s3FileService.uploadImage(file));
    }
}
