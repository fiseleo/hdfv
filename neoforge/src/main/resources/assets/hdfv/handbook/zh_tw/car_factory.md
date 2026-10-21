---
id: car_factory
section: main
order: 14
title: 組裝室造第一台車
icon: hdfv:car_factory_controller
advancement: hdfv:tutorial/first_car
---

車在**汽車廠**裡造：一個封閉房間，地面鋪{item hdfv:assembly_floor}，牆上有和地面同高的門（{item hdfv:roller_shutter}），{item hdfv:car_factory_controller}嵌在牆上、正面朝內。屋頂的{item hdfv:crane_rail}用來吊重件。長 8 公尺、寬 5 公尺、高 4 公尺是不錯的起點。

{items hdfv:car_factory_controller hdfv:assembly_floor hdfv:roller_shutter hdfv:crane_rail hdfv:welding_station}
- 掃描：控制台檢查房間，說出缺什麼。
- 配置：把零件藍圖（車身、底盤、車軸、引擎、變速箱……）放進圖紙格，在配置畫面擺好；它會檢查放不放得下（引擎要放得進引擎室）和車是否合理。{item hdfv:vehicle_blueprint}可以存下配置。
- 開工：它從房間裡的箱子取零件、元件和料件來造。房間裡有人操作（玩家或焊工）的焊接台就是它的工班。
- 出廠：完成後門會打開，車開出來。

{design sedan_body}

{see driving}
