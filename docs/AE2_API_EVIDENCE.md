# AE2 19.2.20 — Evidence Log for `AE2_API.md`

Raw material behind every claim in `AE2_API.md`. Nothing here is inferred from memory: each entry is
either a command output or a `path:line` citation that can be re-run.

Environment

```
java   : C:\Program Files\Java\jdk-21\bin\javap.exe   (21.0.5)
         C:\Program Files\Java\jdk-21\bin\jar.exe     (21.0.5)
jar    : <repo>\libs\appliedenergistics2-19.2.20.jar
         (8,242,323 bytes, 5203 entries)
CCB jar: <repo>\libs\createcraftedbeginning-0.1.1.jar
         (8,613,991 bytes, 5307 entries)
AE2 src: <workspace>\Applied-Energistics-2   @ neoforge/v19.2.20 + 2 commits
```

Reproduce any `javap` excerpt with:

```powershell
$jdk = "C:\Program Files\Java\jdk-21\bin"
$cp  = "<repo>\libs\appliedenergistics2-19.2.20.jar"
& "$jdk\javap.exe" -p -classpath $cp appeng.api.stacks.AEKeyType
```

---

## E1. Source-tree version and its divergence from the shipped jar

```
$ git describe --tags
neoforge/v19.2.20-2-g67d2a9b08

$ git log --oneline neoforge/v19.2.20..HEAD
67d2a9b08 Only update charger clients when it actually drew power (#9004) (#9105)
f466f2a92 Backport: Fix matter cannon particle direction and trail length (#9104)

$ git diff --stat neoforge/v19.2.20..HEAD
 src/main/java/appeng/blockentity/misc/ChargerBlockEntity.java  | 6 ++++--
 src/main/java/appeng/items/tools/powered/MatterCannonItem.java | 6 +++---
 2 files changed, 7 insertions(+), 5 deletions(-)
```

**Conclusion:** the two commits on top of the 19.2.20 tag do not touch `appeng.api`,
`appeng.parts.automation`, `appeng.items.storage` or `appeng.me.*`. Source tree == jar for our purposes.

`gradle.properties` in the AE2 checkout contains no `mod_version`; the version lives in the git tag
(`neoforge/v19.2.20`). The target jar is `appliedenergistics2-19.2.20.jar` — **same version, no
difference to note**.

---

## E2. Class-absence verification (the brief's assumed API vs. 19.2.20)

```
$ jar tf appliedenergistics2-19.2.20.jar | Select-String \
    "IUpgradeModule|ICellInventory|CellHandler|IAEStack|IStorageChannel|StorageChannels|\
     IExternalStorageMonitor|ItemVariantHolder|BasicCellInventory|CellHandlerRegistry"
appeng/me/cells/CreativeCellHandler.class
appeng/me/cells/BasicCellHandler.class
appeng/me/cells/BasicCellInventory.class
appeng/api/storage/cells/ICellHandler.class
```

Only four matches, and none of them is the assumed class:

* `BasicCellHandler` / `CreativeCellHandler` are AE2's *implementations* of `ICellHandler`.
* `BasicCellInventory` is internal (`appeng.me.cells`), private constructor (§E5).
* There is **no** `CellHandler`, `CellHandler.Impl`, `CellHandler.Stats`, `CellHandlerRegistry`,
  `ICellInventory`, `IAEStack`, `IStorageChannel`, `StorageChannels`, `IExternalStorageMonitor`,
  `ItemVariantHolder`, `IUpgradeModule`.

Full key-package inventory (filtered to top-level classes):

```
$ jar tf appliedenergistics2-19.2.20.jar | Select-String \
    "^appeng/(api/stacks|api/storage|api/behaviors|parts/automation|items/storage|me/cells|helpers/patternprovider)/[^/]+\.class$"

appeng/api/storage/{MEStorage,StorageCells,StorageHelper,IStorageProvider,IStorageMounts,AEKeyFilter,
                    AEKeySlotFilter,NoOpKeyFilter,ITerminalHost,ISubMenuHost,LinkStatus,ILinkStatus,
                    SupplierStorage,IPatternAccessTermMenuHost}.class
appeng/api/storage/cells/{CellState,IBasicCellItem,ICellHandler,ICellWorkbenchItem,ISaveProvider,StorageCell}.class
appeng/api/stacks/{AEKey,AEKeyType,AEKeyTypes,AEKeyTypesInternal,AEItemKey,AEItemKeys,AEFluidKey,
                   AEFluidKeys,GenericStack,KeyCounter,AmountFormat,FuzzySearch,VariantCounter,
                   AEKey2LongMap,GenericStackListCodec}.class
appeng/api/behaviors/{StackImportStrategy,StackImportStrategy$Factory,StackExportStrategy,
                      StackExportStrategy$Factory,ExternalStorageStrategy,ExternalStorageStrategy$Factory,
                      StackTransferContext,GenericInternalInventory,GenericSlotCapacities,
                      ContainerItemStrategy,ContainerItemStrategies,ContainerItemContext,
                      PlacementStrategy,PickupStrategy,EmptyingAction,NoopPlacementStrategy,
                      FluidContainerItemStrategy,FluidContainerItemStrategy$*,PickupSink}.class
appeng/api/client/{AEKeyRenderHandler,AEKeyRendering,StorageCellModels}.class
appeng/parts/automation/{HandlerStrategy,StorageImportStrategy,StorageExportStrategy,StackWorldBehaviors,
                         StackImportFacade,StackExportFacade,IOBusPart,ImportBusPart,ExportBusPart,
                         PlacementStrategyFacade,FluidPlacementStrategy,ItemPlacementStrategy,
                         ItemPickupStrategy,FluidPickupStrategy,...}.class
appeng/items/storage/{BasicStorageCell,StorageTier,CreativeCellItem,SpatialStorageCellItem,
                      ViewCellItem,StorageCellTooltipComponent,SpatialPlotInfo}.class
appeng/items/tools/powered/{AbstractPortableCell,PortableCellItem}.class
appeng/me/cells/{BasicCellHandler,BasicCellInventory,CreativeCellHandler,CreativeCellInventory}.class
appeng/helpers/patternprovider/{PatternProviderTarget,PatternProviderTargetCache,PatternProviderLogic,
                                PatternProviderLogicHost,PatternContainer,PatternProviderReturnInventory}.class
appeng/me/storage/{ExternalStorageFacade,CompositeStorage,ExternalInventoryCache}.class
```

(AE2 also ships `appeng/api/stacks/AEKeyType$1.class` — an anonymous class, irrelevant.)

---

## E3. `javap` — key layer

### `appeng.api.stacks.AEKeyType`

```
public abstract class appeng.api.stacks.AEKeyType {
  public static final net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<appeng.api.stacks.AEKeyType>> REGISTRY_KEY;
  public static final com.mojang.serialization.Codec<appeng.api.stacks.AEKeyType> CODEC;
  public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, appeng.api.stacks.AEKeyType> STREAM_CODEC;
  private final net.minecraft.resources.ResourceLocation id;
  private final java.lang.Class<? extends appeng.api.stacks.AEKey> keyClass;
  private final appeng.api.storage.AEKeyFilter filter;
  private final net.minecraft.network.chat.Component description;
  public appeng.api.stacks.AEKeyType(net.minecraft.resources.ResourceLocation, java.lang.Class<? extends appeng.api.stacks.AEKey>, net.minecraft.network.chat.Component);
  public abstract com.mojang.serialization.MapCodec<? extends appeng.api.stacks.AEKey> codec();
  public static appeng.api.stacks.AEKeyType items();
  public static appeng.api.stacks.AEKeyType fromRawId(int);
  public static appeng.api.stacks.AEKeyType fluids();
  public final net.minecraft.resources.ResourceLocation getId();
  public final java.lang.Class<? extends appeng.api.stacks.AEKey> getKeyClass();
  public final byte getRawId();
  public int getAmountPerOperation();
  public int getAmountPerByte();
  public abstract appeng.api.stacks.AEKey readFromPacket(net.minecraft.network.RegistryFriendlyByteBuf);
  public appeng.api.stacks.AEKey loadKeyFromTag(net.minecraft.core.HolderLookup$Provider, net.minecraft.nbt.CompoundTag);
  public final appeng.api.stacks.AEKey tryCast(appeng.api.stacks.AEKey);
  public final boolean contains(appeng.api.stacks.AEKey);
  public boolean supportsFuzzyRangeSearch();
  public final appeng.api.storage.AEKeyFilter filter();
  public net.minecraft.network.chat.Component getDescription();
  public java.lang.String getUnitSymbol();
  public int getAmountPerUnit();
  public final java.lang.String formatAmount(long, appeng.api.stacks.AmountFormat);
  public java.util.stream.Stream<net.minecraft.tags.TagKey<?>> getTagNames();
  static {};
}
```

### `appeng.api.stacks.AEKey` (abstract members marked)

