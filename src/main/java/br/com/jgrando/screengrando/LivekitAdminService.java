package br.com.jgrando.screengrando;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class LivekitAdminService {
  private static final Logger log = LoggerFactory.getLogger(LivekitAdminService.class);

  private final RestClient client;
  private final TokenService tokens;

  public LivekitAdminService(TokenService tokens, SessionService sessions) {
    this.tokens = tokens;

    String base = sessions.livekitUrl()
        .replaceFirst("^wss://", "https://")
        .replaceFirst("^ws://", "http://")
        .replaceAll("/+$", "");
    this.client = RestClient.builder().baseUrl(base).build();
  }

  public int viewerCount(String room) {
    try {
      ParticipantList response = client
          .post()
          .uri("/twirp/livekit.RoomService/ListParticipants")
          .contentType(MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + tokens.roomAdminToken(room, false))
          .body(Map.of("room", room))
          .retrieve()
          .body(ParticipantList.class);

      if (response == null || response.participants() == null) {
        return 0;
      }

      return (int) response.participants().stream()
          .filter(participant -> !"publisher".equals(participant.identity()))
          .count();
    } catch (RestClientException exception) {
      log.debug(
          "LiveKit participant lookup unavailable for room {}: {}",
          room,
          exception.getMessage());
      return 0;
    }
  }

  public void deleteRoom(String room) {
    try {
      client
          .post()
          .uri("/twirp/livekit.RoomService/DeleteRoom")
          .contentType(MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + tokens.roomAdminToken(room, true))
          .body(Map.of("room", room))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() != 404) {
        log.warn(
            "Could not close LiveKit room {} (HTTP {})",
            room,
            exception.getStatusCode().value());
      }
    } catch (RestClientException exception) {
      log.warn("Could not close LiveKit room {}: {}", room, exception.getMessage());
    }
  }

  public record ParticipantList(List<Participant> participants) {}

  public record Participant(String identity) {}
}
