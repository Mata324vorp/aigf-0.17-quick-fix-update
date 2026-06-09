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

    @Override
    public void setupAnim(CompanionEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {

        // 1. EL TRUCO PARA SENTARSE: Usar el sistema nativo de Minecraft
        // Al activar 'riding', el super.setupAnim se encargará mágicamente de
        // rotar las piernas y el torso a la pose perfecta de sentarse.
        this.riding = (entity.getCommandMode() == CompanionCommandMode.SIT);

        // Llamamos al super DESPUÉS de definir si está sentada
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // 2. Respiración (Suave y aditiva)
        float breathing = Mth.cos(ageInTicks * 0.12F) * 0.03F;
        this.head.xRot += breathing;
        this.body.xRot += breathing;
        this.rightArm.xRot -= breathing;
        this.leftArm.xRot -= breathing; // Añadido para simetría

        // 3. Emociones (Usar suma aditiva para evitar 'snaps' o tirones)
        if (entity.getEmotion() == CompanionEmotion.TIRED) {
            this.head.xRot += 0.15F;
            this.body.xRot += 0.1F;
            this.rightArm.xRot += 0.1F;
            this.leftArm.xRot += 0.1F;
        } else if (entity.getEmotion() == CompanionEmotion.SAD) {
            this.head.xRot += 0.1F;
            this.body.xRot += 0.05F; // Inclinación hacia adelante
        } else if (entity.getEmotion() == CompanionEmotion.HAPPY) {
            this.head.xRot -= 0.05F; // Cabeza un poco en alto
            this.rightArm.zRot -= 0.05F;
            this.leftArm.zRot += 0.05F;
        }

        // 4. Saludo: Usar Lerp (Interpolación) para una transición suave
        float greeting = entity.getGreetingAnimationProgress();
        if (greeting > 0.0F) {
            // Calculamos a dónde queremos que lleguen los brazos
            float targetRightArmX = -1.8F + (Mth.cos(ageInTicks * 0.7F) * 0.3F);
            float targetRightArmZ = 0.2F;
            float targetLeftArmX = -0.5F;

            // Mth.lerp suaviza la transición desde la pose actual hacia la pose de saludo
            // usando 'greeting' (0.0 a 1.0) como porcentaje de la animación.
            this.rightArm.xRot = Mth.lerp(greeting, this.rightArm.xRot, targetRightArmX);
            this.rightArm.zRot = Mth.lerp(greeting, this.rightArm.zRot, targetRightArmZ);
            this.leftArm.xRot = Mth.lerp(greeting, this.leftArm.xRot, targetLeftArmX);

            // Eliminamos el reseteo brusco del torso (body.xRot = 0.0F).
            // Eso era lo que causaba el giro loco de 180 grados al interferir con el LookControl de Minecraft.
        }
    }
}