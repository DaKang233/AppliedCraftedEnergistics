# Applied Crafted Energistics — AE2 19.2.20 Addon API Handbook

Implementer's handbook for adding a **custom stored resource type** ("gas", from *Create: Crafted
Beginning*) to **Applied Energistics 2 19.2.20** on **NeoForge 1.21.1**.

Everything here was verified against the **production jar** the mod will actually run against
(`libs/appliedenergistics2-19.2.20.jar`, 8.2 MB, 5203 entries) with `javap -p`, *not* just against
the AE2 source tree. Where a claim is about the jar rather than source, the evidence file
(`docs/AE2_API_EVIDENCE.md`) contains the raw `javap` output.

---

## 0. Provenance and how to read the citations

| What | Location | Version |
|---|---|---|
| AE2 source | `<workspace>\Applied-Energistics-2` | git tag `neoforge/v19.2.20` + 2 commits (`67d2a9b08`, `f466f2a92`) — **neither commit touches `appeng.api`** (verified: only `ChargerBlockEntity.java`, `MatterCannonItem.java`) |
| AE2 jar (target runtime) | `<repo>\libs\appliedenergistics2-19.2.20.jar` | 19.2.20 |
| Reference addon #1 (Mekanism chemicals) | `<workspace>\Applied-Mekanistics` | AE2 **19.2.10** |
| Reference addon #2 (PneumaticCraft air) | `<workspace>\AppliedPneumatics` | AE2 **19.2.17** |
| Gas API we must bridge | `<workspace>\CreateCraftedBeginning` + `libs\createcraftedbeginning-0.1.1.jar` | 0.1.1 |

**Citation conventions used throughout**

* `appeng/.../Foo.java:123` — a path **relative to**
  `<workspace>\Applied-Energistics-2\src\main\java\` (AE2 source).
* `mekae2/...` — relative to `...\Applied-Mekanistics\src\main\java\me\ramidzkh\mekae2\`.
* `pneumatics/...` — relative to
  `...\AppliedPneumatics\src\main\java\com\wintercogs\appliedpneumatics\`.
* `javap:` — verified against `libs/appliedenergistics2-19.2.20.jar`; raw output in the evidence file.
* `CCB` — refers to `net.ty.createcraftedbeginning.api.gas.*` from Create: Crafted Beginning 0.1.1.

### Version-difference headline (details in §9)

`git diff neoforge/v19.2.10..neoforge/v19.2.20 -- src/main/java/appeng/api` is **two files, 6 lines**
(`AEFluidKey.hasComponents`, `AEItemKey.getFuzzySearchMaxValue`). `19.2.17..19.2.20` is the same two
files. **There is no API break between 19.2.10, 19.2.17 and 19.2.20 for anything this mod needs.**
Both reference addons can be copied almost verbatim.

---

## 1. Ground truth: what the brief assumed vs. what 19.2.20 actually has

The task brief names several classes from *older* AE2 (1.19/1.20-era, and the older 1.18 API doc that
still ships in `API.md` in the AE2 repo). **These do not exist in the 19.2.20 jar.** Verified with
`jar tf` — no matches at all:

| Assumed in brief | Status in 19.2.20 | Replacement |
|---|---|---|
| `IAEKey` | **absent** | `appeng.api.stacks.AEKey` (abstract class) |
| `IAEStack` | **absent** (removed in 1.18) | `appeng.api.stacks.GenericStack` (record of `AEKey` + `long`) |
| `IStorageChannel` / `StorageChannels` | **absent** | `AEKeyType` / `AEKeyTypes` |
| `CellHandler` / `CellHandler.Impl` / `CellHandler.Stats` | **absent** | `ICellHandler` + `StorageCells`, or just `IBasicCellItem` |
| `CellHandlerRegistry` | **absent** | static list inside `appeng.api.storage.StorageCells` |
| `ICellInventory` | **absent** | `appeng.api.storage.cells.StorageCell` |
| `ItemVariantHolder` | **absent** | — |
| `IExternalStorageMonitor` | **absent** | `appeng.api.behaviors.ExternalStorageStrategy` |
| `IUpgradeModule` | **absent** | `appeng.api.upgrades.IUpgradeableItem` / `Upgrades` |
| `BasicCellInventory` | **present but internal**: `appeng.me.cells.BasicCellInventory`, private ctor, only `createInventory(ItemStack, ISaveProvider)` is public | use `IBasicCellItem`, or `ICellHandler` + your own `StorageCell` |
| `ae2.api.storage.cells.CellHandler` etc. | absent | see above |
| `AEApi.instance().registries()...` | **absent** | `AEKeyTypes` / `StorageCells` / `StackXStrategy.register(...)` statics |
| `IExternalStorageMonitor`, `StorageAdapter` | absent | `ExternalStorageStrategy`, `MEStorage`, `StackTransferContext` |

**What *does* exist** (all verified in-jar, see evidence file):

```
appeng.api.stacks.AEKeyType                  appeng.api.stacks.AEKey
appeng.api.stacks.AEKeyTypes                 appeng.api.stacks.GenericStack
appeng.api.storage.MEStorage                 appeng.api.storage.StorageCells
appeng.api.storage.cells.StorageCell         appeng.api.storage.cells.IBasicCellItem
appeng.api.storage.cells.ICellHandler        appeng.api.storage.cells.ISaveProvider
appeng.api.storage.cells.CellState           appeng.api.storage.cells.ICellWorkbenchItem
appeng.api.behaviors.ExternalStorageStrategy appeng.api.behaviors.StackImportStrategy
appeng.api.behaviors.StackExportStrategy     appeng.api.behaviors.StackTransferContext
appeng.api.behaviors.GenericInternalInventory appeng.api.behaviors.GenericSlotCapacities
appeng.api.client.AEKeyRendering             appeng.api.client.AEKeyRenderHandler
appeng.api.client.StorageCellModels          appeng.api.AECapabilities (ME_STORAGE, GENERIC_INTERNAL_INV)
appeng.items.storage.BasicStorageCell        appeng.items.storage.StorageTier
appeng.items.tools.powered.PortableCellItem  appeng.parts.automation.HandlerStrategy
appeng.parts.automation.StorageImportStrategy appeng.parts.automation.StorageExportStrategy
appeng.me.storage.ExternalStorageFacade      appeng.me.storage.CompositeStorage
```

### Public API vs. internal — be explicit about which you are using

* **True API** (`appeng.api.*`): `AEKeyType`, `AEKey`, `AEKeyTypes`, `GenericStack`, `MEStorage`,
  `StorageCells`, `storage.cells.*`, `behaviors.*`, `client.*`, `upgrades.*`, `AECapabilities`.
* **Internal but public and used by every addon** (no `@ApiStatus.Internal`, but not in `appeng.api`):
  `appeng.items.storage.BasicStorageCell`, `StorageTier`, `PortableCellItem`,
  `appeng.me.storage.ExternalStorageFacade`, `appeng.me.storage.CompositeStorage`,
  `appeng.util.ConfigInventory`, `appeng.core.localization.GuiText/Tooltips`, `appeng.core.AEConfig`.
  **AppMek and AppliedPneumatics both use these.** We will too. Risk: no compatibility guarantee.
* **Internal with `@ApiStatus.Internal` upstream**: `appeng.api.stacks.AEKeyTypesInternal` (don't touch),
  `appeng.parts.automation.StackWorldBehaviors` (only reachable via the API's `register` statics — use
  the statics), `appeng.arrays...`, `appeng.me.cells.BasicCellInventory` (use via `IBasicCellItem`).
* **`@ApiStatus.Experimental`**: the whole `appeng.api.behaviors` package. It is the designated
  extension point for exactly our problem, but signatures may shift in a future 19.2.x/20.x.
  Pin AE2 to `[19.2.20,20.0.0)` in `neoforge.mods.toml`.

---

## 2. Layer 1 — Key type and key

### 2.1 `AEKeyType` — one instance per resource *kind*

`appeng/api/stacks/AEKeyType.java:56`. It is an **abstract class**, not an interface. Required
overrides are only two; everything else is a tuning knob.

```java
// appeng/api/stacks/AEKeyType.java (verified: javap appeng.api.stacks.AEKeyType)
public abstract class AEKeyType {
    public static final ResourceKey<Registry<AEKeyType>> REGISTRY_KEY;   // ae2:keytypes
    public static final Codec<AEKeyType> CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, AEKeyType> STREAM_CODEC;

    public AEKeyType(ResourceLocation id, Class<? extends AEKey> keyClass, Component description);

    public abstract MapCodec<? extends AEKey> codec();                    // NBT/JSON codec for keys
    public abstract AEKey readFromPacket(RegistryFriendlyByteBuf input);  // packet reader

