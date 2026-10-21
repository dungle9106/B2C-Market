package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.AddToCartRequest;
import com.marketplace.backend.dto.response.CartItemResponse;
import com.marketplace.backend.entity.CartItem;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.ProductVariant;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.CartItemRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ProductVariantRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

// class-level: open-in-view is off, so mapping lazy associations to DTOs
// must happen inside the transaction
@Service
@Transactional
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;

    public List<CartItemResponse> getCart() {
        return cartItemRepository.findByUserId(CurrentUser.id()).stream()
                .map(CartItemResponse::from).collect(Collectors.toList());
    }

    public CartItemResponse addItem(AddToCartRequest req) {
        Long userId = CurrentUser.id();
        Product product = productRepository.findById(req.getProductId())
                .filter(Product::isActive)
                .orElseThrow(() -> new ApiException("Sản phẩm không tồn tại", HttpStatus.NOT_FOUND));

        ProductVariant variant = null;
        if (req.getVariantId() != null) {
            variant = variantRepository.findById(req.getVariantId())
                    .filter(v -> v.getProduct().getId().equals(product.getId()))
                    .orElseThrow(() -> new ApiException("Phân loại sản phẩm không tồn tại", HttpStatus.NOT_FOUND));
        } else if (!product.getVariants().isEmpty()) {
            throw new ApiException("Vui lòng chọn phân loại hàng", HttpStatus.BAD_REQUEST);
        }

        Long variantId = variant != null ? variant.getId() : null;
        CartItem item = cartItemRepository.findByUserIdAndProductIdAndVariantId(userId, product.getId(), variantId)
                .orElseGet(() -> {
                    User user = userRepository.getReferenceById(userId);
                    return CartItem.builder().user(user).product(product).variant(null).quantity(0).build();
                });

        int available = variant != null ? variant.getStock() : product.getStock();
        if (item.getQuantity() + req.getQuantity() > available) {
            throw new ApiException(available <= 0 ? "Sản phẩm đã hết hàng"
                    : "Số lượng vượt quá tồn kho (còn " + available + " sản phẩm)", HttpStatus.BAD_REQUEST);
        }

        item.setVariant(variant);
        item.setQuantity(item.getQuantity() + req.getQuantity());
        return CartItemResponse.from(cartItemRepository.save(item));
    }

    public CartItemResponse updateQuantity(Long cartItemId, int quantity) {
        CartItem item = ownedItemOrThrow(cartItemId);
        if (quantity <= 0) {
            cartItemRepository.delete(item);
            return null;
        }
        int available = item.getVariant() != null ? item.getVariant().getStock() : item.getProduct().getStock();
        if (quantity > available) {
            throw new ApiException(available <= 0 ? "Sản phẩm đã hết hàng"
                    : "Chỉ còn " + available + " sản phẩm", HttpStatus.BAD_REQUEST);
        }
        item.setQuantity(quantity);
        return CartItemResponse.from(cartItemRepository.save(item));
    }

    // "Xoa" for the selected rows of the cart page
    public void removeItems(List<Long> cartItemIds) {
        cartItemIds.forEach(this::removeItem);
    }

    public void removeItem(Long cartItemId) {
        CartItem item = ownedItemOrThrow(cartItemId);
        cartItemRepository.delete(item);
    }

    private CartItem ownedItemOrThrow(Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ApiException("Sản phẩm trong giỏ hàng không tồn tại", HttpStatus.NOT_FOUND));
        if (!item.getUser().getId().equals(CurrentUser.id())) {
            throw new ApiException("Bạn không có quyền thao tác giỏ hàng này", HttpStatus.FORBIDDEN);
        }
        return item;
    }
}
