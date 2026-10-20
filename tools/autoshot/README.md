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
- 塔台內部與機場的對照用 `/hdfv dev airport view <spot>`（本機專用伺服器＋Dev，`AeroCore Airport QA 21a`），和 0.21 的 `compare-0.21\after\` 同樣的鏡頭。
