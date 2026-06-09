package com.litewer.aigf.entity;

public enum CompanionEmotion {
   NEUTRAL,
   HAPPY,
   SHY,
   TIRED,
   SAD;

   public static CompanionEmotion fromName(String value) {
      for (CompanionEmotion emotion : values()) {
         if (emotion.name().equalsIgnoreCase(value)) {
            return emotion;
         }
      }

      return NEUTRAL;
   }

   public static CompanionEmotion fromOrdinal(int ordinal) {
      CompanionEmotion[] values = values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NEUTRAL;
   }
}