    // Optional overrides that matter for a custom ratio:
    public int  getAmountPerOperation();   // default 1   — one "bus operation"
    public int  getAmountPerByte();        // default 8   — storage-cell ratio
    public int  getAmountPerUnit();        // default 1   — display divisor
    public String getUnitSymbol();         // default null
    public AEKey loadKeyFromTag(HolderLookup.Provider, CompoundTag);       // default: via codec()
    public boolean supportsFuzzyRangeSearch();                             // default false
    public Stream<TagKey<?>> getTagNames();                                // default empty
    public final byte getRawId();          // registry id, must be 0..127
    public final Component getDescription();
}
```

Notes that bite:

* `getRawId()` is `byte` and the registry is built with `.maxId(127)`
  (`appeng/core/AppEngBase.java:231-233`). In practice items = 0 and fluids = 1 because AE2 registers
  them first (`AppEngBase.java:164-165` → `:217-220`) and we load after AE2, so our gas type lands on
  **2**. Don't hard-code the number: it is written to the network as a varint only via
  `getRawId()`, never persisted.
* `readFromPacket` returns `@Nullable`; AE2 logs and drops unknown ids.
* `loadKeyFromTag` default already goes through `codec()`, so you only override it if you want a
  cheaper parse or a fallback (both reference mods override it just to add debug logging).

### 2.2 `AEKey` — one instance per *gas type*

`appeng/api/stacks/AEKey.java`. It is an abstract class. **Abstract members you must implement**
(exact list from `javap appeng.api.stacks.AEKey`):

```java
public abstract AEKeyType getType();
public abstract AEKey dropSecondary();
public abstract CompoundTag toTag(HolderLookup.Provider registries);
public abstract Object getPrimaryKey();
public abstract ResourceLocation getId();
public abstract void writeToPacket(RegistryFriendlyByteBuf data);
protected abstract Component computeDisplayName();
public abstract void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos);
public abstract boolean hasComponents();
```

Everything else is `final` or defaulted: `getDisplayName()` (caches `computeDisplayName()`),
`toTagGeneric`/`fromTagGeneric` (adds `"#t"` = `AEKey.TYPE_FIELD` so the type can be restored
without knowing it in advance), `matches(GenericStack)`, `getAmountPerByte()` etc. (delegated to the
`AEKeyType`), `isTagged`, `get(DataComponentType)`, `wrapForDisplayOrFilter()`.

Sample implementation for CCB gases (mirrors `mekae2/ae2/MekanismKey.java`, but stores the immutable
`Holder<Gas>` instead of a mutable `GasStack` — **important**, because `GasStack.setAmount/shrink/grow`
are mutating):

```java
package com.appliedcraftedenergistics.me;

public final class GasKey extends AEKey {
    /** Codec: exactly the shape MekanismKey uses. */
    public static final MapCodec<GasKey> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Gas.HOLDER_CODEC.fieldOf("id").forGetter(key -> key.holder))
            .apply(instance, GasKey::new));
    public static final Codec<GasKey> CODEC = MAP_CODEC.codec();

    private final Holder<Gas> holder;

    private GasKey(Holder<Gas> holder) {
        this.holder = holder;
    }

    @Nullable
    public static GasKey of(GasStack stack) {
        return stack.isEmpty() ? null : new GasKey(stack.getGasHolder());
    }

    public Holder<Gas> holder() { return holder; }
    public Gas gas()           { return holder.value(); }
    public GasStack toStack(long amount) { return new GasStack(holder, amount); }

    @Override public AEKeyType getType()  { return GasKeyType.INSTANCE; }
    @Override public AEKey dropSecondary() { return this; }        // we ignore data components
    @Override public Object getPrimaryKey() { return gas(); }      // identity of the gas

    @Override public ResourceLocation getId() { return holder.unwrapKey().orElseThrow().location(); }

    @Override public CompoundTag toTag(HolderLookup.Provider registries) {
        return (CompoundTag) CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this)
                .getOrThrow();
    }

    @Override public void writeToPacket(RegistryFriendlyByteBuf data) {
        GasStack.STREAM_CODEC.encode(data, new GasStack(holder, 1));
    }

    public static GasKey fromPacket(RegistryFriendlyByteBuf data) {
        return new GasKey(GasStack.STREAM_CODEC.decode(data).getGasHolder());
    }

    @Override protected Component computeDisplayName() { return gas().getName(new GasStack(holder, 1)); }

    @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        // Gases have no item form -> nothing to drop. (Same choice as AirKey.)
    }

    @Override public boolean hasComponents() { return false; }
    @Override public boolean isTagged(TagKey<?> tag) { return gas().is((TagKey<Gas>) tag); }

    @Override public boolean equals(Object o) {
        return o instanceof GasKey other && other.holder.unwrapKey().equals(holder.unwrapKey());
    }
    @Override public int hashCode() { return holder.unwrapKey().hashCode(); }
}
```

Gotchas:

* `equals`/`hashCode` must be stable and cheap — keys are hash-map keys in `KeyCounter`,
  `Object2LongOpenHashMap`, network storage, and the cell's stored-amount map. Compare the *registry
  key*, not the `Holder` object (`Holder` does not override `equals`).
* `addDrops` is called when a block holding the key is broken (e.g. an interface drops its stocked
  slots). Gases have no item → no-op is correct and is what AppliedPneumatics does
  (`pneumatics/common/me/keys/AirKey.java:78-81`).
* If CCB gas stacks can carry meaningful data components, ignoring them (as here) means two
  differently-componented stacks collapse into one key. Decide deliberately.

### 2.3 Registration — and the one timing trap

`AEKeyType` instances live in a real NeoForge registry (`ae2:keytypes`), created by AE2 in
`NewRegistryEvent` (`appeng/core/AppEngBase.java:230-235`) and populated **inside the `RegisterEvent`
for that registry** (`appeng/core/AppEngBase.java:164-165` → `registerKeyTypes(...)` at `:217`).

**Correct, AE2-mirroring pattern** (use this):

```java
bus.addListener((RegisterEvent event) -> {
    if (event.getRegistryKey().equals(AEKeyType.REGISTRY_KEY)) {
        Registry.register(event.getRegistry(AEKeyType.REGISTRY_KEY), GAS_TYPE_ID, GasKeyType.INSTANCE);
    }
});
```

**Alternative used by AppMek** (`mekae2/AppliedMekanistics.java:56-62`): gate on
`Registries.BLOCK` and call the public helper

```java
AEKeyTypes.register(GasKeyType.INSTANCE);   // AEKeyTypes.java:56 -> AEKeyTypesInternal.register (:50)
```

which internally does `Registry.register(getRegistry(), keyType.getId(), keyType)`. It works because
NeoForge keeps all registries unlocked during the whole `RegisterEvent` phase, but it depends on
*which* registry event fires first and on AE2 having already created the registry. It is also what
**AppliedPneumatics** does (`pneumatics/common/me/AEPlugin.java:39`, called from a
`Registries.BLOCK`-gated listener in `AppliedPneumatics.java:42-45`). Prefer the first form; it is a
one-line change and removes an ordering assumption.

**Never** register a key type from a `DeferredRegister`, from the mod constructor, or from
`FMLCommonSetupEvent` — the registry is frozen by then and you get
`IllegalStateException: Registry is already frozen`.

### 2.4 The numbers: what to return for "gas"

AE2's convention is **one byte = `getAmountPerByte()` units of the resource**:

| Type | `getAmountPerByte()` | `getAmountPerUnit()` | `getAmountPerOperation()` | source |
|---|---|---|---|---|
| items | `8` (8 items/byte, i.e. 1 bit each) | 1 | 1 | `AEKeyType.java:133,141,217` |
| fluids | `8 * 1000 = 8000` mB/byte | `1000` (mB per bucket) | `125` mB | `appeng/api/stacks/AEFluidKeys.java:51-77` |
| chemicals (AppMek) | `8 * 1000 = 8000` | `1000` | `125` | `mekae2/ae2/MekanismKeyType.java:36-47` |
| air (AppliedPneumatics) | `250` mL/byte | `1000` (1 L) | `1000` | `pneumatics/common/me/keys/types/AirKeyType.java:39-58` |
| **gas (recommended)** | **`8000` GU/byte** | **`1000` (kGU)** | **`1000` GU** | this doc |

Recommended `GasKeyType` body:

```java
public final class GasKeyType extends AEKeyType {
    private static final ResourceLocation ID = AppliedCraftedEnergistics.id("gas");
    public static final Component NAME = Component.translatable("ae2.key_type.appliedcraftedenergistics.gas");
    public static final GasKeyType INSTANCE = new GasKeyType();

    private GasKeyType() { super(ID, GasKey.class, NAME); }

