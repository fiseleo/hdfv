---
id: machinery
section: branch
order: 12
title: Heavy machinery
icon: hdfv:heavy_yard_controller
---

Excavators, dozers, loaders, rollers, pavers, cranes and the rest are built from parts like any vehicle: tracks or drums or axles under them, a diesel turning hydraulic pumps, work links (booms, sticks, arms, telescopes, masts, slewing rings, centre hinges) moved by their rams or swing motors, and the work tools on their tips. Every part is designed in the design screen's **Undercarriage** and **Work equipment** categories. The operator sits in an operator cab from the **Bodies** category (its size, how far its front window leans back, a ROPS or ROPS and FOPS frame, the door's side), with a seat set inside it. The engine, pumps and tanks go under an engine hood from the same category (its size, how far its top falls toward the radiator grille, its rounded tail, where the grille is, the exhaust stack): sheet metal with no floor, so it keeps nothing out of the water.

{design excavator_boom}

## The heavy yard
Machines are put together in the open **heavy yard**: assembly floor with the sky over it, its controller at the edge facing in. Heavy parts need a gantry crane's rails over the yard or a crane parked within 32 m. A machine of 10 t or less may also be built in a car factory.

{recipe hdfv:heavy_yard_controller}

## Driving and working
A machine has two modes; {key key.hdfv.work_mode} switches them. In drive mode the movement keys drive it (a crawler's tracks turn opposite ways to pivot). In work mode the movement keys and the arrow keys are two joysticks; the longer a key is held, the wider its valve opens.

- An excavator: left stick out and in moves the stick, sideways swings; right stick moves the boom and curls the bucket (ISO; the client setting swaps to SAE).
- A crane: sideways swings, forward and back telescopes; the right stick hoists and luffs.
- A loader, dozer, grader or truck: the movement keys still drive; the right stick lifts and tilts the tool.
- Functions left over: {key key.hdfv.work_select} picks one, {key key.hdfv.work_raise} and {key key.hdfv.work_lower} move it or switch it on and off; {key key.hdfv.work_float} floats a loader's arm.

The engine idles by itself when nothing has moved for four seconds. The dashboard shows the hydraulic pressure, what the bucket or body carries, the hook's load and the load moment limiter.

## Moving earth
Tools really dig the world. Soil comes away as bulk, weighed at its real density: grass and dirt as dirt, stone as cobblestone, ores as themselves. Solid stone stops a bucket; break it first with a ripper, a hydraulic hammer or a cutter drum. Tip a bucket or a body to pour: onto the ground it piles as loose fill, into another machine's body or a hopper it goes in. A roller compacts loose fill into a road base.

## Roads
The **asphalt plant** and the **concrete plant** are 3 x 3 x 3 multiblocks of iron blocks that mix batches into the body or drum standing in front of them. Hot mix cools on the way and must be laid and rolled while it is hot; concrete sets after about four hours.

{recipe hdfv:asphalt_plant_controller}

A paver lays the mat behind its screed, replacing the top block; a roller finishes it into asphalt. A cold planer mills old pavement into reclaimed asphalt for the plant.

## Lifting
A machine with a winch and a boom hangs its hook on a rope over the boom's tip. {key key.hdfv.work_select} to the hook latch and switch it on to take a vehicle or a block under the hook; switch it off to set it down.

{see cargo}
