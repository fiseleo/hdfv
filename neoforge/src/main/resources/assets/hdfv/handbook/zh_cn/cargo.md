---
id: cargo
section: branch
order: 10
title: 货物与储物
icon: hdfv:shipping_container
advancement: hdfv:tutorial/loaded
---

每样东西都有重量：一锭或一份料件 10 公斤，零件照它的设计算，装满的油桶连油一起算。打开储物界面时，鼠标移到物品上，说明里就会标出重量；界面下方显示用了几格、总共多重。

## 储物方块
{items hdfv:storage_crate hdfv:steel_locker hdfv:parts_rack hdfv:shipping_container}
- {item hdfv:storage_crate}：27 组，用木材和铁粒做，最早就做得出来。
- {item hdfv:steel_locker}：一格方块放 54 组。
- {item hdfv:parts_rack}：36 格，只收零件、工件、标准件和图纸。漏斗也放不进别的东西，所以能从混在一起的输送线里把零件挑出来。
- {item hdfv:shipping_container}：108 组，分成两页（用右上角的按钮或鼠标滚轮换页）。

漏斗和管线用起来跟箱子一样。放在组装室、改装厂、喷漆房或修理厂旁，它们就是那个房间的仓库：房间从这里拿零件和材料，拆下来的东西也放回这里。

{recipe hdfv:storage_crate}

## 载具的货舱
有货舱容积的车体模块（轿车的行李厢、皮卡的车斗、货舱）可以载货，每立方米 27 格。坐在车上按{key key.inventory}会打开最近的货舱；人在车外时，状态界面的“货物”分页（空手{key key.sneak}加{key key.use}）可以打开几米内的货舱。

想多载一点，就设计一个**储物箱**（设计界面的“储物”分类）：车顶行李箱、皮卡车斗工具箱或 20 英尺集装箱。它和其他零件一样放进配置：车顶行李箱放在车顶（车身有车顶架就放在横杆上），车斗工具箱和集装箱放在车斗或平台上。

{design roof_box}

## 用机器装卸
把{item hdfv:cargo_loader}放在停车位旁边、正面朝向车子，它就等于停在前面那台车的货舱：漏斗和管线可以像对箱子一样放进、取出，车一开走就没东西可装了。空手使用它，会显示它正在服务哪个货舱。

## 重量
货物的重量会算进物理，作用在货舱的位置：载满的车会沉一点，加速、刹车和过弯都会变差。每个货舱都有额定载重（车体设计的载重、箱子的强度）；超过时会用红字显示，但不会挡你，后果就由车子自己扛。服务器设置 **storage.itemMassScale** 调整所有物品的重量（0：什么都没有重量）；**storage.bucketLitres** 决定一桶水、岩浆或其他 Mod 的流体算几升重（默认 10，和真的水桶一样；油桶的 20 升照实际算）。

{see damage}
