package com.litewer.aigf.network.packet;

import com.litewer.aigf.data.CompanionPromiseCategory;
import com.litewer.aigf.data.CompanionWorldData;
import com.litewer.aigf.entity.CompanionEmotion;
import com.litewer.aigf.entity.CompanionEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent.Context;

public record ServerboundGiftReactionPacket(
   int entityId, String assistantText, String emotionName, int moodDelta, int trustDelta, String memoryFact, String careHint
) {
   public static void encode(ServerboundGiftReactionPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
      buffer.m_130072_(packet.assistantText, 4096);
      buffer.m_130072_(packet.emotionName, 64);
      buffer.writeInt(packet.moodDelta);
      buffer.writeInt(packet.trustDelta);
      buffer.m_130072_(packet.memoryFact == null ? "" : packet.memoryFact, 1024);
      buffer.m_130072_(packet.careHint == null ? "" : packet.careHint, 1024);
   }

   public static ServerboundGiftReactionPacket decode(FriendlyByteBuf buffer) {
      return new ServerboundGiftReactionPacket(
         buffer.readInt(), buffer.m_130136_(4096), buffer.m_130136_(64), buffer.readInt(), buffer.readInt(), buffer.m_130136_(1024), buffer.m_130136_(1024)
      );
   }

   public static void handle(ServerboundGiftReactionPacket packet, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(
         () -> {
            if (sender != null && sender.m_9236_().m_6815_(packet.entityId) instanceof CompanionEntity companion) {
               if (sender.m_20148_().equals(companion.getOwnerUuid())) {
                  int moodDelta = Mth.m_14045_(packet.moodDelta, -15, 15);
                  int trustDelta = Mth.m_14045_(packet.trustDelta, -12, 12);
                  companion.setEmotion(CompanionEmotion.fromName(packet.emotionName));
                  companion.setMood(companion.getMood() + moodDelta);
                  companion.setTrust(companion.getTrust() + trustDelta);
                  companion.registerConversationImpact(moodDelta, trustDelta);
                  companion.triggerGreeting();
                  CompanionWorldData data = CompanionWorldData.get(sender.m_284548_());
                  CompanionWorldData.StoredCompanionState state = data.getOrCreate(sender.m_20148_());
                  state.addTurn("assistant", packet.assistantText);
                  state.addFact(packet.memoryFact);
                  CompanionWorldData.StoredCompanionState.PromiseEffect promiseEffect = state.resolvePromise(
                     sender.m_284548_().m_46467_(), CompanionPromiseCategory.GIFT
                  );
                  if (promiseEffect.hasImpact()) {
                     companion.setMood(companion.getMood() + promiseEffect.moodDelta());
                     companion.setTrust(companion.getTrust() + promiseEffect.trustDelta());
                  }

                  state.lastAction = "GIFT";
                  if ((state.lastCareHint == null || state.lastCareHint.isBlank() || !state.lastCareHint.startsWith("hint.promise."))
                     && packet.careHint != null
                     && !packet.careHint.isBlank()) {
                     state.lastCareHint = packet.careHint;
                  }

                  if (moodDelta > 0 && trustDelta > 0 && state.resentment > 0) {
                     state.reconciliationProgress = Math.min(100, state.reconciliationProgress + moodDelta + trustDelta);
                     state.resentment = Math.max(0, state.resentment - Math.max(2, (moodDelta + trustDelta) / 2));
                  }

                  data.m_77762_();
                  companion.persistState(true);
               }
            }
         }
      );
      context.setPacketHandled(true);
   }
}
