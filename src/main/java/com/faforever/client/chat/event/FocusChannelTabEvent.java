package com.faforever.client.chat.event;

import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.main.event.NavigationItem;
import lombok.Getter;

@Getter
public class FocusChannelTabEvent extends NavigateEvent {
  private final String channel;

  public FocusChannelTabEvent(String channel) {
    super(NavigationItem.CHAT);
    this.channel = channel;
  }
}
