# Copyright (c) 2026 DaKang233.
# SPDX-License-Identifier: LGPL-3.0-only

<#
.SYNOPSIS
Converts the retained artwork into Minecraft textures and upload-ready images.
.DESCRIPTION
The AE2 interface sprite is read from the supplied jar. Only its three blue
shades are mapped to the gas-cell teal ramp. All six faces use the same native
16x16 image. The existing cube model and texture names remain unchanged.
The generated logo master is retained in docs/branding/source.
#>
param(
    [string]$ProjectDirectory = (Split-Path $PSScriptRoot -Parent),
    [string]$SourceDirectory,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$TextureSize = 16
if (-not $SourceDirectory) { $SourceDirectory = Join-Path $ProjectDirectory 'docs\branding\source' }
if (-not $OutputDirectory) { $OutputDirectory = $ProjectDirectory }
$blockDirectory = Join-Path $OutputDirectory 'src\main\resources\assets\appliedcraftedenergistics\textures\block'
$brandDirectory = Join-Path $OutputDirectory 'docs\branding'
New-Item -ItemType Directory -Path $blockDirectory, $brandDirectory -Force | Out-Null

function Get-InterfaceTexture {
    $jarPath = Join-Path $ProjectDirectory 'libs\appliedenergistics2-19.2.20.jar'
    $archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        $entry = $archive.GetEntry('assets/ae2/textures/block/interface.png')
        if (-not $entry) { throw 'AE2 interface texture is missing from the supplied jar.' }
        $stream = $entry.Open()
        try {
            $source = [System.Drawing.Bitmap]::new($stream)
            try { $image = $source.Clone() } finally { $source.Dispose() }
        } finally { $stream.Dispose() }
    } finally { $archive.Dispose() }
    if ($image.Width -ne 16 -or $image.Height -ne 16) {
        $image.Dispose(); throw 'Expected AE2 19.2 native 16x16 interface art.'
    }
    # Only the blue ramp changes. White casing and both purple-gray trim shades
    # remain byte-identical to AE2. The new ramp follows ACE gas-cell colours.
    $mapping = @{}
    foreach ($pair in @(@('#708cba','#548c9c'),@('#7da9d2','#6ca4b2'),@('#9cd3ff','#9cccd6'))) {
        $from = [System.Drawing.ColorTranslator]::FromHtml($pair[0])
        $mapping[$from.ToArgb().ToString()] = [System.Drawing.ColorTranslator]::FromHtml($pair[1])
    }
    for ($y=0; $y -lt 16; $y++) {
        for ($x=0; $x -lt 16; $x++) {
            $key = $image.GetPixel($x,$y).ToArgb().ToString()
            if ($mapping.ContainsKey($key)) { $image.SetPixel($x,$y,$mapping[$key]) }
        }
    }
    return ,$image
}

