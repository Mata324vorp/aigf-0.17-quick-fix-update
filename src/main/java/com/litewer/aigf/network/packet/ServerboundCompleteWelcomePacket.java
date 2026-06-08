package com.litewer.aigf.network.packet;

import com.litewer.aigf.command.CompanionCommands;
import com.litewer.aigf.data.CompanionWorldData;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public record ServerboundCompleteWelcomePacket(boolean spawnNow) {
   public static void encode(ServerboundCompleteWelcomePacket packet, FriendlyByteBuf buffer) {
      buffer.writeBoolean(packet.spawnNow);
   }

   public static ServerboundCompleteWelcomePacket decode(FriendlyByteBuf buffer) {
      return new ServerboundCompleteWelcomePacket(buffer.readBoolean());
   }

   public static void handle(ServerboundCompleteWelcomePacket packet, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null) {
            CompanionWorldData data = CompanionWorldData.get(sender.m_284548_());
            CompanionWorldData.StoredCompanionState state = data.getOrCreate(sender.m_20148_());
            state.markWelcomeSeen();
            data.m_77762_();
            if (packet.spawnNow) {
               CompanionCommands.spawnForPlayer(sender);
            }
         }
      });
      context.setPacketHandled(true);
   }
}
