# Device subsystem notes — ME Gas Interface

Scope: everything under `com.appliedcraftedenergistics.device`, `com.appliedcraftedenergistics.client`
(device screen only) and the `ACEBlocks` / `ACEBlockEntities` / `ACEMenus` / `ACEScreens` /
`ACECapabilities` registration classes. Written by the device workstream; the build/asset
workstreams own their own documents.

## 1. What the block is

`appliedcraftedenergistics:me_gas_interface` is an AE2 machine (needs a channel, draws idle power) that owns
a **real** Create: Crafted Beginning pressure vessel — `com.appliedcraftedenergistics.core.GasBufferTank`.

* To AE2 it is a normal grid block (`AENetworkedBlockEntity`, `GridFlags.REQUIRE_CHANNEL`,
  idle power = `Config.COMMON.interfaceIdlePower`).
* To CCB it is a normal gas tank: `ACECapabilities` registers `GasCapabilities.BLOCK` for every side,
  returning the very same `GasBufferTank`. CCB pipes, pumps and gauges can therefore attach,
  pressurise and drain it exactly as they would a `GasTank` block.
* The two systems never exchange gas directly. Every transfer goes through the buffer, and the buffer
  enforces CCB's own pressure ceiling (24 atm hard limit), so the interface can never bypass CCB's
  overpressure rules.

That is the whole answer to "how does a pressure-less quantum store talk to a physical gas network".

## 2. Files

| File | Role |
| --- | --- |
| `device/GasInterfaceMode.java` | `IMPORT` / `EXPORT` / `BOTH` / `OFF` enum + display names + `cycle()` |
| `device/GasInterfaceBlock.java` | AE block, orientation, GUI opening, block item with description tooltip |
| `device/GasInterfaceBlockEntity.java` | buffer tank, mode, upgrades, per-tick ME ↔ buffer exchange, NBT |
| `device/GasInterfaceMenu.java` | `UpgradeableMenu`, `@GuiSync` mirror fields, mode cycle client action |
| `client/GasInterfaceScreen.java` | `UpgradeableScreen`, layout from AE2 style JSON, no new art |
| `client/ACEScreens.java` | binds menu type → screen (`RegisterMenuScreensEvent`), idempotent |
| `client/ACEDeviceClient.java` | `@EventBusSubscriber(..., value = Dist.CLIENT)` entry point |
| `init/ACEBlocks.java` | block + block item registration, speed-card upgrade association |
| `init/ACEBlockEntities.java` | BE type, ticker + block item association (AE2 pattern) |
| `init/ACEMenus.java` | menu type (`MenuTypeBuilder.buildUnregistered`) |
| `init/ACECapabilities.java` | AE grid node host, CCB gas handler, item handler |
| `resources/assets/ae2/screens/me_gas_interface.json` | screen layout (see §7) |

## 3. Entry points (main class wiring)

```java
ACEBlocks.register(IEventBus modBus);          // block + block item + upgrade association
ACEBlockEntities.register(IEventBus modBus);   // BlockEntityType
ACEMenus.register(IEventBus modBus);           // MenuType
ACECapabilities.register(IEventBus modBus);    // RegisterCapabilitiesEvent listeners

ACEBlocks.ME_GAS_INTERFACE                       // DeferredBlock<GasInterfaceBlock>
ACEBlocks.ME_GAS_INTERFACE_ITEM                  // DeferredItem<GasInterfaceBlock.Item>
ACEBlocks.getMeGasInterface()                    // GasInterfaceBlock
ACEBlockEntities.ME_GAS_INTERFACE                // DeferredHolder<BlockEntityType<?>, BlockEntityType<GasInterfaceBlockEntity>>
ACEMenus.ME_GAS_INTERFACE                        // DeferredHolder<MenuType<?>, MenuType<GasInterfaceMenu>>
```

Client side needs **no** main-class wiring: `client/ACEDeviceClient` is annotated with
`@EventBusSubscriber(modid = "appliedcraftedenergistics", value = Dist.CLIENT)`
and registers the screen itself. `ACEScreens.register(IEventBus)` also exists (idempotent, safe to call
from a manual client setup if preferred).

## 4. Transfer rules (what happens every server tick)

`serverTick()` returns immediately when the block is `OFF`, when the grid node is not active (no
channel or no power), or when there is no grid. Otherwise, per mode:

| Mode | Behaviour |
| --- | --- |
| `IMPORT` (default) | buffer → ME network. What CCB pushed in is collected into the network. |
| `EXPORT` | ME network → buffer, until the buffer holds the amount that equals the block's supply pressure setpoint. CCB pipes can then take the gas away at a physical pressure. |
| `BOTH` | pressure regulator: above the supply pressure it imports the surplus, below it the network tops the vessel up. |
| `OFF` | no AE-side movement (the vessel still works as a CCB tank). |

