package dev.mcmod.hdfv.assembly;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Binding;
import dev.mcmod.aerocore.api.vehicle.Connection;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.part.ControlSurfaceDesign;
import dev.mcmod.hdfv.part.MotorDesign;
import dev.mcmod.hdfv.part.PropellerDesign;
import dev.mcmod.hdfv.part.air.AirPropellerDesign;
import dev.mcmod.hdfv.part.air.RotorDesign;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.TransferCaseDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wires a vehicle laid out in an assembly room (0.7): shafts from what drives to what is driven, slipstreams, and
 * every input bound to the channel hdfv's keys set. What the blueprint already has is kept; the rest follows the rules
 * of hdfv's sample vehicles:
 * <ul>
 * <li>A car's drive line: engine to the gearbox's input, its output to a transfer case or the driven axles (propeller
 *     shafts, 0.99), a transfer case's front and rear outputs to the front- and rearmost driven axles; a motor with no
 *     gearbox straight to the nearest driven axle.</li>
 * <li>Otherwise each engine or motor turns the nearest propeller, screw or rotor, geared from its rated speed to the
 *     driven one's: a rotor's design speed, an air propeller's governed speed, a screw's at 35 m/s tip speed (small
 *     craft); a helicopter's main rotor through a freewheel. Rotors left undriven (a tail rotor) are geared from the
 *     nearest driven rotor. Each 6:1 of reduction is a gear stage and each turn of the shaft's axis a bevel stage, at
 *     0.99 a stage (a direct drive 1).</li>
 * <li>A control surface up to three diameters behind a propeller or screw, inside its disc, lies in its slipstream.</li>
 * <li>Inputs: an engine's throttle to "power" in an aircraft and "throttle" otherwise (ignition and starter left
 *     unbound: the engine runs); a vertical control surface's deflection to "rudder", a horizontal one's to "pitch"
 *     (reversed, as a tailplane's elevator); a wing's flap to "flap", aileron to "roll", elevator to "pitch" (reversed),
 *     rudder to "rudder"; a main rotor's collective, cyclic pitch (reversed) and roll to "collective", "pitch", "roll";
 *     a tail rotor's collective to "rudder" about the sample helicopter's trim (0.55 - 0.35 rudder); a propeller's
 *     governor left alone; wheels, gearboxes and transfer cases to channels of their roles' names, except (0.14) a
 *     transfer case's "lock" to "centre_lock" and an axle's "lock" to "diff_lock" (locked at 1, or at 2 for the
 *     frontmost of two or more lockers).</li>
 * </ul>
 */
public final class AutoWiring {
    private AutoWiring() {}

    /** Gear stages: up to 6:1 each; a stage's efficiency; a screw's tip speed, m/s. */
    public static final double STAGE_RATIO = 6, STAGE_EFFICIENCY = 0.99, SCREW_TIP_SPEED = 35, PROPSHAFT_EFFICIENCY = 0.99;
    /** The sample helicopter's tail rotor: collective = TAIL_TRIM + TAIL_GAIN * rudder. */
    public static final double TAIL_TRIM = 0.55, TAIL_GAIN = -0.35;

    /** A blueprint with every unconnected drive joined and every unbound input bound. */
    public static VehicleBlueprint wire(VehicleBlueprint draft) {
        Vehicle v = new VehicleBlueprint(draft.name(), unbound(draft.parts()), List.of()).compile();
        List<Vehicle.Installed> parts = v.installed();
        boolean aircraft = Room.of(draft) == Room.HANGAR;
        List<Connection> links = new ArrayList<>(draft.connections());
        Set<String> driven = new HashSet<>(), driving = new HashSet<>(), streamed = new HashSet<>();
        for (Connection c : links) {
            if (c instanceof Connection.Shaft s) { driving.add(s.from()); driven.add(s.to()); }
            else if (c instanceof Connection.Slipstream s) { streamed.add(s.surface()); }
        }

        // a car's drive line
        List<Vehicle.Installed> boxes = of(parts, p -> p.part().design() instanceof GearboxDesign);
        List<Vehicle.Installed> cases = of(parts, p -> p.part().design() instanceof TransferCaseDesign);
        List<Vehicle.Installed> axles = new ArrayList<>(of(parts, p -> p.part().design() instanceof AxleDesign && p.part().port("input") >= 0));
        axles.sort(Comparator.comparingDouble((Vehicle.Installed p) -> p.mount().position().x()).reversed());
        for (Vehicle.Installed engine : of(parts, p -> engine(p.part().design()))) {
            if (driving.contains(engine.id())) { continue; }
            Vehicle.Installed box = nearest(engine, boxes, driven);
            if (box == null) { continue; }
            links.add(Connection.Shaft.ports(engine.id(), null, box.id(), "input", 1, 1));
            driving.add(engine.id()); driven.add(box.id());
        }
        for (Vehicle.Installed box : boxes) {
            if (driving.contains(box.id())) { continue; }
            Vehicle.Installed tc = nearest(box, cases, driven);
            if (tc != null) {
                links.add(Connection.Shaft.ports(box.id(), "output", tc.id(), "input", 1, PROPSHAFT_EFFICIENCY));
                driven.add(tc.id());
                // 0.15: every free driven axle ahead of the case on its front output, behind it on its rear one (a tandem's
                // axles together on one: locked to each other); the frontmost on the front one when none is ahead
                List<Vehicle.Installed> free = of(axles, a -> !driven.contains(a.id()));
                double at = tc.mount().position().x();
                for (int k = 0; k < free.size(); k++) {
                    Vehicle.Installed a = free.get(k);
                    boolean front = a.mount().position().x() > at || (k == 0 && free.size() > 1 && free.stream().noneMatch(f -> f.mount().position().x() > at));
                    if (free.size() == 1) { front = true; }
                    links.add(Connection.Shaft.ports(tc.id(), front ? "front" : "rear", a.id(), "input", 1, PROPSHAFT_EFFICIENCY));
                    driven.add(a.id());
                }
            } else {
                for (Vehicle.Installed a : axles) {
                    if (driven.add(a.id())) { links.add(Connection.Shaft.ports(box.id(), "output", a.id(), "input", 1, PROPSHAFT_EFFICIENCY)); }
                }
            }
            driving.add(box.id());
        }

        // engines turning propellers, screws and rotors
        List<Vehicle.Installed> loads = of(parts, p -> load(p.part().design()) > 0);
        for (Vehicle.Installed engine : of(parts, p -> engine(p.part().design()))) {
            if (driving.contains(engine.id())) { continue; }
            Vehicle.Installed load = nearest(engine, loads, driven);
            if (load == null) { continue; }
            links.add(shaft(v, engine, load, rated(engine.part().design()), load.part().design() instanceof RotorDesign));
            driving.add(engine.id()); driven.add(load.id());
        }
        for (Vehicle.Installed engine : of(parts, p -> engine(p.part().design()))) {
            if (driving.contains(engine.id())) { continue; }
            Vehicle.Installed axle = nearest(engine, axles, driven);
            if (axle == null) { continue; }
            links.add(Connection.Shaft.ports(engine.id(), null, axle.id(), "input", 1, PROPSHAFT_EFFICIENCY));
            driving.add(engine.id()); driven.add(axle.id());
        }
        for (Vehicle.Installed rotor : of(parts, p -> p.part().design() instanceof RotorDesign)) {
            if (driven.contains(rotor.id())) { continue; }
            Vehicle.Installed from = nearest(rotor, of(parts, p -> p.part().design() instanceof RotorDesign && driven.contains(p.id())), Set.of());
            if (from == null) { continue; }
            links.add(shaft(v, from, rotor, load(from.part().design()), false));
            driven.add(rotor.id());
        }

        // 0.26: hydraulic pumps on the nearest engine's power take-off (1:1, as a pump flanged to the engine), the
        // travel motors to the nearest tracks' sprockets on their side (one a side; +z is right)
        for (Vehicle.Installed pump : of(parts, p -> p.part().design() instanceof dev.mcmod.hdfv.part.machine.HydraulicPumpDesign)) {
            if (driven.contains(pump.id())) { continue; }
            Vehicle.Installed engine = nearest(pump, of(parts, p -> engine(p.part().design())), Set.of());
            if (engine == null) { continue; }
            links.add(Connection.Shaft.ports(engine.id(), null, pump.id(), "input", 1, 1));
            driven.add(pump.id());
        }
        Set<String> sprockets = new HashSet<>();
        List<Vehicle.Installed> tracks = of(parts, p -> p.part().design() instanceof dev.mcmod.hdfv.part.machine.TrackDesign);
        for (Vehicle.Installed motor : of(parts, p -> p.part().design() instanceof dev.mcmod.hdfv.part.machine.HydraulicMotorDesign m && !m.winch())) {
            if (driving.contains(motor.id())) { continue; }
            Vehicle.Installed cutter = cutterOf(v, motor);
            if (cutter != null) {
                // a motor nearer a rotating tool than any track turns the tool's cutter
                links.add(Connection.Shaft.ports(motor.id(), "output", cutter.id(), "cutter", 1, 1));
                driving.add(motor.id());
                continue;
            }
            Vehicle.Installed track = nearest(motor, tracks, Set.of());
            if (track == null) { continue; }
            String side = motor.mount().position().z() > track.mount().position().z() ? "right" : "left";
            if (!sprockets.add(track.id() + "/" + side)) { continue; }
            links.add(Connection.Shaft.ports(motor.id(), "output", track.id(), side, 1, 1));
            driving.add(motor.id());
        }

        // slipstreams
        for (Vehicle.Installed prop : of(parts, p -> p.part().design() instanceof AirPropellerDesign || p.part().design() instanceof PropellerDesign)) {
            Vehicle.Rotor r = firstRotor(v, prop);
            if (r == null || Math.abs(r.axis().x()) < 0.7) { continue; }
            double d = diameter(prop.part().design());
            for (Vehicle.Installed s : of(parts, p -> p.part().design() instanceof ControlSurfaceDesign)) {
                if (streamed.contains(s.id())) { continue; }
                Vec3d rel = s.mount().position().subtract(r.position());
                double aft = -rel.x(), radial = Math.hypot(rel.y(), rel.z());
                if (aft > 0 && aft <= 3 * d && radial <= 0.5 * d) {
                    links.add(new Connection.Slipstream(prop.id(), s.id()));
                    streamed.add(s.id());
                }
            }
        }

        // bindings
        List<VehicleBlueprint.Entry> entries = new ArrayList<>();
        for (int i = 0; i < draft.parts().size(); i++) {
            VehicleBlueprint.Entry e = draft.parts().get(i);
            entries.add(e.bindings().isEmpty() ? new VehicleBlueprint.Entry(e.id(), e.design(), e.mount(), bindings(v, parts.get(i), aircraft)) : e);
        }
        return new VehicleBlueprint(draft.name(), entries, links);
    }

    /** The bindings hdfv's keys drive for an installed part (see the class description). */
    static List<Binding> bindings(Vehicle v, Vehicle.Installed p, boolean aircraft) {
        PartDesign d = p.part().design();
        List<Binding> out = new ArrayList<>();
        for (String role : p.part().inputs()) {
            Binding b = switch (d) {
                case ControlSurfaceDesign c -> Math.abs(p.rotation().rotate(Vec3d.Y).y()) > 0.7 ? Binding.of(role, "rudder") : new Binding(role, "pitch", -1, 0);
                case RotorDesign r -> {
                    Vehicle.Rotor rotor = firstRotor(v, p);
                    boolean main = rotor == null || Math.abs(rotor.axis().y()) > 0.7;
                    yield main ? switch (role) {
                        case "collective" -> Binding.of(role, "collective");
                        case "cyclicPitch" -> new Binding(role, "pitch", -1, 0);
                        case "cyclicRoll" -> Binding.of(role, "roll");
                        default -> null;
                    } : role.equals("collective") ? new Binding(role, "rudder", TAIL_GAIN, TAIL_TRIM) : null;
                }
                case AirPropellerDesign a -> null;
                // 0.26: heavy machinery (WorkInputs' channels)
                case dev.mcmod.hdfv.part.machine.LinkDesign l -> link(v, p, l, role);
                case dev.mcmod.hdfv.part.machine.WorkToolDesign w -> tool(p, w, role);
                case dev.mcmod.hdfv.part.machine.HydraulicMotorDesign m -> switch (role) {
                    case "command" -> m.winch() ? Binding.of(role, "hoist")
                            : cutterOf(v, p) != null ? Binding.of(role, toggle(cutterOf(v, p)))
                            : Binding.of(role, travelSide(v, p) > 0 ? "travel_right" : "travel_left");
                    case "brake" -> m.winch() ? Binding.of(role, "hoist_brake") : cutterOf(v, p) != null ? null : Binding.of(role, "travel_brake");
                    case "tension" -> Binding.of(role, dev.mcmod.aerocore.api.vehicle.Coupling.tensionChannel(p.id()));
                    default -> null;
                };
                case dev.mcmod.hdfv.part.machine.TrackDesign t -> switch (role) {
                    case "brake" -> Binding.of(role, "brake");
                    // a crawler on travel motors steers by them and is parked by their brakes; one on a transmission by its steering brakes
                    case "park" -> Binding.of(role, hydrostatic(v) ? "travel_brake" : "handbrake");
                    case "brake_left" -> hydrostatic(v) ? null : new Binding(role, "steer", 1, 0);
                    case "brake_right" -> hydrostatic(v) ? null : new Binding(role, "steer", -1, 0);
                    default -> null;
                };
                case dev.mcmod.hdfv.part.machine.DrumDesign r -> switch (role) {
                    case "brake" -> Binding.of(role, "brake");
                    case "park" -> Binding.of(role, hydrostatic(v) ? "travel_brake" : "handbrake");
                    case "vibration" -> Binding.of(role, "vibration");
                    default -> null;
                };
                // an outrigger's jack: its foot always held, down while "jacks" is on
                case dev.mcmod.hdfv.part.machine.OutriggerDesign o -> role.equals("brake") ? new Binding(role, "brake", 0, 1)
                        : role.equals("retract") ? new Binding(role, "jacks", -1, 1) : null;
                // 0.18: a landing leg's foot held by its brake (bound to 1 whatever the channel), drawn up on the landing gear's
                case dev.mcmod.hdfv.part.truck.LandingLegsDesign l -> role.equals("brake") ? new Binding(role, "brake", 0, 1)
                        : role.equals("retract") ? Binding.of(role, "landing_gear") : Binding.of(role, role);
                default -> switch (role) {
                    case "throttle" -> Binding.of(role, aircraft ? "power" : "throttle");
                    case "ignition", "starter" -> null;
                    case "flap" -> Binding.of(role, "flap");
                    case "aileron" -> Binding.of(role, "roll");
                    case "elevator" -> new Binding(role, "pitch", -1, 0);
                    case "rudder" -> Binding.of(role, "rudder");
                    // 0.12: a retractable leg folds on the landing gear's channel (0 down, 1 up)
                    case "retract" -> Binding.of(role, "landing_gear");
                    // 0.14: a transfer case's centre lock and the axles' lockers on channels of their own (they shared "lock");
                    // with two lockers or more the frontmost locks at the second step (rear first, then front)
                    case "lock" -> d instanceof TransferCaseDesign ? Binding.of(role, "centre_lock")
                            : d instanceof AxleDesign ? new Binding(role, "diff_lock", 1, frontLocker(v, p) ? -1 : 0) : Binding.of(role, role);
                    case "ride_height" -> new Binding(role, "ride_height", 1, 0.5);
                    default -> Binding.of(role, role);
                };
            };
            if (b != null) { out.add(b); }
        }
        return out;
    }

    /**
     * 0.26: a work link's input: its valve on its kind's channel (WorkInputs: a boom, a lattice boom and a loader's arm
     * on "boom", the loader's arm on "lift" when the machine also has a stick (a backhoe loader), a stick on "stick"
     * reversed (q up swings its tip out), a telescope, a mast, an outrigger beam, a slewing ring on "swing" (q up turns
     * left), a centre hinge on "steer" (q up turns left), the knuckle booms on "jib"); float only for a loader's arm;
     * a tool's "full" and "pushed" from the game's channels of the part ("<id>.full", "<id>.pushed").
     */
    static Binding link(Vehicle v, Vehicle.Installed p, dev.mcmod.hdfv.part.machine.LinkDesign l, String role) {
        return switch (role) {
            case "command" -> switch (l.linkKind()) {
                case BOOM, LATTICE -> Binding.of(role, "boom");
                case LOADER_ARM -> Binding.of(role, has(v, dev.mcmod.hdfv.part.machine.LinkDesign.LinkKind.STICK) ? "lift" : "boom");
                case STICK -> new Binding(role, "stick", -1, 0);
                case TELESCOPIC -> Binding.of(role, "telescope");
                case MAST -> Binding.of(role, "mast");
                case SLEW_RING -> Binding.of(role, "swing");
                case ARTICULATION -> Binding.of(role, "steer");
                case PLATFORM_BOOM, PLACING_BOOM -> Binding.of(role, "jib");
                case OUTRIGGER_BEAM -> Binding.of(role, "outriggers");
            };
            case "float" -> l.linkKind() == dev.mcmod.hdfv.part.machine.LinkDesign.LinkKind.LOADER_ARM ? Binding.of(role, "float") : null;
            case "full", "pushed" -> Binding.of(role, p.id() + "." + role);
            default -> null;
        };
    }

    /**
     * 0.26: a work tool's input: its ram on its kind's channel (a bucket's curl on "bucket", a clamshell's or a grapple's
     * jaws on "aux", a dozer blade's tilt on "blade_tilt", an angling blade's on "blade_angle", a grader's tip and a
     * conveyor's lift on "tilt", a ripper on "ripper", a scraper's bowl on "bowl", a dump body on "dump", a trencher's
     * boom on "boom", a platform's rotator on "rotate", an erector on "erector"); "full" and "pushed" from the game.
     */
    static Binding tool(Vehicle.Installed p, dev.mcmod.hdfv.part.machine.WorkToolDesign w, String role) {
        return switch (role) {
            case "command" -> switch (w.toolKind()) {
                case BACKHOE_BUCKET, TRENCH_BUCKET, LOADER_BUCKET, FACE_SHOVEL_BUCKET, QUICK_COUPLER -> Binding.of(role, "bucket");
                case CLAMSHELL, GRAPPLE -> Binding.of(role, "aux");
                case DOZER_BLADE -> Binding.of(role, w.bladeStyle() == dev.mcmod.hdfv.part.machine.WorkToolDesign.BladeStyle.ANGLE ? "blade_angle" : "blade_tilt");
                case SNOW_PLOW -> Binding.of(role, "blade_angle");
                case GRADER_MOLDBOARD, CONVEYOR -> Binding.of(role, "tilt");
                case RIPPER -> Binding.of(role, "ripper");
                case SCRAPER_BOWL -> Binding.of(role, "bowl");
                case DUMP_BODY -> Binding.of(role, "dump");
                case TRENCHER_CHAIN -> Binding.of(role, "boom");
                case WORK_PLATFORM -> Binding.of(role, "rotate");
                case SEGMENT_ERECTOR -> Binding.of(role, "erector");
                default -> Binding.of(role, "aux");
            };
            case "full", "pushed" -> Binding.of(role, p.id() + "." + role);
            default -> null;
        };
    }

    /** 0.26: the rotating tool a hydraulic motor turns: the nearest tool with a cutter when it is nearer than any track (null: it travels). */
    static Vehicle.Installed cutterOf(Vehicle v, Vehicle.Installed motor) {
        Vehicle.Installed tool = nearest(motor, of(v.installed(), i -> i.part().design() instanceof dev.mcmod.hdfv.part.machine.WorkToolDesign w && w.cutter() != null), Set.of());
        if (tool == null) { return null; }
        Vehicle.Installed track = nearest(motor, of(v.installed(), i -> i.part().design() instanceof dev.mcmod.hdfv.part.machine.TrackDesign), Set.of());
        if (track == null) { return tool; }
        return motor.mount().position().subtract(tool.mount().position()).length() < motor.mount().position().subtract(track.mount().position()).length() ? tool : null;
    }

    /** 0.26: the toggle channel that switches a rotating tool's drive (WorkInputs). */
    static String toggle(Vehicle.Installed tool) {
        return switch (((dev.mcmod.hdfv.part.machine.WorkToolDesign) tool.part().design()).toolKind()) {
            case BROOM -> "broom";
            case MIXER_DRUM -> "mixer";
            default -> "cutter";
        };
    }

    /** 0.26: whether a vehicle has a link of a kind. */
    static boolean has(Vehicle v, dev.mcmod.hdfv.part.machine.LinkDesign.LinkKind kind) {
        return v.installed().stream().anyMatch(i -> i.part().design() instanceof dev.mcmod.hdfv.part.machine.LinkDesign l && l.linkKind() == kind);
    }

    /** 0.26: whether a vehicle travels on hydraulic motors (it has a motor that is not a winch). */
    static boolean hydrostatic(Vehicle v) {
        return v.installed().stream().anyMatch(i -> i.part().design() instanceof dev.mcmod.hdfv.part.machine.HydraulicMotorDesign m && !m.winch());
    }

    /** 0.26: which side of its nearest track a travel motor is on: +1 right (+z), -1 left. */
    static int travelSide(Vehicle v, Vehicle.Installed motor) {
        Vehicle.Installed track = nearest(motor, of(v.installed(), i -> i.part().design() instanceof dev.mcmod.hdfv.part.machine.TrackDesign), Set.of());
        double z = track == null ? 0 : track.mount().position().z();
        return motor.mount().position().z() > z ? 1 : -1;
    }

    /** Whether an axle is the frontmost of two or more with a locking differential. */
    static boolean frontLocker(Vehicle v, Vehicle.Installed p) {
        List<Vehicle.Installed> lockers = of(v.installed(), a -> a.part().design() instanceof AxleDesign && a.part().inputs().contains("lock"));
        if (lockers.size() < 2) { return false; }
        Vehicle.Installed front = lockers.stream().max(Comparator.comparingDouble((Vehicle.Installed a) -> a.mount().position().x())).orElseThrow();
        return front.id().equals(p.id());
    }

    /** A shaft from a driver turning at rpm to a driven part, geared to its speed. */
    static Connection.Shaft shaft(Vehicle v, Vehicle.Installed from, Vehicle.Installed to, double rpm, boolean freewheel) {
        double ratio = rpm / load(to.part().design());
        Vehicle.Rotor a = firstRotor(v, from), b = firstRotor(v, to);
        boolean turned = a != null && b != null && Math.abs(a.axis().dot(b.axis())) < 0.9;
        if (Math.abs(ratio - 1) < 0.03) { ratio = 1; }
        int stages = (ratio == 1 ? 0 : Math.max(1, (int) Math.ceil(Math.abs(Math.log(Math.abs(ratio))) / Math.log(STAGE_RATIO) - 1e-9))) + (turned ? 1 : 0);
        return new Connection.Shaft(from.id(), to.id(), ratio, Math.pow(STAGE_EFFICIENCY, stages), freewheel);
    }

    static boolean engine(PartDesign d) { return d instanceof PistonEngineDesign || d instanceof ShaftTurbineDesign || d instanceof MotorDesign; }

    /** An engine's rated output speed, rpm: a piston engine at 85 % of its rev limit. */
    static double rated(PartDesign d) {
        return switch (d) {
            case PistonEngineDesign e -> 0.85 * e.ignition().revLimitRpm();
            case ShaftTurbineDesign t -> t.outputRpm();
            case MotorDesign m -> m.maxSpeedRpm();
            default -> 0;
        };
    }

    /** The speed a propulsor wants, rpm (0: not one). */
    static double load(PartDesign d) {
        return switch (d) {
            case RotorDesign r -> r.controls().designRpm();
            case AirPropellerDesign a -> a.hub().maxRpm();
            case PropellerDesign p -> SCREW_TIP_SPEED / (Math.PI * p.diameterM()) * 60;
            default -> 0;
        };
    }

    static double diameter(PartDesign d) {
        return switch (d) {
            case AirPropellerDesign a -> a.blades().diameterM();
            case PropellerDesign p -> p.diameterM();
            default -> 0;
        };
    }

    static Vehicle.Rotor firstRotor(Vehicle v, Vehicle.Installed p) {
        return p.firstRotor() >= 0 && p.firstRotor() < v.rotors().size() && v.rotors().get(p.firstRotor()).part() == p.index() ? v.rotors().get(p.firstRotor()) : null;
    }

    static Vehicle.Installed nearest(Vehicle.Installed from, List<Vehicle.Installed> among, Set<String> taken) {
        Vehicle.Installed best = null;
        double d = Double.POSITIVE_INFINITY;
        for (Vehicle.Installed p : among) {
            if (p == from || taken.contains(p.id())) { continue; }
            double x = p.mount().position().subtract(from.mount().position()).length();
            if (x < d) { d = x; best = p; }
        }
        return best;
    }

    static List<Vehicle.Installed> of(List<Vehicle.Installed> parts, java.util.function.Predicate<Vehicle.Installed> keep) {
        return parts.stream().filter(keep).toList();
    }

    static List<VehicleBlueprint.Entry> unbound(List<VehicleBlueprint.Entry> entries) {
        return entries.stream().map(e -> new VehicleBlueprint.Entry(e.id(), e.design(), e.mount())).toList();
    }

    /** A blueprint's parts and mounts alone (what an assembly room's layout holds before wiring). */
    public static VehicleBlueprint stripped(VehicleBlueprint b) { return new VehicleBlueprint(b.name(), unbound(b.parts()), List.of()); }

    /** Bindings by part id (for comparing wirings). */
    public static Map<String, List<Binding>> bindings(VehicleBlueprint b) {
        Map<String, List<Binding>> out = new java.util.LinkedHashMap<>();
        for (VehicleBlueprint.Entry e : b.parts()) { out.put(e.id(), e.bindings()); }
        return out;
    }
}
