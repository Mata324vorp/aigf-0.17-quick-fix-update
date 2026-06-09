package com.litewer.aigf.client.screen.widget;

import com.litewer.aigf.client.screen.AigfUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.BooleanSupplier;

public class AigfSoftButton extends Button {
   private final BooleanSupplier selectedSupplier;
   private final int accentColor;
   private float hoverLerp;

   public AigfSoftButton(int x, int y, int width, int height, Component message,
                         OnPress onPress, BooleanSupplier selectedSupplier, int accentColor) {
      super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
      this.selectedSupplier = selectedSupplier;
      this.accentColor = accentColor;
   }

   @Override
   protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      this.hoverLerp = Mth.lerp(0.2F, this.hoverLerp, this.isHovered() ? 1.0F : 0.0F);
      boolean selected = this.selectedSupplier != null && this.selectedSupplier.getAsBoolean();

      int topColor = selected
              ? AigfUi.colorAlpha(AigfUi.lighten(this.accentColor, 0.14F), 0.92F)
              : AigfUi.colorAlpha(-10866092, 0.6F + this.hoverLerp * 0.15F);

      int bottomColor = selected
              ? AigfUi.colorAlpha(this.accentColor, 0.9F)
              : AigfUi.colorAlpha(-14477276, 0.88F);

      int outline = selected
              ? AigfUi.colorAlpha(-133381, 0.38F)
              : AigfUi.colorAlpha(-2505259, 0.1F + this.hoverLerp * 0.15F);

      AigfUi.drawRoundedPanel(guiGraphics,
              this.getX(), this.getY(),
              this.width, this.height,
              topColor, bottomColor, outline);

      if (!this.active) {
         guiGraphics.fill(this.getX(), this.getY(),
                 this.getX() + this.width, this.getY() + this.height,
                 0x66000000); // 0x66 = 102, 0x000000 = negro (translúcido)
      }

      Font font = Minecraft.getInstance().font;
      int textColor = this.active ? -133381 : -2505259;
      guiGraphics.drawCenteredString(font, this.getMessage(),
              this.getX() + this.width / 2,
              this.getY() + (this.height - 8) / 2,
              textColor);
   }
}