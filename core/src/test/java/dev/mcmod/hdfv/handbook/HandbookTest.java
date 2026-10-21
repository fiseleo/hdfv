package dev.mcmod.hdfv.handbook;

import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.handbook.HandbookText.Entry;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The handbook (0.24): the page format reads as documented, and the pages are whole: every language has the same
 * entries with the same structure (headers, blocks, the keys and items in the text), and everything a page names
 * exists (items, recipes, advancements, pictures of 320 by 180, sample designs, entries, key mappings).
 */
class HandbookTest {
    private static final Path ROOT = Path.of(System.getProperty("hdfv.root", "..")).toAbsolutePath().normalize();
    private static final Path RES = ROOT.resolve("neoforge/src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/hdfv"), DATA = RES.resolve("data/hdfv");
    static final List<String> LANGUAGES = List.of("en_us", "zh_tw", "zh_cn");

    @Test void aPageReadsAsDocumented() {
        Entry e = HandbookText.parse("""
                ---
                id: sample
                section: branch
                order: 3
                title: A sample
                icon: hdfv:lathe
                advancement: hdfv:tutorial/lathe
                ---

                ## A **bold** heading
                First line of a paragraph
                and its second line, with {key key.hdfv.shift_up} and {item hdfv:lathe}.
                - a bullet
                - another
                {items hdfv:lathe minecraft:iron_ingot}
                {image lathe}
                {recipe hdfv:alloying/coke}
                {design inline_four}
                {see start}
                第一行中文
                接著第二行
                """);
        assertEquals("sample", e.id());
        assertEquals(HandbookText.Section.BRANCH, e.section());
        assertEquals(3, e.order());
        assertEquals("hdfv:tutorial/lathe", e.advancement());
        assertEquals(10, e.blocks().size());
        var heading = (HandbookText.Heading) e.blocks().get(0);
        assertEquals(new HandbookText.Text("bold", true), heading.spans().get(1));
        var paragraph = (HandbookText.Paragraph) e.blocks().get(1);
        assertFalse(paragraph.bullet());
        assertEquals(new HandbookText.Text("First line of a paragraph and its second line, with ", false), paragraph.spans().get(0));
        assertEquals(new HandbookText.Key("key.hdfv.shift_up"), paragraph.spans().get(1));
        assertEquals(new HandbookText.ItemName("hdfv:lathe"), paragraph.spans().get(3));
        assertTrue(((HandbookText.Paragraph) e.blocks().get(2)).bullet());
        assertEquals(List.of("hdfv:lathe", "minecraft:iron_ingot"), ((HandbookText.Items) e.blocks().get(4)).ids());
        assertEquals(new HandbookText.Recipe("hdfv:alloying/coke"), e.blocks().get(6));
        // Chinese lines join without a space
        assertEquals(new HandbookText.Text("第一行中文接著第二行", false), ((HandbookText.Paragraph) e.blocks().get(9)).spans().getFirst());
    }

    @Test void mistakesAreRefusedWithTheirLine() {
        String header = "---\nid: x\nsection: main\norder: 1\ntitle: X\nicon: hdfv:lathe\n---\n";
        assertThrows(IllegalArgumentException.class, () -> HandbookText.parse("id: x\n"));
        assertThrows(IllegalArgumentException.class, () -> HandbookText.parse("---\nid: x\nsection: main\norder: 1\ntitle: X\n---\n"));
        assertThrows(IllegalArgumentException.class, () -> HandbookText.parse(header.replace("main", "side")));
        var unknown = assertThrows(IllegalArgumentException.class, () -> HandbookText.parse(header + "\n{picture x}\n"));
        assertTrue(unknown.getMessage().startsWith("line 9"), unknown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> HandbookText.parse(header + "\n**not closed\n"));
        assertThrows(IllegalArgumentException.class, () -> HandbookText.parse(header + "\n{items lathe}\n"));
    }

