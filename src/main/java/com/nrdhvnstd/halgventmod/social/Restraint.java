package com.nrdhvnstd.halgventmod.social;

import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModEffects;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

/**
 * Мешок, кандалы, наручники. Сами держатся эффектом (сохраняется с игроком, переживает выход из игры),
 * а вложение ModAttachments.RESTRAINTS — их маска для клиентов: ванильные эффекты чужим игрокам не рассылаются,
 * а меню атакующего и значки над головой должны знать, что на жертве.
 */
public enum Restraint {
    BAG,
    SHACKLES,
    CUFFS;

    /** Маска рассылается всем, кто видит игрока. */
    public static final AttachmentSyncHandler<Integer> SYNC = new AttachmentSyncHandler<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, Integer mask, boolean initialSync) {
            buf.writeByte(mask);
        }

        @Override
        public Integer read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, Integer previousValue) {
            return (int) buf.readByte();
        }
    };

    public Holder<MobEffect> effect() {
        return switch (this) {
            case BAG -> ModEffects.BAGGED;
            case SHACKLES -> ModEffects.SHACKLED;
            case CUFFS -> ModEffects.CUFFED;
        };
    }

    private int bit() {
        return 1 << ordinal();
    }

    /** Есть ли на игроке (по маске — одинаково на сервере и клиенте). */
    public boolean on(Player player) {
        return (player.getData(ModAttachments.RESTRAINTS) & bit()) != 0;
    }

    /** Маска по эффектам на игроке. */
    static int maskOf(Player player) {
        int mask = 0;
        for (Restraint restraint : values()) {
            if (player.hasEffect(restraint.effect())) {
                mask |= restraint.bit();
            }
        }
        return mask;
    }

    /** Сверить маску с эффектами и разослать, если изменилась (эффект сняли командой, игрок умер, вошёл в игру). */
    static void refresh(ServerPlayer player) {
        int mask = maskOf(player);
        if (player.getData(ModAttachments.RESTRAINTS) != mask) {
            player.setData(ModAttachments.RESTRAINTS, mask);
            player.syncData(ModAttachments.RESTRAINTS);
        }
    }
}
