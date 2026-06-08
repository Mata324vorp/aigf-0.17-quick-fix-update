package com.litewer.aigf.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public final class AigfUi {
   public static final ResourceLocation SOFT_FONT = ResourceLocation.fromNamespaceAndPath("aigf", "soft_ui");
   public static final int ROSE_50 = -133381;
   public static final int ROSE_100 = -532496;
   public static final int ROSE_200 = -868392;
   public static final int ROSE_300 = -1596986;
   public static final int ROSE_500 = -2986325;
   public static final int PLUM_700 = -10866092;
   public static final int PLUM_900 = -14478305;
   public static final int NIGHT_900 = -15594732;
   public static final int NIGHT_800 = -15134435;
   public static final int NIGHT_700 = -14477276;
   public static final int MIST = -2505259;
   public static final int SKY = -3424778;
   public static final int MINT = -3607324;

   private AigfUi() {}

   public static Component text(String text) {
      return Component.literal(text).withStyle(style -> style.withFont(SOFT_FONT));
   }

   public static void drawBackdrop(GuiGraphics guiGraphics, int width, int height, float time) {
      guiGraphics.fill(0, 0, width, height, -435353583);
      drawShaderQuad(
              guiGraphics,
              -80.0F + wave(time, 42.0F, 22.0F),
              -40.0F,
              width * 0.62F,
              height * 0.72F,
              colorAlpha(-2986325, 0.18F),
              colorAlpha(-10866092, 0.05F),
              colorAlpha(-14478305, 0.02F),
              colorAlpha(-1596986, 0.08F)
      );
      drawShaderQuad(
              guiGraphics,
              width * 0.35F,
              height * 0.08F + wave(time * 0.7F, 36.0F, 17.0F),
              width + 120.0F,
              height * 0.88F,
              colorAlpha(-3424778, 0.1F),
              colorAlpha(-1596986, 0.05F),
              colorAlpha(-14478305, 0.02F),
              colorAlpha(-2986325, 0.08F)
      );
      drawGlow(guiGraphics, width - 210.0F, 120.0F + wave(time, 18.0F, 11.0F), 230.0F, colorAlpha(-1596986, 0.1F));
      drawGlow(guiGraphics, 150.0F, height - 150.0F + wave(time * 0.9F, 24.0F, 8.0F), 180.0F, colorAlpha(-3424778, 0.08F));
      drawGlow(guiGraphics, width * 0.55F, height * 0.46F + wave(time * 1.4F, 12.0F, 13.0F), 160.0F, colorAlpha(-3607324, 0.05F));
   }

   public static void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, int topColor, int bottomColor, int outlineColor) {
      drawShadow(guiGraphics, x - 8, y - 8, x + width + 8, y + height + 10, 671088640);
      guiGraphics.fillGradient(x, y, x + width, y + height, topColor, bottomColor);
      guiGraphics.fill(x, y, x + width, y + 1, colorAlpha(-133381, 0.22F));
      guiGraphics.fill(x, y + height - 1, x + width, y + height, colorAlpha(-14478305, 0.3F));
      guiGraphics.fill(x, y, x + 1, y + height, outlineColor);
      guiGraphics.fill(x + width - 1, y, x + width, y + height, outlineColor);
   }

   public static void drawRoundedPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, int topColor, int bottomColor, int outlineColor) {
      drawShadow(guiGraphics, x - 6, y - 6, x + width + 6, y + height + 8, 536870912);
      guiGraphics.fillGradient(x + 2, y, x + width - 2, y + height, topColor, bottomColor);
      guiGraphics.fillGradient(x, y + 2, x + width, y + height - 2, topColor, bottomColor);
      guiGraphics.fill(x + 3, y, x + width - 3, y + 1, colorAlpha(-133381, 0.18F));
      guiGraphics.fill(x + 3, y + height - 1, x + width - 3, y + height, colorAlpha(-14478305, 0.3F));
      guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 2, outlineColor);
      guiGraphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, outlineColor);
      guiGraphics.fill(x, y + 2, x + 1, y + height - 2, outlineColor);
      guiGraphics.fill(x + width - 1, y + 2, x + width, y + height - 2, outlineColor);
   }

   public static void drawCard(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean active) {
      int top = active ? -868933323 : -1289217494;
      int bottom = active ? -870050525 : -1290268646;
      int outline = active ? colorAlpha(-1596986, 0.65F) : colorAlpha(-2505259, 0.16F);
      drawPanel(guiGraphics, x, y, width, height, top, bottom, outline);
   }

   public static void drawProgressBar(GuiGraphics guiGraphics, Font font, int x, int y, int width, String label, int value, int fillColor) {
      guiGraphics.drawString(font, text(label + "  " + value + "/100"), x, y, -133381, false);
      int barY = y + 18;
      drawPanel(guiGraphics, x, barY, width, 12, -1507715034, -803992296, colorAlpha(-2505259, 0.12F));
      int fillWidth = Math.max(8, (int) ((width - 4) * Mth.clamp(value / 100.0F, 0.0F, 1.0F)));
      guiGraphics.fillGradient(x + 2, barY + 2, x + 2 + fillWidth, barY + 10, lighten(fillColor, 0.18F), fillColor);
      guiGraphics.fill(x + 2, barY + 2, x + 2 + fillWidth, barY + 3, colorAlpha(-133381, 0.32F));
   }

   public static void drawSectionTitle(GuiGraphics guiGraphics, Font font, String title, String subtitle, int x, int y, int width) {
      guiGraphics.drawString(font, text(title), x, y, -133381, false);
      if (subtitle != null && !subtitle.isBlank()) {
         int maxWidth = Math.max(80, width);
         int lineY = y + 16;
         for (FormattedCharSequence line : font.split(text(subtitle), maxWidth)) {
            guiGraphics.drawString(font, line, x, lineY, -2505259, false);
            lineY += 11;
         }
      }
   }

   public static int colorAlpha(int color, float alpha) {
      return Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24 | (color & 0xFFFFFF);
   }

   public static int lighten(int color, float amount) {
      int a = FastColor.ARGB32.alpha(color);
      int r = Mth.clamp((int) (FastColor.ARGB32.red(color) + (255 - FastColor.ARGB32.red(color)) * amount), 0, 255);
      int g = Mth.clamp((int) (FastColor.ARGB32.green(color) + (255 - FastColor.ARGB32.green(color)) * amount), 0, 255);
      int b = Mth.clamp((int) (FastColor.ARGB32.blue(color) + (255 - FastColor.ARGB32.blue(color)) * amount), 0, 255);
      return FastColor.ARGB32.color(a, r, g, b);
   }

   public static float wave(float time, float amplitude, float speed) {
      return (float) Math.sin(time / speed) * amplitude;
   }

   private static void drawShadow(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
      guiGraphics.fill(x1, y1, x2, y2, color);
   }

   private static void drawGlow(GuiGraphics guiGraphics, float centerX, float centerY, float radius, int color) {
      drawShaderQuad(guiGraphics, centerX - radius, centerY - radius, centerX + radius, centerY + radius, 0, color, 0, color);
   }

   private static void drawShaderQuad(GuiGraphics guiGraphics, float x1, float y1, float x2, float y2,
                                      int topLeft, int bottomLeft, int bottomRight, int topRight) {
      Matrix4f matrix = guiGraphics.pose().last().pose();
      RenderSystem.enableBlend();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      BufferBuilder builder = Tesselator.getInstance().getBuilder();
      builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      vertex(builder, matrix, x1, y1, topLeft);
      vertex(builder, matrix, x1, y2, bottomLeft);
      vertex(builder, matrix, x2, y2, bottomRight);
      vertex(builder, matrix, x2, y1, topRight);
      BufferUploader.drawWithShader(builder.end());
      RenderSystem.disableBlend();
   }

   private static void vertex(BufferBuilder builder, Matrix4f matrix, float x, float y, int color) {
      int a = FastColor.ARGB32.alpha(color);
      int r = FastColor.ARGB32.red(color);
      int g = FastColor.ARGB32.green(color);
      int b = FastColor.ARGB32.blue(color);
      builder.vertex(matrix, x, y, 0.0F).color(r, g, b, a).endVertex();
   }
}