package com.litewer.aigf.client.screen;

import com.litewer.aigf.client.ClientLocalization;
import com.litewer.aigf.client.screen.widget.AigfSoftButton;
import com.litewer.aigf.network.AigfNetwork;
import com.litewer.aigf.network.packet.ServerboundCompleteWelcomePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CompanionWelcomeScreen extends Screen {
   private final String companionName;
   private final boolean hasCompanion;
   private List<Page> pages = List.of();
   private int currentPage;
   private Button previousButton;
   private Button nextButton;
   private Button spawnNowButton;
   private Button laterButton;
   private boolean completionSent;

   public CompanionWelcomeScreen(String companionName, boolean hasCompanion) {
      super(Component.literal("AIGF Welcome"));
      this.companionName = companionName != null && !companionName.isBlank() ? companionName.trim() : "Aira";
      this.hasCompanion = hasCompanion;
   }

   @Override
   protected void init() {
      super.init();
      this.pages = this.buildPages();
      int panelWidth = this.panelWidth();
      int panelHeight = this.panelHeight();
      int panelX = (this.width - panelWidth) / 2;
      int panelY = (this.height - panelHeight) / 2;
      int footerY = panelY + panelHeight - 34;

      this.previousButton = this.addRenderableWidget(
              new AigfSoftButton(panelX + 18, footerY, 86, 20,
                      Component.literal(t("Back", "Назад")),
                      button -> this.changePage(-1), () -> false, -10866092)
      );

      this.nextButton = this.addRenderableWidget(
              new AigfSoftButton(panelX + panelWidth - 104, footerY, 86, 20,
                      Component.literal(t("Next", "Дальше")),
                      button -> this.changePage(1), () -> false, -2986325)
      );

      int choiceWidth = (panelWidth - 48) / 2;
      int firstChoiceX = panelX + 18;
      int secondChoiceX = panelX + panelWidth - 18 - choiceWidth;

      this.spawnNowButton = this.addRenderableWidget(
              new AigfSoftButton(firstChoiceX, footerY, choiceWidth, 20,
                      Component.literal(t("Call me right now", "Позвать меня сейчас")),
                      button -> this.completeWelcome(true), () -> false, -2986325)
      );

      this.laterButton = this.addRenderableWidget(
              new AigfSoftButton(secondChoiceX, footerY, choiceWidth, 20,
                      Component.literal(t("Maybe later", "Позже")),
                      button -> this.completeWelcome(false), () -> false, -10866092)
      );

      this.updateButtons();
   }

   private void changePage(int delta) {
      this.currentPage = Mth.clamp(this.currentPage + delta, 0, this.pages.size() - 1);
      this.updateButtons();
   }

   private void completeWelcome(boolean spawnNow) {
      this.completionSent = true;
      AigfNetwork.CHANNEL.sendToServer(new ServerboundCompleteWelcomePacket(spawnNow));
      this.onClose();
   }

   private void updateButtons() {
      boolean lastPage = this.isLastPage();
      this.previousButton.visible = this.currentPage > 0;
      this.previousButton.active = this.currentPage > 0;
      this.nextButton.visible = !lastPage;
      this.nextButton.active = !lastPage;
      this.spawnNowButton.visible = lastPage;
      this.spawnNowButton.active = lastPage;
      this.laterButton.visible = lastPage;
      this.laterButton.active = lastPage;
   }

   private List<Page> buildPages() {
      String playerName;
      if (Minecraft.getInstance().player != null) {
         playerName = Minecraft.getInstance().player.getDisplayName().getString();
      } else {
         playerName = Minecraft.getInstance().getUser().getName();
      }

      String titleOne = String.format(Locale.ROOT, t("Hi, %s.", "Привет, %s."), playerName);
      String pageOneBody = t(
              "I'm " + this.companionName +
                      ". I'm your companion in this world: not just a menu, but a presence beside you.\n\n" +
                      "You can talk to me, argue with me, make up with me, give me gifts and set a home for me.\n\n" +
                      "Let me show you the basics before we begin.",
              "Я " + this.companionName +
                      ". Я твоя спутница в этом мире: не просто меню, а живое присутствие рядом.\n\n" +
                      "Со мной можно говорить, спорить, мириться, дарить мне вещи и задавать мне дом.\n\n" +
                      "Сначала я быстро покажу тебе самое важное."
      );

      String pageTwoBody = t(
              "Right-click me to open my main menu.\n" +
                      "In normal Minecraft chat you can write: @aigf <message>.\n" +
                      "I remember tone: kindness brings us closer, rough words can hurt.\n\n" +
                      "I answer in my own voice, and my mood changes with the conversation.",
              "ПКМ по мне открывает главное меню.\n" +
                      "В обычном чате Minecraft можно писать: @aigf <сообщение>.\n" +
                      "Я запоминаю тон разговора: доброта сближает, а грубые слова могут ранить.\n\n" +
                      "Я отвечаю своим голосом, и моё настроение меняется от разговора."
      );

      String pageThreeBody = t(
              "From my menu you can tell me: follow, stay, sit, set home, go home.\n" +
                      "In the inventory tab you can rename me, equip me, and gift an item from your main hand.\n" +
                      "I react to gifts in unique ways, not always positively.\n\n" +
                      "Useful and sweet things warm me up. Strange junk can confuse or annoy me.",
              "Из моего меню ты можешь сказать мне: за мной, стоять, сидеть, поставить дом, идти домой.\n" +
                      "Во вкладке инвентаря меня можно переименовать, выдать мне экипировку и подарить предмет из главной руки.\n" +
                      "На подарки я реагирую по-разному и не всегда положительно.\n\n" +
                      "Полезные и милые вещи мне нравятся, а странный мусор может удивить или разозлить."
      );

      String pageFourBody = this.hasCompanion
              ? t(
              "I'm already tied to this world, so I can come to you right now.\n\n" +
                      "If you want a calm start, choose later and call me whenever you're ready with /aigf spawn.\n\n" +
                      "What shall we do?",
              "Я уже связана с этим миром, так что могу прийти к тебе прямо сейчас.\n\n" +
                      "Если хочешь начать спокойно, выбери позже и позови меня в любой момент через /aigf spawn.\n\n" +
                      "Что решаем?"
      )
              : t(
              "If you want, I can appear beside you right now.\n\n" +
                      "If not, that's okay. You can always call me later with /aigf spawn.\n\n" +
                      "Ready to invite me in?",
              "Если хочешь, я могу появиться рядом с тобой прямо сейчас.\n\n" +
                      "Если нет, ничего страшного. Ты всегда сможешь позвать меня позже через /aigf spawn.\n\n" +
                      "Готов(а) пригласить меня?"
      );

      return List.of(
              new Page(titleOne, t("A small first meeting", "Небольшое первое знакомство"), pageOneBody, -1596986),
              new Page(t("How to talk to me", "Как со мной общаться"), t("Simple, but alive", "Просто, но живо"), pageTwoBody, -3424778),
              new Page(t("What I can do", "Что я умею"), t("Commands, gifts and presence", "Команды, подарки и присутствие"), pageThreeBody, -3607324),
              new Page(t("Ready?", "Ну что, начнём?"), t("Your choice decides the first moment", "Твой выбор решит наш первый момент"), pageFourBody, -2986325)
      );
   }

   @Override
   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      float time = (Minecraft.getInstance().player == null ? 0.0F : Minecraft.getInstance().player.tickCount) + partialTick;
      AigfUi.drawBackdrop(guiGraphics, this.width, this.height, time);

      int panelWidth = this.panelWidth();
      int panelHeight = this.panelHeight();
      int panelX = (this.width - panelWidth) / 2;
      int panelY = (this.height - panelHeight) / 2;
      int accent = this.pages.get(this.currentPage).accentColor();

      AigfUi.drawRoundedPanel(
              guiGraphics,
              panelX, panelY,
              panelWidth, panelHeight,
              AigfUi.colorAlpha(-10866092, 0.9F),
              AigfUi.colorAlpha(-15594732, 0.94F),
              AigfUi.colorAlpha(AigfUi.lighten(accent, 0.1F), 0.5F)
      );

      guiGraphics.fillGradient(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + 46,
              AigfUi.colorAlpha(accent, 0.22F), AigfUi.colorAlpha(-15594732, 0.02F));

      Page page = this.pages.get(this.currentPage);
      guiGraphics.drawString(this.font, Component.literal(page.title()), panelX + 18, panelY + 16, -133381, false);
      guiGraphics.drawString(this.font, Component.literal(page.subtitle()), panelX + 18, panelY + 30, -2505259, false);

      int quoteX = panelX + 18;
      int quoteY = panelY + 58;
      int quoteWidth = panelWidth - 36;
      int quoteHeight = panelHeight - 108;

      AigfUi.drawRoundedPanel(
              guiGraphics,
              quoteX, quoteY,
              quoteWidth, quoteHeight,
              AigfUi.colorAlpha(-14477276, 0.78F),
              AigfUi.colorAlpha(-15594732, 0.88F),
              AigfUi.colorAlpha(AigfUi.lighten(accent, 0.2F), 0.24F)
      );

      guiGraphics.drawString(this.font, Component.literal(this.companionName), quoteX + 14, quoteY + 12, accent, false);
      this.renderPageBody(guiGraphics, page.body(), quoteX + 14, quoteY + 28, quoteWidth - 28, quoteHeight - 40);
      this.renderPageDots(guiGraphics, panelX, panelY, panelWidth, panelHeight, accent);
      this.renderPageCounter(guiGraphics, panelX, panelY, panelWidth);

      super.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   private void renderPageBody(GuiGraphics guiGraphics, String body, int x, int y, int width, int maxHeight) {
      List<FormattedCharSequence> lines = new ArrayList<>();
      for (String paragraph : body.split("\\n\\n")) {
         if (!lines.isEmpty()) {
            lines.add(FormattedCharSequence.EMPTY);
         }
         lines.addAll(this.font.split(Component.literal(paragraph), width));
      }
      int lineHeight = 12;
      int maxLines = Math.max(1, maxHeight / lineHeight);
      for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
         guiGraphics.drawString(this.font, lines.get(i), x, y + i * lineHeight, -133381, false);
      }
   }

   private void renderPageDots(GuiGraphics guiGraphics, int panelX, int panelY, int panelWidth, int panelHeight, int accent) {
      int totalWidth = this.pages.size() * 12 - 4;
      int startX = panelX + (panelWidth - totalWidth) / 2;
      int dotY = panelY + panelHeight - 46;
      for (int i = 0; i < this.pages.size(); i++) {
         int color = i == this.currentPage ? AigfUi.colorAlpha(accent, 0.95F) : AigfUi.colorAlpha(-2505259, 0.3F);
         AigfUi.drawRoundedPanel(guiGraphics, startX + i * 12, dotY, 8, 8, color, color, color);
      }
   }

   private void renderPageCounter(GuiGraphics guiGraphics, int panelX, int panelY, int panelWidth) {
      String counter = String.format(Locale.ROOT, t("Page %d/%d", "Страница %d/%d"), this.currentPage + 1, this.pages.size());
      guiGraphics.drawString(this.font, Component.literal(counter),
              panelX + panelWidth - this.font.width(counter) - 18, panelY + 16, -2505259, false);
   }

   private boolean isLastPage() {
      return this.currentPage >= this.pages.size() - 1;
   }

   private int panelWidth() {
      return Math.min(440, Math.max(220, this.width - 32));
   }

   private int panelHeight() {
      return Math.min(300, Math.max(220, this.height - 24));
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 256 && !this.isLastPage()) {
         // Escape while not last page: do nothing (prevents closing)
         return true;
      }
      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return this.isLastPage();
   }

   @Override
   public void onClose() {
      if (this.isLastPage()) {
         if (!this.completionSent) {
            this.completionSent = true;
            AigfNetwork.CHANNEL.sendToServer(new ServerboundCompleteWelcomePacket(false));
         }
         super.onClose();
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   private static String t(String english, String russian) {
      return ClientLocalization.text(english, russian);
   }

   private record Page(String title, String subtitle, String body, int accentColor) {}
}