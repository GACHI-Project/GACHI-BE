package com.gachi.be.tools.newsletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrField;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrPageResult;
import com.gachi.be.domain.newsletter.pipeline.NewsletterDateCandidateExtractor;
import com.gachi.be.domain.newsletter.pipeline.OcrTextRefiner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NewsletterOcrBatchTest {
  @TempDir Path root;
  private final ObjectMapper mapper = new ObjectMapper();
  private final LocalDate reference = LocalDate.of(2026, 10, 18);

  private NewsletterOcrBatch batch(NewsletterOcrBatch.PageReader reader) {
    return new NewsletterOcrBatch(
        mapper,
        new OcrTextRefiner(),
        new NewsletterDateCandidateExtractor(Clock.systemUTC()),
        reader);
  }

  private Path file(String name) throws IOException {
    return Files.writeString(root.resolve(name), "source content");
  }

  private OcrPageResult page(int index, String text) throws IOException {
    var field =
        mapper.readValue(
            mapper.writeValueAsString(java.util.Map.of("inferText", text)), OcrField.class);
    return new OcrPageResult(index, true, List.of(field));
  }

  @Test
  void joinsPagesAndExportsActualCandidateOffsets() throws Exception {
    Path pdf = file("안내.pdf");
    var result =
        batch(path -> List.of(page(0, "□ 10월 20일까지 제출"), page(1, "행사 2026. 10. 22.")))
            .run(
                List.of(new NewsletterOcrBatch.Document("pilot_002", List.of(pdf))),
                root.resolve("results"),
                reference,
                false,
                ignored -> {});

    assertThat(result.succeeded()).isEqualTo(1);
    var json = mapper.readTree(root.resolve("results/pilot_002.json").toFile());
    assertThat(json.path("ocrText").asText()).startsWith("□ ");
    String original = json.path("input").path("originalText").asText();
    assertThat(original).isEqualTo("10월 20일까지 제출\n행사 2026. 10. 22.");
    assertThat(json.path("pages")).hasSize(2);
    var candidates = json.path("input").path("dateCandidates");
    assertThat(candidates).hasSize(2);
    for (var candidate : candidates) {
      assertThat(
              original.substring(
                  candidate.path("startOffset").asInt(), candidate.path("endOffset").asInt()))
          .isEqualTo(candidate.path("originalText").asText());
    }
    assertThat(candidates.get(0).path("candidateId").asText()).isEqualTo("dc_1");
    assertThat(candidates.get(0).path("normalizedDate").asText()).isEqualTo("2026-10-20");
    assertThat(json.path("input").path("referenceDate").asText()).isEqualTo("2026-10-18");
  }

  @Test
  void keepsUnreadablePdfPageNumbersAndDoesNotCachePartialResults() throws Exception {
    Path pdf = file("partial.pdf");
    AtomicInteger calls = new AtomicInteger();
    var batch =
        batch(
            path -> {
              calls.incrementAndGet();
              return List.of(new OcrPageResult(0, false, List.of()), page(1, "10월 20일 행사"));
            });
    var documents = List.of(new NewsletterOcrBatch.Document("partial", List.of(pdf)));
    assertThat(batch.run(documents, root.resolve("out"), reference, false, ignored -> {}).failed())
        .isEqualTo(1);
    var json = mapper.readTree(root.resolve("out/partial.json").toFile());
    assertThat(json.path("status").asText()).isEqualTo("PARTIAL");
    assertThat(json.path("pages").get(0).path("recognized").asBoolean()).isFalse();
    assertThat(json.path("pages").get(1).path("pageNo").asInt()).isEqualTo(2);
    batch.run(documents, root.resolve("out"), reference, false, ignored -> {});
    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  void cachesOnlyUnchangedSuccessfulDocumentsAndSupportsForce() throws Exception {
    Path pdf = file("one.pdf");
    AtomicInteger calls = new AtomicInteger();
    var batch =
        batch(
            path -> {
              calls.incrementAndGet();
              return List.of(page(0, "10월 20일 행사"));
            });
    var documents = List.of(new NewsletterOcrBatch.Document("one", List.of(pdf)));
    Path output = root.resolve("out");
    batch.run(documents, output, reference, false, ignored -> {});
    assertThat(batch.run(documents, output, reference, false, ignored -> {}).reused()).isEqualTo(1);
    assertThat(calls.get()).isEqualTo(1);
    batch.run(documents, output, reference, true, ignored -> {});
    batch.run(documents, output, reference.plusYears(1), false, ignored -> {});
    Files.writeString(pdf, "changed");
    batch.run(documents, output, reference.plusYears(1), false, ignored -> {});
    assertThat(calls.get()).isEqualTo(4);
  }

  @Test
  void continuesAfterFailureAndRetriesOnlyFailedDocuments() throws Exception {
    Path bad = file("bad.pdf");
    Path good = file("good.pdf");
    List<String> calls = new ArrayList<>();
    var batch =
        batch(
            path -> {
              calls.add(path.getFileName().toString());
              if (path.equals(bad)) {
                throw new IOException("SECRET_OR_SIGNED_URL");
              }
              return List.of(page(0, "10월 20일 제출"));
            });
    var documents =
        List.of(
            new NewsletterOcrBatch.Document("bad", List.of(bad)),
            new NewsletterOcrBatch.Document("good", List.of(good)));
    Path output = root.resolve("out");
    assertThat(batch.run(documents, output, reference, false, ignored -> {}).failed()).isEqualTo(1);
    assertThat(Files.readString(output.resolve("bad.json"))).doesNotContain("SECRET_OR_SIGNED_URL");
    var rerun = batch.run(documents, output, reference, false, ignored -> {});
    assertThat(rerun.reused()).isEqualTo(1);
    assertThat(calls).containsExactly("bad.pdf", "good.pdf", "bad.pdf");
  }

  @Test
  void preservesManifestImageOrder() throws Exception {
    Path first = file("z.png");
    Path second = file("a.png");
    var batch = batch(path -> List.of(page(0, path.equals(first) ? "첫 페이지" : "두 번째 페이지")));
    batch.run(
        List.of(new NewsletterOcrBatch.Document("images", List.of(first, second))),
        root.resolve("out"),
        reference,
        false,
        ignored -> {});
    var json = mapper.readTree(root.resolve("out/images.json").toFile());
    assertThat(json.path("input").path("originalText").asText()).isEqualTo("첫 페이지\n두 번째 페이지");
    assertThat(json.path("pages").get(1).path("sourcePageNo").asInt()).isEqualTo(1);
  }

  @Test
  void unreadableDocumentIsFailedAndEmptyCacheIsRecreated() throws Exception {
    Path pdf = file("empty.pdf");
    Path output = Files.createDirectories(root.resolve("out"));
    Files.writeString(output.resolve("empty.json"), "");
    var summary =
        batch(path -> List.of(new OcrPageResult(0, false, List.of())))
            .run(
                List.of(new NewsletterOcrBatch.Document("empty", List.of(pdf))),
                output,
                reference,
                false,
                ignored -> {});
    assertThat(summary.failed()).isEqualTo(1);
    var result = mapper.readTree(output.resolve("empty.json").toFile());
    assertThat(result.path("status").asText()).isEqualTo("FAILED");
    assertThat(result.path("input").path("originalText").isNull()).isTrue();
  }

  @Test
  void mixedPdfAndImageIsRejectedBeforeOcr() throws Exception {
    var summary =
        batch(
                path -> {
                  throw new AssertionError("must not call OCR");
                })
            .run(
                List.of(
                    new NewsletterOcrBatch.Document(
                        "mixed", List.of(file("one.pdf"), file("two.png")))),
                root.resolve("out"),
                reference,
                false,
                ignored -> {});
    assertThat(summary.failed()).isEqualTo(1);
  }

  @Test
  void unsupportedFilesAndMissingFilesAreRecordedWithoutCallingOcr() throws Exception {
    var batch =
        batch(
            path -> {
              throw new AssertionError("OCR must not be called");
            });
    var documents =
        List.of(
            new NewsletterOcrBatch.Document("hwp", List.of(file("one.hwp"))),
            new NewsletterOcrBatch.Document("missing", List.of(root.resolve("missing.pdf"))));
    assertThat(batch.run(documents, root.resolve("out"), reference, false, ignored -> {}).failed())
        .isEqualTo(2);
    assertThat(mapper.readTree(root.resolve("out/hwp.json").toFile()).path("errorCode").asText())
        .isEqualTo("INVALID_INPUT");
  }

  @Test
  void rejectsPathTraversalAndDuplicateSampleIdsBeforeExternalCalls() throws Exception {
    assertThatThrownBy(() -> new NewsletterOcrBatch.Document("../escape", List.of(root)))
        .isInstanceOf(IllegalArgumentException.class);
    var document = new NewsletterOcrBatch.Document("same", List.of(file("one.pdf")));
    assertThatThrownBy(
            () ->
                batch(
                        path -> {
                          throw new AssertionError();
                        })
                    .run(
                        List.of(document, document),
                        root.resolve("out"),
                        reference,
                        false,
                        ignored -> {}))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
