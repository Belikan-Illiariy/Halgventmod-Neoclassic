package com.nrdhvnstd.halgventmod.network;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.WeaponProfiles;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: профили оружия исходным JSON (классы по одному, файл предметов целиком) — при входе и по /reload. */
public record WeaponProfilesSyncPayload(List<String> classes, String items) implements CustomPacketPayload {
    private static final int MAX_JSON = 1 << 18;

    public static final Type<WeaponProfilesSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "weapon_profiles"));

    public static final StreamCodec<ByteBuf, WeaponProfilesSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_JSON).apply(ByteBufCodecs.list()), WeaponProfilesSyncPayload::classes,
            ByteBufCodecs.stringUtf8(MAX_JSON), WeaponProfilesSyncPayload::items,
            WeaponProfilesSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WeaponProfilesSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> WeaponProfiles.applyClient(payload));
    }
}
