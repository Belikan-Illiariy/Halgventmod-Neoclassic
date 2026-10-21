package com.nrdhvnstd.halgventmod.brewing;

import java.util.List;

import com.nrdhvnstd.halgventmod.cooking.FoodQuality;
import com.nrdhvnstd.halgventmod.cooking.HearthBlock;
import com.nrdhvnstd.halgventmod.cooking.HearthBlockEntity;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Перегонный куб — чугунный котёл над огнём (горящий очаг, костёр или огонь прямо под ним). E пивом, вином,
 * сидром или медовухой — залить (до {@link #CAPACITY}, одно за раз). Каждые {@link #DISTILL_TIME} единиц жара
 * перегоняется порция; из Brewing.DISTILL_RATIO порций — одна крепкого (E склянкой). Жар 2 — в самый раз;
 * жар 3 (пышущий очаг, лава) — быстрее, но «первач»: качество −1; жар 1 — медленно.
 * Пиво — хлебное вино, вино — бренди, сидр — яблочный бренди, медовуха — крепкий мёд.
 * Shift + E пустой рукой — забрать не перегнанное.
 */
public class StillBlockEntity extends StationBlockEntity {
    // Меняйте здесь ⚠️
    public static final int CAPACITY = 8;
    public static final int DISTILL_TIME = 1200;

    private ItemStack input = ItemStack.EMPTY;
    private int inputs;
    private int qualitySum;
    private int used;
    private int harsh;
    private int progress;
    private int spirits;
    private int spiritQuality = FoodQuality.NORMAL;
    private String spiritKind = "";

    public StillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STILL.get(), pos, state);
    }

    public boolean working() {
        return inputs > 0 && heat(level, worldPosition) > 0;
    }

    @Override
    public List<ItemStack> contents() {
        return inputs > 0 ? List.of(input.copyWithCount(inputs)) : List.of();
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.brewing." + key, args), true);
    }

    /** Жар снизу: очаг — свой (1–3), костёр и огонь — 2, лава и магма — 3. */
    public static int heat(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState state = level.getBlockState(below);
        if (state.getBlock() instanceof HearthBlock && level.getBlockEntity(below) instanceof HearthBlockEntity hearth) {
            return hearth.heat();
        }
        if (CampfireBlock.isLitCampfire(state) || state.is(BlockTags.FIRE)) {
            return 2;
        }
        return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.LAVA) ? 3 : 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StillBlockEntity still) {
        if (still.inputs <= 0) {
            return;
        }
        int heat = heat(level, pos);
        if (heat <= 0) {
            return;
        }
        still.progress += heat;
        if (heat >= 3) {
            still.harsh++;
        }
        if (still.progress < DISTILL_TIME) {
            if (level.getGameTime() % 100 == 0) {
                still.setChanged();
            }
            return;
        }
        still.progress = 0;
        still.inputs--;
        still.used++;
        if (still.used >= Brewing.DISTILL_RATIO) {
            int quality = Math.round((float) still.qualitySum / still.used) - (still.harsh > DISTILL_TIME ? 1 : 0);
            still.spiritQuality = still.spirits == 0 ? quality : Math.min(still.spiritQuality, quality);
            still.spirits++;
            still.used = 0;
            still.qualitySum = 0;
            still.harsh = 0;
            still.sound(SoundEvents.BREWING_STAND_BREW, 0.8F, 1.2F);
        }
        if (still.inputs == 0) {
            still.input = ItemStack.EMPTY;
            level.setBlock(pos, state.setValue(StillBlock.FULL, false), 3);
        }
        still.changed();
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        String kind = Brewing.distillsInto(stack);
        if (kind != null) {
            if (inputs > 0 && !stack.is(input.getItem())) {
                tell(player, "still_mixed");
                return true;
            }
            if (spirits > 0 && !kind.equals(spiritKind)) {
                tell(player, "still_take_first");
                return true;
            }
            if (inputs >= CAPACITY) {
                tell(player, "still_full");
                return true;
            }
            input = stack.copyWithCount(1);
            inputs++;
            qualitySum += FoodQuality.get(stack);
            spiritKind = kind;
            consumeOne(player, stack);
            give(player, new ItemStack(Items.GLASS_BOTTLE));
            sound(SoundEvents.BOTTLE_EMPTY, 1.0F, 0.9F);
            if (!getBlockState().getValue(StillBlock.FULL)) {
                level.setBlock(worldPosition, getBlockState().setValue(StillBlock.FULL, true), 3);
            }
            status(player);
            changed();
            return true;
        }
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (spirits <= 0) {
                status(player);
                return true;
            }
            consumeOne(player, stack);
            give(player, FoodQuality.set(Brewing.make(ModItems.SPIRIT.get(), Brew.of(spiritKind)), spiritQuality));
            spirits--;
            sound(SoundEvents.BOTTLE_FILL, 1.0F, 1.1F);
            changed();
            return true;
        }
        if (!stack.isEmpty()) {
            return false;
        }
        if (player.isShiftKeyDown() && inputs > 0) {
            for (int i = 0; i < inputs; i++) {
                give(player, input.copyWithCount(1));
            }
            inputs = 0;
            used = 0;
            qualitySum = 0;
            progress = 0;
            input = ItemStack.EMPTY;
            level.setBlock(worldPosition, getBlockState().setValue(StillBlock.FULL, false), 3);
            changed();
            return true;
        }
        status(player);
        return true;
    }

    private void status(Player player) {
        int heat = heat(level, worldPosition);
        tell(player, heat == 0 ? "still_cold" : heat >= 3 ? "still_hot" : "still_status",
                inputs, spirits, Brewing.DISTILL_RATIO);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!input.isEmpty()) {
            tag.put("Input", input.save(registries));
        }
        tag.putInt("Inputs", inputs);
        tag.putInt("QualitySum", qualitySum);
        tag.putInt("Used", used);
        tag.putInt("Harsh", harsh);
        tag.putInt("Progress", progress);
        tag.putInt("Spirits", spirits);
        tag.putInt("SpiritQuality", spiritQuality);
        tag.putString("SpiritKind", spiritKind);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input = tag.contains("Input") ? ItemStack.parseOptional(registries, tag.getCompound("Input")) : ItemStack.EMPTY;
        inputs = tag.getInt("Inputs");
        qualitySum = tag.getInt("QualitySum");
        used = tag.getInt("Used");
        harsh = tag.getInt("Harsh");
        progress = tag.getInt("Progress");
        spirits = tag.getInt("Spirits");
        spiritQuality = tag.contains("SpiritQuality") ? tag.getInt("SpiritQuality") : FoodQuality.NORMAL;
        spiritKind = tag.getString("SpiritKind");
    }
}
