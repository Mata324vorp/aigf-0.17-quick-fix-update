package com.litewer.aigf.command;

import com.litewer.aigf.client.ai.OpenAiClient;  // ← NUEVA IMPORTACIÓN
import com.litewer.aigf.data.CompanionPromiseCategory;
import com.litewer.aigf.data.CompanionWorldData;
import com.litewer.aigf.entity.CompanionActionIntent;
import com.litewer.aigf.entity.CompanionCommandMode;
import com.litewer.aigf.entity.CompanionEntity;
import com.litewer.aigf.registry.ModEntities;
import com.litewer.aigf.server.CompanionTabListManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class CompanionCommands {
   private CompanionCommands() {}

   public static void register(RegisterCommandsEvent event) {
      CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
      dispatcher.register(
              Commands.literal("aigf")
                      .then(Commands.literal("spawn").executes(context -> spawnForPlayer(context.getSource().getPlayerOrException())))
                      .then(Commands.literal("recall").executes(context -> recall(context.getSource().getPlayerOrException())))
                      .then(Commands.literal("sethome").executes(context -> setHome(context.getSource().getPlayerOrException())))
                      .then(Commands.literal("home").executes(context -> goHome(context.getSource().getPlayerOrException())))
                      .then(Commands.literal("remove").executes(context -> remove(context.getSource().getPlayerOrException())))
                      .then(Commands.literal("test").executes(context -> testConnection(context.getSource().getPlayerOrException())))  // ← NUEVO
      );
   }

   public static int spawnForPlayer(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      CompanionWorldData data = CompanionWorldData.get(level);
      CompanionWorldData.StoredCompanionState state = data.getOrCreate(player.getUUID());
      CompanionEntity entity = getLoadedCompanion(level, state);
      if (entity == null) {
         entity = ModEntities.COMPANION.get().create(level);
         if (entity == null) return 0;
         entity.setPos(player.getX() + 1.0, player.getY(), player.getZ() + 1.0);
         entity.setYRot(player.getYRot());
         entity.setOwnerUuid(player.getUUID());
         entity.applyStoredState(state);
         entity.setCommandMode(CompanionCommandMode.FOLLOW);
         entity.triggerGreeting();
         level.addFreshEntity(entity);
         state.companionUuid = entity.getUUID();
         data.setDirty();
      } else {
         entity.teleportTo(player.getX() + 1.0, player.getY(), player.getZ() + 1.0);
         entity.setCommandMode(CompanionCommandMode.FOLLOW);
         entity.triggerGreeting();
      }
      entity.persistState(true);
      player.sendSystemMessage(Component.translatable("message.aigf.spawned"));
      return 1;
   }

   private static int recall(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      CompanionWorldData data = CompanionWorldData.get(level);
      CompanionWorldData.StoredCompanionState state = data.getOrCreate(player.getUUID());
      CompanionEntity entity = getLoadedCompanion(level, state);
      if (entity == null) {
         return spawnForPlayer(player);
      }
      entity.teleportTo(player.getX() + 1.0, player.getY(), player.getZ() + 1.0);
      entity.setCommandMode(CompanionCommandMode.FOLLOW);
      entity.triggerGreeting();
      entity.persistState(true);
      player.sendSystemMessage(Component.translatable("message.aigf.recalled"));
      return 1;
   }

   private static int remove(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      CompanionWorldData data = CompanionWorldData.get(level);
      CompanionWorldData.StoredCompanionState state = data.getOrCreate(player.getUUID());
      CompanionEntity entity = getLoadedCompanion(level, state);
      if (entity != null) {
         entity.discard();
      }
      data.clearCompanion(player.getUUID());
      CompanionTabListManager.removeForOwner(player);
      player.sendSystemMessage(Component.translatable("message.aigf.removed"));
      return 1;
   }

   private static int setHome(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      CompanionWorldData data = CompanionWorldData.get(level);
      CompanionWorldData.StoredCompanionState state = data.getOrCreate(player.getUUID());
      state.setHome(level, player.blockPosition());
      CompanionWorldData.StoredCompanionState.PromiseEffect promiseEffect = state.resolvePromise(level.getGameTime(), CompanionPromiseCategory.HOME);
      state.lastAction = "SET_HOME";
      data.setDirty();
      CompanionEntity entity = getLoadedCompanion(level, state);
      if (entity != null) {
         if (promiseEffect.hasImpact()) {
            entity.setMood(entity.getMood() + promiseEffect.moodDelta());
            entity.setTrust(entity.getTrust() + promiseEffect.trustDelta());
         }
         entity.persistState(true);
      }
      player.sendSystemMessage(Component.literal("AIGF home set at " + state.homeX + ", " + state.homeY + ", " + state.homeZ));
      return 1;
   }

   private static int goHome(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      CompanionWorldData data = CompanionWorldData.get(level);
      CompanionWorldData.StoredCompanionState state = data.getOrCreate(player.getUUID());
      if (!state.hasHome()) {
         player.sendSystemMessage(Component.literal("Set home first with /aigf sethome."));
         return 0;
      }
      CompanionEntity entity = getLoadedCompanion(level, state);
      if (entity == null) {
         player.sendSystemMessage(Component.literal("Your companion is not loaded. Use /aigf spawn first."));
         return 0;
      }
      entity.applyActionIntent(CompanionActionIntent.GO_HOME, player);
      CompanionWorldData.StoredCompanionState.PromiseEffect promiseEffect = state.resolvePromise(level.getGameTime(), CompanionPromiseCategory.HOME);
      if (promiseEffect.hasImpact()) {
         entity.setMood(entity.getMood() + promiseEffect.moodDelta());
         entity.setTrust(entity.getTrust() + promiseEffect.trustDelta());
      }
      entity.persistState(true);
      player.sendSystemMessage(Component.literal("AIGF companion is heading home."));
      return 1;
   }

   private static CompanionEntity getLoadedCompanion(ServerLevel level, CompanionWorldData.StoredCompanionState state) {
      if (state.companionUuid == null) return null;
      return level.getEntity(state.companionUuid) instanceof CompanionEntity companion ? companion : null;
   }

   // ========== NUEVO MÉTODO PARA /aigf test ==========
   private static int testConnection(ServerPlayer player) {
      player.sendSystemMessage(Component.literal("Testing connection to AI provider..."));
      OpenAiClient.testConnection().thenAccept(result -> {
         player.sendSystemMessage(Component.literal(result));
      }).exceptionally(throwable -> {
         player.sendSystemMessage(Component.literal("Error: " + throwable.getMessage()));
         return null;
      });
      return 1;
   }
}