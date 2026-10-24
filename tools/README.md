# 工具说明

日常编译、测试和客户端运行使用根目录 README 中的 Gradle 任务。

- `install-compat-r17.py`：安装固定版本 Curios/Jade/JEI 到 `run/compat-r17/mods`，逐文件校验 SHA-512。可重复执行；安装清单写入 `build/a-r17-compat-evidence/install-manifest.json`。
- `generate_element_item_compositions_r23.py`、`generate_elemental_block_compositions_r23.py`：生成元素样品/方块组成；支持 `--check` 只读检查，并依赖 `docs/` 中的校准数据。
- `assets/`、`core/`：保留资源制作、审计及早期代码生成过程。部分脚本会覆盖资源或源码，部分手动编译脚本仍引用原机器的 JDK 路径。它们不是当前构建入口；使用前须阅读脚本并确认输出范围。当前完整测试入口为 Gradle `build` / `integrationVerify`。

Python 缓存、运行日志及生成证据不入库。正式资源已包含在 `src/main/resources/`，正常构建无需重新运行历史生成脚本。
