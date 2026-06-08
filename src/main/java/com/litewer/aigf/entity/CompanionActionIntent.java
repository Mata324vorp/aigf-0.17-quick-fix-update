package com.litewer.aigf.entity;

public enum CompanionActionIntent {
   NOOP,
   FOLLOW,
   STAY,
   GO_HOME,
   SET_HOME,
   COME_HERE,
   SIT,
   STAND,
   LOOK_AT_PLAYER;

   public static CompanionActionIntent fromName(String value) {
      for (CompanionActionIntent intent : values()) {
         if (intent.name().equalsIgnoreCase(value)) {
            return intent;
         }
      }

      return NOOP;
   }
}
