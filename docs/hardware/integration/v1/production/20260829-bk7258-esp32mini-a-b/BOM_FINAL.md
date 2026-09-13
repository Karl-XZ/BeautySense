# 银龄智护 V1｜BK7258 + ESP32-S3-MINI-1U A+B 最终采购 BOM

> 基于 2026-08-29 生产 Gerber 包、当前冻结 A/B 8Pin 架构及本轮对话最终确认项整理。Gerber ZIP 本身不含 BOM/CPL；无法从 Gerber 唯一恢复数值/MPN 的项目均显式标记为 VERIFY/TBD。

- 生产包：`BK7258_ESP32MINI_A+B板.zip`
- SHA256：`b834a71457935364cc6fce3904cf0fb06f7b8a92acd41b15d06b30a908ea1a39`
- A-ESP 与 A-BK 共用绝大多数外围；B 为公共远端板。
- 采购前重点复核：ESP/BK TPS63021 电感版本、BK RF L4 最终值、磁吸接口、8Pin FPC 机械方向、电池容量/尺寸。

|板位|变体|类别|RefDes|器件/功能|规格/数值|MPN|LCSC|ESP数量|BK数量|状态|备注|
|---|---|---|---|---|---|---|---|---:|---:|---|---|
|A|COMMON|Power IC|U2|Li-ion charger + PowerPath|1S Li-ion, PowerPath|BQ24074RGTR|C54313|1|1|CONFIRMED|A板充电/PowerPath|
|A|COMMON|Power IC|U3|3.3V buck-boost|Fixed 3.3V|TPS63021DSJR|C202140|1|1|CONFIRMED|SYS_PWR→3.3V|
|A|A-ESP|Power Inductor|L_SYS|TPS63021 inductor|1.0uH; >=6A class|FAUL0420-1R0MT|C3040380|1|0|VERIFY_SOURCE|Latest ESP source was observed with 1R0; TI supports 1uH + 3×22uF. Verify final source before assembly.|
|A|A-BK|Power Inductor|L_SYS|TPS63021 inductor|1.5uH; ~5A class|FAUL0420-1R5MT|C3040382|0|1|USER_CONFIRMED|User explicitly selected this part for BK version.|
|A|COMMON|LDO|U4|Camera 2.8V LDO|2.8V / 1A|TLV75728PDBVR|C2863639|1|1|CONFIRMED||
|A|COMMON|LDO|U5|Camera core LDO|1.5V / 500mA|TLV75515PDBVR|C2867152|1|1|CONFIRMED||
|A|COMMON|IMU|U6|6-axis IMU|I2C + INT1|BMI270|C2836813|1|1|CONFIRMED||
|A|COMMON|I2C Mux|U7|2-channel I2C mux|0x70|PCA9540BDP,118|C2652901|1|1|CONFIRMED|Separates two DRV2605L @0x5A|
|A|COMMON|Haptic Driver|U8,U9|Dual LRA drivers|I2C 0x5A|DRV2605LDGSR||2|2|MPN_CONFIRMED_CNUMBER_TBD||
|A|COMMON|Microphone|U10|Digital I2S MEMS microphone|1.65–3.63V|ICS-43434|C5656610|1|1|CONFIRMED||
|A|COMMON|Audio Amp|U11|Mono I2S Class-D amplifier|2.5–5.5V; BTL|MAX98357AETE+T|C910544|1|1|CONFIRMED|Drives two 8Ω bone units in parallel (~4Ω)|
|A|COMMON|USB Protection|D1|USB D+/D- ESD array|USB 2.0 low-capacitance|USBLC6-2SC6||1|1|MPN_CONFIRMED_CNUMBER_VERIFY||
|A|COMMON|5V Protection|D2|5V TVS|5V bidirectional TVS|D5V0L1B2WS-7|C182013|1|1|CONFIRMED||
|A|COMMON|5V Protection|F1|Resettable fuse|Hold 0.75A / Trip ~1.5A|SMD1206-075-16||1|1|MPN_CONFIRMED_CNUMBER_TBD||
|A|COMMON|Camera Connector|FPC1|OV5640 24Pin FPC connector|24P / 0.5mm|FH34SRJ-24S-0.5SH(50)|C324726|1|1|CONFIRMED||
|A|COMMON|Inter-board Connector|FPC2|A-side 8Pin FPC connector|8P / 0.5mm|FH34SRJ-8S-0.5SH(50)|C88372|1|1|VERIFY_CNUMBER|Pin map: BAT+,BAT+,GND,GND,SPK+,SPK-,LRA_B+,LRA_B-|
|A|COMMON|External Interface|J1|4-pin magnetic/USB connector|5V/GND/D+/D-|Magnetic 4Pin||1|1|MECHANICAL_TBD|Electrical definition frozen; mechanical part still TBD.|
|A|COMMON|Local LRA Interface|J2|LRA-A cable/pads|2-pin|2-pin solder/connector||1|1|MECHANICAL_TBD||
|A|COMMON|Local Bone Interface|J3|Bone-A cable/pads|2-pin BTL|2-pin solder/connector||1|1|MECHANICAL_TBD||
|A|COMMON|Capacitor|C1,C20,C21,C22,C23,C25,C26,C29,C47|Decoupling|100nF|X5R/X7R ceramic||9|9|VALUE_CONFIRMED|Use footprint from final source project.|
|A|COMMON|Capacitor|C7,C8,C9,C10,C11,C16,C17,C18,C19|Decoupling / local bypass|1uF|X5R/X7R ceramic||9|9|VALUE_CONFIRMED||
|A|COMMON|Capacitor|C12,C13,C14,C15,C24|Bulk/local bypass|4.7uF|X5R/X7R ceramic||5|5|VALUE_CONFIRMED||
|A|COMMON|Capacitor|C2,C3,C46|Bulk capacitors|10uF|X5R/X7R ceramic||3|3|VERIFY_SOURCE|C2/C3 were historically both input caps; compact version may remove one. Verify final schematic/BOM export.|
|A|COMMON|Capacitor|C4,C5,C6|TPS63021 output capacitors|22uF|X5R/X7R ceramic||3|3|CONFIRMED|TI-supported 3×22uF output network.|
|A|COMMON|Resistor|R1|BQ24074 ISET|1.8kΩ|1% resistor||1|1|VALUE_CONFIRMED||
|A|COMMON|Resistor|R2|BQ24074 ILIM|1.5kΩ|1% resistor||1|1|VALUE_CONFIRMED||
|A|COMMON|Resistor|R3|BQ24074 TMR|46.4kΩ|1% resistor||1|1|VALUE_CONFIRMED||
|A|COMMON|Resistor|R4,R5,R6|TS + CHG/PGOOD pullups|10kΩ|1% resistor||3|3|VALUE_CONFIRMED||
|A|COMMON|Resistor|R7,R8,R9,R10,R13,R14|I2C/SCCB pullups|2.2kΩ|1% resistor||6|6|VALUE_CONFIRMED||
|A|COMMON|Resistor|R11,R12|AMP_SD / MIC_DATA pulldowns|100kΩ|1% resistor||2|2|VALUE_CONFIRMED||
|A|COMMON|Resistor|R18,R19|Sensor I2C upstream pullups|4.7kΩ|1% resistor||2|2|VALUE_CONFIRMED||
|A|A-ESP|MCU Module|ESP1|Main MCU module|4MB Flash + 2MB PSRAM|ESP32-S3-MINI-1U-N4R2|C22356044|1|0|CONFIRMED||
|A|A-ESP|Capacitor|C27|EN RC capacitor|1uF|X5R/X7R ceramic||1|0|CONFIRMED||
|A|A-ESP|Capacitor|C28|ESP local bulk|10uF|X5R/X7R ceramic||1|0|CONFIRMED||
|A|A-ESP|Capacitor|C30|ESP local decoupling|100nF|X5R/X7R ceramic||1|0|CONFIRMED||
|A|A-ESP|Resistor|R15,R16|EN and BOOT pullups|10kΩ|1% resistor||2|0|CONFIRMED||
|A|A-ESP|Resistor|R_CAM_RST,R_CAM_PWDN|Camera RESET/PWDN pullups to 2.8V|10kΩ|1% resistor||2|0|FINAL_LOGIC|GPIO16/17 firmware must use Open-Drain.|
|A|A-ESP|Resistor|R_USB_DP,R_USB_DM|USB series resistors|22Ω|1% resistor||2|0|LATEST_AGREED|Near ESP module.|
|A|A-ESP|Capacitor|C_USB_DP,C_USB_DM|USB tuning capacitors|DNP / NC|0402 footprint only||2|0|OPTIONAL_DNP|Do not populate first build.|
|A|A-ESP|Resistor|R_AMP_SD_SER|MAX98357A SD_MODE current limiting|~2kΩ|1% resistor||1|0|VERIFY_SOURCE|Recommended for 3.3V GPIO when amp VDD can fall below 3.0V; confirm added in final source.|
|A|A-ESP|Debug|TP_ESP|Recovery/test interface|3V3/GND/EN/BOOT/UART_TX/UART_RX|Test pads/header||1|0|CONFIRMED_FUNCTION||
|SYSTEM|A-ESP|RF Antenna|ANT_ESP|External 2.4GHz antenna|Compatible with MINI-1U module RF connector|2.4GHz antenna||1|0|MECHANICAL_TBD||
|A|A-BK|SoC|U1|Main SoC|BK7258 QFN88; supplier 8MB Flash + 16MB PSRAM candidate|BK7258QN88616|C53049810|0|1|ORDER_CODE_VERIFY|Exact order code/flash/PSRAM/package grade still supplier-verification item.|
|A|A-BK|Crystal|X1|26MHz crystal|26MHz ±10ppm CL=12pF|CF4026M00012T8188052|C709163|0|1|CONFIRMED||
|A|A-BK|Capacitor|C44,C45|Crystal load capacitors|10pF|C0G/NP0||0|2|CONFIRMED||
|A|A-BK|Inductor|L2,L3|BK internal buck inductors|4.7uH; Isat >=200mA, DCR <=500mΩ|Shielded power inductor||0|2|SPEC_CONFIRMED_MPN_TBD||
|A|A-BK|Capacitor|C32,C34,C35|VDDA/VDDD/VIO bulk|4.7uF|X5R/X7R ceramic||0|3|CONFIRMED||
|A|A-BK|Capacitor|C33,C37,C38,C42|BK internal/local rails|1uF|X5R/X7R ceramic||0|4|CONFIRMED||
|A|A-BK|Capacitor|C36|BK VBAT/local bulk|10uF|X5R/X7R ceramic||0|1|CONFIRMED||
|A|A-BK|Capacitor|C39,C43,C30,C31|BK local decoupling|100nF|X5R/X7R ceramic||0|4|CONFIRMED||
|A|A-BK|Capacitor|C40,C41|VCCPLL/VCCPAD bypass|2.2uF|X5R/X7R ceramic||0|2|CONFIRMED||
|A|A-BK|Resistor|R15|CEN pullup|10kΩ|1% resistor||0|1|CONFIRMED||
|A|A-BK|Resistor|R16|VCCPLL isolation|3.9Ω|1% resistor||0|1|CONFIRMED||
|A|A-BK|RF Capacitor|C48|RF shunt capacitor|1.2pF|C0G/NP0||0|1|CONFIRMED||
|A|A-BK|RF Inductor|L4|RF matching inductor|3.0–3.3nH|High-Q RF inductor||0|1|VERIFY_VALUE|Conversation froze 3.0nH; repo draft BOM recorded 3.3nH. Check final source schematic before assembly.|
|A|A-BK|RF Capacitor|C49|RF shunt capacitor|1.0pF|C0G/NP0||0|1|CONFIRMED||
|A|A-BK|Resistor|R17|RF series jumper|0Ω|RF-compatible 0Ω||0|1|CONFIRMED||
|A|A-BK|RF Capacitor|C50|RF shunt capacitor|0.4pF|C0G/NP0||0|1|CONFIRMED||
|A|A-BK|RF Connector|JP1|2.4GHz antenna connector|U.FL/IPEX class, 50Ω|U.FL-R-SMT-1(10)|C88373|0|1|FOOTPRINT_VERIFY|Recommended part; confirm final footprint is compatible.|
|SYSTEM|A-BK|RF Antenna|ANT_BK|External 2.4GHz FPC antenna|50Ω, U.FL/IPEX|AIWP007|C55084683|0|1|RECOMMENDED|47×6.8mm candidate from prior selection.|
|A|A-BK|Debug|TP_BK|BK recovery interface|3V3/GND/CEN/UART_TX/UART_RX|Test pads/header||0|1|CONFIRMED_FUNCTION||
|B|COMMON|Inter-board Connector|FPC4/J_INTER_B|B-side 8Pin FPC connector|8P / 0.5mm|FH34SRJ-8S-0.5SH(50)|C88372|1|1|VERIFY_CNUMBER|Same conductor order as A-side.|
|B|COMMON|Battery Interface|J_BAT_B|1S LiPo local interface|BAT+/GND|Solder pads or battery connector||1|1|MECHANICAL_TBD||
|B|COMMON|Bone Interface|J_BONE_B|Bone-B local interface|SPK+/SPK-|2-pin solder/connector||1|1|MECHANICAL_TBD||
|B|COMMON|LRA Interface|J_LRA_B|LRA-B local interface|LRA_B+/LRA_B-|2-pin solder/connector||1|1|MECHANICAL_TBD||
|B|COMMON|Capacitor|C_BAT_B|Optional battery-side bulk|47uF DNP/optional|X5R/X7R or low-profile electrolytic||1|1|OPTIONAL|Only if included in final B source/layout.|
|SYSTEM|COMMON|Battery|BAT1|Main battery|1S LiPo; protected pack preferred|1S LiPo protected pack||1|1|CAPACITY_TBD|Capacity/mechanical size remains to be frozen.|
|SYSTEM|COMMON|Camera|CAM1|First-person camera module|OV5640 DVP, 24Pin|OV5640 DVP module||1|1|EXISTING_MODULE||
|SYSTEM|COMMON|Bone Transducer|BONE_A,BONE_B|Bone conduction units|8Ω, approx 1–1.5W each|8Ω bone transducer||2|2|CONFIRMED_TYPE||
|SYSTEM|COMMON|Haptic|LRA_A,LRA_B|0809 X-axis LRA|2 units per set|0809 X-axis LRA||2|2|CONFIRMED_TYPE||
|SYSTEM|COMMON|Cable|FPC_AB|Inter-temple cable|8 conductors, 0.5mm system-compatible|8-conductor FPC/FFC||1|1|LENGTH_CURRENT_TBD|Pins 1-2 BAT+, 3-4 GND, 5-6 SPK±, 7-8 LRA-B±.|
|SYSTEM|COMMON|Magnetic Cable|MAG_CABLE|Mating magnetic power/USB cable|5V/GND/D+/D-|4Pin magnetic mating cable||1|1|MECHANICAL_TBD||
