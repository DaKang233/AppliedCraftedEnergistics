# CCB 气体计量与气压换算（GU / 升 / Pa）

来源：`CreateCraftedBeginning` 0.1.1 源码
（`api/gas/GasPressure.java`、`GasPressureLimits.java`、`foundation/BoundedMath.java`、
`gas/storage/GasTank.java`、`gas/storage/GasTankLimits.java`、`gas/network/math/GasPressureTransferMath.java`、
`config/CCBMachines.java`）。

## 1. 三个基本量

| 量 | 单位 | 类型 | 说明 |
|---|---|---|---|
| 气体量 | **GU**（Gas Unit） | `long` | 整数；世界/储罐里只有"多少 GU"，没有别的量纲 |
| 体积 | **升 L** | `long` | 储罐/隔间的物理容积；配置文件里用 **kL**（1 kL = 1,000 L） |
| 压力 | **Pa** | `long` | 100,000 Pa = 1 atm = CCB 的**参考压力** |

## 2. 唯一的核心公式

```
压力(Pa) = 气体量(GU) × 100,000 / 体积(L)
气体量(GU) = 体积(L) × 压力(Pa) / 100,000
```

- **1 GU = 1 升气体在 100,000 Pa（1 atm）下**，等价地 1 kGU = 1 m³ @1 atm。
- CCB 的 "1 atm" 严格取 **100,000 Pa**（不是 101,325 Pa）。
- 压力是**推导量，从不存储**：每次读取都用当前 GU 和体积现算。所以同一只罐装不同量、或多方块罐扩容后，压力会立刻变化。

## 3. 容量怎么算（maxAmount）

容量不是单独字段，而是"体积 × 承压上限"换算出来的：

```
maxAmount(GU) = 体积(L) × maxPressure(Pa) / 100,000
```

承压上限只有两个全局档位：

| 档位 | 压力 | Pa | 含义 |
|---|---|---|---|
| SAFE | 16 atm | 1,600,000 | 正常运行上限；超过开始积累过压应力 |
| HARD | 24 atm | 2,400,000 | 物理硬上限；任何储罐不得超过（构造 `GasTankLimits` 时超限直接抛异常） |

因此"这罐能装多少"始终等于 `体积 × 它自己的承压上限 / 100,000`。

### 常见容器容量（默认配置）

| 容器 | 体积 | 承压 | 满容量 |
|---|---|---|---|
| 密封储罐 `airtighttank` | 80 kL = 80,000 L / 方块 | HARD 24 atm | **1,920,000 GU** |
| 同上，按安全线 | 80 kL | SAFE 16 atm | 1,280,000 GU |
| 同上，1 atm | 80 kL | 1 atm | 80,000 GU |
| 气罐物品/方块 | 2 kL | 10 atm | 20,000 GU |
| 锻造压机输入罐 | 30 kL | 按机器配置 | 30,000 × P/100,000 |
| 反应釜 / 分馏塔 | 90 kL / 罐 | 按机器配置 | 90,000 × P/100,000 |
| Breeze Chamber | 40 kL | 按配置 | 40,000 × P/100,000 |
| 注气室 | 10 kL | HARD | 最高 240,000 GU |
| 气球（物品） | 1 个标准球 | 参考 1 atm | 10 kGU（`referenceGasPerBalloon = 10 kGU`，当地打包上限随环境压力缩放） |

## 4. 整数与边界行为

- 内部使用**饱和乘除** `BoundedMath.saturatedMultiplyDivideNonNegative`：`a*b/c` 先判溢出，
  放不下时用 `BigInteger`，仍超 `long` 则饱和到 `Long.MAX_VALUE`（不会回绕成负数）。
- `GU ≤ 0` → 压力返回 `0`（真空）；`体积 ≤ 0` → 压力返回 `Long.MAX_VALUE`（视为无限压）。
- 压力为整数，**向下取整**：例如 1 GU 装在 80,000 L 罐里 → `1×100,000/80,000 = 1 Pa`（小数被截掉）。
  所以极小量/极大体积时压力分辨率为 1 Pa。
- 气体种类不能混装：同一罐/隔间只能是一种气体（`isGasValid`/`isGasValid(tank, stack)`）；
  换气必须先把罐抽空。

## 5. 传输时"能进多少 / 能出多少"

