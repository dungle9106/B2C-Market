package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.CategoryNode;
import com.marketplace.backend.entity.Category;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.CategoryRepository;
import com.marketplace.backend.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

// The category table is small (tens of rows), so it's simplest to load it
// whole and build the tree / descendant sets in memory.
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // public tree: hidden categories (and the sub-categories of a hidden one) are left out; admin order
    @org.springframework.cache.annotation.Cacheable(com.marketplace.backend.config.CacheConfig.CATEGORY_TREE)
    public List<CategoryNode> tree() {
        Map<Long, List<Category>> byParent = new HashMap<>();
        ordered().stream().filter(Category::isActive)
                .forEach(c -> byParent.computeIfAbsent(c.parentIdForJson(), k -> new ArrayList<>()).add(c));
        return build(byParent, null);
    }

    /** Visible categories in the admin's order: sortOrder, then id. */
    public List<Category> ordered() {
        return categoryRepository.findAll(Sort.by("sortOrder").and(Sort.by("id")));
    }

    private List<CategoryNode> build(Map<Long, List<Category>> byParent, Long parentId) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(c -> new CategoryNode(c.getId(), c.getName(), parentId, build(byParent, c.getId())))
                .toList();
    }

    // one category + its ancestors (for breadcrumbs) + its direct children
    public Map<String, Object> detail(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException("Danh mục không tồn tại", HttpStatus.NOT_FOUND));
        List<CategoryNode> path = new ArrayList<>();
        for (Category c = category; c != null; c = c.getParent()) {
            path.add(0, new CategoryNode(c.getId(), c.getName(), c.parentIdForJson(), List.of()));
        }
        List<CategoryNode> children = categoryRepository.findByParentId(id).stream()
                .filter(Category::isActive)
                .sorted(Comparator.comparing(Category::getSortOrder).thenComparing(Category::getId))
                .map(c -> new CategoryNode(c.getId(), c.getName(), id, List.of()))
                .toList();
        return Map.of("id", category.getId(), "name", category.getName(), "path", path, "children", children);
    }

    // the category itself plus every sub-category below it
    public Set<Long> withDescendants(Long id) {
        Map<Long, List<Long>> childrenOf = new HashMap<>();
        categoryRepository.findAll().forEach(c ->
                childrenOf.computeIfAbsent(c.parentIdForJson(), k -> new ArrayList<>()).add(c.getId()));
        Set<Long> result = new LinkedHashSet<>();
        Deque<Long> todo = new ArrayDeque<>(List.of(id));
        while (!todo.isEmpty()) {
            Long next = todo.pop();
            if (result.add(next)) todo.addAll(childrenOf.getOrDefault(next, List.of()));
        }
        return result;
    }

    public List<CategoryNode> search(String normalizedKeyword, int limit) {
        return ordered().stream().filter(Category::isActive)
                .filter(c -> TextNormalizer.normalize(c.getName()).contains(normalizedKeyword))
                .limit(limit)
                .map(c -> new CategoryNode(c.getId(), c.getName(), c.parentIdForJson(), List.of()))
                .toList();
    }
}
