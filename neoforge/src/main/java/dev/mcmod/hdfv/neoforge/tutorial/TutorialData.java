package dev.mcmod.hdfv.neoforge.tutorial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * A player's tutorial state (0.24, saved with the player and kept on death): whether the handbook was given, and the
 * metres driven in assembled vehicles by kind (car, aircraft, boat) for the driven milestones.
 */
public record TutorialData(boolean handbookGiven, Map<String, Double> metres) {
    public static final TutorialData EMPTY = new TutorialData(false, Map.of());
    public static final Codec<TutorialData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("handbook_given", false).forGetter(TutorialData::handbookGiven),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("metres", Map.of()).forGetter(TutorialData::metres)).apply(i, TutorialData::new));

    public TutorialData withHandbook() { return new TutorialData(true, metres); }

    public TutorialData drove(String kind, double m) {
        Map<String, Double> next = new HashMap<>(metres);
        next.merge(kind, m, Double::sum);
        return new TutorialData(handbookGiven, Map.copyOf(next));
    }
}
