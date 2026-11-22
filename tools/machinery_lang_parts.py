"""hdfv 0.26 (heavy machinery): the names the design screen needs for the new part types of dev.mcmod.hdfv.part.machine
(TrackDesign, DrumDesign, HydraulicPumpDesign, HydraulicMotorDesign, LinkDesign, HookDesign, OutriggerDesign and
SampleMachinery), in both languages: the two categories, the kinds, the samples, every parameter of the "design" page
that is not translated already (widthM, diameterM, material, maxSpeedRpm, lengthM, ratedLoadKg and strokeM are, with
meanings that fit), every enum value (as hdfv.choice.design.<parameter>.<value>, so nothing shared changes: the
generic hdfv.choice.gear is "Gears"), the proposed figures, the derived values the checks name, the workpieces the
proposed PartPlanner breakdown names and the tracks' condition.

groups() gives (anchor key, [(key, en, zh_tw)]) in the order they go in, for storage_assets.insert_lang (new keys go
after their anchor, a key already there gets its value set: none of these exists in the 0.25 files).
"""

CATEGORIES = [
    ('hdfv.category.undercarriage', 'Undercarriage', '行走機構'),
    ('hdfv.category.work', 'Work equipment', '作業裝置'),
]

KINDS = [
    ('hdfv.kind.track', 'Crawler tracks', '履帶'),
    ('hdfv.kind.drum', 'Roller drum', '壓路機滾筒'),
    ('hdfv.kind.outrigger', 'Outrigger jack', '外伸支腳'),
    ('hdfv.kind.hydraulic_pump', 'Hydraulic pump', '油壓泵'),
    ('hdfv.kind.hydraulic_motor', 'Hydraulic motor', '油壓馬達'),
    ('hdfv.kind.link', 'Work link (boom, arm, mast...)', '作業連桿（動臂、斗桿、門架…）'),
    ('hdfv.kind.hook', 'Hook block', '吊鉤組'),
    ('hdfv.kind.operator_cab', 'Operator cab', '機械駕駛室'),   # 2026-11-22
    ('hdfv.kind.engine_hood', 'Engine hood', '引擎蓋'),   # 2026-11-22
]

SAMPLES = [
    ('hdfv.sample.excavator_tracks_20t', 'Excavator tracks (20 t)', '挖土機履帶（20 噸級）'),
    ('hdfv.sample.dozer_tracks_d6', 'Dozer tracks (D6 class)', '推土機履帶（D6 級）'),
    ('hdfv.sample.mini_excavator_tracks', 'Mini excavator rubber tracks (1.7 t)', '小型挖土機橡膠履帶（1.7 噸）'),
    ('hdfv.sample.roller_drum_11t', 'Single-drum roller drum (11 t)', '單鋼輪壓路機滾筒（11 噸）'),
    ('hdfv.sample.padfoot_drum_11t', 'Padfoot drum (11 t)', '羊足滾筒（11 噸）'),
    ('hdfv.sample.tandem_drum', 'Tandem roller drum (10 t)', '雙鋼輪壓路機滾筒（10 噸）'),
    ('hdfv.sample.outrigger', 'Crane outrigger jack (40 t)', '吊車外伸支腳（40 噸）'),
    ('hdfv.sample.excavator_pump', 'Excavator main pump', '挖土機主泵'),
    ('hdfv.sample.pilot_pump', 'Pilot pump', '先導泵'),
    ('hdfv.sample.travel_motor', 'Travel drive (motor and final drive)', '行走馬達（含終傳動）'),
    ('hdfv.sample.hoist_winch', 'Hoist winch (40 t crane)', '主捲揚機（40 噸吊車）'),
    ('hdfv.sample.excavator_boom', 'Excavator boom (5.7 m)', '挖土機動臂（5.7 m）'),
    ('hdfv.sample.excavator_stick', 'Excavator stick (2.9 m)', '挖土機斗桿（2.9 m）'),
    ('hdfv.sample.loader_arm', 'Wheel loader lift arm', '輪式裝載機舉臂'),
    ('hdfv.sample.crane_boom_section', 'Telescopic crane boom section', '吊車伸縮臂節'),
    ('hdfv.sample.lattice_boom', 'Lattice boom (30 m)', '桁架吊臂（30 m）'),
    ('hdfv.sample.mast_stage', 'Forklift mast stage', '堆高機門架節'),
    ('hdfv.sample.swing_ring', 'Excavator swing ring', '挖土機迴轉支承'),
    ('hdfv.sample.articulation_hinge', 'Articulation hinge', '鉸接轉向中樞'),
    ('hdfv.sample.platform_boom', 'Work platform boom section', '高空作業車臂節'),
    ('hdfv.sample.placing_boom', 'Concrete pump placing boom section', '混凝土泵送車布料臂節'),
    ('hdfv.sample.outrigger_beam', 'Crane outrigger beam', '吊車支腳伸縮梁'),
    ('hdfv.sample.crane_hook_40t', 'Crane hook block (40 t)', '吊車吊鉤組（40 噸）'),
    ('hdfv.sample.excavator_cab', 'Excavator cab (20 t class, ROPS)', '挖土機駕駛室（20 噸級，ROPS）'),
    ('hdfv.sample.mini_excavator_cab', 'Mini excavator cab', '小型挖土機駕駛室'),
    ('hdfv.sample.loader_cab', 'Wheel loader cab (ROPS and FOPS)', '輪式裝載機駕駛室（ROPS＋FOPS）'),
    ('hdfv.sample.excavator_hood', 'Excavator engine hood (20 t class)', '挖土機引擎蓋（20 噸級）'),
    ('hdfv.sample.mini_excavator_hood', 'Mini excavator engine hood', '小型挖土機引擎蓋'),
    ('hdfv.sample.loader_hood', 'Wheel loader engine hood', '輪式裝載機引擎蓋'),
]

