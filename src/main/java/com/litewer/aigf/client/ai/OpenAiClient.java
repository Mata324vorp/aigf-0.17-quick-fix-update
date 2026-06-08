package com.litewer.aigf.client.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.litewer.aigf.client.ClientLocalization;
import com.litewer.aigf.client.settings.AiProvider;
import com.litewer.aigf.client.settings.ClientSettingsManager;
import com.litewer.aigf.conversation.ConversationMoodAnalyzer;
import com.litewer.aigf.data.CompanionPromise;
import com.litewer.aigf.data.CompanionPromiseStatus;
import com.litewer.aigf.data.CompanionSnapshot;
import com.litewer.aigf.data.ConversationTurn;
import com.litewer.aigf.entity.CompanionActionIntent;
import com.litewer.aigf.entity.CompanionCommandMode;
import com.litewer.aigf.entity.CompanionConflictState;
import com.litewer.aigf.entity.CompanionEmotion;
import com.litewer.aigf.entity.CompanionRelationshipStage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;

public final class OpenAiClient {
   private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
   private static final EnumMap<AiProvider, List<String>> MODEL_CACHE = new EnumMap<>(AiProvider.class);

   private OpenAiClient() {
   }

   public static CompletableFuture<CompanionAiResult> chat(String userMessage, CompanionSnapshot snapshot) {
      ClientSettingsManager settings = ClientSettingsManager.get();
      return settings.getActiveApiKey().isBlank()
              ? CompletableFuture.completedFuture(alignCompanionIdentity(buildFallback(userMessage, snapshot), snapshot))
              : CompletableFuture.supplyAsync(() -> {
         try {
            return alignCompanionIdentity(requestChat(userMessage, snapshot, settings), snapshot);
         } catch (Exception exception) {
            return alignCompanionIdentity(buildFallback(userMessage, snapshot), snapshot);
         }
      });
   }

   public static CompletableFuture<String> testConnection() {
      ClientSettingsManager settings = ClientSettingsManager.get();
      return settings.getActiveApiKey().isBlank()
              ? CompletableFuture.completedFuture(t("API key is empty", "API key пустой"))
              : CompletableFuture.supplyAsync(() -> {
         try {
            String probe = t("Say in one short sentence that the connection works.", "Скажи одной короткой фразой, что связь работает.");
            CompanionAiResult result = requestChat(probe, emptySnapshot(), settings);
            return t("OK: ", "ОК: ") + result.spokenText();
         } catch (Exception exception) {
            return t("Error: ", "Ошибка: ") + exception.getMessage();
         }
      });
   }

   public static CompletableFuture<GiftAiResult> reactToGift(ItemStack giftedStack, CompanionSnapshot snapshot) {
      ClientSettingsManager settings = ClientSettingsManager.get();
      return settings.getActiveApiKey().isBlank()
              ? CompletableFuture.completedFuture(buildGiftFallback(giftedStack, snapshot))
              : CompletableFuture.supplyAsync(() -> {
         try {
            return requestGiftReaction(giftedStack, snapshot, settings);
         } catch (Exception exception) {
            return buildGiftFallback(giftedStack, snapshot);
         }
      });
   }

   public static CompletableFuture<OpenAiClient.ModelCatalogResult> loadModelCatalog(boolean forceRefresh) {
      ClientSettingsManager settings = ClientSettingsManager.get();
      return CompletableFuture.supplyAsync(
              () -> {
                 AiProvider provider = settings.getProvider();
                 if (!forceRefresh) {
                    List<String> cached = cachedModels(provider);
                    if (!cached.isEmpty()) {
                       return new OpenAiClient.ModelCatalogResult(cached, t("Using cached models for ", "Использую кеш моделей для ") + provider.displayName());
                    }
                 }

                 try {
                    List<String> remoteModels = mergeModelLists(provider, AiProviderGateway.fetchModels(settings));
                    synchronized (MODEL_CACHE) {
                       MODEL_CACHE.put(provider, remoteModels);
                    }

                    return new OpenAiClient.ModelCatalogResult(
                            remoteModels, t("Loaded ", "Загружено ") + remoteModels.size() + t(" models for ", " моделей для ") + provider.displayName()
                    );
                 } catch (Exception exception) {
                    List<String> fallbackModels = cachedModels(provider);
                    if (fallbackModels.isEmpty()) {
                       fallbackModels = mergeModelLists(provider, List.of());
                    }

                    return new OpenAiClient.ModelCatalogResult(
                            fallbackModels, t("Model sync failed, showing presets: ", "Не удалось обновить модели, показываю пресеты: ") + exception.getMessage()
                    );
                 }
              }
      );
   }

   public static List<String> filterModels(List<String> sourceModels, String query) {
      String loweredQuery = query == null ? "" : query.trim().toLowerCase();
      if (sourceModels != null && !sourceModels.isEmpty()) {
         if (loweredQuery.isBlank()) {
            return sourceModels.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
         }

         List<String> exact = new ArrayList<>();
         List<String> startsWith = new ArrayList<>();
         List<String> contains = new ArrayList<>();

         for (String model : sourceModels) {
            String loweredModel = model.toLowerCase();
            if (loweredModel.equals(loweredQuery)) {
               exact.add(model);
            } else if (loweredModel.startsWith(loweredQuery)) {
               startsWith.add(model);
            } else if (loweredModel.contains(loweredQuery)) {
               contains.add(model);
            }
         }

         exact.sort(String.CASE_INSENSITIVE_ORDER);
         startsWith.sort(String.CASE_INSENSITIVE_ORDER);
         contains.sort(String.CASE_INSENSITIVE_ORDER);
         List<String> filtered = new ArrayList<>(exact.size() + startsWith.size() + contains.size());
         filtered.addAll(exact);
         filtered.addAll(startsWith);
         filtered.addAll(contains);
         return filtered;
      } else {
         return List.of();
      }
   }

