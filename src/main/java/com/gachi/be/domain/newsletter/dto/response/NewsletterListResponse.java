package com.gachi.be.domain.newsletter.dto.response;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 가정통신문 목록 조회 API 응답 DTO.*/
public record NewsletterListResponse(
    List<NewsletterItem> newsletters,
    int totalCount) {

    public record NewsletterItem(
        Long newsletterId,
        String title,
        String childName,
        Integer childGrade,
        String childColor,
        boolean isCalendarRegistered,
        String createdAt) {

        public static NewsletterItem from(Newsletter newsletter, boolean calendarRegistered) {
            String createdAtStr =
                newsletter.getCreatedAt() == null
                    ? null
                    : newsletter
                    .getCreatedAt()
                    .withOffsetSameInstant(ZoneOffset.ofHours(9))
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            return new NewsletterItem(
                newsletter.getId(),
                newsletter.getTitle(),
                newsletter.getChildName(),
                newsletter.getChildGrade(),
                newsletter.getChildColor(),
                calendarRegistered,
                createdAtStr);
        }
    }
}
