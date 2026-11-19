# Create: Crafted Beginning — Gas API handbook

Target: **createcraftedbeginning 0.1.1** (NeoForge 21.1.233, MC 1.21.1, Create 6.0.10)
Audience: implementers of **Applied Crafted Energistics** (`com.appliedcraftedenergistics`).
Companion file: `docs/CCB_GAS_EVIDENCE.md` (every claim here carries a `path:line` citation and `javap` proof there).

Source root used for citations: `<workspace>\CreateCraftedBeginning\src\main\java\net\ty\createcraftedbeginning\`
Shipped jar used for signature verification: `libs\createcraftedbeginning-0.1.1.jar`.

---

## 0. Ten rules before you type anything

1. **Gas amounts are `long` "GU" (gas units).** `1 GU = 1 litre of gas at 1 atm (100 000 Pa)`. There is **no** mB/bucket conversion and gases are **not** fluids.
2. **Pressure is never stored.** It is derived: `pressurePa = GU * 100000 / volumeLiters`. Volumes are `long` **litres**; configs are written in **kL** (1 kL = 1000 L).
3. **The capability is `GasCapabilities.BLOCK`**, a `BlockCapability<GasHandler, @Nullable Direction>` created with `createSided` on id `createcraftedbeginning:gas_handler`.
4. **A plain `GasHandler` is not enough for pipes.** CCB pipes will *connect* to any block exposing the capability, but the solver only *transfers* with blocks that implement `GasStorageHandler` (with `getPressureCompartment(t).getVolume() > 0`) or a single-tank `GasPressureBoundary`.
5. **`SIMULATE` must be pure** and `getTanks()`/`getGasInTank()` must not mutate. The pipe solver simulates constantly, and it may **execute then roll back** a fill via `captureTransferSnapshot()`. Treat your handler as a transactional sink.
6. **You get push/pull for free** once you register the capability and implement `GasStorageHandler`: the pipe network each tick solves pressures and moves gas by pressure difference. No pump code needed on your side.
7. **Safe ceiling 16 atm, hard ceiling 24 atm** (`GasPressureLimits.SAFE_PRESSURE_PA` / `HARD_PRESSURE_PA`). A **storage block** held above 16 atm accumulates stress and ruptures after ~5 s at the hard limit, releasing its gas plus a small explosion. Pipes/valves do not rupture — they just carry the pressure onward.
8. **Registration is a NeoForge registry** `createcraftedbeginning:gas` (defaulted, synced, intrusive holders). You may add your own gases with `CCBGasDeferredRegister`, but for AE integration you only need to *look gases up*, never create them.
9. **`api.**` is declared public integration surface; `gas.**` is implementation.** Use `api.*` types as your contract; if you touch `gas.*` helpers (`GasTank`, `GasPressureFillService`, …) accept that they are unversioned internals.
10. **0.1.1 is early.** Pin the version range tightly and isolate CCB calls behind one adapter package.

---

## 1. Gas type and gas stacks

### 1.1 `Gas` — the type object (`api.gas.Gas`)

One `Gas` instance per gas type. It is **not** a fluid and has no vanilla analogue; it carries texture, tint and tags only.

```java
public class Gas {
    public static final Codec<Holder<Gas>> HOLDER_CODEC;                                   // Gas.java:32
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Gas>> HOLDER_STREAM_CODEC; // :33
    public static final Holder<Gas> EMPTY_GAS_HOLDER;                                       // :36  createcraftedbeginning:empty

    public static Gas findById(ResourceLocation location);                                  // :87  returns EMPTY gas if absent
    public static Optional<Holder<Gas>> parseHolder(Provider lookup, Tag tag);              // :58
    public static Holder<Gas> parseOptionalHolder(Provider lookup, String id);              // :63

    public Holder<Gas> getHolder();                  // :132
    public ResourceLocation getResourceLocation();   // :140
    public String getTranslationKey();               // :96  "gas.<namespace>.<path>"
    public Component getName(GasStack stack);        // :152
    public int getTint();                            // :144 ARGB32.color(alpha, tint)
    public int getTint(GasStack stack);              // :148
    public ResourceLocation getTexture();            // :136
    public boolean isEmpty();                        // :115 true only for the empty gas
    public boolean is(TagKey<Gas> tag);              // :111
    public Set<TagKey<Gas>> getTags();               // :164
    public List<Component> getTooltip(GasStack s);   // :160
    public boolean isUsableInEquipment();            // :128 airtight tools/armour only; irrelevant to storage
}
```

Lookup patterns:

```java
// by ResourceLocation (never null; empty gas if unknown)
Gas gas = Gas.findById(ResourceLocation.fromNamespaceAndPath("createcraftedbeginning", "steam"));

// by Holder, from a registry lookup provider (BE save/load, packets)
Optional<Holder<Gas>> holder = Gas.parseHolder(registryLookupProvider, nbtTag);
Holder<Gas> h = gas.getHolder();               // the intrusive registry holder for a registered gas
boolean empty = gas.isEmpty();                // do not compare against null
```

`Gas` instances registered through the registry get an **intrusive holder in their constructor** (`Gas.java:38`). Never `new Gas(...)` outside a `DeferredRegister` registration — it throws.

### 1.2 Registry (`api.gas.GasRegistries`)

```java
ResourceKey<Registry<Gas>> GAS_REGISTRY_KEY = createcraftedbeginning:gas;   // GasRegistries.java:15
ResourceKey<Gas> EMPTY_GAS_KEY             = createcraftedbeginning:empty; // :16
DefaultedRegistry<Gas> GAS_REGISTRY        = ... .defaultKey(EMPTY_GAS_KEY).sync(true).withIntrusiveHolders(); // :19
```

Because it is a **defaulted** registry, an unknown id resolves to `createcraftedbeginning:empty` instead of throwing; a missing gas therefore surfaces as `GasStack.EMPTY`, not as a crash. Always check `isEmpty()`.

### 1.3 `GasStack` — amount + gas + data components (`api.gas.GasStack`)

```java
public final class GasStack implements MutableDataComponentHolder {
    public static final GasStack EMPTY;                            // GasStack.java:39
    public static final Codec<GasStack> CODEC;                     // :88   {id, amount, components?}
    public static final Codec<GasStack> OPTIONAL_CODEC;            // :89   empty allowed
    public static final StreamCodec<..., GasStack> OPTIONAL_STREAM_CODEC; // :41  empty allowed
    public static final StreamCodec<..., GasStack> STREAM_CODEC;   // :66   THROWS on empty

    public GasStack(Gas gasType, long amount);                     // :107
    public GasStack(Holder<Gas> holder, long amount);              // :103
    public GasStack(Gas gasType, long amount, DataComponentPatch p); // :99

