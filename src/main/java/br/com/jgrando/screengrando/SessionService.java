package br.com.jgrando.screengrando;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SessionService {
  public record Session(
      String code,
      String title,
      String publisherSecret,
      String pinSalt,
      String pinHash,
      Instant createdAt,
      String status) {}

  private static final SecureRandom RANDOM = new SecureRandom();
  private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
  private final String frontendBaseUrl;
  private final String livekitUrl;

  public SessionService(
      @Value("${screengrando.frontend-base-url}") String frontendBaseUrl,
      @Value("${screengrando.livekit-url}") String livekitUrl) {
    this.frontendBaseUrl = frontendBaseUrl;
    this.livekitUrl = livekitUrl;
  }

  public Session create(String requestedTitle, String pin) {
    Instant now = Instant.now();
    byte[] secret = new byte[32];
    RANDOM.nextBytes(secret);
    byte[] salt = new byte[16];
    String code = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    String title = requestedTitle == null || requestedTitle.isBlank()
        ? "Compartilhamento de tela"
        : requestedTitle.trim();
    String publisherSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    String pinSalt = null;
    if (pin != null && !pin.isBlank()) {
      RANDOM.nextBytes(salt);
      pinSalt = Base64.getEncoder().encodeToString(salt);
    }
    Session session = new Session(
        code,
        title,
        publisherSecret,
        pinSalt,
        pinSalt == null ? null : hashPin(publisherSecret, pinSalt, pin),
        now,
        "WAITING");
    sessions.put(code, session);
    return session;
  }

  public Session get(String code) {
    Session session = sessions.get(code);
    if (session == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sessão não encontrada.");
    }
    return session;
  }

  public Session markLive(String code, String suppliedSecret) {
    Session session = requirePublisher(code, suppliedSecret);
    if (session.status().equals("ENDED")) {
      throw new ResponseStatusException(HttpStatus.GONE, "Esta sessão foi encerrada.");
    }
    if (session.status().equals("WAITING")) {
      session = withStatus(session, "LIVE");
      sessions.put(code, session);
    }
    return session;
  }

  public Session validatePublisher(String code, String suppliedSecret) {
    return requirePublisher(code, suppliedSecret);
  }

  public boolean verifyViewerPin(Session session, String suppliedPin) {
    if (session.pinHash() == null) {
      return true;
    }
    if (suppliedPin == null || suppliedPin.isBlank()) {
      return false;
    }
    String suppliedHash = hashPin(session.publisherSecret(), session.pinSalt(), suppliedPin);
    return MessageDigest.isEqual(
        session.pinHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
        suppliedHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
  }

  public Session end(String code, String suppliedSecret) {
    Session session = requirePublisher(code, suppliedSecret);
    Session ended = withStatus(session, "ENDED");
    sessions.put(code, ended);
    return ended;
  }

  public String viewerUrl(Session session) {
    return frontendBaseUrl.replaceAll("/$", "") + "/w/" + session.code();
  }

  public String livekitUrl() {
    return livekitUrl;
  }

  private Session requirePublisher(String code, String suppliedSecret) {
    Session session = get(code);
    if (suppliedSecret == null || !MessageDigest.isEqual(
        session.publisherSecret().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
        suppliedSecret.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chave de controle da sala inválida.");
    }
    return session;
  }

  private Session withStatus(Session session, String status) {
    return new Session(
        session.code(), session.title(), session.publisherSecret(),
        session.pinSalt(), session.pinHash(), session.createdAt(), status);
  }

  private String hashPin(String secret, String salt, String pin) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(secret.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      digest.update(salt.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      return Base64.getEncoder().encodeToString(
          digest.digest(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponível", exception);
    }
  }
}