```
public abstract class appeng.api.stacks.AEKey {
  public static final java.lang.String TYPE_FIELD;
  public static final com.mojang.serialization.MapCodec<appeng.api.stacks.AEKey> MAP_CODEC;
  public static final com.mojang.serialization.Codec<appeng.api.stacks.AEKey> CODEC;
  public static final net.minecraft.network.codec.StreamCodec<...RegistryFriendlyByteBuf, appeng.api.stacks.AEKey> STREAM_CODEC;
  public static final net.minecraft.network.codec.StreamCodec<...RegistryFriendlyByteBuf, appeng.api.stacks.AEKey> OPTIONAL_STREAM_CODEC;
  public static void writeOptionalKey(...); public static void writeKey(...);
  public static appeng.api.stacks.AEKey readOptionalKey(...); public static appeng.api.stacks.AEKey readKey(...);
  public static appeng.api.stacks.AEKey fromTagGeneric(net.minecraft.core.HolderLookup$Provider, net.minecraft.nbt.CompoundTag);
  public final net.minecraft.nbt.CompoundTag toTagGeneric(net.minecraft.core.HolderLookup$Provider);
  public final int getAmountPerUnit();  public final java.lang.String getUnitSymbol();
  public final int getAmountPerOperation();  public final int getAmountPerByte();
  public java.lang.String formatAmount(long, appeng.api.stacks.AmountFormat);
  public abstract appeng.api.stacks.AEKeyType getType();                    // ABSTRACT
  public abstract appeng.api.stacks.AEKey dropSecondary();                   // ABSTRACT
  public abstract net.minecraft.nbt.CompoundTag toTag(net.minecraft.core.HolderLookup$Provider);  // ABSTRACT
  public abstract java.lang.Object getPrimaryKey();                          // ABSTRACT
  public int getFuzzySearchValue();  public int getFuzzySearchMaxValue();
  public final boolean fuzzyEquals(appeng.api.stacks.AEKey, appeng.api.config.FuzzyMode);
  public final boolean matches(appeng.api.stacks.GenericStack);
  public java.lang.String getModId();
  public abstract net.minecraft.resources.ResourceLocation getId();          // ABSTRACT
  public abstract void writeToPacket(net.minecraft.network.RegistryFriendlyByteBuf);  // ABSTRACT
  public net.minecraft.world.item.ItemStack wrapForDisplayOrFilter();
  public final boolean supportsFuzzyRangeSearch();
  public final net.minecraft.network.chat.Component getDisplayName();
  protected abstract net.minecraft.network.chat.Component computeDisplayName();   // ABSTRACT
  public abstract void addDrops(long, java.util.List<net.minecraft.world.item.ItemStack>,
                                net.minecraft.world.level.Level, net.minecraft.core.BlockPos);  // ABSTRACT
  public boolean isTagged(net.minecraft.tags.TagKey<?>);
  public <T> T get(net.minecraft.core.component.DataComponentType<T>);
  public abstract boolean hasComponents();                                   // ABSTRACT
}
```

### `appeng.api.stacks.AEKeyTypes` / `AEKeyTypesInternal`

```
public final class appeng.api.stacks.AEKeyTypes {
  public static synchronized void register(appeng.api.stacks.AEKeyType);
  public static appeng.api.stacks.AEKeyType get(net.minecraft.resources.ResourceLocation);
  public static java.util.Set<appeng.api.stacks.AEKeyType> getAll();
}

public final class appeng.api.stacks.AEKeyTypesInternal {          // @ApiStatus.Internal
  public static net.minecraft.core.Registry<appeng.api.stacks.AEKeyType> getRegistry();
  public static void setRegistry(net.minecraft.core.Registry<appeng.api.stacks.AEKeyType>);
  public static java.util.Set<appeng.api.stacks.AEKeyType> getAllTypes();
  public static void register(appeng.api.stacks.AEKeyType);
}
```

`AEKeyTypesInternal.register` body (source `appeng/api/stacks/AEKeyTypesInternal.java:50-52`) is
`Registry.register(getRegistry(), keyType.getId(), keyType)` — i.e. `AEKeyTypes.register(...)` is a
thin wrapper over a direct registry write, which is why it only works during the registry phase.

### `appeng.api.stacks.GenericStack`

```
public final class appeng.api.stacks.GenericStack extends java.lang.Record {
  public static final java.lang.String AMOUNT_FIELD;
  public static final com.mojang.serialization.Codec<appeng.api.stacks.GenericStack> CODEC;
  public static final net.minecraft.network.codec.StreamCodec<...RegistryFriendlyByteBuf, appeng.api.stacks.GenericStack> STREAM_CODEC;
  public static final com.mojang.serialization.Codec<java.util.List<appeng.api.stacks.GenericStack>> FAULT_TOLERANT_NULLABLE_LIST_CODEC;
  public static final com.mojang.serialization.Codec<java.util.List<appeng.api.stacks.GenericStack>> FAULT_TOLERANT_LIST_CODEC;
  public appeng.api.stacks.GenericStack(appeng.api.stacks.AEKey, long);
  public static appeng.api.stacks.GenericStack readTag(net.minecraft.core.HolderLookup$Provider, net.minecraft.nbt.CompoundTag);
  public static net.minecraft.nbt.CompoundTag writeTag(net.minecraft.core.HolderLookup$Provider, appeng.api.stacks.GenericStack);
  public static appeng.api.stacks.GenericStack fromItemStack(net.minecraft.world.item.ItemStack);
  public static appeng.api.stacks.GenericStack fromFluidStack(net.neoforged.neoforge.fluids.FluidStack);
  public static net.minecraft.world.item.ItemStack wrapInItemStack(appeng.api.stacks.AEKey, long);
  public static net.minecraft.world.item.ItemStack wrapInItemStack(appeng.api.stacks.GenericStack);
  public static boolean isWrapped(net.minecraft.world.item.ItemStack);
  public static appeng.api.stacks.GenericStack unwrapItemStack(net.minecraft.world.item.ItemStack);
  public static appeng.api.stacks.GenericStack sum(appeng.api.stacks.GenericStack, appeng.api.stacks.GenericStack);
  public appeng.api.stacks.AEKey what();
  public long amount();
}
```

---

## E4. `javap` — cell layer

