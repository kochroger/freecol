<#
.SYNOPSIS
    Converts the original Sid Meier's Colonization soundtrack (MP3) into
    16-bit PCM WAV files that the FreeCol Classic UI can play.

.DESCRIPTION
    WHY THIS SCRIPT EXISTS
    The Steam release of Sid Meier's Colonization ships the original 1994
    soundtrack as 26 MP3 files ("Bonus Content\Soundtrack"). FreeCol's runtime
    can only play OGG Vorbis (bundled jorbis/jogg) and whatever javax.sound
    decodes natively (PCM WAV / AIFF / AU). It has no MP3 decoder, and the
    project deliberately adds no third-party decoder or ffmpeg dependency.

    Windows, however, already contains an MP3 decoder: Media Foundation. This
    script drives it through the WinRT API
    Windows.Media.Transcoding.MediaTranscoder, which Windows PowerShell 5.1 can
    call directly. Nothing is downloaded and nothing is installed.

    COPYRIGHT
    The music is copyrighted and belongs to the owner of the game. The output
    goes to data/mods/classic_music/, which is listed in the repository's
    .gitignore, so the converted files are never committed.

    OUTPUT
    <Target>\track01.wav .. track26.wav, where trackNN corresponds to
    "Sid Meier's Colonization Soundtrack - Track N.mp3". Format: RIFF/WAVE,
    PCM signed 16-bit little-endian, 44100 Hz, stereo (about 10 MB per minute,
    roughly 340 MB for the whole ~32 minute soundtrack).

    IDEMPOTENCE
    A track is skipped when its WAV already exists, is non-empty and is not
    older than its MP3. Each conversion is written to a temporary
    "*.wav.part" file first and only renamed to its final name once Media
    Foundation reports success, so an interrupted run never leaves a
    truncated WAV that a later run would mistake for up to date.

    REQUIREMENTS
    Windows 10/11 with Windows PowerShell 5.1 (powershell.exe, NOT pwsh 7:
    PowerShell 7 cannot load WinRT types through the ContentType=WindowsRuntime
    syntax used below). The "N" editions of Windows lack Media Foundation unless
    the Media Feature Pack is installed.

.PARAMETER Source
    Directory holding "Sid Meier's Colonization Soundtrack - Track N.mp3".
    Defaults to the standard Steam location.

