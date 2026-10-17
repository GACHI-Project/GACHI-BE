package com.gachi.be.tools.newsletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrPageResult;
import com.gachi.be.domain.newsletter.pipeline.ImagePreprocessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3BatchPageReaderTest {
  @TempDir Path root;
  private final S3Client s3 = mock(S3Client.class);
  private final ClovaOcrClient ocr = mock(ClovaOcrClient.class);
  private final ImagePreprocessor preprocessor = mock(ImagePreprocessor.class);

  @Test
  void deletesOnlyUploadedVersionEvenWhenOcrFails() throws Exception {
    Path file = Files.writeString(root.resolve("sample.pdf"), "pdf");
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().versionId("uploaded-version").build());
    when(ocr.callOcrPages(eq("bucket"), any())).thenThrow(new IllegalStateException("failed"));

    assertThatThrownBy(() -> new S3BatchPageReader(s3, ocr, preprocessor, "bucket").read(file))
        .isInstanceOf(IllegalStateException.class);

    var put = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3).putObject(put.capture(), any(RequestBody.class));
    var delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3).deleteObject(delete.capture());
    assertThat(put.getValue().key()).startsWith(S3BatchPageReader.PREFIX).endsWith(".pdf");
    assertThat(delete.getValue().key()).isEqualTo(put.getValue().key());
    assertThat(delete.getValue().versionId()).isEqualTo("uploaded-version");
  }

  @Test
  void usesSameExifCorrectionAsServiceBeforeOcr() throws Exception {
    Path file = Files.write(root.resolve("rotated.png"), new byte[] {1, 2});
    when(preprocessor.preprocessForDisplay(any()))
        .thenReturn(new ImagePreprocessor.PreprocessedImage(new byte[] {3}, 2, 3));
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());
    when(ocr.callOcrPages(eq("bucket"), any()))
        .thenReturn(List.of(new OcrPageResult(0, true, List.of())));
    new S3BatchPageReader(s3, ocr, preprocessor, "bucket").read(file);
    var put = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3).putObject(put.capture(), any(RequestBody.class));
    assertThat(put.getValue().key()).endsWith(".jpg");
    assertThat(put.getValue().contentType()).isEqualTo("image/jpeg");
  }
}
