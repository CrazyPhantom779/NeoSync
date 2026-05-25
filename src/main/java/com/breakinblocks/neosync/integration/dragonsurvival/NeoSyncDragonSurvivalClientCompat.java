package com.breakinblocks.neosync.integration.dragonsurvival;

import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class NeoSyncDragonSurvivalClientCompat {
    private static final String GENERATED_CLASS_NAME =
        "com.breakinblocks.neosync.integration.dragonsurvival.NeoSyncGeneratedDragonPreviewPlayer";
    private static final String GENERATED_INTERNAL_NAME =
        "com/breakinblocks/neosync/integration/dragonsurvival/NeoSyncGeneratedDragonPreviewPlayer";
    private static final String FAKE_PLAYER_INTERNAL_NAME =
        "by/dragonsurvivalteam/dragonsurvival/client/util/FakeClientPlayer";

    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(900000);
    private static final AtomicInteger PREVIEW_PLAYER_COUNTER = new AtomicInteger();
    private static final Map<UUID, AbstractClientPlayer> PREVIEW_PLAYERS = new ConcurrentHashMap<>();

    private static Class<?> previewPlayerClass;
    private static Constructor<?> previewPlayerConstructor;
    private static Method setRenderPositionMethod;
    private static Field handlerField;
    private static Method deserializeNbtMethod;
    private static Method getGrowthMethod;
    private static Method setGrowthMethod;
    private static Method getDesiredGrowthMethod;
    private static Method setDesiredGrowthMethod;
    private static Method recompileCurrentSkinMethod;
    private static Method isDragonMethod;

    private NeoSyncDragonSurvivalClientCompat() {
    }

    public static void clear() {
        PREVIEW_PLAYERS.clear();
    }

    public static boolean hasDragonShellData(@Nullable ShellState shell) {
        if (shell == null || !NeoSyncDragonSurvivalCompat.isLoaded()) {
            return false;
        }

        DragonSurvivalShellStateComponent component = shell.getComponent().as(DragonSurvivalShellStateComponent.class);
        return component != null && component.hasDragonData();
    }

    @Nullable
    public static AbstractClientPlayer getRenderPlayer(ShellState shell, boolean previewMode) {
        if (!hasDragonShellData(shell)) {
            return null;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return null;
        }

        try {
            ensureLoaded();

            AbstractClientPlayer previewPlayer = PREVIEW_PLAYERS.get(shell.getUuid());
            if (previewPlayer == null || previewPlayer.level() != client.level) {
                previewPlayer = (AbstractClientPlayer) previewPlayerConstructor.newInstance(
                    PREVIEW_PLAYER_COUNTER.getAndIncrement()
                );
                previewPlayer.setId(ENTITY_ID_COUNTER.getAndIncrement());
                PREVIEW_PLAYERS.put(shell.getUuid(), previewPlayer);
            }

            Vec3 renderPos = previewMode
                ? Vec3.ZERO
                : new Vec3(shell.getPos().getX() + 0.5D, shell.getPos().getY(), shell.getPos().getZ() + 0.5D);

            previewPlayer.moveTo(renderPos.x, renderPos.y, renderPos.z, 0.0F, 0.0F);
            previewPlayer.setDeltaMovement(Vec3.ZERO);
            setRenderPositionMethod.invoke(previewPlayer, renderPos);

            shell.getInventory().copyTo(previewPlayer.getInventory());

            DragonSurvivalShellStateComponent component =
                shell.getComponent().as(DragonSurvivalShellStateComponent.class);
            if (component == null || !component.hasDragonData()) {
                return null;
            }

            Object handler = handlerField.get(previewPlayer);
            HolderLookup.Provider registries = previewPlayer.registryAccess();
            CompoundTag dragonData = component.getDragonData();

            deserializeNbtMethod.invoke(handler, registries, dragonData);

            double growth = ((Number) getGrowthMethod.invoke(handler)).doubleValue();
            double desiredGrowth = ((Number) getDesiredGrowthMethod.invoke(handler)).doubleValue();

            setGrowthMethod.invoke(handler, previewPlayer, growth, true);
            setDesiredGrowthMethod.invoke(handler, previewPlayer, desiredGrowth);
            recompileCurrentSkinMethod.invoke(handler);
            previewPlayer.refreshDimensions();

            boolean isDragon = (Boolean) isDragonMethod.invoke(handler);
            if (!isDragon) {
                NeoSyncDebug.warn(
                    "dragon-client",
                    "prepared preview player but handler is not dragon shell={} preview={}",
                    shell.getUuid(),
                    previewMode
                );
                return null;
            }

            NeoSyncDebug.info(
                "dragon-client",
                "prepared dragon preview player shell={} preview={} class={}",
                shell.getUuid(),
                previewMode,
                previewPlayer.getClass().getName()
            );
            return previewPlayer;
        } catch (Throwable throwable) {
            NeoSyncDebug.error(
                "dragon-client",
                "failed to prepare dragon preview player shell={} preview={}",
                throwable,
                shell.getUuid(),
                previewMode
            );
            return null;
        }
    }

    private static void ensureLoaded() throws Throwable {
        if (previewPlayerClass != null) {
            return;
        }

        previewPlayerClass = definePreviewSubclassIfNeeded();
        previewPlayerConstructor = previewPlayerClass.getConstructor(int.class);
        setRenderPositionMethod = previewPlayerClass.getMethod("neosync$setRenderPosition", Vec3.class);

        Class<?> dragonStateHandlerClass =
            Class.forName("by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateHandler");
        handlerField = previewPlayerClass.getField("handler");
        deserializeNbtMethod =
            dragonStateHandlerClass.getMethod("deserializeNBT", HolderLookup.Provider.class, CompoundTag.class);
        getGrowthMethod = dragonStateHandlerClass.getMethod("getGrowth");
        setGrowthMethod = dragonStateHandlerClass.getMethod("setGrowth", Player.class, double.class, boolean.class);
        getDesiredGrowthMethod = dragonStateHandlerClass.getMethod("getDesiredGrowth");
        setDesiredGrowthMethod =
            dragonStateHandlerClass.getMethod("setDesiredGrowth", Player.class, double.class);
        recompileCurrentSkinMethod = dragonStateHandlerClass.getMethod("recompileCurrentSkin");
        isDragonMethod = dragonStateHandlerClass.getMethod("isDragon");
    }

    private static Class<?> definePreviewSubclassIfNeeded() throws Throwable {
        try {
            return Class.forName(GENERATED_CLASS_NAME);
        } catch (ClassNotFoundException ignored) {
        }

        Class.forName(FAKE_PLAYER_INTERNAL_NAME.replace('/', '.'));

        byte[] bytecode = createPreviewSubclassBytecode();
        return MethodHandles.lookup().defineClass(bytecode);
    }

    private static byte[] createPreviewSubclassBytecode() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        writer.visit(
            Opcodes.V21,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
            GENERATED_INTERNAL_NAME,
            null,
            FAKE_PLAYER_INTERNAL_NAME,
            null
        );

        writer.visitField(
            Opcodes.ACC_PRIVATE,
            "neosync$renderPosition",
            "Lnet/minecraft/world/phys/Vec3;",
            null,
            null
        ).visitEnd();

        writer.visitField(
            Opcodes.ACC_PRIVATE,
            "neosync$renderBlockPos",
            "Lnet/minecraft/core/BlockPos;",
            null,
            null
        ).visitEnd();

        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ILOAD, 1);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, FAKE_PLAYER_INTERNAL_NAME, "<init>", "(I)V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitFieldInsn(
            Opcodes.GETSTATIC,
            "net/minecraft/world/phys/Vec3",
            "ZERO",
            "Lnet/minecraft/world/phys/Vec3;"
        );
        ctor.visitFieldInsn(
            Opcodes.PUTFIELD,
            GENERATED_INTERNAL_NAME,
            "neosync$renderPosition",
            "Lnet/minecraft/world/phys/Vec3;"
        );
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitFieldInsn(
            Opcodes.GETSTATIC,
            "net/minecraft/core/BlockPos",
            "ZERO",
            "Lnet/minecraft/core/BlockPos;"
        );
        ctor.visitFieldInsn(
            Opcodes.PUTFIELD,
            GENERATED_INTERNAL_NAME,
            "neosync$renderBlockPos",
            "Lnet/minecraft/core/BlockPos;"
        );
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitEnd();

        MethodVisitor setter = writer.visitMethod(
            Opcodes.ACC_PUBLIC,
            "neosync$setRenderPosition",
            "(Lnet/minecraft/world/phys/Vec3;)V",
            null,
            null
        );
        setter.visitCode();
        Label notNull = new Label();
        setter.visitVarInsn(Opcodes.ALOAD, 1);
        setter.visitJumpInsn(Opcodes.IFNONNULL, notNull);
        setter.visitVarInsn(Opcodes.ALOAD, 0);
        setter.visitFieldInsn(
            Opcodes.GETSTATIC,
            "net/minecraft/world/phys/Vec3",
            "ZERO",
            "Lnet/minecraft/world/phys/Vec3;"
        );
        setter.visitFieldInsn(
            Opcodes.PUTFIELD,
            GENERATED_INTERNAL_NAME,
            "neosync$renderPosition",
            "Lnet/minecraft/world/phys/Vec3;"
        );
        setter.visitVarInsn(Opcodes.ALOAD, 0);
        setter.visitFieldInsn(
            Opcodes.GETSTATIC,
            "net/minecraft/core/BlockPos",
            "ZERO",
            "Lnet/minecraft/core/BlockPos;"
        );
        setter.visitFieldInsn(
            Opcodes.PUTFIELD,
            GENERATED_INTERNAL_NAME,
            "neosync$renderBlockPos",
            "Lnet/minecraft/core/BlockPos;"
        );
        setter.visitInsn(Opcodes.RETURN);

        setter.visitLabel(notNull);
        setter.visitVarInsn(Opcodes.ALOAD, 0);
        setter.visitVarInsn(Opcodes.ALOAD, 1);
        setter.visitFieldInsn(
            Opcodes.PUTFIELD,
            GENERATED_INTERNAL_NAME,
            "neosync$renderPosition",
            "Lnet/minecraft/world/phys/Vec3;"
        );
        setter.visitVarInsn(Opcodes.ALOAD, 0);
        setter.visitVarInsn(Opcodes.ALOAD, 1);
        setter.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "net/minecraft/core/BlockPos",
            "containing",
            "(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/core/BlockPos;",
            false
        );
        setter.visitFieldInsn(
            Opcodes.PUTFIELD,
            GENERATED_INTERNAL_NAME,
            "neosync$renderBlockPos",
            "Lnet/minecraft/core/BlockPos;"
        );
        setter.visitInsn(Opcodes.RETURN);
        setter.visitEnd();

        MethodVisitor position = writer.visitMethod(
            Opcodes.ACC_PUBLIC,
            "position",
            "()Lnet/minecraft/world/phys/Vec3;",
            null,
            null
        );
        position.visitCode();
        position.visitVarInsn(Opcodes.ALOAD, 0);
        position.visitFieldInsn(
            Opcodes.GETFIELD,
            GENERATED_INTERNAL_NAME,
            "neosync$renderPosition",
            "Lnet/minecraft/world/phys/Vec3;"
        );
        Label positionNotNull = new Label();
        position.visitJumpInsn(Opcodes.IFNONNULL, positionNotNull);
        position.visitFieldInsn(
            Opcodes.GETSTATIC,
            "net/minecraft/world/phys/Vec3",
            "ZERO",
            "Lnet/minecraft/world/phys/Vec3;"
        );
        position.visitInsn(Opcodes.ARETURN);
        position.visitLabel(positionNotNull);
        position.visitVarInsn(Opcodes.ALOAD, 0);
        position.visitFieldInsn(
            Opcodes.GETFIELD,
            GENERATED_INTERNAL_NAME,
            "neosync$renderPosition",
            "Lnet/minecraft/world/phys/Vec3;"
        );
        position.visitInsn(Opcodes.ARETURN);
        position.visitEnd();

        MethodVisitor blockPosition = writer.visitMethod(
            Opcodes.ACC_PUBLIC,
            "blockPosition",
            "()Lnet/minecraft/core/BlockPos;",
            null,
            null
        );
        blockPosition.visitCode();
        blockPosition.visitVarInsn(Opcodes.ALOAD, 0);
        blockPosition.visitFieldInsn(
            Opcodes.GETFIELD,
            GENERATED_INTERNAL_NAME,
            "neosync$renderBlockPos",
            "Lnet/minecraft/core/BlockPos;"
        );
        Label blockPosNotNull = new Label();
        blockPosition.visitJumpInsn(Opcodes.IFNONNULL, blockPosNotNull);
        blockPosition.visitFieldInsn(
            Opcodes.GETSTATIC,
            "net/minecraft/core/BlockPos",
            "ZERO",
            "Lnet/minecraft/core/BlockPos;"
        );
        blockPosition.visitInsn(Opcodes.ARETURN);
        blockPosition.visitLabel(blockPosNotNull);
        blockPosition.visitVarInsn(Opcodes.ALOAD, 0);
        blockPosition.visitFieldInsn(
            Opcodes.GETFIELD,
            GENERATED_INTERNAL_NAME,
            "neosync$renderBlockPos",
            "Lnet/minecraft/core/BlockPos;"
        );
        blockPosition.visitInsn(Opcodes.ARETURN);
        blockPosition.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }
}
