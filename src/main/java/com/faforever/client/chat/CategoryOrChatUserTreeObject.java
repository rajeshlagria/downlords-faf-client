package com.faforever.client.chat;

import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import lombok.Value;

/**
 * Since it's easiest if all items within the chat user tree table view are of the same type, this class represents
 * both, a category item as well as a chat user item. If it's a category-only object, {@code user} will be {@code null}.
 * If it's a chat user, {@code category} will be {@code null}.
 */
@Value
class CategoryOrChatUserTreeObject extends RecursiveTreeObject<CategoryOrChatUserTreeObject> {
  ChatUserCategory category;
  ChatChannelUser user;
}
