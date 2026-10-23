package com.nrdhvnstd.halgventmod.smithing;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.needs.Stamina;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Добыча руды ударами ЛКМ (клиент шлёт OreStrikePayload, OreMiningInput). Удар — только с полного замаха, тратит
 * выносливость ({@link OreKind#stamina}) и прочность кирки или лопаты. После каждого удара на камне вспыхивает
 * искра — трещина: следующий удар точно по ней ({@link #SPARK_RADIUS}) засчитывается за два. Набрали
 * {@link OreKind#strikes} — выпадает кусок руды (или пустая порода), и жила может обеднеть на ступень.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class OreMining {
    public static final double REACH = 5.0;
    public static final double SPARK_RADIUS = 0.2;     // ⚠️ насколько точно надо попасть по искре (доля блока)
    public static final int SPARK_TICKS = 40;          // искра горит 2 секунды
    public static final float STEADY = 0.9F;           // замах, с которого удар засчитывается
    public static final float RICH_BONUS = 0.3F;       // ⚠️ у богатой жилы шанс на второй кусок

    /** Ход добычи одного блока: удары и где искра. */
    private static final class Progress {
        int strikes;
        Vec3 spark;
        long sparkUntil;
        ServerLevel level;
    }

    private static final Map<GlobalPos, Progress> PROGRESS = new HashMap<>();

    private OreMining() {}

    public static void strike(ServerPlayer player, BlockPos pos, Vec3 hit) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof OreVeinBlock vein) || !player.canInteractWithBlock(pos, 1.0)
                || player.isSpectator()) {
            return;
        }
        OreKind kind = vein.kind();
        state = VeinClock.catchUp(level, pos, state, kind.regrow());
        ItemStack tool = player.getMainHandItem();
        if (!tool.is(kind.tool())) {
            Smithing.say(player, kind.dug() ? "need_shovel" : "need_pickaxe");
            return;
        }
        int richness = state.getValue(OreVeinBlock.RICHNESS);
        if (richness <= 0) {
            Smithing.say(player, kind.dug() ? "silt_empty" : "vein_empty", VeinClock.until(level, pos));
            return;
        }
        if (Stamina.exhausted(player) || player.getAttackStrengthScale(0.5F) < STEADY) {
            return;
        }
        player.resetAttackStrengthTicker();
        player.swing(InteractionHand.MAIN_HAND, true);
        Stamina.spend(player, kind.stamina());
        if (!player.getAbilities().instabuild) {
            tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }

        GlobalPos key = GlobalPos.of(level.dimension(), pos.immutable());
        Progress progress = PROGRESS.computeIfAbsent(key, k -> new Progress());
        progress.level = level;
        boolean onSpark = progress.spark != null && level.getGameTime() <= progress.sparkUntil
                && progress.spark.distanceTo(hit) <= SPARK_RADIUS;
        progress.strikes += onSpark ? 2 : 1;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), hit.x, hit.y, hit.z, onSpark ? 14 : 6,
                0.08, 0.08, 0.08, 0.1);
        level.playSound(null, pos, kind.dug() ? SoundEvents.MUD_HIT : SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.0F,
                onSpark ? 1.3F : 0.8F + level.random.nextFloat() * 0.2F);
        if (onSpark) {
            level.sendParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 10, 0.05, 0.05, 0.05, 0.3);
            level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.25F, 2.0F);
        }

        if (progress.strikes < kind.strikes()) {
            progress.spark = kind.dug() ? null : sparkOn(level, pos, hit);
            progress.sparkUntil = level.getGameTime() + SPARK_TICKS;
            Smithing.say(player, onSpark ? "strike_spark" : "strike", progress.strikes, kind.strikes());
            return;
        }
        PROGRESS.remove(key);
        if (level.random.nextFloat() < kind.barren()) {
            Smithing.say(player, "barren");
            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
        } else {
            int count = richness == 3 && level.random.nextFloat() < RICH_BONUS ? 2 : 1;
            ItemStack lump = new ItemStack(kind.ore(), count);
            Smithing.say(player, "lump", lump.getHoverName(), count);
            player.getInventory().add(lump);
            if (!lump.isEmpty()) {
                player.drop(lump, false);
            }
            level.playSound(null, pos, kind.dug() ? SoundEvents.MUD_BREAK : SoundEvents.STONE_BREAK, SoundSource.BLOCKS,
                    1.0F, 0.9F);
        }
        if (level.random.nextFloat() < kind.deplete()) {
            level.setBlock(pos, state.setValue(OreVeinBlock.RICHNESS, richness - 1), 3);
            VeinClock.depleted(level, pos, kind.regrow());
            if (richness - 1 == 0) {
                Smithing.say(player, kind.dug() ? "silt_exhausted" : "vein_exhausted", VeinClock.until(level, pos));
            }
        }
    }

    /** Искра — случайная точка на той грани, по которой пришёлся удар. */
    private static Vec3 sparkOn(ServerLevel level, BlockPos pos, Vec3 hit) {
        Vec3 center = pos.getCenter();
        Vec3 d = hit.subtract(center);
        Direction face = Direction.getNearest(d.x, d.y, d.z);
        double u = (level.random.nextDouble() - 0.5) * 0.6;
        double v = (level.random.nextDouble() - 0.5) * 0.6;
        double out = 0.51;
        return switch (face.getAxis()) {
            case X -> center.add(face.getStepX() * out, u, v);
            case Y -> center.add(u, face.getStepY() * out, v);
            case Z -> center.add(u, v, face.getStepZ() * out);
        };
    }

    // Искры горят, пока не погаснут: раз в 4 тика — вспышка в том же месте
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PROGRESS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<GlobalPos, Progress>> it = PROGRESS.entrySet().iterator();
        while (it.hasNext()) {
            Progress progress = it.next().getValue();
            long now = progress.level.getGameTime();
            if (now > progress.sparkUntil + 20 * 60) {
                it.remove(); // бросили на минуту — начатое пропало
                continue;
            }
            if (progress.spark != null && now <= progress.sparkUntil && now % 4 == 0) {
                Vec3 s = progress.spark;
                progress.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, s.x, s.y, s.z, 2, 0.01, 0.01, 0.01, 0.02);
                progress.level.sendParticles(ParticleTypes.SMALL_FLAME, s.x, s.y, s.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }
}