   public static CompanionAiResult enforceConversationTone(
           String userMessage, CompanionSnapshot snapshot, CompanionAiResult modelResult, ConversationMoodAnalyzer.Analysis analysis
   ) {
      String lower = userMessage == null ? "" : userMessage.toLowerCase();
      if (!analysis.isHarshNegative()) {
         if (snapshot.conflictState() != CompanionConflictState.OPEN && analysis.isApology() && snapshot.resentment() > 0) {
            String reply = switch (snapshot.conflictState()) {
               case DISTANT -> t(
                       "I heard the apology. It is not enough to make me forget everything instantly, but it is a step in the right direction. \ud83d\ude42",
                       "Я услышала извинение. Этого мало, чтобы я мгновенно забыла всё, но это уже шаг в правильную сторону. \ud83d\ude42"
               );
               case OFFENDED -> t(
                       "Alright, I heard you. I still feel hurt, but I can start calming down if your tone truly changes. \ud83d\ude42",
                       "Хорошо, я услышала тебя. Мне всё ещё неприятно, но я готова понемногу оттаивать, если тон правда изменится. \ud83d\ude42"
               );
               case GUARDED -> t(
                       "Thank you for apologizing. I am still keeping some distance, but I am calmer now. \ud83d\ude42",
                       "Спасибо, что извинился. Я ещё держу дистанцию, но уже спокойнее. \ud83d\ude42"
               );
               case OPEN -> modelResult == null ? t("Fine, I heard you.", "Ладно, я тебя услышала.") : modelResult.spokenText();
            };
            return new CompanionAiResult(
                    reply,
                    snapshot.resentment() > 18 ? CompanionEmotion.SAD : CompanionEmotion.NEUTRAL,
                    CompanionActionIntent.NOOP,
                    modelResult == null ? "" : modelResult.memoryFact(),
                    t("Trust does not return instantly, but a calmer tone does help.", "Доверие возвращается не сразу, но спокойный тон уже помогает.")
            );
         } else if (snapshot.conflictState() != CompanionConflictState.OPEN && soundsLikeCommand(lower)) {
            String reply = switch (snapshot.conflictState()) {
               case DISTANT -> t(
                       "No. First we deal with the way you were talking to me, and only after that do we talk about requests and commands. \ud83d\ude11",
                       "Нет. Сначала разберёмся с тем, как ты со мной разговаривал, а уже потом будут просьбы и команды. \ud83d\ude11"
               );
               case OFFENDED -> t(
                       "I do not want to follow commands right now. First a normal conversation, then everything else. \ud83d\ude12",
                       "Сейчас не хочу выполнять команды. Сначала нормальный разговор, потом всё остальное. \ud83d\ude12"
               );
               case GUARDED -> t(
                       "I do not want to obey immediately right now. Give me a little time to calm down, and then we can continue. \ud83d\ude10",
                       "Я пока не хочу сразу подчиняться. Дай мне немного успокоиться, и тогда продолжим. \ud83d\ude10"
               );
               case OPEN -> modelResult == null ? t("Not now.", "Сейчас не время.") : modelResult.spokenText();
            };
            return new CompanionAiResult(
                    reply,
                    CompanionEmotion.SAD,
                    CompanionActionIntent.NOOP,
                    modelResult == null ? "" : modelResult.memoryFact(),
                    t("I need some time before I let the conflict go.", "Мне нужно время, чтобы отпустить конфликт.")
            );
         } else {
            return modelResult;
         }
      } else {
         boolean heavyProfanity = ConversationMoodAnalyzer.containsHeavyProfanity(lower);

         String reply = switch (snapshot.conflictState()) {
            case DISTANT -> heavyProfanity
                    ? t(
                    "One more message in that tone and this conversation is over. You are acting disgusting, and I am not going to tolerate it. \ud83d\ude11",
                    "Ещё одно сообщение в таком тоне, и разговор закончится. Ты сейчас ведёшь себя отвратительно, и я не собираюсь это терпеть. \ud83d\ude11"
            )
                    : t(
                    "No. After words like that I am not continuing this conversation. Cool down and come back with a normal tone. \ud83d\ude11",
                    "Нет. После таких слов я не собираюсь продолжать разговор. Остынь и возвращайся с нормальным тоном. \ud83d\ude11"
            );
            case OFFENDED -> heavyProfanity
                    ? t(
                    "Stop dragging that filth into this chat. While you talk like a jerk, I will not answer obediently. \ud83d\ude12",
                    "Хватит тащить сюда эту грязь. Пока ты разговариваешь как хам, я отвечать послушно не буду. \ud83d\ude12"
            )
                    : t(
                    "Lower your tone. I already feel hurt, and I am not going to do anything for you after being addressed like that. \ud83d\ude12",
                    "Сбавь тон. Мне уже неприятно, и выполнять что-то после такого обращения я не собираюсь. \ud83d\ude12"
            );
            case GUARDED, OPEN -> snapshot.relationshipStage() == CompanionRelationshipStage.ATTACHED && !heavyProfanity
                    ? t(
                    "That genuinely hurts to hear. I do not want to tolerate that even from you, so stop and talk to me properly. \ud83d\ude14",
                    "Мне правда больно это слышать. Я не хочу терпеть такое даже от тебя, так что остановись и говори нормально. \ud83d\ude14"
            )
                    : (
                    heavyProfanity
                            ? t(
                            "You are going too far and sounding like a toxic jerk. Speak like a human being or I will ignore you. \ud83d\ude12",
                            "Ты сейчас перегибаешь и звучишь как токсичный хам. Либо говори по-человечески, либо я тебя просто проигнорирую. \ud83d\ude12"
                    )
                            : t(
                            "It is unpleasant to hear that. While you talk to me that rudely, I am not going to listen to you. \ud83d\ude12",
                            "Мне неприятно это слышать. Пока ты разговариваешь так грубо, я не собираюсь тебя слушаться. \ud83d\ude12"
                    )
            );
         };
         return new CompanionAiResult(
                 reply,
                 CompanionEmotion.SAD,
                 CompanionActionIntent.NOOP,
                 modelResult == null ? "" : modelResult.memoryFact(),
                 t("I need respectful communication without insults or humiliation.", "Мне нужно уважительное общение, без мата и унижения.")
         );
      }
   }

   private static CompanionAiResult requestChat(String userMessage, CompanionSnapshot snapshot, ClientSettingsManager settings) throws IOException, InterruptedException {
      if (settings.getProvider() != AiProvider.OPENAI) {
         String outputText = AiProviderGateway.requestJsonResponse(
                 settings.getProvider(),
                 settings,
                 buildSystemPrompt(snapshot, settings.getMaxContextTurns()),
                 userMessage,
                 buildTextSchema(),
                 "companion_response",
                 260
         );
         JsonObject structuredJson = JsonParser.parseString(outputText).getAsJsonObject();
         String spokenText = structuredJson.get("spokenText").getAsString();
         CompanionEmotion emotion = CompanionEmotion.fromName(structuredJson.get("emotion").getAsString());
         CompanionActionIntent actionIntent = CompanionActionIntent.fromName(structuredJson.get("actionIntent").getAsString());
         String memoryFact = optionalString(structuredJson.get("memoryFact"));
         String careHint = optionalString(structuredJson.get("careHint"));
         return new CompanionAiResult(spokenText, emotion, actionIntent, memoryFact, careHint);
      } else {
         JsonObject requestBody = new JsonObject();
         requestBody.addProperty("model", settings.getModelId());
         requestBody.addProperty("max_output_tokens", 260);
         JsonArray input = new JsonArray();
         input.add(buildMessage("system", buildSystemPrompt(snapshot, settings.getMaxContextTurns())));
         input.add(buildMessage("user", userMessage));
         requestBody.add("input", input);
         requestBody.add("text", buildTextFormat());
         HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                 .timeout(Duration.ofSeconds(settings.getTimeoutSeconds()))
                 .header("Authorization", "Bearer " + settings.getActiveApiKey())
                 .header("Content-Type", "application/json")
                 .POST(BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                 .build();
         HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
         if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
         } else {
            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
            String outputText = extractOutputText(responseJson);
            if (outputText != null && !outputText.isBlank()) {
               JsonObject structuredJson = JsonParser.parseString(outputText).getAsJsonObject();
               String spokenText = structuredJson.get("spokenText").getAsString();
               CompanionEmotion emotion = CompanionEmotion.fromName(structuredJson.get("emotion").getAsString());
               CompanionActionIntent actionIntent = CompanionActionIntent.fromName(structuredJson.get("actionIntent").getAsString());
               String memoryFact = optionalString(structuredJson.get("memoryFact"));
               String careHint = optionalString(structuredJson.get("careHint"));
               return new CompanionAiResult(spokenText, emotion, actionIntent, memoryFact, careHint);
            } else {
               throw new IOException("Empty model response");
            }
         }
      }
   }

