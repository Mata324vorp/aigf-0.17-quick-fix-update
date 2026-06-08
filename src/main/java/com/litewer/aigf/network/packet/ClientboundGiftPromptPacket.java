package com.litewer.aigf.network.packet;

import com.litewer.aigf.client.ClientPacketHandlers;
import com.litewer.aigf.data.CompanionSnapshot;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public record ClientboundGiftPromptPacket(int entityId, ItemStack giftedStack, CompanionSnapshot snapshot) {
   public static void encode(ClientboundGiftPromptPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
      buffer.writeItem(packet.giftedStack);
      buffer.writeNbt(packet.snapshot.toTag());
   }

   public static ClientboundGiftPromptPacket decode(FriendlyByteBuf buffer) {
      return new ClientboundGiftPromptPacket(buffer.readInt(), buffer.readItem(), CompanionSnapshot.fromTag(buffer.readNbt()));
   }


   public static void handle(ClientboundGiftPromptPacket packet, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleGiftPrompt(packet)));
      context.setPacketHandled(true);
   }
}
