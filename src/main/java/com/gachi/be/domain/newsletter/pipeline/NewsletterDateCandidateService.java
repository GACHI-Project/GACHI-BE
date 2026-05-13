package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.NewsletterDateCandidate;
import com.gachi.be.domain.newsletter.pipeline.NewsletterDateCandidateExtractor.ExtractedDateCandidate;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsletterDateCandidateService {

  private final NewsletterDateCandidateExtractor dateCandidateExtractor;
  private final NewsletterRepository newsletterRepository;
  private final Clock clock;

  /** 가정통신문 원문에서 날짜 후보를 추출한 뒤 newsletter JSON 컬럼에 교체 저장합니다. */
  @Transactional
  public void extractAndReplace(Long newsletterId, String sourceText) {
    LocalDate referenceDate = LocalDate.now(clock);
    List<ExtractedDateCandidate> extracted =
        dateCandidateExtractor.extract(sourceText, referenceDate);

    List<NewsletterDateCandidate> candidates =
        extracted.stream()
            .map(
                candidate ->
                    new NewsletterDateCandidate(
                        candidate.originalText(),
                        candidate.normalizedDate(),
                        candidate.startOffset(),
                        candidate.endOffset(),
                        candidate.extractionType()))
            .toList();

    Newsletter newsletter =
        newsletterRepository
            .findById(newsletterId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Newsletter not found during date extraction: " + newsletterId));
    newsletter.replaceDateCandidates(candidates);
    newsletterRepository.save(newsletter);

    log.debug("[NewsletterDate] 날짜 후보 {}건 저장 완료. newsletterId={}", candidates.size(), newsletterId);
  }
}
