package com.faforever.client.chat;

import com.faforever.client.audio.AudioService;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.WebViewConfigurer;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerOnlineEvent;
import com.faforever.client.player.PlayerService;
import com.faforever.client.player.SocialStatus;
import com.faforever.client.preferences.ChatPrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.reporting.ReportingService;
import com.faforever.client.theme.UiService;
import com.faforever.client.uploader.ImageUploadService;
import com.faforever.client.user.UserService;
import com.faforever.client.util.TimeService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import com.jfoenix.controls.JFXTreeTableColumn;
import com.jfoenix.controls.JFXTreeTableView;
import com.jfoenix.controls.RecursiveTreeItem;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import javafx.collections.WeakSetChangeListener;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.faforever.client.chat.ChatColorMode.DEFAULT;
import static com.faforever.client.player.SocialStatus.FOE;
import static java.util.Locale.US;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ChannelTabController extends AbstractChatTabController {

  private static final String USER_CSS_CLASS_FORMAT = "user-%s";

  private static final Comparator<CategoryOrChatUserTreeObject> CHAT_USER_ITEM_COMPARATOR = (o1, o2) -> {
    ChatChannelUser left = o1.getUser();
    ChatChannelUser right = o2.getUser();

    Assert.state(left != null, "Only users must be compared");
    Assert.state(right != null, "Only users must be compared");

    if (isSelf(left)) {
      return 1;
    }
    if (isSelf(right)) {
      return -1;
    }
    return right.getUsername().compareToIgnoreCase(left.getUsername());
  };

  @VisibleForTesting
  static final String CSS_CLASS_MODERATOR = "moderator";

  /** Prevents garbage collection of listener. Key is the username. */
  private final ChangeListener<ChatColorMode> chatColorModeChangeListener;
  /** Prevents garbage collection of listeners. Key is the username. */
  private final Map<String, Collection<ChangeListener<Boolean>>> hideFoeMessagesListeners;
  /** Prevents garbage collection of listeners. Key is the username. */
  private final Map<String, Collection<ChangeListener<SocialStatus>>> socialStatusMessagesListeners;
  /** Prevents garbage collection of listeners. Key is the username. */
  private final Map<String, Collection<ChangeListener<Color>>> colorPropertyListeners;
  private final Map<ChatUserCategory, CategoryOrChatUserTreeObject> categoryNodes;
  private final Map<String, CategoryOrChatUserTreeObject> userNamesToTreeObjects;

  public ToggleButton advancedUserFilter;
  public HBox searchFieldContainer;
  public Button closeSearchFieldButton;
  public TextField searchField;
  public VBox channelTabScrollPaneVBox;
  public Tab channelTabRoot;
  public WebView messagesWebView;
  public TextField userSearchTextField;
  public TextField messageTextField;
  public JFXTreeTableView<CategoryOrChatUserTreeObject> chatUserTableView;
  public JFXTreeTableColumn<CategoryOrChatUserTreeObject, CategoryOrChatUserTreeObject> chatUserColumn;

  private Channel channel;
  private Popup filterUserPopup;
  private UserFilterController userFilterController;
  private MapChangeListener<String, ChatChannelUser> usersChangeListener;
  @SuppressWarnings("FieldCanBeLocal")
  private SetChangeListener<String> moderatorsChangedListener;

  // TODO cut dependencies
  public ChannelTabController(UserService userService, ChatService chatService,
                              PreferencesService preferencesService,
                              PlayerService playerService, AudioService audioService, TimeService timeService,
                              I18n i18n, ImageUploadService imageUploadService,
                              NotificationService notificationService, ReportingService reportingService,
                              UiService uiService, AutoCompletionHelper autoCompletionHelper, EventBus eventBus,
                              WebViewConfigurer webViewConfigurer,
                              CountryFlagService countryFlagService) {

    super(webViewConfigurer, userService, chatService, preferencesService, playerService, audioService,
        timeService, i18n, imageUploadService, notificationService, reportingService, uiService, autoCompletionHelper,
        eventBus, countryFlagService);

    hideFoeMessagesListeners = new HashMap<>();
    socialStatusMessagesListeners = new HashMap<>();
    colorPropertyListeners = new HashMap<>();
    categoryNodes = new HashMap<>();
    userNamesToTreeObjects = new HashMap<>();

    chatColorModeChangeListener = (observable, oldValue, newValue) -> {
      if (newValue != DEFAULT) {
        setAllMessageColors();
      } else {
        removeAllMessageColors();
      }
    };
  }

  private static boolean isSelf(ChatChannelUser chatUser) {
    return chatUser.getPlayer().isPresent() && chatUser.getPlayer().get().getSocialStatus() == SocialStatus.SELF;
  }

  public void setChannel(Channel channel) {
    Assert.state(this.channel == null, "Channel has already been set");
    this.channel = channel;

    String channelName = channel.getName();
    setReceiver(channelName);
    channelTabRoot.setId(channelName);
    channelTabRoot.setText(channelName);

    moderatorsChangedListener = change -> {
      if (change.wasAdded()) {
        addModerator(userNamesToTreeObjects.get(change.getElementAdded()));
      } else if (change.wasRemoved()) {
        removeModerator(userNamesToTreeObjects.get(change.getElementRemoved()));
      }
    };
    JavaFxUtil.addListener(channel.getModerators(), new WeakSetChangeListener<>(moderatorsChangedListener));

    usersChangeListener = change -> {
      if (change.wasAdded()) {
        onUserJoinedChannel(change.getValueAdded());
      } else if (change.wasRemoved()) {
        onUserLeft(change.getValueRemoved().getUsername());
      }
      updateUserCount(change.getMap().size());
    };
    updateUserCount(channel.getUsers().size());

    chatService.addUsersListener(channelName, usersChangeListener);

    // Maybe there already were some users; fetch them
    channel.getUsers().forEach(ChannelTabController.this::onUserJoinedChannel);

    channelTabRoot.setOnCloseRequest(event -> {
      chatService.leaveChannel(channel.getName());
      chatService.removeUsersListener(channelName, usersChangeListener);
    });

    searchFieldContainer.visibleProperty().bind(searchField.visibleProperty());
    closeSearchFieldButton.visibleProperty().bind(searchField.visibleProperty());
    addSearchFieldListener();
  }

  private void removeModerator(CategoryOrChatUserTreeObject chatUserTreeObject) {
    ChatChannelUser chatChannelUser = chatUserTreeObject.getUser();
    chatChannelUser.setModerator(false);
    updateCssClass(chatChannelUser);
  }

  private void updateUserCount(int count) {
    Platform.runLater(() -> userSearchTextField.setPromptText(i18n.get("chat.userCount", count)));
  }

  private void addModerator(CategoryOrChatUserTreeObject chatUserTreeObject) {
    ChatChannelUser chatChannelUser = chatUserTreeObject.getUser();
    chatChannelUser.setModerator(true);
    updateCssClass(chatChannelUser);
  }

  @Override
  public void initialize() {
    super.initialize();

    userSearchTextField.textProperty().addListener((observable, oldValue, newValue) -> filterChatUsers(newValue));

    channelTabScrollPaneVBox.setMinWidth(preferencesService.getPreferences().getChat().getChannelTabScrollPaneWidth());
    channelTabScrollPaneVBox.setPrefWidth(preferencesService.getPreferences().getChat().getChannelTabScrollPaneWidth());
    JavaFxUtil.addListener(preferencesService.getPreferences().getChat().chatColorModeProperty(), chatColorModeChangeListener);
    addUserFilterPopup();

    List<CategoryOrChatUserTreeObject> categoryObjects = createCategoryTreeObjects();
    ObservableList<CategoryOrChatUserTreeObject> categories = FXCollections.observableArrayList(categoryObjects);

    RecursiveTreeItem<CategoryOrChatUserTreeObject> root = new RecursiveTreeItem<>(categories, RecursiveTreeObject::getChildren);
    root.getChildren().forEach(treeItem -> {
      treeItem.setExpanded(true);
      categoryNodes.put(treeItem.getValue().getCategory(), treeItem.getValue());
    });

    chatUserTableView.setRoot(root);
    chatUserTableView.setShowRoot(false);

    // Hack to hide the column headers
    chatUserTableView.skinProperty().addListener(observable -> {
      Pane header = (Pane) chatUserTableView.lookup(".column-header-background");
      header.setMinHeight(0);
      header.setPrefHeight(0);
      header.setMaxHeight(0);
      header.setVisible(false);
    });


    chatUserColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getValue()));
    chatUserColumn.setCellFactory(param -> new ChatUserTreeTableCell(uiService));
    // -4 prevents the horizontal scroll bar from showing. Not very scientific, but effective.
    chatUserColumn.prefWidthProperty().bind(chatUserTableView.widthProperty().subtract(4));
  }

  @Override
  protected void onClosed(Event event) {
    super.onClosed(event);
    JavaFxUtil.removeListener(preferencesService.getPreferences().getChat().chatColorModeProperty(), chatColorModeChangeListener);
  }

  @NotNull
  private List<CategoryOrChatUserTreeObject> createCategoryTreeObjects() {
    return Arrays.stream(ChatUserCategory.values())
        .map(chatUserCategory -> new CategoryOrChatUserTreeObject(chatUserCategory, null))
        .collect(Collectors.toList());
  }

  private void setAllMessageColors() {
    Map<String, String> userToColor = new HashMap<>();
    channel.getUsers().stream().filter(chatUser -> chatUser.getColor() != null).forEach(chatUser
        -> userToColor.put(chatUser.getUsername(), JavaFxUtil.toRgbCode(chatUser.getColor())));
    getJsObject().call("setAllMessageColors", new Gson().toJson(userToColor));
  }

  private void removeAllMessageColors() {
    getJsObject().call("removeAllMessageColors");
  }

  @VisibleForTesting
  boolean isUsernameMatch(ChatChannelUser user) {
    String lowerCaseSearchString = user.getUsername().toLowerCase();
    return lowerCaseSearchString.contains(userSearchTextField.getText().toLowerCase());
  }

  @Override
  public Tab getRoot() {
    return channelTabRoot;
  }

  @Override
  protected TextInputControl messageTextField() {
    return messageTextField;
  }

  @Override
  protected WebView getMessagesWebView() {
    return messagesWebView;
  }

  @Override
  protected void onMention(ChatMessage chatMessage) {
    if (preferencesService.getPreferences().getNotification().getNotifyOnAtMentionOnlyEnabled()
        && !chatMessage.getMessage().contains("@" + userService.getUsername())) {
      return;
    }
    if (!hasFocus()) {
      audioService.playChatMentionSound();
      showNotificationIfNecessary(chatMessage);
      incrementUnreadMessagesCount(1);
      setUnread(true);
    }
  }

  @Override
  protected String getMessageCssClass(String login) {
    ChatChannelUser chatUser = chatService.getChatUser(login, channel.getName());
    Optional<Player> currentPlayerOptional = playerService.getCurrentPlayer();

    if (currentPlayerOptional.isPresent()) {
      return "";
    }

    if (chatUser.isModerator()) {
      return CSS_CLASS_MODERATOR;
    }

    return super.getMessageCssClass(login);
  }

  private void addUserFilterPopup() {
    filterUserPopup = new Popup();
    filterUserPopup.setAutoFix(false);
    filterUserPopup.setAutoHide(true);
    filterUserPopup.setAnchorLocation(PopupWindow.AnchorLocation.CONTENT_TOP_RIGHT);

    userFilterController = uiService.loadFxml("theme/chat/user_filter.fxml");
    userFilterController.setChannelController(this);
    userFilterController.filterAppliedProperty().addListener(((observable, oldValue, newValue) -> advancedUserFilter.setSelected(newValue)));
    filterUserPopup.getContent().setAll(userFilterController.getRoot());
  }

  private void updateUserMessageColor(ChatChannelUser chatUser) {
    String color = "";
    if (chatUser.getColor() != null) {
      color = JavaFxUtil.toRgbCode(chatUser.getColor());
    }
    getJsObject().call("updateUserMessageColor", chatUser.getUsername(), color);
  }

  private void removeUserMessageClass(ChatChannelUser chatUser, String cssClass) {
    //TODO: DOM Exception 12 when cssClass string is empty string, not sure why cause .remove in the js should be able to handle it
    if (cssClass.isEmpty()) {
      return;
    }
    Platform.runLater(() -> getJsObject().call("removeUserMessageClass", String.format(USER_CSS_CLASS_FORMAT, chatUser.getUsername()), cssClass));

  }

  private void addUserMessageClass(ChatChannelUser player, String cssClass) {
    Platform.runLater(() -> getJsObject().call("addUserMessageClass", String.format(USER_CSS_CLASS_FORMAT, player.getUsername()), cssClass));
  }

  private void updateUserMessageDisplay(ChatChannelUser chatUser, String display) {
    Platform.runLater(() -> getJsObject().call("updateUserMessageDisplay", chatUser.getUsername(), display));
  }

  /** Filters by username "contains" case insensitive. */
  @SuppressWarnings("unchecked")
  private void filterChatUsers(String searchString) {
    if (Strings.isNullOrEmpty(searchString)) {
      chatUserTableView.setPredicate(treeItem -> true);
      return;
    }

    chatUserTableView.setPredicate(treeItem -> {
      if (Strings.isNullOrEmpty(searchString)) {
        return true;
      }

      ChatChannelUser user = treeItem.getValue().getUser();

      boolean isCategoryObject = user == null;
      if (isCategoryObject) {
        return true;
      }

      return user.getUsername().toLowerCase(US).contains(searchString.toLowerCase(US));
    });
  }

  private void associateChatUserWithPlayer(Player player, ChatChannelUser chatUser) {
    chatUser.setPlayer(player);
    player.getChatChannelUsers().add(chatUser);

    updateCssClass(chatUser);
    updateInChatUserTree(chatUser);

    ChatPrefs chatPrefs = preferencesService.getPreferences().getChat();
    JavaFxUtil.addListener(player.socialStatusProperty(), createWeakSocialStatusListener(chatPrefs, chatUser, player));
  }

  private void onUserJoinedChannel(ChatChannelUser chatUser) {
    Optional<Player> playerOptional = playerService.getPlayerForUsername(chatUser.getUsername());
    if (playerOptional.isPresent()) {
      associateChatUserWithPlayer(playerOptional.get(), chatUser);
    } else {
      updateInChatUserTree(chatUser);
    }
    // FIXME check if this code is still needed. I think it was disabled just to find a race condition, which has been found.
//
//    ChangeListener<Boolean> weakHideFoeMessagesListener = createWeakHideFoeMessagesListener(chatUser);
//    WeakChangeListener<Color> weakColorPropertyListener = createWeakColorPropertyListener(chatUser);
//
//    ChatPrefs chatPrefs = preferencesService.getPreferences().getChat();
//    JavaFxUtil.addListener(chatUser.colorProperty(), weakColorPropertyListener);
//    JavaFxUtil.addListener(chatPrefs.hideFoeMessagesProperty(), weakHideFoeMessagesListener);
//
//    Platform.runLater(() -> {
//      weakColorPropertyListener.changed(chatUser.colorProperty(), null, chatUser.getColor());
//      weakHideFoeMessagesListener.changed(chatPrefs.hideFoeMessagesProperty(), null, chatPrefs.getHideFoeMessages());
//    });
  }

  private void updateInChatUserTree(ChatChannelUser chatUser) {
    Platform.runLater(() -> {
      CategoryOrChatUserTreeObject treeObject = new CategoryOrChatUserTreeObject(null, chatUser);

      Set<ChatUserCategory> userCategories = new HashSet<>();

      if (!chatUser.getPlayer().isPresent()) {
        userCategories.add(ChatUserCategory.CHAT_ONLY);
      }

      if (chatUser.isModerator()) {
        userCategories.add(ChatUserCategory.MODERATOR);
      }

      if (isSelf(chatUser) || chatUser.getPlayer().isPresent()) {
        userCategories.add(ChatUserCategory.OTHER);
      }

      Arrays.stream(ChatUserCategory.values())
          .filter(chatUserCategory -> !userCategories.contains(chatUserCategory))
          .map(categoryNodes::get)
          .forEach(categoryItem -> categoryItem.getChildren()
              .removeIf(child -> Objects.equals(child, categoryItem)));

      userCategories.stream()
          .map(categoryNodes::get)
          .forEach(parent -> addToTreeItemSorted(parent, treeObject));

      chatUser.getPlayer().ifPresent(player -> {
        switch (player.getSocialStatus()) {
          case FRIEND:
            addToTreeItemSorted(categoryNodes.get(ChatUserCategory.FRIEND), treeObject);
            break;
          case FOE:
            addToTreeItemSorted(categoryNodes.get(ChatUserCategory.FOE), treeObject);
            break;
          default:
            // Nothing to do
        }
      });
    });
  }

  private void addToTreeItemSorted(CategoryOrChatUserTreeObject parent, CategoryOrChatUserTreeObject child) {
    Platform.runLater(() -> {
      ObservableList<CategoryOrChatUserTreeObject> children = parent.getChildren();

      for (int index = 0; index < children.size(); index++) {
        CategoryOrChatUserTreeObject otherChild = children.get(index);
        if (CHAT_USER_ITEM_COMPARATOR.compare(child, otherChild) > 0) {
          children.add(index, child);
          return;
        }
      }
      children.add(child);
    });
  }

  private void updateCssClass(ChatChannelUser chatUser) {
    Platform.runLater(() -> {
      if (chatUser.getPlayer().isPresent()) {
        removeUserMessageClass(chatUser, CSS_CLASS_CHAT_ONLY);
      } else {
        addUserMessageClass(chatUser, CSS_CLASS_CHAT_ONLY);
      }
      if (chatUser.isModerator()) {
        addUserMessageClass(chatUser, CSS_CLASS_MODERATOR);
      } else {
        removeUserMessageClass(chatUser, CSS_CLASS_MODERATOR);
      }
    });
  }

  private WeakChangeListener<Color> createWeakColorPropertyListener(ChatChannelUser chatUser) {
    ChangeListener<Color> listener = (observable, oldValue, newValue) -> updateUserMessageColor(chatUser);

    colorPropertyListeners.computeIfAbsent(chatUser.getUsername(), i -> new ArrayList<>()).add(listener);
    return new WeakChangeListener<>(listener);
  }

  private WeakChangeListener<SocialStatus> createWeakSocialStatusListener(ChatPrefs chatPrefs, ChatChannelUser chatUser, Player player) {
    ChangeListener<SocialStatus> listener = (observable, oldValue, newValue) -> {
      removeUserMessageClass(chatUser, oldValue.getCssClass());
      addUserMessageClass(chatUser, newValue.getCssClass());

      if (chatPrefs.getHideFoeMessages() && newValue == FOE) {
        updateUserMessageDisplay(chatUser, "none");
      } else {
        updateUserMessageDisplay(chatUser, "");
      }
    };
    socialStatusMessagesListeners.computeIfAbsent(player.getUsername(), i -> new ArrayList<>()).add(listener);
    return new WeakChangeListener<>(listener);
  }

  private ChangeListener<Boolean> createWeakHideFoeMessagesListener(ChatChannelUser chatUser) {
    ChangeListener<Boolean> listener = (observable, oldValue, newValue) -> {
      if (newValue && chatUser.getPlayer().isPresent() && chatUser.getPlayer().get().getSocialStatus() == FOE) {
        updateUserMessageDisplay(chatUser, "none");
      } else {
        updateUserMessageDisplay(chatUser, "");
      }
    };
    hideFoeMessagesListeners.computeIfAbsent(chatUser.getUsername(), i -> new ArrayList<>()).add(listener);
    return new WeakChangeListener<>(listener);
  }

  private void onUserLeft(String username) {
    Platform.runLater(() -> {
      categoryNodes.values()
          .forEach(categoryItem -> categoryItem.getChildren()
              .removeIf(userItem -> userItem.getUser().getUsername().equalsIgnoreCase(username)));

      hideFoeMessagesListeners.remove(username);
      socialStatusMessagesListeners.remove(username);
      colorPropertyListeners.remove(username);
    });
  }

  // FIXME use this again
