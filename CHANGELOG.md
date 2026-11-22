# Changelog

High Degree of Freedom Vehicles' versions, newest first. Before 0.24 hdfv was not released; the detailed development
log (in Traditional Chinese) is [docs/HISTORY.zh-TW.md](docs/HISTORY.zh-TW.md).

## 0.26.0: heavy machinery, on AeroCore 0.43.0

- **Machine parts** to design (new design screen categories Undercarriage and Work equipment): crawler tracks, roller
  drums (smooth and padfoot), outrigger jacks, hydraulic pumps and motors (travel drives, winches), work links (booms,
  sticks, loader arms, telescopic and lattice booms, masts, slewing rings, centre hinges, platform and placing booms,
  outrigger beams) with their rams, hook blocks, and work tools of 33 kinds (buckets, blades, rippers, hammers, grapples,
  forks, couplers, compactors, bowls, bodies, conveyors, hoppers, screeds, milling drums, cutterheads, roadheader heads,
  bucket wheels, trenchers, augers, pile hammers, brooms, snow blowers, spray bars, mixer drums, work platforms,
  segment erectors). Operator cabs (in Bodies): size, the front window's lean, a ROPS or ROPS and FOPS frame, the
  door's side; the seat goes inside. Engine hoods (in Bodies) over the engine, pumps and tanks: size, the top falling
  toward the radiator grille, a rounded tail, the grille at the sides, rear or front, the exhaust stack; sheet metal
  with no floor (it floats nothing). Sample parts only: whole machines are yours to put together.
- **Joints and hydraulics**: booms rise, houses slew, loaders steer on their hinges, bodies tip; pumps load the engine,
  rams give way at their relief, a machine can tip over when it reaches too far.
- **Work mode** (the work mode key, N by default): the movement keys and the arrows are two joysticks (ISO, or SAE in the
  client settings), the valve opening wider the longer a key is held; the other functions are picked and moved with X,
  R and F; auto-idle; new dashboard lamps (work mode, float, pressure, load, hook, load moment).
- **The world is dug**: tools cut blocks cell by cell; dug material is carried as bulk at its real density (grass and
  dirt as dirt, stone as cobblestone, ores as themselves) and poured back as loose fill, or into another machine's body
  or a container. Rock stops a bucket until ripped, hammered or milled. Rollers compact fill into road base; pile
  hammers drive piles; tunnel boring machines line their tunnel. New blocks: loose fill, blasted rock, compacted base,
  hot asphalt, fresh concrete, reclaimed asphalt, pile, tunnel lining; block tags `hdfv:earth/*`; server option
  `worksite.editWorld`.
- **Heavy yard**: an open-air assembly yard for machines (gantry crane rails or a crane parked nearby for the heavy
  parts); machines of 10 t or less may still be built in a car factory.
- **Batching plants**: the asphalt plant and the concrete plant (multiblocks of iron blocks) mix batches into a body or
  drum standing in front; hot mix cools and must be laid hot, concrete sets after about four hours.
- **Cranes**: a machine with a winch and a boom hangs its hook on a rope over the boom's tip; the hook latches a vehicle
  or a block under it.
- **Looks** (laid out in Blender, built from each design's numbers): booms, sticks and arms as welded boxes (a mono
  boom bent at its knee) with pin bosses and brackets; the hydraulic rams, barrel and chrome rod, moving with their
  joints; buckets on the profile their capacity is rated on, with teeth, lip and lugs; curved blades with cutting edges,
  end bits and wings; crawler tracks shoe by shoe round toothed sprockets, idlers and double-flanged rollers; smooth and
  padfoot drums in their yokes; dump bodies with their canopy, ribs and rails; hook blocks with sheaves and a forged
  hook; operator cabs glazed to the floor in front, with their door, mirror, work lights and beacon; engine hoods with
  louvres, door seams and latches, the engine lid, a walkway and handrail on a house, the stack and its rain cap. The tower crane: lattice mast sections and slewing unit, a triangular lattice jib with its trolley at the hook's
  radius, the counter-jib's walkway, winch and ballast, a glazed cab hung under the jib's root, looking along it (a
  crane built before gets the new superstructure when it is loaded).
- **Every machine part redrawn** (laid out in Blender against the old shapes): pumps with their flange, regulator and
  ports; travel drives on their bolted reducers; winches with the rope wound on, side frames, reducer and motor;
  outrigger jacks with a banded barrel and a cast float, outrigger beams with warning stripes; slewing rings with their
  bolts, seal and swing drive; channel masts with rollers, chains and sheaves; centre hinges with tongues and pins;
  telescopic booms of a rounded section with a collar; a placing boom's delivery line, a platform boom's cable carrier.
  Tools: a curved ripper shank, breakers in silenced housings with their chisel, pile hammers with leader guides and a
  helmet, plate compactors on rubber mounts, quick couplers, grapples of curved tines, forks with a backrest, railed
  work baskets with their console, augers with a wound flight, spray bars, segment erectors, milling drums with their
  picks under a hood, roadheader heads, bucket wheels with their buckets, rim and spokes, brooms, snow blowers with
  their augers and chute, TBM cutterheads with spokes, disc cutters and buckets, trencher chains with cutters, mixer
  drums with their roller track, scraper bowls with an apron, paver hoppers with slat conveyors and push rollers,
  extending screeds with their walkway, troughed conveyors.
- **More detail elsewhere**: gearboxes and transfer cases (ribbed cases, bolted bell flange, mounts, plugs, breather,
  sensor, a manual's boot, an automatic's bolted pan), the turbofan's cowl seams and latches, ladder frames (crossmembers
  bolted through the rails, spring hangers with their shackles, tow hooks), hydrogen tanks (a cylinder's valve and
  straps, a cryogenic tank's saddles, valve box and relief valves), wing panel lines, and seven machine blocks (the
  assembly bench, alloy furnace, battery, engine stand, hydrogen liquefier, fuel dispenser and air separation unit).
- New handbook chapter; test machines for development (`/hdfv dev drive spawn`).
- Vehicles without machine parts behave as in 0.25; saves from 0.25 load unchanged.

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
