package com.litewer.aigf;

import com.litewer.aigf.command.CompanionCommands;
import com.litewer.aigf.entity.CompanionEntity;
import com.litewer.aigf.network.AigfNetwork;
import com.litewer.aigf.registry.ModEntities;
import com.litewer.aigf.server.CompanionPlayerEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod("aigf")
public final class AigfMod {
   public static final String MOD_ID = "aigf";
   public static final Logger LOGGER = LogUtils.getLogger();

   public AigfMod(FMLJavaModLoadingContext context) {
      IEventBus modBus = context.getModEventBus();
      ModEntities.ENTITY_TYPES.register(modBus);
      modBus.addListener(this::onCommonSetup);
      modBus.addListener(this::onCreateAttributes);
      AigfNetwork.register();
      MinecraftForge.EVENT_BUS.addListener(CompanionCommands::register);
      MinecraftForge.EVENT_BUS.addListener(CompanionPlayerEvents::onPlayerLoggedIn);
      MinecraftForge.EVENT_BUS.addListener(CompanionPlayerEvents::onPlayerLoggedOut);
   }

   private void onCommonSetup(FMLCommonSetupEvent event) {
      LOGGER.info("Initializing AIGF Companion");
   }

   private void onCreateAttributes(EntityAttributeCreationEvent event) {
      event.put((EntityType)ModEntities.COMPANION.get(), CompanionEntity.createAttributes().build());
   }
}
