package com.breakinblocks.neosync.integration.jade;

import com.breakinblocks.neosync.NeoSync;
import com.breakinblocks.neosync.common.block.entity.TreadmillBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public final class TreadmillComponentProvider implements IBlockComponentProvider {
    public static final TreadmillComponentProvider INSTANCE = new TreadmillComponentProvider();
    private static final ResourceLocation UID = NeoSync.locate("treadmill");

    private TreadmillComponentProvider() {}

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof TreadmillBlockEntity treadmill)) {
            return;
        }

        if (treadmill.isOverheated()) {
            tooltip.add(Component.translatable("jade.neosync.overheated")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        } else if (treadmill.getMaxEnergyStored() == 0) {
            tooltip.add(Component.translatable("jade.neosync.idle").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}

