package com.faforever.client.discord;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.game.Game;
import com.faforever.client.player.PlayerService;
import com.faforever.client.remote.domain.GameStatus;
import lombok.extern.slf4j.Slf4j;
import net.arikia.dev.drpc.DiscordRPC;
import net.arikia.dev.drpc.DiscordRichPresence;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.text.MessageFormat;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@Service
public class DiscordRichPresenceService {
  public static final String GAME_ID_REGEX_NAME = "gameId";
  public static final String PLAYER_ID_REGEX_NAME = "playerId";
  /**
   * Discord complains if we simply use the game id as a secret that is why we do the following
   */
  public static final String SPECTATE_SECRET = GAME_ID_REGEX_NAME + "=(?<" + GAME_ID_REGEX_NAME + ">\\d*);" + PLAYER_ID_REGEX_NAME + "=(?<" + PLAYER_ID_REGEX_NAME + ">\\d*)";
  public static final String JOIN_SECRET = GAME_ID_REGEX_NAME + "=(?<" + GAME_ID_REGEX_NAME + ">\\d*)";
  private static final String HOSTING = "Hosting";
  private static final String WAITING = "Waiting";
  private static final String PLAYING = "Playing";
  /**
   * It is suggested as per the library's GitHub page to look for callbacks every 5 seconds
   * See https://github.com/Vatuu/discord-rpc
   */
  private static final int INITIAL_DELAY_FOR_CALLBACK_MILLIS = 5000;
  private static final int PERIOD_FOR_CALLBACK_MILLIS = 5000;

  private final ClientProperties clientProperties;
  private final PlayerService playerService;
  private final Timer timer;


  public DiscordRichPresenceService(PlayerService playerService, ClientDiscordEventHandler discordEventHandler, ClientProperties clientProperties) {
    this.playerService = playerService;
    this.clientProperties = clientProperties;
    this.timer = new Timer(true);
    try {
      DiscordRPC.discordInitialize(this.clientProperties.getDiscord().getApplicationId(), discordEventHandler, true);
      timer.schedule(new TimerTask() {
        @Override
        public void run() {
          DiscordRPC.discordRunCallbacks();
        }
      }, INITIAL_DELAY_FOR_CALLBACK_MILLIS, PERIOD_FOR_CALLBACK_MILLIS);
    } catch (Exception e) {
      //TODO: report to bugsnap
      log.error("Error in discord init", e);
    }
  }

  public void updatePlayedGameTo(Game game, int currentPlayerId) {
    try {
      if (game.getStatus() == GameStatus.CLOSED) {
        DiscordRPC.discordClearPresence();
      }
      DiscordRichPresence.Builder discordRichPresence = new DiscordRichPresence.Builder(getDiscordState(game));
      discordRichPresence.setDetails(MessageFormat.format("{0} | {1}", game.getFeaturedMod(), game.getTitle()));
      discordRichPresence.setParty(String.valueOf(game.getId()), game.getNumPlayers(), game.getMaxPlayers());
      discordRichPresence.setSmallImage(clientProperties.getDiscord().getSmallImageKey(), "");
      discordRichPresence.setBigImage(clientProperties.getDiscord().getBigImageKey(), "");
      String joinSecret = null;
      String spectateSecret = null;
      if (game.getStatus() == GameStatus.OPEN) {
        joinSecret = JOIN_SECRET.replaceAll("\\(?<gameId>(.*)\\)", String.valueOf(game.getId()));
      }

      if (game.getStatus() == GameStatus.PLAYING) {
        spectateSecret = SPECTATE_SECRET.replaceAll("\\(?<gameId>(.*)\\)", String.valueOf(game.getId()))
            .replaceAll("\\(\\?<playerId>(.*)\\)", String.valueOf(currentPlayerId));
      }

      discordRichPresence.setSecrets(joinSecret, spectateSecret);

      DiscordRichPresence update = discordRichPresence.build();
      if (game.getStartTime() != null) {
        //Sadly, this can not be set via the builder. I created an issue in the library's repo
        // TODO set via builder when https://github.com/Vatuu/discord-rpc/issues/18 has been fixed
        update.startTimestamp = game.getStartTime().toEpochMilli();
      }
      DiscordRPC.discordUpdatePresence(update);
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
      default:
        return PLAYING;
    }
  }

  @PreDestroy
  public void onDestroy() {
    DiscordRPC.discordShutdown();
    timer.cancel();
  }
}
