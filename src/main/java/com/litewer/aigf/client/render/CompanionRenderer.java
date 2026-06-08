package com.litewer.aigf.client.render;

import com.litewer.aigf.client.skin.CompanionSkinManager;
import com.litewer.aigf.entity.CompanionEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public final class CompanionRenderer extends LivingEntityRenderer<CompanionEntity, CompanionModel> {
   public CompanionRenderer(Context context) {
      super(context, new CompanionModel(context.m_174023_(ModelLayers.f_171166_)), 0.45F);
      this.m_115326_(
         new HumanoidArmorLayer(
            this, new HumanoidModel(context.m_174023_(ModelLayers.f_171167_)), new HumanoidModel(context.m_174023_(ModelLayers.f_171168_)), context.m_266367_()
         )
      );
      this.m_115326_(new ItemInHandLayer(this, context.m_234598_()));
   }

   public ResourceLocation getTextureLocation(CompanionEntity entity) {
      return CompanionSkinManager.resolveTexture(entity.getActiveSkinId());
   }
}
