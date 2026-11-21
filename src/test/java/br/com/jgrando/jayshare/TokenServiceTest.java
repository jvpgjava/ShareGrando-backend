package br.com.jgrando.jayshare;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TokenServiceTest {
  @ParameterizedTest
  @ValueSource(ints = {32, 48, 64})
  void livekitCanVerifyEveryTokenWithHs256(int secretLength) {
    String secret = "k".repeat(secretLength);
    TokenService service = new TokenService("ws://localhost", "test-key", secret);
    JwtParser verifier = Jwts.parser()
        .verifyWith(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
        .build();
    for (String token : new String[] {
        service.livekit("room", "Host", true).token(),
        service.livekit("room", "Guest", false).token(),
        service.roomAdminToken("room", false),
        service.roomAdminToken("room", true)
    }) {
      assertEquals("HS256", verifier.parseSignedClaims(token).getHeader().getAlgorithm());
    }
  }
}
