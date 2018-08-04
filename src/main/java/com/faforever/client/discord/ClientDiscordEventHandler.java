package com.faforever.client.discord;


import com.faforever.client.notification.NotificationService;
import com.faforever.client.preferences.PreferencesService;
import lombok.extern.slf4j.Slf4j;
import net.arikia.dev.drpc.DiscordEventHandlers;
import net.arikia.dev.drpc.DiscordRPC;
import net.arikia.dev.drpc.DiscordRPC.DiscordReply;
import net.arikia.dev.drpc.DiscordUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ClientDiscordEventHandler extends DiscordEventHandlers {
  private final ApplicationEventPublisher applicationEventPublisher;
  private final NotificationService notificationService;
  private final PreferencesService preferencesService;

  public ClientDiscordEventHandler(ApplicationEventPublisher applicationEventPublisher, NotificationService notificationService, PreferencesService preferencesService) {
    this.applicationEventPublisher = applicationEventPublisher;
    this.notificationService = notificationService;
    this.preferencesService = preferencesService;
    ready = this::onDiscordReady;
    disconnected = this::onDisconnected;
    errored = this::onError;
    spectateGame = this::onSpectate;
    joinGame = this::onJoinGame;
    joinRequest = this::onJoinRequest;
  }

  private void onJoinRequest(DiscordUser discordUser) {
    if (preferencesService.getPreferences().isDisallowJoinsViaDiscord()) {
      DiscordRPC.discordRespond(discordUser.userId, DiscordReply.NO);
      return;
    }
    DiscordRPC.discordRespond(discordUser.userId, DiscordReply.YES);
  }

  private void onJoinGame(String s) {
    Matcher matcher = Pattern.compile(DiscordRichPresenceService.JOIN_SECRET).matcher(s);
    int gameId = Integer.parseInt(matcher.group(DiscordRichPresenceService.GAME_ID_REGEX_NAME));
    try {
      applicationEventPublisher.publishEvent(new DiscordJoinEvent(gameId));
    } catch (Exception e) {
      notificationService.addImmediateErrorNotification(e, "game.couldNotJoin", gameId);
      log.error("Could not join game from discord rich presence", e);
    }
  }

  private void onSpectate(String spectateSecret) {
    Matcher matcher = Pattern.compile(DiscordRichPresenceService.SPECTATE_SECRET).matcher(spectateSecret);
    int replayId = Integer.parseInt(matcher.group(DiscordRichPresenceService.GAME_ID_REGEX_NAME));
    int playerId = Integer.parseInt(matcher.group(DiscordRichPresenceService.PLAYER_ID_REGEX_NAME));
    try {
      applicationEventPublisher.publishEvent(new DiscordSpectateEvent(replayId, playerId));
    } catch (Exception e) {
      notificationService.addImmediateErrorNotification(e, "replay.couldNotOpen", replayId);
      log.error("Could not join game from discord rich presence", e);
    }
  }

  private void onError(int errorCode, String message) {
    log.error("Discord error with code ''{}'' and message ''{}''", errorCode, message);
  }

  private void onDisconnected(int code, String message) {
    log.info("Discord disconnected with code ''{}'' and message ''{}''", code, message);
  }

  private void onDiscordReady(DiscordUser discordUser) {
    log.info("Discord is ready, with user: ''{}''", discordUser.username);
  }
}
