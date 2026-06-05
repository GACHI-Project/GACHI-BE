package com.gachi.be.domain.newsletter.repository;

import com.gachi.be.domain.newsletter.entity.ConversationTopic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationTopicRepository extends JpaRepository<ConversationTopic, Long> {

    List<ConversationTopic> findAllByNewsletterIdOrderByIdAsc(Long newsletterId);
}
