package com.nrdhvnstd.halgventmod.weaving;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.leather.LeatherQuality;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.network.WeaveOpenPayload;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Ткацкий станок. Всё на E:
 * <pre>
 *   грузило                  — подвесить ({@link Weaving#WEIGHTS} штук; без них основу не натянуть)
 *   пряжа                    — натянуть основу ({@link Weaving#WARP_YARN} мотков одного волокна)
 *   челнок                   — окно тканья, мини-игра «Зев и прибой» (WeavingScreen): {@link Weaving#PASSES} прокидок,
 *                              моток утка из инвентаря на {@link Weaving#PASSES_PER_YARN}; изъяны портят ткань
 *   пустая рука              — готовую ткань снять ({@link Weaving#CLOTH} отреза); до тканья — забрать последний
 *                              моток основы или грузило
 * </pre>
 * ПКМ — как идёт дело. Ткань растёт сверху вниз, как на настоящем станке с грузилами. Каждая прокидка приходит
 * с клиента отдельно (WeavePassPayload) — сервер сам проверяет станок, челнок, уток и темп.
 */
public class LoomBlockEntity extends StationBlockEntity {
    public static final double REACH = 6.0;

    private int weights;
    @Nullable
    private Fiber warp;
    private int warpYarn;
    private int passes;
    private int flaws;
    private int looseRows;  // по биту на ряд: рыхлый
    private int tightRows;  // по биту на ряд: перетянутый
    private long lastPass;

    public LoomBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOOM.get(), pos, state);
    }

    // Для отрисовки и окна тканья
    @Nullable
    public Fiber warp() {
        return warp;
    }

    public int passes() {
        return passes;
    }

    public int flaws() {
        return flaws;
    }

    public boolean looseRow(int row) {
        return (looseRows >> row & 1) != 0;
    }

    public boolean tightRow(int row) {
        return (tightRows >> row & 1) != 0;
    }

    /** Сколько ткани соткано, 0–1. */
    public float woven() {
        return (float) passes / Weaving.PASSES;
    }

    public boolean warped() {
        return warp != null && warpYarn >= Weaving.WARP_YARN;
    }

    public boolean done() {
        return passes >= Weaving.PASSES;
    }

    /** Нужен ли на следующую прокидку новый моток утка. */
    public boolean needsWeft() {
        return passes % Weaving.PASSES_PER_YARN == 0;
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(ModItems.LOOM_WEIGHT.get())) {
            return hangWeight(player, stack);
        }
        Fiber yarn = Fiber.ofYarn(stack);
        if (yarn != null) {
            return addWarp(player, stack, yarn);
        }
        if (stack.is(ModItems.SHUTTLE.get())) {
            return openWeaving(player);
        }
        if (stack.isEmpty()) {
            return takeOff(player);
        }
        return false;
    }

    private boolean hangWeight(Player player, ItemStack stack) {
        if (weights >= Weaving.WEIGHTS) {
            Weaving.say(player, "weights_full", Weaving.WEIGHTS);
            return true;
        }
        weights++;
        consumeOne(player, stack);
        sound(SoundEvents.DECORATED_POT_INSERT, 0.8F, 1.1F);
        Weaving.say(player, "weight_hung", weights, Weaving.WEIGHTS);
        changed();
        return true;
    }

    private boolean addWarp(Player player, ItemStack stack, Fiber fiber) {
        if (done()) {
            Weaving.say(player, "take_cloth");
            return true;
        }
        if (warped()) {
            Weaving.say(player, "warp_full");
            return true;
        }
        if (weights < Weaving.WEIGHTS) {
            Weaving.say(player, "need_weights", weights, Weaving.WEIGHTS);
            return true;
        }
        if (warp != null && warp != fiber) {
            Weaving.say(player, "warp_other", new ItemStack(warp.yarn()).getHoverName());
            return true;
        }
        warp = fiber;
        warpYarn++;
        consumeOne(player, stack);
        sound(SoundEvents.WOOL_PLACE, 0.8F, 1.0F);
        Weaving.say(player, warped() ? "warp_ready" : "warp_added", warpYarn, Weaving.WARP_YARN);
        threaded(true);
        changed();
        return true;
    }

    /** Почему ткать нельзя (ключ сообщения) или null — можно. */
    @Nullable
    private String weavingBlocker(Player player) {
        if (done()) {
            return "take_cloth";
        }
        if (weights < Weaving.WEIGHTS) {
            return "need_weights";
        }
        if (!warped()) {
            return "need_warp";
        }
        if (needsWeft() && !player.getAbilities().instabuild && player.getInventory().countItem(warp.yarn()) <= 0) {
            return "need_weft";
        }
        return null;
    }

    private void explain(Player player, String blocker) {
        switch (blocker) {
            case "need_weights" -> Weaving.say(player, blocker, weights, Weaving.WEIGHTS);
            case "need_warp" -> Weaving.say(player, blocker, warpYarn, Weaving.WARP_YARN);
            case "need_weft" -> Weaving.say(player, blocker, new ItemStack(warp.yarn()).getHoverName());
            default -> Weaving.say(player, blocker);
        }
    }

    private boolean openWeaving(Player player) {
        String blocker = weavingBlocker(player);
        if (blocker != null) {
            explain(player, blocker);
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new WeaveOpenPayload(worldPosition));
        }
        return true;
    }

    /**
     * Прокидка из окна тканья (WeavePassPayload): итог — Weaving.PASS_*. Сервер заново проверяет станок, расстояние,
     * челнок в руке, уток и темп; клиенту доверяем только сам итог.
     */
    public void pass(ServerPlayer player, int result) {
        if (player.distanceToSqr(worldPosition.getCenter()) > REACH * REACH || level == null) {
            return;
        }
        InteractionHand hand = player.getMainHandItem().is(ModItems.SHUTTLE.get()) ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().is(ModItems.SHUTTLE.get()) ? InteractionHand.OFF_HAND : null;
        long now = level.getGameTime();
        if (hand == null || now - lastPass < Weaving.MIN_PASS_TICKS) {
            return;
        }
        String blocker = weavingBlocker(player);
        if (blocker != null) {
            explain(player, blocker);
            return;
        }
        lastPass = now;
        damage(player, hand, player.getItemInHand(hand), 1);
        if (result == Weaving.PASS_SNAG) {
            flaws++;
            sound(SoundEvents.WOOL_BREAK, 1.0F, 0.7F);
            changed();
            return;
        }
        if (needsWeft()) {
            takeWeft(player);
        }
        if (result == Weaving.PASS_LOOSE) {
            looseRows |= 1 << passes;
            flaws++;
        } else if (result == Weaving.PASS_TIGHT) {
            tightRows |= 1 << passes;
            flaws++;
        }
        passes++;
        sound(SoundEvents.UI_LOOM_SELECT_PATTERN, 0.9F, 0.8F + 0.4F * level.random.nextFloat());
        if (done()) {
            sound(SoundEvents.UI_LOOM_TAKE_RESULT, 1.0F, 1.0F);
            Weaving.say(player, "woven");
        }
        changed();
    }

    private void takeWeft(Player player) {
        if (player.getAbilities().instabuild) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(warp.yarn())) {
                stack.shrink(1);
                return;
            }
        }
    }

    private boolean takeOff(Player player) {
        if (done()) {
            ItemStack cloth = new ItemStack(warp.cloth(), Weaving.CLOTH);
            LeatherQuality.set(cloth, Weaving.clothQuality(flaws));
            Component name = cloth.getHoverName();
            give(player, cloth);
            sound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
            Weaving.say(player, flaws == 0 ? "cloth_taken_even" : "cloth_taken", Weaving.CLOTH, name, flaws);
            warp = null;
            warpYarn = passes = flaws = looseRows = tightRows = 0;
            threaded(false);
            changed();
            return true;
        }
        if (passes > 0) {
            Weaving.say(player, "weaving", passes, Weaving.PASSES);
            return true;
        }
        if (warpYarn > 0) {
            give(player, new ItemStack(warp.yarn()));
            if (--warpYarn == 0) {
                warp = null;
                threaded(false);
            }
            sound(SoundEvents.WOOL_BREAK, 0.8F, 1.0F);
            changed();
            return true;
        }
        if (weights > 0) {
            weights--;
            give(player, new ItemStack(ModItems.LOOM_WEIGHT.get()));
            sound(SoundEvents.DECORATED_POT_HIT, 0.6F, 1.2F);
            changed();
            return true;
        }
        report(player);
        return true;
    }

    private void threaded(boolean threaded) {
        BlockState state = getBlockState();
        if (level != null && state.getValue(LoomBlock.THREADED) != threaded) {
            level.setBlock(worldPosition, state.setValue(LoomBlock.THREADED, threaded), Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    @Override
    public void report(Player player) {
        if (weights < Weaving.WEIGHTS) {
            Weaving.say(player, "report_weights", weights, Weaving.WEIGHTS);
        } else if (!warped()) {
            Weaving.say(player, "report_warp", warpYarn, Weaving.WARP_YARN);
        } else if (!done()) {
            Weaving.say(player, "report_weaving", passes, Weaving.PASSES, flaws,
                    new ItemStack(warp.yarn()).getHoverName());
        } else {
            Weaving.say(player, "report_done", new ItemStack(warp.cloth()).getHoverName());
        }
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        if (weights > 0) {
            all.add(new ItemStack(ModItems.LOOM_WEIGHT.get(), weights));
        }
        if (warp != null) {
            if (done()) {
                all.add(LeatherQuality.set(new ItemStack(warp.cloth(), Weaving.CLOTH), Weaving.clothQuality(flaws)));
            } else {
                all.add(new ItemStack(warp.yarn(), warpYarn)); // недотканный уток пропадает
            }
        }
        return all;
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Weights", weights);
        tag.putString("Warp", warp == null ? "" : warp.id());
        tag.putInt("WarpYarn", warpYarn);
        tag.putInt("Passes", passes);
        tag.putInt("Flaws", flaws);
        tag.putInt("LooseRows", looseRows);
        tag.putInt("TightRows", tightRows);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        weights = tag.getInt("Weights");
        warp = Fiber.byId(tag.getString("Warp"));
        warpYarn = warp == null ? 0 : tag.getInt("WarpYarn");
        passes = warp == null ? 0 : Math.min(tag.getInt("Passes"), Weaving.PASSES);
        flaws = tag.getInt("Flaws");
        looseRows = tag.getInt("LooseRows");
        tightRows = tag.getInt("TightRows");
    }
}
