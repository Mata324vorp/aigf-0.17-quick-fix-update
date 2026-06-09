package com.litewer.aigf.network.packet;

import com.litewer.aigf.entity.CompanionEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ServerboundGiftFromHandPacket(int entityId) {

   public static void encode(ServerboundGiftFromHandPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
   }

   public static ServerboundGiftFromHandPacket decode(FriendlyByteBuf buffer) {
      return new ServerboundGiftFromHandPacket(buffer.readInt());
   }

   public static void handle(ServerboundGiftFromHandPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null && sender.level().getEntity(packet.entityId) instanceof CompanionEntity companion) {
            if (sender.getUUID().equals(companion.getOwnerUuid())) {
               companion.giveGiftFromMainHand(sender);
            }
         }
      });
      context.setPacketHandled(true);
   }
}