```
public interface appeng.api.storage.cells.IBasicCellItem extends appeng.api.storage.cells.ICellWorkbenchItem {
  public abstract appeng.api.stacks.AEKeyType getKeyType();
  public abstract int getBytes(net.minecraft.world.item.ItemStack);
  public abstract int getBytesPerType(net.minecraft.world.item.ItemStack);
  public abstract int getTotalTypes(net.minecraft.world.item.ItemStack);
  public default boolean isBlackListed(net.minecraft.world.item.ItemStack, appeng.api.stacks.AEKey);
  public default boolean storableInStorageCell();
  public default boolean isStorageCell(net.minecraft.world.item.ItemStack);
  public abstract double getIdleDrain();
  public default void addCellInformationToTooltip(net.minecraft.world.item.ItemStack, java.util.List<net.minecraft.network.chat.Component>);
  public default java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getCellTooltipImage(net.minecraft.world.item.ItemStack);
}

public interface appeng.api.storage.cells.ICellWorkbenchItem extends appeng.api.upgrades.IUpgradeableItem {
  public default boolean isEditable(net.minecraft.world.item.ItemStack);
  public default appeng.util.ConfigInventory getConfigInventory(net.minecraft.world.item.ItemStack);
  public abstract appeng.api.config.FuzzyMode getFuzzyMode(net.minecraft.world.item.ItemStack);
  public abstract void setFuzzyMode(net.minecraft.world.item.ItemStack, appeng.api.config.FuzzyMode);
}

public interface appeng.api.storage.cells.StorageCell extends appeng.api.storage.MEStorage {
  public abstract appeng.api.storage.cells.CellState getStatus();
  public abstract double getIdleDrain();
  public default boolean canFitInsideCell();
  public abstract void persist();
}

public interface appeng.api.storage.cells.ICellHandler {
  public abstract boolean isCell(net.minecraft.world.item.ItemStack);
  public abstract appeng.api.storage.cells.StorageCell getCellInventory(
      net.minecraft.world.item.ItemStack, appeng.api.storage.cells.ISaveProvider);
}

public interface appeng.api.storage.cells.ISaveProvider {
  public abstract void saveChanges();
}

public final class appeng.api.storage.StorageCells {
  public static synchronized void addCellHandler(appeng.api.storage.cells.ICellHandler);
  public static synchronized boolean isCellHandled(net.minecraft.world.item.ItemStack);
  public static synchronized appeng.api.storage.cells.ICellHandler getHandler(net.minecraft.world.item.ItemStack);
  public static synchronized appeng.api.storage.cells.StorageCell getCellInventory(
      net.minecraft.world.item.ItemStack, appeng.api.storage.cells.ISaveProvider);
}

public interface appeng.api.storage.MEStorage {
  public default boolean isPreferredStorageFor(appeng.api.stacks.AEKey, appeng.api.networking.security.IActionSource);
  public default long insert(appeng.api.stacks.AEKey, long, appeng.api.config.Actionable, appeng.api.networking.security.IActionSource);
  public default long extract(appeng.api.stacks.AEKey, long, appeng.api.config.Actionable, appeng.api.networking.security.IActionSource);
  public default void getAvailableStacks(appeng.api.stacks.KeyCounter);
  public abstract net.minecraft.network.chat.Component getDescription();
  public default appeng.api.stacks.KeyCounter getAvailableStacks();
  public static void checkPreconditions(appeng.api.stacks.AEKey, long, appeng.api.config.Actionable, appeng.api.networking.security.IActionSource);
}

public class appeng.items.storage.BasicStorageCell extends appeng.items.AEBaseItem
        implements appeng.api.storage.cells.IBasicCellItem, appeng.hooks.AEToolItem {
  protected final double idleDrain;
  protected final int totalBytes;
  protected final int bytesPerType;
  protected final int totalTypes;
  private final appeng.api.stacks.AEKeyType keyType;
  public appeng.items.storage.BasicStorageCell(net.minecraft.world.item.Item$Properties, double, int, int, int,
                                               appeng.api.stacks.AEKeyType);
  public appeng.api.stacks.AEKeyType getKeyType();
  public int getBytes(net.minecraft.world.item.ItemStack);
  public int getTotalTypes(net.minecraft.world.item.ItemStack);
  public double getIdleDrain();
  public int getBytesPerType(net.minecraft.world.item.ItemStack);
  public appeng.api.upgrades.IUpgradeInventory getUpgrades(net.minecraft.world.item.ItemStack);
  public appeng.util.ConfigInventory getConfigInventory(net.minecraft.world.item.ItemStack);
  public appeng.api.config.FuzzyMode getFuzzyMode(net.minecraft.world.item.ItemStack);
  public void setFuzzyMode(net.minecraft.world.item.ItemStack, appeng.api.config.FuzzyMode);
  public static int getColor(net.minecraft.world.item.ItemStack, int);
}

public final class appeng.items.storage.StorageTier extends java.lang.Record {
  public static final appeng.items.storage.StorageTier SIZE_1K;   // bytes=1024,   idleDrain=0.5
  public static final appeng.items.storage.StorageTier SIZE_4K;   // bytes=4096,   idleDrain=1.0
  public static final appeng.items.storage.StorageTier SIZE_16K;  // bytes=16384,  idleDrain=1.5
  public static final appeng.items.storage.StorageTier SIZE_64K;  // bytes=65536,  idleDrain=2.0
  public static final appeng.items.storage.StorageTier SIZE_256K; // bytes=262144, idleDrain=2.5
  public appeng.items.storage.StorageTier(int, java.lang.String, int, double, java.util.function.Supplier<net.minecraft.world.item.Item>);
  public int index();  public java.lang.String namePrefix();  public int bytes();
  public double idleDrain();  public java.util.function.Supplier<net.minecraft.world.item.Item> componentSupplier();
}

public class appeng.items.tools.powered.PortableCellItem extends appeng.items.tools.powered.AbstractPortableCell
        implements appeng.api.storage.cells.IBasicCellItem {
  public appeng.items.tools.powered.PortableCellItem(appeng.api.stacks.AEKeyType, int,
      net.minecraft.world.inventory.MenuType<?>, appeng.items.storage.StorageTier,
      net.minecraft.world.item.Item$Properties, int);
  public double getChargeRate(...);  public net.minecraft.resources.ResourceLocation getRecipeId();
  ... (IBasicCellItem + ICellWorkbenchItem methods)
}

public interface appeng.api.implementations.items.IAEItemPowerStorage {
  public abstract double injectAEPower(net.minecraft.world.item.ItemStack, double, appeng.api.config.Actionable);
  public abstract double extractAEPower(net.minecraft.world.item.ItemStack, double, appeng.api.config.Actionable);
  public abstract double getAEMaxPower(net.minecraft.world.item.ItemStack);
  public abstract double getAECurrentPower(net.minecraft.world.item.ItemStack);
  public abstract appeng.api.config.AccessRestriction getPowerFlow(net.minecraft.world.item.ItemStack);
  public abstract double getChargeRate(net.minecraft.world.item.ItemStack);
}
```

### Cell byte accounting — source (the formulas are not in the javap)

`appeng/me/cells/BasicCellInventory.java`:

```
:56   private static final int MAX_ITEM_TYPES = 63;
:110  if (upgrades.isInstalled(AEItems.EQUAL_DISTRIBUTION_CARD)) {
:125  this.hasVoidUpgrade = upgrades.isInstalled(AEItems.VOID_CARD);
:129  return i.getOrDefault(AEComponents.STORAGE_CELL_INV, List.of());
:144  public static BasicCellInventory createInventory(ItemStack o, @Nullable ISaveProvider container) {
:187  public void persist() {
:206        i.remove(AEComponents.STORAGE_CELL_INV);
:208        i.set(AEComponents.STORAGE_CELL_INV, stacks);
:269  public boolean canHoldNewItem() {
:296  public long getRemainingItemTypes() {
:302  public long getUsedBytes() {
:307  public long getRemainingItemCount() {
:312  public int getUnusedItemCount() {
:337  public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
:355        if (!isPreformatted() && hasVoidUpgrade && !canHoldNewItem()) {
:359        return hasVoidUpgrade ? amount : inserted;
:406  public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
```

Verbatim formula bodies (`:302-316`):

```java
public long getUsedBytes() {
    var bytesForItemCount = (this.getStoredItemCount() + this.getUnusedItemCount()) / keyType.getAmountPerByte();
    return this.getStoredItemTypes() * this.getBytesPerType() + bytesForItemCount;
}

public long getRemainingItemCount() {
    final long remaining = this.getFreeBytes() * keyType.getAmountPerByte() + this.getUnusedItemCount();
    return remaining > 0 ? remaining : 0;
}

public int getUnusedItemCount() {
    final int div = (int) (this.getStoredItemCount() % keyType.getAmountPerByte());
    if (div == 0) return 0;
    return keyType.getAmountPerByte() - div;
}
```

### The `IBasicCellItem` fast path is registration-free — source proof

`appeng/me/cells/BasicCellHandler.java:37-48`:

```java
public class BasicCellHandler implements ICellHandler {
    public static final BasicCellHandler INSTANCE = new BasicCellHandler();

    @Override
    public boolean isCell(ItemStack is) { return BasicCellInventory.isCell(is); }

    @Override
    public BasicCellInventory getCellInventory(ItemStack is, ISaveProvider container) {
        return BasicCellInventory.createInventory(is, container);
    }
```

`appeng/me/cells/BasicCellInventory.java:160-168`:

```java
public static boolean isCell(ItemStack input) { return getStorageCell(input) != null; }

private static IBasicCellItem getStorageCell(ItemStack input) {
    if (input != null && input.getItem() instanceof IBasicCellItem basicCellItem) return basicCellItem;
    return null;
}
```

`appeng/init/internal/InitStorageCells.java:75-76`:

```java
StorageCells.addCellHandler(BasicCellHandler.INSTANCE);
StorageCells.addCellHandler(CreativeCellHandler.INSTANCE);
```

### Cell numbers used by AE2 itself — source

`appeng/core/definitions/AEItems.java:260-266`:

```java
ITEM_CELL_1K    = new BasicStorageCell(p.stacksTo(1), 0.5f,   1,   8, 63, AEKeyType.items());
ITEM_CELL_4K    = new BasicStorageCell(p.stacksTo(1), 1.0f,   4,  32, 63, AEKeyType.items());
ITEM_CELL_16K   = new BasicStorageCell(p.stacksTo(1), 1.5f,  16, 128, 63, AEKeyType.items());
ITEM_CELL_64K   = new BasicStorageCell(p.stacksTo(1), 2.0f,  64, 512, 63, AEKeyType.items());
ITEM_CELL_256K  = new BasicStorageCell(p.stacksTo(1), 2.5f, 256, 2048, 63, AEKeyType.items());

FLUID_CELL_1K   = new BasicStorageCell(p.stacksTo(1), 0.5f,   1,   8, 18, AEKeyType.fluids());
FLUID_CELL_4K   = new BasicStorageCell(p.stacksTo(1), 1.0f,   4,  32, 18, AEKeyType.fluids());
FLUID_CELL_16K  = new BasicStorageCell(p.stacksTo(1), 1.5f,  16, 128, 18, AEKeyType.fluids());
FLUID_CELL_64K  = new BasicStorageCell(p.stacksTo(1), 2.0f,  64, 512, 18, AEKeyType.fluids());
FLUID_CELL_256K = new BasicStorageCell(p.stacksTo(1), 2.5f, 256, 2048, 18, AEKeyType.fluids());
```

`appeng/items/storage/BasicStorageCell.java:60-68` — **the third argument is kilobytes**:

```java
public BasicStorageCell(Properties properties, double idleDrain, int kilobytes, int bytesPerType,
        int totalTypes, AEKeyType keyType) {
    super(properties);
    this.idleDrain = idleDrain;
    this.totalBytes = kilobytes * 1024;
    ...
```

