package com.litewer.aigf.network.packet;

import com.litewer.aigf.client.ClientPacketHandlers;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public record ClientboundOpenWelcomePacket(String companionName, boolean hasCompanion) {
   public static void encode(ClientboundOpenWelcomePacket packet, FriendlyByteBuf buffer) {
      buffer.writeUtf(packet.companionName, 32);
      buffer.writeBoolean(packet.hasCompanion);
   }

   public static ClientboundOpenWelcomePacket decode(FriendlyByteBuf buffer) {
      return new ClientboundOpenWelcomePacket(buffer.readUtf(32), buffer.readBoolean());
   }


   public static void handle(ClientboundOpenWelcomePacket packet, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.openWelcomeScreen(packet)));
      context.setPacketHandled(true);
   }
}
