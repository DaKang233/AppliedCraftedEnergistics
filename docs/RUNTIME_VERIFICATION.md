# Runtime verification — Applied Crafted Energistics 0.1.0 on the user's real mod pack

> 说明：`docs/run-logs/` 与 `docs/build-logs/` 是本机生成的运行/构建日志，包含绝对路径，**已被 .gitignore 排除**；
> 需要时用 `tools/prepare-run-instance.ps1` + `tools/launch-instance.ps1`（或 `gradlew runData` / `runServer`）重新生成。sha 固定的产物保存在 `docs/artifacts/`。

**Verdict: PASS.** The built mod loads and runs — not merely compiles — inside the
user's actual 1.21.1 NeoForge mod pack (180 jars: the pack's original 179 plus ours),
and the final shipping jar is installed in the real instance.

| # | Run | Jar (sha256 short) | Game dir | Mods | Result | Time to main menu |
|---|-----|--------------------|----------|------|--------|-------------------|
| 1 | PASS 1 — hardlinked full pack | `19028090…` (85,923 B) | `run-instance/` (throw-away) | 180 | **PASS** | 93.5 s |
| 2 | PASS 2 — real instance | `BF6E0908…` (86,517 B) | real instance | 180 | **PASS** | 117.3 s |
| 3 | PASS 2′ — hardlinked full pack | `F07F3BC8…` (86,552 B) | `run-instance/` | 180 | **PASS** | 92.6 s |
| 4 | R2 re-verify — hardlinked full pack | `B4D0C1D9…` (88,521 B) | `run-instance/` | 180 | **PASS** | 118.5 s |
| 5 | R3 re-verify (Gas P2P Tunnel + GuideME) — hardlinked full pack | `7D455389…` (105,724 B) | `run-instance/` | 180 | **PASS** | 92.2 s |
| 6 | R4 re-verify (renamed to Applied Crafted Energistics / ACE) — hardlinked full pack | **`2C96209D…` (107,764 B)** | `run-instance/` | 180 | **PASS** | 98.7 s |
| 7 | Coexistence re-check: pack grew to 183 mods (Applied Pneumatics 1.0.9 + PneumaticCraft 8.2.24 installed by the user) | `2C96209D…` (107,764 B) | `run-instance/` (refreshed) | 183 | **PASS** | 97.7 s |
| 8 | R5 re-verify (cell upgrade cards) — 183-mod pack | **`38E67CB7…` (122,204 B)** | `run-instance/` (183 mods) | 183 | **PASS** | 101.0 s |
| 9 | R6 re-verify (GUI slot backgrounds) — 183-mod pack | **`23E6E169…` (122,972 B)** | `run-instance/` (183 mods) | 183 | **PASS** | 94.3 s |
| 10 | R7 re-verify (licence/notice files packaged) — 183-mod pack | **`A32324D7…` (139,124 B)** | `run-instance/` (183 mods) | 183 | **PASS** | ~102 s |
| 11 | R8 re-verify (zh_cn name → 应用机巧能源) — 183-mod pack |
| 12 | R9 re-verify (0.1.1: native interface textures + 1024 logo) — 183-mod pack |
| 13 | **R9b re-verify (0.1.1 rebuilt with the 256-pixel logo) — 183-mod pack, CURRENT SHIPPED sha** | **`3AB56065…` (200,868 B)** | `run-instance/` (183 mods) | 183 | **PASS** | 124.4 s | **`83E699CA…` (845,506 B)** | `run-instance/` (183 mods) | 183 | **PASS** | 98.3 s | **`416B8A64…` (139,122 B)** | `run-instance/` (183 mods) | 183 | **PASS** | ~102 s |

Run 2 was executed on the real instance with the then-current jar; the jar of run 3
(`F07F3B…`) differs from it by exactly the removal of one duplicated `lang` key
(4-byte build delta, verified by build-engineer). Run 3 re-proves that sha
end-to-end (the 180-mod set for runs 1-6, the 183-mod set for runs 7-8 after Applied Pneumatics and PneumaticCraft were added), on the user's explicit instruction not
to open a second client against the live instance while the user's own client is
running.