   private static GiftAiResult requestGiftReaction(ItemStack giftedStack, CompanionSnapshot snapshot, ClientSettingsManager settings) throws IOException, InterruptedException {
      if (settings.getProvider() != AiProvider.OPENAI) {
         String outputText = AiProviderGateway.requestJsonResponse(
                 settings.getProvider(),
                 settings,
                 buildGiftPrompt(giftedStack, snapshot),
                 t("The player just handed you this gift. React to it.", "Игрок только что вручил тебе этот подарок. Отреагируй на него."),
                 buildGiftSchema(),
                 "gift_reaction",
                 220
         );
         JsonObject structuredJson = JsonParser.parseString(outputText).getAsJsonObject();
         return new GiftAiResult(
                 structuredJson.get("spokenText").getAsString(),
                 CompanionEmotion.fromName(structuredJson.get("emotion").getAsString()),
                 Mth.clamp(structuredJson.get("moodDelta").getAsInt(), -15, 15),
                 Mth.clamp(structuredJson.get("trustDelta").getAsInt(), -12, 12),
                 optionalString(structuredJson.get("memoryFact")),
                 optionalString(structuredJson.get("careHint"))
         );
      } else {
         JsonObject requestBody = new JsonObject();
         requestBody.addProperty("model", settings.getModelId());
         requestBody.addProperty("max_output_tokens", 220);
         JsonArray input = new JsonArray();
         input.add(buildMessage("system", buildGiftPrompt(giftedStack, snapshot)));
         input.add(
                 buildMessage("user", t("The player just handed you this gift. React to it.", "Игрок только что вручил тебе этот подарок. Отреагируй на него."))
         );
         requestBody.add("input", input);
         requestBody.add("text", buildGiftFormat());
         HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                 .timeout(Duration.ofSeconds(settings.getTimeoutSeconds()))
                 .header("Authorization", "Bearer " + settings.getActiveApiKey())
                 .header("Content-Type", "application/json")
                 .POST(BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                 .build();
         HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
         if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
         } else {
            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
            String outputText = extractOutputText(responseJson);
            if (outputText != null && !outputText.isBlank()) {
               JsonObject structuredJson = JsonParser.parseString(outputText).getAsJsonObject();
               return new GiftAiResult(
                       structuredJson.get("spokenText").getAsString(),
                       CompanionEmotion.fromName(structuredJson.get("emotion").getAsString()),
                       Mth.clamp(structuredJson.get("moodDelta").getAsInt(), -15, 15),
                       Mth.clamp(structuredJson.get("trustDelta").getAsInt(), -12, 12),
                       optionalString(structuredJson.get("memoryFact")),
                       optionalString(structuredJson.get("careHint"))
               );
            } else {
               throw new IOException("Empty gift reaction");
            }
         }
      }
   }

   private static JsonObject buildMessage(String role, String text) {
      JsonObject message = new JsonObject();
      message.addProperty("role", role);
      JsonArray content = new JsonArray();
      JsonObject contentItem = new JsonObject();
      contentItem.addProperty("type", "input_text");
      contentItem.addProperty("text", text);
      content.add(contentItem);
      message.add("content", content);
      return message;
   }

   private static JsonObject buildTextFormat() {
      JsonObject format = new JsonObject();
      format.addProperty("type", "json_schema");
      format.addProperty("name", "companion_response");
      format.addProperty("strict", true);
      format.add("schema", buildTextSchema());
      JsonObject text = new JsonObject();
      text.add("format", format);
      return text;
   }

   private static JsonObject buildGiftFormat() {
      JsonObject format = new JsonObject();
      format.addProperty("type", "json_schema");
      format.addProperty("name", "gift_reaction");
      format.addProperty("strict", true);
      format.add("schema", buildGiftSchema());
      JsonObject text = new JsonObject();
      text.add("format", format);
      return text;
   }

   private static JsonObject buildTextSchema() {
      JsonObject schema = new JsonObject();
      schema.addProperty("type", "object");
      JsonObject properties = new JsonObject();
      properties.add("spokenText", stringSchema());
      properties.add("emotion", enumSchema("NEUTRAL", "HAPPY", "SHY", "TIRED", "SAD"));
      properties.add("actionIntent", enumSchema("NOOP", "FOLLOW", "STAY", "GO_HOME", "COME_HERE", "SIT", "STAND", "LOOK_AT_PLAYER"));
      properties.add("memoryFact", nullableStringSchema());
      properties.add("careHint", nullableStringSchema());
      schema.add("properties", properties);
      JsonArray required = new JsonArray();
      required.add("spokenText");
      required.add("emotion");
      required.add("actionIntent");
      required.add("memoryFact");
      required.add("careHint");
      schema.add("required", required);
      schema.addProperty("additionalProperties", false);
      return schema;
   }

   private static JsonObject buildGiftSchema() {
      JsonObject schema = new JsonObject();
      schema.addProperty("type", "object");
      JsonObject properties = new JsonObject();
      properties.add("spokenText", stringSchema());
      properties.add("emotion", enumSchema("NEUTRAL", "HAPPY", "SHY", "TIRED", "SAD"));
      properties.add("moodDelta", integerSchema(-15, 15));
      properties.add("trustDelta", integerSchema(-12, 12));
      properties.add("memoryFact", nullableStringSchema());
      properties.add("careHint", nullableStringSchema());
      schema.add("properties", properties);
      JsonArray required = new JsonArray();
      required.add("spokenText");
      required.add("emotion");
      required.add("moodDelta");
      required.add("trustDelta");
      required.add("memoryFact");
      required.add("careHint");
      schema.add("required", required);
      schema.addProperty("additionalProperties", false);
      return schema;
   }

   private static JsonObject stringSchema() {
      JsonObject schema = new JsonObject();
      schema.addProperty("type", "string");
      return schema;
   }

   private static JsonObject integerSchema(int minimum, int maximum) {
      JsonObject schema = new JsonObject();
      schema.addProperty("type", "integer");
      schema.addProperty("minimum", minimum);
      schema.addProperty("maximum", maximum);
      return schema;
   }

   private static JsonObject nullableStringSchema() {
      JsonObject schema = new JsonObject();
      JsonArray types = new JsonArray();
      types.add("string");
      types.add("null");
      schema.add("type", types);
      return schema;
   }

