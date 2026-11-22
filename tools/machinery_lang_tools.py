"""hdfv 0.26 (heavy machinery): the names the design screen needs for the work tool (WorkToolDesign, hdfv:work_tool, and
its samples in SampleTools), in both languages: its kind, its 36 samples, every parameter of the "design" page that is
not translated already (plateM, material, ramBoreM, ramRodM, ramStrokeM, ramCount, reliefPa, holdPa, valveFlowM3PerS,
circuit, ratedLoadKg and massGivenKg are, from machinery_lang_parts.py and before, with meanings that fit), hints for
the parameters whose meaning changes with the kind, every enum value as hdfv.choice.design.<parameter>.<value> (the
circuits' are machinery_lang_parts.py's), the figures (mass, capacity, ratedLoad, jointRange and ramPush are there
already), the derived values the checks name, the workpieces of WorkToolDesign.massBreakdownKg's keys, and the
proposed conditions of the tools (teeth, cutting edges, liners, picks, discs).

groups() gives (anchor key, [(key, en, zh_tw)]) in the order they go in, for storage_assets.insert_lang (new keys go
after their anchor, a key already there gets its value set). The anchors are machinery_lang_parts.py's last keys.
"""

KINDS = [
    ('hdfv.kind.work_tool', 'Work tool (bucket, blade, drum...)', '作業工具（挖斗、鏟刀、轉鼓…）'),
]

S = 'hdfv.sample.'
SAMPLES = [
    (S + 'excavator_bucket_1m3', 'Excavator bucket (1.2 m³, 20 t class)', '挖土機挖斗（1.2 m³，20 噸級）'),
    (S + 'mini_excavator_bucket', 'Mini excavator bucket (600 mm)', '小型挖土機挖斗（600 mm）'),
    (S + 'loader_bucket_3m3', 'Wheel loader bucket (3 m³)', '輪式裝載機鏟斗（3 m³）'),
    (S + 'mining_dipper_30m3', 'Mining face-shovel bucket (30 m³)', '礦用正鏟鏟斗（30 m³）'),
    (S + 'dragline_bucket', 'Dragline bucket (2.3 m³)', '拉鏟斗（2.3 m³）'),
    (S + 'clamshell', 'Clamshell bucket (1.5 m³)', '抓斗（1.5 m³）'),
    (S + 'dozer_su_blade', 'Dozer SU blade (D6 class)', '推土機半 U 型鏟刀（D6 級）'),
    (S + 'dozer_u_blade', 'Dozer U blade (D8 class)', '推土機 U 型鏟刀（D8 級）'),
    (S + 'grader_moldboard', 'Grader moldboard (12 ft)', '平路機刮刀（12 呎）'),
    (S + 'snow_plow', 'Truck snow plow (3 m)', '卡車除雪鏟（3 m）'),
    (S + 'ripper_single', 'Single-shank ripper', '單齒裂土器'),
    (S + 'hydraulic_hammer', 'Hydraulic breaker (3600 J)', '油壓破碎機（3600 J）'),
    (S + 'grapple', 'Demolition grapple', '拆除抓具'),
    (S + 'pallet_forks', 'Pallet forks (4 t)', '棧板貨叉（4 噸）'),
    (S + 'quick_coupler', 'Quick coupler (20 t class)', '快速接頭（20 噸級）'),
    (S + 'plate_compactor', 'Excavator plate compactor', '挖土機用平板夯實機'),
    (S + 'scraper_bowl_15m3', 'Scraper bowl (15 m³)', '鏟運機鏟斗（15 m³）'),
    (S + 'haul_truck_body_60m3', 'Haul truck body (60 m³)', '礦用卡車車斗（60 m³）'),
    (S + 'articulated_truck_body_18m3', 'Articulated dump truck body (18 m³)', '鉸接式卡車車斗（18 m³）'),
    (S + 'dump_truck_body_10m3', 'Tipper body (10 m³)', '傾卸車車斗（10 m³）'),
    (S + 'conveyor_1m', 'Discharge conveyor (1 m belt)', '卸料輸送帶（1 m 帶寬）'),
    (S + 'paver_hopper', 'Paver hopper', '鋪裝機料斗'),
    (S + 'screed_3m', 'Extending screed (3 to 6 m)', '伸縮式熨平板（3 至 6 m）'),
    (S + 'milling_drum_2m', 'Milling drum (2 m)', '銑刨轉鼓（2 m）'),
    (S + 'tbm_cutterhead_6m', 'TBM cutterhead (6 m)', '潛盾機刀盤（6 m）'),
    (S + 'roadheader_head', 'Roadheader cutting heads', '懸臂式掘進機截割頭'),
    (S + 'bucket_wheel_10m', 'Bucket wheel (10 m)', '斗輪（10 m）'),
    (S + 'trencher_chain', 'Trencher chain boom (1.8 m)', '挖溝機鏈條臂（1.8 m）'),
    (S + 'drill_auger', 'Drill auger (600 mm)', '螺旋鑽（600 mm）'),
    (S + 'pile_hammer_5t', 'Hydraulic pile hammer (5 t)', '油壓打樁錘（5 噸）'),
    (S + 'broom', 'Pickup broom (1.8 m)', '集塵掃刷（1.8 m）'),
    (S + 'snow_blower', 'Snow blower (1.8 m)', '拋雪機（1.8 m）'),
    (S + 'water_spray_bar', 'Water spray bar', '灑水噴桿'),
    (S + 'mixer_drum_9m3', 'Truck mixer drum (9 m³)', '預拌車攪拌筒（9 m³）'),
    (S + 'work_platform_basket', 'Work platform basket', '高空作業車工作籃'),
    (S + 'segment_erector', 'Segment erector', '環片組立機'),
]

