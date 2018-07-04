package com.faforever.client.discord;

import com.github.psnrigner.discordrpcjava.DiscordEventHandler;
import com.github.psnrigner.discordrpcjava.DiscordJoinRequest;
import com.github.psnrigner.discordrpcjava.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ClientDiscordEventHandler implements DiscordEventHandler {
  @Override
  public void ready() {
    log.trace("Discord is ready");
  }

  @Override
  public void disconnected(ErrorCode errorCode, String message) {
    if (message != null && !message.equals("SUCCESS")) {
      log.error("Discord disconnected , message: {} , code: {}", errorCode, message);
    } else {
      log.trace("Discord disconnected , message: {} , code: {}", errorCode, message);
    }
  }

  @Override
  public void errored(ErrorCode errorCode, String message) {
    log.error("Discord had an error , message: {} , code: {}", errorCode, message);
  }

  @Override
  public void joinGame(String joinSecret) {
    log.info("Discord requests to join game joinSecrete: {}", joinSecret);
  }

  @Override
  public void spectateGame(String spectateSecret) {
    log.info("Discord requests to spectate game spectateSecret: {}", spectateSecret);

  }

  @Override
  public void joinRequest(DiscordJoinRequest joinRequest) {
    log.info("Discord user requests to join current game ,userName: {}", joinRequest.getUsername());
  }
}
