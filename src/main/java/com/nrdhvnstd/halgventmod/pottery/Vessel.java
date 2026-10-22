package com.nrdhvnstd.halgventmod.pottery;

import java.util.Arrays;
import java.util.stream.IntStream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Форма сырца или посудины: какая форма и как её вылепили — разрез тела и воксели деталей ({@link VesselGeometry}).
 * У каждой посудины своя: так её и видно в мире.
 */
public record Vessel(String form, int[] profile, int[] details) {
    public static final Codec<Vessel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("form").forGetter(Vessel::form),
            Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream).fieldOf("profile").forGetter(Vessel::profile),
            Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream).optionalFieldOf("details", new int[0])
                    .forGetter(Vessel::details)
    ).apply(instance, Vessel::new));

    private static final StreamCodec<ByteBuf, int[]> INTS = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list())
            .map(list -> list.stream().mapToInt(Integer::intValue).toArray(),
                    array -> Arrays.stream(array).boxed().toList());

    public static final StreamCodec<ByteBuf, Vessel> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Vessel::form,
            INTS, Vessel::profile,
            INTS, Vessel::details,
            Vessel::new);

    public Vessel {
        profile = profile.length == VesselGeometry.LAYERS ? profile.clone() : Arrays.copyOf(profile, VesselGeometry.LAYERS);
        details = VesselGeometry.normalize(details);
    }

    public VesselForm vesselForm() {
        return VesselForm.byId(form);
    }

    /** Образец формы — для вещей без своей формы (из творческого, /give). */
    public static Vessel template(VesselForm form) {
        VesselGeometry.Template t = VesselGeometry.template(form);
        return new Vessel(form.id(), t.profile(), t.details());
    }

    // Массивы сравниваются по содержимому: иначе одинаковые вещи не складывались бы и не узнавались
    @Override
    public boolean equals(Object other) {
        return other instanceof Vessel v && form.equals(v.form) && Arrays.equals(profile, v.profile)
                && Arrays.equals(details, v.details);
    }

    @Override
    public int hashCode() {
        return (form.hashCode() * 31 + Arrays.hashCode(profile)) * 31 + Arrays.hashCode(details);
    }

    @Override
    public String toString() {
        return "Vessel[" + form + "]";
    }
}
