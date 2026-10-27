package com.nrdhvnstd.halgventmod.npc;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.downed.Downed;
import com.nrdhvnstd.halgventmod.network.NpcInfoPayload;
import com.nrdhvnstd.halgventmod.pickup.Pickup;
import com.nrdhvnstd.halgventmod.social.Social;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * НПС на E (docs/INTERACTION_E_SPEC.md, 3.4): в 2 блоках под прицелом — «[E] Поговорить», E — окно «Информация»
 * с именем и профессией. Пока заглушка: диалогов нет, настройщика НПС нет. Кто считается НПС — тег сущностей
 * halgventmod:npc (по умолчанию житель; администрация дописывает в датапаке).
 */
public final class Npcs {
    public static final TagKey<EntityType<?>> NPC =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "npc"));
    private static final double REACH_MARGIN = 1.0; // сервер прощает сдвиг за время пакета

    private Npcs() {}

    /** Можно ли заговорить (без дальности). Поверженный и несущий не разговаривают. */
    public static boolean canTarget(Player player, Entity entity) {
        return entity instanceof LivingEntity && entity.getType().is(NPC) && entity.isAlive()
                && !player.isSpectator() && !Downed.busy(player);
    }

    /** InteractPayload: проверить и открыть «Информацию». Shift пока ничего не меняет. */
    public static void handle(ServerPlayer player, int entityId, boolean shift) {
        Entity entity = player.level().getEntity(entityId);
        if (entity == null || !canTarget(player, entity) || !player.hasLineOfSight(entity)
                || !Pickup.inReach(player, entity, Social.INTERACT_REACH + REACH_MARGIN)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new NpcInfoPayload(entity.getDisplayName(), profession(entity)));
    }

    // Профессия жителя — ванильным названием; у остальных пока не задаётся
    private static Component profession(Entity entity) {
        if (entity instanceof VillagerDataHolder villager) {
            VillagerProfession profession = villager.getVillagerData().getProfession();
            if (profession != VillagerProfession.NONE) {
                ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
                return Component.translatable("entity." + id.getNamespace() + ".villager." + id.getPath());
            }
        }
        return Component.translatable("npc.halgventmod.no_profession");
    }
}
