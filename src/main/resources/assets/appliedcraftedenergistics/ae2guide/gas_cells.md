---
navigation:
  parent: index.md
  title: Gas Storage Cells
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

# Gas Storage Cells

<Row>
  <ItemImage id="gas_cell_1k" scale="3" />
  <ItemImage id="gas_cell_64k" scale="3" />
  <ItemImage id="gas_cell_1m" scale="3" />
  <ItemImage id="gas_cell_256m" scale="3" />
</Row>

Gas storage cells put **Create: Crafted Beginning gas** into the ME network. They use AE2's normal
cell inventory, so partitioning, the Cell Workbench, ME Drives, storage-cell upgrades and the terminal
work exactly as they do for item and fluid cells.

`1 AE byte = 1,000 GU`. CCB defines 1 GU as one litre of gas at 100 kPa, so one byte is one cubic
metre at reference pressure.

## Tiers

| Cell | Bytes | GU capacity | Recipe prerequisite |
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

All cells accept the gas-specific upgrade cards described in [Cell Upgrade Cards](upgrades.md).

## Recipes

The 1k–256k cells use AE2's item cell housing, an AE2 storage component, and a CCB airtight sheet or
gas canister.

The 1M–256M cells use **MEGA Cells** as their progression prerequisite:

- `megacells:mega_item_cell_housing`
- `megacells:cell_component_<tier>`
- CCB airtight sheet or gas canister
- one CCB airtight tank (1M/4M/16M) or two (64M/256M)

Their recipe files contain a `neoforge:mod_loaded` condition for `megacells`. Therefore the **default
1M–256M recipes only load and display when MEGA Cells is installed**.

The high-tier cell items themselves are still registered without MEGA Cells. They can be obtained
through the creative menu or commands, but have no default survival recipe.

## Enabling 1M–256M without MEGA Cells

Recipe files are ordinary JSON data. A world or modpack datapack can override the default recipe by
providing a file with the same path:

```text
data/appliedcraftedenergistics/recipe/gas_cell_1m.json
```

Remove the `neoforge:conditions` block and replace the `megacells:*` ingredients with materials you
want to use. For example, a minimal 1M recipe using AE2 components:

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

Do the same for `gas_cell_4m.json`, `gas_cell_16m.json`, `gas_cell_64m.json` and
`gas_cell_256m.json`. The recipe ids and result ids must stay unchanged.

This is a datapack override, not a config option: it changes recipes for the world/pack that loads the
datapack, without changing the installed mod jar.

## See Also

- [Cell Upgrade Cards](upgrades.md)
- [Gas P2P Tunnel](gas_p2p_tunnel.md)
- [Applied Crafted Energistics](index.md)
