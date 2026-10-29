package com.nrdhvnstd.halgventmod.npc;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.trade.MarketStallBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Жезл НПС — инструмент администрации для ОДНОГО НПС. Режим меняется Shift+ПКМ по воздуху:
 * <pre>
 * Место работы / Дом / Маршрут — ПКМ по НПС выбирает его; ПКМ по блоку ставит место работы, дом или добавляет
 *                                 точку маршрута выбранного; Shift+ПКМ по блоку сбрасывает привязку (у маршрута —
 *                                 убирает последнюю точку).
 * Прилавок — ПКМ по прилавку привязывает его к выбранному НПС-торговцу (им торговец и торгует); повторно или
 *                                 Shift+ПКМ — отвязать.
 * </pre>
 * Пока жезл в руке, частицами видно: место работы (оранжевые), дом (синие), маршрут выбранного (зелёные, с линиями).
 * Выбор и режим хранятся в самом жезле. Общие точки интереса для всех НПС — другой жезл (NpcPoiWandItem).
 */
public class NpcWandItem extends Item {
    /** Режим жезла. */
    public enum Mode {
        POST("post"), HOME("home"), ROUTE("route"), STALL("stall");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public Component title() {
            return Component.translatable("item.halgventmod.npc_wand.mode." + id);
        }