P = 'hdfv.param.design.'
PARAMS = [
    (P + 'toolKind', 'Kind of tool', '工具種類'),
    (P + 'bladeStyle', 'Blade style', '鏟刀型式'),
    (P + 'toolWidthM', 'Width', '寬度'),
    (P + 'toolWidthM.hint', 'Across the machine: a bucket or blade, a body or bowl, a belt, a drum, a trench, a broom', '橫向寬度：挖斗或鏟刀、車斗或鏟斗、皮帶、轉鼓、溝寬、掃刷'),
    (P + 'toolHeightM', 'Height or depth', '高度或深度'),
    (P + 'toolHeightM.hint', "A bucket's bowl depth; a blade's, body's or hood's height; a ripper's penetration; a drum's or trench's greatest depth",
     '挖斗的斗深；鏟刀、車斗或護罩的高度；裂土器的入土深度；轉鼓或溝的最大深度'),
    (P + 'toolLengthM', 'Length', '長度'),
    (P + 'toolLengthM.hint', "A bucket's strike plane (lip to the back's top); a body's, bowl's or belt's length; a boom's or auger's; a hammer's with its chisel",
     '挖斗的刮平面（斗唇到斗背頂）；車斗、鏟斗或皮帶的長度；臂或螺旋鑽的長度；破碎機含鑿桿的長度'),
    (P + 'teeth', 'Teeth (shanks, picks, buckets)', '斗齒數（裂土齒、截齒、鏟斗數）'),
    (P + 'teeth.hint', "A bucket's teeth, a ripper's shanks, a drum's picks, a wheel's buckets, a chain's or an auger's teeth",
     '挖斗的斗齒、裂土器的齒柄、轉鼓的截齒、斗輪的鏟斗、鏈條或螺旋鑽的齒'),
    (P + 'rakeDeg', 'Rake of the cutting edge', '切削刃前角'),
    (P + 'capacityGivenM3', 'Rated capacity (blank: SAE / ISO from the shape)', '額定容量（空白＝依形狀以 SAE／ISO 計算）'),
    (P + 'jointed', 'Its own joint (curl, tilt, tip...)', '自有關節（翻轉、傾斜、舉升…）'),
    (P + 'jointMinDeg', 'Joint lower limit', '關節下限角度'),
    (P + 'jointMaxDeg', 'Joint upper limit', '關節上限角度'),
    (P + 'travelM', 'Lift travel', '舉升行程'),
    (P + 'ramArmM', 'Rod pin from the joint', '桿端銷至關節距離'),
    (P + 'ramArmDeg', 'Rod pin angle', '桿端銷角度'),
    (P + 'ramArmDeg.hint', "In the joint's plane: from +x toward +y (a pin across), +z toward +y (a tilt), +x toward +z (an angle)",
     '在關節平面內量：自 +x 向 +y（橫向銷）、自 +z 向 +y（傾斜）、自 +x 向 +z（偏角）'),
    (P + 'ramBaseM', 'Base pin from the joint (blank: placed to fit)', '底銷至關節距離（空白＝自動配置）'),
    (P + 'ramBaseM.hint', 'Blank: the base pin that makes the closed and extended lengths the joint\'s limits', '空白時，自動找出讓油壓缸收縮與伸出長度正好對應關節極限的底銷位置'),
    (P + 'ramBaseDeg', 'Base pin angle (blank: placed to fit)', '底銷角度（空白＝自動配置）'),
    (P + 'cutterRadiusM', 'Cutter radius (to the picks)', '切削半徑（至截齒）'),
    (P + 'discs', 'Disc cutters', '盤形滾刀數'),
    (P + 'discDiameterM', 'Disc cutter diameter', '盤形滾刀直徑'),
    (P + 'discTipM', 'Disc tip width', '滾刀刃寬'),
    (P + 'discSpacingM', 'Disc spacing', '滾刀間距'),
    (P + 'impactEnergyJ', 'Impact energy class (J)', '衝擊能量等級（J）'),
    (P + 'ramMassKg', 'Drop weight (ram)', '錘體質量'),
    (P + 'dropM', 'Stroke (drop height)', '落距'),
    (P + 'beltSpeedMps', 'Belt speed (m/s)', '皮帶速度（m/s）'),
    (P + 'extensionM', 'Extension each side', '單側伸縮寬度'),
    (P + 'matThicknessM', 'Mat thickness', '鋪築厚度'),
    (P + 'centrifugalN', 'Centrifugal force', '激振力'),
    (P + 'vibrationRpm', 'Vibrations per minute', '每分鐘振動次數'),
    (P + 'sprayM3PerS', 'Spray rate', '噴灑流量'),
]

