# Applied Crafted Energistics — 项目设计文档 (v0.1)

> AE2 × Create: Crafted Beginning 气体集成模组
> 目标版本：Minecraft 1.21.1 / NeoForge 21.1.251 / AE2 19.2.x / Create 6.0.10 / Create Crafted Beginning 0.1.1
> 目标运行实例：见仓库根目录 .env 的 MINECRAFT_ROOT / VERSION_NAME（模板 nv.example）

## 1. 名称与定位

- 英文名：**Applied Crafted Energistics**
- 中文名：**应用机巧能源**
- mod id：`appliedcraftedenergistics`
- 包名：`com.appliedcraftedenergistics`
- 定位：AE2 与 Create: Crafted Beginning（CCB，机械动力：机巧新生）的桥接附属。让 CCB 的气体 (Gas) 成为 AE2 网络中的一级存储资源：可被专用存储元件存储、被 ME 接口/样板供应器/输入输出总线搬运，并与 CCB 既有的气体管路、储罐、气压体系互通。

## 2. 参考实现

| 参考 | 作用 | 复用点 |
|---|---|---|
| `<workspace>\Applied-Mekanistics` | AE2 自定义资源类型最完整的样例 | `AEKeyType` / `AEKey` / `BasicStorageCell` / `ExternalStorageStrategy` / `StackExportStrategy` / `StackImportStrategy` |
| `<workspace>\AppliedPneumatics` | 压力型资源的 AE2 集成样例 | `ICellHandler` 自定义元件、`ME 压力接口` 方块、GUI、气压语义 |
| `<workspace>\Applied-Energistics-2` | AE2 本体 API | 扩展点签名 |
| `<workspace>\CreateCraftedBeginning` | CCB 气体 API | `GasStack` / `GasHandler` / `GasCapabilities` / `GasTank` / 气压模型 |

## 3. CCB 气体模型（已确认）

来自 `net.ty.createcraftedbeginning.api.gas`：

- **量纲**：气体量 `long`，单位 **GU**（Gas Unit）。1 GU = **参考压力 (100 kPa) 下 1 升气体**。
  - `GasPressure.REFERENCE_PRESSURE_PA = 100_000`
  - `GasPressure.pressure(amount, volumeLiters) = amount * 100000 / volumeLiters` (Pa)
  - 因此 `amount = volume(L) × pressure(Pa) / 100000`，即 GU ≈ 升·大气压。
- **气压上限**：`GasPressureLimits.SAFE_PRESSURE_ATM = 16` (1.6 MPa, 正常运行上限)，`HARD_PRESSURE_ATM = 24` (2.4 MPa, 结构物理上限)。
- **储罐**：`GasTankLimits(volumeLiters, maxPressurePa)`，`maxAmount() = volume × maxPressure / 100000`。有体积与承压上限，超过安全压力即 `isOverpressure`。
- **能力**：`GasCapabilities` + `GasHandler`/`GasStorageHandler`（insert/extract，`GasAction` 模拟/执行），`GasTank`/`SmartGasTank` 是现成实现。
- **传输**：CCB 自己的管路求解器（`gas.network`）只把带气体能力的方块当作端点 → 我们的接口只要暴露 CCB 的 `GasHandler` 能力，就能接入既有管路体系。

## 4. 核心换算规则（本文档规定的"1 byte 存多少气体"）

> 这是本模组的官方数值规则，写进 tooltip、GUI 与 config。

- AE2 的字节换算接口是 `AEKeyType.getAmountPerByte()`。
- **规则：1 AE byte = 1,000 GU = 1 kGU**，即"1 字节 ≈ 1000 升参考压气体（1 m³ @ 100 kPa）"。
  - `getAmountPerByte() = 1000`
  - `getAmountPerUnit() = 1000`（终端显示以 **kGU** 为单位）
  - `getAmountPerOperation() = 1000`（每次 IO/合成操作搬运 1 kGU）
  - 单位符号 `"kGU"`，完整单位名 `GU`。
- config `gas.guPerByte` 可改（默认 1000，允许 1–100000），改后所有元件容量、总线速率、接口吞吐同步缩放。
- 由此得到标准元件容量（`bytes` = AE2 的 1k/4k/16k/64k/256k）：

| 元件 | 字节 | 气体容量 (GU) | 等价体积 @100 kPa |
|---|---|---|---|
| 1k 气体元件 | 1,024 | 1,024,000 | 1,024 m³ |
| 4k | 4,096 | 4,096,000 | 4,096 m³ |
| 16k | 16,384 | 16,384,000 | 16,384 m³ |
| 64k | 65,536 | 65,536,000 | 65,536 m³ |
| 256k | 262,144 | 262,144,000 | 262,144 m³ |

