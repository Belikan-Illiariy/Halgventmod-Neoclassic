package com.nrdhvnstd.halgventmod.weaving;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Ткачество: числа и сообщения. Меняйте здесь.
 *
 * <pre>
 *   лён (грядка) ─┐                    ┌─ льняная пряжа ─┐
 *                 ├─ чесалка → кудель ─┤                 ├─ станок: основа + уток челноком → ткань ─ шитейный стол
 *   шерсть ───────┘   (со льна — пакля) └─ шерстяная ─────┘
 * </pre>
 * Качество ткани — от изъянов в мини-игре «Зев и прибой» (WeavingScreen), дальше оно идёт в вещь
 * с шитейного стола вместе с поправкой за шитьё. Хранится тем же компонентом, что и качество кожи (LeatherQuality).
 */
public final class Weaving {
    // Чесалка и веретено: сколько держать ПКМ на одну горсть, тиков
    public static final int COMB_TICKS = 30;
    public static final int SPIN_TICKS = 60;
    public static final float TOW_CHANCE = 0.5F;     // с горсти льна падает пакля

    // Станок
    public static final int WEIGHTS = 8;             // грузил на основу
    public static final int WARP_YARN = 4;           // пряжи на основу
    public static final int PASSES = 16;             // прокидок утка на одну ткань
    public static final int PASSES_PER_YARN = 4;     // столько прокидок на моток утка (16 / 4 = 4 мотка)
    public static final int CLOTH = 3;               // отрезов с одной ткани
    public static final int BASE_QUALITY = 1;        // Посредственная; ни одного изъяна — +1
    public static final int FLAWS_BAD = 3;           // столько изъянов — −1
    public static final int FLAWS_RUINED = 7;        // а столько — −2
    public static final int MIN_PASS_TICKS = 10;     // прокидка не чаще (защита от подделанных пакетов)

    // Мини-игра «Зев и прибой» (WeavingScreen): итог прокидки
    public static final int PASS_EVEN = 0;           // ровный ряд
    public static final int PASS_LOOSE = 1;          // прибито слабо — рыхлый ряд, изъян
    public static final int PASS_TIGHT = 2;          // прибито сильно — уток перетянут, кромка сужается, изъян
    public static final int PASS_SNAG = 3;           // челнок зацепил основу — ряда нет, изъян

    private Weaving() {}

    /** Качество ткани по числу изъянов (шкала качества кожи 0–4). */
    public static int clothQuality(int flaws) {
        int quality = BASE_QUALITY + (flaws == 0 ? 1 : 0) - (flaws >= FLAWS_BAD ? 1 : 0) - (flaws >= FLAWS_RUINED ? 1 : 0);
        return Math.clamp(quality, 0, 4);
    }

    public static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.weaving." + key, args), true);
    }
}