C = 'hdfv.choice.design.'
TOOL_KINDS = [
    ('backhoe_bucket', 'Backhoe bucket', '反鏟挖斗'),
    ('loader_bucket', 'Loader bucket', '裝載機鏟斗'),
    ('face_shovel_bucket', 'Face-shovel bucket (dipper)', '正鏟鏟斗'),
    ('dragline_bucket', 'Dragline bucket', '拉鏟斗'),
    ('clamshell', 'Clamshell', '抓斗'),
    ('trench_bucket', 'Trenching bucket', '挖溝斗'),
    ('dozer_blade', 'Dozer blade', '推土鏟刀'),
    ('grader_moldboard', 'Grader moldboard', '平路機刮刀'),
    ('snow_plow', 'Snow plow', '除雪鏟'),
    ('ripper', 'Ripper', '裂土器'),
    ('hydraulic_hammer', 'Hydraulic breaker', '油壓破碎機'),
    ('grapple', 'Grapple', '抓具'),
    ('forks', 'Forks', '貨叉'),
    ('quick_coupler', 'Quick coupler', '快速接頭'),
    ('compactor_plate', 'Plate compactor', '平板夯實機'),
    ('scraper_bowl', 'Scraper bowl', '鏟運機鏟斗'),
    ('dump_body', 'Dump body', '傾卸車斗'),
    ('conveyor', 'Conveyor', '輸送帶'),
    ('paver_hopper', 'Paver hopper', '鋪裝機料斗'),
    ('screed', 'Screed', '熨平板'),
    ('milling_drum', 'Milling drum', '銑刨轉鼓'),
    ('cutterhead', 'TBM cutterhead', '潛盾機刀盤'),
    ('roadheader_head', 'Roadheader head', '掘進機截割頭'),
    ('bucket_wheel', 'Bucket wheel', '斗輪'),
    ('trencher_chain', 'Trencher chain', '挖溝鏈條'),
    ('auger', 'Auger', '螺旋鑽'),
    ('pile_hammer', 'Pile hammer', '打樁錘'),
    ('broom', 'Broom', '掃刷'),
    ('snow_blower', 'Snow blower', '拋雪機'),
    ('spray_bar', 'Spray bar', '噴灑桿'),
    ('mixer_drum', 'Mixer drum', '攪拌筒'),
    ('work_platform', 'Work platform basket', '高空作業籃'),
    ('segment_erector', 'Segment erector', '環片組立機'),
]
CHOICES = [(C + 'toolKind.' + v, en, zh) for v, en, zh in TOOL_KINDS] + [
    (C + 'bladeStyle.s', 'Straight (S)', '直鏟（S）'),
    (C + 'bladeStyle.su', 'Semi-U (SU)', '半 U 型（SU）'),
    (C + 'bladeStyle.u', 'Universal (U)', 'U 型（U）'),
    (C + 'bladeStyle.angle', 'Angle (A)', '角鏟（A）'),
]

