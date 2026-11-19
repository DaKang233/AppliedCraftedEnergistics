#Requires -Version 7.0
# Copyright (c) 2026 DaKang233.
# SPDX-License-Identifier: LGPL-3.0-or-later

<#
.SYNOPSIS
    Build a throw-away game directory that mirrors the user's mod pack, without
    touching the user's instance.

.DESCRIPTION
    1. Creates <Dest>\mods and hardlinks every *.jar from the real instance's mods
       folder into it (same volume required; verified afterwards).
    2. Copies the freshly built mod jar in as appliedcraftedenergistics-1.21.1-neoforge-0.1.0.jar.
    3. Copies the pack's config/, defaultconfigs/ and kubejs/ so the run sees the
       same configuration as the user (copies, never hardlinks: the game rewrites
       config on startup and a hardlink would write through to the real instance).
    4. Verifies the result: file counts, per-file sizes, hardlink identity.

    options.txt is deliberately NOT copied: a fresh one keeps the window at
    --width/--height windowed, avoiding fullscreen/shader settings.

.EXAMPLE
    ./prepare-run-instance.ps1 -VersionName <installed version id>
#>
[CmdletBinding()]
param(
    [string]$MinecraftRoot = '',
    [string]$VersionName   = '',
    [string]$Dest          = '',
    [string]$ModJar        = '',
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot

# Local machine values come from .env (gitignored); see env.example. Explicit parameters win.
. (Join-Path $PSScriptRoot 'env.ps1')
Import-AceEnv (Join-Path $repoRoot '.env')

if (-not $MinecraftRoot) { $MinecraftRoot = $env:MINECRAFT_ROOT }
if (-not $MinecraftRoot) {
    $MinecraftRoot = Join-Path $env:APPDATA '.minecraft'
}
if (-not $VersionName) { $VersionName = $env:VERSION_NAME }
if (-not $VersionName) {
    throw 'Pass -VersionName (or set VERSION_NAME in .env) with the installed version id under <MinecraftRoot>\versions.'
}
if (-not $Dest) { $Dest = $env:GAME_DIR }
if (-not $Dest) {
    $Dest = Join-Path $repoRoot 'run-instance'
}
if (-not $ModJar) {
    $ModJar = Join-Path $repoRoot 'build\libs\appliedcraftedenergistics-1.21.1-neoforge-0.1.0.jar'
}

$instanceDir = Join-Path (Join-Path $MinecraftRoot 'versions') $VersionName
$srcMods     = Join-Path $instanceDir 'mods'
$dstMods     = Join-Path $Dest 'mods'

if (-not (Test-Path -LiteralPath $srcMods)) { throw "source mods folder not found: $srcMods" }
if (-not (Test-Path -LiteralPath $ModJar))  { throw "built mod jar not found: $ModJar" }

if ($Force -and (Test-Path -LiteralPath $Dest)) {
    Write-Host "removing previous $Dest"
    Remove-Item -LiteralPath $Dest -Recurse -Force
}

New-Item -ItemType Directory -Path $dstMods -Force | Out-Null

# --- native hardlink helper -------------------------------------------------
if (-not ('Native.Fs' -as [type])) {
    Add-Type -Namespace Native -Name Fs -MemberDefinition @'
[System.Runtime.InteropServices.DllImport("kernel32.dll", CharSet = System.Runtime.InteropServices.CharSet.Unicode, SetLastError = true)]
public static extern bool CreateHardLink(string lpFileName, string lpExistingFileName, System.IntPtr lpSecurityAttributes);
'@
}

$linked = 0
$linkFailed = [System.Collections.Generic.List[string]]::new()
foreach ($f in Get-ChildItem -LiteralPath $srcMods -Filter *.jar -File) {
    $target = Join-Path $dstMods $f.Name
    if (Test-Path -LiteralPath $target) {
        $linked++
        continue
    }
    $ok = [Native.Fs]::CreateHardLink($target, $f.FullName, [IntPtr]::Zero)
    if ($ok) { $linked++ }
    else {
        $err = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
        $linkFailed.Add(("{0}  (Win32 error {1})" -f $f.Name, $err))
    }
}

# --- our mod jar (a real copy, not a link: it must not be mutated in place) --
Copy-Item -LiteralPath $ModJar -Destination (Join-Path $dstMods (Split-Path -Leaf $ModJar)) -Force

# --- copy pack configuration (copies only!) --------------------------------
$copiedDirs = @()
foreach ($name in @('config', 'defaultconfigs', 'kubejs')) {
    $src = Join-Path $instanceDir $name
    if (Test-Path -LiteralPath $src) {
        Copy-Item -LiteralPath $src -Destination (Join-Path $Dest $name) -Recurse -Force
        $copiedDirs += $name
    }
}

# --- verification -----------------------------------------------------------
$srcJars = @(Get-ChildItem -LiteralPath $srcMods -Filter *.jar -File)
$dstJars = @(Get-ChildItem -LiteralPath $dstMods -Filter *.jar -File)

$nameMismatch = Compare-Object ($srcJars.Name | Sort-Object) ($dstJars.Name | Sort-Object) |
    Where-Object { $_.InputObject -ne (Split-Path -Leaf $ModJar) }
$sizeMismatch = foreach ($s in $srcJars) {
    $d = $dstJars | Where-Object Name -eq $s.Name
    if ($null -eq $d -or $d.Length -ne $s.Length) { "{0}: src={1} dst={2}" -f $s.Name, $s.Length, ($d.Length) }
}

Write-Host '=== prepare-run-instance.ps1 ==='
Write-Host ("test gameDir      : {0}" -f $Dest)
Write-Host ("hardlinked jars   : {0} of {1} source jars" -f $linked, $srcJars.Count)
if ($linkFailed.Count -gt 0) {
    Write-Host 'HARDLINK FAILURES:'
    $linkFailed | ForEach-Object { Write-Host ("  " + $_) }
}
Write-Host ("mods now contains : {0} jar(s)" -f $dstJars.Count)
Write-Host ("our jar           : {0}  ({1:N0} bytes, {2})" -f (Split-Path -Leaf $ModJar), (Get-Item -LiteralPath $ModJar).Length, (Get-Item -LiteralPath $ModJar).LastWriteTime)
Write-Host ("copied dirs       : {0}" -f ($copiedDirs -join ', '))
Write-Host ("name mismatches   : {0}" -f @($nameMismatch).Count)
Write-Host ("size mismatches   : {0}" -f @($sizeMismatch).Count)
if ($sizeMismatch) { $sizeMismatch | ForEach-Object { Write-Host ("  " + $_) } }

# hardlink identity spot-check: the link should report LinkType HardLink and
# fsutil should list both paths for the same file.
$sampleSrc = $srcJars | Select-Object -First 1
$sampleDst = Get-Item -LiteralPath (Join-Path $dstMods $sampleSrc.Name)
Write-Host ("link spot-check   : {0} -> LinkType={1}" -f $sampleDst.Name, $sampleDst.LinkType)
& fsutil hardlink list $sampleDst.FullName | ForEach-Object { Write-Host ("   fsutil: " + $_) }

if ($linkFailed.Count -gt 0 -or @($sizeMismatch).Count -gt 0) { exit 1 }
