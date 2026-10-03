package com.gachi.be.domain.newsletter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.gachi.be.domain.newsletter.entity.NewsletterPageBlock;
import com.gachi.be.domain.newsletter.pipeline.OcrBlockGrouper.OcrWord;
import java.util.List;
import org.junit.jupiter.api.Test;

// 오버레이 블록 묶기 테스트 (1000x1000 이미지 기준 좌표)
class OcrBlockGrouperTest {

    private final OcrBlockGrouper grouper = new OcrBlockGrouper();

    @Test
    void groupsTitleParagraphListItemsAndTableCells() {
        List<OcrWord> words =
            List.of(
                // 제목 (큰 글씨)
                word("2026학년도", 100, 50, 300, 100),
                word("수영", 310, 50, 400, 100),
                word("안내", 410, 50, 500, 100),
                // 1번 항목: 두 줄짜리 문단
                word("1.", 50, 150, 70, 180),
                word("일시:", 75, 150, 140, 180),
                word("6월", 145, 150, 190, 180),
                word("15일", 195, 150, 250, 180),
                word("총", 75, 185, 100, 215),
                word("4일", 105, 185, 150, 215),
                // 2번 항목: 항목 기호로 시작하므로 새 블록
                word("2.", 50, 240, 70, 270),
                word("장소:", 75, 240, 140, 270),
                // 표: 같은 줄이지만 간격이 넓어 다른 칸
                word("1교시", 50, 320, 120, 350),
                word("국어", 400, 320, 460, 350));

        List<NewsletterPageBlock> blocks = grouper.groupWords(words, 1000, 1000);

        assertThat(blocks)
            .extracting(NewsletterPageBlock::originalText)
            .containsExactly("2026학년도 수영 안내", "1. 일시: 6월 15일 총 4일", "2. 장소:", "1교시", "국어");
        assertThat(blocks).extracting(NewsletterPageBlock::blockNo).containsExactly(1, 2, 3, 4, 5);
        assertThat(blocks).allSatisfy(block -> assertThat(block.translatedText()).isNull());
    }

    @Test
    void convertsCoordinatesToZeroToOneRatio() {
        List<NewsletterPageBlock> blocks =
            grouper.groupWords(List.of(word("안내", 100, 50, 500, 100)), 1000, 1000);

        NewsletterPageBlock.BlockBox box = blocks.get(0).box();
        assertThat(box.x()).isEqualTo(0.1);
        assertThat(box.y()).isEqualTo(0.05);
        assertThat(box.width()).isEqualTo(0.4);
        assertThat(box.height()).isEqualTo(0.05);
    }

    @Test
    void returnsEmptyWhenNoWordsOrInvalidImageSize() {
        assertThat(grouper.groupWords(List.of(), 1000, 1000)).isEmpty();
        assertThat(grouper.groupWords(List.of(word("안내", 0, 0, 10, 10)), 0, 1000)).isEmpty();
    }

    private OcrWord word(String text, double minX, double minY, double maxX, double maxY) {
        return new OcrWord(text, minX, minY, maxX, maxY);
    }
}
