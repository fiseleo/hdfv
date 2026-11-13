---
id: damage
section: branch
order: 9
title: 损坏与修理
icon: hdfv:repair_shop_controller
advancement: hdfv:tutorial/repaired
---

载具的每个零件都有自己的状况，从全新一路到报废：轮胎、刹车、悬吊、齿轮与离合器、发动机本体、发动机附件、涡轮的热段、油箱、结构。零件耗损了，表现就跟着变差：抓地力变小、刹车变弱、动力或推力下降。

## 什么会让零件耗损
- 使用：胎纹、摩擦片、离合器片、齿轮、发动机本体和涡轮热段都会随着运转磨耗。转速超过红线，发动机会磨得快上好几倍；原地烧胎不到一分钟就能磨掉一对轮胎。
- 碰撞：撞击的能量会分给撞到的零件，猛然停下时车上的人也会受伤。
- 攻击、爆炸、火与岩浆。漏油的油箱碰到火，或油箱被炸破，燃料就会爆炸。
- 泡水：发动机或马达泡在水里大约二十秒就会坏掉（船体完好就不会进水）。
- 腐蚀：漆面完好时很慢（停在雨中一天大约 1 %，海边更快），撞凹、漆面破掉的地方就快得多；停在屋顶下几乎不会生锈。

车体或底盘耗损到底，这台车就全毁了，发动机也会跟着停摆、发不动。

## 查看状况
空手对着载具蹲下按使用（{key key.sneak}加{key key.use}）会打开状态界面：“状况”分页显示每个零件耗损了多少，“修理”分页列出每一项要怎么修、去哪里修，“货物”分页是它的货舱。开车时，仪表上方的警示灯会提醒发动机、刹车、爆胎、变速箱、车体和漏油。

## 就地急修
{items hdfv:field_repair_kit hdfv:tyre_patch_kit}
- 看着要修的零件，对载具按住{key key.use}几秒钟；视线移开或走远就会中断。
- {item hdfv:field_repair_kit}能修补结构、附件和油箱：每次最多 15 %，最多只修到耗损一半，可用 8 次。
- {item hdfv:tyre_patch_kit}把漏气的轮胎塞好，打气到剩一半。胎纹磨平不算漏气，那就得换新胎。可用 4 次。
- 刹车、离合器、齿轮、发动机内部和悬吊，还有耗损 80 % 以上的东西，工具包都修不了，要交给修理厂。

{recipe hdfv:field_repair_kit}

## 修理厂
和改装厂一样：{item hdfv:repair_shop_controller}装在封闭房间的墙上，房间要有门和两盏{item hdfv:work_light}，控制台要接电；工作需要焊接时，房间里还要有{item hdfv:welding_station}。

{items hdfv:repair_shop_controller hdfv:work_light hdfv:welding_station hdfv:friction_lining}
- 把载具开进去，所有人下车，等它停稳。
- 按“扫描”，在界面上勾选要修的项目（或按“所有损伤”），再按“开工”。它会从房间旁的箱子和储物方块取出需要的材料，接着用电施工，做满这份工作的工时。
- 磨耗或受损的部分用料件和标准件修：刹车和离合器换{item hdfv:friction_lining}，齿轮换轴承，附件用电线和钢管。轮胎一律换新。
- 拆下来的旧料会退回房间旁的箱子。

## 超过 80 %
耗损到 80 % 以上就不修了，改换一个照同一份设计新做的：发动机或变速箱换上新的{item hdfv:part}，要先放进房间旁的箱子；刹车、悬吊、支柱或离合器，照零件的蓝图在机床上做出新的工件；车体、底盘或机翼则在原地重造。全毁的车，车体要和发动机一起修，或者先修车体。

服务器设置在 **damage** 底下：enabled、wearScale、collisionScale、attackScale、corrosionScale、occupantInjury 和 explosions；设成 0 或 false 就关掉那一项。

{see cargo}
