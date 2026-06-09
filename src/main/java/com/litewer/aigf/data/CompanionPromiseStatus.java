package com.litewer.aigf.data;

public enum CompanionPromiseStatus {
   PENDING,
   KEPT,
   BROKEN;

   public static CompanionPromiseStatus fromName(String name) {
      for (CompanionPromiseStatus value : values()) {
         if (value.name().equalsIgnoreCase(name)) {
            return value;
         }
      }

      return PENDING;
   }
}
