# Applied Crafted Energistics（应用机巧能源）

<p align="center">
  <img src="docs/branding/ace-logo-512.png" alt="Applied Crafted Energistics — ME 气体接口方块标志" width="256" />
</p>

一个 **Applied Energistics 2（AE2）** 附属模组：把 **Create: Crafted Beginning（机械动力：机巧新生）** 的气体接入 ME 网络。

## 目的

- 气体可存进 ME 网络：气体存储元件 1k / 4k / 16k / 64k / 256k / 1M / 4M / 16M / 64M / 256M（**1 byte = 1,000 GU**；1M-256M 配方需要可选前置 **MEGA Cells**）；
- 与 CCB 的储罐、管路互通：ME 气体接口（9 组配置/缓存槽，逐槽目标数量、目标压力与传输模式）、气体 P2P 隧道，以及 AE2 原生的输入/输出/存储总线、ME 接口、样板供应器；
- 气体标记：气体样本、CCB 气体容器、CCB 虚拟气体物品，以及 JEI 拖拽（安装 JEI 时）；
- JEI 样板编码（安装 AE2-JEI-Integration 时）：在带 CCB 气体的处理配方上点击 JEI 的 “+”，气体输入/输出会以 `GasKey + 数量` 写入 AE2 处理样板；目标压力仍由机器/容器决定，标准样板不携带压力字段；
- 气体合成在保存并重新加载存档后保持稳定（`GasKey` 使用注册表规范主键，修复重载后的计划输出匹配）；
- 气体元件支持 AE2 原生升级卡（反相 / 均分 / 溢出销毁）和四张气体卡（高压 / 扩容 / 多类型 / 安全阀）；接口支持逐槽控制卡与安全阀卡（经 CCB 真实释放泄压）。

## 前置

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | ≥ 21.1.251 |
| AE2 | 19.2.x |
| Create | 6.0.10.x |
| Create: Crafted Beginning | 0.1.x |
| MEGA Cells（可选） | 4.11.x；1M-256M 气体元件配方前置 |

## 1M–256M 元件与 MEGA Cells

1M–256M 气体元件本身始终注册，但它们的默认配方带有 `neoforge:mod_loaded` 条件：
**只有安装 MEGA Cells 时，配方才会加载并在 JEI/指南中显示。** 不安装 MEGA Cells 时，这些元件仍可通过创造模式或命令获得，但没有默认生存配方。

如果整合包希望在没有 MEGA Cells 的情况下启用它们，可以用同路径数据包覆盖配方：删除 `neoforge:conditions`，替换 `megacells:*` 材料即可。详细示例见游戏内指南的 **Gas Storage Cells** 页面和 `docs/` 下的说明。


## 配置

配置文件：`config/appliedcraftedenergistics-common.toml`（首次启动生成）。修改后保存并重启游戏即可生效。

| 键 | 默认 | 说明 |
|---|---|---|
| `gas_interface.idlePower` | 8.0 | 接口待机耗电（AE/t） |
| `gas_p2p.bufferVolumeLiters` | 4000 | 气体 P2P 每个面的缓冲体积（升，承压 16 atm） |
| `recipes.enable_mega_cell_recipes` | true | 是否加载默认 1M-256M MEGA Cells 配方；设为 false 可让数据包提供自己的高容量配方 |

接口的逐槽目标数量、目标压力与传输模式在 GUI 中设置；0.1.x 的 `bufferVolumeLiters` / `supplyPressureKpa` /
`transferRate` / `energyPerKiloGasUnit` 已废弃（旧配置项会被加载器忽略）。

`recipes.enable_mega_cell_recipes=false` 只会移除默认的 MEGA-gated 配方，不会注销 1M-256M 元件本身；
高容量元件仍可被创造模式、命令或自定义数据包配方获得。详见 `docs/GAS_CELLS.md`。

## 构建

```bat
gradlew.bat build
```

产物：`build/libs/appliedcraftedenergistics-1.21.1-neoforge-0.2.2.jar`。
本地测试用的路径配置见 `env.example`（复制为 `.env` 后填写）。

## 许可证

- 本模组的代码与原创资源：**LGPL v3.0**（SPDX: `LGPL-3.0-only`），全文见 `LICENSE`；不再提供 “or later” 选项，迁移说明见 `docs/LICENSE_MIGRATION.md`；
- 衍生自 AE2 的贴图：**CC BY-NC-SA 3.0**，清单与署名见 `THIRD_PARTY_NOTICES.md`（本项目为非商业项目）。

## 文档

- `docs/CCB_PRESSURE_MATH.md` — GU / 升 / Pa 与容量换算
- `docs/UPGRADE_CARDS_PLAN.md` — 元件升级卡设计与数值
- `docs/P2P_NOTES.md` — 气体 P2P 隧道
- `docs/NEW_MECHANICS.md` — 逐槽设置、总线压力卡、AE2 原生设备接入、JEI 与存档稳定性等新机制
- `docs/GAS_CELLS.md` — 气体元件档位、MEGA 配方条件与数据包覆盖说明
- `docs/LICENSE_MIGRATION.md` — LGPL v3.0 许可迁移说明
- `docs/RUNTIME_VERIFICATION.md` — 运行验证记录
