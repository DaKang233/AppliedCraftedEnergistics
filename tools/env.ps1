# Copyright (c) 2026 DaKang233.
# SPDX-License-Identifier: LGPL-3.0-or-later

# Loads the repository's local `.env` file into process environment variables.
#
# Called by tools/launch-instance.ps1 and tools/prepare-run-instance.ps1. Values already present in
# the environment are overwritten, so a `.env` line wins over a stale shell variable; explicit
# script parameters still win over both because the scripts resolve defaults after this import.
function Import-AceEnv {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    Get-Content -LiteralPath $Path -Encoding utf8 | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) {
            return
        }
        $separator = $line.IndexOf('=')
        if ($separator -lt 1) {
            return
        }
        $name = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1).Trim()
        if ($value.Length -ge 2 -and (
                ($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        Set-Item -Path "env:$name" -Value $value
    }
}
