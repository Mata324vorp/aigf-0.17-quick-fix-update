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
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CompanionAdaptiveScreen extends Screen {
   private static final EquipmentSlot[] INVENTORY_SLOTS = {
           EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
           EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   private final CompanionEntity companion;
   private CompanionSnapshot snapshot;
   private final List<String> chatLines = new ArrayList<>();
   private final List<AbstractWidget> sidebarWidgets = new ArrayList<>();
   private final List<AbstractWidget> chatWidgets = new ArrayList<>();
   private final List<AbstractWidget> inventoryWidgets = new ArrayList<>();
   private final List<AbstractWidget> appearanceWidgets = new ArrayList<>();
   private final List<AbstractWidget> settingsWidgets = new ArrayList<>();
   private ScreenTab activeTab = ScreenTab.CHAT;
   private EditBox chatInput;
   private Button sendButton;
   private EditBox nameBox;
   private Button renameButton;
   private Button giftButton;
   private EditBox importNameBox;
   private Button importSkinButton;
   private Button previousSkinButton;
   private Button nextSkinButton;
   private Button applySkinButton;
   private Button reloadSkinsButton;
   private EditBox apiKeyBox;
   private EditBox modelIdBox;
   private EditBox timeoutBox;
   private EditBox contextTurnsBox;
   private Button previousModelButton;
   private Button nextModelButton;
   private Button saveSettingsButton;
   private Button testConnectionButton;
   private List<String> skinIds = new ArrayList<>();
   private int selectedSkinIndex;
   private String statusLine = "";
   private boolean requestInFlight;

   public CompanionAdaptiveScreen(CompanionEntity companion, CompanionSnapshot snapshot) {
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
      this.sidebarWidgets.clear();
      this.chatWidgets.clear();
      this.inventoryWidgets.clear();
      this.appearanceWidgets.clear();
      this.settingsWidgets.clear();
      Layout layout = this.layout();
      this.buildSidebar(layout);
      this.buildChat(layout);
      this.buildInventory(layout);
      this.buildAppearance(layout);
      this.buildSettings(layout);
      this.reloadSkinList();
      this.loadSettings();
      this.updateWidgetVisibility();
   }

   private void buildSidebar(Layout layout) {
      int x = layout.sidebarX + 12;
      int y = layout.sidebarY + 92;
      int width = layout.sidebarWidth - 24;

      for (ScreenTab tab : ScreenTab.values()) {
         this.sidebarWidgets.add(this.addRenderableWidget(
                 new AigfSoftButton(x, y, width, 26, AigfUi.text(tab.label()),
                         button -> this.switchTab(tab), () -> this.activeTab == tab, -2986325)
         ));
         y += 32;
      }

      int actionY = layout.sidebarBottom() - 124;
      int half = (width - 8) / 2;
      this.sidebarWidgets.add(this.addRenderableWidget(actionButton(x, actionY, half, t("Follow", "За мной"), CompanionActionIntent.FOLLOW)));
      this.sidebarWidgets.add(this.addRenderableWidget(actionButton(x + half + 8, actionY, half, t("Stay", "Стоять"), CompanionActionIntent.STAY)));
      this.sidebarWidgets.add(this.addRenderableWidget(actionButton(x, actionY + 32, half, t("Sit", "Сидеть"), CompanionActionIntent.SIT)));
      this.sidebarWidgets.add(this.addRenderableWidget(actionButton(x + half + 8, actionY + 32, half, t("Set home", "Точка дома"), CompanionActionIntent.SET_HOME)));
      this.sidebarWidgets.add(this.addRenderableWidget(
              new AigfSoftButton(x, actionY + 64, width, 26, AigfUi.text(t("Go home", "Иди домой")),
                      button -> this.sendDirectAction(CompanionActionIntent.GO_HOME),
                      () -> this.snapshot.commandMode().name().equals("HOME"), -1596986)
      ));
   }

   private Button actionButton(int x, int y, int width, String text, CompanionActionIntent intent) {
      return new AigfSoftButton(x, y, width, 26, AigfUi.text(text), button -> this.sendDirectAction(intent), () -> {
         return switch (intent) {
            case FOLLOW -> this.snapshot.commandMode().name().equals("FOLLOW");
            case STAY -> this.snapshot.commandMode().name().equals("STAY");
            case SIT -> this.snapshot.commandMode().name().equals("SIT");
            default -> false;
         };
      }, -1596986);
   }

   private void buildChat(Layout layout) {
      this.chatInput = this.addRenderableWidget(
              new AigfSoftEditBox(this.font, layout.contentX + 18, layout.contentBottom() - 36, layout.contentWidth - 140, 26,
                      AigfUi.text(t("Talk...", "Напиши...")))
      );
      this.chatInput.setMaxLength(256);
      this.sendButton = this.addRenderableWidget(
              new AigfSoftButton(layout.contentRight() - 110, layout.contentBottom() - 36, 92, 26,
                      AigfUi.text(t("Send", "Отправить")), button -> this.sendChat(), () -> false, -2986325)
      );
      this.chatWidgets.add(this.chatInput);
      this.chatWidgets.add(this.sendButton);
   }

   private void buildInventory(Layout layout) {
      this.nameBox = this.addRenderableWidget(
              new AigfSoftEditBox(this.font, layout.contentX + 18, layout.contentY + 40, layout.contentWidth - 180, 26,
                      AigfUi.text(t("Name", "Имя")))
      );
      this.nameBox.setMaxLength(24);
      this.nameBox.setValue(this.snapshot.companionName());
      this.renameButton = this.addRenderableWidget(
              new AigfSoftButton(layout.contentRight() - 144, layout.contentY + 40, 126, 26,
                      AigfUi.text(t("Rename", "Переименовать")), button -> this.renameCompanion(), () -> false, -2986325)
      );
      this.giftButton = this.addRenderableWidget(
              new AigfSoftButton(layout.contentX + 18, layout.contentY + 74, layout.contentWidth - 36, 26,
                      AigfUi.text(t("Gift from hand", "Подарить из руки")), button -> this.sendGiftFromHand(), () -> false, -1596986)
      );
      this.inventoryWidgets.add(this.nameBox);
      this.inventoryWidgets.add(this.renameButton);
      this.inventoryWidgets.add(this.giftButton);

      for (int i = 0; i < INVENTORY_SLOTS.length; i++) {
         SlotRect rect = this.inventorySlotRect(layout, i);
         EquipmentSlot slot = INVENTORY_SLOTS[i];
         this.inventoryWidgets.add(this.addRenderableWidget(
                 new AigfSoftButton(rect.x + rect.width - 148, rect.y + 48, 68, 22,
                         AigfUi.text(t("From hand", "Из руки")), button -> this.sendInventoryAction(slot, "PUT_FROM_HAND"), () -> false, -1596986)
         ));
         this.inventoryWidgets.add(this.addRenderableWidget(
                 new AigfSoftButton(rect.x + rect.width - 72, rect.y + 48, 54, 22,
                         AigfUi.text(t("Take", "Забрать")), button -> this.sendInventoryAction(slot, "TAKE_TO_PLAYER"), () -> false, -2986325)
         ));
      }
   }

   private void buildAppearance(Layout layout) {
      this.importNameBox = this.addRenderableWidget(
              new AigfSoftEditBox(this.font, layout.contentX + 18, layout.contentY + 40, layout.contentWidth - 180, 26,
                      AigfUi.text(t("Nickname", "Ник")))
      );
      this.importNameBox.setMaxLength(16);
      this.importSkinButton = this.addRenderableWidget(
              new AigfSoftButton(layout.contentRight() - 144, layout.contentY + 40, 126, 26,
                      AigfUi.text(t("Import skin", "Импорт скина")), button -> this.importSkin(), () -> false, -2986325)
      );
      this.previousSkinButton = this.addRenderableWidget(
              new AigfSoftButton(layout.contentX + 18, layout.contentBottom() - 36, 40, 26,
                      AigfUi.text("<"), button -> this.shiftSkinSelection(-1), () -> false, -1596986)
      );
      this.nextSkinButton = this.addRenderableWidget(
              new AigfSoftButton(layout.contentX + 64, layout.contentBottom() - 36, 40, 26,
                      AigfUi.text(">"), button -> this.shiftSkinSelection(1), () -> false, -1596986)
      );
      this.applySkinButton = this.addRenderableWidget(
              new AigfSoftButton(layout.contentRight() - 234, layout.contentBottom() - 36, 104, 26,
                      AigfUi.text(t("Apply", "Применить")), button -> this.applySelectedSkin(), () -> false, -2986325)
      );
      this.reloadSkinsButton = this.addRenderableWidget(
              new AigfSoftButton(layout.contentRight() - 122, layout.contentBottom() - 36, 104, 26,
                      AigfUi.text(t("Reload", "Обновить")), button -> this.reloadSkinList(), () -> false, -1596986)
      );
      this.appearanceWidgets.add(this.importNameBox);
      this.appearanceWidgets.add(this.importSkinButton);
      this.appearanceWidgets.add(this.previousSkinButton);
      this.appearanceWidgets.add(this.nextSkinButton);
      this.appearanceWidgets.add(this.applySkinButton);
      this.appearanceWidgets.add(this.reloadSkinsButton);
   }

   private void buildSettings(Layout layout) {
      this.apiKeyBox = this.addRenderableWidget(
              new AigfSoftEditBox(this.font, layout.contentX + 18, layout.contentY + 40, layout.contentWidth - 36, 26,
                      AigfUi.text("API Key"))
      );
      this.apiKeyBox.setMaxLength(256);
      this.previousModelButton = this.addRenderableWidget(
              new AigfSoftButton(layout.contentX + 18, layout.contentY + 82, 36, 26,
                      AigfUi.text("<"), button -> this.cycleModel(-1), () -> false, -1596986)
      );
      this.modelIdBox = this.addRenderableWidget(
              new AigfSoftEditBox(this.font, layout.contentX + 60, layout.contentY + 82, layout.contentWidth - 156, 26,
                      AigfUi.text("Model"))
      );
      this.modelIdBox.setMaxLength(128);
      this.nextModelButton = this.addRenderableWidget(
              new AigfSoftButton(layout.contentRight() - 54, layout.contentY + 82, 36, 26,
                      AigfUi.text(">"), button -> this.cycleModel(1), () -> false, -1596986)
      );
      this.timeoutBox = this.addRenderableWidget(
              new AigfSoftEditBox(this.font, layout.contentX + 18, layout.contentY + 124, 110, 26,
                      AigfUi.text(t("Timeout", "Таймаут")))
      );
      this.contextTurnsBox = this.addRenderableWidget(
              new AigfSoftEditBox(this.font, layout.contentX + 136, layout.contentY + 124, 128, 26,
                      AigfUi.text(t("Context", "Контекст")))
      );
      this.saveSettingsButton = this.addRenderableWidget(
              new AigfSoftButton(layout.contentX + 18, layout.contentBottom() - 36, 156, 26,
                      AigfUi.text(t("Save settings", "Сохранить настройки")), button -> this.saveSettings(), () -> false, -2986325)
      );
      this.testConnectionButton = this.addRenderableWidget(
              new AigfSoftButton(layout.contentX + 182, layout.contentBottom() - 36, 168, 26,
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
      this.updateWidgetVisibility();
   }

   private void updateWidgetVisibility() {
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
         this.statusLine = t("Waiting for reply...", "Жду ответ...");
         this.chatLines.add(t("You: ", "Ты: ") + userMessage);
         this.chatInput.setValue("");
         OpenAiClient.chat(userMessage, this.snapshot)
                 .whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
                    this.requestInFlight = false;
                    if (error == null && result != null) {
                       ConversationMoodAnalyzer.Analysis analysis = ConversationMoodAnalyzer.analyze(userMessage);
                       CompanionAiResult adjusted = OpenAiClient.enforceConversationTone(userMessage, this.snapshot, result, analysis);
                       this.chatLines.add(this.snapshot.companionName() + ": " + adjusted.spokenText());
                       this.statusLine = adjusted.careHint() != null && !adjusted.careHint().isBlank()
                               ? this.localizeCareHint(adjusted.careHint())
                               : humanizeAnalysis(analysis.summary());
                       AigfNetwork.CHANNEL.sendToServer(
                               new ServerboundChatTurnPacket(
                                       this.companion.getId(),
                                       userMessage,
                                       adjusted.spokenText(),
                                       adjusted.emotion().name(),
                                       adjusted.actionIntent().name(),
                                       adjusted.memoryFact(),
                                       adjusted.careHint(),
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
         case FOLLOW -> t("Follow mode", "Режим следования");
         case STAY -> t("Stay mode", "Режим ожидания");
         case SIT -> t("Sit mode", "Режим сидеть");
         case SET_HOME -> t("Home saved", "Дом сохранён");
         case GO_HOME -> this.snapshot.hasHome() ? t("Going home", "Идёт домой") : t("Home is not set", "Дом не задан");
         default -> t("Command sent", "Команда отправлена");
      };
   }

   private void renameCompanion() {
      String name = this.nameBox.getValue().trim();
      if (name.isBlank()) {
         this.statusLine = t("Name is empty", "Имя пустое");
      } else {
         if (name.length() > 24) name = name.substring(0, 24);
         this.nameBox.setValue(name);
         this.snapshot = this.withCompanionName(name);
         CompanionClientState.updateSnapshot(this.companion.getId(), this.snapshot);
         this.rebuildChatLines();
         AigfNetwork.CHANNEL.sendToServer(new ServerboundRenameCompanionPacket(this.companion.getId(), name));
         this.statusLine = t("Name sent", "Имя отправлено");
      }
   }

   private void sendInventoryAction(EquipmentSlot slot, String actionName) {
      AigfNetwork.CHANNEL.sendToServer(new ServerboundCompanionInventoryPacket(this.companion.getId(), slot.name(), actionName));
      this.statusLine = switch (actionName) {
         case "PUT_FROM_HAND" -> t("Item moved", "Предмет отправлен");
         case "TAKE_TO_PLAYER" -> t("Item taken", "Предмет забран");
         default -> "";
      };
   }

   private void sendGiftFromHand() {
      ItemStack handStack = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
      if (handStack.isEmpty()) {
         this.statusLine = t("Main hand is empty", "Главная рука пуста");
      } else {
         AigfNetwork.CHANNEL.sendToServer(new ServerboundGiftFromHandPacket(this.companion.getId()));
         this.statusLine = t("Gift sent", "Подарок отправлен");
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
         int found = this.skinIds.indexOf(this.snapshot.activeSkinId());
         this.selectedSkinIndex = found >= 0 ? found : 0;
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
         this.statusLine = t("Importing...", "Импортирую...");
         CompanionSkinManager.importSkinByUsername(nickname).whenComplete((skinId, error) -> Minecraft.getInstance().execute(() -> {
            if (error == null && skinId != null) {
               this.reloadSkinList();
               int found = this.skinIds.indexOf(skinId);
               if (found >= 0) this.selectedSkinIndex = found;
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
      this.statusLine = t("Testing...", "Проверяю...");
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
      Layout layout = this.layout();
      AigfUi.drawBackdrop(guiGraphics, this.width, this.height, (float) (Util.getMillis() % 100000L) / 1000.0F);
      this.renderSidebar(guiGraphics, layout);
      this.renderHeader(guiGraphics, layout);
      this.renderContent(guiGraphics, layout);
      this.renderStatus(guiGraphics, layout);
      super.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   private void renderSidebar(GuiGraphics guiGraphics, Layout layout) {
      AigfUi.drawPanel(guiGraphics, layout.sidebarX, layout.sidebarY, layout.sidebarWidth, layout.sidebarHeight,
              -870050271, -535753966, AigfUi.colorAlpha(-1596986, 0.16F));
      guiGraphics.drawString(this.font, AigfUi.text(this.snapshot.companionName()), layout.sidebarX + 18, layout.sidebarY + 18, -133381, false);
      guiGraphics.drawString(this.font,
              AigfUi.text(t("Bond", "Связь") + ": " + humanizeRelationship(this.snapshot.relationshipStage().name())),
              layout.sidebarX + 18, layout.sidebarY + 36, -2505259, false);
      guiGraphics.drawString(this.font,
              AigfUi.text(t("Mode", "Режим") + ": " + humanizeCommandMode(this.snapshot.commandMode().name())),
              layout.sidebarX + 18, layout.sidebarY + 50, -2505259, false);
      guiGraphics.drawString(this.font,
              AigfUi.text(this.snapshot.hasHome() ? t("Home", "Дом") + ": " + this.snapshot.homeX() + ", " + this.snapshot.homeY()
                      : t("Home not set", "Дом не задан")),
              layout.sidebarX + 18, layout.sidebarBottom() - 18, -2505259, false);
   }

   private void renderHeader(GuiGraphics guiGraphics, Layout layout) {
      AigfUi.drawPanel(guiGraphics, layout.headerX, layout.headerY, layout.headerWidth, layout.headerHeight,
              -869327828, -535425000, AigfUi.colorAlpha(-1596986, 0.2F));
      guiGraphics.drawString(this.font, AigfUi.text(this.snapshot.companionName()), layout.headerX + 18, layout.headerY + 18, -133381, false);
      this.drawChip(guiGraphics, layout.headerX + 18, layout.headerY + 42, humanizeEmotion(this.snapshot.emotion().name()), -1596986);
      this.drawChip(guiGraphics, layout.headerX + 140, layout.headerY + 42, humanizeCommandMode(this.snapshot.commandMode().name()), -3424778);
      this.drawChip(guiGraphics, layout.headerX + 262, layout.headerY + 42, humanizeConflict(this.snapshot.conflictState().name()), -3607324);
      guiGraphics.drawString(this.font,
              AigfUi.text(t("Mood", "Настроение") + " " + this.snapshot.mood() + "   " +
                      t("Trust", "Доверие") + " " + this.snapshot.trust() + "   " +
                      t("Energy", "Энергия") + " " + this.snapshot.energy()),
              layout.headerX + 18, layout.headerY + 72, -2505259, false);
   }

   private void renderContent(GuiGraphics guiGraphics, Layout layout) {
      AigfUi.drawPanel(guiGraphics, layout.contentX, layout.contentY, layout.contentWidth, layout.contentHeight,
              -770240488, -284161519, AigfUi.colorAlpha(-1596986, 0.14F));
      switch (this.activeTab) {
         case CHAT -> this.renderChatTab(guiGraphics, layout);
         case INVENTORY -> this.renderInventoryTab(guiGraphics, layout);
         case APPEARANCE -> this.renderAppearanceTab(guiGraphics, layout);
         case SETTINGS -> this.renderSettingsTab(guiGraphics, layout);
         default -> {}
      }
   }

   private void renderStatus(GuiGraphics guiGraphics, Layout layout) {
      if (this.statusLine != null && !this.statusLine.isBlank()) {
         int width = Math.min(420, layout.contentWidth - 36);
         AigfUi.drawPanel(guiGraphics, layout.contentX + 18, layout.contentBottom() + 8, width, 24,
                 -1088023002, -703328233, AigfUi.colorAlpha(-1596986, 0.16F));
         // Truncar el texto como String
         String truncatedText = this.font.plainSubstrByWidth(this.statusLine, width - 12);
         // Dibujar el Component truncado
         guiGraphics.drawString(this.font, AigfUi.text(truncatedText),
                 layout.contentX + 24, layout.contentBottom() + 16, -133381, false);
      }
   }

   private void renderChatTab(GuiGraphics guiGraphics, Layout layout) {
      guiGraphics.drawString(this.font, AigfUi.text(t("Chat", "Чат")), layout.contentX + 18, layout.contentY + 14, -133381, false);
      int boxX = layout.contentX + 18;
      int boxY = layout.contentY + 38;
      int boxWidth = layout.contentWidth - 36;
      int boxHeight = layout.contentHeight - 84;
      AigfUi.drawCard(guiGraphics, boxX, boxY, boxWidth, boxHeight, true);
      List<FormattedCharSequence> wrappedLines = new ArrayList<>();
      for (String line : this.chatLines) {
         wrappedLines.addAll(this.font.split(AigfUi.text(line), boxWidth - 16));
      }
      int maxLines = Math.max(4, (boxHeight - 12) / 10);
      int start = Math.max(0, wrappedLines.size() - maxLines);
      int y = boxY + 8;
      for (int i = start; i < wrappedLines.size(); i++) {
         guiGraphics.drawString(this.font, wrappedLines.get(i), boxX + 8, y, -133381, false);
         y += 10;
      }
   }

   private void renderInventoryTab(GuiGraphics guiGraphics, Layout layout) {
      guiGraphics.drawString(this.font, AigfUi.text(t("Inventory", "Инвентарь")), layout.contentX + 18, layout.contentY + 14, -133381, false);
      for (int i = 0; i < INVENTORY_SLOTS.length; i++) {
         SlotRect rect = this.inventorySlotRect(layout, i);
         EquipmentSlot slot = INVENTORY_SLOTS[i];
         ItemStack stack = this.companion.getItemBySlot(slot);
         String itemName = stack.isEmpty() ? t("Empty", "Пусто") : stack.getHoverName().getString();
         AigfUi.drawCard(guiGraphics, rect.x, rect.y, rect.width, rect.height, false);
         guiGraphics.drawString(this.font, AigfUi.text(localizedSlotName(slot)), rect.x + 12, rect.y + 10, -133381, false);
         // Corregir aquí: usar plainSubstrByWidth en lugar de substrByWidth
         guiGraphics.drawString(this.font, AigfUi.text(this.font.plainSubstrByWidth(itemName, rect.width - 24)), rect.x + 12, rect.y + 26, -2505259, false);
      }
   }

   private void renderAppearanceTab(GuiGraphics guiGraphics, Layout layout) {
      guiGraphics.drawString(this.font, AigfUi.text(t("Looks", "Внешность")), layout.contentX + 18, layout.contentY + 14, -133381, false);
      AigfUi.drawCard(guiGraphics, layout.contentX + 18, layout.contentY + 80, layout.contentWidth - 36, layout.contentHeight - 124, true);
      String selectedSkin = this.skinIds.isEmpty() ? "builtin:alex" : this.skinIds.get(this.selectedSkinIndex);
      guiGraphics.drawString(this.font, AigfUi.text(t("Active", "Текущий") + ": " + this.snapshot.activeSkinId()),
              layout.contentX + 34, layout.contentY + 104, -133381, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Selected", "Выбран") + ": " + selectedSkin),
              layout.contentX + 34, layout.contentY + 122, -2505259, false);
      guiGraphics.drawString(this.font, AigfUi.text("config/aigf/skins"),
              layout.contentX + 34, layout.contentY + 140, -2505259, false);
   }

   private void renderSettingsTab(GuiGraphics guiGraphics, Layout layout) {
      guiGraphics.drawString(this.font, AigfUi.text(t("Settings", "Настройки")), layout.contentX + 18, layout.contentY + 14, -133381, false);
      guiGraphics.drawString(this.font, AigfUi.text("API"), layout.contentX + 18, layout.contentY + 28, -2505259, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Model", "Модель")), layout.contentX + 18, layout.contentY + 70, -2505259, false);
      guiGraphics.drawString(this.font, AigfUi.text(t("Quick fields only", "Только нужные поля")), layout.contentX + 18, layout.contentY + 196, -2505259, false);
   }

   private void drawStatCard(GuiGraphics guiGraphics, int x, int y, int width, String label, int value, int accent) {
      AigfUi.drawCard(guiGraphics, x, y, width, 106, true);
      guiGraphics.drawString(this.font, AigfUi.text(label), x + 12, y + 10, -133381, false);
      guiGraphics.drawString(this.font, AigfUi.text(String.valueOf(value)), x + 12, y + 28, -133381, false);
      AigfUi.drawProgressBar(guiGraphics, this.font, x + 12, y + 48, width - 24, t("Level", "Уровень"), value, accent);
   }

   private void drawChip(GuiGraphics guiGraphics, int x, int y, String text, int accent) {
      int chipWidth = Math.min(110, this.font.width(AigfUi.text(text)) + 18);
      AigfUi.drawRoundedPanel(guiGraphics, x, y, chipWidth, 22,
              AigfUi.colorAlpha(AigfUi.lighten(accent, 0.18F), 0.32F),
              AigfUi.colorAlpha(-14477276, 0.86F),
              AigfUi.colorAlpha(accent, 0.24F));
      guiGraphics.drawString(this.font, AigfUi.text(text), x + 9, y + 7, -133381, false);
   }

   private SlotRect inventorySlotRect(Layout layout, int index) {
      int width = (layout.contentWidth - 48) / 2;
      int x = layout.contentX + 18 + (index % 2) * (width + 12);
      int y = layout.contentY + 110 + (index / 2) * 84;
      return new SlotRect(x, y, width, 74);
   }

   private Layout layout() {
      int outer = 16;
      int rootWidth = Math.min(this.width - outer * 2, 980);
      int rootHeight = Math.min(this.height - outer * 2, 620);
      int rootX = (this.width - rootWidth) / 2;
      int rootY = (this.height - rootHeight) / 2;
      int sidebarWidth = Math.min(210, rootWidth / 4);
      int sidebarX = rootX;
      int sidebarY = rootY;
      int sidebarHeight = rootHeight;
      int headerX = sidebarX + sidebarWidth + 12;
      int headerY = rootY;
      int headerWidth = rootWidth - sidebarWidth - 12;
      int headerHeight = 92;
      int contentX = headerX;
      int contentY = headerY + headerHeight + 12;
      int contentWidth = headerWidth;
      int contentHeight = rootHeight - headerHeight - 44;
      return new Layout(sidebarX, sidebarY, sidebarWidth, sidebarHeight,
              headerX, headerY, headerWidth, headerHeight,
              contentX, contentY, contentWidth, contentHeight);
   }

   private static String humanizeAnalysis(String summary) {
      return switch (summary) {
         case "hurt_by_words" -> t("You hurt her.", "Ты её задел.");
         case "praised" -> t("She liked that.", "Ей это понравилось.");
         case "shared_discussion" -> t("She likes discussing things together.", "Ей нравится обсуждать всё вместе.");
         case "apology" -> t("The apology helped.", "Извинение помогло.");
         default -> "";
      };
   }

   private static String humanizeRelationship(String stage) {
      return switch (stage) {
         case "ATTACHED" -> t("Attached", "Близка");
         case "WARM" -> t("Warm", "Тепло");
         case "COLD" -> t("Cold", "Холодно");
         default -> t("Neutral", "Нейтрально");
      };
   }

   private static String humanizeConflict(String state) {
      return switch (state) {
         case "DISTANT" -> t("Distant", "Отстранена");
         case "OFFENDED" -> t("Offended", "Обижена");
         case "GUARDED" -> t("Guarded", "Насторожена");
         default -> t("Open", "Открыта");
      };
   }

   private static String humanizeEmotion(String emotion) {
      return switch (emotion) {
         case "HAPPY" -> t("Happy", "Рада");
         case "SHY" -> t("Shy", "Смущена");
         case "TIRED" -> t("Tired", "Устала");
         case "SAD" -> t("Sad", "Грустно");
         default -> t("Neutral", "Спокойна");
      };
   }

   private String localizeCareHint(String hint) {
      if (hint == null || hint.isBlank()) return "";
      if (hint.startsWith("hint.rename:")) return t("New name saved.", "Новое имя сохранено.");
      return switch (hint) {
         case "gift" -> t("She liked the gift.", "Ей понравился подарок.");
         case "hint.home.set" -> t("Home point saved.", "Точка дома сохранена.");
         case "hint.home.going" -> t("She is going home.", "Она идёт домой.");
         case "hint.home.arrived" -> t("She reached home.", "Она дошла до дома.");
         case "hint.home.missing" -> t("Set home first.", "Сначала задай дом.");
         default -> hint;
      };
   }

   private static String humanizeCommandMode(String mode) {
      return switch (mode) {
         case "FOLLOW" -> t("Following", "За тобой");
         case "STAY" -> t("Waiting", "Ждёт");
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
      if (this.activeTab == ScreenTab.CHAT && keyCode == 257) { // Enter key
         this.sendChat();
         return true;
      }
      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   // ---------- Inner classes ----------
   private record Layout(int sidebarX, int sidebarY, int sidebarWidth, int sidebarHeight,
                         int headerX, int headerY, int headerWidth, int headerHeight,
                         int contentX, int contentY, int contentWidth, int contentHeight) {
      int sidebarBottom() { return sidebarY + sidebarHeight; }
      int contentRight() { return contentX + contentWidth; }
      int contentBottom() { return contentY + contentHeight; }
   }

   private enum ScreenTab {
      CHAT("Chat", "Чат"),
      CARE("Care", "Уход"),
      INVENTORY("Inventory", "Инвентарь"),
      APPEARANCE("Looks", "Внешность"),
      SETTINGS("Settings", "Настройки");

      private final String english, russian;
      ScreenTab(String english, String russian) { this.english = english; this.russian = russian; }
      String label() { return CompanionAdaptiveScreen.t(english, russian); }
   }

   private record SlotRect(int x, int y, int width, int height) {}
}