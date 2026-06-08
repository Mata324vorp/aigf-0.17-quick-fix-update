package com.litewer.aigf.network.packet;

import com.litewer.aigf.data.CompanionPromiseCategory;
import com.litewer.aigf.data.CompanionWorldData;
import com.litewer.aigf.entity.CompanionActionIntent;
import com.litewer.aigf.entity.CompanionEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public record ServerboundDirectActionPacket(int entityId, String actionName) {
   public static void encode(ServerboundDirectActionPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
      buffer.m_130072_(packet.actionName, 64);
   }

   public static ServerboundDirectActionPacket decode(FriendlyByteBuf buffer) {
      return new ServerboundDirectActionPacket(buffer.readInt(), buffer.m_130136_(64));
   }

   public static void handle(ServerboundDirectActionPacket packet, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null && sender.m_9236_().m_6815_(packet.entityId) instanceof CompanionEntity companion) {
            if (sender.m_20148_().equals(companion.getOwnerUuid())) {
               CompanionActionIntent intent = CompanionActionIntent.fromName(packet.actionName);
               companion.applyActionIntent(intent, sender);
               CompanionWorldData data = CompanionWorldData.get(sender.m_284548_());
               CompanionWorldData.StoredCompanionState state = data.getOrCreate(sender.m_20148_());
               CompanionWorldData.StoredCompanionState.PromiseEffect promiseEffect = CompanionWorldData.StoredCompanionState.PromiseEffect.none();
               if (intent == CompanionActionIntent.SET_HOME || intent == CompanionActionIntent.GO_HOME && state.hasHome()) {
                  promiseEffect = state.resolvePromise(sender.m_284548_().m_46467_(), CompanionPromiseCategory.HOME);
               }

               if (promiseEffect.hasImpact()) {
                  companion.setMood(companion.getMood() + promiseEffect.moodDelta());
                  companion.setTrust(companion.getTrust() + promiseEffect.trustDelta());
               }

               state.lastAction = intent.name();
               data.m_77762_();
               companion.persistState(true);
            }
         }
      });
      context.setPacketHandled(true);
   }
}
