package com.nrdhvnstd.halgventmod.pottery;

import java.util.List;

import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.network.WheelOpenPayload;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Гончарный круг. E гончарной глиной — положить комья (до {@link #MAX_LUMPS}); E пустой рукой — сесть за круг
 * (окно WheelScreen: форма по вокселям); Shift + E пустой рукой — забрать глину. Готовую форму окно присылает
 * ({@link com.nrdhvnstd.halgventmod.network.WheelResultPayload}), здесь она проверяется заново и становится сырцом.
 *
 * <p>Качество сырца = качество глины + сходство с образцом − неровность (смятые и налепленные кольца, обвалы).
 */
public class PotteryWheelBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int MAX_LUMPS = 6;
    public static final double MATCH_FINE = 0.93;   // +1 к качеству
    public static final double MATCH_GOOD = 0.85;   // как глина
    public static final double MATCH_FAIR = 0.78;   // −1, ниже — −2
    public static final int UNEVEN_PER_LEVEL = 4;   // столько неровностей — минус ступень
    public static final int MAX_UNEVEN_PENALTY = 2;

    private int lumps;
    private int quality = Pottery.DEFAULT_QUALITY;
    private int crack;

    public PotteryWheelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POTTERY_WHEEL.get(), pos, state);
    }

    public int lumps() {
        return lumps;
    }

    @Override
    public List<ItemStack> contents() {
        return lumps > 0 ? List.of(clay(lumps)) : List.of();
    }

    private ItemStack clay(int count) {
        ItemStack stack = Pottery.setQuality(new ItemStack(ModItems.POTTER_CLAY.get(), count), quality);
        if (crack != 0) {
            stack.set(ModComponents.CLAY_CRACK.get(), crack);
        }
        return stack;
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(ModItems.POTTER_CLAY.get())) {
            int moved = Math.min(MAX_LUMPS - lumps, stack.getCount());
            if (moved <= 0) {
                Pottery.say(player, "wheel_full", MAX_LUMPS);
                return true;
            }
            // Разная глина вместе — по худшей
            quality = lumps == 0 ? Pottery.quality(stack) : Math.min(quality, Pottery.quality(stack));
            crack = lumps == 0 ? Pottery.crack(stack) : Math.max(crack, Pottery.crack(stack));
            lumps += moved;
            if (!player.getAbilities().instabuild) {
                stack.shrink(moved);
            }
            sound(SoundEvents.MUD_PLACE, 0.8F, 1.0F);
            Pottery.say(player, "wheel_lumps", lumps, MAX_LUMPS);
            changed();
            return true;
        }
        if (!stack.isEmpty()) {
            return false;
        }
        if (player.isShiftKeyDown()) {
            if (lumps > 0) {
                give(player, clay(lumps));
                lumps = 0;
                changed();
            }
            return true;
        }
        if (lumps <= 0) {
            Pottery.say(player, "wheel_empty");
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new WheelOpenPayload(worldPosition, lumps, quality));
        }
        return true;
    }

    /** Окно круга прислало готовую форму: проверить всё заново, отдать сырец. */
    public void finish(ServerPlayer player, int formIndex, int[] profile, int[] details, int uneven) {
        if (player.distanceToSqr(worldPosition.getCenter()) > 64 || profile.length != VesselGeometry.LAYERS) {
            return;
        }
        VesselForm form = VesselForm.byIndex(formIndex);
        if (lumps < form.lumps()) {
            Pottery.say(player, "wheel_not_enough", form.lumps());
            return;
        }
        int[] cleanProfile = profile.clone();
        for (int y = 0; y < cleanProfile.length; y++) {
            cleanProfile[y] &= (1 << VesselGeometry.RINGS) - 1;
        }
        int[] cleanDetails = VesselGeometry.normalize(details);
        String problem = VesselGeometry.problem(form, cleanProfile, cleanDetails, VesselGeometry.budget(form));
        if (problem != null) {
            player.displayClientMessage(Component.translatable("gui.halgventmod.wheel.problem." + problem), true);
            return;
        }
        double match = VesselGeometry.match(form, cleanProfile, cleanDetails);
        if (match < VesselGeometry.MIN_MATCH) {
            Pottery.say(player, "wheel_unlike", Component.translatable(form.langKey()), Math.round(match * 100));
            return;
        }
        int result = quality + matchBonus(match) - Math.min(MAX_UNEVEN_PENALTY, Math.max(0, uneven) / UNEVEN_PER_LEVEL);
        ItemStack greenware = new ItemStack(ModItems.GREENWARE.get());
        greenware.set(ModComponents.VESSEL.get(), new Vessel(form.id(), cleanProfile, cleanDetails));
        Pottery.setQuality(greenware, result);
        if (crack != 0) {
            greenware.set(ModComponents.CLAY_CRACK.get(), crack);
        }
        lumps -= form.lumps();
        give(player, greenware);
        sound(SoundEvents.MUD_BRICKS_PLACE, 1.0F, 1.2F);
        Pottery.say(player, "wheel_done", greenware.getHoverName(), Math.round(match * 100));
        changed();
    }

    private static int matchBonus(double match) {
        return match >= MATCH_FINE ? 1 : match >= MATCH_GOOD ? 0 : match >= MATCH_FAIR ? -1 : -2;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Lumps", lumps);
        tag.putInt("Quality", quality);
        tag.putInt("Crack", crack);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lumps = tag.getInt("Lumps");
        quality = tag.contains("Quality") ? tag.getInt("Quality") : Pottery.DEFAULT_QUALITY;
        crack = tag.getInt("Crack");
    }
}
