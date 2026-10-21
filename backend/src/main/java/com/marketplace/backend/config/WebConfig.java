package com.marketplace.backend.config;

import com.marketplace.backend.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Serves files saved by UploadService at /uploads/<name>.
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final UploadService uploadService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadService.root().toUri().toString())
                .setCachePeriod(60 * 60 * 24 * 30); // names are random, so content never changes
    }
}
