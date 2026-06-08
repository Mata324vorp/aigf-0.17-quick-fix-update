package com.litewer.aigf.client;

import com.litewer.aigf.client.ai.GiftAiResult;
import com.litewer.aigf.client.ai.OpenAiClient;
import com.litewer.aigf.client.screen.CompanionScreen;
import com.litewer.aigf.client.screen.CompanionWelcomeScreen;
import com.litewer.aigf.entity.CompanionEntity;
import com.litewer.aigf.network.AigfNetwork;
import com.litewer.aigf.network.packet.ClientboundGiftPromptPacket;
import com.litewer.aigf.network.packet.ClientboundOpenCompanionScreenPacket;
import com.litewer.aigf.network.packet.ClientboundOpenWelcomePacket;
import com.litewer.aigf.network.packet.ClientboundSyncCompanionPacket;
import com.litewer.aigf.network.packet.ServerboundGiftReactionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ClientPacketHandlers {
   private ClientPacketHandlers() {
   }

   public static void openWelcomeScreen(ClientboundOpenWelcomePacket packet) {
      Minecraft minecraft = Minecraft.getInstance();
      minecraft.setScreen(new CompanionWelcomeScreen(packet.companionName(), packet.hasCompanion()));
   }

   public static void openCompanionScreen(ClientboundOpenCompanionScreenPacket packet) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level != null) {
         CompanionClientState.updateSnapshot(packet.entityId(), packet.snapshot());
         if (minecraft.level.getEntity(packet.entityId()) instanceof CompanionEntity companion) {
            minecraft.setScreen(new CompanionScreen(companion, packet.snapshot()));
         }
      }
   }

   public static void syncCompanionScreen(ClientboundSyncCompanionPacket packet) {
      Minecraft minecraft = Minecraft.getInstance();
      CompanionClientState.updateSnapshot(packet.entityId(), packet.snapshot());
      if (minecraft.screen instanceof CompanionScreen screen && screen.getEntityId() == packet.entityId()) {
         screen.applySnapshot(packet.snapshot());
      }
   }

   public static void handleGiftPrompt(ClientboundGiftPromptPacket packet) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level != null) {
         CompanionClientState.updateSnapshot(packet.entityId(), packet.snapshot());
         if (minecraft.level.getEntity(packet.entityId()) instanceof CompanionEntity companion) {
            String companionName = packet.snapshot().companionName() != null && !packet.snapshot().companionName().isBlank()
                    ? packet.snapshot().companionName()
                    : "AIGF";
            String itemName = packet.giftedStack().getHoverName().getString();
            minecraft.gui.getChat().addMessage(Component.literal(ClientLocalization.text("You gave ", "Ты подарил(а) ") + companionName + ": " + itemName));
            OpenAiClient.reactToGift(packet.giftedStack(), packet.snapshot())
                    .whenComplete((result, error) -> minecraft.execute(() -> {
                       GiftAiResult safeResult = result;
                       if (error == null && safeResult != null) {
                          minecraft.gui.getChat().addMessage(Component.literal(companionName + ": " + safeResult.spokenText()));
                          AigfNetwork.CHANNEL.sendToServer(
                                  new ServerboundGiftReactionPacket(
                                          companion.getId(),
                                          safeResult.spokenText(),
                                          safeResult.emotion().name(),
                                          safeResult.moodDelta(),
                                          safeResult.trustDelta(),
                                          safeResult.memoryFact(),
                                          safeResult.careHint()
                                  )
                          );
                       } else {
                          minecraft.gui.getChat().addMessage(
                                  Component.literal(
                                          companionName + ": " + ClientLocalization.text(
                                                  "I'm not sure what to say about that gift right now.",
                                                  "Я сейчас даже не знаю, что сказать про такой подарок."
                                          )
                                  )
                          );
                       }
                    }));
         }
      }
   }
}