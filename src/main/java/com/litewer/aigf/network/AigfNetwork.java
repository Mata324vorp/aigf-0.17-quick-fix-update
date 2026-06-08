package com.litewer.aigf.network;

import com.litewer.aigf.data.CompanionSnapshot;
import com.litewer.aigf.network.packet.ClientboundGiftPromptPacket;
import com.litewer.aigf.network.packet.ClientboundOpenCompanionScreenPacket;
import com.litewer.aigf.network.packet.ClientboundOpenWelcomePacket;
import com.litewer.aigf.network.packet.ClientboundSyncCompanionPacket;
import com.litewer.aigf.network.packet.ServerboundApplySkinPacket;
import com.litewer.aigf.network.packet.ServerboundChatTurnPacket;
import com.litewer.aigf.network.packet.ServerboundCompanionInventoryPacket;
import com.litewer.aigf.network.packet.ServerboundCompleteWelcomePacket;
import com.litewer.aigf.network.packet.ServerboundDirectActionPacket;
import com.litewer.aigf.network.packet.ServerboundGiftFromHandPacket;
import com.litewer.aigf.network.packet.ServerboundGiftReactionPacket;
import com.litewer.aigf.network.packet.ServerboundRenameCompanionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AigfNetwork {
   private static final String PROTOCOL = "1";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      ResourceLocation.fromNamespaceAndPath("aigf", "main"), () -> "1", "1"::equals, "1"::equals
   );
   private static boolean registered;

   private AigfNetwork() {
   }

   public static void register() {
      if (!registered) {
         registered = true;
         int id = 0;
         CHANNEL.registerMessage(
            id++,
            ClientboundOpenWelcomePacket.class,
            ClientboundOpenWelcomePacket::encode,
            ClientboundOpenWelcomePacket::decode,
            ClientboundOpenWelcomePacket::handle
         );
         CHANNEL.registerMessage(
            id++,
            ClientboundOpenCompanionScreenPacket.class,
            ClientboundOpenCompanionScreenPacket::encode,
            ClientboundOpenCompanionScreenPacket::decode,
            ClientboundOpenCompanionScreenPacket::handle
         );
         CHANNEL.registerMessage(
            id++,
            ClientboundSyncCompanionPacket.class,
            ClientboundSyncCompanionPacket::encode,
            ClientboundSyncCompanionPacket::decode,
            ClientboundSyncCompanionPacket::handle
         );
         CHANNEL.registerMessage(
            id++,
            ClientboundGiftPromptPacket.class,
            ClientboundGiftPromptPacket::encode,
            ClientboundGiftPromptPacket::decode,
            ClientboundGiftPromptPacket::handle
         );
         CHANNEL.registerMessage(
            id++,
            ServerboundCompleteWelcomePacket.class,
            ServerboundCompleteWelcomePacket::encode,
            ServerboundCompleteWelcomePacket::decode,
            ServerboundCompleteWelcomePacket::handle
         );
         CHANNEL.registerMessage(
            id++, ServerboundChatTurnPacket.class, ServerboundChatTurnPacket::encode, ServerboundChatTurnPacket::decode, ServerboundChatTurnPacket::handle
         );
         CHANNEL.registerMessage(
            id++, ServerboundApplySkinPacket.class, ServerboundApplySkinPacket::encode, ServerboundApplySkinPacket::decode, ServerboundApplySkinPacket::handle
         );
         CHANNEL.registerMessage(
            id++,
            ServerboundDirectActionPacket.class,
            ServerboundDirectActionPacket::encode,
            ServerboundDirectActionPacket::decode,
            ServerboundDirectActionPacket::handle
         );
         CHANNEL.registerMessage(
            id++,
            ServerboundGiftFromHandPacket.class,
            ServerboundGiftFromHandPacket::encode,
            ServerboundGiftFromHandPacket::decode,
            ServerboundGiftFromHandPacket::handle
         );
         CHANNEL.registerMessage(
            id++,
            ServerboundGiftReactionPacket.class,
            ServerboundGiftReactionPacket::encode,
            ServerboundGiftReactionPacket::decode,
            ServerboundGiftReactionPacket::handle
         );
         CHANNEL.registerMessage(
            id++,
            ServerboundRenameCompanionPacket.class,
            ServerboundRenameCompanionPacket::encode,
            ServerboundRenameCompanionPacket::decode,
            ServerboundRenameCompanionPacket::handle
         );
         CHANNEL.registerMessage(
            id++,
            ServerboundCompanionInventoryPacket.class,
            ServerboundCompanionInventoryPacket::encode,
            ServerboundCompanionInventoryPacket::decode,
            ServerboundCompanionInventoryPacket::handle
         );
      }
   }

   public static void sendToPlayer(ServerPlayer player, Object message) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
   }

   public static void syncCompanion(ServerPlayer player, int entityId, CompanionSnapshot snapshot) {
      sendToPlayer(player, new ClientboundSyncCompanionPacket(entityId, snapshot));
   }
}
