---
navigation:
  parent: index.md
  title: 元件升级卡
  icon: appliedcraftedenergistics:pressure_card
  position: 20
item_ids:
  - appliedcraftedenergistics:pressure_card
  - appliedcraftedenergistics:volume_card
  - appliedcraftedenergistics:type_expansion_card
  - appliedcraftedenergistics:relief_valve_card
---

# 元件升级卡

<Row>
  <ItemImage id="pressure_card" scale="4" />
  <ItemImage id="volume_card" scale="4" />
  <ItemImage id="type_expansion_card" scale="4" />
  <ItemImage id="relief_valve_card" scale="4" />
</Row>

气体存储元件共有 **5 个升级卡槽**，在 **元件工作台** 里安装。其中 3 张是 AE2 原生的存储元件升级卡，
4 张是气体专属卡。

## AE2 通用卡（对气体元件同样有效）

| 卡 | 对气体元件的效果 |
|---|---|
| <ItemLink id="ae2:inverter_card" /> | 分区列表在"白名单/黑名单"之间切换 |
| <ItemLink id="ae2:equal_distribution_card" /> | 把元件容量平均分给已存的气体种类 |
| <ItemLink id="ae2:void_card" /> | 元件满后销毁多余气体，避免堵塞网络 |

（模糊卡刻意不支持：气体 key 没有 fuzzy 值，AE2 自己的流体元件也没有注册它。）

## 气体专属卡

气体元件相当于一只"虚拟压力容器"：`容量(GU) = 容积(L) × 额定气压(Pa) / 100,000`。下面每张卡都在改其中一个量，
所以数值可以直接和气管上的数字对照。

### 高压卡

<ItemImage id="pressure_card" scale="2" />

把元件的**额定气压从 16 atm（CCB 安全线）提升到 24 atm（CCB 硬上限）**——也就是气体系统的物理上限，
容量 ×1.5。每个元件只能装 1 张，因为 24 atm 已经到顶。

1k 元件：1,024,000 GU → **1,536,000 GU**（= 64,000 L @24 atm）。

### 扩容卡

<ItemImage id="volume_card" scale="2" />

每张增加 **50% 虚拟容积**，最多 4 张（4 张时 ×3），与高压卡相乘叠加。

1k 元件：1,024,000 GU → 1,536,000 GU（1 张）→ 3,072,000 GU（4 张、不装高压卡）。

### 多类型卡

<ItemImage id="type_expansion_card" scale="2" />

每张多允许 **4 种气体**（基础 5 → 9 → 13）。CCB 一共有 12 种气体，没有这张卡时，光是"种类数"就要占掉三张元件。

### 安全阀卡

<ItemImage id="relief_valve_card" scale="2" />

当升级变化让元件**容量小于已有存量**时（例如拆掉扩容卡），自动把超出的气体排掉，而不是让元件永久处于"超装"状态。
不装这张卡时元件仍能工作，但会拒绝再进气，直到手动清空，tooltip 会提示超装。

## 容量是如何显示的

容量本质上是 AE2 的字节记账，所以装卡后元件显示的字节数会比档位更大。tooltip 会同时用气体语言列出：

- 容量（GU，= 字节 × 1,000）；
- 参考压下的等效体积；
- 额定气压（atm）；
- 虚拟容积（升）；
- 可存气体种类数。

## 配方

<RecipeFor id="appliedcraftedenergistics:pressure_card" />
<RecipeFor id="appliedcraftedenergistics:volume_card" />
<RecipeFor id="appliedcraftedenergistics:type_expansion_card" />
<RecipeFor id="appliedcraftedenergistics:relief_valve_card" />
