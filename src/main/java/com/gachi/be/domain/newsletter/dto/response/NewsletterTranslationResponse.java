package com.gachi.be.domain.newsletter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.NewsletterDateCandidate;
import com.gachi.be.domain.newsletter.entity.NewsletterPage;
import com.gachi.be.domain.newsletter.entity.NewsletterPageBlock;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPageStatus;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterSourceType;
import java.util.List;

/**
 * 번역 결과 조회 API 응답 DTO
 *
 * <p>페이지별 결과(pages)를 추가 기존 필드(originalText/translatedText/fileUrl 등)는 그대로 유지하므로 기존 화면은 영향이 없다. 기능
 * 도입 전에 분석된 문서는 pages가 빈 배열이며, 이 경우 기존처럼 전체 텍스트로 보여주면 된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewsletterTranslationResponse(
    String originalText,
    String translatedText,
    String language,
    String fileUrl,
    List<NewsletterDateCandidateResponse> dateCandidates,
    NewsletterSourceType sourceType,
    Integer totalPages,
    List<PageItem> pages) {

  public static NewsletterTranslationResponse from(
      Newsletter newsletter, List<NewsletterDateCandidate> dateCandidates, String fileUrl) {
    // 페이지 정보 없이 호출하면 빈 페이지 목록으로 응답한다. (기존 호출부 호환용)
    return from(newsletter, dateCandidates, fileUrl, List.of());
  }

  /** 페이지별 결과까지 포함한 번역 응답을 만든다. 페이지 이미지 URL은 서비스에서 만들어 PageItem에 담아 넘긴다. */
  public static NewsletterTranslationResponse from(
      Newsletter newsletter,
      List<NewsletterDateCandidate> dateCandidates,
      String fileUrl,
      List<PageItem> pages) {
    return new NewsletterTranslationResponse(
        newsletter.getOriginalText(),
        newsletter.getTranslatedText(),
        newsletter.getLanguage(),
        fileUrl,
        dateCandidates == null
            ? List.of()
            : dateCandidates.stream().map(NewsletterDateCandidateResponse::from).toList(),
        newsletter.resolveSourceType(),
        newsletter.getTotalPages(),
        pages == null ? List.of() : pages);
  }

  /**
   * 페이지 1장의 결과.
   *
   * <p>PDF 페이지는 imageUrl/imageWidth/imageHeight/blocks가 응답에서 빠진다. (NON_NULL) PDF 썸네일은 fileUrl(PDF)의
   * pageNo 페이지를 앱에서 그리면 된다.
   *
   * @param pageNo 1부터 시작하는 페이지 번호
   * @param status 페이지 처리 상태 (SUCCESS / SKIPPED 등)
   * @param originalText 페이지 한국어 원문 (OCR). 인식 못 한 페이지는 null
   * @param translatedText 페이지 번역문. 한국어 사용자이거나 번역을 건너뛴 페이지는 null
   * @param imageUrl 이미지 페이지 표시용 presigned URL (회전 보정 이미지). PDF는 null
   * @param imageWidth 이미지 가로 픽셀. PDF는 null
   * @param imageHeight 이미지 세로 픽셀. PDF는 null
   * @param blocks 이미지 오버레이 블록 목록. PDF는 null
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PageItem(
      Integer pageNo,
      NewsletterPageStatus status,
      String originalText,
      String translatedText,
      String imageUrl,
      Integer imageWidth,
      Integer imageHeight,
      List<BlockItem> blocks) {

    public static PageItem from(NewsletterPage page, String imageUrl) {
      List<BlockItem> blocks =
          page.getBlocks() == null ? null : page.getBlocks().stream().map(BlockItem::from).toList();
      return new PageItem(
          page.getPageNo(),
          page.getStatus(),
          page.getOriginalText(),
          page.getTranslatedText(),
          imageUrl,
          page.getImageWidth(),
          page.getImageHeight(),
          blocks);
    }
  }

  /**
   * 오버레이 블록 1개. box는 0~1 비율 좌표다.
   *
   * @param blockNo 페이지 안에서의 블록 순서 (1부터)
   * @param originalText 블록 한국어 원문
   * @param translatedText 블록 번역문. 한국어 사용자이거나 번역을 건너뛴 페이지는 null
   * @param box 블록 영역 (x, y, width, height 모두 0~1)
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record BlockItem(int blockNo, String originalText, String translatedText, BoxItem box) {

    public static BlockItem from(NewsletterPageBlock block) {
      NewsletterPageBlock.BlockBox box = block.box();
      return new BlockItem(
          block.blockNo(),
          block.originalText(),
          block.translatedText(),
          box == null ? null : new BoxItem(box.x(), box.y(), box.width(), box.height()));
    }
  }

  /** 블록 영역. 이미지 좌상단이 (0, 0), 우하단이 (1, 1). */
  public record BoxItem(double x, double y, double width, double height) {}
}
