package br.com.jgrando.jayshare;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  @GetMapping("/internal/health")
  public ResponseEntity<Void> health() {
    return ResponseEntity.noContent().build();
  }
}
