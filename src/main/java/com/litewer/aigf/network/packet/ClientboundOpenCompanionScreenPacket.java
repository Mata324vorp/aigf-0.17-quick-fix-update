package com.litewer.aigf.network.packet;

import com.litewer.aigf.client.ClientPacketHandlers;
import com.litewer.aigf.data.CompanionSnapshot;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public record ClientboundOpenCompanionScreenPacket(int entityId, CompanionSnapshot snapshot) {
   public static void encode(ClientboundOpenCompanionScreenPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
      buffer.writeNbt(packet.snapshot.toTag());
   }

   public static ClientboundOpenCompanionScreenPacket decode(FriendlyByteBuf buffer) {
      return new ClientboundOpenCompanionScreenPacket(buffer.readInt(), CompanionSnapshot.fromTag(buffer.readNbt()));
   }


   public static void handle(ClientboundOpenCompanionScreenPacket packet, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.openCompanionScreen(packet)));
      context.setPacketHandled(true);
   }
}
