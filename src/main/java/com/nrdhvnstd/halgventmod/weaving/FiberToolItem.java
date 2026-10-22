package com.nrdhvnstd.halgventmod.weaving;

import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Чесалка и веретено: держите ПКМ — одна горсть из инвентаря (сначала из второй руки) переходит на ступень дальше.
 * Чесалка: лён → льняная кудель (и иногда пакля), шерсть → шерстяная кудель. Веретено: кудель → пряжа своего волокна.
 * Каждая горсть тупит инструмент на единицу. Работает и в режиме приключения: это применение предмета, а не блока.
 */
public class FiberToolItem extends Item {
    public enum Step {
        COMB, SPIN
    }

    private final Step step;

    public FiberToolItem(Properties properties, Step step) {
        super(properties);
        this.step = step;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tool = player.getItemInHand(hand);
        if (inputSlot(player) < 0) {
            if (!level.isClientSide) {
                Weaving.say(player, step == Step.COMB ? "comb_nothing" : "spin_nothing");
            }
            return InteractionResultHolder.fail(tool);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(tool);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return step == Step.COMB ? Weaving.COMB_TICKS : Weaving.SPIN_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BRUSH;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide && remaining % 10 == 0) {
            level.playSound(null, entity.blockPosition(), step == Step.COMB ? SoundEvents.BRUSH_GENERIC : SoundEvents.WOOL_HIT,
                    SoundSource.PLAYERS, 0.5F, 0.9F + 0.2F * level.random.nextFloat());
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return stack;
        }
        int slot = inputSlot(player);
        if (slot < 0) {
            return stack;
        }
        Inventory inventory = player.getInventory();
        ItemStack input = inventory.getItem(slot);
        if (step == Step.COMB) {
            Fiber fiber = Fiber.ofRaw(input);
            give(player, new ItemStack(fiber.roving()));
            if (fiber == Fiber.LINEN && level.random.nextFloat() < Weaving.TOW_CHANCE) {
                give(player, new ItemStack(ModItems.TOW.get()));
            }
        } else {
            give(player, new ItemStack(Fiber.ofRoving(input).yarn()));
        }
        if (!player.getAbilities().instabuild) {
            input.shrink(1);
            EquipmentSlot hand = player.getUsedItemHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND
                    : EquipmentSlot.MAINHAND;
            stack.hurtAndBreak(1, player, hand);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.7F, 1.1F);
        return stack;
    }

    /** Где лежит то, что этим инструментом обрабатывают: сначала вторая рука, потом весь инвентарь; −1 — нигде. */
    private int inputSlot(Player player) {
        Inventory inventory = player.getInventory();
        int offhand = Inventory.SLOT_OFFHAND;
        if (accepts(inventory.getItem(offhand))) {
            return offhand;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (accepts(inventory.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && (step == Step.COMB ? Fiber.ofRaw(stack) != null : Fiber.ofRoving(stack) != null);
    }

    private static void give(Player player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }
}
