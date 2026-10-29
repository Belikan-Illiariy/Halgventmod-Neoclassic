package com.nrdhvnstd.halgventmod.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Невидимое сиденье на стуле: НПС садится на него верхом (startRiding), и модель игрока сама принимает позу сидящего.
 * Живёт, пока на нём кто-то сидит; без седока — до RESERVE_TICKS (занято на время дороги к стулу), потом исчезает;
 * дольше MAX_TICKS не живёт совсем (на случай, если НПС забыл встать). Сохраняется вместе с седоком, чтобы тот
 * не пропал при сохранении мира.
 */
public class SeatEntity extends Entity {
    private static final int RESERVE_TICKS = 1200;  // стул занят, пока НПС идёт к нему: 1 минута
    private static final int MAX_TICKS = 4800;      // ⚠️ дольше 4 минут не сидят

    private boolean hadPassenger;

    public SeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        boolean occupied = !getPassengers().isEmpty();
        hadPassenger |= occupied;
        if (tickCount > MAX_TICKS) {
            ejectPassengers();
            discard();
        } else if (hadPassenger ? !occupied : tickCount > RESERVE_TICKS) {
            discard();
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
