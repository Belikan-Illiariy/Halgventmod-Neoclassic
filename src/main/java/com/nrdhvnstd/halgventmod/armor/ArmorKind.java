package com.nrdhvnstd.halgventmod.armor;

import java.util.function.Supplier;

import com.nrdhvnstd.halgventmod.registry.ModSounds;

import net.minecraft.sounds.SoundEvent;

/**
 * Материал вещи брони — для звука: надевание, звон на ходу, звук удара по ней.
 * (Имя ArmorMaterial занято ванилью.) Материал каждой вещи — docs/sound_spec/armor_sounds.csv.
 */
public enum ArmorKind {
    PLATE   (ModSounds.ARMOR_EQUIP_PLATE),   // латы, бригантины, ламелляр
    MAIL    (ModSounds.ARMOR_EQUIP_MAIL),    // кольчуга
    PADDED  (ModSounds.ARMOR_EQUIP_PADDED),  // ткань: стёганка, накидка, подшлемник, штаны
    LEATHER (ModSounds.ARMOR_EQUIP_LEATHER); // кожа: обувь, перчатки

    private final Supplier<SoundEvent> equipSound;

    ArmorKind(Supplier<SoundEvent> equipSound) {
        this.equipSound = equipSound;
    }

    public SoundEvent equipSound() {
        return equipSound.get();
    }

    /** Материал по умолчанию для слота ⚠️; если у вещи иначе — .armorKind(...) в билдере ModItems. */
    public static ArmorKind forSlot(ArmorSlot slot) {
        return switch (slot) {
            case HELMET, BREASTPLATE, GREAVES -> PLATE;
            case COIF, MAIL, CHAUSSES -> MAIL;
            case BOOTS, GLOVES -> LEATHER;
            case CLOAK, ARMING_CAP, PADDING, TROUSERS -> PADDED;
        };
    }
}
