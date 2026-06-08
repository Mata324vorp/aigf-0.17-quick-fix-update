package com.litewer.aigf.client;

import com.litewer.aigf.client.render.CompanionRenderer;
import com.litewer.aigf.client.settings.ClientSettingsManager;
import com.litewer.aigf.client.skin.CompanionSkinManager;
import com.litewer.aigf.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = "aigf", value = Dist.CLIENT, bus = Bus.MOD)
public final class AigfClient {
   private AigfClient() {
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      event.enqueueWork(() -> {
         ClientSettingsManager.get();
         CompanionSkinManager.initialize();
      });
   }

   @SubscribeEvent
   public static void registerRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType)ModEntities.COMPANION.get(), CompanionRenderer::new);
   }
}
