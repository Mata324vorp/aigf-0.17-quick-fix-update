package com.litewer.aigf.client.screen;

import com.litewer.aigf.client.ClientLocalization;
import com.litewer.aigf.client.CompanionClientState;
import com.litewer.aigf.client.ai.CompanionAiResult;
import com.litewer.aigf.client.ai.OpenAiClient;
import com.litewer.aigf.client.screen.widget.AigfSoftButton;
import com.litewer.aigf.client.screen.widget.AigfSoftEditBox;
import com.litewer.aigf.client.settings.ClientSettingsManager;
import com.litewer.aigf.client.skin.CompanionSkinManager;
import com.litewer.aigf.conversation.ConversationMoodAnalyzer;
import com.litewer.aigf.data.CompanionSnapshot;
import com.litewer.aigf.data.ConversationTurn;
import com.litewer.aigf.entity.CompanionActionIntent;
import com.litewer.aigf.entity.CompanionEntity;
import com.litewer.aigf.network.AigfNetwork;
import com.litewer.aigf.network.packet.ServerboundApplySkinPacket;
import com.litewer.aigf.network.packet.ServerboundChatTurnPacket;
import com.litewer.aigf.network.packet.ServerboundCompanionInventoryPacket;
import com.litewer.aigf.network.packet.ServerboundDirectActionPacket;
import com.litewer.aigf.network.packet.ServerboundGiftFromHandPacket;
import com.litewer.aigf.network.packet.ServerboundRenameCompanionPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class CompanionModernScreen extends Screen {
   private static final InventorySlotView[] INVENTORY_SLOTS = new InventorySlotView[]{
           new InventorySlotView(EquipmentSlot.MAINHAND),
           new InventorySlotView(EquipmentSlot.OFFHAND),
           new InventorySlotView(EquipmentSlot.HEAD),
           new InventorySlotView(EquipmentSlot.CHEST),
           new InventorySlotView(EquipmentSlot.LEGS),
           new InventorySlotView(EquipmentSlot.FEET)
   };
   private final CompanionEntity companion;
   private CompanionSnapshot snapshot;
   private final List<String> chatLines = new ArrayList<>();
   private final List<AbstractWidget> chromeWidgets = new ArrayList<>();
   private final List<AbstractWidget> chatWidgets = new ArrayList<>();
   private final List<AbstractWidget> inventoryWidgets = new ArrayList<>();
   private final List<AbstractWidget> appearanceWidgets = new ArrayList<>();
   private final List<AbstractWidget> settingsWidgets = new ArrayList<>();
   private ScreenTab activeTab = ScreenTab.CHAT;
   private EditBox chatInput;
   private Button sendButton;
   private Button followButton;
   private Button stayButton;
   private Button sitButton;
   private Button setHomeButton;
   private Button goHomeButton;
   private EditBox nameBox;
   private Button saveNameButton;
   private Button giftFromHandButton;
   private Button previousSkinButton;
   private Button nextSkinButton;
   private Button applySkinButton;
   private Button reloadSkinsButton;
   private Button importSkinButton;
   private EditBox importNameBox;
   private EditBox apiKeyBox;
   private EditBox modelIdBox;
   private Button previousModelButton;
   private Button nextModelButton;
   private EditBox timeoutBox;
   private EditBox contextTurnsBox;
   private Button saveSettingsButton;
   private Button testConnectionButton;
   private List<String> skinIds = new ArrayList<>();
   private int selectedSkinIndex;
   private String statusLine = "";
   private boolean requestInFlight;

   public CompanionModernScreen(CompanionEntity companion, CompanionSnapshot snapshot) {
      super(AigfUi.text("AIGF"));
      this.companion = companion;
      this.snapshot = snapshot;
      this.rebuildChatLines();
   }

   public int getEntityId() {
      return this.companion.getId();
   }

   public void applySnapshot(CompanionSnapshot snapshot) {
      this.snapshot = snapshot;
      this.rebuildChatLines();
      this.statusLine = this.localizeCareHint(snapshot.lastCareHint());
      this.selectCurrentSkin();
      if (this.nameBox != null && !this.nameBox.isFocused()) {
         this.nameBox.setValue(snapshot.companionName());
      }
   }

   @Override
   protected void init() {
      super.init();
      this.chromeWidgets.clear();
      this.chatWidgets.clear();
      this.inventoryWidgets.clear();
      this.appearanceWidgets.clear();
      this.settingsWidgets.clear();
      UiLayout layout = this.layout();
      this.buildSidebarWidgets(layout);
      this.buildChatWidgets(layout);
      this.buildInventoryWidgets(layout);
      this.buildAppearanceWidgets(layout);
      this.buildSettingsWidgets(layout);
      this.reloadSkinList();
      this.loadSettings();
      this.updateTabVisibility();
   }

   private void buildSidebarWidgets(UiLayout layout) {
      int tabWidth = layout.sidebarWidth - 28;
      int tabX = layout.sidebarX + 14;
      int tabY = layout.sidebarY + 146;

      for (ScreenTab tab : ScreenTab.values()) {
         this.chromeWidgets.add(this.addRenderableWidget(
                 new AigfSoftButton(tabX, tabY, tabWidth, 32, AigfUi.text(tab.label()),
                         button -> this.switchTab(tab), () -> this.activeTab == tab, -2986325)
         ));
         tabY += 38;
      }

      int actionY = layout.sidebarY + layout.sidebarHeight - 170;
      int actionWidth = (tabWidth - 12) / 2;
      this.followButton = this.addRenderableWidget(this.actionButton(tabX, actionY, actionWidth, t("Follow me", "За мной"), CompanionActionIntent.FOLLOW));
      this.stayButton = this.addRenderableWidget(this.actionButton(tabX + actionWidth + 12, actionY, actionWidth, t("Stay", "Стоять"), CompanionActionIntent.STAY));
      this.sitButton = this.addRenderableWidget(this.actionButton(tabX, actionY + 40, actionWidth, t("Sit", "Сидеть"), CompanionActionIntent.SIT));
      this.setHomeButton = this.addRenderableWidget(this.actionButton(tabX + actionWidth + 12, actionY + 40, actionWidth, t("Set home", "Точка дома"), CompanionActionIntent.SET_HOME));
      this.goHomeButton = this.addRenderableWidget(
              new AigfSoftButton(tabX, actionY + 80, tabWidth, 32, AigfUi.text(t("Go home", "Иди домой")),
                      button -> this.sendDirectAction(CompanionActionIntent.GO_HOME),
                      () -> this.snapshot.commandMode().name().equals("HOME"), -1596986)
      );
      this.chromeWidgets.add(this.followButton);
      this.chromeWidgets.add(this.stayButton);
      this.chromeWidgets.add(this.sitButton);
      this.chromeWidgets.add(this.setHomeButton);
      this.chromeWidgets.add(this.goHomeButton);
   }

   private Button actionButton(int x, int y, int width, String text, CompanionActionIntent intent) {
      return new AigfSoftButton(x, y, width, 32, AigfUi.text(text), button -> this.sendDirectAction(intent), () -> {
         return switch (intent) {
            case FOLLOW -> this.snapshot.commandMode().name().equals("FOLLOW");
            case STAY -> this.snapshot.commandMode().name().equals("STAY");
            case SIT -> this.snapshot.commandMode().name().equals("SIT");
            default -> false;
         };
      }, -1596986);
   }

   private void buildChatWidgets(UiLayout layout) {
      this.chatInput = this.addRenderableWidget(
              new AigfSoftEditBox(this.font, layout.bodyX + 28, layout.bodyBottom() - 54, layout.bodyWidth - 190, 34,
                      AigfUi.text(t("Talk to her...", "Напиши ей что-нибудь...")))
      );
      this.chatInput.setMaxLength(256);
      this.sendButton = this.addRenderableWidget(
              new AigfSoftButton(layout.bodyRight() - 138, layout.bodyBottom() - 54, 110, 34,
                      AigfUi.text(t("Send", "Отправить")), button -> this.sendChat(), () -> false, -2986325)
      );
      this.chatWidgets.add(this.chatInput);
      this.chatWidgets.add(this.sendButton);
   }

   private void buildInventoryWidgets(UiLayout layout) {
      this.nameBox = this.addRenderableWidget(
              new AigfSoftEditBox(this.font, layout.bodyX + 28, layout.bodyY + 72, layout.bodyWidth - 240, 34,
                      AigfUi.text(t("Companion name", "Имя спутницы")))
      );
      this.nameBox.setMaxLength(24);
      this.nameBox.setValue(this.snapshot.companionName());
      this.saveNameButton = this.addRenderableWidget(
              new AigfSoftButton(layout.bodyRight() - 184, layout.bodyY + 72, 156, 34,
                      AigfUi.text(t("Rename", "Переименовать")), button -> this.renameCompanion(), () -> false, -2986325)
      );
      this.giftFromHandButton = this.addRenderableWidget(
              new AigfSoftButton(layout.bodyX + 28, layout.bodyY + 120, layout.bodyWidth - 56, 34,
                      AigfUi.text(t("Gift an item from your hand", "Подарить предмет из руки")), button -> this.sendGiftFromHand(), () -> false, -1596986)
      );
      this.inventoryWidgets.add(this.nameBox);
      this.inventoryWidgets.add(this.saveNameButton);
      this.inventoryWidgets.add(this.giftFromHandButton);

      for (int i = 0; i < INVENTORY_SLOTS.length; i++) {
         SlotRect rect = this.inventorySlotRect(layout, i);
         InventorySlotView slotView = INVENTORY_SLOTS[i];
         Button fromHandButton = this.addRenderableWidget(
                 new AigfSoftButton(rect.x + rect.width - 170, rect.y + rect.height - 34, 76, 24,
                         AigfUi.text(t("From hand", "Из руки")), button -> this.sendInventoryAction(slotView.slot(), "PUT_FROM_HAND"), () -> false, -1596986)
         );
         Button takeButton = this.addRenderableWidget(
                 new AigfSoftButton(rect.x + rect.width - 86, rect.y + rect.height - 34, 62, 24,
                         AigfUi.text(t("Take", "Забрать")), button -> this.sendInventoryAction(slotView.slot(), "TAKE_TO_PLAYER"), () -> false, -2986325)
         );
         this.inventoryWidgets.add(fromHandButton);
         this.inventoryWidgets.add(takeButton);
      }
   }

   private void buildAppearanceWidgets(UiLayout layout) {
      this.importNameBox = this.addRenderableWidget(
              new AigfSoftEditBox(this.font, layout.bodyX + 28, layout.bodyY + 72, 210, 34,
                      AigfUi.text(t("Nickname to import", "Ник для импорта")))
      );
      this.importNameBox.setMaxLength(16);
      this.importSkinButton = this.addRenderableWidget(
              new AigfSoftButton(layout.bodyX + 248, layout.bodyY + 72, 136, 34,
                      AigfUi.text(t("Import skin", "Импорт скина")), button -> this.importSkin(), () -> false, -2986325)
      );
      this.previousSkinButton = this.addRenderableWidget(
              new AigfSoftButton(layout.bodyX + 28, layout.bodyBottom() - 54, 54, 34,
                      AigfUi.text("←"), button -> this.shiftSkinSelection(-1), () -> false, -1596986)
      );
      this.nextSkinButton = this.addRenderableWidget(
              new AigfSoftButton(layout.bodyX + 92, layout.bodyBottom() - 54, 54, 34,
                      AigfUi.text("→"), button -> this.shiftSkinSelection(1), () -> false, -1596986)
      );
      this.applySkinButton = this.addRenderableWidget(
              new AigfSoftButton(layout.bodyRight() - 276, layout.bodyBottom() - 54, 116, 34,
                      AigfUi.text(t("Apply", "Применить")), button -> this.applySelectedSkin(), () -> false, -2986325)
      );
      this.reloadSkinsButton = this.addRenderableWidget(
              new AigfSoftButton(layout.bodyRight() - 148, layout.bodyBottom() - 54, 120, 34,
                      AigfUi.text(t("Reload list", "Обновить список")), button -> this.reloadSkinList(), () -> false, -1596986)
      );
      this.appearanceWidgets.add(this.importNameBox);
      this.appearanceWidgets.add(this.importSkinButton);
      this.appearanceWidgets.add(this.previousSkinButton);
      this.appearanceWidgets.add(this.nextSkinButton);
      this.appearanceWidgets.add(this.applySkinButton);
      this.appearanceWidgets.add(this.reloadSkinsButton);
   }

   private void buildSettingsWidgets(UiLayout layout) {
      this.apiKeyBox = this.addRenderableWidget(
              new AigfSoftEditBox(this.font, layout.bodyX + 28, layout.bodyY + 72, layout.bodyWidth - 56, 34,
                      AigfUi.text("API Key"))
      );
      this.apiKeyBox.setMaxLength(256);
      this.previousModelButton = this.addRenderableWidget(
              new AigfSoftButton(layout.bodyX + 28, layout.bodyY + 130, 46, 34,
                      AigfUi.text("←"), button -> this.cycleModel(-1), () -> false, -1596986)
      );
      this.modelIdBox = this.addRenderableWidget(
              new AigfSoftEditBox(this.font, layout.bodyX + 84, layout.bodyY + 130, layout.bodyWidth - 260, 34,
                      AigfUi.text("Model"))
      );
      this.modelIdBox.setMaxLength(128);
      this.nextModelButton = this.addRenderableWidget(
              new AigfSoftButton(layout.bodyRight() - 176, layout.bodyY + 130, 46, 34,
                      AigfUi.text("→"), button -> this.cycleModel(1), () -> false, -1596986)
      );
      this.timeoutBox = this.addRenderableWidget(
              new AigfSoftEditBox(this.font, layout.bodyRight() - 120, layout.bodyY + 130, 92, 34,
                      AigfUi.text(t("Timeout", "Таймаут")))
      );
      this.contextTurnsBox = this.addRenderableWidget(
              new AigfSoftEditBox(this.font, layout.bodyRight() - 120, layout.bodyY + 188, 92, 34,
                      AigfUi.text(t("Context", "Контекст")))
      );
      this.saveSettingsButton = this.addRenderableWidget(
              new AigfSoftButton(layout.bodyX + 28, layout.bodyBottom() - 54, 156, 34,
                      AigfUi.text(t("Save settings", "Сохранить настройки")), button -> this.saveSettings(), () -> false, -2986325)
      );
      this.testConnectionButton = this.addRenderableWidget(
              new AigfSoftButton(layout.bodyX + 196, layout.bodyBottom() - 54, 188, 34,
                      AigfUi.text(t("Test connection", "Проверить связь")), button -> this.testConnection(), () -> false, -1596986)
      );
      this.settingsWidgets.add(this.apiKeyBox);
      this.settingsWidgets.add(this.previousModelButton);
      this.settingsWidgets.add(this.modelIdBox);
      this.settingsWidgets.add(this.nextModelButton);
      this.settingsWidgets.add(this.timeoutBox);
      this.settingsWidgets.add(this.contextTurnsBox);
      this.settingsWidgets.add(this.saveSettingsButton);
      this.settingsWidgets.add(this.testConnectionButton);
   }

   private void switchTab(ScreenTab tab) {
      this.activeTab = tab;
      this.updateTabVisibility();
   }

   private void updateTabVisibility() {
      setVisible(this.chatWidgets, this.activeTab == ScreenTab.CHAT);
      setVisible(this.inventoryWidgets, this.activeTab == ScreenTab.INVENTORY);
      setVisible(this.appearanceWidgets, this.activeTab == ScreenTab.APPEARANCE);
      setVisible(this.settingsWidgets, this.activeTab == ScreenTab.SETTINGS);
   }

   private void setVisible(List<AbstractWidget> widgets, boolean visible) {
      for (AbstractWidget widget : widgets) {
         widget.visible = visible;
         widget.active = visible;
      }
   }

   private void rebuildChatLines() {
      this.chatLines.clear();
      for (ConversationTurn turn : this.snapshot.recentTurns()) {
         String prefix = "assistant".equals(turn.speaker())
                 ? this.snapshot.companionName() + ": "
                 : ("user".equals(turn.speaker()) ? t("You: ", "Ты: ") : "* ");
         this.chatLines.add(prefix + turn.text());
      }
   }

   private void sendChat() {
      String userMessage = this.chatInput.getValue().trim();
      if (!this.requestInFlight && !userMessage.isBlank()) {
         this.requestInFlight = true;
         this.statusLine = t("Waiting for her answer...", "Жду её ответ...");
         this.chatLines.add(t("You: ", "Ты: ") + userMessage);
         this.chatInput.setValue("");
         OpenAiClient.chat(userMessage, this.snapshot)
                 .whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
                    this.requestInFlight = false;
                    if (error == null && result != null) {
                       ConversationMoodAnalyzer.Analysis analysis = ConversationMoodAnalyzer.analyze(userMessage);
                       CompanionAiResult adjustedResult = OpenAiClient.enforceConversationTone(userMessage, this.snapshot, result, analysis);
                       this.chatLines.add(this.snapshot.companionName() + ": " + adjustedResult.spokenText());
                       this.statusLine = adjustedResult.careHint() != null && !adjustedResult.careHint().isBlank()
                               ? this.localizeCareHint(adjustedResult.careHint())
                               : humanizeAnalysis(analysis.summary());
                       AigfNetwork.CHANNEL.sendToServer(
                               new ServerboundChatTurnPacket(
                                       this.companion.getId(),
                                       userMessage,
                                       adjustedResult.spokenText(),
                                       adjustedResult.emotion().name(),
                                       adjustedResult.actionIntent().name(),
                                       adjustedResult.memoryFact(),
                                       adjustedResult.careHint(),
                                       analysis.moodDelta(),
                                       analysis.trustDelta()
                               )
                       );
                    } else {
                       this.statusLine = t("AI unavailable", "AI недоступен");
                    }
                 }));
      }
   }

   private void sendDirectAction(CompanionActionIntent intent) {
      AigfNetwork.CHANNEL.sendToServer(new ServerboundDirectActionPacket(this.companion.getId(), intent.name()));
      this.statusLine = switch (intent) {
         case FOLLOW -> t("She will follow you now", "Теперь она пойдёт за тобой");
         case STAY -> t("She will stay here", "Она останется здесь");
         case SIT -> t("She sat down", "Она села");
         case SET_HOME -> t("Home point saved", "Точка дома сохранена");
         case GO_HOME -> this.snapshot.hasHome() ? t("She is heading home", "Она идёт домой") : t("Home point is not set", "Точка дома не задана");
         default -> t("Command sent", "Команда отправлена");
      };
   }

   private void renameCompanion() {
      String name = this.nameBox.getValue().trim();
      if (name.isBlank()) {
         this.statusLine = t("Name is empty", "Имя пустое");
      } else {
         if (name.length() > 24) {
            name = name.substring(0, 24);
            this.nameBox.setValue(name);
         }
         this.snapshot = this.withCompanionName(name);
         CompanionClientState.updateSnapshot(this.companion.getId(), this.snapshot);
         this.rebuildChatLines();
         AigfNetwork.CHANNEL.sendToServer(new ServerboundRenameCompanionPacket(this.companion.getId(), name));
         this.statusLine = t("Name sent to server", "Имя отправлено на сервер");
      }
   }

   private void sendInventoryAction(EquipmentSlot slot, String actionName) {
      AigfNetwork.CHANNEL.sendToServer(new ServerboundCompanionInventoryPacket(this.companion.getId(), slot.name(), actionName));
      this.statusLine = switch (actionName) {
         case "PUT_FROM_HAND" -> t("Item moved to ", "Предмет отправлен в ") + localizedSlotName(slot).toLowerCase();
         case "TAKE_TO_PLAYER" -> t("Item taken from ", "Предмет забран из ") + localizedSlotName(slot).toLowerCase();
         default -> "";
      };
   }

   private void sendGiftFromHand() {
      ItemStack handStack = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
      if (handStack.isEmpty()) {
         this.statusLine = t("Your main hand is empty", "Главная рука пуста");
      } else {
         AigfNetwork.CHANNEL.sendToServer(new ServerboundGiftFromHandPacket(this.companion.getId()));
         this.statusLine = t("Gift sent from your hand", "Подарок отправлен из руки");
      }
   }

   private void shiftSkinSelection(int delta) {
      if (!this.skinIds.isEmpty()) {
         this.selectedSkinIndex = (this.selectedSkinIndex + delta + this.skinIds.size()) % this.skinIds.size();
      }
   }

   private void reloadSkinList() {
      this.skinIds = CompanionSkinManager.listSkinIds();
      this.selectCurrentSkin();
      this.statusLine = "";
   }

   private void selectCurrentSkin() {
      if (this.skinIds.isEmpty()) {
         this.selectedSkinIndex = 0;
      } else {
         int foundIndex = this.skinIds.indexOf(this.snapshot.activeSkinId());
         this.selectedSkinIndex = foundIndex >= 0 ? foundIndex : 0;
      }
   }

   private void applySelectedSkin() {
      if (this.skinIds.isEmpty()) {
         this.statusLine = t("No skins found", "Скины не найдены");
      } else {
         String selectedSkin = this.skinIds.get(this.selectedSkinIndex);
         if (selectedSkin.startsWith("local:")) {
            String validationError = CompanionSkinManager.validateLocalSkin(selectedSkin);
            if (!validationError.isBlank()) {
               this.statusLine = validationError;
               return;
            }
         }
         this.statusLine = t("Skin applied", "Скин применён");
         AigfNetwork.CHANNEL.sendToServer(new ServerboundApplySkinPacket(this.companion.getId(), selectedSkin));
      }
   }

   private void importSkin() {
      String nickname = this.importNameBox.getValue().trim();
      if (nickname.isBlank()) {
         this.statusLine = t("Nickname is empty", "Ник пустой");
      } else {
         this.statusLine = t("Importing skin...", "Импортирую скин...");
         CompanionSkinManager.importSkinByUsername(nickname).whenComplete((skinId, error) -> Minecraft.getInstance().execute(() -> {
            if (error == null && skinId != null) {
               this.reloadSkinList();
               int foundIndex = this.skinIds.indexOf(skinId);
               if (foundIndex >= 0) {
                  this.selectedSkinIndex = foundIndex;
               }
               this.statusLine = t("Skin imported", "Скин импортирован");
            } else {
               this.statusLine = t("Import failed", "Импорт не удался");
            }
         }));
      }
   }

   private void loadSettings() {
      ClientSettingsManager settings = ClientSettingsManager.get();
      this.apiKeyBox.setValue(settings.getOpenaiApiKey());
      this.modelIdBox.setValue(settings.getModelId());
      this.timeoutBox.setValue(String.valueOf(settings.getTimeoutSeconds()));
      this.contextTurnsBox.setValue(String.valueOf(settings.getMaxContextTurns()));
   }

   private void saveSettings() {
      ClientSettingsManager settings = ClientSettingsManager.get();
      settings.setOpenaiApiKey(this.apiKeyBox.getValue());
      settings.setModelId(this.modelIdBox.getValue());
      settings.setTimeoutSeconds(parseInteger(this.timeoutBox.getValue(), 45, 5, 120));
      settings.setMaxContextTurns(parseInteger(this.contextTurnsBox.getValue(), 10, 2, 20));
      settings.save();
      this.statusLine = t("Settings saved", "Настройки сохранены");
   }

   private void cycleModel(int direction) {
      this.modelIdBox.setValue(ClientSettingsManager.get().cycleModel(direction));
   }

   private void testConnection() {
      this.saveSettings();
      this.statusLine = t("Testing connection...", "Проверяю соединение...");
      OpenAiClient.testConnection().thenAccept(result -> Minecraft.getInstance().execute(() -> this.statusLine = result));
   }

   private static int parseInteger(String value, int fallback, int min, int max) {
      try {
         return Mth.clamp(Integer.parseInt(value.trim()), min, max);
      } catch (NumberFormatException ignored) {
         return fallback;
      }
   }

   @Override
   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      float time = (float) (Util.getMillis() % 100000L) / 1000.0F;
      UiLayout layout = this.layout();
      AigfUi.drawBackdrop(guiGraphics, this.width, this.height, time);
      this.renderSidebar(guiGraphics, layout);
      this.renderHero(guiGraphics, layout);
      this.renderBody(guiGraphics, layout);
      this.renderStatus(guiGraphics, layout);
      super.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   private void renderSidebar(GuiGraphics guiGraphics, UiLayout layout) {
      AigfUi.drawPanel(guiGraphics, layout.sidebarX, layout.sidebarY, layout.sidebarWidth, layout.sidebarHeight,
              -953870555, -518845163, AigfUi.colorAlpha(-1596986, 0.18F));
      guiGraphics.fillGradient(layout.sidebarX + 1, layout.sidebarY + 1,
              layout.sidebarX + layout.sidebarWidth - 1, layout.sidebarY + 118,
              AigfUi.colorAlpha(-1596986, 0.14F), 1182484);
      int orbX = layout.sidebarX + 32;
      int orbY = layout.sidebarY + 32;
      guiGraphics.fill(orbX, orbY, orbX + 52, orbY + 52, AigfUi.colorAlpha(-10866092, 0.86F));
      guiGraphics.fillGradient(orbX + 4, orbY + 4, orbX + 48, orbY + 48,
              AigfUi.colorAlpha(-1596986, 0.6F), AigfUi.colorAlpha(-2986325, 0.34F));
      guiGraphics.drawString(this.font, AigfUi.text("AIGF"), orbX + 13, orbY + 20, -133381, false);
      this.drawScaledText(guiGraphics, this.snapshot.companionName(), layout.sidebarX + 96, layout.sidebarY + 28, 1.4F, -133381);
      guiGraphics.drawString(this.font, AigfUi.text(t("android companion", "android-спутница")), layout.sidebarX + 96, layout.sidebarY + 54, -2505259, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("mood-aware, memory-rich, alive", "эмоции, память, характер")), layout.sidebarX + 96, layout.sidebarY + 68, -2505259, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Bond", "Связь") + "  " + humanizeRelationship(this.snapshot.relationshipStage().name())), layout.sidebarX + 18, layout.sidebarY + 104, -532496, false);
      int actionTitleY = layout.sidebarY + layout.sidebarHeight - 194;
      guiGraphics.drawString(this.font, AigfUi.text(t("Quick actions", "Быстрые действия")), layout.sidebarX + 18, actionTitleY, -133381, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("soft controls for her behavior", "мягкое управление её поведением")), layout.sidebarX + 18, actionTitleY + 14, -2505259, false);
      String homeText = this.snapshot.hasHome()
              ? t("Home: ", "Дом: ") + this.snapshot.homeX() + ", " + this.snapshot.homeY() + ", " + this.snapshot.homeZ()
              : t("Home point is not set", "Точка дома не задана");
      guiGraphics.drawString(this.font, AigfUi.text(homeText), layout.sidebarX + 18, layout.sidebarY + layout.sidebarHeight - 24, -2505259, false);
   }

   private void renderHero(GuiGraphics guiGraphics, UiLayout layout) {
      AigfUi.drawPanel(guiGraphics, layout.heroX, layout.heroY, layout.heroWidth, layout.heroHeight,
              -1087366098, -535359462, AigfUi.colorAlpha(-1596986, 0.22F));
      guiGraphics.fillGradient(layout.heroX + 1, layout.heroY + 1,
              layout.heroX + layout.heroWidth - 1, layout.heroY + 70,
              AigfUi.colorAlpha(-1596986, 0.18F), 1182484);
      this.drawScaledText(guiGraphics, this.snapshot.companionName(), layout.heroX + 28, layout.heroY + 24, 1.9F, -133381);
      guiGraphics.drawString(this.font, AigfUi.text(t("soft presence, sharp memory, personal tone", "мягкое присутствие, память, личный характер")), layout.heroX + 28, layout.heroY + 58, -2505259, false);
      int chipY = layout.heroY + 84;
      this.drawChip(guiGraphics, layout.heroX + 28, chipY, t("Emotion", "Эмоция") + ": " + humanizeEmotion(this.snapshot.emotion().name()), -1596986);
      this.drawChip(guiGraphics, layout.heroX + 200, chipY, t("Mode", "Режим") + ": " + humanizeCommandMode(this.snapshot.commandMode().name()), -3424778);
      this.drawChip(guiGraphics, layout.heroX + 388, chipY, t("Conflict", "Конфликт") + ": " + humanizeConflict(this.snapshot.conflictState().name()), -3607324);
      int rightCardX = layout.heroRight() - 236;
      AigfUi.drawCard(guiGraphics, rightCardX, layout.heroY + 18, 208, 92, true);
      guiGraphics.drawString(this.font, AigfUi.text(t("Current pulse", "Текущий пульс")), rightCardX + 16, layout.heroY + 34, -133381, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Mood", "Настроение") + "  " + this.snapshot.mood() + "/100"), rightCardX + 16, layout.heroY + 54, -2505259, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Trust", "Доверие") + "  " + this.snapshot.trust() + "/100"), rightCardX + 16, layout.heroY + 68, -2505259, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Energy", "Энергия") + "  " + this.snapshot.energy() + "/100"), rightCardX + 16, layout.heroY + 82, -2505259, false);
   }

   private void renderBody(GuiGraphics guiGraphics, UiLayout layout) {
      AigfUi.drawPanel(guiGraphics, layout.bodyX, layout.bodyY, layout.bodyWidth, layout.bodyHeight,
              -719646437, -284030189, AigfUi.colorAlpha(-1596986, 0.16F));
      switch (this.activeTab) {
         case CHAT -> this.renderChatTab(guiGraphics, layout);
         case CARE -> this.renderCareTab(guiGraphics, layout);
         case INVENTORY -> this.renderInventoryTab(guiGraphics, layout);
         case APPEARANCE -> this.renderAppearanceTab(guiGraphics, layout);
         case SETTINGS -> this.renderSettingsTab(guiGraphics, layout);
      }
   }

   private void renderStatus(GuiGraphics guiGraphics, UiLayout layout) {
      if (this.statusLine != null && !this.statusLine.isBlank()) {
         int statusWidth = Math.min(layout.bodyWidth, 480);
         int statusX = layout.bodyX + 18;
         int statusY = layout.bodyBottom() + 10;
         AigfUi.drawPanel(guiGraphics, statusX, statusY, statusWidth, 28,
                 -1087760592, -702935010, AigfUi.colorAlpha(-1596986, 0.16F));
         String truncated = this.font.plainSubstrByWidth(this.statusLine, statusWidth - 20);
         guiGraphics.drawString(this.font, AigfUi.text(truncated), statusX + 10, statusY + 10, -133381, false);
      }
   }

   private void renderChatTab(GuiGraphics guiGraphics, UiLayout layout) {
      AigfUi.drawSectionTitle(guiGraphics, this.font,
              t("Conversation", "Разговор"),
              t("Minecraft chat still works with @aigf, but this fullscreen view gives you the full flow.",
                      "Minecraft-чат с @aigf тоже работает, но здесь полный красивый диалог."),
              layout.bodyX + 28, layout.bodyY + 24, layout.bodyWidth - 56);
      String homeInfo = this.snapshot.hasHome()
              ? t("Home point: ", "Точка дома: ") + this.snapshot.homeX() + ", " + this.snapshot.homeY() + ", " + this.snapshot.homeZ()
              : t("Home point: not set yet", "Точка дома пока не задана");
      guiGraphics.drawString(this.font, AigfUi.text(homeInfo), layout.bodyX + 28, layout.bodyY + 74, -2505259, false);
      int conversationX = layout.bodyX + 28;
      int conversationY = layout.bodyY + 96;
      int conversationWidth = layout.bodyWidth - 56;
      int conversationHeight = layout.bodyHeight - 160;
      AigfUi.drawCard(guiGraphics, conversationX, conversationY, conversationWidth, conversationHeight, true);
      List<FormattedCharSequence> wrappedLines = new ArrayList<>();
      for (String line : this.chatLines) {
         wrappedLines.addAll(this.font.split(AigfUi.text(line), conversationWidth - 24));
      }
      int maxLines = Math.max(4, (conversationHeight - 24) / 12);
      int start = Math.max(0, wrappedLines.size() - maxLines);
      int y = conversationY + 12;
      for (int i = start; i < wrappedLines.size(); i++) {
         guiGraphics.drawString(this.font, wrappedLines.get(i), conversationX + 12, y, -133381, false);
         y += 12;
      }
   }

   private void renderCareTab(GuiGraphics guiGraphics, UiLayout layout) {
      AigfUi.drawSectionTitle(guiGraphics, this.font,
              t("Heart State", "Состояние сердца"),
              t("Your tone, distance, gifts, and calm moments all leave a mark on her.",
                      "На неё влияют тон, дистанция, подарки и спокойные моменты рядом."),
              layout.bodyX + 28, layout.bodyY + 24, layout.bodyWidth - 56);
      int cardWidth = (layout.bodyWidth - 76) / 3;
      this.drawStatCard(guiGraphics, layout.bodyX + 28, layout.bodyY + 82, cardWidth, t("Mood", "Настроение"), this.snapshot.mood(), -2986325);
      this.drawStatCard(guiGraphics, layout.bodyX + 42 + cardWidth, layout.bodyY + 82, cardWidth, t("Trust", "Доверие"), this.snapshot.trust(), -8926977);
      this.drawStatCard(guiGraphics, layout.bodyX + 56 + cardWidth * 2, layout.bodyY + 82, cardWidth, t("Energy", "Энергия"), this.snapshot.energy(), -6360361);
      int lowerY = layout.bodyY + 208;
      int leftCardWidth = (layout.bodyWidth - 68) / 2;
      AigfUi.drawCard(guiGraphics, layout.bodyX + 28, lowerY, leftCardWidth, 126, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Emotional readout", "Эмоциональный срез")), layout.bodyX + 42, lowerY + 18, -133381, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Emotion", "Эмоция") + ": " + humanizeEmotion(this.snapshot.emotion().name())), layout.bodyX + 42, lowerY + 42, -2505259, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Bond", "Связь") + ": " + humanizeRelationship(this.snapshot.relationshipStage().name())), layout.bodyX + 42, lowerY + 58, -2505259, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Conflict", "Конфликт") + ": " + humanizeConflict(this.snapshot.conflictState().name())), layout.bodyX + 42, lowerY + 74, -2505259, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Mode", "Режим") + ": " + humanizeCommandMode(this.snapshot.commandMode().name())), layout.bodyX + 42, lowerY + 90, -2505259, false);
      AigfUi.drawCard(guiGraphics, layout.bodyX + 40 + leftCardWidth, lowerY, leftCardWidth, 126, true);
      guiGraphics.drawString(this.font, AigfUi.text(t("Long memory", "Долгая память")), layout.bodyX + 54 + leftCardWidth, lowerY + 18, -133381, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Resentment", "Обида") + ": " + this.snapshot.resentment() + "/100"), layout.bodyX + 54 + leftCardWidth, lowerY + 42, -2505259, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Repair", "Примирение") + ": " + this.snapshot.reconciliationProgress() + "/100"), layout.bodyX + 54 + leftCardWidth, lowerY + 58, -2505259, false);
      String homeLine = this.snapshot.hasHome()
              ? t("Home", "Дом") + ": " + this.snapshot.homeX() + ", " + this.snapshot.homeY() + ", " + this.snapshot.homeZ()
              : t("Home point is not set", "Точка дома не задана");
      guiGraphics.drawString(this.font, AigfUi.text(homeLine), layout.bodyX + 54 + leftCardWidth, lowerY + 74, -2505259, false);
      String hintLine = this.statusLine.isBlank() ? t("No fresh hint right now", "Сейчас без новой подсказки") : this.statusLine;
      guiGraphics.drawString(this.font, AigfUi.text(hintLine), layout.bodyX + 54 + leftCardWidth, lowerY + 98, -532496, false);
   }

   private void renderInventoryTab(GuiGraphics guiGraphics, UiLayout layout) {
      AigfUi.drawSectionTitle(guiGraphics, this.font,
              t("Personal Inventory", "Личный инвентарь"),
              t("Give her a proper name, hand over gifts, and manage what she carries for herself.",
                      "Задай ей настоящее имя, дари предметы и управляй тем, что она носит с собой."),
              layout.bodyX + 28, layout.bodyY + 24, layout.bodyWidth - 56);
      for (int i = 0; i < INVENTORY_SLOTS.length; i++) {
         SlotRect rect = this.inventorySlotRect(layout, i);
         InventorySlotView slotView = INVENTORY_SLOTS[i];
         ItemStack stack = this.companion.getItemBySlot(slotView.slot());
         String itemName = stack.isEmpty() ? t("Empty", "Пусто") : stack.getHoverName().getString();
         AigfUi.drawCard(guiGraphics, rect.x, rect.y, rect.width, rect.height, false);
         guiGraphics.drawString(this.font, AigfUi.text(localizedSlotName(slotView.slot())), rect.x + 16, rect.y + 14, -133381, false);
         List<FormattedCharSequence> lines = this.font.split(AigfUi.text(itemName), rect.width - 34);
         if (!lines.isEmpty()) {
            guiGraphics.drawString(this.font, lines.get(0), rect.x + 16, rect.y + 34, -2505259, false);
         }
      }
   }

   private void renderAppearanceTab(GuiGraphics guiGraphics, UiLayout layout) {
      AigfUi.drawSectionTitle(guiGraphics, this.font,
              t("Looks & Skins", "Внешность и скины"),
              t("A softer dressing room for swapping local skins, built-ins, and imported player looks.",
                      "Мягкая гардеробная для встроенных, локальных и импортированных скинов."),
              layout.bodyX + 28, layout.bodyY + 24, layout.bodyWidth - 56);
      int previewWidth = layout.bodyWidth - 56;
      AigfUi.drawCard(guiGraphics, layout.bodyX + 28, layout.bodyY + 122, previewWidth, 210, true);
      String selectedSkin = this.skinIds.isEmpty() ? "builtin:alex" : this.skinIds.get(this.selectedSkinIndex);
      guiGraphics.drawString(this.font, AigfUi.text(t("Active skin", "Активный скин") + ": " + this.snapshot.activeSkinId()), layout.bodyX + 48, layout.bodyY + 148, -133381, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Selected", "Выбран") + ": " + selectedSkin), layout.bodyX + 48, layout.bodyY + 168, -2505259, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Available skins", "Доступно скинов") + ": " + this.skinIds.size()), layout.bodyX + 48, layout.bodyY + 188, -2505259, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Folder", "Папка") + ": config/aigf/skins"), layout.bodyX + 48, layout.bodyY + 208, -2505259, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Tip: keep 64x64 PNG skins for the cleanest result.", "Совет: используй PNG 64x64 для самого чистого результата.")), layout.bodyX + 48, layout.bodyY + 238, -532496, false);
   }

   private void renderSettingsTab(GuiGraphics guiGraphics, UiLayout layout) {
      AigfUi.drawSectionTitle(guiGraphics, this.font,
              t("AI Settings", "AI-настройки"),
              t("OpenAI connection, model presets, timeouts, and context depth all live here in one calm place.",
                      "Здесь собраны подключение OpenAI, выбор модели, таймаут и глубина контекста."),
              layout.bodyX + 28, layout.bodyY + 24, layout.bodyWidth - 56);
      guiGraphics.drawString(this.font, AigfUi.text(t("API key", "API ключ")), layout.bodyX + 28, layout.bodyY + 58, -133381, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Model", "Модель")), layout.bodyX + 84, layout.bodyY + 116, -133381, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Timeout", "Таймаут")), layout.bodyRight() - 120, layout.bodyY + 116, -133381, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Context turns", "Контекст ходов")), layout.bodyRight() - 120, layout.bodyY + 174, -133381, false);
      AigfUi.drawCard(guiGraphics, layout.bodyX + 28, layout.bodyY + 246, layout.bodyWidth - 56, 84, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Character prompt is locked in-code", "Характерный промпт зашит в коде")), layout.bodyX + 42, layout.bodyY + 264, -133381, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("She keeps her own personality, tone, and boundaries no matter which model is selected.",
              "Она сохраняет свой характер, тон и границы вне зависимости от выбранной модели.")), layout.bodyX + 42, layout.bodyY + 286, -2505259, false);
   }

   private void drawStatCard(GuiGraphics guiGraphics, int x, int y, int width, String label, int value, int accent) {
      AigfUi.drawCard(guiGraphics, x, y, width, 104, true);
      guiGraphics.drawString(this.font, AigfUi.text(label), x + 16, y + 16, -133381, false);
      this.drawScaledText(guiGraphics, String.valueOf(value), x + 16, y + 34, 1.6F, -133381);
      AigfUi.drawProgressBar(guiGraphics, this.font, x + 16, y + 66, width - 32, t("Level", "Уровень"), value, accent);
   }

   private void drawChip(GuiGraphics guiGraphics, int x, int y, String text, int accent) {
      int chipWidth = Math.min(170, this.font.width(AigfUi.text(text)) + 22);
      AigfUi.drawPanel(guiGraphics, x, y, chipWidth, 24,
              AigfUi.colorAlpha(AigfUi.lighten(accent, 0.22F), 0.32F),
              AigfUi.colorAlpha(-14477276, 0.86F),
              AigfUi.colorAlpha(accent, 0.28F));
      guiGraphics.drawString(this.font, AigfUi.text(text), x + 10, y + 8, -133381, false);
   }

   private void drawScaledText(GuiGraphics guiGraphics, String text, int x, int y, float scale, int color) {
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate(x, y, 0.0F);
      guiGraphics.pose().scale(scale, scale, 1.0F);
      guiGraphics.drawString(this.font, AigfUi.text(text), 0, 0, color, false);
      guiGraphics.pose().popPose();
   }

   private SlotRect inventorySlotRect(UiLayout layout, int index) {
      int columnWidth = (layout.bodyWidth - 72) / 2;
      int x = layout.bodyX + 28 + (index % 2) * (columnWidth + 16);
      int y = layout.bodyY + 170 + (index / 2) * 92;
      return new SlotRect(x, y, columnWidth, 78);
   }

   private UiLayout layout() {
      int padding = 22;
      int sidebarWidth = Mth.clamp(this.width / 5, 220, 280);
      int sidebarX = padding;
      int sidebarY = padding;
      int sidebarHeight = this.height - padding * 2;
      int heroX = sidebarX + sidebarWidth + 18;
      int heroY = padding;
      int heroWidth = this.width - heroX - padding;
      int heroHeight = 126;
      int bodyX = heroX;
      int bodyY = heroY + heroHeight + 16;
      int bodyWidth = heroWidth;
      int bodyHeight = this.height - bodyY - padding - 44;
      return new UiLayout(sidebarX, sidebarY, sidebarWidth, sidebarHeight,
              heroX, heroY, heroWidth, heroHeight,
              bodyX, bodyY, bodyWidth, bodyHeight);
   }

   private static String humanizeAnalysis(String summary) {
      return switch (summary) {
         case "hurt_by_words" -> t("Your words hurt her.", "Её задели твои слова.");
         case "praised" -> t("She liked your kindness.", "Ей приятно твоё отношение.");
         case "shared_discussion" -> t("She enjoys discussing things together.", "Ей нравится обсуждать всё вместе.");
         case "apology" -> t("The apology helped calm the conversation.", "Извинение помогло смягчить разговор.");
         case "hurt_by_words,apology" -> t("She heard the apology, but the hurt is still there.", "Извинение услышано, но осадок ещё остался.");
         case "hurt_by_words,praised", "praised,hurt_by_words" -> t("The conversation feels warm and painful at once.", "Разговор одновременно тёплый и болезненный.");
         default -> "";
      };
   }

   private static String humanizeRelationship(String stage) {
      return switch (stage) {
         case "ATTACHED" -> t("Attached", "Сильная привязанность");
         case "WARM" -> t("Warm", "Тепло");
         case "COLD" -> t("Cold", "Холодно");
         default -> t("Neutral", "Нейтрально");
      };
   }

   private static String humanizeConflict(String state) {
      return switch (state) {
         case "DISTANT" -> t("Distant", "Отстранённость");
         case "OFFENDED" -> t("Offended", "Обида");
         case "GUARDED" -> t("Guarded", "Настороженность");
         default -> t("Open", "Открыто");
      };
   }

   private static String humanizeEmotion(String emotion) {
      return switch (emotion) {
         case "HAPPY" -> t("Happy", "Рада");
         case "SHY" -> t("Shy", "Смущена");
         case "TIRED" -> t("Tired", "Устала");
         case "SAD" -> t("Sad", "Грустит");
         default -> t("Neutral", "Спокойна");
      };
   }

   private String localizeCareHint(String hint) {
      if (hint == null || hint.isBlank()) return "";
      if (hint.startsWith("hint.rename:")) {
         String name = hint.substring("hint.rename:".length()).trim();
         return t("My name is now ", "Теперь меня зовут ") + name + ".";
      }
      return switch (hint) {
         case "gift" -> t("She appreciated the gift.", "Ей понравился подарок.");
         case "hint.home.set" -> t("Home point saved. She will remember this place.", "Точка дома сохранена. Она запомнила это место.");
         case "hint.home.going" -> t("She is heading home now.", "Она сейчас идёт домой.");
         case "hint.home.arrived" -> t("She made it home and will stay there.", "Она дошла до дома и останется там.");
         case "hint.home.missing" -> t("Set a home point first.", "Сначала задай точку дома.");
         case "hint.home.dimension" -> t("Home is saved in another dimension, so she stopped.", "Дом сохранён в другом измерении, поэтому она остановилась.");
         case "hint.conflict.deep", "Я всё ещё помню эти слова и не готова сразу оттаять." -> t("She still remembers those words and is not ready to thaw out yet.", "Я всё ещё помню эти слова и не готова сразу оттаять.");
         case "hint.conflict.fresh", "Мне всё ещё неприятно после такого разговора." -> t("She still feels hurt after that conversation.", "Мне всё ещё неприятно после такого разговора.");
         case "hint.apology.heard", "Извинение я услышала, но мне нужно немного времени." -> t("She heard the apology, but she still needs some time.", "Извинение я услышала, но мне нужно немного времени.");
         case "hint.apology.helped", "Спасибо за извинение. Мне уже спокойнее." -> t("The apology helped and she feels calmer now.", "Спасибо за извинение. Мне уже спокойнее.");
         case "hint.repair.slow", "Хороший тон помогает, но я ещё не до конца отпустила обиду." -> t("A kinder tone helps, but she has not fully let go of the hurt yet.", "Хороший тон помогает, но я ещё не до конца отпустила обиду.");
         case "hint.repair.better", "Мне уже заметно легче говорить с тобой." -> t("It is already easier for her to talk to you.", "Мне уже заметно легче говорить с тобой.");
         case "hint.calm.restored", "Я снова чувствую себя спокойнее рядом с тобой." -> t("She feels calmer beside you again.", "Я снова чувствую себя спокойнее рядом с тобой.");
         default -> hint;
      };
   }

   private static String humanizeCommandMode(String mode) {
      return switch (mode) {
         case "FOLLOW" -> t("Following", "За тобой");
         case "STAY" -> t("Staying", "Стоит");
         case "SIT" -> t("Sitting", "Сидит");
         case "HOME" -> t("Going home", "Идёт домой");
         default -> mode;
      };
   }

   private static String localizedSlotName(EquipmentSlot slot) {
      return switch (slot) {
         case MAINHAND -> t("Weapon", "Оружие");
         case OFFHAND -> t("Offhand", "Левая рука");
         case HEAD -> t("Head", "Шлем");
         case CHEST -> t("Chest", "Нагрудник");
         case LEGS -> t("Legs", "Поножи");
         case FEET -> t("Feet", "Ботинки");
         default -> slot.getName();
      };
   }

   private static String t(String english, String russian) {
      return ClientLocalization.text(english, russian);
   }

   private CompanionSnapshot withCompanionName(String companionName) {
      return new CompanionSnapshot(
              this.snapshot.ownerUuid(), companionName,
              this.snapshot.mood(), this.snapshot.energy(), this.snapshot.trust(),
              this.snapshot.resentment(), this.snapshot.reconciliationProgress(),
              this.snapshot.activeSkinId(), this.snapshot.lastAction(), this.snapshot.lastCareHint(),
              this.snapshot.commandMode(), this.snapshot.emotion(),
              this.snapshot.conflictState(), this.snapshot.relationshipStage(),
              this.snapshot.recentTurns(), this.snapshot.importantFacts(), this.snapshot.promises(),
              this.snapshot.lastSeenWorldTime(),
              this.snapshot.homeDimension(), this.snapshot.homeX(), this.snapshot.homeY(), this.snapshot.homeZ()
      );
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.activeTab == ScreenTab.CHAT && keyCode == 257) {
         this.sendChat();
         return true;
      }
      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   private record InventorySlotView(EquipmentSlot slot) {}

   private enum ScreenTab {
      CHAT("Chat", "Чат"),
      CARE("Heart", "Уход"),
      INVENTORY("Inventory", "Инвентарь"),
      APPEARANCE("Looks", "Внешность"),
      SETTINGS("Settings", "Настройки");

      private final String english, russian;
      ScreenTab(String english, String russian) { this.english = english; this.russian = russian; }
      public String label() { return CompanionModernScreen.t(english, russian); }
   }

   private record SlotRect(int x, int y, int width, int height) {}

   private record UiLayout(int sidebarX, int sidebarY, int sidebarWidth, int sidebarHeight,
                           int heroX, int heroY, int heroWidth, int heroHeight,
                           int bodyX, int bodyY, int bodyWidth, int bodyHeight) {
      int heroRight() { return heroX + heroWidth; }
      int bodyRight() { return bodyX + bodyWidth; }
      int bodyBottom() { return bodyY + bodyHeight; }
   }
}