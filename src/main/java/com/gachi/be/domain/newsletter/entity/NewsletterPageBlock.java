package com.gachi.be.domain.newsletter.entity;

/**
 * 이미지 오버레이용 문단/블록 1개. newsletter_page.blocks(JSONB)에 목록으로 저장된다.
 *
 * <p>box 좌표는 이미지 픽셀이 아니라 0~1 비율값이다. 프론트는 이미지가 화면에 그려진 크기에 곱하기만 하면 위치를 잡을 수 있다.
 *
 * @param blockNo 페이지 안에서의 블록 순서 (1부터, 위→아래 읽는 순서)
 * @param originalText 블록 한국어 원문
 * @param translatedText 블록 번역문. 한국어 사용자이거나 번역 전이면 null
 * @param box 블록 영역 (0~1 비율)
 */
public record NewsletterPageBlock(
    int blockNo, String originalText, String translatedText, BlockBox box) {

  /** 번역문만 바꾼 새 블록을 만든다. (record는 불변이므로 교체 방식으로 갱신) */
  public NewsletterPageBlock withTranslatedText(String newTranslatedText) {
    return new NewsletterPageBlock(blockNo, originalText, newTranslatedText, box);
  }

  /**
   * 블록 영역. 이미지 좌상단이 (0, 0), 우하단이 (1, 1)이다.
   *
   * @param x 왼쪽 위 x (0~1)
   * @param y 왼쪽 위 y (0~1)
   * @param width 너비 (0~1)
   * @param height 높이 (0~1)
   */
  public record BlockBox(double x, double y, double width, double height) {}
}
