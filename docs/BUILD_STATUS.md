# BUILD_STATUS

> 说明：`docs/run-logs/` 与 `docs/build-logs/` 是本机生成的运行/构建日志，包含绝对路径，**已被 .gitignore 排除**；
> 需要时用 `tools/prepare-run-instance.ps1` + `tools/launch-instance.ps1`（或 `gradlew runData` / `runServer`）重新生成。sha 固定的产物保存在 `docs/artifacts/`。

Build engineering notes for **Applied Crafted Energistics** (`appliedcraftedenergistics`, NeoForge 1.21.1).
Owner of this file: build-engineer. Everything here was measured on this machine, not assumed.

---

## 1. Versions (all fixed, do not change without updating `neoforge.mods.toml`)

| Thing | Value |
|---|---|
| Gradle | 8.14.3 (wrapper, `-bin`) |
| ModDevGradle (`net.neoforged.moddev`) | 2.0.107 |
| NeoForge | 21.1.251 |
| Minecraft | 1.21.1 |
| Parchment | 1.21.1 / `2024.11.17` |
| Java / toolchain | 21 (`java` on PATH is 21.0.5; `JAVA_HOME` is NOT set) |
| AE2 | 19.2.20 (Maven Central) |
| Create | 6.0.10 (local jar, upstream `6.0.10-280`) |
| Create: Crafted Beginning | 0.1.1 (local jar only) |

Instance metadata expands from `src/main/templates/META-INF/neoforge.mods.toml` via the
`generateModMetadata` task into `build/generated/sources/modMetadata`.

## 2. The working `dependencies { }` block

```groovy
dependencies {
    // AE2 - Maven Central. 'implementation' ALSO puts it on the dev runtime
    // classpath, so no extra localRuntime line is needed.
    implementation "org.appliedenergistics:appliedenergistics2:${ae2_version}"

    // Create - the local jar, which is the same build as
    // com.simibubi.create:create-1.21.1:6.0.10-280 (same Git-Hash ac0c444,
    // same 12536 entries; only MANIFEST timestamp differs). Chosen over the
    // maven coordinate because maven.createmod.net RSTs Gradle's TLS
    // handshake intermittently here. Self-contained: flywheel/ponder/
    // Registrate are nested inside it as META-INF/jarjar jars, which FML's
    // JarJar mounts at runtime.
    implementation files("libs/create-1.21.1-6.0.10.jar")

    // Compile-only views of those nested jars so code can use the Flywheel /
    // Ponder / Registrate APIs. Deliberately NOT on the runtime classpath -
    // JarJar already supplies exactly one copy; a second would be a
    // duplicate-mod crash.
    compileOnly files(
            "libs/flywheel-neoforge-1.21.1-1.0.6.jar",
            "libs/ponder-neoforge-1.0.82+mc1.21.1.jar",
            "libs/Registrate-MC1.21-1.3.0+67.jar"
    )

    // Create: Crafted Beginning - no maven exists, ./libs is the only source.
    implementation files("libs/createcraftedbeginning-${ccb_version}.jar")
    localRuntime files("libs/createcraftedbeginning-${ccb_version}.jar")
}
```

Repositories: `mavenCentral()` plus (for reference only) `https://maven.createmod.net`.
NeoForge/Minecraft artifacts come from the repositories ModDevGradle adds itself.

**Why not `implementation "com.simibubi.create:..."`?** Two independent reasons:
1. Its `.pom` declares `flywheel`, `ponder` and `Registrate` as transitive *runtime* deps, but
   the same three are also nested **inside** the Create jar (`META-INF/jarjar/`, mounted by FML's
   JarJar). Taking the pom's copies double-loads the mods. Using the jar file keeps exactly one copy.
2. It also drags in jei / ftb-chunks / ftb-teams / ftb-library / journeymap / cc-tweaked /
   architectury / curios / vanillin, whose mavens this project does not declare.

`webArchive`-free note: `libs/flywheel-*.jar`, `libs/ponder-*.jar` and `libs/Registrate-*.jar`
were extracted from Create's `META-INF/jarjar/` purely to give `compileOnly` a classpath.
Do not add them to `implementation` — that is the duplicate-mod crash described above.

### How to add a new dependency
- **Maven-hosted mod**: add the repo in `repositories { }`, then
  `implementation "group:artifact:version"` (this covers dev runtime too). If the mod is only
  needed to *load* during dev and must not leak to dependents, use `localRuntime`.
