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
import java.util.ArrayList;
import java.util.List;
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

public final class CompanionAdaptiveScreen extends Screen {
   private static final EquipmentSlot[] INVENTORY_SLOTS = new EquipmentSlot[]{
      EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   private final CompanionEntity companion;
   private CompanionSnapshot snapshot;
   private final List<String> chatLines = new ArrayList<>();
   private final List<AbstractWidget> sidebarWidgets = new ArrayList<>();
   private final List<AbstractWidget> chatWidgets = new ArrayList<>();
   private final List<AbstractWidget> inventoryWidgets = new ArrayList<>();
   private final List<AbstractWidget> appearanceWidgets = new ArrayList<>();
   private final List<AbstractWidget> settingsWidgets = new ArrayList<>();
   private CompanionAdaptiveScreen.ScreenTab activeTab = CompanionAdaptiveScreen.ScreenTab.CHAT;
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
      return this.companion.m_19879_();
   }

   public void applySnapshot(CompanionSnapshot snapshot) {
      this.snapshot = snapshot;
      this.rebuildChatLines();
      this.statusLine = this.localizeCareHint(snapshot.lastCareHint());
      this.selectCurrentSkin();
      if (this.nameBox != null && !this.nameBox.m_93696_()) {
         this.nameBox.m_94144_(snapshot.companionName());
      }
   }

   protected void m_7856_() {
      this.m_169413_();
      this.sidebarWidgets.clear();
      this.chatWidgets.clear();
      this.inventoryWidgets.clear();
      this.appearanceWidgets.clear();
      this.settingsWidgets.clear();
      CompanionAdaptiveScreen.Layout layout = this.layout();
      this.buildSidebar(layout);
      this.buildChat(layout);
      this.buildInventory(layout);
      this.buildAppearance(layout);
      this.buildSettings(layout);
      this.reloadSkinList();
      this.loadSettings();
      this.updateWidgetVisibility();
   }

