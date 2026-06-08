package com.litewer.aigf.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(modid = "aigf", value = Dist.CLIENT, bus = Bus.FORGE)
final class AigfClientForgeEvents {
   private AigfClientForgeEvents() {
   }

   @SubscribeEvent
   public static void onClientChat(ClientChatEvent event) {
      String message = event.getMessage().trim();
      if (message.startsWith("@aigf ")) {
         event.setCanceled(true);
         CompanionChatBridge.handleChatMessage(message.substring(6).trim());
      }
   }
}
