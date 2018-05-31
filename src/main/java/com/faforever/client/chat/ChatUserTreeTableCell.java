package com.faforever.client.chat;

import com.faforever.client.theme.UiService;
import javafx.scene.control.TreeTableCell;

import java.util.Optional;

public class ChatUserTreeTableCell extends TreeTableCell<CategoryOrChatUserTreeObject, CategoryOrChatUserTreeObject> {

  private final ChatUserItemController chatUserItemController;
  private final ChatUserItemCategoryController chatUserCategoryController;
  private Optional<Runnable> onSocialStatusUpdatedListener;
  private Object oldItem;

  public ChatUserTreeTableCell(UiService uiService) {
    chatUserItemController = uiService.loadFxml("theme/chat/chat_user_item.fxml");
    chatUserCategoryController = uiService.loadFxml("theme/chat/chat_user_category.fxml");
  }

  // TODO check whether or not this is meant to be used. Fix accordingly.
  public void setOnSocialStatusUpdatedListener(Runnable onSocialStatusUpdatedListener) {
    this.onSocialStatusUpdatedListener = Optional.ofNullable(onSocialStatusUpdatedListener);
  }

  @Override
  protected void updateItem(CategoryOrChatUserTreeObject item, boolean empty) {
    if (item == oldItem) {
      return;
    }
    oldItem = item;

    super.updateItem(item, empty);

    setText(null);
    if (item == null || empty) {
      setGraphic(null);
      return;
    }

    if (item.getUser() != null) {
      chatUserItemController.setChatUser(item.getUser());
      chatUserItemController.setOnSocialStatusUpdatedListener(onSocialStatusUpdatedListener);
      setText(null);
      setGraphic(chatUserItemController.getRoot());
    } else {
      chatUserCategoryController.setChatUserCategory(item.getCategory());
      setText(null);
      setGraphic(chatUserCategoryController.getRoot());
    }
  }
}
