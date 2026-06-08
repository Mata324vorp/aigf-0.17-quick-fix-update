package com.litewer.aigf.entity;

public enum CompanionConflictState {
   OPEN,
   GUARDED,
   OFFENDED,
   DISTANT;

   public static CompanionConflictState fromName(String value) {
      for (CompanionConflictState state : values()) {
         if (state.name().equalsIgnoreCase(value)) {
            return state;
         }
      }

      return OPEN;
   }

   public static CompanionConflictState fromResentment(int resentment) {
      if (resentment >= 65) {
         return DISTANT;
      } else if (resentment >= 38) {
         return OFFENDED;
      } else {
         return resentment >= 14 ? GUARDED : OPEN;
      }
   }
}
