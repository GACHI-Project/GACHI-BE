package com.gachi.be.tools.newsletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrPageResult;
import com.gachi.be.domain.newsletter.pipeline.NewsletterDateCandidateExtractor;
import com.gachi.be.domain.newsletter.pipeline.OcrTextRefiner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Spring context, DB, 번역 및 AI 분석을 사용하지 않는 라벨링 입력 추출기. */
public class NewsletterOcrBatch {
  static final String SCHEMA_VERSION = "gachi-newsletter-ocr-v1";
  // OCR/정제/결합/후보 생성 규칙 변경 시 캐시 재사용을 막도록 함께 올린다.
  static final String PROCESSING_VERSION = "1";
  private static final Set<String> EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
  private final ObjectMapper mapper;
  private final OcrTextRefiner refiner;
  private final NewsletterDateCandidateExtractor extractor;
  private final PageReader reader;

  public NewsletterOcrBatch(
      ObjectMapper mapper,
      OcrTextRefiner refiner,
      NewsletterDateCandidateExtractor extractor,
      PageReader reader) {
    this.mapper = mapper;
    this.refiner = refiner;
    this.extractor = extractor;
    this.reader = reader;
  }

  @FunctionalInterface
  public interface PageReader {
    List<OcrPageResult> read(Path file) throws IOException;
  }

