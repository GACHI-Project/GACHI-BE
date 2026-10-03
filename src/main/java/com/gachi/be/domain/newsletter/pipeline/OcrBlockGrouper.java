package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.newsletter.entity.NewsletterPageBlock;
import com.gachi.be.domain.newsletter.entity.NewsletterPageBlock.BlockBox;
import com.gachi.be.domain.newsletter.pipeline.ClovaOcrClient.OcrField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 클로바 OCR 단어 박스들을 이미지 오버레이용 문단/블록으로 묶는 컴포넌트. 클로바 field는 거의 단어 단위라서 그대로 번역하면 문장이 잘린다. 그래서 아래 순서로 문단
 * 단위 블록을 만든다. 줄 묶기: Y 중심값이 가까운 단어를 같은 줄로 묶는다. (OcrTextRefiner와 같은 기준: 이전 단어 높이의 70%) 칸 나누기: 같은 줄이라도
 * 단어 사이 간격이 글자 높이의 1.5배를 넘으면 다른 칸(표의 셀 등)으로 나눈다. 문단 묶기: 바로 윗줄 블록과 세로 간격이 좁고, 가로로 겹치고, 글자 크기가 비슷하면
 * 같은 블록으로 이어 붙인다. 단, "1.", "가.", "※", "①" 처럼 항목 기호로 시작하는 줄은 항상 새 블록으로 시작한다. (가정통신문은 번호 항목 구성이 많기
 * 때문) 좌표는 이미지 픽셀이 아니라 0~1 비율로 변환해서 반환한다.
 */
@Slf4j
@Component
public class OcrBlockGrouper {

  /** 같은 줄 판단 기준: 이전 단어 높이 대비 Y 중심 차이 비율. (OcrTextRefiner와 동일) */
  private static final double SAME_ROW_RATIO = 0.7;

  /** 높이 정보가 없을 때 같은 줄 판단 기준 픽셀. (OcrTextRefiner와 동일) */
  private static final double SAME_ROW_FALLBACK_PX = 15.0;

  /** 같은 줄 안에서 이 비율(글자 높이 대비) 이상 떨어져 있으면 다른 칸으로 나눈다. */
  private static final double SEGMENT_GAP_RATIO = 1.5;

  /** 윗줄 블록과 세로 간격이 이 비율(글자 높이 대비) 이하이면 같은 문단으로 본다. */
  private static final double PARAGRAPH_LINE_GAP_RATIO = 0.8;

  /** 윗줄 블록과 가로로 이 비율 이상 겹쳐야 같은 문단으로 본다. */
  private static final double PARAGRAPH_MIN_OVERLAP_RATIO = 0.3;

  /** 글자 높이 차이가 이 범위를 벗어나면(제목 vs 본문 등) 다른 문단으로 본다. */
  private static final double MIN_HEIGHT_RATIO = 0.6;

  private static final double MAX_HEIGHT_RATIO = 1.6;

  /** 좌표 소수점 자리수 (0.0001 단위면 4000px 이미지에서도 1px 미만 오차) */
  private static final double COORDINATE_SCALE = 10000.0;

  /** 새 문단으로 시작해야 하는 항목 기호: 1. 1) (1) 가. 가) ① ※ - • ○ ▶ 등 */
  private static final Pattern LIST_MARKER =
      Pattern.compile("^(\\d{1,2}[.)]|\\(\\d{1,2}\\)|[가나다라마바사아자차카타파하][.)]|[①-⑳]|[-•●■□▶▷◆◇※○·*])");

  /**
   * 클로바 OCR field 목록을 블록으로 묶는다.
   *
   * @param fields 한 페이지의 OCR field 목록
   * @param imageWidth OCR에 사용한 이미지 가로 픽셀
   * @param imageHeight OCR에 사용한 이미지 세로 픽셀
   * @return 읽는 순서(위→아래, 왼→오)대로 번호가 붙은 블록 목록. translatedText는 null
   */
  public List<NewsletterPageBlock> group(List<OcrField> fields, int imageWidth, int imageHeight) {
    if (fields == null || fields.isEmpty()) {
      return List.of();
    }
    List<OcrWord> words = new ArrayList<>();
    for (OcrField field : fields) {
      OcrWord word = toWord(field);
      if (word != null) {
        words.add(word);
      }
    }
    return groupWords(words, imageWidth, imageHeight);
  }

