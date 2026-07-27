package com.gachi.be.domain.newsletter.repository;

import com.gachi.be.domain.newsletter.entity.ConversationTopic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationTopicRepository extends JpaRepository<ConversationTopic, Long> {

  List<ConversationTopic> findAllByNewsletterIdOrderByIdAsc(Long newsletterId);

    /** 특정 가정통신문의 대화 주제 전체 삭제 (재분석 시 사용). */
    void deleteByNewsletterId(Long newsletterId);
}
