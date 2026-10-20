package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.engine.Turbojet;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Bench;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.neoforge.design.BlueprintItem;
import dev.mcmod.hdfv.neoforge.design.Blueprints;
import dev.mcmod.hdfv.neoforge.design.DesignNetwork;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.engine.EngineMaterial;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The design screen (設計畫面): a part design's parameters page by page (an engine's components), generated from the
 * component records (RecordEditor), with its issues as they change (INVALID red, EXTRAPOLATION yellow), its figures,
 * and for an engine a bench (Bench) computed off the game thread. Saving sends the JSON to the server, which reads it
 * again and writes it onto a blueprint (Blueprints.save).
 */
public final class DesignScreen extends Screen {
    static final String OVERVIEW = "overview", BENCH = "bench";
    private static final int LIST_X = 6, LIST_W = 84, TOP = 38, ENTRY = 11, ROW = 16, FIELD_W = 58, FIELD_H = 14;
    private static final int WHITE = 0xFFFFFFFF, GREY = 0xFFA0A0A0, RED = 0xFFFF5555, YELLOW = 0xFFFFD24A, GREEN = 0xFF7CDB6A,
            BLUE = 0xFF6FB7FF, ORANGE = 0xFFFF9F43, EDIT = 0xFFE0E0E0;
    private static final ExecutorService BENCH_THREAD = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hdfv-bench");
        t.setDaemon(true);
        return t;
    });

    /** The design last edited in this game session and its page, to come back to. */
    private static PartDesign last;
    private static String lastPage = OVERVIEW;

    private PartDesign design;
    private Blueprints.Target target;
    private String page;
    /** Text that is not a value of its parameter, by "page.parameter" (shown red; the design keeps the last good value). */
    private final Map<String, String> pending = new HashMap<>();
    private List<DesignIssue> issues = List.of();
    private final Map<String, DesignIssue.Kind> worst = new HashMap<>();
    private List<Designs.Figure> figures = List.of();
    private int sample, listScroll, scroll, contentHeight;
    /** The category and kind the top bar shows (its new-design button and samples are of that kind). */
    private Designs.Category category;
    private String kind;
    /** The kind the module tools add next. */
    private Enum<?> addKind = dev.mcmod.hdfv.part.body.BodyModuleDesign.Kind.CABIN;
    /** Height of the module tools under the page list (a body's add, move, copy and remove buttons). */
    private static final int MODULE_TOOLS = 46;
    private Component status;
    private int statusColor = GREY;

    private record Row(RecordEditor.Parameter parameter, AbstractWidget widget, int column, int y) {}

    /** A colour swatch's width. */
    private static final int SWATCH = 12;

    /** The colour a colour row shows: its value, or for a blank module colour the body's main colour (grey while it is not a colour yet). */
    private int swatch(Row r) {
        String v = r.widget() instanceof EditBox box ? box.getValue().trim() : String.valueOf(r.parameter().value());
        try {
            v = RecordEditor.color(v);
            if (v.isEmpty() && design instanceof BodyDesign body) { v = body.settings().paintColor(); }
            return v.isEmpty() ? 0xff808080 : RecordEditor.argb(v);
        } catch (RuntimeException e) {
            return 0xff808080;
        }
    }
    private final List<Row> rows = new ArrayList<>();
    private int labelWidth, columnWidth, rowsHeight;

    /** The 3D view and where it is this page (x, y, w, h; null when there is no room or nothing to draw); the overview's figure columns. */
    private final Preview preview = new Preview();
    private int[] previewRect;
    private boolean dragging;
    private Button cutawayButton, enlargeButton, doorsButton;
    /** The 3D view over the whole page instead of beside it. */
    private boolean enlarged;
    private int figureLabels, figureValues;

    /**
     * A turbojet's or turbofan's bench: thrust, TSFC and turbine inlet temperature against throttle (0..1) at a flight
     * condition, and the start and run-up at sea level.
     */
    private record JetBench(double[] throttle, double[] thrustN, double[] tsfc, double[] turbineInletK, Bench.JetRun run, double altitudeM, double speedMps,
                            Bench.WetPoint wet) {
        static JetBench of(List<double[]> rows, Bench.JetRun run, double altitudeM, double speedMps, Bench.WetPoint wet) {
            int n = rows.size();
            double[][] c = new double[4][n];
            for (int i = 0; i < n; i++) { for (int k = 0; k < 4; k++) { c[k][i] = rows.get(i)[k]; } }
            return new JetBench(c[0], c[1], c[2], c[3], run, altitudeM, speedMps, wet);
        }
    }
    /** 0.10: thrust and specific impulse against Mach at an altitude (a ramjet, a scramjet; a turbo-ramjet with its bypass). */
    private record MachBench(List<Bench.MachPoint> sweep, double altitudeM, boolean bypass) {}
    /** A shaft engine's bench: output power against throttle, the free turbine's torque against output speed, the start and run-up driving the bench's load. */
    private record ShaftBench(List<Bench.ShaftPoint> sweep, List<Bench.TorquePoint> torque, Bench.ShaftRun run, double altitudeM, double speedMps) {}

    /** Whether a design's bench runs at a flight condition (altitude and speed). */
    private static boolean flies(PartDesign d) { return d instanceof JetEngineDesign || d instanceof TurbofanDesign || d instanceof ShaftTurbineDesign; }
    /** 0.10: whether a design's bench runs at an altitude only (a sweep in Mach or speed, or a booster's firing). */
    private static boolean sweeps(PartDesign d) {
        return d instanceof dev.mcmod.hdfv.part.engine.RamjetDesign || d instanceof dev.mcmod.hdfv.part.engine.ScramjetDesign
                || d instanceof dev.mcmod.hdfv.part.engine.TurboRamjetDesign || d instanceof dev.mcmod.hdfv.part.engine.PulsejetDesign
                || d instanceof dev.mcmod.hdfv.part.engine.SolidRocketDesign;
    }
    private static boolean airframe(PartDesign d) {
        return d instanceof dev.mcmod.hdfv.part.air.WingDesign || d instanceof dev.mcmod.hdfv.part.air.AirPropellerDesign || d instanceof dev.mcmod.hdfv.part.air.RotorDesign;
    }
    private CompletableFuture<Object> running;
    private long runningSince;
    private Object result;
    private PartDesign resultFor;
    private String benchError;
    private double altitude, speed;

    public DesignScreen(PartDesign design, Blueprints.Target target, Component status) {
        super(Component.translatable("hdfv.screen.title"));
        if (design == null) {
            this.design = last != null ? last : Designs.SAMPLES.get(0).design().get();
            this.page = last != null ? lastPage : OVERVIEW;
        } else {
            this.design = design;
            this.page = OVERVIEW;
        }
        this.target = target;
        pickKind(this.design);
        if (status != null) { this.status = status; statusColor = RED; }
        if (!entries().contains(page)) { page = OVERVIEW; }
        refresh();
    }

    // ---------------------------------------------------------------- state

    /** The top bar follows the design shown: its kind's category, the kind, and the sample it is (if it is one). */
    private void pickKind(PartDesign d) {
        Designs.Kind k = Designs.kindOf(d);
        category = k.category();
        kind = k.id();
        sample = 0;
        List<Designs.Sample> samples = Designs.samples(kind);
        for (int i = 0; i < samples.size(); i++) { if (samples.get(i).design().get().equals(d)) { sample = i; } }
    }

    private List<String> entries() {
        List<String> e = new ArrayList<>();
        e.add(OVERVIEW);
        e.addAll(Designs.pages(design));
        if (design instanceof PistonEngineDesign || flies(design) || sweeps(design) || airframe(design) || design instanceof BodyDesign
                || design instanceof dev.mcmod.hdfv.part.wheel.AxleDesign || design instanceof dev.mcmod.hdfv.part.chassis.ChassisDesign) { e.add(BENCH); }
        return e;
    }

    private void refresh() {
        try { issues = design.check(); }
        catch (RuntimeException e) { issues = List.of(DesignIssue.invalid("design", String.valueOf(e.getMessage()))); }
        worst.clear();
        for (DesignIssue i : issues) {
            Designs.Place p = Designs.place(design, i);
            if (p.page() == null || p.parameter() == null) { continue; }
            worst.merge(p.page() + "." + p.parameter(), i.kind(), (a, b) -> a == DesignIssue.Kind.INVALID ? a : b);
        }
        figures = Designs.figures(design);
        last = design;
    }

    private boolean invalid() { return issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID); }

    private void load(PartDesign d, Blueprints.Target t) {
        design = d;
        target = t;
        pickKind(d);
        pending.clear();
        scroll = 0;
        result = null;
        benchError = null;
        if (!entries().contains(page)) { page = OVERVIEW; }
        refresh();
        rebuildWidgets();
    }

    private void open(String p) {
        page = p;
        lastPage = p;
        scroll = 0;
        rebuildWidgets();
    }

    private void edit(String name, String text, EditBox box) {
        String key = page + "." + name;
        try {
            design = Designs.set(design, page, name, text);
            pending.remove(key);
            if (box != null) { box.setTextColor(EDIT); }
        } catch (RuntimeException e) {
            pending.put(key, text);
            if (box != null) { box.setTextColor(RED); }
        }
        refresh();
    }

    private void save() {
        String json = Designs.write(design);
        if (json.length() > Designs.MAX_JSON) { setStatus(Component.translatable("hdfv.save.too_long"), RED); return; }
        DesignNetwork.save(json, target);
        setStatus(Component.translatable(pending.isEmpty() ? "hdfv.save.sent" : "hdfv.save.sent_pending"), pending.isEmpty() ? GREY : YELLOW);
    }

    void setStatus(Component message, int color) { status = message; statusColor = color; }

    /** The server's answer to a save. */
    void saved(Blueprints.Result r) {
        setStatus(Blueprints.message(r), r == Blueprints.Result.SAVED || r == Blueprints.Result.OVERWRITTEN ? GREEN : RED);
    }

    // ---------------------------------------------------------------- widgets

    private int contentX() { return LIST_X + LIST_W + 8; }

    @Override protected void init() {
        rows.clear();
        Component save = Component.translatable(target == Blueprints.Target.NEW ? "hdfv.screen.save_new" : "hdfv.screen.save_held");
        int saveWidth = font.width(save) + 12;
        // the top bar: name, then category, kind, a new design of it, its samples and loading one; the name gives way first
        Component fresh = Component.translatable("hdfv.screen.new_design"), loadSample = Component.translatable("hdfv.screen.load_sample");
        int categoryW = 58, kindW = 92, freshW = font.width(fresh) + 10, sampleW = 100, loadW = font.width(loadSample) + 10;
        // 0.12: on a narrow screen the name, then the kind and sample give way (to 50, 60 and 64) before the bar runs into Save
        int room = width - 6 - saveWidth - 8 - LIST_X - (categoryW + freshW + loadW + 5 * 3);
        int nameW = Math.clamp(room - kindW - sampleW, 50, 110);
        int over = nameW + kindW + sampleW - room;
        if (over > 0) { int k = Math.min(over, kindW - 60); kindW -= k; over -= k; sampleW -= Math.clamp(over, 0, sampleW - 64); }
        int x = LIST_X;
        EditBox name = new EditBox(font, x, 8, nameW, FIELD_H, Component.translatable("hdfv.screen.name"));
        name.setMaxLength(64);
        name.setValue(design.name());
        name.setResponder(s -> { if (!s.isBlank()) { design = Designs.named(design, s.strip()); refresh(); } });
        addRenderableWidget(name);
        x += nameW + 4;
        addRenderableWidget(CycleButton.<Designs.Category>builder(c -> Component.translatable("hdfv.category." + c.name().toLowerCase(java.util.Locale.ROOT)))
                .withValues(Designs.Category.values()).withInitialValue(category).displayOnlyValue()
                .withTooltip(c -> net.minecraft.client.gui.components.Tooltip.create(Component.translatable("hdfv.screen.category")))
                .create(x, 7, categoryW, 16, Component.translatable("hdfv.screen.category"), (b, v) -> {
                    category = v;
                    kind = Designs.kinds(v).get(0).id();
                    sample = 0;
                    rebuildWidgets();
                }));
        x += categoryW + 3;
        List<Designs.Kind> kinds = Designs.kinds(category);
        Designs.Kind shown = kinds.stream().filter(k -> k.id().equals(kind)).findFirst().orElse(kinds.get(0));
        addRenderableWidget(CycleButton.<Designs.Kind>builder(k -> Component.translatable("hdfv.kind." + k.id())).withValues(kinds).withInitialValue(shown)
                .displayOnlyValue().create(x, 7, kindW, 16, Component.translatable("hdfv.screen.kind"), (b, v) -> {
                    kind = v.id();
                    sample = 0;
                    rebuildWidgets();
                }));
        x += kindW + 3;
        addRenderableWidget(Button.builder(fresh, b -> {
            String named = I18n.get("hdfv.screen.new_name", I18n.get("hdfv.kind." + kind));
            load(Designs.fresh(kind, named), Blueprints.Target.NEW);
        }).bounds(x, 7, freshW, 16).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("hdfv.screen.new_design.hint"))).build());
        x += freshW + 3;
        List<Designs.Sample> samples = Designs.samples(kind);
        if (!samples.isEmpty()) {
            sample = Math.clamp(sample, 0, samples.size() - 1);
            addRenderableWidget(CycleButton.<Designs.Sample>builder(s -> Component.translatable("hdfv.sample." + s.id())).withValues(samples)
                    .withInitialValue(samples.get(sample)).displayOnlyValue()
                    .create(x, 7, sampleW, 16, Component.translatable("hdfv.screen.sample"), (b, v) -> sample = samples.indexOf(v)));
            x += sampleW + 3;
            addRenderableWidget(Button.builder(loadSample, b -> load(samples.get(sample).design().get(), Blueprints.Target.NEW)).bounds(x, 7, loadW, 16).build());
        }
        addRenderableWidget(Button.builder(save, b -> save()).bounds(width - 6 - saveWidth, 7, saveWidth, 16).build());
        previewRect = null;
        cutawayButton = null;
        doorsButton = null;
        enlargeButton = null;
        if (enlarged && !page.equals(BENCH) && Preview.draws(design)) {
            previewRect = new int[]{contentX(), TOP, width - 6 - contentX(), height - TOP - 6};
            if (!page.equals(OVERVIEW)) { rowsHeight = 0; }
        }
        else if (Designs.pages(design).contains(page)) { buildParameters(); }
        else if (page.equals(BENCH)) { buildBench(); }
        else { placeOverviewPreview(); }
        if (design instanceof dev.mcmod.hdfv.design.ListedDesign) { buildModuleTools(); }
        if (previewRect != null) {
            // wings, propellers and rotors have no casing to cut away
            if (!airframe(design)) {
                Component label = Component.translatable(preview.cutaway ? "hdfv.screen.cutaway_on" : "hdfv.screen.cutaway_off");
                cutawayButton = addRenderableWidget(Button.builder(label, b -> { preview.cutaway = !preview.cutaway; rebuildWidgets(); })
                        .bounds(previewRect[0] + 2, previewRect[1] + previewRect[3] - 14, font.width(label) + 8, 12).build());
                // 0.12: a body with doors can show them open
                if (design instanceof dev.mcmod.hdfv.part.body.BodyDesign body && body.modules().stream().anyMatch(m -> m.doors() > 0)) {
                    Component doors = Component.translatable(preview.doorsOpen ? "hdfv.screen.doors_open" : "hdfv.screen.doors_shut");
                    doorsButton = addRenderableWidget(Button.builder(doors, b -> { preview.doorsOpen = !preview.doorsOpen; rebuildWidgets(); })
                            .bounds(previewRect[0] + font.width(label) + 14, previewRect[1] + previewRect[3] - 14, font.width(doors) + 8, 12).build());
                }
            }
            Component size = Component.translatable(enlarged ? "hdfv.screen.shrink" : "hdfv.screen.enlarge");
            enlargeButton = addRenderableWidget(Button.builder(size, b -> { enlarged = !enlarged; scroll = 0; rebuildWidgets(); })
                    .bounds(previewRect[0] + previewRect[2] - font.width(size) - 10, previewRect[1] + previewRect[3] - 14, font.width(size) + 8, 12).build());
        }
        layout();
    }

    /**
     * A listed design's tools under the page list (0.15: a body's modules, a chassis's stations; ListedDesign): the kind
     * to add, add it (after the item shown, else at the end), and for the item shown move it up or down the list, copy
     * it, remove it. Their names are the list's (hdfv.screen.&lt;list&gt;_add...).
     */
    private void buildModuleTools() {
        var list = (dev.mcmod.hdfv.design.ListedDesign) design;
        String name = list.listName();
        int y = height - 6 - MODULE_TOOLS + 2, at = list.listIndex(page);
        List<? extends Enum<?>> kinds = list.itemKinds();
        if (addKind == null || !kinds.contains(addKind)) { addKind = kinds.get(0); }
        addRenderableWidget(CycleButton.<Enum<?>>builder(k -> Component.translatable(list.kindKey(k)))
                .withValues(new ArrayList<Enum<?>>(kinds)).withInitialValue(addKind).displayOnlyValue()
                .create(LIST_X - 2, y, LIST_W + 2, 13, Component.translatable("hdfv.screen." + name + "_kind"), (b, v) -> addKind = v));
        addRenderableWidget(Button.builder(Component.translatable("hdfv.screen." + name + "_add"), b -> {
            int after = at >= 0 ? at : list.items() - 1;
            changeList(list.added(after, addKind), after + 1);
        }).bounds(LIST_X - 2, y + 15, LIST_W + 2, 13).build());
        if (at < 0) { return; }
        int w = (LIST_W + 2 - 6) / 4;
        String[] keys = {name + "_up", name + "_down", name + "_copy", name + "_remove"};
        for (int k = 0; k < 4; k++) {
            int which = k;
            Button button = Button.builder(Component.translatable("hdfv.screen." + keys[k]), b -> {
                switch (which) {
                    case 0 -> changeList(list.moved(at, -1), Math.max(at - 1, 0));
                    case 1 -> changeList(list.moved(at, 1), Math.min(at + 1, list.items() - 1));
                    case 2 -> changeList(list.duplicated(at), at + 1);
                    default -> changeList(list.removed(at), Math.min(at, list.items() - 2));
                }
            }).bounds(LIST_X - 2 + k * (w + 2), y + 30, w, 13).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("hdfv.screen." + keys[k] + ".hint"))).build();
            button.active = switch (which) {
                case 0 -> at > 0;
                case 1 -> at < list.items() - 1;
                case 3 -> list.items() > list.minItems();
                default -> true;
            };
            addRenderableWidget(button);
        }
    }

    /** The design changed by a list tool, showing the given item's page. */
    private void changeList(dev.mcmod.hdfv.design.ListedDesign changed, int show) {
        design = changed;
        pending.clear();
        page = changed.listPage(Math.max(show, 0));
        lastPage = page;
        scroll = 0;
        refresh();
        rebuildWidgets();
    }

    /** The overview's figures in two columns and the 3D view to their right, when there is room. */
    private void placeOverviewPreview() {
        figureLabels = 0;
        figureValues = 0;
        for (Designs.Figure f : figures) {
            figureLabels = Math.max(figureLabels, font.width(Component.translatable("hdfv.figure." + f.key())));
            figureValues = Math.max(figureValues, font.width(figureText(f)));
        }
        int px = contentX() + figureLabels + 10 + figureValues + 12, pw = width - 6 - px, ph = Math.min(height - TOP - 6, (int) (pw * 1.1));
        if (pw >= 90 && Preview.draws(design)) { previewRect = new int[]{px, TOP, pw, ph}; }
    }

    private static String figureText(Designs.Figure f) { return Plot.number(f.value()) + (f.unit().isEmpty() ? "" : " " + f.unit()); }

    private void buildParameters() {
        List<RecordEditor.Parameter> parameters = Designs.parameters(design, page);
        int units = 0, choices = 0;
        labelWidth = 0;
        for (RecordEditor.Parameter p : parameters) {
            labelWidth = Math.max(labelWidth, font.width(label(page, p.name())));
            units = Math.max(units, font.width(p.unit()));
            if (p.type() == RecordEditor.Type.CHOICE) { for (String c : p.choices()) { choices = Math.max(choices, font.width(choice(page, p, c)) + 10); } }
        }
        labelWidth = Math.min(labelWidth, 130);
        int choiceWidth = Math.min(Math.max(FIELD_W + 4 + units, choices), 120);
        columnWidth = labelWidth + 4 + Math.max(FIELD_W + 4 + units, choiceWidth) + 12;
        int columns = Math.max(1, Math.min(2, (width - contentX() - 6) / columnWidth));
        int perColumn = (parameters.size() + columns - 1) / columns;
        rowsHeight = perColumn * ROW;
        for (int i = 0; i < parameters.size(); i++) {
            RecordEditor.Parameter p = parameters.get(i);
            AbstractWidget w = switch (p.type()) {
                case SWITCH -> CycleButton.booleanBuilder(Component.translatable("hdfv.screen.yes"), Component.translatable("hdfv.screen.no"))
                        .displayOnlyValue().withInitialValue((Boolean) p.value())
                        .create(0, 0, FIELD_W, FIELD_H, label(page, p.name()), (b, v) -> edit(p.name(), String.valueOf(v), null));
                case CHOICE -> {
                    List<String> values = new ArrayList<>(p.choices());
                    if (!values.contains(p.text())) { values.add(0, p.text()); }   // a custom material keeps its place
                    yield CycleButton.<String>builder(v -> choice(page, p, v)).withValues(values).withInitialValue(p.text()).displayOnlyValue()
                            .create(0, 0, choiceWidth, FIELD_H, label(page, p.name()), (b, v) -> edit(p.name(), v, null));
                }
                default -> {
                    EditBox box = new EditBox(font, 0, 0, FIELD_W, FIELD_H, label(page, p.name()));
                    box.setMaxLength(p.type() == RecordEditor.Type.NUMBERS ? 128 : 32);
                    String key = page + "." + p.name();
                    box.setValue(pending.getOrDefault(key, p.text()));
                    box.moveCursorToStart(false);
                    box.setTextColor(pending.containsKey(key) ? RED : EDIT);
                    if (p.type() == RecordEditor.Type.ESTIMATE) { box.setHint(Component.translatable("hdfv.screen.estimate").withStyle(ChatFormatting.DARK_GRAY)); }
                    // 0.15: a blank hardpoint shows the simple geometry's point it stands for
                    String hint = Designs.hint(design, page, p.name());
                    if (hint != null) { box.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY)); }
                    box.setResponder(s -> edit(p.name(), s, box));
                    yield box;
                }
            };
            addRenderableWidget(w);
            rows.add(new Row(p, w, i / perColumn, (i % perColumn) * ROW));
        }
        // the 3D view beside the parameters, with this page's issues under it
        int besideX = contentX() + columns * columnWidth + 4, besideWidth = width - 6 - besideX;
        if (besideWidth >= 80 && Preview.draws(design)) {
            previewRect = new int[]{besideX, TOP, besideWidth, Math.min(besideWidth, (int) ((height - TOP - 6) * 0.6))};
        }
    }

    private void buildBench() {
        int x = contentX(), y = TOP;
        if (design instanceof dev.mcmod.hdfv.part.air.WingDesign || design instanceof BodyDesign) {
            EditBox spd = new EditBox(font, x + 40, y, 50, FIELD_H, Component.translatable("hdfv.bench.speed"));
            if (speed < 5) { speed = 50; }
            spd.setValue(Plot.number(speed));
            spd.setResponder(s -> speed = number(s, spd, 5, 340, speed));
            addRenderableWidget(spd);
            x += 130;
        }
        if (sweeps(design)) {
            EditBox alt = new EditBox(font, x + 40, y, 50, FIELD_H, Component.translatable("hdfv.bench.altitude"));
            alt.setValue(Plot.number(altitude));
            alt.setResponder(s -> altitude = number(s, alt, 0, 30000, altitude));
            addRenderableWidget(alt);
            x += 130;
        }
        if (flies(design)) {
            EditBox alt = new EditBox(font, x + 40, y, 50, FIELD_H, Component.translatable("hdfv.bench.altitude"));
            alt.setValue(Plot.number(altitude));
            alt.setResponder(s -> altitude = number(s, alt, 0, 20000, altitude));
            addRenderableWidget(alt);
            EditBox spd = new EditBox(font, x + 140, y, 50, FIELD_H, Component.translatable("hdfv.bench.speed"));
            spd.setValue(Plot.number(speed));
            spd.setResponder(s -> speed = number(s, spd, 0, 1000, speed));
            addRenderableWidget(spd);
            x += 230;
        }
        Button run = Button.builder(Component.translatable("hdfv.bench.run"), b -> runBench()).bounds(x, y - 1, 70, 16).build();
        run.active = !invalid();
        addRenderableWidget(run);
    }

    private double number(String s, EditBox box, double min, double max, double previous) {
        try {
            double v = Double.parseDouble(s.strip());
            if (v >= min && v <= max) { box.setTextColor(EDIT); return v; }
        } catch (NumberFormatException ignored) {
            // shown red below
        }
        box.setTextColor(RED);
        return previous;
    }

    /** Places the parameter widgets at the scroll position; those outside the page area are hidden. */
    private void layout() {
        int x0 = contentX(), y0 = TOP, bottom = height - 6;
        for (Row r : rows) {
            int y = y0 + r.y() - scroll;
            r.widget().setX(x0 + r.column() * columnWidth + labelWidth + 4);
            r.widget().setY(y);
            r.widget().visible = y >= y0 && y + FIELD_H <= bottom;
        }
    }

    private boolean overPreview(double mx, double my) {
        return previewRect != null && mx >= previewRect[0] && mx < previewRect[0] + previewRect[2] && my >= previewRect[1] && my < previewRect[1] + previewRect[3];
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) { preview.drag(dx, dy); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (overPreview(mx, my)) { preview.zoom(sy); return true; }
        if (mx < LIST_X + LIST_W) {
            int max = Math.max(0, entries().size() * ENTRY - (listBottom() - TOP));
            listScroll = (int) Math.clamp(listScroll - sy * ENTRY * 2, 0, max);
            return true;
        }
        if (!page.equals(BENCH)) {
            int max = Math.max(0, contentHeight - (height - TOP - 6));
            scroll = (int) Math.clamp(scroll - sy * ROW, 0, max);
            layout();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Row r : rows) {
                if (r.parameter().type() != RecordEditor.Type.COLOR || !r.widget().visible || !(r.widget() instanceof EditBox box)) { continue; }
                int sx = r.widget().getX() + FIELD_W + 4, y = r.widget().getY();
                if (mx >= sx && mx < sx + SWATCH && my >= y && my < y + FIELD_H) {
                    box.setValue(RecordEditor.nextColor(box.getValue()));
                    return true;
                }
            }
        }
        if (button == 0 && mx >= LIST_X && mx < LIST_X + LIST_W && my >= TOP && my < listBottom()) {
            int i = (int) ((my - TOP + listScroll) / ENTRY);
            List<String> e = entries();
            if (i >= 0 && i < e.size()) { open(e.get(i)); return true; }
        }
        if (super.mouseClicked(mx, my, button)) { return true; }
        if (button == 0 && overPreview(mx, my)) { dragging = true; setFocused(null); return true; }
        return false;
    }

    @Override public boolean isPauseScreen() { return false; }

    // ---------------------------------------------------------------- the bench

    void runBench() {
        if (running != null || invalid()) { return; }
        PartDesign d = design;
        double alt = altitude, v = speed;
        result = null;
        benchError = null;
        resultFor = d;
        runningSince = Util.getMillis();
        running = CompletableFuture.supplyAsync(() -> compute(d, alt, v), BENCH_THREAD);
    }

    private static Object compute(PartDesign d, double altitudeM, double speedMps) {
        if (d instanceof PistonEngineDesign p) { return Bench.fullLoad(p); }
        if (d instanceof JetEngineDesign j) {
            List<double[]> rows = Bench.throttleSweep(j, Bench.flight(altitudeM, speedMps), 21).stream()
                    .map(p -> new double[]{p.throttle(), p.thrustN(), p.tsfcGPerKNs(), p.point().tt4()}).toList();
            return JetBench.of(rows, Bench.startAndRunUp(j), altitudeM, speedMps, Bench.wet(j, Bench.flight(altitudeM, speedMps)));
        }
        if (d instanceof dev.mcmod.hdfv.part.engine.RamjetDesign r) { return new MachBench(Bench.machSweep(r, altitudeM), altitudeM, false); }
        if (d instanceof dev.mcmod.hdfv.part.engine.ScramjetDesign x) { return new MachBench(Bench.machSweep(x, altitudeM), altitudeM, false); }
        if (d instanceof dev.mcmod.hdfv.part.engine.TurboRamjetDesign t) { return new MachBench(Bench.machSweep(t, altitudeM), altitudeM, true); }
        if (d instanceof dev.mcmod.hdfv.part.engine.PulsejetDesign pj) { return Bench.pulsejet(pj, altitudeM); }
        if (d instanceof dev.mcmod.hdfv.part.engine.SolidRocketDesign sr) { return Bench.rocket(sr, altitudeM); }
        if (d instanceof TurbofanDesign f) {
            List<double[]> rows = Bench.throttleSweep(f, Bench.flight(altitudeM, speedMps), 21).stream()
                    .map(p -> new double[]{p.throttle(), p.thrustN(), p.tsfcGPerKNs(), p.point().tt4()}).toList();
            return JetBench.of(rows, Bench.startAndRunUp(f), altitudeM, speedMps, null);
        }
        if (d instanceof dev.mcmod.hdfv.part.air.WingDesign w) { return Bench.wingCurve(w, Math.max(speedMps, 5)); }
        if (d instanceof dev.mcmod.hdfv.part.air.AirPropellerDesign a) { return Bench.propellerCurve(a); }
        if (d instanceof dev.mcmod.hdfv.part.air.RotorDesign r) { return Bench.rotorHover(r); }
        if (d instanceof BodyDesign b) { return Bench.body(b, Math.max(speedMps, 5)); }
        // 0.15: an axle's travel and kinematics, a chassis's structure
        if (d instanceof dev.mcmod.hdfv.part.wheel.AxleDesign a) { return dev.mcmod.hdfv.design.WheelBench.axle(a); }
        if (d instanceof dev.mcmod.hdfv.part.chassis.ChassisDesign c) { return c.analysis(); }
        if (d instanceof ShaftTurbineDesign t) {
            Turbojet.Flight flight = Bench.flight(altitudeM, speedMps);
            return new ShaftBench(Bench.powerSweep(t, flight, 21), Bench.torqueCurve(t, flight, 31), Bench.startAndRunUp(t), altitudeM, speedMps);
        }
        throw new IllegalStateException("No bench for " + d.type());
    }

    private void pollBench() {
        if (running == null || !running.isDone()) { return; }
        try { result = running.join(); }
        catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            benchError = String.valueOf(cause.getMessage());
        }
        running = null;
    }

    // ---------------------------------------------------------------- drawing

    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        pollBench();
        List<Component> tooltip = new ArrayList<>();
        renderList(g, mx, my);
        int x0 = contentX();
        long bad = issues.stream().filter(i -> i.kind() == DesignIssue.Kind.INVALID).count(), out = issues.size() - bad;
        MutableComponent summary = BlueprintItem.typeName(design.type()).append("  ");
        summary.append(issues.isEmpty() ? Component.translatable("hdfv.screen.ok").withStyle(ChatFormatting.GREEN)
                : Component.translatable("hdfv.screen.issues", bad, out).withStyle(bad > 0 ? ChatFormatting.RED : ChatFormatting.YELLOW));
        g.drawString(font, summary, x0, 26, WHITE);
        if (status != null) { g.drawString(font, status, width - 6 - font.width(status), 26, statusColor); }
        if (enlarged && previewRect != null) { /* the view has the page */ }
        else if (page.equals(OVERVIEW)) { renderOverview(g); }
        else if (page.equals(BENCH)) { renderBench(g); }
        else { renderParameters(g, mx, my, tooltip); }
        if (previewRect != null) {
            preview.render(g, previewRect[0], previewRect[1], previewRect[2], previewRect[3], design, page);
            // the cutaway button sits over the view: draw it again on top
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            if (cutawayButton != null) { cutawayButton.render(g, mx, my, partialTick); }
            if (doorsButton != null) { doorsButton.render(g, mx, my, partialTick); }
            if (enlargeButton != null) { enlargeButton.render(g, mx, my, partialTick); }
            g.pose().popPose();
        }
        if (!tooltip.isEmpty()) { g.renderComponentTooltip(font, tooltip, mx, my); }
    }

    /** Where the page list ends: above the module tools for a body. */
    private int listBottom() { return height - 6 - (design instanceof dev.mcmod.hdfv.design.ListedDesign ? MODULE_TOOLS : 0); }

    private void renderList(GuiGraphics g, int mx, int my) {
        List<String> e = entries();
        int bottom = listBottom();
        g.fill(LIST_X - 2, TOP - 2, LIST_X + LIST_W, bottom, 0x60000000);
        g.enableScissor(LIST_X - 2, TOP - 2, LIST_X + LIST_W, bottom);
        for (int i = 0; i < e.size(); i++) {
            int y = TOP + i * ENTRY - listScroll;
            String key = e.get(i);
            boolean selected = key.equals(page), hover = mx >= LIST_X && mx < LIST_X + LIST_W && my >= y && my < y + ENTRY && my < bottom;
            if (selected) { g.fill(LIST_X - 2, y - 1, LIST_X + LIST_W, y + ENTRY - 1, 0x80305080); }
            else if (hover) { g.fill(LIST_X - 2, y - 1, LIST_X + LIST_W, y + ENTRY - 1, 0x40FFFFFF); }
            int color = WHITE;
            if (!key.equals(OVERVIEW) && !key.equals(BENCH)) {
                for (DesignIssue issue : issues) {
                    Designs.Place p = Designs.place(design, issue);
                    if (key.equals(p.page())) { color = issue.kind() == DesignIssue.Kind.INVALID ? RED : color == RED ? RED : YELLOW; }
                }
                for (String k : pending.keySet()) { if (k.startsWith(key + ".")) { color = RED; } }
            }
            g.drawString(font, font.plainSubstrByWidth(pageLabel(key).getString(), LIST_W - 4), LIST_X + 2, y + 1, color, false);
        }
        g.disableScissor();
    }

    private void renderParameters(GuiGraphics g, int mx, int my, List<Component> tooltip) {
        int x0 = contentX(), y0 = TOP, bottom = height - 6;
        g.enableScissor(x0, y0, width - 6, bottom);
        for (Row r : rows) {
            int y = y0 + r.y() - scroll, x = x0 + r.column() * columnWidth;
            if (y + FIELD_H < y0 || y > bottom) { continue; }
            String name = r.parameter().name();
            DesignIssue.Kind kind = worst.get(page + "." + name);
            int color = pending.containsKey(page + "." + name) || kind == DesignIssue.Kind.INVALID ? RED : kind == DesignIssue.Kind.EXTRAPOLATION ? YELLOW : WHITE;
            g.drawString(font, font.plainSubstrByWidth(label(page, name).getString(), labelWidth), x, y + 3, color, false);
            if (r.parameter().type() != RecordEditor.Type.CHOICE && !r.parameter().unit().isEmpty()) {
                g.drawString(font, r.parameter().unit(), x + labelWidth + 4 + FIELD_W + 4, y + 3, GREY, false);
            }
            if (r.parameter().type() == RecordEditor.Type.COLOR) {
                // the colour's swatch (a blank module colour shows the body's main colour); clicking it steps through the palette
                int sx = x + labelWidth + 4 + FIELD_W + 4;
                g.fill(sx, y + 1, sx + SWATCH, y + FIELD_H - 1, 0xff000000);
                g.fill(sx + 1, y + 2, sx + SWATCH - 1, y + FIELD_H - 2, swatch(r));
                if (mx >= sx && mx < sx + SWATCH && my >= y && my < y + FIELD_H && my >= y0 && my < bottom) {
                    tooltip.add(Component.translatable("hdfv.screen.palette"));
                }
            }
            if (mx >= x && mx < x + labelWidth && my >= y && my < y + FIELD_H && my >= y0 && my < bottom) {
                tooltip.add(label(page, name));
                String hint = "hdfv.param." + langPage(page) + "." + name + ".hint";
                if (I18n.exists(hint)) { tooltip.add(Component.translatable(hint).withStyle(ChatFormatting.GRAY)); }
                for (DesignIssue i : issues) {
                    Designs.Place p = Designs.place(design, i);
                    if (page.equals(p.page()) && name.equals(p.parameter())) { tooltip.add(issueText(i, p)); }
                }
            }
        }
        // this page's issues: under the 3D view, or beside the parameters when there is room for them, else below
        int columns = rows.isEmpty() ? 1 : rows.get(rows.size() - 1).column() + 1;
        int besideX = x0 + columns * columnWidth + 4, besideWidth = width - 6 - besideX;
        boolean beside = previewRect != null || besideWidth >= 150;
        int ix = beside ? besideX : x0, iw = beside ? besideWidth : width - x0 - 6;
        int y = previewRect != null ? previewRect[1] + previewRect[3] + 6 : (beside ? y0 : y0 + rowsHeight + 6) - scroll;
        List<DesignIssue> here = issues.stream().filter(i -> page.equals(Designs.place(design, i).page())).toList();
        int lines = 0;
        for (DesignIssue i : here) {
            for (FormattedCharSequence line : font.split(issueText(i, Designs.place(design, i)), iw)) {
                g.drawString(font, line, ix, y, i.kind() == DesignIssue.Kind.INVALID ? RED : YELLOW, false);
                y += 10;
                lines++;
            }
            y += 3;
        }
        g.disableScissor();
        int issueHeight = lines * 10 + here.size() * 3;
        contentHeight = previewRect != null ? rowsHeight : beside ? Math.max(rowsHeight, issueHeight) : rowsHeight + 6 + issueHeight;
    }

    private void renderOverview(GuiGraphics g) {
        int x0 = contentX(), y0 = TOP, bottom = height - 6, w = (previewRect != null ? previewRect[0] - 6 : width - 6) - x0;
        g.enableScissor(x0, y0, x0 + w, bottom);
        int y = y0 - scroll;
        for (Designs.Figure f : figures) {
            g.drawString(font, Component.translatable("hdfv.figure." + f.key()), x0, y, GREY, false);
            g.drawString(font, figureText(f), x0 + figureLabels + 10, y, WHITE, false);
            y += 10;
        }
        y += 6;
        if (target != Blueprints.Target.NEW) { g.drawString(font, Component.translatable("hdfv.screen.editing_held"), x0, y, BLUE, false); y += 12; }
        for (DesignIssue i : issues) {
            Designs.Place p = Designs.place(design, i);
            MutableComponent line = p.page() == null ? Component.empty() : pageLabel(p.page()).append(" › ");
            for (FormattedCharSequence s : font.split(line.append(issueText(i, p)), w)) {
                g.drawString(font, s, x0, y, i.kind() == DesignIssue.Kind.INVALID ? RED : YELLOW, false);
                y += 10;
            }
        }
        g.disableScissor();
        contentHeight = y + scroll - y0;
    }

    private void renderBench(GuiGraphics g) {
        int x0 = contentX(), w = width - x0 - 6, y = TOP + 20, bottom = height - 6;
        if (design instanceof dev.mcmod.hdfv.part.air.WingDesign || design instanceof BodyDesign) {
            g.drawString(font, Component.translatable("hdfv.bench.speed"), x0, TOP + 3, GREY, false);
            g.drawString(font, "m/s", x0 + 94, TOP + 3, GREY, false);
        }
        if (sweeps(design)) {
            g.drawString(font, Component.translatable("hdfv.bench.altitude"), x0, TOP + 3, GREY, false);
            g.drawString(font, "m", x0 + 94, TOP + 3, GREY, false);
        }
        if (flies(design)) {
            g.drawString(font, Component.translatable("hdfv.bench.altitude"), x0, TOP + 3, GREY, false);
            g.drawString(font, "m", x0 + 94, TOP + 3, GREY, false);
            g.drawString(font, Component.translatable("hdfv.bench.speed"), x0 + 104, TOP + 3, GREY, false);
            g.drawString(font, "m/s", x0 + 194, TOP + 3, GREY, false);
        }
        if (invalid()) { g.drawString(font, Component.translatable("hdfv.bench.invalid"), x0, y, RED, false); return; }
        if (running != null) {
            g.drawString(font, Component.translatable("hdfv.bench.running", (Util.getMillis() - runningSince) / 1000), x0, y, YELLOW, false);
            return;
        }
        if (benchError != null) { g.drawString(font, Component.translatable("hdfv.bench.failed", benchError), x0, y, RED, false); return; }
        if (result == null) { g.drawString(font, Component.translatable("hdfv.bench.hint"), x0, y, GREY, false); return; }
        if (resultFor != design) { g.drawString(font, Component.translatable("hdfv.bench.stale"), x0 + w - font.width(Component.translatable("hdfv.bench.stale")), TOP + 3, YELLOW, false); }
        if (result instanceof Bench.PistonCurve c) {
            if (c.peakTorque() == null) { g.drawString(font, Component.translatable("hdfv.bench.no_points"), x0, y, RED, false); return; }
            g.drawString(font, Component.translatable("hdfv.bench.peak_torque", Plot.number(c.peakTorque().torqueNm()), Plot.number(c.peakTorque().rpm())), x0, y, ORANGE, false);
            g.drawString(font, Component.translatable("hdfv.bench.peak_power", Plot.number(c.peakPower().powerW() / 1000), Plot.number(c.peakPower().rpm())), x0, y + 10, BLUE, false);
            long knocking = c.points().stream().filter(Bench.PistonPoint::knocking).count();
            g.drawString(font, Component.translatable("hdfv.bench.best_bsfc", Plot.number(c.bestBsfcGPerKWh()), knocking), x0, y + 20, GREY, false);
            int n = c.points().size();
            double[] rpm = new double[n], torque = new double[n], power = new double[n];
            for (int i = 0; i < n; i++) { Bench.PistonPoint p = c.points().get(i); rpm[i] = p.rpm(); torque[i] = p.torqueNm(); power[i] = p.powerW() / 1000; }
            Plot.draw(g, font, x0, y + 34, w, bottom - y - 34, rpm[0], rpm[n - 1], "rpm",
                    Component.translatable("hdfv.bench.torque").getString(), ORANGE, Component.translatable("hdfv.bench.power").getString(), BLUE,
                    List.of(new Plot.Series(rpm, torque, ORANGE, false), new Plot.Series(rpm, power, BLUE, true)));
        } else if (result instanceof JetBench j) {
            int last = j.throttle().length - 1;
            Bench.JetRun r = j.run();
            // thrust in kN, or in N for an engine under a kilonewton
            boolean small = r.fullThrustN() < 1000;
            double scale = small ? 1 : 1e-3;
            String thrustUnit = small ? "N" : "kN";
            for (FormattedCharSequence line : font.split(Component.translatable("hdfv.bench.full_throttle", Plot.number(j.altitudeM()), Plot.number(j.speedMps()),
                    Plot.number(j.thrustN()[last] * scale) + " " + thrustUnit, Plot.number(j.tsfc()[last]), Plot.number(j.turbineInletK()[last])), w)) {
                g.drawString(font, line, x0, y, ORANGE, false);
                y += 10;
            }
            for (FormattedCharSequence line : font.split(Component.translatable("hdfv.bench.start", seconds(r.lightOffS()), seconds(r.idleS()),
                    seconds(r.fullThrustS() - r.throttleS())), w)) {
                g.drawString(font, line, x0, y, BLUE, false);
                y += 10;
            }
            if (j.wet() != null) {
                g.drawString(font, Component.translatable("hdfv.bench.wet", Plot.number(j.wet().thrustN() * scale) + " " + thrustUnit,
                        Plot.number(j.wet().fuelKgPerS() / j.wet().thrustN() * 1e6)), x0, y, RED, false);
                y += 10;
            }
            int n = j.throttle().length, h = (bottom - y - 4) / 2;
            double[] throttle = new double[n], thrust = new double[n], tsfc = j.tsfc();
            for (int i = 0; i < n; i++) { throttle[i] = 100 * j.throttle()[i]; thrust[i] = j.thrustN()[i] * scale; }
            String thrustLabel = Component.translatable("hdfv.bench.thrust", thrustUnit).getString();
            Plot.draw(g, font, x0, y + 2, w, h - 2, 0, 100, Component.translatable("hdfv.bench.throttle").getString(),
                    thrustLabel, ORANGE, Component.translatable("hdfv.bench.tsfc").getString(), GREY,
                    List.of(new Plot.Series(throttle, thrust, ORANGE, false), new Plot.Series(throttle, tsfc, GREY, true)));
            double[] t = r.timeS(), speedPercent = new double[t.length], f = new double[t.length];
            for (int i = 0; i < t.length; i++) { speedPercent[i] = 100 * r.speedFraction()[i]; f[i] = r.thrustN()[i] * scale; }
            if (t.length > 1) {
                Plot.draw(g, font, x0, y + 2 + h, w, h, 0, t[t.length - 1], Component.translatable("hdfv.bench.time").getString(),
                        Component.translatable("hdfv.bench.spool").getString(), GREEN, thrustLabel, ORANGE,
                        List.of(new Plot.Series(t, speedPercent, GREEN, false), new Plot.Series(t, f, ORANGE, true)));
            }
        } else if (result instanceof MachBench b) {
            renderMachBench(g, b, x0, y, w, bottom);
        } else if (result instanceof Bench.PulseBench b) {
            renderPulseBench(g, b, x0, y, w, bottom);
        } else if (result instanceof Bench.RocketBench b) {
            renderRocketBench(g, b, x0, y, w, bottom);
        } else if (result instanceof ShaftBench b) {
            renderShaftBench(g, b, x0, y, w, bottom);
        } else if (result instanceof Bench.WingCurve c) {
            renderWingBench(g, c, x0, y, w, bottom);
        } else if (result instanceof Bench.PropellerCurve c) {
            renderPropellerBench(g, c, x0, y, w, bottom);
        } else if (result instanceof Bench.RotorCurve c) {
            renderRotorBench(g, c, x0, y, w, bottom);
        } else if (result instanceof Bench.BodyBench b) {
            renderBodyBench(g, b, x0, y, w, bottom);
        } else if (result instanceof dev.mcmod.hdfv.design.WheelBench.AxleBench a) {
            renderAxleBench(g, a, x0, y, w, bottom);
        } else if (result instanceof dev.mcmod.hdfv.part.chassis.ChassisDesign.Analysis a) {
            renderChassisBench(g, a, x0, y, w, bottom);
        }
    }

    /**
     * 0.15: an axle's ride figures; its camber and toe over the travel (toe flat in the simple mode), and the wheel rate
     * with (hardpoints) the spring's motion ratio and the side-view angle of the contact patch's path.
     */
    private void renderAxleBench(GuiGraphics g, dev.mcmod.hdfv.design.WheelBench.AxleBench a, int x0, int y, int w, int bottom) {
        for (FormattedCharSequence line : font.split(Component.translatable("hdfv.bench.axle_summary", Plot.number(a.rideFrequencyHz()), Plot.number(a.dampingRatio()),
                Plot.number(a.rollStiffnessNmPerDeg()), Plot.number(a.rollCentreM() * 1000), Plot.number(a.designCompressionM() * 1000)), w)) {
            g.drawString(font, line, x0, y, ORANGE, false);
            y += 10;
        }
        if (a.axis() != null) {
            var s = a.axis();
            for (FormattedCharSequence line : font.split(Component.translatable("hdfv.bench.axle_axis", Plot.number(Math.toDegrees(s.casterRad())),
                    Plot.number(Math.toDegrees(s.kingpinRad())), Plot.number(s.scrubM() * 1000), Plot.number(s.trailM() * 1000)), w)) {
                g.drawString(font, line, x0, y, BLUE, false);
                y += 10;
            }
        }
        String mm = Component.translatable("hdfv.bench.compression").getString();
        int h = (bottom - y - 4) / 2;
        boolean table = !a.kinematics().isEmpty();
        int n = table ? a.kinematics().size() : a.travel().size();
        double[] c = new double[n], camber = new double[n], toe = new double[n];
        for (int i = 0; i < n; i++) {
            if (table) { var k = a.kinematics().get(i); c[i] = k.compressionM() * 1000; camber[i] = k.camberDeg(); toe[i] = k.toeDeg(); }
            else { var t = a.travel().get(i); c[i] = t.compressionM() * 1000; camber[i] = t.camberDeg(); toe[i] = 0; }
        }
        // the toe only from hardpoints (the simple mode has none to change)
        Plot.draw(g, font, x0, y + 2, w, h - 2, c[0], c[n - 1], mm, Component.translatable("hdfv.bench.camber").getString(), ORANGE,
                table ? Component.translatable("hdfv.bench.toe").getString() : "", BLUE,
                table ? List.of(new Plot.Series(c, camber, ORANGE, false), new Plot.Series(c, toe, BLUE, true)) : List.of(new Plot.Series(c, camber, ORANGE, false)));
        int m = a.travel().size();
        double[] tc = new double[m], rate = new double[m];
        for (int i = 0; i < m; i++) { tc[i] = a.travel().get(i).compressionM() * 1000; rate[i] = a.travel().get(i).rateNPerM() / 1000; }
        List<Plot.Series> lower = new ArrayList<>(List.of(new Plot.Series(tc, rate, GREEN, false)));
        String right = "";
        if (table) {
            double[] angle = new double[n];
            for (int i = 0; i < n; i++) { angle[i] = a.kinematics().get(i).sideAngleDeg(); }
            lower.add(new Plot.Series(c, angle, GREY, true));
            right = Component.translatable("hdfv.bench.side_angle").getString();
        }
        Plot.draw(g, font, x0, y + 2 + h, w, h, tc[0], tc[m - 1], mm, Component.translatable("hdfv.bench.wheel_rate").getString(), GREEN, right, GREY, lower);
    }

    /**
     * 0.15: a chassis's mass, twist and bending stiffness and its worst member at 1.5 times the standard cases; each
     * member's usage along the frame (rails and the rest apart), against the 100 % line.
     */
    private void renderChassisBench(GuiGraphics g, dev.mcmod.hdfv.part.chassis.ChassisDesign.Analysis a, int x0, int y, int w, int bottom) {
        if (a.failure() != null) { g.drawString(font, Component.translatable("hdfv.bench.failed", a.failure()), x0, y, RED, false); return; }
        String worst = "-";
        if (a.worstMember() >= 0) {
            var mem = a.layout().members.get(a.worstMember());
            worst = Component.translatable("hdfv.bench.member." + mem.role().name().toLowerCase(java.util.Locale.ROOT), mem.station() + 1).getString();
        }
        Component summary = Component.translatable("hdfv.bench.chassis_summary", Plot.number(a.massKg()), Plot.number(a.endTwistNmPerDeg() / 1000),
                Double.isFinite(a.bendingNPerM()) ? Plot.number(a.bendingNPerM() / 1e6) : "-", Plot.number(100 * a.usage()), worst);
        for (FormattedCharSequence line : font.split(summary, w)) { g.drawString(font, line, x0, y, a.usage() > 1 ? RED : ORANGE, false); y += 10; }
        if (a.twist() != null && a.twist().length > 2) {
            StringBuilder pairs = new StringBuilder();
            for (int i = 0; i + 1 < a.twist().length; i++) { pairs.append(i == 0 ? "" : ", ").append(Plot.number(-a.twist()[i][i + 1] * Math.toRadians(1) / 1000)); }
            for (FormattedCharSequence line : font.split(Component.translatable("hdfv.bench.chassis_pairs", pairs.toString()), w)) { g.drawString(font, line, x0, y, BLUE, false); y += 10; }
        }
        var f = a.layout().frame;
        double length = 0;
        for (var p : f.nodes()) { length = Math.max(length, p.x()); }
        int bins = 40;
        double[] x = new double[bins + 1], rails = new double[bins + 1], others = new double[bins + 1], limit = new double[bins + 1];
        java.util.Arrays.fill(rails, Double.NaN);
        java.util.Arrays.fill(others, Double.NaN);
        for (int k = 0; k <= bins; k++) { x[k] = length * k / bins; limit[k] = 100; }
        for (int i = 0; i < f.memberCount(); i++) {
            var mem = f.member(i);
            double xa = f.position(mem.a()).x(), xb = f.position(mem.b()).x();
            int from = (int) Math.floor(Math.min(xa, xb) / length * bins), to = (int) Math.ceil(Math.max(xa, xb) / length * bins);
            double[] row = a.layout().members.get(i).role() == dev.mcmod.hdfv.look.ChassisLayout.Role.RAIL ? rails : others;
            for (int k = Math.max(0, from); k <= Math.min(bins, Math.max(to, from)); k++) {
                double u = 100 * a.memberUsage()[i];
                row[k] = Double.isNaN(row[k]) ? u : Math.max(row[k], u);
            }
        }
        Plot.draw(g, font, x0, y + 2, w, bottom - y - 4, 0, length, Component.translatable("hdfv.bench.along_frame").getString(),
                Component.translatable("hdfv.bench.member_usage").getString(), ORANGE, "", GREY,
                List.of(new Plot.Series(x, limit, RED, false), new Plot.Series(x, rails, ORANGE, false), new Plot.Series(x, others, BLUE, false)));
    }

    /** 0.10: thrust (kN) and specific impulse against Mach; a turbo-ramjet's bypass share below. */
    private void renderMachBench(GuiGraphics g, MachBench b, int x0, int y, int w, int bottom) {
        Bench.MachPoint best = null;
        double bestIsp = 0;
        for (Bench.MachPoint p : b.sweep()) {
            if (p.lit() && Double.isFinite(p.thrustN()) && (best == null || p.thrustN() > best.thrustN())) { best = p; }
            if (Double.isFinite(p.specificImpulseS())) { bestIsp = Math.max(bestIsp, p.specificImpulseS()); }
        }
        Component summary = best == null ? Component.translatable("hdfv.bench.mach_none", Plot.number(b.altitudeM()))
                : Component.translatable("hdfv.bench.mach_summary", Plot.number(b.altitudeM()), Plot.number(best.thrustN() / 1000), Plot.number(best.mach()), Plot.number(bestIsp));
        for (FormattedCharSequence line : font.split(summary, w)) { g.drawString(font, line, x0, y, ORANGE, false); y += 10; }
        int n = b.sweep().size();
        double[] mach = new double[n], thrust = new double[n], isp = new double[n], bypass = new double[n];
        for (int i = 0; i < n; i++) {
            Bench.MachPoint p = b.sweep().get(i);
            mach[i] = p.mach(); thrust[i] = p.thrustN() / 1000; isp[i] = p.specificImpulseS(); bypass[i] = 100 * p.bypassRatio();
        }
        int h = b.bypass() ? (bottom - y - 4) / 2 : bottom - y - 4;
        Plot.draw(g, font, x0, y + 2, w, h - 2, mach[0], mach[n - 1], Component.translatable("hdfv.bench.mach").getString(),
                Component.translatable("hdfv.bench.thrust", "kN").getString(), ORANGE, Component.translatable("hdfv.bench.isp").getString(), GREY,
                List.of(new Plot.Series(mach, thrust, ORANGE, false), new Plot.Series(mach, isp, GREY, true)));
        if (b.bypass()) {
            Plot.draw(g, font, x0, y + 2 + h, w, h, mach[0], mach[n - 1], Component.translatable("hdfv.bench.mach").getString(),
                    Component.translatable("hdfv.bench.bypass_share").getString(), GREEN, "", GREY, List.of(new Plot.Series(mach, bypass, GREEN, false)));
        }
    }

    /** 0.10: a pulsejet's thrust and frequency against airspeed above, one cycle of its chamber pressure at standstill below. */
    private void renderPulseBench(GuiGraphics g, Bench.PulseBench b, int x0, int y, int w, int bottom) {
        Bench.PulsePoint rest = b.sweep().get(0);
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double p : b.pressureTracePa()) { lo = Math.min(lo, p); hi = Math.max(hi, p); }
        Component summary = rest.running() ? Component.translatable("hdfv.bench.pulse_summary", Plot.number(b.altitudeM()), Plot.number(rest.thrustN()),
                Plot.number(b.frequencyHz()), Plot.number((hi - lo) / 2000)) : Component.translatable("hdfv.bench.pulse_dead", Plot.number(b.altitudeM()));
        for (FormattedCharSequence line : font.split(summary, w)) { g.drawString(font, line, x0, y, ORANGE, false); y += 10; }
        int n = b.sweep().size(), h = (bottom - y - 4) / 2;
        double[] v = new double[n], thrust = new double[n], hz = new double[n];
        for (int i = 0; i < n; i++) { Bench.PulsePoint p = b.sweep().get(i); v[i] = p.speedMps(); thrust[i] = p.thrustN(); hz[i] = p.running() ? p.frequencyHz() : Double.NaN; }
        Plot.draw(g, font, x0, y + 2, w, h - 2, 0, v[n - 1], Component.translatable("hdfv.bench.airspeed").getString(),
                Component.translatable("hdfv.bench.thrust", "N").getString(), ORANGE, Component.translatable("hdfv.bench.frequency").getString(), BLUE,
                List.of(new Plot.Series(v, thrust, ORANGE, false), new Plot.Series(v, hz, BLUE, true)));
        int m = b.pressureTracePa().length;
        double[] phase = new double[m], kpa = new double[m];
        for (int i = 0; i < m; i++) { phase[i] = 360.0 * i / (m - 1); kpa[i] = b.pressureTracePa()[i] / 1000; }
        Plot.draw(g, font, x0, y + 2 + h, w, h, 0, 360, Component.translatable("hdfv.bench.phase").getString(),
                Component.translatable("hdfv.bench.chamber_kpa").getString(), RED, "", GREY, List.of(new Plot.Series(phase, kpa, RED, false)));
    }

    /** 0.10: a booster's thrust and chamber pressure against time. */
    private void renderRocketBench(GuiGraphics g, Bench.RocketBench b, int x0, int y, int w, int bottom) {
        double maxPa = 0;
        for (double p : b.chamberPa()) { maxPa = Math.max(maxPa, p); }
        for (FormattedCharSequence line : font.split(Component.translatable("hdfv.bench.rocket_summary", Plot.number(b.altitudeM()), Plot.number(b.burnS()),
                Plot.number(b.totalImpulse() / 1000), Plot.number(b.specificImpulseS()), Plot.number(maxPa / 1e6)), w)) {
            g.drawString(font, line, x0, y, ORANGE, false);
            y += 10;
        }
        int n = b.timeS().length;
        double[] kn = new double[n], mpa = new double[n];
        for (int i = 0; i < n; i++) { kn[i] = b.thrustN()[i] / 1000; mpa[i] = b.chamberPa()[i] / 1e6; }
        Plot.draw(g, font, x0, y + 2, w, bottom - y - 4, 0, b.timeS()[n - 1], Component.translatable("hdfv.bench.time").getString(),
                Component.translatable("hdfv.bench.thrust", "kN").getString(), ORANGE, Component.translatable("hdfv.bench.chamber_mpa").getString(), RED,
                List.of(new Plot.Series(b.timeS(), kn, ORANGE, false), new Plot.Series(b.timeS(), mpa, RED, true)));
    }

    /** A shaft engine's bench: power and PSFC against throttle above; the free turbine's torque curve and the start and run-up side by side below. */
    private void renderShaftBench(GuiGraphics g, ShaftBench b, int x0, int y, int w, int bottom) {
        Bench.ShaftPoint full = b.sweep().get(b.sweep().size() - 1);
        Bench.ShaftRun r = b.run();
        for (FormattedCharSequence line : font.split(Component.translatable("hdfv.bench.shaft_full", Plot.number(b.altitudeM()), Plot.number(b.speedMps()),
                Plot.number(full.powerW() / 1000), Plot.number(full.psfcGPerKWh()), Plot.number(full.torqueNm()), Plot.number(full.point().tt4())), w)) {
            g.drawString(font, line, x0, y, BLUE, false);
            y += 10;
        }
        for (FormattedCharSequence line : font.split(Component.translatable("hdfv.bench.shaft_start", seconds(r.lightOffS()), seconds(r.idleS()),
                seconds(r.fullPowerS() - r.throttleS())), w)) {
            g.drawString(font, line, x0, y, GREEN, false);
            y += 10;
        }
        int n = b.sweep().size(), h = (bottom - y - 4) / 2;
        double[] throttle = new double[n], power = new double[n], psfc = new double[n];
        for (int i = 0; i < n; i++) {
            Bench.ShaftPoint p = b.sweep().get(i);
            // PSFC only where the engine gives a useful share of its power (it runs off to infinity towards idle)
            throttle[i] = 100 * p.throttle(); power[i] = p.powerW() / 1000; psfc[i] = p.powerW() > 0.05 * full.powerW() ? p.psfcGPerKWh() : Double.NaN;
        }
        String powerLabel = Component.translatable("hdfv.bench.power").getString();
        Plot.draw(g, font, x0, y + 2, w, h - 2, 0, 100, Component.translatable("hdfv.bench.throttle").getString(), powerLabel, BLUE,
                Component.translatable("hdfv.bench.psfc").getString(), GREY, List.of(new Plot.Series(throttle, power, BLUE, false), new Plot.Series(throttle, psfc, GREY, true)));
        int half = (w - 6) / 2;
        int m = b.torque().size();
        double[] speed = new double[m], torque = new double[m], shaft = new double[m];
        for (int i = 0; i < m; i++) { Bench.TorquePoint p = b.torque().get(i); speed[i] = 100 * p.outputSpeed(); torque[i] = p.torqueNm(); shaft[i] = p.powerW() / 1000; }
        Plot.draw(g, font, x0, y + 2 + h, half, h, 0, speed[m - 1], Component.translatable("hdfv.bench.output_speed").getString(),
                Component.translatable("hdfv.bench.torque").getString(), ORANGE, powerLabel, BLUE,
                List.of(new Plot.Series(speed, torque, ORANGE, false), new Plot.Series(speed, shaft, BLUE, true)));
        double[] t = r.timeS();
        if (t.length > 1) {
            double[] ng = new double[t.length], np = new double[t.length], kw = new double[t.length];
            for (int i = 0; i < t.length; i++) { ng[i] = 100 * r.gasGeneratorSpeed()[i]; np[i] = 100 * r.powerTurbineSpeed()[i]; kw[i] = r.powerW()[i] / 1000; }
            Plot.draw(g, font, x0 + half + 6, y + 2 + h, w - half - 6, h, 0, t[t.length - 1], Component.translatable("hdfv.bench.time").getString(),
                    Component.translatable("hdfv.bench.spool").getString(), GREEN, powerLabel, BLUE,
                    List.of(new Plot.Series(t, ng, GREEN, false), new Plot.Series(t, np, ORANGE, false), new Plot.Series(t, kw, BLUE, true)));
        }
    }

    /** A wing's lift (clean, and with its trailing-edge surface down) and drag against angle of attack. */
    private void renderWingBench(GuiGraphics g, Bench.WingCurve c, int x0, int y, int w, int bottom) {
        for (FormattedCharSequence line : font.split(Component.translatable("hdfv.bench.wing_summary", Plot.number(c.speedMps()), Plot.number(c.maximum().cl()),
                Plot.number(c.maximum().alphaDeg()), Plot.number(c.bestLiftOverDrag())), w)) {
            g.drawString(font, line, x0, y, ORANGE, false);
            y += 10;
        }
        if (c.deflected() != null) {
            g.drawString(font, Component.translatable("hdfv.bench.wing_flap", pageName("trailingEdge"), Plot.number(c.deflectedMaximum())), x0, y, BLUE, false);
            y += 10;
        }
        int n = c.clean().size();
        double[] a = new double[n], cl = new double[n], cd = new double[n], fl = new double[n];
        for (int i = 0; i < n; i++) {
            a[i] = c.clean().get(i).alphaDeg(); cl[i] = c.clean().get(i).cl(); cd[i] = c.clean().get(i).cd();
            fl[i] = c.deflected() == null ? Double.NaN : c.deflected().get(i).cl();
        }
        Plot.draw(g, font, x0, y + 2, w, bottom - y - 2, a[0], a[n - 1], Component.translatable("hdfv.bench.alpha").getString(),
                Component.translatable("hdfv.bench.lift_coefficient").getString(), ORANGE, Component.translatable("hdfv.bench.drag_coefficient").getString(), GREY,
                List.of(new Plot.Series(a, cl, ORANGE, false), new Plot.Series(a, fl, BLUE, false), new Plot.Series(a, cd, GREY, true)));
    }

    /** A propeller's efficiency (fine and coarse stops) and thrust coefficient against advance ratio. */
    private void renderPropellerBench(GuiGraphics g, Bench.PropellerCurve c, int x0, int y, int w, int bottom) {
        for (FormattedCharSequence line : font.split(Component.translatable("hdfv.bench.prop_summary", Plot.number(c.rpm()), Plot.number(c.staticThrustN() / 1000),
                Plot.number(c.staticPowerW() / 1000), Plot.number(Math.round(c.peakEfficiency() * 100) / 100.0), Plot.number(c.peakAdvanceRatio())), w)) {
            g.drawString(font, line, x0, y, ORANGE, false);
            y += 10;
        }
        int n = c.fine().size();
        double[] j = new double[n], eta = new double[n], ct = new double[n];
        for (int i = 0; i < n; i++) { j[i] = c.fine().get(i).advanceRatio(); eta[i] = 100 * c.fine().get(i).efficiency(); ct[i] = c.fine().get(i).ct(); }
        int m = c.coarse().size();
        double[] jc = new double[m], etac = new double[m];
        for (int i = 0; i < m; i++) { jc[i] = c.coarse().get(i).advanceRatio(); etac[i] = 100 * c.coarse().get(i).efficiency(); }
        double jMax = Math.max(n > 0 ? j[n - 1] : 1, m > 0 ? jc[m - 1] : 1);
        List<Plot.Series> series = new ArrayList<>(List.of(new Plot.Series(j, eta, ORANGE, false), new Plot.Series(j, ct, GREY, true)));
        if (m > 0) { series.add(new Plot.Series(jc, etac, BLUE, false)); }
        Plot.draw(g, font, x0, y + 2, w, bottom - y - 2, 0, jMax, Component.translatable("hdfv.bench.advance_ratio").getString(),
                Component.translatable("hdfv.bench.efficiency").getString(), ORANGE, Component.translatable("hdfv.bench.thrust_coefficient").getString(), GREY, series);
    }

    /** A rotor's hover thrust and power against collective. */
    private void renderRotorBench(GuiGraphics g, Bench.RotorCurve c, int x0, int y, int w, int bottom) {
        if (c.design() != null) {
            for (FormattedCharSequence line : font.split(Component.translatable("hdfv.bench.rotor_summary", Plot.number(c.rpm()), Plot.number(c.design().thrustN() / 1000),
                    Plot.number(c.design().powerW() / 1000), Plot.number(Math.round(c.design().figureOfMerit() * 100) / 100.0), Plot.number(c.design().collectiveDeg())), w)) {
                g.drawString(font, line, x0, y, ORANGE, false);
                y += 10;
            }
        }
        int n = c.points().size();
        double[] coll = new double[n], thrust = new double[n], power = new double[n];
        for (int i = 0; i < n; i++) { var p = c.points().get(i); coll[i] = p.collectiveDeg(); thrust[i] = p.thrustN() / 1000; power[i] = p.powerW() / 1000; }
        Plot.draw(g, font, x0, y + 2, w, bottom - y - 2, coll[0], coll[n - 1], Component.translatable("hdfv.bench.collective").getString(),
                Component.translatable("hdfv.bench.rotor_thrust").getString(), ORANGE, Component.translatable("hdfv.bench.rotor_power").getString(), BLUE,
                List.of(new Plot.Series(coll, thrust, ORANGE, false), new Plot.Series(coll, power, BLUE, true)));
    }

    /**
     * A body's bench: its drag at the speed; a car's drag power and lift against speed, otherwise lift, drag and moment
     * against angle of attack; on the water below, resistance and trim against speed beside the righting arms.
     */
    private void renderBodyBench(GuiGraphics g, Bench.BodyBench b, int x0, int y, int w, int bottom) {
        for (FormattedCharSequence line : font.split(Component.translatable("hdfv.bench.body_summary", Plot.number(b.speedMps()), Plot.number(round(b.dragAreaM2(), 3)),
                Plot.number(round(b.dragCoefficient(), 3)), Plot.number(Math.round(b.massKg()))), w)) {
            g.drawString(font, line, x0, y, ORANGE, false);
            y += 10;
        }
        boolean car = b.purpose() == dev.mcmod.hdfv.part.body.BodySettingsDesign.Purpose.CAR;
        if (car) {
            Bench.BodyRoadPoint at = b.level().stream().min(java.util.Comparator.comparingDouble(p -> Math.abs(p.speedMps() - b.speedMps()))).orElseThrow();
            g.drawString(font, Component.translatable("hdfv.bench.body_road", Plot.number(at.speedMps()), Plot.number(Math.round(at.dragN())),
                    Plot.number(round(at.powerW() / 1000, 1)), Plot.number(Math.round(at.liftN()))), x0, y, BLUE, false);
            y += 10;
        }
        var water = b.water();
        if (water != null && !water.isEmpty()) {
            var last = water.get(water.size() - 1);
            var top = last.lost() && water.size() > 1 ? water.get(water.size() - 2) : last;
            var hump = b.hump();
            Component text = hump != null
                    ? Component.translatable("hdfv.bench.body_water", Plot.number(round(b.draftM(), 2)), Plot.number(Math.round(hump.resistanceN())),
                            Plot.number(round(hump.speedMps(), 1)), Plot.number(round(top.speedMps(), 1)), Plot.number(Math.round(top.resistanceN())),
                            Plot.number(round(top.trimDeg(), 1)))
                    : Component.translatable("hdfv.bench.body_water_plain", Plot.number(round(b.draftM(), 2)), Plot.number(round(top.speedMps(), 1)),
                            Plot.number(Math.round(top.resistanceN())), Plot.number(round(top.trimDeg(), 1)));
            for (FormattedCharSequence line : font.split(text, w)) { g.drawString(font, line, x0, y, BLUE, false); y += 10; }
            if (last.lost()) {
                for (FormattedCharSequence line : font.split(Component.translatable("hdfv.bench.body_lost", Plot.number(round(last.speedMps(), 1))), w)) {
                    g.drawString(font, line, x0, y, RED, false);
                    y += 10;
                }
            } else if (water.stream().anyMatch(r -> r.trimDeg() < -0.5)) {
                for (FormattedCharSequence line : font.split(Component.translatable("hdfv.bench.body_bow_down"), w)) { g.drawString(font, line, x0, y, YELLOW, false); y += 10; }
            }
        }
        int h = water != null ? (bottom - y - 4) / 2 : bottom - y - 2;
        if (car) {
            int n = b.level().size();
            double[] v = new double[n], kw = new double[n], lift = new double[n];
            for (int i = 0; i < n; i++) { var p = b.level().get(i); v[i] = p.speedMps(); kw[i] = p.powerW() / 1000; lift[i] = p.liftN(); }
            Plot.draw(g, font, x0, y + 2, w, h - 2, v[0], v[n - 1], Component.translatable("hdfv.bench.speed_axis").getString(),
                    Component.translatable("hdfv.bench.drag_power").getString(), BLUE, Component.translatable("hdfv.bench.body_lift").getString(), GREY,
                    List.of(new Plot.Series(v, kw, BLUE, false), new Plot.Series(v, lift, GREY, true)));
        } else {
            int n = b.air().size();
            double[] a = new double[n], lift = new double[n], drag = new double[n], moment = new double[n];
            for (int i = 0; i < n; i++) { var p = b.air().get(i); a[i] = p.alphaDeg(); lift[i] = p.liftN(); drag[i] = p.dragN(); moment[i] = p.momentNm(); }
            Plot.draw(g, font, x0, y + 2, w, h - 2, a[0], a[n - 1], Component.translatable("hdfv.bench.alpha").getString(),
                    Component.translatable("hdfv.bench.body_force").getString(), ORANGE, Component.translatable("hdfv.bench.pitching_moment").getString(), GREY,
                    List.of(new Plot.Series(a, lift, ORANGE, false), new Plot.Series(a, drag, BLUE, false), new Plot.Series(a, moment, GREY, true)));
        }
        if (water == null) { return; }
        int half = (w - 6) / 2, m = 0;
        for (var r : water) { if (!r.lost()) { m++; } }
        if (m > 1) {
            double[] v = new double[m], res = new double[m], trim = new double[m];
            int i = 0;
            for (var r : water) { if (!r.lost()) { v[i] = r.speedMps(); res[i] = r.resistanceN(); trim[i] = r.trimDeg(); i++; } }
            Plot.draw(g, font, x0, y + 2 + h, half, h, 0, v[m - 1], Component.translatable("hdfv.bench.speed_axis").getString(),
                    Component.translatable("hdfv.bench.resistance").getString(), ORANGE, Component.translatable("hdfv.bench.trim").getString(), BLUE,
                    List.of(new Plot.Series(v, res, ORANGE, false), new Plot.Series(v, trim, BLUE, true)));
        }
        int k = b.heel().size();
        double[] heel = new double[k], gz = new double[k];
        for (int i = 0; i < k; i++) { heel[i] = b.heel().get(i).heelDeg(); gz[i] = round(b.heel().get(i).gzM(), 4); }
        Plot.draw(g, font, x0 + half + 6, y + 2 + h, w - half - 6, h, 0, 90, Component.translatable("hdfv.bench.heel").getString(),
                Component.translatable("hdfv.bench.righting_arm").getString(), GREEN, "", GREY, List.of(new Plot.Series(heel, gz, GREEN, false)));
    }

    private static double round(double v, int places) { double f = Math.pow(10, places); return Math.round(v * f) / f; }

    private static String seconds(double s) { return Double.isFinite(s) ? Plot.number(Math.round(s * 10) / 10.0) : "—"; }

    // ---------------------------------------------------------------- names

    static MutableComponent pageName(String page) {
        return page.equals(OVERVIEW) || page.equals(BENCH) ? Component.translatable("hdfv.screen." + page)
                : Component.translatableWithFallback("hdfv.page." + page, page);
    }

    static MutableComponent label(String page, String parameter) {
        return Component.translatableWithFallback("hdfv.param." + langPage(page) + "." + parameter, parameter);
    }

    /** The page translations come under: every item page of a list under the list's name ("module", 0.15 "station"). */
    static String langPage(String page) {
        String list = dev.mcmod.hdfv.design.ListedDesign.listOf(page);
        return list != null ? list : page;
    }

    /** A page's name in the list: a listed design's item pages as their number and kind ("3. 客艙"). */
    private MutableComponent pageLabel(String page) {
        if (design instanceof dev.mcmod.hdfv.design.ListedDesign l) {
            int i = l.listIndex(page);
            if (i >= 0 && i < l.items()) { return Component.literal((i + 1) + ". ").append(Component.translatable(l.kindKey(l.itemKind(i)))); }
        }
        return pageName(page);
    }

    /**
     * A choice as shown: enum values, standard materials and fuels translated (hdfv.choice.&lt;page&gt;.&lt;parameter&gt;.&lt;value&gt;
     * where a value means something else for one parameter, else hdfv.choice.&lt;value&gt;); oil grades and custom materials as they are.
     */
    static Component choice(String page, RecordEditor.Parameter p, String value) {
        if (p.name().equals("oilGrade")) { return Component.literal(value); }
        if (p.value() instanceof EngineMaterial && EngineMaterial.STANDARD.stream().noneMatch(m -> m.name().equals(value))) { return Component.literal(value); }
        String specific = "hdfv.choice." + langPage(page) + "." + p.name() + "." + value;
        return Component.translatable(I18n.exists(specific) ? specific : "hdfv.choice." + value);
    }

    private MutableComponent issueText(DesignIssue i, Designs.Place p) {
        Component name = p.parameter() != null ? label(p.page(), p.parameter())
                : Component.translatableWithFallback("hdfv.derived." + i.parameter().substring(i.parameter().lastIndexOf('.') + 1), i.parameter());
        if (i.kind() == DesignIssue.Kind.EXTRAPOLATION) {
            RecordEditor.Display d = RecordEditor.display(i.parameter().substring(i.parameter().lastIndexOf('.') + 1));
            String unit = d.unit().isEmpty() ? "" : " " + d.unit();
            return Component.translatable("hdfv.issue.extrapolation", name, Plot.number(d.of(i.value())) + unit, Plot.number(d.of(i.min())),
                    Plot.number(d.of(i.max())) + unit, i.source());
        }
        return Component.translatable("hdfv.issue.invalid", name, i.message());
    }

    @Override public void onClose() {
        lastPage = page;
        super.onClose();
    }

    // ---------------------------------------------------------------- development hooks (DevCommands)

    String page() { return page; }
    void devCutaway(boolean on) { preview.cutaway = on; rebuildWidgets(); }
    void devDoors(boolean open) { preview.doorsOpen = open; rebuildWidgets(); }
    void devEnlarge(boolean on) { enlarged = on; rebuildWidgets(); }
    void devView(float yaw, float pitch) { preview.hold(yaw, pitch); }
    void devZoom(double factor) { preview.zoomTo(factor); }
    void devFocus(dev.mcmod.aerocore.api.Vec3d point) { preview.focus(point); }
    int devQuads() { return preview.size(); }
    dev.mcmod.aerocore.api.Vec3d[] devBounds() { return preview.bounds(); }
    PartDesign devDesign() { return design; }
    boolean devCutawayOn() { return preview.cutaway; }
    void devOpen(String p) { if (entries().contains(p)) { open(p); } }

    /** Development: scrolls the parameters to the bottom (AutoShot cannot turn the mouse wheel). */
    void devScrollToEnd() { scroll = Math.max(0, contentHeight - (height - TOP - 6)); layout(); }
    void devSet(String p, String parameter, String text) {
        String was = page;
        page = p;
        edit(parameter, text, null);
        page = was;
        rebuildWidgets();
    }
}
