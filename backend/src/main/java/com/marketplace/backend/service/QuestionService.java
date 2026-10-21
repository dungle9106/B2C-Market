package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.QuestionRequest;
import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.dto.response.QuestionResponse;
import com.marketplace.backend.entity.NotificationType;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.ProductQuestion;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ProductQuestionRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marketplace.backend.util.ReplyPlaceholders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Public product Q&A: any logged-in user asks, the product's seller answers.
@Service
@Transactional
@RequiredArgsConstructor
public class QuestionService {

    private final ProductQuestionRepository questionRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ReplySuggestService suggestService;

    @Transactional(readOnly = true)
    public PageResponse<QuestionResponse> forProduct(Long productId, int page, int size) {
        return PageResponse.of(
                questionRepository.findByProductIdAndHiddenFalseOrderByCreatedAtDesc(productId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))),
                QuestionResponse::from);
    }

    public QuestionResponse ask(QuestionRequest req) {
        Product product = productRepository.findById(req.getProductId())
                .filter(Product::isActive)
                .orElseThrow(() -> new ApiException("Sản phẩm không tồn tại", HttpStatus.NOT_FOUND));
        Long userId = CurrentUser.id();
        if (product.getShop().getSeller().getId().equals(userId)) {
            throw new ApiException("Bạn không thể đặt câu hỏi cho sản phẩm của chính mình", HttpStatus.BAD_REQUEST);
        }
        ProductQuestion q = questionRepository.save(ProductQuestion.builder()
                .product(product)
                .asker(userRepository.getReferenceById(userId))
                .question(req.getQuestion().trim())
                .build());
        notificationService.notify(product.getShop().getSeller().getId(), NotificationType.MESSAGE,
                "Câu hỏi mới về sản phẩm", "\"" + NotificationService.cut(q.getQuestion(), 120) + "\" - " + product.getName(),
                "/seller/questions", product.getThumbnailUrl());
        return QuestionResponse.from(questionRepository.findById(q.getId()).orElseThrow());
    }

    // ==== seller side ====

    /** status: UNANSWERED (oldest first), ANSWERED or HIDDEN; q searches the question and the product name. */
    @Transactional(readOnly = true)
    public PageResponse<QuestionResponse> forMyShop(String status, String q, Long productId, int page, int size) {
        Long shopId = myShop().getId();
        String st = status == null ? "UNANSWERED" : status.toUpperCase();
        if (!List.of("UNANSWERED", "ANSWERED", "HIDDEN").contains(st)) {
            throw new ApiException("Trạng thái không hợp lệ", HttpStatus.BAD_REQUEST);
        }
        String needle = q == null ? "" : q.trim().toLowerCase();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return PageResponse.of(questionRepository.findForShop(shopId, st, productId, needle, pageable), QuestionResponse::forSeller);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        Long shopId = myShop().getId();
        return Map.of("unanswered", questionRepository.countByProductShopIdAndAnswerIsNullAndHiddenFalse(shopId),
                "answered", questionRepository.countByProductShopIdAndAnswerIsNotNullAndHiddenFalse(shopId),
                "hidden", questionRepository.countByProductShopIdAndHiddenTrue(shopId));
    }

    /** Answers, or edits an existing answer (the asker is notified only the first time). */
    public QuestionResponse answer(Long questionId, String answer) {
        ProductQuestion q = owned(questionId);
        String text = ReplyPlaceholders.fill(answer.trim(), q.getAsker().getFullName(), q.getProduct().getShop().getName(), q.getProduct().getName());
        if (text.length() > 2000) throw new ApiException("Câu trả lời tối đa 2000 ký tự", HttpStatus.BAD_REQUEST);
        boolean first = q.getAnswer() == null;
        q.setAnswer(text);
        if (first) {
            q.setAnsweredAt(LocalDateTime.now());
            notificationService.notify(q.getAsker().getId(), NotificationType.MESSAGE,
                    q.getProduct().getShop().getName() + " đã trả lời câu hỏi của bạn",
                    NotificationService.cut(q.getAnswer(), 200), "/products/" + q.getProduct().getId(), q.getProduct().getThumbnailUrl());
        } else {
            q.setAnswerUpdatedAt(LocalDateTime.now());
        }
        return QuestionResponse.forSeller(questionRepository.save(q));
    }

    /** Hidden questions disappear from the product page (spam, off-topic); they can be shown again. */
    public QuestionResponse setHidden(Long questionId, boolean hidden) {
        ProductQuestion q = owned(questionId);
        q.setHidden(hidden);
        return QuestionResponse.forSeller(q);
    }

    @Transactional(readOnly = true)
    public Map<String, String> suggest(Long questionId) {
        ProductQuestion q = owned(questionId);
        return suggestService.suggest(q.getQuestion(), q.getProduct(), q.getProduct().getShop(), q.getAsker().getFullName());
    }

    private ProductQuestion owned(Long questionId) {
        ProductQuestion q = questionRepository.findById(questionId)
                .orElseThrow(() -> new ApiException("Câu hỏi không tồn tại", HttpStatus.NOT_FOUND));
        if (!q.getProduct().getShop().getId().equals(myShop().getId())) {
            throw new ApiException("Câu hỏi không tồn tại", HttpStatus.NOT_FOUND);
        }
        return q;
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }
}
