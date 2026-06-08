package com.litewer.aigf.client.screen;

import com.litewer.aigf.client.ClientLocalization;
import com.litewer.aigf.client.CompanionClientState;
import com.litewer.aigf.client.ai.CompanionAiResult;
import com.litewer.aigf.client.ai.OpenAiClient;
import com.litewer.aigf.client.settings.AiProvider;
import com.litewer.aigf.client.settings.ClientSettingsManager;
import com.litewer.aigf.client.skin.CompanionSkinManager;
import com.litewer.aigf.conversation.ConversationMoodAnalyzer;
import com.litewer.aigf.data.CompanionPromise;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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

public final class CompanionScreen extends Screen {
   private static final int PANEL_WIDTH = 320;
   private static final int PANEL_HEIGHT = 266;
   private static final InventorySlotView[] INVENTORY_SLOTS = new InventorySlotView[]{
           new InventorySlotView("weapon", EquipmentSlot.MAINHAND),
           new InventorySlotView("gift", EquipmentSlot.OFFHAND),
           new InventorySlotView("head", EquipmentSlot.HEAD),
           new InventorySlotView("chest", EquipmentSlot.CHEST),
           new InventorySlotView("legs", EquipmentSlot.LEGS),
           new InventorySlotView("feet", EquipmentSlot.FEET)
   };
   private final CompanionEntity companion;
   private CompanionSnapshot snapshot;
   private final List<String> chatLines = new ArrayList<>();
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
   private Button previousProviderButton;
   private Button nextProviderButton;
   private EditBox modelSearchBox;
   private Button loadModelsButton;
   private EditBox modelIdBox;
   private Button previousModelButton;
   private Button nextModelButton;
   private EditBox timeoutBox;
   private EditBox contextTurnsBox;
   private Button saveSettingsButton;
   private Button testConnectionButton;
   private List<String> skinIds = new ArrayList<>();
   private List<String> availableModels = new ArrayList<>();
   private int selectedSkinIndex;
   private String statusLine = "";
   private boolean requestInFlight;
   private boolean modelCatalogLoading;

   public CompanionScreen(CompanionEntity companion, CompanionSnapshot snapshot) {
      super(Component.translatable("screen.aigf.title"));
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
      int left = this.leftPos();
      int top = this.topPos();

      this.addRenderableWidget(Button.builder(ScreenTab.CHAT.title(), button -> this.switchTab(ScreenTab.CHAT))
              .bounds(left + 8, top + 8, 56, 20).build());

      this.addRenderableWidget(Button.builder(ScreenTab.CARE.title(), button -> this.switchTab(ScreenTab.CARE))
              .bounds(left + 68, top + 8, 56, 20).build());

      this.addRenderableWidget(Button.builder(ScreenTab.INVENTORY.title(), button -> this.switchTab(ScreenTab.INVENTORY))
              .bounds(left + 128, top + 8, 64, 20).build());

      this.addRenderableWidget(Button.builder(ScreenTab.APPEARANCE.title(), button -> this.switchTab(ScreenTab.APPEARANCE))
              .bounds(left + 196, top + 8, 56, 20).build());

      this.addRenderableWidget(Button.builder(ScreenTab.SETTINGS.title(), button -> this.switchTab(ScreenTab.SETTINGS))
              .bounds(left + 256, top + 8, 56, 20).build());

      this.buildChatWidgets(left, top);
      this.buildInventoryWidgets(left, top);
      this.buildAppearanceWidgets(left, top);
      this.buildSettingsWidgets(left, top);
      this.reloadSkinList();
      this.loadSettings();
      this.updateTabVisibility();
   }

