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
