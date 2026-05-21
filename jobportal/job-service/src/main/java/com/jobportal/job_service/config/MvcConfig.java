package com.jobportal.job_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve company logo images uploaded to photos/company/
        Path photosPath = Paths.get("photos/company");
        registry.addResourceHandler("/photos/company/**")
                .addResourceLocations("file:" + photosPath.toAbsolutePath() + "/");
    }
}