    public long getAmount();          // :317  0 when empty
    public void setAmount(long);      // :325  clamped to >= 0
    public void grow(long);           // :341  saturating
    public void shrink(long);         // :333  saturating
    public GasStack copyWithAmount(long amount);  // :299  EMPTY if amount <= 0
    public GasStack copy();                       // :309
    public boolean isEmpty();                     // :255
    public Gas getGasType();                      // :283  EMPTY gas when empty
    public Holder<Gas> getGasHolder();            // :247
    public boolean is(Gas g);  public boolean is(Holder<Gas> h);
    public boolean is(TagKey<Gas> tag);  public boolean is(HolderSet<Gas> set);
    public static boolean matches(GasStack a, GasStack b);              // :154  amount AND gas AND components
    public static boolean isSameGasSameComponents(GasStack a, GasStack b);// :158  ignores amount  <-- use this
    public DataComponentPatch getComponentsPatch();                    // :291
    public Tag save(Provider p);                                       // :353  throws when empty
    public Tag save(Provider p, Tag tag);                              // :361
    public Tag saveOptional(Provider p);                               // :369  empty -> new CompoundTag()
    public static GasStack parseOptional(Provider p, CompoundTag tag); // :142  empty on failure
    public static Optional<GasStack> parse(Provider p, Tag tag);       // :150
    public Component getHoverName();                                   // :377
    public String getTranslationKey();                                 // :381
    public int getHint();                                              // :385 tint
}
```

**Amount semantics**

* Valid amounts are `1 .. Long.MAX_VALUE`; `GasStack` constructors and `setAmount` normalise `amount <= 0` to `EMPTY` and `POSITIVE_AMOUNT_CODEC` (`GasStack.java:87`) rejects non-positive amounts on load.
* `new GasStack(someGas, 0)` silently yields `GasStack.EMPTY` — no exception, no warning.
* There is **no** "capacity" on a stack; capacity belongs to a handler/tank/compartment.

**Unit `GU`**

`GasUnits` (`api.gas.GasUnits.java:11-14`):

```java
GU_PER_KGU = 1000; GU_PER_MGU = 1_000_000; GU_PER_GGU = 1_000_000_000;
LITERS_PER_KILOLITER = 1000;
static int toInt(long gu);   // clamp to non-negative int
static int toKilo(long gu);  // gu / 1000, clamped
```

The GU↔litre↔Pa relation is exactly (`api.gas.GasPressure.java:13,39-49,63-69`):

```
REFERENCE_PRESSURE_PA = 100_000        // = 1 atm
pressure(gasAmount, volumeLiters)  = gasAmount * 100_000 / volumeLiters   (saturating, 0 if amount<=0, MAX_VALUE if volume<=0)
amount(volumeLiters, pressurePa)   = volumeLiters * pressurePa / 100_000
```

CCB states the same relation in config prose: *"At 1 atm, each litre holds 1 GU"* (`config/CCBMachines.java:118`). So:

```
1 GU  == 1 litre at 1 atm  == 0.001 mB-equivalent of "standard litres"
1 kGU == 1000 GU == 1 kL at 1 atm
```

**There is no bucket/mB relation and no fluid conversion anywhere in the gas API.** `GasStack` has no fluid counterpart, no `FluidStack` adapter, no Forge gas capability. If you need "volume at 1 atm" arithmetic in AE2, do it with `GasPressure.amount(...)`.

### 1.4 Creating, saving, comparing, naming

```java
// create
GasStack steam = new GasStack(CCBGases.STEAM.get(), 12_000);      // 12 kGU = 12 kL at 1 atm

// copy / resize
GasStack half = steam.copyWithAmount(steam.getAmount() / 2);
GasStack grow = steam.copy(); grow.grow(500);

// compare (single-tank semantics)
GasStack.isSameGasSameComponents(a, b)   // same gas + same data components; this is what CCB itself uses
GasStack.matches(a, b)                   // ALSO requires equal amounts

// save (BE NBT)
CompoundTag tag = new CompoundTag();
tag.put("Gas", stack.saveOptional(provider));       // empty-safe
GasStack back = GasStack.parseOptional(provider, tag.getCompound("Gas"));

// network
GasStack.STREAM_CODEC.encode(buf, stack);           // throws if empty
GasStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);  // use this for "may be empty"

