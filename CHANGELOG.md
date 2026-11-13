# Changelog

High Degree of Freedom Vehicles' versions, newest first. Before 0.24 hdfv was not released; the detailed development
log (in Traditional Chinese) is [docs/HISTORY.zh-TW.md](docs/HISTORY.zh-TW.md).

## 0.25.0: damage and repair, cargo and storage, ramps, on AeroCore 0.42.0

- **Damage.** Parts wear as they are used (tyres, brake linings, clutches, gears, engines, hot sections, motors) and are
  damaged by crashes, attacks and explosions, fire and lava, water and corrosion; each part shows how much of it is used
  up, and a worn part does less (grip, braking, power, lift, thrust). Occupants can be hurt in crashes. A wrecked body
  stops the engines; a leaking tank can burn. Sneak and right-click a vehicle with an empty hand for its status screen;
  warning lamps on the dashboard; smoke and leaks. Server options in `damage` (scales for each source, injuries,
  explosions, or off).
- **Repair.** The repair shop (a room like the refit shop) mends worn and damaged parts with stock and standard parts,
  time and power; a part past 80 % must be replaced by a newly made one of the same design (or, for brakes, suspension
  and clutches, its worn pieces). Field repair kit and tyre patch kit to get home. New standard part: friction lining.
- **Cargo.** A body's cargo spaces (boot, bed, hold) carry items, weighed into the physics; open them with the
  inventory key while riding, or from the status screen. The storage box part: roof boxes, bed boxes, 20 ft containers.
  The cargo loader serves a parked vehicle's hold to hoppers. Every item has a mass (data map `hdfv:item_mass`, server
  options `storage.itemMassScale` and `storage.bucketLitres`: a bucket of fluid weighs as 10 litres).
- **Storage blocks.** Crate, steel locker, parts rack (parts only), shipping container; assembly rooms and the refit
  and repair shops take materials from them.
- **Road ramps** of asphalt and concrete, 1:2, 1:4 and 1:8, placed run by run; wheels drive them as a real slope.
- **Bench charts** in the design screen for gearboxes (traction, a full-throttle run, the torque converter), transfer
  cases, axles (tyre curves, brake fade) and landing gear (drop test).
- A body's design cargo no longer counts in the empty vehicle's mass (the sample sedan is 45 kg lighter, the helicopter
  120 kg); real cargo does.
- Saves from 0.24 load with every part as new and empty holds.

## 0.24.0: first public release (beta), on AeroCore 0.41.0

- **New-player tutorial.** The Vehicle Engineering Handbook (24 entries: the main line from the first ore to driving,
  a chapter per branch; text with your key bindings, item rows, recipes, 3D sample designs; JEI recipes on click), given
  once on first join with a welcome in the chat; 39 advancements in a High Degree of Freedom Vehicles tab, many of them
  for things you do (save a design, form a machine, roll a vehicle out, drive a kilometre, be cleared by a tower);
  `/hdfv handbook`; JEI pages for the blocks that had none. Server options `tutorial.giveHandbook` and
  `tutorial.welcome`.
- **Simplified Chinese**, besides English and Traditional Chinese.
- Licence LGPL-3.0-only; mod metadata, logo, JEI and Create declared as optional dependencies.
- Development code is out of the release JAR (GameTests, `/hdfv dev`, `/hdfvdev`, test vehicles).

## Before the release

- 0.23: recipes and tech tree: standard components made on the machines, machines fitted on the assembly bench, the
  arc furnace and electrolysis cell, `c:` tags, Create recipes.
- 0.22: better models everywhere ("beautify").
- 0.20 to 0.21: control towers, airports (large, medium, small) with lights and ATC, the tower's inside.
- 0.19: synthesized sound for vehicles and machines.
- 0.18: car fronts, cab-over trucks, articulated trailers.
- 0.17: all machining families (grinding, finishing, gears, shaping and broaching, sawing, EDM and ECM, cutting tables,
  threading, riveting), consumables, the air separation unit.
- 0.16: the refit shop.
- 0.15: player-designed chassis.
- 0.14: driving controls and dials; parts must fit their engine bay; interiors.
- 0.13: stickers and the paint booth.
- 0.11 to 0.12: realistic engines, machines and vehicles.
- 0.10: ramjets, scramjets, pulsejets, boosters, afterburners, turbo-ramjets, hydrogen.
- 0.8 to 0.9: power (Forge Energy, line shafts, engine stands), the multiblock CNC, petroleum, diesel, multiblock machine
  tools, the petroleum screens.
- 0.7: manufacturing: ores and smelting, machine tools, CNC, welding, assembly rooms, the welder villager.
- 0.6: wheels, axles, landing gear, gearboxes, four-wheel drive.
- 0.1 to 0.5: part designs (engines, turbines, wings, propellers, rotors, modular bodies), the design screen and bench.
