# Applied Crafted Energistics（应用机巧能源）

<p align="center">
  <img src="docs/branding/ace-logo-512.png" alt="Applied Crafted Energistics — ME 气体接口方块标志" width="256" />
</p>

一个 **Applied Energistics 2（AE2）** 附属模组：把 **Create: Crafted Beginning（机械动力：机巧新生）** 的气体接入 ME 网络。

## 目的

- 气体可存进 ME 网络：气体存储元件 1k / 4k / 16k / 64k / 256k（**1 byte = 1,000 GU**）；
- 与 CCB 的储罐、管路互通：ME 气体接口、气体 P2P 隧道，以及 AE2 原生的输入/输出/存储总线、ME 接口、样板供应器；
- 气体元件支持 AE2 原生升级卡（反相 / 均分 / 溢出销毁）和四张气体卡（高压 / 扩容 / 多类型 / 安全阀）。

## 前置

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | ≥ 21.1.251 |
| AE2 | 19.2.x |
| Create | 6.0.10.x |
| Create: Crafted Beginning | 0.1.x |

## 配置

配置文件：`config/appliedcraftedenergistics-common.toml`（首次启动生成）。修改后保存并重启游戏即可生效。

| 键 | 默认 | 说明 |
|---|---|---|
| `gas_interface.bufferVolumeLiters` | 8000 | ME 气体接口缓冲罐体积（升） |
| `gas_interface.supplyPressureKpa` | 100 | 接口供给压力（kPa） |
| `gas_interface.transferRate` | 1000 | 每 tick 缓冲罐 ↔ 网络搬运上限（GU/t） |
| `gas_interface.energyPerKiloGasUnit` | 1.0 | 每 kGU 搬运耗电（AE） |
| `gas_interface.idlePower` | 8.0 | 接口待机耗电（AE/t） |
| `gas_p2p.bufferVolumeLiters` | 4000 | 气体 P2P 每个面的缓冲体积（升，承压 16 atm） |

## 构建

```bat
gradlew.bat build
```

产物：`build/libs/appliedcraftedenergistics-1.21.1-neoforge-0.1.1.jar`。
本地测试用的路径配置见 `env.example`（复制为 `.env` 后填写）。

## 许可证

- 本模组的代码与原创资源：**LGPL-3.0-or-later**，全文见 `LICENSE`；
- 衍生自 AE2 的贴图：**CC BY-NC-SA 3.0**，清单与署名见 `THIRD_PARTY_NOTICES.md`（本项目为非商业项目）。

## 文档

- `docs/CCB_PRESSURE_MATH.md` — GU / 升 / Pa 与容量换算
- `docs/UPGRADE_CARDS_PLAN.md` — 元件升级卡设计与数值
- `docs/P2P_NOTES.md` — 气体 P2P 隧道
- `docs/RUNTIME_VERIFICATION.md` — 运行验证记录
