# AE2 ME Interface ↔ CCB gas pipes — compatibility note

Owner: ae2-researcher. Implementation: `src/main/java/com/appliedcraftedenergistics/integration/AeInterfaceGasCompat.java`.

This document records *why* the adapter is shaped the way it is, with the exact source references
that forced each decision. All AE2 lines are from `<workspace>\Applied-Energistics-2`
(v19.2.20), all CCB lines from `<workspace>\CreateCraftedBeginning` (0.1.1).

---

## 1. What was missing

AE2's **ME Interface** already holds gases. Nothing in our mod has to teach it that:

| Fact | Evidence |
|---|---|
| `InterfaceLogic` builds its stocking requests with `ConfigInventory.configStacks(slots)`, whose supported types default to **all** registered key types | `appeng/helpers/InterfaceLogic.java:105`, `appeng/helpers/externalstorage/GenericStackInv.java:84` |
| …and its storage with `ConfigInventory.storage(slots)` + a slot filter, again all key types | `InterfaceLogic.java:106`, `GenericStackInv.java:105` |
| Per-slot capacity for gas comes from our `GenericSlotCapacities.register(GasKeyType.TYPE, 64 × 1000 GU)` | `ACEGasIntegration.registerStrategies()`, `appeng/helpers/externalstorage/GenericStackInv.java:234-246` |
| It re-stocks from the network every tick and pushes overflow back, so it behaves like a self-refilling tank | `InterfaceLogic.java:296-330` (`updateStorage`/`usePlan`/`tryUsePlan`) |
| It exposes those slots as `AECapabilities.GENERIC_INTERNAL_INV` | `appeng/init/InitCapabilityProviders.java:122-135` |

What it does **not** do is expose CCB's `GasCapabilities.BLOCK`. AE2 has no idea CCB exists, and CCB's
pipe solver only connects to blocks that answer that capability. So a player could stock gas in an
interface and no pipe would ever take it.

## 2. What the adapter registers

`RegisterCapabilitiesEvent` → `GasCapabilities.BLOCK` on the block whose registry id is `ae2:interface`,
backed by that block's `GENERIC_INTERNAL_INV` inventory. Same shape as AppMek's
`GenericStackChemicalStorage` bridge for Mekanism chemicals, adapted to CCB's pressure model.

### 2.1 Why a `GasStorageHandler` and not a plain `GasHandler`

CCB's solver **discards** a storage endpoint that is not a `GasStorageHandler` with a positive-volume
compartment:

```java
// CCB gas/network/solver/endpoint/GasEndpointDiscovery.java — discoverStorageAccesses
GasHandler rawHandler = level.getCapability(GasCapabilities.BLOCK, adjacentPos, endpointFace.getOppositeFace());
if (!(rawHandler instanceof GasStorageHandler handler)) {
    return null;
}
for (int tank = 0; tank < handler.getTanks(); tank++) {
    GasPressureCompartment compartment = handler.getPressureCompartment(tank);
    if (compartment.getVolume() <= 0) {
        continue;                       // <-- silently no endpoint
    }
    accesses.add(new StorageAccess(endpointFace, compartment));
}
```

A multi-tank `GasHandler` therefore yields a pipe that visibly connects and moves nothing — the
documented "most likely integration bug". Hence: one `GasPressureCompartment` per AE slot, each with
a positive volume.

### 2.2 Why `PressureModel.FIXED` at 100 kPa

The planner special-cases fixed-pressure vessels — it asks them for everything instead of deriving a
limit from `amount / volume`:

```java
// CCB gas/network/solver/endpoint/GasEndpointPlanner.java — createPressureEndpoint
PressureModel pressureModel = physicalCompartment.getPressureModel();          // :260
long requestedDrain = pressureModel == PressureModel.FIXED ? Long.MAX_VALUE : currentAmount;   // :276
long requestedFill  = pressureModel == PressureModel.FIXED ? Long.MAX_VALUE
                      : maxAmount - currentAmount;                             // :288
double pressurePa   = clampToHardLimit(pressureModel == PressureModel.FIXED
                      ? physicalCompartment.getPressurePa()                    // :320
                      : GasPressure.pressureExact(currentAmount, volume));
```

