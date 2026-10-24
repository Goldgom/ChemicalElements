# 文档导航

## 当前入口

- [当前代码集成记录](current-code-integration.md)：模块接入、构建验证入口与尚未启用的生产路径。
- [2026-10-25 续作记录](tokenbird-continuation-20261025.md)：本机 TokenBird 执行日志的恢复依据、原根桥实现及同版测试/客户端证据。
- [架构合同](architecture-contract-v1.md)：账本、隔离和事务约束。
- [世界材料合同](world-material-implementation-r24/CONTRACT-2.md)：世界材料边界的后续实施约束。
- [持久化事务检查点](material-transactions-r24/durable-next/checkpoint2.md)：事务与恢复的历史实施边界。

## 历史记录的使用

其余按版本和批次命名的目录保留设计、科学数据校准、实施报告、候选方案、补丁和当时的文件指纹。它们用于追溯决策，不作为当前源码或当前验收结论；正式构建仅使用 `src/` 与 Gradle 配置。旧候选源码、备份以及一次性修改脚本不可直接覆盖现有实现。

历史报告包含当时的本机路径和执行状态；其中“不提交”等操作限制描述的是当时的任务。当前构建和功能状态以上方入口及实际源码为准。

报告引用的 `build/`、`run/`、日志、截图及本机 TokenBird 会话均为本地证据，不随源码提交。克隆仓库后可按根目录 README 重新运行验证；历史 manifest 的哈希不保证匹配后续修订。
