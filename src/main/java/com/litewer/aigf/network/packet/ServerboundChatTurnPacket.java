package com.litewer.aigf.network.packet;

import com.litewer.aigf.conversation.ConversationMoodAnalyzer;
import com.litewer.aigf.conversation.PromiseAnalyzer;
import com.litewer.aigf.data.CompanionPromiseCategory;
import com.litewer.aigf.data.CompanionWorldData;
import com.litewer.aigf.entity.CompanionActionIntent;
import com.litewer.aigf.entity.CompanionConflictState;
import com.litewer.aigf.entity.CompanionEmotion;
import com.litewer.aigf.entity.CompanionEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ServerboundChatTurnPacket(
        int entityId,
        String userText,
        String assistantText,
        String emotionName,
        String actionName,
        String memoryFact,
        String careHint,
        int moodDelta,
        int trustDelta
) {
   public static void encode(ServerboundChatTurnPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
      buffer.writeUtf(packet.userText, 4096);
      buffer.writeUtf(packet.assistantText, 4096);
      buffer.writeUtf(packet.emotionName, 64);
      buffer.writeUtf(packet.actionName, 64);
      buffer.writeUtf(packet.memoryFact == null ? "" : packet.memoryFact, 1024);
      buffer.writeUtf(packet.careHint == null ? "" : packet.careHint, 1024);
      buffer.writeInt(packet.moodDelta);
      buffer.writeInt(packet.trustDelta);
   }

   public static ServerboundChatTurnPacket decode(FriendlyByteBuf buffer) {
      return new ServerboundChatTurnPacket(
              buffer.readInt(),
              buffer.readUtf(4096),
              buffer.readUtf(4096),
              buffer.readUtf(64),
              buffer.readUtf(64),
              buffer.readUtf(1024),
              buffer.readUtf(1024),
              buffer.readInt(),
              buffer.readInt()
      );
   }

   public static void handle(ServerboundChatTurnPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null && sender.level().getEntity(packet.entityId) instanceof CompanionEntity companion) {
            if (sender.getUUID().equals(companion.getOwnerUuid())) {
               ConversationMoodAnalyzer.Analysis analysis = ConversationMoodAnalyzer.analyze(packet.userText);
               CompanionEmotion emotion = CompanionEmotion.fromName(packet.emotionName);
               CompanionActionIntent actionIntent = CompanionActionIntent.fromName(packet.actionName);

               companion.setEmotion(emotion);
               companion.setMood(companion.getMood() + analysis.moodDelta());
               companion.setTrust(companion.getTrust() + analysis.trustDelta());
               companion.setEnergy(companion.getEnergy() - 1);
               companion.registerConversationImpact(analysis.moodDelta(), analysis.trustDelta());
               companion.applyActionIntent(actionIntent, sender);

               CompanionWorldData data = CompanionWorldData.get(sender.serverLevel());
               CompanionWorldData.StoredCompanionState state = data.getOrCreate(sender.getUUID());

               state.applyConversationImpact(analysis, sender.serverLevel().getGameTime());
               state.addTurn("user", packet.userText);
               state.addTurn("assistant", packet.assistantText);
               state.addFact(packet.memoryFact);

               PromiseAnalyzer.PromiseSeed promiseSeed = PromiseAnalyzer.extract(packet.userText, sender.serverLevel().getGameTime());
               CompanionWorldData.StoredCompanionState.PromiseEffect promiseEffect = CompanionWorldData.StoredCompanionState.PromiseEffect.none();

               if (promiseSeed != null) {
                  promiseEffect = state.registerPromise(promiseSeed, sender.serverLevel().getGameTime());
               } else if (analysis.isApology()) {
                  promiseEffect = state.resolvePromise(sender.serverLevel().getGameTime(), CompanionPromiseCategory.CARE, CompanionPromiseCategory.TALK);
               } else if (analysis.isPositive() || analysis.isCollaborative()) {
                  promiseEffect = state.resolvePromise(sender.serverLevel().getGameTime(), CompanionPromiseCategory.TALK, CompanionPromiseCategory.CARE);
               }

               if (promiseEffect.hasImpact()) {
                  companion.setMood(companion.getMood() + promiseEffect.moodDelta());
                  companion.setTrust(companion.getTrust() + promiseEffect.trustDelta());
               }

               state.lastAction = actionIntent.name();
               String modelCareHint = packet.careHint == null ? "" : packet.careHint;
               if (!state.lastCareHint.startsWith("hint.promise.")
                       && (state.lastCareHint.isBlank()
                       || (!analysis.hasNegative() && state.getConflictState() == CompanionConflictState.OPEN && !modelCareHint.isBlank()))) {
                  state.lastCareHint = modelCareHint;
               }

               data.setDirty();
               companion.persistState(true);
            }
         }
      });
      context.setPacketHandled(true);
   }
}