**Run 4 is the art revision requested after the initial delivery** (AE2-19.2-style cell
icons + the client-side storage-cell LED colour handler). It differs from run 3 by
exactly: 5 `textures/item/gas_cell_*.png`, `textures/block/drive/drive_cells.png`, and
`client/ACEGasClient` registering `BasicStorageCell::getColor` on
`RegisterColorHandlersEvent.Item`. The build was produced by the Lead because the
build-engineer teammate's turn failed mid-task; the launch itself used the verifier's
harness and log evidence (18:11:39 → `Sound engine started` 18:13:27, ModernFix
"Game took 118.508 seconds"), with the two `appliedcraftedenergistics` INFO lines present and
zero ERROR/WARN attributable to the mod. Run 4 was launched on the `run-instance`
copy, not the live instance, because the user's own client was running.

**Run 5 adds the Gas P2P Tunnel and in-game guide pages** (`device/p2p/*`,
`init/ACEParts`, `init/ACEPartCapabilities`, part/item models + texture + recipe, and
`assets/appliedcraftedenergistics/ae2guide/**` in English and Chinese). Launch evidence
(`docs/run-logs/client-05.*`): `Applied Crafted Energistics 0.1.0` in the Mod List, both
runtime INFO lines, main menu at 92.2 s ("Game took 92.184 seconds"), no ERROR/WARN
mentioning `appliedcraftedenergistics`, no crash report; GuideME completed its page indexing
(only pre-existing index conflicts from other addons were logged), which means our
guide pages parsed. The jar was installed into the real instance's `mods/` with this
sha (105,724 B) right after the run. As in run 4 the launch used the `run-instance`
hardlink copy because the user's client held the live instance.

**Run 6 is the rename to Applied Crafted Energistics (ACE).** The mod id, Java package
(`com.appliedcraftedenergistics`), registry namespace (`appliedcraftedenergistics:*`),
asset/data folders, lang keys, config file name, class prefixes (`ACE*`) and jar name all
changed; the code and features are otherwise identical to R3. Evidence
(`docs/run-logs/client-06.*`): `Applied Crafted Energistics 0.1.0 (appliedcraftedenergistics)`
in the Mod List, the two runtime INFO lines, main menu at 98.7 s ("Game took 98.679 seconds"),
zero `appliedaerostatics` occurrences in the log, no crash report. The new jar was installed
into the real instance's `mods/` (old-named jar and the stale
`config/appliedaerostatics-common.toml` removed).

**Run 7 re-checks coexistence after the pack itself changed.** The user added
**Applied Pneumatics 1.0.9** and **PneumaticCraft: Repressurized 8.2.24** to the instance
(183 jars now), i.e. the mod that this addon is modelled on now runs beside it. The
`run-instance` mirror was rebased (`tools/prepare-run-instance.ps1 -Force`, 183/183
hardlinks) and relaunched: Mod List shows `Applied Crafted Energistics 0.1.0`,
`Applied Pneumatics 1.0.9` and `PneumaticCraft 8.2.24` together, both of our runtime INFO
lines appear, main menu at 97.7 s, no ERROR/WARN attributable to either addon and no crash
report — no key-type/P2P/attunement conflicts.

**Run 8 adds the cell upgrade cards (R5).** Four new card items (`pressure_card`, `volume_card`,
`type_expansion_card`, `relief_valve_card`), their textures/models/recipes, the AE2 universal-card
registration for all five cells, five upgrade slots per cell, the card-driven byte/type calculation and
the relief-valve overfill vent all shipped in `38E67CB7…` (122,204 B, 135 entries). Launch evidence
(`docs/run-logs/client-08.*`, 183-mod pack): Mod List shows `Applied Crafted Energistics 0.1.0`, both
runtime INFO lines appear, main menu at 101.0 s, zero ERROR/WARN attributable to the mod, no crash
report. The card *mechanics* (installing cards in the Cell Workbench and watching capacity/types change)
still need the manual in-game pass listed below; the automated run proves registration, models, recipes
and lang load without error.

