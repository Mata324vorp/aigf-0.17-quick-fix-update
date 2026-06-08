package com.litewer.aigf.client.render;

import com.litewer.aigf.entity.CompanionCommandMode;
import com.litewer.aigf.entity.CompanionEmotion;
import com.litewer.aigf.entity.CompanionEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class CompanionModel extends PlayerModel<CompanionEntity> {
   public CompanionModel(ModelPart root) {
      super(root, true);
   }

   public void setupAnim(CompanionEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
      super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
      // Si no tienes un método setAnimating, elimina la siguiente línea o coméntala
      // this.setAnimating(true); // anteriormente this.m_8009_(true);

      float breathing = Mth.cos(ageInTicks * 0.12F) * 0.03F;
      this.head.xRot += breathing;      // f_102810_ -> head
      this.rightArm.xRot -= breathing;  // f_102812_ -> rightArm (o leftArm, según tu modelo)
      this.body.xRot += breathing;      // f_102811_ -> body

      if (entity.getCommandMode() == CompanionCommandMode.SIT) {
         this.leftLeg.xRot = -1.35F;   // f_102813_ -> leftLeg
         this.rightLeg.xRot = -1.35F;  // f_102814_ -> rightLeg
         this.leftLeg.yRot = 0.2F;
         this.rightLeg.yRot = -0.2F;
         this.body.xRot = -0.35F;
         this.rightArm.xRot = -0.35F;
      }

      if (entity.getEmotion() == CompanionEmotion.TIRED) {
         this.head.xRot += 0.12F;
         this.body.xRot = Math.min(this.body.xRot, -0.5F);
         this.rightArm.xRot = Math.min(this.rightArm.xRot, -0.5F);
      } else if (entity.getEmotion() == CompanionEmotion.SAD) {
         this.head.xRot += 0.1F;
         this.body.zRot -= 0.08F;
         this.rightArm.zRot += 0.08F;
      } else if (entity.getEmotion() == CompanionEmotion.HAPPY) {
         this.body.zRot += 0.05F;
         this.rightArm.zRot -= 0.05F;
      }

      float greeting = entity.getGreetingAnimationProgress();
      if (greeting > 0.0F) {
         this.body.xRot = -1.1F + Mth.cos(ageInTicks * 0.7F) * 0.45F * greeting;
         this.body.yRot = -0.1F;
      }
   }
}
