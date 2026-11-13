# High Degree of Freedom Vehicles 開發代理指南

本文件適用於整個專案。開始修改前先讀本文件，再讀相關原始碼與 AeroCore 的 `AGENTS.md`（物理與計算 API 的約定在那裡）。
與使用者溝通以繁體中文為主，說明完成的功能、驗證結果，以及仍有的模型限制與未驗證項目。

## 專案目標與現況

- Mod ID `hdfv`。前置是 AeroCore（`../AeroCore`，0.28 起）。
- 使用者 2026-10-11 決定的方向：
  - 《Automation》式高自由度：玩家自己設計每個零件，參數自由、形狀也是參數，再用零件組合出載具，陸海空都要。
  - 合成是 CNC／機加工：新增礦石與冶煉；機床先只花時間，之後加 Forge Energy 與發電機。
  - 設計畫面可由設計桌方塊或設計平板物品打開。測試有兩種：設計畫面試算藍圖，測功機／推力台方塊測做出來的實物。
- 分工（使用者：「AeroCore 是計算 API」，零件「全部搬到 hdfv」）：
  - **hdfv**：零件設計（參數、檢查與容差、外推範圍、藍圖 JSON、質量估計）、引擎執行時的控制規則（起動、怠速、斷油、
    噴射燃油控制、喘振規則）、零件外觀、設計畫面、測試台、物品與方塊、礦石與冶煉、CNC、組裝、遊戲平衡。
  - **AeroCore**：所有物理與從設計抽出的計算（`api.engine` 的 `EngineCycle`、`EngineMap`、`Turbojet`、`PistonGeometry`、
    `Turbomachinery`…）、零件的執行時受力（`api.vehicle.part`）、通用載具框架、`PartVehicle`、同步與存檔、
    海浪與尾浪的計算與渲染。
  - hdfv 缺什麼物理或計算，就到 AeroCore 補成通用 API，不要在 hdfv 自己寫一份。
- 0.1.0（2026-10-11）：專案骨架；從 AeroCore 0.19 接收所有零件設計，類型 ID 改成 `hdfv:*`；範例；
  `/hdfv vehicle samples`；引擎外觀；搬來的 JUnit 與 GameTest。
- 0.2.0（2026-10-11）：設計畫面與試算；設計桌、設計平板、空白藍圖、藍圖（`hdfv:design`）；貼圖、掉落表。
  使用者當時「合成先不寫」「等有 CNC 那些的再寫合成」；0.7 有了 CNC，合成隨之加入（GameTest 檢查配方、掉落、名稱與分頁）。
  使用者要求 hdfv 的方塊與物品放在自己的創造模式分頁（`HdfvRegistry.TAB`，「高自由度載具」）；新的方塊與物品都加進那個分頁。
- 0.3.0（2026-10-11）：渦扇、渦槳、渦軸（使用者要求「加入 Turbojet…Turbofan…Turboprop…Turboshaft…Ramjet…Scramjet…Pulsejet」，
  選「現在先做」、軸功率「接到載具傳動軸」）。計畫的後續階段：0.4 翼型、機翼、空氣螺旋槳、旋翼（「機翼與輪子也是高自由度」）；
  0.5 輪子；0.6 衝壓、超燃衝壓、脈衝噴射；之後才是 CNC 與合成。
- 0.4.0（2026-10-11）：機翼、空氣螺旋槳、直升機旋翼（`dev.mcmod.hdfv.part.air`），水上飛機與直升機範例，飛行按鍵。
- 0.5.0（2026-10-12）：模組化的機身、船身、車身（`dev.mcmod.hdfv.part.body`；使用者「機倉也要高自由度 像模塊一樣」「車身 船身 也是」，
  選「分段模組串起來」「先做機身／船身／車身」），範例水上飛機與直升機改用機體與掛載點。
- 0.6（進行中，計畫 H1～H6；AeroCore 0.24）：輪子、傳動與四驅。使用者 2026-10-12 選了手排（自己踩離合器、會熄火、啟動馬達重新發動）、
  液力自排、DCT、CVT 四種變速箱，全時、分時、適時三種四驅；2026-10-13 要求「車身不應該有水動力模型」「設計 GUI 要有分類」
  「加入新的設計」「3D model 還要更逼真」。已完成 H1（車身的引擎室與車軸、數字清單、引擎 `starter`）、H2（車軸、起落架、ABS、試算）、
  H3（變速箱、分動箱、換檔規則、試算）與設計畫面的分類、新的設計、新零件的 3D 模型；H4～H6 未做。
- 0.7.0（2026-10-13；AeroCore 0.25）：製造。使用者：「先搞車床／銑床／鉋床／搪床／鑽床 & CNC & 焊接 和 組裝室（組裝室是一個結構），
  載具是組裝才能開，飛機、船、車要不同組裝室，村民要有新職業：焊工」，0.6 的 H4～H6 暫停。使用者的選擇：礦石與冶煉一起做、1 錠＝10 kg、
  真實 1 小時＝遊戲 1 小時（兩者是伺服器設定）、依設計自動拆工序、手動機床要人在機床前開著介面、元件級、非切削製程做簡化版、
  組裝室是彈性房間＋控制台、整車配置在組裝室畫面裡排、焊工交易＋在組裝室工作。
- 0.8.0（2026-10-14；AeroCore 0.26）：動力、CNC 多方塊、組裝室外觀、柴油引擎、石油工業與載具加油。使用者 2026-10-13：「車床／銑床／鉋床／
  搪床／鑽床（要電或是動力），CNC 工具機最多可以達到 12 軸，要是多方塊；組裝室還要更好看；CNC 要電；加入石油工業」。使用者的選擇：
  - 電力（Forge Energy：發電機、電線、電池）與機械動力（天軸、皮帶）都要；原動機是玩家設計的引擎裝在引擎座上；CNC 只吃電；
    起步用人力踏板與可合成的蒸汽機；其他機器也要能源（鑄造爐、複材爐、硫化機燒燃料或電熱，鍛壓機電或天軸，焊接台電，合金爐照舊）。
  - CNC 是多方塊加模組：3 軸加工中心起，旋轉台與搖籃台到 4／5 軸，車削主軸、副主軸、刀塔到車銑複合 9～12 軸。
  - 石油全套：油田與抽油機、煉油廠（蒸餾、FCC、重組、蒸汽裂解）、石化（塑膠、合成橡膠、環氧、潤滑油；煤焦油路線照舊）、載具要加油。
  - 柴油（壓縮點火）與渦輪增壓這次一起做；組裝室外觀全部（天車模型與吊裝、依進度出現的載具、火花與聲音、門的動畫、工作燈）；一次做完。
  - 衝壓、超燃衝壓、脈衝噴射順延到 AeroCore 0.27。
- 0.9.0（2026-10-14；AeroCore 0.27）：多方塊手動機床與石油工業的畫面。使用者 2026-10-14：「車床／銑床／鉋床／搪床／鑽床（要電或是動力）
  要是多方塊；石油工業要有 GUI 配置」。使用者的選擇：
  - 機床可加長，大小限制能做的工件（放不下就說）：車床床身 1～6 m、銑床工作台 1～4 m、鉋床 2～8 m、搪床立柱 1～4 段、鑽床立柱 1～3 段。
  - 動力由一格驅動決定：電動機座吃電線、皮帶輪座接天軸，換方塊就換動力；控制方塊本身不再吃電或天軸。
  - 手動機床也有附件，附件影響能做什麼與工時（尾座、中心架、刀座磨頭、快換刀架、分度頭、迴轉台、第二刀架、側刀架、平旋盤、多軸頭、動力進給、
    鑽模板、數位讀數尺）。
  - 石油畫面設操作與製程條件，製程條件由 AeroCore 0.27 的模型改變收率：蒸餾塔的切點與減壓段、FCC 提升管出口溫度、重組辛烷值、裂解爐管出口溫度；
    抽油機的衝次、衝程、柱塞，井口油嘴，鑽井目標深度，加油機的加油量；出料罐裝什麼。名義條件下的數字與 0.8 相同。
  - 衝壓、超燃衝壓、脈衝噴射順延到 AeroCore 0.28。
- 0.10.0（2026-10-14；AeroCore 0.28）：衝壓、超燃衝壓、脈衝噴射。使用者 2026-10-14「下一步」，選擇：
  - 一起加入固體火箭助推器、後燃器（渦噴與混合排氣渦扇）、J58 式渦輪衝壓、氫燃料。
  - 超燃照物理做（試算台到 20 km 以上）**而且**處理遊戲中的極高速（AeroCore 0.28 的掃掠碰撞、區塊票、外推）。
  - 脈衝噴射有閥（簧片閥會磨損）與無閥都做；靜止要吹氣起動；有聲音與震動。
  - 範例引擎、試算台、世界中的脈衝噴射艇；推進劑 KNSB（火藥當硝石＋糖）與 APCP（過氯酸銨＝火藥＋紅石在合金爐、鋁、合成橡膠）；
    助推器可燒完拋棄（掉成物品）或用藥柱重新裝填；氫要液態（模擬蒸發、要保溫）與 700 bar 壓縮兩種。
- 0.11.0（2026-10-15；AeroCore 0.29）：逼真的引擎 3D。使用者 2026-10-15「3d model 還要更逼真 我有裝blender」，接著「機器也要用blender」。選擇：
  - 範圍是所有引擎（設計畫面與世界中都要），機器排在 0.12（「引擎先、機器後」）。
  - 重點：幾何細節、材質貼圖、光影、世界中的效果，四項都要。
  - Blender 做細節庫與貼圖，程式依設計尺寸縮放擺放，並用 Blender 算圖對照。機器方塊貼圖每格 64 像素（0.12）。
  - 計畫：本機計畫檔 `frolicking-imagining-corbato.md`（AeroCore R1～R6、hdfv V0、B1、B2、E1～E4、W1、W2、V11；0.12 M1～M4、V12）。
- 0.12.0（2026-10-17；AeroCore 0.30）：機器與載具的逼真 3D。使用者 2026-10-15「機器 載具 也用blender 做」。選擇：
  - 機器和載具一起出一版；內裝要完整（窗真的開孔、透明玻璃、座椅、儀表板、方向盤或駕駛桿、踏板、客艙座位排）；
    世界效果四項都要（燈光、可動部件、地面與水面效果、組裝室裡製造中的載具）；外觀參數要（只影響外觀）；
    開車按鍵先加最基本的（煞車、P/R/N/D）；起落架阻力只給可收放的。
  - 計畫：本機計畫檔 `frolicking-imagining-corbato.md`（AeroCore A1～A7；hdfv V0、B3、M1～M4、V1～V7、V12）。
  - 2026-10-17 使用者「Minecraft 開在第2個螢幕」：AutoShot 計畫加 `monitor 2`（AeroCore 0.30 的步驟）。
    2026-10-18 使用者「我在打lol 的時候 開minecraft 都在第2個螢幕 還有把不要搶滑鼠」：AeroCore 0.33.1 的開發用視窗（`DevWindow`）——
    `runClient`、`runClient2` 帶 `-Daerocore.window.monitor=2`（`-Pdev_window_monitor=<n>` 換，0 照原樣），一開就在第 2 個螢幕、不取得焦點、不鎖滑鼠，
    並在啟動前把 `client`／`client2` 的 `config/fml.toml` 改成 `earlyWindowControl = false`（NeoForge 的早期載入視窗開在主螢幕並取得焦點）。
    `neoforge/build.gradle` 的這段不要拿掉；計畫裡的 `monitor 2` 可留可不留。
- 0.13.0（2026-10-17；AeroCore 0.31）：痛車與烤漆房。使用者 2026-10-17「你先優化 加入痛車」。選擇：
  - 圖片在遊戲裡上傳、多人同步，並加入烤漆房；多張貼紙、位置可調；所有機身；不附示範圖（Mod 不帶任何圖）。
  - 計畫：本機計畫檔 `frolicking-imagining-corbato.md`（AeroCore A1；hdfv T1～T6）。T1 在另一個工作階段做。
- 0.14.0（2026-10-17；AeroCore 0.32）：開車操作、儀表與 0.13 的驗證。使用者 2026-10-17 選「先補 0.13 的驗證」，接著
  「不要補7 台範例車 ，加入換檔/手煞車/四驅模式等操作  儀表畫面」。選擇：同意 Minecraft EULA（開本機專用伺服器測多人）、選檔視窗用腳本測、
  拖檔由使用者手動試、開車時 R 升檔 F 降檔、指針式雙圓錶。計畫：本機計畫檔 `frolicking-imagining-corbato.md`。
  - 0.14 追加（使用者 2026-10-17「汽車和引擎之間有破圖」「還有內裝怪怪的 還有加入引擎的大小要符合大小才能裝車」）：引擎室的零件要放得下
    （整個引擎都算，底部開著；引擎、變速箱、分動箱）、引擎模型改緊湊並往後挪、車的座位改「前 2 後 3」。同一個計畫檔的 F1～F6。
- 0.15.0（2026-10-17；AeroCore 0.33）：底盤高度自由化。使用者 2026-10-17「底盤也要高度自由化」，選擇：
  - 範圍四項都要：車架種類（梯形大樑、管架、脊骨式，加上原本的承載式）、車軸自由擺放（6×6、8×8、任一軸轉向）、懸吊硬點、車高與離地
    （離地間隙、接近角與離去角、氣壓懸吊）。
  - 形式：獨立的底盤零件，有自己的設計畫面與 CNC／焊接合成；剛性「都要」（質量與強度檢查，也進物理）；承載式車身的扭轉「也做」。
  - 計畫：本機計畫檔 `frolicking-imagining-corbato.md`（AeroCore A1～A6；hdfv H1～H6）。
- 0.16.0（2026-10-18；AeroCore 0.34）：改裝廠。使用者 2026-10-18「加入載具調整系統 可以有自由外觀和大小」，接著「車燈也是要自由擺放」。選擇（都是建議項）：
  - 新的「改裝廠」房間＋控制台（掃描同烤漆房）：已組好的車開進去改，改完重跑組裝室的檢查，原地換成新車（保留油量與位置）；烤漆房照舊只管塗裝與貼紙。
  - 範圍四項都要：機體造型、外掛空力套件（下壓力與阻力進物理）、輪圈與胎壁字、性能調校。
  - 大小兩種都要：整台一個倍率（零件照物理重算）與各部分分別調（機體各段、輪胎、輪距、軸距、底盤）。
  - 照實花費（新工件、料、烤漆罐、燈具、工時與電；拆下的退回）＋重新檢查。
  - 車燈：現有的燈種都能擺、車身任一點、可增刪改樣式（上限 32 盞）、每盞一個燈具；航行燈放錯邊是警告。
  - 計畫：本機計畫檔 `lovely-launching-avalanche.md`（AeroCore A1～A3；hdfv H1～H7）。
- 0.16.1（2026-10-18；AeroCore 0.35.1）：空氣套件的下壓力對量產車校準。0.16.0 的範例轎車加溫和套件在 200 km/h 有 4.0 kN（車底 2.96 kN），
  比整台量產賽道車還多；AeroCore 0.35.1 的車底加了車輪、漏氣面積的側裙、擋風板分離區取代車底壓力，hdfv 的側裙封閉再乘側裙長度占車底的比例
  （只在輪拱之間）。同一套件變成 1.78 kN（車底 0.75 kN），轎車車身自己約 0.74 kN 升力，整車淨下壓力約 1.0 kN。
- 0.17.0（2026-10-18；AeroCore 0.35）：所有機加工機器。使用者 2026-10-18「加入所有機加工機器」，選擇：
  - 九族都要：磨床（外圓、內圓、平面、無心、工具磨）、精整（珩磨、精研、拋光、去毛邊）、齒輪（滾齒、插齒、刮齒、磨齒、倒角）、
    往復（牛頭鉋、插床、拉床）、鋸（帶鋸、圓鋸、弓鋸）、電加工（成形放電、線切割、放電鑽孔、電化學）、切割台（雷射、電漿、水刀）、
    螺紋（滾牙、搓絲、攻牙、銑牙）、鉚接（壓鉚、衝擊鉚、自動鑽鉚）。
  - 形式：每族一座可加長的多方塊，換「頭」換機種。操作：傳統機手動，可裝數控驅動座；電加工與切割台本身數控。動力同 0.9，放電、雷射、電漿只吃電。
  - 工序改成真實工序，舊機器能做的照樣能做；舊工件（非建議項）轉到新路線對應的步驟，可能要補做新插入的步驟。
  - 耗材「都要」、新舊機器都吃（刀具依 Taylor 壽命，工具磨床可重磨）；新增空氣分離機（氧、氮、氬）；外觀全套；配方與 JEI 製程頁；一版做完。
  - 順序：改裝廠（0.16）先、機加工接著；AeroCore 0.35 的計算類別先做（0.35.1 的車底校準也在同一版號後面）。
  - 計畫：本機計畫檔 `foamy-giggling-crescent.md`（AeroCore A1～A4；hdfv H0～H8）。
- 0.18.0（2026-10-18；AeroCore 0.36）：車頭、平頭卡車與鉸接拖車。使用者看了改裝廠的車說「車頭應該是長這樣」（三張參考圖：Astra G 類轎車、
  Panda 類方正小車、Scania 類平頭牽引車＋鵝頸低底板三軸拖車），選擇：
  - 車頭細節用「參數＋自動預設」：車的機體多一頁 `front`（水箱罩、頭燈組、保險桿、下進氣口、霧燈、引擎蓋隆起），預設依車頭造型自動。
  - 平頭駕駛室做成「新的模組種類」（`CAB_OVER`），拖車平台是 `DECK`。
  - 「做鉸接拖車」：AeroCore 的第五輪鉸接、拖車零件（第五輪、支撐腳）、在世界中按 J 掛上與卸下。
  - 「新增範例機體」：範例零件（小掀背、平頭駕駛室、低底板平台、6×4 牽引車與三軸鵝頸低底板大樑、第五輪、支撐腳）；不加整台範例車，
    測試車只在 `TestCars`（`tractor6x4`、`lowLoader`）與開發指令（`/hdfv dev drive spawn tractor|lowloader|semi`）。
  - 計畫：本機計畫檔 `lovely-launching-avalanche.md`（AeroCore A1～A5；hdfv H1～H6）。
- 0.19.0（2026-10-19；AeroCore 0.37）：聲音。使用者 2026-10-18「加入聲音」，選擇（都是建議項，另加音爆）：
  - 範圍：載具三類（引擎與推進、行駛與環境、車上操作）、機器四類（機加工與 CNC、組裝室／改裝廠／烤漆房、動力、石油與化工）。
  - **全部程式合成**、不帶音檔；都卜勒與傳播延遲、真實距離衰減與空氣吸收、室內迴響與隔音（OpenAL EFX，只套 hdfv 的聲音）、音爆與超音速。
  - 最近或最大聲的幾台載具（設定，預設 4）各一條混音串流，其他用循環聲；機器與一次性的聲音用一般聲道。
  - 多人：車外附近的人都聽得到（各客戶端用同步的狀態自己合成，撞擊由伺服器送），坐在封閉車艙裡的人聽車內版本。
  - 音量：載具歸「中立生物」、機器歸「方塊」，加 hdfv 的分項音量（Mod 清單的設定畫面）。
  - 計畫：本機計畫檔 `sharded-brewing-lemon.md`（AeroCore A1～A5；hdfv H1～H6）。
- 0.20.0（2026-10-19；AeroCore 0.38）：機場塔台與機場。使用者 2026-10-19「加入 機場塔台 你模型還有更好看」，選擇：
  - 塔台功能四項都要（雷達與航空器清單；風與 ATIS 加風向袋；跑道燈、進場燈與標線；起降許可，沒有許可只警告、不擋）。
  - 多方塊、塔身可加高（越高雷達與視距越遠）；取得：合成、世界生成的機場、新村民「塔台管制員」。
  - 雷達與燈要電，生成的機場附充滿的蓄電池；管制員村民自動管制，玩家可在塔台畫面接手（多人可以）；航空單位，可在用戶端改公制；
    生成的機場「小型、少見」（跑道 600～900 m × 23 m，平原、草原、沙漠、雪原約每 3～4 km 一座，伺服器設定可關）。
  - 分兩版：先塔台與機場（模型直接做到新水準），再全面美化（0.21，AeroCore 0.39）；沒有參考圖，照常見的中型機場塔台做。
  - 計畫：本機計畫檔 `tranquil-hatching-matsumoto.md`（AeroCore A1～A5；hdfv B1～B8；0.21 的大綱）。
  - 2026-10-19 完成（未 commit）：core 371、GameTest 127 全過，遊戲中兩輪與在新區塊的複驗見版本表；已知限制在〈約定〉的「機場」。
- 0.21.0（2026-10-20；AeroCore 不變，仍 0.38）：塔台內部與大、中、小機場。使用者 2026-10-19 看了塔底的截圖說「開始做 0.21 全面美化 塔台裡面可以更好看，
  還有大小機場之生成」，選擇：
  - 「現在的機場為大機場，中、小為縮小」；大機場約每 10～15 km 一座。
  - 塔台內部四項都要：塔底機房與大廳、塔身改樓梯、管制室內裝、夜間的室內燈光。
  - 版本：0.21＝塔台內部＋大中小機場，0.22＝全面美化（AeroCore 0.39）。
  - 計畫：本機計畫檔 `tranquil-hatching-matsumoto.md`（最上面一段；0.20 的在附錄）。
- 0.22.0（2026-10-20；AeroCore 0.39）：全面美化。使用者 2026-10-20「開始做 0.22 全面美化」，補充「美化 是指模型更好看」，選擇：
  - 一版做完；四類模型都要（車；飛機、直升機與船；引擎；機器與塔台）；夜裡的燈是燈罩加光暈（不做全畫面泛光）。
  - 客戶端設定「美化著色」（AeroCore，預設開；關掉是 0.21 的著色），效能維持現在，場景光影保留（接觸陰影、生態域地面色、方塊光暖色高光）。
  - 做了什麼：
    - 材質：每種材質都有法線貼圖與 `beautify` 子物件（凹陷深度依起伏，車漆約 3 %）；機器、成員方塊與機場方塊的貼圖改 128 px
      （`machines.py` 的 `grid()`、`box()` 讓 64 px 的寫法照樣能用，`enhance()` 依 alpha 算浮雕）。
    - 塔台：開放式鋼樓梯（踏板與側樑，沒有豎板）、較暗的格柵、中間調的水磨石。
    - 車輪：通風碟的 36 片葉片（第 0 層）、胎紋只到第 1 層、第 2 層起 24 邊的輪胎；車輪真的轉與轉向（見〈約定〉）。
    - 卡車：圓角油箱加綁帶與加油口、重型車軸的擋泥板。飛機：控制面與主翼之間的縫與暗色凹槽、控制面端蓋、皮托管、大翼的襟翼滑軌整流罩。
      船：艏欄杆、細節庫的纜樁。
  - 計畫：本機計畫檔 `tranquil-hatching-matsumoto.md`（最上面一段）。
- 0.23.0（2026-10-20；AeroCore 0.40）：合成配方與科技樹。使用者 2026-10-20「先改合成配方」，選擇：
  - 範圍四類都要（機器與方塊的工作台配方；冶煉、合金與化學品；石油產品、燃料與推進劑；機加工工序與零件材料）；方向四項都要
    （科技樹順序、更真實的材料與製程、平衡、和其他 Mod 相容）。
  - 機器材料：「新增中間件」＋「機器也用設計與工序做」；用量「統一的遊戲尺度」；冶金「中等：電弧爐＋電解槽」；起步「鑄鍛起步」；
    「機器在組裝台組，建材用工作台」；相容「標籤＋Create 條件配方」（開發環境裝 Create 驗證）；「同意，一版做完」。
  - 計畫：共用筆記資料夾的 `recipes-0.23-plan.md`（原 本機計畫檔 `tiered-forging-recipes.md`）；0.22 的配方盤點在同一處的
    `hdfv-recipe-inventory-0.22.md`。做法與規則見〈約定〉的「合成配方與科技樹」。
- 0.24.0（2026-10-21；AeroCore 0.41）：新手教學與第一次對外發布（Beta）。使用者 2026-10-20「加入新手教學 我要發布要到外面」，
  2026-10-21「AutoShot 可能要移除」。選擇：
  - 教學：進度樹、自己寫的指南書、第一次進入的提示與 JEI 說明頁；主線詳細、分支各一章。
  - 語言：繁中、英文、簡中（新增 zh_cn）。
  - 發布：
    - 0.x、標 Beta；Modrinth、CurseForge、GitHub Releases。
    - 公開 hdfv 原始碼，commit 由代理整理，每次推送先問。
    - 授權兩個專案都 LGPL-3.0（`-only`）。
    - 發布頁要說明、截圖、Logo，不做影片。
  - AutoShot：從發布的 JAR 拿掉，開發環境保留（AeroCore 的 `aerocore_devtools`；hdfv 的 `neoforge/src/dev`，見〈約定〉的「開發用程式」）。
  - 計畫：本機計畫檔 `snoopy-gathering-fog.md`；進度在共用筆記資料夾的 `release-0.41-0.24-progress.md`。
- 0.25.0（2026-11-12～13；AeroCore 0.42）：0.6 的 H4～H6 剩下的、修理系統、儲物箱系統。使用者 2026-11-12
  「H4～H6（變速箱的試算圖、路面坡道方塊） + 還有修理系統 + 還有儲物箱系統」，選擇：
  - 損壞來源四種都要（碰撞、使用磨損、攻擊／爆炸／火、泡水與腐蝕）；預設「寫實」（伺服器設定可調倍率或關掉）；乘員會受傷（可關）。
  - 修理：修理廠＋就地急修；損壞超過 80 % 的零件必須換新（照原設計再做一個）。
  - 儲物三種都要：載具上的儲物（車身的貨艙）、可設計的儲物箱零件、工坊的儲物方塊；貨物算重量。
  - 「一版做完」。H4 的七台範例車使用者在 0.14 已說不補，按鍵與 HUD 在 0.14 做完，這版只補試算圖與坡道方塊。
  - 計畫：本機計畫檔 `inherited-questing-rossum.md`（P1～P6）。
- 下一步：未定。

## 固定開發基底

| 項目 | 目前設定 |
| --- | --- |
| Minecraft | Java Edition 1.21.1 |
| NeoForge | 21.1.252 |
| Java | JDK 21，64 位元 |
| ModDevGradle | 2.0.148 |
| Gradle Wrapper | 9.2.1 |
| Mod ID／Java package | `hdfv`／`dev.mcmod.hdfv` |
| AeroCore | `gradle.properties` 的 `aerocore_version`，由 `../AeroCore/build/repo` 取用 |

版本號以 `gradle.properties` 為準。不要為一般功能順便升級 Minecraft、NeoForge、Gradle 或 Java。
授權：0.24 起 LGPL-3.0-only（使用者 2026-10-20 選擇；`COPYING.LESSER`、`COPYING`；MDK 範本的 `TEMPLATE_LICENSE.txt` 是 MIT），三個檔都打包進 JAR。

## 工作目錄與本機資料

- `Z:\MCMod\HighDegreeOfFreedomVehicles` 與網路資料夾的 UNC 路徑（實際位址在工作區的 `Z:\MCMod\AGENTS.md`，不在倉庫裡）是同一份；兩台電腦共用，
  同一個專案不要同時在兩台的 IDE 開。
- 遊戲世界、設定、記錄與截圖留在本機 `%USERPROFILE%\.mcmod\runs\hdfv`（`client`、`server`、`gametest`），不放進網路資料夾。
  不修改使用者的「新的世界」，測試用 QA 世界；測試結束正常存檔離開，不強制結束有世界開著的遊戲。
- 建置輸出也在本機（2026-10-18，使用者「載入非常慢」）：根 `build.gradle` 把每個專案的 `build` 放到
  `%USERPROFILE%\.mcmod\build\hdfv-<資料夾路徑的雜湊>\{root,core,neoforge}`（每份 checkout 或 worktree 各一份），
  用到的 AeroCore 版本也從 `../AeroCore/build/repo` 鏡像到同一處的 `aerocore-repo`（AeroCore 重新發佈同一版時自動再複製）。
  原因：開發用遊戲每次啟動都從建置資料夾讀 hdfv 的約 2400 個 class 與資源檔和 Minecraft 的 JAR；經網路磁碟（Tailscale）
  直連時讀一遍要 21 秒（本機 0.8 秒），改走中繼（東京，64 ms）時啟動拖到 5 分鐘以上，視窗被 Windows 判為沒有回應。
  `.\dev.ps1 showBuildDirectories -q`、`.\dev.ps1 :neoforge:showRuntimeDirectories -q` 印出位置（測試結果在 `core\test-results`）；
  `-Phdfv_local_build=false` 改回專案裡的 `build`。網路資料夾裡舊的 `core\build`、`neoforge\build` 不再更新。
- 所有遊戲 run 把 `java.io.tmpdir` 與 `jdk.net.unixdomain.tmpdir` 固定在 `%USERPROFILE%\.mcmod\tmp`（`第二台電腦` 少了它建立世界會崩潰）；
  NeoForm 暫存在 Gradle 使用者目錄的 `caches/hdfv-work`。
- git：自己的倉庫，2026-10-21 起依使用者要求上傳到公開的 GitHub `fiseleo/hdfv`。網路磁碟上的 git 指令加
  `-c safe.directory=Z:/MCMod/HighDegreeOfFreedomVehicles -c safe.directory=%(prefix)///<網路資料夾>/MCMod/HighDegreeOfFreedomVehicles`（實際位址見工作區的 AGENTS.md），
  不改全域設定。commit 只在使用者要求時做；推送是對外公開的動作，每次先經使用者同意。

## 常用指令

AeroCore 有改動時先在 AeroCore 執行 `.\dev.ps1 build publish`，再在這裡：

