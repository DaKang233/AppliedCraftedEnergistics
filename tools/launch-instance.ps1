#Requires -Version 7.0
<#
.SYNOPSIS
    Launch an installed Minecraft version exactly the way
    PCL2 (Plain Craft Launcher 2) launches it, without PCL.

.DESCRIPTION
    Reads the expanded version JSON (the one PCL writes), evaluates every entry's
    "rules" against this machine (windows / x86_64 / no launcher features),
    expands the ${...} placeholders a Minecraft launcher is expected to fill in,
    and starts java.exe with the resulting command line.

    The reference for the "extra" JVM flags below is PCL's own generated script
    (the launcher's own LatestLaunch.bat) for this very instance; they are reproduced
    verbatim (memory, G1 tuning, log4j lookups, stdout encoding) so that the run is
    as close to the user's normal launch as possible. Use -NoPclExtras to omit them.

    Nothing here writes to the instance unless -GameDir points at it; the primary
    use is a throw-away gameDir (run-instance/) whose mods folder is hardlinked
    from the real one.

.PARAMETER GameDir
    Value for --gameDir (logs, options, saves, config are resolved against it).

.PARAMETER DryRun
    Render and validate everything, print the command, do not start java.

.EXAMPLE
    ./launch-instance.ps1 -GameDir <repo>\run-instance -DryRun

.EXAMPLE
    ./launch-instance.ps1 -GameDir <repo>\run-instance `
        -OutLog docs/run-logs/client-01.stdout.log -ErrLog docs/run-logs/client-01.stderr.log `
        -PidFile docs/run-logs/client-01.pid -Wait
#>
[CmdletBinding()]
param(
    [string]$MinecraftRoot = '',
    [string]$VersionName   = '',
    [string]$GameDir       = '',
    [string]$Java          = '',

    # identity handed to the game (offline/legacy is fine for a client smoke test)
    [string]$Username    = 'Dev',
    [string]$Uuid        = '00000000000000000000000000000000',
    [string]$AccessToken = '0',
    [string]$UserType    = 'legacy',
    [string]$VersionType = 'release',
    [string]$ClientId    = '0',
    [string]$Xuid        = '0',

    # window size, as PCL passes --width/--height (JSON gates these behind a feature)
    [int]$Width = 854,
    [int]$Height = 480,

    [string]$Xmx = '8G',
    [switch]$NoPclExtras,

    [switch]$DryRun,
    [switch]$Wait,

    [string]$OutLog,
    [string]$ErrLog,
    [string]$PidFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Local machine values come from .env (gitignored); see env.example. Explicit parameters win.
. (Join-Path $PSScriptRoot 'env.ps1')
Import-AceEnv (Join-Path (Split-Path -Parent $PSScriptRoot) '.env')

# Defaults that are not tied to one machine: the vanilla Minecraft folder and the java on PATH.
if (-not $MinecraftRoot) { $MinecraftRoot = $env:MINECRAFT_ROOT }
if (-not $MinecraftRoot) {
    $MinecraftRoot = Join-Path $env:APPDATA '.minecraft'
}
if (-not $VersionName) { $VersionName = $env:VERSION_NAME }
if (-not $VersionName) {
    throw 'Pass -VersionName (or set VERSION_NAME in .env) with the installed version id under <MinecraftRoot>\versions.'
}
if (-not $Java) { $Java = $env:JAVA }
if (-not $Java) {
    if ($env:JAVA_HOME) {
        $Java = Join-Path $env:JAVA_HOME 'bin\java.exe'
    } else {
        $javaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
        if ($javaCmd) { $Java = $javaCmd.Source }
    }
}
if (-not $Java) {
    throw 'Pass -Java with the path to java.exe (or set JAVA in .env, or set JAVA_HOME / put java.exe on PATH).'
}

# ----------------------------------------------------------------------------
# 0. Locate everything
# ----------------------------------------------------------------------------
$instanceDir = Join-Path (Join-Path $MinecraftRoot 'versions') $VersionName
$jsonPath    = Join-Path $instanceDir "$VersionName.json"
$nativesDir  = Join-Path $instanceDir "$VersionName-natives"
$assetsDir   = Join-Path $MinecraftRoot 'assets'
$libDir      = Join-Path $MinecraftRoot 'libraries'
$versionJar  = Join-Path $instanceDir "$VersionName.jar"

if (-not (Test-Path -LiteralPath $jsonPath))   { throw "version JSON not found: $jsonPath" }
if (-not (Test-Path -LiteralPath $Java))       { throw "java not found: $Java" }
if (-not (Test-Path -LiteralPath $nativesDir)) { throw "natives dir not found: $nativesDir" }
if (-not (Test-Path -LiteralPath $assetsDir))  { throw "assets dir not found: $assetsDir" }
if (-not (Test-Path -LiteralPath $versionJar)) { throw "version jar not found: $versionJar" }
if ([string]::IsNullOrWhiteSpace($GameDir)) { $GameDir = $env:GAME_DIR }
if ([string]::IsNullOrWhiteSpace($GameDir)) { $GameDir = $instanceDir }
if (-not (Test-Path -LiteralPath $GameDir))    { throw "gameDir not found: $GameDir" }

$j = Get-Content -LiteralPath $jsonPath -Raw -Encoding utf8 | ConvertFrom-Json

$osName  = 'windows'
$osArch  = if ([Environment]::Is64BitOperatingSystem) { 'x86_64' } else { 'x86' }
$osVer   = [Environment]::OSVersion.Version.ToString()

# ----------------------------------------------------------------------------
# 1. Launcher "rules" evaluation
#    Semantics: no rules -> allowed. With rules: start disallowed, the LAST
#    matching rule decides (allow/deny). OS version is a regex on the OS version.
#    Feature-gated entries never match: this is an offline launcher.
# ----------------------------------------------------------------------------
function Get-Opt {
    param($Object, [string]$Name)
    if ($null -eq $Object) { return $null }
    $p = $Object.PSObject.Properties[$Name]
    if ($null -eq $p) { return $null }
    return $p.Value
}

function Test-Rules {
    param($Rules)

    if ($null -eq $Rules -or @($Rules).Count -eq 0) { return $true }

    $allowed = $false
    foreach ($r in @($Rules)) {
        $match = $true
        $os = Get-Opt $r 'os'
        if ($null -ne $os) {
            $rn = Get-Opt $os 'name'
            $ra = Get-Opt $os 'arch'
            $rv = Get-Opt $os 'version'
            if ($rn -and $rn -ne $osName) { $match = $false }
            if ($ra) {
                # JSON uses "x86_64" for amd64; be liberal about the spelling
                $want = @($ra)
                $okArch = ($want -contains $osArch) -or
                          ($osArch -eq 'x86_64' -and ($want -contains 'amd64'))
                if (-not $okArch) { $match = $false }
            }
            if ($rv -and -not ($osVer -match $rv)) { $match = $false }
        }
        if ($null -ne (Get-Opt $r 'features')) { $match = $false }  # never demo/quickplay/custom-res
        if ($match) { $allowed = ((Get-Opt $r 'action') -eq 'allow') }
    }
    return $allowed
}

# ----------------------------------------------------------------------------
# 2. Placeholder substitution
# ----------------------------------------------------------------------------
$subs = [ordered]@{
    '${natives_directory}'   = $nativesDir
    '${library_directory}'   = $libDir
    '${classpath_separator}' = [IO.Path]::PathSeparator      # ';' on windows
    '${launcher_name}'       = 'PCL'
    '${launcher_version}'    = '408'
    '${version_name}'        = $VersionName
    '${game_directory}'      = $GameDir
    '${assets_root}'         = $assetsDir
    '${assets_index_name}'   = [string]$j.assetIndex.id
    '${auth_player_name}'    = $Username
    '${auth_uuid}'           = $Uuid
    '${auth_access_token}'   = $AccessToken
    '${clientid}'            = $ClientId
    '${auth_xuid}'           = $Xuid
    '${user_type}'           = $UserType
    '${version_type}'        = $VersionType
    '${resolution_width}'    = [string]$Width
    '${resolution_height}'   = [string]$Height
}

function Expand-Placeholders {
    param([string]$Text, [string]$Classpath)
    $out = $Text
    foreach ($k in $subs.Keys) { $out = $out.Replace($k, $subs[$k]) }
    $out = $out.Replace('${classpath}', $Classpath)
    return $out
}

# ----------------------------------------------------------------------------
# 3. Classpath: allowed library artifacts (in JSON order) + the merged version jar
# ----------------------------------------------------------------------------
$cpEntries = [System.Collections.Generic.List[string]]::new()
$cpSeen    = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$missing   = [System.Collections.Generic.List[string]]::new()
$duplicates = [System.Collections.Generic.List[string]]::new()
$skipped   = 0

foreach ($l in $j.libraries) {
    if (-not (Test-Rules (Get-Opt $l 'rules'))) { $skipped++; continue }
    $artifact = Get-Opt (Get-Opt $l 'downloads') 'artifact'
    if ($null -eq $artifact) { continue }
    $rel = Get-Opt $artifact 'path'
    if ([string]::IsNullOrWhiteSpace($rel)) { continue }
    $full = Join-Path $libDir ($rel -replace '/', '\')
    if (-not (Test-Path -LiteralPath $full)) { $missing.Add($full); continue }
    # The JSON carries the same coordinate more than once (e.g. gson appears as
    # both a Minecraft dependency and a FML dependency). BootstrapLauncher builds
    # a UnionFileSystem keyed by jar path and dies on a duplicate key, so the
    # classpath must be a set: first occurrence wins, order otherwise preserved.
    if (-not $cpSeen.Add($full)) { $duplicates.Add($rel); continue }
    $cpEntries.Add($full)
}
$cpEntries.Add($versionJar)
$classpath = $cpEntries -join ([IO.Path]::PathSeparator)

if ($missing.Count -gt 0) {
    throw ("missing library jar(s):`n  " + ($missing -join "`n  "))
}

# ----------------------------------------------------------------------------
# 4. JVM args from the JSON (rules honoured, placeholders expanded)
# ----------------------------------------------------------------------------
$jvmArgs = [System.Collections.Generic.List[string]]::new()
foreach ($a in $j.arguments.jvm) {
    if ($a -is [string]) {
        $jvmArgs.Add((Expand-Placeholders $a $classpath))
    }
    elseif (Test-Rules (Get-Opt $a 'rules')) {
        foreach ($v in @(Get-Opt $a 'value')) { $jvmArgs.Add((Expand-Placeholders ([string]$v) $classpath)) }
    }
}

# PCL's own additions, verbatim from the launcher's own LatestLaunch.bat
if (-not $NoPclExtras) {
    $pclExtras = @(
        '-XX:-OmitStackTraceInFastThrow',
        '-Djdk.lang.Process.allowAmbiguousCommands=True',
        '-Dfml.ignoreInvalidMinecraftCertificates=True',
        '-Dfml.ignorePatchDiscrepancies=True',
        "-Xmx$Xmx",
        '-XX:+UnlockExperimentalVMOptions',
        '-XX:+UseG1GC',
        '-XX:G1NewSizePercent=20',
        '-XX:G1ReservePercent=20',
        '-XX:G1HeapRegionSize=32M',
        '-XX:MaxGCPauseMillis=50',
        '-XX:+PerfDisableSharedMem',
        '-XX:MinHeapFreeRatio=25',
        '-XX:MaxHeapFreeRatio=40',
        '-Dlog4j2.formatMsgNoLookups=true',
        '-Dstdout.encoding=utf-8',
        '-Dstderr.encoding=utf-8'
    )
    foreach ($e in $pclExtras) { $jvmArgs.Add($e) }
}

# The -p module path is a single ';'-joined string in the JSON; make it a set too
# (the same jar must not appear twice on the module path either).
for ($i = 0; $i -lt $jvmArgs.Count; $i++) {
    if ($jvmArgs[$i] -eq '-p' -and ($i + 1) -lt $jvmArgs.Count) {
        $seenMod = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        $uniqMod = [System.Collections.Generic.List[string]]::new()
        foreach ($part in ($jvmArgs[$i + 1] -split [regex]::Escape([IO.Path]::PathSeparator))) {
            if ([string]::IsNullOrWhiteSpace($part)) { continue }
            if ($seenMod.Add($part)) { $uniqMod.Add($part) }
        }
        $jvmArgs[$i + 1] = ($uniqMod -join [IO.Path]::PathSeparator)
    }
}

# ----------------------------------------------------------------------------
# 5. Main class + game args
# ----------------------------------------------------------------------------
$gameArgs = [System.Collections.Generic.List[string]]::new()
foreach ($a in $j.arguments.game) {
    if ($a -is [string]) {
        $gameArgs.Add((Expand-Placeholders $a $classpath))
    }
    elseif (Test-Rules (Get-Opt $a 'rules')) {
        foreach ($v in @(Get-Opt $a 'value')) { $gameArgs.Add((Expand-Placeholders ([string]$v) $classpath)) }
    }
}
# The JSON gates --width/--height behind "has_custom_resolution"; PCL always
# passes them, so do the same (a smaller window keeps the smoke test cheap).
$gameArgs.Add('--width');  $gameArgs.Add([string]$Width)
$gameArgs.Add('--height'); $gameArgs.Add([string]$Height)

$mainClass = $j.mainClass

# ----------------------------------------------------------------------------
# 6. Assemble, quote, (optionally) write an @argfile, launch
# ----------------------------------------------------------------------------
function ConvertTo-WinArg {
    param([string]$a)
    if ($a -match '[\s"]') {
        $e = [regex]::Replace($a, '(\\*)"', '$1$1\"')
        $e = [regex]::Replace($e, '(\\+)$', '$1$1')
        return '"' + $e + '"'
    }
    return $a
}

$allArgs    = @($jvmArgs) + @($mainClass) + @($gameArgs)
$argString  = ($allArgs | ForEach-Object { ConvertTo-WinArg $_ }) -join ' '

# For evidence/debugging, record one argument per line next to the script.
$tag       = 'default'
if ($GameDir -match '([^\\/]+)[\\/]?$') { $tag = $Matches[1] }
$argFile   = Join-Path $PSScriptRoot "launch-$tag.args.txt"
Set-Content -LiteralPath $argFile -Value $allArgs -Encoding utf8

Write-Host '=== launch-instance.ps1 ==='
Write-Host ("version           : {0}" -f $VersionName)
Write-Host ("version json      : {0}" -f $jsonPath)
Write-Host ("gameDir           : {0}" -f $GameDir)
Write-Host ("java              : {0}" -f $Java)
Write-Host ("classpath entries : {0}  (rules-skipped libs: {1}, duplicate coordinates dropped: {2})" -f $cpEntries.Count, $skipped, $duplicates.Count)
if ($duplicates.Count -gt 0) { Write-Host ("  deduped        : {0}" -f (($duplicates | Select-Object -Unique) -join ', ')) }
Write-Host ("jvm args          : {0}" -f $jvmArgs.Count)
Write-Host ("game args         : {0}  (main class {1})" -f $gameArgs.Count, $mainClass)
Write-Host ("command length    : {0} chars" -f $argString.Length)
Write-Host ("argfile           : {0}" -f $argFile)

if ($DryRun) {
    Write-Host ''
    Write-Host '=== COMMAND (dry run, not executed) ==='
    Write-Host ("`"$Java`" " + $argString)
    return [pscustomobject]@{
        Command       = "`"$Java`" $argString"
        ArgFile       = $argFile
        Classpath     = $cpEntries.Count
        CommandLength = $argString.Length
        Pid           = $null
    }
}

$launchArgs = $argString
if ($argString.Length -gt 30000) {
    # Windows caps a command line at 32767 chars; fall back to an argument file.
    Write-Host 'command line too long - launching via @argfile'
    $atFile = Join-Path $PSScriptRoot "launch-$tag.at.txt"
    Set-Content -LiteralPath $atFile -Value $allArgs -Encoding utf8
    $launchArgs = '@' + $atFile
}

$startParams = @{
    FilePath     = $Java
    ArgumentList = $launchArgs
    WorkingDirectory = $GameDir
    PassThru     = $true
    WindowStyle  = 'Hidden'
}
if ($OutLog) { $startParams.RedirectStandardOutput = (Resolve-Path -LiteralPath (Split-Path -Parent $OutLog) | ForEach-Object { Join-Path $_ (Split-Path -Leaf $OutLog) }) }
if ($ErrLog) { $startParams.RedirectStandardError  = (Resolve-Path -LiteralPath (Split-Path -Parent $ErrLog) | ForEach-Object { Join-Path $_ (Split-Path -Leaf $ErrLog) }) }

if (-not $OutLog -and -not $ErrLog) { $startParams.NoNewWindow = $false }

$proc = Start-Process @startParams
Write-Host ("pid               : {0}" -f $proc.Id)
if ($PidFile) {
    $dir = Split-Path -Parent $PidFile
    if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    Set-Content -LiteralPath $PidFile -Value $proc.Id -Encoding ascii
}

if ($Wait) {
    $proc.WaitForExit()
    Write-Host ("exit code         : {0}" -f $proc.ExitCode)
}

[pscustomobject]@{
    Command       = "`"$Java`" $launchArgs"
    ArgFile       = $argFile
    Classpath     = $cpEntries.Count
    CommandLength = $argString.Length
    Pid           = $proc.Id
}
