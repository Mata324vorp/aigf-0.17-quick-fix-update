package com.litewer.aigf.network.packet;

import com.litewer.aigf.data.CompanionWorldData;
import com.litewer.aigf.entity.CompanionEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ServerboundRenameCompanionPacket(int entityId, String companionName) {

   public static void encode(ServerboundRenameCompanionPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
      buffer.writeUtf(packet.companionName, 64);
   }

   public static ServerboundRenameCompanionPacket decode(FriendlyByteBuf buffer) {
      return new ServerboundRenameCompanionPacket(buffer.readInt(), buffer.readUtf(64));
   }

   public static void handle(ServerboundRenameCompanionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null && sender.level().getEntity(packet.entityId) instanceof CompanionEntity companion) {
            if (sender.getUUID().equals(companion.getOwnerUuid())) {
               companion.setCompanionName(packet.companionName);
               CompanionWorldData.StoredCompanionState state = CompanionWorldData.get(sender.serverLevel()).getOrCreate(sender.getUUID());
               state.applyRename(companion.getCompanionName());
               companion.persistState(true);
            }
         }
      });
      context.setPacketHandled(true);
   }
}