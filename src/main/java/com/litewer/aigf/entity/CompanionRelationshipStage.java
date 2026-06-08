package com.litewer.aigf.entity;

public enum CompanionRelationshipStage {
   COLD,
   NEUTRAL,
   WARM,
   ATTACHED;

   public static CompanionRelationshipStage fromName(String value) {
      for (CompanionRelationshipStage stage : values()) {
         if (stage.name().equalsIgnoreCase(value)) {
            return stage;
         }
      }

      return NEUTRAL;
   }

   public static CompanionRelationshipStage fromValues(int trust, int mood, int resentment) {
      if (trust >= 78 && mood >= 68 && resentment <= 10) {
         return ATTACHED;
      } else if (trust >= 56 && mood >= 44 && resentment <= 28) {
         return WARM;
      } else {
         return trust > 28 && mood > 22 && resentment < 52 ? NEUTRAL : COLD;
      }
   }
}
