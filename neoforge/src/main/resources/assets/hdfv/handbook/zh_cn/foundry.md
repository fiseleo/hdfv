---
id: foundry
section: main
order: 3
title: 铸、锻与组装
icon: hdfv:foundry
advancement: hdfv:tutorial/foundry
---

机器不是直接合成出来的：它们在{item hdfv:assembly_bench}用**标准件**组起来。第一批标准件是 A 级，只有{item hdfv:foundry}和{item hdfv:forging_press}做得出来的铸件和锻件。这三台用工作台做。

{image furnaces}

{recipe hdfv:foundry}

{recipe hdfv:forging_press}

{recipe hdfv:assembly_bench}

## 做一个标准件
- 用{item hdfv:design_table}或{item hdfv:design_tablet}打开设计界面；在“标准件”分类选一种（床身铸件、锻造轴……），存到{item hdfv:blank_blueprint}上。
- 把图纸放进机器的图纸格、料件放进投料格。成形类机器会自己做；箭头选择先做哪一个。
- B、C、D 级之后才做：先是车床、钻床、刨床，再来是铣床和磨床，最后是 CNC。

## 组装一台机器

把一台机器需要的所有标准件放上{item hdfv:assembly_bench}，并保持它的界面开着：组装要花好几个工作小时。JEI 的组装台页面会列出需要什么。

{see power}
