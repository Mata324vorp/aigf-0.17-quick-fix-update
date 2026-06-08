package com.litewer.aigf.network.packet;

import com.litewer.aigf.command.CompanionCommands;
import com.litewer.aigf.data.CompanionWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ServerboundCompleteWelcomePacket(boolean spawnNow) {

   public static void encode(ServerboundCompleteWelcomePacket packet, FriendlyByteBuf buffer) {
      buffer.writeBoolean(packet.spawnNow);
   }

   public static ServerboundCompleteWelcomePacket decode(FriendlyByteBuf buffer) {
      return new ServerboundCompleteWelcomePacket(buffer.readBoolean());
   }

   public static void handle(ServerboundCompleteWelcomePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null) {
            CompanionWorldData data = CompanionWorldData.get(sender.serverLevel());
            CompanionWorldData.StoredCompanionState state = data.getOrCreate(sender.getUUID());
            state.markWelcomeSeen();
            data.setDirty();
            if (packet.spawnNow) {
               CompanionCommands.spawnForPlayer(sender);
            }
         }
      });
      context.setPacketHandled(true);
   }
}