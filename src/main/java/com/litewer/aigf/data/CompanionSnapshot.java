package com.litewer.aigf.data;

import com.litewer.aigf.entity.CompanionCommandMode;
import com.litewer.aigf.entity.CompanionConflictState;
import com.litewer.aigf.entity.CompanionEmotion;
import com.litewer.aigf.entity.CompanionRelationshipStage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public record CompanionSnapshot(
   UUID ownerUuid,
   String companionName,
   int mood,
   int energy,
   int trust,
   int resentment,
   int reconciliationProgress,
   String activeSkinId,
   String lastAction,
   String lastCareHint,
   CompanionCommandMode commandMode,
   CompanionEmotion emotion,
   CompanionConflictState conflictState,
   CompanionRelationshipStage relationshipStage,
   List<ConversationTurn> recentTurns,
   List<String> importantFacts,
   List<CompanionPromise> promises,
   long lastSeenWorldTime,
   String homeDimension,
   int homeX,
   int homeY,
   int homeZ
) {
   public List<CompanionPromise> pendingPromises() {
      return this.promises.stream().filter(CompanionPromise::isPending).toList();
   }

   public boolean hasHome() {
      return this.homeDimension != null && !this.homeDimension.isBlank();
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      if (this.ownerUuid != null) {
         tag.putUUID("Owner", this.ownerUuid);
      }
      tag.putString("CompanionName", this.companionName);
      tag.putInt("Mood", this.mood);
      tag.putInt("Energy", this.energy);
      tag.putInt("Trust", this.trust);
      tag.putInt("Resentment", this.resentment);
      tag.putInt("ReconciliationProgress", this.reconciliationProgress);
      tag.putString("SkinId", this.activeSkinId);
      tag.putString("LastAction", this.lastAction);
      tag.putString("LastCareHint", this.lastCareHint);
      tag.putString("CommandMode", this.commandMode.name());
      tag.putString("Emotion", this.emotion.name());
      tag.putString("ConflictState", this.conflictState.name());
      tag.putString("RelationshipStage", this.relationshipStage.name());
      tag.putLong("LastSeenWorldTime", this.lastSeenWorldTime);
      if (this.hasHome()) {
         tag.putString("HomeDimension", this.homeDimension);
         tag.putInt("HomeX", this.homeX);
         tag.putInt("HomeY", this.homeY);
         tag.putInt("HomeZ", this.homeZ);
      }
      ListTag recentTurnsTag = new ListTag();
      for (ConversationTurn turn : this.recentTurns) {
         recentTurnsTag.add(turn.toTag());
      }
      tag.put("RecentTurns", recentTurnsTag);
      ListTag factsTag = new ListTag();
      for (String fact : this.importantFacts) {
         factsTag.add(StringTag.valueOf(fact));
      }
      tag.put("ImportantFacts", factsTag);
      ListTag promisesTag = new ListTag();
      for (CompanionPromise promise : this.promises) {
         promisesTag.add(promise.toTag());
      }
      tag.put("Promises", promisesTag);
      return tag;
   }

   public static CompanionSnapshot fromTag(CompoundTag tag) {
      List<ConversationTurn> recentTurns = new ArrayList<>();
      for (Tag element : tag.getList("RecentTurns", 10)) {
         recentTurns.add(ConversationTurn.fromTag((CompoundTag) element));
      }
      List<String> importantFacts = new ArrayList<>();
      for (Tag element : tag.getList("ImportantFacts", 8)) {
         importantFacts.add(element.getAsString());
      }
      List<CompanionPromise> promises = new ArrayList<>();
      for (Tag element : tag.getList("Promises", 10)) {
         promises.add(CompanionPromise.fromTag((CompoundTag) element));
      }
      return new CompanionSnapshot(
              tag.hasUUID("Owner") ? tag.getUUID("Owner") : null,
              tag.contains("CompanionName") ? tag.getString("CompanionName") : "Aira",
              tag.getInt("Mood"),
              tag.getInt("Energy"),
              tag.getInt("Trust"),
              tag.contains("Resentment") ? tag.getInt("Resentment") : 0,
              tag.contains("ReconciliationProgress") ? tag.getInt("ReconciliationProgress") : 0,
              tag.getString("SkinId"),
              tag.getString("LastAction"),
              tag.getString("LastCareHint"),
              CompanionCommandMode.valueOf(tag.getString("CommandMode")),
              CompanionEmotion.fromName(tag.getString("Emotion")),
              tag.contains("ConflictState") ? CompanionConflictState.fromName(tag.getString("ConflictState")) : CompanionConflictState.OPEN,
              tag.contains("RelationshipStage")
                      ? CompanionRelationshipStage.fromName(tag.getString("RelationshipStage"))
                      : CompanionRelationshipStage.fromValues(tag.getInt("Trust"), tag.getInt("Mood"), tag.contains("Resentment") ? tag.getInt("Resentment") : 0),
              recentTurns,
              importantFacts,
              promises,
              tag.getLong("LastSeenWorldTime"),
              tag.contains("HomeDimension") ? tag.getString("HomeDimension") : "",
              tag.getInt("HomeX"),
              tag.getInt("HomeY"),
              tag.getInt("HomeZ")
      );
   }
}
