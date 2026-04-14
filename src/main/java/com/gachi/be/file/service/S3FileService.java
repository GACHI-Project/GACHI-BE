package com.gachi.be.file.service;

import com.gachi.be.file.dto.response.S3UploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface S3FileService {

  S3UploadResponse uploadImage(MultipartFile file);

  /** 가정통신문 파일을 S3에 업로드 */
  S3UploadResponse uploadNewsletter(MultipartFile file);
}
