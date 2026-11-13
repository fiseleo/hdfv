package dev.mcmod.hdfv.neoforge.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.design.Blueprints;
import dev.mcmod.hdfv.neoforge.HdfvRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Development-only client commands (not in a production game), so the design screen can be driven without a person,
 * e.g. by AeroCore's AutoShot "client" step:
 * <pre>
 * /hdfvdev design &lt;sample&gt; &lt;page&gt;      open the design screen on a sample at a page (overview, a component, bench)
 * /hdfvdev page &lt;page&gt;                 switch the open screen to a page (a refinery screen's: 0, 1, 2)
 * /hdfvdev set &lt;page&gt; &lt;parameter&gt; &lt;text&gt;  type a value into the open screen
 * /hdfvdev bench                       start the open screen's bench
 * /hdfvdev tab                         the creative inventory on hdfv's tab
 * /hdfvdev cutaway on|off              the open screen's 3D view as a cutaway or from outside
 * /hdfvdev enlarge on|off              the 3D view over the whole page
 * /hdfvdev focus x y z | off           the 3D view turned about a point of the model (close-ups)
 * /hdfvdev clip                        log where the open design's world model passes through itself
 * /hdfvdev status &lt;tab&gt;                0.25: the status screen of the vehicle ridden or the nearest (0 condition, 1 repair, 2 cargo)
 * /hdfvdev mouse &lt;x&gt; &lt;y&gt;              0.25: the mouse put at a point of the screen (GUI units), for tooltips
 * /hdfvdev hover &lt;slot&gt;                0.25: the mouse put over a slot of the open container screen
 * /hdfvdev click &lt;x&gt; &lt;y&gt;              0.25: a left click at a point of the open screen
 * /hdfvdev scroll &lt;x&gt; &lt;y&gt; &lt;amount&gt;     0.25: the mouse wheel turned at a point of the open screen (positive: up)
 * /hdfvdev drag &lt;x&gt; &lt;y&gt; &lt;dx&gt; &lt;dy&gt;      0.25: a left-button drag on the open screen (the advancements' tree)
 * /hdfvdev advancements                0.25: the advancements screen on hdfv's tab
 * /hdfvdev lang &lt;code&gt;                 0.25: the language for this run (en_us, zh_tw, zh_cn; options.txt unchanged)
 * /hdfvdev jei &lt;item&gt;                  0.25: JEI's information page of hdfv:&lt;item&gt;
 * </pre>
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class DevCommands {
    private DevCommands() {}

    @SubscribeEvent public static void register(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) { return; }
        event.getDispatcher().register(Commands.literal("hdfvdev")
                .then(Commands.literal("design").then(Commands.argument("sample", StringArgumentType.word()).then(Commands.argument("page", StringArgumentType.word())
                        .executes(c -> {
                            DesignScreen screen = new DesignScreen(Designs.sample(StringArgumentType.getString(c, "sample")), Blueprints.Target.NEW, null);
                            Minecraft.getInstance().setScreen(screen);
                            screen.devOpen(StringArgumentType.getString(c, "page"));
                            return 1;
                        }))))
                .then(Commands.literal("set").then(Commands.argument("page", StringArgumentType.word()).then(Commands.argument("parameter", StringArgumentType.word())
                        .then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                            if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                            screen.devSet(StringArgumentType.getString(c, "page"), StringArgumentType.getString(c, "parameter"), StringArgumentType.getString(c, "text"));
                            return 1;
                        })))))
                .then(Commands.literal("page").then(Commands.argument("page", StringArgumentType.word()).executes(c -> {
                    // a refinery screen's page by number (0.9; AutoShot cannot click its tabs)
                    if (Minecraft.getInstance().screen instanceof PetroleumScreen petroleum) {
                        try { petroleum.devPage(Integer.parseInt(StringArgumentType.getString(c, "page"))); } catch (NumberFormatException e) { return 0; }
                        return 1;
                    }
                    if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                    screen.devOpen(StringArgumentType.getString(c, "page"));
                    return 1;
                })))
                .then(Commands.literal("roompage").then(Commands.argument("page", StringArgumentType.word()).executes(c -> {
                    RoomScreen.devPage(StringArgumentType.getString(c, "page").equals("layout"));
                    return 1;
                })))
                .then(Commands.literal("tab").executes(c -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.player == null) { return 0; }
                    try {
                        // the creative screen opens on the tab it last showed (a private static; development only)
                        java.lang.reflect.Field selected = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
                        selected.setAccessible(true);
                        selected.set(null, HdfvRegistry.TAB.get());
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                    minecraft.setScreen(new CreativeModeInventoryScreen(minecraft.player, minecraft.player.connection.enabledFeatures(),
                            minecraft.options.operatorItemsTab().get()));
                    return 1;
                }))
                .then(Commands.literal("cutaway").then(Commands.argument("on", StringArgumentType.word()).executes(c -> {
                    if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                    screen.devCutaway(StringArgumentType.getString(c, "on").equals("on"));
                    return 1;
                })))
                // 0.12: a machine (or pump jack) at a block shown working on this client only, as the server would say
                .then(Commands.literal("working").then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                        .then(Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .then(Commands.argument("on", StringArgumentType.word()).executes(c -> {
                    var level = Minecraft.getInstance().level;
                    if (level == null) { return 0; }
                    var pos = new net.minecraft.core.BlockPos(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "x"),
                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "y"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "z"));
                    boolean on = StringArgumentType.getString(c, "on").equals("on");
                    var be = level.getBlockEntity(pos);
                    var tag = new net.minecraft.nbt.CompoundTag();
                    tag.putBoolean("working", on);
                    tag.putBoolean("Pumping", on);
                    if (be instanceof dev.mcmod.hdfv.neoforge.machine.MachineBlockEntity || be instanceof dev.mcmod.hdfv.neoforge.petroleum.PumpJackBlockEntity) {
                        be.handleUpdateTag(tag, level.registryAccess());
                        return 1;
                    }
                    return 0;
                }))))))
                .then(Commands.literal("doors").then(Commands.argument("open", StringArgumentType.word()).executes(c -> {
                    if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                    screen.devDoors(StringArgumentType.getString(c, "open").equals("open"));
                    return 1;
                })))
                .then(Commands.literal("enlarge").then(Commands.argument("on", StringArgumentType.word()).executes(c -> {
                    if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                    screen.devEnlarge(StringArgumentType.getString(c, "on").equals("on"));
                    var box = screen.devBounds();
                    com.mojang.logging.LogUtils.getLogger().info("hdfv preview: {} quads, box {} to {}", screen.devQuads(), box == null ? "-" : box[0], box == null ? "-" : box[1]);
                    return 1;
                })))
                .then(Commands.literal("end").executes(c -> {
                    if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                    screen.devScrollToEnd();
                    return 1;
                }))
                .then(Commands.literal("export").then(Commands.argument("what", StringArgumentType.word()).executes(c -> {
                    // 0.11: the open design's model as OBJ + MTL in %USERPROFILE%\.mcmod\exports, for Blender (review renders)
                    // and checks: "world" is the mesh a vehicle in the world draws, "screen" the design screen's (page, cutaway)
                    if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                    boolean world = StringArgumentType.getString(c, "what").equals("world");
                    var model = Preview.model(screen.devDesign(), world ? null : screen.page(), !world && screen.devCutawayOn());
                    if (model == null) { return 0; }
                    dev.mcmod.aerocore.api.mesh.Mesh mesh = dev.mcmod.aerocore.api.mesh.Mesh.of(model.at(0));
                    if (!world) { for (var s : model.sections) { mesh.add(s.behind(), dev.mcmod.aerocore.api.Orientation.IDENTITY, dev.mcmod.aerocore.api.Vec3d.ZERO); } }
                    String name = screen.devDesign().name().replaceAll("[^A-Za-z0-9_.-]", "_") + (world ? "_world" : "_" + screen.page());
                    var files = dev.mcmod.aerocore.api.mesh.ObjWriter.write(java.util.Map.of(name, mesh), name + ".mtl");
                    java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("user.home"), ".mcmod", "exports");
                    try {
                        java.nio.file.Files.createDirectories(dir);
                        java.nio.file.Files.writeString(dir.resolve(name + ".obj"), files.obj());
                        java.nio.file.Files.writeString(dir.resolve(name + ".mtl"), files.mtl());
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException(e);
                    }
                    com.mojang.logging.LogUtils.getLogger().info("hdfv export: {} quads to {}", mesh.quads().size(), dir.resolve(name + ".obj"));
                    return 1;
                })))
                .then(Commands.literal("view").then(Commands.argument("yaw", com.mojang.brigadier.arguments.FloatArgumentType.floatArg())
                        .then(Commands.argument("pitch", com.mojang.brigadier.arguments.FloatArgumentType.floatArg()).executes(c -> {
                    // 0.11: a fixed 3D view on the design screen, so screenshots compare
                    if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                    screen.devView(com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "yaw"), com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "pitch"));
                    return 1;
                }))))
                .then(Commands.literal("focus")
                        .then(Commands.literal("off").executes(c -> {
                            if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                            screen.devFocus(null);
                            return 1;
                        }))
                        .then(Commands.argument("x", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg()).then(Commands.argument("y", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                        .then(Commands.argument("z", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg()).executes(c -> {
                            if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                            screen.devFocus(new dev.mcmod.aerocore.api.Vec3d(com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "x"),
                                    com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "y"), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "z")));
                            return 1;
                        })))))
                .then(Commands.literal("clip").executes(c -> {
                    // 0.11: pieces of the world model that pass through each other (dev.mcmod.hdfv.neoforge.client.model.ClipCheck)
                    if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                    var model = Preview.model(screen.devDesign(), null, false);
                    if (model == null) { return 0; }
                    var mesh = model.at(0);
                    var box = mesh.bounds();
                    double extent = box[1].subtract(box[0]).length();
                    long t0 = System.nanoTime();
                    var clashes = dev.mcmod.hdfv.neoforge.client.model.ClipCheck.check(mesh, Math.max(0.005, extent / 120));
                    var log = com.mojang.logging.LogUtils.getLogger();
                    log.info("hdfv clip {}: {} quads, box {} to {}, {} clashes in {} ms", screen.devDesign().name(), mesh.quads().size(), box[0], box[1],
                            clashes.size(), (System.nanoTime() - t0) / 1_000_000);
                    // every clash as a line of JSON in %USERPROFILE%\.mcmod\exports\clip\<design>.jsonl, for sorting outside the game
                    StringBuilder json = new StringBuilder();
                    java.util.function.Function<dev.mcmod.aerocore.api.Vec3d, String> v = p -> String.format(java.util.Locale.ROOT, "[%.4f,%.4f,%.4f]", p.x(), p.y(), p.z());
                    for (var k : clashes) {
                        json.append(String.format(java.util.Locale.ROOT, "{\"a\":\"%08x\",\"b\":\"%08x\",\"n\":%d,\"inside\":%b,\"boxA\":[%s,%s],\"boxB\":[%s,%s],\"crossed\":[%s,%s],\"centre\":%s}%n",
                                k.colourA(), k.colourB(), k.crossings(), k.inside(), v.apply(k.boxA()[0]), v.apply(k.boxA()[1]), v.apply(k.boxB()[0]), v.apply(k.boxB()[1]),
                                v.apply(k.crossed()[0]), v.apply(k.crossed()[1]), v.apply(k.centre())));
                    }
                    java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("user.home"), ".mcmod", "exports", "clip");
                    try {
                        java.nio.file.Files.createDirectories(dir);
                        java.nio.file.Files.writeString(dir.resolve(screen.devDesign().name().replaceAll("[^A-Za-z0-9_.-]", "_") + ".jsonl"), json);
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException(e);
                    }
                    return 1;
                }))
                // 0.14: what of a part's model lies outside its envelope (core's Envelopes, which a body is checked against):
                // the open design, or every sample with an envelope; the log line per design, the vertices to
                // %USERPROFILE%\.mcmod\exports\envelope\<design>.jsonl
                .then(Commands.literal("envelope")
                        .executes(c -> {
                            if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                            return envelopeCheck(screen.devDesign()) ? 1 : 0;
                        })
                        .then(Commands.literal("all").executes(c -> {
                            int checked = 0;
                            for (var sample : dev.mcmod.hdfv.design.Designs.SAMPLES) { if (envelopeCheck(sample.design().get())) { checked++; } }
                            return checked;
                        })))
                // 0.15: a chassis's members (role, station, ends, usage at 1.5 times the standard cases) to
                // exports/frame/<design>.jsonl, and an axle's travel (camber, toe, motion ratio, side-view angle) to
                // exports/kinematics/<design>.csv: the open design, or every sample of the kind
                // 0.15: the nearest vehicle's shown values per part, as this client has them, to the log
                .then(Commands.literal("shown").executes(c -> {
                    var mc = Minecraft.getInstance();
                    if (mc.player == null || mc.level == null) { return 0; }
                    var near = mc.level.getEntitiesOfClass(dev.mcmod.aerocore.neoforge.vehicle.PartVehicle.class, mc.player.getBoundingBox().inflate(16));
                    near.sort(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(mc.player)));
                    if (near.isEmpty() || near.getFirst().vehicle() == null) { return 0; }
                    var v = near.getFirst();
                    try {
                        var field = dev.mcmod.aerocore.neoforge.vehicle.PartVehicle.class.getDeclaredField("DISPLAY");
                        field.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        var key = (net.minecraft.network.syncher.EntityDataAccessor<String>) field.get(null);
                        String text = v.getEntityData().get(key);
                        com.mojang.logging.LogUtils.getLogger().info("hdfv shown {} (id {}) display: {}", v.blueprint().name(), v.getId(), text.length() > 900 ? text.substring(0, 900) : text);
                        var server = mc.getSingleplayerServer();
                        if (server != null && server.overworld().getEntity(v.getId()) instanceof dev.mcmod.aerocore.neoforge.vehicle.PartVehicle sv) {
                            com.mojang.logging.LogUtils.getLogger().info("hdfv shown {} on the server: ready {}, fault {}, speed {} m/s", sv.blueprint().name(), sv.ready(), sv.fault(),
                                    sv.vehicleState() == null ? "-" : String.format("%.3f", sv.vehicleState().body().velocity().length()));
                        }
                    } catch (ReflectiveOperationException e) {
                        com.mojang.logging.LogUtils.getLogger().info("hdfv shown: no display field ({})", e.toString());
                    }
                    for (var i : v.vehicle().installed()) {
                        // an axle's rig as drawn now: each piece's turn (angle and axis)
                        if (i.part().design() instanceof dev.mcmod.hdfv.part.wheel.AxleDesign a) {
                            var rig = dev.mcmod.hdfv.neoforge.client.model.WheelRig.axle(a);
                            var st = new dev.mcmod.hdfv.neoforge.client.model.WheelRig.State(new double[]{v.shown(i.id(), 2, 1f), v.shown(i.id(), 5, 1f)},
                                    new double[]{v.shown(i.id(), 1, 1f), v.shown(i.id(), 4, 1f)}, new double[]{0, 0}, 0);
                            StringBuilder poses = new StringBuilder();
                            for (int p = 1; p <= rig.motions().size(); p++) {
                                var q = rig.pose(p, st).turn();
                                double angle = 2 * Math.acos(Math.min(1, Math.abs(q.w())));
                                double s = Math.sqrt(Math.max(1e-12, 1 - q.w() * q.w()));
                                poses.append(String.format(java.util.Locale.ROOT, " %d:%.1fdeg(y %.2f)", p, Math.toDegrees(angle), q.y() / s * Math.signum(q.w())));
                            }
                            com.mojang.logging.LogUtils.getLogger().info("hdfv rig {} {} (hardpoints {}):{}", v.blueprint().name(), i.id(), a.kinematics() != null, poses);
                        }
                        int n = i.part().shownValues();
                        StringBuilder line = new StringBuilder();
                        for (int k = 0; k < n; k++) { line.append(String.format(java.util.Locale.ROOT, " %.4f", v.shown(i.id(), k, 1f))); }
                        com.mojang.logging.LogUtils.getLogger().info("hdfv shown {} {}:{}", v.blueprint().name(), i.id(), line);
                    }
                    return 1;
                }))
                .then(Commands.literal("frame")
                        .executes(c -> Minecraft.getInstance().screen instanceof DesignScreen screen && frameExport(screen.devDesign()) ? 1 : 0)
                        .then(Commands.literal("all").executes(c -> {
                            int n = 0;
                            for (var sample : dev.mcmod.hdfv.design.Designs.SAMPLES) { if (frameExport(sample.design().get())) { n++; } }
                            return n;
                        })))
                .then(Commands.literal("kinematics")
                        .executes(c -> Minecraft.getInstance().screen instanceof DesignScreen screen && kinematicsExport(screen.devDesign()) ? 1 : 0)
                        .then(Commands.literal("all").executes(c -> {
                            int n = 0;
                            for (var sample : dev.mcmod.hdfv.design.Designs.SAMPLES) { if (kinematicsExport(sample.design().get())) { n++; } }
                            return n;
                        })))
                // 0.13: stickers' pictures without the file dialog: a file by path, or the test picture (a checkerboard with an
                // upright, unmirrored arrow and a band at the top, written to .mcmod/tmp)
                .then(Commands.literal("decal")
                        .then(Commands.literal("upload").then(Commands.argument("file", StringArgumentType.greedyString()).executes(c -> {
                            DecalUpload.upload(java.nio.file.Path.of(StringArgumentType.getString(c, "file")));
                            return 1;
                        })))
                        .then(Commands.literal("testpicture").executes(c -> {
                            java.nio.file.Path file = java.nio.file.Path.of(System.getProperty("user.home"), ".mcmod", "tmp", "hdfv-sticker-test.png");
                            try {
                                java.nio.file.Files.createDirectories(file.getParent());
                                javax.imageio.ImageIO.write(testPicture(512, 384), "png", file.toFile());
                            } catch (java.io.IOException e) {
                                throw new IllegalStateException(e);
                            }
                            DecalUpload.upload(file);
                            return 1;
                        }))
                        .then(Commands.literal("list").executes(c -> {
                            DecalUpload.refresh();
                            return 1;
                        }))
                        // 0.14: the real file dialog (as the booth's upload button), with a test picture of another size
                        // written to .mcmod/tmp/hdfv-sticker-pick.png for a script to pick in it
                        .then(Commands.literal("pick").executes(c -> {
                            java.nio.file.Path file = java.nio.file.Path.of(System.getProperty("user.home"), ".mcmod", "tmp", "hdfv-sticker-pick.png");
                            try {
                                java.nio.file.Files.createDirectories(file.getParent());
                                javax.imageio.ImageIO.write(testPicture(384, 384), "png", file.toFile());
                            } catch (java.io.IOException e) {
                                throw new IllegalStateException(e);
                            }
                            DecalUpload.pick();
                            return 1;
                        })))
                // 0.13: the paint booth's screen (AutoShot): a page, a part, a look, a picture placed by a click on the view, an action, closing
                .then(Commands.literal("booth")
                        .then(Commands.literal("page").then(Commands.argument("page", StringArgumentType.word()).executes(c -> {
                            if (!(Minecraft.getInstance().screen instanceof PaintBoothScreen screen)) { return 0; }
                            screen.devPage(PaintBoothScreen.Page.valueOf(StringArgumentType.getString(c, "page").toUpperCase(java.util.Locale.ROOT)));
                            return 1;
                        })))
                        .then(Commands.literal("part").then(Commands.argument("part", StringArgumentType.word()).executes(c -> {
                            if (!(Minecraft.getInstance().screen instanceof PaintBoothScreen screen)) { return 0; }
                            screen.devSelect(StringArgumentType.getString(c, "part"));
                            return 1;
                        })))
                        .then(Commands.literal("look").then(Commands.argument("page", StringArgumentType.word()).then(Commands.argument("param", StringArgumentType.word())
                                .then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                                    if (!(Minecraft.getInstance().screen instanceof PaintBoothScreen screen)) { return 0; }
                                    screen.devLook(StringArgumentType.getString(c, "page"), StringArgumentType.getString(c, "param"), StringArgumentType.getString(c, "text"));
                                    return 1;
                                })))))
                        .then(Commands.literal("place").then(Commands.argument("picture", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                .then(Commands.argument("fx", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 1))
                                        .then(Commands.argument("fy", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 1)).executes(c -> {
                                            if (!(Minecraft.getInstance().screen instanceof PaintBoothScreen screen)) { return 0; }
                                            screen.devPlace(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "picture"),
                                                    com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "fx"), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "fy"));
                                            return 1;
                                        })))))
                        .then(Commands.literal("view").then(Commands.argument("yaw", com.mojang.brigadier.arguments.FloatArgumentType.floatArg())
                                .then(Commands.argument("pitch", com.mojang.brigadier.arguments.FloatArgumentType.floatArg()).executes(c -> {
                                    if (!(Minecraft.getInstance().screen instanceof PaintBoothScreen screen)) { return 0; }
                                    screen.devView(com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "yaw"), com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "pitch"));
                                    return 1;
                                }))))
                        .then(Commands.literal("action").then(Commands.argument("op", StringArgumentType.word()).executes(c -> {
                            if (!(Minecraft.getInstance().screen instanceof PaintBoothScreen screen)) { return 0; }
                            screen.devAction(dev.mcmod.hdfv.neoforge.paint.PaintBoothBlockEntity.Op.valueOf(StringArgumentType.getString(c, "op").toUpperCase(java.util.Locale.ROOT)));
                            return 1;
                        }))))
                // 0.20: the tower's screen (AutoShot): a page, take or release control, pick a strip, an instruction to the picked aircraft
                .then(Commands.literal("tower")
                        .then(Commands.literal("page").then(Commands.argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1)).executes(c -> {
                            if (!(Minecraft.getInstance().screen instanceof TowerScreen screen)) { return 0; }
                            screen.devPage(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "page"));
                            return 1;
                        })))
                        .then(Commands.literal("take").executes(c -> Minecraft.getInstance().screen instanceof TowerScreen screen && screen.devTake() ? 1 : 0))
                        .then(Commands.literal("pick").then(Commands.argument("strip", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 6)).executes(c ->
                                Minecraft.getInstance().screen instanceof TowerScreen screen && screen.devPick(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "strip")) ? 1 : 0)))
                        .then(Commands.literal("order").then(Commands.argument("instruction", StringArgumentType.word()).executes(c ->
                                Minecraft.getInstance().screen instanceof TowerScreen screen && screen.devOrder(StringArgumentType.getString(c, "instruction")) ? 1 : 0))))
                // 0.16: the refit shop's screen (AutoShot): a page, a part, an adjustable, the scale, a lamp put by a click, an action
                .then(Commands.literal("refit")
                        .then(Commands.literal("page").then(Commands.argument("page", StringArgumentType.word()).executes(c -> {
                            if (!(Minecraft.getInstance().screen instanceof RefitShopScreen screen)) { return 0; }
                            screen.devPage(RefitShopScreen.Page.valueOf(StringArgumentType.getString(c, "page").toUpperCase(java.util.Locale.ROOT)));
                            return 1;
                        })))
                        .then(Commands.literal("part").then(Commands.argument("part", StringArgumentType.word()).executes(c -> {
                            if (!(Minecraft.getInstance().screen instanceof RefitShopScreen screen)) { return 0; }
                            screen.devSelect(StringArgumentType.getString(c, "part"));
                            return 1;
                        })))
                        .then(Commands.literal("set").then(Commands.argument("page", StringArgumentType.word()).then(Commands.argument("param", StringArgumentType.word())
                                .then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                                    if (!(Minecraft.getInstance().screen instanceof RefitShopScreen screen)) { return 0; }
                                    screen.devSet(StringArgumentType.getString(c, "page"), StringArgumentType.getString(c, "param"), StringArgumentType.getString(c, "text"));
                                    return 1;
                                })))))
                        .then(Commands.literal("scale").then(Commands.argument("scale", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.25, 4)).executes(c -> {
                            if (!(Minecraft.getInstance().screen instanceof RefitShopScreen screen)) { return 0; }
                            screen.devScale(com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "scale"));
                            return 1;
                        })))
                        .then(Commands.literal("scroll").then(Commands.argument("rows", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(c -> {
                            if (!(Minecraft.getInstance().screen instanceof RefitShopScreen screen)) { return 0; }
                            screen.devScroll(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "rows"));
                            return 1;
                        })))
                        .then(Commands.literal("lamp").then(Commands.literal("kit").executes(c -> {
                                    if (!(Minecraft.getInstance().screen instanceof RefitShopScreen screen)) { return 0; }
                                    screen.devLampKit();
                                    return 1;
                                }))
                                .then(Commands.argument("role", StringArgumentType.word()).then(Commands.argument("style", StringArgumentType.word())
                                .then(Commands.argument("pair", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .then(Commands.argument("size", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(dev.mcmod.hdfv.look.LampSpec.MIN_SIZE_M, dev.mcmod.hdfv.look.LampSpec.MAX_SIZE_M))
                                .then(Commands.argument("fx", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 1))
                                .then(Commands.argument("fy", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 1)).executes(c -> {
                                    if (!(Minecraft.getInstance().screen instanceof RefitShopScreen screen)) { return 0; }
                                    screen.devLamp(dev.mcmod.hdfv.look.LampRules.Role.valueOf(StringArgumentType.getString(c, "role").toUpperCase(java.util.Locale.ROOT)),
                                            dev.mcmod.hdfv.look.LampSpec.Style.valueOf(StringArgumentType.getString(c, "style").toUpperCase(java.util.Locale.ROOT)),
                                            com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "pair"), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "size"),
                                            com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "fx"), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "fy"));
                                    return 1;
                                }))))))))
                        .then(Commands.literal("view").then(Commands.argument("yaw", com.mojang.brigadier.arguments.FloatArgumentType.floatArg())
                                .then(Commands.argument("pitch", com.mojang.brigadier.arguments.FloatArgumentType.floatArg()).executes(c -> {
                                    if (!(Minecraft.getInstance().screen instanceof RefitShopScreen screen)) { return 0; }
                                    screen.devView(com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "yaw"), com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "pitch"));
                                    return 1;
                                }))))
                        .then(Commands.literal("action").then(Commands.argument("op", StringArgumentType.word()).executes(c -> {
                            if (!(Minecraft.getInstance().screen instanceof RefitShopScreen screen)) { return 0; }
                            screen.devAction(dev.mcmod.hdfv.neoforge.refit.RefitShopBlockEntity.Op.valueOf(StringArgumentType.getString(c, "op").toUpperCase(java.util.Locale.ROOT)));
                            return 1;
                        }))))
                // 0.25 (AutoShot cannot sneak-use a vehicle, move the mouse, click or turn the wheel)
                .then(Commands.literal("status").then(Commands.argument("tab", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(c -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.player == null || minecraft.level == null) { return 0; }
                    var player = minecraft.player;
                    dev.mcmod.aerocore.neoforge.vehicle.PartVehicle nearest = player.getVehicle() instanceof dev.mcmod.aerocore.neoforge.vehicle.PartVehicle riding ? riding : null;
                    if (nearest == null) {
                        for (var e : minecraft.level.entitiesForRendering()) {
                            if (e instanceof dev.mcmod.aerocore.neoforge.vehicle.PartVehicle v && v.vehicle() != null && v.distanceToSqr(player) < 24 * 24
                                    && (nearest == null || v.distanceToSqr(player) < nearest.distanceToSqr(player))) { nearest = v; }
                        }
                    }
                    if (nearest == null) { return 0; }
                    VehicleStatusScreen screen = new VehicleStatusScreen(nearest);
                    int tab = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "tab");
                    try {
                        java.lang.reflect.Field tabs = VehicleStatusScreen.class.getDeclaredField("tabs"), at = VehicleStatusScreen.class.getDeclaredField("tab");
                        tabs.setAccessible(true);
                        at.setAccessible(true);
                        java.util.List<?> list = (java.util.List<?>) tabs.get(screen);
                        if (tab >= list.size()) { return 0; }
                        at.setInt(screen, tab);
                        ((VehicleStatusScreen.Tab) list.get(tab)).refresh();
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                    minecraft.execute(() -> minecraft.setScreen(screen));
                    return 1;
                })))
                .then(Commands.literal("mouse").then(Commands.argument("x", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg()).executes(c -> {
                            mouse(com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "x"), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "y"));
                            return 1;
                        }))))
                .then(Commands.literal("hover").then(Commands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(c -> {
                    if (!(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s)) { return 0; }
                    int i = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "slot");
                    if (i >= s.getMenu().slots.size()) { return 0; }
                    var slot = s.getMenu().slots.get(i);
                    mouse(s.getGuiLeft() + slot.x + 8, s.getGuiTop() + slot.y + 8);
                    return 1;
                })))
                .then(Commands.literal("click").then(Commands.argument("x", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg()).executes(c -> {
                            var screen = Minecraft.getInstance().screen;
                            if (screen == null) { return 0; }
                            double x = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "x"), y = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "y");
                            Minecraft.getInstance().execute(() -> { screen.mouseClicked(x, y, 0); screen.mouseReleased(x, y, 0); });
                            return 1;
                        }))))
                .then(Commands.literal("scroll").then(Commands.argument("x", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                .then(Commands.argument("amount", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg()).executes(c -> {
                                    var screen = Minecraft.getInstance().screen;
                                    if (screen == null) { return 0; }
                                    double x = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "x"), y = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "y");
                                    double amount = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "amount");
                                    Minecraft.getInstance().execute(() -> screen.mouseScrolled(x, y, 0, amount));
                                    return 1;
                                })))))
                .then(Commands.literal("drag").then(Commands.argument("x", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                .then(Commands.argument("dx", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("dy", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg()).executes(c -> {
                                            var screen = Minecraft.getInstance().screen;
                                            if (screen == null) { return 0; }
                                            double x = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "x"), y = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "y");
                                            double dx = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "dx"), dy = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "dy");
                                            // a still drag first: the advancements screen takes the first as the drag's start
                                            Minecraft.getInstance().execute(() -> { screen.mouseDragged(x, y, 0, 0, 0); screen.mouseDragged(x, y, 0, dx, dy); screen.mouseReleased(x + dx, y + dy, 0); });
                                            return 1;
                                        }))))))
                // 0.25: the advancements screen on hdfv's tab (with Create in the run it opens on Create's)
                .then(Commands.literal("advancements").executes(c -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.player == null) { return 0; }
                    var advancements = minecraft.player.connection.getAdvancements();
                    var root = advancements.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "tutorial/root"));
                    if (root == null) { return 0; }
                    minecraft.execute(() -> {
                        advancements.setSelectedTab(root, false);
                        minecraft.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(advancements));
                    });
                    return 1;
                }))
                // 0.25: the game's language for this run only (options.txt keeps the player's), as the language screen reloads it
                .then(Commands.literal("lang").then(Commands.argument("code", StringArgumentType.word()).executes(c -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    String code = StringArgumentType.getString(c, "code");
                    if (minecraft.getLanguageManager().getLanguage(code) == null) { return 0; }
                    minecraft.execute(() -> { minecraft.getLanguageManager().setSelected(code); minecraft.reloadResourcePacks(); });
                    return 1;
                })))
                // 0.25: JEI's information page of an item (hdfv:<id>), when JEI is in the run
                .then(Commands.literal("jei").then(Commands.argument("item", StringArgumentType.word()).executes(c -> {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID,
                            StringArgumentType.getString(c, "item")));
                    Minecraft.getInstance().execute(() -> jeiInformation(new net.minecraft.world.item.ItemStack(item)));
                    return 1;
                })))
                .then(Commands.literal("close").executes(c -> {
                    Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(null));
                    return 1;
                }))
                .then(Commands.literal("zoom").then(Commands.argument("factor", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.4, 40)).executes(c -> {
                    if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                    screen.devZoom(com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "factor"));
                    return 1;
                })))
                .then(Commands.literal("bench").executes(c -> {
                    if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                    screen.runBench();
                    return 1;
                })
                        // 0.25: an axle's bench view (0 the travel, 1 tyre and brakes)
                        .then(Commands.literal("view").then(Commands.argument("view", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1)).executes(c -> {
                            if (!(Minecraft.getInstance().screen instanceof DesignScreen screen)) { return 0; }
                            screen.devAxleView(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "view"));
                            return 1;
                        })))));
    }

    /**
     * 0.25: puts the mouse at a point of the screen in GUI units (the game reads the cursor from MouseHandler, which GLFW
     * only updates when the real mouse moves over the window), so a screen draws its tooltip there.
     */
    private static void mouse(double x, double y) {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        try {
            java.lang.reflect.Field xpos = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos"), ypos = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(minecraft.mouseHandler, x * window.getScreenWidth() / (double) window.getGuiScaledWidth());
            ypos.setDouble(minecraft.mouseHandler, y * window.getScreenHeight() / (double) window.getGuiScaledHeight());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 0.25: opens JEI's information recipes of an item (the hdfv.jei.* lines), by reflection: the dev source set is
     * not compiled against JEI's API, and JeiLink holds the runtime JEI hands hdfv. Logs and does nothing without JEI.
     */
    private static void jeiInformation(net.minecraft.world.item.ItemStack stack) {
        var log = com.mojang.logging.LogUtils.getLogger();
        try {
            java.lang.reflect.Field f = Class.forName("dev.mcmod.hdfv.neoforge.compat.JeiLink").getDeclaredField("runtime");
            f.setAccessible(true);
            Object runtime = f.get(null);
            if (runtime == null) { log.warn("hdfvdev jei: no JEI runtime"); return; }
            Class<?> runtimeType = Class.forName("mezz.jei.api.runtime.IJeiRuntime"), managerType = Class.forName("mezz.jei.api.recipe.IRecipeManager");
            Class<?> recipeType = Class.forName("mezz.jei.api.recipe.RecipeType");
            Object information = Class.forName("mezz.jei.api.constants.RecipeTypes").getField("INFORMATION").get(null);
            Object manager = runtimeType.getMethod("getRecipeManager").invoke(runtime);
            Object category = managerType.getMethod("getRecipeCategory", recipeType).invoke(manager, information);
            Object lookup = managerType.getMethod("createRecipeLookup", recipeType).invoke(manager, information);
            java.util.stream.Stream<?> all = (java.util.stream.Stream<?>) Class.forName("mezz.jei.api.recipe.IRecipeLookup").getMethod("get").invoke(lookup);
            Class<?> infoType = Class.forName("mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe"), typed = Class.forName("mezz.jei.api.ingredients.ITypedIngredient");
            java.util.List<Object> mine = new java.util.ArrayList<>();
            for (Object r : all.toList()) {
                for (Object ingredient : (java.util.List<?>) infoType.getMethod("getIngredients").invoke(r)) {
                    Object o = typed.getMethod("getIngredient").invoke(ingredient);
                    if (o instanceof net.minecraft.world.item.ItemStack s && s.is(stack.getItem())) { mine.add(r); break; }
                }
            }
            if (mine.isEmpty()) { log.warn("hdfvdev jei: no information for {}", stack); return; }
            Object gui = runtimeType.getMethod("getRecipesGui").invoke(runtime);
            Class.forName("mezz.jei.api.runtime.IRecipesGui").getMethod("showRecipes", Class.forName("mezz.jei.api.recipe.category.IRecipeCategory"), java.util.List.class,
                    java.util.List.class).invoke(gui, category, mine, java.util.List.of());
        } catch (ReflectiveOperationException e) {
            log.warn("hdfvdev jei: {}", e.toString());
        }
    }

    /** 0.15: writes a development export under %USERPROFILE%\.mcmod\exports\<kind>\<design>.<ext>. */
    private static void export(String kind, dev.mcmod.aerocore.api.vehicle.PartDesign design, String ext, CharSequence text) {
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("user.home"), ".mcmod", "exports", kind);
        try {
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve(design.name().replaceAll("[^A-Za-z0-9_.-]", "_") + "." + ext), text);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 0.15: a chassis's figures to the log and its members to exports/frame; false for anything else. */
    static boolean frameExport(dev.mcmod.aerocore.api.vehicle.PartDesign design) {
        if (!(design instanceof dev.mcmod.hdfv.part.chassis.ChassisDesign d)) { return false; }
        var a = d.analysis();
        var f = a.layout().frame;
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < f.memberCount(); i++) {
            var m = f.member(i);
            var role = a.layout().members.get(i);
            var p = f.position(m.a());
            var q = f.position(m.b());
            json.append(String.format(java.util.Locale.ROOT, "{\"member\":%d,\"role\":\"%s\",\"station\":%d,\"a\":[%.4f,%.4f,%.4f],\"b\":[%.4f,%.4f,%.4f],\"usage\":%.4f}%n",
                    i, role.role(), role.station() + 1, p.x(), p.y(), p.z(), q.x(), q.y(), q.z(), a.memberUsage()[i]));
        }
        com.mojang.logging.LogUtils.getLogger().info("hdfv frame {}: {} kg, {} kN m/deg between the end axles, {} kN/mm bending, usage {} % (member {}), {} members{}",
                design.name(), Math.round(a.massKg()), String.format("%.2f", a.endTwistNmPerDeg() / 1000), String.format("%.2f", a.bendingNPerM() / 1e6),
                Math.round(100 * a.usage()), a.worstMember(), f.memberCount(), a.failure() == null ? "" : "; " + a.failure());
        export("frame", design, "jsonl", json);
        return true;
    }

    /** 0.15: an axle's travel to exports/kinematics (CSV: compression mm, camber and toe deg, motion ratio, side angle deg); false for anything else. */
    static boolean kinematicsExport(dev.mcmod.aerocore.api.vehicle.PartDesign design) {
        if (!(design instanceof dev.mcmod.hdfv.part.wheel.AxleDesign d)) { return false; }
        var bench = dev.mcmod.hdfv.design.WheelBench.axle(d);
        StringBuilder csv = new StringBuilder("compression_mm,camber_deg,toe_deg,motion_ratio,side_angle_deg").append(System.lineSeparator());
        if (bench.kinematics().isEmpty()) {
            for (var t : bench.travel()) { csv.append(String.format(java.util.Locale.ROOT, "%.2f,%.4f,0,1,0%n", t.compressionM() * 1000, t.camberDeg())); }
        } else {
            for (var k : bench.kinematics()) {
                csv.append(String.format(java.util.Locale.ROOT, "%.2f,%.4f,%.4f,%.4f,%.4f%n", k.compressionM() * 1000, k.camberDeg(), k.toeDeg(), k.motionRatio(), k.sideAngleDeg()));
            }
        }
        com.mojang.logging.LogUtils.getLogger().info("hdfv kinematics {}: {} mode, roll centre {} mm, ride {} Hz{}", design.name(), d.hardpoints().mode(),
                Math.round(bench.rollCentreM() * 1000), String.format("%.2f", bench.rideFrequencyHz()),
                bench.axis() == null ? "" : String.format(java.util.Locale.ROOT, ", caster %.1f deg, kingpin %.1f deg, scrub %.0f mm, trail %.0f mm",
                        Math.toDegrees(bench.axis().casterRad()), Math.toDegrees(bench.axis().kingpinRad()), bench.axis().scrubM() * 1000, bench.axis().trailM() * 1000));
        export("kinematics", design, "csv", csv);
        return true;
    }

    /**
     * 0.14: checks a design's model (not cut away) against its envelope: logs how many vertices lie more than 3 mm
     * outside every box and how far the farthest is, and writes them (up to 200) to exports/envelope. False when the part
     * has no envelope or no model.
     */
    static boolean envelopeCheck(dev.mcmod.aerocore.api.vehicle.PartDesign design) {
        var envelope = dev.mcmod.hdfv.look.Envelopes.of(design);
        var model = envelope == null ? null : Preview.model(design, null, false);
        if (model == null) { return false; }
        var mesh = model.at(0);
        // each vertex outside every box (by more than 3 mm), by the box it is nearest: how many, how far, where
        record Group(String box, int[] n, double[] worst, double[] lo, double[] hi) {}
        java.util.Map<String, Group> groups = new java.util.LinkedHashMap<>();
        StringBuilder json = new StringBuilder();
        int vertices = 0, outside = 0;
        double worst = 0;
        for (var q : mesh.quads()) {
            for (var p : new dev.mcmod.aerocore.api.Vec3d[]{q.a(), q.b(), q.c(), q.d()}) {
                vertices++;
                if (envelope.contains(p, 0.003)) { continue; }
                double d = Double.POSITIVE_INFINITY;
                String near = "";
                for (var b : envelope.boxes()) {
                    double dx = Math.max(0, Math.max(b.min().x() - p.x(), p.x() - b.max().x())), dy = Math.max(0, Math.max(b.min().y() - p.y(), p.y() - b.max().y()));
                    double dz = Math.max(0, Math.max(b.min().z() - p.z(), p.z() - b.max().z()));
                    double e = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (e < d) { d = e; near = b.name(); }
                }
                outside++;
                worst = Math.max(worst, d);
                Group g = groups.computeIfAbsent(near, k -> new Group(k, new int[1], new double[1], new double[]{1e9, 1e9, 1e9}, new double[]{-1e9, -1e9, -1e9}));
                g.n()[0]++;
                g.worst()[0] = Math.max(g.worst()[0], d);
                double[] c = {p.x(), p.y(), p.z()};
                for (int k = 0; k < 3; k++) { g.lo()[k] = Math.min(g.lo()[k], c[k]); g.hi()[k] = Math.max(g.hi()[k], c[k]); }
                if (outside <= 20000) { json.append(String.format(java.util.Locale.ROOT, "{\"box\":\"%s\",\"d\":%.4f,\"p\":[%.4f,%.4f,%.4f]}%n", near, d, p.x(), p.y(), p.z())); }
            }
        }
        var log = com.mojang.logging.LogUtils.getLogger();
        var bounds = envelope.bounds();
        log.info("hdfv envelope {}: {} of {} vertices outside, farthest {} mm; envelope {} to {}", design.name(), outside, vertices,
                Math.round(worst * 1000), bounds.min(), bounds.max());
        groups.values().stream().sorted((x, y) -> Double.compare(y.worst()[0], x.worst()[0])).forEach(g -> log.info(
                "hdfv envelope {}:   nearest {}: {} vertices, farthest {} mm, from ({}, {}, {}) to ({}, {}, {})", design.name(), g.box(), g.n()[0],
                Math.round(g.worst()[0] * 1000), String.format("%.3f", g.lo()[0]), String.format("%.3f", g.lo()[1]), String.format("%.3f", g.lo()[2]),
                String.format("%.3f", g.hi()[0]), String.format("%.3f", g.hi()[1]), String.format("%.3f", g.hi()[2])));
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("user.home"), ".mcmod", "exports", "envelope");
        try {
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve(design.name().replaceAll("[^A-Za-z0-9_.-]", "_") + ".jsonl"), json);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return true;
    }

    /**
     * 0.13: the stickers' test picture (made here: the mod ships no pictures): a two-tone checkerboard, a red band along
     * the top, an arrow pointing up and an L in the bottom-left corner (which way is up, and whether it is mirrored), a
     * transparent ring round the middle (the cut-out).
     */
    static java.awt.image.BufferedImage testPicture(int w, int h) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int cell = w / 8;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = ((x / cell) + (y / cell)) % 2 == 0 ? 0xff2a4fa8 : 0xfff2f2f2;
                if (y < h / 10) { argb = 0xffd02020; }
                double dx = x - w / 2.0, dy = y - h / 2.0, r = Math.sqrt(dx * dx + dy * dy);
                if (r > h * 0.36 && r < h * 0.40) { argb = 0; }
                // the arrow: a shaft and a head pointing up
                if (Math.abs(dx) < w * 0.03 && dy > -h * 0.2 && dy < h * 0.25) { argb = 0xff101010; }
                if (dy >= -h * 0.32 && dy <= -h * 0.2 && Math.abs(dx) < (dy + h * 0.32) * 0.9) { argb = 0xff101010; }
                // an L in the bottom-left corner
                boolean stem = x > w * 0.05 && x < w * 0.09 && y > h * 0.68 && y < h * 0.94, foot = x > w * 0.05 && x < w * 0.2 && y > h * 0.9 && y < h * 0.94;
                if (stem || foot) { argb = 0xff20a040; }
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }
}
