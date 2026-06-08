package com.litewer.aigf.command;

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
   private CompanionCommands() {
   }

   public static void register(RegisterCommandsEvent event) {
      CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_("aigf")
                        .then(Commands.m_82127_("spawn").executes(context -> spawnForPlayer(((CommandSourceStack)context.getSource()).m_81375_()))))
                     .then(Commands.m_82127_("recall").executes(context -> recall(((CommandSourceStack)context.getSource()).m_81375_()))))
                  .then(Commands.m_82127_("sethome").executes(context -> setHome(((CommandSourceStack)context.getSource()).m_81375_()))))
               .then(Commands.m_82127_("home").executes(context -> goHome(((CommandSourceStack)context.getSource()).m_81375_()))))
            .then(Commands.m_82127_("remove").executes(context -> remove(((CommandSourceStack)context.getSource()).m_81375_())))
      );
   }

   public static int spawnForPlayer(ServerPlayer player) {
      ServerLevel level = player.m_284548_();
      CompanionWorldData data = CompanionWorldData.get(level);
      CompanionWorldData.StoredCompanionState state = data.getOrCreate(player.m_20148_());
      CompanionEntity entity = getLoadedCompanion(level, state);
      if (entity == null) {
         entity = (CompanionEntity)((EntityType)ModEntities.COMPANION.get()).m_20615_(level);
         if (entity == null) {
            return 0;
         }

         entity.m_7678_(player.m_20185_() + 1.0, player.m_20186_(), player.m_20189_() + 1.0, player.m_146908_(), 0.0F);
         entity.setOwnerUuid(player.m_20148_());
         entity.applyStoredState(state);
         entity.setCommandMode(CompanionCommandMode.FOLLOW);
         entity.triggerGreeting();
         level.m_7967_(entity);
         state.companionUuid = entity.m_20148_();
         data.m_77762_();
      } else {
         entity.m_6021_(player.m_20185_() + 1.0, player.m_20186_(), player.m_20189_() + 1.0);
         entity.setCommandMode(CompanionCommandMode.FOLLOW);
         entity.triggerGreeting();
      }

      entity.persistState(true);
      player.m_213846_(Component.m_237115_("message.aigf.spawned"));
      return 1;
   }

   private static int recall(ServerPlayer player) {
      ServerLevel level = player.m_284548_();
      CompanionWorldData data = CompanionWorldData.get(level);
      CompanionWorldData.StoredCompanionState state = data.getOrCreate(player.m_20148_());
      CompanionEntity entity = getLoadedCompanion(level, state);
      if (entity == null) {
         return spawnForPlayer(player);
      }

      entity.m_6021_(player.m_20185_() + 1.0, player.m_20186_(), player.m_20189_() + 1.0);
      entity.setCommandMode(CompanionCommandMode.FOLLOW);
      entity.triggerGreeting();
      entity.persistState(true);
      player.m_213846_(Component.m_237115_("message.aigf.recalled"));
      return 1;
   }

   private static int remove(ServerPlayer player) {
      ServerLevel level = player.m_284548_();
      CompanionWorldData data = CompanionWorldData.get(level);
      CompanionWorldData.StoredCompanionState state = data.getOrCreate(player.m_20148_());
      CompanionEntity entity = getLoadedCompanion(level, state);
      if (entity != null) {
         entity.m_146870_();
      }

      data.clearCompanion(player.m_20148_());
      CompanionTabListManager.removeForOwner(player);
      player.m_213846_(Component.m_237115_("message.aigf.removed"));
      return 1;
   }

   private static int setHome(ServerPlayer player) {
      ServerLevel level = player.m_284548_();
      CompanionWorldData data = CompanionWorldData.get(level);
      CompanionWorldData.StoredCompanionState state = data.getOrCreate(player.m_20148_());
      state.setHome(level, player.m_20183_());
      CompanionWorldData.StoredCompanionState.PromiseEffect promiseEffect = state.resolvePromise(level.m_46467_(), CompanionPromiseCategory.HOME);
      state.lastAction = "SET_HOME";
      data.m_77762_();
      CompanionEntity entity = getLoadedCompanion(level, state);
      if (entity != null) {
         if (promiseEffect.hasImpact()) {
            entity.setMood(entity.getMood() + promiseEffect.moodDelta());
            entity.setTrust(entity.getTrust() + promiseEffect.trustDelta());
         }

         entity.persistState(true);
      }

      player.m_213846_(Component.m_237113_("AIGF home set at " + state.homeX + ", " + state.homeY + ", " + state.homeZ));
      return 1;
   }

   private static int goHome(ServerPlayer player) {
      ServerLevel level = player.m_284548_();
      CompanionWorldData data = CompanionWorldData.get(level);
      CompanionWorldData.StoredCompanionState state = data.getOrCreate(player.m_20148_());
      if (!state.hasHome()) {
         player.m_213846_(Component.m_237113_("Set home first with /aigf sethome."));
         return 0;
      }

      CompanionEntity entity = getLoadedCompanion(level, state);
      if (entity == null) {
         player.m_213846_(Component.m_237113_("Your companion is not loaded. Use /aigf spawn first."));
         return 0;
      }

      entity.applyActionIntent(CompanionActionIntent.GO_HOME, player);
      CompanionWorldData.StoredCompanionState.PromiseEffect promiseEffect = state.resolvePromise(level.m_46467_(), CompanionPromiseCategory.HOME);
      if (promiseEffect.hasImpact()) {
         entity.setMood(entity.getMood() + promiseEffect.moodDelta());
         entity.setTrust(entity.getTrust() + promiseEffect.trustDelta());
      }

      entity.persistState(true);
      player.m_213846_(Component.m_237113_("AIGF companion is heading home."));
      return 1;
   }

   private static CompanionEntity getLoadedCompanion(ServerLevel level, CompanionWorldData.StoredCompanionState state) {
      if (state.companionUuid == null) {
         return null;
      } else {
         return level.m_8791_(state.companionUuid) instanceof CompanionEntity companion ? companion : null;
      }
   }
}
