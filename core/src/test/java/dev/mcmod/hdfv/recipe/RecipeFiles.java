package dev.mcmod.hdfv.recipe;

import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.hdfv.manufacture.Alloy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** The mod's recipe files as the tests read them (0.23): each recipe's ingredients as sets of item ids, its outputs, the tech tree's ways. */
final class RecipeFiles {
    private RecipeFiles() {}

    static final Path ROOT = Path.of(System.getProperty("hdfv.root", "..")).toAbsolutePath().normalize();
    static final Path DATA = ROOT.resolve("neoforge/src/main/resources/data");
    static final Path RECIPES = DATA.resolve("hdfv/recipe");

    /** A recipe file: its path under hdfv: (as "alloying/coke"), type and json. */
    record Recipe(String path, String type, Map<String, Object> json) {}

    @SuppressWarnings("unchecked")
    static List<Recipe> all() {
        List<Recipe> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(RECIPES)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                String rel = RECIPES.relativize(p).toString().replace('\\', '/');
                Map<String, Object> json = (Map<String, Object>) Json.parse(Files.readString(p, StandardCharsets.UTF_8));
                out.add(new Recipe(rel.substring(0, rel.length() - 5), (String) json.get("type"), json));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static final Map<String, Set<String>> TAGS = new LinkedHashMap<>();

    /** The items a tag the mod writes holds (recursively); empty for a tag it does not write (vanilla's, NeoForge's). */
    @SuppressWarnings("unchecked")
    static Set<String> tag(String name) {
        if (TAGS.containsKey(name)) { return TAGS.get(name); }
        TAGS.put(name, Set.of());
        int colon = name.indexOf(':');
        Path f = DATA.resolve(name.substring(0, colon)).resolve("tags/item").resolve(name.substring(colon + 1) + ".json");
        Set<String> out = new LinkedHashSet<>();
        if (Files.exists(f)) {
            try {
                Map<String, Object> json = (Map<String, Object>) Json.parse(Files.readString(f, StandardCharsets.UTF_8));
                for (Object v : (List<Object>) json.get("values")) {
                    String s = (String) v;
                    if (s.startsWith("#")) { out.addAll(tag(s.substring(1))); } else { out.add(s); }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        TAGS.put(name, out);
        return out;
    }

    /** An ingredient's items: an item, a tag's items (a tag the mod does not write: "#name", a thing of the world), a stock of an alloy, alternatives. */
    @SuppressWarnings("unchecked")
    static Set<String> items(Object ingredient) {
        Set<String> out = new LinkedHashSet<>();
        if (ingredient instanceof List<?> list) {
            for (Object o : list) { out.addAll(items(o)); }
            return out;
        }
        Map<String, Object> m = (Map<String, Object>) ingredient;
        if (m.containsKey("item")) { out.add((String) m.get("item")); }
        else if (m.containsKey("tag")) {
            // a tag the mod writes is its items (other mods' would count too in the game); another is a thing of the world
            Set<String> t = tag((String) m.get("tag"));
            if (t.isEmpty()) { out.add("#" + m.get("tag")); } else { out.addAll(t); }
        } else if ("neoforge:components".equals(m.get("type"))) {
            out.add(withAlloy((String) m.get("items"), (Map<String, Object>) m.get("components")));
        } else {
            throw new IllegalArgumentException("An ingredient of an unknown form: " + m);
        }
        return out;
    }

    /** A result's id (a stock's with its alloy). */
    @SuppressWarnings("unchecked")
    static String result(Map<String, Object> r) {
        return r.containsKey("components") ? withAlloy((String) r.get("id"), (Map<String, Object>) r.get("components")) : (String) r.get("id");
    }

    private static String withAlloy(String item, Map<String, Object> components) {
        String alloy = (String) components.get("hdfv:alloy");
        Alloy a = java.util.Arrays.stream(Alloy.values()).filter(x -> x.id().equals(alloy)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No alloy " + alloy));
        return item.equals("hdfv:stock_bit") ? TechTree.bit(a) : TechTree.stock(a);
    }

    /** A recipe's ingredients, each with its count (shaped: a key's cells; shapeless: each; sized: its count). */
    @SuppressWarnings("unchecked")
    static List<Map.Entry<Set<String>, Integer>> ingredients(Recipe r) {
        List<Map.Entry<Set<String>, Integer>> out = new ArrayList<>();
        Map<String, Object> j = r.json();
        switch (r.type()) {
            case "minecraft:crafting_shaped" -> {
                Map<String, Object> key = (Map<String, Object>) j.get("key");
                Map<String, Integer> n = new LinkedHashMap<>();
                for (Object row : (List<Object>) j.get("pattern")) {
                    for (char c : ((String) row).toCharArray()) { if (c != ' ') { n.merge(String.valueOf(c), 1, Integer::sum); } }
                }
                for (var e : n.entrySet()) { out.add(Map.entry(items(key.get(e.getKey())), e.getValue())); }
            }
            case "minecraft:crafting_shapeless" -> { for (Object i : (List<Object>) j.get("ingredients")) { out.add(Map.entry(items(i), 1)); } }
            case "hdfv:alloying", "hdfv:assembling", "hdfv:electric_furnace", "hdfv:electrolysis" -> {
                for (Object i : (List<Object>) j.get("ingredients")) {
                    Map<String, Object> s = (Map<String, Object>) i;
                    out.add(Map.entry(items(s.get("ingredient")), ((Number) s.getOrDefault("count", 1.0)).intValue()));
                }
            }
            case "minecraft:smelting", "minecraft:blasting" -> out.add(Map.entry(items(j.get("ingredient")), 1));
            default -> { }
        }
        return out;
    }

    /** A recipe's outputs (the result and any by-products). */
    @SuppressWarnings("unchecked")
    static List<String> outputs(Recipe r) {
        Map<String, Object> j = r.json();
        List<String> out = new ArrayList<>();
        if (j.get("result") instanceof Map<?, ?> res) { out.add(result((Map<String, Object>) res)); }
        if (j.get("byproduct") instanceof Map<?, ?> by) { out.add(result((Map<String, Object>) by)); }
        if (j.get("byproducts") instanceof List<?> bys) { for (Object b : bys) { out.add(result((Map<String, Object>) b)); } }
        return out;
    }

    /** The tech tree's ways of the recipe files (an optional mod's, behind conditions, left out), the fixed ones and the components'. */
    static List<TechTree.Way> ways() {
        List<TechTree.Way> out = new ArrayList<>();
        for (Recipe r : all()) {
            if (r.json().containsKey("neoforge:conditions")) { continue; }
            List<Set<String>> needs = new ArrayList<>();
            for (var e : ingredients(r)) { needs.add(e.getKey()); }
            String where = switch (r.type()) {
                case "hdfv:assembling" -> { needs.add(Set.of("hdfv:assembly_bench")); yield TechTree.ASSEMBLING; }
                case "hdfv:alloying" -> { needs.add(Set.of("hdfv:alloy_furnace")); yield TechTree.ALLOYING; }
                case "hdfv:electric_furnace" -> { needs.add(Set.of("hdfv:arc_furnace")); needs.add(Set.of(TechTree.POWER)); yield TechTree.ARC; }
                case "hdfv:electrolysis" -> { needs.add(Set.of("hdfv:electrolysis_cell")); needs.add(Set.of(TechTree.POWER)); yield TechTree.CELL; }
                case "minecraft:smelting", "minecraft:blasting" -> TechTree.SMELTING;
                default -> TechTree.CRAFTING;
            };
            for (String o : outputs(r)) { out.add(new TechTree.Way(o, where, needs, 0)); }
        }
        out.addAll(TechTree.fixed());
        out.addAll(TechTree.components());
        return out;
    }

    /** The world's things: every vanilla item the recipes name, the ores and what mining them gives. */
    static Set<String> world() {
        Set<String> out = new HashSet<>();
        for (Recipe r : all()) {
            for (var e : ingredients(r)) { for (String i : e.getKey()) { if (i.startsWith("minecraft:") || i.startsWith("#")) { out.add(i); } } }
        }
        out.addAll(TechTree.MINED);
        return out;
    }
}
