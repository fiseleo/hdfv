package dev.mcmod.hdfv.neoforge.client;

import com.mojang.math.Axis;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.drive.CarKit;
import dev.mcmod.hdfv.drive.Dashboard;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvClientConfig;
import dev.mcmod.hdfv.neoforge.fuel.Fuelling;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.HashMap;
import java.util.Map;

/**
 * A car's dashboard (0.14): while the player drives a car, at the bottom right (above the hotbar where the screen is
 * narrow), a tachometer and a speedometer (round dials, tools/hud.py's face; the scale, numbers and needles drawn here
 * so they fit each car: the engine's red line, the speed its gearing reaches), the gear between them and the warning
 * lamps with the fuel under them. What is on it is Dashboard's; the fuel gauge (FuelGauge) gives way to it.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DashboardHud {
    private DashboardHud() {}

    private static final ResourceLocation DIAL = ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "textures/gui/hud/dial.png"),
            LAMPS = ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "textures/gui/hud/lamps.png");
    /** The dials' size, the gear box between them, the gaps and the lamp row, GUI pixels. */
    private static final int D = 64, BOX = 30, GAP = 5, ROW = 15, WIDTH = 2 * D + BOX + 2 * GAP, HEIGHT = D + ROW;
    /** The needle's sweep: from 150 degrees (lower left, screen angles, y down) clockwise through 240. */
    private static final float START = 150, SWEEP = 240;
    private static final int ICON_HAND = 0, ICON_FUEL = 1, ICON_ENGINE = 2, ICON_LOCK = 3;

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "dashboard"), DashboardHud::render);
    }

    /** Whether the dashboard is up (FuelGauge then draws nothing). */
    public static boolean shown(Minecraft mc) { return HdfvClientConfig.dashboard() && !mc.options.hideGui && DriveControls.car(mc) != null; }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!shown(mc)) { return; }
        PartVehicle v = DriveControls.car(mc);
        Vehicle compiled = v.vehicle();
        if (compiled == null) { return; }
        CarKit kit = DriveControls.kit(v);
        float pt = delta.getGameTimeDeltaPartialTick(false);
        Map<String, Double> controls = new HashMap<>(v.controls());
        controls.putAll(DriveControls.sent(v));
        double rpm = Double.NaN;
        boolean running = true;
        if (kit.engine() != null && compiled.installed(kit.engine()) != null) {
            rpm = v.rotorSpeed(compiled.installed(kit.engine()).firstRotor()) * 30 / Math.PI;
            if (kit.piston()) { running = v.shown(kit.engine(), PistonEngineDesign.SHOWN_RUNNING, pt) > 0.5; }
        }
        Dashboard.Box box = kit.gearboxId() == null ? null
                : new Dashboard.Box(v.shown(kit.gearboxId(), 0, 1), v.shown(kit.gearboxId(), 1, 1), v.shown(kit.gearboxId(), 3, 1));
        Dashboard.Transfer transfer = kit.transferId() == null ? null : new Dashboard.Transfer(v.shown(kit.transferId(), 0, 1), v.shown(kit.transferId(), 1, 1));
        double capacity = Fuelling.capacityLitres(v), share = capacity > 0 ? Math.clamp(Fuelling.litres(v) / capacity, 0, 1) : -1;
        Dashboard.Face face = Dashboard.of(kit, new Dashboard.Readings(DriveControls.speed(v), Double.isNaN(rpm) ? 0 : rpm, box, transfer, controls, share, running),
                HdfvClientConfig.mph());

        // where: the bottom right, above the hotbar when it would be in the way; smaller on a narrow screen
        int w = g.guiWidth(), h = g.guiHeight();
        float scale = Math.min(1f, (w - 12f) / WIDTH);
        float x0 = w - 6 - WIDTH * scale, y0 = h - 4 - HEIGHT * scale;
        if (x0 < w / 2f + 91 + 4) { y0 = h - 52 - HEIGHT * scale; }
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x0, y0, 0);
        pose.scale(scale, scale, 1);
        Font font = mc.font;
        long ticks = mc.level == null ? 0 : mc.level.getGameTime();
        boolean blink = ticks / 5 % 2 == 0;
        turnOn = dev.mcmod.hdfv.look.LampRules.blinkOn(dev.mcmod.hdfv.neoforge.client.model.VehicleLamps.inputs(v, pt));

        // the tachometer
        if (kit.engine() != null && kit.fullScaleRpm() > 0) {
            double full = kit.fullScaleRpm(), red = kit.redlineRpm();
            dial(g, 0, 0);
            for (int r = 0; r <= full + 1e-6; r += 250) {
                boolean major = r % 1000 == 0, half = r % 500 == 0;
                if (!major && !half && r < red) { continue; }
                int colour = r >= red ? 0xFFE5534B : 0xFFE8E8E8;
                tick(g, D / 2f, D / 2f, angle(r / full), major ? 22.5f : 25.5f, 28.5f, major ? 1.0f : 0.6f, colour);
                if (major) { label(g, font, D / 2f, D / 2f, angle(r / full), 17, Integer.toString(r / 1000), colour); }
            }
            small(g, font, D / 2f, D / 2f + 12, "x1000 r/min", 0xFF9AA0A6);
            needle(g, D / 2f, D / 2f, angle(Math.clamp(face.rpm() / full, 0, 1.02)));
        }
        // the gear between the dials
        float bx = D + GAP, by = 12;
        g.fill((int) bx, (int) by, (int) bx + BOX, (int) by + 38, 0xD0101418);
        g.renderOutline((int) bx, (int) by, BOX, 38, 0xFF3A4048);
        Dashboard.Gear gear = face.gear();
        if (!gear.letters().isEmpty()) {
            String letters = gear.letters();
            float lw = font.width(letters) * 0.6f;
            pose.pushPose();
            pose.translate(bx + BOX / 2f - lw / 2, by + 3, 0);
            pose.scale(0.6f, 0.6f, 1);
            int cx = 0;
            for (int i = 0; i < letters.length(); i++) {
                String c = letters.substring(i, i + 1);
                g.drawString(font, c, cx, 0, i == gear.lit() ? 0xFFFFC857 : 0xFF5C636B, false);
                cx += font.width(c);
            }
            pose.popPose();
        }
        if (!gear.text().isEmpty()) {
            int colour = gear.grinding() ? (blink ? 0xFFE5534B : 0xFF5C2020) : gear.changing() ? 0xFF8A9099 : 0xFFFFFFFF;
            float s = gear.text().length() > 2 ? 1.4f : 2f;
            pose.pushPose();
            pose.translate(bx + BOX / 2f, by + 22, 0);
            pose.scale(s, s, 1);
            g.drawString(font, gear.text(), -font.width(gear.text()) / 2, -4, colour, true);
            pose.popPose();
        }
        // the speedometer
        float sx = D + GAP + BOX + GAP;
        int top = face.speedScale(), step = top > 200 ? 40 : 20;
        dial(g, sx, 0);
        for (int s = 0; s <= top; s += step / 2) {
            boolean major = s % step == 0;
            tick(g, sx + D / 2f, D / 2f, angle((double) s / top), major ? 22.5f : 25.5f, 28.5f, major ? 1.0f : 0.6f, 0xFFE8E8E8);
            if (major) { label(g, font, sx + D / 2f, D / 2f, angle((double) s / top), 17, Integer.toString(s), 0xFFE8E8E8); }
        }
        String speed = Long.toString(Math.round(face.speed()));
        g.drawString(font, speed, (int) (sx + D / 2f - font.width(speed) / 2f), D / 2 + 7, 0xFFFFFFFF, true);
        small(g, font, sx + D / 2f, D / 2f + 18, face.speedUnit(), 0xFF9AA0A6);
        needle(g, sx + D / 2f, D / 2f, angle(Math.clamp(face.speed() / top, 0, 1.02)));

        // the lamps, then the fuel
        int lx = 2, ly = D + 2;
        for (Dashboard.Lit lit : face.lamps()) {
            if (lit.blinking() && !blink) { lx += lampWidth(font, lit); continue; }
            lx += lamp(g, font, lit, lx, ly);
        }
        // 0.25: what is damaged, above the dials, only while something is wrong
        dev.mcmod.hdfv.neoforge.damage.VehicleConditions.Warnings warnings = dev.mcmod.hdfv.neoforge.damage.VehicleConditions.warnings(v);
        if (warnings.any()) { warnings(g, font, warnings, blink); }
        if (face.fuelShare() >= 0) {
            int fw = 40, fx = (int) (sx + D - fw), fy = ly + 5;
            icon(g, ICON_FUEL, fx - 14, ly, face.fuelShare() < Dashboard.LOW_FUEL ? 0xFFE3B341 : 0xFF9AA0A6);
            g.fill(fx - 1, fy - 1, fx + fw + 1, fy + 4, 0xC0000000);
            int colour = face.fuelShare() > 0.25 ? 0xFF3FB950 : face.fuelShare() > 0.1 ? 0xFFE3B341 : 0xFFE5534B;
            g.fill(fx, fy, fx + (int) Math.round(fw * face.fuelShare()), fy + 3, colour);
        }
        // 0.25: the cargo aboard under the fuel, when there is any (red when a hold carries more than it is rated for)
        double cargo = 0;
        boolean over = false;
        var holding = v.vehicle();
        for (int k = 0; k < v.holdCount(); k++) {
            double kg = v.holdKg(k);
            cargo += kg;
            if (holding != null) { over |= dev.mcmod.hdfv.storage.CargoRules.overloaded(kg, dev.mcmod.hdfv.neoforge.storage.VehicleCargo.limitKg(holding, k)); }
        }
        if (cargo >= 1) {
            small(g, font, sx + D - 20, ly + 12, net.minecraft.network.chat.Component.translatable("hdfv.drive.cargo", dev.mcmod.hdfv.storage.CargoMass.text(cargo)).getString(),
                    over ? 0xFFE5534B : 0xFF9AA0A6);
        }
        pose.popPose();
    }

    /** A value's needle angle, degrees (screen: 0 right, 90 down). */
    private static float angle(double share) { return START + SWEEP * (float) share; }

    private static void dial(GuiGraphics g, float x, float y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.blit(DIAL, 0, 0, D, D, 0, 0, 128, 128, 128, 128);
        g.pose().popPose();
    }

    /** A tick from radius r0 to r1 at an angle, half a width either side, in GUI pixels (drawn at a quarter pixel). */
    private static void tick(GuiGraphics g, float cx, float cy, float deg, float r0, float r1, float halfWidth, int colour) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(deg));
        pose.scale(0.25f, 0.25f, 1);
        g.fill(Math.round(r0 * 4), Math.round(-halfWidth * 4), Math.round(r1 * 4), Math.round(halfWidth * 4), colour);
        pose.popPose();
    }

    private static void needle(GuiGraphics g, float cx, float cy, float deg) {
        tick(g, cx, cy, deg, -5, 26, 0.9f, 0xFFFF4D2E);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(0.25f, 0.25f, 1);
        g.fill(-11, -11, 11, 11, 0xFF2A2E33);
        g.fill(-7, -7, 7, 7, 0xFF8A9099);
        pose.popPose();
    }

    private static void label(GuiGraphics g, Font font, float cx, float cy, float deg, float r, String text, int colour) {
        double a = Math.toRadians(deg);
        float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(0.5f, 0.5f, 1);
        g.drawString(font, text, -font.width(text) / 2, -4, colour, false);
        pose.popPose();
    }

    private static void small(GuiGraphics g, Font font, float cx, float y, String text, int colour) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, y, 0);
        pose.scale(0.5f, 0.5f, 1);
        g.drawString(font, text, -font.width(text) / 2, 0, colour, false);
        pose.popPose();
    }

    /** A lamp's icon (tools/hud.py: 12 px each, drawn 1:1), tinted. */
    private static void icon(GuiGraphics g, int index, int x, int y, int colour) {
        g.setColor(((colour >> 16) & 0xFF) / 255f, ((colour >> 8) & 0xFF) / 255f, (colour & 0xFF) / 255f, 1f);
        g.blit(LAMPS, x, y, 12, 12, 12 * index, 0, 12, 12, 60, 12);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** 0.19: whether the indicators' flash is lit this frame (in step with the lamps outside). */
    private static boolean turnOn;

    private static int lampWidth(Font font, Dashboard.Lit lit) {
        return switch (lit.lamp()) {
            case TURN_LEFT, TURN_RIGHT -> 11;
            case MODE, AUTO_CLUTCH, RIDE_HEIGHT, TRAILER -> (int) Math.ceil(font.width(label(lit)) * 0.75f) + 7;
            case CENTRE_LOCK, REAR_LOCK, FRONT_LOCK -> 19;
            default -> 14;
        };
    }

    private static String label(Dashboard.Lit lit) { return lit.lamp() == Dashboard.Lamp.AUTO_CLUTCH ? "A" : lit.label(); }

    /** One lamp at x, y; returns the room it took. */
    private static int lamp(GuiGraphics g, Font font, Dashboard.Lit lit, int x, int y) {
        int amber = 0xFFE3B341, red = 0xFFE5534B, green = 0xFF3FB950;
        switch (lit.lamp()) {
            case HANDBRAKE -> {
                icon(g, ICON_HAND, x, y, red);
                tiny(g, font, x + 6, y + 3, "P", red);
            }
            case MODE, AUTO_CLUTCH -> {
                // a text badge: the drive mode, or A for the automatic clutch
                int width = lampWidth(font, lit) - 2;
                g.fill(x, y + 1, x + width, y + 11, 0xC0101418);
                g.renderOutline(x, y + 1, width, 10, green);
                tiny(g, font, x + width / 2f, y + 3.5f, label(lit), green);
            }
            case CENTRE_LOCK, REAR_LOCK, FRONT_LOCK -> {
                icon(g, ICON_LOCK, x, y, amber);
                tiny(g, font, x + 15, y + 4, lit.label(), amber);
            }
            case RIDE_HEIGHT -> {
                // 0.15: the air suspension's level, a blue badge
                int width = lampWidth(font, lit) - 2, blue = 0xFF58A6FF;
                g.fill(x, y + 1, x + width, y + 11, 0xC0101418);
                g.renderOutline(x, y + 1, width, 10, blue);
                tiny(g, font, x + width / 2f, y + 3.5f, label(lit), blue);
            }
            case TRAILER -> {
                // 0.18: a trailer on the fifth wheel, an amber badge
                int width = lampWidth(font, lit) - 2;
                g.fill(x, y + 1, x + width, y + 11, 0xC0101418);
                g.renderOutline(x, y + 1, width, 10, amber);
                tiny(g, font, x + width / 2f, y + 3.5f, label(lit), amber);
            }
            case TURN_LEFT, TURN_RIGHT -> {
                // 0.19: a green arrow, flashing with the indicators
                if (turnOn) {
                    boolean left = lit.lamp() == Dashboard.Lamp.TURN_LEFT;
                    for (int i = 0; i < 5; i++) {
                        int col = left ? x + 1 + i : x + 9 - i;
                        g.fill(col, y + 6 - i, col + 1, y + 7 + i, green);
                    }
                    g.fill(left ? x + 5 : x + 2, y + 5, left ? x + 9 : x + 6, y + 8, green);
                }
            }
            case LOW_FUEL -> icon(g, ICON_FUEL, x, y, amber);
            case ENGINE -> icon(g, ICON_ENGINE, x, y, amber);
        }
        return lampWidth(font, lit);
    }

    /**
     * 0.25: the damage warnings, a row of badges over the dials from the left: the engine's lamp in red, then red badges
     * for the brakes, a flat tyre and a fuel leak (the leak's blinking), amber ones for the transmission and the structure.
     */
    private static void warnings(GuiGraphics g, Font font, dev.mcmod.hdfv.neoforge.damage.VehicleConditions.Warnings w, boolean blink) {
        int amber = 0xFFE3B341, red = 0xFFE5534B, x = 2, y = -13;
        if (w.engine()) {
            g.fill(x, y, x + 14, y + 12, 0xC0101418);
            icon(g, ICON_ENGINE, x + 1, y, red);
            x += 16;
        }
        x = badge(g, font, "hdfv.hud.warning.brakes", w.brakes(), red, x, y);
        x = badge(g, font, "hdfv.hud.warning.tyres", w.tyres(), red, x, y);
        x = badge(g, font, "hdfv.hud.warning.transmission", w.transmission(), amber, x, y);
        x = badge(g, font, "hdfv.hud.warning.structure", w.structure(), amber, x, y);
        badge(g, font, "hdfv.hud.warning.leak", w.leak() && blink, red, x, y);
    }

    /** One text badge when lit; returns where the next goes. */
    private static int badge(GuiGraphics g, Font font, String key, boolean lit, int colour, int x, int y) {
        if (!lit) { return x; }
        String text = net.minecraft.client.resources.language.I18n.get(key);
        int width = (int) Math.ceil(font.width(text) * 0.75f) + 6;
        g.fill(x, y + 1, x + width, y + 11, 0xC0101418);
        g.renderOutline(x, y + 1, width, 10, colour);
        tiny(g, font, x + width / 2f, y + 3.5f, text, colour);
        return x + width + 2;
    }

    private static void tiny(GuiGraphics g, Font font, float cx, float y, String text, int colour) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, y, 0);
        pose.scale(0.75f, 0.75f, 1);
        g.drawString(font, text, -font.width(text) / 2, 0, colour, false);
        pose.popPose();
    }
}
