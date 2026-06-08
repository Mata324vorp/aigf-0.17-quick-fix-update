package com.litewer.aigf.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;

public final class ClientLocalization {
   private ClientLocalization() {
   }

   public static boolean isRussian() {
      return languageCode().startsWith("ru");
   }

   public static String text(String english, String russian) {
      return isRussian() ? russian : english;
   }

   private static String languageCode() {
      Minecraft minecraft = Minecraft.getInstance();
      return minecraft != null && minecraft.getLanguageManager() != null && minecraft.getLanguageManager().getSelected() != null
              ? minecraft.getLanguageManager().getSelected().toLowerCase(Locale.ROOT)
              : "en_us";
   }
}
