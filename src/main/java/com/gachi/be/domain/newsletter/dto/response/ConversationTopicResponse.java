package com.gachi.be.domain.newsletter.dto.response;

import com.gachi.be.domain.newsletter.entity.ConversationTopic;
import java.util.List;

public record ConversationTopicResponse(List<TopicItem> topics) {

    public record TopicItem(Long topicId, String topic) {}

    public static ConversationTopicResponse from(List<ConversationTopic> entities) {
        List<TopicItem> items =
            entities.stream().map(e -> new TopicItem(e.getId(), e.getTopic())).toList();
        return new ConversationTopicResponse(items);
    }
}
