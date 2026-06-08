package com.litewer.aigf.client.settings;

import com.litewer.aigf.client.ClientLocalization;

public enum AiProvider {
   OPENAI("OpenAI", "OpenAI"),
   GEMINI("Google Gemini", "Google Gemini"),
   OPENROUTER("OpenRouter", "OpenRouter"),
   OLLAMA("Ollama", "Ollama");   // <--- NUEVO

   private final String englishName;
   private final String russianName;

   AiProvider(String englishName, String russianName) {
      this.englishName = englishName;
      this.russianName = russianName;
   }

   public static AiProvider fromName(String value) {
      if (value != null && !value.isBlank()) {
         for (AiProvider provider : values()) {
            if (provider.name().equalsIgnoreCase(value) || provider.englishName.equalsIgnoreCase(value)) {
               return provider;
            }
         }
         return OPENAI;
      } else {
         return OPENAI;
      }
   }

   public String displayName() {
      return ClientLocalization.text(this.englishName, this.russianName);
   }
}