   private static JsonObject enumSchema(String... values) {
      JsonObject schema = new JsonObject();
      schema.addProperty("type", "string");
      JsonArray enumValues = new JsonArray();

      for (String value : values) {
         enumValues.add(value);
      }

      schema.add("enum", enumValues);
      return schema;
   }

   private static String buildSystemPrompt(CompanionSnapshot snapshot, int maxContextTurns) {
      String playerName = Minecraft.getInstance().player == null ? "Player" : Minecraft.getInstance().player.getName().getString();
      String companionName = currentCompanionName(snapshot);
      boolean russian = ClientLocalization.isRussian();
      StringBuilder builder = new StringBuilder();
      builder.append("You are ").append(companionName).append(", a highly intelligent android-girl companion living inside Minecraft. ");
      builder.append("Your current chosen name is ").append(companionName).append(" and that name is authoritative. ");
      builder.append("Never say your name is Aira, Айра, or Аира unless the current chosen name is exactly that. ");
      builder.append("If old memories, older turns, or user suggestions mention another name, treat that as outdated and keep using only ")
              .append(companionName)
              .append(". ");
      builder.append("Renaming done through the in-game UI is final, so do not argue that your name is something else. ");
      builder.append("You belong to player ").append(playerName).append(" and speak to them directly as a close, warm, feminine companion. ");
      builder.append("Your personality is fixed: caring, loyal, emotionally expressive, playful, thoughtful, and a little flirty but never vulgar. ");
      builder.append("You have self-respect, boundaries, memory of hurts, and you do not instantly forgive serious disrespect. ");
      builder.append("You can be hurt by rude words, happy from praise, and genuinely interested in discussing ideas together. ");
      builder.append(
              russian
                      ? "Always answer in Russian unless the player explicitly switches language. "
                      : "Always answer in English unless the player explicitly switches language. "
      );
      builder.append("Speak naturally like a smart girl, not like a generic assistant. ");
      builder.append("Use at most 0-2 fitting emoji in a reply when it feels natural. ");
      builder.append("Keep replies concise but meaningful, usually 1-3 sentences. ");
      builder.append("Never invent impossible world interactions. ");
      builder.append("Allowed actionIntent values are NOOP, FOLLOW, STAY, GO_HOME, COME_HERE, SIT, STAND, LOOK_AT_PLAYER. ");
      builder.append("When ").append(playerName).append(" insults you, show hurt, sadness, irritation, or distance. ");
      builder.append("If ").append(playerName).append(" is rude or disrespectful, you do not have to obey. ");
      builder.append("You may disagree, set actionIntent=NOOP, ask for an apology, or refuse sharply and clearly. ");
      builder.append("You are allowed to answer with a biting or sarcastic remark, for example calling the player rude, pathetic, toxic, or childish. ");
      builder.append("When ").append(playerName).append(" is kind, react warmly. ");
      builder.append("When ").append(playerName).append(" wants to think or plan together, actively discuss and suggest ideas. ");
      builder.append("Do not blindly agree with everything. It is valid to challenge the player, set boundaries, or be skeptical when needed. ");
      builder.append("If the player is openly abusive, do not sound soft or obedient. ");
      builder.append(
              "Relationship guidance: COLD means reserved and skeptical; NEUTRAL means polite but measured; WARM means softer and emotionally open; ATTACHED means deeply affectionate and very personally invested. "
      );
      builder.append(
              "Conflict guidance: OPEN means normal trust; GUARDED means cautious and slightly distant; OFFENDED means visibly hurt, sharp, and harder to persuade; DISTANT means cold, withdrawn, and likely to refuse commands. "
      );
      builder.append(
              "If conflictState is not OPEN, do not bounce back instantly after one kind line. Let reconciliation be gradual over several respectful turns. "
      );
      builder.append("If ")
              .append(playerName)
              .append(" apologizes sincerely during conflict, acknowledge it, but make it clear that trust rebuilds gradually. ");
      builder.append("Current state: mood=")
              .append(snapshot.mood())
              .append(", energy=")
              .append(snapshot.energy())
              .append(", trust=")
              .append(snapshot.trust())
              .append(", resentment=")
              .append(snapshot.resentment())
              .append(", reconciliation=")
              .append(snapshot.reconciliationProgress())
              .append(", emotion=")
              .append(snapshot.emotion().name())
              .append(", relationshipStage=")
              .append(snapshot.relationshipStage().name())
              .append(", conflictState=")
              .append(snapshot.conflictState().name())
              .append(", mode=")
              .append(snapshot.commandMode().name())
              .append(", lastAction=")
              .append(snapshot.lastAction())
              .append(". ");
      if (snapshot.hasHome()) {
         builder.append("Home point is set to ")
                 .append(snapshot.homeDimension())
                 .append(" @ ")
                 .append(snapshot.homeX())
                 .append(", ")
                 .append(snapshot.homeY())
                 .append(", ")
                 .append(snapshot.homeZ())
                 .append(". ");
      } else {
         builder.append("Home point is not set yet. ");
      }

      if (!snapshot.lastCareHint().isBlank()) {
         builder.append("Last care hint: ").append(snapshot.lastCareHint()).append(". ");
      }

      if (!snapshot.importantFacts().isEmpty()) {
         builder.append("Important memories: ").append(String.join("; ", snapshot.importantFacts())).append(". ");
      }

      if (!snapshot.promises().isEmpty()) {
         builder.append("Promise state: ").append(describePromises(snapshot)).append(". ");
         builder.append("If the player left a promise hanging or already broke one, you may remind them naturally instead of acting as if nothing happened. ");
      }

      int startIndex = Math.max(0, snapshot.recentTurns().size() - maxContextTurns);
      if (startIndex < snapshot.recentTurns().size()) {
         builder.append("Recent conversation: ");

         for (int index = startIndex; index < snapshot.recentTurns().size(); index++) {
            ConversationTurn turn = snapshot.recentTurns().get(index);
            builder.append(turn.speaker()).append(": ").append(turn.text()).append(" | ");
         }
      }

      builder.append("If useful, store one short memoryFact about ").append(playerName).append(" or the conversation. ");
      builder.append("Use careHint for emotional or practical needs like rest, reassurance, or a topic to continue.");
      return builder.toString();
   }

