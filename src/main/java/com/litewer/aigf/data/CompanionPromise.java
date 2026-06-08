package com.litewer.aigf.data;

import net.minecraft.nbt.CompoundTag;

public record CompanionPromise(
   String summary,
   CompanionPromiseCategory category,
   CompanionPromiseStatus status,
   long createdWorldTime,
   long dueWorldTime,
   long resolvedWorldTime,
   long lastReminderWorldTime
) {
   public boolean isPending() {
      return this.status == CompanionPromiseStatus.PENDING;
   }

   public boolean isDueSoon(long worldTime) {
      return this.isPending() && this.dueWorldTime > worldTime && this.dueWorldTime - worldTime <= 6000L;
   }

   public boolean isOverdue(long worldTime) {
      return this.isPending() && this.dueWorldTime > 0L && worldTime >= this.dueWorldTime;
   }

   public CompanionPromise withReminder(long worldTime) {
      return new CompanionPromise(this.summary, this.category, this.status, this.createdWorldTime, this.dueWorldTime, this.resolvedWorldTime, worldTime);
   }

   public CompanionPromise kept(long worldTime) {
      return new CompanionPromise(
         this.summary, this.category, CompanionPromiseStatus.KEPT, this.createdWorldTime, this.dueWorldTime, worldTime, this.lastReminderWorldTime
      );
   }

   public CompanionPromise broken(long worldTime) {
      return new CompanionPromise(
         this.summary, this.category, CompanionPromiseStatus.BROKEN, this.createdWorldTime, this.dueWorldTime, worldTime, this.lastReminderWorldTime
      );
   }

   public String shortSummary(int maxLength) {
      if (this.summary == null) {
         return "";
      }

      String trimmed = this.summary.trim();
      return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("Summary", this.summary);
      tag.putString("Category", this.category.name());
      tag.putString("Status", this.status.name());
      tag.putLong("CreatedWorldTime", this.createdWorldTime);
      tag.putLong("DueWorldTime", this.dueWorldTime);
      tag.putLong("ResolvedWorldTime", this.resolvedWorldTime);
      tag.putLong("LastReminderWorldTime", this.lastReminderWorldTime);
      return tag;
   }

   public static CompanionPromise fromTag(CompoundTag tag) {
      return new CompanionPromise(
              tag.getString("Summary"),
              CompanionPromiseCategory.fromName(tag.getString("Category")),
              CompanionPromiseStatus.fromName(tag.getString("Status")),
              tag.getLong("CreatedWorldTime"),
              tag.getLong("DueWorldTime"),
              tag.getLong("ResolvedWorldTime"),
              tag.getLong("LastReminderWorldTime")
      );
   }
}
