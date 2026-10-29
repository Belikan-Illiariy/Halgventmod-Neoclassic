package com.nrdhvnstd.halgventmod.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorProtection;
import com.nrdhvnstd.halgventmod.character.Appearance;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.trade.Purse;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * НПС (docs/TZ_npc.md): одна сущность на три роли — стражник, торговец, горожанин. Модель игрока, скин задаёт
 * администрация (пусто — скин собирается из облика). Этап 1: только каркас; движения, расписания и диалогов пока нет.
 */
public class HalgNpc extends PathfinderMob {
    // Меняйте здесь
    private static final double MAX_HEALTH = 20;
    private static final double MOVEMENT_SPEED = 0.3;     // ⚠️ было 0,25
    private static final float LOOK_DISTANCE = 6.0F;
    private static final double ATTACK_DAMAGE = 3;      // кулаком; с оружием урон считает бой по профилю
    private static final double CHASE_SPEED = 1.35;     // стражник за нарушителем
    private static final double FOLLOW_RANGE = 48;      // дальность поиска пути
    private static final double WALK_SPEED = 1.0;       // множители скорости
    private static final double STROLL_SPEED = 1.0;
    private static final double FLEE_SPEED = 1.4;
    private static final double PANIC_SPEED = 1.5;
    private static final float AVOID_DISTANCE = 10.0F;  // от чудовища, блоков
    private static final int ROUTINE_INTERVAL = 20;     // как часто пересчитывать распорядок, тиков
    private static final double DORMANT_RANGE = 48;     // дальше от игроков НПС «спят»
    private static final int BED_SEARCH = 8;            // поиск кровати вокруг дома, блоков
    public static final int ROUTE_MAX = 32;             // точек в маршруте
    public static final int STALLS_MAX = 32;            // прилавков у торговца
    /** Метка в данных яйца призыва (ModItems): «выдать личность при появлении». */
    public static final String EGG_TAG = "EggSpawn";

    private static final EntityDataAccessor<Integer> ROLE =
            SynchedEntityData.defineId(HalgNpc.class, EntityDataSerializers.INT);
    // Скин: «пространство:путь» — текстура textures/entity/npc/<путь>.png; пусто — по облику
    private static final EntityDataAccessor<String> SKIN =
            SynchedEntityData.defineId(HalgNpc.class, EntityDataSerializers.STRING);

    // Облик по частям (Appearance): «часть → вариант»; скин из него собирает клиент
    private static final EntityDataAccessor<CompoundTag> APPEARANCE =
            SynchedEntityData.defineId(HalgNpc.class, EntityDataSerializers.COMPOUND_TAG);
    // Тонкие руки (модель Alex)
    private static final EntityDataAccessor<Boolean> SLIM =
            SynchedEntityData.defineId(HalgNpc.class, EntityDataSerializers.BOOLEAN);

    // Привязки к миру: сервер знает, клиенту не нужны. Место работы (post) и дом (home) задаются порознь,
    // BlockPos.ZERO — не задано; маршрут обхода — точки в мире, их ставит жезл НПС
    private BlockPos home = BlockPos.ZERO;
    private BlockPos post = BlockPos.ZERO;
    private final List<BlockPos> route = new ArrayList<>();
    private final List<BlockPos> stalls = new ArrayList<>(); // прилавки, которыми торгует
    // Шаблон, из которого НПС создан (NpcTemplates); пусто — поставлен вручную
    private String template = "";
    // Игрок-торговец, чьи прилавки продаёт этот НПС (только у роли «торговец»)
    @Nullable
    private UUID merchantOwner;
    private String merchantName = "";
    // Кошелёк торговца: выручка с продаж, из него платится скупка (trade/Shop)
    private final Purse purse = new Purse();
    // Облик картой для отрисовки; сбрасывается при смене данных
    @Nullable
    private Map<String, String> appearanceCache;
    // Создан из яйца (EggSpawn в данных яйца): личность и дом выдаются на первом тике. В сохранение не пишется
    private boolean identityPending;
    // Распорядок (не сохраняется, считается заново после загрузки)
    @Nullable
    private NpcSchedule.Activity activity;
    @Nullable
    private BlockPos bed;
    private boolean awake = true; // рядом есть игрок; пока не проверили — считаем, что есть
    @Nullable
    private BlockPos alarmPos;    // тревога стражи: место правонарушения
    private long alarmUntil;

