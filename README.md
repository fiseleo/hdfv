# High Degree of Freedom Vehicles

**English** · [繁體中文](README.zh-TW.md) · [简体中文](README.zh-CN.md)

Vehicles for Minecraft 1.21.1 (NeoForge), built the way engineers build them. You design every part yourself (engines
from block and crank to turbine and nozzle, wings, propellers, rotors, hulls, car bodies, chassis, wheels and gearboxes),
make it in a workshop of real machine tools, and put the vehicle together in an assembly room. Cars, trucks, aircraft,
helicopters and boats; the physics is [AeroCore](https://github.com/fiseleo/aerocore)'s.

> Beta (0.x): saves are kept working between versions where possible, but the format may still change.

## Features

- **Design anything.** A design screen for every kind of part, with live checks, a 3D view and a test bench: run an
  engine on a dynamometer, a wing through its polar, a car on a test track. Piston engines (petrol and diesel,
  turbocharged), turbojets, turbofans, turboprops, turboshafts, ramjets, scramjets, pulsejets, rockets, hydrogen.
- **Make it.** New ores and smelting, an alloy furnace, a foundry and forging press, standard components, multiblock
  lathes, mills, planers, boring mills and drill presses you can lengthen, nine families of machining (grinding, gears,
  EDM, laser, waterjet, riveting...), a CNC up to 12 axes, welding. Every part follows its own route of real steps.
- **Power it.** Treadles, steam engines, line shafts, generators, cables, batteries, and the engines you design on
  engine stands.
- **Build it.** Car factories, hangars and shipyards: rooms you build, where vehicles are laid out, built from your
  parts by a crane and welders, and rolled out.
- **Fuel it.** Oil fields, drilling rigs and pump jacks, a distillation column and process units (FCC, reformer,
  cracker, polymers), fuel dispensers.
- **Drive it.** Manual and automatic gearboxes, four-wheel drive, dials, lights; flight controls, landing gear,
  airports and control towers with ATC; trailers on a fifth wheel.
- **Make it yours.** A refit shop (shape, size, aero kits, rims, tuning, lamps) and a paint booth with your own stickers.
- **Keep it running.** Parts wear and get damaged (crashes, attacks, fire, water, rust); a repair shop and field
  kits put them right, and a part past 80 % is replaced by a new one.
- **Load it.** Boots, beds and holds carry cargo that weighs; roof boxes, bed boxes and containers you design;
  crates, lockers, parts racks and a cargo loader.
- **Dig it.** Heavy machinery from parts you design: tracks, drums, hydraulic booms and rams, buckets, blades and 30-odd
  other work tools. Machines really dig the world and carry what they dig by weight; roll it, pave it, lift with cranes
  and a tower crane; asphalt and concrete plants; an open-air heavy yard to build them in.
- **Hear it.** Every sound is synthesized from the running engine or machine.

## Getting started

New players get the **Vehicle Engineering Handbook**: right-click it. It goes from the first ore to the first car,
aircraft and boat, a chapter per branch after that. The advancements screen has a High Degree of Freedom Vehicles tab
that follows the handbook, and every hdfv item's tooltip says its tech stage and where it is made. With JEI installed,
every recipe and machining step is there too.

## Requirements

| | |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.252 or newer (21.1.x) |
| AeroCore | 0.43.x (required) |
| JEI | optional: recipes and information pages |
| Create | optional: some materials by Create's crushing, mixing and pressing |
| Java | 21 |
| Side | client and server (both need it) |

## Multiplayer and servers

- Server options (`serverconfig/hdfv-server.toml`): how much metal an ingot is (10 kg) and how long an hour of work
  takes (1000 ticks); tool wear and chip recovery; stickers (uploads, limits); airport generation; the tutorial's
  handbook and welcome.
- Stickers are pictures players upload; they are shared with everyone on the server. Operators can list and remove
  them: `/hdfv decal list|remove|purge`.
- Operators: `/hdfv vehicle samples` writes the sample vehicle blueprints for AeroCore's `/aerocore vehicle spawn`.
- Client settings (Mods, High Degree of Freedom Vehicles, Config): driving, sound volumes, aviation units.

## Building from source

JDK 21 and an AeroCore checkout next to this one: hdfv builds against AeroCore's Maven folder
(`../AeroCore/build/repo`; build AeroCore with `publish` first).

```
./gradlew build                          # the game JAR in neoforge's build/libs
./gradlew :core:test                     # the unit tests (about 390)
./gradlew :neoforge:runGameTestServer    # the GameTests (about 140)
```

On Windows `.\dev.ps1 build` finds a JDK 21. Development code (GameTests, `/hdfv dev`, `/hdfvdev`, test vehicles)
lives in `neoforge/src/dev` and core's test fixtures and is never released; `verifyReleaseJar` checks the JAR.
`tools/` holds the generators for recipes, assets and language files (Python) and the Blender scripts for models
and textures.

## Known limits

- Vehicles are only as good as their design: a badly designed car really is slow, and a wrong engine really stalls.
- The physics is validated against published data but is game physics (see AeroCore's docs).
- Performance depends on how many vehicles and machines are running; very fast vehicles need the world ahead loaded.
- With shader packs (Iris), parts are drawn with vanilla shading; with Sound Physics Remastered, hdfv's own reverb is off.

## Community

Questions, bug reports, screenshots and news about High Degree of Freedom Vehicles and AeroCore: join the
[Discord server](https://discord.gg/4y2mubMg7e).

## License

High Degree of Freedom Vehicles is released under the [GNU Lesser General Public License v3.0 only](COPYING.LESSER)
(LGPL-3.0-only), which builds on the [GNU GPL v3](COPYING). Files from NeoForge's MDK template keep their
[MIT licence](TEMPLATE_LICENSE.txt). Models and textures are made by the scripts in `tools/` and fall under the same
licence; there are no sound files.

History: [CHANGELOG](CHANGELOG.md); the development log before the public release is in
[docs/HISTORY.zh-TW.md](docs/HISTORY.zh-TW.md).
