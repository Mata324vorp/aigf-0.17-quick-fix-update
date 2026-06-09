package com.litewer.aigf.client;

import com.litewer.aigf.client.ai.CompanionAiResult;
import com.litewer.aigf.client.ai.OpenAiClient;
import com.litewer.aigf.conversation.ConversationMoodAnalyzer;
import com.litewer.aigf.data.CompanionSnapshot;
import com.litewer.aigf.entity.CompanionEntity;
import com.litewer.aigf.network.AigfNetwork;
import com.litewer.aigf.network.packet.ServerboundChatTurnPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

public final class CompanionChatBridge {
   private CompanionChatBridge() {
   }

   public static void handleChatMessage(String userMessage) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level != null && minecraft.player != null) {
         CompanionEntity companion = findNearbyOwnedCompanion();
         if (companion == null) {
            minecraft.gui.getChat().addMessage(Component.literal("AIGF: " + ClientLocalization.text("No companion was found nearby.", "Рядом не найдено спутницы.")));
         } else {
            CompanionSnapshot snapshot = CompanionClientState.getSnapshot(companion);
            String companionName = snapshot.companionName() != null && !snapshot.companionName().isBlank() ? snapshot.companionName() : "AIGF";
            minecraft.gui.getChat().addMessage(Component.literal(ClientLocalization.text("You -> ", "Ты -> ") + companionName + ": " + userMessage));
            OpenAiClient.chat(userMessage, snapshot)
                    .whenComplete((result, error) -> minecraft.execute(() -> {
                       CompanionAiResult safeResult = result;
                       if (error == null && safeResult != null) {
                          ConversationMoodAnalyzer.Analysis analysis = ConversationMoodAnalyzer.analyze(userMessage);
                          CompanionAiResult adjustedResult = OpenAiClient.enforceConversationTone(userMessage, snapshot, safeResult, analysis);
                          String finalEmotion = adjustedResult.emotion().name();
                          minecraft.gui.getChat().addMessage(Component.literal(companionName + ": " + adjustedResult.spokenText()));
                          AigfNetwork.CHANNEL.sendToServer(
                                  new ServerboundChatTurnPacket(
                                          companion.getId(),
                                          userMessage,
                                          adjustedResult.spokenText(),
                                          finalEmotion,
                                          adjustedResult.actionIntent().name(),
                                          adjustedResult.memoryFact(),
                                          adjustedResult.careHint(),
                                          analysis.moodDelta(),
                                          analysis.trustDelta()
                                  )
                          );
                       } else {
                          minecraft.gui.getChat().addMessage(Component.literal(companionName + ": " + ClientLocalization.text("I can't answer right now.", "Сейчас не могу ответить.")));
                       }
                    }));
         }
      }
   }

   private static CompanionEntity findNearbyOwnedCompanion() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level == null || minecraft.player == null) return null;
      AABB searchBox = minecraft.player.getBoundingBox().inflate(96.0);
      return minecraft.level.getEntitiesOfClass(CompanionEntity.class, searchBox,
                      entity -> minecraft.player.getUUID().equals(entity.getOwnerUuid()))
              .stream()
              .findFirst()
              .orElse(null);
   }
}