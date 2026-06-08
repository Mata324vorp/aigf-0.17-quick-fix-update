package com.litewer.aigf.network.packet;

import com.litewer.aigf.entity.CompanionEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ServerboundApplySkinPacket(int entityId, String skinId) {

   public static void encode(ServerboundApplySkinPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
      buffer.writeUtf(packet.skinId, 256);
   }

   public static ServerboundApplySkinPacket decode(FriendlyByteBuf buffer) {
      return new ServerboundApplySkinPacket(buffer.readInt(), buffer.readUtf(256));
   }

   public static void handle(ServerboundApplySkinPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null && sender.level().getEntity(packet.entityId) instanceof CompanionEntity companion) {
            if (sender.getUUID().equals(companion.getOwnerUuid())) {
               String normalizedSkinId = packet.skinId == null ? "" : packet.skinId.trim();
               if (normalizedSkinId.startsWith("builtin:") || normalizedSkinId.startsWith("local:") || normalizedSkinId.startsWith("imported:")) {
                  companion.setActiveSkinId(normalizedSkinId);
                  companion.persistState(true);
               }
            }
         }
      });
      context.setPacketHandled(true);
   }
}