---
id: damage
section: branch
order: 9
title: 損壞與修理
icon: hdfv:repair_shop_controller
advancement: hdfv:tutorial/repaired
---

載具的每個零件都有自己的狀況，從全新一路到報廢：輪胎、煞車、懸吊、齒輪與離合器、引擎本體、引擎附件、渦輪的熱段、油箱、結構。零件耗損了，表現就跟著變差：抓地力變小、煞車變弱、動力或推力下降。

## 什麼會讓零件耗損
- 使用：胎紋、來令片、離合器片、齒輪、引擎本體和渦輪熱段都會隨著運轉磨耗。轉速超過紅線，引擎會磨得快上好幾倍；原地燒胎不到一分鐘就能磨掉一對輪胎。
- 碰撞：撞擊的能量會分給撞到的零件，猛然停下時車上的人也會受傷。
- 攻擊、爆炸、火與岩漿。漏油的油箱碰到火，或油箱被炸破，燃料就會爆炸。
- 泡水：引擎或馬達泡在水裡大約二十秒就會壞掉（船體完好就不會進水）。
- 腐蝕：漆面完好時很慢（停在雨中一天大約 1 %，海邊更快），撞凹、漆面破掉的地方就快得多；停在屋頂下幾乎不會生鏽。

車體或底盤耗損到底，這台車就全毀了，引擎也會跟著停擺、發不動。

## 查看狀況
空手對著載具蹲下按使用（{key key.sneak}加{key key.use}）會打開狀態畫面：「狀況」分頁顯示每個零件耗損了多少，「修理」分頁列出每一項要怎麼修、去哪裡修，「貨物」分頁是它的貨艙。開車時，儀表上方的警示燈會提醒引擎、煞車、爆胎、變速箱、車體和漏油。

## 就地急修
{items hdfv:field_repair_kit hdfv:tyre_patch_kit}
- 看著要修的零件，對載具按住{key key.use}幾秒鐘；視線移開或走遠就會中斷。
- {item hdfv:field_repair_kit}能修補結構、附件和油箱：每次最多 15 %，最多只修到耗損一半，可用 8 次。
- {item hdfv:tyre_patch_kit}把漏氣的輪胎塞好，打氣到剩一半。胎紋磨平不算漏氣，那就得換新胎。可用 4 次。
- 煞車、離合器、齒輪、引擎內部和懸吊，還有耗損 80 % 以上的東西，工具包都修不了，要交給修理廠。

{recipe hdfv:field_repair_kit}

## 修理廠
和改裝廠一樣：{item hdfv:repair_shop_controller}裝在封閉房間的牆上，房間要有門和兩盞{item hdfv:work_light}，控制台要接電；工作需要焊接時，房間裡還要有{item hdfv:welding_station}。

{items hdfv:repair_shop_controller hdfv:work_light hdfv:welding_station hdfv:friction_lining}
- 把載具開進去，所有人下車，等它停穩。
- 按「掃描」，在畫面上勾選要修的項目（或按「所有損傷」），再按「開工」。它會從房間旁的箱子和儲物方塊取出需要的材料，接著用電施工，做滿這份工作的工時。
- 磨耗或受損的部分用料件和標準件修：煞車和離合器換{item hdfv:friction_lining}，齒輪換軸承，附件用電線和鋼管。輪胎一律換新。
- 拆下來的舊料會退回房間旁的箱子。

## 超過 80 %
耗損到 80 % 以上就不修了，改換一個照同一份設計新做的：引擎或變速箱換上新的{item hdfv:part}，要先放進房間旁的箱子；煞車、懸吊、支柱或離合器，照零件的藍圖在機床上做出新的工件；車體、底盤或機翼則在原地重造。全毀的車，車體要和引擎一起修，或者先修車體。

伺服器設定在 **damage** 底下：enabled、wearScale、collisionScale、attackScale、corrosionScale、occupantInjury 和 explosions；設成 0 或 false 就關掉那一項。

{see cargo}