`FIXED` + `getPressurePa() = GasPressure.REFERENCE_PRESSURE_PA` (100 kPa = 1 atm) gives the semantics
we want: *"the interface slot is a regulator held at one atmosphere; a pipe may take as much as it
likes, and gas flows only into vessels below 1 atm."*

It is deliberately **not** wired to `Config.interfaceSupplyPressureKpa`. That config belongs to our own
ME Gas Interface device, which has a real buffer vessel. Letting a server owner raise the pressure of
*fake* AE2 interface slots up to 2,400 kPa would let AE2 push a player's sealed tank toward CCB's
rupture limit — the interface must never be the reason a base explodes.

### 2.3 Why the virtual volume equals the slot capacity

CCB defines `1 GU = 1 litre at 100 kPa` (`GasPressure.REFERENCE_PRESSURE_PA`), and
`GasPressureCompartment#getMaxAmount()` defaults to `GasPressure.amount(volume, maxPressure)`.
At a fixed 100 kPa that is numerically `maxAmount == volume`.

So the compartment's volume is set to **the AE slot's gas capacity in GU** (64,000 with the current
ratio, read live from `inventory.getCapacity(GasKeyType.TYPE)` with a clamp against
`Long.MAX_VALUE`). Consequences:

* `getMaxAmount() == getMaxAmount(key)` on the AE side — the two systems agree on capacity.
* CCB's own gauges/tooltips (which divide stored by max) show a sane fill level.
* A full slot really is a 64 m³ vessel at exactly 1 atm, which is a physically consistent story.

### 2.4 Why `fill()` is always 0 (drain-only)

* AE2 owns the slots: it stocks them from the network and pushes the overflow back
  (`InterfaceLogic#updateStorage`). If a pipe could also insert, both systems would fight over the
  same slot each tick and every intermediate disagreement would duplicate or void gas.
* A pure source can never be pressurised by a pipe, so no overpressure path is needed here at all.
* The opposite direction (AE network → CCB machine) is already handled by our `StackExportStrategy`
  in `ACEGasIntegration`; AE2's Pattern Provider and Export Bus use it to push gas into an adjacent
  `GasHandler`.

`InterfaceGasHandler#fill`, `SlotCompartment#fill` and both `isGasValid` paths therefore reject
insertion, and `predictTransferLimits` reports `fillLimit = 0` (a *known* blocked direction, which is
more useful to the solver than "unknown").

### 2.5 Why the Pattern Provider is **not** registered

In 19.2.20 exactly two blocks publish `GENERIC_INTERNAL_INV`: `ae2:interface` and
`ae2:pattern_provider` (`InitCapabilityProviders.java:122-140`). The pattern provider's inventory is
`PatternProviderReturnInventory`, which **refuses extraction**:

```java
// appeng/helpers/patternprovider/PatternProviderReturnInventory.java:38-46
/** Used to prevent injection through the handlers when we are pushing items out in the network.
 *  Otherwise, a storage bus on the pattern provider could potentially void items. */
private boolean injectingIntoNetwork = false;

@Override public boolean canExtract() { return false; }
```

Exposing it to CCB would either move nothing (every drain returns 0 — the exact failure mode §2.1 is
about) or require bypassing AE2's deliberate guard, letting a gas pipe steal gas out of the
pattern-provider return queue. Pattern-provider gas flow stays on the export-strategy path.

If a future AE2 version changes this, the guard to revisit is `canExtract()`; the adapter body would
work unchanged.

### 2.6 Scope: block interface only, not the cable interface

`ae2:cable_interface` is a **part**; its inventory is published through
`RegisterPartCapabilitiesEvent` (`InitCapabilityProviders.java:230-232`), i.e. it is not a block
capability, and a cable position can host several parts. Bridging it would need an AE2 block-level
proxy that does not exist for foreign capabilities. Use the block ME Interface (or our own ME Gas
Interface device) where a CCB pipe must connect.

