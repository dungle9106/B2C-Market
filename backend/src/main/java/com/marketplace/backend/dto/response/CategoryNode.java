package com.marketplace.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor   // read back from the Redis cache
public class CategoryNode {
    private Long id;
    private String name;
    private Long parentId;
    private List<CategoryNode> children;
}
