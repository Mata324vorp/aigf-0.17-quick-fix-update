package com.litewer.aigf.client.screen.widget;

import com.litewer.aigf.client.screen.AigfUi;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class AigfSoftButton extends Button {
   private final BooleanSupplier selectedSupplier;
   private final int accentColor;
   private float hoverLerp;

   public AigfSoftButton(int x, int y, int width, int height, Component message, OnPress onPress, BooleanSupplier selectedSupplier, int accentColor) {
      super(x, y, width, height, message, onPress, f_252438_);
      this.selectedSupplier = selectedSupplier;
      this.accentColor = accentColor;
   }

   protected void m_87963_(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      this.hoverLerp = Mth.m_14179_(0.2F, this.hoverLerp, this.m_198029_() ? 1.0F : 0.0F);
      boolean selected = this.selectedSupplier != null && this.selectedSupplier.getAsBoolean();
      int topColor = selected ? AigfUi.colorAlpha(AigfUi.lighten(this.accentColor, 0.14F), 0.92F) : AigfUi.colorAlpha(-10866092, 0.6F + this.hoverLerp * 0.15F);
      int bottomColor = selected ? AigfUi.colorAlpha(this.accentColor, 0.9F) : AigfUi.colorAlpha(-14477276, 0.88F);
      int outline = selected ? AigfUi.colorAlpha(-133381, 0.38F) : AigfUi.colorAlpha(-2505259, 0.1F + this.hoverLerp * 0.15F);
      AigfUi.drawRoundedPanel(guiGraphics, this.m_252754_(), this.m_252907_(), this.f_93618_, this.f_93619_, topColor, bottomColor, outline);
      if (!this.f_93623_) {
         guiGraphics.m_280509_(this.m_252754_(), this.m_252907_(), this.m_252754_() + this.f_93618_, this.m_252907_() + this.f_93619_, 1711998992);
      }

      Font font = Minecraft.m_91087_().f_91062_;
      int textColor = this.f_93623_ ? -133381 : -2505259;
      guiGraphics.m_280653_(font, this.m_6035_(), this.m_252754_() + this.f_93618_ / 2, this.m_252907_() + (this.f_93619_ - 8) / 2, textColor);
   }
}
