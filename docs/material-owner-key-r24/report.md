# material-owner-key-r24-v1

任务：chem-material-owner-key-r24。范围：仅原 OwnerKey.java、两份专属新增测试、此 docs 目录。

## 实施

在原 sealed OwnerKey 内增加：

```java
record WorldBlock(ResourceLocation dimension, long blockPos, UUID incarnation) implements OwnerKey
record FiniteHeatSource(ResourceLocation dimension, long blockPos, UUID incarnation,
                        ResourceLocation port) implements OwnerKey
```

permits 列出原两种及新两种 variant；所有新引用字段通过 Objects.requireNonNull 校验。long 是既有打包块位置值，不附加非负假设。PlayerInventory 与 MachineInventory 原 record 源码块逐字保留（统一行尾后的静态比对见 static-evidence.json）。

写前 OwnerKey SHA256 实读匹配 A3CD1BBE496D577FA175217F4864E92460030768CA35560B2E404AEEFF409291。完整读取 A 释放板 chem-a-ownerkey-material-release-r24 rev1 和 C 身份/释放板 chem-c-heat-owner-release-r24 rev1。software-development 已激活并实读；项目中上下文文件发现检查未找到 AGENTS.md/CLAUDE.md，已实读 gradle.properties、build.gradle 相关 Java21/sourceSet/GameTest 部分及现有测试风格。

## 验证状态

- **源码已写**：值类型、standalone main 断言、2 个 GameTest 方法。
- **静态文本/hash 检查**：执行同目录 static-check.ps1；具体逐项布尔值、SHA、13 条暂存路径及 index.lock 状态见 static-evidence.json。该脚本不是 Java 编译器或测试运行器。
- **编译未运行**：未运行 Gradle、javac。
- **测试未运行**：未运行 Java main 或 GameTest。
- **实机未验证**：未启动 client、未截图。

测试源码覆盖新 record 逐字段等值/差异、相等 hash、四种 variant 两两区分、新5+旧4引用 null 拒绝；固定 UUID 仅测试 fixture，不是生产持久身份生成。GameTest 沿现有 core_empty 模板，方法 materialIdentityValues 与 materialIdentityRejectsNull；standalone 入口 org.goldgom.chemicalelements.api.tx.MaterialOwnerKeyTest.main。未增加 Gradle task 或修改现有测试。

## 契约与安全

这是值身份，不含 session/revision/live generation，不生成任何随机 incarnation。持久 incarnation 由真实权威块/BE 生命周期供应；port 是服务器认可的稳定端口标识，不是热预算。非 block 不伪造块位置。真实 adapter 必须将同 material/world/machine 物理根的热端口别名归一到同一 Owner/锁，record 相异不证明独立储库或 CAS。

B 负责新 codec 显式处理新 variant。本任务未改 B codec/journal，旧 schema1/2 和已有 schema3/hash 不改。没有修改 content/world、映射、热库、固定2693/classes/resources、正式 mods 或存档；没有 git add/reset/commit，没有删除文件或外发。

值类型落盘不等于真实 Owner、联合 CAS、材料放置/拾取/相变、水生产就绪；安全门保持关闭。

## 交接

精确签名及三源码 SHA 已即时发布 chem-material-owner-key-implemented-r24 rev1，并已通知 B subagent_7a081a43b84545a3921aa2cdba61626f 与热源 subagent_ef8239f837074166a21b7f0ff1871e08。manifest.json 为本批明确输入版本，交唯一集成下一明确版本编译并运行上述 main / 两个 GameTest 方法，不混入固定2693旧快照、不把其旧结果外推到本版。

此任务结束无本人后台编辑、构建、未返回写调用；后续变更须经正式文件交接，旧 A 释放板只读边界不自行回收。
