package com.nrdhvnstd.halgventmod.npc;

import com.nrdhvnstd.halgventmod.Permissions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Жезл точек интереса — инструмент администрации для ВСЕХ НПС сразу: точки общие для мира, их учитывают все горожане
 * в 96 блоках (VisitPoiGoal). Вид точки (магазин, рынок, трактир, площадь) меняется Shift+ПКМ по воздуху,
 * ПКМ по воздуху показывает текущий. ПКМ по блоку ставит точку, Shift+ПКМ по блоку убирает ближайшую в 3 блоках.
 * Пока жезл в руке, точки в 48 блоках видны частицами цвета их вида. Для привязок одного НПС — жезл НПС (NpcWandItem).
 */
public class NpcPoiWandItem extends Item {
    // Меняйте здесь
    private static final int PARTICLE_INTERVAL = 10;   // тиков между показами
    private static final double SHOW_RADIUS = 48;      // точки показываются в этом радиусе
    private static final double REMOVE_RADIUS = 3;     // Shift+ПКМ убирает точку не дальше

    private static final String TAG_TYPE = "Type";

    public NpcPoiWandItem(Properties properties) {
        super(properties);
    }

    /** Вид точки, который жезл ставит сейчас (хранится в самом жезле). */
    public static PoiType type(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        PoiType[] all = PoiType.values();
        int ordinal = tag.getInt(TAG_TYPE);
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : PoiType.SHOP;
    }

    private static void store(ItemStack stack, PoiType type) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(TAG_TYPE, type.ordinal());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // ПКМ по воздуху: Shift — следующий вид, без Shift — какой сейчас
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !Permissions.isAdmin(player)) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            PoiType[] all = PoiType.values();
            PoiType next = all[(type(stack).ordinal() + 1) % all.length];
            store(stack, next);
            NpcWandItem.say(player, Component.translatable("npc.halgventmod.wand.mode", next.title()));
        } else {
            NpcWandItem.say(player, Component.translatable("npc.halgventmod.wand.mode", type(stack).title()));
        }
        return InteractionResultHolder.success(stack);
    }

    // ПКМ по блоку: поставить точку; Shift — убрать ближайшую
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !Permissions.isAdmin(player)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace()); // где встанет НПС
        NpcPois pois = NpcPois.get(level.getServer());
        if (player.isShiftKeyDown()) {
            NpcPois.Poi found = pois.nearest(level, pos, REMOVE_RADIUS);
            if (found == null) {
                NpcWandItem.say(player, Component.translatable("npc.halgventmod.wand.poi_none"));
            } else {
                pois.remove(found);
                NpcWandItem.say(player, Component.translatable("npc.halgventmod.wand.poi_removed", found.type().title()));
            }
        } else {
            PoiType type = type(context.getItemInHand());
            if (pois.add(type, level, pos)) {
                NpcWandItem.say(player, Component.translatable("npc.halgventmod.wand.poi_added", type.title()));
            } else {
                NpcWandItem.say(player, Component.translatable("npc.halgventmod.wand.poi_full", NpcPois.MAX_POIS));
            }
        }
        return InteractionResult.SUCCESS;
    }

    // Точки вокруг видны только этому администратору
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (!(level instanceof ServerLevel serverLevel) || !(holder instanceof ServerPlayer player)
                || !(selected || player.getOffhandItem() == stack) || !Permissions.isAdmin(player)
                || level.getGameTime() % PARTICLE_INTERVAL != 0) {
            return;
        }
        for (NpcPois.Poi poi : NpcPois.get(serverLevel.getServer()).near(serverLevel, player.blockPosition(), SHOW_RADIUS)) {
            NpcWandItem.pillar(serverLevel, player, poi.pos(), poi.type().color());
        }
    }
}
