package com.litewer.aigf.network.packet;

import com.litewer.aigf.client.ClientPacketHandlers;
import com.litewer.aigf.data.CompanionSnapshot;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public record ClientboundSyncCompanionPacket(int entityId, CompanionSnapshot snapshot) {
   public static void encode(ClientboundSyncCompanionPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
      buffer.writeNbt(packet.snapshot.toTag());
   }

   public static ClientboundSyncCompanionPacket decode(FriendlyByteBuf buffer) {
      return new ClientboundSyncCompanionPacket(buffer.readInt(), CompanionSnapshot.fromTag(buffer.readNbt()));
   }


   public static void handle(ClientboundSyncCompanionPacket packet, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.syncCompanionScreen(packet)));
      context.setPacketHandled(true);
   }
}
