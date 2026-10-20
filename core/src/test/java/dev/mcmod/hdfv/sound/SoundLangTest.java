package dev.mcmod.hdfv.sound;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.19: every name the sound's settings screen, subtitles and keys show has a translation in both languages: the
 * config entries' translation keys (HdfvClientConfig), the subtitles the client plays (Cues and its callers) and the
 * key mappings (FlightControls, DriveControls). Read from the sources, so a new one is caught.
 */
class SoundLangTest {
    private static final Path NEOFORGE = Path.of("..", "neoforge", "src", "main");
    private static final Path JAVA = NEOFORGE.resolve("java/dev/mcmod/hdfv/neoforge"), LANG = NEOFORGE.resolve("resources/assets/hdfv/lang");

    private static String read(Path p) throws IOException { return Files.readString(p, StandardCharsets.UTF_8); }

    private static void all(Pattern p, String text, String prefix, TreeSet<String> into) {
        Matcher m = p.matcher(text);
        while (m.find()) { into.add(prefix + m.group(1)); }
    }

    @Test void everySoundNameIsTranslated() throws IOException {
        TreeSet<String> keys = new TreeSet<>();
        all(Pattern.compile("\\.translation\\(\"([^\"]+)\"\\)"), read(JAVA.resolve("HdfvClientConfig.java")), "", keys);
        assertTrue(keys.size() >= 13, "the sound section and its 12 entries: " + keys);
        // subtitles: every quoted name on a Cues.subtitle line, and Cues's own switches
        Pattern quoted = Pattern.compile("\"([a-z_]+)\"");
        try (var files = Files.list(JAVA.resolve("client/sound"))) {
            for (Path f : files.toList()) {
                for (String line : read(f).split("\n")) {
                    if (line.contains("Cues.subtitle(") || line.contains("subtitle(\"")) { all(quoted, line, "subtitles.hdfv.", keys); }
                }
            }
        }
        all(Pattern.compile("-> \"([a-z_]+)\";"), read(JAVA.resolve("client/sound/Cues.java")), "subtitles.hdfv.", keys);
        assertTrue(keys.contains("subtitles.hdfv.boom") && keys.contains("subtitles.hdfv.machine") && keys.contains("subtitles.hdfv.door_motor"), keys.toString());
        // the 0.19 keys
        for (String k : List.of("horn", "indicate_left", "indicate_right", "hazard")) { keys.add("key.hdfv." + k); }
        for (String file : List.of("en_us.json", "zh_tw.json")) {
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) dev.mcmod.aerocore.api.vehicle.Json.parse(read(LANG.resolve(file)));
            TreeSet<String> missing = new TreeSet<>();
            for (String k : keys) { if (!m.containsKey(k)) { missing.add(k); } }
            assertTrue(missing.isEmpty(), file + " lacks " + missing);
        }
    }
}