```powershell
.\dev.ps1 build
.\dev.ps1 :core:test
.\dev.ps1 :neoforge:runGameTestServer
.\dev.ps1 :neoforge:runClient
py -3 tools/tutorial.py      # 0.24：教學的進度與語言鍵
py -3 tools/zh_cn.py         # 0.24：改過 zh_tw（語言檔或手冊）之後重做 zh_cn
```

正式版環境的載入測試（0.24）：AeroCore 的 `scripts/release-test/release_test.py`（`setup`、`server <base|jei|create>`、
`client <base|jei> [計畫]`、`multiplayer [計畫]`），裝在 `%USERPROFILE%\.mcmod\release-test`，不碰使用者的 `.minecraft` 與啟動器。

網路磁碟的未簽署腳本被拒絕時：`powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1 build`。
輸出導到檔案時用背景執行再讀記錄檔（Gradle daemon 會佔住 stdout）。

## 模組邊界

| 位置 | 職責 |
| --- | --- |
| `core/` | 純 Java（不依賴 Minecraft）：零件設計、範例、之後的設計與加工計算；只在編譯與測試時依 `aerocore-physics` |
| `core/.../part/` | 0.17 的九種零件設計、`HdfvParts`（登記零件類型）、`SampleBlueprints` |
| `core/.../part/engine/` | 活塞引擎與噴射引擎的元件、`EngineMaterial`、`PistonEngineDesign`、`JetEngineDesign`、`TurbofanDesign`、`ShaftTurbineDesign`（渦槳與渦軸）、`JetRuntime`（燃氣渦輪共用的執行時規則）、`SampleEngines`；0.10 `AfterburnerDesign`、`RamjetDesign`、`ScramjetDesign`、`PulsejetDesign`、`SolidRocketDesign`、`TurboRamjetDesign` 與它們的元件、`SampleJets` |
| `core/.../refit/` | 0.16 改裝：`Scaling`（整台縮放的規則表）、`Refit`（改裝廠可改的參數與類別、編輯、車燈清單）、`RefitJob`（工件逐件比對、料、烤漆、燈具、工時、電）、`RefitRules`（改裝廠與載具的問題） |
| `core/.../part/chassis/` | 0.15 底盤：`ChassisDesign`（車架設定＋站點清單，`ListedDesign`）、`ChassisSettingsDesign`、`ChassisStationDesign`、`SampleChassis`；構件只在 `look/ChassisLayout` 產生 |
| `core/.../design/` | 設計畫面的資料層：`RecordEditor`（用 record 元件反射讀寫參數）、`Designs`（分頁、範例、問題位置、數值、JSON 上限）、`Bench`（試算） |
| `core/.../manufacture/` | 0.7 製程：`Alloy`（24 種材料）、`Process`、`Form`、`Station`（機器的製程、最佳 IT、功率、是否要人、能否做複雜形狀）、`Operation`、`Workpiece`、`PartPlan`、`StructureWork`、各零件的規劃器（`EnginePlanner`、`PartPlanner`、`StructurePlanner`）、`Plans`（計畫與工時，依 JSON 快取）、`Pace`（兩個比例的預設） |
| `core/.../manufacture/`（0.17） | `Head`（34 個頭：家族、製程×特徵→最佳 IT、功率、底載、準備時間、只吃電、耗材、外形容量）、`Feature`（工序特徵）、`MachineFrame`（九族與五台機床的版面）、`Machining`（頭的工時，用 AeroCore 0.35 的 `api.manufacture`）、`Routes`（路線版本與舊工件轉換）、`Consumable`／`Wear`（耗材與用量、重磨）、`GasSupply`（空氣分離機） |
| `core/.../part/component/` | 0.23 標準件：`Component`（26 種：級 A～D、成品質量）、`ComponentDesign`（`hdfv:component`，只有 `kind`，編成 `MassPart`）；路線在 `manufacture/ComponentPlanner`，切屑回收在 `manufacture/Chips`，碳鋼刀具在 `Wear.Tool.CARBON` |
| `core/.../recipe/` | 0.23 配方的規則（純 Java）：`TechTree`（由配方與標準件路線算每個物品的科技階段）、`Intermediate`（中間件與冶金品的質量）、`Metallurgy`（電弧爐與電解槽每批的 kWh，呼叫 AeroCore 的 `api.metallurgy`） |
| `core/.../structure/` | 0.8 多方塊圖樣：`Pattern`（四向旋轉、選配格、可重複層，`'.'` 必須空、`' '` 不管）、`Structures`（CNC、大型儲槽、鑽井架、抽油機、蒸餾塔、製程單元與反應器種類） |
| `core/.../fluid/` | 0.8 流體：`HdfvFluids`（18 種與水：密度、黏度、顏色；0.10 加液氫與 700 bar 氫）、`Piping`（DN50 管、泵、儲槽容量）、`FuelGrades`（流體→燃料族與辛烷值、`takenBy` 零件燒哪些流體）、0.10 `HydrogenStorage`（哪種容器收哪種氫、蒸發、壓縮與液化用電、載具油箱收什麼） |
| `core/.../power/` | 0.8 動力：`PowerPlant`（電線、天軸、發電機、電動機、蒸汽機、電池、固體燃料）、`Grid`（按比例分配）、`ShaftLine`、`EngineUnit`（引擎座：引擎＋負載，PI 調速，失速跳脫） |
| `core/.../petroleum/` | 0.8 石油：`OilFields`（由世界種子算的油藏，每 512 格一區）、`Oilwell`（鑽井、套管、抽油機）、`Refining`（蒸餾與各單元的轉化，質量守恆） |
| `core/.../assembly/` | 0.7 組裝：`Room`、`AutoWiring`（軸、尾流、按鍵通道）、`Placing`（掛載點建議與配置編輯）、`AssemblyPlan`（要帶的零件、工件、料件、工時、最重件、尺寸）、`RoomScan`（抽象格子上的房間掃描）、`RoomRules`、`SampleLayouts`；0.8 `BuildSequence`（骨架、蒙皮、依重量吊裝、固化）、`CraneRunway` |
| `neoforge/` | Mod 主類別 `Hdfv`、`HdfvRegistry`（方塊、物品、資料元件、創造模式分頁）、指令、GameTest；依 `aerocore-neoforge` 的遊戲 JAR |
| `neoforge/.../design/` | 設計桌、設計平板、藍圖物品、`Blueprints`（存檔規則）、`DesignNetwork`（存檔封包） |
| `neoforge/.../client/` | `DesignScreen`、`Preview`（3D 預覽）、`Plot`、`ClientAccess`（共用程式碼在客戶端呼叫的入口）、`DevCommands`（開發用 `/hdfvdev`）、`EngineRenderers`、0.10 `JetFamilyRenderers`（新引擎與火焰）、`Vibration`（脈衝噴射的畫面震動）、`sound/`（0.10 的 `EngineSounds`、`SynthStream` 在 0.19 換成新的聲音，見下方）；0.11 `Exhausts`（尾焰、熱的金屬發光、煙與火花的發射）、`ExhaustParticle`、`EnginePieces`（世界中依轉子轉動的件）、`EngineStandRenderer`（試車台上的引擎）；0.19 的聲音見 `client/sound/` 一列 |
| `core/.../sound/` | 0.19 聲音的規則（純 Java）：`VehicleSound`（一台載具的聲源、混音、車內外、`balance`、`engine`（試車台）、`bakeLoop`、`far`、`updateEngines`）、`PartSounds`（哪個零件發什麼聲）、`SoundProfiles`（設計 → AeroCore 聲源的規格）、`SoundInputs`、`EventVoice`、`SoundBench`（離線算 WAV）、`MachineSound`（機器的製程聲 `Hum`、事件、循環烘焙） |
| `neoforge/.../sound/` | 0.19 共用：`SoundSync`（機器的 `Hum` 與事件送給附近玩家；客戶端存放）、`FarSignatures`（給 AeroCore `FarSounds` 的遠處簽名）、`SoundDevServerCommands`（`/hdfv dev sound hum|event`） |
| `neoforge/.../client/sound/` | 0.19 客戶端：`VehicleSounds`（串流分配、循環、試車台、音爆與字幕）、`ClientInputs`、`MachineSounds`（機器循環與事件）、`FarVehicles`（追蹤範圍外）、`SoundPaths`（EFX 的牆、空氣、車艙、迴響）、`Cues`（音爆、字幕）、`SoundDebug`（`/hdfvdev sound hud|log`）、`SoundDevCommands`（`record`、`render`） |
| `neoforge/.../client/model/` | 引擎模型（預覽與世界共用）：`EngineModel`（固定部分、會動的零件與它們隨曲軸角的位置、剖視用的切面）、`PistonEngineModel`、`JetEngineModel`、`GasTurbineModel`（渦扇、渦槳、渦軸）、`Shapes`（顏色、齒輪、皮帶、葉片）；0.11 `Looks`（顏色→材質）、`DetailKit`（Blender 細節庫）、`Details`（擺放：螺栓圈、管夾、編織管、線束）、`StandIn`（遠處的替身）、`ClipCheck`（開發用：找零件互穿） |
| `core/.../look/` | 0.11 `Finishes`：材質（表面處理）的 id 與鋪設長度，和 `tools/blender/finishes_spec.json`、`assets/hdfv/finishes.json` 一致（`FinishAssetsTest`）；`FX_*` 是特效貼圖 |
| `tools/blender/`、`scripts/blender.ps1` | 0.11 Blender 腳本（Blender 5.2，無畫面執行）：`textures.py`（材質貼圖）、`details.py`（細節庫 OBJ，`DetailKitTest`）、`fx.py`（尾焰雜訊、旋翼模糊、煙與火花的粒子貼圖）、`review.py`（算圖對照）、`selftest.py`；產物進版本控制 |
| `neoforge/.../material/` | 0.7 礦石、錠、化學品、料件（`Stock`）、合金爐與配方；0.23 `HdfvComponents`（標準件、中間件與冶金品的物品）、`AssemblingRecipe`（`hdfv:assembling`）、`ElectricRecipe`（`hdfv:electric_furnace`、`hdfv:electrolysis`）、`TechStages`（遊戲中的科技樹，提示在 `client/TechTooltips`）、`RecipeGameTests` |
| `neoforge/.../smelting/` | 0.23 電弧爐與電解槽：`HdfvSmelting`、`SmelterBlock(Entity)`（四個輸入、成品、兩個副產物、FE 緩衝）、`SmelterMenu`；畫面在 `client/MaterialScreens.SmelterScreen` |
| `tools/recipes.py`、`tools/components_assets.py` | 0.23：所有配方 JSON 的產生器（見〈約定〉的「合成配方與科技樹」）；標準件、中間件與冶金品的圖示、模型、名稱、標籤與流體標籤（兩份語言檔照錨點就地插入） |
| `neoforge/.../machine/` | 0.7 機器：`MachineBlock`、`MachineBlockEntity`（十二種機器共用）、`MachineMenu`、`MachineItems`（漏斗）、`Workpieces`（工件與零件物品、藍圖快取） |
| `neoforge/.../villager/` | 0.7 焊工：POI、職業、交易 |
| `neoforge/.../assembly/` | 0.7 組裝室：房間方塊與門、三種控制台（`RoomControllerBlockEntity`：掃描、配置、開工、出廠、拆解）、`WorldGrid`、`Staging`（從箱子取料）、`HdfvVehicle`（未組裝不模擬）、整車藍圖、`Packed`（壓縮的配置 JSON）、`AssemblyNetwork`、`/hdfv dev` 指令 |
| `neoforge/.../multiblock/` | 0.8 多方塊：`PatternBlock`、`MemberBlock`（FORMED）、`Multiblock`（每 40 tick 檢查、拆一塊就解散）、`Multiblocks`（成員→控制器）、`ControllerBlock(Entity)`（能源緩衝、工作）、`HdfvMultiblocks`（CNC 模組）、`/hdfv dev place` |
| `neoforge/.../fluid/` | 0.8 流體類型（沒有方塊）、管線網路、儲槽（可疊）、電動泵、大型儲槽、油桶；0.10 低溫儲槽與高壓氫氣瓶組（`TankBlock` 的另兩種容器）、氫液化機 |
| `neoforge/.../power/` | 0.8 電網與天軸網路（`EnergyNetworks`、`ShaftNetworks`）、電線、天軸、電池、發電機、電動機、蒸汽機、踏板、引擎座、創造模式電源 |
| `neoforge/.../petroleum/` | 0.8 油田存檔（`OilFieldData`）、油苗、探勘組、鑽井架、井口、抽油機、蒸餾塔與製程單元（`RefineryUnitBlockEntity`）、出料罐、瀝青、塑膠、`/hdfv dev oilfield` |
| `neoforge/.../fuel/` | 0.8 加油機、`Fuelling`（加進載具的規則、油桶倒進載具）；0.10 推進劑與簧片組物品、`Servicing`（裝填助推器、換簧片、拋棄的零件掉成物品） |
| `neoforge/.../refit/` | 0.16 改裝廠：`HdfvRefit`（控制台、燈具、選單）、`RefitShopBlock(Entity)`（掃描、待改藍圖、編輯、取料、耗電、完成時 `PartVehicle.refit`）、`RefitShopMenu`、`RefitNetwork`、`RefitDevCommands`、`RefitShopGameTests`；畫面在 `client/RefitShopScreen` |
| `neoforge/.../machining/` | 0.17 耗材物品（`HdfvMachining`：`SupplyItem`、重磨次數 `REGRINDS`、空鋼瓶）、空氣分離機（`AirSeparatorBlock(Entity)`）；機器端的耗材、重磨與舊工件轉換在 `machine/MachineBlockEntity`，GameTest 在 `machine/MachiningGameTests` |
| `neoforge/.../client/`（0.17） | `FamilyRenderer`（九族的成形外觀、動作、工具的粒子與光）、`model/MachineKit`（Blender 機加工零件組）、`MachineEffects.tool` |
| `tools/machining_assets.py`、`tools/blender/machining_kit.py` | 0.17 機加工資產產生器（方塊狀態、模型、掉落、標籤、石榴石礦、16 px 原圖、語言檔就地插入；`MachiningAssetsTest`；0.23 起配方由 `tools/recipes.py` 寫）與零件組（51 件 OBJ；`DetailKitTest`） |
| `core/.../airport/` | 0.21 `AirportTier`（大、中、小三級的規格）、`AirportLayout.towerInside`（塔內：螺旋梯、中柱、活門格柵、地磚、電纜槽、燈、蓄電池、床）；0.20 機場的規則（純 Java）：`AviationUnits`、`AircraftKind`、`WakeCategory`、`Runway`、`RunwayFinder`（由跑道頭標線找跑道）、`RunwayChoice`（頂風、順風上限、遲滯）、`RunwayMarkings`（跑道頭條紋、編號字型、中心線、接地區、瞄準點）、`Fixture`、`AirfieldLighting`（燈具 → AeroCore 的 `FieldLamp`、迴路、PAPI）、`Atis`、`TowerRules`（電、雷達與目視範圍）、`Separation`、`Atc`（每架的階段、請求、指示、警告、手動指令）、`AirportPlanner`（依世界種子的區域規劃）、`AirportLayout`（一份規劃展開成地面、標線、燈、塔台、機庫與東西） |
| `neoforge/.../airport/` | 0.20：`HdfvAirports`（方塊、物品、選單、地物）、`TowerBlockEntity`（電、ATIS、跑道、管制、迴路、泛光燈、接手）、`AirfieldData`（燈具、跑道頭、塔台、泛光燈、生成的規劃、舊區域）、`AirTraffic`（區塊票、燈的同步）、`AirportNetwork`（呼叫、指令、狀態、畫面、燈）、`AirportClientState`、`TowerMenu`、`TowerView`、`FixtureBlock`、`MarkedSurfaceBlock`（0.8 的柏油也是它）、`LinePaintItem`、`AirfieldBlocks`、`WindsockBlockEntity`、`AirportFeature`（每個區塊放自己那片）、`AirportWorldgen`（規劃快取、地形探測、舊區域清單、登記）、`AirportDevCommands`（`/hdfv dev airport`）、`AirportGameTests`；0.21 塔內的方塊（`steel_stairs`、`steel_column`、`steel_grating`、`interior_lamp`、`cable_duct`、`tower_floor`，`HdfvAirports.INSIDE`） |
| `neoforge/.../client/`（0.20） | `TowerRenderer`（塔台整座，細節 64／256 m、看到 384 m）、`WindsockRenderer`、`TowerScreen`、`AtcHud`、`AtcControls`、`AtcWords`、`AirfieldLights`（→ AeroCore `FieldLamps`）、`model/AirfieldKit`；`MachineMeshes` 可指定細節距離 |
| `tools/airport_assets.py`、`tools/blender/airfield_blocks.py`、`tools/blender/airfield_kit.py` | 0.20 機場資產（方塊狀態、模型、掉落、標籤、生成資料、16 px 原圖、村民衣服、兩份語言檔就地插入；`AirportAssetsTest`；0.23 起配方由 `tools/recipes.py` 寫）、燈具等方塊的 OBJ（`BlockModelTest`）、塔台零件組（`DetailKitTest`）；`textures.py` 0.20 起可輸出法線貼圖（`board_formed_concrete_n`） |
| `neoforge/.../compat/` | `HdfvJeiPlugin`、`AlloyingCategory`、`RefiningCategory`（0.8）、`MachiningCategory`（0.17：每個頭一頁）、`AssemblingCategory`、`SmeltingCategory`（0.23：組裝台、電弧爐、電解槽）：JEI 外掛（只在裝了 JEI 時載入）；0.24 `JeiLink`（手冊點物品開 JEI 配方） |
| `core/.../handbook/` | 0.24 `HandbookText`：手冊頁面的格式（標頭、段落、`{items}`、`{image}`、`{recipe}`、`{design}`、`{see}`、行內 `{key}`、`{item}`、粗體）與 `structure`（各語言要一致的部分） |
| `neoforge/.../tutorial/` | 0.24 新手教學：`HdfvTutorial`（手冊物品、`hdfv:milestone` 觸發的登記、玩家附件 `hdfv:tutorial`、第一次登入給手冊與歡迎訊息、`/hdfv handbook`、進度同步封包）、`Milestone`（觸發）、`Milestones`（事件一覽與呼叫入口）、`DriveTracker`（每秒累計開車距離、速度、高度、馬赫）、`TutorialData` |
| `neoforge/.../client/HandbookScreen`、`HandbookPages` | 0.24 手冊畫面與頁面讀取（依語言、缺的退回 en_us、資源重載時重讀） |
| `assets/hdfv/handbook/<語言>/*.md`、`data/hdfv/advancement/tutorial/` | 0.24 手冊頁面（24 篇；en_us 與 zh_tw 手寫，zh_cn 由 `tools/zh_cn.py` 產生）、進度（`tools/tutorial.py` 產生） |
| `tools/tutorial.py`、`tools/zh_cn.py`、`tools/zh_cn_terms.json` | 0.24 進度與教學語言鍵的產生器；繁轉簡（OpenCC tw2sp＋術語表） |
| `neoforge/src/dev`、core 的 `src/testFixtures` | 0.24 開發用程式（只在開發 run 載入，不進發布的 JAR）：所有 `*GameTests`、GameTest 結構、`/hdfv dev` 與它拉進來的指令、`/hdfvdev`、`ClipCheck`、`HdfvDev`（登記）；`TestCars`、`SoundBench` |
| `core/.../road/`、`neoforge/.../road/` | 0.25 路面坡道：`RampShape`（坡度、每格的高度、碰撞的 16 階）；`HdfvRoads`、`RoadRampBlock`（柏油與混凝土 × 1:2、1:4、1:8；`FACING` 是上坡方向、`part` 是在坡上的第幾格，放置時自動接下一格或下一段；碰撞是階梯、畫的是平滑的楔形 OBJ；實作 AeroCore 的 `SlopedGround`，輪子走的是真斜面）；`tools/road_assets.py`；GameTest `road/RoadGameTests` |
| `core/.../storage/`、`neoforge/.../storage/` | 0.25 儲物：`StorageKind`（四種方塊的格數）、`CargoMass`（後備質量、工件質量、頁尾的編碼）、`CargoRules`（貨艙格數＝每 m³ 27 格、9～216（貨櫃 324）、額定載重）；`ItemMass`（每個物品的 kg：hdfv 自己的規則＋資料映射 `hdfv:item_mass`＋後備；設定 `storage.itemMassScale`）、`HdfvStorage`、`StorageBlock(Entity)`（木箱、鋼製置物櫃、零件架、貨櫃）、`StorageMenu`（不綁方塊位置，載具貨艙也用它）、`VehicleCargo`（載具實體的附件：每個貨艙一個容器、重排、秤重 → `PartVehicle.setHold`、打開、卸下、掉落）、`HdfvCargo`、`CargoNetwork`、`CargoLoaderBlock(Entity)`（裝卸台）；畫面 `client/StorageScreen`、`client/CargoTab`；`tools/storage_assets.py`、`tools/item_mass.py`、`tools/cargo_assets.py`；GameTest `StorageGameTests`、`CargoGameTests` |
| `core/.../part/StorageBoxDesign` | 0.25 儲物箱零件 `hdfv:storage_box`（車頂箱、貨斗箱、20 呎貨櫃；長寬高、壁厚、材料；一個貨艙；設計畫面分類 STORAGE）；模型 `client/model/StorageBoxModel` |
| `core/.../damage/`、`neoforge/.../damage/` | 0.25 損壞：`Conditions`（每種零件的狀況項目、撞擊分配、韌性、伸及、腐蝕的金屬與壁厚、`REPLACE_FROM` 0.8）、`Impacts`（撞擊、爆炸、攻擊 → 各項目的損壞；乘員受傷）、`Wearings`（由設計數字算 AeroCore 零件的 `Wearing`）、`EngineWear`（hdfv 自己的引擎與油箱）、`Elements`（腐蝕、泡水、火、油箱爆炸）、`DamagePace`（遊戲的時間尺度）；`VehicleDamage`（四種來源、報廢規則、狀態畫面的互動）、`VehicleConditions`（讀取與警示）、`client/VehicleStatusScreen`（分頁：狀況、修理、貨物）；設定 `damage.*`；`/hdfv vehicle condition [set|repair]`；`tools/damage_assets.py`；GameTest `damage/DamageGameTests` |
| `core/.../repair/`、`neoforge/.../repair/` | 0.25 修理：`RepairJob`（每項的修法：原地修、換件、換整個零件、原地重做；料、工時、電、回收）、`Repairs`（規則表）、`RepairRules`（房間與載具的條件、報廢順序）、`FieldRepair`（急修工具組、補胎片的限制）；`HdfvRepair`、`RepairShopBlock(Entity)`、`RepairShopMenu`、`RepairNetwork`、`FieldKitItem`、`FieldKits`；畫面 `client/RepairShopScreen`、`client/RepairTab`；標準件 `friction_lining`；`tools/repair_assets.py`；GameTest `repair/RepairShopGameTests` |
| `release/` | 0.24 發布頁的文字（`page.md` 三語、`summary.txt`、各版的 `notes-*.md`、`checklist.md`）；圖示與截圖在本機 `%USERPROFILE%\.mcmod\release\hdfv` |

hdfv 的遊戲 JAR 包含 core；執行時不要再放 AeroCore 的 physics JAR（AeroCore 遊戲 JAR 已含同樣的套件，重複會讓模組層衝突）。

## 約定

- 零件類型在 `HdfvParts.register()` 登記（Mod 建構時與測試的靜態區塊）；新零件類型也加在那裡。類型 ID 用 `hdfv:` 命名空間。
- 零件設計只放參數、檢查、JSON、質量估計與控制規則；受力與性能呼叫 AeroCore。0.17 的被動零件編譯成 AeroCore 的執行時零件
  （`SpherePart`、`HullPart`、`PlaningHullPart`、`PropellerPart`、`ControlSurfacePart`、`BoxPart`、`MotorPart`、`MassPart`）。
- 參數原則（同 AeroCore 的 VEHICLE_PARTS）：每個參數都開放；只擋不合物理的值（INVALID）；超出驗證範圍照算並標示外推
  （EXTRAPOLATION）；代價由物理而來，不加平衡用的懲罰；新增參數要有預設值，舊藍圖才讀得回來。
- 活塞引擎：
  - 壓縮比不是參數，由燃燒室、床墊、缸面間隙與活塞頂算出；不要加一個壓縮比欄位繞過它。
  - 性能只來自 AeroCore 的 `EngineMap`／`EngineCycle`／`Friction`；不要直接改 MAP 的值。
  - MAP 在背景建立（`Part.warmUp`，數秒），依設計 JSON 快取；執行時零件被所有載具共用，每台載具的狀態放 `PartStep.internal`。
- 噴射引擎：
  - 性能只來自 AeroCore 的 `Turbojet`；燃油控制、起動、點火、熄火、喘振（30 %、0.6 s）是規則，寫在
    `JetControlDesign`／`JetEngineDesign`／`JetRuntime` 的註解。起動機若在點火時就切離，模型引擎會在點火轉速附近反覆熄火。
  - 起動照真實引擎（使用者 2026-10-11「起動要真實引擎一樣」）：起動機扭矩從靜止扭矩線性降到切離點上方 15 % 為零，
    切離點是半速或 0.9 倍核心慢車（取低者）；點火後慢車以下的燃油目標是起動排程的轉速（每秒上升「起動率」，最多領先核心 5 %），
    渦扇在慢車以下改以核心轉速控制（風扇還沒轉起來，用 N1 誤差加油會過量）。範例的起動機扭矩設成單靠它約帶到 28 %。
    起動因此要數十秒；世界中的 GameTest（噴射艇、渦扇船、渦軸船）用起動率 10 % 的複本（`quickStart`），真實起動由試算台測。
  - 質量係數以 J85-GE-17A（181 kg）與 JetCat P80（1.32 kg）一起定；改任何元件的質量估計都要重看兩者（`JetEngineTest`）。
- 渦扇、渦槳、渦軸（0.3）：
  - 性能只來自 AeroCore 的 `Turbofan`／`FreeTurbine`；兩種引擎各自把元件換成 AeroCore 的 `Design`（`cycle()`），模型（尺寸與運轉線）依設計 JSON 快取。
  - 執行時每一步先用上一個燃油跑準穩態（`quasiSteady`，匹配解存在內部狀態暖啟動），再由它算下一步的燃油（`command`）；
    起動、點火、熄火、喘振規則與渦噴相同（`JetRuntime`）。試算的穩態掃描與零件用同一個 `command`／運轉線。
  - 渦扇的控制以風扇轉速（N1）為目標，點火與起動機看核心轉速（N2）；`JetControlDesign` 的慢車與最高轉速比因此是 N1 的。
  - 軸引擎的輸出轉子必須是第一個（`Connection.Shaft` 只接第一個轉子）；輸出扭矩＝動力渦輪扭矩×減速比×齒輪效率。
    渦軸的調速器積分只在需求沒有飽和時累積（防積分飽和）。
  - 核心壓縮機在風扇後方：用 `CompressorDesign.check(inletK, inletPa)` 以增壓後的進口狀態檢查（功隨進口溫度增加、進口看修正流量）。
  - 質量（使用者要求修小型引擎）：渦扇的附件與機架 85 kg ×（1 − exp(−(核心流量/3)²)）＋0.6 kg 每 kg/s 總流量；
    軸引擎 23 g/kW 的核心燃氣功率 ×（1 − exp(−(流量/1.5)²)）；指數項讓模型級核心幾乎不帶這些固定設備。
    範例在真實質量 ±15 % 內（CFM56 級 +4 %、FJ44 級 −6 %、PT6A 級 −2 %、T700 級 −6 %），`TurbineEnginesTest` 檢查。
    轉子慣量：壓縮機轉子取模組質量 40 %、渦輪 50 %，以葉尖半徑的圓盤計。
  - 軸引擎的試算功率曲線帶螺旋槳定律負載（`Bench.loadCoefficient`：設計點功率在設計轉速），動力渦輪轉速取扭矩平衡；
    不要改回固定 100 % 轉速（慢車時動力渦輪會被當成煞車，出現負功率）。
  - 試算台的負載零件（`Bench.Load`）把參數寫進 JSON：AeroCore 依 JSON 快取已編譯的零件，參數空白時不同引擎的負載會共用（曾讓 T700 帶著 PT6A 的負載）。