F = 'hdfv.figure.'
FIGURES = [
    (F + 'heapedCapacity', 'Heaped capacity', '堆裝容量'),
    (F + 'struckCapacity', 'Struck capacity', '平裝容量'),
    (F + 'bladeCapacity', 'Blade capacity (SAE J1265)', '鏟刀容量（SAE J1265）'),
    (F + 'toothWidth', 'Tooth width', '斗齒寬度'),
    (F + 'tipForce', 'Force at the edge (relief, mid-range)', '刃口出力（溢流壓力、行程中點）'),
    (F + 'cutterInertia', 'Cutter inertia', '切削轉子慣量'),
    (F + 'faceCoverage', 'Discs cover the face', '滾刀覆蓋刀盤面比'),
    (F + 'brokenPerBlow', 'Granite broken per blow', '每次衝擊破碎花崗岩量'),
    (F + 'blowEnergy', 'Blow energy', '打擊能量'),
    (F + 'beltCapacity', 'Belt capacity', '輸送能力'),
    (F + 'pavingWidth', 'Paving width', '鋪築寬度'),
    (F + 'persons', 'Persons', '乘載人數'),
    (F + 'deckArea', 'Floor area', '底板面積'),
    (F + 'staticPressure', 'Static pressure under the plate', '底板靜壓力'),
    (F + 'drumVolume', 'Drum gross volume', '攪拌筒總容積'),
    (F + 'wheelCapacity', 'Buckets on the wheel', '斗輪總斗容'),
]

D = 'hdfv.derived.'
DERIVED = [
    (D + 'capacityM3', 'Capacity', '容量'),
    (D + 'persons', 'Persons', '乘載人數'),
    (D + 'faceCoverage', 'Discs cover the face', '滾刀覆蓋刀盤面比'),
    (D + 'pavingWidthM', 'Paving width', '鋪築寬度'),
    (D + 'forkRatingKg', 'Rated load against the forks\' bending', '額定載重與貨叉彎曲強度'),
    (D + 'ramSpeedMps', 'Cylinder speed at full flow', '全流量油壓缸速度'),
    (D + 'rodToBore', 'Rod over bore', '桿徑與缸徑比'),
]

