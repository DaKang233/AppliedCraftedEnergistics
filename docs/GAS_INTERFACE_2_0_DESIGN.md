# 气体 ME 接口 2.0（9×2 槽位 + 压力 + 逐槽控制）设计草案

> 状态：设计草案（Phase 0），代码尚未开始。对应需求：ACE 气体 ME 接口与气体标记系统改进。
> 目标版本：0.2.0。

## 1. 复用 AE2 接口逻辑的可行性

AE2 19.2 的 ME 接口实现可以直接复用（已核对源码）：

| AE2 类 | 用途 | 复用方式 |
|---|---|---|
| `appeng.blockentity.misc.InterfaceBlockEntity` | 方块实体，`protected InterfaceLogic createLogic()` | 我们的 BE **继承**它，覆写 `createLogic()` 返回 `GasInterfaceLogic` |
| `appeng.helpers.InterfaceLogic` | 9 个配置槽 + 9 个缓存槽、自动补货 `updatePlan/usePlan`、合成请求、优先级 | **不能直接继承**：`upgrades` 字段私有且固定 1 槽，无法扩到 6；改为按同构语义自行实现 `GasInterfaceLogic` |
| `appeng.helpers.InterfaceLogicHost` | 宿主接口 | 由 BE 实现（或沿用父类实现并覆写 `openMenu`） |
| `appeng.util.ConfigInventory` | 配置槽（`GenericStack`，数量即目标值）/ 缓存槽 | 直接使用；目标数量 = `GenericStack.amount`，上限由 `GenericSlotCapacities` 控制 |
| `appeng.menu.implementations.InterfaceMenu` | 9 CONFIG + 9 STORAGE 槽位菜单 | 继承，新增逐槽压力/模式编辑动作 |
| `appeng.client.gui.implementations.InterfaceScreen` | 接口 GUI | 继承，新增压力/模式控件；背景用自绘样式文档 |
| `appeng.api.behaviors.GenericSlotCapacities` | 每种资源的槽位容量 | 已注册气体 64kGU；本接口每槽上限即 64kGU |

结论：槽位配置交互（`ConfigInventory` 与数量设置子菜单）可直接复用 AE2；
**库存管理/补货逻辑需要自行实现**（6 个升级槽 + 逐槽压力/模式无法塞进 AE2 的 `InterfaceLogic`），
算法按 AE2 的 `updatePlan/usePlan` 语义重写。
逐槽压力、逐槽模式（升级卡解锁）、实现 `GasCapabilities.BLOCK` 的真实容器视图。

## 2. 槽位数据模型

- 配置槽（9）：`GenericStack`（GasKey + 目标数量），数量即"目标缓存数量"（0–64kGU）。
- 缓存槽（9）：`GenericStack`（由 `InterfaceLogic` 自动补货/回收到网络），单槽上限 64kGU。
- 新增每槽附加数据（我们自己的 NBT，不是 GasKey 的一部分）：

```java
record GasSlotSettings(int targetPressureAtm, boolean pressureExplicit, GasSlotMode mode) {}
enum GasSlotMode { INPUT, OUTPUT, BOTH, OFF }   // BOTH 为默认；无模式卡时一律按 BOTH 处理
```

- 存储：BE 内 `GasSlotSettings[9]`，NBT key `gas_slots`；旧存档没有该 key 时全部按默认（BOTH、未显式压力）。
- 升级槽：6 个（父类 `InterfaceLogic` 默认 1 个，需要覆写为 6；速度卡仍走 AE2 的 `Upgrades.add`）。

## 3. 压力模型（关键设计）

**目标数量 + 目标压力 → 虚拟容器体积**，让两者在物理上自洽：

```
volumeLiters = targetAmountGU / targetPressureAtm        // 1 GU = 1 L·atm
pressurePa   = storedGU × 100,000 / volumeLiters
maxPressure  = min(targetPressureAtm, 16 atm)（或按设备上限）
```

- 未显式配置压力时：沿用默认供给压力（1 atm）与默认体积（目标数量 / 1 atm）；
- 达到目标数量时，槽内压力恰好等于目标压力；不足时压力更低，CCB 管道按压差自然抽取；
- 外部管网压力更高时，气体可以反向进入槽位（`fill`），AE2 的 `InterfaceLogic` 会把超过目标的部分
  推回网络（复用原逻辑）；
- **不会**把压力当成 GasKey 的属性，也不会绕过 CCB 的压差/流量限制（槽位实现
  `GasStorageHandler` + `VARIABLE` 压力模型，交给 CCB 求解器）。

> 需要确认：体积按"目标数量 / 目标压力"推导（推荐），还是固定体积、压力仅作为上限参考。
> 推荐前者，否则两个目标值无法同时成立。

## 4. 逐槽模式与升级卡

- 新卡：**逐槽控制卡**（`gas_slot_control_card`，最多 1 张）。
- 未安装：9 个槽位统一 BOTH；GUI 隐藏/禁用逐槽模式控件。
- 安装后：每槽可 INPUT / OUTPUT / BOTH / OFF：
  - INPUT：只允许 CCB → 槽位（`fill` 生效，`drain` 返回空）；
  - OUTPUT：只允许槽位 → CCB（`drain` 生效，`fill` 返回 0）；
  - BOTH：两者皆可；
  - OFF：`fill`/`drain` 都返回 0，但 AE2 网络仍可补货/回收（槽位仍参与 ME 库存）。
- 移除卡：立即回到统一 BOTH；逐槽模式数据保留在 NBT，重新安装后恢复显示与生效。

## 5. 安全阀卡（真实泄压）

触发链（每 tick）：