//  private void updateRandomColorsAllowed(ChatUserHeader parent, ChatChannelUser chatUser, ChatUserItemController chatUserItemController) {
//    chatUserItemController.setRandomColorAllowed(
//        (parent == othersTreeItem || parent == chatOnlyTreeItem)
//            && chatUser.getPlayer().isPresent()
//            && chatUser.getPlayer().get().getSocialStatus() != SELF
//    );
//  }

  public void onKeyReleased(KeyEvent event) {
    if (event.getCode() == KeyCode.ESCAPE) {
      onSearchFieldClose();
    } else if (event.isControlDown() && event.getCode() == KeyCode.F) {
      searchField.clear();
      searchField.setVisible(!searchField.isVisible());
      searchField.requestFocus();
    }
  }

  public void onSearchFieldClose() {
    searchField.setVisible(false);
    searchField.clear();
  }

  private void addSearchFieldListener() {
    searchField.textProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue.trim().isEmpty()) {
        getJsObject().call("removeHighlight");
      } else {
        getJsObject().call("highlightText", newValue);
      }
    });
  }

  public void onAdvancedUserFilter(ActionEvent actionEvent) {
    advancedUserFilter.setSelected(userFilterController.isFilterApplied());
    if (filterUserPopup.isShowing()) {
      filterUserPopup.hide();
      return;
    }

    ToggleButton button = (ToggleButton) actionEvent.getSource();

    Bounds screenBounds = advancedUserFilter.localToScreen(advancedUserFilter.getBoundsInLocal());
    filterUserPopup.show(button.getScene().getWindow(), screenBounds.getMinX(), screenBounds.getMaxY());
  }

  @Override
  protected String getInlineStyle(String username) {
    ChatChannelUser chatUser = chatService.getChatUser(username, channel.getName());

    Optional<Player> playerOptional = playerService.getPlayerForUsername(username);

    ChatPrefs chatPrefs = preferencesService.getPreferences().getChat();
    String color = "";
    String display = "";

    if (chatPrefs.getHideFoeMessages() && playerOptional.isPresent() && playerOptional.get().getSocialStatus() == FOE) {
      display = "display: none;";
    } else {
      ChatColorMode chatColorMode = chatPrefs.getChatColorMode();
      if ((chatColorMode == ChatColorMode.CUSTOM || chatColorMode == ChatColorMode.RANDOM)
          && chatUser.getColor() != null) {
        color = createInlineStyleFromColor(chatUser.getColor());
      }
    }

    return String.format("%s%s", color, display);
  }

  @SuppressWarnings("unchecked")
  void setUserFilter(Predicate<TreeItem<CategoryOrChatUserTreeObject>> predicate) {
    chatUserTableView.setPredicate(predicate);
  }

  @Subscribe
  public void onPlayerOnline(PlayerOnlineEvent event) {
    // We could add a listener on chatChannelUser.playerProperty() but this would result in thousands of mostly idle
    // listeners which we're trying to avoid.
    ChatChannelUser chatUser = chatService.getChatUser(event.getPlayer().getUsername(), channel.getName());
    associateChatUserWithPlayer(event.getPlayer(), chatUser);
  }
}
