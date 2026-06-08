package com.litewer.aigf.network.packet;

import com.litewer.aigf.entity.CompanionEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public record ServerboundGiftFromHandPacket(int entityId) {
   public static void encode(ServerboundGiftFromHandPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
   }

   public static ServerboundGiftFromHandPacket decode(FriendlyByteBuf buffer) {
      return new ServerboundGiftFromHandPacket(buffer.readInt());
   }

   public static void handle(ServerboundGiftFromHandPacket packet, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null && sender.m_9236_().m_6815_(packet.entityId) instanceof CompanionEntity companion) {
            if (sender.m_20148_().equals(companion.getOwnerUuid())) {
               companion.giveGiftFromMainHand(sender);
            }
         }
      });
      context.setPacketHandled(true);
   }
}
