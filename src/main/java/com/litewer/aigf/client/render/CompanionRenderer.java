package com.litewer.aigf.client.render;

import com.litewer.aigf.client.skin.CompanionSkinManager;
import com.litewer.aigf.entity.CompanionCommandMode;
import com.litewer.aigf.entity.CompanionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public final class CompanionRenderer extends LivingEntityRenderer<CompanionEntity, CompanionModel> {

   public CompanionRenderer(EntityRendererProvider.Context context) {
      super(context, new CompanionModel(context.bakeLayer(ModelLayers.PLAYER)), 0.45F);
      this.addLayer(new HumanoidArmorLayer<>(
              this,
              new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
              new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
              context.getModelManager()
      ));
      this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
   }

   // --- AQUÍ ESTÁ EL AJUSTE PARA LA LEVITACIÓN ---
   @Override
   protected void setupRotations(CompanionEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
      super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);

      // Si la entidad está en modo sentada, bajamos todo el renderizado en el eje Y
      if (entity.getCommandMode() == CompanionCommandMode.SIT) {
         // Valores típicos para sentarse: x=0.0, y=-0.4 a -0.5, z=0.0
         poseStack.translate(0.0D, -0.4D, 0.0D);
      }
   }
   // ----------------------------------------------

   @Override
   public ResourceLocation getTextureLocation(CompanionEntity entity) {
      return CompanionSkinManager.resolveTexture(entity.getActiveSkinId());
   }
}