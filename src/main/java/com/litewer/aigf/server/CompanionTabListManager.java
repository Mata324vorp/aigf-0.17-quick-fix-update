package com.litewer.aigf.server;

import com.litewer.aigf.entity.CompanionEntity;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public final class CompanionTabListManager {
   private static final Map<UUID, String> ACTIVE_NAMES = new ConcurrentHashMap<>();

   private CompanionTabListManager() {
   }

   public static void syncForOwner(ServerPlayer owner, CompanionEntity companion) {
      if (owner.f_8906_ != null) {
         UUID ownerUuid = owner.m_20148_();
         UUID tabUuid = tabUuid(ownerUuid);
         String displayName = companion.getCompanionName();
         String previousName = ACTIVE_NAMES.get(ownerUuid);
         if (!displayName.equals(previousName)) {
            if (previousName != null) {
               owner.f_8906_.m_9829_(new ClientboundPlayerInfoRemovePacket(List.of(tabUuid)));
            }

            CompanionTabListManager.CompanionTabEntryPlayer fakePlayer = new CompanionTabListManager.CompanionTabEntryPlayer(
               owner.f_8924_, owner.m_284548_(), new GameProfile(tabUuid, profileNameFor(ownerUuid)), Component.m_237113_(displayName)
            );
            fakePlayer.f_8943_ = 0;
            fakePlayer.m_143403_(GameType.SURVIVAL);
            owner.f_8906_
               .m_9829_(
                  new ClientboundPlayerInfoUpdatePacket(
                     EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_DISPLAY_NAME, Action.UPDATE_GAME_MODE, Action.UPDATE_LATENCY, Action.UPDATE_LISTED),
                     List.of(fakePlayer)
                  )
               );
            ACTIVE_NAMES.put(ownerUuid, displayName);
         }
      }
   }

   public static void removeForOwner(ServerPlayer owner) {
      if (owner != null && owner.f_8906_ != null) {
         UUID ownerUuid = owner.m_20148_();
         ACTIVE_NAMES.remove(ownerUuid);
         owner.f_8906_.m_9829_(new ClientboundPlayerInfoRemovePacket(List.of(tabUuid(ownerUuid))));
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

      public Component m_8957_() {
         return this.displayName;
      }

      public boolean m_184128_() {
         return true;
      }
   }
}