P = 'hdfv.param.design.'
PARAMS = [
    # tracks
    (P + 'designMassKg', 'Machine mass it is sized for', '設計機重'),
    (P + 'gaugeM', 'Track gauge (centre to centre)', '軌距（中心距）'),
    (P + 'contactLengthM', 'Length on the ground (end rollers)', '接地長度（首末支重輪）'),
    (P + 'shoeWidthM', 'Shoe width', '履帶板寬度'),
    (P + 'roadWheels', 'Road wheels per side', '每側支重輪數'),
    (P + 'roadWheelRadiusM', 'Road wheel radius', '支重輪半徑'),
    (P + 'shoe', 'Shoes', '履帶板'),
    (P + 'grouserHeightM', 'Grouser (lug) height', '履齒（凸塊）高度'),
    (P + 'pitchM', 'Track pitch', '履帶節距'),
    (P + 'idlerRadiusM', 'Front idler radius', '前導輪半徑'),
    (P + 'sprocketRadiusM', 'Sprocket radius', '驅動鏈輪半徑'),
    (P + 'runLiftM', 'Idler and sprocket clearance', '導輪與鏈輪離地'),
    (P + 'rollerStiffnessNPerM', 'Road wheel stiffness (0: for 25 mm under the machine)', '支重輪剛性（0＝設計機重下沉 25 mm）'),
    (P + 'rollerDampingNsPerM', 'Road wheel damping (0: 0.3 of critical)', '支重輪阻尼（0＝臨界阻尼的 0.3）'),
    (P + 'lateralResistance', 'Lateral resistance coefficient', '側向阻力係數'),
    (P + 'internalResistance', 'Internal resistance coefficient', '內部阻力係數'),
    (P + 'brakeNm', 'Brake torque', '煞車扭矩'),
    (P + 'parkNm', 'Parking brake torque', '駐車煞車扭矩'),
    # drums
    (P + 'shellM', 'Shell thickness', '滾筒殼厚'),
    (P + 'surface', 'Shell surface', '滾筒表面'),
    (P + 'amplitudeM', 'Nominal amplitude', '名目振幅'),
    (P + 'frequencyHz', 'Vibration frequency (Hz)', '振動頻率（Hz）'),
    (P + 'designLoadKg', 'Load on the drum (with itself)', '滾筒承載（含自重）'),
    (P + 'ballastKg', 'Ballast', '配重'),
    (P + 'sliceStiffnessNPerM', 'Slice stiffness (0: for 10 mm under the load)', '分段剛性（0＝承載下沉 10 mm）'),
    (P + 'sliceDampingNsPerM', 'Slice damping (0: 0.3 of critical)', '分段阻尼（0＝臨界阻尼的 0.3）'),
    # hydraulics
    (P + 'circuit', 'Hydraulic circuit', '油壓迴路'),
    (P + 'pumpKind', 'Pump type', '泵型式'),
    (P + 'control', 'Displacement control', '排量控制'),
    (P + 'displacementM3', 'Displacement per revolution', '每轉排量'),
    (P + 'reliefPa', 'Relief pressure (a pump: its relief or compensator)', '溢流壓力（泵：溢流或補償設定）'),
    (P + 'powerLimitW', 'Power limit (W)', '功率上限（W）'),
    (P + 'ratedSpeedRpm', 'Rated shaft speed', '額定轉速'),
    (P + 'motorKind', 'Motor type', '馬達型式'),
    (P + 'reductionRatio', 'Reduction ratio (1: none)', '減速比（1＝無）'),
    (P + 'winch', 'Winch drum', '捲揚滾筒'),
    (P + 'drumDiameterM', 'Drum diameter (first layer)', '滾筒直徑（第一層）'),
    (P + 'drumWidthM', 'Drum width between flanges', '滾筒凸緣間寬度'),
    (P + 'ropeDiameterM', 'Rope diameter', '鋼索直徑'),
    (P + 'partsOfLine', 'Parts of line (falls)', '鋼索股數（穿繩數）'),
    (P + 'ropeMinM', 'Shortest fall', '最短吊索長度'),
    (P + 'ropeMaxM', 'Longest fall', '最長吊索長度'),
    (P + 'ropeStartM', 'Starting fall', '起始吊索長度'),
    # links
    (P + 'linkKind', 'Kind of link', '連桿種類'),
    (P + 'sectionHeightM', 'Section height', '斷面高度'),
    (P + 'sectionWidthM', 'Section width', '斷面寬度'),
    (P + 'plateM', 'Plate (chord wall) thickness', '鋼板（弦桿管壁）厚度'),
    (P + 'jointType', 'Joint type', '關節型式'),
    (P + 'jointAxis', 'Joint axis', '關節軸'),
    (P + 'endless', 'Turns endlessly (no stops)', '無限迴轉（無止檔）'),
    (P + 'lowerDeg', 'Lower limit', '下限角度'),
    (P + 'upperDeg', 'Upper limit', '上限角度'),
    (P + 'startDeg', 'Starting angle', '起始角度'),
    (P + 'lowerM', 'Retracted end', '收回端'),
    (P + 'upperM', 'Extended end', '伸出端'),
    (P + 'startM', 'Starting extension', '起始伸出量'),
    (P + 'actuation', 'Driven by', '驅動方式'),
    (P + 'ramBoreM', 'Cylinder bore', '油壓缸缸徑'),
    (P + 'ramRodM', 'Cylinder rod diameter', '油壓缸桿徑'),
    (P + 'ramStrokeM', 'Cylinder stroke', '油壓缸行程'),
    (P + 'ramCount', 'Cylinders acting together', '並聯油壓缸數'),
    (P + 'basePinXM', 'Cylinder base pin x (mount frame)', '油壓缸底銷 x（安裝座標）'),
    (P + 'basePinYM', 'Cylinder base pin y (mount frame)', '油壓缸底銷 y（安裝座標）'),
    (P + 'basePinZM', 'Cylinder base pin z (mount frame)', '油壓缸底銷 z（安裝座標）'),
    (P + 'rodPinXM', 'Cylinder rod pin x (link frame)', '油壓缸桿端銷 x（連桿座標）'),
    (P + 'rodPinYM', 'Cylinder rod pin y (link frame)', '油壓缸桿端銷 y（連桿座標）'),
    (P + 'rodPinZM', 'Cylinder rod pin z (link frame)', '油壓缸桿端銷 z（連桿座標）'),
    (P + 'holdPa', 'Load-holding valve pressure', '負載保持閥壓力'),
    (P + 'valveFlowM3PerS', 'Control valve flow', '控制閥流量'),
    (P + 'slewDisplacementM3', 'Drive motor displacement', '驅動馬達排量'),
    (P + 'slewRatio', 'Drive gear ratio', '驅動減速比'),
    (P + 'slewBrakeNm', 'Holding brake at the joint', '關節保持煞車扭矩'),
    (P + 'freeFriction', 'Pin friction (N·m, or N sliding)', '銷軸摩擦（N·m；滑動為 N）'),
    # hooks and outriggers
    (P + 'hookKind', 'Hook', '吊鉤型式'),
    (P + 'dampingRatio', 'Rope damping ratio', '鋼索阻尼比'),
    (P + 'extensionTimeS', 'Time to draw up (s)', '收起時間（秒）'),
    (P + 'padDiameterM', 'Pad diameter', '墊盤直徑'),
    # operator cabs (2026-11-22)
    (P + 'frontRakeDeg', 'Front window leaning back', '前窗後傾角'),
    (P + 'protection', 'Protective structure', '防護結構'),
    (P + 'doorSide', 'Door side', '車門側'),
    # engine hoods (2026-11-22)
    (P + 'topDropM', 'Top falling toward the grille', '頂面往格柵端降低'),
    (P + 'tailRadiusM', 'Tail corner radius', '尾端圓角半徑'),
    (P + 'grille', 'Radiator grille', '散熱格柵'),
    (P + 'stackM', 'Exhaust stack height', '排氣管高度'),
]