// name / display
int argb   = stack.getHint();                       // or stack.getGasType().getTint()
String key = stack.getTranslationKey();             // "gas.createcraftedbeginning.steam"
Component name = stack.getHoverName();
```

### 1.5 Item / block conversions that already exist

There is **no fluid conversion**, but CCB ships several item-level gas containers. All of them are relevant only if you want item logistics; AE integration does not need them.

| Container | What it is | API |
|---|---|---|
| **Gas Canister item** | Item holding one gas tank. Default 2 kL, max 10 atm ⇒ 20 000 GU. Capacity enchant multiplies volume. | `CanisterCapabilities.ITEM` (`api.canister.CanisterCapabilities.java:13`) → `GasCanisterContainer` (`api/canister/GasCanisterContainer.java`). Non-empty/full semantics by `getTankVolume/getTankMaxPressurePa/getTankPressurePa/getTankMaxAmount`. |
| **Gas Canister block** | Placed canister exposing a `GasStorageHandler` built from the item's tank. | `GasCanisterBlockEntity.registerCapabilities` (`content/airtights/gascanister/GasCanisterBlockEntity.java:46-47`). |
| **Creative Gas Canister** | Infinite fixed-pressure source. | `content/airtights/creativegascanister/*` |
| **Balloon (packaged gas)** | A Create `PackageItem` subclass carrying a `GasStack` data component (`CCBDataComponents.BALLOON_GAS`) — this is how gas travels through Create's package logistics. | `BalloonItem.containsGas/isBalloon/getGas` (`content/airtights/balloon/BalloonItem.java:66-80`) |
| **Gas Packager / Unpackager / Repackager** | Packager drains adjacent gas storage into balloon items; Unpackager unwraps a balloon and inserts its gas into the adjacent gas handler; Repackager moves gas between balloons. Reference amount: `reference_gas_per_balloon = 10 kGU` (`config/CCBMachines.java:165`). | `content/airtights/gaspackager/**`; unpackager insert at `gasunpackager/GasUnpackagerBlockEntity.java:124-201` |
| **Airtight Hatch** | Bulk item↔gas: exchanges a canister's gas with the network at up to `max_transfer_per_second = 20 000 GU/s` (`config/CCBMachines.java:73`). | `content/airtights/airtighthatch/*` |

> Practical takeaway: **for an ME gas cell, keep gas as GU in your own storage.** If you ever want item interop, the cheapest path is `CanisterCapabilities.ITEM` (canister items), not fluids.

---

## 2. The handler capability

### 2.1 The capability

`api.gas.GasCapabilities` (`api/gas/GasCapabilities.java`):

```java
public static final BlockCapability<GasHandler, @Nullable Direction> BLOCK =
        BlockCapability.createSided(CCBAPI.asResource("gas_handler"), GasHandler.class);   // :19
public static final EntityCapability<GasHandler, @Nullable Direction> ENTITY = ...;        // :21

public static boolean hasBlockHandler(BlockGetter level, BlockPos pos, Direction side);    // :26
```

* Id: `createcraftedbeginning:gas_handler`.
* Type argument is `@Nullable Direction` ⇒ the capability may be queried with `side == null` ("any/unsided"). Register a direction-agnostic provider unless you actually need per-face behaviour.
* CCB itself registers most of its blocks with `(be, ignoredDirection) -> be.handler`, and a few directionally — e.g. the Gas Injection Chamber only exposes **UP** (`GasInjectionChamberBlockEntity.java:87-89`).

### 2.2 `GasHandler` — the minimum contract (`api.gas.handler.GasHandler`)

```java
public interface GasHandler {
    default @Nullable GasTransferSnapshot captureTransferSnapshot() { return null; }  // :21 rollback hook, see §3.4
    boolean isGasValid(int tank, GasStack stack);                                     // :25
    GasStack drain(GasStack resource, GasAction action);                              // :27 drain exactly this gas
    GasStack drain(long maxDrain, GasAction action);                                  // :29 drain anything
    GasStack getGasInTank(int tank);                                                  // :31
    int getTanks();                                                                   // :33
    long fill(GasStack resource, GasAction action);                                   // :35 returns accepted GU

    default AtomicFillResult tryFillAtomically(List<GasStack> resources, GasAction action);                    // :37
    default AtomicFillResult tryFillAtomicallyFromPressure(List<GasStack> resources, long sourcePressurePa,
                                                           GasAction action);                                   // :53
    enum AtomicFillResult { SUCCESS, REJECTED, UNSUPPORTED }                          // :64
}
```

`GasAction` (`api.gas/GasAction.java`) is `EXECUTE` / `SIMULATE` with `execute()`, `simulate()`, `GasAction.get(boolean)`, and adapters to/from `IFluidHandler.FluidAction`.

Return semantics, matching CCB's own `GasTank` (`gas/storage/GasTank.java:152-209`):

| method | SIMULATE | EXECUTE |
|---|---|---|
| `drain(GasStack, action)` | return a **copy** of what *would* be drained (never mutate) | mutate and return what **was** drained |
| `drain(long max, action)` | same, gas-agnostic | same |
| `fill(GasStack, action)` | return **how much would be accepted** (0 … amount) | mutate, return accepted amount |
| failures | return `GasStack.EMPTY` / `0`; **do not throw** | same |

`tryFillAtomically*` defaults return `UNSUPPORTED` (or `SUCCESS` when the list is effectively empty); they exist so a source can hand over several stacks as one transaction. You can ignore them — the solver degrades gracefully.

### 2.3 `GasStorageHandler` — what makes you a *pipe endpoint* (`api.gas.handler.GasStorageHandler`)

```java
public interface GasStorageHandler extends GasHandler {
    GasPressureCompartment getPressureCompartment(int tank);                 // :12
    default long getTankVolume(int tank)         { return getPressureCompartment(tank).getVolume(); }        // :14
    default long getTankPressurePa(int tank)     { ... getPressurePa(); }                                    // :18
    default long getTankMaxPressurePa(int tank)  { ... getMaxPressurePa(); }                                 // :22
    default long getTankMaxAmount(int tank)      { ... getMaxAmount(); }                                     // :26
    default PressureModel getTankPressureModel(int tank) { ... getPressureModel(); }                         // :30
}
```

and `GasPressureCompartment` (`api.gas.pressure/GasPressureCompartment.java`) is itself a `GasHandler` describing **one physical volume**:

```java
public interface GasPressureCompartment extends GasHandler {
    default boolean isGasValid(int tank, GasStack stack) { return tank == 0 && isGasValid(stack); }  // :16
    default GasStack getGasInTank(int tank);   // :27 only tank 0
    default int getTanks() { return 1; }       // :36
    Object getCompartmentIdentity();           // :43  identity of the physical volume (shared across faces/views)
    boolean isGasValid(GasStack stack);        // :45
    GasStack getGasStack();                    // :47
    default boolean supportsExactDrainRecovery() { return false; }                 // :49
    default long restoreDrainedGas(GasStack resource, GasAction action) { return fill(...); } // :53
    long getVolume();                          // :57 litres
    long getPressurePa();                      // :59 derived or fixed
    long getMaxPressurePa();                   // :61
    default long getMaxAmount() { return GasPressure.amount(getVolume(), getMaxPressurePa()); } // :63
    long getStoredAmount();                    // :67
    default Object getPredictionGroup() { return getCompartmentIdentity(); }        // :73
    default @Nullable PredictedTransferLimits predictTransferLimits(GasStack gas, long storedAmount); // :82
    default @Nullable PredictedTransferLimits predictTransferLimits(GasStack gas, GasPressurePrediction p); // :91
    record PredictedTransferLimits(long drainLimit, long fillLimit) {}             // :95
    default PressureModel getPressureModel() { return PressureModel.VARIABLE; }    // :97
    enum PressureModel { VARIABLE, FIXED }                                        // :101
}
```

Contract notes that actually matter (all observed in CCB's own implementations):

* `getTanks()` counts **compartments**; most blocks have exactly 1. The Gas Injection Chamber has 1; the Reactor Kettle's combined gas port exposes **5** (3 input + 2 output tanks, `AirtightReactorKettleGasPortHandler.getTanks()` = `input.getTanks() + output.getTanks()`). That port is also the reference example of *cross-tank policy*: its `GasPressureCompartmentView` subclass refuses to fill/drain a gas that already sits in the matching output/input tank, and exposes a shared `getPredictionGroup()`. Read it before modelling anything smarter than one tank (`content/airtights/airtightreactorkettle/AirtightReactorKettleGasPortHandler.java:122-236`).
* `getCompartmentIdentity()` must be a **stable object identity** for the same physical gas volume (CCB uses `this` for a `GasTank`, and the ItemStack for a canister). The solver groups endpoint faces by it to avoid double-counting a multiblock tank.
* `getMaxAmount()` must be **monotone with** `getMaxPressurePa()`; `GasTankLimits` refuses `maxPressurePa > 24 atm`.
* `PressureModel.FIXED` means "this compartment's pressure does not depend on its contents" (used for creative/infinite or boundary-like sources). For a normal buffer tank use `VARIABLE`.

### 2.4 Registering the capability for your block entity

Exactly the NeoForge pattern CCB uses (`content/airtights/airtighttank/AirtightTankBlockEntity.java:79-81`):

```java
public static void registerCapabilities(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(GasCapabilities.BLOCK, CCBBlockEntities.AIRTIGHT_TANK.get(),
        (tank, ignoredDirection) -> tank.getCapability());
}
```

Call your own `registerCapabilities` from the `RegisterCapabilitiesEvent` handler on the **mod event bus**.

### 2.5 A minimal, correct BE that participates in CCB pipes

This is the smallest thing that both the pipe solver and CCB machines will use. It owns a real buffer tank (`GasTank` implements *both* `GasStorageHandler` and `GasPressureCompartment`, so endpoint discovery accepts it) and it is rollback-safe by construction.

```java
package com.appliedcraftedenergistics.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.ty.createcraftedbeginning.api.gas.GasAction;
import net.ty.createcraftedbeginning.api.gas.GasCapabilities;
import net.ty.createcraftedbeginning.api.gas.GasPressureLimits;
import net.ty.createcraftedbeginning.api.gas.GasStack;
import net.ty.createcraftedbeginning.api.gas.handler.GasStorageHandler;
import net.ty.createcraftedbeginning.gas.storage.GasTank;
import net.ty.createcraftedbeginning.gas.storage.SmartGasTank;

public class GasInterfaceBlockEntity extends BlockEntity {
    /** 4 kL buffer; at 16 atm that is 64 000 GU. */
    public static final long BUFFER_VOLUME_LITERS = 4_000;
    public static final long BUFFER_MAX_PRESSURE_PA = GasPressureLimits.SAFE_PRESSURE_PA; // 16 atm

    /** GasTank implements GasStorageHandler + GasPressureCompartment. */
    private final GasTank buffer = new SmartGasTank(BUFFER_VOLUME_LITERS, BUFFER_MAX_PRESSURE_PA, this::setChanged);

    public GasInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Direction-agnostic: the solver queries with the face it connects on, but `side` may be null.
        event.registerBlockEntity(GasCapabilities.BLOCK, ModBlockEntities.GAS_INTERFACE.get(),
            (be, side) -> be.buffer);
    }

    public GasStorageHandler gasHandler() {
        return buffer;
    }

    // ---- things you may want to expose to your own logic -------------------
    public long storedGu()             { return buffer.getStoredAmount(); }
    public long pressurePa()           { return buffer.getPressurePa(); }
    public long capacityGu()           { return buffer.getMaxAmount(); }
    public GasStack contents()         { return buffer.getGasStack(); }

    /** Read-only convenience: push into a neighbour respecting its pressure ceiling. */
    public long pushInto(net.minecraft.core.Direction dir, long maxGu) {
        if (level == null) return 0;
        var neighbour = level.getCapability(GasCapabilities.BLOCK, worldPosition.relative(dir), dir.getOpposite());
        if (neighbour == null) return 0;
        GasStack available = buffer.getGasStack();
        if (available.isEmpty()) return 0;
        long request = Math.min(maxGu, available.getAmount());
        GasStack simulated = buffer.drain(request, GasAction.SIMULATE);
        if (simulated.isEmpty()) return 0;
        long accepted = neighbour.fill(simulated, GasAction.SIMULATE);   // simulated already carries the amount
        if (accepted <= 0) return 0;
        // execute both halves, draining only what the target agreed to take
        GasStack actuallyDrained = buffer.drain(Math.min(accepted, simulated.getAmount()), GasAction.EXECUTE);
        if (actuallyDrained.isEmpty()) return 0;
        long filled = neighbour.fill(actuallyDrained, GasAction.EXECUTE);
        if (filled < actuallyDrained.getAmount()) {
            // return the remainder to our own buffer (fill is our own tank: always possible up to capacity)
            buffer.fill(actuallyDrained.copyWithAmount(actuallyDrained.getAmount() - filled), GasAction.EXECUTE);
        }
        return filled;
    }
}
```

Notes on that snippet:

* We register the **tank itself**, not a wrapper. That is legal here because we only have one compartment and no multiblock. If you need per-face views (e.g. a "push-only" face), wrap and delegate, keeping `getCompartmentIdentity()` pointing at the same object for every view.
* `SmartGasTank` (== `GasTank` + an update callback) is in `gas.storage`; if you prefer zero dependence on `gas.*`, implement `GasStorageHandler` + `GasPressureCompartment` yourself (~80 lines, see §7.4). `GasTank` is the reference behaviour.
* `pushInto` uses the neighbour's plain `fill()`; that is pressure-*blind*. Use the pressure-aware variant in §2.7 if you care about not over-pressuring the target.

### 2.6 Getting a neighbouring block's gas handler

Three supported ways, all on `Level`:

```java
// one-shot (cheap, but re-resolves the provider every call)
@Nullable GasHandler h = level.getCapability(GasCapabilities.BLOCK, pos, side);   // side may be null
boolean any = GasCapabilities.hasBlockHandler(level, pos, side);                  // GasCapabilities.java:26

