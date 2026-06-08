package com.litewer.aigf.registry;

import com.litewer.aigf.entity.CompanionEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
   public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "aigf");
   public static final RegistryObject<EntityType<CompanionEntity>> COMPANION = ENTITY_TYPES.register(
           "companion", () -> Builder.of(CompanionEntity::new, MobCategory.CREATURE)
                   .sized(0.6F, 1.8F)
                   .clientTrackingRange(10)
                   .updateInterval(1)
                   .build("aigf:companion")
   );

   private ModEntities() {
   }
}