C = 'hdfv.choice.design.'
CHOICES = [
    (C + 'shoe.steel', 'Steel shoes', '鋼製履帶板'),
    (C + 'shoe.rubber', 'Rubber belt', '橡膠履帶'),
    (C + 'surface.smooth', 'Smooth', '光面'),
    (C + 'surface.padfoot', 'Padfoot', '羊足（凸塊）'),
    (C + 'pumpKind.axial_piston', 'Axial piston', '軸向柱塞'),
    (C + 'pumpKind.gear', 'External gear', '外齒輪'),
    (C + 'motorKind.axial_piston', 'Axial piston', '軸向柱塞'),
    (C + 'motorKind.gear', 'External gear', '外齒輪'),
    (C + 'control.fixed', 'Fixed', '定量'),
    (C + 'control.pressure_compensated', 'Pressure compensated', '壓力補償'),
    (C + 'control.power_limited', 'Power limited', '恆功率'),
    (C + 'linkKind.boom', 'Boom', '動臂'),
    (C + 'linkKind.stick', 'Stick (arm)', '斗桿'),
    (C + 'linkKind.loader_arm', 'Loader lift arm', '裝載機舉臂'),
    (C + 'linkKind.telescopic', 'Telescopic section', '伸縮臂節'),
    (C + 'linkKind.lattice', 'Lattice boom', '桁架吊臂'),
    (C + 'linkKind.mast', 'Mast stage', '門架節'),
    (C + 'linkKind.slew_ring', 'Slewing ring', '迴轉支承'),
    (C + 'linkKind.articulation', 'Articulation hinge', '鉸接中樞'),
    (C + 'linkKind.platform_boom', 'Platform boom section', '高空作業臂節'),
    (C + 'linkKind.placing_boom', 'Placing boom section', '布料臂節'),
    (C + 'linkKind.outrigger_beam', 'Outrigger beam', '支腳伸縮梁'),
    (C + 'jointType.auto', "The kind's", '依連桿種類'),
    (C + 'jointType.revolute', 'Turning', '旋轉'),
    (C + 'jointType.prismatic', 'Sliding', '滑動'),
    (C + 'jointAxis.auto', "The kind's", '依連桿種類'),
    (C + 'jointAxis.x', 'x (along the link)', 'x（沿連桿）'),
    (C + 'jointAxis.y', 'y (up)', 'y（向上）'),
    (C + 'jointAxis.z', 'z (across)', 'z（橫向）'),
    (C + 'actuation.ram', 'Hydraulic cylinders', '油壓缸'),
    (C + 'actuation.slew', 'Geared motor', '馬達與減速機'),
    (C + 'actuation.free', 'Free pin', '自由銷'),
    (C + 'actuation.held', 'Held', '固定'),
    (C + 'hookKind.single', 'Single hook', '單鉤'),
    (C + 'hookKind.ramshorn', 'Ramshorn hook', '雙鉤（羊角鉤）'),
    (C + 'protection.cab', 'Cab (not rated)', '一般駕駛室（無認證）'),
    (C + 'protection.rops', 'ROPS (roll-over protection)', 'ROPS（防翻覆）'),
    (C + 'protection.rops_fops', 'ROPS and FOPS (falling objects too)', 'ROPS＋FOPS（防翻覆與防落物）'),
    (C + 'doorSide.left', 'Left', '左側'),
    (C + 'doorSide.right', 'Right', '右側'),
    (C + 'grille.sides', 'Louvred side doors', '左右兩側百葉'),
    (C + 'grille.rear', 'Rear', '後端'),
    (C + 'grille.front', 'Front', '前端'),
    # the circuits (if RecordEditor.choices offers HydraulicPumpDesign.CIRCUITS for "circuit")
    (C + 'circuit.main', 'Main', '主迴路'),
    (C + 'circuit.second', 'Second pump', '第二泵'),
    (C + 'circuit.swing', 'Swing', '迴轉'),
    (C + 'circuit.travel', 'Travel', '行走'),
    (C + 'circuit.steering', 'Steering', '轉向'),
    (C + 'circuit.auxiliary', 'Auxiliary', '輔助'),
    (C + 'circuit.pilot', 'Pilot', '先導'),
]

