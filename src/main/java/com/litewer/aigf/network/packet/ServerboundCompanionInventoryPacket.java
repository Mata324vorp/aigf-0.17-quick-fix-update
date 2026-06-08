package com.litewer.aigf.network.packet;

import com.litewer.aigf.entity.CompanionEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ServerboundCompanionInventoryPacket(int entityId, String slotName, String actionName) {

   public static void encode(ServerboundCompanionInventoryPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
      buffer.writeUtf(packet.slotName, 32);
      buffer.writeUtf(packet.actionName, 32);
   }

   public static ServerboundCompanionInventoryPacket decode(FriendlyByteBuf buffer) {
      return new ServerboundCompanionInventoryPacket(buffer.readInt(), buffer.readUtf(32), buffer.readUtf(32));
   }

   public static void handle(ServerboundCompanionInventoryPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null && sender.level().getEntity(packet.entityId) instanceof CompanionEntity companion) {
            if (sender.getUUID().equals(companion.getOwnerUuid())) {
               EquipmentSlot slot;
               try {
                  slot = EquipmentSlot.valueOf(packet.slotName);
               } catch (IllegalArgumentException ignored) {
                  return;
               }
               switch (packet.actionName) {
                  case "PUT_FROM_HAND" -> putFromHand(sender, companion, slot);
                  case "TAKE_TO_PLAYER" -> takeToPlayer(sender, companion, slot);
               }
            }
         }
      });
      context.setPacketHandled(true);
   }

   private static void putFromHand(ServerPlayer sender, CompanionEntity companion, EquipmentSlot slot) {
      ItemStack held = sender.getMainHandItem();
      if (held.isEmpty()) {
         sender.sendSystemMessage(Component.literal("Take an item in your main hand."), true);
      } else if (!canPlaceInSlot(slot, held)) {
         sender.sendSystemMessage(Component.literal("This item does not fit the selected slot."), true);
      } else {
         ItemStack previous = companion.getItemBySlot(slot).copy();
         companion.setItemSlot(slot, held.copy());
         sender.setItemInHand(InteractionHand.MAIN_HAND, previous);
         companion.persistState(true);
      }
   }

   private static void takeToPlayer(ServerPlayer sender, CompanionEntity companion, EquipmentSlot slot) {
      ItemStack stack = companion.getItemBySlot(slot).copy();
      if (!stack.isEmpty()) {
         companion.setItemSlot(slot, ItemStack.EMPTY);
         if (!sender.getInventory().add(stack)) {
            sender.drop(stack, false);
         }
         companion.persistState(true);
      }
   }

   private static boolean canPlaceInSlot(EquipmentSlot slot, ItemStack stack) {
      return switch (slot) {
         case MAINHAND, OFFHAND -> true;
         default -> LivingEntity.getEquipmentSlotForItem(stack) == slot;
      };
   }
}