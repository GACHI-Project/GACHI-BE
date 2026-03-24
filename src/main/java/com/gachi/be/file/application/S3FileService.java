package com.gachi.be.file.application;

import com.gachi.be.file.config.S3Properties;
import com.gachi.be.file.dto.S3UploadResponse;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.ExternalApiException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@RequiredArgsConstructor
public class S3FileService {
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public S3UploadResponse uploadImage(MultipartFile file) {
        validateImage(file);
        String bucket = s3Properties.getBucket();
        if (!StringUtils.hasText(bucket)) {
            throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, "AWS_S3_BUCKET is not configured.");
        }

        String key = buildObjectKey(file.getOriginalFilename());
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, file.getSize()));
        } catch (IOException e) {
            throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, "Failed to read uploaded file.");
        } catch (S3Exception e) {
            throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, e.awsErrorDetails().errorMessage());
        }

        return new S3UploadResponse(key, buildObjectUrl(key));
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, "File is empty.");
        }

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, "Unsupported image content type.");
        }
    }

    private String buildObjectKey(String originalFilename) {
        String safeFilename = StringUtils.cleanPath(originalFilename == null ? "image.bin" : originalFilename);
        LocalDate today = LocalDate.now();
        return String.format(
                "%s/%d/%02d/%02d/%s-%s",
                s3Properties.getImagePrefix(),
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                UUID.randomUUID(),
                safeFilename
        );
    }

    private String buildObjectUrl(String key) {
        String encodedKey = UriUtils.encodePath(key, java.nio.charset.StandardCharsets.UTF_8);
        if (StringUtils.hasText(s3Properties.getPublicBaseUrl())) {
            return String.format("%s/%s", trimTrailingSlash(s3Properties.getPublicBaseUrl()), encodedKey);
        }
        return String.format(
                "https://%s.s3.%s.amazonaws.com/%s",
                s3Properties.getBucket(),
                s3Properties.getRegion(),
                encodedKey
        );
    }

    private String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
