package dev.mcmod.hdfv.handbook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The handbook's page files (0.24, the new-player tutorial): one entry per file, assets/hdfv/handbook/&lt;language&gt;/&lt;id&gt;.md,
 * the same entries in every language. A file is a header between two "---" lines (id, section, order, title, icon,
 * advancement) and a body of blocks separated by blank lines:
 * <pre>
 * ## heading
 * - a bullet (one per line)
 * a paragraph (its lines are joined: with a space between words, without one between Chinese characters)
 * {items hdfv:lathe hdfv:drill_press}   a row of item icons
 * {image lathe}                         assets/hdfv/textures/gui/handbook/lathe.png (16:9)
 * {recipe hdfv:lathe}                   the recipe making it
 * {design &lt;sample name&gt;}                a sample design in 3D (Designs.SAMPLES)
 * {see &lt;entry id&gt;}                      a link to another entry
 * </pre>
 * Inside a paragraph, heading or bullet: **bold**, {key key.hdfv.shift_up} (the key it is bound to now) and
 * {item hdfv:lathe} (the item's name). Pure Java: the game draws it (HandbookScreen), the tests check every language.
 */
public final class HandbookText {
    private HandbookText() {}

    public enum Section { MAIN, BRANCH }

    public record Entry(String id, Section section, int order, String title, String icon, String advancement, List<Block> blocks) {}

    public sealed interface Block permits Heading, Paragraph, Items, Image, Recipe, Design, See {}
    public record Heading(List<Span> spans) implements Block {}
    public record Paragraph(List<Span> spans, boolean bullet) implements Block {}
    public record Items(List<String> ids) implements Block {}
    public record Image(String name) implements Block {}
    public record Recipe(String id) implements Block {}
    public record Design(String sample) implements Block {}
    public record See(String entry) implements Block {}

    public sealed interface Span permits Text, Key, ItemName {}
    public record Text(String text, boolean bold) implements Span {}
    /** A key mapping by its name (key.hdfv.shift_up), shown as the key bound to it. */
    public record Key(String mapping) implements Span {}
    /** An item by its id, shown as its name. */
    public record ItemName(String id) implements Span {}

    private static final Pattern DIRECTIVE = Pattern.compile("\\{(items|image|recipe|design|see)\\s+([^{}]+)}");
    private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|\\{(key|item)\\s+([^{}\\s]+)}");
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]+");

    /** Reads one page file; throws IllegalArgumentException naming the line for anything it does not understand. */
    public static Entry parse(String text) {
        List<String> lines = text.replace("﻿", "").replace("\r\n", "\n").replace('\r', '\n').lines().toList();
        if (lines.isEmpty() || !lines.getFirst().strip().equals("---")) { throw new IllegalArgumentException("line 1: the header must start with ---"); }
        Map<String, String> header = new LinkedHashMap<>();
        int n = 1;
        for (; n < lines.size() && !lines.get(n).strip().equals("---"); n++) {
            String line = lines.get(n);
            int colon = line.indexOf(':');
            if (colon < 1) { throw new IllegalArgumentException("line " + (n + 1) + ": a header line is name: value"); }
            header.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
        }
        if (n >= lines.size()) { throw new IllegalArgumentException("the header has no closing ---"); }
        String id = required(header, "id");
        if (!NAME.matcher(id).matches()) { throw new IllegalArgumentException("id: lower case, digits and _ only: " + id); }
        Section section = switch (required(header, "section")) {
            case "main" -> Section.MAIN;
            case "branch" -> Section.BRANCH;
            default -> throw new IllegalArgumentException("section: main or branch");
        };
        int order;
        try { order = Integer.parseInt(required(header, "order")); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("order: a whole number"); }
        String icon = required(header, "icon");
        if (!ID.matcher(icon).matches()) { throw new IllegalArgumentException("icon: an item id: " + icon); }
        String advancement = header.getOrDefault("advancement", "");
        if (!advancement.isEmpty() && !ID.matcher(advancement).matches()) { throw new IllegalArgumentException("advancement: an id: " + advancement); }
        for (String k : header.keySet()) {
            if (!List.of("id", "section", "order", "title", "icon", "advancement").contains(k)) { throw new IllegalArgumentException("unknown header: " + k); }
        }

        List<Block> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        int paragraphLine = 0;
        for (int i = n + 1; i <= lines.size(); i++) {
            String line = i < lines.size() ? lines.get(i).strip() : "";
            boolean special = line.startsWith("## ") || line.startsWith("- ") || DIRECTIVE.matcher(line).matches();
            if ((line.isEmpty() || special) && !paragraph.isEmpty()) {
                blocks.add(new Paragraph(spans(paragraph.toString(), paragraphLine), false));
                paragraph.setLength(0);
            }
            if (line.isEmpty()) { continue; }
            if (line.startsWith("## ")) { blocks.add(new Heading(spans(line.substring(3).strip(), i + 1))); }
            else if (line.startsWith("- ")) { blocks.add(new Paragraph(spans(line.substring(2).strip(), i + 1), true)); }
            else if (DIRECTIVE.matcher(line).matches()) { blocks.add(directive(line, i + 1)); }
            else {
                if (line.startsWith("{") && line.endsWith("}") && !INLINE.matcher(line).matches()) {
                    throw new IllegalArgumentException("line " + (i + 1) + ": unknown directive " + line);
                }
                if (paragraph.isEmpty()) { paragraphLine = i + 1; }
                else if (!(cjk(paragraph.charAt(paragraph.length() - 1)) || cjk(line.charAt(0)))) { paragraph.append(' '); }
                paragraph.append(line);
            }
        }
        return new Entry(id, section, order, required(header, "title"), icon, advancement, List.copyOf(blocks));
    }

    private static String required(Map<String, String> header, String key) {
        String v = header.get(key);
        if (v == null || v.isEmpty()) { throw new IllegalArgumentException("the header needs " + key); }
        return v;
    }

    private static Block directive(String line, int number) {
        Matcher m = DIRECTIVE.matcher(line);
        if (!m.matches()) { throw new IllegalStateException(); }
        List<String> args = List.of(m.group(2).strip().split("\\s+"));
        switch (m.group(1)) {
            case "items" -> {
                for (String a : args) { if (!ID.matcher(a).matches()) { throw new IllegalArgumentException("line " + number + ": not an item id: " + a); } }
                return new Items(args);
            }
            case "recipe" -> {
                if (args.size() != 1 || !ID.matcher(args.getFirst()).matches()) { throw new IllegalArgumentException("line " + number + ": {recipe <id>}"); }
                return new Recipe(args.getFirst());
            }
            default -> {
                if (args.size() != 1 || !NAME.matcher(args.getFirst()).matches()) {
                    throw new IllegalArgumentException("line " + number + ": {" + m.group(1) + " <name>} (lower case, digits and _)");
                }
                return switch (m.group(1)) {
                    case "image" -> new Image(args.getFirst());
                    case "design" -> new Design(args.getFirst());
                    default -> new See(args.getFirst());
                };
            }
        }
    }

    /** The spans of a line of text: plain, bold, keys and item names. */
    public static List<Span> spans(String text, int line) {
        List<Span> out = new ArrayList<>();
        Matcher m = INLINE.matcher(text);
        int at = 0;
        while (m.find()) {
            if (m.start() > at) { out.add(new Text(text.substring(at, m.start()), false)); }
            if (m.group(1) != null) { out.add(new Text(m.group(1), true)); }
            else if (m.group(2).equals("key")) { out.add(new Key(m.group(3))); }
            else {
                if (!ID.matcher(m.group(3)).matches()) { throw new IllegalArgumentException("line " + line + ": not an item id: " + m.group(3)); }
                out.add(new ItemName(m.group(3)));
            }
            at = m.end();
        }
        if (at < text.length()) { out.add(new Text(text.substring(at), false)); }
        for (Span s : out) {
            if (s instanceof Text t && (t.text().contains("{") || t.text().contains("}") || t.text().contains("**"))) {
                throw new IllegalArgumentException("line " + line + ": unmatched {, } or ** in: " + t.text());
            }
        }
        return List.copyOf(out);
    }

    /** Whether a character is written without spaces around it (CJK ideographs, kana, full-width punctuation). */
    static boolean cjk(char c) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS || b == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || b == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS || b == Character.UnicodeBlock.HIRAGANA
                || b == Character.UnicodeBlock.KATAKANA || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || b == Character.UnicodeBlock.GENERAL_PUNCTUATION && (c == '…' || c == '—');
    }

    /** What must be the same in every language: the header but the title, the blocks' kinds and arguments, the keys and items in the text. */
    public static List<String> structure(Entry e) {
        List<String> out = new ArrayList<>(List.of(e.id(), e.section().name(), Integer.toString(e.order()), e.icon(), e.advancement()));
        for (Block b : e.blocks()) {
            out.add(switch (b) {
                case Heading h -> "heading";
                case Paragraph p -> p.bullet() ? "bullet" : "paragraph";
                case Items i -> "items " + String.join(" ", i.ids());
                case Image i -> "image " + i.name();
                case Recipe r -> "recipe " + r.id();
                case Design d -> "design " + d.sample();
                case See s -> "see " + s.entry();
            });
        }
        java.util.SortedSet<String> refs = new java.util.TreeSet<>();
        for (Block b : e.blocks()) {
            List<Span> spans = b instanceof Heading h ? h.spans() : b instanceof Paragraph p ? p.spans() : List.of();
            for (Span s : spans) {
                if (s instanceof Key k) { refs.add("key " + k.mapping()); }
                if (s instanceof ItemName i) { refs.add("item " + i.id()); }
            }
        }
        out.addAll(refs);
        return out;
    }
}
