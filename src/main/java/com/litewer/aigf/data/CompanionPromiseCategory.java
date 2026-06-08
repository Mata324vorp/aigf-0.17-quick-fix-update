package com.litewer.aigf.data;

public enum CompanionPromiseCategory {
   GIFT,
   HOME,
   BUILD,
   TALK,
   CARE,
   GENERAL;

   public static CompanionPromiseCategory fromName(String name) {
      for (CompanionPromiseCategory value : values()) {
         if (value.name().equalsIgnoreCase(name)) {
            return value;
         }
      }

      return GENERAL;
   }
}
