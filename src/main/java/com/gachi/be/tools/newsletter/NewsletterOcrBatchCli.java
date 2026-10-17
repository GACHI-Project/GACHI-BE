package com.gachi.be.tools.newsletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient;
import com.gachi.be.domain.newsletter.pipeline.ImagePreprocessor;
import com.gachi.be.domain.newsletter.pipeline.NewsletterDateCandidateExtractor;
import com.gachi.be.domain.newsletter.pipeline.OcrTextRefiner;
import com.gachi.be.global.config.external.ClovaOcrProperties;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 운영 애플리케이션과 별도 진입점. SpringApplication.run을 호출하지 않는다. */
public final class NewsletterOcrBatchCli {
  private NewsletterOcrBatchCli() {}

  public static void main(String[] args) {
    System.exit(execute(args, System.getenv(), System.out));
  }

  static int execute(String[] args, Map<String, String> environment, PrintStream out) {
    try {
      Options options = Options.parse(args);
      if (options.help()) {
        out.println(
            "newsletterOcrBatch --input <file-or-folder> | --manifest <json> "
                + "--output <folder> --reference-date YYYY-MM-DD [--live] [--force]");
        return 0;
      }
      ObjectMapper mapper = new ObjectMapper();
      List<NewsletterOcrBatch.Document> documents = discover(options, mapper);
      if (documents.isEmpty()) {
        throw new IllegalArgumentException("처리할 문서가 없습니다.");
      }
      if (documents.stream().map(NewsletterOcrBatch.Document::sampleId).distinct().count()
          != documents.size()) {
        throw new IllegalArgumentException("중복 sampleId가 있습니다.");
      }
      out.printf(
          "Documents=%d; mode=%s; referenceDate=%s%n",
          documents.size(),
          options.live() ? "LIVE (S3 / Clova OCR 비용 발생 가능)" : "DRY_RUN",
          options.referenceDate());
      for (var document : documents) {
        out.printf("%s files=%d%n", document.sampleId(), document.files().size());
      }
      if (!options.live()) {
        out.println("외부 호출 없음. 실행하려면 --live를 추가하세요.");
        return 0;
      }
      String bucket = required(environment, "AWS_S3_BUCKET");
      ClovaOcrProperties ocrProperties = new ClovaOcrProperties();
      ocrProperties.setInvokeUrl(required(environment, "CLOVA_OCR_API_URL"));
      ocrProperties.setSecretKey(required(environment, "CLOVA_OCR_SECRET_KEY"));
      Region region = Region.of(environment.getOrDefault("AWS_REGION", "ap-northeast-2"));
      try (var credentials = DefaultCredentialsProvider.builder().build();
          var s3 = S3Client.builder().region(region).credentialsProvider(credentials).build();
          var presigner =
              S3Presigner.builder().region(region).credentialsProvider(credentials).build()) {
        var reader =
            new S3BatchPageReader(
                s3,
                new ClovaOcrClient(ocrProperties, presigner, mapper),
                new ImagePreprocessor(),
                bucket);
        var batch =
            new NewsletterOcrBatch(
                mapper,
                new OcrTextRefiner(),
                new NewsletterDateCandidateExtractor(Clock.system(ZoneId.of("Asia/Seoul"))),
                reader);
        var summary =
            batch.run(
                documents,
                options.output(),
                options.referenceDate(),
                options.force(),
                out::println);
        out.printf(
            "SUCCESS=%d REUSED=%d FAILED_OR_PARTIAL=%d%n",
            summary.succeeded(), summary.reused(), summary.failed());
        return summary.failed() == 0 ? 0 : 1;
      }
    } catch (IllegalArgumentException exception) {
      out.println("설정 또는 입력 오류. --help 및 docs/newsletter-ocr-batch.md를 확인하세요.");
      return 2;
    } catch (Exception exception) {
      // SDK/HTTP 예외의 자격증명, 서명 URL, 본문이 콘솔에 출력되지 않도록 한다.
      out.println("실행 실패. 환경 설정, 파일 접근 및 S3/OCR 권한을 확인하세요.");
      return 2;
    }
  }

