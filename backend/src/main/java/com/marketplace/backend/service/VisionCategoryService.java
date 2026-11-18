package com.marketplace.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.entity.Category;
import com.marketplace.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * "Gợi ý danh mục từ ảnh" (computer vision): the seller uploads the cover photo, the AI service's model
 * (review-ai-service/app/vision.py: MobileNetV2 features + a classifier trained on the marketplace's product photos)
 * says which categories it looks like. Only a suggestion: the seller picks the category.
 * Empty when the AI service is off or its vision model is not trained.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisionCategoryService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final WebClient webClient;
    private final UploadService uploadService;
    private final CategoryRepository categoryRepository;

    @Value("${app.review-ai.url:}")
    private String serviceUrl;

    /** imageUrl: a Shoppi upload ("/uploads/...") or a public http(s) picture. */
    public Map<String, Object> suggest(String imageUrl) {
        if (serviceUrl == null || serviceUrl.isBlank() || imageUrl == null || imageUrl.isBlank()) return Map.of("available", false, "items", List.of());
        try {
            byte[] image = read(imageUrl.trim());
            if (image == null || image.length == 0 || image.length > MAX_BYTES) return Map.of("available", true, "items", List.of());
            String raw = webClient.post().uri(serviceUrl.replaceAll("/+$", "") + "/vision/category")
                    .bodyValue(Map.of("image", Base64.getEncoder().encodeToString(image), "k", 3))
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(20));
            JsonNode n = JSON.readTree(raw);
            if (!n.path("trained").asBoolean(false)) return Map.of("available", false, "items", List.of());
            List<Long> ids = new ArrayList<>();
            n.path("categories").forEach(c -> ids.add(c.path("id").asLong()));
            Map<Long, Category> found = new HashMap<>();
            categoryRepository.findAllById(ids).forEach(c -> found.put(c.getId(), c));
            // parent names by id (the lazy parent is not loaded outside a transaction)
            Map<Long, String> parents = new HashMap<>();
            categoryRepository.findAllById(found.values().stream().map(Category::parentIdForJson).filter(Objects::nonNull).toList())
                    .forEach(c -> parents.put(c.getId(), c.getName()));
            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode c : n.path("categories")) {
                Category cat = found.get(c.path("id").asLong());
                double p = c.path("probability").asDouble();
                if (cat == null || (!items.isEmpty() && p < 0.05)) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("categoryId", cat.getId());
                m.put("name", cat.getName());
                String parent = parents.get(cat.parentIdForJson());
                m.put("path", parent == null ? cat.getName() : parent + " > " + cat.getName());
                m.put("probability", p);
                items.add(m);
            }
            return Map.of("available", true, "items", items);
        } catch (Exception e) {
            log.debug("Vision category suggestion unavailable: {}", e.getMessage());
            return Map.of("available", false, "items", List.of());
        }
    }

    private byte[] read(String url) throws Exception {
        if (url.startsWith("/uploads/")) {
            Path file = uploadService.root().resolve(url.substring("/uploads/".length())).normalize();
            if (!file.startsWith(uploadService.root()) || !Files.isRegularFile(file)) return null;
            return Files.readAllBytes(file);
        }
        if (url.startsWith("https://") || url.startsWith("http://")) {
            return webClient.get().uri(url).retrieve().bodyToMono(byte[].class).block(Duration.ofSeconds(10));
        }
        return null;
    }
}