// cached, invalidated automatically (this is what CCB's own pipe endpoints do)
BlockCapabilityCache<GasHandler, @Nullable Direction> cache =
    BlockCapabilityCache.create(GasCapabilities.BLOCK, (ServerLevel) level, targetPos, side,
        () -> !this.isRemoved(),        // validity
        () -> { /* provider invalidated: drop any derived state */ });
GasHandler h2 = cache.getCapability();
```

`ExternalGasEndpoint` is the canonical example — server side uses `BlockCapabilityCache`, client/Ponder uses `ICapabilityProvider.of(() -> level.getCapability(...))` (`gas/network/endpoint/ExternalGasEndpoint.java:30-53`). Use the cache in a BE that polls; use `getCapability` in one-off interactions.

There is **no** `IItemHandler`-style "get all sides" helper; iterate `Direction.values()` if you must.

### 2.7 Pressure-aware push/pull with CCB helpers (`gas.transfer`, unversioned)

```java
// target endpoint from any handler + tank index (empty Optional if it is not a compartment/storage/boundary)
Optional<GasPressureTransferEndpoint> ep = GasPressureTransferEndpoint.tryHandler(neighbourHandler, 0);
if (ep.isPresent()) {
    GasPressureTransferEndpoint t = ep.get();

    // how much of `stack` can I push in, given that my gas arrives at `sourcePressurePa`?
    long canFill  = GasPressureFillService.getTransferableAmount(t, stack, stack.getAmount(), sourcePressurePa);
    long pushed   = t.executeFill(stack, canFill, sourcePressurePa);

    // how much can I pull out?
    long canDrain = t.simulateDrainAmount(stack, stack.getAmount());
    GasStack got   = t.executeDrain(stack, canDrain);
}
```

`getTransferableAmount` implements the CCB pressure gate precisely: `sourcePressure <= target fill pressure` ⇒ 0; otherwise, for `VARIABLE` targets, the fill is additionally capped by `GasPressureTransferMath.maxFillAmount(...)` so the target never exceeds `min(sourcePressure, target.maxPressure)` (`gas/transfer/GasPressureFillService.java:70-93`, `gas/network/math/GasPressureTransferMath.java:25-33`).

If you implement `GasPressureBoundary` instead of `GasStorageHandler`, the reverse is available: CCB will call **your** `fillFromPressure(stack, sourcePressurePa, action)` and read your `getFillPressurePa` / `getDrainPressurePa` / `getFillFlowResistanceFactor` / `getDrainFlowResistanceFactor` (`api/gas/pressure/GasPressureBoundary.java:14-29`).

---

## 3. Transport: pipes, pumps, valves, and the solver

### 3.1 Will CCB pipes push gas into my block automatically?

**Yes — if and only if** your block exposes `GasCapabilities.BLOCK` **and** the handler implements `GasStorageHandler` (volume > 0) or a single-tank `GasPressureBoundary`. The chain:

1. Pipe blocks refresh their connection faces every 20 ticks and on neighbour updates; a face opens if the neighbour is a `GasConnectable`, **or** `GasCapabilities.hasBlockHandler(level, pos, face)` is true (`content/airtights/airtightpipe/AirtightPipeConnections.java:100`, `gas/behaviour/GasTransportBehaviour.java:62-69`).
2. The topology scan (per pipe network, cached) marks a face as an **endpoint face** when the neighbour has a gas handler (`gas/network/solver/GasNetworkTopology.java:130-133`).
3. Endpoint discovery turns each endpoint face into a usable endpoint only if the neighbour is:
   * `GasStorageHandler` **and** `getPressureCompartment(t).getVolume() > 0` (`gas/network/solver/endpoint/GasEndpointDiscovery.java:45-63,151-164`), or
   * a `GasPressureBoundary` with `getTanks() == 1` that is **not** a `GasStorageHandler` (`:166-179`), or
   * atmospheric/open air (not applicable to us).
4. Each tick `GasNetworkSimulator` solves a pressure–flow graph over pipes/pumps/valves and the discovered endpoints, then executes routed transfers with rollback (`gas/network/solver/GasNetworkSimulator.java:45-128`, `gas/network/solver/transfer/GasTransferExecutor.java`).

So: **a plain `GasHandler` gets a visually connected pipe that moves nothing.** This is the single most likely integration bug. Implement `GasStorageHandler`.

Cache/invalidation caveat: the topology snapshot is keyed per pipe and invalidated by the pipe's own `neighborChanged`/`onPlace`/`tick` and by chunk-unload checks. Placing your block next to a pipe triggers that naturally. If you change endpoint *availability* at runtime (e.g. you only become a valid endpoint when powered), the pipe will not notice by itself: call

```java
GasTransportBehaviour behaviour =
    BlockEntityBehaviour.get(level, pipePos, GasTransportBehaviour.TYPE); // Create API
