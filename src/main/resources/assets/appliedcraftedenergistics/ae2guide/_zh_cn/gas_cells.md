---
navigation:
  parent: index.md
  title: 气体存储元件
  icon: appliedcraftedenergistics:gas_cell_256k
  position: 10
item_ids:
  - appliedcraftedenergistics:gas_cell_1k
  - appliedcraftedenergistics:gas_cell_4k
  - appliedcraftedenergistics:gas_cell_16k
  - appliedcraftedenergistics:gas_cell_64k
  - appliedcraftedenergistics:gas_cell_256k
  - appliedcraftedenergistics:gas_cell_1m
  - appliedcraftedenergistics:gas_cell_4m
  - appliedcraftedenergistics:gas_cell_16m
  - appliedcraftedenergistics:gas_cell_64m
  - appliedcraftedenergistics:gas_cell_256m
---

# 气体存储元件

<Row>
  <ItemImage id="gas_cell_1k" scale="3" />
  <ItemImage id="gas_cell_64k" scale="3" />
  <ItemImage id="gas_cell_1m" scale="3" />
  <ItemImage id="gas_cell_256m" scale="3" />
</Row>

气体存储元件把 **机械动力：机巧新生** 的气体存进 ME 网络。它们使用 AE2 原生元件库存，
因此分区、元件工作台、ME Drive、存储元件升级卡和终端显示都与物品/流体元件一致。

`1 AE 字节 = 1,000 GU`。CCB 定义 1 GU 为 100 kPa 参考压下 1 升气体，所以 1 字节相当于参考压下的
1 立方米气体。

## 档位

| 元件 | 字节 | GU 容量 | 配方前置 |
|---|---:|---:|---|
| 1k | 1,024 | 1,024,000 | — |
| 4k | 4,096 | 4,096,000 | — |
| 16k | 16,384 | 16,384,000 | — |
| 64k | 65,536 | 65,536,000 | — |
| 256k | 262,144 | 262,144,000 | — |
| 1M | 1,048,576 | 1,048,576,000 | MEGA Cells |
| 4M | 4,194,304 | 4,194,304,000 | MEGA Cells |
| 16M | 16,777,216 | 16,777,216,000 | MEGA Cells |
| 64M | 67,108,864 | 67,108,864,000 | MEGA Cells |
| 256M | 268,435,456 | 268,435,456,000 | MEGA Cells |

所有元件都支持 [元件升级卡](upgrades.md) 中介绍的气体升级卡。

## 配方

1k–256k 元件使用 AE2 物品元件外壳、AE2 存储组件，以及 CCB 气密板或气体罐。

1M–256M 元件以 **MEGA Cells** 为进度前置：

- `megacells:mega_item_cell_housing`
- `megacells:cell_component_<档位>`
- CCB 气密板或气体罐
- 1 个 CCB 气密储罐（1M/4M/16M）或 2 个（64M/256M）

它们的配方 JSON 带有 `neoforge:mod_loaded` 条件（`megacells`）。因此 **默认的 1M–256M 配方只有在
安装 MEGA Cells 时才会加载和显示**。

即使没有 MEGA Cells，高容量元件本身仍会注册；可以通过创造模式或命令获得，但没有默认生存配方。

## 删除默认 MEGA 配方

配置项：

```text
recipes.enable_mega_cell_recipes = true
```

控制是否加载默认的 MEGA-gated 1M–256M 配方。在
`config/appliedcraftedenergistics-common.toml` 中设为 `false`，即可彻底移除这些默认配方；
之后可以用数据包提供自己的配方。

这个开关与 MEGA Cells 的 `mod_loaded` 检查相互独立：即使安装了 MEGA Cells，关闭该配置项后默认配方也不会加载。

## 不安装 MEGA Cells 时启用 1M–256M

配方是普通 JSON 数据。世界或整合包可以提供一个同路径的数据包文件来覆盖默认配方：

```text
data/appliedcraftedenergistics/recipe/gas_cell_1m.json
```

删除 `neoforge:conditions`，把 `megacells:*` 材料替换成你希望使用的材料。例如，一个使用 AE2
组件的 1M 配方：

```json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    {"item": "ae2:item_cell_housing"},
    {"item": "ae2:cell_component_256k"},
    {"item": "createcraftedbeginning:airtight_sheet"},
    {"item": "createcraftedbeginning:airtight_tank"}
  ],
  "result": {
    "count": 1,
    "id": "appliedcraftedenergistics:gas_cell_1m"
  }
}
```

对 `gas_cell_4m.json`、`gas_cell_16m.json`、`gas_cell_64m.json`、`gas_cell_256m.json` 做同样处理。
配方 id 和产物 id 必须保持不变。

这是数据包覆盖，不是配置开关：只影响加载该数据包的世界/整合包，不修改已安装的 mod jar。

## 另见

- [元件升级卡](upgrades.md)
- [气体 P2P 隧道](gas_p2p_tunnel.md)
- [应用机巧能源](index.md)
