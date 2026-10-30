package com.nrdhvnstd.halgventmod.client.particle;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModParticles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Искра от удара по металлу: светится сама (не темнеет в тени), летит, падает и отскакивает от земли, остывает —
 * из бело-жёлтой в оранжевую и тёмно-красную, к концу тает. Размер и время жизни чуть разные у каждой.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public class SparkParticle extends TextureSheetParticle {
    // Меняйте здесь ⚠️
    private static final float GRAVITY = 0.9F;
    private static final float DRAG = 0.9F;
    private static final int LIFE_MIN = 6, LIFE_SPREAD = 10;
    private static final float SIZE_MIN = 0.025F, SIZE_SPREAD = 0.03F;
    private static final double BOUNCE = 0.35;

    protected SparkParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
        super(level, x, y, z, dx, dy, dz);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = GRAVITY;
        this.friction = DRAG;
        this.lifetime = LIFE_MIN + random.nextInt(LIFE_SPREAD);
        this.quadSize = SIZE_MIN + random.nextFloat() * SIZE_SPREAD;
        this.hasPhysics = true;
        cool(0);
    }

    // Остывание: 0 — только что выбита, 1 — погасла
    private void cool(float t) {
        rCol = 1.0F;
        gCol = Mth.lerp(t, 0.95F, 0.25F);
        bCol = Mth.lerp(Math.min(1, t * 2), 0.7F, 0.05F);
        alpha = t < 0.7F ? 1.0F : 1.0F - (t - 0.7F) / 0.3F;
    }

    @Override
    public void tick() {
        boolean wasOnGround = onGround;
        super.tick();
        if (onGround && !wasOnGround) {
            yd = -yd * BOUNCE; // отскочила от камня
            xd *= 0.6;
            zd *= 0.6;
        }
        cool((float) age / lifetime);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0; // светится сама
    }

    @Override
    public float getQuadSize(float partialTick) {
        return quadSize * (1.0F - 0.5F * ((age + partialTick) / lifetime));
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            SparkParticle particle = new SparkParticle(level, x, y, z, dx, dy, dz);
            particle.pickSprite(sprites);
            return particle;
        }
    }

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.COMBAT_SPARK.get(), Provider::new);
    }
}