        Mode next() {
            Mode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    // Меняйте здесь
    private static final int PARTICLE_INTERVAL = 10;     // тиков между показами
    private static final double NPC_REACH = 64;          // выбранный НПС ищется не дальше
    private static final float PARTICLE_SIZE = 1.0F;
    private static final int LINE_STEPS_MAX = 24;
    private static final int PILLAR_HEIGHT = 3;

    private static final String TAG_NPC = "Npc";
    private static final String TAG_MODE = "Mode";

    public NpcWandItem(Properties properties) {
        super(properties);
    }

    // ===== Состояние в самом жезле =====

    public static Mode mode(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Mode[] all = Mode.values();
        int ordinal = tag.getInt(TAG_MODE);
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : Mode.POST;
    }

    @Nullable
    public static UUID selected(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(TAG_NPC) ? tag.getUUID(TAG_NPC) : null;
    }

    private static void store(ItemStack stack, @Nullable UUID npc, Mode mode) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (npc == null) {
            tag.remove(TAG_NPC);
        } else {
            tag.putUUID(TAG_NPC, npc);
        }
        tag.putInt(TAG_MODE, mode.ordinal());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Nullable
    private static HalgNpc selectedNpc(ServerLevel level, ItemStack stack) {
        UUID id = selected(stack);
        return id != null && level.getEntity(id) instanceof HalgNpc npc && npc.isAlive() ? npc : null;
    }

    // ===== Действия =====

    // ПКМ по воздуху: Shift — следующий режим, без Shift — что выбрано
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !Permissions.isAdmin(player)) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            Mode next = mode(stack).next();
            store(stack, selected(stack), next);
            say(player, Component.translatable("npc.halgventmod.wand.mode", next.title()));
        } else {
            status(player, (ServerLevel) level, stack);
        }
        return InteractionResultHolder.success(stack);
    }

    // ПКМ по НПС: выбрать
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof HalgNpc npc) || !Permissions.isAdmin(player)) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide) {
            // В творческом режиме игра отдаёт сюда копию стека — запись в неё пропадает, поэтому пишем в стек из руки
            ItemStack held = player.getItemInHand(hand);
            store(held, npc.getUUID(), mode(held));
            say(player, Component.translatable("npc.halgventmod.wand.selected", npc.getDisplayName()));
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    // ПКМ по блоку: применить режим; Shift — сбросить
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !Permissions.isAdmin(player)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace()); // где встанет НПС
        Mode mode = mode(stack);
        boolean undo = player.isShiftKeyDown();
        HalgNpc npc = selectedNpc(level, stack);
        if (npc == null) {
            say(player, Component.translatable("npc.halgventmod.wand.no_selection"));
            return InteractionResult.SUCCESS;
        }
        switch (mode) {
            case POST -> {
                npc.setPost(undo ? BlockPos.ZERO : pos);
                say(player, Component.translatable(undo ? "npc.halgventmod.wand.post_cleared" : "npc.halgventmod.wand.post_set"));
            }
            case HOME -> {
                npc.setHome(undo ? BlockPos.ZERO : pos);
                say(player, Component.translatable(undo ? "npc.halgventmod.wand.home_cleared" : "npc.halgventmod.wand.home_set"));
            }
            case ROUTE -> {
                if (undo) {
                    npc.removeLastRoutePoint();
                } else if (!npc.addRoutePoint(pos)) {
                    say(player, Component.translatable("npc.halgventmod.wand.route_full", HalgNpc.ROUTE_MAX));
                    return InteractionResult.SUCCESS;
                }
                say(player, Component.translatable("npc.halgventmod.wand.route_points", npc.getRoute().size()));
            }
            case STALL -> {
                // Прилавок под прицелом: привязать к выбранному НПС-торговцу или отвязать (повторно; Shift — только отвязать)
                BlockPos at = context.getClickedPos();
                if (!(level.getBlockEntity(at) instanceof MarketStallBlockEntity stall)) {
                    say(player, Component.translatable("npc.halgventmod.wand.not_stall"));
                    return InteractionResult.SUCCESS;
                }
                boolean bound = !undo && npc.toggleStall(at);
                if (undo) {
                    npc.removeStall(at);
                }
                stall.setMerchant(bound ? npc.getUUID() : null, npc.getDisplayName().getString());
                say(player, Component.translatable(bound ? "npc.halgventmod.wand.stall_bound" : "npc.halgventmod.wand.stall_unbound",
                        npc.getDisplayName()));
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static void status(Player player, ServerLevel level, ItemStack stack) {
        HalgNpc npc = selectedNpc(level, stack);
        say(player, Component.translatable("npc.halgventmod.wand.status", mode(stack).title(),
                npc == null ? Component.translatable("npc.halgventmod.wand.nobody") : npc.getDisplayName()));
    }

    static void say(Player player, Component text) {
        player.displayClientMessage(text, true);
    }

    // ===== Показ частицами =====

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (!(level instanceof ServerLevel serverLevel) || !(holder instanceof ServerPlayer player)
                || !(selected || player.getOffhandItem() == stack) || !Permissions.isAdmin(player)
                || level.getGameTime() % PARTICLE_INTERVAL != 0) {
            return;
        }
        HalgNpc npc = selectedNpc(serverLevel, stack);
        if (npc != null && npc.distanceToSqr(player) <= NPC_REACH * NPC_REACH) {
            if (!npc.getPost().equals(BlockPos.ZERO)) {
                pillar(serverLevel, player, npc.getPost(), 0xFF9020);
            }
            if (!npc.getHome().equals(BlockPos.ZERO)) {
                pillar(serverLevel, player, npc.getHome(), 0x3070FF);
            }
            BlockPos previous = null;
            for (BlockPos point : npc.getRoute()) {
                pillar(serverLevel, player, point, 0x40E040);
                if (previous != null) {
                    line(serverLevel, player, previous, point);
                }
                previous = point;
            }
            for (BlockPos stall : npc.getStalls()) { // прилавки торговца — жёлтые столбики
                pillar(serverLevel, player, stall.above(), 0xF0C030);
            }
            dust(serverLevel, player, npc.getX(), npc.getBbHeight() + npc.getY() + 0.4, npc.getZ(), 0xFFFF40); // отметка над головой
        }
    }

    // Столбик из пылинок над блоком
    static void pillar(ServerLevel level, ServerPlayer player, BlockPos pos, int color) {
        for (int i = 0; i < PILLAR_HEIGHT; i++) {
            dust(level, player, pos.getX() + 0.5, pos.getY() + 0.2 + i * 0.5, pos.getZ() + 0.5, color);
        }
    }

    private static void line(ServerLevel level, ServerPlayer player, BlockPos from, BlockPos to) {
        double length = Math.sqrt(from.distSqr(to));
        int steps = (int) Math.min(LINE_STEPS_MAX, Math.max(1, length));
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            dust(level, player, from.getX() + 0.5 + (to.getX() - from.getX()) * t,
                    from.getY() + 0.3 + (to.getY() - from.getY()) * t, from.getZ() + 0.5 + (to.getZ() - from.getZ()) * t, 0x40E040);
        }
    }

    // Частица видна только этому администратору
    static void dust(ServerLevel level, ServerPlayer player, double x, double y, double z, int rgb) {
        Vector3f color = new Vector3f((rgb >> 16 & 0xFF) / 255F, (rgb >> 8 & 0xFF) / 255F, (rgb & 0xFF) / 255F);
        level.sendParticles(player, new DustParticleOptions(color, PARTICLE_SIZE), true, x, y, z, 1, 0, 0, 0, 0);
    }
}
