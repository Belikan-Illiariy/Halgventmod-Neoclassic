package com.nrdhvnstd.halgventmod.pottery;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Полка для сырца: {@link #SLOTS} места. E сырцом или кирпичом-сырцом — поставить, пустой рукой — снять
 * (то, на что смотрите). Сохнет по игровому времени:
 * <ul>
 *   <li>под крышей — {@link #ROOF_DAYS} сут., без трещин;</li>
 *   <li>под открытым небом — {@link #OPEN_DAYS} сут., но {@link #OPEN_CRACK}% трещин; в дождь размокает обратно в глину;</li>
 *   <li>летом ×{@link #SUMMER}, зимой ×{@link #WINTER}.</li>
 * </ul>
 * Подсохший до кожетвёрдого (от {@link #BURNISH_FROM} до {@link #BURNISH_TO} сушки) сырец лощат костью или кремнём:
 * в дыму горна он станет чернолощёным. Жирная глина и ручки трескаются чаще (Pottery.crackChance).
 */
public class DryingShelfBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int SLOTS = 4;
    public static final double ROOF_DAYS = 1.0;
    public static final double OPEN_DAYS = 0.5;
    public static final int OPEN_CRACK = 25;
    public static final double SUMMER = 0.75;
    public static final double WINTER = 1.5;
    public static final double BURNISH_FROM = 0.3;
    public static final double BURNISH_TO = 0.9;
    private static final long DAY = 24000L;
    // Верх нижней и верхней доски стеллажа
    public static final float LOWER = 2.0F / 16;
    public static final float UPPER = 9.5F / 16;

    private final ItemStack[] slots = new ItemStack[SLOTS];
    private final long[] progress = new long[SLOTS];
    private long lastDayTime = -1;

    public DryingShelfBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRYING_SHELF.get(), pos, state);
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
    }

    public ItemStack slot(int i) {
        return slots[i];
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) {
                all.add(stack.copy());
            }
        }
        return all;
    }

    private static boolean dryable(ItemStack stack) {
        return Pottery.isGreenware(stack) || stack.is(ModItems.RAW_BRICK.get());
    }

    private static boolean wet(ItemStack stack) {
        return !Pottery.has(stack, Pottery.DRIED) && !Pottery.has(stack, Pottery.CRACKED);
    }

    private boolean exposed() {
        return level.canSeeSky(worldPosition.above()) || level.canSeeSky(worldPosition);
    }

    /** Сколько суток сохнет здесь и в этот сезон. */
    private double days() {
        double days = exposed() ? OPEN_DAYS : ROOF_DAYS;
        Calendar.Season season = Calendar.date(Calendar.today(level)).season();
        return days * (season == Calendar.Season.SUMMER ? SUMMER : season == Calendar.Season.WINTER ? WINTER : 1.0);
    }

    // ---------------------------------------------------------------- ход времени

    public static void serverTick(Level level, BlockPos pos, BlockState state, DryingShelfBlockEntity shelf) {
        if (level.getGameTime() % 40 != 0) {
            return;
        }
        long now = level.getDayTime();
        long delta = shelf.lastDayTime < 0 ? 0 : Math.clamp(now - shelf.lastDayTime, 0, 4 * DAY);
        shelf.lastDayTime = now;
        boolean exposed = shelf.exposed();
        boolean rain = exposed && (level.isRainingAt(pos.above()) || level.isRainingAt(pos));
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = shelf.slots[i];
            if (stack.isEmpty() || !dryable(stack) || !wet(stack)) {
                continue; // размокшая глина лежит как есть — её снимают и несут в бочку
            }
            if (rain) {
                // Размок: снова мягкая глина того же замеса
                shelf.slots[i] = Pottery.clayOf(stack, Pottery.lumps(stack));
                shelf.progress[i] = 0;
                shelf.sound(SoundEvents.MUD_STEP, 0.6F, 0.8F);
                changed = true;
                continue;
            }
            shelf.progress[i] += delta;
            if (shelf.progress[i] >= (long) (shelf.days() * DAY)) {
                int chance = Pottery.crackChance(stack, exposed ? OPEN_CRACK : 0);
                boolean cracked = level.random.nextInt(100) < chance;
                Pottery.dried(stack, cracked);
                shelf.progress[i] = 0;
                shelf.sound(cracked ? SoundEvents.DECORATED_POT_SHATTER : SoundEvents.MUD_BRICKS_STEP, 0.7F, 1.2F);
                changed = true;
            }
        }
        if (changed) {
            shelf.changed();
        } else {
            shelf.setChanged();
        }
    }

    // ---------------------------------------------------------------- E

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        return use(player, hand, stack, worldPosition.getCenter());
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack, Vec3 hit) {
        int aimed = slotAt(hit);
        if (stack.isEmpty()) {
            int i = !slots[aimed].isEmpty() ? aimed : lastFilled();
            if (i < 0) {
                Pottery.say(player, "shelf_empty");
                return true;
            }
            if (wet(slots[i]) && Pottery.isGreenware(slots[i]) && progress[i] > 0) {
                Pottery.say(player, "shelf_not_dry", percent(i));
            }
            give(player, slots[i]);
            slots[i] = ItemStack.EMPTY;
            progress[i] = 0;
            sound(SoundEvents.DECORATED_POT_HIT, 0.6F, 1.2F);
            changed();
            return true;
        }
        if (stack.is(Items.BONE) || stack.is(Items.FLINT)) {
            return burnish(player, hand, stack, aimed);
        }
        if (!dryable(stack)) {
            return false;
        }
        int free = slots[aimed].isEmpty() ? aimed : firstFree();
        if (free < 0) {
            Pottery.say(player, "shelf_full");
            return true;
        }
        slots[free] = stack.copyWithCount(1);
        progress[free] = 0;
        consumeOne(player, stack);
        sound(SoundEvents.DECORATED_POT_PLACE, 0.8F, 1.0F);
        if (wet(slots[free])) {
            Pottery.say(player, exposed() ? "shelf_open" : "shelf_roof", String.format("%.1f", days()));
        }
        changed();
        return true;
    }

    private boolean burnish(Player player, InteractionHand hand, ItemStack tool, int aimed) {
        int i = !slots[aimed].isEmpty() ? aimed : lastFilled();
        if (i < 0 || !Pottery.isGreenware(slots[i])) {
            Pottery.say(player, "burnish_nothing");
            return true;
        }
        ItemStack ware = slots[i];
        if (Pottery.has(ware, Pottery.BURNISHED)) {
            Pottery.say(player, "burnish_done_already");
            return true;
        }
        double dried = (double) progress[i] / (days() * DAY);
        if (!wet(ware) || dried > BURNISH_TO) {
            Pottery.say(player, "burnish_too_dry");
            return true;
        }
        if (dried < BURNISH_FROM) {
            Pottery.say(player, "burnish_too_wet", percent(i));
            return true;
        }
        strike(player, hand, tool);
        Pottery.addFlags(ware, Pottery.BURNISHED);
        sound(SoundEvents.BRUSH_GENERIC, 1.0F, 1.0F);
        Pottery.say(player, "burnished");
        changed();
        return true;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ: где сохнет и как каждое место — проценты и сколько ждать, сухой, треснул, размок. */
    @Override
    public void report(Player player) {
        boolean open = exposed();
        MutableComponent line = Component.translatable(open ? "message.halgventmod.pottery.shelf_where_open"
                : "message.halgventmod.pottery.shelf_where_roof", String.format("%.1f", days()));
        boolean any = false;
        long need = (long) (days() * DAY);
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = slots[i];
            if (stack.isEmpty()) {
                continue;
            }
            any = true;
            Component state;
            if (!dryable(stack)) {
                state = Component.translatable("message.halgventmod.pottery.shelf_soaked");
            } else if (Pottery.has(stack, Pottery.CRACKED)) {
                state = Component.translatable("message.halgventmod.pottery.shelf_cracked");
            } else if (Pottery.has(stack, Pottery.DRIED)) {
                state = Component.translatable("message.halgventmod.pottery.shelf_dry");
            } else {
                state = Component.translatable("message.halgventmod.pottery.shelf_drying", percent(i),
                        realMinutes(Math.max(0, need - progress[i])));
            }
            line.append(Component.literal(" · " + (i + 1) + ": ")).append(state);
        }
        if (!any) {
            Pottery.say(player, "shelf_empty");
            return;
        }
        player.displayClientMessage(line, true);
    }

    private int percent(int i) {
        return (int) Math.min(99, 100 * progress[i] / Math.max(1, (long) (days() * DAY)));
    }

    private int firstFree() {
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private int lastFilled() {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (!slots[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** Где стоит место i (как у стеллажа, смотрящего на север): {вдоль, высота дна, вглубь} в долях блока. */
    public static float[] spot(int i) {
        return new float[] {i % 2 == 0 ? 4.5F / 16 : 11.5F / 16, i < 2 ? LOWER : UPPER, 7.5F / 16};
    }

    /** Место на стеллаже, куда пришёлся щелчок: ярус — по высоте, левая или правая половина — вдоль. */
    private int slotAt(Vec3 hit) {
        double wx = hit.x - worldPosition.getX();
        double wz = hit.z - worldPosition.getZ();
        Direction facing = getBlockState().getValue(StationBlock.FACING);
        double mx = switch (facing) {
            case EAST -> wz;
            case SOUTH -> 1 - wx;
            case WEST -> 1 - wz;
            default -> wx;
        };
        boolean upper = hit.y - worldPosition.getY() >= UPPER - 1.0 / 16;
        return (upper ? 2 : 0) + (mx < 0.5 ? 0 : 1);
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < SLOTS; i++) {
            if (!slots[i].isEmpty()) {
                tag.put("Slot" + i, slots[i].save(registries));
                tag.putLong("Progress" + i, progress[i]);
            }
        }
        tag.putLong("LastDayTime", lastDayTime);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = tag.contains("Slot" + i)
                    ? ItemStack.parseOptional(registries, tag.getCompound("Slot" + i)) : ItemStack.EMPTY;
            progress[i] = tag.getLong("Progress" + i);
        }
        lastDayTime = tag.contains("LastDayTime") ? tag.getLong("LastDayTime") : -1;
    }
}
