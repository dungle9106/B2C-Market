package com.marketplace.backend.service;

import com.marketplace.backend.entity.Category;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.CategoryRepository;
import com.marketplace.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * "Quan ly danh muc chuan toan san": the 2-level category tree (category > sub-category).
 * Create, rename, move, reorder, hide/show. A category is deleted only when it is empty: its products
 * are first moved to another category (so no product ends up without one), and a category with
 * sub-categories must be emptied first.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CategoryAdminService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final AdminAuditService auditService;

    /** The whole tree, hidden categories included, with product counts (a parent counts its sub-categories too). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> tree() {
        Map<Long, Long> counts = new HashMap<>();
        productRepository.countPerCategory().forEach(r -> counts.put((Long) r[0], ((Number) r[1]).longValue()));
        List<Category> all = categoryService.ordered();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Category top : all) {
            if (top.getParent() != null) continue;
            List<Map<String, Object>> children = all.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId().equals(top.getId()))
                    .map(c -> node(c, counts.getOrDefault(c.getId(), 0L), counts.getOrDefault(c.getId(), 0L), List.of())).toList();
            long own = counts.getOrDefault(top.getId(), 0L);
            long total = own + children.stream().mapToLong(c -> (Long) c.get("productCount")).sum();
            out.add(node(top, own, total, children));
        }
        return out;
    }

    @org.springframework.cache.annotation.CacheEvict(cacheNames = com.marketplace.backend.config.CacheConfig.CATEGORY_TREE, allEntries = true)
    public Map<String, Object> create(String name, Long parentId) {
        Category parent = parentId == null ? null : topLevel(parentId);
        String n = name(name, parent, null);
        int order = categoryService.ordered().stream().filter(c -> Objects.equals(c.parentIdForJson(), parentId))
                .mapToInt(Category::getSortOrder).max().orElse(-1) + 1;
        Category c = categoryRepository.save(Category.builder().name(n).parent(parent).sortOrder(order).active(true).build());
        auditService.log("CREATE_CATEGORY", "CATEGORY", c.getId(), n, parent == null ? "Danh mục cấp 1" : "Thuộc " + parent.getName());
        return node(c, 0, 0, List.of());
    }

    /** Rename and/or move (parentId null = top level). A category with sub-categories stays top level. */
    @org.springframework.cache.annotation.CacheEvict(cacheNames = com.marketplace.backend.config.CacheConfig.CATEGORY_TREE, allEntries = true)
    public Map<String, Object> update(Long id, String name, Long parentId) {
        Category c = find(id);
        Category parent = parentId == null ? null : topLevel(parentId);
        if (parent != null && parent.getId().equals(id)) throw bad("Danh mục không thể là danh mục cha của chính nó");
        if (parent != null && !categoryRepository.findByParentId(id).isEmpty()) {
            throw bad("Danh mục đang có danh mục con nên không thể chuyển thành danh mục con (chỉ hỗ trợ 2 cấp)");
        }
        String before = c.getName() + (c.getParent() == null ? "" : " (thuộc " + c.getParent().getName() + ")");
        c.setName(name(name, parent, id));
        if (!Objects.equals(c.parentIdForJson(), parentId)) {
            c.setParent(parent);
            c.setSortOrder(categoryService.ordered().stream().filter(x -> Objects.equals(x.parentIdForJson(), parentId))
                    .mapToInt(Category::getSortOrder).max().orElse(-1) + 1);
        }
        auditService.log("UPDATE_CATEGORY", "CATEGORY", c.getId(), c.getName(), before + " → " + c.getName()
                + (parent == null ? "" : " (thuộc " + parent.getName() + ")"));
        return node(c, 0, 0, List.of());
    }

    /** Hidden categories leave the buyer menu and the seller's picker; their products stay listed. */
    @org.springframework.cache.annotation.CacheEvict(cacheNames = com.marketplace.backend.config.CacheConfig.CATEGORY_TREE, allEntries = true)
    public Map<String, Object> toggle(Long id) {
        Category c = find(id);
        c.setActive(!c.isActive());
        auditService.log(c.isActive() ? "SHOW_CATEGORY" : "HIDE_CATEGORY", "CATEGORY", c.getId(), c.getName(),
                c.isActive() ? "Hiện danh mục" : "Ẩn danh mục");
        return node(c, 0, 0, List.of());
    }

    /** New order of the siblings of one parent (parentId null = top level): ids in display order. */
    @org.springframework.cache.annotation.CacheEvict(cacheNames = com.marketplace.backend.config.CacheConfig.CATEGORY_TREE, allEntries = true)
    public void reorder(Long parentId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw bad("Danh sách trống");
        Map<Long, Category> siblings = new HashMap<>();
        categoryService.ordered().stream().filter(c -> Objects.equals(c.parentIdForJson(), parentId)).forEach(c -> siblings.put(c.getId(), c));
        if (!siblings.keySet().equals(new HashSet<>(ids)) || ids.size() != siblings.size()) {
            throw bad("Danh sách phải gồm đúng các danh mục cùng cấp");
        }
        for (int i = 0; i < ids.size(); i++) siblings.get(ids.get(i)).setSortOrder(i);
    }

    /** moveTo: where its products go (required when it has products). */
    @org.springframework.cache.annotation.CacheEvict(cacheNames = com.marketplace.backend.config.CacheConfig.CATEGORY_TREE, allEntries = true)
    public void delete(Long id, Long moveTo) {
        Category c = find(id);
        if (!categoryRepository.findByParentId(id).isEmpty()) throw bad("Hãy xóa hoặc chuyển các danh mục con trước");
        long products = productRepository.countPerCategory().stream().filter(r -> id.equals(r[0]))
                .findFirst().map(r -> ((Number) r[1]).longValue()).orElse(0L);
        String detail = "Xóa danh mục";
        if (products > 0) {
            if (moveTo == null) throw bad("Danh mục đang có " + products + " sản phẩm, hãy chọn danh mục để chuyển sản phẩm sang");
            if (moveTo.equals(id)) throw bad("Hãy chọn một danh mục khác");
            Category target = find(moveTo);
            productRepository.moveCategory(c, target);
            detail = "Chuyển " + products + " sản phẩm sang \"" + target.getName() + "\" rồi xóa";
        }
        categoryRepository.delete(c);
        auditService.log("DELETE_CATEGORY", "CATEGORY", id, c.getName(), detail);
    }

    // ---- helpers ----

    // 2 levels: a parent must itself be top level
    private Category topLevel(Long parentId) {
        Category p = find(parentId);
        if (p.getParent() != null) throw bad("Chỉ hỗ trợ 2 cấp danh mục: hãy chọn một danh mục cấp 1 làm danh mục cha");
        return p;
    }

    // 2-60 characters, unique among its siblings (ignoring case)
    private String name(String name, Category parent, Long selfId) {
        String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (n.length() < 2 || n.length() > 60) throw bad("Tên danh mục từ 2 đến 60 ký tự");
        Long parentId = parent == null ? null : parent.getId();
        boolean taken = categoryService.ordered().stream().anyMatch(c -> Objects.equals(c.parentIdForJson(), parentId)
                && !c.getId().equals(selfId) && c.getName().equalsIgnoreCase(n));
        if (taken) throw bad("Đã có danh mục \"" + n + "\" ở cùng cấp");
        return n;
    }

    private Map<String, Object> node(Category c, long own, long total, List<Map<String, Object>> children) {
        Map<String, Object> m = AdminUserService.map("id", c.getId(), "name", c.getName(), "parentId", c.parentIdForJson(),
                "active", c.isActive(), "sortOrder", c.getSortOrder(), "ownProductCount", own, "productCount", total);
        m.put("children", children);
        return m;
    }

    private Category find(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ApiException("Danh mục không tồn tại", HttpStatus.NOT_FOUND));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
