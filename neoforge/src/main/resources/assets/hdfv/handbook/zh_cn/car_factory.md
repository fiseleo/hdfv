---
id: car_factory
section: main
order: 14
title: 组装室造第一台车
icon: hdfv:car_factory_controller
advancement: hdfv:tutorial/first_car
---

车在**汽车厂**里造：一个封闭房间，地面铺{item hdfv:assembly_floor}，墙上有和地面同高的门（{item hdfv:roller_shutter}），{item hdfv:car_factory_controller}嵌在墙上、正面朝内。屋顶的{item hdfv:crane_rail}用来吊重件。长 8 米、宽 5 米、高 4 米是不错的起点。

{image car_factory}

{items hdfv:car_factory_controller hdfv:assembly_floor hdfv:roller_shutter hdfv:crane_rail hdfv:welding_station}
- 扫描：控制台检查房间，说出缺什么。
- 配置：把零件蓝图（车身、底盘、车轴、发动机、变速箱……）放进图纸格，在配置界面摆好；它会检查放不放得下（发动机要放得进发动机舱）和车是否合理。{item hdfv:vehicle_blueprint}可以存下配置。
- 开工：它从房间里的箱子取零件、组件和料件来造。房间里有人操作（玩家或焊工）的焊接台就是它的工班。
- 出厂：完成后门会打开，车开出来。

{design sedan_body}

{see driving}