    @Override public MapCodec<? extends AEKey> codec() { return GasKey.MAP_CODEC; }
    @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return GasKey.fromPacket(input); }

    /** Buses transfer one "operation" per tick per unit of speed; 1000 GU ≈ one kilo-unit step. */
    @Override public int getAmountPerOperation() { return 1000; }
    /** 8 kGU per byte — the same "8 buckets per byte" shape AE2 uses for fluids. */
    @Override public int getAmountPerByte() { return 8 * 1000; }
    /** Display: show kGU so numbers stay readable in terminals. */
    @Override public int getAmountPerUnit() { return 1000; }
    @Override public String getUnitSymbol() { return "kGU"; }
    @Override public Stream<TagKey<?>> getTagNames() {
        return GasRegistries.GAS_REGISTRY.getTagNames().map(t -> t);
    }
}
```

Why `amountPerOperation = 1000` and not 1: an export bus does
`getOperationsPerTick() * getAmountPerOperation()` units per tick
(`appeng/parts/automation/ExportBusPart.java:162-166`, `:164`), and `getOperationsPerTick()` is 1 / 8 /
32 / 64 / 96 for 0–4 speed cards (`appeng/parts/automation/IOBusPart.java:194`). With `1` you would
move 96 GU/tick at max speed — unusably slow. With `1000` you move 96 000 GU/tick.
**Whatever you choose, it must stay ≤ `Integer.MAX_VALUE` and ≥ 1.**

### 2.5 Where the type shows up *for free*, and where it does not

Free, once registered (all of these are written against `AEKey`/`MEStorage` generically — no addon
code needed):

* **Network storage**: `IStorageService` keeps one sub-inventory per registered `AEKeyType`
  (`AEKeyTypes.getAll()`), so gas is stored/indexed/watched like items.
* **Terminals**: `MEStorageScreen`/`Repo` iterate network storage; the terminal's key-type filter list
  is built from `AEKeyTypes.getAll()` (`appeng/client/gui/implementations/KeyTypeSelectionScreen.java:104,119`,
  `appeng/menu/interfaces/KeyTypeSelectionMenu.java:50-58`).
* **Interface slots**: `InterfaceLogic` builds `ConfigInventory.configStacks/storage(slots)` with *all*
  key types (`appeng/helpers/InterfaceLogic.java:105-106`) and `GenericStackInv` accepts any registered
  type (`appeng/helpers/externalstorage/GenericStackInv.java:105`).
* **Import/export bus config slots**: `ConfigInventory.configTypes(63).supportedTypes(...)` where the
  supported set is "all types with an import/export strategy"
  (`appeng/parts/automation/IOBusPart.java:92-96`, `ImportBusPart.java:45`, `ExportBusPart.java:85`).
* **Cells**: `StorageCells.getCellInventory` → `BasicCellHandler.isCell` → `instanceof IBasicCellItem`
  (`appeng/me/cells/BasicCellHandler.java:40-48`).
* **Patterns**: `IPatternDetails` inputs/outputs are `GenericStack`s
  (`appeng/crafting/pattern/AEProcessingPattern.java:47`).

**NOT free — you must write it:** the client-side icon renderer. `AEKeyRendering.getOrThrow`
**throws** `IllegalArgumentException("Missing render handler for channel ...")`
(`appeng/api/client/AEKeyRendering.java:77-91`), and it is called from every terminal row draw. See §4.3.

---

## 3. Layer 2 — Storage cells

### 3.1 Two paths — pick one deliberately

| | Path A: `IBasicCellItem` | Path B: `ICellHandler` + `StorageCell` |
|---|---|---|
| Effort | tiny (subclass `BasicStorageCell`) | ~200 lines of inventory code |
| Byte/type accounting | AE2's, done for you | yours |
| Multi-type | yes, up to 63 (`BasicCellInventory.MAX_ITEM_TYPES`) | yours |
| Persistence | AE2's `AEComponents.STORAGE_CELL_INV` (a `List<GenericStack>` component) | yours (own `DataComponentType`, e.g. a single `long`) |
| Upgrades (inverter/fuzzy/void/equal-distribution) | handled by `BasicCellInventory` | you implement each |
| Tooltip (bytes/types/content/upgrades) | free (`addCellInformationToTooltip`, `getCellTooltipImage`) | you build `StorageCellTooltipComponent` yourself |
| Cell Workbench (partition/fuzzy) | free | you implement `ICellWorkbenchItem` |
| Used by | **AppMek** (`mekae2/item/ChemicalStorageCell.java`) | **AppliedPneumatics** (`pneumatics/common/me/storage/AirCellInventory.java`) |

**Recommendation for Applied Crafted Energistics v1: Path A.** Gases are a handful of types; AE2's accounting
is what players expect; 90 % less code. Path B is only justified if you want *single-gas-per-cell*
semantics with pressure-style quirks (which is exactly why AppliedPneumatics wrote it for air).

### 3.2 Path A — `IBasicCellItem`

`appeng/api/storage/cells/IBasicCellItem.java` (javap-verified):

```java
public interface IBasicCellItem extends ICellWorkbenchItem {
    AEKeyType getKeyType();
    int  getBytes(ItemStack cellItem);        // total bytes
    int  getBytesPerType(ItemStack cellItem);// bytes reserved per stored type
    int  getTotalTypes(ItemStack cellItem);   // 1..63
    default boolean isBlackListed(ItemStack cellItem, AEKey requestedAddition) { return false; }
    default boolean storableInStorageCell() { return false; }
    default boolean isStorageCell(ItemStack i) { return true; }
    double getIdleDrain();                    // AE/t
    default void addCellInformationToTooltip(ItemStack is, List<Component> lines);
    default Optional<TooltipComponent> getCellTooltipImage(ItemStack is);
}
```

and `ICellWorkbenchItem` (`javap`) adds:

```java
default boolean isEditable(ItemStack is);
default ConfigInventory getConfigInventory(ItemStack is);  // the partition/filter slots
FuzzyMode getFuzzyMode(ItemStack is);
void setFuzzyMode(ItemStack is, FuzzyMode fzMode);
```

**No registration call is needed.** `BasicCellHandler.INSTANCE` is registered by AE2 itself
(`appeng/init/internal/InitStorageCells.java:79-80`) and matches *any* item that implements
`IBasicCellItem` (`appeng/me/cells/BasicCellHandler.java:41` → `BasicCellInventory.isCell`).

Simplest correct implementation is a subclass of `appeng.items.storage.BasicStorageCell`
(javap-verified ctor):

```java
public class GasStorageCell extends BasicStorageCell {
    public GasStorageCell(Properties props, StorageTier tier, int bytesPerType, int totalTypes) {
        // NOTE 3rd arg is KILOBYTES, multiplied by 1024 inside (BasicStorageCell.java:68)
        super(props, tier.idleDrain(), tier.bytes() / 1024, bytesPerType, totalTypes, GasKeyType.INSTANCE);
    }

    @Override
    public boolean isBlackListed(ItemStack cellItem, AEKey requestedAddition) {
        // A gas cell may only ever store gas keys.
        return !(requestedAddition instanceof GasKey);
    }
}
```

Reference numbers, straight out of AE2's own registration (`appeng/core/definitions/AEItems.java:262-266`):

```java
// AE2 fluid cells — the correct template for gas cells
FLUID_CELL_1K   = new BasicStorageCell(p.stacksTo(1), 0.5f,   1,   8, 18, AEKeyType.fluids());
FLUID_CELL_4K   = new BasicStorageCell(p.stacksTo(1), 1.0f,   4,  32, 18, AEKeyType.fluids());
FLUID_CELL_16K  = new BasicStorageCell(p.stacksTo(1), 1.5f,  16, 128, 18, AEKeyType.fluids());
FLUID_CELL_64K  = new BasicStorageCell(p.stacksTo(1), 2.0f,  64, 512, 18, AEKeyType.fluids());
FLUID_CELL_256K = new BasicStorageCell(p.stacksTo(1), 2.5f, 256, 2048, 18, AEKeyType.fluids());
```

`BasicStorageCell` also gives you, already wired: `getUpgrades` (3 slots for non-item types,
`BasicStorageCell.java:115-118`), `getConfigInventory` = `CellConfig.create(Set.of(keyType), is)`
(`:120-123`), fuzzy mode via `AEComponents.STORAGE_CELL_FUZZY_MODE`, right-click-in-alt-mode
disassembly via `StorageCellDisassemblyRecipe`, and LED tinting via the static
`BasicStorageCell.getColor(stack, tintIndex)`.

### 3.3 The byte accounting model, exactly

From `appeng/me/cells/BasicCellInventory.java` (these are the formulas that decide
"how much gas fits", "when is the LED orange", "what does the tooltip say"):

```java
// :302
getUsedBytes()            = storedTypes * bytesPerType
                          + (storedItemCount + getUnusedItemCount()) / amountPerByte;

// :312  — the partially-filled byte at the end
getUnusedItemCount()      = (storedItemCount % amountPerByte == 0) ? 0
                          : amountPerByte - (storedItemCount % amountPerByte);

// :307
getRemainingItemCount()   = max(0, freeBytes * amountPerByte + getUnusedItemCount());
getFreeBytes()            = totalBytes - getUsedBytes();

// :296
getRemainingItemTypes()   = min(freeBytes / bytesPerType, totalTypes - storedTypes);

// :269
canHoldNewItem()          = (freeBytes > bytesPerType
                          || (freeBytes == bytesPerType && getUnusedItemCount() > 0))
                          && getRemainingItemTypes() > 0;
```

`getStatus()` (`:319-330`) maps those to `CellState.EMPTY / NOT_EMPTY / TYPES_FULL / FULL`, which is
what the drive LED and the ME chest show (`CellState.getStateColor()`).

`insert` deducts the type cost *before* it deducts capacity for a brand-new type:

```java
// :378-395 (innerInsert)
if (currentAmount <= 0) {
    if (!canHoldNewItem()) return 0;
    remainingItemCount -= (long) bytesPerType * amountPerByte;   // upfront type cost
    if (remainingItemCount <= 0) return 0;
}
```

So with `1k` (1024 bytes), `bytesPerType = 8`, `amountPerByte = 8000`:
`8192 kGU` if one gas, `(1024 - 8*18)*8000 = 6 976 000 GU` if all 18 types are used. Same shape as
AE2 fluid cells — that is a *feature*, users recognise it.

**These formulas are AE2's, not a suggestion.** If you go Path B you must reimplement them or accept
that your cell's tooltip and LED will disagree with its capacity.

### 3.4 Upgrades

AE2 handles upgrades **inside `BasicCellInventory`**, driven by `Upgrades.add(...)` associations plus
the upgrade inventory your item exposes:

| Card | Where the behaviour lives | What it does for a gas cell |
|---|---|---|
| Inverter | `BasicCellInventory.java:98-108` (`partitionListMode = BLACKLIST`) | flips the partition list |
| Fuzzy | `:98-106` + `getFuzzyMode()` | only meaningful if `supportsFuzzyRangeSearch()`; for gas it is a no-op — **don't advertise it** |
| Void | `:125`, `:337-361` | accepts and discards overflow |
| Equal Distribution | `:110-124` | caps amount per type at `(totalBytes - bytesPerType*maxTypes) * amountPerByte / maxTypes` |
| Capacity / Energy / Crafting | not applicable to non-portable cells | — |

Register the associations in `FMLCommonSetupEvent` (AppMek pattern,
`mekae2/AppliedMekanistics.java:112-127`):

```java
Upgrades.add(AEItems.INVERTER_CARD,          GAS_CELL_1K, 1, "gui.tooltips.ae2.StorageCells");
Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD,GAS_CELL_1K, 1, "gui.tooltips.ae2.StorageCells");
Upgrades.add(AEItems.VOID_CARD,              GAS_CELL_1K, 1, "gui.tooltips.ae2.StorageCells");
```

(`GuiText.StorageCells.getTranslationKey()` is the constant AE2 uses; the string above is its value —
read it from `appeng.core.localization.GuiText` rather than hard-coding, as AppMek does.)
The upgrade *inventory* comes from `BasicStorageCell.getUpgrades` — you get it for free.

### 3.5 Persistence — how cell contents are stored in 19.2.x

**Not in a raw NBT tag on the item any more.** `BasicCellInventory` writes a **data component**:

```java
// appeng/me/cells/BasicCellInventory.java:129, :206-209
private List<GenericStack> getStoredStacks() {
    return i.getOrDefault(AEComponents.STORAGE_CELL_INV, List.of());
}
...
if (stacks.isEmpty()) i.remove(AEComponents.STORAGE_CELL_INV);
else                  i.set(AEComponents.STORAGE_CELL_INV, stacks);
```

* `GenericStack` is a record `(AEKey what, long amount)`; its codec writes `{"#t": "<keytype id>", ...}`
  so gas entries deserialize back into `GasKey` **only if `GasKeyType` is registered on both sides**.
* Save is lazy: `saveChanges()` marks dirty and calls `ISaveProvider.saveChanges()` if a host (drive /
  ME chest) is present, else persists immediately (`:217-230`).
* Consequence: a gas cell dropped into the world by a *different* mod build, or with our mod removed,
  becomes AE2's `MISSING_CONTENT` item and **keeps its raw data** so it can be restored
  (`appeng/api/stacks/AEKey.java:63-108`). Don't try to "clean this up".

### 3.6 Path B — `ICellHandler` + `StorageCell` (only if you need custom semantics)

```java
public interface ICellHandler {                                   // javap-verified
    boolean isCell(ItemStack is);
    @Nullable StorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host);
}

