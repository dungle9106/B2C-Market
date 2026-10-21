package com.marketplace.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// returned as JSON by the category endpoints: expose parentId instead of the lazy parent
@Entity
@Table(name = "categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent; // null = top-level category

    // order among its siblings (admin "Danh muc nganh hang"); ties by id
    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer sortOrder = 0;

    // hidden categories disappear from the buyer menu and the seller's category picker
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;

    // reading the id of a Hibernate proxy doesn't initialize it, so this is safe outside a session.
    // Deliberately not named getParentId(): Spring Data would then treat "parentId" as a
    // property and break the derived query CategoryRepository#findByParentId.
    @JsonProperty("parentId")
    public Long parentIdForJson() {
        return parent != null ? parent.getId() : null;
    }
}
