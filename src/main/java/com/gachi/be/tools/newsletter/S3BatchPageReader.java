package com.gachi.be.tools.newsletter;

import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrPageResult;
import com.gachi.be.domain.newsletter.pipeline.ImagePreprocessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** OCR에 필요한 파일만 임시 업로드하고 정확히 그 객체(버전)만 제거한다. */
final class S3BatchPageReader implements NewsletterOcrBatch.PageReader {
  static final String PREFIX = "gachi-tools/newsletter-ocr/";
  private final S3Client s3;
  private final ClovaOcrClient ocr;
  private final ImagePreprocessor preprocessor;
  private final String bucket;

  S3BatchPageReader(
      S3Client s3, ClovaOcrClient ocr, ImagePreprocessor preprocessor, String bucket) {
    this.s3 = s3;
    this.ocr = ocr;
    this.preprocessor = preprocessor;
    this.bucket = bucket;
  }

  @Override
  public List<OcrPageResult> read(Path file) throws IOException {
    String extension = NewsletterOcrBatch.extension(file);
    RequestBody body = RequestBody.fromFile(file);
    if (!extension.equals("pdf")) {
      var prepared = preprocessor.preprocessForDisplay(Files.readAllBytes(file));
      if (prepared.rotated()) {
        extension = "jpg";
        body = RequestBody.fromBytes(prepared.jpegBytes());
      }
    }
    String mimeType =
        switch (extension) {
          case "pdf" -> "application/pdf";
          case "png" -> "image/png";
          default -> "image/jpeg";
        };
    String key = PREFIX + UUID.randomUUID() + "." + extension;
    String version =
        s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(mimeType).build(),
                body)
            .versionId();
    try {
      return ocr.callOcrPages(bucket, key);
    } finally {
      // 버전 관리 버킷에서도 임시 파일의 실제 데이터가 남지 않도록 업로드한 버전을 지정한다.
      s3.deleteObject(
          DeleteObjectRequest.builder().bucket(bucket).key(key).versionId(version).build());
    }
  }
}