if (behaviour != null) behaviour.markConnectionsDirty();                  // public, invalidates topology
level.invalidateCapabilities(myPos);                                      // NeoForge: provider changed
```

`markConnectionsDirty()` is public but lives in `gas.behaviour` (unversioned); guard with a try/catch or make capability availability static.

### 3.2 What the "existing transport system" is, from an addon's point of view

* **Pipes** (`airtightpipe`, `smartairtightpipe`) are Create `SmartBlockEntity`s carrying `GasTransportBehaviour`. They are passive: flow is driven by pressure difference and conductance (`GasFlowResistance.fromFactor(0.958)` per segment, `GasTransportBehaviour.java:47`).
* **Pumps** (`airtightpump`, `airtightregulatorpump`) inject a pressure boost: config default **+4 atm max boost** scaling with rotation speed, **4000 GU/t max flow** (`config/CCBMachines.java:82-83`); regulator pump uses a **pressure rise** model (8 atm/stage) instead of a boost (`,,:92-93`).
* **Valves** (`airtightvalve`, `airtightcheckvalve`) are flow gates: the plain valve opens/closes both directions (`AirtightValveTransportBehaviour.allowsInbound/OutboundFlow`), the check valve allows one. Pumps and the regulator pump additionally publish an *internal* pressure boundary through `GasTransportBehaviour.getInternalPressureBoundary(face)`, which the solver discovers as an extra endpoint (`gas/network/solver/endpoint/GasEndpointDiscovery.java:75-89`).
* **Nothing is stored in pipes.** Pipes hold flow state only; buffering is in endpoints.
* **Rates are quantised and pressure-driven**: expected throughput to your block is "whatever the pressure difference supports, up to pump/free-flow limits"; you cannot request a rate.

### 3.3 Natural CCB endpoints you will meet

| Block | Role | Notes |
|---|---|---|
| `airtighttank` (multiblock) | bulk storage | **80 kL per block** (`config/CCBMachines.java:118`), **24 atm** hard limit ⇒ **1 920 000 GU per block** at full pressure. Every block face exposes the controller's tank. |
| `creativeairtighttank` | infinite source/sink at a settable pressure | `CreativeGasReservoir` — `PressureModel.FIXED` |
| `airtightpump` / `airtightregulatorpump` | pressure source/sink | +4 atm boost / 8 atm rise, 4000 GU/t |
| `airtighthatch` | canister↔network bridge | 20 000 GU/s |
| `gascanister` (block) | small tank, 2 kL default | 10 atm default |
| `gasinjectionchamber` | gas consumer, belt/basin processing | 10 kL, **capability only on UP** face (`GasInjectionChamberBlockEntity.java:87-89`) |
| `airtightreactorkettle` | gas-processing multiblock | 90 kL per tank segment, separate input/output tanks |
| `airtightfractionationtower` | gas separation | 90 kL per tank |
| `boilersteamoutlet` | **steam producer** | `SteamOutletGasHandler implements GasStorageHandler` (`content/airtights/boilersteamoutlet/SteamOutletGasHandler.java:18`) |
| `teslaturbine` / `teslaturbinenozzle` | gas→rotation consumer | `GasPressureBoundary` (fill-only, converts pressure into kinetic power) |
| `gasfactorygauge`, `gasobserver`, `airtightmeters` | read-only telemetry | not endpoints |
| `gaspackager` family | gas↔balloon items | item logistics |

### 3.4 Transfer semantics you must respect (rollback!)

`GasTransferExecutor.Execution` (`gas/network/solver/transfer/GasTransferExecutor.java:213-294`):

* before each drain/fill it calls `handler.captureTransferSnapshot()` (`:273-279`);
* on `SIMULATE`-purity violations or exceptions it calls `snapshot.restore()` + `afterRestore()` (`:148-165,281-294`);
* if you return `null` (the default) and the operation fails, the solver records the amount as *unconfirmed* and **does not refund it** (`:289-294`).

Therefore:

* implement `captureTransferSnapshot()` whenever your `fill`/`drain` can be executed and later need undoing;
* keep `fill` **idempotent under SIMULATE** and **cheap** (it is called many times per tick);
* never route a `fill()` straight into shared/global storage (ME network is not rollback-able). Buffer locally (see §7).

A complete `GasTank`-style snapshot (`gas/storage/GasTank.java:66-81`) is the model to copy:

```java
@Override public GasTransferSnapshot captureTransferSnapshot() {
    GasTankState state = snapshot();                 // limits + contents
    return new GasTransferSnapshot() {
        @Override public void restore() { limits = state.limits(); gas = state.contents(); }
        @Override public void afterRestore() { onStateChanged(); }
    };
}
```

### 3.5 Overpressure is a property of storage, not of transport

`OverpressureBehaviour` is attached by the **storage/machines**, not by pipes or valves — confirmed at exactly four sites plus the fractionation tower's own variant:

* `airtighttank` (`AirtightTankBlockEntity.java:130`)
* `airtightforgingpress` (`AirtightForgingPressBlockEntity.java:168`)
* `gasinjectionchamber` (`GasInjectionChamberBlockEntity.java:118`)
* `airtightreactorkettle` (`AirtightReactorKettleBlockEntity.java:527`), channels per gas
* `airtightfractionationtower` via `AirtightFractionationTowerOverpressure` (`:58-60`)

Behaviour: pressure is sampled each tick; above **16 atm** stress rises (quadratically, reaching 1.0 in 5 s at 24 atm), below 16 atm it recovers over 15 s, with a 40-tick stabilisation grace after load (`gas/overpressure/OverpressureStressCalculator.java:11-55`, `gas/overpressure/OverpressureState.java:20-64`). When `isFailureReady()` the block **ruptures**:

* gas is drained and released as a radial release (`RUPTURE` cause, at the compartment's pressure);
* optional explosion: default strength **4**, `explosion_damages_surrounding_blocks = false` (`config/CCBGas.java:23-24`);
* the block is destroyed (`gas/overpressure/PressureRuptureService.java:23-57`).

Consequence for an addon: pipes will happily carry 24 atm, so an interface that keeps injecting gas into a **closed valve/pipe run feeding a tank** raises that tank's pressure until it bursts. Pipes themselves are inert. Rate-limit and pressure-limit your throughput anyway.

---

## 4. Pressure model (this is what your cells must not get wrong)

### 4.1 Units and the one formula

```
pressure [Pa] = amount [GU] * 100_000 / volume [L]        (exact, integer, saturating)
amount   [GU] = volume [L] * pressure [Pa] / 100_000
```

* 100 000 Pa = **1 atm** = "reference pressure" (`GasPressure.REFERENCE_PRESSURE_PA`).
* Vacuum = 0 Pa. Hard ceiling = **2 400 000 Pa (24 atm)**; nothing may be clamped above it (`GasPressureLimits.clampToHardLimit`).
* Normal-operation ceiling = **1 600 000 Pa (16 atm)** (`SAFE_PRESSURE_PA`); above it `isOverpressure` is true.
* Pressure is a **property of a compartment (volume + contents)**, never a field on a stack. A `GasStack` in isolation has **no pressure**.
* Doubling the volume at the same amount halves the pressure. This is why multiblock tanks "de-pressurise" when extended, and why CCB drains gas from a large tank at lower pressure than from a canister.
* Extra integer helpers: `minimumAmountForPressure`, `amountAbovePressureFloor`, `pascals(double atm)`, `atmospheres(long pa)`, `format`/`formatAtm` (`api/gas/GasPressure.java:84-159`).

### 4.2 Tiers

`api.gas.pressure.GasPressureTier` (`GasPressureTier.java:13-19`), upper bounds in atm/Pa:

| Tier | max atm | max Pa | notes |
|---|---|---|---|
| `VACUUM` | 0 | 0 | empty |
| `AMBIENT` | 1 | 100 000 | "1 atm"; world atmosphere default |
| `LOW` | 6 | 600 000 | |
| `MEDIUM` | 10 | 1 000 000 | canister default ceiling (10 atm) |
| `HIGH` | 14 | 1 400 000 | |
| `EXTREME` | 16 | 1 600 000 | == `SAFE_PRESSURE_ATM` == `MAX_DEFINED_PRESSURE_PA` |

`resolve(pa)` returns the **first tier whose max ≥ pa**, so `resolve(16 atm) == EXTREME`, `resolve(16 atm + 1 Pa) == EXTREME` after clamping (the hard 24 atm ceiling is deliberately *not* a tier — `GasPressureLimits` javadoc, `:7-13`). Useful helpers: `satisfies`, `highestSatisfied`, `nearestUnsatisfied`, `minimumPressureFor`, `pressureNeededForNextTier`, `normalizePressurePa`.

Recipe pressure requirements use these tiers, so tier matters if you ever *feed* a machine.

### 4.3 Tanks and their limits

`gas.storage.GasTankLimits` (record, `GasTankLimits.java:11-32`):

```java
record GasTankLimits(long volumeLiters, long maxPressurePa) {
    // rejects volume < 0, maxPressure < 0, maxPressure > GasPressureLimits.HARD_PRESSURE_PA
    static GasTankLimits atReferencePressure(long volumeLiters);   // maxPressure = 1 atm
    long maxAmount();                                             // GasPressure.amount(volume, maxPressure)
}
```

`gas.storage.GasTank` implements `GasStorageHandler` **and** `GasPressureCompartment` (`GasTank.java:22`). Behaviour worth copying exactly:

* one compartment, `getTanks() == 1`; `getPressureCompartment(t)` throws unless `t == 0` (`:98-104`);
* `getPressurePa()` is **derived every call** from stored amount and volume (`:132-134`);
* `fill` accepts only the same gas+components, otherwise 0; fills up to `maxAmount()` only (`:182-209`);
* `drain` never goes below 0 and returns a copy of what it removed (`:152-175`);
* `predictTransferLimits` reports `(drainLimit, fillLimit)` for a hypothetical content amount (`:84-95`);
* config/state changes go through `tryReconfigure` / `tryReplaceContents` / `tryApplyState` returning `GasTankMutationResult` (`APPLIED`, `UNCHANGED`, `INVALID_GAS`, `EXCEEDS_PRESSURE_LIMIT(excess)`) — i.e. **never silently clamp**;
* save/load is just `write(provider, tag)` / `read(provider, tag)`, key `"Gas"`, and `readClampedToLimits` returns the overflow for spilling (`:278-303`).

`gas.storage.SmartGasTank` = `GasTank` + `Runnable updateCallback` invoked from `onStateChanged()` (`SmartGasTank.java:9-21`). `gas.storage.CreativeGasReservoir` = fixed-pressure infinite source with `PressureModel.FIXED` and a settable `fixedPressurePa`.

`gas.storage.handler.CombinedGasStorageHandler` is a public helper that concatenates several `GasStorageHandler`s into one multi-tank view (useful if your interface owns 2 tanks: buffer + pass-through).

### 4.4 Fill/drain admission rules (what the solver will let through)

From `GasPressureFillService.getTransferableAmount` + `GasPressureTransferMath`:

```
fill allowed  iff  sourcePressure > target.getFillPressurePa(gas)
targetPressureCeiling = min(sourcePressure, target.getMaxPressurePa())
maxFill  = min(target.getMaxAmount(), amountAtOrBelowPressure(volume, targetPressureCeiling)) - currentAmount
drain allowed iff  currentPressure > manifoldPressure
maxDrain = currentAmount - minimumAmountAtOrAbovePressure(volume, manifoldPressure)
```

i.e. **gas only moves into a compartment when the giving side has strictly higher pressure**, and a compartment is never filled past `min(source, its own max)` pressure. `PressureModel.VARIABLE` compartments have fill pressure == their own current pressure; `FIXED` ones advertise a constant pressure.

### 4.5 Consequences for an AE gas cell

1. **Store a quantity, not a pressure.** A `GasPressureCompartment` needs `volumeLiters`; an ME cell does not have one. Model AE-side gas as *GU at 1 atm* ("standard litres") and give the **block** a real physical volume. Then `pressure = GU * 100000 / blockVolume` is well defined at the block, and the ME network just sees an amount.
2. **Never let the ME network be the direct `fill()` target.** The solver's simulate-then-execute-then-maybe-rollback model cannot roll back a network insert. Fill a local `GasTank` buffer; move buffer↔network in your own tick step.
3. **Respect 16 atm on the CCB side.** Two good choices:
   * block buffer `maxPressurePa = SAFE_PRESSURE_PA` (16 atm) — never contributes to overpressure; or
   * `= HARD_PRESSURE_PA` (24 atm) only if you *want* to be able to pressurise lines near the burst limit.
4. **Decide the pressure you present to the network.**
   * Buffer tank, `VARIABLE` (recommended): acts like a real tank — CCB pushes in as long as the line is above buffer pressure, and can pull out as long as the buffer is above line pressure. This is physically honest and requires zero pressure code from you.
   * `GasPressureBoundary` with `getFillPressurePa = 1 atm` + `getDrainPressurePa = 1 atm`: behaves like an infinite main held at exactly 1 atm. Simpler to reason about ("ME holds gas at ambient"), but you must rate-limit yourself, since the boundary is treated as infinite.
5. **`AEKey` amount == GU.** With `getAmountPerOperation() = 1000` (1 kGU, mirroring AE2's fluid "1000 per bucket") and a sensible `getAmountPerByte()` (e.g. 8000 ⇒ 1 AE byte = 8 kGU), the cell capacity in GU is `bytes * amountPerByte`. Because 1 GU is defined at 1 atm, cell capacity is **pressure-independent** — which is the only way AE2's byte accounting stays coherent.
6. **Do not encode pressure into the key.** One AE key per `Gas` (`createcraftedbeginning:<gas>`), no components. Pressure lives at the block. Encoding pressure in keys explodes type counts.
7. **Tooltips/units:** show `GasPressure.format(pa)` for block pressure and `GasUnitFormat`/`kGU`-style for amounts so numbers agree with CCB goggles.

---

## 5. Gas acquisition: what produces or exposes gas

### 5.1 The gas list (`registry.gas.CCBGases`, 12 gases)

| Constant | id (`createcraftedbeginning:…`) | tint | tags |
|---|---|---|---|
| `NATURAL_AIR` | `natural_air` | `0xCBCFFA` | `natural` |
| `ULTRAWARM_AIR` | `ultrawarm_air` | `0xF0D2B2` | `ultrawarm` |
| `ETHEREAL_AIR` | `ethereal_air` | `0xF4BAF5` | `ethereal` |
| `ENERGIZED_NATURAL_AIR` | `energized_natural_air` | `0xD2D5FB` | `natural`, `energized` |
| `ENERGIZED_ULTRAWARM_AIR` | `energized_ultrawarm_air` | `0xF2D8BC` | `ultrawarm`, `energized` |
| `ENERGIZED_ETHEREAL_AIR` | `energized_ethereal_air` | `0xF5C3F6` | `ethereal`, `energized` |
| `MOIST_AIR` | `moist_air` | `0xBEDAED` | `moist` |
| `SPORE_AIR` | `spore_air` | `0xE8F5C4` | `spore` |
| `SCULK_AIR` | `sculk_air` | `0x111B21` | `sculk` |
| `STEAM` | `steam` | `0xE7EEF2` | (none) |
| `POTION_GAS` | `potion_gas` | `0xC080D0` | (PotionGas subclass, not usable in equipment) |
| `CREATIVE_AIR` | `creative_air` | `0x000000` | `creative` |

Plus `createcraftedbeginning:empty` (the registry default). Tags are in the mod namespace (`createcraftedbeginning:natural`, …), **not** `c:` (`registry/CCBTags.java:261-298`). Access them by constant (`CCBGasTags.NATURAL.tag`) or by `GasStack.is(TagKey)`.

`STEAM` is the one gas with an obvious continuous producer (boiler steam outlet), which makes it the best first end-to-end test gas.

### 5.2 Where gas comes from

* **Atmosphere sampling / open pipe ends.** A pipe face that is open to air, or adjacent to a `#createcraftedbeginning:gas_sources` block, becomes an **atmospheric endpoint**: it can *extract* the biome/dimension gas and can *dump* gas into the air, at the local atmospheric pressure (`gas/network/GasConnectionResolver.java:121-123`, `gas/network/endpoint/AtmosphericGasEndpoint.java:75-90`). Composition/pressure come from `AtmosphereProviderRegistry`, extensible by addons (`api/atmosphere/AtmosphereProvider`, `api.events.RegisterAtmosphereProvidersEvent`), resolved per block position by `gas.atmosphere.AtmosphereStateResolver`.
  So: **an airtight pump with an open pipe end is a gas well**; its outlet pressure is inlet pressure + up to 4 atm. Ambient pressure is obtained with `AtmosphereStateResolver.resolvePressurePa(level, pos)` (or `resolve(...)` for gas + pressure).
