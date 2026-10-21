---
id: lathe
section: main
order: 5
title: 第一台车床
icon: hdfv:lathe
advancement: hdfv:tutorial/lathe
---

手动机床是可以加长的多方块。从操作者站的位置看，一台车床是这样：
- 下排：左边是驱动座（接天轴的{item hdfv:pulley_drive}，或接电线的{item hdfv:motor_drive}），右边接一到六格{item hdfv:lathe_bed}：每格一米床身。
- 上排：{item hdfv:lathe}方块在驱动座正上方（床头座）；床身上方可以放附件，尾端放尾座。

{image lathe}

对车床方块按使用：它会检查图样并成形。床身越长能做越长的工件；放不下时它会告诉你。钻床和刨床也是这样盖（JEI 会列出每台的图样与大小）。

{items hdfv:pulley_drive hdfv:lathe_bed hdfv:lathe hdfv:carbon_tool_set}

切削会磨耗刀具：在耗材格放{item hdfv:carbon_tool_set}。手动机床只在有人操作时才会动：保持它的界面开着，人待在旁边。

{recipe hdfv:lathe}

{see workshop}
