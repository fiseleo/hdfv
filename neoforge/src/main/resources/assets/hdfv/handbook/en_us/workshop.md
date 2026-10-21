---
id: workshop
section: main
order: 6
title: Drawings, workpieces and steps
icon: hdfv:workpiece
advancement: hdfv:tutorial/machined
---

Every part you design is a drawing. Its components each have a **route**: the steps (casting, turning, drilling, milling, grinding, welding...) that make them, worked out from the design itself.
- Put the drawing in a machine: it does the steps of the route it can, to its precision, and hands out a {item hdfv:workpiece} that remembers how far it got.
- Take the workpiece, with the drawing, to the next machine. A machine that cannot do the next step says which one can.
- The last step hands out the finished component; when every component of a part is done, the {item hdfv:assembly_bench} puts the {item hdfv:part} together.

JEI's machining pages show what each machine does and to which IT grade. Time is real work: an hour of work is a Minecraft hour by default (a server option).

{see electricity}