- 機翼、螺旋槳、旋翼（0.4）：
  - 元件放在 `part.air`，和引擎一樣實作 `EngineComponent`（名稱是歷史原因）；分頁鍵＝record 欄位名＝JSON 鍵（`Designs.withPage` 依欄位名換元件）。
    旋翼的分頁叫 `controls`，不要改成 `control`（會和噴射引擎「燃油控制」的分頁名稱與翻譯撞名）。
  - 性能只來自 AeroCore（`Wing`、`AirPropeller`、`HelicopterRotor`、`WingStructure`）；機翼直接編譯成 AeroCore 的 `WingPart`，
    螺旋槳與旋翼由 hdfv 的 `Compiled` 包住 AeroCore 的零件、加上規則（調速器、增穩阻尼），規則寫在設計類別的說明。
  - 翼型元件存 NACA 參數與 CST 權重兩組；`SectionDesign.naca` 會用 `Airfoil.cstFit` 填好權重，切換翼型族時形狀不跳。
    螺旋槳與旋翼的厚度由槳葉決定（`section.airfoil(thickness)`）。
  - 範例水上飛機（0.5 起）：有斷階的機體浮筒（`SampleBodies.seaplaneFloat`），斷階在重心稍後（`SampleBlueprints.FLOAT_X`），
    機翼安裝角 3°（0.4 的浮筒沒有斷階，用了 9°）。浮筒艉有水舵綁 `rudder`。改浮筒、重心或機翼前，跑 `AirframeTest.theSeaplaneLeavesTheWater`
    （離水速度、滑行距離、離水時航向在 ±10° 內）。
    最初的單船身飛行艇（船寬 1.6 m）在推力下會翻滾（重心高、寬度不夠）且翼尖浮球的阻力讓它打轉，所以改成雙浮筒。
  - 範例直升機：引擎經單向離合器（`Connection.Shaft` 的 `freewheel`）接主旋翼；尾槳的推力朝右舷抵消主旋翼的反扭力，
    踏板綁在尾槳總距的 0.55 ± 0.35，尾槳有偏航阻尼 −0.3。離線懸停由 `AirframeTest.theHelicopterSpinsUpAndHovers` 測。
  - 直升機在停機坪上起動時，0.22 開發中兩度讓 GameTest 伺服器當掉（「Rotation step failed to converge」）：旋翼幾乎不轉時的 Drees 入流
    與顯式的陀螺力矩，都在 AeroCore 修好。改旋翼、直升機範例或 AeroCore 前跑 `AirframeTest.theHelicopterStartsUpFreeToTurn`
    （只固定位置、讓姿態自由的起動 30 秒）。GameTest 批次的順序每次不同，世界中的直升機測試通過一次不代表沒問題。
  - 引擎的輸出轉子要給真實角動量（`Part.Rotor` 第四個參數）：減速箱後的輸出軸慣量是渦輪的 × 減速比²，角動量只 × 減速比
    （0.22 開發時水上飛機在引擎起動時翻覆，原因之一就是陀螺與反扭力被放大 15 倍）。
  - 世界中的 GameTest（`AircraftGameTests`）只測浮著、起動、滑行與直升機離地 3 m；完整起飛與懸停在離線測試（世界中沒有夠大的水面）。
    水上飛機的測試條件一成立就通過：水池只有 40 格長，30 % 推力約 5 秒就會撞到對面的牆。
    模板 `seaplane_pool`（40×12×28）與 `helipad`（28×24×28）是空的，測試自己鋪方塊。
  - 機翼與槳葉的蒙皮用 `AirframeModel.skin`／`cap`：每格一個面、頂點從外面看逆時針、法線朝外。世界中的載具網格會剔除背面
    （看繞向），設計預覽不剔除（看法線）；不要改回 `PartMesh.sheet(..., true)` 的雙面：兩個面疊在同一處，鏡像的那半翼會變暗。
- 範例藍圖與範例引擎只示範格式與物理，不是平衡過的遊戲內容。
- 設計畫面：
  - 參數由元件 record 的元件自動產生（`RecordEditor`）：`double` 數字、`int` 整數、`boolean` 是／否、列舉與 `EngineMaterial` 循環選項，
    `massGivenKg` 空白為估計；燃料與機油這類字串選項的清單在 `RecordEditor.choices`。新元件或新參數不必改畫面，
    但要在 `lang` 加 `hdfv.param.<分頁>.<參數>`（和選項 `hdfv.choice.<值>`），`DesignEditorTest.everyNameIsTranslated` 會抓漏。
    兩種語言檔由腳本產生時，鍵的順序照現有檔案。
  - 單位由參數名稱的 SI 字尾推得（`boreM` 是 m）；新參數照這個命名。輪胎與輪圈的尺寸照慣例用英吋（字尾 `In`），
    阻尼 `NsPerM`（N·s/m）、黏性係數 `NmsPerRad`。`double[]` 是數字清單（`Type.NUMBERS`，齒比：逗號、分號或空白分隔）。
  - 分類與新的設計（0.6，使用者「設計 GUI 要有分類」「加入新的設計」）：`Designs.KINDS` 是可以設計的零件種類（`Kind`：id、
    `Category`、新設計的起點），`Designs.SAMPLES` 的每個範例標明種類。上方列：分類、種類、「新的設計」、該種類的範例、載入範例。
    新零件類型要加一個 `Kind`（翻譯 `hdfv.kind.<id>`），範例加 `Sample`；`DesignEditorTest` 檢查每個種類的新設計可編譯、每個範例的種類對。
    機體依用途分三個種類（`kindOf` 看 `purpose`）；車身用 `RecordEditor.Shown` 藏起水動力模型、設計速度與船底類別（車身一律排水型）。
  - 問題：`DesignIssue` 的路徑對到分頁與參數（`Designs.place`，`massKg` 對到 `massGivenKg`）；超出範圍的訊息由數值組成中文，
    不成立的訊息目前是零件檢查的英文原文。
  - 試算只呼叫 `Bench`，`Bench` 只用載具上跑的同一套模型（引擎的 `map()`／`model()` 快取、零件本身在測試台上跑），不另寫控制規則。
  - 存檔一律在伺服器重新讀取（`Blueprints.save`）：客戶端送來的 JSON 不可信；資料元件與封包都限制在 `Designs.MAX_JSON`。
- 客戶端程式碼只放 `client` 套件；方塊與物品在客戶端開畫面時呼叫 `ClientAccess`，不要在共用類別直接引用畫面類別。
- 3D 預覽與引擎外觀（使用者要求「我想要有逼真的 Model」）：`PistonEngineModel`／`JetEngineModel` 照真實零件的形狀畫，尺寸只來自設計參數
  （發電機、起動馬達這類設計裡沒有的附件用典型尺寸，隨缸徑或機匣直徑縮放，不要用固定的公尺數，小引擎會被蓋過去）。
  - 分頁鍵（`block`、`piston`…）就是高亮的名字；新元件要畫進模型並用它的分頁鍵上色（`c(page, color)`）。
  - `GasTurbineModel` 一律以氣流往 −x 建模；反流式軸引擎畫完後整個繞 y 軸轉 180°（氣流向前、螺旋槳凸緣在 +x）。
    三組會轉的網格：低壓（風扇或動力渦輪）、高壓（核心或燃氣產生器）、輸出（凸緣；偏置齒輪箱時繞它自己的軸）。
  - 會動的零件各自建一次網格，用 `EngineModel.Placement` 隨曲軸角放置：活塞與連桿照曲柄連桿運動，氣門照 `ValveLift`（凸輪輪廓也由它算，
    經搖臂時除以搖臂比），不要另寫一套運動。
  - 氣門機構依汽缸蓋配置（`valves`、`cams`、`rocker`）：DOHC 凸輪直接壓挺桿；SOHC 搖臂；OHV 凸輪在缸體（V 型一支在 V 谷）、
    挺桿、推桿、搖臂，氣門沿曲軸排成一列。凸輪蓋高度由裡面最高的運動件決定（`coverHeight`）。
  - 剖視：外殼放 `casing(b)`、外部零件放 `outside()`，剖視時沿平面切開（`EngineModel.section`），預覽依視角拿掉靠近觀看者的一半；
    正時鏈這類想完整看到的放 `fixed`。
  - 世界中的載具用不剖視的模型：固定部分是零件的靜態網格（AeroCore 在背景烘焙），0.11 起會動的件由 `EnginePieces` 依零件的轉子轉動（見 0.11 段）。
  - 通用的網格工具在 AeroCore 的 `PartMesh`，不要在 hdfv 另寫。
- JEI 是可選的：`compileOnly` 它的 API、`runtimeOnly` 它本身（開發用遊戲），版本在 `gradle.properties` 的 `jei_version`；
  它的倉庫（`maven.blamejared.com`）只放行 `mezz.jei` 與 `net.mezzdev` 群組。hdfv 的類別除了 `compat` 以外不要引用 JEI。
- Create 也是可選的（0.23）：開發用遊戲與 GameTest `runtimeOnly` 它（`create_version`，`maven.createmod.net` 只放行 `com.simibubi.create`，
  `transitive = false`：它的 JAR 內含 Registrate、Flywheel、Ponder），`-Phdfv_create=false` 不載入。hdfv 不引用 Create 的類別，只有帶
  `neoforge:conditions`（`mod_loaded create`）的配方。
- 開發用客戶端指令 `/hdfvdev`（`DevCommands`，正式環境不登記）讓 AeroCore 的 `AutoShot` 用 `client` 步驟操作畫面、`gui` 步驟截圖。
- GameTest 的模板在 `data/hdfv/structure/`（只開 `hdfv` 命名空間時，其他 Mod 的模板會被濾掉）。引擎船與噴射艇會跑過整個水池，
  各自在自己的批次；判定在撞到池壁前條件成立就成功。

- 輪子與傳動（0.6，`part.wheel`、`part.drive`）：
  - 車軸 `hdfv:axle`（分頁 layout、tyre、wheel、brake、suspension、differential）編成 AeroCore 的 `AxlePart`；起落架 `hdfv:landing_gear`
    （leg、strut、tyre、wheel、brake）編成 `LegPart`。原點是掛載點，輪轂在設計壓縮量時低於它 `hubDropM`。
    ABS（`AntiLock`）是 AeroCore `BrakeControl` 的規則；記憶值是壓力削減量（0＝全壓，新車輪一踩就煞）。
  - 變速箱：一個 `GearboxDesign`、四種類型 ID（`hdfv:manual_gearbox`、`automatic`、`dual_clutch`、`cvt`），種類用不到的分頁在建構子裡固定成預設值
    （讀回才相等）。分動箱 `hdfv:transfer_case` 是單頁設計。
  - 控制規則（`ShiftRules`）包住 AeroCore 的硬體：在自己的 `loads` 用 `PartStep.override` 把駕駛輸入（`selector` −2 P／−1 R／0 N／1 D／2 M、
    `shift`、`throttle`、`brake`）變成硬體的 `gear`、`clutch`、`lockup`、`preselect`、`ratio`、`couple`，記憶放在 `TransmissionPart.STATES` 之後。
    手排沒有規則：`gear`（排檔桿）與 `clutch`（踏板）就是駕駛的。顯示值多一個（選檔位置）。
  - 試算：`WheelBench`（輪胎曲線、懸吊行程與外傾、乘坐頻率、側傾剛性、鎖死比、十次煞車熱衰退、FAR 23.725 落震）、
    `DriveBench`（Wong 牽引力圖、變扭器曲線與失速轉速、引擎＋變速箱＋道路負載的試驗台加速；試驗台沒有輪胎打滑，比整車快）。
    0.25 起設計畫面畫出來：變速箱（牽引力圖、全油門起步、自排與 CVT 的變扭器曲線）、分動箱（`DriveBench.transferCase`：前後分配對轉差、
    高低速檔的牽引力）、車軸的第二個畫面（乾濕的煞車與轉彎曲線、十次煞車衰退）、起落架（落震，預設 FAR 23 的 2.59 m/s，另算 1.2 倍的保留能量）。
  - 模型：`WheelModel`（輪胎胎紋、輪圈、碟盤與卡鉗、鼓煞、各型懸吊連桿、差速器、轉向機、防傾桿、油氣支柱與彈簧鋼腳）、`GearboxModel`；
    世界中由 `WheelRenderers` 依顯示值（每輪 spin、steer、compression）擺輪子與連桿。車身預覽在車軸掛載點畫家用車的輪子（只在設計畫面）。
  - AeroCore 0.24 的油氣支柱阻尼在 2026-10-13 修過（割線係數並迭代）；支柱要有線性阻尼（`StrutDesign.SEAL`，靜止位置臨界的 1/6）才停得穩。
- 機體（0.5）：
  - `part.body`：`BodyDesign` 是一頁機體設定（`BodySettingsDesign`，分頁 `settings`）加一串 `BodyModuleDesign`（分頁 `module1`、`module2`…，
    1 是機頭端）。模組頁的參數翻譯共用 `module` 這個頁名（`hdfv.param.module.*`、`hdfv.choice.module.*`），不要每個模組各寫一份。
  - 模組的參數依種類與截面隱藏用不到的（`RecordEditor.Shown`）；新參數要決定在哪些截面、種類顯示。種類的預設值在 `BodyModuleDesign.defaults(kind, after)`
    （新加的模組從前一段的後截面接上）。
  - 開口下緣從模組最低的底部量起（後截面或前截面，含斷階）；從後截面量時，底部往後升高的尾錐前半會留成密封的。
  - 承載質量（`supportedKg`）放在 `supportedAt`（從後端起算的長度比例）：計入結構與船底，試算時當配重放在那裡；機體在載具裡的質量不含它
    （由其他零件提供）。
  - 掛載點名稱是公開的藍圖介面（`BodyDesign.mountPoints`）：改名會讓舊藍圖的 `attach` 失效（成為設計問題）。船身家族的最後一段也有 `transom` 與 `rudder`。
  - 3D 模型（`BodyModel`，使用者要求「車身 船身 機倉 3D 預覽的 model 要更好、逼真」）：
    - `BodySurface` 是放樣面：每站沿固定的橢圓角度從中點射線取樣（沿周長取樣時，硬舷在收窄的船艏落在不同的取樣點之間），
      站與站之間線性內插；可問任一 x、角度的點與法線，以及一側某高度的角度。窗、門、條紋、燈都貼在這個面上，不要另算幾何。
    - 外殼平滑著色：頂點法線取共用該點、夾角小於 38° 的面平均（硬舷、斷階、艉板保留稜角）。
      面的法線用對角線外積（角法線相加的寫法在平的四邊形上抵消，世界中剔除後浮筒成條紋），頂點逆時針朝外：世界中的載具會剔除背面。
    - 細節貼片離外殼 6 mm（框、條紋、門縫）與 11 mm（玻璃、門把）：再近在遠處會閃。玻璃的 alpha 小於 0xff，設計畫面半透明、世界中不透明。
    - 飛機的條紋與機腹依射線的序號上色（沿機身是平順的線）；船的防污漆與水線帶依高度（水線在船身模組深度的 38 %）。
    - 車窗：腰線取有最多座位的玻璃乘員艙；車頂是最高點附近坡度小於 0.3 的部分，前後坡度陡的地方是擋風玻璃與後窗。
      駕駛艙擋風玻璃是從機頭爬升的那段（艙長的 35～60 %）。
    - 開放模組：外殼切到開口下緣，另畫舷緣、內壁、地板（開口下 0.45 m）、對封閉鄰段的隔艙壁；座位不剖視也畫。
    - 改模型後用 AutoShot 看設計畫面（放大、剖視開關）與世界中的範例；預覽與世界共用同一個模型。
    - 塗裝來自設計：機體設定的 `paintColor`、`accentColor`、`lowerColor` 與模組的 `paintColor`（空白＝機體主色）。
      不是塗裝的顏色（玻璃、窗框、甲板、保險桿、底盤、鍍鉻）固定在 `BodyModel`。
- 顏色參數：record 的 `String` 欄位名以 `Color` 結尾就是顏色（`RecordEditor.Type.COLOR`）：存成大寫的 #RRGGBB，可以不加 # 或用小寫輸入；
  設計畫面在欄位旁畫色塊，按色塊循環 `RecordEditor.PALETTE`。可以空白的（模組的顏色）由零件的檢查決定。
- 開發用 `/hdfvdev end` 把參數頁捲到最底（AutoShot 沒有滾輪），看最後幾個參數（例如顏色）時用。
  - 試算（`Bench.body`）：飛機與車只算空氣；船或有船底模組的另做拖曳（到設計速度 16 點，一趟縱傾超過 30° 就停）與橫傾（0～90°）。
    快艇一趟約 9 秒，在背景執行緒。拖曳是 2D+t：船艏朝下的縱傾超出它的範圍（畫面提示把重量往後移）。
- GameTest 的注意事項（0.5）：
  - `neoforge/build.gradle` 的 GameTest 伺服器設 `aerocore.gametests=true`：世界風靜止（和 AeroCore 一樣）。0.5 以前 hdfv 的 GameTest 有真實的風，
    機體的空氣負載會把水上飛機吹去撞池壁。
  - 載具的表在背景建，GameTest 伺服器比真實時間跑得快：生成前先 `blueprint.compile().warmUp()`，否則逾時都還在等表。
  - 直升機起動時測試裡的駕駛一開始就用踏板抵住旋翼的反扭力；放著不管時機身在停機坪上以 1.6 rad/s 打轉，會不會翻倒取決於極小的差異。
- 動力、工業與燃油（0.8）：
  - 兩個時鐘：物理（載具、引擎座與天軸的動力學、天車與門的動畫）用真實時間；工業（能量、引擎座燒的油、原油、產品、管線流量）用工作時間，
    `Pace.workSecondsPerTick`＝3600／`ticksPerWorkHour`（預設 3.6 s），工業量只經過這一個換算。加油機例外：每分鐘 50 公升是真實時間。
  - 1 FE＝`HdfvConfig.joulesPerFE`（預設 1000 J，工作時間）。電、天軸、管線網路只在方塊變動時以 BFS 重建（上限 4096 格），網路本身不存能量。
  - 多方塊：圖樣寫在 core 的 `Structures`；新多方塊要有圖樣、成員方塊、`PatternTest` 與 GameTest，`/hdfv dev place <圖樣>` 放出完整的一座。
    成員方塊成形時換成空模型，由控制器的方塊實體渲染器畫整台（`CncRenderer`、`PumpJackRenderer`）。
  - CNC：`CncConfig` 的聯動軸數決定能不能做（翼型、葉輪、斜孔要 5 軸），總軸數決定換裝次數與時間；`Plans.job` 給換裝、工時與能量。
  - 組裝室的門（`RoomDoorBlock`）是一整片門簾：只有每欄最上面那格有頂部（捲門的捲軸、機庫門的門板收納箱、船塢閘門的吊樑），門簾下緣由下往上
    穿過各排、收到頂部下方。PHASE 仍是整扇門同步（0～4，每步 `STEP_TICKS`，4 才能通過；GameTest 依賴這個時序），外觀屬性由 PHASE 與這格在欄裡的位置算出
    （`looks`）：`top`、`edge`（下緣在這格的位置：0 在下方、1～4 在 0／4／8／12 px（頂排 0／4／9 px）、5 在上方）、`axis`（沿相鄰的門，
    否則看兩側的牆）。放置（`onPlace`，`/fill` 與 GameTest 也走這裡）、鄰格變動、每一步、開關門時重算；舊世界存的門靠隨機刻在一兩分鐘內補上。
    模型與狀態檔由腳本產生（每種門 10 個模型＋頂部與門簾下緣條的貼圖 `<門>_head`、`<門>_trim`；物品仍用 `<門>.json` 的方塊）。
  - 柴油：燃料系統的燃料是柴油就是壓縮點火，性能來自 AeroCore 的 `DieselMap`；增壓器是活塞引擎的第 18 個元件 `turbo`（`none()`＝自然進氣，舊藍圖讀回成它）。
  - 載具燃油：
    - 機體編譯成乾重，每個 `fuelM3` 大於 0 的模組一個 `Part.Tank`（燃料是 `BodySettingsDesign.fuel`，密度由它決定）；沒有機體的載具用
      油箱零件 `FuelTankDesign`（`hdfv:fuel_tank`）。從藍圖生成的載具照設計的油量；組裝室做出的載具油箱是空的。
    - 引擎每步 `PartStep.burn`，上一步拿到不到一半就熄火；燃氣渦輪點火前也要一點點油（`JetRuntime.PRIME_KG_PER_S`），乾油箱點不著。
    - 加油的規則都在 `Fuelling`：引擎不燒的油拒絕（沒有引擎的載具什麼油都收），只加進空的或同種油的油箱。房間問題 `no_tank`：有引擎沒油箱。
    - 改機體質量或油箱時跑 `FuelTest`（水上飛機的油放油箱與放進機體質量，吃水與縱傾差 1 mm 內）。用 `properties().massKg()` 算重量的地方要加
      `VehicleState.fuelKg()`（直升機懸停測試就是這樣改的）。
  - 煉油：`Refining` 的每個轉化質量守恆（產品＋燒掉的＝進料）；`RefineryUnitBlockEntity` 每個工作 tick 依產能、進料比例、出料罐空間與電處理，零頭記著。
  - GameTest：有引擎圖的載具生成前先 `compile().warmUp()`。AeroCore 的表在一條共用執行緒建，同一批次的其他載具會等它；0.8 開發時加油機測試沒先建，
    同批次的浮台與組裝測試就逾時了。網路類的測試用真實 tick。
- 多方塊手動機床（0.9）：
  - 圖樣在 `Structures`（`LATHE`、`MILL`、`PLANER`、`BORING_MILL`、`DRILL_PRESS`）；車床、銑床、鉋床用 `Pattern.repeatAcross` 橫向加長
    （`Match.length`），搪床與鑽床用垂直的 `repeat`。`Structures.toolConfig` 由圖樣得到 `ToolConfig`（大小與附件），`Plans.jobOn` 用它；
    `Plans.job(..., null)` 仍是 0.8 的算法。工件外形是 `Workpiece.Envelope`（各規劃器填設計尺寸，沒有的由料重估成立方體）。
  - 方向：控制器在圖樣左邊的機床往觀看者的右邊長；面向北放時往西長。GameTest 的 `MachineGameTests.tool` 面向南放，避免床身蓋掉測試旁邊的天軸與電線。
  - 動力只從驅動（`DriveBlockEntity`）進：電動機座把 Forge Energy 轉給控制方塊的緩衝，皮帶輪座是天軸的端點；`MachineBlockEntity.power()` 只看驅動。
    機床的狀態多了「放不下」「缺附件」，畫面多一行（大小、附件、驅動）。成形時成員與控制方塊換成空模型，由 `ToolRenderer` 畫整台並在工作時動。
- 石油的畫面（0.9）：
  - 一個選單型別 `PetroleumMenu`（`Kind`：蒸餾塔、製程單元、鑽井架、抽油機、井口、加油機），方塊實作 `Configurable`（`set`、`view`）。
    設定只走 `PetroleumNetwork.SetPayload(pos, key, value)`：伺服器檢查玩家開著這個方塊的畫面、在 8 格內，再由方塊檢查範圍（超出回傳拒絕訊息）。
    畫面開著時每 10 tick 以方塊更新標籤送 `view()`，客戶端讀 `shown()`。空手使用打開畫面，蹲下使用維持 0.8 的快速操作與狀態列。
  - 預設值等於 0.8：沒有設定的方塊與舊世界照 0.8 的數字跑（`RefineryGameTests`、`PetroleumGameTests`、`FuelGameTests` 不變）。
  - GameTest 不能用 `makeMockServerPlayerInLevel`（登入時各 Mod 會送它收不了的封包，AeroCore 的 `world_wind` 讓測試失敗），
    改用 `PetroleumScreenGameTests.Viewer`（`FakePlayer` 的子類，自己開選單並用開啟資料建一份客戶端選單）。
- 衝壓家族、脈衝噴射、助推器、後燃器與氫（0.10）：
  - 性能只來自 AeroCore 0.28 的 `Ramjet`、`Scramjet`、`Pulsejet`（表在背景建）、`SolidRocket`、`Reheat`、`TurboRamjet`；hdfv 的設計只放參數、檢查、
    質量估計與控制規則（寫在各 `*ControlDesign` 與設計類別的說明）。範例對公開資料的比對在 `JetFamilyTest`，執行時規則在 `JetFamilyRuntimeTest`。
  - 不改舊行為：沒裝後燃器的渦噴與渦扇沒有 `reheat` 輸入、內部狀態與顯示值和 0.9 相同（舊存檔的內部狀態長度不變）；後燃器的襯套溫度規則是
    `AfterburnerDesign.LINER` 0.3（J58 的 Hastelloy X 襯套在 2144 K 後燃）。
  - 輸入與按鍵：`reheat`（B 切換）、`fire`（X 按住：助推器在按下的瞬間點火）、`start`（N 按住：脈衝噴射的起動空氣）；`AutoWiring` 把它們接到同名頻道，
    點火開關照舊不接（一直開）。
  - 超燃：進氣道依 Kantrowitz 極限起動；不起動時控制自己斷油，起動後再供油；燃料過多造成不起動後，控制只供到那個當量比的九成。範例 X-51 級的內收縮比
    1.2（1.4 時在 Mach 8 以下都不會自起動）；設計的數字清單有自起動馬赫數，超出控制的範圍時提出警告。
  - 脈衝噴射：簧片閥的磨損是零件的內部狀態（`PulsejetDesign.STATE_CYCLES`），用掉壽命的 0.8 後推力開始下降、1.2 時壞掉；換簧片用 AeroCore 的
    `VehicleState.setInternal`。範例 Argus 的表約 2～3 秒建好（GameTest 生成前要 `warmUp`）。
  - 助推器的藥柱是零件自己的油箱（`Part.Tank` 的 `own`）：加油機與油桶不加進去（`HydrogenStorage.tankTakes`），只用藥柱物品裝填；組裝室出廠時是空的。
    燒完拋棄的由 AeroCore 的 `PartVehicle.Jettisoned` 事件掉成零件物品（`Servicing`）。
  - 氫：液氫在任何容器都會蒸發（每個工作 tick 依容器的隔熱與表面積），普通儲槽約 13 分鐘工作時間就燒乾；壓縮氫只進高壓氣瓶組與管線。重組器的氫只有在畫面上
    指定出料罐時才是產品（`Refining.ROUTED_ONLY`，加計壓縮用電），沒指定時照舊燒掉，所以舊工廠照舊運轉；裂解爐的氫一直燒掉（爐子的熱量要它）。
  - 顯示值（給火焰、聲音、震動）：衝壓、超燃、助推器的第 0 個是火焰比例；有後燃器的引擎與渦輪衝壓是後燃比例與轉速；脈衝噴射是頻率、壓力擺幅、是否運轉。
  - 聲音：0.10 的 `HdfvSounds`、`EngineSounds`、`SynthStream` 與 sounds.json 在 0.19 拿掉，這四種引擎改由 0.19 的 `VehicleSound` 發聲（見〈聲音（0.19）〉）。
  - 火焰畫在 `PartRenderer.animate`（不透明的實體繪製，全亮度）；脈衝噴射的模型每格繪製（才能震動），網格依零件快取。
- 3D 與繪製（0.11，AeroCore 0.29 的 `api.mesh`、`PartLayers`、零件著色器；詳見 AeroCore 的 docs/RENDERING.md）：
  - Blender 產物（`assets/hdfv/textures/part/`、`finishes.json`、`models/part/detail/`、`textures/particle/`、`particles/`）進版本控制，建置不需要 Blender。
    腳本固定亂數、內容沒變就不重寫。改材質：`finishes_spec.json`、`look/Finishes`、`textures.py` 一起改，再執行 `scripts/blender.ps1 textures`；
    `finishes.json` 的 `hdfv:fx_*` 是 `fx.py` 的，兩支腳本都保留對方的項目。
  - 材質依顏色指定（`Looks.of`），模型用到新的固定顏色時在 `Looks` 加對應；同一部件要特定材質時用筆 `finish(...)`。
  - 細節零件經 `Details` 擺放，尺寸都來自設計。一圈或一排的扣件用細節庫的粗版（8 面）：0.11 開發時每圈 64 顆精細螺帽讓 J79 到 13.8 萬面。
  - `JetEngineModel.lathe`（JetFamilyModel、GasTurbineModel 共用）會把封閉剖面轉成順時針（法線朝外）。0.11 開發時好幾個剖面寫反了：
    遮蔽烘焙往金屬裡取樣成大片暗斑，薄壁的兩面互判「被覆蓋」，脈衝噴射管和火箭噴嘴整個消失。直接呼叫 `PartMesh.lathe` 時，(t, r) 也要順時針。
  - 從外面看不到的內部（燃氣渦輪第一級之後的壓縮機級、最後一級以前的渦輪、活塞引擎的曲軸連桿活塞氣門）標成 `inside`／`EngineModel.inside`，
    只在非剖視時標（剖視要看到它們）。氣流通道是通的，遮蔽的「外面的空氣」會流到葉片排，不標的話世界中照樣畫（J79 曾到 3.3 萬面）。
  - 會動的件記下轉子與轉速比（`EngineModel.Piece` 的 `rotor`、`rate`：預覽角度 × rate = 該轉子的角度）；新加的件要選對轉子
    （渦扇：0 風扇、1 核心；軸引擎：0 輸出、1 燃氣產生器，動力渦輪經減速比）。
  - 遠處用 `StandIn`（AeroCore 的 `MeshBuilder.STAND_IN`）：引擎類零件的靜態網格要加，否則 LOD2 仍是上萬面。
  - 熱的面用筆 `hot(true)`；溫度與發光由 `Exhausts.metalTarget`（顯示規則：燃氣渦輪噴嘴取排氣溫度 0.75、衝壓與火箭 0.55、脈衝噴射管 1050 K、
    活塞排氣管依負載）與延遲（升溫 4 s、冷卻 12 s）決定。
  - 尾焰：零件顯示值的尾端是 `Exhaust` 的三個值（溫度到 10 K、出口馬赫到 0.05、出口壓力比到 0.05；`Exhaust.first(design)` 是起點），
    活塞引擎是負載與運轉（`PistonEngineDesign.SHOWN_*`）。新的顯示值只能加在它們之後；`ShownExhaustTest` 檢查位置、範圍與穩態不變。
    模型沒有出口狀態的引擎（渦扇、軸引擎、脈衝噴射、超燃）是註明的顯示估計，只給畫面用。
  - 試車台同步引擎物品、運轉、轉速（50 rpm）與負載（0.05），有 ¾ 級距的遲滯，穩態不送（GameTest `anEngineStandShowsItsEngineAndSendsOnlyChanges`）。
  - 世界中的繪製經 `PartLayers.gpu(true)` 走 AeroCore 的 GPU 快取（`PartGpu`）；新的世界渲染器（方塊實體也是）照做，畫的網格必須來自 `MeshBakery`
    （每幀現烤的網格會被當成要保存而上傳）。
  - 效能（RTX 5070、1280×720）：設計畫面最多 J79 5.6 萬面；世界中 J79 靜態 LOD0 約 2.1 萬面（含 AO 細分），轉動件約 3 千面；
    場景 12.1 萬面時送出 1.5 ms／幀（不走 GPU 快取時 13.1 ms），107 FPS。
  - 穿模（使用者 2026-10-15「3d model 有些穿模」）：`/hdfvdev clip` 用 `ClipCheck` 查開著的設計在世界中的網格：依共用頂點分成一塊塊零件，
    邊對三角形求交，每對交叉寫一行到 `%USERPROFILE%\.mcmod\exports\clip\<設計>.jsonl`；`/hdfvdev focus x y z|off` 讓預覽繞那一點轉（近拍，`zoom` 到 40）。
    - 大部分交叉是故意的：葉片根在輪盤裡、螺栓在法蘭裡、同材料的方塊拼成一塊、軸上的環與支柱。要看的是離開軸的外部零件，
      而且交叉沿較小那塊的長邊延伸（穿過去或半陷進去）；只在一端交叉的是裝在上面。
    - 改了外部零件就跑一次，和改之前的檔案比。0.11 這樣找到並修掉的：點火線穿過機匣、可變靜子致動器穿過搖臂、相鄰噴嘴瓣互穿、
      附件齒輪箱的起動機與燃油泵畫在箱子裡面（改到箱子前面、用自己的大小）、噴射引擎的燃油管沒接到總管環、總管環穿過油箱
      （燃油管改沿齒輪箱側面上到環，油箱移到燃燒室機匣上方）、渦扇燃燒室的外部零件穿出核心整流罩（整流罩蓋住的標成 `inside`）、渦扇起動機從齒輪箱底穿出、
      渦扇燃油管自己橫過外涵道（改走底部的風扇框架支柱，支柱改在 0°、60°…）、活塞引擎的排氣尾管穿過飛輪齒圈與起動機
      （尾管從齒圈外經過，直列引擎的起動機改到進氣側；V 型兩側都有排氣，起動機不動）。
  - 範例引擎船的直列四缸放在後甲板上（-1.5, 0.76），駕駛座移到 -0.6：船身是圓舭、裝不下 0.88 m 高的引擎，放在船身裡時缸蓋與進氣從甲板穿出。
    渦軸船的引擎整個在船身裡、看不到，沒有動。`/hdfv vehicle samples` 只寫還沒有的藍圖，舊的 `engineboat.json` 要刪掉才會換成新的。
