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
      this.m_94182_(false);
      this.m_94202_(-133381);
      this.m_94205_(-2505259);
   }

   public void m_87963_(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      this.focusLerp = Mth.m_14179_(0.24F, this.focusLerp, this.m_93696_() ? 1.0F : 0.0F);
      int outline = AigfUi.colorAlpha(-1596986, 0.14F + this.focusLerp * 0.36F);
      int top = AigfUi.colorAlpha(-10866092, 0.4F + this.focusLerp * 0.2F);
      int bottom = AigfUi.colorAlpha(-15134435, 0.92F);
      AigfUi.drawRoundedPanel(guiGraphics, this.m_252754_(), this.m_252907_(), this.f_93618_, this.f_93619_, top, bottom, outline);
      super.m_87963_(guiGraphics, mouseX, mouseY, partialTick);
   }
}
