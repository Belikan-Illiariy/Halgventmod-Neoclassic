package com.nrdhvnstd.halgventmod.npc;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

/** Роль НПС (docs/TZ_npc.md, 2.1): выбирает цели ИИ и обработчик взаимодействия. */
public enum NpcRole {
    CITIZEN("citizen"),
    MERCHANT("merchant"),
    GUARD("guard");

    private final String id;

    NpcRole(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Название роли — вместо профессии в окне «Информация». */
    public Component title() {
        return Component.translatable("npc.halgventmod.role." + id);
    }

    @Nullable
    public static NpcRole byId(String id) {
        for (NpcRole role : values()) {
            if (role.id.equals(id)) {
                return role;
            }
        }
        return null;
    }

    /** Роль по порядковому номеру из синхронизируемых данных; неверный номер — горожанин. */
    public static NpcRole byOrdinal(int ordinal) {
        NpcRole[] roles = values();
        return ordinal >= 0 && ordinal < roles.length ? roles[ordinal] : CITIZEN;
    }
}
