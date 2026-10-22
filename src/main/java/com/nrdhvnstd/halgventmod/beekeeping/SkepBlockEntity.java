package com.nrdhvnstd.halgventmod.beekeeping;

import java.util.List;

import com.nrdhvnstd.halgventmod.leather.LeatherTableBlockEntity;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Улей (плетёная колода). Живёт по календарю мода, раз в игровые сутки:
 * <ul>
 *   <li><b>пустой</b> весной и летом, если рядом не меньше {@link #MIN_FLOWERS} цветов (радиус {@link #RADIUS}),
 *       с шансом {@link #SETTLE_CHANCE} заселяется роем;</li>
 *   <li><b>с пчёлами</b> копит мёд до {@link #MAX_HONEY}: весной и осенью +1, летом +2 в сутки, зимой — ничего;
 *       от {@link #RICH_FLOWERS} цветов — ещё +1 (кроме зимы);</li>
 *   <li><b>зимовка</b>: в первые сутки зимы пчёлы съедают {@link #WINTER_FOOD} мёда; не хватило — рой гибнет.
 *       Оставляйте мёд на зиму.</li>
 * </ul>
 * На E: дымарём — окурить (пчёлы притихают на {@link #CALM_TICKS} тиков); ножом — срезать соты (весь мёд →
 * медовые соты); без дыма пчёлы жалят. Пустой рукой — посмотреть, как живёт улей.
 */
public class SkepBlockEntity extends StationBlockEntity {
    // Меняйте здесь ⚠️
    public static final int MAX_HONEY = 6;
    public static final int RADIUS = 5;
    public static final int MIN_FLOWERS = 4;
    public static final int RICH_FLOWERS = 12;
    public static final float SETTLE_CHANCE = 0.35F;
    public static final int WINTER_FOOD = 2;
    public static final int CALM_TICKS = 600;
    public static final int STING_SECONDS = 6;

    private boolean bees;
    private int honey;
    private long lastDay = -1;
    private long calmUntil;
    private int flowers;

    public SkepBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKEP.get(), pos, state);
    }

    public boolean hasBees() {
        return bees;
    }

    @Override
    public List<ItemStack> contents() {
        return honey > 0 ? List.of(new ItemStack(ModItems.HONEYCOMB_FULL.get(), honey)) : List.of();
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.bees." + key, args), true);
    }

    private int countFlowers() {
        int found = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    at.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    if (level.getBlockState(at).is(BlockTags.FLOWERS)) {
                        found++;
                    }
                }
            }
        }
        return found;
    }

    // ---------------------------------------------------------------- сутки

    public static void serverTick(Level level, BlockPos pos, BlockState state, SkepBlockEntity skep) {
        if (level.getGameTime() % 100 != 0) {
            return;
        }
        long today = Calendar.today(level);
        if (skep.lastDay < 0) {
            skep.lastDay = today;
            skep.flowers = skep.countFlowers();
            skep.setChanged();
            return;
        }
        boolean changed = false;
        // Пропущенные сутки (никого не было рядом) догоняем, но не больше месяца
        for (long day = Math.max(skep.lastDay + 1, today - Calendar.DAYS_PER_MONTH); day <= today; day++) {
            changed |= skep.liveDay(day);
        }
        skep.lastDay = today;
        if (changed) {
            skep.changed();
        }
    }

    private boolean liveDay(long day) {
        Calendar.Season season = Calendar.date(day).season();
        Calendar.Season yesterday = Calendar.date(day - 1).season();
        flowers = countFlowers();
        if (!bees) {
            boolean warm = season == Calendar.Season.SPRING || season == Calendar.Season.SUMMER;
            if (warm && flowers >= MIN_FLOWERS && level.random.nextFloat() < SETTLE_CHANCE) {
                bees = true;
                return true;
            }
            return false;
        }
        if (season == Calendar.Season.WINTER) {
            if (yesterday != Calendar.Season.WINTER) {
                honey -= WINTER_FOOD;
                if (honey < 0) {
                    honey = 0;
                    bees = false; // не хватило мёда на зиму
                }
                return true;
            }
            return false;
        }
        int gain = season == Calendar.Season.SUMMER ? 2 : 1;
        if (flowers >= RICH_FLOWERS) {
            gain++;
        }
        if (flowers < MIN_FLOWERS) {
            gain = 0; // без цветов не с чего брать
        }
        int before = honey;
        honey = Math.min(MAX_HONEY, honey + gain);
        return honey != before;
    }

    // ---------------------------------------------------------------- E

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(ModItems.BEE_SMOKER.get())) {
            calmUntil = level.getGameTime() + CALM_TICKS;
            damage(player, hand, stack, 1);
            sound(SoundEvents.CAMPFIRE_CRACKLE, 0.8F, 1.2F);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.6, worldPosition.getZ() + 0.5, 4, 0.15, 0.1, 0.15, 0.01);
            }
            tell(player, bees ? "calmed" : "empty_smoke");
            return true;
        }
        if (stack.is(LeatherTableBlockEntity.KNIVES)) {
            if (!bees && honey == 0) {
                tell(player, "empty");
                return true;
            }
            if (honey == 0) {
                tell(player, "no_honey");
                return true;
            }
            if (bees && level.getGameTime() > calmUntil) {
                player.hurt(level.damageSources().generic(), 2.0F);
                player.addEffect(new MobEffectInstance(MobEffects.POISON, STING_SECONDS * 20, 0));
                sound(SoundEvents.BEE_STING, 1.0F, 1.0F);
                tell(player, "stung");
            }
            give(player, new ItemStack(ModItems.HONEYCOMB_FULL.get(), honey));
            honey = 0;
            damage(player, hand, stack, 1);
            sound(SoundEvents.BEEHIVE_SHEAR, 1.0F, 1.0F);
            changed();
            return true;
        }
        if (!stack.isEmpty()) {
            return false;
        }
        report(player);
        return true;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ (и E пустой рукой): есть ли рой, сколько мёда и цветов рядом. */
    @Override
    public void report(Player player) {
        flowers = countFlowers();
        if (!bees) {
            tell(player, flowers >= MIN_FLOWERS ? "waiting" : "few_flowers", flowers, MIN_FLOWERS);
        } else {
            tell(player, "status", honey, MAX_HONEY, flowers);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Bees", bees);
        tag.putInt("Honey", honey);
        tag.putLong("LastDay", lastDay);
        tag.putLong("CalmUntil", calmUntil);
        tag.putInt("Flowers", flowers);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bees = tag.getBoolean("Bees");
        honey = tag.getInt("Honey");
        lastDay = tag.contains("LastDay") ? tag.getLong("LastDay") : -1;
        calmUntil = tag.getLong("CalmUntil");
        flowers = tag.getInt("Flowers");
    }
}