- 機器與載具的 3D（0.12，AeroCore 0.30 的開口、內裝、件、燈；詳見 AeroCore 的 docs/RENDERING.md）：
  - 開口：`core/look/BodyLayout` 由設計算出窗、門、座艙罩、舷窗、輪拱與座位，`BodyModel` 用 AeroCore 的 `SurfaceCut` 從外殼挖孔，
    內襯、門框與玻璃用同一組孔切。遠處（第 1～3 層）是沒開孔的外殼加畫上的窗；近處（第 0 層）是挖孔版。兩組要各自 `levels`，否則遮蔽互相覆蓋。
  - 玻璃標 `clear`、內裝標 `cabin`（第 0 層不判封閉、遠處拿掉）；車門是件（1 + 第幾個門），鉸鏈在前緣，`EngineModel.posed` 依 "doors" 轉。
  - 顏色由 `BodyModel.finish` 指定材質：不是塗裝的顏色在 `NOT_PAINT`，其他是機體設定的烤漆種類（`PaintFinish.finish()`）；玻璃用設定的顏色與不透明度。
  - 條紋是外殼格子的射線列（門板一起帶走）：飛機在側面中間、車在腰線下、船在乾舷；`AUTO` 是 0.11 的樣子（飛機窗線、船的水線帶）。
  - 文字（`core/look/Lettering`）用 `tools/blender/glyphs.py` 的字形圖集，貼花四邊形、只在第 0～1 層；從外面讀的方向：左舷往機頭、右舷往機尾。
    剖視時看得到遠側字的背面（貼花不剔除），是已知的樣子。
  - 車輪與起落架（`WheelRig`）：剛體件與它們隨顯示值的擺放；可收放起落架整支繞收放軸轉（`LegLayoutDesign.retractTimeS` 大於 0 才可收放）。
  - 燈：`VehicleLamps` 把細節庫的燈放上外殼並給 AeroCore 的 `Lamp`（角色是 `LampRules.Role` 的序號），亮度由 `LampRules` 依
    "lights"、"brake"、"selector" 與引擎運轉決定；閃爍相位用遊戲時間與 UUID 雜湊。新的燈要加角色與規則，`LampRulesTest` 檢查。
  - 地面效果：`core/look/GroundEffects` 是規則（方塊 id → 地面種類；滑移、載重、速度 → 粒子率與痕跡），`client/WheelEffects` 每 tick 依車輪的
    顯示值（AeroCore 0.30 的滑移與載重）放粒子、畫 `GroundMarks`；旋翼下洗用 AeroCore 的 `RotorWash`（顯示值第 6 個是誘導速度）。
    粒子（`tyre_smoke`、`dust`、`mud`、`snow_puff`、`road_spray`）與排氣共用 `ExhaustParticle` 的 1500 個預算。
  - 機器方塊（M1）：`tools/blender/machines.py` 用方塊像素寫形狀（北面是正面），輸出 OBJ＋MTL 到 `models/block/machine/`、
    方塊模型（NeoForge 的 `neoforge:obj`、`flip_v`、`map_Kd #材質`）與 64 px 貼圖。貼圖座標由面在方塊裡的位置投影（不出 0～1，
    圖集裡的貼圖不能重複鋪）；`gauge`、`panel` 這類貼花材質拉滿自己的面。每個方塊最多 800 面（`BlockModelTest`）。
    模型沒填滿方塊的方塊要 `noOcclusion`，否則旁邊的方塊面被剔掉、看得穿。
  - 成員方塊的貼圖（M2）：16 px 原圖在 `tools/blender/blocks16`，`machines.py` 依亮度加浮雕與顆粒重畫成 64 px；改圖樣要改 16 px 原圖再執行腳本。
  - 成形多方塊（M3）：`MachineMeshes` 把不動的部分依（種類、朝向、大小、附件）烘焙一次、走 GPU，會動的件每幀用少數面建；
    光取控制方塊與上方方塊較亮者（不透光的控制方塊裡面是 0，抽油機曾整台畫黑）。抽油機的衝次隨 "Pumping" 一起同步（GameTest `pumpJackSyncsStrokes`）。
    抽油機只在井口真的沒空間時才是「滿」：每 tick 抽不到 1 公升時井口收進 0 公升（零頭記著），0.8～0.11 因此在「滿」與「抽油」之間每 tick 切換，
    每次切換都送一次更新、曲柄停停走走。
  - 機器效果（M4）：光是 AeroCore 的燈（`MachineEffects`，`late` 才有光暈），煙、火花與蒸汽在方塊的 `animateTick`（`MachineParticles`）。
    開發用 `/hdfvdev working <x y z> on|off` 只在這個客戶端顯示「工作中」（截圖用）。
  - AeroCore 的 `MeshBakery` 以弱引用保存快取鍵：鍵要是呼叫端一直拿著的物件（零件、建好的網格）。0.12 開發時用每幀新建的字串與 record 當鍵，
    每次垃圾回收後就重烤（同幾個網格每秒重烤一次）；現在多方塊、機器燈、施工中的骨架與蒙皮、鷹架都以自己保留的網格當鍵。
  - 施工中的載具（`HdfvVehicleRenderer`）：骨架依豎起的框數、蒙皮依八分之一烘焙；其他零件照 `PartVehicleRenderer` 的畫法（含件）。
  - 效能（2026-10-17 使用者「你先優化」，用 JFR 對 AutoShot 的場景剖析）：
    - 沒在工作的機器（CNC、機床、停著的抽油機）用 `MachineMeshes.drawResting`：運動件依停的位置建一次並烘焙，不要每幀建。
    - `Looks.of` 記住每個顏色找到的材質（原本每個四邊形都搜一次）。
    - 玻璃只畫一面（不剔除背面）：兩面會讓不透明度加倍，晚段佇列的頂點也加倍。
    - 伺服器的熱點在 AeroCore（接觸、波浪輻射、船體剖面），改法見 AeroCore 的 0.30 效能。
  - 0.12 的已知限制：
    - 機身第一次烘焙在背景要 1.2～1.7 秒（計畫預算 0.4 秒），烘焙好之前那架載具的機身不畫。
    - 機器加四架載具：優化後，剖析場景的送出時間約 2.5 ms，每幀約 14 萬面，FPS 達垂直同步上限 120；伺服器 tick 平均 22～26 ms。
      版本表的 8.3 ms、28 FPS 是另一個有海面的場景（V12 的計畫），優化後沒有重拍。
    - 剖視時看得到遠側機身文字的背面；直升機客艙椅背頂端與機身肩部的內襯還交叉 1～2 cm、飛行艇船首纜樁的一端陷進甲板，都在艙內。
  - GameTest 注意：生成有引擎表的載具前先 `compile().warmUp()` 並放在自己的批次；0.12 開發時一個新測試在預設批次生成水上飛機，
    它的引擎表在共用執行緒背景建，同批次 `hull_pool` 的平台與汽艇等不到表而逾時。
- 痛車與烤漆房（0.13，AeroCore 0.31 的執行時材質與 `replaceBlueprint`）：
  - 貼紙是設計的外觀參數（`BodySettingsDesign.stickers`、`WingStructureDesign.stickers`，`core/look/Sticker`）：每台痛車因此有自己的編譯零件與烘焙，
    同步與存檔跟著藍圖。沒有貼紙時不寫這個鍵（舊設計的 JSON、工件雜湊、零件快取都不變）。`image` 是 SHA-256，讀取時就擋掉其他字（之後當檔名用）。
  - 網格在 `core/look/Decals`：貼紙在表面參數（機身 x 與 t、機翼 eta 與弦向 s）上切成多邊形，帶著圖的 UV，在那裡剪裁（機身兩端、
    車門與門縫、控制面的條帶），寫成四邊形時才算 3D。離表面 7 mm 起每層加 0.25 mm（大於遮蔽的覆蓋門檻 6 mm）；只在第 0～1 層，
    第 0 層跨車門的分到門件。圖的上方：表面直立時是世界的上，朝上或朝下的面是 +x（車頭、機頭）。
  - `Finishes.decal(sha)` 是執行時材質，客戶端 `DecalCache` 在第一次畫到時定義（透明暫代，圖到了換內容，不重新烘焙）。
    貼圖 alpha 改成零件著色器的光澤：不到一半透明的切掉，其餘是貼膜的光澤 0.6。渲染器在 `animate` 呼叫 `DecalCache.wantFor(design)`。
  - 圖片：`core/paint/DecalImages`（縮圖、PNG、檢查）、伺服器 `DecalStore`（世界資料夾 `hdfv/decals/<sha>.png`）與 `DecalIndex`（SavedData，
    記上傳者；同一張圖多人上傳時大家都算），`DecalNetwork`（上傳每塊 30000 bytes，低於伺服端自訂封包上限；下載每塊 256 KB；每人每 10 秒 64 次請求）。
    開發用 `/hdfvdev decal testpicture` 產生並上傳測試圖（棋盤、上方紅帶、向上箭頭、左下 L：看得出方向與鏡像）。
  - 烤漆房是自己的方塊（不是 `Room` 的值：`Room.of` 選不到它，每台載具都會變成 `wrong_room`），掃描沿用 `RoomScan`；規則 `core/paint/PaintBoothRules`，
    工作量 `PaintJob`（面積、罐數、張數、工時、電），只改外觀的檢查 `Repaint.looksOnly`（設計 JSON 在放回原本外觀後必須一樣）。
    完成時 `PartVehicle.replaceBlueprint`；改了零件設計時零件的表在背景重建（GameTest 先 `compile().warmUp()` 新藍圖，否則佔住共用執行緒，
    0.13 開發時噴射艇等三個測試因此等不到表）。
  - `MeshBuilder.add` 保留每個四邊形自己的標記，不會蓋上 `piece(n)` 筆：要放到某個件就用 `AirframeRenderers.asPiece`（或 BodyModel 的 `addAs`）。
    0.12 的控制面、螺旋槳與旋翼因此都在件 0：控制面畫在機翼根部（以鉸鏈為原點的網格），螺旋槳與旋翼多一份不轉的（缺件時改走舊路徑，又畫一份）。
    0.22 查到同一個錯：`WheelRig` 用 `m.add(...)` 加車輪，0.15 起車輪都在件 0，在世界中（烘焙的路徑）不轉、不轉向、不隨懸吊動；
    現在 `Builder.piece` 把畫進來的四邊形都蓋上件號。
  - 0.22：AeroCore 烘焙時對**所有件建好時的位置**一起算遮蔽與剔除，件要建在靜止時的位置。控制面原本以鉸鏈為原點、疊在翼根，
    主翼的蒙皮在鉸鏈處封起來（凹槽）後就落在主翼「裡面」被剔掉；現在烘焙時移到鉸鏈（`AirframeRenderers`：`turned` 繞鉸鏈轉）。
  - 開發用（AutoShot）：`/hdfv dev sticker add|clear`（不用烤漆房貼紙，`latest` 是最後上傳的圖）、`/hdfv dev booth <pos> open|finish`、
    `/hdfvdev decal pick`（0.14：和「上傳圖片」按鈕同一個系統選檔視窗，先把 384×384 的測試圖寫到 `.mcmod/tmp/hdfv-sticker-pick.png`）、
    `/hdfvdev booth page|part|look|place|view|action`、`/hdfvdev close`（關掉畫面）。開畫面時玩家要在控制台 8 格內，否則選單立刻關掉。
- 開車操作與儀表（0.14）：
  - 按鍵到頻道的規則都在 core 的 `drive/DriverInputs`（可離線測），車上有什麼看 `drive/CarKit`（由藍圖的接線與設計讀出），儀表顯示什麼看 `drive/Dashboard`；
    客戶端的 `DriveControls` 只讀鍵、送出、在動作列提示，`DashboardHud` 只畫。新的開車功能先加在這三個 core 類別與它們的測試（`DriveControlsTest`）。
  - 有車軸（`AxleDesign`）的載具是「車」：車用鍵在 `DriveControls.CAR` 情境，飛行鍵在 `FLIGHT` 情境，所以可以共用 R、F、G、B（NeoForge 對同一個鍵
    的所有對應都送點擊，`isDown` 再看情境）；空白鍵手煞車和原版跳躍共用。在車上 `FlightControls` 只送燈、門、雨刷，煞車與排檔鍵由 `DriveControls` 讀。
  - `steer`：+1 往左，平順、隨車速縮小（`DriverInputs.steer`，起落架鼻輪也用）。0.13 以前沒有任何鍵送它，組裝出的車不能轉彎。
  - 自排在 D 時按升降檔就進 M：`shift` 送 ±1 兩個 tick 再一個 tick 的 0（`ShiftRules` 只在上升邊換檔，連按會排隊）。
  - 手排的自動離合在伺服器的 `ShiftRules`（輸入 `auto_clutch`、`throttle`，記憶 2 格）：沒接時是 0，舊手排車與既有測試不變；手排的內部狀態多兩格，
    存檔裡舊手排車的內部狀態長度不同，讀回時零件從頭開始（一次）。客戶端設定 `hdfv-client.toml` 的 `autoClutch` 決定送不送 1。
  - 接線：分動箱的 `lock` 接 `centre_lock`，車軸的 `lock` 接 `diff_lock`（兩個以上能鎖時最前面的偏移 −1）。0.13 以前組的車接在 `lock`，
    `DriveControls` 也照送（`CarKit.lock`）。
  - 速度表的刻度：斷油轉速在最高的非超速檔（齒比 0.95 以上；CVT 取 1:1）的車速，往上取到 120／160／200／260／320／400 km/h；
    用最高檔估會到 360 km/h（超速檔在斷油轉速到不了）。
  - 測試用的車在 core 的 `drive/TestCars`（手排轎車、分時四驅車）：只給測試與開發指令 `/hdfv dev drive spawn manual|4x4`（只在開發環境），不是範例車
    （使用者：不要新增範例車）。
  - GameTest 注意：`succeedWhen` 裡丟 `IllegalStateException` 會讓整個 GameTest 伺服器崩潰（「Exception in server tick loop」），而
    `GameTestAssertException` 會一直重試到逾時（之後的判斷會在別的時間點成立或失敗）；要在某個時間點判一次，用 `startSequence()` 的
    `thenIdle`／`thenExecute`。手煞車（後軸，煞車扭矩 0.35）約 1.1 m/s²；D 檔加一點油門，變扭器就推得動手煞車（真車也是）。
- 引擎室與座位（0.14 追加，AeroCore 0.32 的 `BodyShape.overhang`）：
  - 零件的外形是 core 的 `look/Envelope`（零件座標裡有名字的盒子，加 `inputX`／`outputX`：傳動從哪進、從哪出），`look/Envelopes.of(design)` 依種類給出。
    活塞引擎的幾何全在 core 的 `look/PistonEngineLayout`：`PistonEngineModel` 只畫，座標都從它讀，外形也從它算，兩邊不會各算一套。
    改活塞引擎的外部零件時，在 layout 改，並確認 `envelope()` 還包得住：`/hdfvdev envelope [all]` 把模型超出外形 3 mm 以上的頂點
    依最近的盒子分組記在記錄檔（每組點數、最遠幾 mm、範圍），每個點寫到 `%USERPROFILE%\.mcmod\exports\envelope\<設計>.jsonl`（寫法同 `clip`）。
    0.14 的檢查補進外形的：汽缸蓋兩端的吊耳（發電機柴油超出 8 cm）、線圈的導線與接頭、節溫器的感知器與散熱器水管、缸體的感知器、
    凸輪蓋兩端與排氣口螺帽、自排的冷卻管、CVT 後皮帶輪殼。之後活塞引擎、變速箱與分動箱的模型都在外形內（只剩線束前端 ≤ 9 mm 的幾個頂點）。
    已知限制：噴射與渦輪家族的外形只是長度與直徑的圓柱，模型會超出（J79 的後燃器約 1 m、J58 0.5 m、渦槳 0.18 m），裝進車身引擎室時判斷偏寬鬆。
  - 緊湊的進氣（直列引擎）：歧管從進氣口往外、往下彎到汽缸蓋旁，長度仍是設計的 `runnerLengthM`（長的往外伸，不往上爬，最高到線圈頂）；
    集氣室在汽缸蓋旁、節氣門在它前端；空濾箱取代紅色錐形濾清器（直列在缸體旁，V 型在左前角）；散熱器寬高 1.4：1；排氣從集氣管往下，
    排氣煙方向 −Y。範例直列四缸約 0.73 m 高（原 0.87 m）。自排變速箱的冷卻管只到 0.3 倍比例，不再伸進引擎。
  - 規則（`assembly/Fitting`）：直接掛在機體 `engine_bay` 或 `mN_engine` 上、有外形的零件（引擎、電動機、變速箱、分動箱）每個盒子都要在機體裡，
    穿出超過 0.5 cm 就是 `part_does_not_fit`（零件、哪一面、幾公分），組裝室不開工。底部是開的（油底殼可以低於車身底線）。
    機頭的 `engine`、`mast`、`transom`、自由放的零件不檢查（飛機與船的引擎照舊）。只看零件和車身，不看零件之間（鐘形殼本來就包著飛輪）。
  - 擺位（`Placing`）：引擎放上引擎室時從 0 往後每 5 cm（到 −0.5 m），再往前（到 +0.3 m），取第一個留得下 1 cm 間隙的位置；
    變速箱的 `inputX` 接在同一點上引擎的 `outputX`（飛輪面），分動箱接在變速箱後（`Placing.behind`；橫置變速箱沒有面，用外形的兩端）。
    範例轎車也用這兩個方法：引擎在 `engine_bay` 往後 5 cm（比 0.13 往後 15 cm），變速箱接在後面，沒有座位零件。
  - 座位：車的乘客艙（`PASSENGER_CELL`）除不盡時人少的那排放最前面、用外側位置，位置離車側 `CAR_SEAT_EDGE_M`（0.52 m）；範例轎車前 2（±0.39）後 3。
    飛機與船的座位排法沒變（`SeatLayoutTest`）。機體編譯時駕駛座（`BodyDesign.driverSeat`）排第一，第一個上車的人坐駕駛座；船的掌舵位因此是座位 0。
    存檔裡舊載具的座位讀回時重新編號。
  - 儀表板的位置在 core 的 `BodyLayout.Dash`（`InteriorKit` 畫它，`seat_clash` 也用它）；機體有座位時，座位零件離機體座位 0.4 m 內或卡進儀表板是
    `seat_clash`，`Placing` 也不再把座位零件放到 `m2_bottom`。後窗只到有玻璃的乘員艙段（後車廂蓋是烤漆，不是深色玻璃加置物板）。
    後視鏡是細節庫的 `DOOR_MIRROR`（車身色），坐在從車身伸出 13 cm 的底座上。
    儀表板下方中間有中控台下段（從排檔前 0.25 m 到防火牆、從地板到儀表板底）：引擎在引擎室時，變速箱會伸到儀表板下（傳動通道），
    範例轎車的鐘形殼原本在腳踏空間露出來。
  - 舊世界的範例轎車藍圖不會自動換（`/hdfv vehicle samples` 只寫還沒有的）：刪掉 `sedan.json` 再寫。舊藍圖在組裝室會被擋下；已經在世界中的車照常能開。
- 多人連線測試（0.14，使用者 2026-10-17 同意 Minecraft EULA）：
  - 伺服器在 `%USERPROFILE%\.mcmod\runs\hdfv\server`：`eula=true`、離線模式、只聽 127.0.0.1、平坦世界 `AeroCore MP QA`、創造模式；
    `ops.json` 只有 Dev（離線 UUID `380df991-f603-344c-a090-369bad2a924a`，等級 4）。Dev2 是一般玩家。
  - 執行：`.\dev.ps1 :neoforge:runServer`（背景；記錄在 `server\logs\latest.log`，看到 `Done` 再開客戶端），`.\dev.ps1 :neoforge:runClient`（Dev）、
    `.\dev.ps1 :neoforge:runClient2`（Dev2，資料夾 `client2`，第一次執行前把 `client\options.txt` 複製過去以免出現首次啟動畫面）。三個 Gradle 同時跑在同一個專案可以。
  - AutoShot 計畫第一行寫 `server 127.0.0.1:25565`（AeroCore 0.32）：不要寫 `localhost`，這台電腦先解析成 IPv6 `::1`，只聽 127.0.0.1 的伺服器拒絕連線；伺服器指令用 `client <指令>`（以那個玩家的權限），`cmd` 不能用。
    伺服器沒有接主控台輸入：由 Dev 的計畫 `client stop` 關掉，兩個客戶端在連線中斷時自己關掉。
  - 兩個客戶端加入的時間差十幾秒，計畫不要靠固定的時間點對齊：Dev 用 `tp Dev2 ...` 擺 Dev2 的視角，Dev2 每 5 秒拍一張。
  - 新世界沒有範例藍圖：先 `client hdfv vehicle samples` 再 `aerocore vehicle spawn`（寫到伺服器的 `aerocore/blueprints`）。
  - 伺服器的 hdfv 設定在伺服器資料夾的 `config/hdfv-server.toml`（不在世界的 `serverconfig`）；`allowUploads` 改成 false 測拒絕上傳。
  - 0.19 聲音補驗：`client fill` 在 Dev 附近沒載入的區塊會失敗（「該座標尚未載入」），先 `tp` 過去等一下再放方塊；遠處的測試靠伺服器的 view distance 8
    （實體追蹤 128 m），Dev 留在車旁讓那裡的區塊載入。兩個客戶端記錄裡的 `hdfv sound:` 行（`log on`）就是結果，HUD 截圖每 2.5～5 秒一張。
  - 0.14 多人測試找到並修掉：`DecalCache` 用 `NativeImage.read(byte[])` 讀圖，它把整張 PNG 放進 LWJGL 每個執行緒 64 KB 的堆疊，
    超過 64 KB 的圖在所有客戶端都讀不進來（「Out of stack space」是 Error，不是 Exception，每 30 秒重試又失敗）；改從串流讀。
    測試圖只有 12 KB，使用者自己上傳的 584 KB 圖才發現。讀遊戲裡的大圖不要用 `NativeImage.read(byte[])`。
- 底盤與懸吊（0.15，AeroCore 0.33 的 `api.frame`、`FrameFlex`、`api.wheel.kinematics`、`Clearance`；詳見 AeroCore 的 docs/FRAME.md 與 WHEELS.md）：
  - 構件只在 `look/ChassisLayout` 產生（x 從車架後端起、y 從大樑底面起）：梁有限元素、3D 模型（`ChassisModel`）、質量與加工（`StructurePlanner.chassis`）都讀它。
  - `ChassisDesign.analysis()` 依設計快取（最近 32 個）：質量、軸站之間的扭轉剛度矩陣、兩端車軸間每度的扭轉剛度、中段彎曲剛度、
    每個構件在四種標準工況 × 1.5（`ULTIMATE`）下的使用率。最吃力的構件超過 1 是 INVALID（指到大樑壁厚或該站橫樑的壁厚）；
    扭轉剛度每度 1～60 kN·m 以外是外推。工況的車重是設定的設計總重（組裝室再用實際質量檢查 `frame_overloaded`）。
  - 編譯成 AeroCore 的 `FramePart`（質量、掛載點、濃縮的扭轉矩陣）；一台載具只能一個車架，車架不能裝成有俯仰或滾轉。
  - 範例卡車大樑（2026-10-17）：AeroCore 的壕溝改成地面下降 15 cm、開口截面分 St Venant 與翹曲後，原本的 270 × 80 × 8 mm 槽鋼仍過載；
    改成 300 × 90 × 8 mm 鉚接槽鋼、200 × 100 × 6 mm 方管橫樑、10 個站點（約 710 kg、每度 3.8 kN·m、使用率 0.96）。
  - 車殼 `BodySettingsDesign.onFrame`：車殼的彎曲不再是 INVALID（車架承受），要掛在底盤的 `body` 點（否則 `body_needs_frame`）。
    承載式車身（用途是車、不是 `onFrame`、兩個以上相距 5 cm 以上的軸點）編譯時帶 `BodyStructure.torsion` 的扭轉：範例轎車每度約 26 kN·m，
    0.14 以前的車開起來會小幅改變。
  - 擺位（`Placing`）：零件交出的掛載點沒有順序（AeroCore 的零件用 `Map.copyOf`），所以依名稱排、數字照大小（`axle_2` 在 `axle_10` 前）；
    車軸先找 `axle_\d+`，跳過同一零件上前後 `AXLE_APART_M`（0.3 m）內已有車軸的點（車殼的 `m1_axle` 和 `front_axle` 在同一處）；
    車殼找 `body`。底盤引擎室上的零件由 `Fitting.inBody` 換到同一底盤上車殼的座標（車殼掛點的反變換），用 0.14 的同一套檢查與往後滑。
  - 接線（`AutoWiring`）：分動箱前面（x 較大）的車軸接前輸出、後面的接後輸出；同一輸出接兩根軸就是剛性連結的雙軸（AeroCore 的同一傳動列）。
    氣壓車軸的 `ride_height` 綁定加 0.5 的偏移：沒人送時就是設計車高；`DriverInputs` 送 0、+0.5、−0.5（H 鍵，車用情境，不和飛行的 H 衝突）。
  - 房間（`ChassisRules`）：`frame_overloaded`（載具加滿油比設計總重重）、`body_needs_frame`、`no_wheel_arch`（車軸前後 15 cm 內沒有車殼的軸點）、
    `touches_ground`（設計壓縮時車殼底線、車架構件、引擎類外形的底低於車輪下的地面；AeroCore 的 `Clearance`）。
  - 硬點：`HardpointsDesign` 存相對輪轂的座標（右輪），只寫有值的點；簡化模式時 `AxleDesign.kinematics()` 是 null，AeroCore 不用表，物理和 0.14 一樣。
    硬點模式的表依設計快取（64 個）；連桿在行程內卡死是 INVALID（`hardpoints.mode`）。氣壓懸吊的 JSON 鍵只在 `springKind` 是 AIR 時寫。
  - 後輪轉向是控制規則：`SteerSchedule` 包住 `AxlePart`（同 `ShiftRules`），依車速混合低速與高速的比例；要拿到裡面的 `AxlePart`
    （顯示值的位置、調高閥）用 `SteerSchedule.axleOf`。
  - 3D：`ChassisModel`（構件依截面拆成板件、彈簧吊耳、車殼座、引擎座），設計畫面與世界共用；硬點模式的車軸由 `WheelRig` 依表擺放
    （轉向節與車輪照表的輪轂路徑與姿態，繞過球頭的主銷轉向），設計畫面畫靜止的同一組連桿。
  - 測試車（`TestCars`）：`sixBySix(Frame)`（標準、軟：鉚接槽鋼每度 0.52 kN·m、硬：方管加 X 撐每度 11.5 kN·m）、`rearSteerSedan`、`airSedan`。
  - 開發用：`/hdfvdev frame [all]` 把構件與使用率寫到 `exports/frame/<設計>.jsonl`，`/hdfvdev kinematics [all]` 把行程表寫到 `exports/kinematics/<設計>.csv`；
    `/hdfvdev shown` 把最近載具伺服器端的 `ready`／故障、客戶端收到的同步字串、每個零件的顯示值、車軸每個件的姿態（轉角與軸）寫進記錄。
    `/hdfv dev layout <控制台> 6x6|shell|sunk_6x6`（車殼裝在三軸大樑上、單獨的車殼、車軸掛太高）與 `/hdfv dev drive spawn 6x6|rearsteer|air|hardpoints` 給截圖用。
  - 設計畫面：頁面參數經 `Designs.parameters(design, page)`，設計可以實作 `RecordEditor.ShownOnPage` 依整個設計藏參數（梯形大樑的站點沒有管架的框高）；
    空白的硬點欄位用灰字顯示它代表的點（`Designs.hint` → `AxleDesign.hardpointHint`）。選項值用小寫（`/hdfvdev set hardpoints mode hardpoints`）。
  - 已知：範例前軸的簡化參數換成硬點時，主銷內傾 31°、磨胎半徑 −75 mm（0.6 的擺臂參數沒有考慮主銷；實車約 10～15°），沒有改範例的手感。
  - AutoShot 注意：QA 世界載具多，新生成的車要等引擎表在共用執行緒排到，0.15 時要 25～50 秒才 `ready`；之前的車停在生成的姿勢
    （顯示值全是 0：輪子在全伸位置、轉向不動）。截圖前等 60 秒，或用 `/hdfvdev shown` 確認 `ready true`。
