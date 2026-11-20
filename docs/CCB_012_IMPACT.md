# CCB 0.1.2 气体网络改动对本模组的影响评估

评估对象：`CreateCraftedBeginning` 仓库 `b027fb9b`（"Rework gas network flow and pressure scheduling"）
与 `94dc6379`（"Update to v0.1.2"）；0.1.2 尚未发布，以下结论基于源码静态比对。

## 结论

**公开 API 无改动，现有集成逻辑不需要改代码；但求解器被重写，流量/时序行为需要等 0.1.2 发布后做一次运行时回归。**

## 变更内容

- 旧实现：图求解器（`GasPressureGraphSolver`、`GasLinearPressureSolver`、`GasNonlinearPressureSolver`、
  `GasQuantizedEquilibriumPlanner`、`GasRoutedTransferExecutor` 等）。
- 新实现：Zone / Branch 调度（`GasZonePressure`、`GasZoneRoute`、`GasZonePorts`、`GasBranchOrder`、
  `GasBranchRound`、`GasBranchShare`、`GasFixedManifold`、`GasOrdinaryPathSearch`、`GasPassiveQuantum`），
  传输执行改为 pair 级（`GasPairTransferExecutor` → 仍走 `GasTransferExecutor.executePooledTransfer`）。
- 同一提交还调整了气体释放/进气范围（`gas/flow/*`）与相关视觉/读数，属于世界侧表现，不涉及存储端点契约。

## 契约点核对（0.1.1 → 0.1.2）

| 依赖点 | 0.1.2 状态 | 影响 |
|---|---|---|
| `api.gas.*`、`api.canister.*` 公开类/方法 | **无改动**（diff 仅 `api.gasreleasehandlers.*`，本模组未使用） | 可直接编译 |
| `GasStorageHandler` + `getPressureCompartment(i).getVolume() > 0` | 新 `GasEndpointDiscovery` 判定不变 | 容器仍被识别 |
| `PressureModel.FIXED` / `VARIABLE` | 新 `GasEndpointPlanner` 仍区分，FIXED 直接用 handler 压力 | AE 接口槽位（FIXED 1 atm）行为保持 |
| `captureTransferSnapshot` / `restoreDrainedGas` / `supportsExactDrainRecovery` | `GasTransferExecutor` 仍调用 | 缓冲罐回滚继续生效 |
| `predictTransferLimits` | `GasNetworkPressureEndpoint` 仍使用 | 抽/注上限继续生效 |
| `getCompartmentIdentity` 分组 | 新 `GasEndpointPlanner.StorageEndpointGroup.matches` 仍按 identity | ME 接口"每槽一组"仍成立 |
| 每 tick 流量/顺序 | 调度算法重写 | **可能变化**；本模组不依赖顺序，但需实测 |

## 发布后回归清单

1. ME 气体接口：空罐导入、到供给压力停止、双向模式在设定值上下切换、过压提示；
2. AE2 ME 接口：单槽 / 多槽、不同气体、两侧管道同时抽取、抽取速率与 0.1.1 对比；
3. 气体 P2P：输入只收、输出只放、断开输出不丢气、按量扣费、快照回滚不重复计费；
4. 与 0.1.2 调整过的进气/释放范围相邻时的表现（接口旁有开放管口、阀门、泵）；
5. 大网络性能：多台接口 + P2P 同网络时的 tick 时间。

## 备注

- `neoforge.mods.toml` 声明的 CCB 版本范围是 `[0.1.1,0.2)`，0.1.2 在范围内，发布后替换 jar 即可测试；
- 若回归中发现 FIXED 端点抽取异常，优先检查 `predictTransferLimits` 的 drainLimit 与槽位 identity
  的稳定性（`AeInterfaceGasCompat.SlotCompartment`）。