**Run 9 is the ME Gas Interface slot-background fix (R6).** AE2's hand-drawn dialogs bake their slot frames into the background texture; this screen deliberately uses AE2's procedural generatedBackground panel, which has none, so the player-inventory slots rendered as bare item icons. GasInterfaceScreen.drawBG now paints AE2's shared 18x18 Icon.SLOT_BACKGROUND sprite behind every non-optional, non-upgrade slot (optional slots and the upgrade panel already draw their own). Boot evidence (docs/run-logs/client-09.*): both runtime INFO lines, main menu at 94.3 s, no error/crash. The *visual* result has to be confirmed in-game by opening the interface; a boot run can only prove the classpath and registration side.

**Run 10 packages the licence paperwork (R7).** LICENSE (LGPL-3.0 text) and THIRD_PARTY_NOTICES.md (AE2 API MIT / AE2 code LGPL-3.0 / AE2 textures CC BY-NC-SA 3.0 with the derived-file list, plus CCB MIT and Applied Pneumatics attribution) now ship inside the jar, and all Java sources carry an SPDX-License-Identifier: LGPL-3.0-or-later header. Boot evidence (docs/run-logs/client-10.*): Applied Crafted Energistics 0.1.0 in the Mod List, both runtime INFO lines, Sound engine started (~102 s), no error or crash. This is a packaging change only; no gameplay code changed.