`appeng/items/storage/StorageTier.java:11-22` — tier values:

```java
SIZE_1K   = new StorageTier(1, "1k",   1024,  0.5, () -> ... CELL_COMPONENT_1K);
SIZE_4K   = new StorageTier(2, "4k",   4096,  1.0, ...);
SIZE_16K  = new StorageTier(3, "16k",  16384, 1.5, ...);
SIZE_64K  = new StorageTier(4, "64k",  65536, 2.0, ...);
SIZE_256K = new StorageTier(5, "256k", 262144, 2.5, ...);
```

---

## E5. `javap` — device layer (behaviors + internals)

### The three API extension points

```
public interface appeng.api.behaviors.StackImportStrategy {
  public abstract boolean transfer(appeng.api.behaviors.StackTransferContext);
  public static void register(appeng.api.stacks.AEKeyType, appeng.api.behaviors.StackImportStrategy$Factory);
}

public interface appeng.api.behaviors.StackExportStrategy {
  public abstract long transfer(appeng.api.behaviors.StackTransferContext, appeng.api.stacks.AEKey, long);
  public abstract long push(appeng.api.stacks.AEKey, long, appeng.api.config.Actionable);
  public static void register(appeng.api.stacks.AEKeyType, appeng.api.behaviors.StackExportStrategy$Factory);
}

public interface appeng.api.behaviors.ExternalStorageStrategy {
  public abstract appeng.api.storage.MEStorage createWrapper(boolean, java.lang.Runnable);
  public static void register(appeng.api.stacks.AEKeyType, appeng.api.behaviors.ExternalStorageStrategy$Factory);
}

public interface appeng.api.behaviors.StackTransferContext {
  public abstract appeng.api.networking.storage.IStorageService getInternalStorage();
  public abstract appeng.api.networking.energy.IEnergySource getEnergySource();
  public abstract appeng.api.networking.security.IActionSource getActionSource();
  public abstract int getOperationsRemaining();
  public abstract void setOperationsRemaining(int);
  public abstract boolean hasOperationsLeft();
  public abstract boolean hasDoneWork();
  public abstract boolean isKeyTypeEnabled(appeng.api.stacks.AEKeyType);
  public abstract boolean isInFilter(appeng.api.stacks.AEKey);
  public abstract appeng.util.prioritylist.IPartitionList getFilter();
  public abstract void setInverted(boolean);
  public abstract boolean isInverted();
  public abstract boolean canInsert(appeng.api.stacks.AEItemKey, long);
  public abstract void reduceOperationsRemaining(long);
}

public class appeng.api.behaviors.GenericSlotCapacities {
  public static void register(appeng.api.stacks.AEKeyType, java.lang.Long);
  public static java.util.Map<appeng.api.stacks.AEKeyType, java.lang.Long> getMap();
}

public interface appeng.api.behaviors.ContainerItemStrategy<T extends appeng.api.stacks.AEKey, C> {
  public abstract appeng.api.stacks.GenericStack getContainedStack(net.minecraft.world.item.ItemStack);
  public abstract C findCarriedContext(net.minecraft.world.entity.player.Player, net.minecraft.world.inventory.AbstractContainerMenu);
  public default C findPlayerSlotContext(net.minecraft.world.entity.player.Player, int);
  public abstract long extract(C, T, long, appeng.api.config.Actionable);
  public abstract long insert(C, T, long, appeng.api.config.Actionable);
  public abstract void playFillSound(net.minecraft.world.entity.player.Player, T);
  public abstract void playEmptySound(net.minecraft.world.entity.player.Player, T);
  public abstract appeng.api.stacks.GenericStack getExtractableContent(C);
  public static <T extends appeng.api.stacks.AEKey> void register(appeng.api.stacks.AEKeyType,
      java.lang.Class<T>, appeng.api.behaviors.ContainerItemStrategy<T, ?>);
}

public interface appeng.api.behaviors.GenericInternalInventory {
  public abstract int size();
  public abstract appeng.api.stacks.GenericStack getStack(int);
  public abstract appeng.api.stacks.AEKey getKey(int);
  public abstract long getAmount(int);
  public abstract long getMaxAmount(appeng.api.stacks.AEKey);
  public abstract long getCapacity(appeng.api.stacks.AEKeyType);
  public abstract boolean canInsert();
  public abstract boolean canExtract();
  public abstract void setStack(int, appeng.api.stacks.GenericStack);
  public abstract boolean isSupportedType(appeng.api.stacks.AEKeyType);
  public default boolean isSupportedType(appeng.api.stacks.AEKey);
  public abstract boolean isAllowedIn(int, appeng.api.stacks.AEKey);
  public abstract long insert(int, appeng.api.stacks.AEKey, long, appeng.api.config.Actionable);
  public abstract long extract(int, appeng.api.stacks.AEKey, long, appeng.api.config.Actionable);
  public abstract void beginBatch();  public abstract void endBatch();
  public abstract void endBatchSuppressed();  public abstract void onChange();
}
```

Package doc (`appeng/api/behaviors/package-info.java:28-31`) — the authoritative mapping:

```
 * <li>Import bus: {@link appeng.api.behaviors.StackImportStrategy}.</li>
 * <li>Export bus: {@link appeng.api.behaviors.StackExportStrategy}.</li>
 * <li>Formation plane: {@link appeng.api.behaviors.PlacementStrategy}.</li>
 * <li>Annihilation plane: {@link appeng.api.behaviors.PickupStrategy}.</li>
 * ...
 * <li>Building {@link appeng.api.storage.MEStorage}s from other kinds of inventories, used by the
 *     storage bus and the pattern provider: {@link appeng.api.behaviors.ExternalStorageStrategy}.</li>
```

### Reusable generic import/export implementations (internal-but-public)