.PARAMETER Target
    Output directory for trackNN.wav. Defaults to
    data\mods\classic_music\resources\music under the repository root
    (derived from this script's own location).

.PARAMETER Force
    Re-convert every track even when the WAV is up to date.

.PARAMETER TitleTrack
    Which track (Steam "Track N" = trackNN.wav) plays on the title screen and
    on into the game. Only needed to CHANGE it: the choice lives in ONE line of
    <pack>\resources.properties ("sound.classic.music.title=..."), which can
    equally be edited by hand. Without -TitleTrack an existing
    resources.properties is never touched, so re-running the converter keeps
    the owner's choice; a new one starts with Track 1.

.NOTES
    PACK DESCRIPTOR
    The Classic UI loads the music as the git-ignored FreeCol mod pack
    "classic_music" (see ClassicSoundController.java). The pack root is two
    levels above -Target (<pack>\resources\music), and the script writes its
    descriptor there:
      <pack>\mod.xml              <mod id="classic_music"/>  (if absent)
      <pack>\resources.properties the title line and the tracks directory
                                  (if absent, or the title line if -TitleTrack)
    The resource paths in resources.properties are relative to the pack, so
    -Target must end in resources\music.

.EXAMPLE
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\classic_assets\convert-soundtrack.ps1

.EXAMPLE
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\classic_assets\convert-soundtrack.ps1 `
        -Source "D:\Steam\steamapps\common\Sid Meier's Colonization\Bonus Content\Soundtrack" `
        -Target "C:\freecol\data\mods\classic_music\resources\music"
#>
[CmdletBinding()]
param(
    [string] $Source = "C:\Program Files (x86)\Steam\steamapps\common\Sid Meier's Colonization\Bonus Content\Soundtrack",
    [string] $Target = '',
    [switch] $Force,
    [ValidateRange(1, 99)]
    [int] $TitleTrack = 1
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

# Default target: <repo>\data\mods\classic_music\resources\music, with <repo>
# two levels above this script (tools\classic_assets). Computed here rather
# than in the param() default because Windows PowerShell 5.1 leaves
# $PSScriptRoot empty while evaluating param() defaults under -File.
if ([string]::IsNullOrEmpty($Target)) {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $repoRoot  = Split-Path -Parent (Split-Path -Parent $scriptDir)
    $Target    = Join-Path $repoRoot 'data\mods\classic_music\resources\music'
}

# The WinRT projection only exists in Windows PowerShell (Desktop edition).
if ($PSVersionTable.PSEdition -eq 'Core') {
    throw 'Run this script with Windows PowerShell 5.1 (powershell.exe), not PowerShell 7 (pwsh): WinRT types cannot be loaded from pwsh.'
}

# Output format. 44.1 kHz / stereo / 16-bit is CD quality, what the MP3s were
# mastered at, and a format every javax.sound implementation opens natively.
$SampleRate    = 44100
$Channels      = 2
$BitsPerSample = 16

# ---------------------------------------------------------------------------
# WinRT plumbing
# ---------------------------------------------------------------------------

# System.Runtime.WindowsRuntime provides WindowsRuntimeSystemExtensions.AsTask,
# which turns a WinRT IAsyncOperation/IAsyncAction into a .NET Task we can wait
# on. PowerShell has no 'await', so we call AsTask via reflection (the usual
# "Await" helper pattern) because the methods are generic over the result type.
Add-Type -AssemblyName System.Runtime.WindowsRuntime

# Loading a WinRT type by name with ContentType=WindowsRuntime makes its
# metadata available to PowerShell; the variables themselves are unused.
$null = [Windows.Storage.StorageFile,                         Windows.Storage,       ContentType = WindowsRuntime]
$null = [Windows.Storage.StorageFolder,                       Windows.Storage,       ContentType = WindowsRuntime]
$null = [Windows.Storage.CreationCollisionOption,             Windows.Storage,       ContentType = WindowsRuntime]
$null = [Windows.Media.Transcoding.MediaTranscoder,           Windows.Media.Transcoding, ContentType = WindowsRuntime]
$null = [Windows.Media.Transcoding.PrepareTranscodeResult,    Windows.Media.Transcoding, ContentType = WindowsRuntime]
$null = [Windows.Media.MediaProperties.MediaEncodingProfile,  Windows.Media.MediaProperties, ContentType = WindowsRuntime]
$null = [Windows.Media.MediaProperties.AudioEncodingQuality,  Windows.Media.MediaProperties, ContentType = WindowsRuntime]
$null = [Windows.Media.MediaProperties.AudioEncodingProperties, Windows.Media.MediaProperties, ContentType = WindowsRuntime]

$extensionMethods = [System.WindowsRuntimeSystemExtensions].GetMethods()

# AsTask<TResult>(IAsyncOperation<TResult>)
$asTaskOperation = $extensionMethods | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1

# AsTask<TProgress>(IAsyncActionWithProgress<TProgress>) - TranscodeAsync
# reports progress as a double, so it returns this interface.
$asTaskActionWithProgress = $extensionMethods | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncActionWithProgress`1'
} | Select-Object -First 1

# AsTask(IAsyncAction) - used for rename/delete on StorageFile.
$asTaskAction = $extensionMethods | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction'
} | Select-Object -First 1

function Wait-WinRtOperation($Operation, [Type] $ResultType) {
    $task = $asTaskOperation.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
    $task.Wait(-1) | Out-Null
    return $task.Result
}

function Wait-WinRtActionWithProgress($Action, [Type] $ProgressType) {
    $task = $asTaskActionWithProgress.MakeGenericMethod($ProgressType).Invoke($null, @($Action))
    $task.Wait(-1) | Out-Null
}

function Wait-WinRtAction($Action) {
    $task = $asTaskAction.Invoke($null, @($Action))
    $task.Wait(-1) | Out-Null
}

# Unwraps the AggregateException / TargetInvocationException layers that the
# reflection + Task plumbing adds, so error messages name the real cause.
function Get-InnermostMessage($ErrorRecord) {
    $e = $ErrorRecord.Exception
    while ($null -ne $e.InnerException) { $e = $e.InnerException }
    return $e.Message
}

# Reads format and length from a RIFF/WAVE header so the summary can show what
# Media Foundation actually wrote (it may choose WAVE_FORMAT_EXTENSIBLE).
function Get-WavInfo([string] $Path) {
    $fs = [System.IO.File]::OpenRead($Path)
    try {
        $br = New-Object System.IO.BinaryReader($fs)
        if ([System.Text.Encoding]::ASCII.GetString($br.ReadBytes(4)) -ne 'RIFF') { return $null }
        $null = $br.ReadUInt32()
        if ([System.Text.Encoding]::ASCII.GetString($br.ReadBytes(4)) -ne 'WAVE') { return $null }
        $info = @{ Tag = 0; Channels = 0; Rate = 0; Bits = 0; DataBytes = 0; ByteRate = 0 }
        while ($fs.Position + 8 -le $fs.Length) {
            $id   = [System.Text.Encoding]::ASCII.GetString($br.ReadBytes(4))
            $size = $br.ReadUInt32()
            $next = $fs.Position + $size + ($size % 2)
            if ($id -eq 'fmt ') {
                $info.Tag      = $br.ReadUInt16()
                $info.Channels = $br.ReadUInt16()
                $info.Rate     = $br.ReadUInt32()
                $info.ByteRate = $br.ReadUInt32()
                $null          = $br.ReadUInt16()
                $info.Bits     = $br.ReadUInt16()
            } elseif ($id -eq 'data') {
                $info.DataBytes = $size
                break
            }
            $fs.Position = $next
        }
        return $info
    } finally {
        $fs.Dispose()
    }
}

# ---------------------------------------------------------------------------
# Inputs
# ---------------------------------------------------------------------------

if (-not (Test-Path -LiteralPath $Source -PathType Container)) {
    throw "Soundtrack directory not found: $Source"
}
$Source = (Resolve-Path -LiteralPath $Source).ProviderPath
if (-not (Test-Path -LiteralPath $Target)) {
    # Not New-Item -Path: that would treat [ ] in the folder name as a
    # wildcard.  Resolve against the PowerShell location (not the process
    # directory) without wildcard expansion, then create literally.
    [void][IO.Directory]::CreateDirectory(
        $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Target))
}
$Target = (Resolve-Path -LiteralPath $Target).ProviderPath

# Collect "... Track N.mp3" and sort numerically (a plain name sort would put
# Track 10 before Track 2).
$tracks = @(Get-ChildItem -LiteralPath $Source -Filter '*.mp3' | ForEach-Object {
    if ($_.Name -match 'Track\s+(\d+)\.mp3$') {
        [pscustomobject]@{ Number = [int] $Matches[1]; File = $_ }
    }
} | Sort-Object Number)

if ($tracks.Count -eq 0) {
    throw "No 'Track N.mp3' files found in $Source"
}

Write-Host ("Source : {0}" -f $Source)
Write-Host ("Target : {0}" -f $Target)
Write-Host ("Format : PCM {0} Hz, {1} channels, {2}-bit" -f $SampleRate, $Channels, $BitsPerSample)
Write-Host ("Tracks : {0}" -f $tracks.Count)
Write-Host ''

# One transcoder and one profile serve all tracks. CreateWav(High) yields
# 44.1 kHz/stereo/16-bit PCM already; the explicit assignments pin it down in
# case a future Windows release changes the preset.
$transcoder = New-Object Windows.Media.Transcoding.MediaTranscoder
$wavProfile = [Windows.Media.MediaProperties.MediaEncodingProfile]::CreateWav(
    [Windows.Media.MediaProperties.AudioEncodingQuality]::High)
$wavProfile.Audio.SampleRate    = $SampleRate
$wavProfile.Audio.ChannelCount  = $Channels
$wavProfile.Audio.BitsPerSample = $BitsPerSample
$wavProfile.Audio.Bitrate       = $SampleRate * $Channels * $BitsPerSample

$targetFolder = Wait-WinRtOperation ([Windows.Storage.StorageFolder]::GetFolderFromPathAsync($Target)) ([Windows.Storage.StorageFolder])

# ---------------------------------------------------------------------------
# Conversion loop
# ---------------------------------------------------------------------------

$converted = 0
$skipped   = 0
$failed    = 0
$results   = New-Object System.Collections.Generic.List[object]
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

foreach ($t in $tracks) {
    $name    = 'track{0:D2}.wav' -f $t.Number
    $outPath = Join-Path $Target $name
    $status  = ''

    $upToDate = (-not $Force) -and (Test-Path -LiteralPath $outPath) -and
                ((Get-Item -LiteralPath $outPath).Length -gt 44) -and
                ((Get-Item -LiteralPath $outPath).LastWriteTimeUtc -ge $t.File.LastWriteTimeUtc)

    if ($upToDate) {
        $status = 'skipped (up to date)'
        $skipped++
    } else {
        $partName = $name + '.part'
        try {
            $inFile = Wait-WinRtOperation ([Windows.Storage.StorageFile]::GetFileFromPathAsync($t.File.FullName)) ([Windows.Storage.StorageFile])
            $partFile = Wait-WinRtOperation ($targetFolder.CreateFileAsync($partName, [Windows.Storage.CreationCollisionOption]::ReplaceExisting)) ([Windows.Storage.StorageFile])

            $prepared = Wait-WinRtOperation ($transcoder.PrepareFileTranscodeAsync($inFile, $partFile, $wavProfile)) ([Windows.Media.Transcoding.PrepareTranscodeResult])
            if (-not $prepared.CanTranscode) {
                throw "Media Foundation cannot transcode this file: $($prepared.FailureReason)"
            }
            Wait-WinRtActionWithProgress ($prepared.TranscodeAsync()) ([double])

            # Atomic replace of the final file only after a successful run.
            if (Test-Path -LiteralPath $outPath) { Remove-Item -LiteralPath $outPath -Force }
            Move-Item -LiteralPath (Join-Path $Target $partName) -Destination $outPath
            $status = 'converted'
            $converted++
        } catch {
            $status = 'FAILED: ' + (Get-InnermostMessage $_)
            $failed++
            $partPath = Join-Path $Target $partName
            if (Test-Path -LiteralPath $partPath) { Remove-Item -LiteralPath $partPath -Force -ErrorAction SilentlyContinue }
        }
    }

    $line = [pscustomobject]@{ File = $name; Status = $status; Format = ''; Duration = ''; SizeMB = '' }
    if (Test-Path -LiteralPath $outPath) {
        $info = Get-WavInfo $outPath
        if ($null -ne $info -and $info.ByteRate -gt 0) {
            $seconds = [double] $info.DataBytes / $info.ByteRate
            $line.Format   = '{0} Hz/{1} ch/{2}-bit (tag 0x{3:X4})' -f $info.Rate, $info.Channels, $info.Bits, $info.Tag
            $line.Duration = '{0}:{1:00}' -f [int][Math]::Floor($seconds / 60), [int][Math]::Floor($seconds % 60)
        }
        $line.SizeMB = '{0:N1}' -f ((Get-Item -LiteralPath $outPath).Length / 1MB)
    }
    $results.Add($line)
    Write-Host ('{0}  <- Track {1,2}  {2}' -f $name, $t.Number, $status)
}

$stopwatch.Stop()

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

Write-Host ''
$results | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
$totalBytes = (Get-ChildItem -LiteralPath $Target -Filter 'track*.wav' | Measure-Object -Property Length -Sum).Sum
if ($null -eq $totalBytes) { $totalBytes = 0 }
Write-Host ('Converted: {0}   Skipped: {1}   Failed: {2}   Total size: {3:N1} MB   Time: {4:N0} s' -f `
    $converted, $skipped, $failed, ($totalBytes / 1MB), $stopwatch.Elapsed.TotalSeconds)

# ---------------------------------------------------------------------------
# Pack descriptor (mod.xml + resources.properties)
# ---------------------------------------------------------------------------

# The pack root is <pack>\resources\music\..\.. -- see .NOTES.
$packDir   = Split-Path -Parent (Split-Path -Parent $Target)
$modXml    = Join-Path $packDir 'mod.xml'
$propsPath = Join-Path $packDir 'resources.properties'
$titleKey  = 'sound.classic.music.title'
$titleLine = '{0}=resources/music/track{1:D2}.wav' -f $titleKey, $TitleTrack
# ASCII without BOM: java.util.Properties reads ISO-8859-1.
$ascii     = New-Object System.Text.ASCIIEncoding

if ((Split-Path -Leaf $Target) -ne 'music' -or
    (Split-Path -Leaf (Split-Path -Parent $Target)) -ne 'resources') {
    Write-Warning "Target does not end in resources\music; the descriptor in $packDir will not find the tracks."
}
if (-not (Test-Path -LiteralPath (Join-Path $Target ('track{0:D2}.wav' -f $TitleTrack)))) {
    Write-Warning ("track{0:D2}.wav does not exist; the game falls back to the first track as title piece." -f $TitleTrack)
}

if (-not (Test-Path -LiteralPath $modXml)) {
    [System.IO.File]::WriteAllText($modXml, "<mod id=`"classic_music`"/>`r`n", $ascii)
    Write-Host "Wrote $modXml"
}

if (-not (Test-Path -LiteralPath $propsPath)) {
    $props = @(
        '# Classic UI: the ORIGINAL Colonization soundtrack, converted from the',
        "# owner's own Steam copy by tools/classic_assets/convert-soundtrack.ps1.",
        '# Copyrighted -- this folder is git-ignored, never commit it.',
        '# Played by net.sf.freecol.client.gui.classic.ClassicSoundController.',
        '#',
        '# THE ONE LINE TO CHANGE BY EAR: the piece on the title screen, which',
        '# also plays on (to its end) when a game starts. Steam "Track N" is',
        '# trackNN.wav (two digits). Save, then restart the game.',
        '# Titelmusik: Nummer hier aendern, speichern, Spiel neu starten.',
        $titleLine,
        '',
        '# All tracks. In game, every track except the title piece cycles',
        '# (shuffled) for the rest of the session. Do not change.',
        'sound.classic.music.tracks=resources/music',
        ''
    ) -join "`r`n"
    [System.IO.File]::WriteAllText($propsPath, $props, $ascii)
    Write-Host "Wrote $propsPath"
} elseif ($PSBoundParameters.ContainsKey('TitleTrack')) {
    # Explicit request: replace just the title line, keep everything else.
    $lines = [System.IO.File]::ReadAllLines($propsPath)
    $found = $false
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match ('^\s*' + [regex]::Escape($titleKey) + '\s*[=:]')) {
            $lines[$i] = $titleLine
            $found = $true
        }
    }
    if (-not $found) { $lines = @($titleLine) + $lines }
    [System.IO.File]::WriteAllText($propsPath, (($lines -join "`r`n") + "`r`n"), $ascii)
    Write-Host "Updated $propsPath"
}

$current = @(Get-Content -LiteralPath $propsPath | Where-Object {
    $_ -match ('^\s*' + [regex]::Escape($titleKey) + '\s*[=:]') })
Write-Host ('Title piece: {0}' -f ($(if ($current.Count -gt 0) { $current[0] } else { '(not set: first track)' })))

if ($failed -gt 0) { exit 1 }
exit 0
