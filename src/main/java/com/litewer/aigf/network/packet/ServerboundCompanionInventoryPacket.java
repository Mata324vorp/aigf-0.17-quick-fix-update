package com.litewer.aigf.network.packet;

import com.litewer.aigf.entity.CompanionEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent.Context;

public record ServerboundCompanionInventoryPacket(int entityId, String slotName, String actionName) {
   public static void encode(ServerboundCompanionInventoryPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.entityId);
      buffer.m_130072_(packet.slotName, 32);
      buffer.m_130072_(packet.actionName, 32);
   }

   public static ServerboundCompanionInventoryPacket decode(FriendlyByteBuf buffer) {
      return new ServerboundCompanionInventoryPacket(buffer.readInt(), buffer.m_130136_(32), buffer.m_130136_(32));
   }

   public static void handle(ServerboundCompanionInventoryPacket packet, Supplier<Context> contextSupplier) {
      Context context = contextSupplier.get();
      ServerPlayer sender = context.getSender();
      context.enqueueWork(() -> {
         if (sender != null && sender.m_9236_().m_6815_(packet.entityId) instanceof CompanionEntity companion) {
            if (sender.m_20148_().equals(companion.getOwnerUuid())) {
               EquipmentSlot slot;
               try {
                  slot = EquipmentSlot.valueOf(packet.slotName);
               } catch (IllegalArgumentException exception) {
                  return;
               }

               switch (packet.actionName) {
                  case "PUT_FROM_HAND":
                     putFromHand(sender, companion, slot);
                     break;
                  case "TAKE_TO_PLAYER":
                     takeToPlayer(sender, companion, slot);
               }
            }
         }
      });
      context.setPacketHandled(true);
   }

   private static void putFromHand(ServerPlayer sender, CompanionEntity companion, EquipmentSlot slot) {
      ItemStack held = sender.m_21205_();
      if (held.m_41619_()) {
         sender.m_5661_(Component.m_237113_("Возьми предмет в главную руку."), true);
      } else if (!canPlaceInSlot(slot, held)) {
         sender.m_5661_(Component.m_237113_("Этот предмет не подходит для выбранного слота."), true);
      } else {
         ItemStack previous = companion.m_6844_(slot).m_41777_();
         companion.m_8061_(slot, held.m_41777_());
         sender.m_21008_(InteractionHand.MAIN_HAND, previous);
         companion.persistState(true);
      }
   }

   private static void takeToPlayer(ServerPlayer sender, CompanionEntity companion, EquipmentSlot slot) {
      ItemStack stack = companion.m_6844_(slot).m_41777_();
      if (!stack.m_41619_()) {
         companion.m_8061_(slot, ItemStack.f_41583_);
         if (!sender.m_150109_().m_36054_(stack)) {
            sender.m_36176_(stack, false);
         }

         companion.persistState(true);
      }
   }

   private static boolean canPlaceInSlot(EquipmentSlot slot, ItemStack stack) {
      return switch (slot) {
         case MAINHAND, OFFHAND -> true;
         default -> LivingEntity.m_147233_(stack) == slot;
      };
   }
}
