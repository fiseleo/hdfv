---
id: damage
section: branch
order: 9
title: Damage and repair
icon: hdfv:repair_shop_controller
advancement: hdfv:tutorial/repaired
---

Every part of a vehicle keeps its condition, from new to used up: tyres, brakes, suspension, gears and clutch, the engine, its ancillaries, a turbine's hot section, a tank, the structure. A worn part does less: less grip, weaker brakes, less power or thrust.

## What uses a part up
- Use: tread, brake linings, clutch facings, gears, the engine and a turbine's hot section wear as they work. Past the rev limit an engine wears many times faster; a burnout eats a pair of tyres in under a minute.
- Crashes: a hit's energy goes into the parts it reaches, and a hard stop hurts those aboard.
- Attacks, explosions, fire and lava. Fire at a leaking tank, or a tank burst open, sets the fuel off.
- Water: an engine or a motor under water dies in some twenty seconds (a sound hull keeps it dry).
- Corrosion: slow while the paint holds (about 1 % a real day standing in the rain, more in sea air), fast where dents have broken it; under a roof hardly at all.

When the body or the chassis is used up the vehicle is a wreck, and its engines die with it.

## Seeing it
Sneak and use a vehicle with an empty hand ({key key.sneak} and {key key.use}) for its status screen: **Condition** shows how much of each part is used, **Repair** what each one needs and where, **Cargo** its holds. At the wheel, lamps over the dials warn of the engine, the brakes, a flat tyre, the gearbox, the body and a fuel leak.

## On the spot
{items hdfv:field_repair_kit hdfv:tyre_patch_kit}
- Look at the part and hold {key key.use} on the vehicle for a few seconds; looking away or stepping back stops the work.
- The {item hdfv:field_repair_kit} patches structure, ancillaries and tanks: up to 15 % a use, never better than half worn, 8 uses.
- The {item hdfv:tyre_patch_kit} plugs a flat tyre and pumps it up to half. Worn tread is not flat: that is new tyres. 4 uses.
- No kit touches brakes, a clutch, gears, an engine's insides or suspension, nor anything used 80 % or more. That is the repair shop's work.

{recipe hdfv:field_repair_kit}

## The repair shop
Like the refit shop: a {item hdfv:repair_shop_controller} in the wall of a closed room with a door, two {item hdfv:work_light} and electricity at the controller; a {item hdfv:welding_station} as well when the job welds.

{items hdfv:repair_shop_controller hdfv:work_light hdfv:welding_station hdfv:friction_lining}
- Drive the vehicle in, get everyone out and let it stand still.
- Scan, tick on its screen what to repair (or everything damaged) and Start. It takes what the job needs from the chests and storage blocks at the room, then works for the job's hours on its power.
- What is worn or damaged is mended with stock and standard components: brakes and clutches get a {item hdfv:friction_lining}, gears bearings, ancillaries wire and tube. Tyres are always new.
- The old metal goes back to the stores.

## Past 80 %
From 80 % used, a part is not mended but replaced by a newly made one of the same design: an engine or a gearbox as a {item hdfv:part} waiting in the stores; brakes, suspension, a strut or a clutch as new pieces machined from the part's blueprint; a body, a chassis or a wing rebuilt where it stands. On a wreck, repair the body with the engines or before them.

Server options, under **damage**: enabled, wearScale, collisionScale, attackScale, corrosionScale, occupantInjury and explosions; 0 or false turns each one off.

{see cargo}
