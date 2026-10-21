# 高自由度載具（High Degree of Freedom Vehicles）

[English](README.md) · **繁體中文** · [简体中文](README.zh-CN.md)

Minecraft 1.21.1（NeoForge）的載具 Mod，用工程師的方式造車。每個零件都由你自己設計（引擎從缸體、曲軸到渦輪、噴嘴，
機翼、螺旋槳、旋翼、船身、車身、底盤、車輪與變速箱），在真正的機床工坊裡做出來，再到組裝室把載具組起來。
汽車、卡車、飛機、直升機和船；物理由 [AeroCore](https://github.com/fiseleo/aerocore) 計算。

> Beta（0.x）：版本之間會盡量讓存檔繼續能用，但格式仍可能變動。

## 功能

- **什麼都能設計。** 每一種零件都有設計畫面，即時檢查、3D 預覽與試算台：把引擎放上測功機、讓機翼跑一遍極曲線、讓車上測試場。
  活塞引擎（汽油與柴油、渦輪增壓）、渦噴、渦扇、渦槳、渦軸、衝壓、超燃、脈衝噴射、火箭、氫燃料。
- **自己做出來。** 新的礦物與冶煉、合金爐、鑄造爐與鍛壓機、標準件、可以加長的多方塊車床、銑床、鉋床、搪床與鑽床，
  九族機加工（磨床、齒輪、放電、雷射、水刀、鉚接……）、最多 12 軸的 CNC、焊接。每個零件都依自己的真實工序路線製作。
- **接上動力。** 踏板、蒸汽機、天軸、發電機、電線、電池，以及裝在引擎座上的你自己設計的引擎。
- **組起來。** 汽車廠、機庫和造船廠：你自己蓋的房間，在裡面配置載具，由天車和焊工用你的零件造出來，再開出廠。
- **加油。** 油田、鑽井架與抽油機、常壓蒸餾塔與製程單元（FCC、重組、裂解、聚合）、加油機。
- **開出去。** 手排與自排變速箱、四驅、儀表、車燈；飛行操作、起落架、機場與有 ATC 的塔台；第五輪拖車。
- **改成你的。** 改裝廠（造型、大小、空力套件、輪圈、調校、車燈）與可以貼自己圖片的烤漆房。
- **聽得到。** 所有聲音都依運轉中的引擎或機器即時合成。

## 怎麼開始

新玩家會拿到《**載具工程手冊**》：右鍵打開。它從第一塊礦石一路教到第一台車、飛機和船，之後每個分支各一章。
進度畫面有「高自由度載具」分頁，跟著手冊走；每個 hdfv 物品的說明都寫著它的科技階段與在哪裡做。裝了 JEI 的話，所有配方與機加工工序也都查得到。

## 需求

| | |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.252 以上（21.1.x） |
| AeroCore | 0.41.x（必要） |
| JEI | 選用：配方與說明頁 |
| Create | 選用：部分材料可用 Create 的粉碎、攪拌與輾壓 |
| Java | 21 |
| 端 | 客戶端與伺服器都要裝 |

## 多人與伺服器

- 伺服器設定（`serverconfig/hdfv-server.toml`）：一錠多少金屬（10 公斤）、一個工作小時多久（1000 tick）；刀具磨耗與切屑回收；
  貼紙（上傳、上限）；機場生成；新手教學的手冊與歡迎訊息。
- 貼紙是玩家上傳的圖片，伺服器上所有人都看得到。管理員可以列出與刪除：`/hdfv decal list|remove|purge`。
- 管理員：`/hdfv vehicle samples` 寫出範例載具藍圖，給 AeroCore 的 `/aerocore vehicle spawn` 用。
- 客戶端設定（Mod 清單 → 高自由度載具 → 設定）：開車、聲音音量、航空單位。

## 從原始碼建置

需要 JDK 21，以及放在旁邊的 AeroCore 原始碼：hdfv 用 AeroCore 的 Maven 資料夾建置（`../AeroCore/build/repo`；先在 AeroCore 執行 `publish`）。

```
./gradlew build                          # 遊戲 JAR 在 neoforge 的 build/libs
./gradlew :core:test                     # 單元測試（約 390 個）
./gradlew :neoforge:runGameTestServer    # GameTest（約 140 個）
```

Windows 上 `.\dev.ps1 build` 會自己找 JDK 21。開發用程式（GameTest、`/hdfv dev`、`/hdfvdev`、測試車）放在 `neoforge/src/dev`
與 core 的 test fixtures，不會發布；`verifyReleaseJar` 會檢查 JAR。`tools/` 裡是配方、資產與語言檔的產生器（Python）
以及模型與貼圖的 Blender 腳本。

## 已知限制

- 載具好不好全看設計：設計不好的車真的會慢，引擎選錯真的會熄火。
- 物理都對照過公開資料，但仍是遊戲用的物理（見 AeroCore 的文件）。
- 效能取決於同時運作的載具與機器數量；極高速的載具需要前方的世界已載入。
- 使用光影包（Iris）時零件改用原版著色；裝了 Sound Physics Remastered 時，hdfv 自己的迴響會關掉。

## 授權

高自由度載具以 [GNU 較寬鬆通用公共授權條款第 3 版（僅此版）](COPYING.LESSER)（LGPL-3.0-only）發布，它建立在
[GNU GPL 第 3 版](COPYING) 之上。來自 NeoForge MDK 範本的檔案沿用它們的 [MIT 授權](TEMPLATE_LICENSE.txt)。
模型與貼圖都由 `tools/` 裡的腳本產生，適用同一授權；沒有任何音檔。

歷史：[CHANGELOG](CHANGELOG.md)；公開之前的開發紀錄在 [docs/HISTORY.zh-TW.md](docs/HISTORY.zh-TW.md)。
