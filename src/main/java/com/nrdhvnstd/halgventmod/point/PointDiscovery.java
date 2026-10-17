package com.nrdhvnstd.halgventmod.point;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.mojang.serialization.Codec;

/** Какие точки игрок уже открыл (номера MapPoint.id). Вложение игрока, сохраняется и переживает смерть. */
public final class PointDiscovery {
    public static final Codec<PointDiscovery> CODEC = Codec.INT.listOf().xmap(
            list -> new PointDiscovery(new HashSet<>(list)),
            discovery -> new ArrayList<>(discovery.ids));

    private final Set<Integer> ids;

    public PointDiscovery() {
        this(new HashSet<>());
    }

    private PointDiscovery(Set<Integer> ids) {
        this.ids = ids;
    }

    public boolean has(int id) {
        return ids.contains(id);
    }

    /** true — точка только что открыта. */
    public boolean add(int id) {
        return ids.add(id);
    }

    public Set<Integer> ids() {
        return ids;
    }
}
