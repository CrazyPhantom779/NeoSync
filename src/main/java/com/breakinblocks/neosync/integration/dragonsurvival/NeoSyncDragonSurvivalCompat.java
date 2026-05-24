package com.breakinblocks.neosync.integration.dragonsurvival;

import com.breakinblocks.neosync.api.shell.ShellStateComponentFactoryRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

public final class NeoSyncDragonSurvivalCompat {
    private static final String MOD_ID = "dragonsurvival";

    private static Class<?> dragonStateProviderClass;
    private static Class<?> dragonStateHandlerClass;

    private static Method getDataMethod;
    private static Method isDragonMethod;
    private static Method serializeNbtMethod;
    private static Method deserializeNbtMethod;
    private static Method getGrowthMethod;
    private static Method setGrowthMethod;
    private static Method getDesiredGrowthMethod;
    private static Method setDesiredGrowthMethod;
    private static Method recompileCurrentSkinMethod;

    private NeoSyncDragonSurvivalCompat() {
    }

    public static void init() {
        if (!isLoaded()) {
            return;
        }

        ShellStateComponentFactoryRegistry.getInstance().register(
                DragonSurvivalShellStateComponent::empty,
                DragonSurvivalShellStateComponent::of
        );
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static CompoundTag captureDragonData(ServerPlayer player) {
        if (!isLoaded()) {
            return new CompoundTag();
        }

        try {
            ensureLoaded();

            Object handler = getDataMethod.invoke(null, player);

            if (!(Boolean) isDragonMethod.invoke(handler)) {
                return new CompoundTag();
            }

            Object result = serializeNbtMethod.invoke(handler, player.registryAccess());
            return result instanceof CompoundTag tag ? tag.copy() : new CompoundTag();
        } catch (Throwable ignored) {
            return new CompoundTag();
        }
    }

    public static void applyDragonData(ServerPlayer player, CompoundTag dragonData) {
        if (!isLoaded() || dragonData == null || dragonData.isEmpty()) {
            clearDragonData(player);
            return;
        }

        try {
            ensureLoaded();

            Object handler = getDataMethod.invoke(null, player);
            HolderLookup.Provider provider = player.registryAccess();

            deserializeNbtMethod.invoke(handler, provider, dragonData);

            double growth = (Double) getGrowthMethod.invoke(handler);
            double desiredGrowth = (Double) getDesiredGrowthMethod.invoke(handler);

            setGrowthMethod.invoke(handler, player, growth, true);
            setDesiredGrowthMethod.invoke(handler, player, desiredGrowth);
            recompileCurrentSkinMethod.invoke(handler);

            player.refreshDimensions();
        } catch (Throwable ignored) {
            // Dragon Survival compatibility is optional.
        }
    }

    private static void clearDragonData(ServerPlayer player) {
        if (!isLoaded()) {
            return;
        }

        try {
            ensureLoaded();

            Object handler = getDataMethod.invoke(null, player);
            HolderLookup.Provider provider = player.registryAccess();

            deserializeNbtMethod.invoke(handler, provider, new CompoundTag());
            player.refreshDimensions();
        } catch (Throwable ignored) {
            // Dragon Survival compatibility is optional.
        }
    }

    private static void ensureLoaded() throws ReflectiveOperationException {
        if (dragonStateProviderClass != null) {
            return;
        }

        dragonStateProviderClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider");
        dragonStateHandlerClass = Class.forName("by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateHandler");

        getDataMethod = dragonStateProviderClass.getMethod("getData", Player.class);
        isDragonMethod = dragonStateHandlerClass.getMethod("isDragon");
        serializeNbtMethod = dragonStateHandlerClass.getMethod("serializeNBT", HolderLookup.Provider.class);
        deserializeNbtMethod = dragonStateHandlerClass.getMethod("deserializeNBT", HolderLookup.Provider.class, CompoundTag.class);

        getGrowthMethod = dragonStateHandlerClass.getMethod("getGrowth");
        setGrowthMethod = dragonStateHandlerClass.getMethod("setGrowth", Player.class, double.class, boolean.class);
        getDesiredGrowthMethod = dragonStateHandlerClass.getMethod("getDesiredGrowth");
        setDesiredGrowthMethod = dragonStateHandlerClass.getMethod("setDesiredGrowth", Player.class, double.class);

        recompileCurrentSkinMethod = dragonStateHandlerClass.getMethod("recompileCurrentSkin");
    }
}