W = 'hdfv.workpiece.'
WORKPIECES = [
    (W + 'tool_wrapper', 'Bucket wrapper plate', '挖斗包板'),
    (W + 'tool_side_plates', 'Side plates', '側板'),
    (W + 'tool_lip', 'Lip (cutting edge plate)', '斗唇（切削刃板）'),
    (W + 'tool_wear_liners', 'Wear liners and strips', '耐磨襯板與耐磨條'),
    (W + 'tool_bosses', 'Hinge ears, bosses and top bar', '鉸耳、銷座與頂樑'),
    (W + 'tool_head', 'Grab head', '抓斗頭'),
    (W + 'tool_teeth', 'Teeth and adapters', '斗齒與齒座'),
    (W + 'tool_moldboard', 'Moldboard', '鏟板'),
    (W + 'tool_ribs', 'Ribs and back box', '肋板與背箱'),
    (W + 'tool_cutting_edge', 'Cutting edge and end bits', '刀刃與端刃'),
    (W + 'tool_wings', 'Blade wings', '鏟刀側翼'),
    (W + 'tool_brackets', 'Trunnions and brackets', '耳軸與支架'),
    (W + 'tool_shanks', 'Ripper shank', '裂土器齒柄'),
    (W + 'tool_tips', 'Ripper tip', '裂土器齒尖'),
    (W + 'tool_beam', 'Ripper beam', '裂土器橫樑'),
    (W + 'tool_power_cell', 'Breaker power cell (piston and cylinder)', '破碎機動力單元（活塞與缸體）'),
    (W + 'tool_housing', 'Housing', '外殼'),
    (W + 'tool_chisel', 'Chisel', '鑿桿'),
    (W + 'tool_drop_weight', 'Pile hammer ram', '打樁錘錘體'),
    (W + 'tool_helmet', 'Pile helmet', '樁帽'),
    (W + 'tool_jaws', 'Grapple jaws', '抓具夾爪'),
    (W + 'tool_tines', 'Fork tines', '貨叉叉臂'),
    (W + 'tool_carriage', 'Fork carriage', '貨叉架'),
    (W + 'tool_frame', 'Frame', '框架'),
    (W + 'tool_lock', 'Coupler hooks and lock', '快速接頭掛鉤與鎖'),
    (W + 'tool_base_plate', 'Compactor base plate', '夯實機底板'),
    (W + 'tool_exciter', 'Exciter', '激振器'),
    (W + 'tool_mounts', 'Mounting bracket and isolators', '安裝座與避震塊'),
    (W + 'tool_floor', 'Floor', '底板'),
    (W + 'tool_apron', 'Scraper apron', '鏟運機斗門'),
    (W + 'tool_ejector', 'Scraper ejector', '鏟運機推板'),
    (W + 'tool_structure', 'Ribs, longitudinals and hinges', '肋板、縱樑與鉸鏈'),
    (W + 'tool_walls', 'Body walls', '車斗側板'),
    (W + 'tool_canopy', 'Body canopy', '車斗護頂'),
    (W + 'tool_belt', 'Conveyor belt', '輸送皮帶'),
    (W + 'tool_idlers', 'Idlers', '托輥'),
    (W + 'tool_pulleys', 'Head and tail pulleys', '頭尾滾筒'),
    (W + 'tool_hopper_shell', 'Hopper shell and wings', '料斗殼與側翼'),
    (W + 'tool_feeder', 'Bar feeder and push roller', '刮板輸料器與推輥'),
    (W + 'tool_screed_plate', 'Screed plate', '熨平底板'),
    (W + 'tool_screed_body', 'Screed body, tamper and heating', '熨平板本體、夯錘與加熱'),
    (W + 'tool_drum_tube', 'Drum tube', '轉鼓筒'),
    (W + 'tool_drum_ends', 'Drum end discs', '轉鼓端板'),
    (W + 'tool_picks', 'Picks and holders', '截齒與齒座'),
    (W + 'tool_hood', 'Drum hood', '轉鼓護罩'),
    (W + 'tool_gearbox', 'Cutter gearbox', '截割減速箱'),
    (W + 'tool_face', 'Cutterhead face plate', '刀盤面板'),
    (W + 'tool_rim', 'Rim', '外環'),
    (W + 'tool_back_structure', 'Cutterhead spokes and bulkhead', '刀盤輻條與隔板'),
    (W + 'tool_disc_cutters', 'Disc cutters', '盤形滾刀'),
    (W + 'tool_muck_buckets', 'Muck buckets and scrapers', '集土斗與刮刀'),
    (W + 'tool_buckets', 'Wheel buckets', '斗輪鏟斗'),
    (W + 'tool_hub', 'Hub and spokes', '輪轂與輻條'),
    (W + 'tool_boom', 'Trencher boom', '挖溝臂'),
    (W + 'tool_chain', 'Digging chain', '挖掘鏈條'),
    (W + 'tool_chain_teeth', 'Chain teeth', '鏈齒'),
    (W + 'tool_sprockets', 'Sprockets', '鏈輪'),
    (W + 'tool_fittings', 'Fittings', '配件'),
    (W + 'tool_auger_pipe', 'Auger pipe', '螺旋鑽管'),
    (W + 'tool_flights', 'Auger flights', '螺旋葉片'),
    (W + 'tool_bit', 'Auger bit and teeth', '鑽頭與鑽齒'),
    (W + 'tool_kelly_box', 'Kelly box', '鑽桿接頭'),
    (W + 'tool_broom_core', 'Broom core', '掃刷芯管'),
    (W + 'tool_bristles', 'Broom bristles', '刷毛'),
    (W + 'tool_auger', 'Snow blower auger', '拋雪機螺旋'),
    (W + 'tool_impeller', 'Impeller', '葉輪'),
    (W + 'tool_spray_pipe', 'Spray pipe', '噴灑管'),
    (W + 'tool_nozzles', 'Nozzles', '噴嘴'),
    (W + 'tool_drum_shell', 'Mixer drum shell', '攪拌筒殼'),
    (W + 'tool_mixing_blades', 'Mixing blades', '攪拌葉片'),
    (W + 'tool_drum_rings', 'Roller track and drive flange', '托輪軌道與驅動法蘭'),
    (W + 'tool_railing', 'Railing', '護欄'),
    (W + 'tool_rotator_mount', 'Rotator and mount', '旋轉器與安裝座'),
    (W + 'tool_gripper', 'Segment gripper', '環片夾持器'),
    (W + 'tool_ram', 'Hydraulic cylinder', '油壓缸'),
]

