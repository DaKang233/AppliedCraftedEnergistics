# Gas Autocrafting Save/Reload Fix

- Date: 2026-11-22
- Status: verified in a clean test instance
- Affected area: `GasKey.getPrimaryKey()` and gas key NBT round-tripping
- Build: `appliedcraftedenergistics-1.21.1-neoforge-0.2.1.jar`
- Build SHA-256 (final): `C8F9464D0DFEEDFCE77791E7E0348DAB6B810047F61CB82FB0500E9F9FB52A7D`
- Test instance: `1.21.1-NeoForge_21.1.267_3`
- Committed locally as `67c1a44`; not pushed.

## 1. Summary

Gas autocrafting worked on the first load of a world, but failed after saving, quitting,
and loading the same world again. The failure surfaced as one of two symptoms:

1. AE2's default planner threw:

   ```text
   java.lang.UnsupportedOperationException:
   Unexpected error in the crafting calculation: can't find created items.

   - Found none of GasKey[createcraftedbeginning:energized_natural_air].
     Remaining request: 1000 of 1000*1.
   - Tried crafting 1 times the pattern ae2:processing_pattern (with patches).
   - Pattern outputs:
     GenericStack[what=GasKey[createcraftedbeginning:energized_natural_air], amount=20000].
   ```

2. The crafting plan screen showed the gas as both requested and available, while still
   declaring the plan incomplete:

   ```text
   合成数量: 20    可用数量: 20
   残缺的合成计划（材料不足）
   ```

The terminal always showed the correct gas amount. The gas disk, creative gas tank, and
pattern provider were all valid. The failure was independent of:

- which world/save was used;
- copying the working instance to another directory;
- adding or removing AE2 addons;
- the presence of Thunderbolt/AE2LT/AE2LTPP;
- the selected crafting planner (AE2 default or Thunderbolt V2).

The root cause was not NBT decoding of the key itself. It was a reference-identity
contract in AE2's `KeyCounter` that `GasKey` did not satisfy.

`AEKey.getPrimaryKey()` must return a **canonical object** for the key's identity.
`GasKey` returned its `ResourceLocation` gas id. After a world reload each deserialized
`GasKey` had a distinct `ResourceLocation` instance with the same value. AE2's
`KeyCounter` indexes variants by the **reference identity** of `getPrimaryKey()`, so the
crafting simulation inserted the pattern output under one `ResourceLocation` reference and
then looked it up under another. Insert and extract therefore used different sub-maps, and
the simulation concluded that the output had never been produced.

The fix is:

```java
@Override
public Object getPrimaryKey() {
    return isKnown() ? getGas() : gasId;
}
```

The registry `Gas` object is canonical for a given gas id, so every reloaded `GasKey` for
that gas shares the same primary-key reference. This mirrors Applied Mekanistics, whose
`MekanismKey.getPrimaryKey()` returns the canonical `Chemical` object.

An additional codec symmetry change was kept as hardening, but the primary-key change is
the actual fix.

## 2. Environment

### Working / test instance

```text
Minecraft 1.21.1
NeoForge 21.1.267
AE2 19.2.20
Applied Crafted Energistics 0.2.1 (test build)
Create: Crafted Beginning 0.1.1
JEI 19.64.0.455
AE2 JEI Integration 1.2.1
Applied Mekanistics 1.6.3
Create 6.0.10
```

The verification instance intentionally contained only the minimum needed for gas
crafting. It did not contain Thunderbolt, AE2LT, AE2LTPP, AdvancedAE, ExtendedAE,
Applied Enhancements, or omnisequence.

### Reproductions that were ruled out

The following were tested and did not explain the failure:

- A brand-new instance with the same active mod list.
- A byte-identical copy of the working instance.
- The working instance after it had been running for a while.
- A new world created after removing later AE2 addons.
- Re-encoding the gas pattern.
- Moving gas from a gas disk to a creative tank, or using both.
- Forcing the AE2 default planner instead of Thunderbolt V2.

The failure reproduced only when the key was loaded back from persisted data, not while
the key remained an in-memory object from the same session.

## 3. Investigation timeline

### 3.1 Initial symptoms in the large modpack

The first observed failures were in a large modpack. Opening the terminal or inventory
crashed with a JEI/`extendedae_plus` mixin error:

```text
MixinPreProcessorException:
extendedae_plus.mixins.json:jei.BookmarkOverlayMixin
ClassNotFoundException: mezz.jei.common.input.handlers.CombinedInputHandler
```

That was a separate mod-version mismatch: JEI 19.64 was paired with `extendedae_plus`
1.6.3, which expected JEI 19.57 internals.

