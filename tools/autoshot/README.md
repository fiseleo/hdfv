# AutoShot 計畫（給 AeroCore 的 `AutoShot`）

遊戲讀 `%USERPROFILE%\.mcmod\runs\hdfv\client\aerocore-autoshot.txt`，讀完就刪，所以計畫本身存在這裡。開遊戲前先問使用者（第 2 個螢幕、不搶焦點）。

- `compare-0.22.txt`：0.22 美化的對照（QA 世界 `AeroCore HDFV QA`）。
  - 內容：
    - 在空中石台排一組載具：半拖車、轎車、直升機、水上飛機、遊艇；拍白天與夜晚的特寫。
    - 機器場景、停放的載具、空中塔台：都等烘焙完再 `fps`。
    - 設計畫面：車身、平頭駕駛室、機翼、直升機機身、直列四缸、J79。
    - 最後清掉那組載具和石台。
  - 檔裡的 `TAG` 換成 `b22`（before）、`a22`（after）或 `o22`（美化著色關掉）再複製過去，例如：

    ```bash
    sed 's/TAG/a22/g' tools/autoshot/compare-0.22.txt > "$USERPROFILE/.mcmod/runs/hdfv/client/aerocore-autoshot.txt"
    ```

  - 截圖在 `client\screenshots\aerocore-<TAG>-*.png`。整理到 `screenshots\compare-0.22\{before,after,off}\` 後，用 AeroCore 的 `scripts/ShotDiff.java` 比對。
- `verify-0.25-world.txt`、`verify-0.25-damage.txt`、`verify-0.25-screens.txt`、`verify-0.25-fixes.txt`：0.25 的客戶端驗證（0.24 的 `AeroCore Production QA` 複製成 `AeroCore 025 QA`，依序跑，後面的用到前面留下的車與修理廠；`fixes` 是改完畫面後的複拍），用 0.25 新的 `/hdfvdev status|hover|mouse|click|scroll|drag|lang|jei|advancements` 與測試車 `roofbox`、`containersemi`（`advancements` 直接開在 hdfv 的分頁，不必 `-Phdfv_create=false`）。
- 塔台內部與機場的對照用 `/hdfv dev airport view <spot>`（本機專用伺服器＋Dev，`AeroCore Airport QA 21a`），和 0.21 的 `compare-0.21\after\` 同樣的鏡頭。
- `verify-0.26-machines.txt`、`verify-0.26-excavator.txt`：0.26 的重型機械（0.25 的 `AeroCore 025 QA` 複製成 `AeroCore 026 QA`）。第一份排 16 台測試機、塔吊、瀝青拌合廠與設計畫面；16 台會讓整合伺服器延遲（截圖與指令會錯位），所以第二份只放挖土機、每個動作等久一點。
- `verify-0.26-looks.txt`：0.26 的外觀（Blender 擺過的形狀、油缸、塔吊標準節與上部結構）。挖土機那段的鏡頭和 `verify-0.26-excavator.txt` 一樣
  （改前是 `v26c-*`，改後 `v26d-*`），再拍推土機、壓路機、卡車、裝載機與塔吊的頭部、小車。清場的 `kill` 也殺掉塔吊的駕駛室與吊鉤，控制台會用新的藍圖重做。
- `verify-0.26-cab.txt`（`v26e-*`，塔吊駕駛室移到吊臂根部下）、`verify-0.26-cabs.txt`（`v26f-*`，機械駕駛室）、`verify-0.26-hoods.txt`（`v26g-*`，引擎蓋）：
  後兩份清場、擺車和鏡頭都一樣（挖土機、輪式裝載機、壓路機、履帶吊），可以逐張對照；`hoods` 另外從後側拍挖土機與裝載機的引擎蓋，最後開設計畫面。
- `verify-0.26-crane.txt`（`v26h-*`）：履帶吊改用 100 噸級吊車的履帶與配重之後（之前一放下就往前倒在吊臂上）。擺車和 `hoods` 一樣，
  另在後排（z 244，在 `row` 鏡頭背後）放拉鏟；`crawlercrane`、`row` 的鏡頭同 `v26g-*`，另從後方、側面拍履帶吊，再拍拉鏟。
  前幾份的 `execute as @e[...] run data get entity @s Pos` 什麼都沒印：`execute as` 把回饋送給機械實體而不是玩家，這份就不放了。
- `verify-0.26-redraw1.txt`（`r1*-*`）：全部重畫的第一組（機械零件）。
  - 分四批、每批 5～7 台測試機，每批拍完清掉。
  - 最後把第二、三組（引擎、變速箱、機身、車身、底盤、氣瓶、儲物箱）各範例的世界模型匯出成 OBJ（`hdfvdev export world`），作為改前的對照。
  - 改前的那份另存在本機 `%USERPROFILE%\.mcmod\exports-before-redraw`。
- `verify-0.26-redraw2.txt`（`r2-*`）：第二到第四組改完。
  - 在空地放七個重畫的機器方塊拍正面，拍完拆掉。
  - 開 12 個設計畫面截圖並匯出世界模型：變速箱、分動箱、渦扇、梯形車架、氫氣瓶、機翼。
