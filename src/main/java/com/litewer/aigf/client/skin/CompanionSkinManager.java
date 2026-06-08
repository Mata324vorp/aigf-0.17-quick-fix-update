package com.litewer.aigf.client.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.litewer.aigf.client.ClientLocalization;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

public final class CompanionSkinManager {
   private static final Path SKINS_DIR = FMLPaths.CONFIGDIR.get().resolve("aigf").resolve("skins");
   private static final Path IMPORTED_DIR = SKINS_DIR.resolve("imported");
   private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
   private static final ResourceLocation DEFAULT_SKIN = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/slim/alex.png");
   private static final Map<String, ResourceLocation> REGISTERED_TEXTURES = new ConcurrentHashMap<>();
   private static final Set<String> IMPORTS_IN_FLIGHT = ConcurrentHashMap.newKeySet();

   private CompanionSkinManager() {
   }

   public static void initialize() {
      try {
         Files.createDirectories(IMPORTED_DIR);
      } catch (IOException var1) {
      }
   }

   public static List<String> listSkinIds() {
      initialize();
      List<String> ids = new ArrayList<>();
      ids.add("builtin:alex");

      try {
         if (Files.exists(SKINS_DIR)) {
            Files.list(SKINS_DIR)
               .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".png"))
               .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
               .forEach(path -> ids.add("local:" + path.getFileName()));
         }