Gas autocrafting in that pack also failed with `can't find created items`. The stack
contained wrappers from Applied Enhancements, omnisequence, and Thunderbolt, so those were
initially suspected. Removing them only changed the failure mode, not the underlying
behavior.

### 3.2 Clean-instance reproduction

A clean NeoForge 21.1.267 instance with only AE2, Create: Crafted Beginning, ACE, and the
JEI/Applied Mekanistics dependencies still reproduced the save/reload failure. A fresh
world's **first** crafting attempt succeeded; after saving and reloading the world, the
same request failed.

This ruled out:

- old world data from the large modpack;
- the large modpack's AE2 addons;
- instance copying or file ordering.

### 3.3 False lead: GasKey codec round-trip

The first fix attempt mirrored Applied Mekanistics' key serialization:

- added `GasKey.CODEC`;
- changed `toTag` to encode through the codec;
- added `GasKey.fromTag(HolderLookup.Provider, CompoundTag)`;
- overrode `GasKeyType.loadKeyFromTag`.

The reasoning was that pattern outputs and cell contents were decoded differently after a
reload. This change was built and installed into `_3`.

Result: the first load worked, but save/reload **still failed**. The codec change was
therefore not sufficient. It remains in the tree as a correctness/robustness improvement,
but it did not address the actual failure.

### 3.4 Comparing with Applied Mekanistics

Applied Mekanistics' source was inspected because it supports Mekanism chemicals in AE2
autocrafting and pattern encoding.

Key observations:

- AM has no custom pattern encoder and no custom crafting-plan class or mixin.
- Its relevant classes are limited to:
  - `MekanismKey`
  - `MekanismKeyType`
  - `ChemicalStorageCell extends BasicStorageCell`
  - `ChemicalContainerItemStrategy`
  - import/export/external-storage strategies
  - generic-inventory adapters
- Its only mixins are `CondenserMEStorageMixin` and `MEInventoryHandlerMixin`, neither
  related to crafting calculation.

This confirmed that AE2's generic-key path is sufficient for chemical autocrafting, and
that ACE should not need special pattern logic either.

The decisive difference was in `MekanismKey.getPrimaryKey()`:

```java
@Override
public Object getPrimaryKey() {
    return stack.getChemical();
}
```

`Chemical` is a canonical registry object. ACE returned `ResourceLocation`, which is not
canonical across NBT deserialization.

### 3.5 Root cause discovery

AE2's `KeyCounter` implementation uses:

```java
private final Reference2ObjectMap<Object, VariantCounter> lists;
```

`getSubIndex()` and `get()` use `key.getPrimaryKey()` as a **reference map key**:

```java
lists.computeIfAbsent(key.getPrimaryKey(), ...)
lists.get(key.getPrimaryKey())
```

This is an intentional identity contract: the primary key object must be the same object
for every equal key.

Before reload:

- the pattern output `GasKey` and the requested `GasKey` may share the same
  `ResourceLocation` instance or otherwise hit the same primary-key reference;
- `insert` and `extract` reach the same `VariantCounter`.

After reload:

- the pattern output key is decoded from NBT and receives a new `ResourceLocation`;
- the requested key is another decoded key with another new `ResourceLocation`;
- their values are equal, but their references differ;
- `insert` writes to one reference's sub-map;
- `extract` queries a different reference's sub-map and returns `0`;
- `CraftingTreeNode` throws `can't find created items`.

This is exactly the observed "first load works, reload fails" behavior.

## 4. Fix

### 4.1 Primary fix

File: `src/main/java/com/appliedcraftedenergistics/gas/GasKey.java`

```java
@Override
public Object getPrimaryKey() {
    // AE2's KeyCounter indexes variants by *reference identity* of the primary key.
    // Returning the deserialized ResourceLocation would split one gas into multiple
    // entries after a save/load (each decoded key has a new ResourceLocation instance),
    // making crafting simulation insert and extract different sub-maps. The registry
    // Gas object is canonical, the same way Applied Mekanistics returns its Chemical.
    return isKnown() ? getGas() : gasId;
}
```

For known gases, `getGas()` returns the registry `Gas` instance, which is canonical for
that gas id. For unknown gas ids, the original `ResourceLocation` is retained as a
conservative fallback.

### 4.2 Hardening: symmetric key codec

File: `src/main/java/com/appliedcraftedenergistics/gas/GasKey.java`

