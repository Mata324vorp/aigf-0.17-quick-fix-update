package com.litewer.aigf.conversation;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ConversationMoodAnalyzer {
   private static final List<String> POSITIVE_WORDS = List.of(
      "спасибо",
      "умница",
      "умничка",
      "молодец",
      "милая",
      "классная",
      "красивая",
      "люблю",
      "обожаю",
      "ты лучшая",
      "солнышко",
      "хорошая",
      "добрая",
      "good",
      "thanks",
      "smart",
      "sweet",
      "love you",
      "best girl"
   );
   private static final List<String> NEGATIVE_PHRASES = List.of(
      "дура",
      "дура тупая",
      "тупая",
      "тупица",
      "идиотка",
      "бесполезная",
      "ненавижу",
      "заткнись",
      "отвали",
      "мерзкая",
      "тварь",
      "сука",
      "блядь",
      "пошла",
      "мразь",
      "мраз",
      "шлюха",
      "шлюх",
      "чмо",
      "манда",
      "пидор",
      "соси",
      "stupid",
      "useless",
      "hate you",
      "shut up",
      "annoying",
      "idiot"
   );
   private static final List<String> COLLAB_WORDS = List.of(
      "давай", "вместе", "обсудим", "помоги", "как думаешь", "посоветуй", "разберем", "разберём", "let's", "together", "help me", "what do you think", "plan"
   );
   private static final List<String> APOLOGY_WORDS = List.of("прости", "извини", "сорри", "sorry", "my bad");
   private static final List<Pattern> HEAVY_PROFANITY_PATTERNS = List.of(
      pattern("(?iu)(^|[^\\p{L}])(ёб|ебан|ебл|ебош|уеб|наеб|заеб|отъеб|разъеб|проеб)([^\\p{L}]|$)"),
      pattern("(?iu)(^|[^\\p{L}])(пизд|хуй|нахуй|долбоеб|долбаеб)([^\\p{L}]|$)")
   );
   private static final List<Pattern> HARSH_NEGATIVE_PATTERNS = List.of(
      pattern("(?iu)(^|[^\\p{L}])(патл)([^\\p{L}]|$)"), pattern("(?iu)(^|[^\\p{L}])(уеби)([^\\p{L}]|$)")
   );

   private ConversationMoodAnalyzer() {
   }

   public static ConversationMoodAnalyzer.Analysis analyze(String userText) {
      String normalized = normalize(userText);
      boolean harshNegative = containsHarshNegative(normalized);
      boolean positive = containsAny(normalized, POSITIVE_WORDS);
      boolean collaborative = containsAny(normalized, COLLAB_WORDS);
      boolean apology = containsAny(normalized, APOLOGY_WORDS);
      int moodDelta = 0;
      int trustDelta = 0;
      String summary = "";
      if (harshNegative) {
         moodDelta -= 22;
         trustDelta -= 15;
         summary = "hurt_by_words";
         if (apology) {
            moodDelta += 4;
            trustDelta += 4;
            summary = summary + ",apology";
         }
      } else {
         if (positive) {
            moodDelta += 7;
            trustDelta += 5;
            summary = "praised";
         }

         if (collaborative) {
            moodDelta += 3;
            trustDelta += 4;
            summary = summary.isEmpty() ? "shared_discussion" : summary + ",shared_discussion";
         }

         if (apology) {
            moodDelta += 5;
            trustDelta += 5;
            summary = summary.isEmpty() ? "apology" : summary + ",apology";
         }
      }

      moodDelta = Math.max(-24, Math.min(15, moodDelta));
      trustDelta = Math.max(-20, Math.min(12, trustDelta));
      return new ConversationMoodAnalyzer.Analysis(moodDelta, trustDelta, summary);
   }

   public static boolean containsHarshNegative(String userText) {
      String normalized = normalize(userText);
      return containsAny(normalized, NEGATIVE_PHRASES) || matchesAny(normalized, HEAVY_PROFANITY_PATTERNS) || matchesAny(normalized, HARSH_NEGATIVE_PATTERNS);
   }

   public static boolean containsHeavyProfanity(String userText) {
      return matchesAny(normalize(userText), HEAVY_PROFANITY_PATTERNS);
   }

   private static String normalize(String userText) {
      return userText == null ? "" : userText.toLowerCase(Locale.ROOT);
   }

   private static boolean containsAny(String normalized, List<String> candidates) {
      for (String candidate : candidates) {
         if (normalized.contains(candidate)) {
            return true;
         }
      }

      return false;
   }

   private static boolean matchesAny(String normalized, List<Pattern> patterns) {
      for (Pattern pattern : patterns) {
         if (pattern.matcher(normalized).find()) {
            return true;
         }
      }

      return false;
   }

   private static Pattern pattern(String regex) {
      return Pattern.compile(regex);
   }

   public record Analysis(int moodDelta, int trustDelta, String summary) {
      public boolean isHarshNegative() {
         return this.moodDelta <= -12 || this.trustDelta <= -10;
      }

      public boolean hasNegative() {
         return this.summary.contains("hurt_by_words");
      }

      public boolean isApology() {
         return this.summary.contains("apology");
      }

      public boolean isPositive() {
         return this.summary.contains("praised");
      }

      public boolean isCollaborative() {
         return this.summary.contains("shared_discussion");
      }
   }
}