```
public abstract class appeng.parts.automation.HandlerStrategy<C, S> {
  public static final appeng.parts.automation.HandlerStrategy<net.neoforged.neoforge.items.IItemHandler,
      net.minecraft.world.item.ItemStack> ITEMS;
  public static final appeng.parts.automation.HandlerStrategy<net.neoforged.neoforge.fluids.capability.IFluidHandler,
      net.neoforged.neoforge.fluids.FluidStack> FLUIDS;
  public appeng.parts.automation.HandlerStrategy(appeng.api.stacks.AEKeyType);
  public boolean isSupported(appeng.api.stacks.AEKey);
  public appeng.api.stacks.AEKeyType getKeyType();
  public abstract appeng.me.storage.ExternalStorageFacade getFacade(C);
  public abstract S getStack(appeng.api.stacks.AEKey, long);
  public abstract long insert(C, appeng.api.stacks.AEKey, long, appeng.api.config.Actionable);
}

public class appeng.parts.automation.StorageImportStrategy<T, S> implements appeng.api.behaviors.StackImportStrategy {
  public appeng.parts.automation.StorageImportStrategy(
      net.neoforged.neoforge.capabilities.BlockCapability<T, net.minecraft.core.Direction>,
      appeng.parts.automation.HandlerStrategy<T, S>,
      net.minecraft.server.level.ServerLevel, net.minecraft.core.BlockPos, net.minecraft.core.Direction);
  public boolean transfer(appeng.api.behaviors.StackTransferContext);
  public static appeng.api.behaviors.StackImportStrategy createItem(...);
  public static appeng.api.behaviors.StackImportStrategy createFluid(...);
}

public class appeng.parts.automation.StorageExportStrategy<T, S> implements appeng.api.behaviors.StackExportStrategy {
  public appeng.parts.automation.StorageExportStrategy(
      net.neoforged.neoforge.capabilities.BlockCapability<T, net.minecraft.core.Direction>,
      appeng.parts.automation.HandlerStrategy<T, S>,
      net.minecraft.server.level.ServerLevel, net.minecraft.core.BlockPos, net.minecraft.core.Direction);
  public long transfer(appeng.api.behaviors.StackTransferContext, appeng.api.stacks.AEKey, long);
  public long push(appeng.api.stacks.AEKey, long, appeng.api.config.Actionable);
  public static appeng.api.behaviors.StackExportStrategy createItem(...);
  public static appeng.api.behaviors.StackExportStrategy createFluid(...);
}

public abstract class appeng.me.storage.ExternalStorageFacade implements appeng.api.storage.MEStorage {
  protected boolean extractableOnly;
  public void setChangeListener(java.lang.Runnable);
  public abstract int getSlots();
  public abstract appeng.api.stacks.GenericStack getStackInSlot(int);
  public abstract appeng.api.stacks.AEKeyType getKeyType();
  public long insert(appeng.api.stacks.AEKey, long, appeng.api.config.Actionable, appeng.api.networking.security.IActionSource);
  public long extract(appeng.api.stacks.AEKey, long, appeng.api.config.Actionable, appeng.api.networking.security.IActionSource);
  public net.minecraft.network.chat.Component getDescription();
  protected abstract int insertExternal(appeng.api.stacks.AEKey, int, appeng.api.config.Actionable);
  protected abstract int extractExternal(appeng.api.stacks.AEKey, int, appeng.api.config.Actionable);
  public abstract boolean containsAnyFuzzy(java.util.Set<appeng.api.stacks.AEKey>);
  public static appeng.me.storage.ExternalStorageFacade of(net.neoforged.neoforge.fluids.capability.IFluidHandler);
  public static appeng.me.storage.ExternalStorageFacade of(net.neoforged.neoforge.items.IItemHandler);
  public void setExtractableOnly(boolean);
}

public class appeng.me.storage.CompositeStorage implements appeng.api.storage.MEStorage, appeng.me.storage.ITickingMonitor {
  public appeng.me.storage.CompositeStorage(java.util.Map<appeng.api.stacks.AEKeyType, appeng.api.storage.MEStorage>);
  public void setStorages(java.util.Map<appeng.api.stacks.AEKeyType, appeng.api.storage.MEStorage>);
  ...
}

public final class appeng.parts.automation.StackWorldBehaviors {   // internal registry behind the API statics
  public static void registerImportStrategy(appeng.api.stacks.AEKeyType, appeng.api.behaviors.StackImportStrategy$Factory);
  public static void registerExportStrategy(appeng.api.stacks.AEKeyType, appeng.api.behaviors.StackExportStrategy$Factory);
  public static void registerExternalStorageStrategy(appeng.api.stacks.AEKeyType, appeng.api.behaviors.ExternalStorageStrategy$Factory);
  public static void registerPlacementStrategy(...);  public static void registerPickupStrategy(...);
  public static java.util.Set<appeng.api.stacks.AEKeyType> withImportStrategy();
  public static java.util.Set<appeng.api.stacks.AEKeyType> withExportStrategy();
  public static java.util.function.Predicate<appeng.api.stacks.AEKeyType> hasImportStrategyTypeFilter();
  public static appeng.api.behaviors.StackImportStrategy createImportFacade(ServerLevel, BlockPos, Direction, Predicate<AEKeyType>);
  public static appeng.api.behaviors.StackExportStrategy createExportFacade(ServerLevel, BlockPos, Direction);
  public static java.util.Map<appeng.api.stacks.AEKeyType, appeng.api.behaviors.ExternalStorageStrategy>
      createExternalStorageStrategies(ServerLevel, BlockPos, Direction);
}

public interface appeng.api.storage.StorageHelper {
  public static long poweredExtraction(IEnergySource, MEStorage, AEKey, long, IActionSource);
  public static long poweredExtraction(IEnergySource, MEStorage, AEKey, long, IActionSource, Actionable);
  public static long poweredInsert(IEnergySource, MEStorage, AEKey, long, IActionSource);
  public static long poweredInsert(IEnergySource, MEStorage, AEKey, long, IActionSource, Actionable);
}

public interface appeng.api.storage.IStorageProvider extends appeng.api.networking.IGridNodeService {
  public abstract void mountInventories(appeng.api.storage.IStorageMounts);
  public static void requestUpdate(appeng.api.networking.IManagedGridNode);
}
public interface appeng.api.storage.IStorageMounts {
  public static final int DEFAULT_PRIORITY;
  public default void mount(appeng.api.storage.MEStorage);
  public abstract void mount(appeng.api.storage.MEStorage, int);
}

public interface appeng.api.AECapabilities {
  public static net.neoforged.neoforge.capabilities.BlockCapability<appeng.api.storage.MEStorage, net.minecraft.core.Direction> ME_STORAGE;
  public static net.neoforged.neoforge.capabilities.BlockCapability<appeng.api.implementations.blockentities.ICraftingMachine, net.minecraft.core.Direction> CRAFTING_MACHINE;
  public static net.neoforged.neoforge.capabilities.BlockCapability<appeng.api.behaviors.GenericInternalInventory, net.minecraft.core.Direction> GENERIC_INTERNAL_INV;
  public static net.neoforged.neoforge.capabilities.BlockCapability<appeng.api.networking.IInWorldGridNodeHost, java.lang.Void> IN_WORLD_GRID_NODE_HOST;
  public static net.neoforged.neoforge.capabilities.BlockCapability<appeng.api.implementations.blockentities.ICrankable, net.minecraft.core.Direction> CRANKABLE;
}
```

### The lookup order that makes gas flow — source proof

**Pattern provider / interface target** — `appeng/helpers/patternprovider/PatternProviderTarget.java:48-77`:

```java
static PatternProviderTarget get(Level l, BlockPos pos, @Nullable BlockEntity be, Direction side,
        IActionSource src) {

    // our capability first: allows any storage channel
    MEStorage storage;
    if (be != null) {
        storage = l.getCapability(AECapabilities.ME_STORAGE, be.getBlockPos(), be.getBlockState(), be, side);
    } else {
        storage = l.getCapability(AECapabilities.ME_STORAGE, pos, side);
    }
    if (storage != null) {
        return wrapMeStorage(storage, src);
    }

    // otherwise fall back to the platform capability
    // TODO: possibly optimize this
    var strategies = StackWorldBehaviors.createExternalStorageStrategies((ServerLevel) l, pos, side);
    var externalStorages = new IdentityHashMap<AEKeyType, MEStorage>(2);
    for (var entry : strategies.entrySet()) {
        var wrapper = entry.getValue().createWrapper(false, Runnables.doNothing());
        if (wrapper != null) {
            externalStorages.put(entry.getKey(), wrapper);
        }
    }

    if (!externalStorages.isEmpty()) {
        return wrapMeStorage(new CompositeStorage(externalStorages), src);
    }

    return null;
}
```

Cached variant for the ticker — `appeng/helpers/patternprovider/PatternProviderTargetCache.java:28-56`:
same order (`AECapabilities.ME_STORAGE` at `:30`, then
`this.strategies = StackWorldBehaviors.createExternalStorageStrategies(l, pos, direction)` at `:32`,
`createWrapper(false, () -> {})` at `:46`, `new CompositeStorage(externalStorages)` at `:54`).

**Import bus** — `appeng/parts/automation/ImportBusPart.java:43-64`:

```java
public ImportBusPart(IPartItem<?> partItem) {
    super(TickRates.ImportBus, StackWorldBehaviors.withImportStrategy(), partItem);

    this.keyTypeSelection = new KeyTypeSelection(() -> {
        getHost().markForSave();
        importStrategy = null;
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }, StackWorldBehaviors.hasImportStrategyTypeFilter());
}

@Override
protected boolean doBusWork(IGrid grid) {
    if (importStrategy == null) {
        var self = this.getHost().getBlockEntity();
        var fromPos = self.getBlockPos().relative(this.getSide());
        var fromSide = getSide().getOpposite();
        importStrategy = StackWorldBehaviors.createImportFacade((ServerLevel) getLevel(), fromPos, fromSide,
                keyTypeSelection.enabledPredicate());
    }
    ...
```

**Export bus** — `appeng/parts/automation/ExportBusPart.java:85, 116, 158-166`:

```java
super(TickRates.ExportBus, StackWorldBehaviors.withExportStrategy(), partItem);
...
exportStrategy = StackWorldBehaviors.createExportFacade((ServerLevel) getLevel(), fromPos, fromSide);
...
var transferFactor = what.getAmountPerOperation();
long amount = (long) context.getOperationsRemaining() * transferFactor;
amount = getExportStrategy().transfer(context, what, amount);
if (amount > 0) {
    context.reduceOperationsRemaining(Math.max(1, amount / transferFactor));
}
```

**Storage bus** — `appeng/parts/storagebus/StorageBusPart.java:436-447, 550-555`:

```java
private void findExternalStorages(Map<AEKeyType, MEStorage> storages) {
    for (var entry : getExternalStorageStrategies().entrySet()) {
        var wrapper = entry.getValue().createWrapper(isExtractableOnly(),
                this::invalidateOnExternalStorageChange);
        ...
private Map<AEKeyType, ExternalStorageStrategy> getExternalStorageStrategies() {
    if (externalStorageStrategies == null) {
        this.externalStorageStrategies = StackWorldBehaviors.createExternalStorageStrategies(...);
```

**Bus budgets** — `appeng/parts/automation/IOBusPart.java:92-96, 190-200`:

```java
public IOBusPart(TickRates tickRates, Set<AEKeyType> supportedKeyTypes, IPartItem<?> partItem) {
    super(partItem);
    this.tickRates = tickRates;
    ...
    this.config = ConfigInventory.configTypes(63).supportedTypes(supportedKeyTypes)
            .changeListener(this::updateState).build();
...
protected int availableSlots() { return Math.min(18 + getInstalledUpgrades(AEItems.CAPACITY_CARD) * 9, this.getConfig().size()); }

protected int getOperationsPerTick() {
    return switch (getInstalledUpgrades(AEItems.SPEED_CARD)) {
        default -> 1;
        case 1 -> 8;
        case 2 -> 32;
        case 3 -> 64;
        case 4 -> 96;
    };
}
```

**Transfer context gates** — `appeng/parts/automation/StackTransferContextImpl.java:81-101, 115-117`:

