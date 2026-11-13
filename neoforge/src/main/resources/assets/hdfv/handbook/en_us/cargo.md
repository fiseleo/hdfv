---
id: cargo
section: branch
order: 10
title: Cargo and storage
icon: hdfv:shipping_container
advancement: hdfv:tutorial/loaded
---

Everything has a mass: an ingot or a stock is 10 kg, a part weighs what its design says, a full can its fuel as well. While a storage screen is open every tooltip says what the item weighs, and the screen's footer shows the slots used and the total.

## Storage blocks
{items hdfv:storage_crate hdfv:steel_locker hdfv:parts_rack hdfv:shipping_container}
- {item hdfv:storage_crate}: 27 stacks, planks and iron nuggets; the first you can make.
- {item hdfv:steel_locker}: 54 stacks in one block.
- {item hdfv:parts_rack}: 36 slots for parts, workpieces, standard components and drawings only. A hopper cannot put anything else in either, so it sorts parts out of a mixed line.
- {item hdfv:shipping_container}: 108 stacks on two pages (the buttons at the top right, or the mouse wheel).

Hoppers and pipes use them like chests. At an assembly room, the refit shop, the paint booth or the repair shop they are the room's stores: it takes its parts and materials from them, and what comes off goes back in.

{recipe hdfv:storage_crate}

## Holds on vehicles
A body module with a hold volume (a car's boot, a pickup's bed, a cargo bay) carries cargo, 27 slots a cubic metre. Riding, {key key.inventory} opens the nearest hold; on foot, the status screen's Cargo tab ({key key.sneak} and {key key.use} with an empty hand) opens one within a few metres.

For more room, design a **storage box** (the design screen's Storage category): a roof box, a pickup's bed box or a 20 ft container. It goes into the layout like any other part: a roof box on the roof (on the bars when the body has a roof rack), a bed box or a container on a bed or a deck.

{design roof_box}

## Loading by machine
A {item hdfv:cargo_loader} set beside where vehicles park, its front toward them, is the hold of the vehicle standing in front of it: hoppers and pipes fill and empty it like a chest, and once the vehicle drives off there is nothing to fill. Use it by hand to see which hold it serves.

## Weight
Cargo goes into the physics at its hold: a laden car sits lower, and accelerates, brakes and corners worse. Each hold has a rating (the body's design payload, a box's walls); more than that is shown in red but not stopped, and the vehicle pays for it. The server option **storage.itemMassScale** scales every mass (0: nothing weighs anything), and **storage.bucketLitres** says how many litres a bucket of water, lava or any other mod's fluid weighs as (10, a real bucket; the jerry can holds its 20 litres as they are).

{see damage}
