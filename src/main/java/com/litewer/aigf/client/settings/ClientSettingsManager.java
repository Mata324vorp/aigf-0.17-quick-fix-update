package com.litewer.aigf.client.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ClientSettingsManager {
   private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("aigf-client.toml");
   private static final Map<AiProvider, List<String>> MODEL_PRESETS = createModelPresets();
   private static ClientSettingsManager instance;
   private AiProvider provider = AiProvider.OPENAI;
   private String openaiApiKey = "";
   private String geminiApiKey = "";
   private String openrouterApiKey = "";
   private String modelId = defaultModelFor(AiProvider.OPENAI);
   private int timeoutSeconds = 45;
   private int maxContextTurns = 10;

   private ClientSettingsManager() {}

   public static synchronized ClientSettingsManager get() {
      if (instance == null) {
         instance = new ClientSettingsManager();
         instance.load();
      }
      return instance;
   }

   public synchronized void load() {
      if (!Files.exists(CONFIG_PATH)) {
         this.save();
      } else {
         try {
            for (String line : Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8)) {
               String trimmed = line.trim();
               if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                  int separator = trimmed.indexOf(61);
                  if (separator > 0) {
                     String key = trimmed.substring(0, separator).trim();
                     String rawValue = trimmed.substring(separator + 1).trim();
                     switch (key) {
                        case "provider":
                           this.provider = AiProvider.fromName(unquote(rawValue));
                           break;
                        case "openaiApiKey":
                           this.openaiApiKey = unquote(rawValue);
                           break;
                        case "geminiApiKey":
                           this.geminiApiKey = unquote(rawValue);
                           break;
                        case "openrouterApiKey":
                           this.openrouterApiKey = unquote(rawValue);
                           break;
                        case "modelId":
                           this.modelId = normalizeModelId(unquote(rawValue));
                           break;
                        case "timeoutSeconds":
                           this.timeoutSeconds = parseInt(rawValue, 45, 5, 120);
                           break;
                        case "maxContextTurns":
                           this.maxContextTurns = parseInt(rawValue, 10, 2, 20);
                           break;
                     }
                  }
               }
            }
         } catch (IOException ignored) {}
         if (this.modelId.isBlank()) {
            this.modelId = defaultModelFor(this.provider);
         }
      }
   }

   public synchronized void save() {
      List<String> lines = new ArrayList<>();
      lines.add("# AIGF client settings");
      lines.add("provider = \"" + this.provider.name() + "\"");
      lines.add("openaiApiKey = \"" + escape(this.openaiApiKey) + "\"");
      lines.add("geminiApiKey = \"" + escape(this.geminiApiKey) + "\"");
      lines.add("openrouterApiKey = \"" + escape(this.openrouterApiKey) + "\"");
      lines.add("modelId = \"" + escape(this.modelId) + "\"");
      lines.add("timeoutSeconds = " + this.timeoutSeconds);
      lines.add("maxContextTurns = " + this.maxContextTurns);
      try {
         Files.write(CONFIG_PATH, lines, StandardCharsets.UTF_8);
      } catch (IOException ignored) {}
   }

   public synchronized AiProvider getProvider() {
      return AiProvider.OLLAMA;  // Forzar Ollama
   }
   public synchronized void setProvider(AiProvider provider) {
      this.provider = provider == null ? AiProvider.OPENAI : provider;
      if (this.modelId.isBlank() || !getModelPresets(this.provider).contains(this.modelId)) {
         this.modelId = defaultModelFor(this.provider);
      }
   }

   public synchronized AiProvider cycleProvider(int direction) {
      AiProvider[] providers = AiProvider.values();
      int currentIndex = this.provider.ordinal();
      currentIndex = Math.floorMod(currentIndex + direction, providers.length);
      this.setProvider(providers[currentIndex]);
      return this.provider;
   }

   public synchronized String getOpenaiApiKey() { return this.openaiApiKey; }
   public synchronized void setOpenaiApiKey(String openaiApiKey) { this.openaiApiKey = sanitizeApiKey(openaiApiKey); }
   public synchronized String getGeminiApiKey() { return this.geminiApiKey; }
   public synchronized void setGeminiApiKey(String geminiApiKey) { this.geminiApiKey = sanitizeApiKey(geminiApiKey); }
   public synchronized String getOpenrouterApiKey() { return this.openrouterApiKey; }
   public synchronized void setOpenrouterApiKey(String openrouterApiKey) { this.openrouterApiKey = sanitizeApiKey(openrouterApiKey); }

   public synchronized String getApiKey(AiProvider provider) {
      return switch (provider == null ? AiProvider.OPENAI : provider) {
         case GEMINI -> this.geminiApiKey;
         case OPENROUTER -> this.openrouterApiKey;
         case OPENAI -> this.openaiApiKey;
         case OLLAMA -> "";   // Ollama no necesita API key
      };
   }

   public synchronized void setApiKey(AiProvider provider, String apiKey) {
      switch (provider) {
         case GEMINI -> this.geminiApiKey = apiKey;
         case OPENROUTER -> this.openrouterApiKey = apiKey;
         case OPENAI -> this.openaiApiKey = apiKey;
         case OLLAMA -> { /* Ignorar, no se necesita */ }
      }
   }

   public synchronized String getActiveApiKey() { return this.getApiKey(this.provider); }

   public synchronized String getModelId() { return this.modelId; }
   public synchronized void setModelId(String modelId) { this.modelId = normalizeModelId(modelId); }
   public synchronized int getTimeoutSeconds() { return this.timeoutSeconds; }
   public synchronized void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = Math.max(5, Math.min(timeoutSeconds, 120)); }
   public synchronized int getMaxContextTurns() { return this.maxContextTurns; }
   public synchronized void setMaxContextTurns(int maxContextTurns) { this.maxContextTurns = Math.max(2, Math.min(maxContextTurns, 20)); }

   public static List<String> getModelPresets() { return getModelPresets(get().getProvider()); }

   public static List<String> getModelPresets(AiProvider provider) {
      return MODEL_PRESETS.getOrDefault(provider == null ? AiProvider.OPENAI : provider, MODEL_PRESETS.get(AiProvider.OPENAI));
   }

   public synchronized String cycleModel(int direction) {
      List<String> presets = getModelPresets(this.provider);
      int currentIndex = presets.indexOf(this.modelId);
      if (currentIndex < 0) currentIndex = 0;
      currentIndex = Math.floorMod(currentIndex + direction, presets.size());
      this.modelId = presets.get(currentIndex);
      return this.modelId;
   }

   public static String defaultModelFor(AiProvider provider) {
      List<String> presets = getModelPresets(provider);
      return presets.isEmpty() ? "gpt-4o" : presets.get(0);
   }

   private static String sanitizeApiKey(String value) { return value == null ? "" : value.trim(); }
   private static String normalizeModelId(String modelId) {
      return modelId != null && !modelId.isBlank() ? modelId.trim() : defaultModelFor(getSafeProvider());
   }
   private static AiProvider getSafeProvider() { return instance == null ? AiProvider.OPENAI : instance.provider; }
   private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
   private static String unquote(String value) {
      if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
         value = value.substring(1, value.length() - 1);
      }
      return value.replace("\\\"", "\"").replace("\\\\", "\\");
   }
   private static int parseInt(String value, int defaultValue, int min, int max) {
      try {
         return Math.max(min, Math.min(Integer.parseInt(value.trim()), max));
      } catch (NumberFormatException ignored) { return defaultValue; }
   }

   private static Map<AiProvider, List<String>> createModelPresets() {
      Map<AiProvider, List<String>> presets = new EnumMap<>(AiProvider.class);
      presets.put(AiProvider.OPENAI, List.of(
              "gpt-4o", "chatgpt-4o", "gpt-4.1-mini", "gpt-5", "gpt-5.1", "gpt-5.2",
              "chatgpt-5", "chatgpt-5.1", "chatgpt-5.2", "gpt-5-mini", "gpt-5-nano"
      ));
      presets.put(AiProvider.GEMINI, List.of(
              "gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite",
              "gemini-2.0-flash", "gemini-2.0-flash-lite"
      ));
      presets.put(AiProvider.OPENROUTER, List.of(
              "openai/gpt-4o", "openai/gpt-5", "openai/gpt-5-mini",
              "google/gemini-2.5-pro", "google/gemini-2.5-flash",
              "anthropic/claude-3.7-sonnet", "deepseek/deepseek-chat-v3-0324"
      ));
      // Añadimos presets para Ollama
      presets.put(AiProvider.OLLAMA, List.of(
              "qwen2.5:0.5b", "llama3", "mistral", "phi3", "codellama", "gemma"
      ));
      return presets;
   }

}