- 改裝廠、空氣套件、輪圈與車燈（0.16，AeroCore 0.34 的 `PartVehicle.refit`／`VehicleState.refitted` 與 `body.RoadAero`）：
  - 改裝廠是自己的方塊（同烤漆房，不是 `Room` 的值），規則 `refit/RefitRules`：封閉、門要過得了改前與改後的車、改後放得進房間、兩盞工作燈、有電、
    一台不在施工中的車、要焊的工作要焊接台、倍率在 `Scaling.MIN`～`MAX` 內；再加上載具自己的問題（`RoomRules.vehicle`，組裝室也用它）。
    問題鍵：房間的 `hdfv.refit_shop.problem.*`，載具的沿用 `hdfv.room.problem.*`。
  - 改什麼只看 `Refit.adjustables`（頁、參數、類別 SHAPE／SIZE／KIT／RIM／TUNE）：加減零件、換種類、改引擎內部與材料是組裝室的。新的可調參數加在 `Refit` 的規則表。
    機體模組的座位數（`seats`、`seatsAbreast`、`seatPitchM`）在 SIZE：每座要 0.5 m³（`BodyDesign.check`），人不會跟著縮，
    範例轎車的後座模組剛好放得下 5 人，所以要縮小得先拆座位（後座 3 人時可到 0.9 倍左右；再小則引擎室放不下引擎的附件）。
    每次編輯在伺服器套在待改藍圖上，零件變 INVALID 就拒絕；整台縮放相對於進廠時的車（`Refit.rescale` 用目前倍率換算，不累乘）。
  - 整台縮放 `Scaling`：等速相似（葉尖與活塞速度不變：轉速 ×1/s、同齒比同車速；彈簧 ×s²、阻尼 ×s^2.5、胎壓與煞車壓力 ×s、乘員質量不變）。
    新參數一定要有規則：依名稱字尾自動決定，沒字尾的要列進 `DIMENSIONLESS` 或 `EXPONENTS`（`ScaleTest.everyNumericParameterHasARule`）。
    s＝1 寫出同一份 JSON。估計的質量照各設計自己的估法（座椅、玻璃、變速箱的相關式），所以整車質量比 s³ 小（1.2× 轎車約 ×1.4）。
  - 工作量 `RefitJob`：台上組裝的零件依 `Workpiece` record 逐件比對，只要不同的新工件（用改好的零件設計藍圖在機器上做；改裝廠可寫空白藍圖）；
    房間組裝的零件（機體、底盤、套件）依 `StructureWork` 的 key 比對，改了的重做、舊的料退一半；都相同時是調校（0.5 h，車軸定位 1 h）；
    機體形狀變了整台重漆；車燈依「樣式＋大小（公分）」比對：新的每盞一個燈具、拆下的退回，只移動、轉向、改用途或顏色的不用。
    開工時從房間的箱子用 `Staging` 取（創造模式免費）。
    已知簡化：規劃器的懸吊與最終傳動工件不隨彈簧率、齒比變（給定質量），所以換彈簧、最終齒比算調校；輪胎、輪圈尺寸、機體、縮放才換工件。
  - 完成時 `PartVehicle.refit(next, 密度)`（油依 `HdfvFluids` 的密度截在新油箱內），再依房間地板放回（`AssemblyPlan.min` 的 y＋2 cm）；
    拆下的工件、料、燈具放進箱子（滿了掉在控制台上）；槽位的整車藍圖換成改好的（`AutoWiring.stripped`）。
  - 空氣套件 `part/body/BodyKitDesign`（機體的 `kit` 頁，只有車的機體有；沒有套件時不寫 `kit` 鍵）。位置、質量、加工與氣動元件都在 `look/KitLayout`：
    模型（`BodyModel.kit`）、質量（材料 × 面積 × 3 mm，金屬一半厚）、`StructurePlanner` 的 `kit` 工作與 AeroCore 的 `RoadAero` 元件都讀它。
    下壓力的趨勢與量級見 AeroCore docs/BODY.md〈車用空氣套件〉；側裙的封閉最多 `SKIRT_SEAL` 0.6（固定側裙），
    0.16.1 起 `KitLayout` 再乘側裙長度占車底的比例（側裙只在輪拱之間，AeroCore 0.35.1 的 `sealing` 是漏氣面積的比例）。
    有擴散器就當作有平整底板（`smooth`；AeroCore 的 `SMOOTH` 是量產車的底板）。改套件的對應時跑 `BodyKitTest.aMildKitIsAProductionTrackCars`
    （溫和套件 200 km/h 整套 1～2 kN、車底 0.3～1 kN）。
    貼著車身的件是 `KitLayout.Panel`，沿 `BodySurface` 產生（使用者 2026-10-18「車頭的模型很怪」：原本是固定位置的方塊，機頭 0.45、引擎蓋 1.2 m 時
    分流板比車頭寬 50 cm、低 25 cm，前輪眉離車身 16 cm，進氣口一端埋進引擎蓋 3 cm）：擋風板、分流板、唇沿車頭下緣（`KitLayout.Edge`：
    從前輪前沿著門檻邊、繞過車頭端面捲邊的下前緣），側裙在門檻邊下，輪眉從輪拱邊的車身往外 `flareM`、再斜回車身，進氣口的底貼著引擎蓋。
    其他（尾翼、擾流板唇、車頂架、越野護桿）還是方塊。沒有自己顏色時，擋風板與唇用保險桿色、側裙用下緣色（`BodyModel.kit`）。
    車的保險桿深色帶改依外殼的射線列分界（`BodyModel.bumperRows`），翹起的車頭上不再是鋸齒邊。`KitLayoutTest` 用三種機頭檢查這些件都貼在車身上。
  - 0.18 車頭（`part/body/BodyFrontDesign`，車的機體的 `front` 頁；每個參數 AUTO 或空白＝自動，`RecordEditor.BlankIsAuto` 讓空白存成 NaN；
    全部自動時不寫 `front` 鍵，所以舊設計與路線快照不變，`DesignJsonFreezeTest` 凍結 0.16 的範例 JSON 雜湊）：
    幾何都在 `look/FrontLayout`：`resolve` 依車頭端面占正面面積的比例（`BOXY_SHARE` 0.45）與機頭模組種類把 AUTO 變成具體的
    （圓滑轎車：包角燈組、細橫條水箱罩與廠徽、車身色保險桿、寬下進氣口、圓霧燈；方正：方燈、全寬水箱罩、黑色保險桿、窄槽、方霧燈；
    `CAB_OVER`：擋風玻璃下的水箱罩面板、保險桿角的方燈、踏階保險桿）；`of` 產生 `KitLayout.Panel`（保險桿外殼沿 `KitLayout.Edge` 往外 `bumperOut`、
    車牌、進氣口、霧燈凹槽、水箱罩、隆起；平頭的擋風玻璃、雨刷、踏階）。原廠的車頭燈由 `FrontLayout.lamps` 給（`LampSpec.Style.CLUSTER` 是包角燈組，
    `LampRules.Role.FOG` 近光以上亮），保險桿高度以下的燈推到保險桿面上；空氣套件的車頭下緣沿保險桿外殼。`BodyModel.front` 只畫。
    平頭駕駛室的擋風玻璃是畫在正面上的（不是切開的開口），從駕駛室裡看正面是背面。
  - 0.18 卡車（`part/truck`、`drive/Hitch`、neoforge `truck/Hitching`）：
    - 底盤站點多 `FIFTH_WHEEL`（`fifth_wheel`：第五輪的底座，`heightM` 在大樑頂上；耦合面再加第五輪零件自己的高度）、`KINGPIN`（`kingpin`：
      拖車承盤，從大樑底算，負的在下面）、`LANDING_LEGS`（`legs_1` 左、`legs_2` 右，大樑外 `LEG_OUTSIDE_M`）；每個底盤多一個 `cab` 點（大樑頂、車架前端，
      平頭駕駛室的前端放在這裡，依機體最低點放、下緣高出大樑 `Placing.CAB_RAISE_M` 5 cm：外殼蓋住大樑、輪拱罩住前輪；0.4 m 時像踩在高蹺上、保險桿離地 1.4 m）。車架多 `neckM`、`neckX`（鵝頸，只在不為 0 時寫）。
      梁 FEM：主銷站點是 AeroCore 的 pins（在牽引車的彈簧上：每節點兩倍輪子的彈簧），第五輪站點承受一半載重；站不住的車架（拖車沒有主銷）是「車架撐不住」。
    - 掛上的規則在 `Hitch`（顎口內側向 0.1 m、後 15 cm、承盤在第五輪下 0～10 cm、夾角 30°、相對速度 0.5 m/s、主銷尺寸、靜態主銷載重）；
      折刀角由駕駛室後牆與拖車前角的迴轉算（碰不到就 90°）、俯仰由鵝頸下緣與牽引車後輪頂的間隙算（至少 3°）；轉送 `brake`、`handbrake`、`lights`、`selector`。
    - 按鍵 J（車用，飛行時是起落架）讓 `couple` 頻道加一（計數，不是脈衝，伺服器不會漏掉）；`Hitching` 在伺服器看每台有第五輪的車的計數變化。
      牽引車的 `coupling` 頻道 0 沒掛、1 掛上、2 正在放支撐腳（存檔、同步、儀表的 TRL 燈）。卸下：支撐腳放下，承受四分之一主銷載重
      （或搖動時間加 1 秒）後解開、拉上拖車手煞車。
    - 支撐腳是 AeroCore 的 `LegPart` 加 `Telescope`：腳是小的高壓實心輪，煞車綁定固定 1（`AutoWiring`）；`Placing.add` 加一支會成對放。
    - `ChassisRules`：`no_wheel_arch` 只管機體長度內、機體底低於輪胎頂 5 cm 的車軸；新問題 `fifth_wheel_off_frame`、`kingpin_without_legs`、`engine_in_cab`
      （引擎頂高出平頭駕駛室地板 0.45 m 或碰到座位）；`Fitting` 不把平頭駕駛室當引擎室（引擎與變速箱在駕駛室下往後伸）。
    - 製程：`PartPlanner.simple` 的第五輪與支撐腳是機加工工作階段加的。
  - 輪圈與胎壁字（只改外觀；`Repaint.looks` 也有，烤漆房能改）：`RimDesign.rimStyle`、`rimColor`、`lipDepthM`（輪面往內，輪距仍是車軸的），
    `TyreDesign.sideText`、`sideTextColor`；預設值不寫進 JSON。`WheelModel.styledFace`、`sideLettering`（字形圖集，只在第 0～1 層）。
  - 車燈 `look/LampSpec`（`BodySettingsDesign.lamps`：null＝原廠的燈，不寫鍵；空清單＝沒有燈，寫 `"lamps":[]`；用 `ownLamps()` 判斷，不要用 `isEmpty()`）：
    側面用機體表面的 x、t（同貼紙），前臉與後方用相對的 across、up，所以改車身形狀時燈跟著。燈 2 cm～3 m（`LampSpec.MAX_SIZE_M`，燈排與放大後的 LED 條）。
    `look/LampLayout` 是唯一的擺法：原廠的燈（`auto`）與自訂的都經 `place`，`BodyModel.lamps` 只畫。原廠的燈和 0.12～0.15 的位置相同，
    但車端的端面只要有正面面積的 `LampLayout.FACE_SHARE`（6 %；0.15 以前要 15 %，機頭鈍度 0.45 的 0.8 × 0.35 m 車頭就沒有燈）就把燈、水箱護罩、車牌放在端面；
    比這更尖的車端，燈成對放在離端點 0.3 m 的側面、往那一端轉到 60° 為止。
    近光頭燈同時有遠光、尾燈組的透明鏡片是倒車燈（同 0.12）。方形、LED 條、燈排由 `BodyModel.housing` 程式產生。顏色常數在 `LampLayout`（`VehicleLamps` 引用）。
  - 開發用（AutoShot）：`/hdfv dev refit <控制台> open|finish|lamp <角色> <樣式> side|front|rear <a> <b> <成對> <大小>|unlamp <序號>`，
    `/hdfvdev refit page|part|set|scale|scroll|lamp|view|action`。
  - 畫面：被拒的編輯（`RefitNetwork` 回來的訊息）同時寫進報告的 `Refused`／`RefusedAt`，畫面在 3D 預覽底部（工作頁在下方）以紅字顯示 5 秒；
    選到機體時預覽不加黃色選取（整台都是機體，蓋住造型的變化），其他零件照舊。
- 機加工機器（0.17，AeroCore 0.35 的 `api.manufacture`；計算見 AeroCore 的 docs/MANUFACTURE.md）：
  - 九族是 `Station` 的家族（`family`），能力全由頭決定：`Station.can(op, w)` 對家族轉給 `Head.can`。頭的工時在 `Machining`（AeroCore 的公式，
    以參考零件校準，`HeadTest.timesAreThoseOfRealMachines`）；沒有頭的舊站照 0.9 的公式，所以舊站的工時與能力不變。
  - 工序的相容：被換成新機種的步驟保留原本的製程、只加 `Feature`（汽缸的磨削＋孔＝珩磨、齒輪的銑削＋齒＝滾齒、噴嘴的鑽孔＋噴孔＝放電鑽孔…），
    舊站不看特徵；新插入的步驟標 `Operation.added`，只插在某個舊步驟之後或最後（絕不在成形之前），去除量是自己的。`legacy()` 拿掉兩者就是 0.16 的那一步。
    `core/src/test/resources/routes-0.16.json` 是 0.16 的路線快照（所有範例、測試車、底盤：每步的欄位與能做的站）；`RouteMigrationTest` 檢查新路線
    去掉插入的步驟後和快照相同、能做每一步的機器只增不減。改規劃器時這個測試會抓到動了舊步驟；要刻意改舊步驟就得改快照並說明。
    新製程（鋸切、精研、拋光、去毛邊、攻牙、螺紋、外形切割、鉚接）都加在 `Process` 的最後；名稱 `hdfv.process.<製程>` 與 `hdfv.process.<製程>.<特徵>`
    （`MachiningAssetsTest` 檢查每個頭與每條範例路線用到的都有）。
  - 舊工件：`Workpieces.Data.route`（沒有這個欄位的是 0.16 做的，版本 0）。機器在 `job()` 前把輸入與輸出裡同一張圖的舊工件改到新路線
    （`Routes.migrate`：做完 s 步的對到舊第 s 步在新路線的位置，它後面插入的步驟要補做；做完的照舊算做完）；`whereNext` 只算不寫，組裝台只數做完的。
  - 數控驅動座 `nc_drive` 是驅動格的第三種（帶電動機，接電線）：不用人、細一級（和數位讀數尺不疊加）、快 15 %、工作時多 1 kW；0.9 的機床也能裝。
    電加工的驅動格只收電動機座或它；雷射、電漿頭裝在皮帶輪座上時狀態是 `NEEDS_ELECTRIC`。
  - 耗材：機器的耗材格接在輸出格之後（舊存檔的格子不動；CNC 4 格、其他切削機與焊接台 2 格）；漏斗的槽位順序是耗材、輸入、輸出，耗材格只收耗材、
    不取出，輸入格不收耗材（磨床例外：鈍的刀具組放輸入格給工具磨頭重磨）。用量在 core 的 `Wear`（刀具：切削時間 ÷ Taylor 壽命，高速鋼 n 0.125、
    金屬陶瓷 0.25；砂輪：去除體積 ÷ 磨削比；電極、銅線、電解液、石榴石、氣體、鉚釘各自的算法），隨工作進度扣：會磨耗的扣耐久、以數量計的整個扣，
    零頭存在 `Owed_<耗材>`。缺耗材是 `NO_TOOL`（畫面說缺哪一種）。工具磨頭把鈍的刀具組重磨回新（`REGRIND_S` 900 秒工作時間），超過次數就報廢。
    伺服器設定 `consumables`（預設開）。會磨耗的耗材堆疊上限 1，配方只能做 1 個（多了 Minecraft 拒絕整個配方，0.17 開發時鋸條就是這樣）。
    GameTest 用 `MachineGameTests.supply(be)` 放耗材（`machine()`、`tool()` 已呼叫）；自己放機器的測試（`CncGameTests.cnc`、引擎座帶 CNC）要記得呼叫，
    否則停在 `NO_TOOL`。
  - 圖樣在 `Structures`（`GRINDER`…`RIVETER`），版面在 core 的 `MachineFrame`（往哪長、大小範圍、控制格、頭格、成員符號），`Structures.toolConfig`、
    `FamilyRenderer`、`MachineScreen`、GameTest 都讀它。每族只有一個頭格，沒有頭或兩種頭都不成形。`/hdfv dev place <族> <變體>`：變體用 `+` 連
    （`bare+nc`、`bare_pulley+laser`），頭寫它的種類或去掉 `_head` 的名字（`cylindrical_grinding`、`wire_edm`）。
  - 資產由 `tools/machining_assets.py` 產生（方塊狀態、模型、物品模型、掉落、挖掘標籤、石榴石礦的生成、16 px 原圖；兩份語言檔照錨點就地插入，
    不整檔重寫），再執行 `scripts/blender.ps1 machines` 放大成 64 px。0.23 起配方由 `tools/recipes.py` 寫（它拒絕同樣排列的配方，含鏡像）。
  - 外觀：`FamilyRenderer` 依 `MachineFrame` 擺（同 `ToolRenderer`：固定部分依種類、朝向、大小、頭、驅動、附件烘焙一次，會動的工作時每幀建、停著時烘焙一次）。
    零件組（`tools/blender/machining_kit.py` → `models/part/machine/`，`MachineKit` 讀，`paint` 換成機族的顏色）放有細節的頭、砂輪、滾刀、龍門、鋸弓…，
    床身、工作台、水槽、立柱由程式畫。零件組的 Blender +y（操作者那側）朝機器正面、+x 朝圖樣的 −x（`Draw.kit` 用正的旋轉，不鏡像）。
    工作中的工具點每 tick 放一次火花、冷卻液、切屑、水霧或煙（48 m 內）；放電、電漿、雷射用 `MachineEffects.tool` 的燈。
  - 空氣分離機：單一方塊，吃電，依 `GasSupply`（AeroCore 的 `AirSeparation`、`Gases`：最小分離功 ÷ 第二定律效率 0.12，加上壓縮到 200 bar）灌 50 公升氣瓶；
    空鋼瓶進、滿的出，蹲下使用換氣體。
  - 組裝室的鉚接：房間範圍內（或離它 2 格內）有成形的鉚接機時，機體的鉚接（`StructurePlanner` 的 RIVET 接合）每顆從手工的 20 秒變成壓鉚 15 秒（孔仍手鑽）、自動鑽鉚 6 秒，衝擊鉚等於手工（`Machining.roomRivetS`、`AssemblyPlan.laborHours(head)`，控制台掃描時找最快的一台）；房間規則不變，鉚接的工作照樣要焊接台。