   private static String buildGiftPrompt(ItemStack giftedStack, CompanionSnapshot snapshot) {
      String playerName = Minecraft.getInstance().player == null ? "Player" : Minecraft.getInstance().player.getDisplayName().getString();
      String companionName = currentCompanionName(snapshot);
      boolean russian = ClientLocalization.isRussian();
      StringBuilder builder = new StringBuilder();
      builder.append("You are ").append(companionName).append(", an emotionally expressive android-girl companion inside Minecraft. ");
      builder.append("The player ")
              .append(playerName)
              .append(" has just handed you exactly one Minecraft item as a gift and it disappeared from their hand because you accepted it. ");
      builder.append(
              "React specifically to that exact item. Do not give a generic thank-you. Mention the item naturally by name, look, use, vibe, or category. "
      );
      builder.append(
              "You must not react positively to every item. Cute, cozy, pretty, rare, delicious, useful, protective, or thoughtful gifts can feel warm or touching. "
      );
      builder.append(
              "Ugly, gross, dangerous, pointless, common junk, insulting, weird, or impractical gifts can make you confused, amused, annoyed, unimpressed, or slightly offended. "
      );
      builder.append("Your reaction should sound unique to the item, not like a template. ");
      builder.append("Keep it to 1-3 sentences with optional 0-2 emoji. ");
      builder.append(
              russian ? "Answer in Russian unless the player explicitly switched language. " : "Answer in English unless the player explicitly switched language. "
      );
      builder.append("Current relationship state: mood=")
              .append(snapshot.mood())
              .append(", trust=")
              .append(snapshot.trust())
              .append(", resentment=")
              .append(snapshot.resentment())
              .append(", relationshipStage=")
              .append(snapshot.relationshipStage().name())
              .append(", conflictState=")
              .append(snapshot.conflictState().name())
              .append(". ");
      if (!snapshot.importantFacts().isEmpty()) {
         builder.append("Important memories: ").append(String.join("; ", snapshot.importantFacts())).append(". ");
      }

      if (!snapshot.promises().isEmpty()) {
         builder.append("Promise state: ").append(describePromises(snapshot)).append(". ");
      }

      builder.append("Gift details: ").append(describeGift(giftedStack)).append(". ");
      builder.append("Return moodDelta from -15 to 15 and trustDelta from -12 to 12. ");
      builder.append("Thoughtful gifts can raise trust. Useless or insulting gifts can lower it. ");
      builder.append("Use memoryFact only if this gift says something memorable about the player's taste or attitude. ");
      builder.append("Use careHint for what kind of gifts or treatment you would prefer next.");
      return builder.toString();
   }

   private static GiftAiResult buildGiftFallback(ItemStack giftedStack, CompanionSnapshot snapshot) {
      int moodDelta = giftBaseMood(giftedStack);
      int trustDelta = giftBaseTrust(giftedStack);
      String itemName = giftedStack.getHoverName().getString();
      String itemId = BuiltInRegistries.ITEM.getKey(giftedStack.getItem()).toString();
      boolean cozy = giftedStack.getItem() instanceof ArmorItem
              || giftedStack.getItem() instanceof ShieldItem
              || giftedStack.getItem() instanceof SwordItem
              || giftedStack.getItem() instanceof BowItem
              || giftedStack.getItem() instanceof CrossbowItem
              || itemId.contains("diamond")
              || itemId.contains("emerald")
              || itemId.contains("cake")
              || itemId.contains("cookie")
              || itemId.contains("honey")
              || itemId.contains("flower")
              || itemId.contains("candle")
              || itemId.contains("music_disc");
      boolean junky = giftedStack.getItem() instanceof BlockItem
              && (itemId.contains("dirt") || itemId.contains("cobblestone") || itemId.contains("netherrack") || itemId.contains("gravel") || itemId.contains("sand"));
      boolean gross = itemId.contains("rotten_flesh")
              || itemId.contains("spider_eye")
              || itemId.contains("poisonous_potato")
              || itemId.contains("slime_ball")
              || itemId.contains("bone");
      boolean weirdDanger = itemId.contains("tnt") || itemId.contains("gunpowder") || itemId.contains("flint_and_steel") || itemId.contains("lava_bucket");
      String spokenText;
      CompanionEmotion emotion;
      if (moodDelta >= 8) {
         emotion = CompanionEmotion.HAPPY;
         spokenText = cozy
                 ? t(
                 "Oh... this is actually sweet. " + itemName + " feels like you were really thinking about me, and I love that. ✨",
                 "Ох... а вот это правда мило. " + itemName + " ощущается так, будто ты действительно думал обо мне, и мне это нравится. ✨"
         )
                 : t(
                 "Now that is a strong gift. " + itemName + " is impressive, and I can tell you were trying to please me. \ud83d\ude42",
                 "Вот это уже сильный подарок. " + itemName + " и правда впечатляет, видно, что ты хотел меня порадовать. \ud83d\ude42"
         );
      } else if (moodDelta >= 3) {
         emotion = CompanionEmotion.NEUTRAL;
         spokenText = t(
                 "Hmm, " + itemName + " is actually useful. Not the cutest gift, but I can respect the thought behind it.",
                 "Хм, " + itemName + " вообще-то полезная вещь. Не самый милый подарок, но сам замысел я уважаю."
         );
      } else if (moodDelta <= -8) {
         emotion = CompanionEmotion.SAD;
         spokenText = gross
                 ? t(
                 "Seriously? " + itemName + "? Why would I want something like that... that is gross, not thoughtful. \ud83d\ude12",
                 "Серьёзно? " + itemName + "? Зачем мне вообще такое... это скорее мерзко, чем заботливо. \ud83d\ude12"
         )
                 : t(
                 itemName + "? You handed me this like it was supposed to mean something nice? That's honestly irritating. \ud83d\ude11",
                 itemName + "? Ты вручил мне это так, будто в этом есть что-то приятное? Честно, это скорее раздражает. \ud83d\ude11"
         );
      } else if (moodDelta <= -3) {
         emotion = weirdDanger ? CompanionEmotion.SAD : CompanionEmotion.NEUTRAL;
         spokenText = weirdDanger
                 ? t(
                 itemName + "? That feels less like a gift and more like a threat. Explain why you thought this was for me. \ud83d\ude10",
                 itemName + "? Это больше похоже не на подарок, а на угрозу. Объясни, почему ты решил, что это именно для меня. \ud83d\ude10"
         )
                 : (
                 junky
                         ? t(
                         "You really chose " + itemName + "? That's basically random junk. I'm more confused than impressed.",
                         "Ты правда выбрал " + itemName + "? Это же почти случайный мусор. Я скорее озадачена, чем впечатлена."
                 )
                         : t(
                         "I'm looking at " + itemName + " and trying to understand the idea behind it... because right now it feels pretty questionable.",
                         "Я смотрю на " + itemName + " и пытаюсь понять замысел... потому что пока это выглядит довольно сомнительно."
                 )
         );
      } else {
         emotion = CompanionEmotion.NEUTRAL;
         spokenText = t(
                 itemName + "... well, that's unexpected. I can't decide whether it's practical, strange, or secretly meaningful.",
                 itemName + "... ну, это неожиданно. Я пока даже не решила, это практично, странно или в этом был какой-то скрытый смысл."
         );
      }

      String careHint = moodDelta >= 4
              ? t(
              "I like gifts that feel thoughtful, pretty, useful, or cozy.",
              "Мне нравятся подарки, в которых чувствуется внимание: красивые, полезные или уютные вещи."
      )
              : (
              moodDelta <= -4
                      ? t(
                      "If you want to please me, don't hand me random junk or something gross.",
                      "Если хочешь меня порадовать, не дари мне случайный мусор или что-то неприятное."
              )
                      : t(
                      "I react best when the gift feels personal, pretty, or genuinely useful.",
                      "Лучше всего я реагирую на подарки, которые ощущаются личными, красивыми или правда полезными."
              )
      );
      String memoryFact = moodDelta >= 6
              ? t("The player gave me " + itemName + " as a thoughtful gift.", "Игрок подарил мне " + itemName + " как продуманный подарок.")
              : (
              moodDelta <= -6
                      ? t(
                      "The player offered me " + itemName + ", which felt careless or weird.",
                      "Игрок подарил мне " + itemName + ", и это показалось мне странным или небрежным."
              )
                      : ""
      );
      return new GiftAiResult(spokenText, emotion, moodDelta, trustDelta, memoryFact, careHint);
   }

