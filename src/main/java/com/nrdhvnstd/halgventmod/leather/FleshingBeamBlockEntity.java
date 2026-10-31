package com.nrdhvnstd.halgventmod.leather;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.needs.Stamina;
import com.nrdhvnstd.halgventmod.network.ScrapeOpenPayload;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Колода: на ней лежит одна шкура (или кусок сыромяти).
 * <ul>
 *   <li>соль по парной шкуре — солёная (хранится 16 суток);</li>
 *   <li>мездряк по парной или солёной — мини-игра: снять мездру ({@link ScrapeJob#FLESH});</li>
 *   <li>струг по золёной — мини-игра: снять волос, получается голье; с овцы падает шерсть ({@link ScrapeJob#HAIR});</li>
 *   <li>струг по сыромяти мелкой шкуры — мини-игра: выскоблить пергамент ({@link ScrapeJob#PARCHMENT});</li>
 *   <li>пустая рука — забрать.</li>
 * </ul>
 * За колодой работает один человек. Брошенная работа ничего не меняет. Каждый порез — качество −1,
 * прореха в пергаменте — {@link #TEAR_PENALTY}.
 */
public class FleshingBeamBlockEntity extends StationBlockEntity {
    // Меняйте здесь ⚠️
    public static final int MAX_CUTS = 2;          // больше порезов качество не снимают
    public static final int MAX_TEARS = 2;
    public static final int TEAR_PENALTY = 2;      // качества за прореху
    public static final double REACH = 6.0;
    public static final int WORK_TIMEOUT = 20 * 60 * 3; // столько тиков колода занята, если работник пропал

    private ItemStack hide = ItemStack.EMPTY;
    @Nullable
    private UUID worker;
    private ScrapeJob workJob = ScrapeJob.FLESH;
    private InteractionHand workHand = InteractionHand.MAIN_HAND;
    private long workSince;

    public FleshingBeamBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLESHING_BEAM.get(), pos, state);
    }

    public ItemStack hide() {
        return hide;
    }

    @Override
    public List<ItemStack> contents() {
        return hide.isEmpty() ? List.of() : List.of(hide.copy());
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (hide.isEmpty()) {
            return place(player, stack);
        }
        if (busy(player)) {
            say(player, "beam_busy");
            return true;
        }
        if (stack.isEmpty()) {
            give(player, hide);
            hide = ItemStack.EMPTY;
            worker = null;
            sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F, 1.0F);
            changed();
            return true;
        }
        if (Freshness.isSpoiled(hide, level)) {
            say(player, "rotten");
            return true;
        }
        Hide info = hide.get(ModComponents.HIDE.get());
        if (stack.is(ModItems.SALT.get())) {
            if (!hide.is(ModItems.RAW_HIDE.get())) {
                say(player, "salt_only_raw");
                return true;
            }
            consumeOne(player, stack);
            hide = LeatherQuality.carry(hide, new ItemStack(ModItems.SALTED_HIDE.get()));
            sound(SoundEvents.SAND_PLACE, 0.8F, 1.2F);
            crumbs(new ItemStack(ModItems.SALT.get()), 1.1, 8);
            say(player, "salted");
            changed();
            return true;
        }
        if (stack.is(ModItems.FLESHING_KNIFE.get())) {
            if (!(hide.is(ModItems.RAW_HIDE.get()) || hide.is(ModItems.SALTED_HIDE.get())) || info == null) {
                say(player, "knife_wrong");
                return true;
            }
            if (info.fleshed()) {
                say(player, "already_fleshed");
                return true;
            }
            startWork(player, hand, stack, ScrapeJob.FLESH, info);
            return true;
        }
        if (stack.is(ModItems.SCRAPER.get())) {
            if (hide.is(ModItems.LIMED_HIDE.get()) && info != null) {
                startWork(player, hand, stack, ScrapeJob.HAIR, info);
                return true;
            }
            if (hide.is(ModItems.RAWHIDE.get())) {
                if (info == null || !info.parchmentGrade()) {
                    say(player, "parchment_small_only");
                    return true;
                }
                startWork(player, hand, stack, ScrapeJob.PARCHMENT, info);
                return true;
            }
            say(player, "scraper_wrong");
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- мини-игра

    private boolean busy(Player player) {
        return worker != null && !worker.equals(player.getUUID()) && level != null
                && level.getGameTime() - workSince < WORK_TIMEOUT;
    }

    private void startWork(Player player, InteractionHand hand, ItemStack tool, ScrapeJob job, Hide info) {
        if (!(player instanceof ServerPlayer serverPlayer) || level == null) {
            return;
        }
        if (Stamina.exhausted(player)) {
            say(player, "beam_tired");
            return;
        }
        worker = player.getUUID();
        workJob = job;
        workHand = hand;
        workSince = level.getGameTime();
        PacketDistributor.sendToPlayer(serverPlayer, new ScrapeOpenPayload(worldPosition, job.ordinal(),
                tool.copyWithCount(1), info.pieces(), level.random.nextInt()));
    }

    /**
     * Итог мини-игры (ScrapeResultPayload). Сервер заново проверяет работника, расстояние, инструмент, шкуру
     * и время; порезы и прорехи считает клиент, как промахи шитья.
     */
    public void scraped(ServerPlayer player, boolean finished, int cuts, int tears) {
        if (worker == null || !worker.equals(player.getUUID()) || level == null) {
            return;
        }
        worker = null;
        ItemStack tool = player.getItemInHand(workHand);
        Hide info = hide.get(ModComponents.HIDE.get());
        if (!finished || info == null || !workJob.usesTool(tool)
                || player.distanceToSqr(worldPosition.getCenter()) > REACH * REACH
                || level.getGameTime() - workSince < workJob.minTicks() || !jobFits(workJob, info)) {
            return;
        }
        int madeCuts = Mth.clamp(cuts, 0, MAX_CUTS);
        int madeTears = workJob == ScrapeJob.PARCHMENT ? Mth.clamp(tears, 0, MAX_TEARS) : 0;
        LeatherQuality.change(hide, -(madeCuts + madeTears * TEAR_PENALTY));
        damage(player, workHand, tool, workJob.wear());
        Stamina.spend(player, workJob.stamina());
        player.swing(workHand, true);
        switch (workJob) {
            case FLESH -> {
                hide.set(ModComponents.HIDE.get(), info.withFleshed());
                give(player, new ItemStack(ModItems.FLESH.get(), 1 + Math.max(0, info.pieces() / 3)));
                crumbs(new ItemStack(ModItems.FLESH.get()), 1.1, 8);
            }
            case HAIR -> {
                hide = LeatherQuality.carry(hide, new ItemStack(ModItems.PELT.get()));
                hide.set(ModComponents.HIDE.get(), info.withoutFur());
                if ("sheep".equals(info.animal())) {
                    give(player, new ItemStack(Items.WHITE_WOOL, 1 + level.random.nextInt(2)));
                }
                crumbs(new ItemStack(Items.WHITE_WOOL), 1.1, 8);
            }
            case PARCHMENT -> {
                crumbs(hide, 1.1, 8);
                hide = LeatherQuality.carry(hide, new ItemStack(ModItems.PARCHMENT.get()));
            }
        }
        sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.2F);
        String done = switch (workJob) {
            case FLESH -> "fleshed";
            case HAIR -> "dehaired";
            case PARCHMENT -> "parchment";
        };
        if (madeCuts + madeTears > 0) {
            say(player, done + "_damaged", madeCuts, madeTears);
        } else {
            say(player, done);
        }
        changed();
    }

    /** На колоде всё ещё то, для чего начата работа. */
    private boolean jobFits(ScrapeJob job, Hide info) {
        return switch (job) {
            case FLESH -> (hide.is(ModItems.RAW_HIDE.get()) || hide.is(ModItems.SALTED_HIDE.get())) && !info.fleshed();
            case HAIR -> hide.is(ModItems.LIMED_HIDE.get());
            case PARCHMENT -> hide.is(ModItems.RAWHIDE.get()) && info.parchmentGrade();
        };
    }

    // ---------------------------------------------------------------- прочее

    private boolean place(Player player, ItemStack stack) {
        boolean accepted = stack.is(ModItems.RAW_HIDE.get()) || stack.is(ModItems.SALTED_HIDE.get())
                || stack.is(ModItems.LIMED_HIDE.get()) || stack.is(ModItems.RAWHIDE.get());
        if (!accepted) {
            if (stack.isEmpty() || isLeatherTool(stack)) {
                say(player, "beam_empty");
                return true;
            }
            return false;
        }
        hide = stack.copyWithCount(1);
        consumeOne(player, stack);
        worker = null;
        sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F, 0.8F);
        changed();
        return true;
    }

    public static boolean isLeatherTool(ItemStack stack) {
        return stack.is(ModItems.FLESHING_KNIFE.get()) || stack.is(ModItems.SCRAPER.get())
                || stack.is(ModItems.SALT.get());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!hide.isEmpty()) {
            tag.put("Hide", hide.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hide = tag.contains("Hide") ? ItemStack.parseOptional(registries, tag.getCompound("Hide")) : ItemStack.EMPTY;
    }
}
