package com.litewer.aigf.event; // Ajusta el paquete a tu proyecto

import com.litewer.aigf.entity.CompanionEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "aigf") // Reemplaza "aigf" con el ID de tu mod
public class ModEvents {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof CompanionEntity companion) {
            // Le decimos al juego que la interacción ha sido manejada por nosotros
            // y evitamos que el código original de Mob.interact() se ejecute, solucionando así el error.
            event.setCancellationResult(companion.handlePlayerInteraction(event.getEntity(), event.getHand()));
            event.setCanceled(true);
        }
    }
}