（参考：CCB 的 `airtighttank` 每方块体积 **80 kL**、承压上限 **24 atm**，满压储量 = 80 000 L × 24 = **1 920 000 GU**。因此一个 1k 气体元件 ≈ 0.53 个满压储罐方块，4k ≈ 2.1 个，64k ≈ 34 个，256k ≈ 136 个——量级与 CCB 的存储体系匹配。）

## 5. 气压问题的处理（设计决定）

AE2 存储元件是"量子存储"，内部没有体积/压力概念；CCB 的储罐与管路是真实物理的。二者交界处必须显式定义气压语义：

1. **元件侧（AE 内部）**：气体以"参考压下的量 (GU)"存储，元件自身无压力、不爆裂。tooltip 同时显示：
   - 存量 GU / 容量 GU，
   - 等价体积 @100 kPa，
   - 若装入某容量储罐，其等效压力（信息性）。
2. **接口侧（AE ↔ CCB 物理体系）**：`ME 气体接口`/`气体输入总线`/`气体输出总线` 与 CCB 交互时遵守对方的气压物理：
   - **向 CCB 注气**：可注入量 = `tank.maxAmount() - tank.storedAmount()`（即对方在自身承压上限内的余量），并且不得超过 `volume × HARD_PRESSURE`。目标压力 ≥ SAFE(16 atm) 时接口限流并在 GUI 显示"过压/Overpressure"；接口绝不让对方超过 HARD(24 atm)。
   - **从 CCB 抽气**：可抽取量 = `handler.getStoredAmount()`（抽到真空为止），抽气会降低对方压力，属正常物理行为。
   - **接口内置缓冲罐**：接口自身持有一个真实的 CCB `GasStorageHandler`/`GasPressureCompartment` 缓冲罐（体积与承压可配置，默认 8,000 L / **16 atm（SAFE 级）**）。对 CCB 管路表现为一个普通气体罐（暴露 `GasHandler` 能力，且实现 `GasStorageHandler` + `getPressureCompartment(t).getVolume() > 0`，否则 CCB 管路只会"连上"而不会"输送"——这是本模组最容易踩的坑）；对 AE 网络则是一个可读写的气体存储端点。**这就是"气压"的落点**：AE 与 CCB 之间永远通过这个缓冲罐交换气体，于是 CCB 的管路求解器能看到真实的体积/压力，AE 侧只看到量。
   - 缓冲罐实现 `captureTransferSnapshot()`：CCB 求解器会"模拟→执行→可能回滚"，而 ME 网络无法回滚，所以缓冲罐必须可回滚——它也确实是 AE 与 CCB 之间唯一的事务边界。
   - **供给压力 config**：`gas.interfaceSupplyPressureKpa`（默认 100 kPa = 1 atm）。缓冲罐被 AE 侧填充到该压力对应的量为止；提高该值（最高 1600 kPa）用于把气体压进高压管路。
3. **总原则**：AE 网络是"恒压理想气库"（量守恒、无体积），CCB 是"有限体积 + 承压上限"的真实体系。所有跨体系搬运都必须经过缓冲罐 + 承压上限检查，绝不凭空绕过 CCB 的过压机制。

## 6. v0.1 功能范围（本次交付）

必须完成（P0）：
1. `gas` 资源接入 AE2：`GasKey` + `GasKeyType` 注册（`appliedcraftedenergistics:gas`），终端可见、可排序、可显示名/图标。
2. **气体存储元件**：`1k/4k/16k/64k` 四档，继承 AE2 `BasicStorageCell`；外壳使用 AE2 原版元件外壳 + CCB 材料（简化配方即可）。元件可放入 ME 驱动器/ME 箱子，被网络读写，支持元件工作台（cell workbench）分区/升级。含 item model、贴图、lang、tooltip。
3. **ME 气体接口**（方块 + 部件）：把 AE 网络与相邻 CCB 气体管路/储罐连通；内部真实缓冲罐；UI 显示存量/压力/方向；支持"推/拉/双向"三种模式；Pattern Provider 语义（样板供应器可以把气体作为产物/原料注入相邻机器）。
4. **气体输入总线 / 气体输出总线**（AE2 part）：在网络与相邻 CCB 气体处理器之间搬运气体，有速率限制（默认每 tick 1 kGU，受 config 与升级影响）。
5. **CCB 管路互通**：接口/总线暴露 CCB `GasHandler` 能力，使 CCB 管道泵可直接把我们的缓冲罐当作线路端点。
6. 数值与气压规则（第 4、5 节）+ config 文件 + 游戏内 tooltip/GUI 表达。
7. 编译通过，产出 jar；并通过 dev 环境 datagen + 在目标实例上加载运行验证。

