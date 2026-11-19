# CCB gas API — evidence & citations

Companion to `docs/CCB_GAS_API.md`. Every claim in the handbook is traced here to source (`path:line`) or to `javap` output.

**Source root (abbreviated `SRC\`):**
`<workspace>\CreateCraftedBeginning\src\main\java\net\ty\createcraftedbeginning\`

**Jars:**
* `<repo>\libs\createcraftedbeginning-0.1.1.jar` (the production jar)
* `<repo>\libs\appliedenergistics2-19.2.20.jar`
* `<repo>\libs\create-1.21.1-6.0.10.jar`

**Toolchain:** `C:\Program Files\Java\jdk-21\bin\{javap,jar}.exe`, invoked as
`javap -p -classpath libs\createcraftedbeginning-0.1.1.jar <fqcn>`.

**Version identity (why the source and the jar are the same artifact):**

| Evidence | Value |
|---|---|
| `SRC\..\..\..\..\..\gradle.properties` → `mod_version` | `0.1.1` |
| jar `META-INF/neoforge.mods.toml` → `[[mods]] version` | `"0.1.1"` |
| `neoforge.mods.toml` → dependency `create` | `[6.0.10,6.1.0)` |
| `neoforge.mods.toml` → dependency `neoforge` | `[21.1.233,)` |
| `neoforge.mods.toml` → dependency `minecraft` | `[1.21.1]` |
| `gradle.properties` → `mod_group_id` | `net.ty.createcraftedbeginning` |

---

## 1. Gas type and stack

### 1.1 `Gas`

`SRC\api\gas\Gas.java`

```
:32   public static final Codec<Holder<Gas>> HOLDER_CODEC = GasRegistries.GAS_REGISTRY.holderByNameCodec();
:33   public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Gas>> HOLDER_STREAM_CODEC =
              ByteBufCodecs.holderRegistry(GasRegistries.GAS_REGISTRY_KEY);
:36   public static final Holder<Gas> EMPTY_GAS_HOLDER = DeferredHolder.create(GasRegistries.EMPTY_GAS_KEY);
:38   private final Reference<Gas> builtInRegistryHolder = GasRegistries.GAS_REGISTRY.createIntrusiveHolder(this);
:58   public static Optional<Holder<Gas>> parseHolder(Provider lookupProvider, Tag tag)
:63   public static Holder<Gas> parseOptionalHolder(Provider lookupProvider, String tag)
:87   public static Gas findById(ResourceLocation location) {
:88       return GasRegistries.GAS_REGISTRY.getOptional(location).orElse(EMPTY_GAS_HOLDER.value());
:96   public String getTranslationKey()          // "gas." + id.getNamespace() + '.' + id.getPath()
:111  public boolean is(TagKey<Gas> tag)
:115  public boolean isEmpty()                   // getHolder().is(GasRegistries.EMPTY_GAS_KEY)
:128  public boolean isUsableInEquipment()
:132  public Holder<Gas> getHolder()
:140  public ResourceLocation getResourceLocation() { return builtInRegistryHolder.key().location(); }
:144  public int getTint() { return ARGB32.color(alpha, tint); }
:152  public Component getName(GasStack stack) { return Component.translatable(getTranslationKey()); }
:164  public Set<TagKey<Gas>> getTags()
```

> Line 38 is why a `Gas` must be created through a `DeferredRegister`: `createIntrusiveHolder` on an unregistered object throws.
> Line 88 is why an unknown id is **not** an error: the registry is defaulted to `empty`.

### 1.2 Registry

`SRC\api\gas\GasRegistries.java`

```
:15  public static final ResourceKey<Registry<Gas>> GAS_REGISTRY_KEY = ResourceKey.createRegistryKey(CCBAPI.asResource("gas"));
:16  public static final ResourceKey<Gas> EMPTY_GAS_KEY = ResourceKey.create(GAS_REGISTRY_KEY, CCBAPI.asResource("empty"));
:19  public static final DefaultedRegistry<Gas> GAS_REGISTRY = (DefaultedRegistry<Gas>) new RegistryBuilder<>(GAS_REGISTRY_KEY)
         .defaultKey(EMPTY_GAS_KEY).sync(true).withIntrusiveHolders().create();
```

`SRC\api\CCBAPI.java`

```
:12  public static final String MOD_ID = "createcraftedbeginning";
:22  public static ResourceLocation asResource(String path)
```

### 1.3 `GasStack`

`SRC\api\gas\GasStack.java`

```
:39  public static final GasStack EMPTY = new GasStack(null);
:40  GAS_NON_EMPTY_CODEC = ... holderByNameCodec().validate(holder -> holder.value().isEmpty() ? DataResult.error(...) : ...)
:41  OPTIONAL_STREAM_CODEC: writes VarLong amount (0 == empty), then Holder, then DataComponentPatch
:66  STREAM_CODEC: throws DecoderException / EncoderException on empty
:87  private static final Codec<Long> POSITIVE_AMOUNT_CODEC =
         Codec.LONG.validate(amount -> amount > 0 ? DataResult.success(amount)
             : DataResult.error(() -> "Gas amount must be positive; got " + amount + " GU."));
:88  CODEC = RecordCodecBuilder ... fieldOf("id"), fieldOf("amount"), optionalFieldOf("components", EMPTY)
:89  OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC).xmap(optional -> optional.orElse(EMPTY), ...)
:111 private GasStack(Gas gasType, long amount, PatchedDataComponentMap components) {
:112     if (gasType.isEmpty() || amount <= 0) { gasHolder = null; this.amount = 0; ... return; }
:154 public static boolean matches(GasStack first, GasStack second)
:155     return first == second || first.getAmount() == second.getAmount() && isSameGasSameComponents(first, second);
:158 public static boolean isSameGasSameComponents(GasStack first, GasStack second)
:163 public static boolean isSameGas(GasStack first, GasStack second)
:255 public boolean isEmpty()
:317 public long getAmount()
:325 public void setAmount(long amount) { ... this.amount = Math.max(0, amount); }
:333 public void shrink(long amount)   // saturated
:341 public void grow(long amount)     // saturated
:299 public GasStack copyWithAmount(long amount)
:353 public Tag save(Provider provider)          // throws IllegalStateException when empty
:369 public Tag saveOptional(Provider provider)  // empty -> new CompoundTag()
:142 public static GasStack parseOptional(Provider lookupProvider, CompoundTag compoundTag)
:150 public static Optional<GasStack> parse(Provider lookupProvider, Tag tag)
:377 public Component getHoverName()
:385 public int getHint() { return getGasType().getTint(this); }
```

### 1.4 Units — the GU definition

`SRC\api\gas\GasUnits.java`

```
:11  public static final long GU_PER_KGU = 1000;
:12  public static final long GU_PER_MGU = GU_PER_KGU * 1000;
:13  public static final long GU_PER_GGU = GU_PER_MGU * 1000;
:14  public static final long LITERS_PER_KILOLITER = 1000;
:19  public static int toInt(long gasUnits)   // BoundedMath.clampToNonNegativeInt
:23  public static int toKilo(long gasUnits)  // (gasUnits / GU_PER_KGU) clamped
```

`SRC\api\gas\GasPressure.java`

```
:13  public static final long REFERENCE_PRESSURE_PA = 100000;
:14  public static final long VACUUM_PA = 0;
:39  public static long pressure(long gasAmount, long volumeLiters) {
:40      if (gasAmount <= 0) return VACUUM_PA;
:44      if (volumeLiters <= 0) return Long.MAX_VALUE;
:48      return BoundedMath.saturatedMultiplyDivideNonNegative(gasAmount, REFERENCE_PRESSURE_PA, volumeLiters);
:51  public static double pressureExact(long gasAmount, long volumeLiters)
:63  public static long amount(long volumeLiters, long pressurePa)   // volume * pa / 100000
:84  public static long minimumAmountForPressure(long volumeLiters, long pressurePa)
:92  public static long amountAbovePressureFloor(long storedAmount, long volumeLiters, long minimumPressurePa)
:105 public static double atmospheres(long pressurePa) { return (double) pressurePa / REFERENCE_PRESSURE_PA; }
:113 public static long pascals(double atmospheres)
```

The GU↔litre statement in CCB's own prose (`SRC\config\CCBMachines.java`):

```
:118  public final ConfigInt gasVolumePerBlock = i(80, 1, "gas_volume_per_block", "[Unit: kL]",
          "Physical gas volume contributed by each block of an Airtight Tank multiblock.
           At 1 atm, each liter holds 1 GU; increasing volume increases the gas amount held at the same pressure.");
```

There is no fluid/mB conversion anywhere in `api\gas`: `GasStack` has no `FluidStack` adapter, and the only `IFluidHandler` reference in the package is `GasAction`'s convenience mapping (`SRC\api\gas\GasAction.java:4,29-35`).

### 1.5 Item-level gas containers (no fluids)

* `SRC\api\canister\CanisterCapabilities.java:13`
  `ItemCapability<GasCanisterContainer, Void> ITEM = ItemCapability.createVoid(CCBAPI.asResource("gas_canister_container"), GasCanisterContainer.class);`
* `SRC\api\canister\GasCanisterContainer.java:26-70` — `drain/fill/getGasInTank/getTankVolume/getTankMaxPressurePa/getTankPressurePa/getTankMaxAmount`, `save()`, `InjectionMode`.
* Canister sizing (`SRC\config\CCBEquipment.java`):
  ```
  :27  gasVolume   = i(2, 1, "gas_volume", "[Unit: kL]", "Base physical volume of a Gas Canister before Capacity enchantment scaling...")
  :28  maxPressure = f(10, 0, GasPressureLimits.HARD_PRESSURE_ATM, "max_pressure", "[Unit: atm; 1 atm = 100000 Pa]", ...)
  ```
  ⇒ default 2000 L × 1 000 000 Pa / 100 000 = **20 000 GU**, matching the handbook.
* Balloon items carry gas in a data component (`SRC\content\airtights\balloon\BalloonItem.java`):
  ```
  :66  public static boolean containsGas(ItemStack stack) { return !getGas(stack).isEmpty(); }
  :72  public static boolean isBalloon(ItemStack stack)
  :75  public static GasStack getGas(ItemStack stack) {
  :79      GasStack gas = stack.getOrDefault(CCBDataComponents.BALLOON_GAS, GasStack.EMPTY);
  ```
  `BalloonItem extends PackageItem` (`:43`) — i.e. packaged gas *is* a Create package.
  Reference amount: `SRC\config\CCBMachines.java:165`
  `referenceGasPerBalloon = i(10, 1, "reference_gas_per_balloon", "[Unit: kGU]", "Gas amount corresponding to one standard balloon volume at 1 atm...")` ⇒ 10 000 GU.
* Gas Packager / Unpackager / Repackager:
  * `SRC\content\airtights\gaspackager\gasunpackager\GasUnpackagerBlockEntity.java:124-145` (`unwrapBox` stages balloon gas)
  * `:190-201` (`performPendingGasInsertion` → `pendingGas.insertInto(gasHandler(), ...)`)
  * `SRC\content\airtights\gaspackager\GasPackagerGasTransfer.java:24-113` (simulate/drain for packaging)
* Airtight Hatch throughput: `SRC\config\CCBMachines.java:73`
  `maxTransferPerSecond = i(20000, 1, "max_transfer_per_second", "[Unit: GU/s]", ...)`.

---

## 2. Handler capability

### 2.1 `GasCapabilities`

`SRC\api\gas\GasCapabilities.java`

```
:19  public static final BlockCapability<GasHandler, @Nullable Direction> BLOCK =
         BlockCapability.createSided(CCBAPI.asResource("gas_handler"), GasHandler.class);
:21  public static final EntityCapability<GasHandler, @Nullable Direction> ENTITY =
         EntityCapability.createSided(CCBAPI.asResource("gas_handler"), GasHandler.class);
:26  public static boolean hasBlockHandler(BlockGetter level, BlockPos pos, Direction side) {
:27      return level instanceof Level serverLevel && serverLevel.getCapability(BLOCK, pos, side) != null;
```

Directional registration exists in-tree: `SRC\content\airtights\gasinjectionchamber\GasInjectionChamberBlockEntity.java`

```
:87  public static void registerCapabilities(RegisterCapabilitiesEvent event) {
:88      event.registerBlockEntity(GasCapabilities.BLOCK, CCBBlockEntities.GAS_INJECTION_CHAMBER.get(),
             (blockEntity, direction) -> direction == Direction.UP ? blockEntity.exposedGasHandler : null);
```

Direction-agnostic registration example: `SRC\content\airtights\airtighttank\AirtightTankBlockEntity.java`

```
:79  public static void registerCapabilities(RegisterCapabilitiesEvent event) {
:80      event.registerBlockEntity(GasCapabilities.BLOCK, CCBBlockEntities.AIRTIGHT_TANK.get(),
             (tank, ignoredDirection) -> tank.getCapability());
```

### 2.2 `GasHandler`

`SRC\api\gas\handler\GasHandler.java`

```
:21  default @Nullable GasTransferSnapshot captureTransferSnapshot() { return null; }
:25  boolean isGasValid(int tank, GasStack stack);
:27  GasStack drain(GasStack resource, GasAction action);
:29  GasStack drain(long maxDrain, GasAction action);
:31  GasStack getGasInTank(int tank);
:33  int getTanks();
:35  long fill(GasStack resource, GasAction action);
:37  default AtomicFillResult tryFillAtomically(List<GasStack> resources, GasAction action)
:53  default AtomicFillResult tryFillAtomicallyFromPressure(List<GasStack> resources, long sourcePressurePa, GasAction action)
:64  enum AtomicFillResult { SUCCESS, REJECTED, UNSUPPORTED; public boolean isSuccess() }
```

Javadoc at `:48-52` is the only written statement of the transaction contract:
> *"Attempts to accept every resource from one fixed-pressure source as a single transaction. Implementations must leave their observable state unchanged for `GasAction.SIMULATE`, `AtomicFillResult.REJECTED`, and `AtomicFillResult.UNSUPPORTED`."*

`SRC\api\gas\GasAction.java`

```
:10  enum GasAction { EXECUTE(FluidAction.EXECUTE), SIMULATE(FluidAction.SIMULATE);
:20      public static GasAction get(boolean execute)
:37      public boolean execute()
:41      public boolean simulate()
```

Reference implementation of the drain/fill semantics: `SRC\gas\storage\GasTank.java:152-209` (quoted in §4.3).

### 2.3 `GasStorageHandler` / `GasPressureCompartment`

`SRC\api\gas\handler\GasStorageHandler.java`

```
:11  public interface GasStorageHandler extends GasHandler {
:12      GasPressureCompartment getPressureCompartment(int tank);
:14      default long getTankVolume(int tank)        { return getPressureCompartment(tank).getVolume(); }
:18      default long getTankPressurePa(int tank)    { return getPressureCompartment(tank).getPressurePa(); }
:22      default long getTankMaxPressurePa(int tank) { return getPressureCompartment(tank).getMaxPressurePa(); }
:26      default long getTankMaxAmount(int tank)     { return getPressureCompartment(tank).getMaxAmount(); }
:30      default PressureModel getTankPressureModel(int tank) { ... }
```

`SRC\api\gas\pressure\GasPressureCompartment.java`

```
:14  public interface GasPressureCompartment extends GasHandler {
:16      default boolean isGasValid(int tank, GasStack stack) { return tank == 0 && isGasValid(stack); }
:27      default GasStack getGasInTank(int tank)   // tank 0 only
:36      default int getTanks() { return 1; }
:43      Object getCompartmentIdentity();
:45      boolean isGasValid(GasStack stack);
:47      GasStack getGasStack();
:49      default boolean supportsExactDrainRecovery() { return false; }
:53      default long restoreDrainedGas(GasStack resource, GasAction action) { return fill(resource, action); }
:57      long getVolume();
:59      long getPressurePa();
:61      long getMaxPressurePa();
:63      default long getMaxAmount() { return GasPressure.amount(getVolume(), getMaxPressurePa()); }
:67      long getStoredAmount();
:70-75   /** Compartments whose permissions depend on each other's inventory share this identity across every access view. */
         default Object getPredictionGroup() { return getCompartmentIdentity(); }
:82      default @Nullable PredictedTransferLimits predictTransferLimits(GasStack gas, long storedAmount)
:91      default @Nullable PredictedTransferLimits predictTransferLimits(GasStack gas, GasPressurePrediction prediction)
:95      record PredictedTransferLimits(long drainLimit, long fillLimit) {}
:97      default PressureModel getPressureModel() { return PressureModel.VARIABLE; }
:101     enum PressureModel { VARIABLE, FIXED }
```

Identity-sharing usage: `SRC\api\gas\logistics\GasInventoryIdentifiers.java:88-125` (`identifiesSameStorage`) compares `getCompartmentIdentity()`; `SRC\gas\network\solver\endpoint\GasEndpointPlanner.java:204-215` groups endpoint faces by `compartment.getCompartmentIdentity()`.

### 2.4 `GasPressureBoundary` (the "main at a fixed pressure" interface)

`SRC\api\gas\pressure\GasPressureBoundary.java`

```
:13  public interface GasPressureBoundary extends GasHandler {
:14      default long getDrainPressurePa(int tank, GasStack gas) { return GasPressure.REFERENCE_PRESSURE_PA; }  // 1 atm
:18      default long getFillPressurePa(int tank, GasStack gas)  { return GasPressure.VACUUM_PA; }
:23      default double getDrainFlowResistanceFactor(int tank, GasStack gas) { return 0; }
:27      default double getFillFlowResistanceFactor(int tank, GasStack gas)  { return 0; }
:31      default long fillFromPressure(GasStack resource, long sourcePressurePa, GasAction action) { return fill(resource, action); }
:35      default boolean supportsExactDrainRecovery(int tank) { return false; }
:39      default long restoreDrainedGas(int tank, GasStack resource, GasAction action) { return fill(resource, action); }
```

Real CCB implementors (so you can see the pattern):

* `SRC\content\airtights\teslaturbinenozzle\TeslaTurbineNozzleGasHandler.java:13` — fill-only sink, `getDrainPressurePa` unused.
* `SRC\content\airtights\teslaturbine\TeslaTurbineGasHandler.java:16`
* `SRC\content\airtights\airtightregulatorpump\AirtightRegulatorPumpExhaust.java:22`
* `SRC\content\airtights\airtightengine\airtightassemblydriver\AirtightAssemblyDriverGasHandler.java:17`
* `SRC\gas\network\endpoint\AtmosphericGasEndpoint.java:96` — `private class AtmosphericGasHandler implements GasPressureBoundary, GasIntakeListener`
* `SRC\content\airtights\airtighthatch\AirtightHatchController.java:196,263` — `CreativeSourceBoundary`, `DiscardBoundary`

### 2.5 Getting a neighbour's handler (and caching)

`SRC\gas\network\endpoint\ExternalGasEndpoint.java`

```
:35      BlockPos targetPos = location.getConnectedPos();
:36      if (level instanceof PonderLevel) {
:37          gasHandlerCache = ICapabilityProvider.of(() -> level.getCapability(GasCapabilities.BLOCK, targetPos, location.getOppositeFace()));
:39      if (!(level instanceof ServerLevel serverLevel)) return;
:43      gasHandlerCache = ICapabilityProvider.of(invalidate -> BlockCapabilityCache.create(
              GasCapabilities.BLOCK, serverLevel, targetPos, location.getOppositeFace(),
              () -> !networkBlockEntity.isRemoved(), () -> { gasHandlerCache = null; invalidate.run(); }));
```

`SRC\api\gas\logistics\GasInventoryIdentifiers.java:34-53` is the one-shot form:

```
:37      GasHandler targetHandler = level.getCapability(GasCapabilities.BLOCK, pos, side);
:69          GasHandler candidate = level.getCapability(GasCapabilities.BLOCK, pos, direction);
```

### 2.6 `javap` proof — core types

```
===== net.ty.createcraftedbeginning.api.gas.GasCapabilities =====
public final class net.ty.createcraftedbeginning.api.gas.GasCapabilities {
  public static final net.neoforged.neoforge.capabilities.BlockCapability
      <net.ty.createcraftedbeginning.api.gas.handler.GasHandler, net.minecraft.core.Direction> BLOCK;
  public static final net.neoforged.neoforge.capabilities.EntityCapability
      <net.ty.createcraftedbeginning.api.gas.handler.GasHandler, net.minecraft.core.Direction> ENTITY;
  private net.ty.createcraftedbeginning.api.gas.GasCapabilities();
  public static boolean hasBlockHandler(net.minecraft.world.level.BlockGetter, net.minecraft.core.BlockPos, net.minecraft.core.Direction);
  static {};
}

===== net.ty.createcraftedbeginning.api.gas.handler.GasHandler =====
public interface net.ty.createcraftedbeginning.api.gas.handler.GasHandler {
  public default net.ty.createcraftedbeginning.api.gas.handler.GasTransferSnapshot captureTransferSnapshot();
  public abstract boolean isGasValid(int, net.ty.createcraftedbeginning.api.gas.GasStack);
  public abstract net.ty.createcraftedbeginning.api.gas.GasStack drain(net.ty.createcraftedbeginning.api.gas.GasStack,
        net.ty.createcraftedbeginning.api.gas.GasAction);
  public abstract net.ty.createcraftedbeginning.api.gas.GasStack drain(long,
        net.ty.createcraftedbeginning.api.gas.GasAction);
  public abstract net.ty.createcraftedbeginning.api.gas.GasStack getGasInTank(int);
  public abstract int getTanks();
  public abstract long fill(net.ty.createcraftedbeginning.api.gas.GasStack, net.ty.createcraftedbeginning.api.gas.GasAction);
  public default net.ty.createcraftedbeginning.api.gas.handler.GasHandler$AtomicFillResult tryFillAtomically(
        java.util.List<net.ty.createcraftedbeginning.api.gas.GasStack>, net.ty.createcraftedbeginning.api.gas.GasAction);
  public default net.ty.createcraftedbeginning.api.gas.handler.GasHandler$AtomicFillResult tryFillAtomicallyFromPressure(
        java.util.List<net.ty.createcraftedbeginning.api.gas.GasStack>, long, net.ty.createcraftedbeginning.api.gas.GasAction);
}

===== net.ty.createcraftedbeginning.api.gas.handler.GasStorageHandler =====
public interface net.ty.createcraftedbeginning.api.gas.handler.GasStorageHandler
        extends net.ty.createcraftedbeginning.api.gas.handler.GasHandler {
  public abstract net.ty.createcraftedbeginning.api.gas.pressure.GasPressureCompartment getPressureCompartment(int);
  public default long getTankVolume(int);
  public default long getTankPressurePa(int);
  public default long getTankMaxPressurePa(int);
  public default long getTankMaxAmount(int);
  public default net.ty.createcraftedbeginning.api.gas.pressure.GasPressureCompartment$PressureModel getTankPressureModel(int);
}

===== net.ty.createcraftedbeginning.api.gas.handler.GasTransferSnapshot =====
public interface net.ty.createcraftedbeginning.api.gas.handler.GasTransferSnapshot {
  public abstract void restore();
  public default void afterRestore();
}

===== net.ty.createcraftedbeginning.api.gas.pressure.GasPressureCompartment =====
public interface net.ty.createcraftedbeginning.api.gas.pressure.GasPressureCompartment
        extends net.ty.createcraftedbeginning.api.gas.handler.GasHandler {
  public default boolean isGasValid(int, net.ty.createcraftedbeginning.api.gas.GasStack);
  public abstract net.ty.createcraftedbeginning.api.gas.GasStack drain(net.ty.createcraftedbeginning.api.gas.GasStack,
        net.ty.createcraftedbeginning.api.gas.GasAction);
  public abstract net.ty.createcraftedbeginning.api.gas.GasStack drain(long, net.ty.createcraftedbeginning.api.gas.GasAction);
  public default net.ty.createcraftedbeginning.api.gas.GasStack getGasInTank(int);
  public default int getTanks();
  public abstract long fill(net.ty.createcraftedbeginning.api.gas.GasStack, net.ty.createcraftedbeginning.api.gas.GasAction);
  public abstract java.lang.Object getCompartmentIdentity();
  public abstract boolean isGasValid(net.ty.createcraftedbeginning.api.gas.GasStack);
  public abstract net.ty.createcraftedbeginning.api.gas.GasStack getGasStack();
  public default boolean supportsExactDrainRecovery();
  public default long restoreDrainedGas(net.ty.createcraftedbeginning.api.gas.GasStack,
        net.ty.createcraftedbeginning.api.gas.GasAction);
  public abstract long getVolume();
  public abstract long getPressurePa();
  public abstract long getMaxPressurePa();
  public default long getMaxAmount();
  public abstract long getStoredAmount();
  public default java.lang.Object getPredictionGroup();
  public default net.ty.createcraftedbeginning.api.gas.pressure.GasPressureCompartment$PredictedTransferLimits
        predictTransferLimits(net.ty.createcraftedbeginning.api.gas.GasStack, long);
  public default net.ty.createcraftedbeginning.api.gas.pressure.GasPressureCompartment$PredictedTransferLimits
        predictTransferLimits(net.ty.createcraftedbeginning.api.gas.GasStack,
        net.ty.createcraftedbeginning.api.gas.pressure.GasPressurePrediction);
  public default net.ty.createcraftedbeginning.api.gas.pressure.GasPressureCompartment$PressureModel getPressureModel();
}

===== net.ty.createcraftedbeginning.api.gas.pressure.GasPressureBoundary =====
public interface net.ty.createcraftedbeginning.api.gas.pressure.GasPressureBoundary
        extends net.ty.createcraftedbeginning.api.gas.handler.GasHandler {
  public default long getDrainPressurePa(int, net.ty.createcraftedbeginning.api.gas.GasStack);
  public default long getFillPressurePa(int, net.ty.createcraftedbeginning.api.gas.GasStack);
  public default double getDrainFlowResistanceFactor(int, net.ty.createcraftedbeginning.api.gas.GasStack);
  public default double getFillFlowResistanceFactor(int, net.ty.createcraftedbeginning.api.gas.GasStack);
  public default long fillFromPressure(net.ty.createcraftedbeginning.api.gas.GasStack, long,
        net.ty.createcraftedbeginning.api.gas.GasAction);
  public default boolean supportsExactDrainRecovery(int);
  public default long restoreDrainedGas(int, net.ty.createcraftedbeginning.api.gas.GasStack,
        net.ty.createcraftedbeginning.api.gas.GasAction);
}
```

(`javap` prints the `@Nullable Direction` type argument as plain `Direction`; annotations are not part of the erased signature. The `@Nullable` comes from `GasCapabilities.java:19,21`.)

`javap` for `GasAction`, `GasUnits`, `GasPressure`, `GasPressureLimits`, `GasRegistries`, `GasBuilder`, `GasHolder`, `GasTags`, `GasUsageContext` produced member lists identical to the source cited in §1; the `GasPressure` constant fields are:

```
===== net.ty.createcraftedbeginning.api.gas.GasPressure =====
public final class net.ty.createcraftedbeginning.api.gas.GasPressure {
  public static final long REFERENCE_PRESSURE_PA;
  public static final long VACUUM_PA;
  public static long pressure(long, long);
  public static double pressureExact(long, long);
  public static long amount(long, long);
  public static double amount(long, double);
  public static long minimumAmountForPressure(long, long);
  public static long amountAbovePressureFloor(long, long, long);
  public static double atmospheres(long);
  public static long pascals(double);
  public static java.lang.String format(long);
  public static java.lang.String formatAtm(long);
  public static long floor(double); public static long ceil(double); public static long round(double);
}

===== net.ty.createcraftedbeginning.api.gas.GasPressureLimits =====
public final class net.ty.createcraftedbeginning.api.gas.GasPressureLimits {
  public static final int SAFE_PRESSURE_ATM;   // 16  (source :17)
  public static final int HARD_PRESSURE_ATM;   // 24  (source :18)
  public static final long SAFE_PRESSURE_PA;   // 1_600_000 (source :20)
  public static final long HARD_PRESSURE_PA;   // 2_400_000 (source :21)
  public static long clampToHardLimit(long);
  public static double clampToHardLimit(double);
  public static boolean isOverpressure(long);
}
```

---

## 3. Transport / pipes

### 3.1 Participation rules — the decisive code

**Pipe face opens** (`SRC\content\airtights\airtightpipe\AirtightPipeConnections.java`):

```
:100  if (!(adjacentBlock instanceof GasConnectable connectable && connectable.canConnectOnFace(adjacentPos, adjacent, opposite)
           || GasCapabilities.hasBlockHandler(level, adjacentPos, opposite))) {
:101      continue;
```

**Pipe validity also uses the capability** (`SRC\gas\behaviour\GasTransportBehaviour.java`):

```
:62  public static boolean isValidConnectionTarget(@Nullable Level level, BlockPos pos, BlockState state, Direction directionFromPipe) {
:67      Direction localFace = directionFromPipe.getOpposite();
:68      return state.getBlock() instanceof GasConnectable component && component.canConnectOnFace(pos, state, localFace)
             || state.getDestroySpeed(level, pos) != -1 && (state.canBeReplaced() || CCBBlockTags.GAS_SOURCES.matches(state))
             || GasCapabilities.hasBlockHandler(level, pos, localFace);
:46  private static final int CONNECTION_REFRESH_INTERVAL = 20;
```

**Topology marks endpoint faces** (`SRC\gas\network\solver\GasNetworkTopology.java`):

```
:130  if (adjacent.hasGasHandler()) {
:131      endpointFaces.add(new BlockFace(pipePos, side));
:132      continue;
```
where `hasGasHandler()` is `GasCapabilities.hasBlockHandler(level, pos, connectedFace)` (`SRC\gas\network\GasConnectionResolver.java:110-119`).

**Discovery requires storage or boundary** (`SRC\gas\network\solver\endpoint\GasEndpointDiscovery.java`):

```
:45   private static @Unmodifiable List<StorageAccess> discoverStorageAccesses(Level level, Snapshot topology) {
:48          GasStorageHandler handler = getStorageHandler(level, endpointFace);
:49          if (handler == null) continue;
:53          for (int tank = 0; tank < handler.getTanks(); tank++) {
:54              GasPressureCompartment compartment = handler.getPressureCompartment(tank);
:55              if (compartment.getVolume() <= 0) continue;      // <-- volume 0 == inert
:59              accesses.add(new StorageAccess(endpointFace, compartment));
...
:152  private static GasStorageHandler getStorageHandler(Level level, BlockFace endpointFace) {
:158      GasHandler rawHandler = level.getCapability(GasCapabilities.BLOCK, adjacentPos, endpointFace.getOppositeFace());
:159      if (!(rawHandler instanceof GasStorageHandler handler)) return null;
...
:167  private static GasPressureBoundary getSingleTankPressureBoundary(Level level, BlockFace endpointFace) {
:173      GasHandler rawHandler = level.getCapability(GasCapabilities.BLOCK, adjacentPos, endpointFace.getOppositeFace());
:174      if (!(rawHandler instanceof GasPressureBoundary handler) || rawHandler instanceof GasStorageHandler
             || handler.getTanks() != 1) return null;
```

⇒ **A `GasHandler` that implements neither interface is never discovered**, even though the pipe connects and the topology lists the face. That is the "connected but nothing flows" failure mode.

### 3.2 The solver

`SRC\gas\network\solver\GasNetworkSimulator.java`

```
:45  public static void simulateTick(Level level, BlockPos startPos) {
:46      if (level.isClientSide || GasConnectionResolver.getTransportBehaviour(level, startPos) == null) return;
:59          Snapshot topology = GasNetworkTopology.get(level, startPos);
:64          if (topology.pipePositions().isEmpty()) return;
:69          GasEndpointPlanner endpointPlanner = GasEndpointPlanner.prepare(level, topology);
:83          for (GasStack gas : endpointPlanner.gasGroups()) simulateGas(...)
:182         GasPressureGraphSolution solution = pressureGraph.solve(endpoints, transportBudget, pressureAccumulator.isEnabled());
:191         if (!solution.converged() || solution.isEmpty()) return;
:217         double fraction = GasEndpointTransferPlanner.calculateSubstepFraction(...)
:240         if (executeQuantizedPlan(...)) ...
:262         executeComponentPlan(level, gas, componentPlan, transportBudget, flowAccumulator, integration);
:39  private static final int MAX_DYNAMIC_SUBSTEPS = 16;
```

Endpoint planning (`SRC\gas\network\solver\endpoint\GasEndpointPlanner.java`): storage endpoints get `PressureState(pressurePa, fixed, currentAmount, volume, maxPressurePa, maxAmount)` where for `VARIABLE` the pressure is `GasPressure.pressureExact(currentAmount, volume)` (`:320`), and drain/fill faces are derived by simulating against the compartment's own limits (`:270-303`).

### 3.3 Pipe/pump/valve parameters

`SRC\gas\behaviour\GasTransportBehaviour.java`

```
:47  private static final long PIPE_RESISTANCE_UNITS = GasFlowResistance.fromFactor(0.958);
:212 public GasTransportEdgeProperties getTransportEdgeProperties(BlockState state, Direction entryFace, Direction exitFace, GasStack gasStack)
:216 public @Nullable GasPressureBoundary getInternalPressureBoundary(Direction face) { return null; }
```

`SRC\content\airtights\airtightpump\AirtightPumpTransportBehaviour.java`

```
:51  long maxPressureBoostPa = Math.max(GasPressure.VACUUM_PA, pump.getPumpMaxPressureBoostPa());
:52  long flowRateLimit = Math.max(0, pump.getPumpFlowRateLimit());
:53  double pumpCurveConductance = ... (double) flowRateLimit / maxPressureBoostPa;
:54  return new GasTransportEdgeProperties(resistanceUnits, GasTransportPressureDrive.pressureBoost(maxPressureBoostPa),
         flowRateLimit, GasPressure.REFERENCE_PRESSURE_PA, pumpCurveConductance, true, true);
```

`SRC\config\CCBMachines.java`

```
:82  maxPressureBoost = f(4, 0, HARD_PRESSURE_ATM, "max_pressure_boost", "[Unit: atm; 1 atm = 100000 Pa]",
        "Maximum pressure boost in the pumping direction at Create's configured maximum rotation speed...")
:83  maxFlowPerTick = i(4000, 0, 1000000, "max_flow_per_tick", "[Unit: GU/t]", ...)
:92  maxPressureRise = f(8, 0, HARD_PRESSURE_ATM, "max_pressure_rise", ...)          // regulator pump
:93  maxFlowPerTick = i(4000, 0, 1000000, "max_flow_per_tick", "[Unit: GU/t]", ...)  // regulator pump
```

Valves gate flow rather than storing it: `SRC\content\airtights\airtightvalve\AirtightValveTransportBehaviour.java:21-28`
(`allowsInboundFlow` / `allowsOutboundFlow` = `valve.isOpen() && canConnectOnFace(...)`).
Internal boundaries are discovered as endpoints at `SRC\gas\network\solver\endpoint\GasEndpointDiscovery.java:75-89`.

### 3.4 Rollback / transaction machinery

`SRC\gas\network\solver\transfer\GasTransferExecutor.java`

```
:148  private static boolean restoreSnapshot(@Nullable GasTransferSnapshot snapshot, Throwable failure) {
:154      snapshot.restore();
:155      restored = true;
:156      snapshot.afterRestore();
...
:273  private void beginOperation(GasHandler handler, long amount, boolean extracting) {
:274      GasTransferSnapshot captured = handler.captureTransferSnapshot();
...
:281  private void restoreFailedOperation(Throwable failure) {
:286      if (restoreSnapshot(snapshot, failure)) return;
:289      if (!extracting) uncertainFill = attemptedAmount;
:292      String operation = extracting ? "drain" : "fill";
:293      failure.addSuppressed(new IllegalStateException("Unconfirmed gas " + operation + " of up to " + attemptedAmount
             + " GU by " + attemptedHandler.getClass().getName() + "; no successful transfer snapshot restoration. ..."));
```

Pooled (multi-handler) transfers restore already-drained gas through `restoreDrainedGas`/`fill` when a later step throws (`:79-125`).

`GasHandler.captureTransferSnapshot` javadoc (`SRC\api\gas\handler\GasHandler.java:14-20`):
> *"Captures a checkpoint before an EXECUTE call. The default keeps existing handlers usable without promising rollback after an exception. Override when all state changed by the operation can be restored; return null otherwise. Capturing must not change state."*

Reference snapshot implementation: `SRC\gas\storage\GasTank.java:66-81`.

### 3.5 Overpressure attachment points

```
SRC\content\airtights\airtighttank\AirtightTankBlockEntity.java:130          overpressureBehaviour = new OverpressureBehaviour(this, this::isController, this::getCurrentGasPressurePa);
SRC\content\airtights\airtightforgingpress\AirtightForgingPressBlockEntity.java:168  new OverpressureBehaviour(this, this::getCurrentGasPressurePa);
SRC\content\airtights\gasinjectionchamber\GasInjectionChamberBlockEntity.java:118    new OverpressureBehaviour(this, this::getCurrentGasPressurePa);
SRC\content\airtights\airtightreactorkettle\AirtightReactorKettleBlockEntity.java:527 new OverpressureBehaviour(this, pressureChannels);
SRC\content\airtights\airtightfractionationtower\AirtightFractionationTowerOverpressure.java:58-60
    if (state.isFailureReady() && sample.compartment() != null) { ... PressureRuptureService.rupture(level, sample.position(), sample.compartment()); }
```

No `OverpressureBehaviour` is constructed in `airtightpipe\`, `airtightvalve\` or `airtightcheckvalve\` (grep result: 4 construction sites, listed above).

Rupture (`SRC\gas\overpressure\PressureRuptureService.java`):

```
:23  public static void rupture(Level level, BlockPos rupturePos, GasPressureCompartment failedCompartment) {
:28      releaseFailedCompartment(level, rupturePos, failedCompartment);
:29      createMechanicalBlast(level, rupturePos);
:30      level.destroyBlock(rupturePos, false);
:34      float explosionStrength = Math.max(0, CCBConfig.server().gas.pressureRupture.explosionStrength.getF());
:39      ExplosionInteraction interaction = ... explosionDamagesSurroundingBlocks.get() ? BLOCK : NONE;
:40      level.explode(null, x+0.5, y+0.5, z+0.5, explosionStrength, false, interaction);
:50      GasStack releasedGas = failedCompartment.drain(storedAmount, GasAction.EXECUTE);
:56      GasReleaseService.release(level, GasReleaseRequest.radial(releasedGas, rupturePos, GasReleaseCause.RUPTURE, sourcePressurePa), knockbackHandled);
```

`SRC\config\CCBGas.java`

```
:12  public final PressureRupture pressureRupture = nested(0, PressureRupture::new, "Pressure Rupture");
:23  explosionStrength = f(4, 0, 64, "explosion_strength", ...)
:24  explosionDamagesSurroundingBlocks = b(false, "explosion_damages_surrounding_blocks", ...)
```

Call sites (where a block actually bursts):
`AirtightTankBlockEntity.java:462`, `AirtightForgingPressBlockEntity.java:371`, `AirtightReactorKettleBlockEntity.java:445`, `GasInjectionChamberBlockEntity.java:298`, `AirtightFractionationTowerOverpressure.java:60`.

---

## 4. Pressure model

### 4.1 Formula and limits

Identical to §1.4 (`GasPressure.java:13,39-69`) — reproduced here because it is the whole model:

```
:13  REFERENCE_PRESSURE_PA = 100000
:39  pressure(amount, volumeLiters)   = amount * 100000 / volumeLiters      // saturating; MAX_VALUE when volume <= 0
:63  amount(volumeLiters, pressurePa) = volumeLiters * pressurePa / 100000
```

`SRC\api\gas\GasPressureLimits.java`

```
:17  public static final int SAFE_PRESSURE_ATM = 16;
:18  public static final int HARD_PRESSURE_ATM = 24;
:20  public static final long SAFE_PRESSURE_PA = SAFE_PRESSURE_ATM * GasPressure.REFERENCE_PRESSURE_PA;
:21  public static final long HARD_PRESSURE_PA = HARD_PRESSURE_ATM * GasPressure.REFERENCE_PRESSURE_PA;
:26  public static long clampToHardLimit(long pressurePa)   // Math.clamp(pa, 0, HARD)
:38  public static boolean isOverpressure(long pressurePa) { return pressurePa > SAFE_PRESSURE_PA; }
```

### 4.2 Tiers

`SRC\api\gas\pressure\GasPressureTier.java`

```
:13  public enum GasPressureTier {
:14      VACUUM(0), AMBIENT(1), LOW(6), MEDIUM(10), HIGH(14), EXTREME(GasPressureLimits.SAFE_PRESSURE_ATM);
:21  public static final long EXTREME_MAX_PRESSURE_PA = EXTREME.maximumPressurePa;
:22  public static final long MAX_DEFINED_PRESSURE_PA = EXTREME_MAX_PRESSURE_PA;
:31      maximumPressurePa = GasPressure.pascals(maximumPressureAtm);
:34  public static GasPressureTier resolve(long pressurePa)   // first tier with max >= pa (after clamp)
:46  public static GasPressureTier highestSatisfied(long pressurePa) { return resolve(pressurePa); }
:73  public static boolean satisfies(long pressurePa, GasPressureTier requiredTier)
:77  public static long minimumPressureFor(GasPressureTier tier)
:101 public static long normalizePressurePa(long pressurePa)   // clamp to [0, 16 atm]
```

`GasPressureLimits` javadoc (`:7-13`):
> *"The rated pressure is the highest pressure considered normal operation. The hard pressure is the absolute physical ceiling used by storage and transport infrastructure. The hard ceiling does not create an additional recipe pressure tier."*

⇒ `resolve()` never returns anything above 16 atm, and 24 atm is a *mechanical* ceiling only.

`javap` confirms the enum constants and the two public long constants:

```
===== net.ty.createcraftedbeginning.api.gas.pressure.GasPressureTier =====
public final class ...GasPressureTier extends java.lang.Enum<...GasPressureTier> {
  public static final ...GasPressureTier VACUUM; AMBIENT; LOW; MEDIUM; HIGH; EXTREME;
  public static final long EXTREME_MAX_PRESSURE_PA;
  public static final long MAX_DEFINED_PRESSURE_PA;
  public static ...GasPressureTier resolve(long);
  public static ...GasPressureTier highestSatisfied(long);
  public static java.util.Optional<...GasPressureTier> nearestUnsatisfied(long);
  public static boolean satisfies(long, ...GasPressureTier);
  public static long minimumPressureFor(...GasPressureTier);
  public static long minimumPressureFor(...);
  public static long normalizePressurePa(long);
  public long minimumPressurePa();  public int maximumPressureAtm();  public long maximumPressurePa();
  public boolean contains(long);  public boolean isSatisfiedBy(long);
}
```

### 4.3 Tank API (`gas.storage`)

`SRC\gas\storage\GasTankLimits.java`

```
:11  public record GasTankLimits(long volumeLiters, long maxPressurePa) {
:12      public GasTankLimits {
:13          if (volumeLiters < 0) throw new IllegalArgumentException(...);
:17          if (maxPressurePa < 0) throw ...;
:21          if (maxPressurePa > GasPressureLimits.HARD_PRESSURE_PA)
:22              throw new IllegalArgumentException("Gas tank maximum pressure must not exceed " + HARD + " Pa; got " + maxPressurePa + " Pa.");
:26      public static GasTankLimits atReferencePressure(long volumeLiters)   // maxPressure = 1 atm
:30      public long maxAmount() { return GasPressure.amount(volumeLiters, maxPressurePa); }
```

`SRC\gas\storage\GasTank.java`

```
:22  public class GasTank implements GasStorageHandler, GasPressureCompartment {
:29-52 ctors: (volume) | (volume, maxPressure) | (volume, validator) | (volume, maxPressure, validator) | (limits[, validator])
:66  public GasTransferSnapshot captureTransferSnapshot()   // snapshot limits+gas, restore() + onStateChanged()
:84  public PredictedTransferLimits predictTransferLimits(GasStack gasType, long storedAmount)
:98  public GasPressureCompartment getPressureCompartment(int tank)   // throws unless tank == 0
:107 public Object getCompartmentIdentity() { return this; }
:127 public long getVolume() { return limits.volumeLiters(); }
:132 public long getPressurePa() { return GasPressure.pressure(getStoredAmount(), getVolume()); }
:137 public long getMaxPressurePa() { return limits.maxPressurePa(); }
:142 public long getMaxAmount() { return limits.maxAmount(); }
:147 public long getStoredAmount() { return gas.getAmount(); }
:152 public GasStack drain(GasStack resource, GasAction action)        // rejects other gas/components
:161 public GasStack drain(long maxDrain, GasAction action)            // copy returned; executed only on EXECUTE
:178 public int getTanks() { return 1; }
:183 public long fill(GasStack resource, GasAction action) {           // validator, same-gas check, remaining space
:188     if (action.simulate()) return getFillableAmount(resource);
:200     long remainingSpace = getRemainingAmount();
:201     long amountToTransfer = Math.min(remainingSpace, resource.getAmount());
:202     gas.grow(amountToTransfer);
:212 public AtomicFillResult tryFillAtomicallyFromPressure(List<GasStack> resources, long sourcePressurePa, GasAction action)
:254 public GasTankLimits getLimits();  :258 snapshot();  :262 canContain(limits, contents)
:266 tryReplaceContents(GasStack);  :270 tryReconfigure(GasTankLimits);  :274 tryApplyState(GasTankState)
:278 read(Provider, CompoundTag);  :282 readClampedToLimits(...)  :300 write(Provider, CompoundTag)
:305 isEmpty();  :309 getRemainingAmount();  :313 protected void onStateChanged()
```

`SRC\gas\storage\SmartGasTank.java`

```
:9   public class SmartGasTank extends GasTank {
:12      public SmartGasTank(long volume, long maxPressurePa, Runnable updateCallback)
:18      @Override protected void onStateChanged() { updateCallback.run(); }
```

`SRC\gas\storage\GasTankState.java` — `record GasTankState(GasTankLimits limits, GasStack contents)`.
`SRC\gas\storage\GasTankMutationResult.java` — `{APPLIED, UNCHANGED, INVALID_GAS, EXCEEDS_PRESSURE_LIMIT}` + `excessAmount`, `accepted()`, `changed()`, `requireAccepted()`.

`SRC\gas\storage\CreativeGasReservoir.java` — `implements GasStorageHandler, GasPressureCompartment`, `PressureModel.FIXED`, `getFixedPressurePa()/setFixedPressurePa(long)/reconfigure(GasTankLimits)`.

### 4.4 Admission rules (fill/drain gating)

`SRC\gas\transfer\GasPressureFillService.java`

```
:70  public static long getTransferableAmount(GasPressureTransferEndpoint target, GasStack gas, long maxAmount, long sourcePressurePa) {
:71      sourcePressurePa = GasPressureLimits.clampToHardLimit(sourcePressurePa);
:77      double targetPressurePa = target.getFillPressurePa(gas);
:78      if (sourcePressurePa <= targetPressurePa) return 0;
:82      long pressureLimitedAmount = requestedAmount;
:83      if (target.getPressureModel() == PressureModel.VARIABLE) {
:84          long safeFill = GasPressureTransferMath.maxFillAmount(target.getStoredAmount(), target.getMaxAmount(),
                  target.getVolume(), targetPressurePa, sourcePressurePa, target.getMaxPressurePa());
:85          pressureLimitedAmount = Math.min(pressureLimitedAmount, safeFill);
:91      long simulatedFill = target.simulateFillAmount(gas, pressureLimitedAmount, sourcePressurePa);
:92      return Math.min(pressureLimitedAmount, simulatedFill);
```

`SRC\gas\network\math\GasPressureTransferMath.java`

```
:16  public static long maxDrainAmount(long currentAmount, long volume, double currentPressurePa, double manifoldPressurePa) {
:17      if (manifoldPressurePa >= currentPressurePa) return 0;
:21      long minimumRemainingAmount = minimumAmountAtOrAbovePressure(volume, manifoldPressurePa);
:22      return Math.max(0, saturatedSubtract(currentAmount, minimumRemainingAmount));
:25  public static long maxFillAmount(long currentAmount, long maxAmount, long volume, double currentPressurePa,
                                       double manifoldPressurePa, long maxPressurePa) {
:26      double pressureCeilingPa = Math.min(manifoldPressurePa, maxPressurePa);
:27      if (pressureCeilingPa <= currentPressurePa) return 0;
:31      long maximumStoredAmount = Math.min(maxAmount, maximumAmountAtOrBelowPressure(volume, pressureCeilingPa));
:32      return Math.max(0, saturatedSubtract(maximumStoredAmount, currentAmount));
```

`SRC\gas\transfer\GasPressureTransferEndpoint.java` — factories (`:29 compartment`, `:33 storage`, `:42 canister`, `:51 pressureBoundary`, `:60 tryHandler`) and the access wrappers `CompartmentAccess` (VARIABLE ⇒ `GasPressure.pressureExact`, FIXED ⇒ reported pressure, `:213-219`) and `BoundaryAccess` (always `FIXED`, `volume/max = Long.MAX_VALUE`, `:383-468`).

### 4.5 Overpressure stress mathematics

`SRC\gas\overpressure\OverpressureStressCalculator.java`

```
:11  TICKS_PER_SECOND = 20;
:12  HARD_LIMIT_FAILURE_TICKS = 5 * TICKS_PER_SECOND;      // 100 ticks
:13  FULL_RECOVERY_TICKS = 15 * TICKS_PER_SECOND;          // 300 ticks
:14  STABILIZATION_GRACE_TICKS = 40;
:16  MIN_STRESS = 0;  :17 MAX_STRESS = 1;
:19  HARD_LIMIT_STRESS_PER_TICK = MAX_STRESS / HARD_LIMIT_FAILURE_TICKS;
:20  RECOVERY_PER_TICK = MAX_STRESS / FULL_RECOVERY_TICKS;
:21  OVERPRESSURE_RANGE_PA = HARD_PRESSURE_PA - SAFE_PRESSURE_PA;
:26  public static double normalizedOverpressure(long pressurePa)   // 0 at 16 atm, 1 at 24 atm
:38  public static double stressIncreasePerTick(long pressurePa) {
:39      double normalized = normalizedOverpressure(pressurePa);
:40      return normalized * normalized * HARD_LIMIT_STRESS_PER_TICK;    // quadratic in the overpressure fraction
:48  public static double advance(double currentStress, long pressurePa)
:57  public static double sanitizeStress(double stress)
```

`SRC\gas\overpressure\OverpressureState.java`

```
:20  public boolean tick(long currentPressurePa)   // decrements stabilisation grace, then advance()
:58  public boolean isAtFailureThreshold() { return stress >= OverpressureStressCalculator.MAX_STRESS; }
:62  public boolean isFailureReady() {
:63      return !isStabilizing() && isAtFailureThreshold() && GasPressureLimits.isOverpressure(lastObservedPressurePa);
```

So: a fresh tank is immune for 40 ticks; at 24 atm it breaks in 5 s; between 16 and 24 atm the break time scales as 1/n² of that range.

---

## 5. Gas acquisition

### 5.1 The gases

`SRC\registry\gas\CCBGases.java`

```
:20   public static final CCBGasDeferredRegister GAS_REGISTER = new CCBGasDeferredRegister(CCBAPI.MOD_ID);
:22   NATURAL_AIR            = GAS_REGISTER.register("natural_air",  builder().tint(0xCBCFFA).tag(CCBGasTags.NATURAL.tag));
:23   ULTRAWARM_AIR          = ... "ultrawarm_air"  ... 0xF0D2B2 ... ULTRAWARM
:24   ETHEREAL_AIR           = ... "ethereal_air"   ... 0xF4BAF5 ... ETHEREAL
:26   ENERGIZED_NATURAL_AIR  = ... "energized_natural_air" ... 0xD2D5FB ... NATURAL + ENERGIZED
:27   ENERGIZED_ULTRAWARM_AIR= ... "energized_ultrawarm_air" ... 0xF2D8BC ... ULTRAWARM + ENERGIZED
:28   ENERGIZED_ETHEREAL_AIR = ... "energized_ethereal_air" ... 0xF5C3F6 ... ETHEREAL + ENERGIZED
:30   MOIST_AIR              = ... "moist_air" ... 0xBEDAED ... MOIST
:31   SPORE_AIR              = ... "spore_air" ... 0xE8F5C4 ... SPORE
:32   SCULK_AIR              = ... "sculk_air" ... 0x111B21 ... SCULK
:34   STEAM                  = ... "steam" ... 0xE7EEF2
:36   POTION_GAS             = GAS_REGISTER.register("potion_gas", () -> new PotionGas(builder().tint(0xC080D0).usableInEquipment(false)));
:38   CREATIVE_AIR           = ... "creative_air" ... 0x000000 ... CREATIVE
:40   public static void provideLang(BiConsumer<String, String> consumer)
```

`javap` field list (12 gases + register) is identical — see §6.

Tag namespace: `SRC\registry\CCBTags.java`

```
:261  public enum CCBGasTags { NATURAL, ULTRAWARM, ETHEREAL, MOIST, SPORE, SCULK, ENERGIZED, CREATIVE;
:274      CCBGasTags() { this(MOD); }                      // MOD = CCBAPI.MOD_ID, not "c"
:287      ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace.id, path == null ? Lang.asId(name()) : path);
:288      tag = optional ? optionalTag(GasRegistries.GAS_REGISTRY, id) : GasTags.create(id);
```

⇒ tag ids are `createcraftedbeginning:natural`, `…:ultrawarm`, `…:ethereal`, `…:moist`, `…:spore`, `…:sculk`, `…:energized`, `…:creative`.

Registration plumbing for add-on gases: `SRC\registry\gas\CCBGasDeferredRegister.java:20-47` (`extends DeferredRegister<Gas>` on `GasRegistries.GAS_REGISTRY_KEY`, `register(String, GasBuilder)` helper, `GasHolder` factory).

### 5.2 Atmosphere

`SRC\gas\atmosphere\AtmosphereStateResolver.java`

```
:21  public static AtmosphereState resolve(Level level, BlockPos pos) {
:34      Gas gas = AtmosphereProviderRegistry.resolveComposition(level, sourcePos);
:35      if (gas == null) gas = Gas.EMPTY_GAS_HOLDER.value();
:39      return new AtmosphereState(gas, resolveWorldPressurePa(level, worldPos));
:42  public static long resolvePressurePa(Level level, BlockPos pos)
:47  private static long resolveWorldPressurePa(Level level, BlockPos worldPos) {
:48      return GasPressureLimits.clampToHardLimit(
              AtmosphereProviderRegistry.resolvePressurePa(level, worldPos).orElse(GasPressure.VACUUM_PA));
```

`SRC\api\atmosphere\AtmosphereProviderRegistry.java:27-41` — `register(id[, priority], provider)`, `resolveComposition`, `resolvePressurePa`; addons hook via `api.events.RegisterAtmosphereProvidersEvent`.

`SRC\api\atmosphere\AtmosphereState.java:11` — `record AtmosphereState(Gas gas, long pressurePa)`.

Atmospheric *endpoints* (open pipe ends / `#createcraftedbeginning:gas_sources` blocks):

* `SRC\gas\network\GasConnectionResolver.java:121-123`
  ```
  :121 public boolean isAtmospheric() {
  :122     return loaded && (!(state.getBlock() instanceof GasConnectable connectable) || !connectable.blocksAtmosphere(pos, state, connectedFace))
              && !hasTransportConnection() && !hasGasHandler()
              && (CCBBlockTags.GAS_SOURCES.matches(state) || state.canBeReplaced() && ...);
  ```
* `SRC\gas\network\endpoint\AtmosphericGasEndpoint.java`
  ```
  :75  public AtmosphereState getAtmosphereState()
  :83  public boolean canExtractAtmosphericGas() {
  :84      if (level == null || !level.isLoaded(outputPos) || ... || !CCBConfig.server().gas.atmosphere.allowEnvironmentAirExtraction.get()) return false;
  :88      BlockState sourceState = level.getBlockState(pos);
  :89      return level.getBlockEntity(pos) instanceof GasTransportNode transporter && transporter.allowsGasTransport(level, sourceState, pos, direction);
  :96  private class AtmosphericGasHandler implements GasPressureBoundary, GasIntakeListener
  ```
* Endpoint discovery: `SRC\gas\network\solver\endpoint\GasEndpointDiscovery.java:93-149` (`AtmosphericAccess`, `canExtractAtmosphericGas()` ⇒ drain limit `Long.MAX_VALUE`).

### 5.3 Producers / consumers with citations

| Claim | Citation |
|---|---|
| Airtight tank: 80 kL per block, **24 atm** ceiling | `SRC\content\airtights\airtighttank\AirtightTankBlockEntity.java:76` (`new SmartGasTank(getVolumePerBlock(), GasPressureLimits.HARD_PRESSURE_PA, ...)`), `:99-101` (`getVolumePerBlock() * GasUnits.LITERS_PER_KILOLITER`), `SRC\config\CCBMachines.java:118` (80 kL) |
| Multiblock volume = blocks × per-block volume | `SRC\content\airtights\airtighttank\AirtightTankStorageController.java:37` |
| Pump: +4 atm boost, 4000 GU/t | `SRC\config\CCBMachines.java:82-83`; applied at `SRC\content\airtights\airtightpump\AirtightPumpTransportBehaviour.java:51-54` |
| Regulator pump: 8 atm rise, 4000 GU/t, active exhaust | `SRC\config\CCBMachines.java:92-93`; `SRC\content\airtights\airtightregulatorpump\AirtightRegulatorPumpExhaust.java:22,62-84` |
| Boiler steam outlet produces steam into the network | `SRC\content\airtights\boilersteamoutlet\SteamOutletGasHandler.java:18` (`implements GasStorageHandler, GasPressureCompartment`) |
| Gas injection chamber: 10 kL, HARD ceiling, UP-only capability | `SRC\config\CCBMachines.java:156`; `GasInjectionChamberBlockEntity.java:113` (`SmartGasTankBehaviour.single(this, ...gasVolume * 1000, HARD_PRESSURE_PA)`), `:87-89` (UP only) |
| Reactor kettle: 90 kL/tank, 3 input + 2 output tanks | `SRC\config\CCBMachines.java:109`; `AirtightReactorKettleBlockEntity.java:515-516` |
| Reactor kettle gas port exposes `input.getTanks() + output.getTanks()` (= 3 + 2 = 5) compartments and cross-tank fill/drain policy | `SRC\content\airtights\airtightreactorkettle\AirtightReactorKettleGasPortHandler.java:83-85` (`getTanks`), `:122-236` (`PortPressureCompartment`: refuses a gas already present in the matching tank, shared `getPredictionGroup()`) |
| Fractionation tower: 90 kL/tank | `SRC\config\CCBMachines.java:64`; `AirtightFractionationTowerInventory.java:302` |
| Forging press: 30 kL input tank | `SRC\config\CCBMachines.java:47`; `AirtightForgingPressBlockEntity.java:166` |
| Breeze chamber: 40 kL, 5120 GU/s, gas conversion gated by ambient pressure | `SRC\config\CCBMachines.java:129-130`; `BreezeChamberConversionPlanner.java:31-60` |
| Tesla turbine / nozzle consume pressure (`GasPressureBoundary`, fill-only) | `TeslaTurbineGasHandler.java:16`, `TeslaTurbineNozzleGasHandler.java:13` (its `drain` returns `GasStack.EMPTY` at `:28-35`) |
| Airtight hatch bridges canisters at 20 000 GU/s | `SRC\config\CCBMachines.java:73`; `AirtightHatchBlockEntity.java:55-56` |
| Gas packager family moves gas via balloon items | `GasPackagerGasTransfer.java:24-113`; `GasUnpackagerBlockEntity.java:124-145,190-201` |
| Creative tank/canister are fixed-pressure infinite | `SRC\gas\storage\CreativeGasReservoir.java`; `CreativeAirtightTankPressureBehaviour.java`; `CreativeAirtightTankStorageController.java:22` |

Read-only monitors (not endpoints): `content\airtights\gasfactorygauge`, `gasobserver`, `airtightmeters` (`AirtightManometerBlockEntity.java:88-99` consumes `GasPressureTelemetryTarget` data), `gas\visual\*`.

Other blocks with a gas capability (from the `RegisterCapabilitiesEvent` sweep):

```
content\airtights\airtighthatch\AirtightHatchBlockEntity.java:55-56
content\airtights\airtighttank\HorizontalAirtightTankBlockEntity.java:21-22
content\airtights\airtighttank\AirtightTankBlockEntity.java:79-81
content\airtights\airtightfractionationtower\AirtightFractionationTowerBlockEntity.java:58-61
content\airtights\portablegasinterface\PortableGasInterfaceBlockEntity.java:37-38
content\airtights\creativeairtighttank\CreativeAirtightTankBlockEntity.java:50-51
content\airtights\gascanister\GasCanisterBlockEntity.java:46-47
content\airtights\creativegascanister\CreativeGasCanisterBlockEntity.java:41-42
content\airtights\gasinjectionchamber\GasInjectionChamberBlockEntity.java:87-89
content\airtights\airtightreactorkettle\AirtightReactorKettleBlockEntity.java:124-127
content\airtights\airtightreactorkettle\AirtightReactorKettleStructuralBlockEntity.java:51-54
content\airtights\airtightforgingpress\AirtightForgingPressBlockEntity.java:113
content\airtights\airtightforgingpress\AirtightForgingPressStructuralBlockEntity.java:40
content\airtights\airtightforgingpress\AirtightForgingPressStructuralShaftBlockEntity.java:37-40
content\airtights\boilersteamoutlet\BoilerSteamOutletBlockEntity.java:37-38
content\airtights\teslaturbinenozzle\TeslaTurbineNozzleBlockEntity.java:34-35
content\breezes\breezechamber\BreezeChamberBlockEntity.java:78-79
content\breezes\breezecooler\BreezeCoolerBlockEntity.java:72
compat\functionalstorage\GasDrawerBlockEntity.java:67-70
compat\functionalstorage\ControllerGasHandler.java:34-48
```

Item-level: `content\airtights\gascanister\GasCanisterItem.java:45`, `gascanisterpack\GasCanisterPackItem.java:55`, `creativegascanister\CreativeGasCanisterItem.java:53` register item capabilities; `internal` containers at `content\airtights\gascanister\GasCanisterContainerContents.java:76-81,243-257`.

---

## 6. Jar verification

### 6.1 Class inventory (jar vs source)

`jar tf createcraftedbeginning-0.1.1.jar | grep '^net/ty/createcraftedbeginning/(api|gas|registry/gas)/'` produced every source class in those trees, including the ones this handbook recommends:

```
api.CCBAPI
api.atmosphere.{AtmosphereProvider, AtmosphereProviderChain, AtmosphereProviderRegistry, AtmosphereState}
api.canister.{CanisterCapabilities, GasCanisterContainer, GasCanisterContainer$InjectionMode, GasConsumptionMath}
api.gas.{Gas, GasAction, GasBuilder, GasCapabilities, GasHolder, GasPressure, GasPressureLimits, GasRegistries,
          GasStack, GasTags, GasUnits, GasUsageContext}
api.gas.handler.{GasHandler, GasHandler$AtomicFillResult, GasStorageHandler, GasTransferSnapshot}
api.gas.logistics.{GasInventoryIdentifierProvider, GasInventoryIdentifiers, GasInventoryIdentifiers$Finder}
api.gas.pressure.{GasPressureBoundary, GasPressureCompartment, GasPressureCompartment$PredictedTransferLimits,
                   GasPressureCompartment$PressureModel, GasPressurePrediction, GasPressureTier,
                   GameplayPressureHandlerRegistry, GameplayPressureProfile, GameplayPressureProfileCompoundTags,
                   GameplayPressureProfiles}
gas.storage.{CreativeGasReservoir, GasPressureCompartmentView, GasTank, GasTankLimits, GasTankMutationResult,
             GasTankMutationResult$Status, GasTankState, SmartGasTank}
gas.storage.handler.{CombinedGasStorageHandler, EmptyGasHandler, GasInventorySummary}
gas.transfer.{GasPressureFillService, GasPressureTransferEndpoint, GasPressureTransferResult, GasPressureTransferService}
gas.overpressure.{OverpressureState, OverpressureStressCalculator, PressureRuptureService}
gas.atmosphere.AtmosphereStateResolver
gas.behaviour.{GasTransportBehaviour, GasManipulationBehaviour, OverpressureBehaviour, SmartGasTankBehaviour, GasFilteringBehaviour}
gas.network.{GasConnectionResolver, GasPipeConnection, GasConnectable, GasTransportEdgeProperties, ...}
gas.network.solver.{GasNetworkSimulator, GasNetworkTopology, GasNetworkTopology$Snapshot, ...}
gas.network.solver.endpoint.{GasEndpointDiscovery, GasEndpointPlanner, GasNetworkPressureEndpoint}
gas.network.math.{GasFlowMath, GasPressureTransferMath}
registry.gas.{CCBGases, CCBGasDeferredRegister}
```

No recommended class is absent. No recommended member differs: each `javap` dump below matches the source signature line-for-line (modulo erased annotations and synthetic `lambda$…` methods, which are omitted).

### 6.2 `javap` dumps — storage / registry

```
===== net.ty.createcraftedbeginning.gas.storage.GasTank =====
public class net.ty.createcraftedbeginning.gas.storage.GasTank
        implements net.ty.createcraftedbeginning.api.gas.handler.GasStorageHandler,
                   net.ty.createcraftedbeginning.api.gas.pressure.GasPressureCompartment {
  public net.ty.createcraftedbeginning.gas.storage.GasTank(long);
  public net.ty.createcraftedbeginning.gas.storage.GasTank(long, java.util.function.Predicate<...GasStack>);
  public net.ty.createcraftedbeginning.gas.storage.GasTank(long, long);
  public net.ty.createcraftedbeginning.gas.storage.GasTank(long, long, java.util.function.Predicate<...GasStack>);
  public net.ty.createcraftedbeginning.gas.storage.GasTank(...GasTankLimits);
  public net.ty.createcraftedbeginning.gas.storage.GasTank(...GasTankLimits, java.util.function.Predicate<...GasStack>);
  public ...GasTransferSnapshot captureTransferSnapshot();
  public ...PredictedTransferLimits predictTransferLimits(...GasStack, long);
  public ...GasPressureCompartment getPressureCompartment(int);
  public java.lang.Object getCompartmentIdentity();
  public boolean isGasValid(...GasStack);
  public ...GasStack getGasStack();
  public boolean supportsExactDrainRecovery();
  public long getVolume();  public long getPressurePa();  public long getMaxPressurePa();  public long getMaxAmount();
  public long getStoredAmount();
  public ...GasStack drain(...GasStack, ...GasAction);  public ...GasStack drain(long, ...GasAction);
  public int getTanks();
  public long fill(...GasStack, ...GasAction);
  public ...AtomicFillResult tryFillAtomicallyFromPressure(java.util.List<...GasStack>, long, ...GasAction);
  public ...GasTankLimits getLimits();  public ...GasTankState snapshot();
  public boolean canContain(...GasTankLimits, ...GasStack);
  public ...GasTankMutationResult tryReplaceContents(...GasStack);
  public ...GasTankMutationResult tryReconfigure(...GasTankLimits);
  public ...GasTankMutationResult tryApplyState(...GasTankState);
  public ...GasTankMutationResult read(net.minecraft.core.HolderLookup$Provider, net.minecraft.nbt.CompoundTag);
  public ...GasStack readClampedToLimits(net.minecraft.core.HolderLookup$Provider, net.minecraft.nbt.CompoundTag);
  public net.minecraft.nbt.CompoundTag write(net.minecraft.core.HolderLookup$Provider, net.minecraft.nbt.CompoundTag);
  public boolean isEmpty();  public long getRemainingAmount();
  protected void onStateChanged();
}

===== net.ty.createcraftedbeginning.gas.storage.GasTankLimits =====
public final class ...GasTankLimits extends java.lang.Record {
  private final long volumeLiters;  private final long maxPressurePa;
  public ...GasTankLimits(long, long);
  public static ...GasTankLimits atReferencePressure(long);
  public long maxAmount();
  public long volumeLiters();  public long maxPressurePa();
}

===== net.ty.createcraftedbeginning.gas.storage.SmartGasTank =====
public class ...SmartGasTank extends ...GasTank {
  public ...SmartGasTank(long, long, java.lang.Runnable);
  protected void onStateChanged();
}

===== net.ty.createcraftedbeginning.gas.storage.GasTankState =====
public final class ...GasTankState extends java.lang.Record {
  public ...GasTankState(...GasTankLimits, ...GasStack);
  public ...GasStack contents();  public ...GasTankLimits limits();
}

===== net.ty.createcraftedbeginning.gas.storage.GasTankMutationResult =====
public final class ...GasTankMutationResult extends java.lang.Record {
  public ...GasTankMutationResult(...Status, long);
  public static ...GasTankMutationResult applied();  unchanged();  invalidGas();  exceedsPressureLimit(long);
  public boolean accepted();  public boolean changed();
  public ...GasTankMutationResult requireAccepted();
  public ...Status status();  public long excessAmount();
}
===== ...GasTankMutationResult$Status =====
APPLIED, UNCHANGED, INVALID_GAS, EXCEEDS_PRESSURE_LIMIT

===== net.ty.createcraftedbeginning.registry.gas.CCBGases =====
public class ...CCBGases {
  public static final ...CCBGasDeferredRegister GAS_REGISTER;
  public static final ...GasHolder<...Gas, ...Gas> NATURAL_AIR, ULTRAWARM_AIR, ETHEREAL_AIR,
        ENERGIZED_NATURAL_AIR, ENERGIZED_ULTRAWARM_AIR, ENERGIZED_ETHEREAL_AIR,
        MOIST_AIR, SPORE_AIR, SCULK_AIR, STEAM, CREATIVE_AIR;
  public static final ...GasHolder<...Gas, ...PotionGas> POTION_GAS;
  public ...CCBGases();
  public static void provideLang(java.util.function.BiConsumer<java.lang.String, java.lang.String>);
}

===== net.ty.createcraftedbeginning.registry.gas.CCBGasDeferredRegister =====
public class ...CCBGasDeferredRegister extends net.neoforged.neoforge.registries.DeferredRegister<...Gas> {
  public ...CCBGasDeferredRegister(java.lang.String);
  public <I extends ...Gas> ...GasHolder<...Gas, I> register(java.lang.String, java.util.function.Supplier<? extends I>);
  public <I extends ...Gas> ...GasHolder<...Gas, I> register(java.lang.String, java.util.function.Function<net.minecraft.resources.ResourceLocation, ? extends I>);
  protected <I extends ...Gas> ...GasHolder<...Gas, I> createHolder(
        net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<...Gas>>, net.minecraft.resources.ResourceLocation);
  public ...GasHolder<...Gas, ...Gas> register(java.lang.String, ...GasBuilder);
}
```

### 6.3 `javap` dumps — transfer / overpressure / atmosphere / canister

```
===== net.ty.createcraftedbeginning.gas.transfer.GasPressureFillService =====
public final class ...GasPressureFillService {
  public static boolean fillAllFromFixedPressure(...GasHandler, java.util.List<...GasStack>, long);
  public static boolean fillExactlyFromFixedPressure(...GasHandler, ...GasStack, long);
  public static long getTransferableAmount(...GasPressureTransferEndpoint, ...GasStack, long, long);
}

===== net.ty.createcraftedbeginning.gas.transfer.GasPressureTransferEndpoint =====
public final class ...GasPressureTransferEndpoint {
  public static ...GasPressureTransferEndpoint compartment(...GasPressureCompartment);
  public static ...GasPressureTransferEndpoint storage(...GasStorageHandler, int);
  public static ...GasPressureTransferEndpoint canister(...GasCanisterContainer, int);
  public static ...GasPressureTransferEndpoint pressureBoundary(...GasPressureBoundary);
  public static java.util.Optional<...GasPressureTransferEndpoint> tryHandler(...GasHandler, int);
  public boolean identifiesSameCompartment(...GasPressureTransferEndpoint);
  public ...GasStack getGasStack();
  public ...PressureModel getPressureModel();
  public long getStoredAmount();  public long getVolume();  public long getMaxPressurePa();  public long getMaxAmount();
  public boolean supportsExactDrainRecovery();
  public double getDrainPressurePa(...GasStack);  public double getFillPressurePa(...GasStack);
  public long simulateDrainAmount(...GasStack, long);
  public ...GasStack executeDrain(...GasStack, long);
  public long simulateFillAmount(...GasStack, long, long);
  public long executeFill(...GasStack, long, long);
  public long restoreDrainedGas(...GasStack);
}

===== net.ty.createcraftedbeginning.gas.overpressure.OverpressureStressCalculator =====
public final class ...OverpressureStressCalculator {
  public static final int TICKS_PER_SECOND;            // 20
  public static final int HARD_LIMIT_FAILURE_TICKS;    // 100
  public static final int FULL_RECOVERY_TICKS;         // 300
  public static final int STABILIZATION_GRACE_TICKS;   // 40
  public static final double MIN_STRESS;  public static final double MAX_STRESS;
  public static double normalizedOverpressure(long);
  public static double stressIncreasePerTick(long);
  public static double stressRecoveryPerTick();
  public static double advance(double, long);
  public static double sanitizeStress(double);
}

===== net.ty.createcraftedbeginning.gas.overpressure.OverpressureState =====
public final class ...OverpressureState {
  public static final java.lang.String COMPOUND_KEY_STRESS;   // "OverpressureStress"
  public boolean tick(long);  public double getStress();  public float getStressFraction();
  public boolean setStress(double);
  public int getStabilizationTicksRemaining();  public boolean isStabilizing();
  public boolean isAtFailureThreshold();  public boolean isFailureReady();
  public void restartStabilizationGrace();
  public void readStress(net.minecraft.nbt.CompoundTag);  public void writeStress(net.minecraft.nbt.CompoundTag);
}

===== net.ty.createcraftedbeginning.gas.overpressure.PressureRuptureService =====
public final class ...PressureRuptureService {
  public static void rupture(net.minecraft.world.level.Level, net.minecraft.core.BlockPos, ...GasPressureCompartment);
}

===== net.ty.createcraftedbeginning.gas.atmosphere.AtmosphereStateResolver =====
public final class ...AtmosphereStateResolver {
  public static ...AtmosphereState resolve(net.minecraft.world.level.Level, net.minecraft.core.BlockPos);
  public static long resolvePressurePa(net.minecraft.world.level.Level, net.minecraft.core.BlockPos);
}

===== net.ty.createcraftedbeginning.api.atmosphere.AtmosphereProvider =====
public interface ...AtmosphereProvider {
  public default ...Gas resolveComposition(net.minecraft.world.level.Level, net.minecraft.core.BlockPos);
  public default java.util.OptionalLong resolvePressurePa(net.minecraft.world.level.Level, net.minecraft.core.BlockPos);
}
===== ...AtmosphereProviderRegistry =====
  public static final int DEFAULT_PRIORITY;
  public static void register(net.minecraft.resources.ResourceLocation, ...AtmosphereProvider);
  public static synchronized void register(net.minecraft.resources.ResourceLocation, int, ...AtmosphereProvider);
  public static ...Gas resolveComposition(net.minecraft.world.level.Level, net.minecraft.core.BlockPos);
  public static java.util.OptionalLong resolvePressurePa(net.minecraft.world.level.Level, net.minecraft.core.BlockPos);
===== ...AtmosphereState =====
  public ...AtmosphereState(...Gas, long);  public ...Gas gas();  public long pressurePa();

===== net.ty.createcraftedbeginning.api.canister.GasCanisterContainer =====
public interface ...GasCanisterContainer {
  public static final int NON_EMPTY_PACK, NON_EMPTY_CANISTER, EMPTY_PACK, EMPTY_CANISTER;  // 1, 0, -1, -2
  public abstract boolean isEmpty();  public abstract boolean isFull();
  public abstract boolean isGasValid(int, ...GasStack);
  public abstract ...GasStack drain(int, ...GasStack, ...GasAction);
  public abstract ...GasStack drain(int, long, ...GasAction);
  public abstract ...GasStack getGasInTank(int);
  public abstract int getPriority();  public abstract int getTanks();
  public abstract net.minecraft.world.item.ItemStack getContainer();
  public abstract java.util.List<net.minecraft.world.item.ItemStack> createVirtualItems();
  public default ...InjectionMode getInjectionMode();
  public abstract long fill(int, ...GasStack, ...GasAction);
  public default boolean supportsExactDrainRecovery(int);
  public default long restoreDrainedGas(int, ...GasStack, ...GasAction);
  public abstract long getTankVolume(int);  public abstract long getTankMaxPressurePa(int);
  public default long getTankPressurePa(int);
  public default ...PressureModel getTankPressureModel(int);
  public default long getTankMaxAmount(int);
  public abstract void save();
}
===== net.ty.createcraftedbeginning.api.canister.CanisterCapabilities =====
  public static final net.neoforged.neoforge.capabilities.ItemCapability<...GasCanisterContainer, java.lang.Void> ITEM;
```

### 6.4 AE2 symbols verified in `appliedenergistics2-19.2.20.jar`

```
appeng.api.stacks.AEKey            (abstract; getType, getId, toTag, writeToPacket, getPrimaryKey, dropSecondary,
                                    computeDisplayName, addDrops, hasComponents, wrapForDisplayOrFilter)
appeng.api.stacks.AEKeyType        (ctor (ResourceLocation, Class<? extends AEKey>, Component);
                                    codec(), readFromPacket(...), getAmountPerOperation(), getAmountPerByte(),
                                    getUnitSymbol(), getAmountPerUnit(), REGISTRY_KEY, CODEC, STREAM_CODEC)
appeng.api.stacks.AEKeyTypes       (public static synchronized void register(AEKeyType); get(ResourceLocation); getAll())
appeng.api.stacks.GenericStack     (record (AEKey what, long amount); readBuffer/writeBuffer/readTag/writeTag/wrapInItemStack)
appeng.api.stacks.AEFluidKey       (AMOUNT_BUCKET = 1000, AMOUNT_BLOCK = 1000  --> "1000 per operation" precedent)
appeng.api.storage.MEStorage       (insert/extract(AEKey, long, Actionable, IActionSource), getAvailableStacks(KeyCounter))
appeng.api.storage.StorageCells, IStorageProvider
appeng.api.storage.cells.IBasicCellItem  (getKeyType, getBytes, getBytesPerType, getTotalTypes, getIdleDrain)
```

`javap -p -constants` on `AEFluidKey` printed `AMOUNT_BUCKET = 1000` and `AMOUNT_BLOCK = 1000`, which is the justification for suggesting `getAmountPerOperation() = 1000` (1 kGU) for the gas key type.

---

## 7. Open questions / things not provable from source alone

These are flagged rather than guessed; they are also the residual risks in `CCB_GAS_API.md` §7.5.

1. **`AEKeyType` registration path in 19.2.20.** Both `AEKeyType.REGISTRY_KEY` (a `ResourceKey<Registry<AEKeyType>>`) and `AEKeyTypes.register(AEKeyType)` exist in the jar. Which one AE2 19.2.20 actually consumes at runtime was not determined here; verify at implementation time (try the registry first, fall back to `AEKeyTypes.register`).
2. **No published API contract for the transfer/rollback rules.** `GasHandler.captureTransferSnapshot`'s javadoc (`api\gas\handler\GasHandler.java:14-20`) is the only written hint; the rest (SIMULATE purity, endpoint identity, "fill then roll back") was read out of `GasTransferExecutor` / `GasEndpointPlanner`. Treat as behaviour, not contract.
3. **`gas.*** has no stability annotation** except `@Internal` on `GasFlowCollisionContext`, `GasReleaseFeedback`, `GasPendingTransfer`, `GasEndpointDiscovery` (`grep` over `SRC\gas\`). Public-but-unannotated is not a promise.
4. **Exact ambient pressures per dimension/biome** come from datapack/registry-driven `AtmosphereProvider`s, not from code; `resolvePressurePa` returns `VACUUM_PA` when no provider answers (`AtmosphereStateResolver.java:48`). Do not hardcode 1 atm for the world; query the level.
5. **Rate limits of the solver** (`MAX_DYNAMIC_SUBSTEPS = 16`, quantised equilibrium planner) mean observed throughput is emergent, not documented. Measure in-game before promising numbers.