F = 'hdfv.figure.'
FIGURES = [
    (F + 'groundPressure', 'Ground pressure', '接地壓力'),
    (F + 'beltLength', 'Track length (each side)', '履帶長度（每側）'),
    (F + 'shoesPerSide', 'Shoes per side', '每側履帶板數'),
    (F + 'turningMoment', 'Turning resistance on the spot', '原地轉向阻力矩'),
    (F + 'glazing', 'Glazing', '玻璃面積'),
    (F + 'louvres', 'Louvres', '百葉面積'),
    (F + 'dynamicForce', 'Centrifugal force', '激振力'),
    (F + 'eccentricMoment', 'Eccentric moment', '偏心力矩'),
    (F + 'linearLoad', 'Static linear load', '靜線壓力'),
    (F + 'ratedFlow', 'Flow at rated speed', '額定轉速流量'),
    (F + 'maxTorque', 'Most torque', '最大扭矩'),
    (F + 'cornerPower', 'Corner power', '角功率'),
    (F + 'maxOutputSpeed', 'Output top speed', '輸出最高轉速'),
    (F + 'maxFlow', 'Flow at top speed', '最高轉速流量'),
    (F + 'hookLoad', 'Hook load at the relief', '溢流壓力下吊鉤載重'),
    (F + 'hookSpeed', 'Hook speed', '吊鉤速度'),
    (F + 'ropeLayers', 'Rope layers on the drum', '滾筒鋼索層數'),
    (F + 'jointRange', 'Range of motion', '活動範圍'),
    (F + 'ramPush', 'Cylinder push', '油壓缸推力'),
    (F + 'jointEffort', 'Joint effort (least lever)', '關節出力（最小力臂）'),
    (F + 'slewTorque', 'Drive torque at the joint', '關節驅動扭矩'),
    (F + 'slewSpeed', 'Drive speed', '驅動轉速'),
    (F + 'ropeCapacity', 'Rope capacity over the falls', '鋼索容許吊重'),
    (F + 'sheaves', 'Sheaves', '滑輪數'),
    (F + 'sheaveDiameter', 'Sheave diameter', '滑輪直徑'),
    (F + 'blockHeight', 'Block height', '吊鉤組高度'),
    (F + 'jackBore', 'Jack bore', '千斤頂缸徑'),
    (F + 'padPressure', 'Pressure on the pad', '墊盤壓力'),
]

