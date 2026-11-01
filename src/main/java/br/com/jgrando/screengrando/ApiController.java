package br.com.jgrando.screengrando;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final SessionService sessions;
  private final TokenService tokens;
  private final LivekitAdminService livekitAdmin;

  public ApiController(
      SessionService sessions, TokenService tokens, LivekitAdminService livekitAdmin) {
    this.sessions = sessions;
    this.tokens = tokens;
    this.livekitAdmin = livekitAdmin;
  }

  public record CreateRequest(
      @NotBlank @Size(max = 100) String title,
      @Pattern(regexp = "(?:[0-9]{4,12})?") String pin) {}

  public record ViewerRequest(String displayName, String pin) {}

  public record Created(
      String code,
      String title,
      String status,
      Instant createdAt,
      String viewerUrl,
      String livekitUrl,
      String publisherSecret) {}

  public record PublicInfo(
      String code,
      String title,
      String status,
      String publisherName,
      int viewerCount,
      boolean pinProtected) {}

  public record TokenResponse(String livekitUrl, String token, String expiresAt) {}

  @PostMapping("/sessions")
  @ResponseStatus(HttpStatus.CREATED)
  public Created create(@Valid @RequestBody CreateRequest request) {
    SessionService.Session session = sessions.create(request.title(), request.pin());
    return new Created(
        session.code(),
        session.title(),
        session.status(),
        session.createdAt(),
        sessions.viewerUrl(session),
        sessions.livekitUrl(),
        session.publisherSecret());
  }

  @GetMapping("/sessions/{code}")
  public PublicInfo publicInfo(@PathVariable String code) {
    SessionService.Session session = sessions.get(code);
    int viewerCount = session.status().equals("LIVE") ? livekitAdmin.viewerCount(session.code()) : 0;
    return new PublicInfo(
        session.code(),
        session.title(),
        session.status(),
        "Apresentador",
        viewerCount,
        session.pinHash() != null);
  }

  @PostMapping("/sessions/{code}/viewer-token")
  public TokenResponse viewerToken(
      @PathVariable String code, @RequestBody(required = false) ViewerRequest request) {
    SessionService.Session session = sessions.get(code);
    if (session.status().equals("ENDED")) {
      throw new ResponseStatusException(HttpStatus.GONE, "Esta transmissão foi encerrada.");
    }
    if (!sessions.verifyViewerPin(session, request == null ? null : request.pin())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PIN incorreto.");
    }
    String displayName = request == null || request.displayName() == null || request.displayName().isBlank()
        ? "Convidado"
        : request.displayName().trim();
    TokenService.LivekitToken token = tokens.livekit(session.code(), displayName, false);
    return new TokenResponse(token.livekitUrl(), token.token(), token.expiresAt());
  }

  @PostMapping("/sessions/{code}/publisher-token")
  public TokenResponse publisherToken(
      @PathVariable String code,
      @RequestHeader(value = "X-Session-Secret", required = false) String publisherSecret) {
    SessionService.Session session = sessions.validatePublisher(code, publisherSecret);
    TokenService.LivekitToken token = tokens.livekit(session.code(), "Apresentador", true);
    return new TokenResponse(token.livekitUrl(), token.token(), token.expiresAt());
  }

  @PostMapping("/sessions/{code}/publisher-started")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void publisherStarted(
      @PathVariable String code,
      @RequestHeader(value = "X-Session-Secret", required = false) String publisherSecret) {
    sessions.markLive(code, publisherSecret);
  }

  @DeleteMapping("/sessions/{code}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void end(
      @PathVariable String code,
      @RequestHeader(value = "X-Session-Secret", required = false) String publisherSecret) {
    sessions.end(code, publisherSecret);
    livekitAdmin.deleteRoom(code);
  }
}