1. 对每个槽位计算当前压力（`stored × 100000 / volume`）；
2. 若压力 ≥ 危险阈值（建议 16 atm 安全线，或设备 `maxPressure` 的 90%），且已安装安全阀卡：
3. 从槽位 `drain` 一小批气体（按 CCB 的 `GasReleaseService.EFFECT_INTERVAL` 与流量上限，默认 1,000 GU/t）；
4. 用 CCB 的**真实释放 API** 排放：

```java
var request = GasReleaseRequest.directional(
        stack, pos, side, GasReleaseCause.OVERFLOW, sourcePressurePa);
var result = GasReleaseService.release(level, request);   // 会产生 CCB 的环境效果/推动实体
if (result.released()) { /* 才提交 drain */ }
```

5. 压力回落到安全范围后停止。正常目标压力必须低于泄压阈值，避免"补气 → 泄压"循环。

注意：`GasReleaseService` 是 CCB 的实现类（公开但非 `api.*`）；若 CCB 后续提供 API 级释放入口应切换过去，
并在 `docs/CCB_GAS_API.md` 记录该依赖。

## 6. 气体标记

- 通用标记沿用 `GasKey`/`GenericStack`：配置槽拖入气体时写 `GenericStack(GasKey, amount)`；
- **气体样本物品**（我们新增，`gas_sample`，携带气体 id 的 data component）：
  在配置槽右键 → 写入该气体；数量沿用当前目标值；
- **气体容器右键**：用 `CanisterCapabilities.ITEM` 读容器内 `GasStack`，写入气体；
- JEI（可选）：注册 `IGhostIngredientHandler`，从 JEI 拖动气体到配置槽；未安装 JEI 时其余功能不受影响；
  实现放在独立类 + `@JeiPlugin`，只有 JEI 存在时才加载。

## 7. 其他设备

| 设备 | 现状 | 需要改 |
|---|---|---|
| 输入/输出总线 | 已通过 `GenericSlotCapacities` 支持气体标记与搬运 | 目标压力需要额外的逐部件配置（AE2 part 无此字段）→ 需自制部件或 mixin，成本高，建议 Phase 4 |
| 存储总线 / 元件工作台 | 配置槽为 `GenericStackInv`，气体已可标记 | 验证 + 气体样本/容器右键支持 |
| 标准发信器 | 配置槽同样支持气体 | 仅验证，不加压力 |
| 元件 | 已支持气体 | 不变 |
| 原版 `ae2:interface` | 目前由 `AeInterfaceGasCompat` 提供气体缓存 | **移除**该兼容（含 `ACEGasIntegration` 中相关注册），功能集中到本接口 |

## 8. 实现阶段

| 阶段 | 内容 | 产出 |
|---|---|---|
| P1 | BE 改为继承 `InterfaceBlockEntity`；`GasInterfaceLogic`（9+9、6 升级槽、逐槽设置 NBT）；气体能力视图（VARIABLE 容器） | 逻辑可编译、可启动 |
| P2 | 菜单/屏幕：CONFIG/STORAGE 槽位、数量设置复用 AE2；逐槽压力/模式编辑（齿轮按钮 + 子窗口） | GUI 可用 |
| P3 | 逐槽控制卡、安全阀卡（真实泄压）、高压卡保留 | 卡行为完整 |
| P4 | 气体样本物品、容器右键、JEI ghost、存储总线/元件工作台/发信器验证 | 标记系统完整 |
| P5 | 移除 `AeInterfaceGasCompat`；迁移旧存档（旧缓冲罐气体转入第 1 槽 / 或保留旧 NBT 并忽略）；测试矩阵 | 0.2.0 |

## 9. 已确认决策（2026-11-21）

1. 压力模型：**体积 = 目标数量 / 目标压力**（满目标时压力恰为目标值，不足时压力更低）；
2. 旧存档：旧缓冲罐气体**转入第一个空槽**；9 槽都满时按丢失处理并写日志；
3. 本期范围：**接口 + 标记 + 卡**；输入/输出总线的逐部件目标压力留到后续版本（本期只保证气体标记与数量）；
4. 逻辑实现：AE2 `InterfaceLogic` 的升级槽字段私有且固定 1 个，无法扩展到 6，故按同构语义
   **自行实现 `GasInterfaceLogic`**（配置/缓存仍用 AE2 的 `ConfigInventory`，数量设置仍复用 AE2 的接口子菜单）。
## 10. 实现状态（0.2.0）

| 项 | 状态 |
|---|---|
| 9 配置 + 9 缓存槽、6 升级槽、逐槽目标数量/压力/模式 | 已实现 |
| `volume = targetAmount / targetPressure` 的 CCB 容器视图 | 已实现（`GasInterfaceStorageView`） |
| 逐槽控制卡（BOTH 默认，安装后 INPUT/OUTPUT/BOTH/OFF） | 已实现 |
| 安全阀卡（20 atm，`GasReleaseService` 径向 OVERFLOW 真实泄压） | 已实现（只有释放成功才扣气） |
| 旧 8 kL 缓冲罐气体迁入第一个空槽 | 已实现 |
| 移除 `AeInterfaceGasCompat` | 已实现 |
| 气体样本物品 + `gas_id` 组件、气体容器右键标记 | 已实现 |
| GUI：中键模式、右键标记、滚轮数量、Shift+滚轮压力、悬停 tooltip | 已实现，游戏内打开验证通过（截图 docs/artifacts/ace-0.2.0-interface-gui.png） |
| 输入/输出总线逐部件压力 | 未实现（按确认决策后置） |
| JEI 拖拽标记 | 已实现（`compat/jei/AceJeiPlugin`，接受 CCB 气体 ingredient 与虚拟气体物品） |