   private void buildChatWidgets(int left, int top) {
      this.followButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.aigf.follow"),
                      button -> this.sendDirectAction(CompanionActionIntent.FOLLOW))
              .bounds(left + 12, top + 58, 92, 20).build());
      this.stayButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.aigf.stay"),
                      button -> this.sendDirectAction(CompanionActionIntent.STAY))
              .bounds(left + 110, top + 58, 92, 20).build());
      this.sitButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.aigf.sit"),
                      button -> this.sendDirectAction(CompanionActionIntent.SIT))
              .bounds(left + 208, top + 58, 100, 20).build());
      this.setHomeButton = this.addRenderableWidget(Button.builder(Component.literal(t("Set home", "Точка дома")),
                      button -> this.sendDirectAction(CompanionActionIntent.SET_HOME))
              .bounds(left + 12, top + 82, 144, 20).build());
      this.goHomeButton = this.addRenderableWidget(Button.builder(Component.literal(t("Go home", "Иди домой")),
                      button -> this.sendDirectAction(CompanionActionIntent.GO_HOME))
              .bounds(left + 164, top + 82, 144, 20).build());
      this.chatInput = this.addRenderableWidget(new EditBox(this.font, left + 12, top + 226, 220, 20,
              Component.translatable("screen.aigf.chat_input")));
      this.chatInput.setMaxLength(256);
      this.sendButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.aigf.send"),
                      button -> this.sendChat())
              .bounds(left + 238, top + 226, 70, 20).build());

      this.chatWidgets.add(this.followButton);
      this.chatWidgets.add(this.stayButton);
      this.chatWidgets.add(this.sitButton);
      this.chatWidgets.add(this.setHomeButton);
      this.chatWidgets.add(this.goHomeButton);
      this.chatWidgets.add(this.chatInput);
      this.chatWidgets.add(this.sendButton);
   }

   private void buildInventoryWidgets(int left, int top) {
      this.nameBox = this.addRenderableWidget(new EditBox(this.font, left + 12, top + 84, 198, 20,
              Component.literal(t("Companion name", "Имя спутницы"))));
      this.nameBox.setMaxLength(24);
      this.nameBox.setValue(this.snapshot.companionName());
      this.saveNameButton = this.addRenderableWidget(Button.builder(Component.literal(t("Rename", "Переименовать")),
                      button -> this.renameCompanion())
              .bounds(left + 216, top + 84, 92, 20).build());

      this.inventoryWidgets.add(this.nameBox);
      this.inventoryWidgets.add(this.saveNameButton);

      this.giftFromHandButton = this.addRenderableWidget(Button.builder(Component.literal(t("Gift from hand", "Подарить из руки")),
                      button -> this.sendGiftFromHand())
              .bounds(left + 12, top + 108, 296, 20).build());
      this.inventoryWidgets.add(this.giftFromHandButton);

      int y = top + 136;
      for (InventorySlotView slotView : INVENTORY_SLOTS) {
         Button fromHandButton = this.addRenderableWidget(Button.builder(Component.literal(t("From hand", "Из руки")),
                         button -> this.sendInventoryAction(slotView.slot(), "PUT_FROM_HAND"))
                 .bounds(left + 192, y, 62, 20).build());
         Button takeButton = this.addRenderableWidget(Button.builder(Component.literal(t("Take", "Забрать")),
                         button -> this.sendInventoryAction(slotView.slot(), "TAKE_TO_PLAYER"))
                 .bounds(left + 258, y, 50, 20).build());
         this.inventoryWidgets.add(fromHandButton);
         this.inventoryWidgets.add(takeButton);
         y += 22;
      }
   }

   private void buildAppearanceWidgets(int left, int top) {
      this.previousSkinButton = this.addRenderableWidget(Button.builder(Component.literal("<"),
                      button -> this.shiftSkinSelection(-1))
              .bounds(left + 12, top + 198, 24, 20).build());
      this.nextSkinButton = this.addRenderableWidget(Button.builder(Component.literal(">"),
                      button -> this.shiftSkinSelection(1))
              .bounds(left + 284, top + 198, 24, 20).build());
      this.applySkinButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.aigf.apply_skin"),
                      button -> this.applySelectedSkin())
              .bounds(left + 12, top + 226, 92, 20).build());
      this.reloadSkinsButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.aigf.reload_skins"),
                      button -> this.reloadSkinList())
              .bounds(left + 110, top + 226, 92, 20).build());
      this.importNameBox = this.addRenderableWidget(new EditBox(this.font, left + 12, top + 166, 146, 20,
              Component.translatable("screen.aigf.import_nick")));
      this.importNameBox.setMaxLength(16);
      this.importSkinButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.aigf.import_skin"),
                      button -> this.importSkin())
              .bounds(left + 164, top + 166, 96, 20).build());

      this.appearanceWidgets.add(this.previousSkinButton);
      this.appearanceWidgets.add(this.nextSkinButton);
      this.appearanceWidgets.add(this.applySkinButton);
      this.appearanceWidgets.add(this.reloadSkinsButton);
      this.appearanceWidgets.add(this.importNameBox);
      this.appearanceWidgets.add(this.importSkinButton);
   }

   private void buildSettingsWidgets(int left, int top) {
      this.previousProviderButton = this.addRenderableWidget(Button.builder(Component.literal("<"),
                      button -> this.cycleProvider(-1))
              .bounds(left + 12, top + 68, 24, 20).build());
      this.nextProviderButton = this.addRenderableWidget(Button.builder(Component.literal(">"),
                      button -> this.cycleProvider(1))
              .bounds(left + 284, top + 68, 24, 20).build());

      this.apiKeyBox = this.addRenderableWidget(new EditBox(this.font, left + 12, top + 108, 296, 20,
              Component.translatable("screen.aigf.api_key")));
      this.apiKeyBox.setMaxLength(256);

      this.modelSearchBox = this.addRenderableWidget(new EditBox(this.font, left + 12, top + 148, 176, 20,
              Component.literal(t("Search models", "Поиск моделей"))));
      this.modelSearchBox.setMaxLength(96);

      this.loadModelsButton = this.addRenderableWidget(Button.builder(Component.literal(t("Sync", "Обновить")),
                      button -> this.refreshModelCatalog(true))
              .bounds(left + 194, top + 148, 114, 20).build());

      this.previousModelButton = this.addRenderableWidget(Button.builder(Component.literal("<"),
                      button -> this.cycleModel(-1))
              .bounds(left + 12, top + 188, 24, 20).build());

      this.modelIdBox = this.addRenderableWidget(new EditBox(this.font, left + 42, top + 188, 176, 20,
              Component.translatable("screen.aigf.model_id")));
      this.modelIdBox.setMaxLength(128);

      this.nextModelButton = this.addRenderableWidget(Button.builder(Component.literal(">"),
                      button -> this.cycleModel(1))
              .bounds(left + 224, top + 188, 24, 20).build());

      this.timeoutBox = this.addRenderableWidget(new EditBox(this.font, left + 256, top + 188, 52, 20,
              Component.translatable("screen.aigf.timeout")));
      this.contextTurnsBox = this.addRenderableWidget(new EditBox(this.font, left + 256, top + 226, 52, 20,
              Component.translatable("screen.aigf.context_turns")));

      this.saveSettingsButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.aigf.save_settings"),
                      button -> this.saveSettings())
              .bounds(left + 12, top + 226, 130, 20).build());

      this.testConnectionButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.aigf.test_connection"),
                      button -> this.testConnection())
              .bounds(left + 148, top + 226, 100, 20).build());

      this.settingsWidgets.add(this.previousProviderButton);
      this.settingsWidgets.add(this.nextProviderButton);
      this.settingsWidgets.add(this.apiKeyBox);
      this.settingsWidgets.add(this.modelSearchBox);
      this.settingsWidgets.add(this.loadModelsButton);
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
         this.statusLine = Component.translatable("screen.aigf.waiting").getString();
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
         case FOLLOW -> t("Mode: follow", "Режим: идти за тобой");
         case STAY -> t("Mode: stay", "Режим: стоять");
         case SIT -> t("Mode: sit", "Режим: сидеть");
         case SET_HOME -> t("Home point saved", "Точка дома сохранена");
         case GO_HOME -> this.snapshot.hasHome() ? t("Going home", "Идёт домой") : t("Home point is not set", "Точка дома не задана");
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
         case "PUT_FROM_HAND" -> t("Item moved to slot ", "Предмет отправлен в слот ") + localizedSlotName(slot);
         case "TAKE_TO_PLAYER" -> t("Item taken from slot ", "Предмет забран из слота ") + localizedSlotName(slot);
         default -> "";
      };
   }

   private void sendGiftFromHand() {
      ItemStack handStack = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
      if (handStack.isEmpty()) {
         this.statusLine = t("Your main hand is empty", "Главная рука пуста");
      } else {
         AigfNetwork.CHANNEL.sendToServer(new ServerboundGiftFromHandPacket(this.companion.getId()));
         this.statusLine = t("Gift sent from main hand", "Подарок отправлен из главной руки");
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
         this.statusLine = Component.translatable("screen.aigf.skin_applied").getString();
         AigfNetwork.CHANNEL.sendToServer(new ServerboundApplySkinPacket(this.companion.getId(), selectedSkin));
      }
   }

   private void importSkin() {
      String nickname = this.importNameBox.getValue().trim();
      if (nickname.isBlank()) {
         this.statusLine = t("Nickname is empty", "Ник пустой");
      } else {
         this.statusLine = Component.translatable("screen.aigf.importing").getString();
         CompanionSkinManager.importSkinByUsername(nickname).whenComplete((skinId, error) -> Minecraft.getInstance().execute(() -> {
            if (error == null && skinId != null) {
               this.reloadSkinList();
               int foundIndex = this.skinIds.indexOf(skinId);
               if (foundIndex >= 0) {
                  this.selectedSkinIndex = foundIndex;
               }
               this.statusLine = Component.translatable("screen.aigf.import_success").getString();
            } else {
               this.statusLine = t("Import failed", "Импорт не удался");
            }
         }));
      }
   }

   private void loadSettings() {
      ClientSettingsManager settings = ClientSettingsManager.get();
      this.apiKeyBox.setValue(settings.getActiveApiKey());
      this.modelIdBox.setValue(settings.getModelId());
      this.modelSearchBox.setValue("");
      this.timeoutBox.setValue(String.valueOf(settings.getTimeoutSeconds()));
      this.contextTurnsBox.setValue(String.valueOf(settings.getMaxContextTurns()));
      this.availableModels = new ArrayList<>(ClientSettingsManager.getModelPresets(settings.getProvider()));
   }

   private void saveSettings() {
      ClientSettingsManager settings = ClientSettingsManager.get();
      settings.setApiKey(settings.getProvider(), this.apiKeyBox.getValue());
      settings.setModelId(this.modelIdBox.getValue());
      settings.setTimeoutSeconds(parseInteger(this.timeoutBox.getValue(), 45, 5, 120));
      settings.setMaxContextTurns(parseInteger(this.contextTurnsBox.getValue(), 10, 2, 20));
      settings.save();
      this.statusLine = Component.translatable("screen.aigf.settings_saved").getString();
   }

   private void cycleModel(int direction) {
      List<String> matches = this.filteredModelChoices();
      if (matches.isEmpty()) {
         ClientSettingsManager settings = ClientSettingsManager.get();
         this.modelIdBox.setValue(settings.cycleModel(direction));
      } else {
         String current = this.modelIdBox.getValue().trim();
         int index = matches.indexOf(current);
         if (index < 0) {
            index = direction > 0 ? -1 : 0;
         }
         index = Math.floorMod(index + direction, matches.size());
         this.modelIdBox.setValue(matches.get(index));
      }
   }

   private void testConnection() {
      this.saveSettings();
      this.statusLine = Component.translatable("screen.aigf.testing").getString();
      OpenAiClient.testConnection().thenAccept(result -> Minecraft.getInstance().execute(() -> this.statusLine = result));
   }

   private void cycleProvider(int direction) {
      ClientSettingsManager settings = ClientSettingsManager.get();
      settings.setApiKey(settings.getProvider(), this.apiKeyBox.getValue());
      AiProvider provider = settings.cycleProvider(direction);
      this.modelIdBox.setValue(settings.getModelId());
      this.apiKeyBox.setValue(settings.getActiveApiKey());
      this.availableModels = new ArrayList<>(ClientSettingsManager.getModelPresets(provider));
      this.modelSearchBox.setValue("");
      this.statusLine = t("Provider: ", "Провайдер: ") + provider.displayName();
   }

   private void refreshModelCatalog(boolean forceRefresh) {
      ClientSettingsManager settings = ClientSettingsManager.get();
      settings.setApiKey(settings.getProvider(), this.apiKeyBox.getValue());
      settings.setModelId(this.modelIdBox.getValue());
      settings.setTimeoutSeconds(parseInteger(this.timeoutBox.getValue(), 45, 5, 120));
      settings.setMaxContextTurns(parseInteger(this.contextTurnsBox.getValue(), 10, 2, 20));
      this.modelCatalogLoading = true;
      this.statusLine = t("Loading models...", "Загружаю модели...");
      OpenAiClient.loadModelCatalog(forceRefresh).thenAccept(result -> Minecraft.getInstance().execute(() -> {
         this.modelCatalogLoading = false;
         this.availableModels = new ArrayList<>(result.models());
         this.statusLine = result.statusMessage();
      }));
   }

   private List<String> filteredModelChoices() {
      List<String> source = this.availableModels.isEmpty()
              ? ClientSettingsManager.getModelPresets(ClientSettingsManager.get().getProvider())
              : this.availableModels;
      return OpenAiClient.filterModels(source, this.modelSearchBox == null ? "" : this.modelSearchBox.getValue());
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
      this.renderBackground(guiGraphics);
      int left = this.leftPos();
      int top = this.topPos();
      guiGraphics.fill(left, top, left + 320, top + 266, -871295717);
      guiGraphics.fill(left + 1, top + 1, left + 320 - 1, top + 36, -14208966);
      String header = this.snapshot.companionName() != null && !this.snapshot.companionName().isBlank()
              ? this.snapshot.companionName()
              : this.title.getString();
      guiGraphics.drawString(this.font, header, left + 12, top + 42, 0xFFFFFF, false);
      switch (this.activeTab) {
         case CHAT -> this.renderChatTab(guiGraphics, left, top);
         case CARE -> this.renderCareTab(guiGraphics, left, top);
         case INVENTORY -> this.renderInventoryTab(guiGraphics, left, top);
         case APPEARANCE -> this.renderAppearanceTab(guiGraphics, left, top);
         case SETTINGS -> this.renderSettingsTab(guiGraphics, left, top);
      }
      String truncatedStatus = this.font.plainSubstrByWidth(this.statusLine, 296);
      guiGraphics.drawString(this.font, truncatedStatus, left + 12, top + 250, 0x9FBF9F, false);
      super.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   private void renderChatTab(GuiGraphics guiGraphics, int left, int top) {
      guiGraphics.drawString(this.font, Component.translatable("screen.aigf.chat_hint"), left + 12, top + 108, 0xC0C0C9, false);
      guiGraphics.drawString(this.font, Component.translatable("screen.aigf.chat_prefix_hint"), left + 12, top + 120, 0x9FBF9F, false);
      String homeInfo = this.snapshot.hasHome()
              ? t("Home: ", "Дом: ") + this.snapshot.homeX() + ", " + this.snapshot.homeY() + ", " + this.snapshot.homeZ()
              : t("Home: not set", "Дом: не задан");
      guiGraphics.drawString(this.font, homeInfo, left + 12, top + 132, 0xFFCF87, false);
      guiGraphics.fill(left + 12, top + 146, left + 308, top + 216, -1441852647);
      List<FormattedCharSequence> wrappedLines = new ArrayList<>();
      for (String line : this.chatLines) {
         wrappedLines.addAll(this.font.split(Component.literal(line), 284));
      }
      int start = Math.max(0, wrappedLines.size() - 6);
      int y = top + 152;
      for (int i = start; i < wrappedLines.size(); i++) {
         guiGraphics.drawString(this.font, wrappedLines.get(i), left + 16, y, 0xFFFFFF, false);
         y += 12;
      }
   }

   private void renderCareTab(GuiGraphics guiGraphics, int left, int top) {
      guiGraphics.drawString(this.font, Component.translatable("screen.aigf.care_hint"), left + 12, top + 54, 0xC0C0C9, false);
      guiGraphics.drawString(this.font, Component.translatable("screen.aigf.words_affect_mood"), left + 12, top + 68, 0xFFCF87, false);
      guiGraphics.drawString(this.font, t("Name: ", "Имя: ") + this.snapshot.companionName(), left + 12, top + 84, 0xFFFFFF, false);
      drawBar(guiGraphics, this.font, left + 12, top + 100, t("Mood", "Настроение"), this.snapshot.mood(), 0xFF71C5);
      drawBar(guiGraphics, this.font, left + 12, top + 128, t("Energy", "Энергия"), this.snapshot.energy(), 0xFF71C5);
      drawBar(guiGraphics, this.font, left + 12, top + 156, t("Trust", "Доверие"), this.snapshot.trust(), 0xFF71C5);
      guiGraphics.drawString(this.font,
              t("Emotion: ", "Эмоция: ") + humanizeEmotion(this.snapshot.emotion().name()) + "  " +
                      t("Mode: ", "Режим: ") + humanizeCommandMode(this.snapshot.commandMode().name()),
              left + 12, top + 186, 0xFFFFFF, false);
      guiGraphics.drawString(this.font, t("Bond: ", "Связь: ") + humanizeRelationship(this.snapshot.relationshipStage().name()),
              left + 12, top + 198, 0xFFFFFF, false);
      String conflictLine = t("Conflict: ", "Конфликт: ") + humanizeConflict(this.snapshot.conflictState().name()) + "  " +
              t("Resentment: ", "Обида: ") + this.snapshot.resentment() + "/100";
      guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(conflictLine, 296), left + 12, top + 210, 0xFFFFFF, false);
      guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(this.promiseLine(), 296), left + 12, top + 222, 0xFFFFFF, false);
      String homeLine = this.snapshot.hasHome()
              ? t("Home point: ", "Точка дома: ") + this.snapshot.homeX() + ", " + this.snapshot.homeY() + ", " + this.snapshot.homeZ()
              : t("Home point: not set", "Точка дома: не задана");
      guiGraphics.drawString(this.font, homeLine, left + 12, top + 234, 0xBFBFB4, false);
   }

   private void renderInventoryTab(GuiGraphics guiGraphics, int left, int top) {
      guiGraphics.drawString(this.font,
              Component.literal(t("Give her a real name and manage what she carries.", "Дай ей настоящее имя и управляй тем, что она носит.")),
              left + 12, top + 58, 0xC0C0C9, false);
      guiGraphics.drawString(this.font, Component.literal(t("Gift an item from your hand.", "Подарить предмет из руки.")),
              left + 12, top + 72, 0x9FBF9F, false);
      int y = top + 124;
      for (InventorySlotView slotView : INVENTORY_SLOTS) {
         ItemStack stack = this.companion.getItemBySlot(slotView.slot());
         String itemName = stack.isEmpty() ? t("Empty", "Пусто") : stack.getHoverName().getString();
         guiGraphics.drawString(this.font, localizedSlotName(slotView.slot()), left + 12, y + 6, 0xFFFFFF, false);
         guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(itemName, 84), left + 96, y + 6, 0xD0D0D2, false);
         y += 22;
      }
   }

   private void renderAppearanceTab(GuiGraphics guiGraphics, int left, int top) {
      guiGraphics.drawString(this.font, Component.translatable("screen.aigf.appearance_hint"), left + 12, top + 58, 0xC0C0C9, false);
      guiGraphics.fill(left + 12, top + 80, left + 308, top + 154, -1441852647);
      String selectedSkin = this.skinIds.isEmpty() ? "builtin:alex" : this.skinIds.get(this.selectedSkinIndex);
      guiGraphics.drawString(this.font, t("Active: ", "Текущий: ") + this.snapshot.activeSkinId(), left + 18, top + 88, 0xFFFFFF, false);
      guiGraphics.drawString(this.font, t("Selected: ", "Выбран: ") + selectedSkin, left + 18, top + 102, 0xFFFFFF, false);
      guiGraphics.drawString(this.font, t("Companion: ", "Спутница: ") + this.snapshot.companionName(), left + 18, top + 116, 0xFFFFFF, false);
      guiGraphics.drawString(this.font, t("Folder: ", "Папка: ") + "config/aigf/skins", left + 18, top + 130, 0xBFBFB4, false);
   }

   private void renderSettingsTab(GuiGraphics guiGraphics, int left, int top) {
      ClientSettingsManager settings = ClientSettingsManager.get();
      List<String> matches = this.filteredModelChoices();
      guiGraphics.drawString(this.font, t("Provider", "Провайдер") + ": " + settings.getProvider().displayName(),
              left + 42, top + 74, 0xFFFFFF, false);
      guiGraphics.drawString(this.font, t("API key", "API ключ"), left + 12, top + 94, 0xFFFFFF, false);
      guiGraphics.drawString(this.font, t("Search", "Поиск"), left + 12, top + 134, 0xFFFFFF, false);
      guiGraphics.drawString(this.font, t("Model", "Модель"), left + 12, top + 174, 0xFFFFFF, false);
      guiGraphics.drawString(this.font, t("Timeout", "Тайм-аут"), left + 256, top + 174, 0xFFFFFF, false);
      guiGraphics.drawString(this.font, t("Context", "Контекст"), left + 256, top + 212, 0xFFFFFF, false);
      guiGraphics.drawString(this.font, this.modelMatchLine(matches), left + 12, top + 162, 0xC0C0C9, false);
      if (this.modelCatalogLoading) {
         guiGraphics.drawString(this.font, t("Syncing models...", "Синхронизация моделей..."), left + 12, top + 206, 0x9FBF9F, false);
      }
   }

   private String modelMatchLine(List<String> matches) {
      return matches.isEmpty() ? t("Matches: 0", "Совпадения: 0") : t("Matches", "Совпадения") + ": " + matches.size();
   }

   private static void drawBar(GuiGraphics guiGraphics, Font font, int x, int y, String label, int value, int color) {
      guiGraphics.drawString(font, label + ": " + value + "/100", x, y, 0xFFFFFF, false);
      guiGraphics.fill(x, y + 12, x + 230, y + 20, 0x55222222);
      guiGraphics.fill(x, y + 12, x + (int) (230.0F * (value / 100.0F)), y + 20, color);
   }

   private static String humanizeAnalysis(String summary) {
      return switch (summary) {
         case "hurt_by_words" -> t("Your words hurt her.", "Её задели твои слова.");
         case "praised" -> t("She liked your kindness.", "Ей приятно твоё отношение.");
         case "shared_discussion" -> t("She enjoys discussing things together.", "Ей нравится обсуждать всё вместе.");
         case "apology" -> t("The apology helped calm the conversation.", "Извинение помогло смягчить разговор.");
         case "hurt_by_words,apology" -> t("She heard the apology, but the hurt is still there.", "Извинение услышано, но осадок ещё остался.");
         case "hurt_by_words,praised", "praised,hurt_by_words" -> t("The conversation is mixed: warm and hurt at the same time.", "Разговор смешанный: ей и приятно, и больно.");
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

   private String promiseLine() {
      if (!this.snapshot.pendingPromises().isEmpty()) {
         CompanionPromise promise = this.snapshot.pendingPromises().get(0);
         return t("Promise: waiting for ", "Обещание: жду ") + promise.shortSummary(42);
      }
      for (int i = this.snapshot.promises().size() - 1; i >= 0; i--) {
         CompanionPromise promise = this.snapshot.promises().get(i);
         if ("BROKEN".equals(promise.status().name())) {
            return t("Broken promise: ", "Сорванное обещание: ") + promise.shortSummary(34);
         }
      }
      return t("Promises: none active", "Обещаний сейчас нет");
   }

   private String localizeCareHint(String hint) {
      if (hint == null || hint.isBlank()) return "";
      if (hint.startsWith("hint.rename:")) {
         String name = hint.substring("hint.rename:".length()).trim();
         return t("My name is now ", "Теперь меня зовут ") + name + ".";
      }
      if (hint.startsWith("hint.promise.new:")) {
         String p = hint.substring("hint.promise.new:".length()).trim();
         return t("I heard your promise: ", "Я запомнила твоё обещание: ") + p + ".";
      }
      if (hint.startsWith("hint.promise.kept:")) {
         String p = hint.substring("hint.promise.kept:".length()).trim();
         return t("You kept your promise: ", "Ты выполнил(а) обещание: ") + p + ".";
      }
      if (hint.startsWith("hint.promise.broken:")) {
         String p = hint.substring("hint.promise.broken:".length()).trim();
         return t("That promise was left hanging: ", "Это обещание так и повисло в воздухе: ") + p + ".";
      }
      if (hint.startsWith("hint.promise.reminder:")) {
         String p = hint.substring("hint.promise.reminder:".length()).trim();
         return t("I'm still waiting for this: ", "Я всё ещё жду вот это: ") + p + ".";
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
         case "Подарок помог немного смягчить обиду, но мне ещё нужно время." -> t("The gift softened the hurt a little, but she still needs time.", "Подарок помог немного смягчить обиду, но мне ещё нужно время.");
         case "Подарок помог мне окончательно оттаять." -> t("The gift helped her finally thaw out.", "Подарок помог мне окончательно оттаять.");
         default -> hint;
      };
   }

   private static String humanizeCommandMode(String mode) {
      return switch (mode) {
         case "FOLLOW" -> t("Follow", "За тобой");
         case "STAY" -> t("Stay", "Стоять");
         case "SIT" -> t("Sit", "Сидеть");
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

   private int leftPos() {
      return (this.width - 320) / 2;
   }

   private int topPos() {
      return (this.height - 266) / 2;
   }

   private record InventorySlotView(String label, EquipmentSlot slot) {}
   private enum ScreenTab {
      CHAT("Chat", "Чат"),
      CARE("Care", "Уход"),
      INVENTORY("Inventory", "Инвентарь"),
      APPEARANCE("Looks", "Внешность"),
      SETTINGS("Settings", "Настройки");
      private final String englishTitle, russianTitle;
      ScreenTab(String englishTitle, String russianTitle) {
         this.englishTitle = englishTitle;
         this.russianTitle = russianTitle;
      }
      private Component title() {
         return Component.literal(CompanionScreen.t(this.englishTitle, this.russianTitle));
      }
   }
}