* **Supply pressure setpoint**: per block, in kPa, NBT tag `supplyPressureKpa`. It starts at
  `Config.COMMON.interfaceSupplyPressureKpa` and is adjustable in the GUI within `0..1600 kPa` (CCB's
  safe limit) in 100 kPa steps — the same "dial in the pressure you want" idea as Applied Pneumatics'
  ME Pressure Interface. A config default above 1600 kPa is accepted up to the vessel's hard rating
  (2400 kPa) and then reported as overpressure in the GUI; the GUI buttons can only lower it into the
  safe range.
* **Rate**: `Config.COMMON.interfaceTransferRate` GU/tick × `(1 + speedCards)`.
* **Energy**: `Config.COMMON.interfaceEnergyPerKiloGasUnit` AE per 1,000 GU, charged through AE2's
  `StorageHelper.poweredInsert` / `poweredExtraction` with `Actionable.SIMULATE` first. If the network
  cannot pay, nothing moves and — importantly — nothing is removed from either side (the transfer is
  always simulated on both sides before being committed).
* **Pressure**: the AE side only ever fills the buffer to `min(supply pressure, rated pressure)`. The
  vessel's own `getMaxAmount()` (volume × 24 atm) is the absolute ceiling, so a network can never
  overpressure a CCB pipe network through this block.
* **Export gas selection**: gas keys have no item form, so there is no filter slot to put a "gas
  sample" in. The interface exports the gas it already holds; if the buffer is empty it asks the
  adjacent CCB gas handlers what gas they hold. An export interface therefore matches the gas of the
  network it is attached to instead of needing a picker UI.

Conversions all come from `gas/GasAmounts` (1 AE byte = 1,000 GU); the device code never invents a
ratio of its own.

## 5. Pressure semantics recap

* Reference pressure 100 kPa = 1 atm.
* Safe operating pressure 16 atm (1.6 MPa) — above it the GUI shows the overpressure hint when the
  configured supply pressure is above it.
* Hard ceiling 24 atm (2.4 MPa) — the vessel's rating; nothing can push past it.
* `interfaceSupplyPressureKpa` outside `0..2400` is clamped by config; the value shown in the GUI is the
  clamped one.

## 6. GUI

Menu: `GasInterfaceMenu extends UpgradeableMenu<GasInterfaceBlockEntity>` (AE2 `@GuiSync` fields, so
the client never reads block entity state). Two client actions, both server-authoritative:
`cycle_mode` and `adjust_supply_pressure` (payload: delta in kPa).

Contents:

