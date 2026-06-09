package com.litewer.aigf.network.packet;

import com.litewer.aigf.data.CompanionPromiseCategory;
import com.litewer.aigf.data.CompanionWorldData;
import com.litewer.aigf.entity.CompanionActionIntent;
import com.litewer.aigf.entity.CompanionEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ServerboundDirectActionPacket(int entityId, String actionName) {

   public static void encode(ServerboundDirectActionPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
      buffer.writeUtf(packet.actionName, 64);
   }

   public static ServerboundDirectActionPacket decode(FriendlyByteBuf buffer) {
      return new ServerboundDirectActionPacket(buffer.readInt(), buffer.readUtf(64));
   }

   public static void handle(ServerboundDirectActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null && sender.level().getEntity(packet.entityId) instanceof CompanionEntity companion) {
            if (sender.getUUID().equals(companion.getOwnerUuid())) {
               CompanionActionIntent intent = CompanionActionIntent.fromName(packet.actionName);
               companion.applyActionIntent(intent, sender);

               CompanionWorldData data = CompanionWorldData.get(sender.serverLevel());
               CompanionWorldData.StoredCompanionState state = data.getOrCreate(sender.getUUID());

               CompanionWorldData.StoredCompanionState.PromiseEffect promiseEffect = CompanionWorldData.StoredCompanionState.PromiseEffect.none();
               if (intent == CompanionActionIntent.SET_HOME || (intent == CompanionActionIntent.GO_HOME && state.hasHome())) {
                  promiseEffect = state.resolvePromise(sender.serverLevel().getGameTime(), CompanionPromiseCategory.HOME);
               }

               if (promiseEffect.hasImpact()) {
                  companion.setMood(companion.getMood() + promiseEffect.moodDelta());
                  companion.setTrust(companion.getTrust() + promiseEffect.trustDelta());
               }

               state.lastAction = intent.name();
               data.setDirty();
               companion.persistState(true);
            }
         }
      });
      context.setPacketHandled(true);
   }
}