  private static String required(Map<String, String> environment, String name) {
    String value = environment.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("필수 환경 변수 누락: " + name);
    }
    return value;
  }

  static List<NewsletterOcrBatch.Document> discover(Options options, ObjectMapper mapper)
      throws IOException {
    if (options.manifest() != null) {
      var manifest = mapper.readTree(options.manifest().toFile());
      if (!manifest.isArray()) {
        throw new IllegalArgumentException("manifest는 배열이어야 합니다.");
      }
      List<NewsletterOcrBatch.Document> documents = new ArrayList<>();
      Path base = options.manifest().getParent();
      for (var entry : manifest) {
        if (!entry.path("files").isArray() || !entry.path("sampleId").isTextual()) {
          throw new IllegalArgumentException("sampleId와 files 배열이 필요합니다.");
        }
        List<Path> files = new ArrayList<>();
        for (var file : entry.path("files")) {
          if (!file.isTextual() || file.asText().isBlank()) {
            throw new IllegalArgumentException("파일 경로는 비어 있지 않은 문자열이어야 합니다.");
          }
          files.add(base.resolve(file.asText()).normalize().toAbsolutePath());
        }
        documents.add(new NewsletterOcrBatch.Document(entry.path("sampleId").asText(), files));
      }
      return documents;
    }
    Path input = options.input();
    if (!Files.isDirectory(input)) {
      return List.of(
          new NewsletterOcrBatch.Document(autoId(input.getFileName().toString()), List.of(input)));
    }
    List<NewsletterOcrBatch.Document> documents = new ArrayList<>();
    try (var walk = Files.walk(input)) {
      for (Path file :
          walk.filter(Files::isRegularFile)
              .filter(
                  file ->
                      Set.of("pdf", "jpg", "jpeg", "png", "hwp", "hwpx")
                          .contains(NewsletterOcrBatch.extension(file)))
              .sorted()
              .toList()) {
        String relative = input.relativize(file).toString().replace('\\', '/');
        documents.add(new NewsletterOcrBatch.Document(autoId(relative), List.of(file)));
      }
    }
    return documents;
  }

  private static String autoId(String source) {
    return "doc_"
        + NewsletterOcrBatch.digest(source.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
  }

  record Options(
      Path input,
      Path manifest,
      Path output,
      LocalDate referenceDate,
      boolean live,
      boolean force,
      boolean help) {
    static Options parse(String[] args) {
      Map<String, String> values = new HashMap<>();
      Set<String> flags = new HashSet<>();
      for (int i = 0; i < args.length; i++) {
        String key = args[i];
        if (Set.of("--live", "--force", "--help").contains(key)) {
          if (!flags.add(key)) {
            throw new IllegalArgumentException("중복 옵션입니다.");
          }
        } else if (Set.of("--input", "--manifest", "--output", "--reference-date").contains(key)
            && i + 1 < args.length
            && !args[i + 1].startsWith("--")) {
          if (values.putIfAbsent(key, args[++i]) != null) {
            throw new IllegalArgumentException("중복 옵션입니다.");
          }
        } else {
          throw new IllegalArgumentException("알 수 없거나 값이 없는 옵션입니다.");
        }
      }
      if (flags.contains("--help")) {
        return new Options(null, null, null, null, false, false, true);
      }
      if (values.containsKey("--input") == values.containsKey("--manifest")
          || !values.containsKey("--output")
          || !values.containsKey("--reference-date")) {
        throw new IllegalArgumentException("입력, 출력, 기준일을 지정하세요.");
      }
      Path input = path(values.get("--input"));
      Path manifest = path(values.get("--manifest"));
      Path output = path(values.get("--output"));
      if (input != null && Files.isDirectory(input) && output.startsWith(input)) {
        throw new IllegalArgumentException("출력 폴더는 입력 폴더 밖에 지정하세요.");
      }
      return new Options(
          input,
          manifest,
          output,
          LocalDate.parse(values.get("--reference-date")),
          flags.contains("--live"),
          flags.contains("--force"),
          false);
    }

    private static Path path(String value) {
      return value == null ? null : Path.of(value).toAbsolutePath().normalize();
    }
  }
}