- 聲音（0.19，AeroCore 0.37 的 `api.acoustics` 與 `client/sound`；物理與接口見 AeroCore 的 docs/ACOUSTICS.md）：
  - 規則都在 core 的 `sound`（可離線測、`SoundBench` 輸出 WAV）；neoforge 只讀同步狀態、分配聲道、播放。哪個零件發什麼聲在 `PartSounds`，
    新零件要決定它的聲源（或明確不發聲）；聲源的參數只來自同步到客戶端的東西（顯示值、轉子轉速、控制頻道、AeroCore 0.37 的 `v` 與刮擦）。
  - 一台載具一條混音：每個聲源有聲功率級，`balance` 把總聲壓級經 AeroCore 的 `Loudness` 換成一個播放增益，聲源依各自的等級分；
    分項音量超過 100 % 的部分在混音裡套。車內：封閉的機體依 `Transmission` 的牆隔音，加上只在車內的（方向燈繼電器）。
  - 串流分配（`VehicleSounds.choose`）有遲滯：播著的至少留 `HOLD_TICKS`、等的要大聲 `SWAP_DB` 才換，騎著的一定有；第一輪沒有遲滯時兩台車在門檻附近每 tick 交換。
    排名用 `rankDb`（跟著聽到的等級立刻上升、峰值保持 `RANK_HOLD_TICKS` 8 秒，之後每 tick 降 `RANK_FALL_DB` 0.25 dB）：掛著 D 檔怠速的自排車，
    負載幾秒一次地來去（HUD：754 rpm 負載 0.80 與 931 rpm 負載 0），等級擺 15 dB 左右；用當下的等級時 QA 世界兩台停著的轎車每 2～6 秒互換串流，
    只降不保持（每秒 5 dB）時仍每 3～4 秒互換（2026-10-19 補驗時發現）。
    其他聽得到的用循環聲（目前狀態的複本、引擎在每八度三格的轉速上烘焙 1.5 s，音高補差）。試車台上的引擎也搶串流（`Hum.stand`）。
  - 機器：方塊實體在伺服器 tick 呼叫 `SoundSync.report(this, hum)`（沒在工作時 null）；變了才送（換製程或運轉、負載差 0.1、轉速或功率差 5 %，至少隔 10 tick），
    新追蹤的玩家在 `ChunkWatchEvent.Sent` 收到現況。新機器也這樣接；`MachineSound.process` 決定它的製程聲。一次性的用 `SoundSync.event`
    （門、焊、鉚、天車、噴油、完工），不要再用原版的 `playSound`（焊工村民的原版工作聲保留）。
  - 喇叭（U，所有載具，頻道 `horn`）、方向燈（車用 ← → 左右、↓ 警示燈，頻道 `indicators` −1／1／2，方向盤回正自動取消：`DriverInputs`）；
    方向燈是車的頭燈與尾燈組裡的琥珀色燈（`BodyModel.indicator`，改裝廠的燈具數不變），儀表最前面有箭頭，和燈同步閃（`LampRules.blinkOn`）。
  - 活塞引擎的顯示值尾端多了增壓（`SHOWN_BOOST`，`ShownExhaustTest` 刻意改成 3 個）；油門用 `input(id, "throttle")`。
  - 開發用：`/hdfvdev sound hud on`（每台載具：串流 ▶、循環 ~、聲壓級、增益、都卜勒、延遲；最大聲的三個聲源與它們的參數；房間的 RT60、EFX、車艙；機器循環）、
    `log on`、`record <秒>`（實際送出的樣本，WAV 到 `%USERPROFILE%\.mcmod\exports\sound\`）、`render <轉速> <負載> <秒>`（`SoundBench`）；
    `/hdfv dev sound hum <pos> <製程> [功率 負載]`、`event <pos> <事件>`、`/hdfv dev drive mount`（騎上自己最後生成的測試車）、
    `launch <m/s>`（那台沿車頭方向給一個速度，音爆用）、`remove`（拿掉那台，飛到哪裡都行，只要區塊還載入著）。
    `tools/sound_report.py <WAV 或資料夾>` 畫頻譜圖並列出峰值與 1/3 倍頻程。
    `log on` 另外記：車上操作的聲音（喇叭、繼電器、手煞車、雨刷）開始時的等級與在這個聽者混音裡的增益（車外聽者的繼電器是 0）、
    遠處載具的 `far … gone`、超音速載具每 tick 的軌跡點與到達數（`supersonic …`）、串流停下時的排名等級。
  - 音爆（AeroCore 的 `SourceTrack.foldArrives`）：載具餵給軌跡的點由 `VehicleSounds.Entry.trackPoint` 給，快速載具用同步速度推、每 tick 往畫的位置拉 0.3：
    伺服器慢時客戶端先推過頭再被拉回幾十公尺，拉向聽者那一步比音速快，就是一個假的音爆（2026-10-19 多人驗證）。`Cues.boom` 每台 40 tick 內只響一次。
  - 遠處載具（`FarVehicles`）：伺服器最新的報告裡已經沒有的（只在 `KEEP_TICKS` 內留著）不再開新的循環聲：怠速的車偶爾一次報到 90 dB，循環做好時已經過時，
    以前會響幾個 tick。
  - 設定在 `hdfv-client.toml` 的 `[sound]`（Mod 清單的設定畫面）；字幕、設定、按鍵的翻譯由 `SoundLangTest` 檢查。
- 機場（0.20，AeroCore 0.38 的 `FieldLamps`、`WorldAir`、`VehicleTracks`、選用著色；詳見 AeroCore 的 docs/RENDERING.md 與 ATMOSPHERE.md）：
  - 規則都在 core 的 `airport`（可離線測：`AtcRulesTest`、`RunwayTest`、`AirfieldTest`、`AirportPlannerTest`）；neoforge 只收集快照（`VehicleTracks`、`WorldAir`）、
    套用結果、同步。管制狀態只在伺服器，不經控制頻道（客戶端的 `ControlsPayload` 能寫任何頻道）；請求只收駕駛、是飛機、在管制區內的，每秒最多 4 次。
  - 飛機的判斷用 `AircraftKind`（有機翼是定翼、有旋翼是旋翼機），不要用 `Room.of(bp) == HANGAR`（螺旋槳雪橇與氣墊船也是）。
  - 燈的清單由伺服器送（`AirTraffic`：8 km 內的玩家，變了才送），客戶端 `AirfieldLights` 交給 AeroCore 的 `FieldLamps`：視距外沒有區塊也沒有方塊實體。
    燈具與跑道頭放下、拆掉時登記到 `AirfieldData`；生成的機場在規劃時整份登記（`AirportWorldgen.register`）。
  - 塔台的畫面只用 `AirportNetwork` 的封包送給開著畫面的人；`ControllerBlockEntity` 的方塊更新只帶「有沒有電」（渲染器轉雷達用）。
    塔台的狀態封包分 `NEWS`、`ATIS`（飛行員要的）、`REMINDER`（每 5 秒）：聊天欄與「剛說的話」只記前兩種。
  - 世界生成：原版結構最多約 272 m，放不下 600～900 m 的跑道，所以用「依區域種子規劃、每個區塊只放自己那一片」的地物（同 0.8 的油田）。
    `AirportPlanner.VERSION` 與 `REGION` 不要改（世界裡已生成的半座要對得上）；`AirportPlannerTest` 凍結一份規劃的雜湊。
    規劃的地形探測只用產生器的雜訊（執行緒安全）：粗看用便宜的 preliminary surface（`initialDensityWithoutJaggedness`，比實際地面低 1～14 m，
    給 `ROUGH_M` 的餘裕），網格用 `getBaseColumn`（每欄約 2 ms，對齊世界座標快取）；村莊與掠奪者前哨看放置點加生態域（只看放置點時幾乎每個區域都被擋）。
    門檻是在真實世界調的（`/hdfv dev airport why|probe`，用無視窗的伺服器加 RCON 查，命令一次只查一個區域：一連串在同一個 tick 跑會觸發 60 秒的看門狗）：
    1.21 的開闊地很起伏，最初的規則（整條 1.4 km 整平、挖 8 填 6、不准有水）49 個區域一座都沒有；現在挖 24 填 18、坡帶 32 m、4 % 離群、
    跑道 40 % 可在其他生態域、水 15 % 以內填掉，三個種子 75 個區域 20 座，一個區域規劃 0.3～4.4 秒。改門檻後用同樣的方法量密度與時間。
    世界第一次載入時記下已有的 `region/*.mca`，規劃碰到其中任何一個就不放：舊世界（QA 世界、使用者的「新的世界」）只在完全沒去過的 512 m 區域出現機場。
    地物在 `top_layer_modification`（樹之後）；只寫自己的 16×16 欄；方塊實體（蓄電池）在世界生成的 DUMMY 下建立（`level` 是 null 的方法要容許）。
    平坦世界預設不生成（GameTest 是平坦世界）。`/hdfv dev airport build [長度]` 在腳下照同一個放置程式蓋一座（每 tick 6 個區塊），給舊世界的 QA 用。
  - 0.8 的 `asphalt` 就是可畫標線的 `MarkedSurfaceBlock`（同一個 id；兩邊各登記一個會衝突）。模型是 multipart：每個四分之一格依 `paint` 與 `mask` 選平面或標線。
  - 塔台的模型：成員方塊成形時的模型是空的、控制方塊照舊；`TowerRenderer` 依層數畫，管制室玻璃是 `hdfv:tower_glass`（反射、遠處 0.8 亮度）、混凝土是
    `hdfv:board_formed_concrete`（法線貼圖）。PatternFrame 是鏡像的座標：自己組的四邊形用 `Tower.quad`／`face`（依世界法線決定繞向），零件組用 `kit`（同 `FamilyRenderer`）。
    `face(..., inside, ...)` 的法線朝「遠離那一點」：洞的側面要拿洞外的點（0.20 管制室地板的艙口側面曾朝外，從管制室看下去是背面，看穿到外面）。
    遮蔽是每個角一個值、在面內內插：四角都在凹角、中間開闊的長面（塔身、管制室天花板）會整面變暗（AeroCore 的細分只看四角的差，通用修正排在 0.39），
    所以 `TowerRenderer.split` 把塔身切成約 3 m、地板與天花板約 1.5 m 的格子。方塊實體只在區塊載入時畫：視距 12 時約 190 m 外看不到塔，遠處只剩 `FieldLamps` 的燈。
  - 世界生成的樹：樹冠會長進隔壁區塊。整地拿掉樹幹時，先（清之前，連到樹冠的葉子還在）找出它 6 格內的葉子，清完再拿掉沒有別的樹幹撐住的（照落葉的規則，
    世界生成不會自己落葉），連同上面的雪；只碰相鄰的區塊（世界生成只准寫 3×3）。清欄位要到 `WORLD_SURFACE`（`_WG` 是長樹前的地面）。
    沒有這兩步時，先整好的區塊上會留下隔壁晚生成的樹的葉子，停機坪上一片浮空的樹冠（2026-10-19 在新區塊看到）。
  - 規劃的時間：玩家周圍 3×3 個區域每 5 秒在背景執行緒（`hdfv airport planner`，低優先）先規劃。不然區域的第一個區塊在世界生成執行緒上規劃 0.3～10 秒，
    其間不產生任何區塊，伺服器等區塊時一次落後 6～7 秒（2026-10-19 實測；先在相鄰區域停過再進去就沒有落後，直接傳送進還沒規劃的區域仍會等一次）。
    沒有機場的區域不存，重開伺服器後再規劃一次（在背景）。開發指令 `nearest`、`goto`、`view` 在伺服器執行緒同步規劃還沒規劃的區域（九個要 10～17 秒），只給 QA 用。
  - 區塊票（`AirTraffic`，設定 `towerTickets`）：只有飛機（`AircraftKind.flies`）在塔台無線電範圍兩倍（6 km）內時，替塔台加半徑 2 的票。
    不要再加「玩家在 8 km 內」：機場每幾公里一座，會讓 8 km 內每座塔台周圍 5×5 個區塊一直載入，而且區域一規劃完就當場生成（2026-10-19 從區域檔的建立時間查到）。
    迴路由塔台算：塔台沒載入時它的燈是暗的（燈的清單照樣送給 8 km 內的玩家；0.20 初版從 2～4 km 外看夜間跑道燈靠的就是那張多餘的票，QA 要在旁邊放一架飛機）。
  - 村民：值班＝醒著、`JOB_SITE` 是這座塔的控制方塊、在 8 格內；原版尋路不爬梯子，所以值班台在塔底。
    機場地圖的交易只找已經規劃過的區域（`AirportWorldgen.nearest(..., false)`）：交易在伺服器執行緒，規劃一個區域要約一秒。
  - GameTest：`FakePlayer` 不能騎乘，所以飛行員呼叫的正向路徑只在 core 測（`AtcRulesTest`），GameTest 測拒絕與接手；改時間（夜間）的測試放自己的批次；
    村民睡覺要在床上（不在床上的會立刻醒）。
  - 大、中、小（0.21，`AirportTier`；規劃器 `VERSION 2`）：
    - 每個區域先用自己的一組亂數抽能有哪些等級（五分之一大→中→小、三分之一中→小、其餘只有小），候選點的亂數不動；每一級依序試遍所有候選點（大的先）。
      等級的長度、寬度、挖填、塔台段數、燈、蓄電池、風向袋都在 `AirportTier.Spec`；塔台段數用 0.20 同一個亂數折進該級的範圍（不多抽）。
    - 大機場的每個數字等於 0.20：0.20 世界裡只生成一半的機場靠存檔的規劃（沒有 `Tier` 讀成大）用新程式生成剩下的。
      `AirportPlannerTest.aPlanOf020IsStillLaidOutTheSame` 凍結 0.20 兩份規劃的 `groundDigest`（地面、標線、燈、機庫、塔台位置；不含塔底的東西），改配置時不能動它。
    - 規劃器版本升級時（`AirfieldData.plannerVersion` 比 `VERSION` 小），載入當下已有的區域檔也加進舊區域：0.20 規劃成「沒有機場」的區域不存，新規劃器可能在那裡放小機場，
      已生成的區塊就會只有一半。
    - 小機場沒有塔台也沒有燈（燈由塔台送與點亮）。塔台只認領離自己 1 km 內的跑道（`Runway.distanceTo`，量到鋪面），附近大機場的塔台不會把小機場的跑道當成自己的。
    - 配置不再用索引取停機坪與機庫（`AirportLayout.Parts`）；鏡頭與開發指令用 `apronAcross`、`taxiwayAcross`。
  - 塔內（0.21）：
    - 塔底與塔身裡面是真的方塊：鋼樓梯（原版樓梯的行為，走上去）繞鋼柱在塔身 3×3 的外圈螺旋，一格升一層，最上一階固定在活門後側（`AirportLayout.TOP_CELL`），
      其他活門格蓋格柵（`HATCH_COVER`）；`TowerRenderer` 只在格柵都在時畫活門的欄杆。0.20 生成的塔照舊是梯子。
    - 每一階朝「走上來的方向」（從前一格來的方向，`AirportLayout.stairFacing`）：轉角那一階是直線的延續，從它高的那半格轉出去，踏上下一階低的那半格。
      0.21.0 初版每一階朝「下一格」，轉角那一階的高半格橫在來路上，玩家（寬 0.6 m、自動上台階 0.6 m）要一次爬 1 格：上不去也下不來
      （使用者 2026-10-20 在自己的世界裡走：「上不去 下不來」）。站在低半格時頭頂到那一層上方 3.3，所以每一階的格子往上三層都要空，
      最後四階的格子不蓋格柵（`HATCH_COVER` 是 0、1、6、7；初版連第四階上面也蓋，走到那裡頭會頂到）。
      `AirportGameTests.aPlayerWalksUpTheStairToTheCabAndBackDown` 讓一個有碰撞、會自動上台階的玩家實體（`FakePlayer.move`）從塔底走上管制室地板再走下來
      （兩種高度：最下一階在直的格與在轉角）；用 0.21.0 的配置跑，它在第一個轉角卡住。core 的 `theTowersStairBatteriesAndLampsFit` 只看格子，看不出這種事。
    - 0.21.0 已生成的塔（QA 世界、使用者的「新的世界」）由 `TowerBlockEntity.repairInside` 在每次載入後第一次跑時修好：轉樓梯、拿掉那格格柵、
      把床掉頭（有人睡時下次再做）；只動版面上該有的鋼樓梯、頂階是鋼樓梯時的那格格柵、照舊方向擺的床（`aTowerGeneratedBy0210IsPutRight`）。
    - 室內燈（`interior_lamp`）由塔台每 40 tick 依有沒有電點亮（塔底與塔身裡面的格子，每盞 20 W）；管制室圖樣要求是空的，所以塔台在上層玻璃的中間格放原版的光源方塊
      （`Blocks.LIGHT`，可取代，所以不破壞圖樣），沒電、解散、拆掉時收回（`CabLight` 存在方塊實體）。
    - 電纜槽（`cable_duct`）是 `CableBlock` 的整格地板版本（`duct`）：世界生成的電纜不經 `updateShape`，原本只畫一顆中心頭。
    - 床的兩半各由自己的區塊放（`AirportFeature.thing` 依區塊判斷），床頭不會因為跨區塊而不見。
    - 村民只認領走得到床頭旁 1 格的床（原版 `AcquirePoi` 的路徑要停在床頭 1 格內）：0.21 初版床頭朝後，被牆、蓄電池格與上方第二階（頭頂沒空間）圍住，
      生成的管制員從來沒有 `home`、夜裡站在門外。床頭改朝大廳後當場認領、夜裡睡在床上（遊戲中用 RCON 查記憶確認）。
      `AirportPlannerTest.theTowersStairBatteriesAndLampsFit` 從門口走塔底（兩格高的空間）檢查到得了床頭旁與管制員的位置；改塔底的東西時它會抓到。
      GameTest 裡的管制員關掉 AI、由測試讓它睡，所以抓不到這種事。
    - 內裝只畫在牆與天花板（塔底）與管制室（`cabin`）：零件組新件 `cab_console_side`、`supervisor_desk`、`ceiling_light`（往下掛，`DetailKitTest` 列為不站在 0）、
      `distribution_board`、`cable_tray`、`notice_board`、`wall_clock`。夜裡的窗燈是另一個只有燈罩的網格（`TowerRenderer.lenses`，燈號 0 塔底窗、1 樓梯窗、2 嵌燈、3 螢幕、4 管制室天花板），
      走 `PartLayers.lamps`；亮度依天空的暗度（`ClientLevel.getSkyDarken(partial)`，同 AeroCore 的 `FieldLamps`）：客戶端的 `Level.getSkyDarken()` 只在進世界時算一次，
      0.21 第一次在遊戲裡看時夜裡窗燈全暗就是這個原因。
    - 塔底的牆與天花板也要 `TowerRenderer.split`（同 0.20 的塔身）：四邊都在凹角的房間牆面整面發黑。
    - 沒有塔台的小機場：`AirportLayout.tower(p)` 是 null，用到它的地方都要判斷（0.21 第一次在遊戲裡看時，小機場每個區塊的放置都在塔台那步丟例外，
      風向袋與等待標誌都沒放；`AirportGameTests.aSmallAirfieldHasNoTowerYetPlacesItsWindsock`）。
- 合成配方與科技樹（0.23，AeroCore 0.40 的 `api.metallurgy`、`petroleum.Methanol`；計算見 AeroCore 的 docs/METALLURGY.md）：
  - 配方 JSON 全部由 `tools/recipes.py` 寫（`py -3 tools/recipes.py`），不要手改 `data/hdfv/recipe/`：產生器刪掉它沒寫的檔案。
    它擋：同樣排列的工作台配方（有形狀的也比鏡像）、0.22 有而現在沒寫又不在 `DROPPED`（寫明去了哪裡）的配方、金屬量超出那一類
    `GAME_SCALE` 範圍的方塊（約真實的十分之一：機器 40～300 kg、工作台 10～100、成員 5～80、頭與附件 10～120、電子 2～80、容器 10～150、建材 0.3～25、
    燈與電線 0.1～6）。材料一律用 `c:` 標籤（`#c:ingots/iron`、`#c:dusts/redstone`、`#c:glass_panes`…；hdfv 的金屬也是），成品寫物品 ID。
    配方存在的檢查仍用「ID＝檔名」：組裝配方在 `recipe/<id>.json`，電爐與電解在 `recipe/electric_furnace/`、`recipe/electrolysis/`，Create 的在 `recipe/create/`。
  - 科技樹：`TechTree` 從配方算（物品的做法＝每組材料、機器、電各取一個的「且」；做法的階段是各組最早者的最晚，物品取最早的做法；
    世界的東西、原版物品、`MINED` 的礦是 0）。標準件比做得出它每一步的機器（控制器、成員、驅動、該族的頭、那一步要的附件，只有數控驅動座
    夠精度時加它，CNC 加模組）晚一階；電（`POWER`）是發電機的階段，電動驅動、CNC、焊接台、電弧爐、電解槽、空氣分離機、煉油要它。
    `RecipeRulesTest` 從配方檔建樹：每個配方的產物都做得出來、錨點的順序（鑄造爐、鍛壓機、組裝台、碳鋼刀具 0；床身鑄件、車床、鑽床、鉋床、蒸汽機 1；
    車削軸、銑床、發電機、鋁 2；切削齒輪、磨床 3；滾珠螺桿晚於螺紋機；CNC 不早於滾珠螺桿；線性滑軌晚於 CNC；電加工與切割台不早於線性滑軌）。
    遊戲裡 `TechStages` 用配方管理器的配方建同一棵樹，物品提示寫「科技階段 Tn：在哪裡做」；資料包改配方時跟著變。
  - 標準件：設計分類 `COMPONENT`，每種一個範例（創造模式有圖紙），零件種類 `component_<id>`。路線由 `ComponentPlanner` 用 `Shop.piece` 定，
    `ComponentRouteTest` 檢查每種一個工件、質量等於設計、那一級的機器做得到（A 鑄造爐與鍛壓機、B 車床鑽床鉋床、C 銑床磨床齒輪機、D CNC 與精加工），
    並和 `core/src/test/resources/routes-0.23-components.json` 比（第一次跑時寫；要改標準件的路線就刪掉重寫並說明）。既有設計的路線不動
    （`RouteMigrationTest`、`DesignJsonFreezeTest` 照舊）。機器做完標準件圖紙的最後一步就輸出 `HdfvComponents` 的物品，不是工件。
  - 組裝台的配方模式：圖紙格空著時照 `hdfv:assembling` 配方組（要人、不吃電、工時同零件組裝 `Plans.assembleS`：300 秒＋每件 60 秒＋每 kg 3 秒，
    產生器以四捨五入寫進 JSON，`RecipeRulesTest` 核對）；`MachineBlockEntity.recipes` 依用料件數多到少、再依 ID 排序（車床的料也湊得出尾座與鑽頭，
    要先組車床），畫面的按鈕換 `selected`；沒有符合的是 `NO_RECIPE`。只扣配方要的數量。放了圖紙時照舊組零件。
  - 中間件（`Intermediate`：電動機、變壓器、控制板、伺服馬達、泵、液壓缸、齒輪組、熱交換器）的質量要等於它的料，`RecipeRulesTest` 核對。
  - 電弧爐（100 kW）與電解槽（60 kW）：`SmelterBlockEntity`，每個工作 tick 從電網拉到額定功率，批次的能量進滿就出料；配方的 `kwh` 由
    `core` 的 `Metallurgy.Batch` 呼叫 AeroCore 算，`MetallurgyRecipesTest` 核對 2 % 內（鋼 0.40 kWh/kg、Hall–Héroult 13.0、MgCl₂ 13.0、
    75 % 矽鐵 8.26、鉻鐵 3.55、鈦渣 1.95、Kroll 15（規則）、真空熔煉＝`Casting.meltJ` ÷ 0.65、瀝青碳纖維 25 MJ/kg、過氯酸銨 8.09）。
    漏斗（任一面）放進輸入格、從成品與副產物格取出。
  - 合金爐：`AlloyFurnaceBlockEntity.handler(side)`：上方進輸入格（含焦炭等燃料當材料）、側面的燃料進燃料格、其他進輸入格、下方只取成品與副產物。
    焦炭是燃料（`data/neoforge/data_maps/item/furnace_fuels.json` 1660 tick；蒸汽機認 `c:coal_coke`）。
  - 切屑：切削、磨削等去除金屬的步驟，去除量的 `chipRecovery`（伺服器設定，預設 0.8）以料塊回到輸出，零頭存在 `Chips_<材料>`；
    複材、橡膠、木料不回收（`Chips.kg`）。機器只在成品、料頭與切屑都放得下時才開工（`fitsAll`；0.22 以前只有一格空位時會弄丟其中一個）。
  - 碳鋼刀具組（`carbon_tool_set`）：Taylor n 0.10、C 鋼 20 m/min、非鋼 70 m/min（AeroCore `ToolLife.N_CARBON`…），切削時間除以 `Wear.CARBON_SPEED` 0.4。
  - 甲醇：製程單元的重組反應器有兩個轉化，進料有石腦油就重組，有 LPG 與水就做甲醇（`Refining.METHANOL`，`METHANOL_FIRED` 0.45；水是原版流體，
    不在 `HdfvFluids.ALL`）。
  - 流體標籤：`HdfvFluidRegistry.COMMON` 把其他 Mod 的流體依 `c:` 標籤當成 hdfv 的那一種（管線、儲槽、加油機都經 `id()`）；hdfv 的流體也在
    `data/c/tags/fluid/`。
  - GameTest（`RecipeGameTests`，批次 `recipes`）：組裝台用鑄鍛件組出第一台車床（少一個鑄齒輪時組不出車床）、鍛壓機軋板只在成品與料頭都放得下時開工、
    車床車軸並退回切屑、電弧爐與電解槽吃電出料、漏斗進出合金爐、裝了 Create 時它的配方有載入、遊戲中的科技樹涵蓋每個配方的產物。
    新的 GameTest 類別要加 `@PrefixGameTestTemplate(false)`（模板名稱不帶類別前綴）；0.23 開發時漏了，GameTest 伺服器在啟動時就崩潰。
- 開發用程式（0.24，使用者「AutoShot 可能要移除」→ 選「從發布的 JAR 拿掉，開發環境保留」）：
  - 只給開發用的東西放 `neoforge/src/dev`（同一個 mod、同 package，只在開發 run 載入）或 core 的 `src/testFixtures`，不放 `src/main`：
    GameTest 類別與結構、`/hdfv dev` 的子指令、`/hdfvdev`、測試車、離線的聲音工具。新的 GameTest 類別在 `HdfvDev.GameTests` 登記。
  - `src/main` 可以留給它們用的小掛鉤（畫面的 `dev*` 方法、`StandDevCommands` 的欄位、`SoundDebug` 的開關），但不能引用 dev 的類別。
  - AutoShot、開發視窗（第 2 個螢幕、不搶焦點）、`/aerocoredev` 是 AeroCore 的 `aerocore_devtools` mod（`aerocore-devtools-1.21.1`，
    `devRuntimeOnly`），開發 run 一起載入；它不會發布，也不能被正式程式依賴。
  - `build` 會跑 `verifyReleaseJar`：發布的 JAR 出現開發用類別、GameTest 結構，或少了授權檔，就失敗。
- 損壞、修理、儲物（0.25；物理與公式在 AeroCore 0.42 的 docs/CONDITION.md）：
  - 狀況只放在 AeroCore 的 `VehicleState.used`（依零件 id 與項目名稱）。不要為了損壞在既有零件加內部狀態或顯示值：AeroCore 在內部狀態數量改變時
    整台重設，0.24 的存檔會失去引擎溫度、檔位這些。設計與藍圖 JSON 也不加參數（`Wearing` 在編譯時由既有的設計數字算），工件雜湊與零件快取才不變。
  - 項目都是 0 時，hdfv 自己的零件（引擎、油箱）要和 0.24 逐位元相同：用 `used > 0` 分支；引擎與熱段的磨損在 1 % 以下不作用（死區），
    新車與輕度使用的引擎照 0.24 走。包住 AeroCore 零件的零件（`ShiftRules.Ruled`、`SteerSchedule`、螺旋槳與旋翼的 `Compiled`）要轉送
    `conditions()`，內層的排最前面。
  - 校準（`ConditionsTest`、`WearTest`、GameTest）：範例轎車正面撞牆 15 km/h 每項 < 3 %；50 km/h 車身約 26 %、引擎附屬件約 42 %、前懸吊約 30 %；
    100 km/h 車身報廢。彈性速度用 8 km/h（美國 5 mph 保險桿；AeroCore 預設 4 km/h 時 15 km/h 壓不到 3 %）；世界中的撞擊衝量含反彈，只算 1 − 恢復係數。
    改韌性、分配或彈性速度時重跑這些並更新數字。
  - 時間尺度：磨損與腐蝕跟製造用同一個壓縮（`ticksPerWorkHour` 1000 = 72 倍）乘設定；輪胎是其 1/6（否則一組胎約 70 km）。
    `DamagePace` 在編譯零件時用，AeroCore 依設計 JSON 快取編譯好的零件，所以改了設定要重開伺服器才對已編譯的設計生效。
  - 火與岩漿：載具實體是防火的，`PartVehicle.Hurt` 收不到；`VehicleDamage` 每秒看零件所在的方塊。
  - 報廢：車身或底盤的 `structure` 到 1 時把引擎的附屬件設 1（一次）；車身修到 1 以下才重新作用。修理廠在引擎被選而報廢的車身沒被選時拒絕（`wreck_first`）。
  - 修理：0.8（`Conditions.REPLACE_FROM`）以上不能原地修；煞車、懸吊、支柱、離合器只換壞的那幾件（照零件自己的設計做），輪胎一律換新，
    引擎、變速箱這類要一個同設計的零件物品，車身、底盤、機翼原地重做。急修：每次最多 0.15、不低於 0.5、不碰 0.8 以上。
  - 貨物：車身設計時的 `cargoFill` 不再算進編譯後的車重（只給試算的「滿載」用）；真的貨在貨艙（`VehicleCargo` → `PartVehicle.setHold`），
    兩邊都算會重複。0.25 起範例轎車輕 45 kg、直升機輕 120 kg。貨艙的 id 是 `m<n>_cargo`：插入或重排模組會改 id，舊貨會當成放不下的掉出來。
  - 物品質量：容器裡的流體照密度算，hdfv 自己的容器（油桶）1 mB 是 1 公升；原版與其他 Mod 的容器一桶（1000 mB）算 `storage.bucketLitres`
    公升（預設 10，真的水桶；一桶水 30＋10 kg）。最初照 hdfv 的算法一桶是 1 m³、1028 kg，使用者 2026-11-13 看了截圖要改。
    zh_cn 的術語：OpenCC 把「預設」轉成「缺省」，術語表加了「缺省 → 默认」；方塊與其他物品照資料映射 `hdfv:item_mass`（後面的條目蓋前面的，
    寬的標籤放前面），hdfv 方塊的質量由 `tools/item_mass.py` 從配方加總寫入。
- 新手教學（0.24）：
  - 新物品、新機器或新玩法要想一下：手冊要不要補一段、要不要一個進度、JEI 說明頁有沒有。手冊頁面三種語言的結構要一樣
    （`HandbookTest`：篇目、區塊種類與參數、行內的按鍵與物品），引用的東西要存在；en_us、zh_tw 手寫，改完 `py -3 tools/zh_cn.py`。
  - 進度一律由 `tools/tutorial.py` 產生（它會刪掉沒寫到的檔）。原版能判斷的用 `inventory_changed`；其他用 `hdfv:milestone`
    （`Milestones` 列出事件與觸發位置），事件在伺服器端、有玩家時才觸發；沒有玩家的（噴油、煉油）給附近的玩家。
  - 手冊的圖放 `assets/hdfv/textures/gui/handbook/<名稱>.png`，320×180（`HandbookTest` 檢查），圖裡不放字（三種語言共用）；
    用 AutoShot 在 QA 世界拍，`tools/autoshot/release-0.24.txt` 是 0.24 的計畫。
  - GameTest 要玩家時用 `new ServerPlayer(server, level, profile, ClientInformation.createDefault())`：`makeMockServerPlayerInLevel`
    會讓玩家加入伺服器，其他 Mod 的登入處理送封包給假連線，GameTest 伺服器崩潰；NeoForge 的 `FakePlayer` 的進度不載入，觸發不到。
    沒有連線的玩家不送訊息（`welcome`、`sendProgress` 已檢查）。
- 三種語言（0.24）：en_us、zh_tw、zh_cn 的鍵與順序一樣（`LangParityTest`；zh_cn 不能有繁體字）。新增語言鍵照舊由產生器插在 en_us 與 zh_tw
  的錨點後；zh_cn 不手改，改 `tools/zh_cn_terms.json`（術語、OpenCC 的誤轉：「設計畫面」被斷成「设计划面」、「呼叫」變「调用」、
  「遮蔽」變「屏蔽」），再跑 `tools/zh_cn.py`。「畫面」在 hdfv 幾乎都是介面（→「界面」）；指算出的畫面時換個說法。
- 對外發布（0.24，兩個專案一起）：
  - 版本：0.x、Beta；hdfv 對 AeroCore 的相依範圍是 `[aerocore_version, 下一個小版本)`，AeroCore 改小版本時 hdfv 要跟著發。
  - 公開的檔案不放使用者資料夾、網路資料夾位址、電腦名稱、`.claude` 路徑（實際值寫在工作區 `Z:\MCMod\AGENTS.md`）。
  - 發布前：`build`、`:core:test`、GameTest、正式版環境的載入測試（`release_test.py` 的伺服器三種組合、客戶端有沒有 JEI、多人），
    `release/checklist.md` 照著走。推送、建倉庫、上傳到平台都是公開動作，每一步先經使用者同意；帳號與憑證由使用者自己處理。
- 網路磁碟上 Gradle 的建置快取偶爾還原失敗（「Failed to load cache entry ... Cannot create directory」或「error while writing ...class」）：
  加 `--no-build-cache` 重跑；仍失敗時 `.\dev.ps1 --stop` 停掉 Gradle 常駐程序再跑。2026-10-18 起建置輸出在本機（見〈工作目錄與本機資料〉），
  用 `-Phdfv_local_build=false` 時才會再遇到。

## 修改與驗證流程

1. 先讀相關程式與文件；不要把討論中的下一步當成已完成。
2. 物理或計算缺口到 AeroCore 補，並在 AeroCore 加測試；hdfv 只放內容與規則。
3. 零件設計變更跑 `:core:test`；遊戲行為跑 GameTest；畫面與互動用 QA 世界實測（自動截圖會在桌面跳出遊戲視窗，先告知使用者）。
   hdfv 的 QA 世界是 `%USERPROFILE%\.mcmod\runs\hdfv\client\saves\AeroCore HDFV QA`（AutoShot 只開 `AeroCore` 開頭的世界）；
   同一個資料夾裡的其他世界（例如使用者建的「新的世界」）是使用者的，不要開。截圖計畫寫在 `client\aerocore-autoshot.txt`，
   用沒有 BOM 的 UTF-8 寫（Windows PowerShell 5 的 `Set-Content -Encoding utf8` 會加 BOM；AeroCore 0.20 的 AutoShot 已會略過）。
   QA 世界的地形（0.4 截圖時查到）：出生的沙灘在 (13.8, 65, 245.6)，海在**西邊**（x < 0，水面約 y 62.9），東邊是草地；
   0.4 在 x 0～14、z 236～256、y 64 鋪了平滑石停機坪（上方清空到 y 74）。截圖的寫法：
   - `cmd` 以伺服器身分執行（位置是世界出生點、不是玩家），放載具用 `execute positioned <x y z> rotated <yaw> 0 run aerocore vehicle spawn <名稱>`
     （載具放在該點前方 4 m），相機用旁觀者模式加 `tp @p <x y z> facing <x y z>`。
   - `shot` 隱藏 GUI 在下一個畫格才生效，第一張要先拍一張丟掉的（否則會拍到聊天訊息）。
   - AutoShot 沒有關閉畫面的步驟：設計畫面要排在世界截圖之後；開著設計畫面時世界截圖會被它蓋住。
   - 滑出載入範圍的載具 `kill` 不到，會留在 QA 世界（0.4 截圖時有一架水上飛機慢車滑到 z≈−100）。
   - 0.22 近拍（`tools/autoshot/closeups-0.22.txt`）：`tp` 設的是腳（眼睛高 1.62 m），`facing` 也從腳算方向，所以注視點要往下 1.62 m；
     FOV 70 是垂直的，16:9 水平約 102°，近拍要站得很近。載具實體的位置不是車身中間（轎車約在前軸、船在船首附近、`semi` 把拖車留在生成點），
     用 `execute at <載具> run tp @p ~dx y ~dz facing ~dx y ~dz` 取景，每次都一樣；`aerocore vehicle control handbrake 1` 拉手煞車。
   - 新舊模型對照時，先把要改的檔案留一份舊版，拍「之前」時換回去，確認 `compileJava UP-TO-DATE`（和上次的舊版一模一樣）再相信那組截圖。
   - 0.24 的發布截圖（`tools/autoshot/release-0.24.txt`、`release-0.24-lang.txt`；正式版的 `production-0.24.txt`、`production-mp-0.24.txt`）：
     - 要玩家的指令（`hdfv dev open`、`hdfvdev ...`）用 `client` 送；`cmd` 以伺服器身分執行，沒有玩家。
     - 設計畫面的 3D 預覽要 `hdfvdev page overview`、`hdfvdev enlarge on` 才拍得到整個零件；畫面之間用 `hdfvdev close` 關掉。
     - 拍過 `gui` 之後的第一張 `shot` 會帶到聊天欄，要裁掉或先拍一張丟掉。
     - 進度畫面打開時停在 Create 的分頁：拍 hdfv 的進度與各語言時用 `-Phdfv_create=false`。
     - 正式版沒有 `/hdfvdev`（在 dev 原始碼集），關不掉畫面；`key` 只在沒有畫面時打得開，所以按鍵打開的排前面，
       之後的用指令疊上去（`client hdfv handbook <篇>`）。
4. 交付前執行 `build`，更新文件與版本，報告實際完成、實際測試與未驗證項目。

| 版本 | 日期／電腦 | JUnit（core） | GameTest | 客戶端 |
| --- | --- | --- | --- | --- |
| 0.25.0 | 2026-11-13，原開發機；AeroCore 0.42.0 | 445 全過（新增 54：`TransferBenchTest` 7、`GearboxBenchTest` 1、`RoadAssetsTest` 3、`RampDriveTest` 2、`StorageAssetsTest` 4、`ConditionsTest` 5（含範例轎車撞牆的校準）、`WearTest` 8（煞車片、輪胎、引擎、熱段的壽命）、`ElementsTest` 3、`RepairTest` 10、`RepairRecipesTest` 1、`StorageBoxTest` 5、`CargoRulesTest` 4、`RecipeRulesTest` 1；`FuelTest.aBodysFuelIsInItsTanks` 的直升機機體 2074.35 → 1954.35 kg（設計載重不再算進車重）） | 178 全過（新增 36：`RoadGameTests` 3（轎車 D 檔爬上 1:4 柏油坡停在上一層）、`StorageGameTests` 7、`AssemblyGameTests` 2（組裝室從儲物方塊取料、拆車的貨退回）、`DamageGameTests` 7（15 m/s 撞牆車身 26.7 %、存讀不變、步行速度沒事、設定關掉沒事、TNT、乘員受傷、岩漿、泡水、報廢、指令）、`RepairShopGameTests` 8、`CargoGameTests` 6（後車廂 200 kg 車尾低 4.55 cm、選單、存讀、裝卸台、掉落、車頂箱）、`RefitShopGameTests` 1（貨艙變小時多的退回）、`TutorialGameTests` 2）；記錄中 `DamageGameTests.thoseAboardAreHurtByAHardCrashOnly` 有一次被接住的 NPE（沒有連線的玩家受傷時原版送封包），測試照過 | 2026-11-13 使用者同意後 AutoShot（第 2 個螢幕；`tools/autoshot/verify-0.25-*.txt`，世界 `AeroCore 025 QA` 是 0.24 正式版測試的 `AeroCore Production QA` 的複本）6 輪：0.24 存檔載入沒有錯誤，17 台舊載具每項 0 %；試算圖、儲物箱零件的 3D、坡道、四種儲物方塊與畫面（頁尾質量、物品提示的質量）、撞牆後的狀況與煙、狀態畫面三個分頁、儀表警示燈、修理廠畫面、車頂箱轎車與載貨櫃的半拖車、騎乘按 E 打開貨艙、裝卸台、手冊四篇與四個新進度（英、繁）、JEI 頁都看過。截圖修掉的版面問題：貨物與修理分頁的行換行、修理廠按鈕寬度與「還有 n 項」、整數數量不帶小數、設計畫面與藍圖提示顯示原始類型 id、半寬圖的軸標題重疊、分動箱兩張模式圖改上下排、起落架與輪胎圖的英文標題縮短；修完 445／178 全過。開發用指令（dev 原始碼集）：`hdfvdev status|mouse|hover|click|scroll|drag|lang|jei|advancements`、測試車 `roofbox`、`containersemi`。FPS：排列場景 108（30.3 萬面、送出 4.54 ms，伺服器 tick 86 ms、約 21 台載具），貨物場景 119～120（伺服器 tick 13.6～13.9 ms）。未修：繁中 JEI 說明在數字前後的空格提早換行（JEI 以空格斷行）、1:8 與 1:4 坡道的物品圖示幾乎一樣、撞車的煙淡、滴油在截圖裡看不出、漏油徽章閃爍沒拍到、英文 JEI 頁（JEI 啟動時固定語言）。未驗證：多人同步；修理廠完成時給附近玩家的進度 |
| 0.24.0 | 2026-10-21，原開發機；AeroCore 0.41.0 | 391 全過（新增 6：`HandbookTest`（三種語言的篇目與結構一樣；引用的物品、進度、配方、圖都存在；圖 320×180）、`LangParityTest`（三份語言檔的鍵與順序一樣、zh_cn 沒有繁體專用字）；core 的 `test` 把 mod 的資源與 `tools/**/*.json` 列為輸入，只改資源也會重跑） | 142 全過（新增 `TutorialGameTests` 4：教學的進度都載入、`hdfv:milestone` 給等著它的進度、新玩家只拿到一次手冊、手冊引用的物品與配方存在），裝著 Create 6.0.11 | 開發客戶端 AutoShot（`tools/autoshot/release-0.24.txt`、`release-0.24-lang.txt`，QA 世界的複本 `AeroCore Release QA`）：0.23 的世界打開後舊玩家拿到手冊與歡迎訊息；手冊三種語言、進度、JEI 說明頁；第一輪手冊在 hdfv 自訂配方上 `/ by zero` 崩潰，已修。正式版 NeoForge 21.1.252 的乾淨環境（AeroCore `scripts/release-test/release_test.py`，`production-0.24.txt`、`production-mp-0.24.txt`）：伺服器只有兩個 mod、加 JEI、加 JEI 與 Create 都起得來、沒有可疑的行；客戶端有 JEI、沒有 JEI 都正常（歡迎、轎車、手冊、JEI）；多人連線拿到手冊、進度同步。0.22 的比較場景：機器 115～116、停車 113～114、塔台 119 FPS（0.22：114、111、119），整合伺服器 tick 54～57 ms（0.22 同場景 58～70 ms）。未驗證：裝 Create 的正式版客戶端；改裝、烤漆、卡車、高速、船的手冊圖沒有拍 |
| 0.23.0 | 2026-10-20，原開發機；AeroCore 0.40.0 | 385 全過（新增 10：`ComponentRouteTest` 4（每種標準件一個工件、質量等於設計、那一級的機器做得到；圖紙是自己種類的範例；路線等於 `routes-0.23-components.json`；名稱都在）、`RecipeRulesTest` 3（每個配方的產物都做得出來；階段錨點的順序；組裝台的工時是零件的規則、中間件的質量等於它的料）、`MetallurgyRecipesTest` 2（每批的 kWh 對 AeroCore 2 % 內；每 kg 的電在工廠的範圍）、`RefiningTest.theReformerMakesMethanolOfLpgAndWater`；`DesignEditorTest` 的參數數量下限對只有種類的標準件是 1） | 138 全過，裝著 Create 6.0.11（載入 3154 個配方，沒有解析錯誤；新增 `RecipeGameTests` 7：組裝台用鑄鍛件組出第一台車床、少一個鑄齒輪時組不出、鍛壓機軋板只在成品與料頭都放得下時開工、車床退回切屑、電弧爐與電解槽吃電出料、漏斗進出合金爐、Create 的配方有載入、遊戲中的科技樹涵蓋每個產物） | 未開客戶端：JEI 的組裝、電爐、電解頁與物品提示的截圖，以及用 0.22 的 QA 存檔在 0.23 開，都等使用者同意再做 |
| 0.21.0 | 2026-10-20，原開發機；AeroCore 0.38.0（不變） | 373 全過（`AirportPlannerTest` 改寫成三級：大機場少見、中小補上（合成平原上大機場的比例在上限內）、每一級自成一體（跑道寬、塔台、燈、蓄電池、風向袋）、0.20 兩份規劃的地面雜湊凍結、`VERSION 2` 大中小各一份凍結（0.21 發佈前因床掉頭改過一次）、塔內：每層一階、每階朝走上來的方向且往上三格淨空、最上一階在活門、中柱連續、四格格柵、電纜槽連成一條且每顆蓄電池在上面、從門口走得到床頭旁與管制員的位置；`RunwayTest` 的 `distanceTo`；`DetailKitTest` 的新零件組件；`AirportAssetsTest` 的新方塊） | 131 全過（新增：中型塔台的四顆蓄電池讓它成形並有電；小機場沒有塔台、放得出風向袋；有碰撞的玩家實體從塔底走上管制室再走下來（兩種高度）；照 0.21.0 擺的塔（樓梯、格柵、床）在塔台跑起來後修好；生成的塔台測試加上室內燈亮、樓梯、電纜槽、管制室的光源方塊） | 密度：無視窗伺服器＋RCON，三個新世界（種子 `hdfv-0.21-a`／`b`／`c`）各 25 個區域：大 2／1／2、中 3／2／3、小 6／11／7，75 個區域共大 5 座（約每 15 個區域一座，間距約 12 km）、中小 32 座；一個區域規劃最多 4.8 秒。遊戲中（使用者同意「現在可以」；本機專用伺服器＋Dev，`AeroCore Airport QA 21a`，`server.properties` 暫改、之後從備份還原成 `AeroCore MP QA`）三輪：中型（550 m × 18 m、6 段塔台、4 顆蓄電池）、大型（900 m、16 段、8 顆）、小型（400 m 與 250 m，沒有塔台與燈，`view tower_close` 回「This airport has no tower」）；空拍、停機坪、跑道頭、風向袋；塔底大廳與機房、螺旋梯、活門與欄杆、管制室內裝（白天、夜晚）、夜裡從外面看塔與管制室天花板。找到並修掉：小機場每個區塊的放置都在塔台那步丟 NullPointerException（280 次，風向袋與等待標誌沒放；`AirportFeature.tower`、`placeTower`、`goto`，新 GameTest）；夜裡窗燈全暗（客戶端的 `Level.getSkyDarken()` 只在進世界時算，改用 `ClientLevel.getSkyDarken(partial)`），之後調亮並加管制室天花板的微光；塔底的牆整面發黑（遮蔽，塔底也 `split`）；使用者自己在「新的世界」裡走螺旋梯「上不去 下不來」（轉角那一階的方向、第四階上方的格柵；新 GameTest 讓玩家實體真的走一趟，舊配置在第一個轉角卡住；已生成的塔載入後自己修好）；生成的管制員從不認領床、夜裡站在門外（床頭被牆、蓄電池格與第二階圍住；床頭改朝大廳後，在無視窗伺服器用 RCON 把 21a 那座的床照新配置擺好：當場有 `home`，`time set 13000` 後 `SleepingX/Z` 是床頭、床 `occupied=true`）；開發鏡頭 `aerial`、`apron` 依停機坪的位置縮放。單人 `AeroCore HDFV QA`：同一份計畫一進世界（還在烘焙 100 個網格）機器場景 99,391 面、送出 8.36 ms（0.20 同計畫 99,603 面、8.52 ms）；烘焙完後機器場景 97 FPS、約 10.4 萬面、送出 5.75 ms（這個場景 0.20 沒有烘焙完的數字可比）；0.20 放在空中的 10 段塔台（現在畫出 0.21 的內裝）119 FPS、6.7 萬面、1.10 ms，管制室裡 119 FPS、1.2 萬面、0.44 ms。截圖 `compare-0.21\`（before 是 0.20 的 a20d、a20q）。已知：夜裡的窗燈只是淡淡的加色燈罩（沒有泛光）、格柵在亮處偏白、樓梯從下面看是灰色方塊，留給 0.22 美化；0.20 與 21a 世界裡已生成的塔照舊（梯子、舊的床方向）；BodyTest 的計時基準與 GameTest 同時跑時會超時（單獨跑通過）。未確認：修好後真人走螺旋梯（GameTest 的玩家實體走得上去）、多人（Dev2）、塔台畫面在中型塔台、定翼機起降。多了三個測試世界 `AeroCore Airport QA 21a`、`21b`、`21c`（`%USERPROFILE%\.mcmod\runs\hdfv\server`），使用者的「新的世界」沒動 |
| 0.22.0 | 2026-10-20，原開發機；AeroCore 0.39.0 | 375 全過（新增 `FinishAssetsTest.everyFinishHasABeautifiedLookAndItsNormalMap`（每種材質都有 `beautify` 與同尺寸的法線貼圖、紅綠平均在中間）、`MemberTextureTest`（328 張成員方塊貼圖 128 px、透明處和 16 px 原圖一樣、不透明處的平均亮度差 6 % 內）；`BlockModelTest` 改 128 px） | 131 全過 | `AutoShot`（第 2 個螢幕，使用者同意「這版的驗證都不用再問」）。開工前用 0.21／AeroCore 0.38 拍 `compare-0.22\before\`（b22）與效能基準；美化著色關掉（o22）和 before 比：管制室逐像素相同，其他只差在水面、旋翼與轉台（會動的東西）。近拍計畫 `tools/autoshot/closeups-0.22.txt`（16 個視角，舊模型的 before 要把改過的檔案換回 0.21 版、確認 `compileJava UP-TO-DATE`）：關掉（`closeups-off`）與開著（`closeups-on`）。找到並修掉：法線貼圖的凹陷每種材質都 0.3，車漆像布（改成依起伏）；機翼的凹槽把主翼封起來後控制面整片被剔掉（以鉸鏈為原點、烘焙時疊在翼根；改在靜止位置烘焙、繞鉸鏈轉；0.21 其實也只有翼根一段看得到）；控制面依顏色配到切削鋼（藍色金屬感，改用主翼的材質）；世界中的車輪從 0.15 起不轉不轉向（`WheelRig` 的 `add` 留在件 0；近拍 `sedan-steer` 前輪轉了）；車輪改成件後排列場景送出 6.0 → 11 ms、FPS 92 → 58（AeroCore 0.39 的 `PartGpu` 每次重套著色器，改成每種材質一次並把 GPU 門檻降到 32）；燈罩光暈初版只有幾個淡點（調大調亮）。效能（同一份計畫、烘焙完、第二次量測；b22 → a22）：機器 112 → 114 FPS、送出 4.32 → 3.55 ms、14.9 萬面；停放 95 → 111 FPS、2.73 → 1.93 ms；塔台 119 → 119、0.99 → 0.87 ms；管制室 119 → 119、0.40 → 0.38 ms；排列（壓力測試）95 → 113 FPS、5.75 → 4.47 ms、40.4 → 34.7 萬面；伺服器 tick 不變。方塊圖集 2048×1024 → 4096×2048。機場（本機專用伺服器＋Dev，`AeroCore Airport QA 21a`，`server.properties` 暫改、之後從備份還原成 `AeroCore MP QA`）：中型塔台白天的塔底、開放式樓梯、活門格柵、管制室，夜晚開與關（`compare-0.22\airport*`）。已知：QA 世界的船停在陸上時每次傾角不同（截圖差很多，與模型無關）；排列場景的 GPU 繪製呼叫 378 → 997（FPS 反而較高）。未確認：Fabulous 畫質、多人、`/hdfvdev faces` 與新的細節材質（`panel_gap`、`rivet_row`…）沒做、機加工零件組的倒角沒做、空氣筒沒做 |
| 0.20.0 | 2026-10-19，原開發機；AeroCore 0.38.0 | 371 全過（新增 39：`AirfieldTest` 8（邊燈最後三分之一變黃、PAPI 在跑道左邊、中心線燈最後一公里的顏色、塔越高看得越遠與燈的耗電、ATIS 字母只在明顯變化時往下、口語的航空單位、順閃燈往跑道頭、跑道頭燈對進場綠對跑道紅）、`AtcRulesTest` 11（離場一架接一架、離場等近的到場、到場排序與第一架許可、許可逾時、車在跑道上讓落地的重飛、直升機原地起降、消失半分鐘的忘掉、重複呼叫與取消、沒許可只警告、玩家接手時只標衝突、無人塔台不給許可也不警告）、`RunwayTest` 6（編號、由跑道頭找跑道、平行跑道的字母只在同一座機場內、頂風、順風立刻換頭與無風不換、Annex 14 標線）、`AirportPlannerTest` 6（平原約每幾公里一座、沿盛行風、水陡坡生態域與聚落都擋、配置連貫、凍結雜湊、聚落距離與診斷）、`AirportAssetsTest` 4、`TowerPatternTest` 3（快速比對等於逐一嘗試）、`DetailKitTest` 的機場零件組） | 127 全過（新增 6：塔台成形與解散、沒電只有 ATIS、生成的塔台有充滿的蓄電池與值班的管制員（睡在生成的床上）、夜間迴路與泛光燈亮、只有接手的玩家能下指令而旁人不能呼叫、機場方塊有配方掉落與名字） | `AutoShot`（第 2 個螢幕，使用者同意「兩輪都跑」）。開工前用 0.19 拍 `compare-0.20\before\`；AeroCore 0.38 的回歸（a38）與燈格子、PAPI。① 本機專用伺服器新世界 `AeroCore Airport QA`（種子 `hdfv-0.20-airports`）＋Dev、Dev2（`server.properties` 暫改、之後從備份還原成 `AeroCore MP QA`）：生成的機場（跑道 18 與跑道頭條紋、中心線、滑行道與等待線、停機坪、機庫與門、塔台、值班的管制員、風向袋）、塔台畫面（雷達掃描、進程單、接手、指令）、Dev 手動管制、Dev2 開直升機呼叫：HUD「許可原地起飛」，夜間無人時「塔台無人：自行宣告」；PAPI 在下滑道兩紅兩白、偏高四白、偏低四紅；2 km 與 4 km 外看得到跑道燈。之後用 `AeroCore Airport QA b`、`c`（兩個種子的普查世界，原本只有出生點）在沒生成過的機場複驗（a20c～a20h）。② 單人 `AeroCore HDFV QA` 的 after（`compare-0.20\after\`）：轎車與設計畫面與 0.19 幾乎一樣（差超過 16 的像素 ≤ 0.06 %），機器 1.7 %（水面波浪與世界裡一塊地，機器模型沒差）；空中石台上放的 10 段塔台（白天、塔身近拍、管制室內、遮蔽與剔除的開關、夜間）。QA 中找到並修掉：規劃器太嚴（49 個區域一座都沒有）→ 重調門檻與探測（三個種子 75 個區域 20 座）；一連串 RCON 診斷觸發看門狗（改成一次一個）；整地到 `_WG` 高度留下浮空的樹、隔壁區塊晚長的樹冠浮在停機坪上（改 `WORLD_SURFACE`，拿掉樹幹時連同它的樹一起判斷）；兩座機場的 ATIS 出現 09L（字母只在同一座機場內，管制區取最近的塔台）；塔身與管制室天花板整面發黑（遮蔽的四角都在凹角，`TowerRenderer.split`）；管制室艙口看穿到外面（側面法線朝外）；塔底電纜溝露出泥土；空拍與 200 m 的鏡頭在視距外；區域在世界生成執行緒規劃時伺服器落後 6～7 秒（改到背景執行緒先規劃）；玩家在 8 km 內就替塔台加區塊票，讓遠處的機場一規劃完就生成、一直載入（改成只看飛機）。已知：視距 12 時 190 m 外看不到塔（方塊實體），沒載入的塔台燈是暗的；新區塊的停機坪偶爾還有一兩塊樹葉；直接傳送進沒規劃的區域仍會等一次；開發指令 `nearest`／`goto`／`view` 會在伺服器執行緒規劃。未確認：定翼機的進場與落地許可（只有 JUnit；遊戲中只飛了直升機）、遠處的燈在新規則下（需要一架飛機在 6 km 內）、實際玩起來的手感。多了三個測試世界 `AeroCore Airport QA`、`b`、`c`（`%USERPROFILE%\.mcmod\runs\hdfv\server`），使用者的「新的世界」沒動 |
| 0.19.0 | 2026-10-19，原開發機；AeroCore 0.37.0 | 332 全過（新增 `VehicleSoundTest` 3（範例引擎的點火間隔：直列四缸每 180°、V8 每 90°、渦輪柴油；轎車的聲源：一具引擎、四個輪胎、風、喇叭、車內的繼電器、封閉車艙減 20 dB 以上；3000 rpm 的 100 Hz 譜線、7.5 m 怠速 40～65 dB、全油門 60 km/h 68～85 dB、車艙擋住怠速 15 dB 以上、同種子同樣本）、`MachineSoundTest` 5（每個頭與站的製程聲、電漿比雷射大聲 15 dB 以上、同步的遲滯、循環無縫且 RMS 0.25、銑刀的模態、每個事件、試車台只有引擎）、`SoundLangTest`（設定、字幕、按鍵的翻譯）；`DriveControlsTest` 加方向燈與喇叭、儀表箭頭，`LampRulesTest` 加方向燈，`ShownExhaustTest` 刻意改成 3 個顯示值） | 121 全過（新增：單方塊機器（硫化機）開工送製程聲、穩定 40 tick 不再送、停工送靜音；試車台運轉送、停下送靜音；電動門開動時送門的聲音（四步的時間）） | `AutoShot`（`AeroCore HDFV QA`，第 2 個螢幕，使用者同意）三輪：HUD（`/hdfvdev sound hud`）看到每台載具的串流 ▶ 與循環 ~、聲壓級、增益、都卜勒、延遲與最大聲的三個聲源；用箱子代替的銑床、磨床、電漿台三個機器循環；門、鉚接、焊接、蜂鳴器事件；試車台 V8（40 kW）起動後 76 dB 串流（錄音的點火線 291 Hz ≈ 4360 rpm）；空中石室 RT60 8.8 s；新轎車在乾淨石地上：車外怠速 50 dB（6 m）、車內 50 dB（車艙 −24 dB、高頻 0.16）、喇叭、左方向燈與儀表綠色箭頭、開門時改成車外版本與車門聲、空檔踩到斷油 7778 rpm、一檔 30 km/h 3406 rpm 時引擎 105 dB 與後輪胎紋；錄音與頻譜圖（`%USERPROFILE%\.mcmod\exports\sound\`）。開發中修正：串流放空（AeroCore）、串流在兩台車之間每 tick 交換（遲滯）、`/ride` 騎到水上飛機（`/hdfv dev drive mount`）、循環聲在轉速快變時一直重做（半個八度內只調音高）、卡在一起的兩台車每 tick 撞擊。同日補驗（另一個工作階段，使用者同意；本機專用伺服器 `AeroCore MP QA`＋Dev、Dev2，`server.properties` 的 `level-name` 從 `AeroCore MP QA 2` 改回；之後單人 `AeroCore HDFV QA`）：多人——Dev2 用同步的狀態合成 Dev 的手排測試車（8 m 外 51 dB 的串流）、喇叭（Dev2 車外增益 1.000）、方向燈繼電器只在車內（Dev 車內增益 0.49～0.95、Dev2 0.000）、Dev 在車內是車內版本；伺服器送的機器製程聲（箱子代替銑床）Dev2 51 dB。遠處的載具（`FarSounds`；伺服器 view distance 8，追蹤範圍 128 m）——Dev 坐在車裡空檔踩油，Dev2 在 515 m 與 1245 m 外 HUD 與記錄有 `far PISTON`（46、44 dB），放油門後 `gone`。音爆——`/hdfv dev drive launch 600` 把 100 m 高石台上的測試車沿車頭發出（石台在發射前一 tick 拿掉），Dev（側方 40 m、前方 50 m）與 Dev2（側方 40 m、前方 200 m）各一次 `hdfv sound: boom`（Dev 4.0～4.7 kPa、41～45 m；Dev2 3.9～4.2 kPa、41～43 m，在預期的錐到達時間）。補驗找到並修掉：AeroCore 的音爆規則抓不到（「到達數從 0 跳到 ≥1」：錐內那段聲音只有 8 ms，又有靜止時的聲音還在到達；改成到達時間的折點，physics 666）、客戶端快速載具用被截到 78 m/s 的原版運動（改用同步的 `v`）與進入追蹤的第一步（假的 Mach 3.4 音爆）、伺服器慢時客戶端位置拉回造成的假音爆（`Entry.trackPoint`）、遠處載具一次過時的報告也開循環聲、QA 世界兩台掛 D 檔怠速的轎車每 2～6 秒互換串流（`rankDb` 峰值保持 8 秒，40 秒內剩 1 次）；之後 core 332、GameTest 121 全過。QA 世界清掉聲音測試留下的手排測試車：汽車廠 8.5 66 241.9 的 `3c843ff5…`（存檔裡只剩這一台，壓在組裝中的 `hdfv:vehicle` 轎車上刮擦）與落到 67.8 64.4 286.4 的 `d90328c1…`，用 UUID `kill`，離線讀實體檔確認其他載具都在。另見（沒修，AeroCore 快速載具）：600 m/s 從石台上發射時撞到石台被拋到 y 210；頭朝下壓在地上發射後停在原地、速度一直保留。未確認：實際聽起來如何（截圖聽不到，音色與音量平衡要使用者自己聽）、真的在加工的機器（GameTest 有同步；遊戲中用箱子代替）、試車台起動中沒有聲音（伺服器只在運轉時送）、Sound Physics Remastered 並存 |
| 0.18.0 | 2026-10-18，原開發機；AeroCore 0.36.0 | 321 全過（新增：`FrontLayoutTest` 4（三種自動預設：圓滑轎車、方正小車、平頭駕駛室；燈組與保險桿貼在車身上、車牌在保險桿、套件下緣沿保險桿外殼、全部自動時沒有 `front` 鍵）、`CabOverTest` 2（平頭駕駛室的擋風玻璃、水箱罩面板、踏階、保險桿角的燈、兩個座位與前軸；平台沒有車頭但有尾燈）、`DesignJsonFreezeTest`（0.16 範例 126 個 JSON 雜湊凍結）、`TruckChassisTest` 3（牽引車的第五輪與 `cab` 點；低底板的鵝頸、主銷、支撐腳、主銷撐住的標準工況，沒有主銷站不住；沒有鵝頸的車架 JSON 不變）、`HitchTest` 4（顎口、主銷與支撐腳的位置與綁定；拖車靠腳站著時承盤比第五輪低 8 cm；拒絕：側向、不在顎口、太高、太低、夾角、太快、主銷尺寸、超載；離線拖行 20 s 走 77 m、主銷在顎口內）；更新 `KitLayoutTest`、`LampLayoutTest`、`RefitTest`、`ChassisTest`、`DesignEditorTest`（原廠燈多了霧燈、底盤多 `cab` 點）） | 119 全過（新增 `TruckGameTests` 3：按 J 掛上靠腳站著的拖車、支撐腳收起、拖行與轉彎時主銷在顎口內、停下按 J 放下支撐腳後解開並拉手煞車、牽引車開走拖車站著不動；拖車退得太遠時不掛；照開發指令生成的半拖車（沒有控制、在 10 m 高的一層石頭上）停在原地並在準備好時掛上。修好 `RefitShopGameTests.lampsTakeLampUnitsAndGiveTheOldOnesBack`（原廠燈 3 對）與 `PaintBoothGameTests` 同一批共用假玩家被設成創造模式造成的偶發失敗） | `AutoShot`（`AeroCore HDFV QA`，第 2 個螢幕，使用者同意）三輪。c24：世界中範例轎車的新車頭（白天、夜晚近光與霧燈）；設計畫面近拍轎車（包角燈組、橫條水箱罩與廠徽、車身色保險桿與圓霧燈）、方正小掀背（方燈、全寬水箱罩、黑色保險桿與方角燈、窄槽）、轎車加套件（分流板沿保險桿、引擎蓋進氣口）、平頭駕駛室與拖車平台；在 x 100～150、z 300～324、y 119 鋪石台放半拖車與低底板。c24 找到並修掉：開發指令把卡車放得太低（輪子埋進地面 19 cm）、掛車等待太短；c25 找到並修掉：開發指令生成的車沒有控制時會自己往前開（從石台邊掉下去，掛著的拖車一起；改成生成時停好：手煞車、P 檔）、平頭駕駛室的儀表板穿出車頭（座位改在正面後 1.1 m、儀表板不超出車身）、轎車的下進氣口太薄（預設 10 cm）。c26：掛著的牽引車與鵝頸拖車、第五輪近拍、轉彎約 40° 的折角（上方）、靠支撐腳站著的低底板、夜晚拖車尾燈、平頭駕駛室的車頭（擋風玻璃、水箱罩面板、踏階保險桿與角燈）。c27、c28（使用者要「再拍一次確認駕駛室高度」）：駕駛室原本浮在大樑上 0.75 m（`CAB_RAISE_M` 0.4 再加機體自己的 0.35 m 下緣），改成依機體最低點放後 c27 剩 0.4 m 仍像踩高蹺，改成下緣高出大樑 5 cm，c28 確認駕駛室坐在大樑上、輪拱罩住前輪、保險桿在輪胎上方。未確認：真人按 J 倒車掛上、駕駛室第一人稱、多人連線、拖車在坡道與顛簸路面上的行為、轎車的車牌（範例車沒有車牌文字時不畫） |
| 0.17.0 | 2026-10-18，原開發機；AeroCore 0.35.1（0.35.0 的機加工計算） | 312 全過（含另一個工作階段同時加的車頭與車燈測試；`RouteMigrationTest` 改成只比快照裡有的設計，之後新增的範例沒有 0.16 路線可比）。0.17 新增：`HeadTest` 7（每族的頭與種類、特徵決定哪個頭接、頭要的附件、數控驅動不用人且只細一級、依大小的容量、工時對照真實機器、裝夾）、`WearTest` 3（量級、高速鋼比金屬陶瓷耗得快、重磨上限）、`RouteMigrationTest` 2（0.16 路線快照 `routes-0.16.json`：去掉插入的步驟後逐步相同、能做每一步的機器只增不減；舊工件對到新步驟）、`MachiningAssetsTest` 3（方塊、耗材、製程與特徵名稱都齊全，會磨耗的配方只做 1 個）、`RoomRivetingTest` 1、`DetailKitTest` 的機加工零件組（51 件、每件 800 面內）、`MachinePatternTest` 的頭與數控；刻意更新釘住舊路線的 `ManufactureTest`、`CncTest`（曲軸最後的精研不在 CNC）、`ToolConfigTest` | 115 全過（0.17 新增 `MachiningGameTests` 8：九族每個頭成形、換頭換能力、拆一塊解散；磨床磨削並磨耗砂輪；舊車床缺刀具停在「缺耗材」、漏斗把刀具送進耗材格後繼續；數控驅動不用人；雷射頭在皮帶輪座「要用電」、電加工不收皮帶輪座；0.16 的舊工件放進機器後轉到新路線；工具磨重磨第三次並報廢超過次數的；空分機灌一瓶氧氣的工作時間與電量在 2 % 內；配方、掉落、名稱；`AssemblyGameTests.aRivetingMachineInTheRoomIsFound`；CNC 做完曲軸到精研前、精整機的數控精研完成；放機器的舊測試補上耗材）。之後加的 `cuttingTablesPlacedByNameForm`（照 `/hdfv dev place` 放的三種切割台成形）在改裝廠的車燈測試修好後，由車頭工作階段 21:24 的整套 GameTest（118 個全過）確認 | `AutoShot`（`AeroCore HDFV QA`，第 2 個螢幕，使用者同意）三輪：在 y 100 的石台上放九族 34 台（每個頭一台，部分裝數控驅動）、空氣分離機；白天每排與特寫（外圓磨的砂輪、頂尖間的工件、冷卻管與火花，滾齒的齒輪與滾刀和切屑，帶鋸、牛頭鉋），夜晚放電間隙的光、雷射光點、電漿弧光、水刀水霧；磨床畫面（頭、2 公尺、電動機、鑽石修整器、耗材格）。工作中的樣子用 `/hdfvdev working`。找到並修掉：耗材標籤和原版「物品欄」上下連成「耗材物品欄」（有耗材格時不畫物品欄標籤）。第一輪雷射切割台在客戶端顯示未成形，存檔裡是成形的，第二輪重放一台 3 秒內就成形，沒有重現。AutoShot 的 `tp … facing` 從腳算方向（特寫要把眼高 1.62 m 扣掉）。未確認：JEI 的機加工頁、真的加工中（電與操作員）的外觀、空分機接電網、石榴石礦的生成、多人連線 |
| 0.16.1 | 2026-10-18，原開發機；AeroCore 0.35.1 | 289（新增 `BodyKitTest.aMildKitIsAProductionTrackCars`：範例轎車的溫和套件 200 km/h 整套 1781 N（1～2 kN）、車底與擋風板、分流板 751 N（0.3～1 kN）、側裙只在輪拱之間時封閉 0.22（側裙的 0.36 乘長度比例 0.62）；`theKitPullsTheCarDownAtSpeed` 照過） | 105（`aKitGivesTheBodyItsAero` 180 km/h 仍超過 500 N） | 未啟動客戶端（只改下壓力的數字；改裝廠工作頁的 200 km/h 下壓力會跟著變小，畫面未看） |
| 0.16.0 | 2026-10-18，原開發機；AeroCore 0.34.0 | 288（新增：`ScaleTest` 5（每個數字參數都有縮放規則；1 倍是同一份 JSON；1.2 倍轎車可編譯、車長 ×1.2、排氣量 ×1.728、轉速 ×1/1.2；0.5 倍放不下人；縮放的數字取 12 位有效數字、後座 3 人時可縮到 0.9 倍）、`RefitTest` 8（改裝廠能改與不能改的；只改調校只花工時；換彈簧是調校；加寬輪胎只要那幾個新工件；加長引擎蓋是車體與烤漆；整台縮放全部重做；改裝廠說出缺什麼與裝不下的引擎；燈依樣式與大小算燈具，移動不算、換樣式退回舊的、全拆退回 4 個）、`BodyKitTest` 4、`LampLayoutTest` 7（原廠燈的位置和 0.12 相同；飛機與船的燈；點哪裡放哪裡、車頂燈排朝前照、防撞燈朝上；自己的燈取代原廠的並跟著車身；航行燈放錯邊是警告、超過 32 盞不合格；全拆是沒有燈、null 是原廠的、3 m 燈排；鈍度 0.45 的車頭燈在端面、更尖的放在側面朝前）、`RimLookTest` 2、`KitLayoutTest` 2（三種機頭下擋風板、分流板、唇、側裙、輪眉、進氣口都貼在車身上）） | 105（新增 `RefitShopGameTests` 7：改裝廠說出缺什麼（門、燈、電、焊接台、改大後門過不去）；只改調校不拿東西、只花工時與電、原地換成新設計、油量不變；整台放大要全部新工件、完成後質量與軸距變了、停在地板上；加套件後同車速的軸載重比沒裝時大；車燈扣燈具、換回原廠退回；車離開時停工；存檔讀回與燈具配方） | `AutoShot`（`AeroCore HDFV QA`，第 2 個螢幕）三輪。c16：改裝廠外觀、七個頁面；照預定把機頭改到 0.3 時引擎從引擎蓋穿出 2 公分，改裝廠照規則不開工（正確），所以世界中沒有改到。c16 找到並修掉：機頭改尖時原廠頭燈消失（尖的車端改放側面）、刪掉最後一盞燈會變回原廠燈（null＝原廠、空清單＝沒有燈）、1.3 m 的 LED 燈條放大後超過 1.5 m 上限讓整台縮放被拒（上限 3 m）、燈具只比數量（改成樣式＋大小）、被拒的修改只出現在動作列（改成畫面紅字）、車燈頁提示與清單重疊、房間問題的「top」沒翻譯、選到機體時黃色選取蓋住整台、縮小連 0.9 倍都被座位卡住（座位數改成大小頁可調）。c17（機頭 0.45）：運動保險桿、分流板、側裙、輪眉、擴散器、唇、1.5 m 尾翼、車頂橫桿、進氣口、雙尾管、碳纖；雙輻黑輪圈與胎壁字；方形頭燈、LED 條尾燈、車頂燈排；工作頁 4 個燈具進 4 個退、200 km/h 下壓力 0 → 4029 N；完成後原地換車、夜晚燈亮與煞車；1.3 倍（6.84 m）與後座 3 人後 0.7 倍（4.79 m）；0.6 倍被拒的紅字。c17 找到並修掉：車頂燈排照向天空（照路的燈立在車頂、引擎蓋上朝前照）、縮放後的數字帶浮點尾數、創造模式的改裝也退回工件而掉滿地（不拿也不退）、工作頁的工時與數字被長清單擠出畫面（先數字後清單、「還有 N 項」）。c18 確認這四項。第一次 c17 遊戲在載入 Mod 時卡住被 Windows 關掉（另一個工作階段在處理載入慢），c18 的載入正常。使用者看了 c17 的預覽說「車頭的模型很怪」：量出來擋風板、分流板、唇是放在地板高度的 1.3 m 寬方塊，機頭 0.45、引擎蓋 1.2 m 的車頭底部往上翹 22 cm、端面只有 0.81 m 寬，分流板比車頭寬 50 cm、低 25 cm；前輪眉在車身最寬處，車頭收窄處離車身 16 cm；進氣口是平盒子，後端埋進引擎蓋 3 cm；保險桿深色帶的上緣在翹起的車頭上是鋸齒；鈍度 0.45 的端面（0.28 m²）差一點不到 15 % 而沒有頭燈。改成沿車身表面產生的件（`KitLayout.Panel`）、保險桿帶沿外殼的射線列、端面門檻降到 6 %（`FACE_SHARE`）。c19～c22 用設計畫面近拍原廠、鈍度 0.45、加套件（0.45 與 0.55）的車頭確認（c19 停在參數頁、c20 開著剖視，這兩輪不算）；GameTest 105 照過。未確認：生存模式在遊戲中從箱子取料與退回（GameTest 有測）、多人連線、改裝過的車再進組裝室拆解 |
| 0.15.0 | 2026-10-18，原開發機；AeroCore 0.33.0 | 260（新增：`ChassisTest` 5（四種範例底盤的質量、扭轉剛度、強度與 JSON；編成會扭的車架與掛載點；X 撐加剛性；站點的新增、移動、複製、刪除；承載式轎車每度 10～40 kN·m、裝在車架上的車殼不扭）、`AxleGeometryTest` 5（0.14 的車軸不變；硬點從簡化行為開始、改點改變側傾中心、太短的臂卡死；氣壓彈簧撐住設計載重；後輪轉向的速度排程；空白硬點顯示它代表的點）、`ChassisAssemblyTest` 8（6×6 擺位、接線與房間；超載、陷地、輪拱、車殼要車架；第三軸不疊在前軸；軟與硬車架；汽車廠擋下錯誤；硬點轎車可編譯；後輪轉向接線；車高鍵、綁定偏移、儀表 HI／LO）；`BayFitTest` 加入 6×6 與後輪轉向轎車） | 98（新增 `ChassisGameTests` 4：6×6 停穩六輪都有載重（前 4.5、中 2.9、後 2.6 kN，合計等於 20.0 kN 車重）並開走；前左輪壓在半格台階上，軟車架扭 11.0° 而硬車架 0.61°，右前輪載重 0.9 對 0.4 kN；氣壓懸吊升高與降低；後輪轉向迴轉半徑 4.3 m 對 5.3 m。開發中修正：底盤站點的工件名稱（物品名稱與機器畫面只換了 `mN_`）） | `AutoShot`（`AeroCore HDFV QA`，第 2 個螢幕）：四種範例底盤的設計畫面（3D、X 撐、後段抬高、脊骨的外伸臂、管架）、站點頁、底盤試算頁（質量、扭轉與彎曲剛度、最吃力的構件、沿車架的使用率圖）；硬點模式的麥花臣前軸（真座標的支柱、下臂、拉桿）與試算頁（外傾、束角、側視路徑角、主銷幾何）、簡化模式的試算頁；組裝室：6×6 可以使用、單獨的車殼「要掛到底盤的車殼座」、車軸掛太高「車底陷進地面 18 公分」、組裝畫面的 6×6；世界中停穩的 6×6（大樑在車殼下、三軸在輪拱裡）、氣壓轎車儀表的 HI／LO。硬點轎車在世界中的轉向：同步的轉角左 0.764、右 0.587 rad，`/hdfvdev shown` 印出的車輪件姿態左 43.8°、右 33.6°、繞傾斜 31° 的主銷，畫面上被葉子板擋住看不清楚。開發中修正：梯形大樑的站點頁列出管架參數、簡化模式試算頁的束角軸溢出、空白硬點看不到值、測試車從腳下生成（6×6 的輪子在地面下）。發現：QA 世界的新車要 25～50 秒才 `ready`，之前幾輪截圖都是未模擬的姿勢。未確認：世界中硬點懸吊的臂隨行程擺動的畫面、軟車架在世界中扭的畫面（只有 GameTest 的數字）、8×8、底盤的 CNC 與焊接實作、多人連線 |
| 0.14.0 | 2026-10-17，原開發機；AeroCore 0.32.0 | 242（新增：`DriveControlsTest` 8（車上有什麼、轉向平順與隨車速縮小、手煞車輕按與按住、P→R→N→D 與撥片進 M、手排排檔桿與高速不進倒檔、四驅模式與差速器鎖的循環、儀表的檔位文字、警示燈與刻度）、`TransmissionDesignTest` 2（手排自動離合起步、只推排檔桿換檔、停車不熄火；自排 M 每按一次換一檔）；0.14 追加：`BayFitTest` 7（範例配置與測試車都放得下、引擎留 1 cm、舊轎車從頂部穿出與座位重疊、柴油發電機引擎被擋、變速箱接在飛輪面與分動箱接在尾端、不在引擎室的不檢查、座位零件不再放上地板）、`SeatLayoutTest` 4（前 2 後 3、外側優先、駕駛與掌舵是座位 0、飛機與船的座位不變）、`EngineEnvelopeTest` 4、`BodyLayoutTest` 1（後車廂不是內襯、玻璃在玻璃段內、儀表板）；`AssemblyTest` 的擺位與房間檢查） | 94（新增：組裝室推出的車保留房間裡加的油、出廠清空的油箱不再加滿；烤漆罐與貼紙膜的配方；轎車 `steer` +1 往左轉；手煞車讓滾動的車停下、另一台照滾；測試用四驅車 4H、靜止進 4L、`diff_lock` 1／2 鎖後軸／前後軸、自動離合起步不熄火；0.14 追加：組裝室拒絕裝不下的引擎與重疊的座位零件、房間頁說出零件、哪一面、幾公分；轎車煞車測試改成等車停穩（引擎往後挪後約 4.3 秒才停穩，原本固定在 3 秒檢查並在 `succeedWhen` 丟 `IllegalStateException`，讓整個 GameTest 伺服器崩潰）） | 單人 `AutoShot`（`AeroCore HDFV QA`，第 2 個螢幕）：腳本操作真正的系統選檔視窗（user32 找到 hdfv 的視窗、填路徑、按開啟），上傳 384×384 測試圖成功、烤漆房圖庫多一張；轎車貼滿 64 張貼紙 87～97 FPS；儀表（指針式雙圓錶、P R N D M、M1、轉彎、手煞車燈）、測試用手排車只推排檔桿起步與換 2 檔（自動離合）、四驅車 4L 與前後鎖的燈、夜晚。多人（使用者同意 EULA；本機專用伺服器＋兩個客戶端 Dev、Dev2）：伺服器沒有載入客戶端類別的錯誤；Dev 上傳、Dev2 第一次看到時下載並畫出貼紙；透過網路用烤漆房點選貼上、改紅、開工、完成，Dev2 看到重漆後的車；管理員 `decal list`、`remove`（兩邊的貼紙消失）；Dev2 用管理員指令被拒；Dev 用鍵盤開車與儀表（D2、手煞車燈）；伺服器關掉上傳時烤漆房說「這個伺服器不接受圖片」、管理員照樣能列出。使用者自己在多人連線上傳 768×1024 的圖（584 KB）時發現：超過 64 KB 的圖在客戶端讀不進來（`NativeImage.read(byte[])` 用 64 KB 的 LWJGL 堆疊），修正後使用者確認看得到。開發中另外修正：`localhost` 解析成 IPv6 連不上只聽 127.0.0.1 的伺服器（改用 127.0.0.1）、新伺服器沒有範例藍圖、Dev2 的暫停選單蓋住截圖（AeroCore 0.32 的 AutoShot 自動關掉）、手煞車測試的判斷方式。使用者在多人連線用「上傳」按鈕（系統選檔視窗）上傳自己的圖兩次；未確認：拖檔、正式版的按鍵設定畫面（衝突標示）、全時與適時四驅在世界中的模式切換（只在離線測試）、手把或方向盤控制器。0.14 追加（使用者「汽車和引擎之間有破圖」「內裝怪怪的」「引擎的大小要符合大小才能裝車」）的 `AutoShot`（`AeroCore HDFV QA`，第 2 個螢幕）：新範例轎車的引擎蓋從前方 3/4、側面貼著引擎蓋、從擋風玻璃沿引擎蓋、正上方與車頭都沒有東西穿出；駕駛座第一人稱：儀表板前沒有椅子，雙圓錶、方向盤、排檔與扶手、兩側新後視鏡；後座、後車廂蓋是烤漆；組裝室放柴油發電機引擎時房間頁「engine 裝不進機體：從頂部穿出 62 公分」、換回範例轎車「房間可以使用」；5 具活塞引擎的設計畫面（緊湊的進氣、空濾箱）；`/hdfvdev envelope all`。開發中修正：腳踏空間露出變速箱鐘形殼（加中控台下段）、外形漏掉吊耳等小零件（補進外形）、QA 世界的汽車廠屋頂缺一塊（補上）。未確認：多人連線下的房間頁訊息、其他車身設計的中控台下段 |
| 0.13.0 | 2026-10-17，原開發機；AeroCore 0.31.0 | 216（新增：`StickerTest`、`BodySurfaceTest`、`WingSurfaceTest` 與擴充的 `BodyTest`（T1）、`DecalsTest` 7、`DecalImagesTest` 3、`PaintBoothTest` 3） | 88（新增：貼紙圖片存進去並拿得回來、分塊上傳、過大／過寬／不是 PNG／雜湊不符／超過限額／關掉上傳時被拒、移除與清除；烤漆房說出缺什麼、開工扣烤漆罐與貼紙膜、在原地重漆並更新藍圖物品、質量不變、載具離開時停工、存檔讀回） | `AutoShot` 在 `AeroCore HDFV QA`（視窗在第 2 個螢幕）：上傳程式產生的測試圖，轎車兩側對稱的大貼紙（左舷正、右舷鏡像）、引擎蓋貼紙、開門時門上的貼紙跟著門、水上飛機機身與兩邊機翼的貼紙、遠處不畫、夜晚；烤漆房（外觀、抽風機、工作燈、控制台）、三頁畫面（烤漆改成紅色、在 3D 預覽上點一下貼上、工作量與開工、完成）、完成後轎車在原地變紅；修正後的副翼與襟翼在機翼後緣、副翼上的貼紙；旋翼走烘焙件。開發中修正：控制面畫在機翼根部（0.12 起：件標記沒蓋上）、螺旋槳與旋翼多畫一份、烤漆房預覽的黃色選取框蓋住貼紙、選項顯示原始鍵、開畫面時離控制台太遠被立刻關掉、測試載具的車頭陷進牆、新機身的表佔住共用執行緒。未在遊戲中確認：真正的系統檔案視窗與拖檔（用開發指令上傳）、多人連線的上傳與下載、管理員指令、伺服器關掉上傳、大量貼紙的效能、烤漆罐與貼紙膜的配方、快艇（範例的船用 AeroCore 船體零件，貼不了；hdfv 的船體設計可以） |
| 0.12.0 | 2026-10-17，原開發機；AeroCore 0.30.0 | 193 | 81（新增：燈、門、起落架、雨刷、煞車與排檔頻道存讀；停著的轎車不送顯示值；煞車滑移的顯示值；可收放起落架在地面不收、空中收起；抽油機同步衝次且穩定抽油時不送更新） | `AutoShot` 在 `AeroCore HDFV QA`（1280×720，視窗在第 2 個螢幕）：設計畫面與世界中的車窗開孔、內裝、第一人稱座位、開門、控制面、旋翼頭、槳葉角；範例載具白天與夜晚（燈沒開）；煞車燈；外觀參數（轎車金屬紅、雙條紋、藍玻璃、車牌「HDFV 12」；水上飛機「N123HD」；快艇「SEABIRD」與側條紋）；試車道（泥土、沙、雪、瀝青）上的揚塵、粉雪、車轍、煞車痕與煞車燈；24 個機器方塊（白天與夜晚、合金爐點火）、64 px 成員方塊、成形的 12 軸 CNC、五種機床、抽油機（工作中）、爐口與焊接電弧的光、組裝室開工到 40% 的轎車骨架與鷹架；用 V0 的同一份計畫重拍（`screenshots` 的 `aerocore-v12*`）。機器加四架載具的畫面每幀 11.7 萬面、140 次 GPU 繪製、送出 8.3 ms，28 FPS（同時海面 14 ms、內建伺服器每 tick 平均 58 ms）。優化（使用者「你先優化」）：剖析場景（四架載具＋機器）的送出時間每幀 3.6～8.4 → 2.5 ms，FPS 85～113 → 120（垂直同步上限），伺服器 tick 平均 36～50 → 22～26 ms，40 秒 JFR 的取樣：伺服器執行緒 1832 → 925、渲染執行緒 704 → 248；Fabulous 畫質下合金爐燈暈是黑方塊（AeroCore 修正）。開發中修正：烘焙快取的鍵（字串與 record 被回收後每秒重烤，背景佇列塞滿、機身晚出現）、胎痕畫錯位置（AeroCore）、抽油機在控制方塊裡無光而全黑、抽油機「滿」與「抽油」的閃爍、新 GameTest 搶走同批次的表、穿模檢查只看最近一層、直升機外側座位與飛行艇纜樁。未在遊戲中確認：車頭燈的光束與地面光斑、航行燈與防撞燈的閃爍、船燈（`LampRulesTest` 只測規則）、可收放起落架實際收起的動畫、旋翼下洗、雨中路面水霧、泥巴、多人連線的燈與門、Iris 光影包、低階顯示卡 |
| 0.11.0 | 2026-10-15，原開發機；AeroCore 0.29.0 | 176 | 77（新增：試車台同步引擎與轉速、穩態不送更新） | `AutoShot` 在 `AeroCore HDFV QA`（1280×720）：設計畫面每具引擎的總覽、背面與剖視（衝壓、火箭、J79、Argus、X-51），J79 與 J58 的噴嘴瓣；世界中 J79 試車架（`jetstand`）乾推與開後燃（1950 K、79.9 kN；白天與夜晚，菱形震波、噴嘴發紅）、助推器試驗台（白煙與鋁粒火花）、脈衝噴射艇（夜晚跟拍）、水上飛機的螺旋槳模糊盤、四座引擎試車台（風扇與皮帶輪轉動、V8 在 150 kW 負載下排氣管發紅）、遠處的替身；GPU 快取開與關的同一畫面（畫面一致，送出 13.1 → 1.5 ms）；用 V0 的同一份計畫重拍與 0.10 並排（`screenshots\compare-0.11\`）。開發中修正：扣件面數（J79 13.8 萬 → 5.6 萬）、寫反的車削剖面（暗斑與消失的管子）、被噴嘴外殼蓋住的噴嘴瓣、試車台被畫黑（方塊內沒有光）與每次同步重烤、替身讓遮蔽把整具引擎判成被包住。未在遊戲中確認：柴油重載黑煙的量（試車台負載 70 kW 只略超過門檻）、KNSB 的粉紫火焰、氫燃料的尾焰、渦扇的後燃器外觀（模型沒有畫後燃筒）、多人連線的試車台同步、Iris 光影包下的備援、低階顯示卡。同日依使用者回報「3d model 有些穿模」修穿模（`/hdfvdev clip` 掃全部 49 個範例設計；見 0.11 段），範例引擎船的引擎移到甲板上；重跑 176 JUnit、77 GameTest 通過，修過的引擎與引擎船再以 `AutoShot` 看過 |
| 0.10.0 | 2026-10-14，原開發機；AeroCore 0.28.0 | 169 | 76（新增：機器沒有這張圖的工序時列出下一步該去的機器（使用者 2026-10-15 回報「這台機器沒有這張圖的工序」：新藍圖放進機床時，毛坯要先在鑄造爐、鍛壓機或複材爐做）；脈衝噴射艇靜止不自己起動、吹氣半秒後自己運轉並推船前進；各容器收哪種氫、液氫在普通儲槽燒乾而在低溫儲槽留住、液化機 10 kg/h 與 11.9 kWh/kg、載具的氫只進同種油箱；重組器的氫沒指定出料罐時燒掉、指定時成為 700 bar 產品；助推器手動裝填（藥柱不足時不裝）、點火、燒完、APCP 拋棄並掉成零件物品、KNSB 重新裝填；簧片組換掉磨損的簧片；推進劑與簧片組的配方與名稱） | `AutoShot` 在 `AeroCore HDFV QA`（1280×720）：在 y 80 鋪一塊平台（x 30～52、z 226～252），放兩格低溫儲槽、兩格高壓氫氣瓶組、液化機與普通儲槽；助推器試驗台點火（兩具助推器噴出火焰）、燒完後 APCP 那具不見、零件物品掉在台上；海上的脈衝噴射艇（西邊）靜止、吹氣起動後尾端有閃動的火焰並拖出尾跡開走；設計畫面：衝壓（BOMARC 級）總覽與剖視（錐形中心體、唇口、火焰穩定環、收斂擴張噴嘴）與 0 m 的推力／比衝對馬赫數；超燃（X-51 級）總覽、剖視與試算（Mach 4.7 以上點著）；Argus 總覽、剖視與試算（靜止 2.8 kN、37 Hz、一個週期的燃燒室壓力）；Lockwood-Hiller；APCP 助推器剖視（BATES 藥柱）與推力曲線；J79 的後燃器頁（後燃筒與噴嘴瓣）與試算（乾推 53.0 kN、開後燃器 80.9 kN）；J58 總覽、剖視（進氣錐、旁通管）與試算（旁通在 Mach 2 以上打開）；Mirai 級氫氣瓶的參數頁。第一次截圖的助推器火焰太細、APCP 藥柱和殼體同色看不出剖面、試驗台的箱子太大；火焰改成先膨脹再收尖並加粗、藥柱改淡褐色、試驗台縮小後再拍（第二次世界剛載入、伺服器延遲，點火一秒後還沒燒起來；第三次等兩秒，兩具助推器的火焰都在，`vehicle info` 確認 APCP 已拋棄、KNSB 燒完）；助推器看起來有一半陷在試驗台箱子的頂面（箱子零件的繪製）。未在遊戲中確認：合成的聲音（截圖聽不到）、畫面震動、超燃與衝壓在世界中真的飛到點火速度（試算與 JUnit 有測）、J58 在 0 m 低超音速的負推力（固定錐的溢流阻力，真實的 J58 會移動錐體；試算台可改高度看 20 km）、液化機接電網與管線的實際工廠、英文畫面、JEI 的新頁、多人連線 |
| 0.9.0 | 2026-10-14，原開發機；AeroCore 0.27.0 | 141 | 66（新增：五台多方塊機床成形與拆塊停機、長曲軸要長車床與磨頭、車床在電動機座上用電線加工、驅動方塊配方與名稱；抽油機衝次加倍產量加倍、油嘴變小流量變少、鑽井停在目標深度並可調深繼續、蒸餾塔切點與出料罐指定、FCC 500 °C 的輕循環油、產品無出口時停機、加油機加到設定量停、各畫面打開與設定存讀與超出範圍被拒、畫面文字） | `AutoShot` 在 `AeroCore HDFV QA`（1280×720）：0.8 的石頭地上（z 236～246）一排五台面向北的機床：車床 4 m（電動機座；數位讀數尺、刀座磨頭、中心架、快換刀架、尾座）、銑床 3 m（皮帶輪座經天軸接電動機；分度頭、迴轉台、數位讀數尺）、龍門鉋床 4 段（側刀架、第二刀架）、搪床 3 段（迴轉台、數位讀數尺、平旋盤）、鑽床 2 段（鑽模板、動力進給、多軸頭）；截圖看到五台整台成形的外觀與附件。畫面：車床（大小、驅動、附件兩行）、CNC、蒸餾塔三頁（製程設定；收率與進料、產能、電力；出料罐）、FCC 設 500 °C（三個預設、收率、出料罐）、鑽井架、抽油機（泵排量 29.2 m³/d、油層供油上限 101 m³/d）、井口（全開 49.8 m³/d）、加油機。第一次截圖發現車床畫面的工具行壓到箭頭、蒸餾塔第一頁的製程行壓到產品格、長的產品名稱超出、加油機加了油還顯示「沒油了」、附近載具顯示成實體名稱；機床畫面加高兩行（CNC 與手動機床）、出料頁加寬、文字依寬度截斷、加油機有油時回到待機、載具用藍圖名稱，修正後再截一次（之後車床畫面的兩行再上移 2 px、避開物品欄標題，這一步未再截圖）。未在遊戲中看：機床加工中的動作（要有人操作）、英文畫面、JEI 的新頁、多人連線 |
| 0.8.0 | 2026-10-14，原開發機；AeroCore 0.26.0 | 130 | 56（含天軸與電網、蒸汽機與踏板、CNC 多方塊 3／12 軸、管線與儲槽、鑽井與自噴、抽油機、蒸餾塔質量守恆與加熱爐燃料、FCC、聚合廠、加油機 50 公升／分、錯油被拒、油桶倒油、噴射艇沒油熄火、組裝室出廠油箱是空的、捲門只有頂排有捲軸且門簾下緣逐排上升） | `AutoShot` 在 `AeroCore HDFV QA`（1280×720）：在 0.4 停機坪東邊鋪一塊平地，放 12 軸 CNC、常壓蒸餾塔（含減壓段）、製程單元、抽油機、鑽井架、加油機與引擎船；截圖看到 CNC 的整台外觀（防護罩、窗、床身、主軸）、蒸餾塔與製程單元、抽油機的遊樑與驢頭、鑽井架、加油機；0.7 的汽車廠從上方看到天車大樑與吊鉤、施工中的轎車（骨架環與機頭蒙皮）、捲門開到一半與全開（看得到工作架）；騎在引擎船上的燃油表（57／57 公升汽油）。第一次截圖時 CNC 是透明的：`CncRenderer` 的快取時間初值 `Long.MIN_VALUE` 相減溢位、永遠不比對圖樣，成形的成員又擋住鄰格的面，地面出現破洞；改成 `Long.MIN_VALUE / 2`，成員由控制器畫時成形後不遮擋（`MemberBlock` 的 `drawnWhenFormed`）。`client` 步驟開門沒有作用，改用 `execute as @p`。未在遊戲中看：CNC 加工中的軸運動、抽油機抽油時的動畫、天車吊裝零件的動作、焊接火花、JEI 的煉油頁、加油機實際加油（GameTest 有測）、柴油引擎座帶天軸的工廠、多人連線。門改成一整片門簾後（同日）再截一次汽車廠的 3×3 捲門：關著、第 1 步、半開（底排空、門簾下緣在中排）、第 3 步、全開（只剩頂上一根捲軸與下緣條）、斜下方與房內看捲軸、關回去；第一次的捲軸貼圖兩側有深色邊，三格看起來是三段，去掉後是一根連續的捲軸。機庫門與船塢閘門用同一套規則與模型產生方式（各自的頂箱、吊樑），未在遊戲中截圖 |
| 0.7.0 | 2026-10-13，原開發機 | 91 | 29（含船塢從箱子到下水、焊工、CNC、漏斗、汽車廠拆解） | `AutoShot` 在 `AeroCore HDFV QA`（1280×720）：在 0.4 的停機坪蓋一間汽車廠（8×5×4 m），載入轎車配置、開工到 40 %；截圖：從上方看房內的半成品轎車、箱子、焊接台、天車軌道，控制台的房間頁（進度、房間、載具摘要、可以使用）與配置頁（轎車 3D 預覽、按鈕），車床介面，創造模式分頁，世界中的機器方塊。指令以伺服器身分開不了捲門（`/hdfv dev open` 要玩家），門外截圖是關著的門；改了配置頁的轉向按鈕（箭頭字元太小）後未再截圖。焊工在組裝室裡走動、出廠的實際操作、多人連線未在遊戲中實測 |
| 0.1.0 | 2026-10-11，原開發機 | 30 | 5 | 未啟動客戶端；AeroCore 0.20.0 以前置 Mod 載入（GameTest 伺服器） |
| 0.5.0 | 2026-10-12，原開發機 | 60 | 13（含機體浮沉） | 機體模型改版後再以 `AutoShot` 看過六個範例機體的設計畫面（放大、剖視）與世界中的水上飛機、直升機：平滑外殼、塗裝、窗、門、燈、敞篷座艙、三廂轎車。先前的截圖：機體的總覽（剖視的座位、貨艙、掛載點）、模組頁、試算（浮筒、快艇的拖曳與 GZ、轎車的阻力功率、直升機機身）、飛艇式機身剖視；世界中水上飛機的斷階浮筒與機身、直升機的模組化機身。修了世界中浮筒的條紋（面法線）與船艏的鋸齒（AeroCore 的截面凸包在混合時丟角，以及沿周長取樣）；模組頁在這個 GUI 縮放下沒有 3D 預覽（參數欄太寬，既有的版面規則）；模組列的加入、上下移、複製、刪除按鈕，駕駛水舵轉向、起飛，多人連線未在遊戲中實測 |
| 0.4.0 | 2026-10-11，原開發機 | 54 | 12（含水上飛機、直升機） | `AutoShot` 在 `AeroCore HDFV QA`（1280×720）截圖：機翼、螺旋槳、旋翼、尾槳、水平與垂直尾翼的總覽、元件頁、3D 預覽、試算（機翼升阻、螺旋槳效率、旋翼懸停）；世界中水上飛機浮在海上（雙浮筒、襟翼放下、慢車滑水約 3 m/s）、直升機在停機坪（推滿 60 秒旋翼轉起、機身轉向）；修了鏡像半翼變暗（雙面蒙皮與剔除）；駕駛按鍵飛行、起飛與懸停在世界中未實測（只有離線測試與 GameTest 離地 3 m） |
| 0.3.0 | 2026-10-11，原開發機 | 49 | 10（含渦扇船、渦軸船） | `AutoShot` 在 `AeroCore HDFV QA`（1280×720）截圖：四具新範例的總覽、元件頁、剖視與外觀、試算（渦扇推力與起動、渦槳與渦軸的功率、扭矩曲線、起動）；預覽 1.2～2.2 萬個面時 77 FPS；滑鼠拖曳縮放、存檔到藍圖、多人連線未實測 |
| 0.2.0 | 2026-10-11，原開發機 | 42 | 8（載入 JEI 19.57.0.451） | `AutoShot` 在 `AeroCore HDFV QA`（1174×660 與 1280×720）截圖：「高自由度載具」分頁與 JEI、設計桌方塊、總覽與元件頁（紅黃提示）、試算圖、3D 預覽（直列四缸 DOHC、V8 OHV、直列四缸 SOHC、兩具渦噴的剖視與外觀、放大）；預覽約 2～4 萬個面時 74～110 FPS；滑鼠拖曳縮放、存檔到藍圖、多人連線、世界中載具上的新引擎外觀未實測 |
