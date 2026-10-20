package com.nrdhvnstd.halgventmod.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Звуки боя, брони и снаряжения (docs/sound_spec/TZ_sounds.md). Файлы и варианты — в assets/halgventmod/sounds.json,
 * он ресурс, а не датаген. Голоса здесь не регистрируются: их собирает Voices по id набора.
 * Помеченные «этап 3» только зарегистрированы — звук вызовет механика, когда появится.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, HalgventMod.MODID);

    private static final List<Definition> DEFINITIONS = new ArrayList<>();

    // ----- combat.hit -----
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HIT_BLADE_PLATE =
            sound("combat.hit.blade.plate", "Клинок бьёт по латам", "Blade hits plate");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HIT_DEFLECT =
            sound("combat.hit.deflect", "Удар соскальзывает со стали", "Blow glances off steel"); // этап 3
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HIT_BLADE_MAIL =
            sound("combat.hit.blade.mail", "Клинок бьёт по кольчуге", "Blade hits mail");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HIT_BLADE_PADDED =
            sound("combat.hit.blade.padded", "Клинок бьёт по ткани", "Blade hits cloth");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HIT_BLADE_FLESH =
            sound("combat.hit.blade.flesh", "Клинок бьёт по телу", "Blade hits flesh");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HIT_POINT_ARMOR =
            sound("combat.hit.point.armor", "Остриё бьёт по броне", "Point hits armor");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HIT_POINT_FLESH =
            sound("combat.hit.point.flesh", "Остриё бьёт по телу", "Point hits flesh");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HIT_BLUNT_PLATE =
            sound("combat.hit.blunt.plate", "Булава бьёт по латам", "Mace hits plate");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HIT_BLUNT_SOFT =
            sound("combat.hit.blunt.soft", "Булава глухо бьёт", "Mace thuds");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HIT_FIST =
            sound("combat.hit.fist", "Кулак бьёт", "Fist hits");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HIT_ARROW_ARMOR =
            sound("combat.hit.arrow.armor", "Стрела попадает в броню", "Arrow hits armor");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HIT_ARROW_FLESH =
            sound("combat.hit.arrow.flesh", "Стрела попадает в тело", "Arrow hits flesh");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HIT_ARROW_WOOD =
            sound("combat.hit.arrow.wood", "Стрела попадает в дерево", "Arrow hits wood");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HIT_ARROW_STONE =
            sound("combat.hit.arrow.stone", "Стрела бьёт в камень", "Arrow strikes stone");

    // ----- combat.block -----
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_BLOCK_SHIELD_WOOD =
            sound("combat.block.shield.wood", "Щит блокирует", "Shield blocks");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_BLOCK_SHIELD_METAL =
            sound("combat.block.shield.metal", "Щит звенит", "Shield rings"); // этап 3
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_BLOCK_WEAPON =
            sound("combat.block.weapon", "Клинки лязгают", "Blades clash"); // этап 3
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_BLOCK_BROKEN =
            sound("combat.block.broken", "Защита пробита", "Guard breaks");

    // ----- combat.shield -----
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_SHIELD_RAISE =
            sound("combat.shield.raise", "Щит поднимается", "Shield rises");

    // ----- combat.swing -----
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_SWING_LIGHT =
            sound("combat.swing.light", "Клинок свистит", "Blade whooshes");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_SWING_HEAVY =
            sound("combat.swing.heavy", "Тяжёлое оружие свистит", "Heavy weapon whooshes");

    // ----- combat.weapon -----
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_WEAPON_DRAW =
            sound("combat.weapon.draw", "Клинок выходит из ножен", "Blade leaves sheath");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_WEAPON_SHEATHE =
            sound("combat.weapon.sheathe", "Клинок входит в ножны", "Blade enters sheath");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_WEAPON_GRIP =
            sound("combat.weapon.grip", "Хват меняется", "Grip shifts"); // этап 3
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_WEAPON_DULL =
            sound("combat.weapon.dull", "Оружие тупится", "Weapon dulls"); // этап 3

    // ----- combat.bow -----
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_BOW_DRAW =
            sound("combat.bow.draw", "Лук скрипит", "Bow creaks");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_BOW_SHOOT =
            sound("combat.bow.shoot", "Лук стреляет", "Bow fires");

    // ----- combat.crossbow -----
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_CROSSBOW_LOAD =
            sound("combat.crossbow.load", "Арбалет взводится", "Crossbow loads");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_CROSSBOW_SHOOT =
            sound("combat.crossbow.shoot", "Арбалет стреляет", "Crossbow fires");

    // ----- combat.arrow -----
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_ARROW_FLY =
            sound("combat.arrow.fly", "Стрела свистит", "Arrow whistles");

    // ----- armor.equip -----
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_EQUIP_PLATE =
            sound("armor.equip.plate", "Латы лязгают", "Plate armor clanks");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_EQUIP_MAIL =
            sound("armor.equip.mail", "Кольчуга звенит", "Mail armor jingles");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_EQUIP_PADDED =
            sound("armor.equip.padded", "Тканевая броня шуршит", "Cloth armor rustles");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_EQUIP_LEATHER =
            sound("armor.equip.leather", "Кожаная броня скрипит", "Leather armor creaks");

    // ----- armor.move -----
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_MOVE_PLATE =
            sound("armor.move.plate", "Латы позвякивают", "Plate clinks");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_MOVE_MAIL =
            sound("armor.move.mail", "Кольчуга шелестит", "Mail rustles");

    // ----- armor.visor -----
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_VISOR_OPEN =
            sound("armor.visor.open", "Забрало открывается", "Visor opens"); // этап 3
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_VISOR_CLOSE =
            sound("armor.visor.close", "Забрало закрывается", "Visor closes"); // этап 3

    // ----- armor.break -----
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMOR_BREAK =
            sound("armor.break", "Броня ломается", "Armor breaks"); // этап 3

    // ----- gear.equip -----
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_EQUIP_BACKPACK =
            sound("gear.equip.backpack", "Заплечный мешок шуршит", "Pack rustles"); // этап 3
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_EQUIP_BELT =
            sound("gear.equip.belt", "Пряжка пояса звякает", "Belt buckle clinks"); // этап 3
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_EQUIP_POUCH =
            sound("gear.equip.pouch", "Подсумок шуршит", "Pouch rustles"); // этап 3
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_EQUIP_QUIVER =
            sound("gear.equip.quiver", "Колчан гремит", "Quiver rattles"); // этап 3

    // ----- combat.stun -----
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_STUN =
            sound("combat.stun", "В ушах звенит", "Ears ring"); // этап 3

    // ----- combat.downed -----
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_DOWNED =
            sound("combat.downed", "Тело падает", "Body falls"); // этап 3

    // ----- combat.finisher -----
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_FINISHER =
            sound("combat.finisher", "Добивающий удар обрушивается", "Finishing blow lands"); // этап 3

    // ----- combat.limb_cut -----
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_LIMB_CUT =
            sound("combat.limb_cut", "Клинок отрубает руку", "Blade severs arm"); // этап 3

    // ----- combat.blood -----
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_BLOOD_SPLASH =
            sound("combat.blood.splash", "Кровь брызжет", "Blood spatters"); // этап 3

    // ----- combat.heartbeat -----
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBAT_HEARTBEAT =
            sound("combat.heartbeat", "Сердце колотится", "Heart pounds"); // этап 3

    // ----- mob.beast -----
    public static final DeferredHolder<SoundEvent, SoundEvent> MOB_BEAST_HURT =
            sound("mob.beast.hurt", "Зверь взвизгивает", "Beast yelps"); // этап 3
    public static final DeferredHolder<SoundEvent, SoundEvent> MOB_BEAST_ATTACK =
            sound("mob.beast.attack", "Зверь рычит", "Beast growls"); // этап 3
    public static final DeferredHolder<SoundEvent, SoundEvent> MOB_BEAST_DEATH =
            sound("mob.beast.death", "Зверь умирает", "Beast dies"); // этап 3

    // ----- mob.monster -----
    public static final DeferredHolder<SoundEvent, SoundEvent> MOB_MONSTER_HURT =
            sound("mob.monster.hurt", "Чудовище ревёт от боли", "Monster roars in pain"); // этап 3
    public static final DeferredHolder<SoundEvent, SoundEvent> MOB_MONSTER_ATTACK =
            sound("mob.monster.attack", "Чудовище ревёт", "Monster roars"); // этап 3
    public static final DeferredHolder<SoundEvent, SoundEvent> MOB_MONSTER_DEATH =
            sound("mob.monster.death", "Чудовище умирает", "Monster dies"); // этап 3

    // ----- mob.undead -----
    public static final DeferredHolder<SoundEvent, SoundEvent> MOB_UNDEAD_HURT =
            sound("mob.undead.hurt", "Нежить гремит костями", "Undead rattles"); // этап 3
    public static final DeferredHolder<SoundEvent, SoundEvent> MOB_UNDEAD_DEATH =
            sound("mob.undead.death", "Нежить рассыпается", "Undead crumbles"); // этап 3

    private ModSounds() {}

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }

    /** Звук и его субтитры: ModLanguageProvider проходит по ним циклом. */
    public record Definition(String id, String subtitleRu, String subtitleEn) {
        public String subtitleKey() {
            return ModSounds.subtitleKey(id);
        }
    }

    public static List<Definition> definitions() {
        return Collections.unmodifiableList(DEFINITIONS);
    }

    /** Ключ субтитра: subtitles.halgventmod.<id>; такой же стоит в sounds.json. */
    public static String subtitleKey(String id) {
        return "subtitles." + HalgventMod.MODID + "." + id;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id, String subtitleRu, String subtitleEn) {
        DEFINITIONS.add(new Definition(id, subtitleRu, subtitleEn));
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, id);
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(location));
    }
}
