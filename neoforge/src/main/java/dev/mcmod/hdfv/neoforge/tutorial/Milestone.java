package dev.mcmod.hdfv.neoforge.tutorial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * The tutorial's advancement criterion (0.24), hdfv:milestone: something a player did that the vanilla criteria cannot
 * see (saved a design, formed a machine, rolled a vehicle out, drove it so far). The conditions are the event, and
 * optionally its kind and a least value:
 * <pre>
 * {"trigger": "hdfv:milestone", "conditions": {"event": "driven", "kind": "car", "at_least": 1000}}
 * </pre>
 * The events and where they happen are in Milestones.
 */
public final class Milestone extends SimpleCriterionTrigger<Milestone.Instance> {
    @Override public Codec<Instance> codec() { return Instance.CODEC; }

    void trigger(ServerPlayer player, String event, String kind, double value) {
        trigger(player, instance -> instance.matches(event, kind, value));
    }

    public record Instance(Optional<ContextAwarePredicate> player, String event, Optional<String> kind, Optional<Double> atLeast)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.fieldOf("event").forGetter(Instance::event),
                Codec.STRING.optionalFieldOf("kind").forGetter(Instance::kind),
                Codec.DOUBLE.optionalFieldOf("at_least").forGetter(Instance::atLeast)).apply(i, Instance::new));

        boolean matches(String event, String kind, double value) {
            return this.event.equals(event) && this.kind.map(k -> k.equals(kind)).orElse(true) && atLeast.map(m -> value >= m).orElse(true);
        }
    }
}
