package com.nrdhvnstd.halgventmod.sound;

import com.nrdhvnstd.halgventmod.client.sound.SoundClient;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Данные источника: id сцены и свой радиус (0 — из сцены). Клиенту уходят с чанком и при правке.
 * На клиенте источник сообщает о себе менеджеру звуков (SoundClient) при загрузке и удалении.
 */
public class SoundEmitterBlockEntity extends BlockEntity {
    public static final int MAX_RADIUS = 64; // ⚠️

    private String scene = "";
    private int radius;

    public SoundEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOUND_EMITTER.get(), pos, state);
    }

    public String scene() {
        return scene;
    }

    /** Сцена как id; null — не задана или неверный id. */
    public ResourceLocation sceneId() {
        return scene.isEmpty() ? null : ResourceLocation.tryParse(scene);
    }

    public int radius() {
        return radius;
    }

    /** Правка администрацией (сервер): сохранить и разослать клиентам. */
    public void set(String newScene, int newRadius) {
        scene = newScene;
        radius = newRadius;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        scene = tag.getString("scene");
        radius = Math.clamp(tag.getInt("radius"), 0, MAX_RADIUS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("scene", scene);
        tag.putInt("radius", radius);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            SoundClient.emitterLoaded(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            SoundClient.emitterRemoved(this);
        }
    }
}
