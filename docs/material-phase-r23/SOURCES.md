# 公开物性来源与采集规则（r23，只读准备）

访问日期：2026-10-24（用户时间 06:19 EDT 起）。仅请求公开化学 URL；未上传工程或调用外部模型。下列“已核”表示本轮 HTTP 正文实际读取，不表示完成物性模型选定或获得再分发许可。

## 实际访问与代表性核对

|ID|对象/来源（实际读取）|观察到的值与属性|压力、方法及局限|
|---|---|---|---|
|RSC-Fe|https://periodic-table.rsc.org/element/26/iron |20°C Solid；熔点1811 K，沸点3134 K|教育汇编；表格没有逐值压力、实验方法、不确定度。不得自动标记为某压力的实验值；生产模型仍缺潜热、Cp(T)、密度(T,P)与原文核实。|
|RSC-Hg|https://periodic-table.rsc.org/element/80/mercury |熔点234.321 K，沸点629.769 K；代表常温液体|表格缺逐值压力/方法，不与NIST不同来源值求平均。|
|RSC-N2|https://periodic-table.rsc.org/element/7/nitrogen |熔点63.2 K，沸点77.355 K；代表气体|必须使用N2物种而非N原子量当分子量；压力未在所读表格逐值列明。|
|RSC-As|https://periodic-table.rsc.org/element/33/arsenic |熔点、沸点两栏均写“Sublimes at 616°C, 889 K”|这是升华，不可把889 K同时编码成熔点和沸点。压力、晶型、气相分子组成未核，保持未建模。|
|RSC-Og|https://periodic-table.rsc.org/element/118/oganesson |熔点、沸点、密度、比热 Unknown；State at 20°C 栏写Solid|该状态栏不提供本轮可核的实验依据；不能据此宣称测得Og固体或填写热参数。数值保持null。|
|NIST-Fe|https://webbook.nist.gov/cgi/cbook.cgi?ID=C7439896&Mask=4 |Fe条目可读，但本次phase mask未返回相变表|并非证明铁无相变；改查凝聚相热化学/JANAF及原始文献。|
|NIST-Hg|https://webbook.nist.gov/cgi/cbook.cgi?ID=C7439976&Mask=4 |Tboil 629.81 K（Marsh 1987，TRC不确定度0.01 K）；Tfus 234.31 K（Marsh 1987，0.005 K）；另列多来源值|方法列N/A；未把编纂推荐值伪称本轮核实的直接实验。该行压力未列明，生产使用前核原文。Antoine区间298.14–749.99 K，P单位bar，不是Pa；须保原模型区间。|
|NIST-N2|https://webbook.nist.gov/cgi/cbook.cgi?ID=C7727379&Mask=4 |N2；Tboil77.34 K (Jacobsen等1986)；Tfus63.3 K (Streng1971，0.3K)；Ttriple63.14 K、Ptriple0.1252 bar (Jacobsen等1986)；Tc126.19K、Pc33.978bar|三相点和临界点不能替代常压熔沸点。0.1252bar=12520Pa，33.978bar=3397800Pa（精确单位换算）。相变焓表有6.1 kJ/mol@78K与5.6 kJ/mol@77K不同来源，未择优为生产值。|
|NIST-home|https://webbook.nist.gov/chemistry/ |SRD69；页面说明数据最后更新2025；https://doi.org/10.18434/T4D303 |公开WebBook和付费WTT/TDE不是同一可用性/许可。不能承诺118元素完整相图可下载。|

NIST-N2列出的可追溯原文：Jacobsen, Stewart, Jahangiri, *Thermodynamic Properties of Nitrogen from the Freezing Line to 2000 K at Pressures to 1000 MPa*, J. Phys. Chem. Ref. Data 15 (1986) 735–909；Giauque & Clayton 1933 https://doi.org/10.1021/ja01339a024；Edejer & Thodos 1967 https://doi.org/10.1021/je60033a014 。**只核WebBook的引用文本，未读取这些论文全文。** Hg: Hicks 1963 https://doi.org/10.1063/1.1733889 同样未核全文。

## 许可（实际读到的条款，不是法律许可结论）

- NIST https://www.nist.gov/open/license 明确 **SRD 受 Standard Reference Data Act 特别版权保护**。WebBook条目页明确All rights reserved、compilation copyright。不能将“.gov”或其他NIST员工资料的一般规则套用成整库公共领域。免费访问≠自由批量再分发；上线数据包前逐数据产品核许可/联系许可方，或选有明确许可的原始数据，保留来源与版本。
- RSC上述元素页附Terms & Conditions：图片归Murray Robertson/RSC许可；页面文字限制进一步复制、修改、分发等。当前仅作少量事实对照和索引，不复制图片/整站数据库进mod。公开可读不代表整表可打包。生产数据许可仍待明确。
- 118矩阵中除以上5元素外的RSC URL为**候选索引、未访问未核值**。不是118份来源已核，更不是118份实测表。
- NIST首次Python urllib请求Fe为HTTP403；改用PowerShell Invoke-WebRequest后公开页面可达（未绕过登录、未使用凭据）。一次命令引号解析失败未发出有效请求；后续按URL有界读取成功。

## 完整采集清单（每个元素/每个指定物种均须执行）

1. 元素ID与物种化学式、晶型/同素异形体、同位素组成；不要把元素原子量来源当相变来源。
2. 逐值记录T(K)、P(Pa)、原单位及精确换算、Cp(J/mol/K)、潜热(J/mol)、密度(kg/m³)及有效T/P区间；未知=null，不允许0或猜测填补。
3. 数值类型：MEASURED / EVALUATED / PREDICTED / GAME_CONVENTION / UNKNOWN；正文未标识则UNVERIFIED，不能因数值熟悉就写MEASURED。
4. 原文作者、年份、DOI/URL、表/行、方法、不确定度、物种与压力、访问日期、再分发许可状态。推荐值和预测值分表，冲突保留不平均。
5. 升华、三相点、压力熔化（例如He需另查）、磁性/晶型转变、P/S/Se等同素异形体为独立模型；超重元素不补编热物性。没有可靠数据仍提供隔离样品/容器形态，但显示UNKNOWN，禁止温度推演。
6. 默认模拟域可以选择近1 atm，**这是游戏模型范围选择，不是文献数据的压力证明**；不得将未核压力数据直接发布成可计算模型。
7. 等离子/超临界在当前候选中UNSUPPORTED，枚举PLASMA存在不等于模型存在；不扩大本任务实现要求。

完整118行缺口见 `coverage-118.csv` 和 `coverage-118.json`；全部 production_model_ready=false。数字对照只集中在本文件，未核采集字段保持空缺。