* **Airtight Pump / Regulator Pump** — the only active pressure sources (see §3.2). Pump types: `airtightpump` (shaft-driven variants) and `airtightregulatorpump` (supply + active exhaust).
* **Boiler Steam Outlet** — produces `steam` into the network (`SteamOutletGasHandler`).
* **Gas Injection Chamber** — consumes gas to process items on a belt / in a basin; 10 kL buffer, `HARD` max pressure, capability exposed **only on the UP face**.
* **Airtight Reactor Kettle** — multiblock reactor with 3 input / 2 output gas tanks (90 kL each); recipes consume gas at a required pressure tier and produce gas/items.
* **Airtight Fractionation Tower** — separates mixed gas into fractions (90 kL per tank).
* **Breeze Chamber / Breeze Cooler** (`content/breezes`) — uses wind charge to run `GasConversion` recipes (one gas into another). The chamber tank is 40 kL and processing is throttled by `max_processing_per_second = 5120 GU/s` and by how far the output tank is below ambient pressure (`BreezeChamberConversionPlanner.java:40-60`).
* **Tesla Turbine + Nozzle** — consumes gas pressure to make rotation (`GasPressureBoundary`, fill-only, converts pressure into power).
* **Airtight Forging Press** — 30 kL input tank, consumes gas for pressing.
* **Airtight Hatch** — canister ↔ network transfer at 20 000 GU/s.
* **Gas Packager / Unpackager / Repackager** — gas ↔ balloon items.
* **Creative Airtight Tank / Creative Gas Canister** — infinite, settable, perfect for testing.
* Airborne/equipment consumers (armour, drill, cannon, extend arm) burn gas from canister items; not network endpoints.

