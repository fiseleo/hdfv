package dev.mcmod.hdfv.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * hdfv's particles (0.11), all from engines' exhausts and spawned by clients only (client.ExhaustSmoke): smoke (a
 * rocket's white cloud, a jet's grey trail, a petrol engine's cough), soot (a diesel's black smoke under load) and
 * sparks (burning aluminium out of a rocket). Each carries its starting size (m), colour (ARGB) and life (s), since
 * one engine's smoke is a few centimetres and another's a metre.
 * <p>
 * 0.12, the wheels' and the ground's (client.WheelEffects): tyre smoke, dust (tinted the ground's colour), mud, powder
 * snow and road spray.
 */
public final class HdfvParticles {
    private HdfvParticles() {}

    /** A particle's starting size (m), colour (ARGB: alpha its density) and life (s). */
    public record Options(ParticleType<Options> type, float size, int colour, float lifeS) implements ParticleOptions {
        @Override public ParticleType<Options> getType() { return type; }
    }

    public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, Hdfv.MOD_ID);
    public static final DeferredHolder<ParticleType<?>, ParticleType<Options>> SMOKE = TYPES.register("exhaust_smoke", HdfvParticles::type);
    public static final DeferredHolder<ParticleType<?>, ParticleType<Options>> SOOT = TYPES.register("soot", HdfvParticles::type);
    public static final DeferredHolder<ParticleType<?>, ParticleType<Options>> SPARK = TYPES.register("spark", HdfvParticles::type);
    public static final DeferredHolder<ParticleType<?>, ParticleType<Options>> TYRE_SMOKE = TYPES.register("tyre_smoke", HdfvParticles::type);
    public static final DeferredHolder<ParticleType<?>, ParticleType<Options>> DUST = TYPES.register("dust", HdfvParticles::type);
    public static final DeferredHolder<ParticleType<?>, ParticleType<Options>> MUD = TYPES.register("mud", HdfvParticles::type);
    public static final DeferredHolder<ParticleType<?>, ParticleType<Options>> SNOW_PUFF = TYPES.register("snow_puff", HdfvParticles::type);
    public static final DeferredHolder<ParticleType<?>, ParticleType<Options>> ROAD_SPRAY = TYPES.register("road_spray", HdfvParticles::type);

    private static ParticleType<Options> type() {
        return new ParticleType<>(false) {
            private final MapCodec<Options> codec = RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.FLOAT.fieldOf("size").forGetter(Options::size),
                    Codec.INT.fieldOf("colour").forGetter(Options::colour),
                    Codec.FLOAT.fieldOf("life").forGetter(Options::lifeS)).apply(i, (size, colour, life) -> new Options(this, size, colour, life)));
            private final StreamCodec<RegistryFriendlyByteBuf, Options> stream = StreamCodec.composite(
                    ByteBufCodecs.FLOAT, Options::size, ByteBufCodecs.INT, Options::colour, ByteBufCodecs.FLOAT, Options::lifeS,
                    (size, colour, life) -> new Options(this, size, colour, life));

            @Override public MapCodec<Options> codec() { return codec; }
            @Override public StreamCodec<? super RegistryFriendlyByteBuf, Options> streamCodec() { return stream; }
        };
    }

    public static void register(IEventBus modBus) { TYPES.register(modBus); }
}