   private void buildSidebar(CompanionAdaptiveScreen.Layout layout) {
      int x = layout.sidebarX + 12;
      int y = layout.sidebarY + 92;
      int width = layout.sidebarWidth - 24;

      for (CompanionAdaptiveScreen.ScreenTab tab : CompanionAdaptiveScreen.ScreenTab.values()) {
         this.sidebarWidgets
            .add(
               (AbstractWidget)this.m_142416_(
                  new AigfSoftButton(x, y, width, 26, AigfUi.text(tab.label()), button -> this.switchTab(tab), () -> this.activeTab == tab, -2986325)
               )
            );
         y += 32;
      }

      int actionY = layout.sidebarBottom() - 124;
      int half = (width - 8) / 2;
      this.sidebarWidgets.add((AbstractWidget)this.m_142416_(this.actionButton(x, actionY, half, t("Follow", "За мной"), CompanionActionIntent.FOLLOW)));
      this.sidebarWidgets.add((AbstractWidget)this.m_142416_(this.actionButton(x + half + 8, actionY, half, t("Stay", "Стоять"), CompanionActionIntent.STAY)));
      this.sidebarWidgets.add((AbstractWidget)this.m_142416_(this.actionButton(x, actionY + 32, half, t("Sit", "Сидеть"), CompanionActionIntent.SIT)));
      this.sidebarWidgets
         .add((AbstractWidget)this.m_142416_(this.actionButton(x + half + 8, actionY + 32, half, t("Set home", "Точка дома"), CompanionActionIntent.SET_HOME)));
      this.sidebarWidgets
         .add(
            (AbstractWidget)this.m_142416_(
               new AigfSoftButton(
                  x,
                  actionY + 64,
                  width,
                  26,
                  AigfUi.text(t("Go home", "Иди домой")),
                  button -> this.sendDirectAction(CompanionActionIntent.GO_HOME),
                  () -> this.snapshot.commandMode().name().equals("HOME"),
                  -1596986
               )
            )
         );
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

   private void buildChat(CompanionAdaptiveScreen.Layout layout) {
      this.chatInput = (EditBox)this.m_142416_(
         new AigfSoftEditBox(
            this.f_96547_, layout.contentX + 18, layout.contentBottom() - 36, layout.contentWidth - 140, 26, AigfUi.text(t("Talk...", "Напиши..."))
         )
      );
      this.chatInput.m_94199_(256);
      this.sendButton = (Button)this.m_142416_(
         new AigfSoftButton(
            layout.contentRight() - 110,
            layout.contentBottom() - 36,
            92,
            26,
            AigfUi.text(t("Send", "Отправить")),
            button -> this.sendChat(),
            () -> false,
            -2986325
         )
      );
      this.chatWidgets.add(this.chatInput);
      this.chatWidgets.add(this.sendButton);
   }

   private void buildInventory(CompanionAdaptiveScreen.Layout layout) {
      this.nameBox = (EditBox)this.m_142416_(
         new AigfSoftEditBox(this.f_96547_, layout.contentX + 18, layout.contentY + 40, layout.contentWidth - 180, 26, AigfUi.text(t("Name", "Имя")))
      );
      this.nameBox.m_94199_(24);
      this.nameBox.m_94144_(this.snapshot.companionName());
      this.renameButton = (Button)this.m_142416_(
         new AigfSoftButton(
            layout.contentRight() - 144,
            layout.contentY + 40,
            126,
            26,
            AigfUi.text(t("Rename", "Переименовать")),
            button -> this.renameCompanion(),
            () -> false,
            -2986325
         )
      );
      this.giftButton = (Button)this.m_142416_(
         new AigfSoftButton(
            layout.contentX + 18,
            layout.contentY + 74,
            layout.contentWidth - 36,
            26,
            AigfUi.text(t("Gift from hand", "Подарить из руки")),
            button -> this.sendGiftFromHand(),
            () -> false,
            -1596986
         )
      );
      this.inventoryWidgets.add(this.nameBox);
      this.inventoryWidgets.add(this.renameButton);
      this.inventoryWidgets.add(this.giftButton);

      for (int index = 0; index < INVENTORY_SLOTS.length; index++) {
         CompanionAdaptiveScreen.SlotRect rect = this.inventorySlotRect(layout, index);
         EquipmentSlot slot = INVENTORY_SLOTS[index];
         this.inventoryWidgets
            .add(
               (AbstractWidget)this.m_142416_(
                  new AigfSoftButton(
                     rect.x + rect.width - 148,
                     rect.y + 48,
                     68,
                     22,
                     AigfUi.text(t("From hand", "Из руки")),
                     button -> this.sendInventoryAction(slot, "PUT_FROM_HAND"),
                     () -> false,
                     -1596986
                  )
               )
            );
         this.inventoryWidgets
            .add(
               (AbstractWidget)this.m_142416_(
                  new AigfSoftButton(
                     rect.x + rect.width - 72,
                     rect.y + 48,
                     54,
                     22,
                     AigfUi.text(t("Take", "Забрать")),
                     button -> this.sendInventoryAction(slot, "TAKE_TO_PLAYER"),
                     () -> false,
                     -2986325
                  )
               )
            );
      }
   }

   private void buildAppearance(CompanionAdaptiveScreen.Layout layout) {
      this.importNameBox = (EditBox)this.m_142416_(
         new AigfSoftEditBox(this.f_96547_, layout.contentX + 18, layout.contentY + 40, layout.contentWidth - 180, 26, AigfUi.text(t("Nickname", "Ник")))
      );
      this.importNameBox.m_94199_(16);
      this.importSkinButton = (Button)this.m_142416_(
         new AigfSoftButton(
            layout.contentRight() - 144,
            layout.contentY + 40,
            126,
            26,
            AigfUi.text(t("Import skin", "Импорт скина")),
            button -> this.importSkin(),
            () -> false,
            -2986325
         )
      );
      this.previousSkinButton = (Button)this.m_142416_(
         new AigfSoftButton(
            layout.contentX + 18, layout.contentBottom() - 36, 40, 26, AigfUi.text("<"), button -> this.shiftSkinSelection(-1), () -> false, -1596986
         )
      );
      this.nextSkinButton = (Button)this.m_142416_(
         new AigfSoftButton(
            layout.contentX + 64, layout.contentBottom() - 36, 40, 26, AigfUi.text(">"), button -> this.shiftSkinSelection(1), () -> false, -1596986
         )
      );
      this.applySkinButton = (Button)this.m_142416_(
         new AigfSoftButton(
            layout.contentRight() - 234,
            layout.contentBottom() - 36,
            104,
            26,
            AigfUi.text(t("Apply", "Применить")),
            button -> this.applySelectedSkin(),
            () -> false,
            -2986325
         )
      );
      this.reloadSkinsButton = (Button)this.m_142416_(
         new AigfSoftButton(
            layout.contentRight() - 122,
            layout.contentBottom() - 36,
            104,
            26,
            AigfUi.text(t("Reload", "Обновить")),
            button -> this.reloadSkinList(),
            () -> false,
            -1596986
         )
      );
      this.appearanceWidgets.add(this.importNameBox);
      this.appearanceWidgets.add(this.importSkinButton);
      this.appearanceWidgets.add(this.previousSkinButton);
      this.appearanceWidgets.add(this.nextSkinButton);
      this.appearanceWidgets.add(this.applySkinButton);
      this.appearanceWidgets.add(this.reloadSkinsButton);
   }

   private void buildSettings(CompanionAdaptiveScreen.Layout layout) {
      this.apiKeyBox = (EditBox)this.m_142416_(
         new AigfSoftEditBox(this.f_96547_, layout.contentX + 18, layout.contentY + 40, layout.contentWidth - 36, 26, AigfUi.text("API Key"))
      );
      this.apiKeyBox.m_94199_(256);
      this.previousModelButton = (Button)this.m_142416_(
         new AigfSoftButton(layout.contentX + 18, layout.contentY + 82, 36, 26, AigfUi.text("<"), button -> this.cycleModel(-1), () -> false, -1596986)
      );
      this.modelIdBox = (EditBox)this.m_142416_(
         new AigfSoftEditBox(this.f_96547_, layout.contentX + 60, layout.contentY + 82, layout.contentWidth - 156, 26, AigfUi.text("Model"))
      );
      this.modelIdBox.m_94199_(128);
      this.nextModelButton = (Button)this.m_142416_(
         new AigfSoftButton(layout.contentRight() - 54, layout.contentY + 82, 36, 26, AigfUi.text(">"), button -> this.cycleModel(1), () -> false, -1596986)
      );
      this.timeoutBox = (EditBox)this.m_142416_(
         new AigfSoftEditBox(this.f_96547_, layout.contentX + 18, layout.contentY + 124, 110, 26, AigfUi.text(t("Timeout", "Таймаут")))
      );
      this.contextTurnsBox = (EditBox)this.m_142416_(
         new AigfSoftEditBox(this.f_96547_, layout.contentX + 136, layout.contentY + 124, 128, 26, AigfUi.text(t("Context", "Контекст")))
      );
      this.saveSettingsButton = (Button)this.m_142416_(
         new AigfSoftButton(
            layout.contentX + 18,
            layout.contentBottom() - 36,
            156,
            26,
            AigfUi.text(t("Save settings", "Сохранить настройки")),
            button -> this.saveSettings(),
            () -> false,
            -2986325
         )
      );
      this.testConnectionButton = (Button)this.m_142416_(
         new AigfSoftButton(
            layout.contentX + 182,
            layout.contentBottom() - 36,
            168,
            26,
            AigfUi.text(t("Test connection", "Проверить связь")),
            button -> this.testConnection(),
            () -> false,
            -1596986
         )
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

   private void switchTab(CompanionAdaptiveScreen.ScreenTab tab) {
      this.activeTab = tab;
      this.updateWidgetVisibility();
   }

   private void updateWidgetVisibility() {
      this.setVisible(this.chatWidgets, this.activeTab == CompanionAdaptiveScreen.ScreenTab.CHAT);
      this.setVisible(this.inventoryWidgets, this.activeTab == CompanionAdaptiveScreen.ScreenTab.INVENTORY);
      this.setVisible(this.appearanceWidgets, this.activeTab == CompanionAdaptiveScreen.ScreenTab.APPEARANCE);
      this.setVisible(this.settingsWidgets, this.activeTab == CompanionAdaptiveScreen.ScreenTab.SETTINGS);
   }

   private void setVisible(List<AbstractWidget> widgets, boolean visible) {
      for (AbstractWidget widget : widgets) {
         widget.f_93624_ = visible;
         widget.f_93623_ = visible;
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
      String userMessage = this.chatInput.m_94155_().trim();
      if (!this.requestInFlight && !userMessage.isBlank()) {
         this.requestInFlight = true;
         this.statusLine = t("Waiting for reply...", "Жду ответ...");
         this.chatLines.add(t("You: ", "Ты: ") + userMessage);
         this.chatInput.m_94144_("");
         OpenAiClient.chat(userMessage, this.snapshot)
            .whenComplete(
               (result, error) -> Minecraft.m_91087_()
                  .execute(
                     () -> {
                        this.requestInFlight = false;
                        if (error == null && result != null) {
                           ConversationMoodAnalyzer.Analysis analysis = ConversationMoodAnalyzer.analyze(userMessage);
                           CompanionAiResult adjusted = OpenAiClient.enforceConversationTone(userMessage, this.snapshot, result, analysis);
                           this.chatLines.add(this.snapshot.companionName() + ": " + adjusted.spokenText());
                           this.statusLine = adjusted.careHint() != null && !adjusted.careHint().isBlank()
                              ? this.localizeCareHint(adjusted.careHint())
                              : humanizeAnalysis(analysis.summary());
                           AigfNetwork.CHANNEL
                              .sendToServer(
                                 new ServerboundChatTurnPacket(
                                    this.companion.m_19879_(),
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
                     }
                  )
            );
      }
   }

   private void sendDirectAction(CompanionActionIntent intent) {
      AigfNetwork.CHANNEL.sendToServer(new ServerboundDirectActionPacket(this.companion.m_19879_(), intent.name()));

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
      String name = this.nameBox.m_94155_().trim();
      if (name.isBlank()) {
         this.statusLine = t("Name is empty", "Имя пустое");
      } else {
         if (name.length() > 24) {
            name = name.substring(0, 24);
            this.nameBox.m_94144_(name);
         }

         this.snapshot = this.withCompanionName(name);
         CompanionClientState.updateSnapshot(this.companion.m_19879_(), this.snapshot);
         this.rebuildChatLines();
         AigfNetwork.CHANNEL.sendToServer(new ServerboundRenameCompanionPacket(this.companion.m_19879_(), name));
         this.statusLine = t("Name sent", "Имя отправлено");
      }
   }

   private void sendInventoryAction(EquipmentSlot slot, String actionName) {
      AigfNetwork.CHANNEL.sendToServer(new ServerboundCompanionInventoryPacket(this.companion.m_19879_(), slot.name(), actionName));

      this.statusLine = switch (actionName) {
         case "PUT_FROM_HAND" -> t("Item moved", "Предмет отправлен");
         case "TAKE_TO_PLAYER" -> t("Item taken", "Предмет забран");
         default -> "";
      };
   }

   private void sendGiftFromHand() {
      ItemStack handStack = Minecraft.m_91087_().f_91074_ == null ? ItemStack.f_41583_ : Minecraft.m_91087_().f_91074_.m_21205_();
      if (handStack.m_41619_()) {
         this.statusLine = t("Main hand is empty", "Главная рука пуста");
      } else {
         AigfNetwork.CHANNEL.sendToServer(new ServerboundGiftFromHandPacket(this.companion.m_19879_()));
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
         AigfNetwork.CHANNEL.sendToServer(new ServerboundApplySkinPacket(this.companion.m_19879_(), selectedSkin));
      }
   }

   private void importSkin() {
      String nickname = this.importNameBox.m_94155_().trim();
      if (nickname.isBlank()) {
         this.statusLine = t("Nickname is empty", "Ник пустой");
      } else {
         this.statusLine = t("Importing...", "Импортирую...");
         CompanionSkinManager.importSkinByUsername(nickname).whenComplete((skinId, error) -> Minecraft.m_91087_().execute(() -> {
            if (error == null && skinId != null) {
               this.reloadSkinList();
               int found = this.skinIds.indexOf(skinId);
               if (found >= 0) {
                  this.selectedSkinIndex = found;
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
      this.apiKeyBox.m_94144_(settings.getOpenaiApiKey());
      this.modelIdBox.m_94144_(settings.getModelId());
      this.timeoutBox.m_94144_(String.valueOf(settings.getTimeoutSeconds()));
      this.contextTurnsBox.m_94144_(String.valueOf(settings.getMaxContextTurns()));
   }

   private void saveSettings() {
      ClientSettingsManager settings = ClientSettingsManager.get();
      settings.setOpenaiApiKey(this.apiKeyBox.m_94155_());
      settings.setModelId(this.modelIdBox.m_94155_());
      settings.setTimeoutSeconds(parseInteger(this.timeoutBox.m_94155_(), 45, 5, 120));
      settings.setMaxContextTurns(parseInteger(this.contextTurnsBox.m_94155_(), 10, 2, 20));
      settings.save();
      this.statusLine = t("Settings saved", "Настройки сохранены");
   }

   private void cycleModel(int direction) {
      this.modelIdBox.m_94144_(ClientSettingsManager.get().cycleModel(direction));
   }

   private void testConnection() {
      this.saveSettings();
      this.statusLine = t("Testing...", "Проверяю...");
      OpenAiClient.testConnection().thenAccept(result -> Minecraft.m_91087_().execute(() -> this.statusLine = result));
   }

   private static int parseInteger(String value, int fallback, int min, int max) {
      try {
         return Mth.m_14045_(Integer.parseInt(value.trim()), min, max);
      } catch (NumberFormatException ignored) {
         return fallback;
      }
   }

   public void m_88315_(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      CompanionAdaptiveScreen.Layout layout = this.layout();
      AigfUi.drawBackdrop(guiGraphics, this.f_96543_, this.f_96544_, (float)(Util.m_137550_() % 100000L) / 1000.0F);
      this.renderSidebar(guiGraphics, layout);
      this.renderHeader(guiGraphics, layout);
      this.renderContent(guiGraphics, layout);
      this.renderStatus(guiGraphics, layout);
      super.m_88315_(guiGraphics, mouseX, mouseY, partialTick);
   }

   private void renderSidebar(GuiGraphics guiGraphics, CompanionAdaptiveScreen.Layout layout) {
      AigfUi.drawPanel(
         guiGraphics, layout.sidebarX, layout.sidebarY, layout.sidebarWidth, layout.sidebarHeight, -870050271, -535753966, AigfUi.colorAlpha(-1596986, 0.16F)
      );
      guiGraphics.m_280614_(this.f_96547_, AigfUi.text(this.snapshot.companionName()), layout.sidebarX + 18, layout.sidebarY + 18, -133381, false);
      guiGraphics.m_280614_(
         this.f_96547_,
         AigfUi.text(t("Bond", "Связь") + ": " + humanizeRelationship(this.snapshot.relationshipStage().name())),
         layout.sidebarX + 18,
         layout.sidebarY + 36,
         -2505259,
         false
      );
      guiGraphics.m_280614_(
         this.f_96547_,
         AigfUi.text(t("Mode", "Режим") + ": " + humanizeCommandMode(this.snapshot.commandMode().name())),
         layout.sidebarX + 18,
         layout.sidebarY + 50,
         -2505259,
         false
      );
      guiGraphics.m_280614_(
         this.f_96547_,
         AigfUi.text(
            this.snapshot.hasHome() ? t("Home", "Дом") + ": " + this.snapshot.homeX() + ", " + this.snapshot.homeY() : t("Home not set", "Дом не задан")
         ),
         layout.sidebarX + 18,
         layout.sidebarBottom() - 18,
         -2505259,
         false
      );
   }

   private void renderHeader(GuiGraphics guiGraphics, CompanionAdaptiveScreen.Layout layout) {
      AigfUi.drawPanel(
         guiGraphics, layout.headerX, layout.headerY, layout.headerWidth, layout.headerHeight, -869327828, -535425000, AigfUi.colorAlpha(-1596986, 0.2F)
      );
      guiGraphics.m_280614_(this.f_96547_, AigfUi.text(this.snapshot.companionName()), layout.headerX + 18, layout.headerY + 18, -133381, false);
      this.drawChip(guiGraphics, layout.headerX + 18, layout.headerY + 42, humanizeEmotion(this.snapshot.emotion().name()), -1596986);
      this.drawChip(guiGraphics, layout.headerX + 140, layout.headerY + 42, humanizeCommandMode(this.snapshot.commandMode().name()), -3424778);
      this.drawChip(guiGraphics, layout.headerX + 262, layout.headerY + 42, humanizeConflict(this.snapshot.conflictState().name()), -3607324);
      guiGraphics.m_280614_(
         this.f_96547_,
         AigfUi.text(
            t("Mood", "Настроение")
               + " "
               + this.snapshot.mood()
               + "   "
               + t("Trust", "Доверие")
               + " "
               + this.snapshot.trust()
               + "   "
               + t("Energy", "Энергия")
               + " "
               + this.snapshot.energy()
         ),
         layout.headerX + 18,
         layout.headerY + 72,
         -2505259,
         false
      );
   }

   private void renderContent(GuiGraphics guiGraphics, CompanionAdaptiveScreen.Layout layout) {
      AigfUi.drawPanel(
         guiGraphics, layout.contentX, layout.contentY, layout.contentWidth, layout.contentHeight, -770240488, -284161519, AigfUi.colorAlpha(-1596986, 0.14F)
      );
      switch (this.activeTab) {
         case CHAT:
            this.renderChatTab(guiGraphics, layout);
            break;
         case CARE:
            this.renderCareTab(guiGraphics, layout);
            break;
         case INVENTORY:
            this.renderInventoryTab(guiGraphics, layout);
            break;
         case APPEARANCE:
            this.renderAppearanceTab(guiGraphics, layout);
            break;
         case SETTINGS:
            this.renderSettingsTab(guiGraphics, layout);
      }
   }

   private void renderStatus(GuiGraphics guiGraphics, CompanionAdaptiveScreen.Layout layout) {
      if (this.statusLine != null && !this.statusLine.isBlank()) {
         int width = Math.min(420, layout.contentWidth - 36);
         AigfUi.drawPanel(guiGraphics, layout.contentX + 18, layout.contentBottom() + 8, width, 24, -1088023002, -703328233, AigfUi.colorAlpha(-1596986, 0.16F));
         guiGraphics.m_280614_(
            this.f_96547_, AigfUi.text(this.f_96547_.m_92834_(this.statusLine, width - 12)), layout.contentX + 24, layout.contentBottom() + 16, -133381, false
         );
      }
   }

   private void renderChatTab(GuiGraphics guiGraphics, CompanionAdaptiveScreen.Layout layout) {
      guiGraphics.m_280614_(this.f_96547_, AigfUi.text(t("Chat", "Чат")), layout.contentX + 18, layout.contentY + 14, -133381, false);
      int boxX = layout.contentX + 18;
      int boxY = layout.contentY + 38;
      int boxWidth = layout.contentWidth - 36;
      int boxHeight = layout.contentHeight - 84;
      AigfUi.drawCard(guiGraphics, boxX, boxY, boxWidth, boxHeight, true);
      List<FormattedCharSequence> wrappedLines = new ArrayList<>();

      for (String line : this.chatLines) {
         wrappedLines.addAll(this.f_96547_.m_92923_(AigfUi.text(line), boxWidth - 16));
      }

      int maxLines = Math.max(4, (boxHeight - 12) / 10);
      int start = Math.max(0, wrappedLines.size() - maxLines);
      int y = boxY + 8;

      for (int i = start; i < wrappedLines.size(); i++) {
         guiGraphics.m_280648_(this.f_96547_, wrappedLines.get(i), boxX + 8, y, -133381);
         y += 10;
      }
   }

   private void renderCareTab(GuiGraphics guiGraphics, CompanionAdaptiveScreen.Layout layout) {
      guiGraphics.m_280614_(this.f_96547_, AigfUi.text(t("State", "Состояние")), layout.contentX + 18, layout.contentY + 14, -133381, false);
      int column = (layout.contentWidth - 48) / 3;
      this.drawStatCard(guiGraphics, layout.contentX + 18, layout.contentY + 38, column, t("Mood", "Настроение"), this.snapshot.mood(), -2986325);
      this.drawStatCard(guiGraphics, layout.contentX + 24 + column, layout.contentY + 38, column, t("Trust", "Доверие"), this.snapshot.trust(), -8926977);
      this.drawStatCard(guiGraphics, layout.contentX + 30 + column * 2, layout.contentY + 38, column, t("Energy", "Энергия"), this.snapshot.energy(), -6360361);
      guiGraphics.m_280614_(
         this.f_96547_,
         AigfUi.text(t("Conflict", "Конфликт") + ": " + humanizeConflict(this.snapshot.conflictState().name())),
         layout.contentX + 18,
         layout.contentY + 160,
         -2505259,
         false
      );
      guiGraphics.m_280614_(
         this.f_96547_,
         AigfUi.text(t("Resentment", "Обида") + ": " + this.snapshot.resentment() + "/100"),
         layout.contentX + 18,
         layout.contentY + 176,
         -2505259,
         false
      );
      guiGraphics.m_280614_(
         this.f_96547_,
         AigfUi.text(t("Repair", "Примирение") + ": " + this.snapshot.reconciliationProgress() + "/100"),
         layout.contentX + 18,
         layout.contentY + 192,
         -2505259,
         false
      );
   }

   private void renderInventoryTab(GuiGraphics guiGraphics, CompanionAdaptiveScreen.Layout layout) {
      guiGraphics.m_280614_(this.f_96547_, AigfUi.text(t("Inventory", "Инвентарь")), layout.contentX + 18, layout.contentY + 14, -133381, false);

      for (int index = 0; index < INVENTORY_SLOTS.length; index++) {
         CompanionAdaptiveScreen.SlotRect rect = this.inventorySlotRect(layout, index);
         EquipmentSlot slot = INVENTORY_SLOTS[index];
         ItemStack stack = this.companion.m_6844_(slot);
         String itemName = stack.m_41619_() ? t("Empty", "Пусто") : stack.m_41786_().getString();
         AigfUi.drawCard(guiGraphics, rect.x, rect.y, rect.width, rect.height, false);
         guiGraphics.m_280614_(this.f_96547_, AigfUi.text(localizedSlotName(slot)), rect.x + 12, rect.y + 10, -133381, false);
         guiGraphics.m_280614_(this.f_96547_, AigfUi.text(this.f_96547_.m_92834_(itemName, rect.width - 24)), rect.x + 12, rect.y + 26, -2505259, false);
      }
   }

   private void renderAppearanceTab(GuiGraphics guiGraphics, CompanionAdaptiveScreen.Layout layout) {
      guiGraphics.m_280614_(this.f_96547_, AigfUi.text(t("Looks", "Внешность")), layout.contentX + 18, layout.contentY + 14, -133381, false);
      AigfUi.drawCard(guiGraphics, layout.contentX + 18, layout.contentY + 80, layout.contentWidth - 36, layout.contentHeight - 124, true);
      String selectedSkin = this.skinIds.isEmpty() ? "builtin:alex" : this.skinIds.get(this.selectedSkinIndex);
      guiGraphics.m_280614_(
         this.f_96547_, AigfUi.text(t("Active", "Текущий") + ": " + this.snapshot.activeSkinId()), layout.contentX + 34, layout.contentY + 104, -133381, false
      );
      guiGraphics.m_280614_(
         this.f_96547_, AigfUi.text(t("Selected", "Выбран") + ": " + selectedSkin), layout.contentX + 34, layout.contentY + 122, -2505259, false
      );
      guiGraphics.m_280614_(this.f_96547_, AigfUi.text("config/aigf/skins"), layout.contentX + 34, layout.contentY + 140, -2505259, false);
   }

   private void renderSettingsTab(GuiGraphics guiGraphics, CompanionAdaptiveScreen.Layout layout) {
      guiGraphics.m_280614_(this.f_96547_, AigfUi.text(t("Settings", "Настройки")), layout.contentX + 18, layout.contentY + 14, -133381, false);
      guiGraphics.m_280614_(this.f_96547_, AigfUi.text("API"), layout.contentX + 18, layout.contentY + 28, -2505259, false);
      guiGraphics.m_280614_(this.f_96547_, AigfUi.text(t("Model", "Модель")), layout.contentX + 18, layout.contentY + 70, -2505259, false);
      guiGraphics.m_280614_(
         this.f_96547_, AigfUi.text(t("Quick fields only", "Только нужные поля")), layout.contentX + 18, layout.contentY + 196, -2505259, false
      );
   }

   private void drawStatCard(GuiGraphics guiGraphics, int x, int y, int width, String label, int value, int accent) {
      AigfUi.drawCard(guiGraphics, x, y, width, 106, true);
      guiGraphics.m_280614_(this.f_96547_, AigfUi.text(label), x + 12, y + 10, -133381, false);
      guiGraphics.m_280614_(this.f_96547_, AigfUi.text(String.valueOf(value)), x + 12, y + 28, -133381, false);
      AigfUi.drawProgressBar(guiGraphics, this.f_96547_, x + 12, y + 48, width - 24, t("Level", "Уровень"), value, accent);
   }

   private void drawChip(GuiGraphics guiGraphics, int x, int y, String text, int accent) {
      int chipWidth = Math.min(110, this.f_96547_.m_92852_(AigfUi.text(text)) + 18);
      AigfUi.drawRoundedPanel(
         guiGraphics,
         x,
         y,
         chipWidth,
         22,
         AigfUi.colorAlpha(AigfUi.lighten(accent, 0.18F), 0.32F),
         AigfUi.colorAlpha(-14477276, 0.86F),
         AigfUi.colorAlpha(accent, 0.24F)
      );
      guiGraphics.m_280614_(this.f_96547_, AigfUi.text(text), x + 9, y + 7, -133381, false);
   }

   private CompanionAdaptiveScreen.SlotRect inventorySlotRect(CompanionAdaptiveScreen.Layout layout, int index) {
      int width = (layout.contentWidth - 48) / 2;
      int x = layout.contentX + 18 + index % 2 * (width + 12);
      int y = layout.contentY + 110 + index / 2 * 84;
      return new CompanionAdaptiveScreen.SlotRect(x, y, width, 74);
   }

   private CompanionAdaptiveScreen.Layout layout() {
      int outer = 16;
      int rootWidth = Math.min(this.f_96543_ - outer * 2, 980);
      int rootHeight = Math.min(this.f_96544_ - outer * 2, 620);
      int rootX = (this.f_96543_ - rootWidth) / 2;
      int rootY = (this.f_96544_ - rootHeight) / 2;
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
      return new CompanionAdaptiveScreen.Layout(
         sidebarX, sidebarY, sidebarWidth, sidebarHeight, headerX, headerY, headerWidth, headerHeight, contentX, contentY, contentWidth, contentHeight
      );
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
      if (hint == null || hint.isBlank()) {
         return "";
      }

      if (hint.startsWith("hint.rename:")) {
         return t("New name saved.", "Новое имя сохранено.");
      }

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
         default -> slot.m_20751_();
      };
   }

   private static String t(String english, String russian) {
      return ClientLocalization.text(english, russian);
   }

   private CompanionSnapshot withCompanionName(String companionName) {
      return new CompanionSnapshot(
         this.snapshot.ownerUuid(),
         companionName,
         this.snapshot.mood(),
         this.snapshot.energy(),
         this.snapshot.trust(),
         this.snapshot.resentment(),
         this.snapshot.reconciliationProgress(),
         this.snapshot.activeSkinId(),
         this.snapshot.lastAction(),
         this.snapshot.lastCareHint(),
         this.snapshot.commandMode(),
         this.snapshot.emotion(),
         this.snapshot.conflictState(),
         this.snapshot.relationshipStage(),
         this.snapshot.recentTurns(),
         this.snapshot.importantFacts(),
         this.snapshot.promises(),
         this.snapshot.lastSeenWorldTime(),
         this.snapshot.homeDimension(),
         this.snapshot.homeX(),
         this.snapshot.homeY(),
         this.snapshot.homeZ()
      );
   }

   public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
      if (this.activeTab == CompanionAdaptiveScreen.ScreenTab.CHAT && keyCode == 257) {
         this.sendChat();
         return true;
      } else {
         return super.m_7933_(keyCode, scanCode, modifiers);
      }
   }

   public boolean m_7043_() {
      return false;
   }

   private record Layout(
      int sidebarX,
      int sidebarY,
      int sidebarWidth,
      int sidebarHeight,
      int headerX,
      int headerY,
      int headerWidth,
      int headerHeight,
      int contentX,
      int contentY,
      int contentWidth,
      int contentHeight
   ) {
      int sidebarBottom() {
         return this.sidebarY + this.sidebarHeight;
      }

      int contentRight() {
         return this.contentX + this.contentWidth;
      }

      int contentBottom() {
         return this.contentY + this.contentHeight;
      }
   }

   private enum ScreenTab {
      CHAT("Chat", "Чат"),
      CARE("Care", "Уход"),
      INVENTORY("Inventory", "Инвентарь"),
      APPEARANCE("Looks", "Внешность"),
      SETTINGS("Settings", "Настройки");

      private final String english;
      private final String russian;

      ScreenTab(String english, String russian) {
         this.english = english;
         this.russian = russian;
      }

      String label() {
         return CompanionAdaptiveScreen.t(this.english, this.russian);
      }
   }

   private record SlotRect(int x, int y, int width, int height) {
   }
}
