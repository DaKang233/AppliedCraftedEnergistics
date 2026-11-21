# Gas Storage Cells

This note records the recipe policy for the gas storage cells, especially the 1M–256M tiers.

## In-game guide

The guidebook page `gas_cells.md` is the primary in-game documentation for every gas cell tier.
It is linked from the main ACE guide index and lists all ten cell items in its `item_ids`
frontmatter. Hovering one of these cells and pressing the GuideME hotkey (**G** by default) opens
the page directly.

## Recipes

- **1k–256k**: AE2 item cell housing + AE2 storage component + CCB airtight sheet or gas canister.
- **1M–256M**: MEGA Cells item cell housing + MEGA cell component + CCB airtight sheet/gas canister
  + one CCB airtight tank (1M/4M/16M) or two (64M/256M).

The 1M–256M recipe JSON files carry:

```json
"neoforge:conditions": [
  {
    "type": "neoforge:mod_loaded",
    "modid": "megacells"
  }
]
```

Therefore their **default recipes only load and display when MEGA Cells is installed**.

The high-tier cell items are still registered without MEGA Cells. They can be obtained from the
creative menu or commands, but have no default survival recipe.

## Enabling 1M–256M without MEGA Cells

Create a datapack override with the same recipe path, for example:

```text
data/appliedcraftedenergistics/recipe/gas_cell_1m.json
```

Remove the `neoforge:conditions` block and replace the `megacells:*` ingredients with materials
you want. The recipe id and result id must remain unchanged. Apply the same edit to
`gas_cell_4m.json`, `gas_cell_16m.json`, `gas_cell_64m.json` and `gas_cell_256m.json`.

A minimal 1M example:

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

This is a data-pack override, not a config switch. It applies only to the world/modpack that loads
the datapack and does not modify the installed mod jar.
