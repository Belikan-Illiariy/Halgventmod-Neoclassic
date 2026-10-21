package com.nrdhvnstd.halgventmod.leather;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Общее у станций кожевника: синхронизация с клиентом (содержимое видно в мире), сообщения, звуки, ритм работы. */
public abstract class StationBlockEntity extends BlockEntity {
    /** Удар, нанесённый раньше, чем замах восстановился на столько, — «спешка»: может испортить работу. */
    public static final float STEADY_STRIKE = 0.9F;

    protected StationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Использование станции: {@code stack} — предмет в руке (пустой — пустая рука). Только на сервере.
     * @return true — щелчок обработан
     */
    public abstract boolean use(Player player, InteractionHand hand, ItemStack stack);

    /** То же, но знает, куда пришёлся щелчок (точка в мире). По умолчанию место не важно. */
    public boolean use(Player player, InteractionHand hand, ItemStack stack, Vec3 hit) {
        return use(player, hand, stack);
    }

    /** Что выпадет, если станцию сломать. */
    public abstract List<ItemStack> contents();

    // ---------------------------------------------------------------- помощники

    protected static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.leather." + key, args), true);
    }

    protected void sound(SoundEvent sound, float volume, float pitch) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    /** Крошки предмета над станцией — видно, что работа идёт. */
    protected void crumbs(ItemStack stack, double height, int count) {
        if (level instanceof ServerLevel server && !stack.isEmpty()) {
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack), worldPosition.getX() + 0.5,
                    worldPosition.getY() + height, worldPosition.getZ() + 0.5, count, 0.2, 0.05, 0.2, 0.05);
        }
    }

    /**
     * Удар инструментом: ровный (замах восстановился) или спешный. Сбрасывает замах и тупит инструмент.
     * @return true — удар ровный
     */
    protected static boolean strike(Player player, InteractionHand hand, ItemStack tool) {
        boolean steady = player.getAttackStrengthScale(0.5F) >= STEADY_STRIKE;
        player.resetAttackStrengthTicker();
        player.swing(hand, true);
        damage(player, hand, tool, 1);
        return steady;
    }

    protected static void damage(Player player, InteractionHand hand, ItemStack tool, int amount) {
        if (!player.getAbilities().instabuild && tool.isDamageableItem()) {
            tool.hurtAndBreak(amount, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
    }

    /**
     * Отдать предмет игроку, как при подборе: сначала дополнить стопку такой же вещи (рука, карманы, сумки),
     * потом свободная ячейка; что не влезло — бросить рядом. В пустую руку специально не кладём.
     */
    protected static void give(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    protected static void consumeOne(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    /** Сохранить и разослать клиентам: содержимое станции рисуется в мире. */
    protected void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