    public HalgNpc(EntityType<? extends HalgNpc> type, Level level) {
        super(type, level);
        setPersistenceRequired(); // НПС не пропадает вдали от игрока
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE);
    }

    // Ходит сквозь двери: открывает и закрывает, запертых на замок не проходит (NpcDoors)
    @Override
    protected PathNavigation createNavigation(Level level) {
        return NpcDoors.navigation(this, level);
    }

    // Горожанин и торговец при ударе или пожаре удирают; стражник — нет (его бой — этап 7)
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, NpcDoors.openDoorGoal(this)); // двери открывает и закрывает за собой, запертые — нет
        goalSelector.addGoal(1, new PanicGoal(this, PANIC_SPEED) {
            @Override
            protected boolean shouldPanic() {
                return getRole() != NpcRole.GUARD && super.shouldPanic();
            }
        });
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Monster.class, monster -> getRole() != NpcRole.GUARD,
                AVOID_DISTANCE, WALK_SPEED, FLEE_SPEED, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test));
        goalSelector.addGoal(3, new SleepInBedGoal(this));
        // Стражник: бьёт нарушителя (бой и броня — боевая система), без розыска ходит по маршруту
        goalSelector.addGoal(2, new MeleeAttackGoal(this, CHASE_SPEED, true) {
            @Override
            public boolean canUse() {
                return getRole() == NpcRole.GUARD && super.canUse();
            }
        });
        targetSelector.addGoal(1, new PursueOffenderGoal(this));
        goalSelector.addGoal(3, new RespondToAlarmGoal(this, CHASE_SPEED));
        goalSelector.addGoal(4, new PatrolGoal(this));
        goalSelector.addGoal(4, new VisitPoiGoal(this));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, WALK_SPEED));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, STROLL_SPEED) {
            @Override
            public boolean canUse() {
                return activity != NpcSchedule.Activity.SLEEP && super.canUse(); // ночью без кровати стоит
            }
        });
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, LOOK_DISTANCE));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROLE, NpcRole.CITIZEN.ordinal());
        builder.define(SKIN, "");
        builder.define(APPEARANCE, new CompoundTag());
        builder.define(SLIM, false);
    }

    public boolean isSlim() {
        return entityData.get(SLIM);
    }

    public void setSlim(boolean slim) {
        entityData.set(SLIM, slim);
    }

    /** Облик НПС; не задан — облик по умолчанию. */
    public Map<String, String> getAppearance() {
        if (appearanceCache == null) {
            CompoundTag tag = entityData.get(APPEARANCE);
            Map<String, String> map = new HashMap<>();
            for (String key : tag.getAllKeys()) {
                map.put(key, tag.getString(key));
            }
            appearanceCache = map.isEmpty() ? Appearance.DEFAULT : Map.copyOf(map);
        }
        return appearanceCache;
    }

    public void setAppearance(Map<String, String> appearance) {
        CompoundTag tag = new CompoundTag();
        appearance.forEach(tag::putString);
        entityData.set(APPEARANCE, tag);
    }

    /** Игрок-торговец, чьим товаром торгует этот НПС (trade/Shop); null — не привязан. */
    @Nullable
    public UUID getMerchantOwner() {
        return merchantOwner;
    }

    public String getMerchantName() {
        return merchantName;
    }

    /** Кошелёк торговца (касса лавки). */
    public Purse getPurse() {
        return purse;
    }

    /** Привязать к игроку-торговцу (null — отвязать). */
    public void setMerchant(@Nullable UUID owner, String name) {
        merchantOwner = owner;
        merchantName = owner == null ? "" : name;
    }

    public String getTemplate() {
        return template;
    }

    /** Личность из шаблона: имя, облик, скин и руки. Шаблон запоминается. */
    public void applyIdentity(NpcTemplate source, NpcTemplate.Identity identity) {
        template = source.id().toString();
        if (!identity.name().isEmpty()) {
            setCustomName(Component.literal(identity.name()));
        }
        setAppearance(identity.appearance());
        setSkin(identity.skin());
        setSlim(identity.slim());
    }

    // Из яйца: роль приходит в данных яйца уже после создания, личность выдаём на первом тике
    @Override
    public void tick() {
        super.tick();
        if (identityPending && !level().isClientSide) {
            identityPending = false;
            NpcTemplate source = NpcTemplates.random(getRole(), getRandom());
            if (source != null) {
                Component name = getCustomName();
                applyIdentity(source, source.generate(getRandom()));
                if (name != null) {
                    setCustomName(name); // имя с наковальни важнее шаблона
                }
            }
            setHome(blockPosition());
            setPost(blockPosition());
        }
        if (!level().isClientSide) {
            if ((tickCount + getId()) % ROUTINE_INTERVAL == 0) {
                updateRoutine();
            }
            updateArmor();
        }
    }

    // ===== Тревога: стражник, услышавший зов, бежит к месту правонарушения (RespondToAlarmGoal) =====

    /** Куда бежать по тревоге; null — тревоги нет или она кончилась. */
    @Nullable
    public BlockPos getAlarm() {
        if (alarmPos != null && level().getGameTime() >= alarmUntil) {
            alarmPos = null;
        }
        return alarmPos;
    }

    public void raiseAlarm(BlockPos where, long until) {
        alarmPos = where;
        alarmUntil = until;
    }

    public void clearAlarm() {
        alarmPos = null;
    }

    // ===== Снаряжение: оружие в руках и броня, как у игрока (docs/TZ_npc.md); выдаётся в окне NpcGearMenu =====

    // Броня НПС уходит клиентам и ложится защитой на атрибуты, как у игрока (ArmorEvents)
    private void updateArmor() {
        ArmorInventory armor = getExistingDataOrNull(ModAttachments.ARMOR);
        if (armor == null) {
            return;
        }
        if (armor.consumeDirty()) {
            syncData(ModAttachments.ARMOR);
        }
        if (armor.consumeProtectionChange()) {
            ArmorProtection.apply(this, armor);
        }
    }

    // ===== Распорядок и сон вдали от игроков (docs/TZ_npc.md, 2.4 и 2.7) =====

    /** Занятие сейчас; null — ещё не определено (после смены роли, дома или поста пересчитывается). */
    @Nullable
    public NpcSchedule.Activity getActivity() {
        return activity;
    }

    /** Кровать, в которую НПС ляжет ночью; null — нет (стоит дома). */
    @Nullable
    public BlockPos getBed() {
        return bed;
    }

    // Раз в ROUTINE_INTERVAL тиков (со сдвигом по номеру сущности, чтобы НПС не считались все в один тик)
    private void updateRoutine() {
        // Тревога будит стражу и вдали от игроков: она должна добежать до места
        boolean near = getAlarm() != null || level().hasNearbyAlivePlayer(getX(), getY(), getZ(), DORMANT_RANGE);
        if (near != awake) {
            awake = near;
            if (!awake) { // уснул вдали от игроков: остановить, чтобы не шёл по старой команде
                getNavigation().stop();
                setZza(0);
                setXxa(0);
                setYya(0);
            }
        }
        if (!awake) {
            return;
        }
        NpcSchedule.Activity now = NpcSchedule.at(getRole(), level().getDayTime());
        if (now == activity) {
            return;
        }
        activity = now;
        if (now != NpcSchedule.Activity.SLEEP && isSleeping()) {
            stopSleeping();
        }
        bed = now == NpcSchedule.Activity.SLEEP ? findBed() : null;
        BlockPos anchor = now == NpcSchedule.Activity.WORK ? post : home;
        if (anchor.equals(BlockPos.ZERO)) {
            clearRestriction(); // привязки нет — бродит где стоит
        } else {
            restrictTo(anchor, NpcSchedule.radius(getRole(), now));
        }
    }

    // Ближайшая свободная кровать у дома (или у поста, если дома нет)
    @Nullable
    private BlockPos findBed() {
        BlockPos center = home.equals(BlockPos.ZERO) ? blockPosition() : home;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-BED_SEARCH, -2, -BED_SEARCH),
                center.offset(BED_SEARCH, 2, BED_SEARCH))) {
            if (!level().getBlockState(pos).is(BlockTags.BEDS)) {
                continue;
            }
            double distance = pos.distSqr(center);
            if (distance < bestDistance && !occupied(pos)) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    private boolean occupied(BlockPos bed) {
        return !level().getEntitiesOfClass(HalgNpc.class, new AABB(bed).inflate(1.0),
                other -> other != this && other.isSleeping()).isEmpty();
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || isSleeping();
    }

    // Нет игроков рядом — ИИ и движение отключены (на клиенте всегда true)
    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && awake;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (APPEARANCE.equals(key)) {
            appearanceCache = null;
        }
    }

    public NpcRole getRole() {
        return NpcRole.byOrdinal(entityData.get(ROLE));
    }

    public void setRole(NpcRole role) {
        entityData.set(ROLE, role.ordinal());
        activity = null; // распорядок зависит от роли
    }

    /** Скин «пространство:путь»; пустая строка — по облику. */
    public String getSkin() {
        return entityData.get(SKIN);
    }

    public void setSkin(String skin) {
        entityData.set(SKIN, skin);
    }

    public BlockPos getHome() {
        return home;
    }

    public void setHome(BlockPos home) {
        this.home = home;
        activity = null;
    }

    public BlockPos getPost() {
        return post;
    }

    public void setPost(BlockPos post) {
        this.post = post;
        activity = null;
    }

    /** Точки маршрута обхода (строит администратор жезлом НПС); пусто — стоит на посту. */
    public List<BlockPos> getRoute() {
        return List.copyOf(route);
    }

    /** Добавить точку в конец маршрута; false — маршрут уже из ROUTE_MAX точек. */
    public boolean addRoutePoint(BlockPos pos) {
        if (route.size() >= ROUTE_MAX) {
            return false;
        }
        route.add(pos.immutable());
        return true;
    }

    /** Убрать последнюю точку; false — маршрут пуст. */
    public boolean removeLastRoutePoint() {
        if (route.isEmpty()) {
            return false;
        }
        route.remove(route.size() - 1);
        return true;
    }

    // ===== Прилавки торговца (привязывает жезл НПС, режим «Прилавок»): ими он и торгует (trade/Shop) =====

    public List<BlockPos> getStalls() {
        return List.copyOf(stalls);
    }

    /** Привязать прилавок (true) или, если уже привязан, отвязать (false). */
    public boolean toggleStall(BlockPos pos) {
        if (stalls.remove(pos)) {
            return false;
        }
        if (stalls.size() < STALLS_MAX) {
            stalls.add(pos.immutable());
        }
        return true;
    }

    public void removeStall(BlockPos pos) {
        stalls.remove(pos);
    }

    public void clearRoute() {
        route.clear();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Role", getRole().id());
        tag.putString("Skin", getSkin());
        tag.put("Appearance", entityData.get(APPEARANCE).copy());
        tag.putBoolean("Slim", isSlim());
        tag.putString("Template", template);
        if (merchantOwner != null) {
            tag.putUUID("MerchantOwner", merchantOwner);
        }
        tag.putString("MerchantName", merchantName);
        tag.putIntArray("Purse", purse.save());
        tag.put("Home", NbtUtils.writeBlockPos(home));
        tag.put("Post", NbtUtils.writeBlockPos(post));
        tag.putLongArray("Route", route.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("Stalls", stalls.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NpcRole role = NpcRole.byId(tag.getString("Role"));
        setRole(role == null ? NpcRole.CITIZEN : role);
        setSkin(tag.getString("Skin"));
        entityData.set(APPEARANCE, tag.getCompound("Appearance"));
        setSlim(tag.getBoolean("Slim"));
        template = tag.getString("Template");
        merchantOwner = tag.hasUUID("MerchantOwner") ? tag.getUUID("MerchantOwner") : null;
        merchantName = tag.getString("MerchantName");
        purse.load(tag.getIntArray("Purse"));
        identityPending = tag.getBoolean(EGG_TAG);
        home = NbtUtils.readBlockPos(tag, "Home").orElse(BlockPos.ZERO);
        post = NbtUtils.readBlockPos(tag, "Post").orElse(BlockPos.ZERO);
        route.clear();
        stalls.clear();
        for (long stall : tag.getLongArray("Stalls")) {
            stalls.add(BlockPos.of(stall));
        }
        for (long point : tag.getLongArray("Route")) {
            route.add(BlockPos.of(point));
        }
    }
}