### 5.3 First integration targets

**Get gas into the ME network**
1. `airtighttank` next to your interface — simplest, real pressure physics, no moving parts.
2. `boilersteamoutlet` → pipes → your interface (continuous `steam` production, good for rate testing).
3. `airtightpump` with an open pipe end → your interface (tests low-pressure/ambient behaviour).

**Use gas from the ME network**
1. `gasinjectionchamber` (UP face) — clean consumer, easy to observe.
2. `airtightreactorkettle` input tanks — pressure-tier recipes, exercises your pressure presentation.
3. `teslaturbine` / nozzle — path where you must **not** over-deliver pressure.
4. `airtighttank` as a sink — verifies you can fill a real tank up to a pressure ceiling without triggering rupture.

---

## 6. Jar verification (source repo vs shipped 0.1.1)

`libs\createcraftedbeginning-0.1.1.jar` was built from the same version as the repo (`gradle.properties: mod_version=0.1.1`; jar `META-INF/neoforge.mods.toml` → `version="0.1.1"`), so no divergence is expected — and none was found.

Verified with `javap -p -classpath libs\createcraftedbeginning-0.1.1.jar <fqcn>` (full dumps in `CCB_GAS_EVIDENCE.md`):

| Type | Verdict |
|---|---|
| `api.CCBAPI` (`MOD_ID`, `asResource`) | present, identical |
| `api.gas.Gas` (all 20 members incl. `findById`, `parseHolder`, `getTint`, `getTranslationKey`) | identical |
| `api.gas.GasStack` (all ctors, codecs, `matches`, `isSameGasSameComponents`, `save*`, `parse*`) | identical |
| `api.gas.GasRegistries` (3 fields) | identical |
| `api.gas.GasCapabilities` (`BLOCK`, `ENTITY`, `hasBlockHandler`) | identical |
| `api.gas.GasAction`, `GasUnits`, `GasPressure`, `GasPressureLimits`, `GasTags`, `GasBuilder`, `GasHolder`, `GasUsageContext` | identical |
| `api.gas.handler.GasHandler` + `AtomicFillResult`, `GasStorageHandler`, `GasTransferSnapshot` | identical |
| `api.gas.pressure.GasPressureCompartment` (+`PressureModel`, `PredictedTransferLimits`), `GasPressureBoundary`, `GasPressureTier`, `GasPressurePrediction` | identical |
| `api.gas.logistics.GasInventoryIdentifiers` (+`Finder`), `GasInventoryIdentifierProvider` | identical |
| `api.atmosphere.AtmosphereProvider/ProviderRegistry/State` | identical |
| `api.canister.CanisterCapabilities`, `GasCanisterContainer` (+`InjectionMode`) | identical |
| `registry.gas.CCBGases` (12 gas constants + `GAS_REGISTER`, `provideLang`), `CCBGasDeferredRegister` | identical |
| `gas.storage.GasTank`, `GasTankLimits`, `SmartGasTank`, `GasTankState`, `GasTankMutationResult`(+`Status`), `CreativeGasReservoir`, `GasPressureCompartmentView` | identical |
| `gas.storage.handler.CombinedGasStorageHandler`, `GasInventorySummary` | identical |
| `gas.transfer.GasPressureFillService`, `GasPressureTransferEndpoint`, `GasPressureTransferService`, `GasPressureTransferResult` | identical |
| `gas.overpressure.OverpressureState`, `OverpressureStressCalculator`, `PressureRuptureService` | identical |
| `gas.atmosphere.AtmosphereStateResolver` | identical |
| `gas.network.solver.GasNetworkSimulator`, `GasNetworkTopology`, `endpoint.GasEndpointDiscovery/Planner` | identical |

Also confirmed in the jar: `gas.network.endpoint.{AtmosphericGasEndpoint, ExternalGasEndpoint, GasConnectionEndpoint}`, `gas.behaviour.{GasTransportBehaviour, GasManipulationBehaviour, OverpressureBehaviour, SmartGasTankBehaviour}`, `gas.network.math.GasPressureTransferMath`, `gas.flow.*`, `gas.release.*`, `gas.visual.{GasUnitFormat, GasPressureDisplayScale}`, `content.airtights.*` (tank/pump/valve/pipe/packager/canister/turbine/kettle/fractionation), `compat.functionalstorage.*`.

Nothing recommended in this document is missing or renamed in the shipped jar. The one thing you cannot verify from `javap` is *behaviour*; §3.4 and §4.4 were derived from source that is byte-equivalent in version terms but is still early-version behaviour, not a contract.

---

## 7. What Applied Crafted Energistics should implement

### 7.1 Reference map (CCB type → your responsibility)

| Need | CCB type(s) to use | Where |
|---|---|---|
| Gas key / `AEKey` wrapper | `api.gas.Gas`, `api.gas.GasRegistries.GAS_REGISTRY`, `Gas.HOLDER_CODEC` | §1.1, §1.2 |
| Wire format for the key | `Gas.getResourceLocation()` / `Gas.findById(ResourceLocation)`; `GasStack.CODEC` if you ever need amount in an item | `Gas.java:87,140`, `GasStack.java:88` |
| Cell amount type | `long` GU (`GasStack.getAmount()`), display via `GasUnits.toKilo` | `GasStack.java:317`, `GasUnits.java:23` |
| ME → CCB **push** | `GasCapabilities.BLOCK` + `GasHandler.fill(GasStack, GasAction)`; pressure-aware: `GasPressureTransferEndpoint.tryHandler(...).executeFill(...)` or implement nothing and let pipes pull via your `GasStorageHandler` | §2.2, §2.7 |
| CCB → ME **pull** (import) | your own `GasStorageHandler` (buffer) drained by CCB, or `GasHandler.drain(...)` on a neighbour | §2.5, §2.6 |
| Pressure-aware capacity | `GasPressureLimits.{SAFE,HARD}_PRESSURE_PA`, `GasPressure.{pressure,amount}` | §4.1, §4.2 |
| Buffer/transaction model | `gas.storage.GasTank`/`SmartGasTank` (or your own impl), `GasTransferSnapshot` | §2.5, §3.4 |
| Optional Create logistics interop | `GasInventoryIdentifiers`/`GasInventoryIdentifierProvider` (so Create packagers treat your block as one inventory) | `api/gas/logistics/*` |
| Optional item interop | `CanisterCapabilities.ITEM` + `GasCanisterContainer` | §1.5 |

### 7.2 Recommended architecture (2 blocks, no exotic CCB internals)

```
CCB pipe  ── GasCapabilities.BLOCK ──►  [Gas Interface BE]
                                          ├─ buffer: GasTank(4 kL, 16 atm)   ← solver-visible endpoint
                                          └─ tick: buffer ⇄ ME network (rate-limited)

ME network ── MEStorage(AEKey=CCBGasKey) ──► [Gas Cell + CellInventory]
```