D = 'hdfv.derived.'
DERIVED = [
    (D + 'groundPressurePa', 'Ground pressure', '接地壓力'),
    (D + 'linearLoadNPerM', 'Static linear load', '靜線壓力'),
    (D + 'hookLoadN', 'Hook load with the block', '含吊鉤組的吊重'),
    (D + 'padPressurePa', 'Pressure on the pad', '墊盤壓力'),
    (D + 'ropeLayers', 'Rope layers on the drum', '滾筒鋼索層數'),
]

W = 'hdfv.workpiece.'
WORKPIECES = [
    (W + 'track_shoe', 'Track shoe', '履帶板'),
    (W + 'track_link', 'Track link', '履帶鏈節'),
    (W + 'track_pin', 'Track pin and bushing', '履帶銷與襯套'),
    (W + 'rubber_track', 'Rubber track belt', '橡膠履帶'),
    (W + 'track_roller', 'Track roller', '支重輪'),
    (W + 'track_idler', 'Front idler', '前導輪'),
    (W + 'track_sprocket', 'Drive sprocket', '驅動鏈輪'),
    (W + 'track_frame', 'Track frame', '履帶架'),
    (W + 'car_body', 'Car body (lower frame)', '下部車架'),
    (W + 'drum_shell', 'Drum shell', '滾筒殼'),
    (W + 'drum_end_plate', 'Drum end plate', '滾筒端板'),
    (W + 'drum_pad', 'Padfoot pad', '羊足凸塊'),
    (W + 'vibrator_shaft', 'Vibrator shaft', '振動軸'),
    (W + 'eccentric_weight', 'Eccentric weight', '偏心塊'),
    (W + 'vibrator_housing', 'Vibrator bearing housing', '振動軸承座'),
    (W + 'pump_housing', 'Pump or motor housing', '泵／馬達殼體'),
    (W + 'rotating_group', 'Rotating group (pistons and barrel)', '旋轉組（柱塞與缸體）'),
    (W + 'swash_plate', 'Swash plate', '斜盤'),
    (W + 'unit_shaft', 'Pump or motor shaft', '泵／馬達軸'),
    (W + 'unit_gears', 'Gear pump gears', '齒輪泵齒輪'),
    (W + 'final_drive_gears', 'Planetary reducer gears', '行星減速機齒輪'),
    (W + 'final_drive_housing', 'Reducer housing', '減速機殼體'),
    (W + 'winch_drum', 'Winch drum', '捲揚滾筒'),
    (W + 'winch_flange', 'Winch drum flange', '捲揚滾筒凸緣'),
    (W + 'wire_rope', 'Wire rope', '鋼索'),
    (W + 'link_plates', 'Boom plates (webs and flanges)', '臂體鋼板（腹板與翼板）'),
    (W + 'link_boss', 'Pin boss', '銷座'),
    (W + 'link_pin', 'Pivot pin', '樞軸銷'),
    (W + 'lattice_chord', 'Lattice chord tube', '桁架弦桿管'),
    (W + 'lattice_lacing', 'Lattice lacing tube', '桁架腹桿管'),
    (W + 'slewing_ring', 'Slewing ring race', '迴轉支承座圈'),
    (W + 'cylinder_barrel', 'Cylinder barrel', '油壓缸缸筒'),
    (W + 'cylinder_rod', 'Cylinder rod', '油壓缸活塞桿'),
    (W + 'cylinder_head', 'Cylinder head and piston', '油壓缸缸頭與活塞'),
    (W + 'hook_forging', 'Hook forging', '鍛造吊鉤'),
    (W + 'hook_sheave', 'Sheave', '滑輪'),
    (W + 'hook_side_plate', 'Hook block side plate', '吊鉤組側板'),
    (W + 'hook_crosshead', 'Hook crosshead', '吊鉤橫樑'),
    (W + 'jack_housing', 'Jack housing', '千斤頂外箱'),
    (W + 'outrigger_pad', 'Outrigger pad', '支腳墊盤'),
    (W + 'cab_frame', 'Cab frame', '駕駛室骨架'),
    (W + 'cab_fops_guard', 'FOPS roof guard', 'FOPS 護頂'),
    (W + 'cab_floor', 'Cab floor plate', '駕駛室地板'),
    (W + 'cab_panels', 'Cab panels and glazing', '駕駛室鈑件與玻璃'),
    (W + 'hood_frame', 'Hood frame', '引擎蓋骨架'),
    (W + 'hood_panels', 'Hood panels and doors', '引擎蓋鈑件與門板'),
    (W + 'hood_grille', 'Hood louvres', '引擎蓋百葉'),
    (W + 'hood_stack', 'Exhaust stack', '排氣管'),
]


# the tracks' condition (TrackPart.TRACKS), if damage.Conditions lists it
CONDITIONS = [
    ('hdfv.condition.tracks', 'Tracks', '履帶'),
]


def groups():
    """(anchor key, [(key, en, zh_tw)]) in the order they go in (anchors as in the 0.25 lang files)."""
    return [
        ('hdfv.category.storage', CATEGORIES),
        ('hdfv.kind.storage_box', KINDS),
        ('hdfv.sample.container_20ft', SAMPLES),
        ('hdfv.param.design.paintColor', PARAMS),
        ('hdfv.choice.container', CHOICES),
        ('hdfv.figure.ratedLoad', FIGURES),
        ('hdfv.derived.roofLoadKg', DERIVED),
        ('hdfv.workpiece.landing_leg_foot', WORKPIECES),
        ('hdfv.condition.tank', CONDITIONS),
    ]


if __name__ == '__main__':
    n = sum(len(e) for _, e in groups())
    print(n, 'keys in', len(groups()), 'groups')
