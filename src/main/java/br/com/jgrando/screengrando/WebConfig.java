package br.com.jgrando.screengrando;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final String frontendBaseUrl;

  public WebConfig(@Value("${screengrando.frontend-base-url}") String frontendBaseUrl) {
    this.frontendBaseUrl = frontendBaseUrl;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins("http://localhost:4200", "http://127.0.0.1:4200", frontendBaseUrl)
        .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
        .allowedHeaders("Content-Type", "X-Session-Secret")
        .allowCredentials(false);
  }
}
