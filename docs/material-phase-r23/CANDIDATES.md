# 候选配置/签名补充（文本，不应用补丁）

## 静态tag示例

未来路径 `src/main/resources/data/chemicalelements/tags/item/material_carriers.json`，由D实施并核真实注册ID：
```json
{"replace":false,"values":["chemicalelements:material_sample","chemicalelements:element_hydrogen"]}
```
仅示意两个已注册ID，完整118列表由ElementItems.NAMES生成，避免手写错漏；不是可替代逐实例组成的tag。不能用tag表示某一堆的70%Cu/30%Zn或300K。固体、液罐、气罐分类标识载体能力，不代表内容当前相态。

## 行为定义候选（不是现有资源）

`R/material_behaviors/sealed_material.json` 建议schema：schema_version=1，carrier_item_tag为ResourceLocation，allowed_model_status数组，capacity={mol_limit:string, volume_l:string|null, pressure_limit_pa:string|null, geometry_basis:string}，unknown_policy=REJECT_TRANSFORM_KEEP_CONTENTS，phase_family=PURE_ONLY|DILUTE_WATER|UNMODELED。
校验：容量是游戏约定且必须明确，不在此编造容器容量数值；物理密度/EOS缺失即拒绝需要体积换算的操作。额定压力不能来自客户端。标准Minecraft tag JSON保持原生schema，不往tag里塞这些自定义字段。

## 与phase-model.candidate.schema.json匹配的未知模型示例

```json
{
  "schema_version":1,
  "id":"chemicalelements:oganesson_unknown",
  "status":"UNKNOWN",
  "subject":{"species":"chemicalelements:oganesson","allotrope":null},
  "pure_only":true,
  "basis":"UNKNOWN",
  "sources":[],
  "domain":null,
  "transitions":[],
  "heat_capacity":[],
  "density":[],
  "energy_reference":null,
  "kinetics":"EQUILIBRIUM_NO_SUPERCOOLING"
}
```
这只是指向旧记账species的“未支持”描述，绝不把Og的旧solid字段升级为实测固体证据。UNKNOWN不能生成PhasePlan READY。

## Schema外必须由A/C校验的语义

- decimal解析BigDecimal：有限字符和精度预算；K>0、Pa>0、Cp>0、密度>0；min≤max，不允许0充未知。
- from/to均存在、中性纯物种、相变化学式与电荷相同，且质量/核素基准一致；同位素质量近似误差另列，不能只验总mol。
- 来源ID唯一且可解析；每个数值的pressure_evidence、method、value_basis缺失则禁止标MEASURED；生产打包需license_status=CLEARED并保声明。
- MODELED只是数据有效状态不是生产启用开关；只有域内、source充分、热预算/容量/Owner验证通过才能READY。PREDICTED必须显式预测标识，不能偷偷替代实测。
- 正向/反向latent符号按from→to能量变化定义；零潜热不作为未知。重复/重叠模型域无优先规则时拒绝，不能依文件顺序择一。
- 当前候选pure_only固定true。现有DiluteWaterPhase保持独立有域模型；将来混合模型独立schema明确组分、浓度/活度/相分配，不放开pure_only后套同一函数。
- 密度为空不能计算几何容量；可接受仅摩尔受限、明示无物理体积模拟的封存展示，但不能以此通过真实液气容积验收。
- heat_capacity示例仅常数窄域模型；加入分段Cp多项式时另加精确定义/连续性规则，不静默外推。
- 候选没有实现EOS、等离子或超临界；域外UNMODELED。相份额用逐species quanta的分配输出，聚合fraction仅显示，不能作为质量账本。

本目录JSON只做语法读取与结构准备，未安装JSON Schema验证器、未声称Java parser或数据包已接受；最终由A实现严格parse/cross-reference并与C的单元/集成测试同版验证。
