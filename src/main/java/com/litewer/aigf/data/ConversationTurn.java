package com.litewer.aigf.data;

import net.minecraft.nbt.CompoundTag;

public record ConversationTurn(String speaker, String text) {
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("Speaker", this.speaker);
      tag.putString("Text", this.text);
      return tag;
   }

   public static ConversationTurn fromTag(CompoundTag tag) {
      return new ConversationTurn(tag.getString("Speaker"), tag.getString("Text"));
   }
}