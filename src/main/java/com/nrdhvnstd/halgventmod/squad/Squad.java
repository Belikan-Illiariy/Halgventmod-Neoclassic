package com.nrdhvnstd.halgventmod.squad;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Отряд: название, знамя, лидер, участники (лидер тоже среди них, порядок — по вступлению),
 * звания участников и настройки лидера: урон по своим и кто видит товарищей на карте.
 */
public final class Squad {
    private final UUID id;
    private String name;
    private DyeColor bannerBase = DyeColor.WHITE;
    private BannerPatternLayers bannerPatterns = BannerPatternLayers.EMPTY;
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Map<UUID, String> ranks = new HashMap<>(); // нет записи — звание по умолчанию
    private boolean friendlyFire;     // урон по своим разрешён
    private SquadMapMode mapMode = SquadMapMode.ALL;      // кто видит товарищей на карте
    private final Set<String> mapRanks = new LinkedHashSet<>(); // звания, которым видно в режиме RANKS ("" — «Участник»)

    Squad(UUID id, String name, UUID leader) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        members.add(leader);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public DyeColor bannerBase() {
        return bannerBase;
    }

    public BannerPatternLayers bannerPatterns() {
        return bannerPatterns;
    }

    public UUID leader() {
        return leader;
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    public Set<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    /** Звание, заданное лидером, или пустая строка. */
    public String rank(UUID member) {
        return ranks.getOrDefault(member, "");
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    public SquadMapMode mapMode() {
        return mapMode;
    }

    public Set<String> mapRanks() {
        return Collections.unmodifiableSet(mapRanks);
    }

    /** Видит ли участник товарищей на карте при нынешнем режиме. */
    public boolean seesOnMap(UUID member) {
        return switch (mapMode) {
            case OFF -> false;
            case RANKS -> isLeader(member) || mapRanks.contains(rank(member));
            case ALL -> true;
        };
    }

    void setName(String name) {
        this.name = name;
    }

    void setBanner(DyeColor base, BannerPatternLayers patterns) {
        this.bannerBase = base;
        this.bannerPatterns = patterns;
    }

    void setLeader(UUID leader) {
        this.leader = leader;
    }

    void setRank(UUID member, String rank) {
        if (rank.isEmpty()) {
            ranks.remove(member);
        } else {
            ranks.put(member, rank);
        }
    }

    void setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
    }

    void setMapMode(SquadMapMode mapMode) {
        this.mapMode = mapMode;
    }

    void setMapRank(String rank, boolean included) {
        if (included) {
            mapRanks.add(rank);
        } else {
            mapRanks.remove(rank);
        }
    }

    void add(UUID player) {
        members.add(player);
    }

    void remove(UUID player) {
        members.remove(player);
        ranks.remove(player);
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putString("banner_base", bannerBase.getSerializedName());
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        BannerPatternLayers.CODEC.encodeStart(ops, bannerPatterns).ifSuccess(patterns -> tag.put("banner_patterns", patterns));
        tag.putUUID("leader", leader);
        ListTag list = new ListTag();
        for (UUID member : members) {
            CompoundTag entry = new CompoundTag();
            entry.put("id", NbtUtils.createUUID(member));
            String rank = ranks.get(member);
            if (rank != null) {
                entry.putString("rank", rank);
            }
            list.add(entry);
        }
        tag.put("members", list);
        tag.putBoolean("friendly_fire", friendlyFire);
        tag.putString("map_mode", mapMode.id());
        ListTag mapRankList = new ListTag();
        for (String rank : mapRanks) {
            mapRankList.add(StringTag.valueOf(rank));
        }
        tag.put("map_ranks", mapRankList);
        return tag;
    }

    static Squad load(CompoundTag tag, HolderLookup.Provider registries) {
        Squad squad = new Squad(tag.getUUID("id"), tag.getString("name"), tag.getUUID("leader"));
        squad.bannerBase = DyeColor.byName(tag.getString("banner_base"), DyeColor.WHITE);
        if (tag.contains("banner_patterns")) {
            RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
            BannerPatternLayers.CODEC.parse(ops, tag.get("banner_patterns"))
                    .ifSuccess(patterns -> squad.bannerPatterns = patterns);
        }
        for (Tag entry : tag.getList("members", Tag.TAG_COMPOUND)) {
            CompoundTag member = (CompoundTag) entry;
            UUID id = NbtUtils.loadUUID(member.get("id"));
            squad.members.add(id);
            if (member.contains("rank")) {
                squad.ranks.put(id, member.getString("rank"));
            }
        }
        squad.friendlyFire = tag.getBoolean("friendly_fire");
        if (tag.contains("map_mode")) {
            squad.mapMode = SquadMapMode.byId(tag.getString("map_mode"));
        } else if (tag.contains("show_on_map")) { // первая версия окна: вкл/выкл
            squad.mapMode = tag.getBoolean("show_on_map") ? SquadMapMode.ALL : SquadMapMode.OFF;
        }
        for (Tag rank : tag.getList("map_ranks", Tag.TAG_STRING)) {
            squad.mapRanks.add(rank.getAsString());
        }
        return squad;
    }
}
