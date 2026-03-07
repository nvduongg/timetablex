package vn.edu.phenikaa.timetablex.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // Áp dụng cho mọi API
                .allowedOriginPatterns("http://localhost:5173", "http://localhost:3000", "http://*:5173", "http://127.0.0.1:5173") // Frontend + truy cập LAN (http://IP:5173)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}