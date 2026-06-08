package com.litewer.aigf.client;

import com.litewer.aigf.data.CompanionSnapshot;
import com.litewer.aigf.entity.CompanionConflictState;
import com.litewer.aigf.entity.CompanionEntity;
import com.litewer.aigf.entity.CompanionRelationshipStage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

public final class CompanionClientState {
   private static final Map<Integer, CompanionSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();

   private CompanionClientState() {
   }

   public static void updateSnapshot(int entityId, CompanionSnapshot snapshot) {
      SNAPSHOTS.put(entityId, snapshot);
   }

   public static CompanionSnapshot getSnapshot(int entityId) {
      return SNAPSHOTS.get(entityId);
   }

   public static CompanionSnapshot getSnapshot(CompanionEntity companion) {
      CompanionSnapshot snapshot = SNAPSHOTS.get(companion.getId());
      if (snapshot != null) {
         return snapshot;
      }
      long gameTime = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
      return new CompanionSnapshot(
              companion.getOwnerUuid(),
              companion.getCompanionName(),
              companion.getMood(),
              companion.getEnergy(),
              companion.getTrust(),
              0, 0,
              companion.getActiveSkinId(),
              companion.getCommandMode().name(),
              "",
              companion.getCommandMode(),
              companion.getEmotion(),
              CompanionConflictState.OPEN,
              CompanionRelationshipStage.fromValues(companion.getTrust(), companion.getMood(), 0),
              List.of(), List.of(), List.of(),
              gameTime,
              "", 0, 0, 0
      );
   }
}