流动的**唯一条件**是**源压力严格大于目标压力**（相等不流动）。在此基础上：

**向目标注气**（目标为可变压力容器）：

```
压力上限 = min(源压力, 目标 maxPressure)          // 不能把对方压过它的额定上限
可注入量 = min( 请求量,
                目标在"压力上限"下的容量 − 目标当前量,
                maxAmount − 当前量 )
```

（源码：`GasPressureTransferMath.maxFillAmount`，`GasPressureFillService.getTransferableAmount`。）

**从目标抽气**：可抽量 = 请求量 与 "当前量 − 维持管道压力所需最低残量" 的较小值。
目标压力模型为 `FIXED` 的（如创造储罐、固定的压力边界）不按 `量/体积` 推导，而是使用它声明的固定压力。

真正的吞吐瓶颈是**流量上限**而不是容量：

| 设备 | 默认速率 |
|---|---|
| 密封泵 `airtightpump` | 4,000 GU/t（并给最多 +4 atm 压升） |
| 调节泵 `airtightregulatorpump` | 4,000 GU/t，单级压升最多 +8 atm |
| 密封舱门 `airtighthatch` | 20,000 GU/s（且随压差缩小而变慢） |
| Breeze Chamber | 5,120 GU/s |

这些速率还会随 Create 转速、入口压力（低于 1 atm 时衰减）与反向压差而下降。

## 6. 环境/大气

- 开放管口或贴着 `#createcraftedbeginning:gas_sources` 的管面是"大气端点"：
  可以按**当地环境压力**抽取/排放对应气体（维度/群系由 `AtmosphereProvider` 决定，可被附属模组扩展）。
- 环境压力最终会被 `GasPressureLimits.clampToHardLimit` 限制在 0…24 atm 之间。
- 从大气取气时，"能取多少"取决于泵的流量与压升能力，而不是某个罐的体积。

## 7. 与本模组（Applied Crafted Energistics / ACE）的换算

- **1 AE byte = 1,000 GU（1 kGU）**，也就是 1 字节 = 1 m³ @1 atm（见 `GasAmounts`）。
- 元件容量：`字节 × 1,000 GU`，例如 1k 元件 = 1,024,000 GU = 1,024 m³ @1 atm
  ≈ 0.53 个打满 24 atm 的 80 kL 密封储罐。
- AE 侧只记"量"，不记压力；跨体系必须经过真实容器：
  - `ME 气体接口` 缓冲罐：8,000 L，按 HARD 24 atm 设计 → 物理极限 192,000 GU；
    AE 侧供给压力默认 100 kPa（1 atm），即可补入 8,000 GU 的量；调到 16 atm 可补到 128,000 GU。
  - `气体 P2P 隧道`：每面 4,000 L，按 SAFE 16 atm 设计 → 64,000 GU。
- 因此"元件容量（GU）÷ 1,000 = 字节数 = 多少 m³ 的 1 atm 气体"。

## 8. 三个算例

1. **80 kL 储罐打到 16 atm**：
   `GU = 80,000 × 1,600,000 / 100,000 = 1,280,000 GU` = 1,280 kGU。
2. **把 500,000 GU 放进空的 80 kL 罐**：
   `P = 500,000 × 100,000 / 80,000 = 625,000 Pa = 6.25 atm`。
3. **一个 1k 气体元件全打进空的 80 kL 罐**：
   `GU = 1,024 × 1,000 = 1,024,000`；`P = 1,024,000 × 100,000 / 80,000 = 1,280,000 Pa = 12.8 atm`（在安全线内）。

## 9. 常见误区

- 不要把 1 atm 当成 101,325 Pa；CCB 全流程用 **100,000 Pa**。
- 压力不是罐的固定属性，而是"当前量 / 当前体积"的结果。
- 容量由 `体积 × 承压上限` 决定，不是"固定槽位数"；多方块储罐每加一块 +80 kL，同步提高容量、降低同量气体的压力。
- 把气体压到 16 atm 以上不是"更划算的存储"：超过 16 atm 会积累过压应力，24 atm 下约 5 秒破裂并释放气体（默认强度 4 的小爆炸）。
- 传输不是瞬时的：容量决定"最多装多少"，泵/舱门/管路的 GU/t 上限决定"多久装满"。
