---
id: workshop
section: main
order: 6
title: 圖紙、工件與工序
icon: hdfv:workpiece
advancement: hdfv:tutorial/machined
---

你設計的每個零件都是一張圖紙。它的每個元件都有一條**路線**：做出它的工序（鑄造、車削、鑽孔、銑削、研磨、焊接……），由設計本身推算出來。
- 把圖紙放進機器：它做路線上自己做得到的工序（以它的精度），交出一個記得做到哪一步的{item hdfv:workpiece}。
- 把工件連同圖紙拿到下一台機器。如果這台做不了下一步，它會告訴你該去哪一台。
- 最後一步交出完成的元件；一個零件的元件全部做好後，{item hdfv:assembly_bench}把它組成{item hdfv:part}。

JEI 的機加工頁面會寫每台機器能做什麼、精度到 IT 幾級。時間是實際工時：預設一個工作小時等於 Minecraft 的一小時（伺服器設定可改）。

{see electricity}
