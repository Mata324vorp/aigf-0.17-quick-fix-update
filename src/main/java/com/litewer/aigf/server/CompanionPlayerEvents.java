package com.litewer.aigf.server;

import com.litewer.aigf.data.CompanionWorldData;
import com.litewer.aigf.entity.CompanionEntity;
import com.litewer.aigf.network.AigfNetwork;
import com.litewer.aigf.network.packet.ClientboundOpenWelcomePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;

public final class CompanionPlayerEvents {
   private CompanionPlayerEvents() {}

   public static void onPlayerLoggedIn(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         CompanionWorldData.StoredCompanionState state = CompanionWorldData.get(player.serverLevel()).getOrCreate(player.getUUID());
         if (!state.hasSeenWelcome()) {
            AigfNetwork.sendToPlayer(player, new ClientboundOpenWelcomePacket(state.companionName, state.companionUuid != null));
         }
         if (state.companionUuid != null && player.serverLevel().getEntity(state.companionUuid) instanceof CompanionEntity companion) {
            CompanionTabListManager.syncForOwner(player, companion);
         }
      }
   }

   public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         CompanionTabListManager.clear(player.getUUID());
      }
   }
}