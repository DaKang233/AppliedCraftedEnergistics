# 气体存储元件升级卡：候选清单与设计

现状（重要）：**目前 ACE 的气体元件没有注册任何升级卡**，所以即使是 AE2 原生的反相/均分/溢出销毁卡也插不进
元件工作台的升级槽。AE2 19.2 里 `BasicStorageCell` 给非物品元件 3 个升级槽，是否接受某张卡由
`Upgrades.add(卡, 元件, 数量, 组)` 决定；AE2 只为**自己的**元件注册了这些卡。因此"支持通用卡"本身就是
一项需要补的代码。

## 0. 先补齐：AE2 原生通用卡（3 张，零新增物品）

`BasicCellInventory` 实际会执行这三张卡的效果（源码 `appeng/me/cells/BasicCellInventory.java`）：

| 卡 | AE2 物品 | 对气体元件的效果 |
|---|---|---|
| 反相卡 | `ae2:inverter_card` | 分区列表白名单 ⇄ 黑名单 |
| 均分卡 | `ae2:equal_distribution_card` | 每种已存气体平均分配总容量 |
| 溢出销毁卡 | `ae2:void_card` | 元件满后销毁多余气体，避免卡网络 |

**模糊卡不做**：AE2 的气体 key 没有 fuzzy 值（AE2 自己的流体元件也没注册模糊卡），注册了也不会生效。

## 1. 槽位

AE2 的元件工作台预建 **8** 个升级槽，实际可用数 = 元件的 `getUpgrades().size()`。
气体元件建议从 **3 → 5** 槽，否则通用卡就占满了，新卡没地方放。

## 2. 气体专属卡候选

设计前提：把元件看成一只有**虚拟容积 V、额定气压 P** 的容器，
`容量(GU) = V(L) × P(Pa) / 100,000`（与 CCB 完全一致）。
基准：1k 元件 = 64,000 L @ 16 atm = 1,024,000 GU。

| # | 卡 | 建议上限 | 效果 | 备注 |
|---|---|---|---|---|
| 1 | **高压卡**（压力卡） | ×1 | 额定气压 16 → 24 atm（CCB 硬上限），容量 ×1.5 | 1k：1,024,000 → 1,536,000 GU；tooltip 显示"额定 24 atm" |
| 2 | **扩容卡** | ×4 | 每张虚拟容积 +50%，容量 ×(1+0.5n) | 4 张时 1k = 3,072,000 GU；与高压卡相乘 |
| 3 | **多类型卡** | ×2 | 每张 +4 种气体类型（5 → 9 → 13） | 受 AE2 `MAX_ITEM_TYPES = 63` 限制；解决 12 种气体要分多张元件 |
| 4 | **安全阀卡** | ×1 | 容量因拆卡/改配置变小时，自动把"超装"部分排空 | 避免 `已用字节 > 总字节` 的怪状态；拆卡安全 |
| 5 | 仪表卡 | ×1 | tooltip/GUI 显示当前等效压力、体积、百分比 | 纯 UI，成本低 |
| 6 | 安全卡（锁定） | ×1 | 仅 owner 可改配置/取出 | 借鉴 Applied Pneumatics 的安全卡；需要 owner 判定 |
| 7 | 气体族系卡 | ×1 | 分区到 CCB 气体标签族（natural/ultrawarm/ethereal/energized…） | 需要自定义分区匹配（AE2 分区按 key 不看 tag），实现较重 |
| 8 | 绝热/温度卡 | — | 未来若 CCB 气体带温度属性再做 | 当前 `api/gas` 里没有温度字段，暂不做 |

实现方式（1/2/3）：
- 覆写 `BasicStorageCell.getBytes(ItemStack)`（容量倍率）与 `getTotalTypes(ItemStack)`（类型数）；
  AE2 的 `BasicCellInventory` 在构造时读取这两个值，所以元件从工作台拿回驱动器后立即生效。
- 卡本身用 `Upgrades.createUpgradeCardItem` 创建，需要贴图/模型/配方/语言项/GuideME 页。
- 注意：容量最终以 **AE 字节**表达，所以装卡后元件显示的"字节数"会变大（1k 高压卡后按 1,536 字节计），
  tooltip 需要写清"额定气压/虚拟容积/等效容量"，避免玩家以为元件降级。

## 3. 推荐首批（4 张气体卡 + 3 张通用卡 + 5 槽）

高压卡、扩容卡、多类型卡、安全阀卡；通用卡注册反相/均分/溢出销毁；槽位 3→5。
第二批再做仪表卡、安全卡；族系卡等需要自定义分区匹配时再评估。

---

## 4. 实现状态（已完成，R5）

实现文件：

| 文件 | 内容 |
|---|---|
| `init/ACECards.java` | 4 张卡物品（`Upgrades.createUpgradeCardItem`）+ 为 5 档元件注册全部卡（AE2 通用 3 张 + 气体 4 张） |
| `cell/GasStorageCellItem.java` | 5 个升级槽、`getBytes`/`getTotalTypes` 按卡计算、安全阀卡的超装排气、tooltip 显示额定气压/虚拟容积/种类 |
| `ACEText.java` / `lang/{en_us,zh_cn}.json` | 卡名、卡说明、tooltip 文案 |
| `assets/.../textures/item/*_card.png` | 4 张卡贴图：重绘版（AE2 `card_void` 卡身为底，右侧接口换成 AE2 **高级卡**的青色像素，中央 8×8 刻纹分别为压力表 / 扩张箭头 / 四格 / 排气阀），由 `tools/gen_card_textures.py` 确定性生成（同输入同输出，可复现） |
| `assets/.../models/item/*_card.json` + `data/.../recipe/*_card.json` | 模型与配方 |
| `assets/.../ae2guide/{upgrades.md,_zh_cn/upgrades.md}` | GuideME 教程页（中英双语） |

数值实现：

* 容量倍率 = `(1 + 0.5 × 扩容卡数) × (1.5 ^ 高压卡数)`，`getBytes` 四舍五入，`getTotalTypes` = `min(63, 5 + 4×多类型卡数)`；
* 安全阀卡在 `IUpgradeInventory` 变化回调里检查 `已存 GU > 字节 × 1000`，超出部分用 `StorageCell.extract(..., IActionSource.empty())` 排掉并 `persist()`；
* 1 byte = 1,000 GU 的全局换算契约不变；卡只改变该元件的字节上限。

验证：`gradlew build` 通过，jar 122,204 B / 135 条目 / sha256 `38E67CB7…8139`；
183 模组全量包启动 run 8 通过（Mod List 含本模组、两条注册日志、101.0 s 到主菜单、0 报错、无崩溃），
详见 `docs/RUNTIME_VERIFICATION.md`。卡的实际插拔与容量变化仍需手动进游戏在元件工作台确认。