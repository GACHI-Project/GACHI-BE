package com.gachi.be.file.service;

import com.gachi.be.file.dto.response.S3UploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface S3FileService {

  S3UploadResponse uploadImage(MultipartFile file);
}
