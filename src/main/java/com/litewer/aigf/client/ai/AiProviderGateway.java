package com.litewer.aigf.client.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.litewer.aigf.client.settings.AiProvider;
import com.litewer.aigf.client.settings.ClientSettingsManager;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class AiProviderGateway {
   private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();

   private AiProviderGateway() {
   }

   static String requestJsonResponse(
           AiProvider provider, ClientSettingsManager settings, String systemPrompt, String userPrompt,
           JsonObject schema, String schemaName, int maxTokens
   ) throws IOException, InterruptedException {
      return switch (provider) {
         case GEMINI -> requestGeminiJson(settings, systemPrompt, userPrompt, schema, maxTokens);
         case OPENROUTER -> requestOpenRouterJson(settings, systemPrompt, userPrompt, schema, schemaName, maxTokens);
         case OLLAMA -> requestOllamaJson(settings, systemPrompt, userPrompt, schema, maxTokens);
         case OPENAI -> throw new IOException("OpenAI is handled directly in OpenAiClient, not in AiProviderGateway");
      };
   }

   static List<String> fetchModels(ClientSettingsManager settings) throws IOException, InterruptedException {
      return switch (settings.getProvider()) {
         case GEMINI -> fetchGeminiModels(settings);
         case OPENROUTER -> fetchOpenRouterModels(settings);
         case OLLAMA -> fetchOllamaModels(settings);
         case OPENAI -> List.of(); // o podrías llamar a un método fetchOpenAiModels si lo implementas
      };
   }

   private static String requestGeminiJson(ClientSettingsManager settings, String systemPrompt, String userPrompt, JsonObject schema, int maxTokens) throws IOException, InterruptedException {
      JsonObject requestBody = new JsonObject();
      JsonObject systemInstruction = new JsonObject();
      JsonArray systemParts = new JsonArray();
      JsonObject systemText = new JsonObject();
      systemText.addProperty("text", systemPrompt + "\n" + jsonOnlyInstruction(schema));
      systemParts.add(systemText);
      systemInstruction.add("parts", systemParts);
      requestBody.add("systemInstruction", systemInstruction);
      JsonArray contents = new JsonArray();
      JsonObject userContent = new JsonObject();
      userContent.addProperty("role", "user");
      JsonArray userParts = new JsonArray();
      JsonObject userText = new JsonObject();
      userText.addProperty("text", userPrompt);
      userParts.add(userText);
      userContent.add("parts", userParts);
      contents.add(userContent);
      requestBody.add("contents", contents);
      JsonObject generationConfig = new JsonObject();
      generationConfig.addProperty("responseMimeType", "application/json");
      generationConfig.addProperty("maxOutputTokens", maxTokens);
      requestBody.add("generationConfig", generationConfig);
      String modelPath = settings.getModelId().startsWith("models/") ? settings.getModelId() : "models/" + settings.getModelId();
      String endpoint = "https://generativelanguage.googleapis.com/v1beta/"
              + modelPath
              + ":generateContent?key="
              + URLEncoder.encode(settings.getActiveApiKey(), StandardCharsets.UTF_8);
      HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
              .timeout(Duration.ofSeconds(settings.getTimeoutSeconds()))
              .header("Content-Type", "application/json")
              .POST(BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
              .build();
      JsonObject responseJson = sendJson(request);
      JsonArray candidates = responseJson.getAsJsonArray("candidates");
      if (candidates != null && candidates.size() != 0) {
         JsonObject candidate = candidates.get(0).getAsJsonObject();
         JsonObject content = candidate.getAsJsonObject("content");
         if (content == null) {
            throw new IOException("Gemini returned no content");
         }

         JsonArray parts = content.getAsJsonArray("parts");
         if (parts != null && parts.size() != 0) {
            StringBuilder builder = new StringBuilder();

            for (JsonElement part : parts) {
               JsonObject partObject = part.getAsJsonObject();
               JsonElement text = partObject.get("text");
               if (text != null && text.isJsonPrimitive()) {
                  builder.append(text.getAsString());
               }
            }

            String raw = builder.toString().trim();
            if (raw.isBlank()) {
               throw new IOException("Gemini returned empty JSON");
            } else {
               return normalizeJsonPayload(raw);
            }
         } else {
            throw new IOException("Gemini returned no text parts");
         }
      } else {
         throw new IOException("Gemini returned no candidates");
      }
   }

   private static String requestOpenRouterJson(
           ClientSettingsManager settings, String systemPrompt, String userPrompt, JsonObject schema, String schemaName, int maxTokens
   ) throws IOException, InterruptedException {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("model", settings.getModelId());
      requestBody.addProperty("max_tokens", maxTokens);
      JsonArray messages = new JsonArray();
      messages.add(chatMessage("system", systemPrompt + "\n" + jsonOnlyInstruction(schema)));
      messages.add(chatMessage("user", userPrompt));
      requestBody.add("messages", messages);
      HttpRequest request = HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/chat/completions"))
              .timeout(Duration.ofSeconds(settings.getTimeoutSeconds()))
              .header("Authorization", "Bearer " + settings.getActiveApiKey())
              .header("Content-Type", "application/json")
              .header("HTTP-Referer", "https://modrinth.com/")
              .header("X-Title", "AIGF Companion")
              .POST(BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
              .build();
      JsonObject responseJson = sendJson(request);
      JsonArray choices = responseJson.getAsJsonArray("choices");
      if (choices != null && choices.size() != 0) {
         JsonObject choice = choices.get(0).getAsJsonObject();
         JsonObject message = choice.getAsJsonObject("message");
         if (message == null) {
            throw new IOException("OpenRouter returned no message");
         } else {
            JsonElement content = message.get("content");
            if (content != null && content.isJsonPrimitive()) {
               return normalizeJsonPayload(content.getAsString());
            } else {
               throw new IOException("OpenRouter returned empty content");
            }
         }
      } else {
         throw new IOException("OpenRouter returned no choices");
      }
   }

   private static String requestOllamaJson(ClientSettingsManager settings, String systemPrompt, String userPrompt, JsonObject schema, int maxTokens) throws IOException, InterruptedException {
       JsonObject requestBody = new JsonObject();
       requestBody.addProperty("model", settings.getModelId());
       JsonArray messages = new JsonArray();
       messages.add(chatMessage("system", systemPrompt + "\n" + jsonOnlyInstruction(schema)));
       messages.add(chatMessage("user", userPrompt));
       requestBody.add("messages", messages);
       requestBody.addProperty("format", "json");
       requestBody.addProperty("stream", false);
       HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:11434/api/chat"))
               .timeout(Duration.ofSeconds(settings.getTimeoutSeconds()))
               .header("Content-Type", "application/json")
               .POST(BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
               .build();
       JsonObject responseJson = sendJson(request);

       // Extraer el contenido de la respuesta
      JsonObject message = responseJson.getAsJsonObject("message");
      if (message == null) {
         throw new IOException("Ollama: no se encontró el campo 'message' en la respuesta");
      }
      JsonElement contentElem = message.get("content");
      if (contentElem == null || !contentElem.isJsonPrimitive()) {
         throw new IOException("Ollama: el contenido no es un string o está vacío");
      }
      String rawContent = contentElem.getAsString();
      System.out.println("=== Ollama final rawContent: " + rawContent);
      if (rawContent.isBlank()) {
         throw new IOException("Ollama: contenido vacío");
      }

       // Verificar que rawContent sea un JSON válido (opcional, pero ayuda a depurar)
       try {
           JsonParser.parseString(rawContent).getAsJsonObject();
       } catch (Exception e) {
           // Si no es JSON válido, intentamos extraer el primer objeto JSON encontrado
           int firstBrace = rawContent.indexOf('{');
           int lastBrace = rawContent.lastIndexOf('}');
           if (firstBrace >= 0 && lastBrace > firstBrace) {
               rawContent = rawContent.substring(firstBrace, lastBrace + 1);
               // Validamos de nuevo
               JsonParser.parseString(rawContent).getAsJsonObject();
           } else {
               throw new IOException("Ollama: la respuesta no es un JSON válido: " + rawContent);
           }
       }

       // Devolvemos el string JSON directamente, sin normalizar (ya es válido)
       return rawContent;
   }

   private static List<String> fetchOllamaModels(ClientSettingsManager settings) throws IOException, InterruptedException {
      HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:11434/api/tags"))
              .timeout(Duration.ofSeconds(settings.getTimeoutSeconds()))
              .GET()
              .build();
      JsonObject responseJson = sendJson(request);
      JsonArray modelsArray = responseJson.getAsJsonArray("models");
      List<String> ids = new ArrayList<>();
      if (modelsArray != null) {
         for (JsonElement item : modelsArray) {
            JsonObject obj = item.getAsJsonObject();
            JsonElement nameElement = obj.get("name");
            if (nameElement != null && nameElement.isJsonPrimitive()) {
               ids.add(nameElement.getAsString());
            }
         }
      }

      return ids;
   }

   private static List<String> fetchGeminiModels(ClientSettingsManager settings) throws IOException, InterruptedException {
      String var10000 = settings.getActiveApiKey();
      String endpoint = "https://generativelanguage.googleapis.com/v1beta/models?key=" + URLEncoder.encode(var10000, StandardCharsets.UTF_8);
      HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(settings.getTimeoutSeconds())).GET().build();
      JsonObject responseJson = sendJson(request);
      JsonArray models = responseJson.getAsJsonArray("models");
      List<String> ids = new ArrayList<>();
      if (models == null) {
         return ids;
      }

      for (JsonElement modelElement : models) {
         JsonObject model = modelElement.getAsJsonObject();
         if (supportsGenerateContent(model.getAsJsonArray("supportedGenerationMethods"))) {
            JsonElement name = model.get("name");
            if (name != null && name.isJsonPrimitive()) {
               ids.add(name.getAsString().replace("models/", ""));
            }
         }
      }

      return ids;
   }

   private static List<String> fetchOpenRouterModels(ClientSettingsManager settings) throws IOException, InterruptedException {
      Builder builder = HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/models"))
              .timeout(Duration.ofSeconds(settings.getTimeoutSeconds()))
              .GET();
      if (!settings.getActiveApiKey().isBlank()) {
         builder.header("Authorization", "Bearer " + settings.getActiveApiKey());
      }

      JsonObject responseJson = sendJson(builder.build());
      return parseModelIds(responseJson.getAsJsonArray("data"), "id", (String)null);
   }

   private static JsonObject sendJson(HttpRequest request) throws IOException, InterruptedException {
      HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
         int var10002 = response.statusCode();
         throw new IOException("HTTP " + var10002 + ": " + response.body());
      } else {
         return JsonParser.parseString(response.body()).getAsJsonObject();
      }
   }

   private static JsonObject chatMessage(String role, String content) {
      JsonObject message = new JsonObject();
      message.addProperty("role", role);
      message.addProperty("content", content);
      return message;
   }

   private static String jsonOnlyInstruction(JsonObject schema) {
      return "Return only valid JSON without markdown, code fences, or extra commentary. Match this schema exactly: " + schema;
   }

   private static String normalizeJsonPayload(String raw) throws IOException {
      String trimmed = raw.trim();
      if (trimmed.startsWith("```")) {
         trimmed = trimmed.replace("```json", "").replace("```JSON", "").replace("```", "").trim();
      }

      int firstBrace = trimmed.indexOf(123);
      int lastBrace = trimmed.lastIndexOf(125);
      if (firstBrace >= 0 && lastBrace > firstBrace) {
         trimmed = trimmed.substring(firstBrace, lastBrace + 1);
      }

      try {
         JsonParser.parseString(trimmed).getAsJsonObject();
         return trimmed;
      } catch (Exception var5) {
         throw new IOException("Model did not return valid JSON: " + raw);
      }
   }

   private static boolean supportsGenerateContent(JsonArray methods) {
      if (methods == null) {
         return false;
      }

      for (JsonElement method : methods) {
         if (method.isJsonPrimitive() && "generateContent".equalsIgnoreCase(method.getAsString())) {
            return true;
         }
      }

      return false;
   }

   private static List<String> parseModelIds(JsonArray items, String propertyName, String requiredPrefix) {
      List<String> ids = new ArrayList<>();
      if (items == null) {
         return ids;
      }

      for (JsonElement item : items) {
         JsonObject object = item.getAsJsonObject();
         JsonElement idElement = object.get(propertyName);
         if (idElement != null && idElement.isJsonPrimitive()) {
            String id = idElement.getAsString();
            if (requiredPrefix == null || id.startsWith(requiredPrefix)) {
               ids.add(id);
            }
         }
      }

      return ids;
   }
}
