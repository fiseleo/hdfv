---
id: cargo
section: branch
order: 10
title: 貨物與儲物
icon: hdfv:shipping_container
advancement: hdfv:tutorial/loaded
---

每樣東西都有重量：一錠或一份料件 10 公斤，零件照它的設計算，裝滿的油桶連油一起算。打開儲物畫面時，滑鼠移到物品上，說明裡就會標出重量；畫面下方顯示用了幾格、總共多重。

## 儲物方塊
{items hdfv:storage_crate hdfv:steel_locker hdfv:parts_rack hdfv:shipping_container}
- {item hdfv:storage_crate}：27 組，用木材和鐵粒做，最早就做得出來。
- {item hdfv:steel_locker}：一格方塊放 54 組。
- {item hdfv:parts_rack}：36 格，只收零件、工件、標準件和圖紙。漏斗也放不進別的東西，所以能從混在一起的輸送線裡把零件挑出來。
- {item hdfv:shipping_container}：108 組，分成兩頁（用右上角的按鈕或滑鼠滾輪換頁）。

漏斗和管線用起來跟箱子一樣。放在組裝室、改裝廠、烤漆房或修理廠旁，它們就是那個房間的倉庫：房間從這裡拿零件和材料，拆下來的東西也放回這裡。

{recipe hdfv:storage_crate}

## 載具的貨艙
有貨艙容積的車體模組（轎車的行李廂、貨卡的車斗、貨艙）可以載貨，每立方公尺 27 格。坐在車上按{key key.inventory}會打開最近的貨艙；人在車外時，狀態畫面的「貨物」分頁（空手{key key.sneak}加{key key.use}）可以打開幾公尺內的貨艙。

想多載一點，就設計一個**儲物箱**（設計畫面的「儲物」分類）：車頂行李箱、貨卡車斗工具箱或 20 呎貨櫃。它和其他零件一樣放進配置：車頂行李箱放在車頂（車身有車頂架就放在橫桿上），車斗工具箱和貨櫃放在車斗或平台上。

{design roof_box}

## 用機器裝卸
把{item hdfv:cargo_loader}放在停車位旁邊、正面朝向車子，它就等於停在前面那台車的貨艙：漏斗和管線可以像對箱子一樣放進、取出，車一開走就沒東西可裝了。空手使用它，會顯示它正在服務哪個貨艙。

## 重量
貨物的重量會算進物理，作用在貨艙的位置：載滿的車會沉一點，加速、煞車和過彎都會變差。每個貨艙都有額定載重（車體設計的載重、箱子的強度）；超過時會用紅字顯示，但不會擋你，後果就由車子自己扛。伺服器設定 **storage.itemMassScale** 調整所有物品的重量（0：什麼都沒有重量）；**storage.bucketLitres** 決定一桶水、岩漿或其他 Mod 的流體算幾公升重（預設 10，和真的水桶一樣；油桶的 20 公升照實際算）。

{see damage}