可选（P1，时间允许才做）：便携气体元件、气体 P2P 隧道、CCB 气体罐↔元件的"灌装台"、JEI/EMI 显示、Create ponder。

明确不做：新的气体种类、新的世界生成、把 AE2 物品/流体塞进 CCB 体系、多人同步/网络协议之外的复杂功能。

## 7. 代码结构（包 `com.appliedcraftedenergistics`）

```
com.appliedcraftedenergistics
├── AppliedCraftedEnergistics.java            主类：注册表、config、事件
├── Config.java                        数值/气压配置
├── ACEText.java / AALang.java          文本常量
├── init/
│   ├── ACEItems.java                   气体存储元件 ×4、外壳等
│   ├── ACEBlocks.java                  ME 气体接口方块
│   ├── ACEBlockEntities.java
│   ├── ACEMenus.java
│   ├── ACEParts.java                   气体输入/输出总线 (AE2 Part)
│   ├── ACECreativeTabs.java
│   └── ACECapabilities.java            CCB GasHandler 能力注册 + AE2 策略注册
├── gas/
│   ├── GasKey.java                    AEKey 实现（包装 Holder<Gas>）
│   ├── GasKeyType.java                AEKeyType，getAmountPerByte 等
│   ├── GasStacks.java                 GasStack ↔ AEKey 换算/工具
│   ├── GasAmounts.java                GU/字节/压力 换算中心（唯一真实来源）
│   └── GasStorageAdapter.java         MEStorage/StorageAdapter 适配
├── cell/
│   ├── GasStorageCellItem.java        extends BasicStorageCell
│   └── (可选) GasCellMenu
├── device/
│   ├── GasInterfaceBlock.java / GasInterfaceBlockEntity.java
│   ├── GasInterfaceMenu.java / GasInterfaceScreen.java
│   ├── GasImportBusPart.java / GasExportBusPart.java
│   └── handler/GasBufferTank.java     缓冲罐（CCB GasTank + AE 端点桥）
├── integration/
│   ├── ae2/…                         AE2 注册入口（CellHandler, ExternalStorageStrategy, Stack(Export|Import)Strategy）
│   └── ccb/…                         CCB 能力/工具（读取 GasHandler、注册能力）
├── datagen/…                          recipes / models / lang / tags
└── client/…                           帖子/模型/渲染
```

## 8. 验证标准

1. `gradlew build` 成功，jar 产出。
2. `gradlew runData` 成功（证明 AE2 + Create + CCB + 本模组在 dev 环境一起加载，注册表无冲突）。
3. 目标实例（.env 的 VERSION_NAME）中放入 jar 后游戏能正常启动：`logs/latest.log` 出现本模组加载、无 mixin/注册错误，退出码正常（客户端能进主菜单框架）。
4. 关键类有单元/集成测试或用 dev `runServer` 的 gametest 覆盖换算规则（GasAmounts 纯函数单测优先）。

## 9. 分工与写入范围（避免冲突）

- build-engineer：`build.gradle`、`gradle.properties`、`settings.gradle`、`gradle/`、`gradlew*`、`src/main/templates/`、`src/main/resources/pack.mcmeta`、`src/main/resources/icon.png`、`docs/BUILD_STATUS.md`、`docs/build-logs/`。
- ae2-researcher / ccb-researcher：`docs/AE2_API.md`、`docs/CCB_GAS_API.md` 及证据文件（只读源码）。
- 实现阶段按包切分：cell 子系统 / device 子系统 / integration+datagen 子系统，各自只写自己的包，共享文件（主类、init 注册表）由 Lead 或指定单人写。

## 10. 风险

1. **CCB 0.1.1 的 API 稳定性**：`api.*` 相对稳定，`gas.*` 内部实现可能变；只依赖 `api.*` + 必要处反射/软依赖。
2. **AE2 19.2.20 与参考模组所用 19.2.10/19.2.17 的签名差异**：一切以 19.2.20 生产 jar 的 `javap` 为准。
3. **dev 环境无法加载生产 jar 版的 CCB**：若 FML 拒绝，退化为"仅编译验证 + 目标实例运行验证"。
4. **首次 Gradle 构建极慢**（无缓存，需下载 gradle/neoforge/MC/neoform）。
