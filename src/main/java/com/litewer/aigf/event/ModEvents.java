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
        // Log para saber si el evento se dispara
        System.out.println("[AIGF] EVENTO EntityInteract disparado");

        if (event.getTarget() == null) {
            System.out.println("[AIGF] Target es null");
            return;
        }

        System.out.println("[AIGF] Target clase: " + event.getTarget().getClass().getName());

        if (event.getTarget() instanceof CompanionEntity companion) {
            System.out.println("[AIGF] Es CompanionEntity, llamando handlePlayerInteraction");
            InteractionResult result = companion.handlePlayerInteraction(event.getEntity(), event.getHand());
            System.out.println("[AIGF] Resultado de handlePlayerInteraction: " + result);
            event.setCancellationResult(result);
            event.setCanceled(true);
        } else {
            System.out.println("[AIGF] Target NO es CompanionEntity: " + event.getTarget().getClass().getName());
        }
    }

    // Opcional: también escucha el evento específico si el anterior no funciona
    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        System.out.println("[AIGF] EVENTO EntityInteractSpecific disparado");
        if (event.getTarget() instanceof CompanionEntity companion) {
            InteractionResult result = companion.handlePlayerInteraction(event.getEntity(), event.getHand());
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }
}