```java
@Override public boolean isKeyTypeEnabled(AEKeyType space) {
    return keyTypes.isEmpty() || isInverted || keyTypes.contains(space);
}
@Override public boolean isInFilter(AEKey key) {
    return filter.isEmpty() || filter.isListed(key);
}
...
@Override public void reduceOperationsRemaining(long inserted) { operationsRemaining -= inserted; }
```

**The ME Interface is passive** — `appeng/helpers/InterfaceLogic.java`:

```
:105  this.config  = ConfigInventory.configStacks(slots).changeListener(this::onConfigRowChanged).build();
:106  this.storage = ConfigInventory.storage(slots).slotFilter(this::isAllowedInStorageSlot)...
:123  getConfig().useRegisteredCapacities();
:124  getStorage().useRegisteredCapacities();
:239  public ConfigInventory getStorage() {
:254  public MEStorage getInventory() {
```

`getInventory()` (`:250-260`) returns the *local* inventory when configured, else the *grid's* storage —
i.e. it is a pull surface for storage buses. `notifyNeighbors()` (`:218-226`) only wakes the ticker and
invalidates capabilities. There is **no** call to `PatternProviderTarget` anywhere in `InterfaceLogic`
(verified by `Select-String -Pattern "PatternProviderTarget"` on the file: 0 matches).

Capabilities registered for the interface — `appeng/init/InitCapabilityProviders.java:122-135`:

```java
private static void initInterface(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(AECapabilities.GENERIC_INTERNAL_INV, AEBlockEntities.INTERFACE.get(),
            (be, context) -> be.getInterfaceLogic().getStorage());

    event.registerBlockEntity(AECapabilities.ME_STORAGE, AEBlockEntities.INTERFACE.get(),
            (blockEntity, context) -> blockEntity.getInterfaceLogic().getInventory());
}
```

Guidebook, `guidebook/items-blocks-machines/interface.md:34-38`:

> Interfaces act like a small chest and fluid tank that fills itself from and empties to network storage
> depending on what you set it to keep a stock of in its slots. … making it a fast method of import or
> export if you have fast item pipes.

and `guidebook/items-blocks-machines/pattern_provider.md:34-36`:

> Pattern providers are the primary way in which your autocrafting system interacts with the world.
> They push the ingredients in their patterns to adjacent inventories …

**Generic slot capacities** — `appeng/helpers/externalstorage/GenericStackInv.java:214-244`:

```java
@Override public long getCapacity(AEKeyType space) { return capacities.getOrDefault(space, Long.MAX_VALUE); }
...
public void useRegisteredCapacities() {
    for (var entry : GenericSlotCapacities.getMap().entrySet()) {
        setCapacity(entry.getKey(), entry.getValue());
    }
}
```

→ Not registering a capacity "works" (unbounded) but gives nonsense UI numbers. Registering 64 000 GU
or similar is the right call.

**Interface slots accept all key types** — `GenericStackInv.java:105` (`isSupportedType` →
`supportedKeyTypes.contains(type)`) with `supportedKeyTypes` defaulting to `AEKeyTypes.getAll()` at
`:84`.

**Pattern provider "all or nothing"** — `appeng/helpers/patternprovider/PatternProviderLogic.java:481-491`
(`adapterAcceptsAll` simulate-inserts every input and requires full acceptance).

---

## E6. `javap` — client layer

```
@OnlyIn(Dist.CLIENT)
public class appeng.api.client.AEKeyRendering {
  public static synchronized <T extends appeng.api.stacks.AEKey> void register(
      appeng.api.stacks.AEKeyType, java.lang.Class<T>, appeng.api.client.AEKeyRenderHandler<T>);
  public static appeng.api.client.AEKeyRenderHandler<?> get(appeng.api.stacks.AEKeyType);
  public static appeng.api.client.AEKeyRenderHandler<?> getOrThrow(appeng.api.stacks.AEKeyType);
  public static void drawInGui(net.minecraft.client.Minecraft, net.minecraft.client.gui.GuiGraphics, int, int, appeng.api.stacks.AEKey);
  public static void drawOnBlockFace(com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.MultiBufferSource,
      appeng.api.stacks.AEKey, float, int, net.minecraft.world.level.Level);
  public static net.minecraft.network.chat.Component getDisplayName(appeng.api.stacks.AEKey);
  public static java.util.List<net.minecraft.network.chat.Component> getTooltip(appeng.api.stacks.AEKey);
}

@OnlyIn(Dist.CLIENT)
public interface appeng.api.client.AEKeyRenderHandler<T extends appeng.api.stacks.AEKey> {
  public abstract void drawInGui(net.minecraft.client.Minecraft, net.minecraft.client.gui.GuiGraphics, int, int, T);
  public abstract void drawOnBlockFace(com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.MultiBufferSource,
      T, float, int, net.minecraft.world.level.Level);
  public abstract net.minecraft.network.chat.Component getDisplayName(T);
  public default java.util.List<net.minecraft.network.chat.Component> getTooltip(T);
}

public final class appeng.api.client.StorageCellModels {
  public static synchronized void registerModel(net.minecraft.world.level.ItemLike, net.minecraft.resources.ResourceLocation);
  public static synchronized net.minecraft.resources.ResourceLocation model(net.minecraft.world.level.ItemLike);
  public static synchronized java.util.Map<net.minecraft.world.item.Item, net.minecraft.resources.ResourceLocation> models();
  public static net.minecraft.resources.ResourceLocation getDefaultModel();
}

public final class appeng.api.upgrades.Upgrades {
  public static synchronized void add(net.minecraft.world.level.ItemLike, net.minecraft.world.level.ItemLike, int);
  public static synchronized void add(net.minecraft.world.level.ItemLike, net.minecraft.world.level.ItemLike, int, java.lang.String);
  public static synchronized int getMaxInstallable(net.minecraft.world.level.ItemLike, net.minecraft.world.level.ItemLike);
  public static int getEnergyCardMultiplier(appeng.api.upgrades.IUpgradeInventory);
  public static net.minecraft.world.item.Item createUpgradeCardItem(net.minecraft.world.item.Item$Properties);
  public static boolean isUpgradeCardItem(net.minecraft.world.level.ItemLike);
}

public final class appeng.api.upgrades.UpgradeInventories {
  public static appeng.api.upgrades.IUpgradeInventory empty();
  public static appeng.api.upgrades.IUpgradeInventory forMachine(net.minecraft.world.level.ItemLike, int,
      appeng.api.upgrades.MachineUpgradesChanged);
  public static appeng.api.upgrades.IUpgradeInventory forItem(net.minecraft.world.item.ItemStack, int);
  public static appeng.api.upgrades.IUpgradeInventory forItem(net.minecraft.world.item.ItemStack, int,
      appeng.api.upgrades.ItemUpgradesChanged);
}

public class appeng.util.ConfigInventory extends appeng.helpers.externalstorage.GenericStackInv {
  public static appeng.util.ConfigInventory emptyTypes();
  public static appeng.util.ConfigInventory$Builder configTypes(int);
  public static appeng.util.ConfigInventory$Builder configStacks(int);
  public static appeng.util.ConfigInventory$Builder storage(int);
  public appeng.api.stacks.GenericStack getStack(int);  public appeng.api.stacks.AEKey getKey(int);
  public java.util.Set<appeng.api.stacks.AEKey> keySet();  public void setStack(int, appeng.api.stacks.GenericStack);
  public long getMaxAmount(appeng.api.stacks.AEKey);
}
// Builder: supportedType(AEKeyType) / supportedTypes(Collection<AEKeyType>) / slotFilter(...) /
//          changeListener(Runnable) / allowOverstacking(boolean) / build()
```

**Mandatory-ness of the render handler** — `appeng/api/client/AEKeyRendering.java:77-91`:

```java
public static AEKeyRenderHandler<?> getOrThrow(AEKeyType channel) {
    var renderHandler = get(channel);
    if (renderHandler == null) {
        throw new IllegalArgumentException("Missing render handler for channel " + channel);
    }
    return renderHandler;
}

@SuppressWarnings("rawtypes")
private static AEKeyRenderHandler getUnchecked(AEKey stack) {
    return getOrThrow(stack.getType());
}
```

**Drive cell models need no `RegisterAdditional`** — `appeng/client/render/model/DriveModel.java:42-72`:

```java
for (var entry : StorageCellModels.models().entrySet()) {
    var cellModel = baker.bake(entry.getValue(), modelTransform);
    cellModels.put(entry.getKey(), cellModel);
}
...
@Override
public Collection<ResourceLocation> getDependencies() {
    return ImmutableSet.<ResourceLocation>builder().add(StorageCellModels.getDefaultModel())
            .addAll(InitStorageCells.getModels())
            .addAll(StorageCellModels.models().values()).build();
}
```

---

## E7. Version-difference evidence (19.2.10 / 19.2.17 / 19.2.20)

