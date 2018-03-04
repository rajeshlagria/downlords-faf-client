package com.faforever.client.chat.event;

import com.faforever.client.chat.ChatMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChatMessageEvent {
  @Getter
  private final ChatMessage message;

}
