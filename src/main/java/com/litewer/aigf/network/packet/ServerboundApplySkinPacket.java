package com.litewer.aigf.network.packet;

import com.litewer.aigf.entity.CompanionEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public record ServerboundApplySkinPacket(int entityId, String skinId) {
   public static void encode(ServerboundApplySkinPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
      buffer.m_130072_(packet.skinId, 256);
   }

   public static ServerboundApplySkinPacket decode(FriendlyByteBuf buffer) {
      return new ServerboundApplySkinPacket(buffer.readInt(), buffer.m_130136_(256));
   }

   public static void handle(ServerboundApplySkinPacket packet, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null && sender.m_9236_().m_6815_(packet.entityId) instanceof CompanionEntity companion) {
            if (sender.m_20148_().equals(companion.getOwnerUuid())) {
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
