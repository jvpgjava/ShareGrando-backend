package br.com.jgrando.screengrando;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
  private final String livekitUrl;
  private final String livekitKey;
  private final SecretKey livekitSecret;

  public TokenService(
      @Value("${screengrando.livekit-url}") String livekitUrl,
      @Value("${screengrando.livekit-api-key}") String livekitKey,
      @Value("${screengrando.livekit-api-secret}") String livekitSecret) {
    this.livekitUrl = livekitUrl;
    this.livekitKey = livekitKey;
    this.livekitSecret = Keys.hmacShaKeyFor(livekitSecret.getBytes(StandardCharsets.UTF_8));
  }

  public String roomAdminToken(String room, boolean closeRoom) {
    Map<String, Object> grant = closeRoom
        ? Map.of("roomCreate", true)
        : Map.of("roomAdmin", true, "room", room);
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(livekitKey)
        .subject("screengrando-server")
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(60)))
        .claim("video", grant)
        .signWith(livekitSecret)
        .compact();
  }

  public LivekitToken livekit(String room, String name, boolean publish) {
    Instant expiry = Instant.now().plusSeconds(60 * 60);
    String identity = publish ? "publisher" : "viewer-" + UUID.randomUUID();
    Map<String, Object> grant = Map.of(
        "roomJoin", true,
        "room", room,
        "canPublish", publish,
        "canSubscribe", true,
        "canPublishData", false);
    String token = Jwts.builder()
        .issuer(livekitKey)
        .subject(identity)
        .issuedAt(Date.from(Instant.now()))
        .expiration(Date.from(expiry))
        .claim("name", name)
        .claim("video", grant)
        .signWith(livekitSecret)
        .compact();
    return new LivekitToken(livekitUrl, token, expiry.toString());
  }

  public record LivekitToken(String livekitUrl, String token, String expiresAt) {}
}
