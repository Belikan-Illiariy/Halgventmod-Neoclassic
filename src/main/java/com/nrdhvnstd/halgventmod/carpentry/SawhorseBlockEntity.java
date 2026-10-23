package com.nrdhvnstd.halgventmod.carpentry;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.network.SawOpenPayload;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Пильные козлы. Всё на E:
 * <pre>
 *   бревно      — положить на козлы (одно; любое, в том числе окорённое, древесина, стебель)
 *   пила        — окно распила, мини-игра «Пропил» (SawingScreen): {@link Carpentry#CUTS} пропила по разметке.
 *                 Досок выходит на одну больше пропилов; каждый кривой пропил — одна доска уходит в горбыль (дрова)
 *   пустая рука — снять бревно, пока не начали пилить
 * </pre>
 * ПКМ — как идёт дело. Каждый пропил приходит с клиента отдельно (SawCutPayload) — сервер сам проверяет козлы,
 * пилу в руке и темп; клиенту доверяем только итог пропила и сколько раз зажало пилу.
 */
public class SawhorseBlockEntity extends StationBlockEntity {
    public static final double REACH = 6.0;

    private ItemStack log = ItemStack.EMPTY;
    private int cuts;
    private int crooked;
    private long lastCut;

    public SawhorseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAWHORSE.get(), pos, state);
    }

    // Для отрисовки и окна распила
    public ItemStack log() {
        return log;
    }

    public int cuts() {
        return cuts;
    }

    public int crooked() {
        return crooked;
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(ModItems.SAW.get())) {
            return openSawing(player);
        }
        if (Carpentry.planksOf(stack) != null) {
            return put(player, stack);
        }
        if (stack.isEmpty()) {
            return take(player);
        }
        return false;
    }

    private boolean put(Player player, ItemStack stack) {
        if (!log.isEmpty()) {
            Carpentry.say(player, "horse_busy");
            return true;
        }
        log = stack.copyWithCount(1);
        cuts = crooked = 0;
        consumeOne(player, stack);
        sound(SoundEvents.WOOD_PLACE, 1.0F, 0.8F);
        Carpentry.say(player, "log_on_horse", Carpentry.CUTS);
        changed();
        return true;
    }

    private boolean openSawing(Player player) {
        if (log.isEmpty()) {
            Carpentry.say(player, "need_log_horse");
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new SawOpenPayload(worldPosition));
        }
        return true;
    }

    /**
     * Пропил из окна распила (SawCutPayload): итог — Carpentry.CUT_*, сколько раз зажало пилу. Сервер заново
     * проверяет козлы, расстояние, пилу в руке и темп.
     */
    public void cut(ServerPlayer player, int result, int jams) {
        if (player.distanceToSqr(worldPosition.getCenter()) > REACH * REACH || level == null || log.isEmpty()) {
            return;
        }
        InteractionHand hand = player.getMainHandItem().is(ModItems.SAW.get()) ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().is(ModItems.SAW.get()) ? InteractionHand.OFF_HAND : null;
        long now = level.getGameTime();
        if (hand == null || now - lastCut < Carpentry.MIN_CUT_TICKS || cuts >= Carpentry.CUTS) {
            return;
        }
        lastCut = now;
        damage(player, hand, player.getItemInHand(hand), 1 + Math.clamp(jams, 0, 5) * Carpentry.JAM_WEAR);
        cuts++;
        if (result == Carpentry.CUT_CROOKED) {
            crooked++;
        }
        crumbs(log, 1.0, 10);
        sound(SoundEvents.WOOD_BREAK, 0.7F, 1.3F);
        if (cuts >= Carpentry.CUTS) {
            finish(player);
        }
        changed();
    }

    private void finish(Player player) {
        Item planks = Carpentry.planksOf(log);
        int boards = Carpentry.CUTS + 1 - crooked;
        if (boards > 0) {
            give(player, new ItemStack(planks == null ? Items.OAK_PLANKS : planks, boards));
        }
        if (crooked > 0) {
            // Горбыль: горящее дерево — на дрова, багровое и искажённое — только на палки
            give(player, log.is(ItemTags.LOGS_THAT_BURN) ? new ItemStack(ModItems.FIREWOOD.get(), crooked)
                    : new ItemStack(Items.STICK, 2 * crooked));
        }
        sound(SoundEvents.WOOD_FALL, 1.0F, 0.9F);
        Carpentry.say(player, crooked == 0 ? "sawn_even" : "sawn", boards, crooked);
        log = ItemStack.EMPTY;
        cuts = crooked = 0;
    }

    private boolean take(Player player) {
        if (log.isEmpty()) {
            report(player);
            return true;
        }
        if (cuts > 0) {
            Carpentry.say(player, "sawing", cuts, Carpentry.CUTS);
            return true;
        }
        give(player, log);
        log = ItemStack.EMPTY;
        sound(SoundEvents.WOOD_HIT, 0.6F, 1.0F);
        changed();
        return true;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    @Override
    public void report(Player player) {
        if (log.isEmpty()) {
            Carpentry.say(player, "report_horse_empty");
        } else {
            Carpentry.say(player, "report_horse", log.getHoverName(), cuts, Carpentry.CUTS, crooked);
        }
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        if (!log.isEmpty()) {
            all.add(log.copy()); // начатое бревно — целиком: пропилы не в счёт
        }
        return all;
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!log.isEmpty()) {
            tag.put("Log", log.save(registries));
        }
        tag.putInt("Cuts", cuts);
        tag.putInt("Crooked", crooked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        log = tag.contains("Log") ? ItemStack.parseOptional(registries, tag.getCompound("Log")) : ItemStack.EMPTY;
        cuts = log.isEmpty() ? 0 : Math.clamp(tag.getInt("Cuts"), 0, Carpentry.CUTS - 1);
        crooked = Math.clamp(tag.getInt("Crooked"), 0, cuts);
    }
}
