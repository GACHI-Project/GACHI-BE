package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 클로바 OCR 결과를 파싱하고 텍스트를 정제하는 컴포넌트. 1. 파싱 2. 정제 */
@Slf4j
@Component
public class OcrTextRefiner {

  /** OcrField 리스트를 파싱하여 하나의 텍스트 문자열로 합친다. */
  public String parseFields(List<OcrField> fields) {
    if (fields == null || fields.isEmpty()) {
      log.warn("[OcrTextRefiner] fields가 비어있습니다.");
      return "";
    }

    // 각 field의 텍스트와 Y좌표 중앙값을 추출
    List<TextBlock> blocks = new ArrayList<>();
    for (OcrField field : fields) {
      String text = field.getInferText();
      if (text == null || text.isBlank()) continue;

      double centerY = calculateCenterY(field);
      double height = calculateHeight(field);
      blocks.add(new TextBlock(text.trim(), centerY, height));
    }

    if (blocks.isEmpty()) return "";

    // Y좌표 기준 오름차순 정렬 (위에서 아래로)
    blocks.sort(Comparator.comparingDouble(TextBlock::centerY));

    // 줄 그룹핑
    StringBuilder result = new StringBuilder();
    TextBlock prev = null;
    for (TextBlock block : blocks) {
      if (prev == null) {
        // 첫 번째 블록
        result.append(block.text());
      } else {
        // 이전 블록의 높이를 기준으로 임계값 동적 계산
        // prev.height()가 0이면 fallback으로 15픽셀 사용
        double threshold = prev.height() > 0 ? prev.height() * 0.7 : 15.0;

        double yDiff = block.centerY() - prev.centerY();

        if (yDiff < threshold) {
          // 같은 줄 → 공백으로 연결
          result.append(" ").append(block.text());
        } else {
          // 새 줄 → 개행 추가
          result.append("\n").append(block.text());
        }
      }
      prev = block;
    }

    log.debug(
        "[OcrTextRefiner] 파싱 완료. blocksCount={}, textLength={}", blocks.size(), result.length());
    return result.toString();
  }

  /** OCR로 추출한 텍스트에서 노이즈를 제거하고 정제 */
  public String refineText(String rawText) {
    if (rawText == null || rawText.isBlank()) return "";

    String refined =
        rawText
            .replaceAll("[●■□▶▷◆◇→※☆★○•·]", "")
            .replace("\t", " ")
            .replaceAll("[ ]{2,}", " ")
            .lines()
            .map(String::trim)
            .collect(Collectors.joining("\n"))
            .replaceAll("\n{3,}", "\n\n")
            .strip();

    log.debug(
        "[OcrTextRefiner] 정제 완료. before={}chars, after={}chars",
        rawText.length(),
        refined.length());
    return refined;
  }

  /**
   * OcrField의 boundingPoly에서 Y좌표 중앙값을 계산 4개 꼭짓점의 Y좌표 평균을 내어 텍스트 블록의 중심 Y값 vertices가 없으면 0.0 반환 → 맨
   * 위에 배치.
   */
  private double calculateCenterY(OcrField field) {
    if (field.getBoundingPoly() == null
        || field.getBoundingPoly().getVertices() == null
        || field.getBoundingPoly().getVertices().isEmpty()) {
      return 0.0;
    }

    double sumY = 0;
    int count = 0;
    for (OcrField.BoundingPoly.Vertex vertex : field.getBoundingPoly().getVertices()) {
      if (vertex.getY() != null) {
        sumY += vertex.getY();
        count++;
      }
    }
    return count > 0 ? sumY / count : 0.0;
  }

  private double calculateHeight(OcrField field) {
    if (field.getBoundingPoly() == null
        || field.getBoundingPoly().getVertices() == null
        || field.getBoundingPoly().getVertices().isEmpty()) {
      return 0.0;
    }

    double minY = Double.MAX_VALUE;
    double maxY = Double.MIN_VALUE;

    for (OcrField.BoundingPoly.Vertex vertex : field.getBoundingPoly().getVertices()) {
      if (vertex.getY() != null) {
        minY = Math.min(minY, vertex.getY());
        maxY = Math.max(maxY, vertex.getY());
      }
    }

    return (minY == Double.MAX_VALUE) ? 0.0 : maxY - minY;
  }

  /** OCR 텍스트 블록 (텍스트 + Y좌표 중앙값) */
  private record TextBlock(String text, double centerY, double height) {}
}
