package com.litewer.aigf.entity;

public enum CompanionCommandMode {
   FOLLOW,
   STAY,
   SIT,
   HOME;

   public static CompanionCommandMode fromOrdinal(int ordinal) {
      CompanionCommandMode[] values = values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FOLLOW;
   }
}