public interface StorageCell extends MEStorage {                  // javap-verified
    CellState getStatus();
    double getIdleDrain();
    default boolean canFitInsideCell() { return true; }
    void persist();
}

public interface ISaveProvider { void saveChanges(); }            // javap-verified
```

Registration is a static list, **and it must be filled after AE2's own registration**:

```java
// StorageCells.java:56 "Never be call before FMLCommonSetupEvent"
StorageCells.addCellHandler(new GasCellHandler());
```

`MEStorage` (javap) is 5 methods, all defaulted except `getDescription()`:

```java
default boolean isPreferredStorageFor(AEKey what, IActionSource source) { return false; }
default long insert(AEKey what, long amount, Actionable mode, IActionSource source) { return 0; }
default long extract(AEKey what, long amount, Actionable mode, IActionSource source) { return 0; }
default void getAvailableStacks(KeyCounter out) {}
Component getDescription();
```

The AppliedPneumatics inventory (`pneumatics/common/me/storage/AirCellInventory.java`) is the
complete worked example: a single `long storedAir` cached in the object, written back to the item only
in `persist()`, `markChanged()` routing to `host.saveChanges()` when hosted or `persist()` when not
(`:180-198`), and byte math reimplemented at `:200-250`. Copy it if you go this route — including its
lesson that `insert` must **return only what it actually accepted** when a void card is absent.

### 3.7 Cell menus — what a basic cell actually needs

**Nothing.** A non-portable cell has no GUI:
the Drive and ME Chest open *their own* menus, and partition/fuzzy configuration goes through the
**Cell Workbench**, which drives `ICellWorkbenchItem.getConfigInventory(is)` — already implemented by
`BasicStorageCell` as `CellConfig.create(Set.of(keyType), is)`.

You only need a menu for a **portable** cell (§4.4), or if you want a bespoke GUI (not v1).

---

## 4. Layer 3 — Cell item resources and client

### 4.1 Item model (inventory/hand)

`item/generated` with `layer0` = housing texture, `layer1` = AE2's `item/storage_cell_led`
(tinted by the LED colour). Copy `mekae2/data/ItemModelProvider.java:53-66`:

```java
private void cell(DeferredItem<?> cell, String background) {
    withExistingParent(cell.getId().getPath(), mcLoc("item/generated"))
            .texture("layer0", AppliedCraftedEnergistics.id(background))    // item/gas_storage_cell_1k
            .texture("layer1", AppEng.makeId("item/storage_cell_led"));  // note: ae2 namespace
}
```

Textures needed: `assets/appliedcraftedenergistics/textures/item/gas_cell_housing.png` (the housing, shared
by all tiers) and `item/gas_storage_cell_1k.png` … `_256k.png` (the tier label). AppMek ships exactly
`textures/item/chemical_cell_housing.png` + one png per tier.

### 4.2 3D model in the Drive / ME Chest

`StorageCellModels.registerModel(item, modelId)` (`appeng/api/client/StorageCellModels.java:74`)
maps an item to a **JSON model that is rendered inside the drive**. You do *not* need
`ModelEvent.RegisterAdditional`: `DriveModel.getDependencies()` explicitly returns
`StorageCellModels.models().values()` (`appeng/client/render/model/DriveModel.java:66-72`), so anything
registered becomes a bake dependency. This is easy to get wrong in the other direction — the model
**must** exist at `assets/<ns>/models/<modelId>.json` and must be registered **before** model baking
(do it in `FMLCommonSetupEvent.enqueueWork`, as AppMek does at
`mekae2/AppliedMekanistics.java:101-110`).

Minimal drive-cell model (AppMek's, `assets/appmek/models/block/drive/cells/chemical_storage_cell_1k.json`):
a single `6×2×2` element with `"cullface": "north"` and a texture from a drive-cell sheet.

```java
// FMLCommonSetupEvent, enqueueWork
for (var tier : Tier.values()) {
    StorageCellModels.registerModel(GasCells.get(tier).get(), id("block/drive/cells/" + path));
}
```

### 4.3 The mandatory piece: `AEKeyRenderHandler`

This is the one thing that *crashes the client* if missing (§2.5). Register client-side only:

```java
AEKeyRendering.register(GasKeyType.INSTANCE, GasKey.class, new GasKeyRenderer());
```

`AEKeyRenderHandler<T extends AEKey>` (javap) requires `drawInGui`, `drawOnBlockFace`,
`getDisplayName`, and optionally `getTooltip`. Model it on
`mekae2/ae2/AMChemicalStackRenderer.java` (block-atlas sprite + tint through `Blitter.sprite(...)`) or
on `pneumatics/client/me/render/AirKeyRenderHandler.java` (custom texture with particles).

For gas the natural version is:

```java
@Override
public void drawInGui(Minecraft mc, GuiGraphics gg, int x, int y, GasKey key) {
    var gas = key.gas();
    var sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(gas.getTexture());
    Blitter.sprite(sprite)
            .colorRgb(gas.getTint(new GasStack(key.holder(), 1)))
            .blending(false)         // gas textures are usually transparent; we want an opaque slot
            .dest(x, y, 16, 16)
            .blit(gg);
}
```

`Gas.getTexture()` → `ResourceLocation`, `Gas.getTint(GasStack)` → `int`, `Gas.getName(GasStack)` →
`Component`, `Gas.getTooltip(GasStack)` → `List<Component>` — all javap-verified in the CCB jar.

Register in `FMLClientSetupEvent` → `event.enqueueWork(...)`, behind `FMLEnvironment.dist == Dist.CLIENT`
(`mekae2/AppliedMekanisticsClient.java:15-17`).

### 4.4 Portable cell (optional for v1)

`appeng.items.tools.powered.PortableCellItem` already does everything, including AE energy storage
(it extends `AbstractPortableCell` → `PoweredContainerItem`); **you do not implement
`IAEItemPowerStorage` yourself.** Constructor (javap):

```java
public PortableCellItem(AEKeyType keyType, int totalTypes, MenuType<?> menuType,
                        StorageTier tier, Item.Properties props, int defaultColor);
```

You must supply a `MenuType<MEStorageMenu>` built from `IPortableTerminal` and a screen JSON; both are
2-line files. Full example: `mekae2/item/ChemicalPortableCellItem.java` + `mekae2/AMMenus.java`.

```java
// menu type
public static final MenuType<MEStorageMenu> GAS_PORTABLE_CELL_TYPE = MenuTypeBuilder
        .create(MEStorageMenu::new, IPortableTerminal.class)
        .build(AppliedCraftedEnergistics.id("portable_gas_cell"));

// screen: the json lives under the *ae2* namespace because AE2's screen loader resolves
// style documents from ae2:screens/...
// file: src/main/resources/assets/ae2/screens/terminals/portable_gas_cell.json
// { "includes": ["portable_fluid_cell.json"] }
InitScreens.<MEStorageMenu, MEStorageScreen<MEStorageMenu>>register(
        event, GAS_PORTABLE_CELL_TYPE, MEStorageScreen::new, "/screens/terminals/portable_gas_cell.json");
```

Note `IAEItemPowerStorage` (`appeng/api/implementations/items/IAEItemPowerStorage.java`, javap:
`injectAEPower/extractAEPower/getAEMaxPower/getAECurrentPower/getPowerFlow/getChargeRate`) is only
needed for a *custom* powered item. `PortableCellItem` already implements the power side.

### 4.5 Item tint (the LED)

```java
// RegisterColorHandlersEvent.Item
ItemColor cells = (stack, tint) -> FastColor.ARGB32.opaque(BasicStorageCell.getColor(stack, tint));
event.register(cells, GAS_CELL_1K::get /* ... */);
```

`BasicStorageCell.getColor` returns `CellState.getStateColor()` for `tintIndex == 1` and white
otherwise (`BasicStorageCell.java:182-192`) — i.e. free, as long as your item model has a `layer1`.

---

## 5. Layer 4 — Device integration (the part that actually decides "can gas flow")

### 5.1 The architecture: how AE2 asks "can I push this key into that block?"

There are exactly **three** hooks, and they are all keyed by `AEKeyType`:

```
                       ┌─────────────────────────────────────────────┐
 pattern provider ────►│ PatternProviderTargetCache / PatternProviderTarget
 storage bus ─────────►│  1. AECapabilities.ME_STORAGE on the neighbour
 interface (pull) ────►│  2. else: StackWorldBehaviors.createExternalStorageStrategies(...)
                       │      └─ per-AEKeyType ExternalStorageStrategy → MEStorage wrapper
                       └─────────────────────────────────────────────┘
 import bus ──────────► StackImportStrategy (per AEKeyType)
 export bus ──────────► StackExportStrategy (per AEKeyType)