* title — `ACEText.INTERFACE_TITLE`
* **Buffer** — gas display name, `stored / capacity` in GU (via `GasAmounts.format`, i.e. `kGU`)
* **Pressure** — `current / rated` (via `GasAmounts.formatPressure`, CCB's own kPa/MPa formatter)
* status line — `gui.appliedcraftedenergistics.interface.overpressure` when the active setpoint is above CCB's
  safe 16 atm, `gui.appliedcraftedenergistics.interface.blocked` when the buffer is at its pressure ceiling
* **Mode** — label + a button whose caption is the current mode; clicking cycles it
* **Supply pressure** — label + `[-] <value> kPa [+]` buttons, 100 kPa per click, 0..1600 kPa
* upgrade slots on the right (up to 4 speed cards) and the player inventory, both positioned by AE2's
  style fragments.

`@GuiSync` fields: `mode` (10), `storedAmount` (11), `maxAmount` (12), `pressurePa` (13),
`maxPressurePa` (14), `supplyPressureKpa` (15), `atPressureCeiling` (16), `supplyAboveSafe` (17),
`gasName` (18). Ids 0/1/5/6 belong to `UpgradeableMenu`.

Only existing `ACEText` keys plus one new key are used:
`gui.appliedcraftedenergistics.interface.supply_pressure` (label of the supply pressure row).

## 7. GUI assets — none required

AE2 style documents are resolved with `AppEng.makeId(...)`, i.e. always in the **`ae2:`** namespace, so
a style file for an addon screen must live at `assets/ae2/screens/<name>.json`. This subsystem ships
exactly one such file, `assets/ae2/screens/me_gas_interface.json`, and it references **no new art**:

| Element | Source |
| --- | --- |
| dialog background | `generatedBackground` (AE2 `BackgroundGenerator`, procedurally drawn panel, 176×207) |
| palette, player inventory positions, upgrade panel anchor | AE2 includes `common/palette.json` (via `common/common.json`), `common/player_inventory.json`, `common/toolbox.json` |
| mode button | AE2 `AE2Button` widget (AE2's own widget textures) |

For the record, the AE2 textures the *alternatives* would have used (deliberately unused):
`ae2:textures/guis/interface.png`, `ae2:textures/guis/background.png`,
`ae2:textures/guis/extra_panels.png`, `ae2:textures/guis/states.png`.

## 8. Assets expected from the assets workstream

Already delivered / confirmed by assets-engineer; listed here as the contract:

| Asset | Path / name |
| --- | --- |
| blockstate | `assets/appliedcraftedenergistics/blockstates/me_gas_interface.json` — 4 variants, `facing=north/east/south/west` → y 0/90/180/270, all → `appliedcraftedenergistics:block/me_gas_interface` |
| block model | `assets/appliedcraftedenergistics/models/block/me_gas_interface.json` |
| item model | `assets/appliedcraftedenergistics/models/item/me_gas_interface.json` (parent = block model) |
| loot table | `data/appliedcraftedenergistics/loot_table/blocks/me_gas_interface.json` |
| recipe | `data/appliedcraftedenergistics/recipe/` |
| lang | `block.appliedcraftedenergistics.me_gas_interface`, `block.appliedcraftedenergistics.me_gas_interface.desc`, `gui.appliedcraftedenergistics.interface.{buffer,pressure,mode,overpressure,blocked,supply_pressure}`, `gui.appliedcraftedenergistics.mode.{import,export,both,off}` |

`gui.appliedcraftedenergistics.interface.supply_pressure` is the only key the device workstream added
(label of the supply pressure row, e.g. "Supply pressure" / "供给压力").

Block state contract (do not change without changing the block): **exactly one** property `facing`,
`BlockStateProperties.HORIZONTAL_FACING`, four horizontal values, contributed by
`OrientationStrategies.horizontalFacing()` on the block (AE2's `AEBaseBlock` adds it in
`createBlockStateDefinition` and handles placement in `getStateForPlacement`). No `spin`, no
`powered`/`active`, no `waterlogged`.

## 9. AE2 19.2.20 API notes (verified against `libs/appliedenergistics2-19.2.20.jar` with javap)

* `appeng.api.storage.StorageHelper` is in `appeng.api.storage`, **not** `appeng.me.helpers`. Its
  powered helpers take an `IEnergySource`, an `MEStorage`, an `AEKey`, a `long`, an `IActionSource`
  and an optional `Actionable`.
* The energy cost inside those helpers is `amount / key.getAmountPerOperation()` — with our
  `getAmountPerOperation() == 1000` that is exactly "AE per kGU", so the configured price and AE2's
  helper agree without us re-implementing the power maths.
* `MenuTypeBuilder.build(ResourceLocation)` also queues the type in AE2's own registry pass
  (`InitMenuTypes.queueRegistration`) — using it together with a `DeferredRegister<MenuType<?>>` would
  double-register. `buildUnregistered(ResourceLocation)` is the correct call for addons; it still
  installs the `MenuOpener` entry, so `MenuOpener.open(...)` works.
* `AEBaseEntityBlock.getTicker` / `newBlockEntity` need `setBlockEntity(Class, BlockEntityType,
  clientTicker, serverTicker)` to be called during block-entity-type registration (AE2's own pattern);
  the block entity item association (`AEBaseBlockEntity.registerBlockEntityItem`) should be made there
  as well.
* `@GuiSync` supports `int`, `long`, `double`, `boolean`, `String`, enums, `Component`, `ResourceLocation`
  and `GenericStack`. Sync ids `0, 1, 5, 6` are taken by `UpgradeableMenu`, so the device menu uses 10+.
* `ScreenStyle.getWidget` **throws** for an unknown widget id and `drawText` dereferences the style's
  static text, so every id passed to `widgets.addButton` / `setTextContent` must exist in the style
  JSON. Dynamic texts therefore declare `"text": {"text": ""}` as a placeholder.
* `AEBaseScreen` NPEs if a style text entry has neither static text nor an override — placeholders are
  mandatory for code-driven text.
* NeoForge 1.21.1 capability lookup for blocks is `level.getCapability(cap, pos, side)` (from
  `ILevelExtension`); the `BlockCapability#getCapability(Level, BlockPos, BlockState, BlockEntity, C)`
  overload exists for the low-level path.
* `InteractionResult.SUCCESS_NO_ITEM_USED` exists in this NeoForge/MC build (it is present in the
  mapped `compiledWithNeoForge` artifact), so the block uses it like AE2's own machines.

## 10. Known limitations / follow-ups

1. `EXPORT` mode uses the buffer's current gas, or the adjacent gas network's gas when the buffer is
   empty. There is no per-block gas filter UI; that would need a `GenericStackInv` filter + a way to
   represent a gas in a fake slot (gas keys have no item form).
2. Pressure is per-block (config default + GUI setpoint). The vessel's *volume* and *rating* are not
   upgradable; speed cards raise the transfer rate only. A "high-pressure tier" gated behind items is a
   design option, not implemented (see PLAN §5.2).
3. There is no canister/container slot yet (an Applied Pneumatics style "fill the item in the slot"
   behaviour). It would need CCB's `api.canister` item capability; skipped because the GUI already has a
   full set of controls and the API should be confirmed against the production jar first.
4. The block entity does not push any visual state to the client (no BER/tint), so the block model has
   no `active`/`powered` variant and stays a plain cube.