$faces = @{}
try {
    $faces['front'] = Get-InterfaceTexture
    $faces['side'] = $faces['front'].Clone()
    $faces['top'] = $faces['front'].Clone()
    foreach ($faceName in @('front','side','top')) {
        $faces[$faceName].Save((Join-Path $blockDirectory ('me_gas_interface_'+$faceName+'.png')),[System.Drawing.Imaging.ImageFormat]::Png)
    }

    $sheet = [System.Drawing.Bitmap]::new(864,328)
    $graphics = [System.Drawing.Graphics]::FromImage($sheet)
    $font = [System.Drawing.Font]::new('Segoe UI',12)
    try {
        $graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#22262b'))
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $index=0
        foreach ($faceName in @('front','side','top')) {
            $graphics.DrawImage($faces[$faceName],[System.Drawing.Rectangle]::new(16+$index*288,16,256,256),0,0,$TextureSize,$TextureSize,[System.Drawing.GraphicsUnit]::Pixel)
            $graphics.DrawString($faceName,$font,[System.Drawing.Brushes]::White,[single](16+$index*288),[single]290)
            $index++
        }
        $sheet.Save((Join-Path $brandDirectory 'interface-textures-preview.png'),[System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $font.Dispose(); $graphics.Dispose(); $sheet.Dispose() }

    # Exact projection of the actual game textures, without an invented 3D model.
    $cube=[System.Drawing.Bitmap]::new(1024,1024,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics=[System.Drawing.Graphics]::FromImage($cube)
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.SmoothingMode=[System.Drawing.Drawing2D.SmoothingMode]::None
        $topPoints=[System.Drawing.Point[]]@([System.Drawing.Point]::new(512,64),[System.Drawing.Point]::new(928,288),[System.Drawing.Point]::new(96,288))
        $frontPoints=[System.Drawing.Point[]]@([System.Drawing.Point]::new(96,288),[System.Drawing.Point]::new(512,512),[System.Drawing.Point]::new(96,736))
        $sidePoints=[System.Drawing.Point[]]@([System.Drawing.Point]::new(512,512),[System.Drawing.Point]::new(928,288),[System.Drawing.Point]::new(512,960))
        $graphics.DrawImage($faces['top'],$topPoints)
        $graphics.DrawImage($faces['front'],$frontPoints)
        $graphics.DrawImage($faces['side'],$sidePoints)
        $cube.Save((Join-Path $brandDirectory 'me-gas-interface-block.png'),[System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $cube.Dispose() }
} finally { foreach ($face in $faces.Values) { $face.Dispose() } }

# The logo master is optional while preparing the first texture pass.
$logoSource = Join-Path $SourceDirectory 'logo.png'
if (Test-Path -LiteralPath $logoSource) {
    $logo=[System.Drawing.Bitmap]::new($logoSource)
    try {
        $minX=$logo.Width; $minY=$logo.Height; $maxX=-1; $maxY=-1
        for ($y=0; $y -lt $logo.Height; $y++) {
            for ($x=0; $x -lt $logo.Width; $x++) {
                if ($logo.GetPixel($x,$y).A -gt 16) {
                    $minX=[Math]::Min($minX,$x); $minY=[Math]::Min($minY,$y)
                    $maxX=[Math]::Max($maxX,$x); $maxY=[Math]::Max($maxY,$y)
                }
            }
        }
        if ($maxX -lt 0) { throw 'The logo source is empty.' }
        $sourceBounds=[System.Drawing.Rectangle]::new($minX,$minY,$maxX-$minX+1,$maxY-$minY+1)
        foreach ($size in @(1024,512,256,64)) {
            $output=[System.Drawing.Bitmap]::new($size,$size,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
            $graphics=[System.Drawing.Graphics]::FromImage($output)
            try {
                $graphics.Clear([System.Drawing.Color]::Transparent)
                $graphics.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.CompositingQuality=[System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                $graphics.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $padding=[int][Math]::Round($size*0.09)
                $scale=($size-2*$padding)/[double][Math]::Max($sourceBounds.Width,$sourceBounds.Height)
                $width=[int][Math]::Round($sourceBounds.Width*$scale); $height=[int][Math]::Round($sourceBounds.Height*$scale)
                $targetBounds=[System.Drawing.Rectangle]::new([int](($size-$width)/2),[int](($size-$height)/2),$width,$height)
                $graphics.DrawImage($logo,$targetBounds,$sourceBounds,[System.Drawing.GraphicsUnit]::Pixel)
                $output.Save((Join-Path $brandDirectory ('ace-logo-'+$size+'.png')),[System.Drawing.Imaging.ImageFormat]::Png)
                if ($size -eq 256) {
                    $resourceDirectory=Join-Path $OutputDirectory 'src\main\resources'
                    New-Item -ItemType Directory -Path $resourceDirectory -Force | Out-Null
                    $output.Save((Join-Path $resourceDirectory 'icon.png'),[System.Drawing.Imaging.ImageFormat]::Png)
                }
            } finally { $graphics.Dispose(); $output.Dispose() }
        }
    } finally { $logo.Dispose() }

    $master=[System.Drawing.Bitmap]::new((Join-Path $brandDirectory 'ace-logo-1024.png'))
    # Keep every logo pixel unchanged; add transparent horizontal margins.
    # Only 12 already-transparent rows are omitted at the top and bottom.
    $wide=[System.Drawing.Bitmap]::new(1600,1000,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $sourceData=$master.LockBits([System.Drawing.Rectangle]::new(0,12,1024,1000),[System.Drawing.Imaging.ImageLockMode]::ReadOnly,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $targetData=$wide.LockBits([System.Drawing.Rectangle]::new(288,0,1024,1000),[System.Drawing.Imaging.ImageLockMode]::WriteOnly,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
            try {
                $row=[byte[]]::new(4096)
                for ($y=0; $y -lt 1000; $y++) {
                    [System.Runtime.InteropServices.Marshal]::Copy([IntPtr]::Add($sourceData.Scan0,$y*$sourceData.Stride),$row,0,4096)
                    [System.Runtime.InteropServices.Marshal]::Copy($row,0,[IntPtr]::Add($targetData.Scan0,$y*$targetData.Stride),4096)
                }
            } finally { $wide.UnlockBits($targetData) }
        } finally { $master.UnlockBits($sourceData) }
        $wide.Save((Join-Path $brandDirectory 'ace-logo-16x10.png'),[System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $wide.Dispose() }

    $dark=[System.Drawing.Bitmap]::new(1024,1024)
    $graphics=[System.Drawing.Graphics]::FromImage($dark)
    try {
        $graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#161d25'))
        $graphics.DrawImageUnscaled($master,0,0)
        $dark.Save((Join-Path $brandDirectory 'ace-logo-dark.png'),[System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $dark.Dispose() }

    $banner=[System.Drawing.Bitmap]::new(1600,640)
    $graphics=[System.Drawing.Graphics]::FromImage($banner)
    $heading=[System.Drawing.Font]::new('Bahnschrift',64,[System.Drawing.FontStyle]::Bold,[System.Drawing.GraphicsUnit]::Pixel)
    $eyebrow=[System.Drawing.Font]::new('Bahnschrift',34,[System.Drawing.FontStyle]::Regular,[System.Drawing.GraphicsUnit]::Pixel)
    $chinese=[System.Drawing.Font]::new('Microsoft YaHei',30,[System.Drawing.FontStyle]::Regular,[System.Drawing.GraphicsUnit]::Pixel)
    $cyan=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#40c6d3'))
    $muted=[System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#aab5c2'))
    $separator=[System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml('#33414f'),2)
    try {
        $graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#161d25'))
        $graphics.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.TextRenderingHint=[System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
        $graphics.DrawImage($master,[System.Drawing.Rectangle]::new(16,24,592,592))
        $graphics.DrawLine($separator,640,104,640,536)
        $graphics.DrawString('APPLIED',$eyebrow,$cyan,[single]704,[single]120)
        $graphics.DrawString('CRAFTED',$heading,[System.Drawing.Brushes]::White,[single]700,[single]180)
        $graphics.DrawString('ENERGISTICS',$heading,[System.Drawing.Brushes]::White,[single]700,[single]254)
        $graphics.DrawString('应用机巧能源',$chinese,$muted,[single]704,[single]378)
        $graphics.FillRectangle($cyan,708,452,96,4)
        $banner.Save((Join-Path $brandDirectory 'ace-logo-banner.png'),[System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $separator.Dispose(); $muted.Dispose(); $cyan.Dispose(); $chinese.Dispose(); $eyebrow.Dispose(); $heading.Dispose()
        $graphics.Dispose(); $banner.Dispose(); $master.Dispose()
    }
}
Write-Output ('Prepared branding assets in '+$OutputDirectory)
