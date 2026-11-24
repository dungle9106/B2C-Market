package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.ChatbotResponse;
import com.marketplace.backend.entity.Category;
import com.marketplace.backend.entity.ChatHistory;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.repository.ChatHistoryRepository;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.service.search.SemanticSearchService;
import com.marketplace.backend.util.TextNormalizer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The rule-based assistant (no AI key) on the ways buyers type: the failures scripts/chatbot-eval.mjs found. */
class ChatbotServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final ChatHistoryRepository history = mock(ChatHistoryRepository.class);
    private final AIService ai = mock(AIService.class);
    private final ShoppingAgent agent = mock(ShoppingAgent.class);
    private final ChatbotService bot = new ChatbotService(products, mock(OrderRepository.class), history, mock(UserRepository.class),
            ai, mock(PlatformTransactionManager.class), mock(SemanticSearchService.class), agent);
    private final List<Product> catalogue = new ArrayList<>();

    ChatbotServiceTest() {
        Shop shop = Shop.builder().id(1L).name("Shop").active(true).build();
        Category cat = Category.builder().id(1L).name("Cat").build();
        long id = 1;
        for (Object[] p : new Object[][]{{"Điện thoại Realme C35", 3_749_000}, {"Điện thoại iPhone X", 22_499_000},
                {"Đèn livestream kẹp điện thoại", 374_000}, {"Son thỏi màu đỏ", 324_000}, {"Tai nghe bluetooth chống ồn", 690_000},
                {"Điện thoại Samsung Galaxy S10", 17_499_000}, {"Sữa tươi tiệt trùng", 30_000}}) {
            String name = (String) p[0];
            catalogue.add(Product.builder().id(id++).name(name).searchText(TextNormalizer.normalize(name))
                    .price(BigDecimal.valueOf((Integer) p[1])).stock(10).active(true).shop(shop).category(cat).soldCount(0).build());
        }
        when(products.activeSearchTexts()).thenReturn(catalogue.stream().map(Product::getSearchText).toList());
        when(products.findForChatbot(anyString(), any())).thenAnswer(inv -> {
            String w = ((String) inv.getArgument(0)).replace("%", "");
            return catalogue.stream().filter(p -> p.getSearchText().contains(w)).toList();
        });
    }

    private ChatbotResponse say(String message, String... before) {
        List<ChatHistory> turns = new ArrayList<>();
        for (String b : before) turns.add(0, ChatHistory.builder().message(b).response("...").build());   // newest first
        when(history.findTop10ByUserIdOrderByCreatedAtDesc(any())).thenReturn(turns);
        try (MockedStatic<CurrentUser> me = mockStatic(CurrentUser.class)) {
            me.when(CurrentUser::id).thenReturn(1L);
            return bot.chat(message);
        }
    }

    private static List<String> names(ChatbotResponse r) {
        return r.getProducts().stream().map(p -> p.getName()).toList();
    }

    @Test
    void followUpKeepsTheSubjectAndTakesTheNewPrice() {
        ChatbotResponse r = say("dưới 5 triệu thôi", "tìm điện thoại");
        assertFalse(r.getProducts().isEmpty(), r.getReply());
        assertTrue(names(r).contains("Điện thoại Realme C35"), names(r).toString());
        assertTrue(r.getProducts().stream().allMatch(p -> p.getPrice().compareTo(BigDecimal.valueOf(5_000_000)) <= 0));
    }

    @Test
    void abbreviationsAndTypos() {
        assertEquals("Tai nghe bluetooth chống ồn", names(say("có tn bluetooth ko shop")).get(0));
        assertTrue(names(say("dien thaoi samsug")).get(0).contains("Samsung"));
        assertEquals("Điện thoại Realme C35", names(say("mk cần đt dưới 5tr")).get(0));
    }

    @Test
    void nothingUnrelatedForSmallTalkOrMissingProducts() {
        assertTrue(say("thời tiết hôm nay thế nào").getProducts().isEmpty());
        assertTrue(say("có bán tủ lạnh samsung không").getProducts().isEmpty());
    }

    @Test
    void changeOfMind() {
        assertTrue(names(say("thôi lấy iphone", "có điện thoại samsung không")).get(0).contains("iPhone"));
    }
}