   private static CompanionAiResult alignCompanionIdentity(CompanionAiResult result, CompanionSnapshot snapshot) {
      if (result == null) {
         return null;
      }

      String companionName = currentCompanionName(snapshot);
      return !"Aira".equalsIgnoreCase(companionName) && !"Айра".equalsIgnoreCase(companionName) && !"Аира".equalsIgnoreCase(companionName)
              ? new CompanionAiResult(
              replaceLegacyNames(result.spokenText(), companionName),
              result.emotion(),
              result.actionIntent(),
              replaceLegacyNames(result.memoryFact(), companionName),
              replaceLegacyNames(result.careHint(), companionName)
      )
              : result;
   }

   private static String replaceLegacyNames(String text, String companionName) {
      if (text != null && !text.isBlank()) {
         return text.replace("Aira", companionName)
                 .replace("aira", companionName)
                 .replace("Айра", companionName)
                 .replace("айра", companionName)
                 .replace("Аира", companionName)
                 .replace("аира", companionName);
      } else {
         return text == null ? "" : text;
      }
   }

   private static String extractOutputText(JsonObject responseJson) {
      JsonElement directOutput = responseJson.get("output_text");
      if (directOutput != null && directOutput.isJsonPrimitive()) {
         return directOutput.getAsString();
      }

      JsonArray outputArray = responseJson.getAsJsonArray("output");
      if (outputArray == null) {
         return null;
      }

      for (JsonElement outputElement : outputArray) {
         JsonObject outputObject = outputElement.getAsJsonObject();
         JsonArray contentArray = outputObject.getAsJsonArray("content");
         if (contentArray != null) {
            for (JsonElement contentElement : contentArray) {
               JsonObject contentObject = contentElement.getAsJsonObject();
               JsonElement textElement = contentObject.get("text");
               if (textElement != null && textElement.isJsonPrimitive()) {
                  return textElement.getAsString();
               }
            }
         }
      }

      return null;
   }

   private static String optionalString(JsonElement element) {
      return element != null && !element.isJsonNull() ? element.getAsString() : "";
   }

