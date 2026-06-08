package com.litewer.aigf.event;

import com.litewer.aigf.entity.CompanionEntity;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "aigf")
public class ModEvents {
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof CompanionEntity companion) {
            // Llamar al método que reemplazó a interact()
            InteractionResult result = companion.handlePlayerInteraction(event.getEntity(), event.getHand());
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }
}