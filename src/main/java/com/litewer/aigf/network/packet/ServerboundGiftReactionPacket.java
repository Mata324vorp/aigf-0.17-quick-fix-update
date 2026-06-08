package com.litewer.aigf.network.packet;

import com.litewer.aigf.data.CompanionPromiseCategory;
import com.litewer.aigf.data.CompanionWorldData;
import com.litewer.aigf.entity.CompanionEmotion;
import com.litewer.aigf.entity.CompanionEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ServerboundGiftReactionPacket(
        int entityId,
        String assistantText,
        String emotionName,
        int moodDelta,
        int trustDelta,
        String memoryFact,
        String careHint
) {
   public static void encode(ServerboundGiftReactionPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
      buffer.writeUtf(packet.assistantText, 4096);
      buffer.writeUtf(packet.emotionName, 64);
      buffer.writeInt(packet.moodDelta);
      buffer.writeInt(packet.trustDelta);
      buffer.writeUtf(packet.memoryFact == null ? "" : packet.memoryFact, 1024);
      buffer.writeUtf(packet.careHint == null ? "" : packet.careHint, 1024);
   }

   public static ServerboundGiftReactionPacket decode(FriendlyByteBuf buffer) {
      return new ServerboundGiftReactionPacket(
              buffer.readInt(),
              buffer.readUtf(4096),
              buffer.readUtf(64),
              buffer.readInt(),
              buffer.readInt(),
              buffer.readUtf(1024),
              buffer.readUtf(1024)
      );
   }

   public static void handle(ServerboundGiftReactionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null && sender.level().getEntity(packet.entityId) instanceof CompanionEntity companion) {
            if (sender.getUUID().equals(companion.getOwnerUuid())) {
               int moodDelta = Mth.clamp(packet.moodDelta, -15, 15);
               int trustDelta = Mth.clamp(packet.trustDelta, -12, 12);

               companion.setEmotion(CompanionEmotion.fromName(packet.emotionName));
               companion.setMood(companion.getMood() + moodDelta);
               companion.setTrust(companion.getTrust() + trustDelta);
               companion.registerConversationImpact(moodDelta, trustDelta);
               companion.triggerGreeting();

               CompanionWorldData data = CompanionWorldData.get(sender.serverLevel());
               CompanionWorldData.StoredCompanionState state = data.getOrCreate(sender.getUUID());

               state.addTurn("assistant", packet.assistantText);
               state.addFact(packet.memoryFact);

               CompanionWorldData.StoredCompanionState.PromiseEffect promiseEffect = state.resolvePromise(
                       sender.serverLevel().getGameTime(), CompanionPromiseCategory.GIFT
               );
               if (promiseEffect.hasImpact()) {
                  companion.setMood(companion.getMood() + promiseEffect.moodDelta());
                  companion.setTrust(companion.getTrust() + promiseEffect.trustDelta());
               }

               state.lastAction = "GIFT";
               if ((state.lastCareHint == null || state.lastCareHint.isBlank() || !state.lastCareHint.startsWith("hint.promise."))
                       && packet.careHint != null && !packet.careHint.isBlank()) {
                  state.lastCareHint = packet.careHint;
               }

               if (moodDelta > 0 && trustDelta > 0 && state.resentment > 0) {
                  state.reconciliationProgress = Math.min(100, state.reconciliationProgress + moodDelta + trustDelta);
                  state.resentment = Math.max(0, state.resentment - Math.max(2, (moodDelta + trustDelta) / 2));
               }

               data.setDirty();
               companion.persistState(true);
            }
         }
      });
      context.setPacketHandled(true);
   }
}