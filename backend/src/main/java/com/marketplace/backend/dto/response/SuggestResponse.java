package com.marketplace.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

// Autocomplete dropdown under the search box.
@Data
@AllArgsConstructor
public class SuggestResponse {
    private List<String> keywords;          // matching product names, best sellers first
    private List<CategoryNode> categories;  // matching categories (children empty)
}
