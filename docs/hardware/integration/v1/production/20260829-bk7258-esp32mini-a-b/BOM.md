# 银龄智护 V1｜BK7258 + ESP32-S3-MINI-1U 双主控 A+B BOM

> 依据当前 A-ESP/A-BK 原理图、8Pin A/B 冻结架构与 2026-08-29 生产 Gerber 包整理。生产 ZIP 不含 BOM/CPL，因此未在源工程锁定的 MPN/LCSC 编号均标记为 TBD/VERIFY。

|板位|变体|类别|RefDes|器件/功能|规格/数值|MPN|LCSC|ESP数量|BK数量|状态|备注|
|---|---|---|---|---|---|---|---|---:|---:|---|---|
|A|A-BK|Capacitor|C36|BK 3.3V local bulk|10uF|Generic X5R/X7R||0|1|CONFIRMED||
|A|A-BK|Capacitor|C33,C37,C38,C42|BK internal/local rails|1uF|Generic X5R/X7R||0|4|CONFIRMED||
|A|A-BK|Capacitor|C39,C43,C30,C31|BK local decoupling|100nF|Generic X5R/X7R||0|4|CONFIRMED||
|A|A-BK|Capacitor|C44,C45|Crystal load capacitors|10pF|C0G/NP0||0|2|CONFIRMED||
|A|A-BK|Capacitor|C40,C41|VCCPLL/VCCPAD bypass|2.2uF|Generic X5R/X7R||0|2|CONFIRMED||
|A|A-BK|Capacitor|C32,C34,C35|VDDA/VDDD/VIO bulk|4.7uF|Generic X5R/X7R||0|3|CONFIRMED||
|A|A-BK|Crystal|X1|26MHz crystal|26MHz ±10ppm CL=12pF|CF4026M00012T8188052|C709163|0|1|CONFIRMED||
|A|A-BK|Debug|TP_BK|BK recovery interface|3V3/GND/CEN/UART_TX/UART_RX|5-pin header/test pads||0|1|CONFIRMED_FUNCTION||
|A|A-BK|Inductor|L2,L3|BK internal buck inductors|4.7uH; Isat >=200mA, DCR <=500mΩ|Generic shielded||0|2|SPEC_CONFIRMED_MPN_TBD||
|A|A-BK|RF Antenna|ANT|External 2.4GHz antenna|50Ω FPC antenna|2.4GHz FPC antenna||0|1|MECHANICAL_TBD||
|A|A-BK|RF Capacitor|C48|RF shunt capacitor|1.2pF|C0G/NP0||0|1|CONFIRMED||
|A|A-BK|RF Capacitor|C49|RF shunt capacitor|1.0pF|C0G/NP0||0|1|CONFIRMED||
|A|A-BK|RF Capacitor|C50|RF shunt capacitor|0.4pF|C0G/NP0||0|1|CONFIRMED||
|A|A-BK|RF Connector|JP1|2.4GHz antenna connector|U.FL/IPEX class, 50Ω|U.FL/IPEX||0|1|FOOTPRINT_VERIFY||
|A|A-BK|RF Inductor|L4|RF matching inductor|3.3nH|High-Q RF inductor||0|1|VALUE_FROM_CURRENT_SCHEMATIC||
|A|A-BK|Resistor|R15|CEN pullup|10kΩ|Generic 1%||0|1|CONFIRMED||
|A|A-BK|Resistor|R17|RF series jumper|0Ω|RF-compatible 0Ω||0|1|CONFIRMED||
|A|A-BK|Resistor|R16|VCCPLL isolation|3.9Ω|Generic 1%||0|1|CONFIRMED||
|A|A-BK|SoC|U1|Main SoC|BK7258 QFN88; supplier 8MB Flash + 16MB PSRAM candidate|BK7258QN88616|C53049810|0|1|ORDER_CODE_VERIFY||
|A|A-ESP|Capacitor|C27|EN RC capacitor|1uF|Generic X5R/X7R||1|0|CONFIRMED||
|A|A-ESP|Capacitor|C28|ESP local bulk|10uF|Generic X5R/X7R||1|0|CONFIRMED||
|A|A-ESP|Capacitor|C30|ESP local decoupling|100nF|Generic X5R/X7R||1|0|CONFIRMED||
|A|A-ESP|Capacitor|C_USB_DP,C_USB_DM|USB tuning capacitors|DNP / NC|0402 footprint only||2|0|OPTIONAL_DNP||
|A|A-ESP|Debug|TP_ESP|ESP recovery interface|3V3/GND/EN/BOOT/UART_TX/UART_RX|6-pin header/test pads||1|0|CONFIRMED_FUNCTION||
|A|A-ESP|MCU Module|ESP1|Main MCU module|4MB Flash + 2MB PSRAM|ESP32-S3-MINI-1U-N4R2|C22356044|1|0|CONFIRMED||
|A|A-ESP|Resistor|R_CAM_RST,R_CAM_PWDN|Camera RESET/PWDN pullups to 2.8V|10kΩ|Generic 1%||2|0|FINAL_LOGIC|GPIO16/17固件配置为Open-Drain。|
|A|A-ESP|Resistor|R15,R16|EN and BOOT pullups|10kΩ|Generic 1%||2|0|CONFIRMED||
|A|A-ESP|Resistor|R_AMP_SD_SER|MAX98357A SD_MODE current limiting|~2kΩ|Generic 1%||1|0|RECOMMENDED_VERIFY_IN_SOURCE|最终源工程确认是否已加入。|
|A|A-ESP|Resistor|R_USB_DP,R_USB_DM|USB series resistors|22Ω|Generic 1%||2|0|LATEST_AGREED||
|A|COMMON|5V Protection|D2|5V TVS|5V bidirectional TVS|D5V0L1B2WS-7|C182013|1|1|CONFIRMED||
|A|COMMON|5V Protection|F1|Resettable fuse|Hold 0.75A / Trip ~1.5A|SMD1206-075-16||1|1|MPN_CONFIRMED_CNUMBER_TBD||
|A|COMMON|Audio Amp|U11|Mono I2S Class-D amp|2.5-5.5V; BTL output|MAX98357AETE+T|C910544|1|1|CONFIRMED||
|A|COMMON|Camera Connector|FPC1|OV5640 24Pin FPC connector|24P / 0.5mm|FH34SRJ-24S-0.5SH(50)|C324726|1|1|CONFIRMED||
|A|COMMON|Capacitor|C1,C20,C21,C22,C23,C25,C26,C29,C47|Ceramic capacitors|100nF|Generic X5R/X7R||9|9|VALUE_CONFIRMED||
|A|COMMON|Capacitor|C7,C8,C9,C10,C11,C16,C17,C18,C19|Ceramic capacitors|1uF|Generic X5R/X7R||9|9|VALUE_CONFIRMED||
|A|COMMON|Capacitor|C12,C13,C14,C15,C24|Ceramic capacitors|4.7uF|Generic X5R/X7R||5|5|VALUE_CONFIRMED||
|A|COMMON|Capacitor|C2,C3,C46|Ceramic capacitors|10uF|Generic X5R/X7R||3|3|VALUE_CONFIRMED||
|A|COMMON|Capacitor|C4,C5,C6|TPS63021 output capacitors|22uF|Generic X5R/X7R||3|3|VALUE_CONFIRMED||
|A|COMMON|Debug|TP/DBG|Recovery/test access|GND/3V3/UART + reset/boot as variant|Test pads/header||1|1|IMPLEMENTATION_DEFINED||
|A|COMMON|External Interface|J1|4-pin magnetic/USB connector|5V/GND/D+/D-|Magnetic 4Pin||1|1|MECHANICAL_TBD||
|A|COMMON|Haptic Driver|U8,U9|Dual LRA drivers|I2C 0x5A|DRV2605LDGSR||2|2|MPN_CONFIRMED_CNUMBER_TBD||
|A|COMMON|I2C Mux|U7|2-channel I2C mux|0x70|PCA9540BDP,118|C2652901|1|1|CONFIRMED||
|A|COMMON|IMU|U6|6-axis IMU|I2C + INT1|BMI270|C2836813|1|1|CONFIRMED||
|A|COMMON|Inter-board Connector|FPC2|A-side 8Pin FPC connector|8P / 0.5mm|FH34SRJ-8S-0.5SH(50)|C88372|1|1|CONFIRMED||
|A|COMMON|LDO|U4|Camera 2.8V LDO|2.8V / 1A|TLV75728PDBVR|C2863639|1|1|CONFIRMED||
|A|COMMON|LDO|U5|Camera core LDO|1.5V / 500mA|TLV75515PDBVR|C2867152|1|1|CONFIRMED||
|A|COMMON|Local Bone interface|J3|Bone-A cable/pad|2-pin BTL|2-pin solder/connector||1|1|MECHANICAL_TBD||
|A|COMMON|Local LRA interface|J2|LRA-A cable/pad|2-pin|2-pin solder/connector||1|1|MECHANICAL_TBD||
|A|COMMON|Microphone|U10|Digital I2S MEMS microphone|1.65-3.63V|ICS-43434|C5656610|1|1|CONFIRMED||
|A|COMMON|Power IC|U3|3.3V buck-boost|Fixed 3.3V|TPS63021DSJR|C202140|1|1|CONFIRMED||
|A|COMMON|Power IC|U2|Li-ion charger + PowerPath|1S Li-ion, PowerPath|BQ24074RGTR|C54313|1|1|CONFIRMED||
|A|COMMON|Power Inductor|L_SYS|TPS63021 inductor|1.5uH, Isat >=5A preferred|FAUL0420-1R5MT|C3040382|1|1|USER_CONFIRMED|按用户最终选择；Gerber本身不携带元件数值，贴片前与最终源工程核一次。|
|A|COMMON|Resistor|R11,R12|AMP_SD/MIC_DATA pulldowns|100kΩ|Generic 1%||2|2|VALUE_CONFIRMED||
|A|COMMON|Resistor|R2|BQ24074 ILIM|1.5kΩ|Generic 1%||1|1|VALUE_CONFIRMED||
|A|COMMON|Resistor|R1|BQ24074 ISET|1.8kΩ|Generic 1%||1|1|VALUE_CONFIRMED||
|A|COMMON|Resistor|R3|BQ24074 TMR|46.4kΩ|Generic 1%||1|1|VALUE_CONFIRMED||
|A|COMMON|Resistor|R7,R8,R9,R10,R13,R14|I2C/SCCB pullups|2.2kΩ|Generic 1%||6|6|VALUE_CONFIRMED||
|A|COMMON|Resistor|R18,R19|Sensor I2C upstream pullups|4.7kΩ|Generic 1%||2|2|VALUE_CONFIRMED||
|A|COMMON|Resistor|R4,R5,R6|TS + CHG/PGOOD pullups|10kΩ|Generic 1%||3|3|VALUE_CONFIRMED||
|A|COMMON|USB Protection|D1|USB D+/D- ESD array|USB 2.0 low-capacitance|USBLC6-2SC6||1|1|MPN_CONFIRMED_CNUMBER_VERIFY||
|B|COMMON|Battery Interface|J_BAT_B|1S LiPo local connector/pads|BAT+/GND|Battery connector or solder pads||1|1|MECHANICAL_TBD||
|B|COMMON|Bone Interface|J_BONE_B|Bone-B local interface|SPK+/SPK-|2-pin solder/connector||1|1|MECHANICAL_TBD||
|B|COMMON|Inter-board Connector|FPC4/J_INTER_B|B-side 8Pin FPC connector|8P / 0.5mm|FH34SRJ-8S-0.5SH(50)|C88372|1|1|CONFIRMED||
|B|COMMON|LRA Interface|J_LRA_B|LRA-B local interface|LRA_B+/LRA_B-|2-pin solder/connector||1|1|MECHANICAL_TBD||
|SYSTEM|A-ESP|RF Antenna|ANT_ESP|External 2.4GHz antenna for MINI-1U|Compatible with module RF connector|2.4GHz antenna||1|0|MECHANICAL_TBD||
|SYSTEM|COMMON|Battery|BAT1|Main battery|1S LiPo, protected pack preferred|1S LiPo||1|1|CAPACITY_TBD||
|SYSTEM|COMMON|Bone Transducer|BONE_A,BONE_B|Bone conduction units|8Ω, approx 1-1.5W each|8Ω bone transducer||2|2|CONFIRMED_TYPE||
|SYSTEM|COMMON|Cable|FPC_AB|Inter-temple cable|8 conductors; 1-2 BAT+, 3-4 GND, 5-6 SPK±, 7-8 LRA-B±|8-conductor FPC/FFC||1|1|LENGTH_CURRENT_TBD||
|SYSTEM|COMMON|Camera|CAM1|First-person camera module|OV5640 DVP, 24Pin|OV5640 DVP module||1|1|EXISTING_MODULE||
|SYSTEM|COMMON|Haptic|LRA_A,LRA_B|0809 X-axis LRA|2 units per set|0809 X-axis LRA||2|2|CONFIRMED_TYPE||
|SYSTEM|COMMON|Magnetic Cable|MAG_CABLE|Mating magnetic power/USB cable|5V/GND/D+/D-|4Pin magnetic mating cable||1|1|MECHANICAL_TBD||
