package com.litewer.aigf.conversation;

import com.litewer.aigf.data.CompanionPromiseCategory;
import java.util.Locale;

public final class PromiseAnalyzer {
   private PromiseAnalyzer() {
   }

   public static PromiseAnalyzer.PromiseSeed extract(String userText, long worldTime) {
      String raw = userText == null ? "" : userText.trim();
      if (raw.isBlank()) {
         return null;
      }

      String normalized = raw.toLowerCase(Locale.ROOT);
      if (!looksLikePromise(normalized)) {
         return null;
      }

      CompanionPromiseCategory category = detectCategory(normalized);
      String summary = cleanupSummary(raw, category);
      long dueWorldTime = worldTime + dueOffset(category);
      return new PromiseAnalyzer.PromiseSeed(summary, category, dueWorldTime);
   }

   public static boolean looksLikePromise(String normalized) {
      return normalized.contains("обещаю")
         || normalized.contains("клянусь")
         || normalized.contains("i promise")
         || normalized.contains("promise ")
         || normalized.contains("я принесу")
         || normalized.contains("я подарю")
         || normalized.contains("я построю")
         || normalized.contains("я сделаю")
         || normalized.contains("я отведу")
         || normalized.contains("я задам")
         || normalized.contains("я позову")
         || normalized.contains("буду с тобой")
         || normalized.contains("не буду груб")
         || normalized.contains("буду говорить нормально")
         || normalized.contains("i will")
         || normalized.contains("i'll")
         || normalized.contains("later i")
         || normalized.contains("soon i");
   }

   private static CompanionPromiseCategory detectCategory(String normalized) {
      if (containsAny(normalized, "подар", "цвет", "принесу", "give you", "bring you", "gift")) {
         return CompanionPromiseCategory.GIFT;
      } else if (containsAny(normalized, "дом", "home", "bed", "кроват", "отведу тебя", "позову домой", "set your home", "take you home")) {
         return CompanionPromiseCategory.HOME;
      } else if (containsAny(normalized, "постро", "комнат", "угол", "место", "decorate", "build", "room", "corner")) {
         return CompanionPromiseCategory.BUILD;
      } else if (containsAny(normalized, "извин", "не буду груб", "буду мягче", "буду добр", "be nicer", "be gentle", "won't insult", "respectful")) {
         return CompanionPromiseCategory.CARE;
      } else {
         return containsAny(normalized, "поговор", "обсуд", "вернусь", "позже", "later", "talk", "discuss", "come back")
            ? CompanionPromiseCategory.TALK
            : CompanionPromiseCategory.GENERAL;
      }
   }

   private static String cleanupSummary(String raw, CompanionPromiseCategory category) {
      String summary = raw.replaceFirst("(?iu)^\\s*@aigf\\s*", "")
         .replaceFirst("(?iu)\\b(обещаю|клянусь|i promise|promise)\\b", "")
         .replaceFirst("(?iu)^\\s*(я\\s+|i\\s+will\\s+|i'll\\s+)", "")
         .trim();
      summary = summary.replaceAll("\\s+", " ").trim();
      if (summary.endsWith(".") || summary.endsWith("!") || summary.endsWith("?")) {
         summary = summary.substring(0, summary.length() - 1).trim();
      }

      if (summary.length() > 72) {
         summary = summary.substring(0, 72).trim();
      }

      if (!summary.isBlank()) {
         return summary;
      }

      return switch (category) {
         case GIFT -> "bring you a gift";
         case HOME -> "take you home";
         case BUILD -> "build you a place";
         case TALK -> "come back and talk calmly";
         case CARE -> "treat you more gently";
         case GENERAL -> "do what I promised";
      };
   }

   private static long dueOffset(CompanionPromiseCategory category) {
      return switch (category) {
         case GIFT, HOME, GENERAL -> 24000L;
         case BUILD -> 36000L;
         case TALK, CARE -> 12000L;
      };
   }

   private static boolean containsAny(String normalized, String... values) {
      for (String value : values) {
         if (normalized.contains(value)) {
            return true;
         }
      }

      return false;
   }

   public record PromiseSeed(String summary, CompanionPromiseCategory category, long dueWorldTime) {
   }
}
