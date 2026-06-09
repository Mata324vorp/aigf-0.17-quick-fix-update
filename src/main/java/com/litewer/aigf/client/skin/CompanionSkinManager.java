package com.litewer.aigf.client.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.litewer.aigf.client.ClientLocalization;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class CompanionSkinManager {
   private static final Path SKINS_DIR = FMLPaths.CONFIGDIR.get().resolve("aigf").resolve("skins");
   private static final Path IMPORTED_DIR = SKINS_DIR.resolve("imported");
   private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
   private static final ResourceLocation DEFAULT_SKIN = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/slim/alex.png");
   private static final Map<String, ResourceLocation> REGISTERED_TEXTURES = new ConcurrentHashMap<>();
   private static final Set<String> IMPORTS_IN_FLIGHT = ConcurrentHashMap.newKeySet();

   private CompanionSkinManager() {}

   public static void initialize() {
      try {
         Files.createDirectories(IMPORTED_DIR);
      } catch (IOException ignored) {}
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
      } catch (IOException ignored) {}
      return ids;
   }

   public static ResourceLocation resolveTexture(String skinId) {
      if (skinId == null || skinId.isBlank() || "builtin:alex".equalsIgnoreCase(skinId)) {
         return DEFAULT_SKIN;
      }
      ResourceLocation cached = REGISTERED_TEXTURES.get(skinId);
      if (cached != null) {
         return cached;
      }
      Path path = pathForSkinId(skinId);
      if (path != null && Files.exists(path)) {
         ResourceLocation loc = registerFromFile(skinId, path);
         if (loc != null) return loc;
      }
      if (skinId.startsWith("imported:") && IMPORTS_IN_FLIGHT.add(skinId)) {
         importSkinByUsername(skinId.substring("imported:".length()))
                 .whenComplete((unused, error) -> IMPORTS_IN_FLIGHT.remove(skinId));
      }
      return DEFAULT_SKIN;
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
            HttpRequest downloadRequest = HttpRequest.newBuilder(URI.create(textureUrl))
                    .GET().timeout(Duration.ofSeconds(15L)).build();
            byte[] pngBytes = HTTP_CLIENT.send(downloadRequest, HttpResponse.BodyHandlers.ofByteArray()).body();
            validatePngBytes(pngBytes);
            Path target = IMPORTED_DIR.resolve(normalized.toLowerCase() + ".png");
            Files.write(target, pngBytes);
            REGISTERED_TEXTURES.remove("imported:" + normalized.toLowerCase());
            return "imported:" + normalized.toLowerCase();
         } catch (Exception e) {
            throw new RuntimeException(e);
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
         } catch (IOException e) {
            return e.getMessage();
         }
      } else {
         return t("Skin file not found", "Файл скина не найден");
      }
   }

   private static ResourceLocation registerFromFile(String skinId, Path path) {
      try {
         validatePngBytes(Files.readAllBytes(path));
         try (InputStream is = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(is);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("aigf", "skins/" + Integer.toHexString(skinId.hashCode()));
            Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(image));
            REGISTERED_TEXTURES.put(skinId, loc);
            return loc;
         }
      } catch (IOException ignored) {
         return null;
      }
   }

   private static Path pathForSkinId(String skinId) {
      if (skinId == null) return null;
      if (skinId.startsWith("local:")) {
         return SKINS_DIR.resolve(skinId.substring("local:".length()));
      } else if (skinId.startsWith("imported:")) {
         return IMPORTED_DIR.resolve(skinId.substring("imported:".length()).toLowerCase() + ".png");
      }
      return null;
   }

   private static void validatePngBytes(byte[] pngBytes) throws IOException {
      NativeImage image = NativeImage.read(new ByteArrayInputStream(pngBytes));
      try {
         if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
            throw new IOException(t("Expected 64x64 player skin PNG", "Ожидается PNG-скин игрока 64x64"));
         }
      } finally {
         if (image != null) image.close();
      }
   }

   private static String resolveUuid(String username) throws IOException, InterruptedException {
      HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
              .GET().timeout(Duration.ofSeconds(10L)).build();
      HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (resp.statusCode() != 200) throw new IOException(t("Nickname not found", "Ник не найден"));
      JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
      return json.get("id").getAsString();
   }

   private static String resolveTextureUrl(String uuid) throws IOException, InterruptedException {
      HttpRequest req = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid))
              .GET().timeout(Duration.ofSeconds(10L)).build();
      HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (resp.statusCode() != 200) throw new IOException(t("Unable to fetch skin metadata", "Не удалось получить метаданные скина"));
      JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
      for (JsonElement propElem : json.getAsJsonArray("properties")) {
         JsonObject prop = propElem.getAsJsonObject();
         if ("textures".equals(prop.get("name").getAsString())) {
            String decoded = new String(Base64.getDecoder().decode(prop.get("value").getAsString()), StandardCharsets.UTF_8);
            JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject();
            return textures.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
         }
      }
      throw new IOException(t("Skin texture not found", "Текстура скина не найдена"));
   }

   private static String sanitizeUsername(String username) {
      String cleaned = username == null ? "" : username.trim().toLowerCase();
      return cleaned.replaceAll("[^a-z0-9_]", "");
   }

   private static String stripExtension(String fileName) {
      int idx = fileName.lastIndexOf('.');
      return idx > 0 ? fileName.substring(0, idx) : fileName;
   }

   private static String t(String english, String russian) {
      return ClientLocalization.text(english, russian);
   }
}