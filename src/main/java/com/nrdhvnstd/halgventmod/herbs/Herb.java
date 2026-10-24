package com.nrdhvnstd.halgventmod.herbs;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModEffects;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Виды трав. Свежая трава — свой предмет; сушёная — «Сушёные травы» с видом в компоненте {@link ModComponents#HERB}
 * и своим названием («Сушёная мята»); отвар из горшка — по главной траве, с её действием ({@link #steep}).
 * Срезают каждую только с её кустика (HerbPlantBlock). Действия ⚠️
 */
public enum Herb {
    CHAMOMILE("chamomile", ModItems.CHAMOMILE),         // ромашка: снимает тошноту и отравление, понемногу лечит
    MINT("mint", ModItems.MINT),                        // мята: освежает — бодрее шаг
    ST_JOHNS_WORT("st_johns_wort", ModItems.ST_JOHNS_WORT), // зверобой: крепче держит удар
    YARROW("yarrow", ModItems.YARROW),                  // тысячелистник: затягивает раны
    WORMWOOD("wormwood", ModItems.WORMWOOD),            // полынь: горькая, выгоняет отравление и голод
    NETTLE("nettle", ModItems.NETTLE),                  // крапива: бодрит — выносливость восстанавливается быстрее
    THYME("thyme", ModItems.THYME),                     // чабрец: согревает, прибавляет сил
    LAVENDER("lavender", ModItems.LAVENDER);            // лаванда: успокаивает, проясняет взгляд

    // Меняйте здесь: сколько длится действие отвара, тиков
    private static final int SHORT = 8 * 20;
    private static final int MEDIUM = 15 * 20;
    private static final int LONG = 60 * 20;

    private final String id;
    private final Supplier<? extends Item> item;

    Herb(String id, Supplier<? extends Item> item) {
        this.id = id;
        this.item = item;
    }

    public String id() {
        return id;
    }

    public Item item() {
        return item.get();
    }

    @Nullable
    public static Herb byId(@Nullable String id) {
        for (Herb herb : values()) {
            if (herb.id.equals(id)) {
                return herb;
            }
        }
        return null;
    }

    /** Вид травы: свежая — по предмету, сушёная, отвар — по компоненту; null — без вида (луговая смесь, не трава). */
    @Nullable
    public static Herb of(ItemStack stack) {
        for (Herb herb : values()) {
            if (stack.is(herb.item())) {
                return herb;
            }
        }
        return byId(stack.get(ModComponents.HERB.get()));
    }

    /**
     * Пометить сушёную траву, отвар, травяной порошок или эссенцию видом: компонент и название
     * («Сушёная мята», «Мятный отвар», «Мятный порошок») — item.halgventmod.&lt;предмет&gt;.&lt;трава&gt;.
     */
    public ItemStack mark(ItemStack stack) {
        stack.set(ModComponents.HERB.get(), id);
        String key = "item.halgventmod." + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                + "." + id;
        stack.set(DataComponents.ITEM_NAME, Component.translatable(key));
        return stack;
    }

    /** Что даёт отвар этой травы: ключ строки для подсказки. */
    public String effectKey() {
        return "tooltip.halgventmod.herb." + id;
    }

    /** Выпили отвар этой травы. */
    public void steep(LivingEntity drinker) {
        steep(drinker, 1);
    }

    /** Выпили отвар или эссенцию: действие длится в times раз дольше. */
    public void steep(LivingEntity drinker, int times) {
        int brief = SHORT * times, medium = MEDIUM * times, lasting = LONG * times;
        switch (this) {
            case CHAMOMILE -> {
                drinker.removeEffect(MobEffects.CONFUSION);
                drinker.removeEffect(ModEffects.POISONING);
                drinker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, brief, 0));
            }
            case MINT -> {
                drinker.removeEffect(MobEffects.CONFUSION);
                drinker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, lasting / 2, 0));
            }
            case ST_JOHNS_WORT -> drinker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, lasting, 0));
            case YARROW -> drinker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, medium, 0));
            case WORMWOOD -> {
                drinker.removeEffect(ModEffects.POISONING);
                drinker.removeEffect(MobEffects.HUNGER);
                drinker.removeEffect(MobEffects.POISON);
            }
            case NETTLE -> drinker.addEffect(new MobEffectInstance(ModEffects.VIGOR, lasting, 0));
            case THYME -> drinker.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, lasting, 0));
            case LAVENDER -> {
                drinker.removeEffect(MobEffects.CONFUSION);
                drinker.removeEffect(MobEffects.BLINDNESS);
                drinker.removeEffect(MobEffects.DARKNESS);
                drinker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, brief / 2, 0));
            }
        }
    }
}
