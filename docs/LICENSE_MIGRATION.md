# Licence change: LGPL v3.0

Effective date: 2026-11-22

Applied Crafted Energistics' own source code, data files and original assets are now licensed under
**LGPL v3.0**. The standard SPDX metadata for this licence is **`LGPL-3.0-only`**. The previous
`LGPL-3.0-or-later` grant is no longer offered for ACE's original work: recipients may not choose a
later LGPL version for it.

## Scope

- All Java sources, build scripts, tools and original documentation carry
  `SPDX-License-Identifier: LGPL-3.0-only`.
- `gradle.properties` sets `mod_license=LGPL-3.0-only`, which is what the generated
  `neoforge.mods.toml` ships.
- The root and jar-packaged `THIRD_PARTY_NOTICES.md` state the new project licence.

The canonical LGPL-3.0 text in `LICENSE` is unchanged; the "only" choice is expressed by the SPDX
identifier and the project notice, not by editing the licence text.

## Re-release of 0.2.2

The original 0.2.2 release and jar were distributed under the licence that applied when they were
published (`LGPL-3.0-or-later`). On 2026-11-22 the 0.2.2 artifact was rebuilt and re-published with
ACE's own source under **LGPL v3.0** (SPDX: `LGPL-3.0-only`), without changing the version string.

Copies obtained under the earlier 0.2.2 artifact keep the rights granted with that copy; the
re-published artifact and the current source are offered under LGPL v3.0 only. This does not revoke
rights already granted.

## Third-party exceptions

Code or assets adapted from an upstream work keep that upstream work's licence. In particular, any
portion found to be adapted from AE2 or Applied Pneumatics remains under its upstream
LGPL-3.0-or-later terms. Those exceptions are documented in `THIRD_PARTY_NOTICES.md` and do not
relicense ACE's original code.