    static Map<String, Entry> entries(String language) throws IOException {
        Path folder = ASSETS.resolve("handbook/" + language);
        Map<String, Entry> out = new TreeMap<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                Entry e;
                try { e = HandbookText.parse(Files.readString(f, StandardCharsets.UTF_8)); }
                catch (IllegalArgumentException ex) { throw new AssertionError(language + "/" + f.getFileName() + ": " + ex.getMessage(), ex); }
                assertEquals(f.getFileName().toString(), e.id() + ".md", "a file is named after its entry");
                assertNull(out.put(e.id(), e));
            }
        }
        return out;
    }

    @Test void everyLanguageHasTheSameEntriesBuiltAlike() throws IOException {
        Map<String, Entry> english = entries("en_us");
        assertTrue(english.size() >= 20, "the handbook has its entries");
        for (String language : LANGUAGES.subList(1, LANGUAGES.size())) {
            Map<String, Entry> other = entries(language);
            assertEquals(english.keySet(), other.keySet(), language + " has the same entries");
            for (String id : english.keySet()) {
                assertEquals(HandbookText.structure(english.get(id)), HandbookText.structure(other.get(id)), language + "/" + id + " is built like English");
                assertNotEquals("", other.get(id).title().strip(), language + "/" + id + " has a title");
            }
        }
        Set<String> orders = english.values().stream().map(e -> e.section() + " " + e.order()).collect(Collectors.toSet());
        assertEquals(english.size(), orders.size(), "no two entries share a place");
    }

    @Test void everythingAPageNamesExists() throws IOException {
        Map<String, Entry> english = entries("en_us");
        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"), StandardCharsets.UTF_8);
        Set<String> samples = Designs.SAMPLES.stream().map(Designs.Sample::id).collect(Collectors.toSet());
        for (Entry e : english.values()) {
            String where = e.id() + ": ";
            item(e.icon(), where);
            if (!e.advancement().isEmpty()) { assertTrue(Files.exists(resource(DATA, "advancement", e.advancement(), ".json")), where + e.advancement()); }
            for (HandbookText.Block b : e.blocks()) {
                switch (b) {
                    case HandbookText.Items i -> i.ids().forEach(id -> item(id, where));
                    case HandbookText.Recipe r -> assertTrue(r.id().startsWith("minecraft:") || Files.exists(resource(DATA, "recipe", r.id(), ".json")), where + r.id());
                    case HandbookText.Image i -> picture(ASSETS.resolve("textures/gui/handbook/" + i.name() + ".png"), where);
                    case HandbookText.Design d -> assertTrue(samples.contains(d.sample()), where + "sample " + d.sample());
                    case HandbookText.See s -> assertTrue(english.containsKey(s.entry()), where + "entry " + s.entry());
                    default -> {}
                }
                List<HandbookText.Span> spans = b instanceof HandbookText.Heading h ? h.spans() : b instanceof HandbookText.Paragraph p ? p.spans() : List.of();
                for (HandbookText.Span s : spans) {
                    if (s instanceof HandbookText.ItemName i) { item(i.id(), where); }
                    if (s instanceof HandbookText.Key k) {
                        assertTrue(k.mapping().startsWith("key."), where + k.mapping());
                        if (k.mapping().startsWith("key.hdfv.")) { assertTrue(lang.contains("\"" + k.mapping() + "\""), where + k.mapping()); }
                    }
                }
            }
        }
    }

    private static Path resource(Path data, String kind, String id, String suffix) {
        assertTrue(id.startsWith("hdfv:"), id);
        return data.resolve(kind + "/" + id.substring(5) + suffix);
    }

    /** An item: hdfv's have an item model; vanilla ones are taken on trust (the GameTest checks the registry). */
    private static void item(String id, String where) {
        if (id.startsWith("minecraft:")) { return; }
        assertTrue(id.startsWith("hdfv:") && Files.exists(ASSETS.resolve("models/item/" + id.substring(5) + ".json")), where + "item " + id);
    }

    /** A picture exists and is 320 by 180 (the screen draws them at 16:9 from that size). */
    private static void picture(Path png, String where) {
        assertTrue(Files.exists(png), where + png.getFileName());
        try (InputStream in = Files.newInputStream(png); DataInputStream d = new DataInputStream(in)) {
            d.skipNBytes(16);
            assertEquals(320, d.readInt(), where + png.getFileName() + " width");
            assertEquals(180, d.readInt(), where + png.getFileName() + " height");
        } catch (IOException ex) { throw new AssertionError(ex); }
    }
}
