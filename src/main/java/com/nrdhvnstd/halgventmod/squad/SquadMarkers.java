package com.nrdhvnstd.halgventmod.squad;

import java.util.List;

import com.nrdhvnstd.halgventmod.network.SquadMembersPayload;

/** На клиенте: последние присланные сервером позиции товарищей по отряду (рисует карта). */
public final class SquadMarkers {
    public static List<SquadMembersPayload.Member> clientMembers = List.of();

    private SquadMarkers() {}
}
