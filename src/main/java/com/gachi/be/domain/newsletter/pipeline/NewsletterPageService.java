package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.NewsletterPage;
import com.gachi.be.domain.newsletter.entity.NewsletterPageBlock;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPageStatus;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterSourceType;
import com.gachi.be.domain.newsletter.repository.NewsletterPageRepository;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파이프라인에서 페이지 단위 결과를 저장하는 서비스.
 *
 * NewsletterPipelineStatusService와 같은 원칙으로, 파이프라인(@Async, 비트랜잭션)의 각 단계 결과를 REQUIRES_NEW 트랜잭션으로
 * 바로 커밋한다. 그래서 중간에 멈추거나 실패해도 이미 끝난 페이지 결과는 DB에 남아 있고, 이어서 진행할 때 그 페이지는 다시 처리하지 않는다.
 *
 * 반환하는 엔티티는 트랜잭션이 끝난 뒤의 스냅샷(detached)이므로 읽기 용도로만 사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsletterPageService {

  private final NewsletterPageRepository newsletterPageRepository;
  private final NewsletterRepository newsletterRepository;

  /**
   * 파이프라인 시작 시 페이지 레코드를 준비한다.
   *
   * 이미 페이지가 있으면(이어서 진행/다시 분석) 그대로 반환한다. 처음 실행이면 이미지는 업로드 순서대로 페이지를 만들고, PDF는 페이지 수를 OCR 응답 후에 알
   * 수 있으므로 빈 목록을 반환한다. (PDF 페이지는 createPdfPages에서 생성)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<NewsletterPage> preparePages(Long newsletterId) {
    Newsletter newsletter = findNewsletter(newsletterId);
    List<NewsletterPage> existing =
        newsletterPageRepository.findAllByNewsletterIdOrderByPageNoAsc(newsletterId);
    if (!existing.isEmpty()) {
      log.debug(
          "[NewsletterPage] 기존 페이지 재사용. newsletterId={}, pages={}", newsletterId, existing.size());
      return existing;
    }

    NewsletterSourceType sourceType = newsletter.resolveSourceType();
    List<String> fileKeys = newsletter.resolveFileKeys();
    if (fileKeys.isEmpty()) {
      throw new IllegalStateException("OCR 대상 파일 키가 없습니다. newsletterId=" + newsletterId);
    }

    if (sourceType == NewsletterSourceType.PDF) {
      newsletter.initPageInfo(NewsletterSourceType.PDF, null);
      newsletterRepository.save(newsletter);
      log.debug("[NewsletterPage] PDF 문서. OCR 응답 후 페이지를 생성합니다. newsletterId={}", newsletterId);
      return List.of();
    }

    List<NewsletterPage> pages = new ArrayList<>();
    for (int i = 0; i < fileKeys.size(); i++) {
      pages.add(
          NewsletterPage.builder()
              .newsletterId(newsletterId)
              .pageNo(i + 1)
              .fileKey(fileKeys.get(i))
              .status(NewsletterPageStatus.PENDING)
              .build());
    }
    List<NewsletterPage> saved = newsletterPageRepository.saveAll(pages);
    newsletter.initPageInfo(NewsletterSourceType.IMAGE, saved.size());
    newsletterRepository.save(newsletter);
    log.debug("[NewsletterPage] 이미지 페이지 생성. newsletterId={}, pages={}", newsletterId, saved.size());
    return saved;
  }

  /**
   * PDF OCR 결과로 페이지 레코드를 만든다. 원문이 없는(인식하지 못한) 페이지는 결정 사항에 따라 멈추지 않고 자동으로 건너뛰기(SKIPPED) 처리한다. (PDF는
   * 해당 페이지만 다시 찍을 방법이 없기 때문)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<NewsletterPage> createPdfPages(
      Long newsletterId, String pdfFileKey, List<PageOcrText> pageTexts) {
    Newsletter newsletter = findNewsletter(newsletterId);

    List<NewsletterPage> pages = new ArrayList<>();
    for (PageOcrText pageText : pageTexts) {
      NewsletterPage page =
          NewsletterPage.builder()
              .newsletterId(newsletterId)
              .pageNo(pageText.pageNo())
              .fileKey(pdfFileKey)
              .status(NewsletterPageStatus.PENDING)
              .build();
      if (pageText.hasOriginalText()) {
        page.completeOcr(pageText.ocrText(), pageText.originalText(), null);
      } else {
        log.info(
            "[NewsletterPage] PDF 인식 불가 페이지 자동 건너뛰기. newsletterId={}, pageNo={}",
            newsletterId,
            pageText.pageNo());
        page.skip();
      }
      pages.add(page);
    }
    List<NewsletterPage> saved = newsletterPageRepository.saveAll(pages);
    newsletter.initPageInfo(NewsletterSourceType.PDF, saved.size());
    newsletterRepository.save(newsletter);
    return saved;
  }

  /** 문서의 전체 페이지를 순서대로 조회한다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public List<NewsletterPage> findPages(Long newsletterId) {
    return newsletterPageRepository.findAllByNewsletterIdOrderByPageNoAsc(newsletterId);
  }

  /** 이미지 표시/OCR/AI 첨부용 파일 정보를 저장한다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NewsletterPage updateDisplayImage(
      Long pageId, String displayFileKey, int imageWidth, int imageHeight) {
    return update(pageId, page -> page.updateDisplayImage(displayFileKey, imageWidth, imageHeight));
  }

  /** 페이지 OCR 성공 결과를 저장한다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NewsletterPage completeOcr(
      Long pageId, String ocrText, String originalText, List<NewsletterPageBlock> blocks) {
    return update(pageId, page -> page.completeOcr(ocrText, originalText, blocks));
  }

  /** 페이지 번역 결과를 저장한다. 한국어 사용자는 translatedText/blocks 모두 null로 들어와 번역 없이 완료된다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NewsletterPage completeTranslation(
      Long pageId, String translatedText, List<NewsletterPageBlock> blocks) {
    return update(pageId, page -> page.completeTranslation(translatedText, blocks));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NewsletterPage markOcrFailed(Long pageId) {
    return update(pageId, NewsletterPage::markOcrFailed);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NewsletterPage markUnreadable(Long pageId) {
    return update(pageId, NewsletterPage::markUnreadable);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NewsletterPage markTranslationFailed(Long pageId) {
    return update(pageId, NewsletterPage::markTranslationFailed);
  }

  private NewsletterPage update(Long pageId, Consumer<NewsletterPage> action) {
    NewsletterPage page =
        newsletterPageRepository
            .findById(pageId)
            .orElseThrow(() -> new IllegalStateException("페이지를 찾을 수 없습니다. pageId=" + pageId));
    action.accept(page);
    return newsletterPageRepository.save(page);
  }

  private Newsletter findNewsletter(Long newsletterId) {
    return newsletterRepository
        .findById(newsletterId)
        .orElseThrow(
            () -> new IllegalStateException("newsletter를 찾을 수 없습니다. newsletterId=" + newsletterId));
  }

  /**
   * PDF 페이지 1장의 OCR 텍스트.
   *
   * @param pageNo 1부터 시작하는 페이지 번호
   * @param ocrText 파싱만 한 OCR 텍스트 (인식 실패 시 null)
   * @param originalText 정제한 원문 (인식 실패 시 null)
   */
  public record PageOcrText(int pageNo, String ocrText, String originalText) {
    public boolean hasOriginalText() {
      return originalText != null && !originalText.isBlank();
    }
  }
}
