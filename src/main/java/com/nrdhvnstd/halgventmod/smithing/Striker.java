package com.nrdhvnstd.halgventmod.smithing;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.nrdhvnstd.halgventmod.needs.Stamina;
import com.nrdhvnstd.halgventmod.network.StrikerBlowPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Молотобоец: второй игрок с кувалдой у наковальни, где идёт ковка. E кувалдой — окно молотобойца (StrikerScreen):
 * бить в такт. Каждый удар уходит кузнецу (StrikerBlowPayload): тяжёлый удар в то место, куда кузнец указывает
 * молотком, — на вытяжке, обжиме, осадке и выколотке работа идёт быстрее. Удар тратит выносливость молотобойца.
 */
public final class Striker {
    public static final float STAMINA = 8;        // ⚠️ за удар кувалдой
    public static final int MIN_INTERVAL = 10;    // тиков между ударами — не чаще
    public static final int JOINED = -1;          // кузнецу: молотобоец встал к наковальне
    public static final int LEFT = -2;            // кузнецу: отошёл

    private static final Map<UUID, Long> LAST = new HashMap<>();

    private Striker() {}

    /** Встать молотобойцем. false — у наковальни никто не куёт (тогда откроется окно наковальни). */
    public static boolean join(ServerPlayer helper, BlockPos anvil) {
        UUID smith = Minigame.workingAt(anvil, Minigame.ANVIL);
        if (smith == null || smith.equals(helper.getUUID())) {
            return false;
        }
        ServerPlayer smithPlayer = helper.server.getPlayerList().getPlayer(smith);
        if (smithPlayer == null) {
            return false;
        }
        Minigame.open(helper, anvil, Minigame.STRIKER, 0, 0, 0, 0);
        PacketDistributor.sendToPlayer(smithPlayer, new StrikerBlowPayload(JOINED));
        Smithing.say(helper, "striker_joined", smithPlayer.getDisplayName());
        return true;
    }

    /** Удар молотобойца: accuracy — насколько попал в такт, 0–100. */
    public static void hit(ServerPlayer helper, BlockPos anvil, int accuracy) {
        Minigame.Session session = Minigame.current(helper);
        if (session == null || session.kind() != Minigame.STRIKER || !session.pos().equals(anvil)
                || helper.distanceToSqr(anvil.getCenter()) > 16) {
            return;
        }
        long now = helper.serverLevel().getGameTime();
        Long last = LAST.get(helper.getUUID());
        if (last != null && now - last < MIN_INTERVAL || Stamina.exhausted(helper)) {
            return;
        }
        ItemStack sledge = helper.getMainHandItem();
        if (!sledge.is(SmithingItems.SLEDGEHAMMER.get())) {
            return;
        }
        UUID smith = Minigame.workingAt(anvil, Minigame.ANVIL);
        ServerPlayer smithPlayer = smith == null ? null : helper.server.getPlayerList().getPlayer(smith);
        if (smithPlayer == null) {
            Smithing.say(helper, "striker_alone");
            return;
        }
        LAST.put(helper.getUUID(), now);
        Stamina.spend(helper, STAMINA);
        helper.swing(InteractionHand.MAIN_HAND, true);
        if (!helper.getAbilities().instabuild) {
            sledge.hurtAndBreak(1, helper, EquipmentSlot.MAINHAND);
        }
        helper.serverLevel().playSound(null, anvil, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5F,
                0.6F + accuracy / 300F);
        PacketDistributor.sendToPlayer(smithPlayer, new StrikerBlowPayload(Math.clamp(accuracy, 0, 100)));
    }

    /** Молотобоец закрыл окно. */
    public static void leave(ServerPlayer helper, BlockPos anvil) {
        UUID smith = Minigame.workingAt(anvil, Minigame.ANVIL);
        ServerPlayer smithPlayer = smith == null ? null : helper.server.getPlayerList().getPlayer(smith);
        if (smithPlayer != null) {
            PacketDistributor.sendToPlayer(smithPlayer, new StrikerBlowPayload(LEFT));
        }
    }
}
