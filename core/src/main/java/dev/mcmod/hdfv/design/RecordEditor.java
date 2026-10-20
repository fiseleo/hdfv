package dev.mcmod.hdfv.design;

import dev.mcmod.aerocore.api.engine.EngineOil;
import dev.mcmod.hdfv.part.engine.EngineMaterial;
import dev.mcmod.hdfv.part.engine.FuelSystemDesign;
import dev.mcmod.hdfv.part.engine.JetControlDesign;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Reads and edits a design's parameters through its record components, so every component record gets an editor
 * without one being written for it. A parameter is a number, a whole number, an estimate (a number, or blank for the
 * design's own estimate: NaN), a switch, or a choice (enums, materials, fuels, oil grades).
 */
public final class RecordEditor {
    private RecordEditor() {}

    /** 0.12: TEXT is a String whose name ends in "Text": lettering (Lettering.clean). */
    public enum Type { NUMBER, WHOLE, ESTIMATE, SWITCH, CHOICE, COLOR, NUMBERS, TEXT }

    /**
     * The colours a colour parameter steps through (white, black, silver, grey, red, orange, yellow, green, blue, navy,
     * sky blue, purple, brown, gold, antifouling red, sand); any other #RRGGBB can be typed.
     */
    public static final List<String> PALETTE = List.of("#F3F4F6", "#1A1A1C", "#B9BEC4", "#6B7077", "#B3161C", "#E0581E", "#F2C230", "#2E7D32",
            "#1D4F91", "#1F3B73", "#4A90C8", "#6A3D9A", "#7A4A2A", "#C8962A", "#8E2F2A", "#D3D0C6");

    /** A colour as stored: "#RRGGBB" in capitals, or "" where blank is allowed; IllegalArgumentException for anything else. */
    public static String color(String text) {
        String t = text.trim();
        if (t.isEmpty()) { return ""; }
        if (t.startsWith("#")) { t = t.substring(1); }
        if (!t.matches("[0-9a-fA-F]{6}")) { throw new IllegalArgumentException("Not a colour (#RRGGBB): " + text); }
        return "#" + t.toUpperCase(Locale.ROOT);
    }

    /** Whether a stored colour is valid: #RRGGBB, or blank where allowed. */
    public static boolean isColor(String value, boolean blankAllowed) {
        if (value == null) { return false; }
        if (value.isEmpty()) { return blankAllowed; }
        return value.matches("#[0-9A-F]{6}");
    }

    /** A stored colour as opaque ARGB. */
    public static int argb(String value) { return 0xff000000 | Integer.parseInt(value.substring(1), 16); }

    /** The palette colour after this one (the first when it is not in the palette). */
    public static String nextColor(String value) {
        int i = PALETTE.indexOf(value == null ? "" : value.toUpperCase(Locale.ROOT));
        return PALETTE.get((i + 1) % PALETTE.size());
    }

    /**
     * One editable parameter: its record component name, kind, value, the choices of a CHOICE, and how a number is
     * shown (Display: unit and factor from SI).
     */
    public record Parameter(String name, Type type, Object value, List<String> choices, Display display) {
        /** The value as an editor shows it (numbers in the display unit). */
        public String text() { return RecordEditor.text(type, value, display); }
        public String unit() { return display.unit(); }
    }

    /**
     * How a number is shown: its unit and the factor from the SI value (a power of ten, so that text and value convert
     * exactly: 0.086 m is "86" mm and "86" reads back as 0.086).
     */
    public record Display(String unit, BigDecimal factor) {
        public static final Display NONE = new Display("", BigDecimal.ONE);
        /** An SI value (a value of the parameter, or of a derived quantity named the same way) in this unit. */
        public double of(double si) { return si * factor.doubleValue(); }
    }

    /** A record that hides parameters that mean nothing in its present state (a section family's other shapes). */
    public interface Shown { boolean shows(String parameter); }

    /** 0.18: a record whose numbers may be blank for "automatic" (NaN; the car front's page): each is an ESTIMATE. */
    public interface BlankIsAuto {}

    /**
     * 0.15: a design that hides parameters of its pages by the rest of it (a chassis station's tube-frame bay on a
     * ladder): Designs.parameters asks it after the page's own Shown.
     */
    public interface ShownOnPage { boolean shows(String page, String parameter); }

    /** The editable parameters of a record (its "name" is not a parameter; a Shown record's hidden ones are left out). */
    public static List<Parameter> parameters(Record record) {
        List<Parameter> out = new ArrayList<>();
        for (RecordComponent c : record.getClass().getRecordComponents()) {
            if (c.getName().equals("name")) { continue; }
            if (record instanceof Shown shown && !shown.shows(c.getName())) { continue; }
            Object value = read(c, record);
            Type type = typeOf(record.getClass(), c);
            if (type == null) { continue; }
            out.add(new Parameter(c.getName(), type, value, choices(record.getClass(), c),
                    type == Type.NUMBER || type == Type.ESTIMATE || type == Type.NUMBERS ? display(c.getName()) : Display.NONE));
        }
        return out;
    }

    /** The record with one component replaced by a value of its type (the record's own checks may refuse it). */
    public static <R extends Record> R with(R record, String name, Object value) {
        RecordComponent[] components = record.getClass().getRecordComponents();
        Object[] args = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        boolean found = false;
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            if (components[i].getName().equals(name)) { args[i] = value; found = true; }
            else { args[i] = read(components[i], record); }
        }
        if (!found) { throw new IllegalArgumentException("No parameter " + name); }
        try {
            Constructor<?> constructor = record.getClass().getDeclaredConstructor(types);
            @SuppressWarnings("unchecked") R built = (R) constructor.newInstance(args);
            return built;
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof RuntimeException r ? r : new IllegalArgumentException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The record with a parameter set from an editor's text; IllegalArgumentException when the text is not a value of it. */
    public static <R extends Record> R set(R record, String name, String text) {
        for (Parameter p : parameters(record)) {
            if (!p.name().equals(name)) { continue; }
            RecordComponent c = component(record.getClass(), name);
            return with(record, name, parse(c.getType(), p, text.trim()));
        }
        throw new IllegalArgumentException("No parameter " + name);
    }

    private static Object parse(Class<?> type, Parameter p, String text) {
        switch (p.type()) {
            case NUMBER -> { return finite(text, p.display()); }
            case WHOLE -> {
                try { return Integer.parseInt(text); } catch (NumberFormatException e) { throw new IllegalArgumentException("Not a whole number: " + text); }
            }
            case ESTIMATE -> { return text.isEmpty() ? Double.NaN : finite(text, p.display()); }
            case SWITCH -> {
                if (!text.equals("true") && !text.equals("false")) { throw new IllegalArgumentException("Not true or false: " + text); }
                return Boolean.parseBoolean(text);
            }
            case COLOR -> { return color(text); }
            case TEXT -> { return dev.mcmod.hdfv.look.Lettering.clean(text); }
            case NUMBERS -> { return numbers(text, p.display()); }
            case CHOICE -> {
                if (!p.choices().contains(text)) { throw new IllegalArgumentException("Not one of " + p.choices() + ": " + text); }
                if (type.isEnum()) {
                    for (Object constant : type.getEnumConstants()) { if (((Enum<?>) constant).name().toLowerCase(Locale.ROOT).equals(text)) { return constant; } }
                }
                if (type == EngineMaterial.class) {
                    for (EngineMaterial m : EngineMaterial.STANDARD) { if (m.name().equals(text)) { return m; } }
                }
                return text;
            }
        }
        throw new IllegalStateException();
    }

    /** A list of numbers (a gearbox's ratios) typed in the display unit, separated by commas, semicolons or spaces. */
    private static double[] numbers(String text, Display display) {
        String[] parts = text.trim().split("[,;\\s]+");
        if (text.isBlank() || parts.length == 0) { throw new IllegalArgumentException("At least one number"); }
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) { out[i] = finite(parts[i], display); }
        return out;
    }

    /** A number typed in the display unit, as the SI value. */
    private static double finite(String text, Display display) {
        try {
            double d = new BigDecimal(text).divide(display.factor()).doubleValue();
            if (!Double.isFinite(d)) { throw new NumberFormatException(); }
            return d;
        } catch (RuntimeException e) { throw new IllegalArgumentException("Not a number: " + text); }
    }

    static String text(Type type, Object value, Display display) {
        return switch (type) {
            case ESTIMATE -> value instanceof Double d && Double.isNaN(d) ? "" : number((Double) value, display);
            case NUMBER -> number((Double) value, display);
            case WHOLE, SWITCH -> String.valueOf(value);
            case COLOR, TEXT -> String.valueOf(value);
            case NUMBERS -> {
                StringBuilder b = new StringBuilder();
                for (double d : (double[]) value) { b.append(b.length() == 0 ? "" : ", ").append(number(d, display)); }
                yield b.toString();
            }
            case CHOICE -> value instanceof Enum<?> e ? e.name().toLowerCase(Locale.ROOT) : value instanceof EngineMaterial m ? m.name() : String.valueOf(value);
        };
    }

    /** The shortest decimal of the double (Double.toString) in the display unit, without an exponent. */
    private static String number(double d, Display display) {
        if (!Double.isFinite(d)) { return String.valueOf(d); }
        return new BigDecimal(Double.toString(d)).multiply(display.factor()).stripTrailingZeros().toPlainString();
    }

    private static Type typeOf(Class<?> owner, RecordComponent c) {
        Class<?> t = c.getType();
        if (t == double.class) { return c.getName().equals("massGivenKg") || BlankIsAuto.class.isAssignableFrom(owner) ? Type.ESTIMATE : Type.NUMBER; }
        if (t == double[].class) { return Type.NUMBERS; }
        if (t == int.class) { return Type.WHOLE; }
        if (t == boolean.class) { return Type.SWITCH; }
        if (t == String.class && c.getName().endsWith("Color")) { return Type.COLOR; }
        if (t == String.class && c.getName().endsWith("Text")) { return Type.TEXT; }
        if (t.isEnum() || t == EngineMaterial.class || !choices(owner, c).isEmpty()) { return Type.CHOICE; }
        return null;
    }

    /** The values a choice may take: enum constants and standard materials in lower case, the fuels and oil grades the parts accept. */
    public static List<String> choices(Class<?> owner, RecordComponent c) {
        Class<?> t = c.getType();
        if (t.isEnum()) { return Arrays.stream(t.getEnumConstants()).map(e -> ((Enum<?>) e).name().toLowerCase(Locale.ROOT)).toList(); }
        if (t == EngineMaterial.class) { return EngineMaterial.STANDARD.stream().map(EngineMaterial::name).toList(); }
        if (t == String.class && c.getName().equals("fuel") && owner == FuelSystemDesign.class) { return FuelSystemDesign.FUELS; }
        if (t == String.class && c.getName().equals("fuel") && owner == JetControlDesign.class) { return List.of("kerosene", "gasoline", "methanol", "propane"); }
        if (t == String.class && c.getName().equals("fuel") && owner == dev.mcmod.hdfv.part.FuelTankDesign.class) { return List.copyOf(dev.mcmod.hdfv.fluid.FuelGrades.fluids()); }
        if (t == String.class && c.getName().equals("fuel") && owner == dev.mcmod.hdfv.part.body.BodySettingsDesign.class) { return List.copyOf(dev.mcmod.hdfv.fluid.FuelGrades.ambient()); }
        if (t == String.class && c.getName().equals("oilGrade")) { return EngineOil.GRADES.stream().map(EngineOil::grade).toList(); }
        if (t == String.class && c.getName().equals("fuel") && (owner == dev.mcmod.hdfv.part.engine.RamControlDesign.class
                || owner == dev.mcmod.hdfv.part.engine.ScramControlDesign.class)) { return dev.mcmod.hdfv.part.engine.RamControlDesign.FUELS; }
        if (t == String.class && c.getName().equals("fuel") && owner == dev.mcmod.hdfv.part.engine.PulseControlDesign.class) { return dev.mcmod.hdfv.part.engine.PulseControlDesign.FUELS; }
        if (t == String.class && c.getName().equals("propellant") && owner == dev.mcmod.hdfv.part.engine.GrainDesign.class) { return dev.mcmod.hdfv.part.engine.GrainDesign.PROPELLANTS; }
        return List.of();
    }

    /** Parameters shown in other units than the rest of their kind. */
    private static final java.util.Map<String, Display> DISPLAY_OVERRIDES = java.util.Map.of(
            "oilVolumeM3", new Display("L", new BigDecimal("1E+3")), "coolantVolumeM3", new Display("L", new BigDecimal("1E+3")),
            "plenumVolumeM3", new Display("L", new BigDecimal("1E+3")), "radiatorAreaM2", new Display("m²", BigDecimal.ONE),
            "finAreaM2", new Display("m²", BigDecimal.ONE), "followerMassKg", new Display("g", new BigDecimal("1E+3")),
            "spanM", new Display("m", BigDecimal.ONE), "radiusM", new Display("m", BigDecimal.ONE), "rootChordM", new Display("m", BigDecimal.ONE),
            "tipChordM", new Display("m", BigDecimal.ONE));

    /** Body module and settings parameters shown in metres and their own units (a body is metres long, not millimetres). */
    private static final java.util.Map<String, Display> BODY_DISPLAY = java.util.Map.ofEntries(
            java.util.Map.entry("lengthM", new Display("m", BigDecimal.ONE)), java.util.Map.entry("widthM", new Display("m", BigDecimal.ONE)),
            java.util.Map.entry("heightM", new Display("m", BigDecimal.ONE)), java.util.Map.entry("bottomM", new Display("m", BigDecimal.ONE)),
            java.util.Map.entry("seatPitchM", new Display("m", BigDecimal.ONE)), java.util.Map.entry("doorWidthM", new Display("m", BigDecimal.ONE)),
            java.util.Map.entry("doorHeightM", new Display("m", BigDecimal.ONE)), java.util.Map.entry("frameSpacingM", new Display("m", BigDecimal.ONE)),
            java.util.Map.entry("sillM", new Display("m", BigDecimal.ONE)), java.util.Map.entry("cargoM3", new Display("m³", BigDecimal.ONE)),
            java.util.Map.entry("fuelM3", new Display("L", new BigDecimal("1E+3"))), java.util.Map.entry("floorKgM2", new Display("kg/m²", BigDecimal.ONE)),
            java.util.Map.entry("cargoDensityKgM3", new Display("kg/m³", BigDecimal.ONE)),
            java.util.Map.entry("extraDragAreaM2", new Display("m²", BigDecimal.ONE)));

    /**
     * How a number named with an SI suffix is shown: lengths in mm, areas in cm², volumes in cm³ (oil, coolant and
     * plenum in litres), pressures in kPa, spring rates in N/mm, the rest in their SI unit; "" for ratios and counts.
     */
    public static Display display(String name) {
        Display d = DISPLAY_OVERRIDES.get(name);
        if (d != null) { return d; }
        d = BODY_DISPLAY.get(name);
        if (d != null) { return d; }
        String unit = unit(name);
        return switch (unit) {
            case "m" -> new Display("mm", new BigDecimal("1E+3"));
            case "m²" -> new Display("cm²", new BigDecimal("1E+4"));
            case "m³" -> new Display("cm³", new BigDecimal("1E+6"));
            case "Pa" -> new Display("kPa", new BigDecimal("1E-3"));
            case "N/m" -> new Display("N/mm", new BigDecimal("1E-3"));
            default -> new Display(unit, BigDecimal.ONE);
        };
    }

    /** The SI unit of a parameter from its name's suffix ("boreM" is in m, "maxAdvanceDeg" in degrees); "" for ratios and counts. */
    public static String unit(String name) {
        String[][] suffixes = {{"NmsPerRad", "N·m·s/rad"}, {"NsPerM", "N·s/m"}, {"In", "in"}, {"KgM2", "kg·m²"}, {"M3PerS", "m³/s"}, {"CcPerMin", "cm³/min"}, {"NPerM", "N/m"}, {"KgPerS", "kg/s"}, {"M3", "m³"},
                {"M2", "m²"}, {"Deg", "°"}, {"Rpm", "rpm"}, {"Pa", "Pa"}, {"Nm", "N·m"}, {"Kg", "kg"}, {"M", "m"}, {"N", "N"}, {"K", "K"}, {"C", "°C"}};
        for (String[] s : suffixes) {
            if (name.endsWith(s[0]) && name.length() > s[0].length() && Character.isLowerCase(name.charAt(name.length() - s[0].length() - 1))) { return s[1]; }
        }
        return "";
    }

    private static RecordComponent component(Class<?> type, String name) {
        for (RecordComponent c : type.getRecordComponents()) { if (c.getName().equals(name)) { return c; } }
        throw new IllegalArgumentException("No parameter " + name);
    }

    private static Object read(RecordComponent c, Record record) {
        try { return c.getAccessor().invoke(record); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
