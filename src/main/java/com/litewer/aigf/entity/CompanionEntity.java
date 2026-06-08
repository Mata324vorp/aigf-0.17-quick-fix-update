package com.litewer.aigf.entity;

import com.litewer.aigf.data.CompanionSnapshot;
import com.litewer.aigf.data.CompanionWorldData;
import com.litewer.aigf.network.AigfNetwork;
import com.litewer.aigf.network.packet.ClientboundGiftPromptPacket;
import com.litewer.aigf.network.packet.ClientboundOpenCompanionScreenPacket;
import com.litewer.aigf.server.CompanionTabListManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;


public class CompanionEntity extends PathfinderMob {
   private static final EquipmentSlot[] MANAGED_SLOTS = new EquipmentSlot[]{
           EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
   private static final EntityDataAccessor<Integer> COMMAND_MODE = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> EMOTION = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> MOOD = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> TRUST = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<String> ACTIVE_SKIN_ID = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<String> COMPANION_NAME = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<Integer> GREETING_TICKS = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
   private int focusOnOwnerTicks;
   private int hurtCooldownTicks;

   public CompanionEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
      this.hurtTime = 0;
      this.registerGoals();
      this.setCompanionName("Aira");
      this.setNoGravity(true);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes()
              .add(Attributes.MAX_HEALTH, 20.0)
              .add(Attributes.MOVEMENT_SPEED, 0.27)
              .add(Attributes.FOLLOW_RANGE, 32.0);
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(OWNER_UUID, Optional.empty());
      this.entityData.define(COMMAND_MODE, CompanionCommandMode.FOLLOW.ordinal());
      this.entityData.define(EMOTION, CompanionEmotion.NEUTRAL.ordinal());
      this.entityData.define(MOOD, 60);
      this.entityData.define(ENERGY, 80);
      this.entityData.define(TRUST, 40);
      this.entityData.define(ACTIVE_SKIN_ID, "builtin:alex");
      this.entityData.define(COMPANION_NAME, "Aira");
      this.entityData.define(GREETING_TICKS, 0);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide()) {
         this.tickParticles();
      } else {
         if (this.getGreetingTicks() > 0) {
            this.entityData.set(GREETING_TICKS, this.getGreetingTicks() - 1);
         }
         if (this.hurtCooldownTicks > 0) {
            this.hurtCooldownTicks--;
         }
         ServerPlayer owner = this.getOwnerPlayer();
         if (this.focusOnOwnerTicks > 0 && owner != null) {
            this.focusOnOwnerTicks--;
            this.smoothLookAt(owner, 20.0F, 14.0F);
         } else if (owner != null && this.shouldWatchOwner(owner)) {
            this.smoothLookAt(owner, 12.0F, 8.0F);
         }
         if (this.tickCount % 10 == 0) {
            if (this.getCommandMode() == CompanionCommandMode.HOME) {
               this.handleGoHome();
            } else if (owner != null) {
               this.handleMovement(owner);
            }
         }
         if (this.tickCount % 20 == 0) {
            this.updateNeeds(owner);
            this.persistState(false);
         }
         Pose targetPose = this.getCommandMode() == CompanionCommandMode.SIT ? Pose.SITTING : Pose.STANDING;
         if (this.getPose() != targetPose) {
            this.setPose(targetPose);
            this.refreshDimensions();
         }
      }
   }

   private void tickParticles() {
      if (this.tickCount % 40 == 0) {
         switch (this.getEmotion()) {
            case HAPPY -> this.level().addParticle(ParticleTypes.HEART, this.getX(), this.getY() + 2.1, this.getZ(), 0.0, 0.02, 0.0);
            case TIRED -> { /* this.level().addParticle(ParticleTypes.SNORE, this.getX(), this.getY() + 1.9, this.getZ(), 0.0, 0.02, 0.0); */ }
            case SAD -> this.level().addParticle(ParticleTypes.SOUL, this.getX(), this.getY() + 1.9, this.getZ(), 0.0, 0.02, 0.0);
            default -> {}
         }
      }
   }

   private void handleMovement(ServerPlayer owner) {
      CompanionCommandMode mode = this.getCommandMode();
      if (mode != CompanionCommandMode.SIT && mode != CompanionCommandMode.STAY) {
         double distance = this.distanceToSqr(owner);
         if (distance > 256.0) {
            BlockPos target = owner.blockPosition().offset(1, 0, 1);
            this.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
            this.triggerGreeting();
         } else {
            if (distance > 9.0) {
               double speed = this.getEnergy() < 20 ? 0.95 : 1.18;
               this.getNavigation().moveTo(owner, speed);
            } else {
               this.getNavigation().stop();
            }
         }
      } else {
         this.getNavigation().stop();
      }
   }

   private void handleGoHome() {
      if (this.level() instanceof ServerLevel serverLevel && this.getOwnerUuid() != null) {
         CompanionWorldData.StoredCompanionState state = CompanionWorldData.get(serverLevel).getOrCreate(this.getOwnerUuid());
         if (!state.hasHome()) {
            this.setCommandMode(CompanionCommandMode.STAY);
            state.lastAction = "GO_HOME_FAILED";
            state.lastCareHint = "hint.home.missing";
            this.persistState(true);
         } else if (!state.isHomeIn(serverLevel)) {
            this.setCommandMode(CompanionCommandMode.STAY);
            state.lastAction = "GO_HOME_FAILED";
            state.lastCareHint = "hint.home.dimension";
            this.persistState(true);
         } else {
            BlockPos homePos = new BlockPos(state.homeX, state.homeY, state.homeZ);
            double distance = this.distanceToSqr(homePos.getX() + 0.5, homePos.getY(), homePos.getZ() + 0.5);
            if (distance <= 4.0) {
               this.getNavigation().stop();
               this.setCommandMode(CompanionCommandMode.STAY);
               this.triggerGreeting();
               state.lastAction = "HOME_ARRIVED";
               state.lastCareHint = "hint.home.arrived";
               this.persistState(true);
            } else if (distance > 4096.0) {
               this.teleportTo(homePos.getX() + 0.5, homePos.getY(), homePos.getZ() + 0.5);
               this.getNavigation().stop();
               this.setCommandMode(CompanionCommandMode.STAY);
               this.triggerGreeting();
               state.lastAction = "HOME_ARRIVED";
               state.lastCareHint = "hint.home.arrived";
               this.persistState(true);
            } else {
               this.getNavigation().moveTo(homePos.getX() + 0.5, homePos.getY(), homePos.getZ() + 0.5, 1.08);
               this.getLookControl().setLookAt(homePos.getX() + 0.5, homePos.getY() + 1.0, homePos.getZ() + 0.5, 30.0F, 30.0F);
            }
         }
      }
   }

   private boolean shouldWatchOwner(ServerPlayer owner) {
      return owner != null && this.getCommandMode() != CompanionCommandMode.HOME && this.distanceToSqr(owner) <= 64.0;
   }

   private void smoothLookAt(ServerPlayer owner, float headStep, float pitchStep) {
      this.getLookControl().setLookAt(owner, headStep, pitchStep);
      if (!this.getNavigation().isInProgress()) {
         double dx = owner.getX() - this.getX();
         double dz = owner.getZ() - this.getZ();
         float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
         this.yBodyRot = Mth.rotLerp(0.18F, this.yBodyRot, targetYaw);
         this.yHeadRot = Mth.rotLerp(0.26F, this.yHeadRot, targetYaw);
         this.setYRot(Mth.rotLerp(0.14F, this.getYRot(), targetYaw));
      }
   }

   private void updateNeeds(ServerPlayer owner) {
      CompanionWorldData.StoredCompanionState state = null;
      if (this.level() instanceof ServerLevel serverLevel && this.getOwnerUuid() != null) {
         state = CompanionWorldData.get(serverLevel).getOrCreate(this.getOwnerUuid());
         CompanionWorldData.StoredCompanionState.PromiseEffect promiseEffect = state.tickPromises(serverLevel.getGameTime());
         if (promiseEffect.hasImpact()) {
            this.setMood(this.getMood() + promiseEffect.moodDelta());
            this.setTrust(this.getTrust() + promiseEffect.trustDelta());
            this.persistState(true);
         }
         boolean closeToOwner = owner != null && this.distanceToSqr(owner) <= 25.0;
         boolean calmMoment = this.hurtCooldownTicks <= 0
                 && closeToOwner
                 && (this.getCommandMode() == CompanionCommandMode.SIT
                 || this.getCommandMode() == CompanionCommandMode.STAY
                 || this.getCommandMode() == CompanionCommandMode.HOME);
         state.calmDownOverTime(calmMoment, serverLevel.getGameTime());
      }
      if (owner != null && this.getCommandMode() == CompanionCommandMode.FOLLOW && this.distanceToSqr(owner) > 9.0) {
         this.setEnergy(this.getEnergy() - 1);
      } else if (this.getCommandMode() == CompanionCommandMode.HOME) {
         this.setEnergy(this.getEnergy() - 1);
      } else {
         this.setEnergy(this.getEnergy() + (this.getCommandMode() == CompanionCommandMode.SIT ? 2 : 1));
      }
      if (this.hurtCooldownTicks <= 0
              && this.getMood() < 65
              && this.tickCount % 100 == 0
              && (this.getCommandMode() == CompanionCommandMode.SIT || this.getCommandMode() == CompanionCommandMode.STAY)
              && (state == null || state.resentment < 18)) {
         this.setMood(this.getMood() + 1);
      }
      if (this.getEnergy() < 15) {
         this.setEmotion(CompanionEmotion.TIRED);
      } else if (state != null && state.getConflictState() == CompanionConflictState.DISTANT) {
         this.setEmotion(CompanionEmotion.SAD);
      } else if (state != null && state.getConflictState() == CompanionConflictState.OFFENDED && this.getMood() < 60) {
         this.setEmotion(CompanionEmotion.SAD);
      } else if (this.getMood() < 25) {
         this.setEmotion(CompanionEmotion.SAD);
      } else if (this.getTrust() > 70 && this.getMood() > 70) {
         this.setEmotion(CompanionEmotion.HAPPY);
      } else {
         this.setEmotion(CompanionEmotion.NEUTRAL);
      }
   }

   public InteractionResult handlePlayerInteraction(Player player, InteractionHand hand) {
      if (this.level().isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      if (player instanceof ServerPlayer serverPlayer) {
         UUID ownerUuid = this.getOwnerUuid();
         if (ownerUuid != null && ownerUuid.equals(player.getUUID())) {
            CompanionSnapshot snapshot = this.persistState(false);
            AigfNetwork.sendToPlayer(serverPlayer, new ClientboundOpenCompanionScreenPacket(this.getId(), snapshot));
            CompanionTabListManager.syncForOwner(serverPlayer, this);
            return InteractionResult.CONSUME;
         } else {
            player.sendSystemMessage(Component.translatable("message.aigf.not_owner"));
            return InteractionResult.CONSUME;
         }
      } else {
         return InteractionResult.CONSUME;
      }
   }

   public boolean giveGiftFromMainHand(ServerPlayer player) {
      ItemStack stack = player.getMainHandItem();
      if (stack.isEmpty()) {
         return false;
      }
      this.applyGift(player, stack);
      return true;
   }

   private void applyGift(ServerPlayer player, ItemStack stack) {
      ItemStack giftedStack = stack.copyWithCount(1);
      if (!player.getAbilities().instabuild) {
         stack.shrink(1);
      }
      this.triggerGreeting();
      if (this.level() instanceof ServerLevel serverLevel) {
         CompanionWorldData data = CompanionWorldData.get(serverLevel);
         CompanionWorldData.StoredCompanionState state = data.getOrCreate(player.getUUID());
         state.addTurn("system", "gift:" + BuiltInRegistries.ITEM.getKey(giftedStack.getItem()));
         state.lastAction = "GIFT";
         state.lastCareHint = "";
         data.setDirty();
         AigfNetwork.sendToPlayer(player, new ClientboundGiftPromptPacket(this.getId(), giftedStack, state.toSnapshot(player.getUUID())));
      }
      this.persistState(true);
   }

   public void applyStoredState(CompanionWorldData.StoredCompanionState state) {
      this.setMood(state.mood);
      this.setEnergy(state.energy);
      this.setTrust(state.trust);
      this.setCompanionName(state.companionName);
      this.setActiveSkinId(state.activeSkinId != null && !state.activeSkinId.isBlank() ? state.activeSkinId : "builtin:alex");
      this.setCommandMode(state.commandMode);
      this.setEmotion(state.emotion);
      for (EquipmentSlot slot : MANAGED_SLOTS) {
         this.setItemSlot(slot, state.getEquipment(slot).copy());
      }
   }

   public CompanionSnapshot persistState(boolean syncToOwner) {
      if (!(this.level() instanceof ServerLevel serverLevel)) {
         return new CompanionSnapshot(
                 null,
                 this.getCompanionName(),
                 this.getMood(),
                 this.getEnergy(),
                 this.getTrust(),
                 0, 0,
                 this.getActiveSkinId(),
                 "NOOP", "",
                 this.getCommandMode(),
                 this.getEmotion(),
                 CompanionConflictState.OPEN,
                 CompanionRelationshipStage.fromValues(this.getTrust(), this.getMood(), 0),
                 List.of(), List.of(), List.of(),
                 0L,
                 "", 0, 0, 0
         );
      } else {
         UUID ownerUuid = this.getOwnerUuid();
         if (ownerUuid == null) {
            return new CompanionSnapshot(
                    null,
                    this.getCompanionName(),
                    this.getMood(),
                    this.getEnergy(),
                    this.getTrust(),
                    0, 0,
                    this.getActiveSkinId(),
                    "NOOP", "",
                    this.getCommandMode(),
                    this.getEmotion(),
                    CompanionConflictState.OPEN,
                    CompanionRelationshipStage.fromValues(this.getTrust(), this.getMood(), 0),
                    List.of(), List.of(), List.of(),
                    serverLevel.getGameTime(),
                    "", 0, 0, 0
            );
         }
         CompanionWorldData data = CompanionWorldData.get(serverLevel);
         CompanionWorldData.StoredCompanionState state = data.getOrCreate(ownerUuid);
         state.companionUuid = this.getUUID();
         state.companionName = this.getCompanionName();
         state.mood = this.getMood();
         state.energy = this.getEnergy();
         state.trust = this.getTrust();
         state.activeSkinId = this.getActiveSkinId();
         state.commandMode = this.getCommandMode();
         state.emotion = this.getEmotion();
         for (EquipmentSlot slot : MANAGED_SLOTS) {
            state.setEquipment(slot, this.getItemBySlot(slot));
         }
         state.lastSeenWorldTime = serverLevel.getGameTime();
         data.setDirty();
         CompanionSnapshot snapshot = state.toSnapshot(ownerUuid);
         if (syncToOwner) {
            ServerPlayer ownerPlayer = this.getOwnerPlayer();
            if (ownerPlayer != null) {
               AigfNetwork.syncCompanion(ownerPlayer, this.getId(), snapshot);
               CompanionTabListManager.syncForOwner(ownerPlayer, this);
            }
         }
         return snapshot;
      }
   }

   public void applyActionIntent(CompanionActionIntent intent, ServerPlayer issuer) {
      switch (intent) {
         case FOLLOW:
            this.setCommandMode(CompanionCommandMode.FOLLOW);
            break;
         case STAY:
            this.setCommandMode(CompanionCommandMode.STAY);
            break;
         case GO_HOME:
            if (this.level() instanceof ServerLevel serverLevel && this.getOwnerUuid() != null) {
               CompanionWorldData.StoredCompanionState state = CompanionWorldData.get(serverLevel).getOrCreate(this.getOwnerUuid());
               if (state.hasHome()) {
                  this.setCommandMode(CompanionCommandMode.HOME);
                  state.lastCareHint = "hint.home.going";
               } else {
                  state.lastCareHint = "hint.home.missing";
               }
            }
            break;
         case SET_HOME:
            if (this.level() instanceof ServerLevel serverLevel && this.getOwnerUuid() != null) {
               CompanionWorldData.StoredCompanionState state = CompanionWorldData.get(serverLevel).getOrCreate(this.getOwnerUuid());
               state.setHome(serverLevel, issuer.blockPosition());
               if (this.getCommandMode() == CompanionCommandMode.HOME) {
                  this.setCommandMode(CompanionCommandMode.STAY);
               }
            }
            break;
         case COME_HERE:
            this.teleportTo(issuer.getX() + 1.0, issuer.getY(), issuer.getZ() + 1.0);
            this.setCommandMode(CompanionCommandMode.FOLLOW);
            this.triggerGreeting();
            break;
         case SIT:
            this.setCommandMode(CompanionCommandMode.SIT);
            break;
         case STAND:
            this.setCommandMode(CompanionCommandMode.STAY);
            break;
         case LOOK_AT_PLAYER:
            this.focusOnOwnerTicks = 80;
         case NOOP:
      }
   }

   public void triggerGreeting() {
      this.entityData.set(GREETING_TICKS, 40);
   }

   public void registerConversationImpact(int moodDelta, int trustDelta) {
      if (moodDelta < 0 || trustDelta < 0) {
         this.hurtCooldownTicks = 900;
      }
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean isPickable() {
      return false;
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (source.getEntity() instanceof Player player && player.getUUID().equals(this.getOwnerUuid())) {
         return false;
      }
      return super.hurt(source, amount);
   }

   @Override
   public void die(DamageSource damageSource) {
      super.die(damageSource);
      if (this.level() instanceof ServerLevel serverLevel && this.getOwnerUuid() != null) {
         CompanionWorldData.get(serverLevel).clearCompanion(this.getOwnerUuid());
         ServerPlayer ownerPlayer = this.getOwnerPlayer();
         if (ownerPlayer != null) {
            CompanionTabListManager.removeForOwner(ownerPlayer);
         }
      }
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      if (this.getOwnerUuid() != null) {
         tag.putUUID("Owner", this.getOwnerUuid());
      }
      tag.putInt("Mood", this.getMood());
      tag.putInt("Energy", this.getEnergy());
      tag.putInt("Trust", this.getTrust());
      tag.putString("CompanionName", this.getCompanionName());
      tag.putString("SkinId", this.getActiveSkinId());
      tag.putString("CommandMode", this.getCommandMode().name());
      tag.putString("Emotion", this.getEmotion().name());
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.hasUUID("Owner")) {
         this.setOwnerUuid(tag.getUUID("Owner"));
      }
      this.setMood(tag.getInt("Mood"));
      this.setEnergy(tag.getInt("Energy"));
      this.setTrust(tag.getInt("Trust"));
      if (tag.contains("CompanionName")) {
         this.setCompanionName(tag.getString("CompanionName"));
      }
      this.setActiveSkinId(tag.getString("SkinId"));
      if (tag.contains("CommandMode")) {
         this.setCommandMode(CompanionCommandMode.valueOf(tag.getString("CommandMode")));
      }
      if (tag.contains("Emotion")) {
         this.setEmotion(CompanionEmotion.fromName(tag.getString("Emotion")));
      }
   }

   public UUID getOwnerUuid() {
      return this.entityData.get(OWNER_UUID).orElse(null);
   }

   public void setOwnerUuid(UUID ownerUuid) {
      this.entityData.set(OWNER_UUID, Optional.ofNullable(ownerUuid));
   }

   public ServerPlayer getOwnerPlayer() {
      if (this.level() instanceof ServerLevel serverLevel && this.getOwnerUuid() != null) {
         return serverLevel.getServer().getPlayerList().getPlayer(this.getOwnerUuid());
      }
      return null;
   }

   public CompanionCommandMode getCommandMode() {
      return CompanionCommandMode.fromOrdinal(this.entityData.get(COMMAND_MODE));
   }

   public void setCommandMode(CompanionCommandMode mode) {
      this.entityData.set(COMMAND_MODE, mode.ordinal());
      if (mode == CompanionCommandMode.SIT || mode == CompanionCommandMode.STAY) {
         this.getNavigation().stop();
      }
   }

   public CompanionEmotion getEmotion() {
      return CompanionEmotion.fromOrdinal(this.entityData.get(EMOTION));
   }

   public void setEmotion(CompanionEmotion emotion) {
      this.entityData.set(EMOTION, emotion.ordinal());
   }

   public int getMood() {
      return this.entityData.get(MOOD);
   }

   public void setMood(int mood) {
      this.entityData.set(MOOD, Mth.clamp(mood, 0, 100));
   }

   public int getEnergy() {
      return this.entityData.get(ENERGY);
   }

   public void setEnergy(int energy) {
      this.entityData.set(ENERGY, Mth.clamp(energy, 0, 100));
   }

   public int getTrust() {
      return this.entityData.get(TRUST);
   }

   public void setTrust(int trust) {
      this.entityData.set(TRUST, Mth.clamp(trust, 0, 100));
   }

   public String getActiveSkinId() {
      return this.entityData.get(ACTIVE_SKIN_ID);
   }

   public void setActiveSkinId(String activeSkinId) {
      this.entityData.set(ACTIVE_SKIN_ID, activeSkinId != null && !activeSkinId.isBlank() ? activeSkinId : "builtin:alex");
   }

   public String getCompanionName() {
      return this.entityData.get(COMPANION_NAME);
   }

   public void setCompanionName(String companionName) {
      String trimmed = companionName == null ? "" : companionName.trim();
      if (trimmed.isEmpty()) {
         trimmed = "Aira";
      }
      if (trimmed.length() > 24) {
         trimmed = trimmed.substring(0, 24);
      }
      this.entityData.set(COMPANION_NAME, trimmed);
      this.setCustomName(Component.literal(trimmed));
      this.setCustomNameVisible(true);
   }

   public int getGreetingTicks() {
      return this.entityData.get(GREETING_TICKS);
   }

   public float getGreetingAnimationProgress() {
      return Mth.clamp(this.getGreetingTicks() / 40.0F, 0.0F, 1.0F);
   }
}