         if (Files.exists(IMPORTED_DIR)) {
            Files.list(IMPORTED_DIR)
               .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".png"))
               .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
               .forEach(path -> ids.add("imported:" + stripExtension(path.getFileName().toString())));
         }
      } catch (IOException var2) {
      }

      return ids;
   }

   public static ResourceLocation resolveTexture(String skinId) {
      if (skinId != null && !skinId.isBlank() && !"builtin:alex".equalsIgnoreCase(skinId)) {
         ResourceLocation cached = REGISTERED_TEXTURES.get(skinId);
         if (cached != null) {
            return cached;
         }

         Path path = pathForSkinId(skinId);
         if (path != null && Files.exists(path)) {
            ResourceLocation resourceLocation = registerFromFile(skinId, path);
            if (resourceLocation != null) {
               return resourceLocation;
            }
         }

         if (skinId.startsWith("imported:") && IMPORTS_IN_FLIGHT.add(skinId)) {
            importSkinByUsername(skinId.substring("imported:".length())).whenComplete((unused, error) -> IMPORTS_IN_FLIGHT.remove(skinId));
         }

         return DEFAULT_SKIN;
      } else {
         return DEFAULT_SKIN;
      }
   }

   public static CompletableFuture<String> importSkinByUsername(String username) {
      String normalized = sanitizeUsername(username);
      if (normalized.isBlank()) {
         return CompletableFuture.failedFuture(new IllegalArgumentException(t("Username is empty", "Ник пустой")));
      }

      initialize();
      return CompletableFuture.supplyAsync(() -> {
         try {
            String uuid = resolveUuid(normalized);
            String textureUrl = resolveTextureUrl(uuid);
            HttpRequest downloadRequest = HttpRequest.newBuilder(URI.create(textureUrl)).GET().timeout(Duration.ofSeconds(15L)).build();
            byte[] pngBytes = HTTP_CLIENT.send(downloadRequest, BodyHandlers.ofByteArray()).body();
            validatePngBytes(pngBytes);
            Path target = IMPORTED_DIR.resolve(normalized.toLowerCase() + ".png");
            Files.write(target, pngBytes);
            REGISTERED_TEXTURES.remove("imported:" + normalized.toLowerCase());
            return "imported:" + normalized.toLowerCase();
         } catch (Exception exception) {
            throw new RuntimeException(exception);
         }
      });
   }

   public static String validateLocalSkin(String skinId) {
      Path path = pathForSkinId(skinId);
      if (path != null && Files.exists(path)) {
         try {
            validatePngBytes(Files.readAllBytes(path));
            REGISTERED_TEXTURES.remove(skinId);
            return "";
         } catch (IOException exception) {
            return exception.getMessage();
         }
      } else {
         return t("Skin file not found", "Файл скина не найден");
      }
   }

   private static ResourceLocation registerFromFile(String skinId, Path path) {
      try {
         validatePngBytes(Files.readAllBytes(path));

         try (InputStream inputStream = Files.newInputStream(path)) {
            NativeImage image = NativeImage.m_85058_(inputStream);
            ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath("aigf", "skins/" + Integer.toHexString(skinId.hashCode()));
            Minecraft.m_91087_().m_91097_().m_118495_(resourceLocation, new DynamicTexture(image));
            REGISTERED_TEXTURES.put(skinId, resourceLocation);
            return resourceLocation;
         }
      } catch (IOException ignored) {
         return null;
      }
   }

   private static Path pathForSkinId(String skinId) {
      if (skinId == null) {
         return null;
      } else if (skinId.startsWith("local:")) {
         return SKINS_DIR.resolve(skinId.substring("local:".length()));
      } else {
         return skinId.startsWith("imported:") ? IMPORTED_DIR.resolve(skinId.substring("imported:".length()).toLowerCase() + ".png") : null;
      }
   }

   private static void validatePngBytes(byte[] pngBytes) throws IOException {
      NativeImage image = NativeImage.m_85058_(new ByteArrayInputStream(pngBytes));

      try {
         if (image == null || image.m_84982_() != 64 || image.m_85084_() != 64) {
            throw new IOException(t("Expected 64x64 player skin PNG", "Ожидается PNG-скин игрока 64x64"));
         }
      } catch (Throwable var5) {
         if (image != null) {
            try {
               image.close();
            } catch (Throwable var4) {
               var5.addSuppressed(var4);
            }
         }

         throw var5;
      }

      if (image != null) {
         image.close();
      }
   }

   private static String resolveUuid(String username) throws IOException, InterruptedException {
      HttpRequest profileRequest = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
         .GET()
         .timeout(Duration.ofSeconds(10L))
         .build();
      HttpResponse<String> profileResponse = HTTP_CLIENT.send(profileRequest, BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (profileResponse.statusCode() != 200) {
         throw new IOException(t("Nickname not found", "Ник не найден"));
      }

      JsonObject profileJson = JsonParser.parseString(profileResponse.body()).getAsJsonObject();
      return profileJson.get("id").getAsString();
   }

   private static String resolveTextureUrl(String uuid) throws IOException, InterruptedException {
      HttpRequest sessionRequest = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid))
         .GET()
         .timeout(Duration.ofSeconds(10L))
         .build();
      HttpResponse<String> sessionResponse = HTTP_CLIENT.send(sessionRequest, BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (sessionResponse.statusCode() != 200) {
         throw new IOException(t("Unable to fetch skin metadata", "Не удалось получить метаданные скина"));
      }

      JsonObject sessionJson = JsonParser.parseString(sessionResponse.body()).getAsJsonObject();

      for (JsonElement propertyElement : sessionJson.getAsJsonArray("properties")) {
         JsonObject property = propertyElement.getAsJsonObject();
         if ("textures".equals(property.get("name").getAsString())) {
            String decoded = new String(Base64.getDecoder().decode(property.get("value").getAsString()), StandardCharsets.UTF_8);
            JsonObject texturesJson = JsonParser.parseString(decoded).getAsJsonObject();
            return texturesJson.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
         }
      }

      throw new IOException(t("Skin texture not found", "Текстура скина не найдена"));
   }

   private static String sanitizeUsername(String username) {
      String cleaned = username == null ? "" : username.trim().toLowerCase();
      return cleaned.replaceAll("[^a-z0-9_]", "");
   }

   private static String stripExtension(String fileName) {
      int index = fileName.lastIndexOf(46);
      return index > 0 ? fileName.substring(0, index) : fileName;
   }

   private static String t(String english, String russian) {
      return ClientLocalization.text(english, russian);
   }
}