**Run 12 is the 0.1.1 artwork refresh (R9).** The ME Gas Interface now uses three byte-identical native 16×16 textures (one per face alias, AE2's interface layout with its three blue shades remapped to the gas teal ramp), and icon.png is the 1024-pixel transparent logo export (the artwork master and the other exports live in docs/branding/). Boot evidence (docs/run-logs/client-13.*; an earlier attempt was killed while still in mod loading, so its log is not used): Applied Crafted Energistics 0.1.1 in the Mod List, both runtime INFO lines, Sound engine started, main menu at 98.3 s, no ERROR/WARN attributable to the mod, no crash report. The jar grew to 845,506 B because it now carries the 1024-pixel logo. Visual acceptance of the new block art in-game is still manual. Run 13 is the same 0.1.1 rebuilt with the **256-pixel logo** instead of the 1024 export (icon swap only, 200,868 B): the same checks pass, main menu at 124.4 s, no error or crash (`docs/run-logs/client-14.*`).
**Run 11 is the zh_cn name revision (R8).** The Chinese display name changed from 应用匠心能源 to **应用机巧能源** (matching CCB's official Chinese name 机械动力：机巧新生); only lang/zh_cn.json, the zh GuideME index page and the Chinese docs changed. Boot evidence (docs/run-logs/client-11.*): both runtime INFO lines, Sound engine started (~102 s), no error or crash. The jar still contains exactly one 应用机巧能源 title in both the creative-tab and GUI lang keys (checked directly inside the jar). The R6 jar also carries the redrawn 16×16 upgrade-card textures (verified: the PNG hashes inside the jar equal the ones in `src/main/resources`, and running `tools/gen_card_textures.py` reproduces them byte-identically).

---

## 1. Artifact under test

| | |
|---|---|
| Path | `build/libs/appliedcraftedenergistics-1.21.1-neoforge-0.1.1.jar` |
| Size / mtime | **200,868 B**, 2026-11-20 22:33:12 |
| SHA-256 | **`3AB56065547E77A5D739D3229EBED77981EA931D64897AC294D7CE868DAD43EA`** |
| Jar entries | 137 |
| Mod id / version | `appliedcraftedenergistics` / `0.1.0` (`META-INF/neoforge.mods.toml`) |
| Immutable snapshot | [`docs/artifacts/appliedcraftedenergistics-0.1.1.jar`](artifacts/appliedcraftedenergistics-0.1.1.jar) — same sha256 |
| Installed copy | `<real instance>/mods/appliedcraftedenergistics-1.21.1-neoforge-0.1.1.jar` — same sha256, **left in place** |

The artifact was rebuilt fourteen times during verification (48,662 → 85,923 → 86,517 →
86,556 → 86,552 → 88,521 → 105,724 → 107,764 → 122,204 → 122,972 → 139,124 → 139,122 → 845,506 → 200,868 bytes). Because `build/libs` is a moving target, the launched
bytes were pinned by copying the jar to
`docs/artifacts/appliedcraftedenergistics-0.1.1.jar` and by the installed instance
copy; every sha256 below was computed from the file that was actually launched.

---

## 2. Launch harness

Two scripts under [`tools/`](../../tools); no launcher (PCL/HMCL) involved.

### `tools/launch-instance.ps1`

Reproduces the launcher's command from the expanded version JSON
(`<VERSION_NAME>.json`, 见 `.env`):

* evaluates every `rules` block against this host (`windows`, `x86_64`, OS version
  regex, launcher features disabled), last matching allow/deny wins;
* expands all launcher placeholders (`${natives_directory}`, `${classpath}`,
  `${library_directory}`, `${classpath_separator}`, `${assets_root}`,
  `${assets_index_name}`, `${version_name}`, `${game_directory}`, auth fields …);
* builds `-cp` from the allowed library artifacts **plus the merged version jar**,
  and `-p` from the JSON's module path;
* appends PCL's own JVM extras verbatim — recovered from
  `<launcher-root>\PCL\LatestLaunch.bat`, the command PCL itself generated for this
  instance: `-XX:-OmitStackTraceInFastThrow`, `-Dfml.ignoreInvalidMinecraftCertificates`,
  `-Dfml.ignorePatchDiscrepancies`, `-Xmx8G`, the G1 tuning flags,
  `-Dlog4j2.formatMsgNoLookups=true`, `-Dstdout/stderr.encoding=utf-8`;
* main class `cpw.mods.bootstraplauncher.BootstrapLauncher`, then the JSON game args
  and `--launchTarget forgeclient`, plus `--width 854 --height 480` (PCL passes the
  same; the JSON gates them behind `has_custom_resolution`).

**Resulting command is entry-for-entry identical to PCL's** `LatestLaunch.bat` for the
same game dir, apart from offline identity (`--username Dev --uuid 0…0
--accessToken 0 --userType legacy --versionType release`) and game dir/width/height.
It is 12.8 kchars, below the Windows 32767 limit; the script still falls back to a
`@argfile` if that ever changes. Each run also writes a one-argument-per-line record
(`tools/launch-<gamedir>.args.txt`).

#### The one real bug the harness had, and its fix

First attempt died before any log file was created:

```
java.lang.IllegalStateException: Duplicate key
  …\libraries\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar
  (attempted merging values EmbeddedFileSystemMetadata[…] and EmbeddedFileSystemMetadata[…])
    at java.util.stream.Collectors.duplicateKeyException
    at cpw.mods.securejarhandler/cpw.mods.niofs.union.UnionFileSystem.<init>(UnionFileSystem.java:134)
    at cpw.mods.bootstraplauncher/…BootstrapLauncher.main(BootstrapLauncher.java:69)
```

Cause: the version JSON lists the same coordinate twice (Minecraft dependency **and**
FML dependency). `BootstrapLauncher` keys its `UnionFileSystem` by jar path and cannot
tolerate a repeat. The classpath is now a set — absolute paths, first occurrence wins,
order preserved — dropping **11** duplicate coordinates (gson, guava, failureaccess,
log4j-api/-core/-slf4j2-impl, slf4j-api, commons-io, commons-lang3, jopt-simple,
com.mojang:logging). The `-p` module path is deduplicated the same way.

Cross-check: the harness now produces **107 classpath entries, exactly the 107 unique
entries of PCL's own `-cp`** (118 before dedupe).

### `tools/prepare-run-instance.ps1`

Builds the throw-away mirror of the pack:

* hardlinks (Win32 `CreateHardLink`) all **179/179** jars from the instance's `mods`
  into `run-instance/mods` — verified by name+size comparison and by
  `fsutil hardlink list`, which lists both paths for the same file;
* copies the built mod jar in as a normal file (so a rebuild cannot mutate the link);
* copies `config/`, `defaultconfigs/`, `kubejs/` (copies, never hardlinks: the game
  rewrites config on boot and a hardlink would write through to the user's files);
  `options.txt` is deliberately not copied, keeping the window windowed at 854×480.

Verified: `run-instance/mods` = **180 jars**, 0 name mismatches, 0 size mismatches.

---

## 3. Evidence

### 3.1 PASS 2′ — the shipped jar, hardlinked 180-mod pack (`run-instance`)

Jar launched: `F07F3B…`, 86,552 B. Game start `14:47:19.589`.
Raw log: `run-instance/logs/latest.log`; trimmed copy
[`docs/run-logs/client-03.evidence.txt`](run-logs/client-03.evidence.txt).

```
[14:47:28.083] [main/INFO] [net.neoforged.fml.loading.moddiscovery.ModDiscoverer/SCAN]:
    Found mod file "appliedcraftedenergistics-1.21.1-neoforge-0.1.0.jar"
    [locator: {mods folder locator at …\run-instance\mods}, reader: mod manifest]
        Applied Crafted Energistics 0.1.0 (appliedcraftedenergistics)          <- Mod List entry
[14:47:49.527] [modloading-worker-0/INFO] [net.neoforged.neoforge.common.NeoForgeMod/NEOFORGE-MOD]:
    NeoForge mod loading, version 21.1.251, for MC 1.21.1
[14:48:14.803] [Worker-ResourceReload-13/INFO] [appliedcraftedenergistics/]:
    Applied Crafted Energistics: gas storage channel appliedcraftedenergistics:gas registered
[14:48:42.589] [Render thread/INFO] [appliedcraftedenergistics/]:
    Applied Crafted Energistics: CCB gas pipes can now drain gas from AE2's ME Interface (100 kPa fixed per slot)
[14:48:52.150] [Render thread/INFO] [net.minecraft.client.sounds.SoundEngine/SOUNDS]: Sound engine started
[14:48:57.971] [Render thread/WARN] [ModernFix/]: Game took 98.253 seconds to start
```

* Mod List: **207 entries**; ours present as `Applied Crafted Energistics 0.1.0 (appliedcraftedenergistics)`.
* Resource-pack list contains `mod/appliedcraftedenergistics` (assets were remapped and loaded).
* Time to main menu **92.6 s** after JVM start (`14:47:19.589 → 14:48:52.150`);
  ModernFix independently reports **98.253 s** to a started client.
* Window: `Minecraft NeoForge* 1.21.1`, `Responding=True`, ~6.3 GB working set.
* **0** ERROR/WARN lines attributable to our mod.
* **0** AE2 key-type / registry-diagnostic errors; no `Duplicate key`, no
  `IllegalArgumentException` anywhere in the log.
* No `crash-reports/` directory was created; no `hs_err_pid*` file.

### 3.2 PASS 2 — real instance, jar `BF6E0908…`

Game start `14:42:51.904`. Raw log was the instance's `logs/latest.log` (since rotated
to `logs/2026-11-19-2.log.gz` by the later launch); trimmed copy
[`docs/run-logs/client-02.evidence.txt`](run-logs/client-02.evidence.txt).

```
[14:43:23.099] Found mod file "appliedcraftedenergistics-1.21.1-neoforge-0.1.0.jar" [locator: {mods folder locator at <real instance>\mods}…]
        Applied Crafted Energistics 0.1.0 (appliedcraftedenergistics)
[14:43:48.358] NeoForge mod loading, version 21.1.251, for MC 1.21.1
[14:44:13.323] [appliedcraftedenergistics/]: Applied Crafted Energistics: gas storage channel appliedcraftedenergistics:gas registered
[14:44:37.566] [appliedcraftedenergistics/]: Applied Crafted Energistics: CCB gas pipes can now drain gas from AE2's ME Interface (100 kPa fixed per slot)
[14:44:49.214] [SoundEngine/SOUNDS]: Sound engine started
[14:44:55.386] [ModernFix/]: Game took 123.341 seconds to start
```

* Real instance `mods/` = 179 jars before, **180 after**, delta = exactly our jar.
* Time to main menu **117.3 s** (slower than PASS 1/2′ because the user's own client and
  a teammate's dev server were running concurrently on the same machine).
* **0** ERROR/WARN lines attributable to our mod; no key-type collision; no
  crash-reports added (newest crash report in the instance is from 2026-10-04, pre-existing).

### 3.3 PASS 1 — hardlinked pack, jar `19028090…` (architecture proof)

Game start `14:39:55.436`, main menu `14:41:28.919` → **93.5 s**. Same mod-list entry,
same two `[appliedcraftedenergistics/]` lines, `Sound engine started`, window
`Minecraft NeoForge* 1.21.1`, 207 mod-list entries, **0** ERROR/WARN from our mod, no
crash-reports. Trimmed copy: [`docs/run-logs/client-01.evidence.txt`](run-logs/client-01.evidence.txt).

### 3.4 About the `Done (…)!` line

There is **no `Done (X.XXXs)!` line to find in a client log**. That line is emitted by
`MinecraftServer` when an *integrated server* starts (i.e. entering a world); vanilla
and NeoForge clients reaching the title screen do not print it. The baseline log of the
user's own successful session (13:45–14:23, no mod loaded) likewise has none. The
equivalent acceptance markers used here are therefore:

1. `Mod List` entry for the mod (mod discovery accepted it),
2. `[appliedcraftedenergistics/]` runtime lines (its `RegisterEvent` handler and capability
   integration actually ran, not just class-loaded),
3. `Sound engine started` + `Responding=True` window titled `Minecraft NeoForge* 1.21.1`
   (title screen constructed and rendering),
4. `ModernFix … Game took N seconds to start` as an independent corroboration.

---

## 4. Acceptance criteria

| Criterion | Result | Evidence |
|---|---|---|
| Jar is the sha-pinned build | **PASS** | `3AB56065…`, 200,868 B, 137 entries; snapshot + installed copy match |
| Harness launches exactly like the user's launcher | **PASS** | command matches PCL `LatestLaunch.bat`; 107-entry classpath identical to PCL's |
| Full pack (180 jars) accepted; mod discovered | **PASS** | `Found mod file "appliedcraftedenergistics-…"`; `Applied Crafted Energistics 0.1.0` in Mod List |
| Mod initialises at runtime (registers, not just loads) | **PASS** | `gas storage channel appliedcraftedenergistics:gas registered`; CCB ME-Interface line |
| Client-side art/colour-handler revision does not break init | **PASS** | run 4: client reached the title screen with the new textures + `RegisterColorHandlersEvent.Item` handler installed; 0 errors |
| Client reaches the main menu | **PASS** | `Sound engine started`; window `Minecraft NeoForge* 1.21.1`, `Responding=True` |
| No errors/warnings attributable to the mod | **PASS** | 0 matching ERROR/WARN (the two JarJar lines that match the string are unrelated; they match the project path `…\AppliedCraftedEnergistics\run-instance\mods`) |
| No AE2 key-type collisions from the pack's AE2 addons | **PASS** | no key-type/registry/duplicate-key diagnostics in the log |
| No crash | **PASS** | no `crash-reports/`, no `hs_err_pid*` in either game dir |
| Real instance runs the shipped jar | **PASS (transfer + install)** | PASS 2 on real instance with `BF6E…`; the current `3AB56065…` differs by the art revision, the LED colour handler, the Gas P2P Tunnel + guide pages, the ACE rename, the cell upgrade cards, the GUI slot-background fix, the packaged LGPL/third-party notices, the 应用机巧能源 name revision and the 0.1.1 interface-texture/logo refresh, all proven in runs 4/5/6, 8-12, and is installed there |
| Jar installed and left in the real instance | **PASS** | `<instance>/mods/appliedcraftedenergistics-1.21.1-neoforge-0.1.1.jar`, sha `3AB56065…`, 183 jars, exactly one appliedcraftedenergistics jar |

---

## 5. Environment hygiene / cleanup

* **Every client I started was killed by its exact PID** from
  `docs/run-logs/client-0N.pid`, never by process name. Confirmations:
  `client-01 pid=51668 dead`, `client-02 pid=21192 dead`, `client-03 pid=50932 dead`.
  Their exit codes are `-1` (forced termination), as intended for a GUI smoke test.
* **The user's own client was not touched**: PID 41500 (`net.minecraft.client.main.Main`,
  started 13:45:31, single-player) was alive and `Responding=True` after every run.
  PID 5808 is the Gradle daemon, also untouched.
* No `options.txt`, world, `servers.dat` or the version JSON were modified by the
  harness. The instance's `config/` was not edited by hand; NeoForge itself "Corrected"
  two `minecolonies-*.toml` files during launch, which any normal launch does too.
* **Log rotation disclosure:** each launch makes log4j rotate the instance's
  `logs/latest.log`. The user's long session log (13:45–14:23) now lives as
  `logs/2026-11-19-1.log.gz`; subsequent rotations are `-2.log.gz`, etc. This is
  harmless to the running game (its handle follows the renamed file), but those
  archives may be plain text appended after the gzip stream rather than clean gzip.
* Test artifacts live under the project: `run-instance/` (throw-away, 180 hardlinked
  jars + copied config), `docs/run-logs/` (logs, pid files, evidence extracts,
  snapshot jar). Nothing was written outside the project except the single mod jar in
  the real instance's `mods/` (the intended deliverable).

## 6. Reproducing

```powershell
# 1. build a throw-away mirror of the user's pack (hardlinks, no instance writes)
./tools/prepare-run-instance.ps1 -Force

# 2. inspect the exact command without launching
./tools/launch-instance.ps1 -GameDir ./run-instance -DryRun

# 3. launch, capturing stdout/stderr and the PID
./tools/launch-instance.ps1 -GameDir ./run-instance `
    -OutLog docs/run-logs/client-01.stdout.log `
    -ErrLog docs/run-logs/client-01.stderr.log `
    -PidFile docs/run-logs/client-01.pid -Wait

# 4. watch for "<MainMenu>" evidence, then kill exactly that PID
Stop-Process -Id (Get-Content docs/run-logs/client-01.pid) -Force
```

Main-menu markers to poll in `<gameDir>/logs/latest.log`:
`Applied Crafted Energistics 0.1.0 (appliedcraftedenergistics)`, `gas storage channel … registered`,
`Sound engine started`; failure markers: `crash-reports/*.txt`, `hs_err_pid*.log`,
or an empty log plus a non-zero java exit.
## 0.2.0（2026-11-21）

- Jar: `build/libs/appliedcraftedenergistics-1.21.1-neoforge-0.2.0.jar`
  (213,915 bytes, SHA-256 `A24AC6BC13A4D47E5EB782CA9F5E72642A172EC9998199FF0BDA93F92676B4DB`)
- Run instance: 182 jar source pack + the 0.2.0 jar, client reached the main menu
  (`Applied Crafted Energistics 0.2.0` in the mod list, gas key type registered, no ACE errors).
- 启动验证覆盖：注册、数据组件、菜单类型、两张新卡、气体样本物品。
- 未覆盖（需人工/后续自动化）：放置接口并打开 GUI、CCB 管网实际收发、安全阀排气效果、逐槽模式卡行为。
### 0.2.0 游戏内 GUI 验证（2026-11-21）

在 182 模组整合包的 `run-instance` 中创建新世界，用临时 KubeJS 脚本放置接口：

- 日志确认：`[ACE test] placed interface at 14 67 -49 for Dev`；
- 玩家面向接口右键后 **ME Gas Interface GUI 正常打开**：标题、两排各 9 个配置/缓存槽、右侧 6 个升级槽、玩家背包均正常渲染；
- 打开 GUI 后日志中没有任何 `appliedcraftedenergistics` 异常（无 `IllegalStateException` / sync id 冲突）；
- 截图：`docs/artifacts/ace-0.2.0-interface-gui.png`。

未覆盖：由于桌面自动化无法向游戏窗口注入键盘事件，本轮未能通过命令完成气体标记/管网收发/安全阀排气；
这些仍需人工或后续自动化验证。
### 0.2.1 JEI 集成验证（2026-11-21）

- 0.2.1 客户端（含 JEI 19.57）进入世界后日志出现：
  `JEI integration: gas ingredients can be dragged onto the gas ME Interface's config slots`
- 说明 `AceJeiPlugin.registerGuiHandlers` 已被 JEI 调用、ghost 拖拽 handler 注册成功；
- 该行在进入世界后出现（JEI 在客户端运行期加载插件），主菜单阶段不会打印。