- **Jar-in-`libs` mod**: `implementation files("libs/x.jar")` + `localRuntime files("libs/x.jar")`.
- Then bump the `[[dependencies.${mod_id}]]` block in
  `src/main/templates/META-INF/neoforge.mods.toml` so the packaged mod advertises it.
- Compile against a jar you must not load at runtime: `compileOnly files(...)`.

## 3. Commands that work

Run from the repo root. `.\gradlew.bat` (PowerShell/cmd) or `./gradlew` (bash).

```powershell
# First build on a cold cache (the long one; see timings below)
.\gradlew.bat build --max-workers=1 --console=plain

# Normal rebuilds
.\gradlew.bat build --console=plain
.\gradlew.bat build --stacktrace --console=plain   # on failure

# Data generation -> src/generated/resources
.\gradlew.bat runData --console=plain

# Dev runs (gameDirectory = run/)
.\gradlew.bat runClient --console=plain
.\gradlew.bat runServer --console=plain     # dedicated server, --nogui
.\gradlew.bat runDevLogin --console=plain   # same server, "dev login" = offline mode,
                                           # any username, no Mojang auth; EULA pre-accepted
```

Output jar: `build/libs/appliedcraftedenergistics-1.21.1-neoforge-0.1.1.jar`
(`archivesName = "${mod_id}-${minecraft_version}-neoforge"`, `version = 0.1.0`).

Run tasks registered by ModDevGradle (verified with `gradlew tasks --all`): `runClient`,
`runServer`, `runData`, `runDevLogin`, plus their `prepareRun*` prerequisites.

`--max-workers=1` is only needed when the Gradle cache is cold: it serialises artifact downloads
and thereby dodges the TLS problem in §5. Once artifacts are cached it can be dropped.

### Artifact log (newest first)