# proposed conditions of the tools (if damage.Conditions lists them; LinkPart has none of its own yet)
CONDITIONS = [
    ('hdfv.condition.teeth', 'Teeth', '斗齒'),
    ('hdfv.condition.cutting_edge', 'Cutting edge', '刀刃'),
    ('hdfv.condition.liners', 'Wear liners', '耐磨襯板'),
    ('hdfv.condition.picks', 'Picks', '截齒'),
    ('hdfv.condition.discs', 'Disc cutters', '盤形滾刀'),
]


def groups():
    """(anchor key, [(key, en, zh_tw)]) in the order they go in (anchors: machinery_lang_parts.py's last keys)."""
    return [
        ('hdfv.kind.hook', KINDS),
        ('hdfv.sample.crane_hook_40t', SAMPLES),
        ('hdfv.param.design.padDiameterM', PARAMS),
        ('hdfv.choice.design.circuit.pilot', CHOICES),
        ('hdfv.figure.padPressure', FIGURES),
        ('hdfv.derived.ropeLayers', DERIVED),
        ('hdfv.workpiece.outrigger_pad', WORKPIECES),
        ('hdfv.condition.tracks', CONDITIONS),
    ]


if __name__ == '__main__':
    n = sum(len(e) for _, e in groups())
    keys = [k for _, e in groups() for k, *_ in e]
    assert len(keys) == len(set(keys)), 'a key twice'
    print(n, 'keys in', len(groups()), 'groups')
