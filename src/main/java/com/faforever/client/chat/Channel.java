package com.faforever.client.chat;

import com.faforever.client.fx.JavaFxUtil;
import javafx.beans.property.ReadOnlySetWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Channel {

  private final ObservableMap<String, ChatChannelUser> users;
  private final StringProperty topic;
  private String name;
  private ObservableSet<String> moderators;

  public Channel(String name) {
    this.name = name;
    users = FXCollections.synchronizedObservableMap(FXCollections.observableHashMap());
    moderators = FXCollections.observableSet();
    topic = new SimpleStringProperty();
  }

  public String getTopic() {
    return topic.get();
  }

  public void setTopic(String topic) {
    this.topic.set(topic);
  }

  public StringProperty topicProperty() {
    return topic;
  }

  public ChatChannelUser removeUser(String username) {
    return users.remove(username);
  }

  public void addUsers(List<ChatChannelUser> users) {
    users.forEach(user -> this.users.put(user.getUsername(), user));
  }

  public void addUser(ChatChannelUser chatUser) {
    users.put(chatUser.getUsername(), chatUser);
  }

  public void clearUsers() {
    users.clear();
  }

  public void addUsersListeners(MapChangeListener<String, ChatChannelUser> listener) {
    JavaFxUtil.addListener(users, listener);
  }

  public void removeUserListener(MapChangeListener<String, ChatChannelUser> listener) {
    users.removeListener(listener);
  }

  public void addModerator(String username) {
    moderators.add(username);
  }

  public ReadOnlySetWrapper<String> getModerators() {
    return new ReadOnlySetWrapper<>(moderators);
  }

  /**
   * Returns an unmodifiable copy of the current users.
   */
  public List<ChatChannelUser> getUsers() {
    return Collections.unmodifiableList(new ArrayList<>(users.values()));
  }

  public ChatChannelUser getUser(String username) {
    return users.get(username);
  }

  public String getName() {
    return name;
  }
}
