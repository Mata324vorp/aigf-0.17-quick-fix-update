package com.litewer.aigf.server;

import com.litewer.aigf.entity.CompanionEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CompanionTabListManager {
   private static final Map<UUID, String> ACTIVE_NAMES = new ConcurrentHashMap<>();

   private CompanionTabListManager() {}

   public static void syncForOwner(ServerPlayer owner, CompanionEntity companion) {
      if (owner.connection != null) {
         UUID ownerUuid = owner.getUUID();
         UUID tabUuid = tabUuid(ownerUuid);
         String displayName = companion.getCompanionName();
         String previousName = ACTIVE_NAMES.get(ownerUuid);
         if (!displayName.equals(previousName)) {
            if (previousName != null) {
               owner.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(tabUuid)));
            }
            CompanionTabEntryPlayer fakePlayer = new CompanionTabEntryPlayer(
                    owner.server, owner.serverLevel(),
                    new GameProfile(tabUuid, profileNameFor(ownerUuid)),
                    Component.literal(displayName)
            );
            fakePlayer.latency = 0;
            fakePlayer.setGameMode(GameType.SURVIVAL);
            owner.connection.send(new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(
                            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED
                    ),
                    List.of(fakePlayer)
            ));
            ACTIVE_NAMES.put(ownerUuid, displayName);
         }
      }
   }

   public static void removeForOwner(ServerPlayer owner) {
      if (owner != null && owner.connection != null) {
         UUID ownerUuid = owner.getUUID();
         ACTIVE_NAMES.remove(ownerUuid);
         owner.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(tabUuid(ownerUuid))));
      }
   }

   public static void clear(UUID ownerUuid) {
      if (ownerUuid != null) {
         ACTIVE_NAMES.remove(ownerUuid);
      }
   }

   private static UUID tabUuid(UUID ownerUuid) {
      return UUID.nameUUIDFromBytes(("aigf-tab:" + ownerUuid).getBytes(StandardCharsets.UTF_8));
   }

   private static String profileNameFor(UUID ownerUuid) {
      return "aigf" + ownerUuid.toString().replace("-", "").substring(0, 12);
   }

   private static final class CompanionTabEntryPlayer extends ServerPlayer {
      private final Component displayName;

      private CompanionTabEntryPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, Component displayName) {
         super(server, level, profile);
         this.displayName = displayName;
      }

      @Override
      public Component getDisplayName() {
         return this.displayName;
      }

      @Override
      public boolean shouldShowName() {
         return true;
      }
   }
}