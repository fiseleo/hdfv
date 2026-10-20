package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An exhaust particle (0.11, HdfvParticles): smoke spreads and thins as it drifts up, soot spreads less and lingers
 * darker, a spark falls bright and shrinks. Sizes, colours and lives come from the engine that let them out
 * (ExhaustSmoke); the growth, buoyancy and drag here are display rules. How many are alive is counted so that the
 * engines stop adding at a budget.
 * <p>
 * 0.12, the ground's (WheelEffects): tyre smoke billows wide and hangs; dust spreads and settles; mud is thrown and
 * falls, bouncing off blocks; powder snow spreads and drifts down; road spray spreads fast and is gone in a second.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ExhaustParticle extends TextureSheetParticle {
    enum Kind { SMOKE, SOOT, SPARK, TYRE_SMOKE, DUST, MUD, SNOW, SPRAY }

    /** hdfv's particles alive now, and how many may be (the engines stop adding at it). */
    static final AtomicInteger LIVE = new AtomicInteger();
    static final int BUDGET = 1500;

    private final SpriteSet sprites;
    private final Kind kind;
    private final float startSize, startAlpha;

    ExhaustParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, HdfvParticles.Options o, SpriteSet sprites, Kind kind) {
        super(level, x, y, z);
        this.xd = xd; this.yd = yd; this.zd = zd;
        this.sprites = sprites;
        this.kind = kind;
        lifetime = Math.max(3, Math.round(o.lifeS() * 20 * (0.8f + 0.4f * random.nextFloat())));
        startSize = Math.max(0.01f, o.size());
        quadSize = startSize / 2;
        int c = o.colour();
        rCol = ((c >> 16) & 255) / 255f; gCol = ((c >> 8) & 255) / 255f; bCol = (c & 255) / 255f;
        startAlpha = ((c >>> 24) & 255) / 255f;
        alpha = startAlpha;
        // smoke is hot and rises a little; soot less; sparks fall (Particle.tick takes 0.04 x gravity off yd each tick)
        gravity = switch (kind) {
            case SMOKE -> -0.03f; case SOOT -> -0.01f; case SPARK -> 0.6f; case TYRE_SMOKE -> -0.008f; case DUST -> 0.004f; case MUD -> 1.0f;
            case SNOW -> 0.06f; case SPRAY -> 0.02f;
        };
        friction = switch (kind) { case SPARK, MUD -> 0.97f; case SPRAY -> 0.85f; case SNOW -> 0.88f; default -> 0.9f; };
        hasPhysics = kind == Kind.SPARK || kind == Kind.MUD;
        roll = oRoll = random.nextFloat() * (float) (2 * Math.PI);
        // a clod is one of the lumps, kept; the clouds go through their frames as they age
        if (kind == Kind.MUD) { pickSprite(sprites); } else { setSpriteFromAge(sprites); }
        LIVE.incrementAndGet();
    }

    @Override public void tick() {
        super.tick();
        if (removed) { return; }
        if (kind != Kind.MUD) { setSpriteFromAge(sprites); }
        float t = Math.min(1, age / (float) lifetime);
        switch (kind) {
            case SMOKE -> { quadSize = startSize / 2 * (1 + 3.5f * (float) Math.sqrt(t)); alpha = startAlpha * (float) Math.pow(1 - t, 1.4); }
            case SOOT -> { quadSize = startSize / 2 * (1 + 2.0f * (float) Math.sqrt(t)); alpha = startAlpha * (1 - t * t); }
            case SPARK -> { quadSize = startSize / 2 * (1 - 0.7f * t); alpha = startAlpha; }
            case TYRE_SMOKE -> { quadSize = startSize / 2 * (1 + 4.5f * (float) Math.sqrt(t)); alpha = startAlpha * (float) Math.pow(1 - t, 1.2); }
            case DUST -> { quadSize = startSize / 2 * (1 + 2.5f * (float) Math.sqrt(t)); alpha = startAlpha * (1 - t); }
            case MUD -> { quadSize = startSize / 2; alpha = startAlpha * (t < 0.8f ? 1 : (1 - t) * 5); }
            case SNOW -> { quadSize = startSize / 2 * (1 + 2.0f * (float) Math.sqrt(t)); alpha = startAlpha * (1 - t); }
            case SPRAY -> { quadSize = startSize / 2 * (1 + 3.0f * t); alpha = startAlpha * (1 - t) * (1 - t); }
        }
        oRoll = roll;
        if (kind != Kind.SPARK && kind != Kind.MUD) { roll += 0.02f; }
    }

    @Override public void remove() {
        if (!removed) { LIVE.decrementAndGet(); }
        super.remove();
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    /** A spark glows; smoke is lit by the world. */
    @Override protected int getLightColor(float partialTick) { return kind == Kind.SPARK ? 0xF000F0 : super.getLightColor(partialTick); }

    @SubscribeEvent public static void providers(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(HdfvParticles.SMOKE.get(), sprites -> (o, level, x, y, z, xd, yd, zd) -> new ExhaustParticle(level, x, y, z, xd, yd, zd, o, sprites, Kind.SMOKE));
        event.registerSpriteSet(HdfvParticles.SOOT.get(), sprites -> (o, level, x, y, z, xd, yd, zd) -> new ExhaustParticle(level, x, y, z, xd, yd, zd, o, sprites, Kind.SOOT));
        event.registerSpriteSet(HdfvParticles.SPARK.get(), sprites -> (o, level, x, y, z, xd, yd, zd) -> new ExhaustParticle(level, x, y, z, xd, yd, zd, o, sprites, Kind.SPARK));
        event.registerSpriteSet(HdfvParticles.TYRE_SMOKE.get(), sprites -> (o, level, x, y, z, xd, yd, zd) -> new ExhaustParticle(level, x, y, z, xd, yd, zd, o, sprites, Kind.TYRE_SMOKE));
        event.registerSpriteSet(HdfvParticles.DUST.get(), sprites -> (o, level, x, y, z, xd, yd, zd) -> new ExhaustParticle(level, x, y, z, xd, yd, zd, o, sprites, Kind.DUST));
        event.registerSpriteSet(HdfvParticles.MUD.get(), sprites -> (o, level, x, y, z, xd, yd, zd) -> new ExhaustParticle(level, x, y, z, xd, yd, zd, o, sprites, Kind.MUD));
        event.registerSpriteSet(HdfvParticles.SNOW_PUFF.get(), sprites -> (o, level, x, y, z, xd, yd, zd) -> new ExhaustParticle(level, x, y, z, xd, yd, zd, o, sprites, Kind.SNOW));
        event.registerSpriteSet(HdfvParticles.ROAD_SPRAY.get(), sprites -> (o, level, x, y, z, xd, yd, zd) -> new ExhaustParticle(level, x, y, z, xd, yd, zd, o, sprites, Kind.SPRAY));
    }
}