  /** 단어 목록을 블록으로 묶는다. (단위 테스트에서 OcrField 없이 바로 호출할 수 있도록 분리) */
  List<NewsletterPageBlock> groupWords(List<OcrWord> words, int imageWidth, int imageHeight) {
    if (words == null || words.isEmpty() || imageWidth <= 0 || imageHeight <= 0) {
      return List.of();
    }

    List<List<OcrWord>> rows = groupIntoRows(words);

    List<BlockBuilder> blocks = new ArrayList<>();
    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      List<Line> lines = splitIntoLines(rows.get(rowIndex));
      List<BlockBuilder> extendedInThisRow = new ArrayList<>();

      for (Line line : lines) {
        BlockBuilder target = findParagraphBlock(blocks, extendedInThisRow, line, rowIndex);
        if (target == null) {
          target = new BlockBuilder();
          blocks.add(target);
        }
        target.append(line, rowIndex);
        extendedInThisRow.add(target);
      }
    }

    List<NewsletterPageBlock> result = new ArrayList<>();
    for (BlockBuilder block : blocks) {
      result.add(block.build(result.size() + 1, imageWidth, imageHeight));
    }

    log.debug(
        "[OcrBlockGrouper] 블록 묶기 완료. words={}, rows={}, blocks={}",
        words.size(),
        rows.size(),
        result.size());
    return result;
  }

  /** Y 중심값 기준으로 단어를 줄 단위로 묶는다. */
  private List<List<OcrWord>> groupIntoRows(List<OcrWord> words) {
    List<OcrWord> sorted = new ArrayList<>(words);
    sorted.sort(Comparator.comparingDouble(OcrWord::centerY));

    List<List<OcrWord>> rows = new ArrayList<>();
    List<OcrWord> currentRow = new ArrayList<>();
    OcrWord prev = null;

    for (OcrWord word : sorted) {
      if (prev != null) {
        double threshold =
            prev.height() > 0 ? prev.height() * SAME_ROW_RATIO : SAME_ROW_FALLBACK_PX;
        if (word.centerY() - prev.centerY() >= threshold) {
          rows.add(currentRow);
          currentRow = new ArrayList<>();
        }
      }
      currentRow.add(word);
      prev = word;
    }
    if (!currentRow.isEmpty()) {
      rows.add(currentRow);
    }
    return rows;
  }

  /** 한 줄을 왼쪽부터 정렬한 뒤, 간격이 넓은 곳(표의 칸 등)에서 나눠 여러 Line으로 만든다. */
  private List<Line> splitIntoLines(List<OcrWord> row) {
    List<OcrWord> sorted = new ArrayList<>(row);
    sorted.sort(Comparator.comparingDouble(OcrWord::minX));

    double avgHeight = sorted.stream().mapToDouble(OcrWord::height).average().orElse(0.0);
    double gapThreshold =
        avgHeight > 0 ? avgHeight * SEGMENT_GAP_RATIO : SAME_ROW_FALLBACK_PX * SEGMENT_GAP_RATIO;

    List<Line> lines = new ArrayList<>();
    List<OcrWord> current = new ArrayList<>();
    OcrWord prev = null;
    for (OcrWord word : sorted) {
      if (prev != null && word.minX() - prev.maxX() > gapThreshold) {
        lines.add(Line.of(current));
        current = new ArrayList<>();
      }
      current.add(word);
      prev = word;
    }
    if (!current.isEmpty()) {
      lines.add(Line.of(current));
    }
    return lines;
  }

  /** 현재 줄을 이어 붙일 윗줄 블록을 찾는다. 없으면 null (새 블록 시작). */
  private BlockBuilder findParagraphBlock(
      List<BlockBuilder> blocks, List<BlockBuilder> extendedInThisRow, Line line, int rowIndex) {
    if (LIST_MARKER.matcher(line.text()).find()) {
      return null;
    }

    BlockBuilder best = null;
    double bestOverlap = 0.0;
    for (BlockBuilder block : blocks) {
      // 바로 윗줄에서 끝난 블록만 이어 붙일 수 있고, 한 줄에서는 블록당 1개의 Line만 이어 붙인다.
      if (block.lastRowIndex != rowIndex - 1 || extendedInThisRow.contains(block)) {
        continue;
      }
      double referenceHeight = Math.max(line.height(), block.lastLine.height());
      double verticalGap = line.minY() - block.maxY;
      if (referenceHeight <= 0 || verticalGap > referenceHeight * PARAGRAPH_LINE_GAP_RATIO) {
        continue;
      }
      double heightRatio =
          block.lastLine.height() > 0 ? line.height() / block.lastLine.height() : 1.0;
      if (heightRatio < MIN_HEIGHT_RATIO || heightRatio > MAX_HEIGHT_RATIO) {
        continue;
      }
      double overlap = horizontalOverlapRatio(line, block.lastLine);
      if (overlap >= PARAGRAPH_MIN_OVERLAP_RATIO && overlap > bestOverlap) {
        best = block;
        bestOverlap = overlap;
      }
    }
    return best;
  }

  /** 두 줄이 가로로 겹치는 길이를 짧은 쪽 너비로 나눈 비율. */
  private double horizontalOverlapRatio(Line a, Line b) {
    double overlap = Math.min(a.maxX(), b.maxX()) - Math.max(a.minX(), b.minX());
    double minWidth = Math.min(a.maxX() - a.minX(), b.maxX() - b.minX());
    if (overlap <= 0 || minWidth <= 0) {
      return 0.0;
    }
    return overlap / minWidth;
  }

  /** OcrField를 좌표 정보가 정리된 단어로 변환. 텍스트나 좌표가 없으면 null. */
  private OcrWord toWord(OcrField field) {
    if (field == null || field.getInferText() == null || field.getInferText().isBlank()) {
      return null;
    }
    if (field.getBoundingPoly() == null
        || field.getBoundingPoly().getVertices() == null
        || field.getBoundingPoly().getVertices().isEmpty()) {
      return null;
    }

    double minX = Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    for (OcrField.BoundingPoly.Vertex vertex : field.getBoundingPoly().getVertices()) {
      if (vertex.getX() == null || vertex.getY() == null) {
        continue;
      }
      minX = Math.min(minX, vertex.getX());
      minY = Math.min(minY, vertex.getY());
      maxX = Math.max(maxX, vertex.getX());
      maxY = Math.max(maxY, vertex.getY());
    }
    if (minX == Double.MAX_VALUE) {
      return null;
    }
    return new OcrWord(field.getInferText().trim(), minX, minY, maxX, maxY);
  }

  /** 좌표가 정리된 OCR 단어. */
  record OcrWord(String text, double minX, double minY, double maxX, double maxY) {
    double centerY() {
      return (minY + maxY) / 2.0;
    }

    double height() {
      return maxY - minY;
    }
  }

  /** 같은 줄에서 붙어 있는 단어 묶음. */
  private record Line(String text, double minX, double minY, double maxX, double maxY) {
    static Line of(List<OcrWord> words) {
      String text = words.stream().map(OcrWord::text).collect(Collectors.joining(" "));
      double minX = words.stream().mapToDouble(OcrWord::minX).min().orElse(0.0);
      double minY = words.stream().mapToDouble(OcrWord::minY).min().orElse(0.0);
      double maxX = words.stream().mapToDouble(OcrWord::maxX).max().orElse(0.0);
      double maxY = words.stream().mapToDouble(OcrWord::maxY).max().orElse(0.0);
      return new Line(text, minX, minY, maxX, maxY);
    }

    double height() {
      return maxY - minY;
    }
  }

  /** 블록을 만들어 가는 중간 상태. */
  private static final class BlockBuilder {
    private final List<String> texts = new ArrayList<>();
    private double minX = Double.MAX_VALUE;
    private double minY = Double.MAX_VALUE;
    private double maxX = -Double.MAX_VALUE;
    private double maxY = -Double.MAX_VALUE;
    private Line lastLine;
    private int lastRowIndex = -1;

    void append(Line line, int rowIndex) {
      texts.add(line.text());
      minX = Math.min(minX, line.minX());
      minY = Math.min(minY, line.minY());
      maxX = Math.max(maxX, line.maxX());
      maxY = Math.max(maxY, line.maxY());
      lastLine = line;
      lastRowIndex = rowIndex;
    }

    NewsletterPageBlock build(int blockNo, int imageWidth, int imageHeight) {
      double x = ratio(minX, imageWidth);
      double y = ratio(minY, imageHeight);
      double width = Math.min(ratio(maxX, imageWidth) - x, 1.0 - x);
      double height = Math.min(ratio(maxY, imageHeight) - y, 1.0 - y);
      // 문단 안의 줄바꿈은 번역 품질을 위해 공백으로 이어 한 문장 흐름으로 만든다.
      String text = String.join(" ", texts);
      return new NewsletterPageBlock(
          blockNo, text, null, new BlockBox(round(x), round(y), round(width), round(height)));
    }

    private static double ratio(double value, int size) {
      return Math.max(0.0, Math.min(1.0, value / size));
    }

    private static double round(double value) {
      return Math.round(value * COORDINATE_SCALE) / COORDINATE_SCALE;
    }
  }
}
