package com.litewer.aigf.data;

import com.litewer.aigf.conversation.ConversationMoodAnalyzer;
import com.litewer.aigf.conversation.PromiseAnalyzer;
import com.litewer.aigf.entity.CompanionCommandMode;
import com.litewer.aigf.entity.CompanionConflictState;
import com.litewer.aigf.entity.CompanionEmotion;
import com.litewer.aigf.entity.CompanionRelationshipStage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.Map.Entry;

public final class CompanionWorldData extends SavedData {
   private static final String DATA_NAME = "aigf_companion_data";
   private final Map<UUID, StoredCompanionState> states = new HashMap<>();

   public static CompanionWorldData get(ServerLevel level) {
      return level.getServer().overworld().getDataStorage().computeIfAbsent(
              CompanionWorldData::load, CompanionWorldData::new, DATA_NAME);
   }

   public StoredCompanionState getOrCreate(UUID ownerUuid) {
      return states.computeIfAbsent(ownerUuid, key -> {
         setDirty();
         return new StoredCompanionState();
      });
   }

   public void clearCompanion(UUID ownerUuid) {
      StoredCompanionState state = states.get(ownerUuid);
      if (state != null) {
         state.companionUuid = null;
         state.lastAction = "REMOVED";
         setDirty();
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      ListTag listTag = new ListTag();
      for (Entry<UUID, StoredCompanionState> entry : states.entrySet()) {
         CompoundTag stateTag = entry.getValue().toTag();
         stateTag.putUUID("Owner", entry.getKey());
         listTag.add(stateTag);
      }
      tag.put("States", listTag);
      return tag;
   }

   private static CompanionWorldData load(CompoundTag tag) {
      CompanionWorldData data = new CompanionWorldData();
      for (Tag element : tag.getList("States", Tag.TAG_COMPOUND)) {
         CompoundTag stateTag = (CompoundTag) element;
         if (stateTag.hasUUID("Owner")) {
            data.states.put(stateTag.getUUID("Owner"), StoredCompanionState.fromTag(stateTag));
         }
      }
      return data;
   }

   // ==================== Clase interna StoredCompanionState ====================

   public static final class StoredCompanionState {
      private static final EquipmentSlot[] MANAGED_SLOTS = {
              EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
              EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
      };
      public UUID companionUuid;
      public String companionName = "Aira";
      public boolean welcomeSeen;
      public int mood = 60;
      public int energy = 80;
      public int trust = 40;
      public int resentment;
      public int reconciliationProgress;
      public String activeSkinId = "builtin:alex";
      public CompanionCommandMode commandMode = CompanionCommandMode.FOLLOW;
      public CompanionEmotion emotion = CompanionEmotion.NEUTRAL;
      public final List<String> importantFacts = new ArrayList<>();
      public final List<ConversationTurn> recentTurns = new ArrayList<>();
      public final List<CompanionPromise> promises = new ArrayList<>();
      public final EnumMap<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
      public String lastAction = "NOOP";
      public String lastCareHint = "";
      public long lastSeenWorldTime = 0L;
      public long lastConflictWorldTime = 0L;
      public String homeDimension = "";
      public int homeX;
      public int homeY;
      public int homeZ;

      public StoredCompanionState() {
         for (EquipmentSlot slot : MANAGED_SLOTS) {
            equipment.put(slot, ItemStack.EMPTY);
         }
      }

      public void addFact(String fact) {
         String trimmed = fact == null ? "" : fact.trim();
         if (!trimmed.isEmpty()) {
            importantFacts.removeIf(existing -> existing.equalsIgnoreCase(trimmed));
            importantFacts.add(trimmed);
            while (importantFacts.size() > 20) {
               importantFacts.remove(0);
            }
         }
      }

      public void addTurn(String speaker, String text) {
         String trimmed = text == null ? "" : text.trim();
         if (!trimmed.isEmpty()) {
            recentTurns.add(new ConversationTurn(speaker, trimmed));
            while (recentTurns.size() > 10) {
               recentTurns.remove(0);
            }
         }
      }

      public void applyRename(String newName) {
         String previousName = companionName == null ? "" : companionName.trim();
         String trimmed = newName == null ? "" : newName.trim();
         if (trimmed.isEmpty()) trimmed = "Aira";
         if (trimmed.length() > 24) trimmed = trimmed.substring(0, 24);
         companionName = trimmed;
         String previousLower = previousName.toLowerCase(Locale.ROOT);
         importantFacts.removeIf(existing -> {
            String lower = existing.toLowerCase(Locale.ROOT);
            return lower.contains("моё текущее имя") || lower.contains("меня зовут") || lower.contains("моё имя") ||
                    lower.contains("aira") || lower.contains("айра") || lower.contains("аира") ||
                    (!previousLower.isBlank() && lower.contains(previousLower));
         });
         recentTurns.removeIf(turn -> {
            String lower = turn.text().toLowerCase(Locale.ROOT);
            return lower.contains("name_changed:") || lower.contains("aira") || lower.contains("айра") ||
                    lower.contains("аира") || lower.contains("меня зовут") || lower.contains("моё имя") ||
                    (!previousLower.isBlank() && lower.contains(previousLower));
         });
         addFact("Моё текущее имя: " + companionName);
         addTurn("system", "name_changed:" + companionName);
         lastCareHint = "Теперь меня зовут " + companionName + ".";
      }

      public boolean hasSeenWelcome() { return welcomeSeen; }
      public void markWelcomeSeen() { welcomeSeen = true; }

      public CompanionSnapshot toSnapshot(UUID ownerUuid) {
         return new CompanionSnapshot(ownerUuid, companionName, mood, energy, trust,
                 resentment, reconciliationProgress, activeSkinId, lastAction, lastCareHint,
                 commandMode, emotion, getConflictState(), getRelationshipStage(),
                 List.copyOf(recentTurns), List.copyOf(importantFacts), List.copyOf(promises),
                 lastSeenWorldTime, homeDimension, homeX, homeY, homeZ);
      }

      public boolean hasHome() { return homeDimension != null && !homeDimension.isBlank(); }

      public void setHome(ServerLevel level, BlockPos pos) {
         homeDimension = level.dimension().location().toString();
         homeX = pos.getX();
         homeY = pos.getY();
         homeZ = pos.getZ();
         lastCareHint = "hint.home.set";
         addFact("Home point: " + homeDimension + " @ " + homeX + ", " + homeY + ", " + homeZ);
      }

      public boolean isHomeIn(Level level) {
         return hasHome() && level.dimension().location().toString().equals(homeDimension);
      }

      public PromiseEffect registerPromise(PromiseAnalyzer.PromiseSeed seed, long worldTime) {
         if (seed == null || seed.summary() == null || seed.summary().isBlank())
            return PromiseEffect.none();
         for (int i = 0; i < promises.size(); i++) {
            CompanionPromise p = promises.get(i);
            if (p.isPending() && (p.category() == seed.category() || p.summary().equalsIgnoreCase(seed.summary()))) {
               promises.set(i, new CompanionPromise(seed.summary(), seed.category(), CompanionPromiseStatus.PENDING,
                       worldTime, seed.dueWorldTime(), 0L, 0L));
               trimPromiseHistory();
               lastCareHint = "hint.promise.new:" + seed.summary();
               addFact("The player renewed a promise: " + seed.summary());
               return new PromiseEffect(0, 0, lastCareHint);
            }
         }
         promises.add(new CompanionPromise(seed.summary(), seed.category(), CompanionPromiseStatus.PENDING,
                 worldTime, seed.dueWorldTime(), 0L, 0L));
         trimPromiseHistory();
         lastCareHint = "hint.promise.new:" + seed.summary();
         addFact("The player promised: " + seed.summary());
         return new PromiseEffect(0, 0, lastCareHint);
      }

      public PromiseEffect resolvePromise(CompanionPromiseCategory category, long worldTime) {
         for (int i = 0; i < promises.size(); i++) {
            CompanionPromise p = promises.get(i);
            if (p.isPending() && p.category() == category) {
               promises.set(i, p.kept(worldTime));
               trimPromiseHistory();
               lastCareHint = "hint.promise.kept:" + p.summary();
               addFact("The player kept a promise: " + p.summary());
               return promiseReward(category);
            }
         }
         return PromiseEffect.none();
      }

      public PromiseEffect resolvePromise(long worldTime, CompanionPromiseCategory... categories) {
         for (CompanionPromiseCategory cat : categories) {
            PromiseEffect effect = resolvePromise(cat, worldTime);
            if (effect.hasImpact()) return effect;
         }
         return PromiseEffect.none();
      }

      public PromiseEffect tickPromises(long worldTime) {
         for (int i = 0; i < promises.size(); i++) {
            CompanionPromise p = promises.get(i);
            if (p.isPending()) {
               if (p.isOverdue(worldTime)) {
                  promises.set(i, p.broken(worldTime));
                  trimPromiseHistory();
                  lastCareHint = "hint.promise.broken:" + p.summary();
                  addFact("The player broke a promise: " + p.summary());
                  return new PromiseEffect(-5, -7, lastCareHint);
               }
               if (p.isDueSoon(worldTime) && (p.lastReminderWorldTime() == 0L || worldTime - p.lastReminderWorldTime() >= 6000L)) {
                  promises.set(i, p.withReminder(worldTime));
                  lastCareHint = "hint.promise.reminder:" + p.summary();
                  return new PromiseEffect(0, 0, lastCareHint);
               }
            }
         }
         return PromiseEffect.none();
      }

      public CompanionPromise firstPendingPromise() {
         for (CompanionPromise p : promises) if (p.isPending()) return p;
         return null;
      }

      public CompanionPromise latestBrokenPromise() {
         for (int i = promises.size() - 1; i >= 0; i--) {
            if (promises.get(i).status() == CompanionPromiseStatus.BROKEN) return promises.get(i);
         }
         return null;
      }

      public ItemStack getEquipment(EquipmentSlot slot) { return equipment.getOrDefault(slot, ItemStack.EMPTY); }
      public void setEquipment(EquipmentSlot slot, ItemStack stack) { equipment.put(slot, stack == null ? ItemStack.EMPTY : stack.copy()); }

      public CompanionConflictState getConflictState() { return CompanionConflictState.fromResentment(resentment); }
      public CompanionRelationshipStage getRelationshipStage() { return CompanionRelationshipStage.fromValues(trust, mood, resentment); }

      public void applyConversationImpact(ConversationMoodAnalyzer.Analysis analysis, long worldTime) {
         if (analysis.isHarshNegative()) {
            resentment = clamp(resentment + 18 + (getConflictState() == CompanionConflictState.DISTANT ? 4 : 0));
            reconciliationProgress = 0;
            lastConflictWorldTime = worldTime;
            addFact("У нас был болезненный конфликт из-за грубых слов игрока.");
            lastCareHint = resentment >= 40 ? "Я всё ещё помню эти слова и не готова сразу оттаять." : "Мне всё ещё неприятно после такого разговора.";
         } else if (analysis.isApology()) {
            int repair = 18;
            if (getRelationshipStage() == CompanionRelationshipStage.WARM || getRelationshipStage() == CompanionRelationshipStage.ATTACHED) repair += 6;
            applyRepair(repair);
            lastCareHint = resentment >= 18 ? "Извинение я услышала, но мне нужно немного времени." : "Спасибо за извинение. Мне уже спокойнее.";
         } else {
            if (!analysis.isPositive() && !analysis.isCollaborative()) {
               if (resentment > 0) reconciliationProgress = Math.max(0, reconciliationProgress - 2);
            } else if (resentment > 0) {
               int repair = analysis.isCollaborative() ? 10 : 8;
               if (analysis.isPositive()) repair += 4;
               applyRepair(repair);
               lastCareHint = resentment >= 18 ? "Хороший тон помогает, но я ещё не до конца отпустила обиду." : "Мне уже заметно легче говорить с тобой.";
            }
         }
      }

      public void calmDownOverTime(boolean calmMoment, long worldTime) {
         if (calmMoment && resentment > 0 && worldTime - lastConflictWorldTime >= 200L && worldTime % 240L == 0L) {
            resentment = Math.max(0, resentment - 1);
            if (resentment == 0) {
               reconciliationProgress = 0;
               lastCareHint = "Я снова чувствую себя спокойнее рядом с тобой.";
            }
         }
      }

      private void applyRepair(int repair) {
         if (resentment <= 0) {
            resentment = 0;
            reconciliationProgress = 0;
         } else {
            reconciliationProgress = clamp(reconciliationProgress + repair);
            while (reconciliationProgress >= 20 && resentment > 0) {
               reconciliationProgress -= 20;
               resentment = Math.max(0, resentment - 6);
            }
            if (resentment == 0) reconciliationProgress = 0;
         }
      }

      private static int clamp(int value) { return Math.max(0, Math.min(value, 100)); }

      private PromiseEffect promiseReward(CompanionPromiseCategory category) {
         return switch (category) {
            case GIFT -> new PromiseEffect(4, 6, lastCareHint);
            case HOME -> new PromiseEffect(4, 5, lastCareHint);
            case BUILD -> new PromiseEffect(5, 6, lastCareHint);
            case TALK -> new PromiseEffect(3, 4, lastCareHint);
            case CARE -> new PromiseEffect(5, 5, lastCareHint);
            case GENERAL -> new PromiseEffect(3, 4, lastCareHint);
         };
      }

      private void trimPromiseHistory() {
         while (promises.size() > 6) {
            int resolvedIndex = -1;
            for (int i = 0; i < promises.size(); i++) {
               if (!promises.get(i).isPending()) { resolvedIndex = i; break; }
            }
            promises.remove(resolvedIndex >= 0 ? resolvedIndex : 0);
         }
      }

      public CompoundTag toTag() {
         CompoundTag tag = new CompoundTag();
         if (companionUuid != null) tag.putUUID("Companion", companionUuid);
         tag.putString("CompanionName", companionName);
         tag.putBoolean("WelcomeSeen", welcomeSeen);
         tag.putInt("Mood", mood);
         tag.putInt("Energy", energy);
         tag.putInt("Trust", trust);
         tag.putInt("Resentment", resentment);
         tag.putInt("ReconciliationProgress", reconciliationProgress);
         tag.putString("SkinId", activeSkinId);
         tag.putString("CommandMode", commandMode.name());
         tag.putString("Emotion", emotion.name());
         tag.putString("LastAction", lastAction);
         tag.putString("LastCareHint", lastCareHint);
         tag.putLong("LastSeenWorldTime", lastSeenWorldTime);
         tag.putLong("LastConflictWorldTime", lastConflictWorldTime);
         if (hasHome()) {
            tag.putString("HomeDimension", homeDimension);
            tag.putInt("HomeX", homeX);
            tag.putInt("HomeY", homeY);
            tag.putInt("HomeZ", homeZ);
         }
         CompoundTag equipmentTag = new CompoundTag();
         for (EquipmentSlot slot : MANAGED_SLOTS) {
            ItemStack stack = getEquipment(slot);
            if (!stack.isEmpty()) {
               equipmentTag.put(slot.getName(), stack.save(new CompoundTag()));
            }
         }
         tag.put("Equipment", equipmentTag);
         ListTag factsTag = new ListTag();
         for (String fact : importantFacts) factsTag.add(StringTag.valueOf(fact));
         tag.put("ImportantFacts", factsTag);
         ListTag promisesTag = new ListTag();
         for (CompanionPromise promise : promises) promisesTag.add(promise.toTag());
         tag.put("Promises", promisesTag);
         ListTag turnsTag = new ListTag();
         for (ConversationTurn turn : recentTurns) turnsTag.add(turn.toTag());
         tag.put("RecentTurns", turnsTag);
         return tag;
      }

      public static StoredCompanionState fromTag(CompoundTag tag) {
         StoredCompanionState state = new StoredCompanionState();
         if (tag.hasUUID("Companion")) state.companionUuid = tag.getUUID("Companion");
         if (tag.contains("CompanionName")) state.companionName = tag.getString("CompanionName");
         state.welcomeSeen = tag.contains("WelcomeSeen") && tag.getBoolean("WelcomeSeen");
         state.mood = tag.getInt("Mood");
         state.energy = tag.getInt("Energy");
         state.trust = tag.getInt("Trust");
         state.resentment = tag.contains("Resentment") ? tag.getInt("Resentment") : 0;
         state.reconciliationProgress = tag.contains("ReconciliationProgress") ? tag.getInt("ReconciliationProgress") : 0;
         state.activeSkinId = tag.getString("SkinId");
         state.commandMode = CompanionCommandMode.valueOf(tag.getString("CommandMode"));
         state.emotion = CompanionEmotion.fromName(tag.getString("Emotion"));
         state.lastAction = tag.getString("LastAction");
         state.lastCareHint = tag.getString("LastCareHint");
         state.lastSeenWorldTime = tag.getLong("LastSeenWorldTime");
         state.lastConflictWorldTime = tag.contains("LastConflictWorldTime") ? tag.getLong("LastConflictWorldTime") : 0L;
         state.homeDimension = tag.contains("HomeDimension") ? tag.getString("HomeDimension") : "";
         state.homeX = tag.getInt("HomeX");
         state.homeY = tag.getInt("HomeY");
         state.homeZ = tag.getInt("HomeZ");
         CompoundTag equipmentTag = tag.getCompound("Equipment");
         for (EquipmentSlot slot : MANAGED_SLOTS) {
            if (equipmentTag.contains(slot.getName(), Tag.TAG_COMPOUND)) {
               state.setEquipment(slot, ItemStack.of(equipmentTag.getCompound(slot.getName())));
            }
         }
         for (Tag element : tag.getList("ImportantFacts", Tag.TAG_STRING)) {
            state.importantFacts.add(element.getAsString());
         }
         for (Tag element : tag.getList("Promises", Tag.TAG_COMPOUND)) {
            state.promises.add(CompanionPromise.fromTag((CompoundTag) element));
         }
         for (Tag element : tag.getList("RecentTurns", Tag.TAG_COMPOUND)) {
            state.recentTurns.add(ConversationTurn.fromTag((CompoundTag) element));
         }
         return state;
      }

      public record PromiseEffect(int moodDelta, int trustDelta, String careHint) {
         public static PromiseEffect none() { return new PromiseEffect(0, 0, ""); }
         public boolean hasImpact() { return moodDelta != 0 || trustDelta != 0 || (careHint != null && !careHint.isBlank()); }
      }
   }
}