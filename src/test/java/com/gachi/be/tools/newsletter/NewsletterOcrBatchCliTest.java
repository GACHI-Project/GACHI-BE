package com.gachi.be.tools.newsletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NewsletterOcrBatchCliTest {
  @TempDir Path root;

  @Test
  void dryRunNeedsNoCredentialsOrDatabaseAndWritesNoResults() throws Exception {
    Path source = Files.writeString(root.resolve("sample.pdf"), "not a real pdf");
    Path output = root.resolve("out");
    var stdout = new ByteArrayOutputStream();
    int code =
        NewsletterOcrBatchCli.execute(
            new String[] {
              "--input",
              source.toString(),
              "--output",
              output.toString(),
              "--reference-date",
              "2026-10-18"
            },
            Map.of(),
            new PrintStream(stdout));
    assertThat(code).isZero();
    assertThat(stdout.toString()).contains("DRY_RUN", "Documents=1");
    assertThat(output).doesNotExist();
  }

  @Test
  void readsManifestRelativePathsAndStableSampleIds() throws Exception {
    Path manifest =
        Files.writeString(
            root.resolve("manifest.json"),
            """
        [{"sampleId":"pilot_002","files":["pages/2.png","pages/1.png"]}]
        """);
    var options =
        NewsletterOcrBatchCli.Options.parse(
            new String[] {
              "--manifest",
              manifest.toString(),
              "--output",
              root.resolve("out").toString(),
              "--reference-date",
              "2026-10-18"
            });
    var documents = NewsletterOcrBatchCli.discover(options, new ObjectMapper());
    assertThat(documents).hasSize(1);
    assertThat(documents.get(0).sampleId()).isEqualTo("pilot_002");
    assertThat(documents.get(0).files())
        .containsExactly(root.resolve("pages/2.png"), root.resolve("pages/1.png"));
  }

  @Test
  void rejectsUnknownOrMissingOptionsAndOutputInsideInput() {
    assertThatThrownBy(() -> NewsletterOcrBatchCli.Options.parse(new String[] {"--liv"}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                NewsletterOcrBatchCli.Options.parse(
                    new String[] {
                      "--input",
                      root.toString(),
                      "--output",
                      root.resolve("out").toString(),
                      "--reference-date",
                      "2026-10-18"
                    }))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(NewsletterOcrBatchCli.execute(new String[] {"--live"}, Map.of(), System.out))
        .isEqualTo(2);
  }
}
