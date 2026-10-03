package com.gachi.be.domain.newsletter.repository;

import com.gachi.be.domain.newsletter.entity.NewsletterPage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 가정통신문 페이지(newsletter_page) 테이블 JPA 레포지토리. */
public interface NewsletterPageRepository extends JpaRepository<NewsletterPage, Long> {

    /** 문서의 전체 페이지를 페이지 순서대로 조회한다. */
    List<NewsletterPage> findAllByNewsletterIdOrderByPageNoAsc(Long newsletterId);

    /** 문서의 특정 페이지를 조회한다. */
    Optional<NewsletterPage> findByNewsletterIdAndPageNo(Long newsletterId, Integer pageNo);

    /** 문서에 페이지 레코드가 하나라도 있는지. (기능 도입 전 문서/첫 실행 여부 판단) */
    boolean existsByNewsletterId(Long newsletterId);
}