* **Filter/interface block** (`gas_interface`): owns the `GasTank` buffer, registers `GasCapabilities.BLOCK`, and per tick moves gas between buffer and ME with an explicit pressure policy:
  * *import*: if `buffer.getPressurePa() > REFERENCE_PRESSURE_PA * 1.05` and ME can accept → `buffer.drain()` into ME;
  * *export*: if `buffer.getPressurePa() < REFERENCE_PRESSURE_PA` and ME has gas → `buffer.fill()` from ME;
  * both sides rate-limited to e.g. 4000 GU/t (same order as a CCB pump) so you never out-run the solver.
* **Storage cell** (`gas_cell`): `AEKeyType` = `appliedcraftedenergistics:gas`, key = `CCBGasKey(Gas)`, amounts in GU, capacity `bytes * amountPerByte`. No pressure state.
* **Never** implement `GasHandler.fill()` by inserting into the ME network directly (rollback hazard, §3.4). The buffer tank *is* the transactional boundary.

### 7.3 AE2 hook checklist (verified present in `appliedenergistics2-19.2.20.jar`)

* `appeng.api.stacks.AEKey` — subclass it: `getType()`, `getId()`, `toTag(Provider)`, `writeToPacket(RegistryFriendlyByteBuf)`, `getPrimaryKey()`, `dropSecondary()`, `computeDisplayName()`, `addDrops(...)`, `hasComponents()`. `wrapForDisplayOrFilter()` has a default but you should return a representative item (e.g. a canister/balloon or a synthetic icon item) so JEI/terminals render it.
* `appeng.api.stacks.AEKeyType` — `new AEKeyType(ResourceLocation, Class<? extends AEKey>, Component)` plus `codec()`, `readFromPacket(...)`; override `getAmountPerOperation()` (suggest **1000** = 1 kGU, mirroring AE2 fluid's 1000-per-bucket) and `getAmountPerByte()` (suggest **8000**).
* Registration: AE2 19.x exposes `AEKeyType.REGISTRY_KEY` (`ResourceKey<Registry<AEKeyType>>`) **and** `appeng.api.stacks.AEKeyTypes.register(AEKeyType)`. Verify which one 19.2.20 expects at runtime (both symbols exist in the jar); prefer a `DeferredRegister` on `AEKeyType.REGISTRY_KEY` if it works, else `AEKeyTypes.register` from a mod-load hook.
* `appeng.api.stacks.GenericStack(AEKey, long)` — use it for terminal/tooltip/reporting; `AEKey` alone carries no amount.
* `appeng.api.storage.MEStorage` — `insert/extract(AEKey, long, Actionable, IActionSource)`, `getAvailableStacks(KeyCounter)`.
* Cells: `appeng.api.storage.cells.IBasicCellItem` (`getKeyType`, `getBytes`, `getBytesPerType`, `getTotalTypes`, `getIdleDrain`), `appeng.api.storage.StorageCells`, `IStorageProvider`.

### 7.4 Optional: implement the CCB interfaces yourself (zero `gas.*` dependency)

Only four `api.` interfaces are needed. Skeleton (buffer-tank semantics, matching `GasTank`):

```java
public final class AerostaticGasTank implements GasStorageHandler, GasPressureCompartment {
    private final Predicate<GasStack> validator;
    private GasStack gas = GasStack.EMPTY;
    private long volumeLiters;
    private long maxPressurePa;

    @Override public int getTanks() { return 1; }
    @Override public GasStack getGasInTank(int tank) { return tank == 0 ? gas.copy() : GasStack.EMPTY; }
    @Override public boolean isGasValid(int tank, GasStack stack) { return tank == 0 && validator.test(stack); }
    @Override public boolean isGasValid(GasStack stack) { return validator.test(stack); }
    @Override public GasStack getGasStack() { return gas.copy(); }
    @Override public Object getCompartmentIdentity() { return this; }
    @Override public long getVolume() { return volumeLiters; }
    @Override public long getStoredAmount() { return gas.getAmount(); }
    @Override public long getPressurePa() { return GasPressure.pressure(gas.getAmount(), volumeLiters); }
    @Override public long getMaxPressurePa() { return maxPressurePa; }         // <= 24 atm
    @Override public long getMaxAmount() { return GasPressure.amount(volumeLiters, maxPressurePa); }

    @Override public GasStack drain(long maxDrain, GasAction action) {
        if (maxDrain <= 0 || gas.isEmpty()) return GasStack.EMPTY;
        GasStack out = gas.copyWithAmount(Math.min(maxDrain, gas.getAmount()));
        if (action.execute()) gas.shrink(out.getAmount());
        return out;                                          // SIMULATE returns the same preview, unmutated
    }
    @Override public GasStack drain(GasStack resource, GasAction action) {
        return !resource.isEmpty() && GasStack.isSameGasSameComponents(resource, gas)
                ? drain(resource.getAmount(), action) : GasStack.EMPTY;
    }
    @Override public long fill(GasStack resource, GasAction action) {
        if (resource.isEmpty() || !validator.test(resource)) return 0;
        if (!gas.isEmpty() && !GasStack.isSameGasSameComponents(gas, resource)) return 0;
        long accept = Math.min(getMaxAmount() - gas.getAmount(), resource.getAmount());
        if (accept <= 0 || action.simulate()) return Math.max(0, accept);
        if (gas.isEmpty()) gas = resource.copyWithAmount(accept); else gas.grow(accept);
        return accept;
    }
    @Override public GasPressureCompartment getPressureCompartment(int tank) {
        if (tank != 0) throw new IndexOutOfBoundsException("tank " + tank);
        return this;                                          // one physical volume
    }
    // save/load: tag.put("Gas", gas.saveOptional(provider)) / GasStack.parseOptional(provider, ...)
    // rollback: implement captureTransferSnapshot() returning a restore() that puts gas/limits back
}
```

This gives you a fully pipe-capable endpoint with **no** compile dependency on `gas.*` — the safest posture for 0.1.x.

### 7.5 API-stability risks (read before coding)

1. **`api.*` is the only documented public surface.** `api/package-info.java` states: *"Public integration surface for Create Crafted Beginning."* `gas.**` is behaviour code: public classes, no stability promise, some members `@Internal`. Using `gas.storage.GasTank`, `gas.transfer.GasPressureFillService`, `gas.behaviour.GasTransportBehaviour` works today but may move/change in any update. **Mitigation:** depend on `api.` only (§7.4), or funnel every `gas.*` call through one adapter class so a version bump is a one-file fix.
2. **Version pinning.** 0.1.1 is the first published version; the mod is explicitly an early addon (mod list, wiki, frequent content drops). `javap` proof only covers *this* jar. **Mitigation:** `versionRange="[0.1.1,0.2)"` for `createcraftedbeginning`, and add a runtime check with `ModList.get().getModContainerById("createcraftedbeginning")` before touching classes that could vanish — or simply hard-depend and document the supported version.
3. **Behavioural contracts are implicit, not API'd.** Nothing in the API says "SIMULATE must be pure", "endpoint identity must be stable", "fills may be rolled back", or "a `GasHandler` that is not a `GasStorageHandler` is inert for pipes". These were reverse-engineered from the solver (§3.4, §4.4). Getting one wrong does not fail a compile — it corrupts a player's gas network silently or throws mid-tick inside the pipe simulation. **Mitigation:** copy `GasTank`'s observable behaviour exactly, write a unit-style test that hammers `SIMULATE` and asserts no mutation, and test with a real pipe run + tank before shipping.
4. **Numeric limits are hard.** `GasTankLimits` throws above 24 atm; `GasPressure.amount` saturates; `GasStack` rejects amount ≤ 0 by normalising to EMPTY (silent). Validate all AE2-supplied numbers (negative/long-overflow) before handing them to CCB.
5. **Pressure is not yours to invent.** If you present a `FIXED` compartment with an arbitrary pressure (e.g. 1 atm) and no rate limit, you become an infinite source/sink that can rupture a player's tanks. Prefer `VARIABLE` buffer tanks with `SAFE_PRESSURE_PA`, plus an explicit tick rate limit.