  public record Document(String sampleId, List<Path> files) {
    public Document {
      if (sampleId == null || !sampleId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,99}")) {
        throw new IllegalArgumentException("sampleId는 영문, 숫자, _, - 조합의 1~100자여야 합니다.");
      }
      files = List.copyOf(files);
      if (files.isEmpty()) {
        throw new IllegalArgumentException("문서에는 파일이 한 개 이상 필요합니다.");
      }
    }
  }

  public record SourceFile(String fileName, String path, String sha256) {}

  public record Page(
      int pageNo,
      String sourceFile,
      int sourcePageNo,
      boolean recognized,
      String ocrText,
      String originalText) {}

  public record DateCandidate(
      String candidateId,
      String originalText,
      String normalizedDate,
      int startOffset,
      int endOffset,
      String extractionType) {}

  public record AnalysisInput(
      String originalText,
      String translatedText,
      String language,
      String referenceDate,
      String timezone,
      List<DateCandidate> dateCandidates) {}

  public record Result(
      String schemaVersion,
      String processingVersion,
      String sampleId,
      String sourceFile,
      List<SourceFile> sourceFiles,
      String fingerprint,
      String status,
      String errorCode,
      String ocrText,
      AnalysisInput input,
      List<Page> pages) {}

  public record Summary(int succeeded, int reused, int failed) {}

  public Summary run(
      List<Document> documents,
      Path output,
      LocalDate referenceDate,
      boolean force,
      Consumer<String> progress)
      throws IOException {
    if (documents.stream().map(Document::sampleId).distinct().count() != documents.size()) {
      throw new IllegalArgumentException("중복 sampleId가 있습니다.");
    }
    Files.createDirectories(output);
    int succeeded = 0;
    int reused = 0;
    int failed = 0;
    for (Document document : documents) {
      Path target = output.resolve(document.sampleId() + ".json");
      List<SourceFile> sources = new ArrayList<>();
      List<Page> pages = new ArrayList<>();
      String fingerprint = null;
      Result result;
      try {
        for (Path file : document.files()) {
          if (!EXTENSIONS.contains(extension(file))) {
            throw new IllegalArgumentException("지원하지 않는 문서 형식입니다.");
          }
          sources.add(
              new SourceFile(
                  file.getFileName().toString(),
                  file.toAbsolutePath().normalize().toString(),
                  hash(file)));
        }
        if (document.files().size() > 1
            && document.files().stream().anyMatch(file -> extension(file).equals("pdf"))) {
          throw new IllegalArgumentException("다중 파일 문서는 이미지만 사용할 수 있습니다.");
        }
        fingerprint =
            digest(
                mapper.writeValueAsBytes(
                    List.of(PROCESSING_VERSION, referenceDate.toString(), sources)));
        if (!force && reusable(target, document.sampleId(), fingerprint)) {
          reused++;
          progress.accept("REUSED " + document.sampleId());
          continue;
        }
        for (Path file : document.files()) {
          List<OcrPageResult> recognizedPages = reader.read(file);
          if (recognizedPages.isEmpty()) {
            throw new IOException("OCR 응답에 페이지가 없습니다.");
          }
          int expectedIndex = 0;
          for (OcrPageResult page : recognizedPages) {
            if (page.pageIndex() != expectedIndex++) {
              throw new IOException("OCR 페이지 순서가 일치하지 않습니다.");
            }
            String raw = page.recognized() ? refiner.parseFields(List.of(page.fields())) : null;
            String original = raw == null ? null : refiner.refineText(raw);
            boolean recognized = original != null && !original.isBlank();
            pages.add(
                new Page(
                    pages.size() + 1,
                    file.getFileName().toString(),
                    page.pageIndex() + 1,
                    recognized,
                    raw,
                    original));
          }
        }
        String original = refiner.joinPageTexts(pages.stream().map(Page::originalText).toList());
        String status =
            original == null
                ? "FAILED"
                : pages.stream().allMatch(Page::recognized) ? "SUCCESS" : "PARTIAL";
        String error = status.equals("SUCCESS") ? null : "UNREADABLE_PAGE";
        result = result(document, sources, fingerprint, status, error, pages, referenceDate);
      } catch (Exception exception) {
        // 외부 예외 메시지는 본문/키/서명 URL을 포함할 수 있으므로 저장하거나 출력하지 않는다.
        result =
            result(
                document,
                sources,
                fingerprint,
                "FAILED",
                exception instanceof IllegalArgumentException
                    ? "INVALID_INPUT"
                    : "OCR_OR_IO_FAILED",
                pages,
                referenceDate);
      }
      writeResult(target, result);
      if (result.status().equals("SUCCESS")) {
        succeeded++;
      } else {
        failed++;
      }
      progress.accept(
          result.status()
              + " "
              + document.sampleId()
              + (result.errorCode() == null ? "" : " " + result.errorCode()));
    }
    return new Summary(succeeded, reused, failed);
  }

  private Result result(
      Document document,
      List<SourceFile> sources,
      String fingerprint,
      String status,
      String error,
      List<Page> pages,
      LocalDate referenceDate) {
    String original = refiner.joinPageTexts(pages.stream().map(Page::originalText).toList());
    List<DateCandidate> candidates = new ArrayList<>();
    for (var candidate : extractor.extract(original, referenceDate)) {
      if (!original
          .substring(candidate.startOffset(), candidate.endOffset())
          .equals(candidate.originalText())) {
        throw new IllegalStateException("날짜 후보 위치가 원문과 일치하지 않습니다.");
      }
      candidates.add(
          new DateCandidate(
              "dc_" + (candidates.size() + 1),
              candidate.originalText(),
              candidate.normalizedDate().toString(),
              candidate.startOffset(),
              candidate.endOffset(),
              candidate.extractionType().name()));
    }
    return new Result(
        SCHEMA_VERSION,
        PROCESSING_VERSION,
        document.sampleId(),
        document.files().get(0).getFileName().toString(),
        List.copyOf(sources),
        fingerprint,
        status,
        error,
        refiner.joinPageTexts(pages.stream().map(Page::ocrText).toList()),
        new AnalysisInput(original, null, "KO", referenceDate.toString(), "Asia/Seoul", candidates),
        List.copyOf(pages));
  }

  private boolean reusable(Path target, String sampleId, String fingerprint) {
    try {
      var cached = mapper.readTree(target.toFile());
      return cached != null
          && cached.path("schemaVersion").asText().equals(SCHEMA_VERSION)
          && cached.path("processingVersion").asText().equals(PROCESSING_VERSION)
          && cached.path("sampleId").asText().equals(sampleId)
          && cached.path("fingerprint").asText().equals(fingerprint)
          && cached.path("status").asText().equals("SUCCESS")
          && !cached.path("input").path("originalText").asText("").isBlank();
    } catch (IOException exception) {
      return false;
    }
  }

  private void writeResult(Path target, Result result) throws IOException {
    Path temporary = Files.createTempFile(target.getParent(), ".ocr-", ".tmp");
    try {
      mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), result);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException exception) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  static String extension(Path file) {
    String name = file.getFileName().toString();
    return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }

  static String digest(byte[] bytes) {
    return HexFormat.of().formatHex(sha256().digest(bytes));
  }

  private static String hash(Path file) throws IOException {
    MessageDigest digest = sha256();
    try (InputStream stream = Files.newInputStream(file)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = stream.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
