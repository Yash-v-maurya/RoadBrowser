# Regenerates the legacy PNG launcher mipmaps from icon.png (512x512, the
# 72dp visible area of the adaptive icon rendered as a rounded square).
#
#   ic_launcher.png        -> plain resize of icon.png
#   ic_launcher_round.png  -> circle-masked resize
#
# The adaptive icon itself is vector-only (drawable/ic_launcher_background.xml,
# ic_launcher_foreground.xml, ic_launcher_monochrome.xml); this script only
# refreshes the fallback bitmaps used by pre-adaptive launchers.

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..')).Path
$src = Join-Path $repoRoot 'icon.png'
if (-not (Test-Path $src)) { Write-Error "icon.png not found at $src"; exit 1 }

$sizes = [ordered]@{
    'mipmap-mdpi'    = 48
    'mipmap-hdpi'    = 72
    'mipmap-xhdpi'   = 96
    'mipmap-xxhdpi'  = 144
    'mipmap-xxxhdpi' = 192
}

Add-Type -AssemblyName System.Drawing

function New-Canvas([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)
    return @($bmp, $g)
}

$img = [System.Drawing.Image]::FromFile($src)
try {
    foreach ($kv in $sizes.GetEnumerator()) {
        $dir = Join-Path $repoRoot ("app/src/main/res/" + $kv.Key)
        $size = [int]$kv.Value
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }

        # Square (rounded corners already baked into icon.png)
        $bmp, $g = New-Canvas $size
        $g.DrawImage($img, 0, 0, $size, $size)
        $out1 = Join-Path $dir 'ic_launcher.png'
        $bmp.Save($out1, [System.Drawing.Imaging.ImageFormat]::Png)
        $g.Dispose(); $bmp.Dispose()

        # Round: clip to a circle inscribed in the canvas
        $bmp, $g = New-Canvas $size
        $path = New-Object System.Drawing.Drawing2D.GraphicsPath
        $path.AddEllipse(0, 0, $size, $size)
        $g.SetClip($path)
        $g.DrawImage($img, 0, 0, $size, $size)
        $g.ResetClip()
        $out2 = Join-Path $dir 'ic_launcher_round.png'
        $bmp.Save($out2, [System.Drawing.Imaging.ImageFormat]::Png)
        $path.Dispose(); $g.Dispose(); $bmp.Dispose()

        Write-Output "Wrote $out1 and $out2"
    }
} finally {
    $img.Dispose()
}
