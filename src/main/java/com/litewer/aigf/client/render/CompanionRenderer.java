package com.litewer.aigf.client.render;

import com.litewer.aigf.client.skin.CompanionSkinManager;
import com.litewer.aigf.entity.CompanionEntity;
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

   @Override
   public ResourceLocation getTextureLocation(CompanionEntity entity) {
      return CompanionSkinManager.resolveTexture(entity.getActiveSkinId());
   }
}