---

## 3. User-visible behaviour

| Setup | Result |
|---|---|
| ME Interface with a gas stocked in a slot, CCB pipe adjacent | Pipe **drains** the gas. AE2 immediately re-stocks from the network (up to the interface's stocking request and the 64 kGU per-slot cap), so the setup behaves like an unlimited gas source that is limited only by what the network holds and by `Config`-independent AE stocking speed (9 slots/tick). |
| ME Interface with gas but nothing on the CCB side below 1 atm | Nothing moves — the interface is a 1 atm source and CCB only flows down-gradient. Connect a vessel/pump that is at < 1 atm. |
| CCB pipe tries to push gas **into** the interface | Rejected (`fill() == 0`). Use the Export Bus or a Pattern Provider, or our own ME Gas Interface, to move gas from CCB into AE. |
| ME Interface empty, player expects the pipe to fill it | Not possible by design — AE2 fills its own slots from the network. |
| Gas in the interface, pipe network contains two different gases | Both flow: each slot is its own CCB endpoint group (`getCompartmentIdentity()` is per-slot), so each gas becomes its own `gasGroup` and is planned in its own pass. |
| Pipe touches the same interface on two faces | Two groups report the same drain limit for that slot; the executor drains for real, so the second drain returns 0 and the transfer is merely smaller than planned — no duplication, no voiding. |
| Interface broken / replaced | No stale state: the capability provider re-resolves the inventory on every query and returns `null` when it is gone. |

Because the interface re-stocks automatically, "put gas in an ME Interface slot and let CCB pipes take
it" works exactly like the fluid equivalent (ME Interface + fluid pipe), which is the behaviour players
expect.

### 3.1 Why one endpoint group per slot (not one per interface)

CCB groups storage accesses into endpoint groups by **identity** of
`GasPressureCompartment#getCompartmentIdentity()`:

```java
// CCB gas/network/solver/endpoint/GasEndpointPlanner.java:208-215
private StorageEndpointGroup(StorageAccess firstAccess) {
    compartmentIdentity = firstAccess.compartment().getCompartmentIdentity();
    accesses.add(firstAccess);
}
private boolean matches(StorageAccess access) {
    return compartmentIdentity == access.compartment().getCompartmentIdentity();   // identity, not equals
}
```

and then reads the group's gas from its **first** access:

```java
// GasEndpointPlanner.java:258-276
GasPressureCompartment physicalCompartment = usableAccesses.getFirst().compartment();
GasStack currentGas = physicalCompartment.getGasStack();
...
if (!currentGas.isEmpty()) {                       // <-- guard uses the FIRST slot only
    long requestedDrain = pressureModel == FIXED ? Long.MAX_VALUE : currentAmount;
    long drain = access.simulateDrainAmount(gas, requestedDrain);
```

So sharing one identity across all nine slots would let an **empty slot 0 mask gas sitting in slot 3** —
no drain would ever be planned. Returning `this` from each `SlotCompartment` gives one group per slot,
where the first access *is* the slot holding the gas. This is also the documented intent of the API
("independent compartments use their own identity").

---

## 4. Wiring

```java
// in AppliedCraftedEnergistics' constructor
modBus.addListener(EventPriority.LOWEST, AeInterfaceGasCompat::registerCapabilities);
```

`LOWEST` matters only as a matter of correctness-of-intent: the provider is runtime-safe, and a wrong
priority produces a warning in the log rather than a broken handler. The event itself has no priority
overload — NeoForge's `RegisterCapabilitiesEvent` is a plain `IModBusEvent`
(`javap net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent`: `registerBlock`,
`registerBlockEntity`, `isBlockRegistered`, no priority parameter); the priority is chosen when the
listener is added to the bus, exactly as AE2 does it
(`AppEngBase.java`: `modEventBus.addListener(EventPriority.LOWEST, InitCapabilityProviders::registerGenericAdapters)`).

---

## 5. API symbols used (all verified against the production jars with `javap`)

| Symbol | Signature |
|---|---|
| `net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent#registerBlock` | `<T, C> void registerBlock(BlockCapability<T, C>, IBlockCapabilityProvider<T, C>, Block...)` |
| `…#isBlockRegistered` | `boolean isBlockRegistered(BlockCapability<?, ?>, Block)` |
| `net.neoforged.neoforge.capabilities.IBlockCapabilityProvider#getCapability` | `T getCapability(Level, BlockPos, BlockState, BlockEntity, C)` |
| `appeng.api.AECapabilities#GENERIC_INTERNAL_INV` | `BlockCapability<GenericInternalInventory, @Nullable Direction>` |
| `appeng.api.behaviors.GenericInternalInventory` | `size()`, `getStack(int)`, `getKey(int)`, `getAmount(int)`, `getCapacity(AEKeyType)`, `getMaxAmount(AEKey)`, `canExtract()`, `canInsert()`, `isSupportedType(AEKeyType)`, `setStack(int, GenericStack)`, `insert(int, AEKey, long, Actionable)`, `extract(int, AEKey, long, Actionable)` |
| `net.ty.createcraftedbeginning.api.gas.GasCapabilities#BLOCK` | `BlockCapability<GasHandler, Direction>` |
| `…api.gas.handler.GasStorageHandler` | `extends GasHandler`; `GasPressureCompartment getPressureCompartment(int)` |
| `…api.gas.pressure.GasPressureCompartment` | `extends GasHandler`; `drain`×2, `fill`, `isGasValid`, `getGasStack`, `getStoredAmount`, `getVolume`, `getPressurePa`, `getMaxPressurePa`, `getCompartmentIdentity`, `getPressureModel`, `predictTransferLimits(GasStack,long)`, `captureTransferSnapshot` |
| `…api.gas.pressure.GasPressureCompartment.PressureModel` | `{ VARIABLE, FIXED }` |
| `…api.gas.pressure.GasPressureCompartment.PredictedTransferLimits` | `record(long drainLimit, long fillLimit)` |
| `…api.gas.GasPressure` | `REFERENCE_PRESSURE_PA = 100_000`; `amount(volume, pressurePa)` |
| `…api.gas.handler.GasTransferSnapshot` | `@FunctionalInterface void restore()` + `default void afterRestore()` |

Compile verification (no Gradle run, per build-engineer ownership): the file was compiled standalone
with `javac 21` against `appliedenergistics2-19.2.20.jar`, `createcraftedbeginning-0.1.1.jar`,
`create-1.21.1-6.0.10.jar`, the NeoForm `compiledWithNeoForge` Minecraft jar and the project's existing
`build/classes/java/main` — **0 errors, 0 warnings from this file**; it emits
`AeInterfaceGasCompat`, `AeInterfaceGasCompat$InterfaceGasHandler`, `AeInterfaceGasCompat$SlotCompartment`.

---

## 6. In-game verification checklist (needs a dev client)

1. `logs/latest.log` contains `CCB gas pipes can now drain gas from AE2's ME Interface (100 kPa fixed per slot)`.
2. Stock a gas in an ME Interface slot (click the gas in the interface's own list, or drag it in from
   JEI/REI); the slot fills up to 64 kGU from the network. *(Confirms §1: the interface stocks gas by
   itself, with no addon code beyond the key type + `GenericSlotCapacities`.)*
3. Place a CCB gas pipe next to the interface, with a tank/pump that is below 1 atm in the same network.
   The pipe should drain the slot and the interface should visibly re-fill.
4. Break the interface: the pipe's endpoint disappears, no crash, no ghost gas.
5. Try to push gas into the interface from a CCB tank at > 1 atm: nothing should enter (by design).
6. Two different gases in two slots: both drain, one after the other (see §3 last row).
7. AE2's own gas export path is unaffected: Pattern Provider with a gas pattern still pushes into an
   adjacent CCB machine via `StackExportStrategy`.