```java
public static final Codec<GasKey> CODEC = MAP_CODEC.codec();

@Override
public CompoundTag toTag(HolderLookup.Provider registries) {
    var ops = registries.createSerializationContext(NbtOps.INSTANCE);
    return (CompoundTag) CODEC.encodeStart(ops, this).getOrThrow();
}

@Nullable
public static GasKey fromTag(HolderLookup.Provider registries, CompoundTag tag) {
    var ops = registries.createSerializationContext(NbtOps.INSTANCE);
    try {
        return CODEC.decode(ops, tag).getOrThrow().getFirst();
    } catch (Exception e) {
        AppliedCraftedEnergistics.LOGGER.debug("Failed to decode gas key from NBT: {}", tag, e);
        return null;
    }
}
```

File: `src/main/java/com/appliedcraftedenergistics/gas/GasKeyType.java`

```java
@Override
public AEKey loadKeyFromTag(HolderLookup.Provider registries, CompoundTag tag) {
    return GasKey.fromTag(registries, tag);
}
```

This mirrors Applied Mekanistics' `MekanismKey` / `MekanismKeyType` structure and makes
the key's NBT encode/decode paths explicit and symmetric.

## 5. Verification

The fixed build was installed only into the `_3` test instance. The normal instance and
other copies were left untouched.

### Build

```text
BUILD SUCCESSFUL
jar:    appliedcraftedenergistics-1.21.1-neoforge-0.2.1.jar
size:   263602 bytes
sha256: C8F9464D0DFEEDFCE77791E7E0348DAB6B810047F61CB82FB0500E9F9FB52A7D
```

### Test procedure

1. Start `_3` with the fixed jar.
2. Create a fresh world and build the minimal gas crafting setup.
3. Encode a gas pattern and request a craft on the first load.
4. Confirm it succeeds.
5. Save and quit the world.
6. Reload the same world.
7. Request the same craft again.

### Result

Confirmed by the user:

- first-load crafting succeeds;
- after saving and reloading the same save, crafting also succeeds;
- loading the previously broken save directly with the fixed jar works.

The previous failure message `Found none of GasKey[...]` / `can't find created items`
no longer appears after reload.

## 6. Files changed

Modified:

```text
src/main/java/com/appliedcraftedenergistics/gas/GasKey.java
src/main/java/com/appliedcraftedenergistics/gas/GasKeyType.java
```

`GasKey`:

- added `Codec<GasKey> CODEC`;
- `toTag` now uses the codec;
- added `fromTag`;
- `getPrimaryKey()` now returns the canonical registry `Gas` object for known gases.

`GasKeyType`:

- added `loadKeyFromTag` delegating to `GasKey.fromTag`.

No AE2 source files were modified. No AE2 mixins were added.

## 7. Test artifact installation

Fixed jar:

```text
E:\PCL启动器\.minecraft\versions\1.21.1-NeoForge_21.1.267_3\mods\
  appliedcraftedenergistics-1.21.1-neoforge-0.2.1.jar
```

Old jars in the same test instance were preserved as:

```text
appliedcraftedenergistics-1.21.1-neoforge-0.2.1.jar.disabled
appliedcraftedenergistics-1.21.1-neoforge-0.2.1.jar.prev
```

These are test-instance backups and are not part of the repository.

## 8. Remaining work

- Sync the fixed jar to the normal `_2` instance and any other maintained instances only
  after explicit confirmation.
- Add a regression test for `GasKey` identity semantics if a test harness becomes
  available:
  - encode GasKey -> NBT -> decode;
  - verify `getPrimaryKey()` references are identical for equal keys;
  - verify a `KeyCounter` insert/extract round-trip.
- Consider canonicalizing the fallback for unknown gas ids. Unknown ids are only produced
  by missing gas mods/legacy data; the current fallback preserves prior behavior.
- Clean up the test-instance `.disabled` / `.prev` jars after verification.
- The fix is committed locally as `67c1a44`; do not push or publish without explicit
  user permission.

## 9. References

AE2 source (local checkout):

- `appeng.api.stacks.KeyCounter`
  - `Reference2ObjectMap<Object, VariantCounter> lists`
  - `getPrimaryKey()` used as reference index.
- `appeng.crafting.CraftingTreeNode`
  - insert/extract failure path that produces `can't find created items`.
- `appeng.crafting.inv.CraftingSimulationState`
  - `modifiableCache.add(...)` / `modifiableCache.get(...)`.

Applied Mekanistics source (local checkout):

- `me.ramidzkh.mekae2.ae2.MekanismKey`
  - uses `ChemicalStack` and returns canonical `Chemical` from `getPrimaryKey()`.
- `me.ramidzkh.mekae2.ae2.MekanismKeyType`
  - uses explicit `loadKeyFromTag` and codec-based NBT.
- `me.ramidzkh.mekae2.AppliedMekanistics`
  - key type, container strategy, generic slot capacity and storage strategy
    registration, with no custom pattern/crafting implementation.