```

Concretely:

* **Pattern provider / ME Interface push target** — `PatternProviderTarget.get(...)`
  (`appeng/helpers/patternprovider/PatternProviderTarget.java:48-77`):
  first `l.getCapability(AECapabilities.ME_STORAGE, ...)` (works only for AE2-aware or addon blocks
  that expose `AECapabilities.ME_STORAGE`), **then**
  `StackWorldBehaviors.createExternalStorageStrategies((ServerLevel) l, pos, side)` (`:64`), asking
  every registered strategy for a wrapper (`:67`) and composing them
  (`new CompositeStorage(externalStorages)`, `:74`).
  The cached variant used by the ticker is `PatternProviderTargetCache` (`:21-56`, same order).
* **Storage bus** — `StorageBusPart.getExternalStorageStrategies()`
  (`appeng/parts/storagebus/StorageBusPart.java:550-555`) and `findExternalStorages(...)` (`:436-447`).
* **Import bus** — `ImportBusPart` (`:45`) constructs its config with
  `StackWorldBehaviors.withImportStrategy()`, and on first tick builds
  `StackWorldBehaviors.createImportFacade(level, fromPos, fromSide, keyTypeSelection.enabledPredicate())`
  (`:62`), then calls `strategy.transfer(context)`.
* **Export bus** — `ExportBusPart` (`:85`) uses `withExportStrategy()` and
  `createExportFacade(...)` (`:116`), then `transfer(context, what, amount)` (`:164`) or
  `push(what, amount, mode)` for crafting (`:202`).

**Therefore: registering the three per-type strategies is all that is required for AE2's stock
devices to handle gas. Do not subclass `ImportBusPart`, `ExportBusPart`, `StorageBusPart`,
`PatternProviderPart` or `InterfacePart`.** Both reference addons do exactly this
(`mekae2/AppliedMekanistics.java:64-66`, `pneumatics/common/me/AEPlugin.java:48-58`).

### 5.2 The one class that carries all three device behaviours: `ExternalStorageStrategy`

```java
// appeng/api/behaviors/ExternalStorageStrategy.java  (javap-verified)
@ApiStatus.Experimental
public interface ExternalStorageStrategy {
    @Nullable MEStorage createWrapper(boolean extractableOnly, Runnable injectOrExtractCallback);
    @FunctionalInterface interface Factory {
        ExternalStorageStrategy create(ServerLevel level, BlockPos fromPos, Direction fromSide);
    }
    static void register(AEKeyType type, Factory factory);   // -> StackWorldBehaviors
}
```

Semantics of the arguments:

* `extractableOnly` — set by the storage bus' *"Extractable Only"* filter
  (`Settings.STORAGE_FILTER`, `StorageBusPart.java:157,420`). Your wrapper must then report only
  what can actually be drained (see `ExternalStorageFacade.getAvailableStacks` for the pattern).
* `injectOrExtractCallback` — run it whenever you *modify* the adjacent inventory, so the bus/network
  invalidates its cache (`PatternProviderTargetCache.java:46` passes `() -> {}`; the storage bus passes
  `this::invalidateOnExternalStorageChange`).

Implementation (adapted from `mekae2/ae2/stack/MekanismExternalStorageStrategy.java` and
`pneumatics/common/me/strategies/AirExternalStorageStrategy.java`):

```java
public final class GasExternalStorageStrategy implements ExternalStorageStrategy {
    private final BlockCapabilityCache<GasHandler, Direction> cache;

    public GasExternalStorageStrategy(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        this.cache = BlockCapabilityCache.create(GasCapabilities.BLOCK, level, fromPos, fromSide);
    }

    @Nullable @Override
    public MEStorage createWrapper(boolean extractableOnly, Runnable injectOrExtractCallback) {
        var handler = cache.getCapability();
        if (handler == null) return null;
        var facade = new GasStorageFacade(handler);
        facade.setExtractableOnly(extractableOnly);
        facade.setChangeListener(injectOrExtractCallback);
        return facade;
    }
}
```

`GasStorageFacade extends appeng.me.storage.ExternalStorageFacade` (javap-verified abstract members
`getSlots/getStackInSlot/getKeyType/insertExternal/extractExternal/containsAnyFuzzy`):

```java
public final class GasStorageFacade extends ExternalStorageFacade {
    private final GasHandler handler;
    public GasStorageFacade(GasHandler handler) { this.handler = handler; }

    @Override public int getSlots() { return handler.getTanks(); }

    @Nullable @Override
    public GenericStack getStackInSlot(int slot) {
        var stack = handler.getGasInTank(slot);
        if (stack.isEmpty()) return null;
        var key = GasKey.of(stack);
        return key == null ? null : new GenericStack(key, stack.getAmount());
    }

    @Override public AEKeyType getKeyType() { return GasKeyType.INSTANCE; }

    @Override protected int insertExternal(AEKey what, int amount, Actionable mode) {
        if (!(what instanceof GasKey key) || amount <= 0) return 0;
        return (int) handler.fill(key.toStack(amount),
                GasAction.fromFluidAction(mode.getFluidAction()));   // fill() returns the FILLED amount
    }

    @Override protected int extractExternal(AEKey what, int amount, Actionable mode) {
        if (!(what instanceof GasKey key) || amount <= 0) return 0;
        return (int) handler.drain(key.toStack(amount),
                GasAction.fromFluidAction(mode.getFluidAction())).getAmount();
    }

    @Override public boolean containsAnyFuzzy(Set<AEKey> keys) {
        for (int i = 0; i < handler.getTanks(); i++) {
            var key = GasKey.of(handler.getGasInTank(i));
            if (key != null && keys.contains(key.dropSecondary())) return true;
        }
        return false;
    }
}
```

Two caveats on `ExternalStorageFacade`:

1. It takes `int` amounts (`insertExternal(AEKey, int, Actionable)`) and clamps with
   `Ints.saturatedCast` internally. Buses never exceed a few hundred thousand GU per operation, but a
   *bulk* network insert (or a very large tank) can. If that matters, implement `MEStorage` directly
   instead of extending the facade — that is AppMek's approach (`mekae2/ae2/stack/ChemicalHandlerFacade.java`).
2. It is in `appeng.me.storage`, i.e. **internal-but-public**. Same risk class as `BasicStorageCell`.

### 5.3 ME Interface: the honest answer (this is a correction to the brief)

**An ME Interface cannot push gas — or anything — into an adjacent block.** It is a passive buffer:
it stocks its own 9 slots *from the network*, and adjacent pipes/machines **pull** from it.

Evidence:

* `InterfaceLogic` has no reference to `PatternProviderTarget` and no push path; its tick only plans
  and fulfils *stocking* (`appeng/helpers/InterfaceLogic.java:296-320`, `:351-400`).
* It exposes its slots as `AECapabilities.GENERIC_INTERNAL_INV` and
  `AECapabilities.ME_STORAGE` (`appeng/init/InitCapabilityProviders.java:122-135`) — both are
  *pull/query* surfaces.
* AE2's own guidebook: *"Interfaces act like a small chest and fluid tank that fills itself from and
  empties to network storage … a fast method of import or export if you have fast item pipes"*
  (`guidebook/items-blocks-machines/interface.md:34-38`).

So requirement **(i) "ME Interface pushes gas into an adjacent gas-capable block"** has three honest
implementations. Pick per use case:

**(i-a) Use a Pattern Provider instead** — it *is* the pushing device ("Pattern providers … push the
ingredients in their patterns to adjacent inventories",
`guidebook/items-blocks-machines/pattern_provider.md:34-36`). With `ExternalStorageStrategy`
registered, a pattern provider pushes gas into an adjacent CCB tank/machine with no further code.
This is the right answer for automation recipes.

**(i-b) Make the adjacent gas pipes able to pull from the Interface.** Register CCB's
`GasCapabilities.BLOCK` on AE2's own **`ae2:interface`** block, backed by an adapter over that block's
`AECapabilities.GENERIC_INTERNAL_INV`. AppMek does the same thing for Mekanism chemicals
(`mekae2/AppliedMekanistics.java:92-99` + `mekae2/ae2/GenericStackChemicalStorage.java`), but for CCB a
plain `GasHandler` is **not enough** — CCB's solver drops any endpoint that is not a `GasStorageHandler`
whose `getPressureCompartment(tank).getVolume() > 0` (`GasEndpointDiscovery#discoverStorageAccesses`),
so the adapter must expose one `GasPressureCompartment` per slot, at `PressureModel.FIXED` and CCB's
reference pressure (100 kPa).

