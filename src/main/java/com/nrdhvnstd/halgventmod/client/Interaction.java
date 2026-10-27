package com.nrdhvnstd.halgventmod.client;

import java.util.Optional;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.downed.Downed;
import com.nrdhvnstd.halgventmod.npc.Npcs;
import com.nrdhvnstd.halgventmod.pickup.Pickup;
import com.nrdhvnstd.halgventmod.social.Social;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Что сделает E сейчас (docs/INTERACTION_E_SPEC.md, 2) — один ответ на тик, по нему и действие (PickupInput),
 * и подсказки (PickupInput, SocialMenuHud, NpcHint, UseHint, DownedHud), поэтому они не расходятся.
 *
 * Порядок: руки заняты (поверженный — сдаться, несущий — бросить); вещь или стрела в 3 блоках; игрок или НПС
 * в 2 блоках; блок с действием в 3 блоках; повязка или мазь в руке — перевязаться; любой другой блок (как ПКМ
 * пустой рукой); ничего. Сервер всё перепроверяет — здесь только выбор.
 */
public record Interaction(Kind kind, @Nullable Entity entity, @Nullable BlockHitResult block) {
    public enum Kind { NONE, SURRENDER, DROP, ITEM, PLAYER, NPC, BLOCK, HEAL_SELF, PLAIN_BLOCK }

    public static final Interaction NONE = new Interaction(Kind.NONE, null, null);

    private static final double RAY_EXTRA = 1.0;    // луч до игрока длиннее радиуса: до хитбокса он доходит позже ближайшей точки
    private static final double SCAN_RADIUS = 4.0;  // игроков и НПС ищем только поблизости

    private static Interaction current = NONE;
    private static int lastUsedTick = Integer.MIN_VALUE / 2;

    /** Результат этого тика. */
    public static Interaction current() {
        return current;
    }

    /** Раз в тик, из PickupInput — до опроса клавиши. */
    static Interaction resolve(Minecraft minecraft, @Nullable LocalPlayer player) {
        current = player == null || minecraft.screen != null || player.isSpectator() ? NONE : find(minecraft, player);
        return current;
    }

    private static Interaction find(Minecraft minecraft, LocalPlayer player) {
        if (Downed.isDowned(player)) {
            return new Interaction(Kind.SURRENDER, player, null);
        }
        Player carried = Downed.carried(player);
        if (carried != null) {
            return new Interaction(Kind.DROP, carried, null);
        }
        Entity item = PickupInput.findTarget(player);
        if (item != null) {
            return new Interaction(Kind.ITEM, item, null);
        }
        Entity someone = findPerson(player);
        if (someone != null) {
            return new Interaction(someone instanceof Player ? Kind.PLAYER : Kind.NPC, someone, null);
        }
        BlockHitResult block = findBlock(player);
        if (block != null && minecraft.level != null && UseHint.usable(minecraft.level.getBlockState(block.getBlockPos()))) {
            return new Interaction(Kind.BLOCK, null, block);
        }
        if (Social.canHealSelf(player)) {
            return new Interaction(Kind.HEAL_SELF, player, null);
        }
        return block != null ? new Interaction(Kind.PLAIN_BLOCK, null, block) : NONE;
    }

    // Ближайший игрок или НПС на луче взгляда до первого блока, в 2 блоках от глаз до хитбокса
    @Nullable
    private static Entity findPerson(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(Social.INTERACT_REACH + RAY_EXTRA));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity other : player.level().getEntities(player, new AABB(player.blockPosition()).inflate(SCAN_RADIUS),
                e -> Social.canTarget(player, e) || Npcs.canTarget(player, e))) {
            Optional<Vec3> hit = other.getBoundingBox().clip(eye, end);
            if (hit.isPresent() && Pickup.inReach(player, other, Social.INTERACT_REACH)) {
                double distance = eye.distanceToSqr(hit.get());
                if (distance < bestDistance) {
                    best = other;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    // Блок под прицелом в 3 блоках (ТЗ 2, пункт 3)
    @Nullable
    private static BlockHitResult findBlock(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(Pickup.REACH));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** Общий кулдаун взаимодействий 0,4 с (ТЗ 3.6): подбор и открытие не идут одно за другим на одном нажатии. */
    static boolean ready(LocalPlayer player) {
        return player.tickCount - lastUsedTick >= Pickup.PICKUP_COOLDOWN || player.tickCount < lastUsedTick;
    }

    static void used(LocalPlayer player) {
        lastUsedTick = player.tickCount;
    }
}