```
$ git diff --stat neoforge/v19.2.10..neoforge/v19.2.20 -- src/main/java/appeng/api
 src/main/java/appeng/api/stacks/AEFluidKey.java | 4 ++--
 src/main/java/appeng/api/stacks/AEItemKey.java  | 6 ++++--
 2 files changed, 6 insertions(+), 4 deletions(-)

$ git diff --stat neoforge/v19.2.17..neoforge/v19.2.20 -- src/main/java/appeng/api
 (identical: same 2 files, same 6 insertions / 4 deletions)
```

Full patch of those two files:

```diff
--- a/src/main/java/appeng/api/stacks/AEFluidKey.java
+++ b/src/main/java/appeng/api/stacks/AEFluidKey.java
@@ -160,7 +160,7 @@ public boolean isTagged(TagKey<?> tag) {
     @Override
     public boolean hasComponents() {
-        return stack.getComponents().isEmpty();
+        return !stack.isComponentsPatchEmpty();
     }
@@ -190,6 +190,6 @@
-        return stack.getComponents().isEmpty() ? idString : idString + " (+components)";
+        return stack.isComponentsPatchEmpty() ? idString : idString + " (+components)";

--- a/src/main/java/appeng/api/stacks/AEItemKey.java
+++ b/src/main/java/appeng/api/stacks/AEItemKey.java
@@ -53,6 +53,7 @@
     private final int damage;
+    private final int maxDamage;
@@ -60,6 +60,7 @@ private AEItemKey(ItemStack stack) {
         this.damage = stack.getDamageValue();
+        this.maxDamage = stack.getMaxDamage();
@@ -184,7 +186,7 @@ public int getFuzzySearchMaxValue() {
-        return getReadOnlyStack().getMaxDamage();
+        return this.maxDamage;
@@ -230,7 +232,7 @@ public boolean hasComponents() {
-        return stack.getComponents().isEmpty();
+        return !stack.isComponentsPatchEmpty();
```

Addon-relevant internal diffs, 19.2.10 → 19.2.20:

```
 src/main/java/appeng/helpers/InterfaceLogic.java                    |  9 ++++++++-
 src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java | 5 +++--
 src/main/java/appeng/me/cells/BasicCellInventory.java               |  5 +++++
 src/main/java/appeng/me/storage/ExternalStorageFacade.java          | 11 +++++++++--
 src/main/java/appeng/parts/automation/AnnihilationPlanePartItem.java| 12 ------------
 src/main/java/appeng/parts/automation/ItemPickupStrategy.java       |  6 ++++--
 src/main/java/appeng/parts/automation/StackTransferContextImpl.java |  2 +-
```

The `BasicCellInventory` delta (the only one that changes behaviour we rely on):

```diff
@@ -404,6 +404,11 @@ private long innerInsert(AEKey what, long amount, Actionable mode) {
     @Override
     public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
+        // Storage preference checks also probe cells from unrelated key spaces.
+        // Avoid loading and searching their inventories for keys they cannot contain.
+        if (!keyType.contains(what)) {
+            return 0;
+        }
         var currentAmount = getCellItems().getLong(what);
```

19.2.17 → 19.2.20 relevant files:

```
 src/main/java/appeng/api/stacks/AEFluidKey.java                    |   4 +-
 src/main/java/appeng/api/stacks/AEItemKey.java                     |   6 +-
 src/main/java/appeng/me/cells/BasicCellInventory.java              |   5 +
 src/main/java/appeng/me/storage/ExternalStorageFacade.java         |   3 +
 src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java    |  28 +++++-
 ... (compass/condenser/testplot/energy/ManagedGridNode work)
```

`PatternEncodingTermMenu` delta is unrelated to key types (blank-pattern auto-refill from network,
`refillBlank()` added at `:310-329`). **No new key-type hooks, no new constraints.**

Conclusion recorded in `AE2_API.md` §9.1: no API break across these three versions for anything this
mod uses; AppMek (19.2.10) and AppliedPneumatics (19.2.17) code compiles unchanged against 19.2.20.

---

## E8. Reference-addon citations (real code, with provenance)

### AppMek (AE2 19.2.10)

| Claim | File:line |
|---|---|
| key type registration inside a registry event | `mekae2/AppliedMekanistics.java:56-62` |
| import/export/external-storage strategy registration | `mekae2/AppliedMekanistics.java:64-66` |
| `ContainerItemStrategy` + `GenericSlotCapacities` registration | `mekae2/AppliedMekanistics.java:68-69` |
| cell-model registration in `FMLCommonSetupEvent` | `mekae2/AppliedMekanistics.java:101-110` |
| upgrade associations + `GuiText.StorageCells.getTranslationKey()` | `mekae2/AppliedMekanistics.java:112-127` |
| P2P attunement tag | `mekae2/AppliedMekanistics.java:129-131` |
| chemical capability adapter over `GENERIC_INTERNAL_INV` for all blocks | `mekae2/AppliedMekanistics.java:92-99` |
| `AEKey` implementation | `mekae2/ae2/MekanismKey.java` |
| `AEKeyType` implementation + ratio choices | `mekae2/ae2/MekanismKeyType.java:34-59` |
| `ExternalStorageStrategy` | `mekae2/ae2/stack/MekanismExternalStorageStrategy.java` |
| hand-rolled export strategy (simulate→commit→overflow-return) | `mekae2/ae2/stack/MekanismStackExportStrategy.java:37-93` |
| hand-rolled import strategy | `mekae2/ae2/stack/MekanismStackImportStrategy.java:31-91` |
| `MEStorage` facade (avoids the int clamp) | `mekae2/ae2/stack/ChemicalHandlerFacade.java` |
| `GenericInternalInventory` → chemical handler | `mekae2/ae2/GenericStackChemicalStorage.java` |
| cell item via `BasicStorageCell` | `mekae2/item/ChemicalStorageCell.java:18-33` |
| cell tier registration | `mekae2/AMItems.java:66-80` |
| portable cell via `PortableCellItem` | `mekae2/item/ChemicalPortableCellItem.java:18-38` |
| portable-cell menu + screen json | `mekae2/AMMenus.java:17-31`; `resources/assets/ae2/screens/terminals/portable_chemical_cell.json` |
| client render handler | `mekae2/ae2/AMChemicalStackRenderer.java` |
| item colours / LED tint | `mekae2/AppliedMekanisticsClient.java:21-35` |
| item model conventions | `mekae2/data/ItemModelProvider.java:49-66` |
| drive-cell model json | `resources/assets/appmek/models/block/drive/cells/chemical_storage_cell_1k.json` |
| AE2 dependency coordinate | `build.gradle`: `implementation("org.appliedenergistics:appliedenergistics2:${ae2_version}")` (ae2_version=19.2.10) |

### AppliedPneumatics (AE2 19.2.17)

| Claim | File:line |
|---|---|
| key type registration (`Registries.BLOCK` gate + `AEKeyTypes.register`) | `pneumatics/AppliedPneumatics.java:42-45` |
| `GenericSlotCapacities.register(type, 64000L)` | `pneumatics/common/me/AEPlugin.java:39` |
| `StorageCells.addCellHandler` in common setup | `pneumatics/common/me/AEPlugin.java:48`, called from `AppliedPneumatics.java:67-70` |
| `ExternalStorageStrategy.register` | `pneumatics/common/me/AEPlugin.java:50` |
| import/export via generic `StorageImportStrategy`/`StorageExportStrategy` | `pneumatics/common/me/AEPlugin.java:52-54, 150-171` |
| `ContainerItemStrategy.register` | `pneumatics/common/me/AEPlugin.java:58` |
| P2P attunement | `pneumatics/common/me/AEPlugin.java:61-62` |
| upgrade associations | `pneumatics/common/me/AEPlugin.java:66-…` |
| air key type + ratio (1000 / 250 / 1000) | `pneumatics/common/me/keys/types/AirKeyType.java:39-58` |
| singleton key, no data, no drops | `pneumatics/common/me/keys/AirKey.java` |
| custom `ICellHandler` | `pneumatics/common/me/storage/AirCellHandler.java` |
| custom `StorageCell` + byte math + `ISaveProvider` routing | `pneumatics/common/me/storage/AirCellInventory.java` (persistence `:200-215`, byte math `:218-259`) |
| cell item (`ICellWorkbenchItem` + tooltip + disassembly) | `pneumatics/common/items/AirStorageCell.java` |
| `HandlerStrategy` subclass | `pneumatics/common/me/strategies/AirHandlerStrategy.java` |
| `ExternalStorageStrategy` + facade | `pneumatics/common/me/strategies/AirExternalStorageStrategy.java`, `AirMachineExternalStorageFacade.java` |
| client render handler (custom texture) | `pneumatics/client/me/render/AirKeyRenderHandler.java` |
| cell models | `pneumatics/client/me/AirStroageModels.java:31-51` |
| AE2 dependency coordinate | `build.gradle`: `implementation "org.appliedenergistics:appliedenergistics2:${ae2_version}"` (ae2_version=19.2.17) |

---

## E9. Create: Crafted Beginning gas API (`net.ty.createcraftedbeginning.api.gas`)

