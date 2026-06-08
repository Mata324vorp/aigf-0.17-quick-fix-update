package com.litewer.aigf.client.screen.widget;

import com.litewer.aigf.client.screen.AigfUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class AigfSoftEditBox extends EditBox {
   private float focusLerp;

   public AigfSoftEditBox(Font font, int x, int y, int width, int height, Component message) {
      super(font, x, y, width, height, message);
      this.setBordered(false);
      this.setTextColor(-133381);
      this.setTextColorUneditable(-2505259);
   }

   @Override
   public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      this.focusLerp = Mth.lerp(0.24F, this.focusLerp, this.isFocused() ? 1.0F : 0.0F);
      int outline = AigfUi.colorAlpha(-1596986, 0.14F + this.focusLerp * 0.36F);
      int top = AigfUi.colorAlpha(-10866092, 0.4F + this.focusLerp * 0.2F);
      int bottom = AigfUi.colorAlpha(-15134435, 0.92F);
      AigfUi.drawRoundedPanel(guiGraphics, this.getX(), this.getY(), this.width, this.height, top, bottom, outline);
      super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
   }
}