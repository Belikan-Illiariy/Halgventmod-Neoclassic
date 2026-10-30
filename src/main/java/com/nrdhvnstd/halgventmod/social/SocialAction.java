package com.nrdhvnstd.halgventmod.social;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import com.nrdhvnstd.halgventmod.downed.Downed;
import com.nrdhvnstd.halgventmod.combat.Injuries;

import net.minecraft.world.entity.player.Player;

/**
 * Пункты кругового меню (ТЗ 3.2, 6): обыск, наложение и снятие мешка, кандалов, наручников;
 * у поверженного — ещё «Вылечить» и «Нести» (docs/TZ_downed_and_hp.md, 4). «Бросить» — не пункт меню, а E у несущего.
 * Номер в пакете — порядковый, новые только в конец.
 */
public enum SocialAction {
    SEARCH(null, false),
    BAG(Restraint.BAG, false),
    SHACKLES(Restraint.SHACKLES, false),
    CUFFS(Restraint.CUFFS, false),
    UNBAG(Restraint.BAG, true),
    UNSHACKLE(Restraint.SHACKLES, true),
    UNCUFF(Restraint.CUFFS, true),
    HEAL(null, false),
    CARRY(null, false),
    DROP(null, false);

    private static final IntFunction<SocialAction> BY_ID =
            ByIdMap.continuous(SocialAction::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, SocialAction> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, SocialAction::ordinal);

    @Nullable
    private final Restraint restraint;
    private final boolean removes;

    SocialAction(@Nullable Restraint restraint, boolean removes) {
        this.restraint = restraint;
        this.removes = removes;
    }

    /** Что накладывает или снимает; у обыска, лечения и переноса — null. */
    @Nullable
    public Restraint restraint() {
        return restraint;
    }

    public boolean removes() {
        return removes;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Подпись пункта меню: «Накинуть мешок», «Снять кандалы». */
    public String menuKey() {
        return "social.halgventmod.action." + id();
    }

    /** Подпись над шкалой: «Обыск», «Мешок». */
    public String barKey() {
        return "social.halgventmod.bar." + id();
    }

    /** Сообщение жертве, когда действие прошло: «Вас обыскали». */
    public String doneKey() {
        return "social.halgventmod.done." + id();
    }

    /**
     * Подходит ли действие к тому, что сейчас на жертве: накладывается — нет такого, снимается — есть;
     * вылечить и нести — только поверженного.
     */
    public boolean fits(Player target) {
        return switch (this) {
            case HEAL -> Downed.isDowned(target) || Injuries.needsCare(target); // повержен, кровь течёт, травмы, раны
            case CARRY -> Downed.isDowned(target);
            case DROP -> false;
            default -> restraint == null || restraint.on(target) == removes;
        };
    }

    /** Пункты меню по жертве: обыск и для каждого ограничения — наложить или снять (ТЗ 3.2), у поверженного — ещё лечение и перенос. */
    public static List<SocialAction> menuFor(Player target) {
        List<SocialAction> options = new ArrayList<>(List.of(SEARCH,
                Restraint.BAG.on(target) ? UNBAG : BAG,
                Restraint.SHACKLES.on(target) ? UNSHACKLE : SHACKLES,
                Restraint.CUFFS.on(target) ? UNCUFF : CUFFS));
        if (Downed.isDowned(target)) {
            options.add(HEAL);
            options.add(CARRY);
        }
        return options;
    }
}
