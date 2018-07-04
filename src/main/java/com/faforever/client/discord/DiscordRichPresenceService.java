package com.faforever.client.discord;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.game.Game;
import com.faforever.client.player.PlayerService;
import com.faforever.client.remote.domain.GameStatus;
import com.github.psnrigner.discordrpcjava.DiscordRichPresence;
import com.github.psnrigner.discordrpcjava.DiscordRpc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@Service
public class DiscordRichPresenceService {
  private static final String HOSTING = "hosting";
  private static final String WAITING = "waiting";
  private static final String PLAYING = "playing";
  /**
   * It is suggested(libaries github page) to look for callbacks every 5 seconds
   */
  private final int initialDelayForCallback = 5000;
  private final int periodForCallBack = 5000;


  private final PlayerService playerService;
  private final Timer timer;
  private DiscordRpc discordRpc;


  public DiscordRichPresenceService(PlayerService playerService, ClientDiscordEventHandler discordEventHandler, ClientProperties clientProperties) {
    this.playerService = playerService;
    this.timer = new Timer(true);
    try {
      discordRpc = new DiscordRpc();
      discordRpc.init(clientProperties.getDiscordConfig().getApplicationId(), discordEventHandler, true, null);
      timer.schedule(new TimerTask() {
        @Override
        public void run() {
          discordRpc.runCallbacks();
        }
      }, initialDelayForCallback, periodForCallBack);
    } catch (Exception e) {
      //TODO: report to bugsnap
      log.error("Error in discord init", e);
    }
  }

  public void updatePlayedGameTo(Game game) {
    try {
      DiscordRichPresence discordRichPresence = new DiscordRichPresence();
      discordRichPresence.setState(getDiscordState(game));
      discordRichPresence.setDetails(MessageFormat.format("Game {0}", game.getTitle()));
      discordRichPresence.setStartTimestamp((game.getStartTime() == null ? System.currentTimeMillis() : game.getStartTime().toEpochMilli()) / 1000);
      discordRichPresence.setPartyId(String.valueOf(game.getId()));
      discordRichPresence.setPartySize(game.getNumPlayers());
      discordRichPresence.setPartyMax(game.getMaxPlayers());
      if (game.getStatus() == GameStatus.CLOSED) {
        discordRichPresence.setEndTimestamp(Instant.now().toEpochMilli() / 1000);
      }
      discordRichPresence.setInstance(false);

      discordRpc.updatePresence(discordRichPresence);
    } catch (Exception e) {
      //TODO: report to bugsnap
      log.error("Error reporting game status to discord", e);
    }
  }

  private String getDiscordState(Game game) {
    //I want no internationalisation in here as it should always be English
    switch (game.getStatus()) {
      case OPEN:
        boolean isHost = game.getHost().equals(playerService.getCurrentPlayer().orElseThrow(() -> new IllegalStateException("Player must have been set")).getUsername());
        return isHost ? HOSTING : WAITING;
      case PLAYING:
        return PLAYING;
    }
    return "";
  }

  @PreDestroy
  public void onDestroy() {
    discordRpc.shutdown();
    timer.cancel();
  }
}