> **Implemented.** This is exactly what `integration/AeInterfaceGasCompat.java` does (see
> `docs/INTERFACE_COMPAT.md` for the full rationale, the drain-only contract and the compile evidence).
> Two deliberate deviations from the sketch below: it registers **only** the ME Interface (not "every
> block with a generic inventory"), and it fills the `GasStorageHandler`/pressure-compartment contract
> instead of a bare `GasHandler`.

```java
// RegisterCapabilitiesEvent, EventPriority.LOWEST — after AE2 has registered its own providers
for (var block : BuiltInRegistries.BLOCK) {
    if (event.isBlockRegistered(AECapabilities.GENERIC_INTERNAL_INV, block)) {
        event.registerBlock(GasCapabilities.BLOCK, (level, pos, state, be, side) -> {
            var genericInv = level.getCapability(AECapabilities.GENERIC_INTERNAL_INV, pos, state, be, side);
            return genericInv == null ? null : new GenericInvGasHandler(genericInv);
        }, block);
    }
}
```

`GenericInvGasHandler implements GasStorageHandler` delegates `getTanks()` → `inv.size()`,
`getGasInTank(t)` → gas stack from `inv.getKey(t)`/`inv.getAmount(t)`, `drain` → `inv.extract(...)`, and
`getPressureCompartment(t)` → a fixed-pressure view with a positive volume. **`fill` must return 0**
(drain-only) so a pipe cannot fight AE2's own stocking logic; the real implementation is
`AeInterfaceGasCompat.InterfaceGasHandler`. With this in place, a CCB gas pipe adjacent to an AE
Interface *can* pull gas out — which is what players will expect.

**Do not register this on `ae2:pattern_provider`.** Its `GENERIC_INTERNAL_INV` is
`PatternProviderReturnInventory`, which overrides `canExtract()` to `false` on purpose
(`appeng/helpers/patternprovider/PatternProviderReturnInventory.java:38-46`: *"Otherwise, a storage bus
on the pattern provider could potentially void items."*). A pipe there would connect and move nothing,
or would need AE2's guard bypassed. Pattern-provider gas flow belongs to the export strategy (i-a).

**(i-c) Ship your own "Gas Interface" block** that does push. Only if (a) and (b) are not enough; it
means writing a `GridHelper.createManagedNode` block entity, a `MEStorage`/`GenericInternalInventory`,
and a ticker. Out of scope for v1.

### 5.4 Pattern Provider — gas as a recipe input/output

Nothing to implement beyond §5.2. The provider pushes pattern outputs through the same
`PatternProviderTarget` chain, so `Transfer`-ing gas works as soon as `ExternalStorageStrategy` is
registered. Two facts worth knowing:

* `pushPattern` pushes **all** inputs, or nothing (`PatternProviderLogic.pushPattern` +
  `adapterAcceptsAll`, `appeng/helpers/patternprovider/PatternProviderLogic.java:481-491`). Your
  facade's `insert(..., Actionable.SIMULATE)` must therefore be honest — do not report partial
  acceptance as success.
* Blocking mode / "contains pattern input" checks go through
  `PatternProviderTarget.containsPatternInput(Set<AEKey>)` → `storage.getAvailableStacks()`
  (`PatternProviderTarget.java:84-92`), so your facade's `getAvailableStacks` is load-bearing for
  blocking-mode correctness.

**Encoding a gas pattern.** The pattern encoder's input/output slots are fully generic
(`ConfigInventory.configStacks(...)`, `appeng/parts/encoding/PatternEncodingLogic.java:54-57`, whose
supported types default to `AEKeyTypes.getAll()`), and a processing pattern's payload is
`List<GenericStack>` (`appeng/crafting/pattern/AEProcessingPattern.java:47,73-77`). So a gas key in
a **processing** pattern should encode and decode like a fluid does. Only *crafting* patterns are
item-only ("Crafting recipes only support real items, not wrapped fluids or similar things",
`PatternEncodingLogic.java:303`).

⚠ **Verify in-game on first run.** If the encoder UI refuses a gas key in the pattern slots, the
fallback is programmatic encoding, which is a public API:

```java
AEProcessingPattern.encode(blankPatternStack, List.of(new GenericStack(gasKey, 1000L)), outputs);
// or, for your own pattern item:
new EncodedPatternItemBuilder<>(decoder).build()   // appeng/api/crafting/EncodedPatternItemBuilder.java
```

### 5.5 Import bus / Export bus — gas between network and adjacent tank

Register **two strategies**. Reuse AE2's generic implementations so you write ~40 lines instead of
~200 (this is AppliedPneumatics' approach, `pneumatics/common/me/AEPlugin.java:150-171`):

```java
// HandlerStrategy<GasHandler, GasStack> — 5 small methods
public final class GasHandlerStrategy extends HandlerStrategy<GasHandler, GasStack> {
    public static final GasHandlerStrategy INSTANCE = new GasHandlerStrategy();
    private GasHandlerStrategy() { super(GasKeyType.INSTANCE); }

    @Override public boolean isSupported(AEKey what) { return what instanceof GasKey; }
    @Override public ExternalStorageFacade getFacade(GasHandler handler) { return new GasStorageFacade(handler); }

    @Nullable @Override
    public GasStack getStack(AEKey what, long amount) {
        return what instanceof GasKey key ? key.toStack(amount) : null;
    }

    @Override public long insert(GasHandler handler, AEKey what, long amount, Actionable mode) {
        if (!(what instanceof GasKey key)) return 0;
        return handler.fill(key.toStack(amount), GasAction.fromFluidAction(mode.getFluidAction()));
    }
}

// registration (FMLCommonSetupEvent -> event.enqueueWork)
StackImportStrategy.register(GasKeyType.INSTANCE, (level, pos, side) ->
        new StorageImportStrategy<>(GasCapabilities.BLOCK, GasHandlerStrategy.INSTANCE, level, pos, side));
StackExportStrategy.register(GasKeyType.INSTANCE, (level, pos, side) ->
        new StorageExportStrategy<>(GasCapabilities.BLOCK, GasHandlerStrategy.INSTANCE, level, pos, side));
```

`HandlerStrategy` (javap-verified, `appeng/parts/automation/HandlerStrategy.java:20-44`) is
`abstract class HandlerStrategy<C, S>` with ctor `(AEKeyType)`, `isSupported(AEKey)`,
`getFacade(C)`, `getStack(AEKey, long)`, `insert(C, AEKey, long, Actionable)`.
`StorageImportStrategy`/`StorageExportStrategy` ctor is
`(BlockCapability<T,Direction>, HandlerStrategy<T,S>, ServerLevel, BlockPos, Direction)` (javap-verified).

Why this works without touching the bus parts:

* `ImportBusPart` sets its supported types to `StackWorldBehaviors.withImportStrategy()`
  (`ImportBusPart.java:45`) — evaluated when the part is constructed (world load, long after mod
  init), so gas appears in the bus config UI automatically.
* The import facade filters by the bus' own key-type checkboxes
  (`keyTypeSelection.enabledPredicate()`, `ImportBusPart.java:62`), and
  `StackTransferContext.isKeyTypeEnabled` (`appeng/parts/automation/StackTransferContextImpl.java:81`)
  gates the transfer.

**Do not** write your own import/export strategy unless you need behaviour the generic ones can't
express. AppMek rolls its own (`mekae2/ae2/stack/MekanismStackImportStrategy.java`) only because
Mekanism's `IChemicalHandler` has no `getTanks()`-shaped facade it wanted. CCB's `GasHandler` does, so
the generic path fits.

### 5.6 Storage bus — gas seen as external storage

Free with §5.2: `StorageBusPart.findExternalStorages` iterates
`StackWorldBehaviors.createExternalStorageStrategies(...)` (`StorageBusPart.java:436-447,550-555`) and
composes whatever wrappers return non-null into a `CompositeStorage` (`:375`).

Remember to honour `extractableOnly` and the change callback, or the bus will show stale contents and
will happily "extract" gas from a tank that refuses drains.

### 5.7 Interface slot capacity + terminal container filling + P2P (all optional but cheap)

```java
// 1. How much gas one ME Interface slot may hold. Without this, getCapacity() falls back to
//    Long.MAX_VALUE (GenericStackInv.java:216-218) — technically works, terrible UI.
//    AE2's own defaults are items = Item.ABSOLUTE_MAX_STACK_SIZE (99) and fluids = 4000 mB
//    (GenericSlotCapacities.java:22-25), i.e. "about 4 buckets per slot". Pick whatever is
//    comparable for gas; AppliedPneumatics chose 64000 mL for air.
GenericSlotCapacities.register(GasKeyType.INSTANCE, 64_000L);

// 2. Let players fill/empty gas canisters by clicking them in a terminal (optional, v1 can skip).
ContainerItemStrategy.register(GasKeyType.INSTANCE, GasKey.class, new GasContainerItemStrategy());

// 3. P2P tunnels that carry gas (optional). AppMek ships a whole P2P part for this.
P2PTunnelAttunement.registerAttunementTag(GAS_P2P_TUNNEL);
```

`ContainerItemStrategy<T extends AEKey, C>` (javap): `getContainedStack`, `findCarriedContext`,
`findPlayerSlotContext` (default), `extract`, `insert`, `playFillSound`, `playEmptySound`,
`getExtractableContent`. CCB ships `GasCanisterContainer` (javap: `drain`/`fill`/`getGasInTank`/
`getTanks`/`getContainer`) which maps onto it — implement it later, not for v1.

`GenericSlotCapacities.register` is important for the *ME Interface* specifically: `InterfaceLogic`
calls `getConfig().useRegisteredCapacities()` / `getStorage().useRegisteredCapacities()` in its
constructor (`appeng/helpers/InterfaceLogic.java:123-124`), which copies
`GenericSlotCapacities.getMap()` into per-instance capacities
(`appeng/helpers/externalstorage/GenericStackInv.java:234-237`).

### 5.8 Do we need any of `IPatternContainer` / `IMECraftingProvider` / `IStorageProvider`?

* `IStorageProvider` (`javap`: `mountInventories(IStorageMounts)`) — only if **we** own a grid node
  whose block entity must mount cells into the network (a custom drive/chest). Not needed: gas cells
  go into AE2's own Drive/ME Chest, which already do this.
* `ICraftingProvider` (present) / `IMECraftingProvider` (**absent** in 19.2.20) — only for machines
  that *provide* patterns (molecular assembler style). Not needed; the pattern provider pushes for us.
* `IPatternContainer` (**absent**), `IStackInterceptor` (**absent**), `StorageAdapter` (**absent**) —
  verified against the jar; these names are from older AE2 versions and must not appear in our code.

---

## 6. Layer 5 — transfer semantics (how a key actually moves)

Signatures (all javap-verified):

```java
public interface StackImportStrategy {                       // appeng/api/behaviors
    boolean transfer(StackTransferContext context);          // note: return value is IGNORED by AE2
    static void register(AEKeyType type, Factory factory);
}
public interface StackExportStrategy {
    long transfer(StackTransferContext context, AEKey what, long maxAmount);
    long push(AEKey what, long maxAmount, Actionable mode);  // crafting-card path
    static void register(AEKeyType type, Factory factory);
}
public interface StackTransferContext {
    IStorageService getInternalStorage();   IEnergySource getEnergySource();
    IActionSource   getActionSource();      int  getOperationsRemaining();
    void setOperationsRemaining(int);       boolean hasOperationsLeft();
    boolean hasDoneWork();                  boolean isKeyTypeEnabled(AEKeyType);
    boolean isInFilter(AEKey);              IPartitionList getFilter();
    void setInverted(boolean);              boolean isInverted();
    boolean canInsert(AEItemKey what, long amount);
    void reduceOperationsRemaining(long inserted);
}
```

Rules that matter:

* **`Actionable` is the simulate/commit switch.** `Actionable.MODULATE` commits;
  `Actionable.SIMULATE` must not. `Actionable#getFluidAction()` maps to
  `IFluidHandler.FluidAction`, and `Actionable.of(FluidAction)` maps back (javap) — which is why
  `GasAction.fromFluidAction(mode.getFluidAction())` is the idiomatic bridge.
* **Always simulate, then commit.** The stock strategies do
  `extract(SIMULATE)` → `insert(SIMULATE)` → `extract(MODULATE)` → `insert(MODULATE)` → push back any
  overflow (`appeng/parts/automation/StorageExportStrategy.java:46-80`). Copy that shape; never
  commit first and hope.
* **`reduceOperationsRemaining` is how you spend the bus' per-tick budget.** Units are *operations*,
  not resource units: `Math.max(1, inserted / keyType.getAmountPerOperation())`
  (`StorageImportStrategy.java:83-84`, `ExportBusPart.java:166`).
* **Insert-only / extract-only sides** are expressed by what the strategy implements, not by a flag:
  * storage bus / interface "extractable only" → `createWrapper(extractableOnly, ...)`.
  * import bus = `StackImportStrategy` only; export bus = `StackExportStrategy` only. A tank that
    should only ever be filled gets no import strategy → the import bus ignores it entirely.
  * per-bus filtering of *which gas* is `StackTransferContext.isInFilter`/`isInverted` (set from the
    bus' config + inverter card, `appeng/parts/automation/StackTransferContextImpl.java:86-101`).
  * per-bus filtering of *which key types* is `isKeyTypeEnabled` (the bus' key-type checkboxes,
    `appeng/parts/automation/ImportBusPart.java:47-53`).
* `StackImportStrategy.transfer` returning `boolean` is a historical artefact: AE2 ignores it and uses
  `context.hasDoneWork()` instead (`IOBusPart.java:236-240`). Both reference mods return `false`.

---

## 7. Recommended implementation skeleton (file-by-file)

Package root: `com.appliedcraftedenergistics`. Mod id `appliedcraftedenergistics`.
Everything in **Path A** (AE2-handled cell) unless marked optional.

### `com.appliedcraftedenergistics`

| File | Extends / implements | Responsibility |
|---|---|---|
| `AppliedCraftedEnergistics.java` | `@Mod(AppliedCraftedEnergistics.MODID)` | mod ctor: `DeferredRegister`s, `RegisterEvent` for `AEKeyType.REGISTRY_KEY`, `FMLCommonSetupEvent` (all `AEPlugin.register()` type hooks), `RegisterCapabilitiesEvent` for the interface bridge, client init behind `Dist.CLIENT`. `static ResourceLocation id(String)`. |
| `AppliedCraftedEnergisticsClient.java` | — | client-only init: `AEKeyRenderHandler`, item colours, cell models, screen. |

### `com.appliedcraftedenergistics.me` — key layer (§2)

| File | Extends / implements | Responsibility |
|---|---|---|
| `GasKeyType.java` | `extends AEKeyType` | `INSTANCE`; `codec()`, `readFromPacket`, `getAmountPerByte/Operation/Unit`, `getUnitSymbol`, `getTagNames`. |
| `GasKey.java` | `extends AEKey` | one instance per `Holder<Gas>`; `MAP_CODEC`, `toTag`, `writeToPacket`, `fromPacket`, `of(GasStack)`, `toStack(long)`, `equals`/`hashCode` on the registry key. |
| `AEPlugin.java` | — (static only) | the single place all AE2 registries are touched: `init()` (nothing left if keytype is registered via `RegisterEvent`) and `register()` for `StackImportStrategy.register`, `StackExportStrategy.register`, `ExternalStorageStrategy.register`, `GenericSlotCapacities.register`, `Upgrades.add`. Mirrors `pneumatics/common/me/AEPlugin.java`. |

### `com.appliedcraftedenergistics.me.strategy` — device layer (§5)

| File | Extends / implements | Responsibility |
|---|---|---|
| `GasExternalStorageStrategy.java` | `implements ExternalStorageStrategy` | `BlockCapabilityCache<GasHandler, Direction>` over `GasCapabilities.BLOCK`; returns a facade. |
| `GasStorageFacade.java` | `extends appeng.me.storage.ExternalStorageFacade` | `GasHandler` ⇄ `MEStorage`: slots = tanks, insert = `fill`, extract = `drain`, `containsAnyFuzzy`, `getAvailableStacks`; honours `extractableOnly` + change listener. |
| `GasHandlerStrategy.java` | `extends appeng.parts.automation.HandlerStrategy<GasHandler, GasStack>` | glue for AE2's generic import/export strategies. |
| `GasStackImportStrategy.java` *or* reuse | `StorageImportStrategy<GasHandler, GasStack>` | **prefer reuse** (one lambda in `AEPlugin`). Write your own only if you need long-amount fidelity. |
| `AeInterfaceGasCompat.java` | `GasStorageHandler` + `GasPressureCompartment` per slot | **(i-b)** registers `GasCapabilities.BLOCK` on `ae2:interface`, backed by `AECapabilities.GENERIC_INTERNAL_INV`; drain-only, fixed 1 atm. Implemented — see `docs/INTERFACE_COMPAT.md`. |
| `GasContainerItemStrategy.java` *(optional)* | `implements ContainerItemStrategy<GasKey, C>` | terminal fill/empty with CCB canisters. Skip for v1. |

### `com.appliedcraftedenergistics.items` — cell layer (§3)

| File | Extends / implements | Responsibility |
|---|---|---|
| `GasStorageCell.java` | `extends appeng.items.storage.BasicStorageCell` | ctor `(props, tier, bytesPerType, totalTypes)`; `isBlackListed` guards non-gas keys. |
| `GasCells.java` | — | `DeferredRegister.Items` + `Tier` enum (`1k…256k`) → `get(tier)`; per-tier `StorageTier`. |
| `GasCellHousingItem.java` | `extends appeng.items.materials.MaterialItem` | the shared housing item (crafting component). |
| `PortableGasCellItem.java` *(optional)* | `extends appeng.items.tools.powered.PortableCellItem` | portable variant; `getRecipeId()`. |

### `com.appliedcraftedenergistics.client` — client layer (§4)

| File | Extends / implements | Responsibility |
|---|---|---|
| `GasKeyRenderer.java` | `implements AEKeyRenderHandler<GasKey>` | **mandatory**; `drawInGui` (blit `Gas.getTexture()` tinted by `Gas.getTint`), `drawOnBlockFace` (may be a no-op like AirKey's), `getDisplayName`, `getTooltip`. |
| `AEClientPlugin.java` | — | `AEKeyRendering.register`, `StorageCellModels.registerModel` loop, item colour handler, screen registration. |

### `com.appliedcraftedenergistics.menu` *(optional)*

| File | Responsibility |
|---|---|
| `GasMenus.java` | `MenuType<MEStorageMenu>` for the portable cell + `InitScreens` registration. |

### Resources

```
assets/appliedcraftedenergistics/lang/en_us.json                      key type name, item names, unit
assets/appliedcraftedenergistics/models/item/gas_cell_housing.json
assets/appliedcraftedenergistics/models/item/gas_storage_cell_1k.json   (+ 4k/16k/64k/256k)
assets/appliedcraftedenergistics/models/block/drive/cells/gas_storage_cell_1k.json (+ tiers)
assets/appliedcraftedenergistics/textures/item/gas_cell_housing.png
assets/appliedcraftedenergistics/textures/item/gas_storage_cell_1k.png  (+ tiers)
assets/appliedcraftedenergistics/textures/block/drive/gas_cells.png     drive-cell sheet
assets/ae2/screens/terminals/portable_gas_cell.json               only if portable cell (ae2 namespace!)
data/appliedcraftedenergistics/recipe/*.json                            cells, housing
data/appliedcraftedenergistics/recipe/*_cell_disassembly.json           if you want alt-click disassembly
```

### `neoforge.mods.toml` dependency

```toml
[[dependencies.appliedcraftedenergistics]]
modId = "ae2"
type = "required"
versionRange = "[19.2.20,20.0.0)"
ordering = "AFTER"

[[dependencies.appliedcraftedenergistics]]
modId = "createcraftedbeginning"
type = "required"
versionRange = "[0.1.1,)"
ordering = "AFTER"
```

---

## 8. build.gradle — the AE2 API artifacts

```gradle
repositories {
    maven { name = "Modmaven"; url = uri("https://modmaven.dev/") }   // AE2 releases live here
    mavenLocal()
    maven { url = uri("https://cursemaven.com"); content { includeGroup("curse.maven") } }
}

dependencies {
    // AE2 — the API surface we compile against. Same coordinate the two reference addons use
    // (mekae2/build.gradle: `implementation("org.appliedenergistics:appliedenergistics2:${ae2_version}")`).
    implementation("org.appliedenergistics:appliedenergistics2:19.2.20")

    // Create + Create: Crafted Beginning — we only need their API at compile time, but they must be
    // present at runtime for dev launches. Local jars are fine (they are already in libs/).
    compileOnly files("libs/create-1.21.1-6.0.10.jar")
    compileOnly files("libs/createcraftedbeginning-0.1.1.jar")
    // Add `runtimeOnly` (or `localRuntime`) for the same two jars so `runClient` starts.
}
```

Notes:

* `libs/appliedenergistics2-19.2.20.jar` can be used with `compileOnly files(...)` as a fallback, but
  the Maven coordinate is strongly preferred: AE2's `@Mod` and its bundled `guideme` dependency must be
  on the runtime classpath for `runClient`, and the coordinate brings its POM dependencies.
* The CCB gas API is `net.ty.createcraftedbeginning.api.gas.*`; nothing else from CCB is needed for v1.
* If you later add JEI/EMI/renderer integration, `EmiStackConverter`/`IngredientConverter` are the API
  hooks (`appeng/api/integrations/emi/EmiStackConverters.java`,
  `appeng/api/integrations/rei/IngredientConverters.java`).

---

## 9. Pitfalls

### 9.1 Version differences 19.2.10 → 19.2.17 → 19.2.20

Verified by `git diff` in the AE2 repo (see evidence file):

| Range | `appeng.api` changes | Addon-relevant internal changes |
|---|---|---|
| 19.2.10 → 19.2.20 | **2 files / 6 lines**: `AEFluidKey.hasComponents()` and `toString()` now use `isComponentsPatchEmpty()`; `AEItemKey.getFuzzySearchMaxValue()` caches `maxDamage`. **No signature change.** | `BasicCellInventory.extract` now early-returns for foreign key types; `ExternalStorageFacade` item-extraction loop bugfix; `InterfaceLogic` +1 line; `PatternProviderLogic` +2/-1; `AnnihilationPlanePartItem` **deleted**; `StackTransferContextImpl` 1 line. |
| 19.2.17 → 19.2.20 | identical two files | `PatternEncodingTermMenu` blank-pattern auto-refill; `BasicCellInventory` early return; `ExternalStorageFacade` fix; compass/condenser/testplot work. |

**Practical conclusion: AppMek (19.2.10) and AppliedPneumatics (19.2.17) code compiles unchanged
against 19.2.20 for every class this mod uses.** The one thing to copy from 19.2.20 rather than from
AppMek is `hasComponents()` — a key returning `true` when it has no components will confuse
`AEKey`-based deduplication.

### 9.2 Classes that are absent / present-only-in-dev

* **Absent** (do not try to use, they are from the 1.18-era API doc that still sits in `API.md`):
  `IAEKey`, `IAEStack`, `IStorageChannel`, `StorageChannels`, `ICellInventory`, `CellHandler`,
  `CellHandler.Impl`, `CellHandler.Stats`, `ItemVariantHolder`, `IExternalStorageMonitor`,
  `IUpgradeModule`, `IMEInventory`, `IStorageMonitorable`. Verified: zero matches in the jar listing.
* **Present in jar but not API**: see §1 third bullet. `BasicCellInventory` is `appeng.me.cells`, its
  constructor is **private** — only `createInventory(ItemStack, ISaveProvider)` is callable.
* **`@ApiStatus.Internal`**: `AEKeyTypesInternal` — never call `setRegistry`/`register` on it; go
  through `AEKeyTypes`/`RegisterEvent`. `StackWorldBehaviors` is not annotated but is *not* in
  `appeng.api`; use the `register(...)` statics on `StackImportStrategy`, `StackExportStrategy`,
  `ExternalStorageStrategy` instead (`StackWorldBehaviors.registerX` also works and is what AppMek
  does, but it is the more internal of the two).
* **Dev-only / not shipped**: AE2's `server/testplots/*`, `AESiteExporter`, `guideExportOnly`
  dependencies. Nothing in `appeng.api` is dev-only.

### 9.3 Signature mismatch found between source and jar

**None for anything we use.** The checkout is `v19.2.20 + 2 commits` and those commits touch only
`ChargerBlockEntity` and `MatterCannonItem`. Spot-checked every class we recommend with `javap`
(see evidence) — all constructors and methods match the source tree exactly.

The *real* mismatch is between the **brief's assumed API (older AE2)** and 19.2.20 — see §1.

### 9.4 Ordering / timing traps

| Hook | Must run in | Why |
|---|---|---|
| `Registry.register` of `AEKeyType` | `RegisterEvent` for `AEKeyType.REGISTRY_KEY` | registry frozen afterwards |
| `StorageCells.addCellHandler` | `FMLCommonSetupEvent` (`enqueueWork`) | `StorageCells.java:55`; AE2's own handlers are added in `postRegistrationInitialization` |
| `StackImportStrategy/StackExportStrategy/ExternalStorageStrategy.register`, `GenericSlotCapacities.register`, `ContainerItemStrategy.register`, `Upgrades.add` | `FMLCommonSetupEvent` (`enqueueWork`) | matches `pneumatics/common/me/AEPlugin.java:45-62`; some of them snapshot `AEKeyTypes.getAll()` at call time (`KeyTypeSelection.java:32`) |
| `StorageCellModels.registerModel` | `FMLCommonSetupEvent` — **before model baking** | `DriveModel.getDependencies()` reads the map at bake time |
| `AEKeyRendering.register` | `FMLClientSetupEvent` (`enqueueWork`), client only | client-only classes in the signature |
| Gas capability registration on AE2's blocks | `RegisterCapabilitiesEvent`, `EventPriority.LOWEST` | must be after AE2 registered `GENERIC_INTERNAL_INV` |

### 9.5 Functional traps

1. **Missing `AEKeyRenderHandler` = client crash** in every terminal that shows the key
   (`AEKeyRendering.getOrThrow` throws). Not a "just shows a missing texture" failure.
2. **`BasicStorageCell`'s 3rd ctor arg is KILOBYTES**, not bytes
   (`BasicStorageCell.java:68`: `this.totalBytes = kilobytes * 1024`). Passing `tier.bytes()` gives a
   1024× too large cell. AppMek passes `tier.bytes() / 1024`.
3. **`IBasicCellItem.getBytes()` must be a multiple of 8** (`IBasicCellItem.java:55`).
4. **`getAmountPerByte()` divides in `getUsedBytes()`** — returning `0` or negative divides by zero.
5. **`ExternalStorageFacade` is `int`-based**; amounts are saturated. Use `MEStorage` directly if GU
   amounts can exceed `2^31`.
6. **`GasStack` is mutable** (`setAmount`, `shrink`, `grow`). Never store one in a `GasKey`, and never
   hand the same instance to two calls. `copyWithAmount` / `new GasStack(holder, n)` instead.
7. **`GenericStack` is a record** — `new GenericStack(key, 0)` is *not* null and inverters/filters may
   treat it as a real entry. Use `null` for "empty".
8. **`insert` must never over-report.** Void card semantics live in `BasicCellInventory`
   (`:337-361`); a custom `StorageCell` that returns `amount` instead of `inserted` silently deletes
   gas when no void card is installed.
9. **`persist()` is not called for you on a custom cell** unless a host exists; `saveChanges()` must
   either call `ISaveProvider.saveChanges()` or persist immediately
   (`AirCellInventory.java:200-215`).
10. **`Actionable.SIMULATE` must not mutate.** Every AE2 device does simulate-then-commit; a facade
    that mutates on simulate will duplicate or destroy gas.
11. **Registering a capability on another mod's block** (§5.3 i-b) is supported
    (`RegisterCapabilitiesEvent.registerBlock(capability, provider, block)`), but do it at
    `EventPriority.LOWEST` and guard with `event.isBlockRegistered(AECapabilities.GENERIC_INTERNAL_INV, block)`
    so you don't shadow other addons.
12. **`getRawId()` must be ≤ 127**; the registry has `.maxId(127)` and `getRawId()` throws beyond it.

---

## 10. Acceptance checklist

Compile-time / static:

* [ ] `AEKeyType` registered inside the `RegisterEvent` for `AEKeyType.REGISTRY_KEY`; `getRawId()` == 2.
* [ ] `GasKey` overrides all 9 abstract members; `equals`/`hashCode` on the registry key.
* [ ] `AEKeyRendering.register(GasKeyType.INSTANCE, GasKey.class, renderer)` exists and is client-only.
* [ ] `StorageCellModels.registerModel` for every cell tier + JSON file present + texture present.
* [ ] `neoforge.mods.toml` requires `ae2 [19.2.20,20.0.0)` and `createcraftedbeginning [0.1.1,)`.

In-game (dev client):

* [ ] Gas cell crafts, holds ≥ 2 different gases, tooltip shows bytes/types, drive LED changes colour.
* [ ] Gas in a cell appears in a terminal with the correct icon, name and `kGU` amounts; sorting and
      the key-type filter both work.
* [ ] Cell Workbench can partition a gas cell to a specific gas.
* [ ] Void card discards overflow; Equal-Distribution card caps per type; Inverter flips the partition.
* [ ] **Import bus** next to a CCB tank pulls gas into the network; the gas shows in storage.
* [ ] **Export bus** pushes gas from the network into a CCB tank; overflow is returned, nothing is voided.
* [ ] **Storage bus** on a CCB tank shows its gas, respects "Extractable Only", and updates when the
      tank changes.
* [ ] **Pattern Provider** with a gas-consuming processing pattern pushes gas into an adjacent CCB
      machine and the machine runs; blocking mode works after the first batch (proves
      `containsPatternInput` → `getAvailableStacks` is right).
* [ ] **Pattern Provider with a gas-producing pattern** accepts the machine's gas output back.
* [ ] **ME Interface** next to a CCB gas pipe: the pipe can pull gas out of the interface, and the
      interface re-stocks itself from the network (i-b). Implemented in
      `integration/AeInterfaceGasCompat.java`; procedure in `docs/INTERFACE_COMPAT.md` §6.
* [ ] Removing the mod from a world leaves gas cells as AE2 `MISSING_CONTENT` items **with their data
      intact**; re-adding the mod restores them.