| Built | Size | Entries | sha256 | Contents / note |
|---|---|---|---|---|
| 2026-11-20 22:33:12 | 200,868 B | 137 | `3AB56065547E77A5D739D3229EBED77981EA931D64897AC294D7CE868DAD43EA` | **CURRENT / FINAL (0.1.1 / R9b)** — artwork refresh with the **256-pixel logo** as `icon.png` (smaller jar): native 16×16 ME Gas Interface textures (front/side/top aliases byte-identical, AE2 interface layout recoloured to the gas teal ramp), branding exports in `docs/branding/`, harness picks the newest built jar. Verified in run 13 |
| 2026-11-20 22:18:33 | 845,506 B | 137 | `83E699CA368DF7C608DDD97392E8DC3E3DB8F44265E020A5B5A1805ED3CCE661` | superseded — same 0.1.1 art refresh with the 1024-pixel logo (run 12) |
| 2026-11-20 04:43:30 | 139,122 B | 137 | `416B8A64F92E6C178BA3CF38FA101B6BA8BAD7F8B9504253AEE0F447D05B6BD3` | superseded (R8) — zh_cn display name 应用匠心能源 → **应用机巧能源** (CCB official name 机械动力：机巧新生); `lang/zh_cn.json` + zh GuideME index + Chinese docs only. Verified in run 11 |
| 2026-11-20 04:36:14 | 139,124 B | 137 | `A32324D74BBE98D14CAEF8BD6CB6990FD4C5111B182BC5D7EB409241E914A0EE` | superseded (R7) — licence compliance: `LICENSE` (LGPL-3.0) + `THIRD_PARTY_NOTICES.md` packaged in the jar (AE2 API MIT / AE2 code LGPL-3.0 / AE2 textures CC BY-NC-SA 3.0 + derived-file list, CCB MIT, Applied Pneumatics attribution), `SPDX-License-Identifier: LGPL-3.0-only` on all Java sources. No gameplay changes. Verified in run 10 |
| 2026-11-20 04:16:58 | 122,972 B | 135 | `23E6E169803C57EFB03CF65501294B01731B276CBF43591D4AC2B92D73A135A1` | superseded (R6) — ME Gas Interface GUI fix: `GasInterfaceScreen.drawBG` draws AE2's shared `Icon.SLOT_BACKGROUND` sprite behind player-inventory/hotbar slots (the procedural `generatedBackground` panel has no baked slot art). No textures touched. Also carries the **redrawn 16×16 upgrade-card textures** (AE2 advanced-card cyan connector + per-card engravings; reproducible from `tools/gen_card_textures.py`; jar hashes match the source PNGs). Verified in run 9 |
| 2026-11-20 03:25:04 | 122,204 B | 135 | `38E67CB7C0F77A316CD6860BB0F07E16A5141D05E16DECD17D53B1C73D0E8139` | superseded (R5) — gas cell upgrade cards: `ACECards` (pressure/volume/type-expansion/relief-valve) + AE2 universal cards registered for all cells, 5 upgrade slots, card-driven `getBytes`/`getTotalTypes`, relief-valve overfill vent, tooltips, textures/models/recipes, GuideME `upgrades.md` (en + zh_cn). Verified in run 8 |
| 2026-11-20 01:51:12 | 107,764 B | 120 | `2C96209DD7F2D257FE9715987D7BB2FFB9E6B223D3410D693F9558EC7BEC43E2` | superseded (R4) — renamed to **Applied Crafted Energistics** (ACE): mod id/package `appliedcraftedenergistics`, registry namespace, asset/data folders, lang keys, config file, `ACE*` class prefixes, jar name. Features identical to R3. Verified in `docs/RUNTIME_VERIFICATION.md` run 6 |
| 2026-11-19 23:29:42 | 105,724 B | 120 | `7D45538928E1EDD19113D92338913BA6EB0C1F812791617814273CFDB1A66D0D` | superseded (R3): Gas P2P Tunnel (`device/p2p/*`, `ACEParts`, `ACEPartCapabilities`, part/item models, texture, recipe) + GuideME pages (`assets/appliedcraftedenergistics/ae2guide/**`, en + zh_cn). Built by the Lead, exit 0; verified in `docs/RUNTIME_VERIFICATION.md` run 5 |
| 2026-11-19 18:10:33 | 88,521 B | 102 | `B4D0C1D99345992998AC3DE62CFEDB9F94948D34D570382C7932D5D0DE601CCF` | superseded (R2): AE2-19.2-style cell icons + drive sheet + storage-cell LED item colour handler. Built by the Lead (the build-engineer teammate's turn failed mid-task); verified in `docs/RUNTIME_VERIFICATION.md` run 4 |
| 2026-11-19 14:45:37 | 86,552 B | 102 | `F07F3BC88C54AB19C8FDCF9ECFBF52CB6339B35395F5179486F3D27D526F378B` | superseded (R1): lang deduped (24 keys each, `supply_pressure` once) |
| 2026-11-19 14:43:11 | 86,556 B | 102 | `EA12606EAFC7E82DFBBBD47C0EF03445598BC557366C0EBD07A00C95319CBCE2` | superseded: carried the duplicated lang key |
| 2026-11-19 14:41:04 | 86,517 B | 102 | `BF6E0908…` | + device menu/screen polish; this is the jar PASS 2 ran on the real instance |
| 2026-11-19 14:38:56 | 85,923 B | 102 | `1902809083FC1745C6E7A7D2D789F475ED283BAABA9CE73C100C0F58E7CC4B26` | + `AeInterfaceGasCompat`, `device/*` |
| 2026-11-19 14:29:16 | 48,662 B | 74 | — | first green jar; core + cells only (stale) |

### Dedicated server smoke test (`runServer`)

`.\gradlew.bat runServer --max-workers=1` — **booted successfully**, log
`docs/build-logs/runServer-01.log`:

```
[14:42:13] [Server thread/INFO] [minecraft/DedicatedServer]: Done (9.671s)! For help, type "help"
[14:41:58] [modloading-worker-0/INFO] [appliedcraftedenergistics/]: Applied Crafted Energistics: gas storage channel appliedcraftedenergistics:gas registered
[14:41:58] [modloading-sync-worker/INFO] [appliedcraftedenergistics/]: Applied Crafted Energistics: CCB gas pipes can now drain gas from AE2's ME Interface (100 kPa fixed per slot)
```

Mod list identical to `runData` (appliedcraftedenergistics + ae2 + create + createcraftedbeginning +
flywheel + ponder + guideme + sablecompanion). So the **server-authoritative** pieces all
registered cleanly: AE2 key type / gas storage channel, the block entity type, the
`GasCapabilities.BLOCK` provider, the AE grid node host, and the AE Interface capability hook.

**No WARN or ERROR anywhere in the log mentions `appliedcraftedenergistics`.** The 14 warnings present
are all benign and pre-existing: Ponder's dev-only refmap notice, a mixin annotation lookup for
`org.jetbrains.annotations.ApiStatus$ScheduledForRemoval`, vanilla command-argument ambiguities,
two NeoForge `VanillaPackResourcesBuilder` assets-URL notices, a missing `server.properties` on
first boot, and the expected **OFFLINE/INSECURE** warnings (that is what makes `devLogin` a
"dev login" — any username, no Mojang auth).

Stopping it: the server does not exit by itself, so the JVM was force-stopped after `Done`.
Verified afterwards: no java process left holding `fml.modFolders=appliedcraftedenergistics`, and port
25565 was free again. (Select the server JVM by that `fml.modFolders` marker — do **not** kill
java processes by name, a real Minecraft client may be running on this machine.)

### Real-instance verification — **PASS** ([RUNTIME_VERIFICATION.md](RUNTIME_VERIFICATION.md))

Independent of the dev runtime, runtime-verifier launched the built jar inside the user's **real
180-mod pack** (the pack's original 179 jars + ours) and reached the main menu every time. The
full write-up, raw evidence extracts, harness rationale and cleanup audit live in
[RUNTIME_VERIFICATION.md](RUNTIME_VERIFICATION.md); this is the summary.

| # | Run | Jar (sha256 short) | Game dir | Mods | Result | To main menu |
|---|---|---|---|---|---|---|
| 1 | PASS 1 — hardlinked full pack | `19028090…` (85,923 B) | `run-instance/` (throw-away) | 180 | **PASS** | 93.5 s |
| 2 | PASS 2 — **real instance** | `BF6E0908…` (86,517 B) | the user's actual instance | 180 | **PASS** | 117.3 s |
| 3 | PASS 2′ — hardlinked full pack | `F07F3BC8…` (86,552 B) | `run-instance/` | 180 | **PASS** | 92.6 s |
| 4 | R2 re-verify — hardlinked full pack | `B4D0C1D9…` (88,521 B) | `run-instance/` | 180 | **PASS** | 118.5 s |
| 5 | R3 re-verify (Gas P2P Tunnel + GuideME) | `7D455389…` (105,724 B) | `run-instance/` | 180 | **PASS** | 92.2 s |
| 6 | R4 re-verify (renamed to Applied Crafted Energistics / ACE) — hardlinked full pack | **`2C96209D…` (107,764 B)** | `run-instance/` | 180 | **PASS** | 98.7 s |
| 7 | Coexistence re-check after the user installed Applied Pneumatics 1.0.9 + PneumaticCraft 8.2.24 (183 mods) | `2C96209D…` (107,764 B) | `run-instance/` (refreshed) | 183 | **PASS** | 97.7 s |
| 8 | R5 re-verify (cell upgrade cards) — 183-mod pack | **`38E67CB7…` (122,204 B)** | `run-instance/` (183 mods) | 183 | **PASS** | 101.0 s |
| 9 | R6 re-verify (GUI slot backgrounds) — 183-mod pack | **`23E6E169…` (122,972 B)** | `run-instance/` (183 mods) | 183 | **PASS** | 94.3 s |
| 10 | R7 re-verify (licence/notice files) — 183-mod pack | **`A32324D7…` (139,124 B)** | `run-instance/` (183 mods) | 183 | **PASS** | ~102 s |
| 11 | R8 re-verify (zh_cn name) — 183-mod pack | **`416B8A64…` (139,122 B)** | `run-instance/` (183 mods) | 183 | **PASS** | ~102 s |
| 12 | R9 re-verify (0.1.1 interface-texture/logo refresh, 1024 logo) — 183-mod pack |
| 13 | **R9b re-verify (0.1.1 rebuilt with the 256-pixel logo) — 183-mod pack, CURRENT SHIPPED sha** | **`3AB56065…` (200,868 B)** | `run-instance/` (183 mods) | 183 | **PASS** | 124.4 s | **`83E699CA…` (845,506 B)** | `run-instance/` (183 mods) | 183 | **PASS** | 98.3 s |

Those sha256 values are exactly the jars in the artifact table above, so each verdict is tied to a
specific artifact rather than "whatever was in `build/libs` at the time". PASS 2 ran on the real
instance with the then-current `BF6E0908…` jar. Runs 4, 5, 6, 8, 9, 10, 11, 12 and 13 re-prove the current shipped sha
end-to-end (runs 4-6 on the identical 180-mod set; runs 7-8 on the 183-mod set that adds Applied Pneumatics + PneumaticCraft): run 4 = the AE2-19.2-style cell art revision, run 5 = the Gas P2P Tunnel + GuideME pages, run 8 = the cell upgrade cards; both were built by the Lead because the build-engineer teammate's
turns failed, and both were launched on the `run-instance` copy because the user's own client was
running on the live instance.

**Shipping state:** the real instance's `mods/` folder contains **exactly one** appliedcraftedenergistics
jar — `appliedcraftedenergistics-1.21.1-neoforge-0.1.1.jar`, sha `3AB56065…`, 200,868 B — and it was **left
installed** for the user (the old-named `appliedaerostatics-*.jar` and the stale
`config/appliedaerostatics-common.toml` were removed). `docs/artifacts/appliedcraftedenergistics-0.1.1.jar`
is an immutable copy of the identical bytes (same sha256), so `build/libs` can never be mistaken for the
accepted artifact again.

**Launch harness** (both under `tools/`, no PCL/HMCL involvement at launch time):
`tools/launch-instance.ps1` reproduces the launcher's own command — it applies the version JSON's
`rules`, expands every launcher placeholder, and appends PCL's JVM extras recovered from
`LatestLaunch.bat`, producing a command entry-for-entry identical to PCL's apart from offline
identity and window size, with **107 classpath entries exactly matching PCL's 107 unique
entries**. `tools/prepare-run-instance.ps1` builds the throw-away mirror by hardlinking all
179/179 pack jars (plus the built jar as a normal file, and `config/`/`defaultconfigs/`/`kubejs/`
as copies so the game cannot write through into the user's files).

**Known-harmless caveat:** each launch makes log4j rotate the instance's `logs/latest.log`, so the
user's own earlier session log now lives as `logs/2026-11-19-1.log.gz` (then `-2.log.gz`, …). The
running game is unaffected — its handle follows the renamed file — but those archives may be plain
text appended after the gzip stream rather than clean gzip, so don't trust them as parseable gzip.

One clarification worth recording, because it bit the verifier first: **a client log has no
`Done (X.XXXs)!` line.** That line is emitted by `MinecraftServer` for an *integrated* server
(entering a world), which is why it appears in our dedicated-server `runServer` evidence above but
never in the client runs; the client markers are the Mod List entry, the `[appliedcraftedenergistics/]`
runtime lines, `Sound engine started`, and `ModernFix … Game took N seconds to start`.

## 4. Logs

All build output is redirected to `docs/build-logs/`:

| Log | What it was |
|---|---|
| `build-01.log` | Gradle wrapper download — died on `services.gradle.org` TLS + 10 s timeout |
| `build-02.log` | First real attempt — `neoFormRuntimeDependenciesCompileClasspath` TLS reset on `libraries.minecraft.net` |
| `build-03.log` | Same, different artifact (retry properties added) |
| `build-04.log` | `--max-workers=1`: **NFRT succeeded** (148 s), failed on `maven.createmod.net` |
| `build-05.log` | Create switched to the local jar: **deps resolve, `compileJava` runs**; 1 error in `GasKey.java` (missing `@Nullable` import) |

Cold-cache timings observed: Gradle 8.14.3 distribution 137 MB ≈ 30-60 s; NeoForge/Minecraft
`createMinecraftArtifacts` (download + decompile + patch + recompile) **147.8 s**; the rest of the
build a few seconds. A warm rebuild is ~10-15 s.

## 5. Network caveats on this machine (important)

**Symptom:** builds fail with
`javax.net.ssl.SSLHandshakeException: Remote host terminated the handshake` /
`java.io.EOFException: SSL peer shut down incorrectly`.

**What it is NOT:** the hosts are not blocked and not broken. Measured with plain Java
(`java.net.http`), 12 sequential GETs each:
`libraries.minecraft.net` 12/12 HTTP 200, `maven.createmod.net` 12/12 HTTP 200 — including the
exact `lwjgl-glfw` native that killed build-03. `curl` and PowerShell also always succeed.

**What it IS:** only Gradle's bundled Apache HttpClient trips over it, on a *different random
artifact* each run. That is the signature of keep-alive connection reuse against a server that
has closed the idle socket (the EOF arrives on a pooled connection, and Gradle does not retry
`SSLException`). It is intermittent, not deterministic.

**Three mitigations, all applied:**

1. `org.gradle.internal.repository.max.tentatives=10`, `initial.backoff=2000`,
   `http.connectionTimeout/socketTimeout=120000` in `gradle.properties`.
2. `--max-workers=1` for cold builds (serialises downloads). Proven: all 130 Mojang libraries
   downloaded and the whole NeoForm pipeline completed.
3. **BMCLAPI mirror init script** — rewrites any repository whose URL contains
   `libraries.minecraft.net` to `https://bmclapi2.bangbang93.com/maven` (a mirror of the same
   content tree; `logging-1.2.7.jar` is byte-identical, 15343 B on both).
   - Installed at `<home>\.gradle\init.d\mojang-mirror.gradle`
   - Source of truth committed at `docs/init-scripts/mojang-mirror.gradle`
   - **This file lives outside the repo, so a fresh clone on this machine must copy it back:**
     `Copy-Item docs\init-scripts\mojang-mirror.gradle "$env:USERPROFILE\.gradle\init.d\"`
   - Opt out with `NO_MOJANG_MIRROR=1` (e.g. on a network where Mojang is faster than BMCLAPI).
   - BMCLAPI answers with HTTP 302 for artifacts; Gradle follows the redirect.

**Also:** `services.gradle.org` cannot complete a TLS handshake at all here, so
`gradle/wrapper/gradle-wrapper.properties` points at the CDN host it would redirect to,
`https://downloads.gradle.org/distributions/gradle-8.14.3-bin.zip`, with
`networkTimeout=180000`. Revert to `services.gradle.org` on a normal network.

## 6. Dev runtime: does Create: Crafted Beginning load? — **YES**

Verified by `.\gradlew.bat runData --max-workers=1` → **BUILD SUCCESSFUL in 1m 15s, exit 0**
(`docs/build-logs/runData-03.log`).

FML's own Mod List, as printed by the run:

```
Applied Crafted Energistics 0.1.0 (appliedcraftedenergistics)
Applied Energistics 2 19.2.20 (ae2)
Create 6.0.10 (create)
Create Crafted Beginning 0.1.1 (createcraftedbeginning)
Flywheel 1.0.6 (flywheel)          <- from Create's nested META-INF/jarjar
Ponder 1.0.82+mc1.21.1 (ponder)    <- from Create's nested META-INF/jarjar
GuideME 21.1.1 (guideme)           <- AE2's transitive runtime dep
Minecraft 1.21.1 (minecraft)
NeoForge 21.1.251 (neoforge)
Sable Companion 1.6.0 (sablecompanion)
```

`Found 0 mod requirements missing (0 mandatory, 0 optional).`

**CCB's production jar and its mixins apply correctly in dev.** The mixin log shows them being
woven, e.g. `common.minecraft.ServerEntityMixin` into
`net.minecraft.server.level.ServerEntity`, `common.accessor.ServerGamePacketListenerImplAccessor`
into `net.minecraft.server.network.ServerGamePacketListenerImpl`, and
`client.create.StockKeeperRequestScreenMixin` into Create's
`StockKeeperRequestScreen`. A production jar with a mixin config loads fine here because
NeoForge 1.21.1 dev already uses **official Mojang mappings** — no remapping and no refmap
problem. No JEI / kubejs / curios / sable / aeronautics / functionalstorage is needed: those are
optional in CCB's `mods.toml`, and it still loads without them.

So the whole team can rely on `runClient` / `runServer` / `runData` for development; there is no
need to test only against a real instance.

**Gotcha:** datagen currently emits **no** files (`Caching: total files: 0`) because no
`DataProvider` is registered yet. `runData` today proves the runtime, not generated assets; it
must be re-run once a provider exists.

_Note:_ the first `runData` attempt failed in `:downloadAssets` — 6 of 3,896 client assets got
HTTP 400 from `resources.download.minecraft.net` (rate-limiting by NeoForm Runtime's parallel
downloader; the same URL returns 200 / 790,489 B via curl). Simply re-running resumes and
completes; assets land in `~/.gradle/caches/neoformruntime/assets` (789 MiB).

## 7. Ownership notes for the team

- `build-engineer` owns: `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/**`,
  `gradlew*`, `src/main/templates/**`, `src/main/resources/pack.mcmeta`,
  `src/main/resources/icon.png`, `libs/**`, `docs/BUILD_STATUS.md`, `docs/build-logs/**`,
  `docs/init-scripts/**`.
- `src/main/java/**` belongs to the Lead; `src/main/resources/**` assets belong to another
  teammate. Build-engineer does not edit them; compile errors there are reported with exact
  `file:line` + caret output from the log.
