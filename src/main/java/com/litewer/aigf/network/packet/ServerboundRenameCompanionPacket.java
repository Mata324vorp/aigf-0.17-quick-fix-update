package com.litewer.aigf.network.packet;

import com.litewer.aigf.data.CompanionWorldData;
import com.litewer.aigf.entity.CompanionEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public record ServerboundRenameCompanionPacket(int entityId, String companionName) {
   public static void encode(ServerboundRenameCompanionPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
      buffer.m_130072_(packet.companionName, 64);
   }

   public static ServerboundRenameCompanionPacket decode(FriendlyByteBuf buffer) {
      return new ServerboundRenameCompanionPacket(buffer.readInt(), buffer.m_130136_(64));
   }

   public static void handle(ServerboundRenameCompanionPacket packet, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null && sender.m_9236_().m_6815_(packet.entityId) instanceof CompanionEntity companion) {
            if (sender.m_20148_().equals(companion.getOwnerUuid())) {
               companion.setCompanionName(packet.companionName);
               CompanionWorldData.StoredCompanionState state = CompanionWorldData.get(sender.m_284548_()).getOrCreate(sender.m_20148_());
               state.applyRename(companion.getCompanionName());
               companion.persistState(true);
            }
         }
      });
      context.setPacketHandled(true);
   }
}