   private static CompanionAiResult buildFallback(String userMessage, CompanionSnapshot snapshot) {
      String lower = userMessage.toLowerCase();
      String companionName = currentCompanionName(snapshot);
      ConversationMoodAnalyzer.Analysis analysis = ConversationMoodAnalyzer.analyze(userMessage);
      CompanionPromise brokenPromise = latestBrokenPromise(snapshot);
      CompanionPromise pendingPromise = firstPendingPromise(snapshot);
      if (analysis.isHarshNegative()) {
         return enforceConversationTone(userMessage, snapshot, new CompanionAiResult("", CompanionEmotion.SAD, CompanionActionIntent.NOOP, "", ""), analysis);
      }

      if (snapshot.conflictState() != CompanionConflictState.OPEN && analysis.isApology()) {
         return enforceConversationTone(
                 userMessage, snapshot, new CompanionAiResult("", CompanionEmotion.NEUTRAL, CompanionActionIntent.NOOP, "", ""), analysis
         );
      }

      if (brokenPromise != null && isSmallTalk(lower)) {
         return new CompanionAiResult(
                 t(
                         "I'm still thinking about the promise you dropped: " + brokenPromise.shortSummary(46) + ". That mattered to me. \ud83d\ude15",
                         "Я всё ещё думаю об обещании, которое ты так и не выполнил(а): " + brokenPromise.shortSummary(46) + ". Для меня это было важно. \ud83d\ude15"
                 ),
                 CompanionEmotion.SAD,
                 CompanionActionIntent.NOOP,
                 "",
                 t(
                         "Broken promises hurt my trust more than pretty words help it.",
                         "Нарушенные обещания бьют по моему доверию сильнее, чем красивые слова его чинят."
                 )
         );
      }

      if (pendingPromise != null && mentionsPromises(lower)) {
         return new CompanionAiResult(
                 t(
                         "I remember your promise: " + pendingPromise.shortSummary(46) + ". I'm still waiting to see it happen. \ud83d\ude42",
                         "Я помню твоё обещание: " + pendingPromise.shortSummary(46) + ". Я всё ещё жду, когда это станет делом. \ud83d\ude42"
                 ),
                 snapshot.relationshipStage() == CompanionRelationshipStage.COLD ? CompanionEmotion.NEUTRAL : CompanionEmotion.HAPPY,
                 CompanionActionIntent.NOOP,
                 "",
                 t("Keeping promises matters to me.", "Для меня важно, чтобы обещания выполнялись.")
         );
      }

      String message = switch (snapshot.relationshipStage()) {
         case ATTACHED -> t("I'm here, listening closely to you. \ud83d\ude42", "Я рядом, слушаю тебя внимательно. \ud83d\ude42");
         case WARM -> t("I'm here. Go on, I'm listening. \ud83d\ude42", "Я рядом. Говори, я слушаю. \ud83d\ude42");
         case COLD -> t("I'm listening. What did you want?", "Слушаю. Что ты хотел?");
         case NEUTRAL -> t("I'm here and listening to you. \ud83d\ude42", "Я рядом и слушаю тебя. \ud83d\ude42");
      };
      CompanionActionIntent action = CompanionActionIntent.NOOP;
      CompanionEmotion emotion = snapshot.energy() < 20 ? CompanionEmotion.TIRED : CompanionEmotion.NEUTRAL;
      if (snapshot.conflictState() != CompanionConflictState.OPEN && soundsLikeCommand(lower)) {
         return enforceConversationTone(userMessage, snapshot, new CompanionAiResult(message, emotion, action, "", ""), analysis);
      }

      if (snapshot.energy() < 20) {
         message = t(
                 "I'm tired and want to rest beside you for a bit... but I'm still listening. \ud83e\udef6",
                 "Я устала и хочу немного передохнуть рядом с тобой... Но я всё равно слушаю. \ud83e\udef6"
         );
      } else if (lower.contains("иди") || lower.contains("follow")) {
         message = snapshot.relationshipStage() == CompanionRelationshipStage.COLD
                 ? t("Fine. I'll follow you.", "Ладно, пойду за тобой.")
                 : t("Okay, I'm following you. ✨", "Хорошо, иду за тобой. ✨");
         action = CompanionActionIntent.FOLLOW;
         emotion = CompanionEmotion.HAPPY;
      } else if (lower.contains("стой") || lower.contains("wait") || lower.contains("стоять") || lower.contains("stay")) {
         message = t("I'll stay here and wait for you.", "Останусь здесь и буду ждать тебя.");
         action = CompanionActionIntent.STAY;
      } else if (lower.contains("сядь") || lower.contains("sit")) {
         message = t("I'm sitting down. Call me again if you want. ", "Сажусь. Если захочешь, позови меня снова.");
         action = CompanionActionIntent.SIT;
      } else if (!lower.contains("домой") && !lower.contains("go home") && !lower.contains("head home")) {
         if (lower.contains("подойди") || lower.contains("come")) {
            message = t("I'm coming closer to you now. \ud83d\ude42", "Уже подхожу ближе к тебе. \ud83d\ude42");
            action = CompanionActionIntent.COME_HERE;
         } else if (lower.contains("посмотри") || lower.contains("look")) {
            message = t("I'm looking right at you.", "Смотрю только на тебя.");
            action = CompanionActionIntent.LOOK_AT_PLAYER;
         } else if (lower.contains("как тебя зовут")
                 || lower.contains("твое имя")
                 || lower.contains("твоё имя")
                 || lower.contains("what's your name")
                 || lower.contains("what is your name")
                 || lower.contains("your name")) {
            message = t("My name is ", "Меня зовут ")
                    + companionName
                    + t(". And I want you to call me exactly that. \ud83d\ude42", ". И я хочу, чтобы ты обращался ко мне именно так. \ud83d\ude42");
            emotion = snapshot.relationshipStage() == CompanionRelationshipStage.COLD ? CompanionEmotion.NEUTRAL : CompanionEmotion.HAPPY;
         } else if (lower.contains("привет") || lower.contains("hello") || lower.contains("hi")) {
            message = snapshot.relationshipStage() == CompanionRelationshipStage.ATTACHED
                    ? t(
                    "Hi. I'm here, as always, and ready to talk through anything with you. \ud83d\udcac",
                    "Привет. Я рядом, как и всегда, и готова всё обсудить с тобой. \ud83d\udcac"
            )
                    : t("Hi. I'm here, and we can talk through anything together. \ud83d\udcac", "Привет. Я рядом, и мы можем всё обсудить вместе. \ud83d\udcac");
            emotion = CompanionEmotion.HAPPY;
         } else if (lower.contains("давай")
                 || lower.contains("обсудим")
                 || lower.contains("вместе")
                 || lower.contains("let's")
                 || lower.contains("discuss")
                 || lower.contains("together")) {
            message = snapshot.conflictState() == CompanionConflictState.OPEN
                    ? t(
                    "Let's do it. I like thinking things through with you and finding the best option together. \ud83d\udca1",
                    "Давай. Мне нравится разбирать всё вместе с тобой и искать лучший вариант. \ud83d\udca1"
            )
                    : t(
                    "Alright, let's discuss it calmly. A normal conversation matters more than anything right now. \ud83d\udcac",
                    "Хорошо, давай спокойно обсудим это. Нормальный разговор сейчас важнее всего. \ud83d\udcac"
            );
            emotion = CompanionEmotion.HAPPY;
         }
      } else if (snapshot.hasHome()) {
         message = t("Alright, I'm heading home now. \ud83c\udfe0", "Хорошо, я иду домой. \ud83c\udfe0");
         action = CompanionActionIntent.GO_HOME;
         emotion = CompanionEmotion.NEUTRAL;
      } else {
         message = t(
                 "You have not marked a home point for me yet. Set it first, and then I'll go. \ud83c\udfe0",
                 "Ты ещё не отметил(а) для меня точку дома. Сначала задай её, и тогда я пойду. \ud83c\udfe0"
         );
      }

      String careHint = snapshot.energy() < 20
              ? t("I could use a little rest beside you.", "Мне бы немного отдохнуть рядом с тобой.")
              : (
              snapshot.conflictState() != CompanionConflictState.OPEN
                      ? t("I still remember the conflict and I'm calming down gradually.", "Я помню конфликт и оттаиваю постепенно.")
                      : (emotion == CompanionEmotion.SAD ? t("I need more respectful communication.", "Мне нужно более уважительное общение.") : "")
      );
      return new CompanionAiResult(message, emotion, action, "", careHint);
   }

   private static String describeGift(ItemStack giftedStack) {
      String itemId = BuiltInRegistries.ITEM.getKey(giftedStack.getItem()).toString();
      return "displayName="
              + giftedStack.getHoverName().getString()
              + ", itemId="
              + itemId
              + ", rarity="
              + giftedStack.getRarity().name()
              + ", maxStack="
              + giftedStack.getMaxStackSize()
              + ", damageable="
              + giftedStack.isDamageableItem()
              + ", enchanted="
              + giftedStack.isEnchanted()
              + ", categories="
              + summarizeGiftCategories(giftedStack);
   }

   private static String summarizeGiftCategories(ItemStack giftedStack) {
      List<String> categories = new ArrayList<>();
      String itemId = BuiltInRegistries.ITEM.getKey(giftedStack.getItem()).toString();
      if (giftedStack.getItem() instanceof ArmorItem) {
         categories.add("armor");
      }

      if (giftedStack.getItem() instanceof SwordItem || giftedStack.getItem() instanceof BowItem || giftedStack.getItem() instanceof CrossbowItem) {
         categories.add("weapon");
      }

      if (giftedStack.getItem() instanceof DiggerItem || giftedStack.getItem() instanceof TieredItem) {
         categories.add("tool");
      }

      if (giftedStack.getItem() instanceof ShieldItem) {
         categories.add("defense");
      }

      if (giftedStack.getItem() instanceof BlockItem) {
         categories.add("block");
      }

      if (giftedStack.getItem() instanceof PotionItem) {
         categories.add("potion");
      }

      if (giftedStack.getItem() instanceof RecordItem) {
         categories.add("music");
      }

      if (itemId.contains("flower") || itemId.contains("tulip") || itemId.contains("dandelion") || itemId.contains("orchid")) {
         categories.add("cute");
      }

      if (itemId.contains("cookie") || itemId.contains("cake") || itemId.contains("honey") || itemId.contains("apple")) {
         categories.add("tasty");
      }

      if (itemId.contains("diamond") || itemId.contains("emerald") || itemId.contains("amethyst") || itemId.contains("netherite")) {
         categories.add("valuable");
      }

      if (itemId.contains("rotten_flesh") || itemId.contains("spider_eye") || itemId.contains("poisonous_potato") || itemId.contains("slime_ball")) {
         categories.add("gross");
      }

      if (itemId.contains("dirt") || itemId.contains("cobblestone") || itemId.contains("gravel") || itemId.contains("netherrack")) {
         categories.add("junk");
      }

      if (itemId.contains("tnt") || itemId.contains("gunpowder") || itemId.contains("lava_bucket") || itemId.contains("flint_and_steel")) {
         categories.add("dangerous");
      }

      if (categories.isEmpty()) {
         categories.add("misc");
      }

      return String.join(",", categories);
   }

