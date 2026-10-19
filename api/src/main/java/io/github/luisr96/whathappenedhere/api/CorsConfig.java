package io.github.luisr96.whathappenedhere.api;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross-origin access for a frontend served from another origin (GitHub Pages calling the API on
 * Fly.io). Origins come from {@code app.cors.allowed-origins} (comma-separated, exact matches);
 * with none configured the API stays same-origin only, as in development behind the Vite proxy.
 * The API is read-only and public, so only GET is allowed, without credentials.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins.stream().map(String::strip).filter(o -> !o.isEmpty()).toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.isEmpty()) {
            return;
        }
        for (String path : List.of("/events", "/events/**", "/places/**", "/stats/**")) {
            registry.addMapping(path)
                    .allowedOrigins(allowedOrigins.toArray(String[]::new))
                    .allowedMethods("GET", "HEAD")
                    .allowCredentials(false);
        }
    }
}
