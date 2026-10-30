package com.nrdhvnstd.halgventmod.combat;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Мобы-люди с оружием дерутся боевым ИИ (CombatAiGoal) — первым приоритетом, ванильная атака ждёт: зомби, скелеты,
 * разбойники, пиглины. Безоружный (или с луком) — по-ванильному. Поборник, брутальный пиглин и скелет-иссушитель —
 * бывалые, прочие — простые.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class CombatAiAttach {
    private static final double SPEED = 1.0; // ⚠️

    private CombatAiAttach() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof PathfinderMob mob)) {
            return;
        }
        if (!(mob instanceof Zombie || mob instanceof AbstractSkeleton || mob instanceof AbstractIllager
                || mob instanceof AbstractPiglin)) {
            return;
        }
        boolean veteran = mob instanceof AbstractIllager || mob instanceof PiglinBrute || mob instanceof WitherSkeleton;
        mob.goalSelector.addGoal(1, new CombatAiGoal(mob, veteran ? CombatAiGoal.VETERAN : CombatAiGoal.COMMON, SPEED));
    }
}
