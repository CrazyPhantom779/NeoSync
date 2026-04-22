package com.breakinblocks.neosync.common.utils.nbt;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class NbtSerializerFactoryBuilder<TTarget> {
    private static final Map<Class<?>, BiFunction<CompoundTag, String, ?>> NBT_GETTERS;
    private static final Map<Class<?>, TriConsumer<CompoundTag, String, ?>> NBT_SETTERS;

    private final Collection<BiConsumer<TTarget, CompoundTag>> readers;
    private final Collection<BiConsumer<TTarget, CompoundTag>> writers;

    public NbtSerializerFactoryBuilder() {
        this.readers = new ArrayList<>();
        this.writers = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public <TProperty> NbtSerializerFactoryBuilder<TTarget> add(Class<TProperty> type, String key, Function<TTarget, TProperty> getter, BiConsumer<TTarget, TProperty> setter) {
        if (getter != null) {
            TriConsumer<CompoundTag, String, TProperty> nbtSetter = (TriConsumer<CompoundTag, String, TProperty>)NBT_SETTERS.get(type);
            if (nbtSetter == null) {
                throw new UnsupportedOperationException();
            }
            this.writers.add((i, x) -> nbtSetter.accept(x, key, getter.apply(i)));
        }

        if (setter != null) {
            BiFunction<CompoundTag, String, TProperty> nbtGetter = (BiFunction<CompoundTag, String, TProperty>)NBT_GETTERS.get(type);
            if (nbtGetter == null) {
                throw new UnsupportedOperationException();
            }
            this.readers.add((i, x) -> setter.accept(i, nbtGetter.apply(x, key)));
        }

        return this;
    }

    public NbtSerializerFactory<TTarget> build() {
        return new NbtSerializerFactory<>(this.readers, this.writers);
    }

    private static BiFunction<CompoundTag, String, ?> getOrDefault(BiFunction<CompoundTag, String, ?> f) {
        return (nbt, key) -> nbt.contains(key) ? f.apply(nbt, key) : null;
    }

    private static TriConsumer<CompoundTag, String, ?> setIfNotNull(TriConsumer<CompoundTag, String, Object> f) {
        return (nbt, key, x) -> {
            if (x != null) {
                f.accept(nbt, key, x);
            }
        };
    }

    static {
        NBT_GETTERS = new HashMap<>();
        NBT_GETTERS.put(Boolean.class, getOrDefault(CompoundTag::getBoolean));
        NBT_GETTERS.put(Byte.class, getOrDefault(CompoundTag::getByte));
        NBT_GETTERS.put(Double.class, getOrDefault(CompoundTag::getDouble));
        NBT_GETTERS.put(Float.class, getOrDefault(CompoundTag::getFloat));
        NBT_GETTERS.put(Integer.class, getOrDefault(CompoundTag::getInt));
        NBT_GETTERS.put(Long.class, getOrDefault(CompoundTag::getLong));
        NBT_GETTERS.put(Short.class, getOrDefault(CompoundTag::getShort));
        NBT_GETTERS.put(String.class, getOrDefault(CompoundTag::getString));
        NBT_GETTERS.put(ResourceLocation.class, getOrDefault((x, key) -> ResourceLocation.parse(x.getString(key))));
        NBT_GETTERS.put(UUID.class, getOrDefault(CompoundTag::getUUID));
        NBT_GETTERS.put(CompoundTag.class, getOrDefault(CompoundTag::getCompound));
        NBT_GETTERS.put(ListTag.class, getOrDefault(CompoundTag::get));
        NBT_GETTERS.put(BlockPos.class, getOrDefault((nbt, key) -> {
            CompoundTag compound = nbt.getCompound(key);
            return new BlockPos(compound.getInt("x"), compound.getInt("y"), compound.getInt("z"));
        }));

        NBT_SETTERS = new HashMap<>();
        NBT_SETTERS.put(Boolean.class, setIfNotNull((nbt, key, x) -> nbt.putBoolean(key, (boolean)x)));
        NBT_SETTERS.put(Byte.class, setIfNotNull((nbt, key, x) -> nbt.putByte(key, (byte)x)));
        NBT_SETTERS.put(Double.class, setIfNotNull((nbt, key, x) -> nbt.putDouble(key, (double)x)));
        NBT_SETTERS.put(Float.class, setIfNotNull((nbt, key, x) -> nbt.putFloat(key, (float)x)));
        NBT_SETTERS.put(Integer.class, setIfNotNull((nbt, key, x) -> nbt.putInt(key, (int)x)));
        NBT_SETTERS.put(Long.class, setIfNotNull((nbt, key, x) -> nbt.putLong(key, (long)x)));
        NBT_SETTERS.put(Short.class, setIfNotNull((nbt, key, x) -> nbt.putShort(key, (short)x)));
        NBT_SETTERS.put(String.class, setIfNotNull((nbt, key, x) -> nbt.putString(key, (String)x)));
        NBT_SETTERS.put(ResourceLocation.class, setIfNotNull((nbt, key, x) -> nbt.putString(key, x.toString())));
        NBT_SETTERS.put(UUID.class, setIfNotNull((nbt, key, x) -> nbt.putUUID(key, (UUID)x)));
        NBT_SETTERS.put(CompoundTag.class, setIfNotNull((nbt, key, x) -> nbt.put(key, (CompoundTag)x)));
        NBT_SETTERS.put(ListTag.class, setIfNotNull((nbt, key, x) -> nbt.put(key, (ListTag)x)));
        NBT_SETTERS.put(BlockPos.class, setIfNotNull((nbt, key, x) -> {
            BlockPos pos = (BlockPos)x;
            CompoundTag compound = new CompoundTag();
            compound.putInt("x", pos.getX());
            compound.putInt("y", pos.getY());
            compound.putInt("z", pos.getZ());
            nbt.put(key, compound);
        }));
    }

    @FunctionalInterface
    private interface TriConsumer<T, K, V> {
        void accept(T arg1, K arg2, V arg3);
    }
}