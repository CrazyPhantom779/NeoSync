package com.breakinblocks.neosync.common.utils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.ItemLike;
import net.minecraft.core.registries.BuiltInRegistries;
import com.breakinblocks.neosync.common.config.SyncConfig;

public final class ItemUtil {
    private static final TagKey<Item> WRENCHES = ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));

    public static boolean isWrench(ItemStack itemStack) {
        if (itemStack.is(WRENCHES)) {
            return true;
        }

        SyncConfig config = SyncConfig.getInstance();
        ResourceLocation wrenchId = config.wrench() == null || config.wrench().isBlank() ? null : ResourceLocation.tryParse(config.wrench());
        if (wrenchId == null) {
            return false;
        }
        Item wrench = BuiltInRegistries.ITEM.get(wrenchId);
        return wrench != null && wrench != Items.AIR && itemStack.is(wrench);
    }

    public static boolean isWrench(ItemLike item) {
        return isWrench(new ItemStack(item));
    }

    public static boolean isArmor(ItemStack itemStack) {
        return isArmor(itemStack.getItem());
    }

    public static boolean isArmor(ItemLike item) {
        return item.asItem() instanceof ArmorItem;
    }

    public static EquipmentSlot getPreferredEquipmentSlot(ItemStack itemStack) {
        // 1.21.1: LivingEntity.getEquipmentSlotForItem became an instance method.
        // Replicate the static behavior: ArmorItem → slot by type; shield → OFFHAND; else MAINHAND.
        Item item = itemStack.getItem();
        if (item instanceof ArmorItem armor) {
            return armor.getEquipmentSlot();
        }
        if (item instanceof ShieldItem) {
            return EquipmentSlot.OFFHAND;
        }
        return EquipmentSlot.MAINHAND;
    }

    public static EquipmentSlot getPreferredEquipmentSlot(ItemLike item) {
        return getPreferredEquipmentSlot(new ItemStack(item));
    }

    private ItemUtil() {
    }
}