Source available at `<workspace>\CreateCraftedBeginning` (mod_id
`createcraftedbeginning`, version `0.1.1`, NeoForge 21.1.233, Create 6.0.10-280 — from
`gradle.properties`). Confirmed by `jar tf` that CCB has **no** existing AE2 integration (no `appeng`
entries, no source references to `appeng`).

### `GasStack` (javap, truncated to what we use)

```
public final class net.ty.createcraftedbeginning.api.gas.GasStack
        implements net.neoforged.neoforge.common.MutableDataComponentHolder {
  public static final net.ty.createcraftedbeginning.api.gas.GasStack EMPTY;
  public static final com.mojang.serialization.Codec<net.minecraft.core.Holder<...Gas>> GAS_NON_EMPTY_CODEC;
  public static final net.minecraft.network.codec.StreamCodec<...RegistryFriendlyByteBuf, GasStack> STREAM_CODEC;
  public static final net.minecraft.network.codec.StreamCodec<...RegistryFriendlyByteBuf, GasStack> OPTIONAL_STREAM_CODEC;
  public static final com.mojang.serialization.Codec<GasStack> CODEC;
  public GasStack(net.minecraft.core.Holder<...Gas>, long);
  public GasStack(net.minecraft.core.Holder<...Gas>, long, net.minecraft.core.component.DataComponentPatch);
  public boolean isEmpty();
  public net.minecraft.core.Holder<...Gas> getGasHolder();
  public ...Gas getGasType();
  public long getAmount();
  public void setAmount(long);          // MUTABLE
  public void shrink(long);             // MUTABLE
  public void grow(long);               // MUTABLE
  public GasStack copy();
  public GasStack copyWithAmount(long);
  public boolean is(net.minecraft.tags.TagKey<...Gas>);
  public java.util.stream.Stream<net.minecraft.tags.TagKey<...Gas>> getTags();
  public net.minecraft.nbt.Tag save(net.minecraft.core.HolderLookup$Provider);
}
```

### `Gas`

```
public class net.ty.createcraftedbeginning.api.gas.Gas {
  public static final com.mojang.serialization.Codec<net.minecraft.core.Holder<Gas>> HOLDER_CODEC;
  public static final net.minecraft.network.codec.StreamCodec<...RegistryFriendlyByteBuf, net.minecraft.core.Holder<Gas>> HOLDER_STREAM_CODEC;
  public static final net.minecraft.network.codec.StreamCodec<...RegistryFriendlyByteBuf, Gas> GAS_STREAM_CODEC;
  public static net.ty.createcraftedbeginning.api.gas.Gas findById(net.minecraft.resources.ResourceLocation);
  public net.minecraft.network.chat.Component getName(GasStack);
  public java.util.List<net.minecraft.network.chat.Component> getTooltip(GasStack);
  public net.minecraft.resources.ResourceLocation getTexture();
  public net.minecraft.resources.ResourceLocation getResourceLocation();
  public int getTint();
  public int getTint(GasStack);
  public net.minecraft.core.Holder<Gas> getHolder();
  public boolean is(net.minecraft.tags.TagKey<Gas>);
  public java.util.Set<net.minecraft.tags.TagKey<Gas>> getTags();
}
```

### `GasHandler` (the capability we bridge to)

```
public interface net.ty.createcraftedbeginning.api.gas.handler.GasHandler {
  default @Nullable GasTransferSnapshot captureTransferSnapshot();
  boolean isGasValid(int tank, GasStack stack);
  GasStack drain(GasStack resource, GasAction action);      // returns DRAINED stack
  GasStack drain(long maxDrain, GasAction action);
  GasStack getGasInTank(int tank);
  int getTanks();
  long fill(GasStack resource, GasAction action);            // returns FILLED amount
  default AtomicFillResult tryFillAtomically(List<GasStack> resources, GasAction action);
  default AtomicFillResult tryFillAtomicallyFromPressure(List<GasStack> resources, long sourcePressurePa, GasAction action);
  enum AtomicFillResult { SUCCESS, REJECTED, UNSUPPORTED; public boolean isSuccess(); }
}

public interface GasStorageHandler extends GasHandler {
  GasPressureCompartment getPressureCompartment(int tank);
  default long getTankVolume(int);  default long getTankPressurePa(int);
  default long getTankMaxPressurePa(int);  default long getTankMaxAmount(int);
  default PressureModel getTankPressureModel(int);
}
```

`fill` returning the *filled* amount (not the remainder) was confirmed against two independent
implementations in the CCB source:
`compat/functionalstorage/ControllerGasHandler.java` and `compat/functionalstorage/GasDrawerHandler.java`
(`public long fill(GasStack resource, GasAction action) { long acceptedAmount = fillExisting(...); ... }`),
matching `IFluidHandler#fill` semantics.

### `GasCapabilities` / `GasAction`

```
public final class net.ty.createcraftedbeginning.api.gas.GasCapabilities {
  public static final net.neoforged.neoforge.capabilities.BlockCapability<GasHandler, net.minecraft.core.Direction> BLOCK;
  public static final net.neoforged.neoforge.capabilities.EntityCapability<GasHandler, net.minecraft.core.Direction> ENTITY;
  public static boolean hasBlockHandler(net.minecraft.world.level.BlockGetter, net.minecraft.core.BlockPos, net.minecraft.core.Direction);
}

public final class net.ty.createcraftedbeginning.api.gas.GasAction extends java.lang.Enum<GasAction> {
  public static final GasAction EXECUTE;  public static final GasAction SIMULATE;
  public static net.ty.createcraftedbeginning.api.gas.GasAction get(boolean);
  public static net.ty.createcraftedbeginning.api.gas.GasAction fromFluidAction(net.neoforged.neoforge.fluids.capability.IFluidHandler$FluidAction);
  public boolean execute();  public boolean simulate();
  public net.neoforged.neoforge.fluids.capability.IFluidHandler$FluidAction toFluidAction();
}
```

`GasCapabilities.BLOCK` is a `BlockCapability<GasHandler, Direction>`, i.e. it drops straight into
`BlockCapabilityCache.create(...)` and into AE2's
`StorageImportStrategy<T,S>(BlockCapability<T,Direction>, HandlerStrategy<T,S>, ...)`.

### `GasRegistries` / `GasUnits`

```
public final class net.ty.createcraftedbeginning.api.gas.GasRegistries {
  public static final net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<Gas>> GAS_REGISTRY_KEY; // ccb:gas
  public static final net.minecraft.resources.ResourceKey<Gas> EMPTY_GAS_KEY;                                 // ccb:empty
  public static final net.minecraft.core.DefaultedRegistry<Gas> GAS_REGISTRY;  // sync(true), intrusiveHolders
}

public final class net.ty.createcraftedbeginning.api.gas.GasUnits {
  public static final long GU_PER_KGU = 1000;
  public static final long GU_PER_MGU = 1_000_000;
  public static final long GU_PER_GGU = 1_000_000_000;
  public static final long LITERS_PER_KILOLITER = 1000;
  public static int toInt(long gasUnits);  public static int toKilo(long gasUnits);
}
```

Amounts are **`long` gas units (GU)**; `GasUnits.GU_PER_KGU = 1000` is the reason `AE2_API.md` §2.4
recommends `getAmountPerUnit() = 1000` + symbol `"kGU"` for display, and `getAmountPerByte() = 8000`
(8 kGU per byte, mirroring AE2's "8 buckets per byte" for fluids).

`AE2_API.md` §9.5 note 6 (don't store a live `GasStack` inside a `GasKey`) is grounded in the
`setAmount` / `shrink` / `grow` mutators listed above.

---

## E10. Things deliberately *not* verified (and why)

* **Pattern-encoder UI accepting a gas key in the input/output slots.** The data model is generic
  (`ConfigInventory.configStacks(...)`, `PatternEncodingLogic.java:54-57`) and processing patterns are
  `List<GenericStack>` (`AEProcessingPattern.java:38-40`), but the click-to-place path goes through
  client GUI code (`MEStorageScreen`/`Repo`) that we did not trace end-to-end. Flagged in
  `AE2_API.md` §5.4 as "verify in-game on first run", with the programmatic fallback
  (`AEProcessingPattern.encode(...)`, `EncodedPatternItemBuilder`) named.
* **`Gas.getTexture()` namespace/atlas.** Confirmed it returns a `ResourceLocation` but not whether the
  texture lives in the block atlas or a mod-specific sheet; the renderer sketch in `AE2_API.md` §4.3 is
  AppMek's block-atlas pattern and must be adjusted if CCB ships gas sprites elsewhere.
* **Runtime behaviour of `RegisterCapabilitiesEvent.registerBlock` on AE2's blocks under a real
  client/server session** — the pattern is copied from AppMek (which ships it for Mekanism chemicals
  against AE2 19.2.10/19.2.17), but it was not executed here (read-only research task).
* **Performance of the gas cell at 63 types** — pure extrapolation from AE2's own fluid-cell numbers.