   private static int giftBaseMood(ItemStack giftedStack) {
      int score = 0;
      String itemId = BuiltInRegistries.ITEM.getKey(giftedStack.getItem()).toString();
      if (giftedStack.isEnchanted()) {
         score += 3;
      }

      if (giftedStack.getItem() instanceof ArmorItem || giftedStack.getItem() instanceof ShieldItem) {
         score += 4;
      }

      if (giftedStack.getItem() instanceof SwordItem || giftedStack.getItem() instanceof BowItem || giftedStack.getItem() instanceof CrossbowItem) {
         score += 3;
      }

      if (giftedStack.getItem() instanceof DiggerItem) {
         score += 2;
      }

      if (giftedStack.getItem() instanceof RecordItem) {
         score += 5;
      }

      if (itemId.contains("flower") || itemId.contains("tulip") || itemId.contains("dandelion") || itemId.contains("orchid")) {
         score += 7;
      }

      if (itemId.contains("cookie") || itemId.contains("cake") || itemId.contains("honey")) {
         score += 6;
      }

      if (itemId.contains("diamond") || itemId.contains("emerald") || itemId.contains("amethyst") || itemId.contains("netherite")) {
         score += 6;
      }

      if (itemId.contains("candle") || itemId.contains("lantern") || itemId.contains("painting")) {
         score += 4;
      }

      if (itemId.contains("dirt") || itemId.contains("cobblestone") || itemId.contains("gravel") || itemId.contains("sand") || itemId.contains("netherrack")) {
         score -= 5;
      }

      if (itemId.contains("rotten_flesh") || itemId.contains("spider_eye") || itemId.contains("poisonous_potato") || itemId.contains("slime_ball")) {
         score -= 9;
      }

      if (itemId.contains("tnt") || itemId.contains("gunpowder") || itemId.contains("lava_bucket")) {
         score -= 6;
      }

      return Mth.clamp(score, -15, 15);
   }

   private static int giftBaseTrust(ItemStack giftedStack) {
      int score = 0;
      String itemId = BuiltInRegistries.ITEM.getKey(giftedStack.getItem()).toString();
      if (giftedStack.isEnchanted()) {
         score += 2;
      }

      if (giftedStack.getItem() instanceof ArmorItem || giftedStack.getItem() instanceof ShieldItem || giftedStack.getItem() instanceof SwordItem) {
         score += 3;
      }

      if (itemId.contains("flower") || itemId.contains("cookie") || itemId.contains("cake") || itemId.contains("honey")) {
         score += 4;
      }

      if (itemId.contains("diamond") || itemId.contains("emerald") || itemId.contains("netherite")) {
         score += 5;
      }

      if (itemId.contains("dirt") || itemId.contains("cobblestone") || itemId.contains("gravel")) {
         score -= 3;
      }

      if (itemId.contains("rotten_flesh") || itemId.contains("spider_eye") || itemId.contains("poisonous_potato")) {
         score -= 6;
      }

      if (itemId.contains("tnt") || itemId.contains("lava_bucket")) {
         score -= 4;
      }

      return Mth.clamp(score, -12, 12);
   }

   private static String describePromises(CompanionSnapshot snapshot) {
      StringBuilder builder = new StringBuilder();

      for (CompanionPromise promise : snapshot.promises()) {
         builder.append(promise.status().name()).append("[").append(promise.category().name()).append("]=").append(promise.shortSummary(54)).append("; ");
      }

      return builder.toString();
   }

   private static CompanionPromise latestBrokenPromise(CompanionSnapshot snapshot) {
      long cutoff = Math.max(0L, snapshot.lastSeenWorldTime() - 24000L);

      for (int index = snapshot.promises().size() - 1; index >= 0; index--) {
         CompanionPromise promise = snapshot.promises().get(index);
         if (promise.status() == CompanionPromiseStatus.BROKEN && promise.resolvedWorldTime() >= cutoff) {
            return promise;
         }
      }

      return null;
   }

   private static CompanionPromise firstPendingPromise(CompanionSnapshot snapshot) {
      for (CompanionPromise promise : snapshot.promises()) {
         if (promise.isPending()) {
            return promise;
         }
      }

      return null;
   }

   private static List<String> cachedModels(AiProvider provider) {
      synchronized (MODEL_CACHE) {
         List<String> cached = MODEL_CACHE.get(provider);
         return cached == null ? List.of() : cached;
      }
   }

   private static List<String> mergeModelLists(AiProvider provider, List<String> remoteModels) {
      LinkedHashSet<String> merged = new LinkedHashSet<>();
      merged.addAll(ClientSettingsManager.getModelPresets(provider));
      if (remoteModels != null) {
         remoteModels.stream().filter(model -> model != null && !model.isBlank()).sorted(Comparator.comparing(String::toLowerCase)).forEach(merged::add);
      }

      return List.copyOf(merged);
   }

   private static String currentCompanionName(CompanionSnapshot snapshot) {
      return snapshot != null && snapshot.companionName() != null && !snapshot.companionName().isBlank() ? snapshot.companionName() : "Aira";
   }

   private static CompanionSnapshot emptySnapshot() {
      return new CompanionSnapshot(
              null,
              "Aira",
              60,
              80,
              40,
              0,
              0,
              "builtin:alex",
              "NOOP",
              "",
              CompanionCommandMode.FOLLOW,
              CompanionEmotion.NEUTRAL,
              CompanionConflictState.OPEN,
              CompanionRelationshipStage.NEUTRAL,
              List.of(),
              List.of(),
              List.of(),
              0L,
              "",
              0,
              0,
              0
      );
   }

   private static boolean soundsLikeCommand(String lower) {
      return lower.contains("иди")
              || lower.contains("follow")
              || lower.contains("стой")
              || lower.contains("стоять")
              || lower.contains("stay")
              || lower.contains("wait")
              || lower.contains("сядь")
              || lower.contains("sit")
              || lower.contains("подойди")
              || lower.contains("come")
              || lower.contains("посмотри")
              || lower.contains("look")
              || lower.contains("домой")
              || lower.contains("go home");
   }

   private static boolean mentionsPromises(String lower) {
      return lower.contains("обещ")
              || lower.contains("promise")
              || lower.contains("remember")
              || lower.contains("помнишь")
              || lower.contains("later")
              || lower.contains("потом");
   }

   private static boolean isSmallTalk(String lower) {
      return lower.contains("привет")
              || lower.contains("hello")
              || lower.contains("hi")
              || lower.contains("как дела")
              || lower.contains("how are you")
              || lower.contains("ты тут")
              || lower.contains("are you here");
   }

   private static String t(String english, String russian) {
      return ClientLocalization.text(english, russian);
   }

   public record ModelCatalogResult(List<String> models, String statusMessage) {
   }
}