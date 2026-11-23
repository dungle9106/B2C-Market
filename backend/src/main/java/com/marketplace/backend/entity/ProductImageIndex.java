package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** "Tim bang hinh anh": the visual features of a product's cover image (ImageFeatures + MobileNetV2), refreshed when the image changes. */
@Entity
@Table(name = "product_image_index")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageIndex {

    @Id
    private Long productId;

    @Column(nullable = false, length = 1000)
    private String imageUrl;          // the image these features came from

    @Column(columnDefinition = "text")
    private String histogram;         // 64 comma-separated weights; null when the image could not be read

    private Long dhash;

    @Column(columnDefinition = "text")
    private String embedding;         // MobileNetV2 vector (base64 float32, VisionCategoryService); null until the AI service has seen it

    @Column(nullable = false)
    private LocalDateTime indexedAt;
}
