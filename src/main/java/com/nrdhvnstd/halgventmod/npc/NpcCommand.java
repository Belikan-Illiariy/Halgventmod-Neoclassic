package com.nrdhvnstd.halgventmod.npc;

import javax.annotation.Nullable;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.registry.ModEntities;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Команды НПС, только для администрации (уровень 2).
 *
 * /halgnpc spawn &lt;citizen|merchant|guard&gt; [имя]  — поставить НПС на месте игрока (личность из случайного шаблона роли)
 * /halgnpc spawn template &lt;id&gt;                   — то же из названного шаблона
 * /halgnpc reroll                                 — новая личность НПС под прицелом из шаблона его роли
 * /halgnpc role &lt;роль&gt;                           — сменить роль НПС под прицелом
 * /halgnpc skin &lt;пространство:путь&gt; [slim]       — скин НПС под прицелом (textures/entity/npc/&lt;путь&gt;.png), slim — тонкие руки
 * /halgnpc skin clear                             — скин по облику
 * /halgnpc remove                                 — убрать НПС под прицелом
 * /halgnpc stress &lt;число&gt; [role &lt;роль&gt;]          — нагрузочный тест: столько НПС кольцом вокруг (до 500)
 * /halgnpc stats                                  — сколько НПС в мире, сколько спят, средний тик сервера
 * /halgnpc purge                                  — убрать всех НПС этого измерения
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class NpcCommand {
    private static final double TARGET_RANGE = 8.0; // Меняйте здесь
    private static final int STRESS_MAX = 500;
    private static final double STRESS_MIN_RADIUS = 6;
    private static final double STRESS_MAX_RADIUS = 30;

    private NpcCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn");
        LiteralArgumentBuilder<CommandSourceStack> role = Commands.literal("role");
        for (NpcRole each : NpcRole.values()) {
            spawn.then(Commands.literal(each.id())
                    .executes(context -> spawn(context.getSource(), each, null))
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(context -> spawn(context.getSource(), each,
                                    StringArgumentType.getString(context, "name")))));
            role.then(Commands.literal(each.id()).executes(context -> setRole(context.getSource(), each)));
        }
        spawn.then(Commands.literal("template")
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(NpcTemplates.ids(), builder))
                        .executes(context -> spawnTemplate(context.getSource(),
                                ResourceLocationArgument.getId(context, "id")))));
        event.getDispatcher().register(Commands.literal("halgnpc")
                .requires(source -> source.hasPermission(Permissions.ADMIN_LEVEL))
                .then(spawn)
                .then(role)
                .then(Commands.literal("skin")
                        .then(Commands.literal("clear").executes(context -> setSkin(context.getSource(), "", false)))
                        .then(Commands.argument("skin", ResourceLocationArgument.id())
                                .executes(context -> setSkin(context.getSource(), skinArgument(context), false))
                                .then(Commands.argument("slim", BoolArgumentType.bool())
                                        .executes(context -> setSkin(context.getSource(), skinArgument(context),
                                                BoolArgumentType.getBool(context, "slim"))))))
                .then(Commands.literal("stress")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, STRESS_MAX))
                                .executes(context -> stress(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count"), NpcRole.CITIZEN))
                                .then(roleArgument())))
                .then(Commands.literal("stats").executes(context -> stats(context.getSource())))
                .then(Commands.literal("purge").executes(context -> purge(context.getSource())))
                .then(Commands.literal("reroll").executes(context -> reroll(context.getSource())))
                .then(Commands.literal("remove").executes(context -> remove(context.getSource()))));
    }

    // stress <число> <роль>: роли литералами, как у spawn
    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> roleArgument() {
        LiteralArgumentBuilder<CommandSourceStack> roles = Commands.literal("role");
        for (NpcRole each : NpcRole.values()) {
            roles.then(Commands.literal(each.id()).executes(context -> stress(context.getSource(),
                    IntegerArgumentType.getInteger(context, "count"), each)));
        }
        return roles;
    }

    // Нагрузочный тест (TZ_npc, этап 3): count НПС кольцом вокруг администратора, у каждого пост на своём месте
    private static int stress(CommandSourceStack source, int count, NpcRole role) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        int placed = 0;
        for (int i = 0; i < count; i++) {
            HalgNpc npc = ModEntities.NPC.get().create(level);
            if (npc == null) {
                break;
            }
            double angle = level.random.nextDouble() * Math.PI * 2;
            double distance = STRESS_MIN_RADIUS + level.random.nextDouble() * (STRESS_MAX_RADIUS - STRESS_MIN_RADIUS);
            BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(
                    player.getX() + Math.cos(angle) * distance, 0, player.getZ() + Math.sin(angle) * distance));
            npc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            npc.setRole(role);
            npc.setHome(at);
            npc.setPost(at);
            NpcTemplate template = NpcTemplates.random(role, level.random);
            if (template != null) {
                npc.applyIdentity(template, template.generate(level.random));
            }
            level.addFreshEntity(npc);
            placed++;
        }
        int total = placed;
        source.sendSuccess(() -> Component.translatable("npc.halgventmod.command.stress", total), true);
        return placed;
    }

    // Сколько НПС в мире, сколько из них не спят вдали от игроков, средний тик сервера
    private static int stats(CommandSourceStack source) {
        int total = 0;
        int sleeping = 0;
        for (Entity entity : source.getLevel().getAllEntities()) {
            if (entity instanceof HalgNpc npc) {
                total++;
                if (npc.isSleeping()) {
                    sleeping++;
                }
            }
        }
        int count = total;
        int asleep = sleeping;
        double tickMillis = source.getServer().getAverageTickTimeNanos() / 1_000_000.0;
        source.sendSuccess(() -> Component.translatable("npc.halgventmod.command.stats", count, asleep,
                String.format("%.1f", tickMillis)), false);
        return total;
    }

    // Убрать всех НПС измерения администратора
    private static int purge(CommandSourceStack source) {
        int removed = 0;
        for (Entity entity : source.getLevel().getAllEntities()) {
            if (entity instanceof HalgNpc) {
                entity.discard();
                removed++;
            }
        }
        int count = removed;
        source.sendSuccess(() -> Component.translatable("npc.halgventmod.command.purged", count), true);
        return removed;
    }

    private static String skinArgument(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(context, "skin");
        return id.toString();
    }

    // Случайный шаблон роли; шаблонов нет — пустой НПС без личности
    private static int spawn(CommandSourceStack source, NpcRole role, @Nullable String name)
            throws CommandSyntaxException {
        return place(source, role, NpcTemplates.random(role, source.getLevel().getRandom()), name);
    }

    private static int spawnTemplate(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        NpcTemplate template = NpcTemplates.get(id);
        if (template == null) {
            source.sendFailure(Component.translatable("npc.halgventmod.command.no_template", id.toString()));
            return 0;
        }
        return place(source, template.role(), template, null);
    }

    private static int place(CommandSourceStack source, NpcRole role, @Nullable NpcTemplate template,
                             @Nullable String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HalgNpc npc = ModEntities.NPC.get().create(source.getLevel());
        if (npc == null) {
            return 0;
        }
        npc.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        npc.setYHeadRot(player.getYRot());
        npc.setRole(role);
        npc.setHome(player.blockPosition());
        npc.setPost(player.blockPosition());
        if (template != null) {
            npc.applyIdentity(template, template.generate(source.getLevel().getRandom()));
        }
        if (name != null) {
            npc.setCustomName(Component.literal(name));
        }
        source.getLevel().addFreshEntity(npc);
        source.sendSuccess(() -> Component.translatable("npc.halgventmod.command.spawned",
                npc.getDisplayName(), role.title()), true);
        return 1;
    }

    // Новая личность НПС под прицелом из случайного шаблона его роли
    private static int reroll(CommandSourceStack source) throws CommandSyntaxException {
        HalgNpc npc = target(source);
        if (npc == null) {
            return 0;
        }
        NpcTemplate template = NpcTemplates.random(npc.getRole(), source.getLevel().getRandom());
        if (template == null) {
            source.sendFailure(Component.translatable("npc.halgventmod.command.no_templates", npc.getRole().title()));
            return 0;
        }
        npc.applyIdentity(template, template.generate(source.getLevel().getRandom()));
        source.sendSuccess(() -> Component.translatable("npc.halgventmod.command.rerolled", npc.getDisplayName()), true);
        return 1;
    }

    private static int setRole(CommandSourceStack source, NpcRole role) throws CommandSyntaxException {
        HalgNpc npc = target(source);
        if (npc == null) {
            return 0;
        }
        npc.setRole(role);
        source.sendSuccess(() -> Component.translatable("npc.halgventmod.command.role", role.title()), true);
        return 1;
    }

    private static int setSkin(CommandSourceStack source, String skin, boolean slim) throws CommandSyntaxException {
        HalgNpc npc = target(source);
        if (npc == null) {
            return 0;
        }
        npc.setSkin(skin);
        npc.setSlim(slim);
        source.sendSuccess(() -> skin.isEmpty() ? Component.translatable("npc.halgventmod.command.skin_cleared")
                : Component.translatable("npc.halgventmod.command.skin", skin), true);
        return 1;
    }

    private static int remove(CommandSourceStack source) throws CommandSyntaxException {
        HalgNpc npc = target(source);
        if (npc == null) {
            return 0;
        }
        npc.discard();
        source.sendSuccess(() -> Component.translatable("npc.halgventmod.command.removed"), true);
        return 1;
    }

    /** НПС под прицелом администратора; нет такого — сообщение об ошибке и null. */
    @Nullable
    private static HalgNpc target(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).scale(TARGET_RANGE);
        AABB box = player.getBoundingBox().expandTowards(look).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, from, from.add(look), box,
                entity -> entity instanceof HalgNpc && entity.isAlive(), TARGET_RANGE * TARGET_RANGE);
        Entity entity = hit == null ? null : hit.getEntity();
        if (entity instanceof HalgNpc npc) {
            return npc;
        }
        source.sendFailure(Component.translatable("npc.halgventmod.command.no_target"));
        return null;
    }
}
