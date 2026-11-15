package com.marketplace.backend.service.reviewhub;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** Rule-based review classifier of the review hub: the agreed label for each sample review. */
class ReviewTextAnalyzerTest {

    @ParameterizedTest(name = "{0} sao: {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "1|Chất vải quá tệ, áo mặc một lần đã rách đường chỉ|QUALITY",
            "1|vai mong, chat luong te, rach ngay sau khi giat|QUALITY",
            "2|Áo bị bung chỉ và chất lượng kém hơn mong đợi nhiều|QUALITY",
            "1|Sản phẩm rất tệ mong shop hoàn tiền|OTHER",
            "1|Hàng tệ quá, muốn hoàn tiền|OTHER",
            "2|Áo mặc xấu lắm|OTHER",
            "2|Giao hàng quá chậm, shipper thái độ khó chịu|DELIVERY",
            "1|Shop thái độ rất tệ|SERVICE",
            "1|Nhắn tin không trả lời, shop làm ăn tệ|SERVICE",
            "1|Shop không hoàn tiền cho tôi|SERVICE",
            "2|Giá tiền cao mà chất lượng tệ|PRICE,QUALITY",
            "2|Áo hơi chật, không vừa|SIZE",
            "2|Không đúng mô tả, màu khác hẳn hình đăng bán|WRONG_DESCRIPTION",
            "5|Áo đẹp, không lỗi gì, giao nhanh, rất hài lòng|"
    })
    void labels(int rating, String content, String expected) {
        var r = ReviewTextAnalyzer.analyze(rating, content);
        Set<String> want = expected == null ? Set.of() : new TreeSet<>(Arrays.asList(expected.split(",")));
        assertEquals(want, new TreeSet<>(r.issues()), "issues of: " + content);
    }

    @Test
    void sentiment() {
        assertEquals("POSITIVE", ReviewTextAnalyzer.analyze(5, "Áo đẹp, không lỗi gì, giao nhanh, rất hài lòng").sentiment());
        assertEquals("NEGATIVE", ReviewTextAnalyzer.analyze(1, "Sản phẩm bị lỗi ngay khi mở hộp, rất thất vọng").sentiment());
    }

    @Test
    void severeWordsAreFlaggedWhateverTheStars() {
        var r = ReviewTextAnalyzer.analyze(4, "Dùng xong bị dị ứng nổi mẩn đỏ, mọi người cẩn thận");
        assertFalse(r.severeHits().isEmpty(), "an allergy must reach the seller first");
    }

    @Test
    void negatedDefectIsPraise() {
        assertTrue(ReviewTextAnalyzer.analyze(5, "Hàng không lỗi, không rách, đóng gói kỹ").issues().isEmpty());
    }
}
