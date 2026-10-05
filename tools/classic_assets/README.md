# Classic UI — original-Colonization asset conversion

One-time, run-once tooling that converts **your own** legally-owned original
*Sid Meier's Colonization* (1994) art into a local FreeCol mod pack for the
classic UI. See [`classic_ui_plan/assets.md`](../../classic_ui_plan/assets.md) for
the rationale.

**Nothing copyrighted is committed.** The tooling reads your install and writes
a **git-ignored** `data/mods/classic_original/` pack; FreeCol loads it like any
other mod.

## Prerequisite

**None beyond a JDK and Ant** — the same toolchain you already build FreeCol
with. The converter is pure Java (`net.sf.freecol.tools.classicassets`), part of
the FreeCol source tree. There is no Python, virtualenv, Pillow, `mpskit`, or
network access involved.

## Usage

```
ant classic-assets -Dcol.install="C:\Program Files (x86)\GOG Galaxy\Games\Colonization\MPS\COLONIZE"
```

`col.install` must point at the directory holding the original `*.SS`,
`*.PIK`, `*.FF` (bitmap fonts) and `VICEROY.PAL` files (the GOG/Steam "Classic" release keeps them
under `MPS\COLONIZE\`).

Output: `data/mods/classic_original/` — `mod.xml`, `resources.properties`,
`resources/images/{pik,ss,ff}/*.png`, `ss-anchors.properties` and
`text/{GAME,NAMES,LABELS}.TXT` (see below).

## How it works

The `classic-assets` Ant target compiles the source and runs
`net.sf.freecol.tools.classicassets.ClassicAssetConverter`, which:

1. Reads the master palette `VICEROY.PAL` (used to decode the palette-less PIK
   screens such as `COLONY.PIK`).
2. Decodes every `*.SS` sprite set and `*.PIK` screen straight from the MADSPACK
   containers into `BufferedImage`s and writes them as PNG:
   - `.PIK` → `resources/images/pik/NAME.PIK.png`
   - `.SS` frames → `resources/images/ss/NAME.SS.000.png`, `.001.png`, …
   - `.FF` bitmap fonts → `resources/images/ff/NAME.FF.png`, one 16×8-cell
     2-bit palette atlas per font (index 0 transparent, the glyph's raw pixel
     values 1–3 as indices), plus the metrics as a quoted string resource
     `image.classic_original.ff.NAME.FF.properties="format=1,height=..,cell=..,widths=w0;...;w127"`.
     One atlas per font instead of one PNG per glyph: FreeCol lists the whole
     directory for every image it maps (~2 s for ~480 glyph files), and the
     many zero-width glyph slots cannot be stored as images at all. The
     runtime reader is `net.sf.freecol.client.gui.classic.ClassicFont`.
3. Copies the original's text files `GAME.TXT`, `NAMES.TXT` and `LABELS.TXT`
   (found case-insensitively) **byte for byte** into `text/` of the pack.
   They hold every German string of the new-game screens (picker labels,
   prompts, nation pages, the audience scroll). They are copyrighted, so they
   live only in this git-ignored pack, never in tracked files; they are
   copied unchanged so the runtime parser
   (`net.sf.freecol.client.gui.classic.ClassicText`) alone owns the format
   (the game's umlaut codes, markup, sections). A missing file is a warning;
   the client then skips the new-game screens and starts with defaults.
4. Writes `ss-anchors.properties`: one line `STEM.SS.nnn=ax,ay` per SS frame,
   the raw bottom-centre screen anchor from bytes 8-11 of the frame's 16-byte
   sprite header (`SsDecoder.anchors`). The original places its sprites by
   these (e.g. the king and the nation banner of the audience screen); the
   frame's top-left is `(ax - w/2, ay - h + 1)` with its PNG size. Numbers
   only, no content.
5. Writes `resources.properties` exposing every frame under a stable
   `image.classic_original.*` key namespace, plus `mod.xml` and messages.

**Packs built before `text/` and `ss-anchors.properties` existed must be
regenerated** (re-run `ant classic-assets`); until then the classic UI's
NEUE WELT starts a game with defaults and logs one INFO line saying so.

The decode path (MADSPACK container, FAB decompression, `.SS` linemode RLE,
`.PIK` indexed images, VGA palettes, `.FF` fonts) is implemented directly in Java —
clean-room from the format documentation — and unit-tested in
`net.sf.freecol.tools.classicassets.ClassicAssetDecoderTest`.

The 6-bit VGA palette values are expanded to 8 bits exactly as DOSBox does,
`(v<<2)|(v>>4)`, so the pack's PNGs equal native DOSBox captures bit for bit
and screens can be pixel-diffed against them without any tolerance.

## A2 — mapping to FreeCol keys

The generated keys are a stable scaffold namespace. To actually skin the UI,
map FreeCol resource keys onto them in a committed `aliases.properties` beside
this file (real FreeCol key `=resource:` scaffold key), e.g.:

```
image.background.MainPanel=resource:image.classic_original.pik.OPENMENU.PIK
image.tile.model.tile.ocean.center=resource:image.classic_original.ss.TERRAIN.SS.010
```

`ClassicAssetConverter` appends `aliases.properties` (if present) to the
generated `resources.properties`, so re-running the conversion never clobbers
that work. Because the pack is loaded last (highest priority — see
`FreeColClient.withClassicOriginalPack`), these aliases override the base/default
FreeCol art whenever `--classic` is active and the pack is present.

**Finding the right frame.** A `.SS` set is many frames (`NAME.SS.000`, `.001`,
…) with no names — you have to identify them. Two reliable ways, used for the
terrain mapping already in `aliases.properties`:

- **Documented enums.** `net.sf.freecol.tools.ColonizationMapReader` records the
  canonical Colonization terrain order (`0x00` tundra, `0x01` desert, `0x02`
  plains, `0x03` prairie, `0x04` grassland, `0x05` savannah, `0x06` marsh,
  `0x07` swamp), which is exactly the order of `TERRAIN.SS` frames 000–007.
- **Visual inspection.** Open the extracted PNGs under
  `data/mods/classic_original/resources/images/ss/` (they are tiny — 16×16 for
  terrain — so scale them up nearest-neighbour to read them). This is how the
  remaining `TERRAIN.SS` frames were identified: 009 arctic, 010 ocean, 011 sea
  lane (008 is an unused cactus-desert variant).

The terrain block in `aliases.properties` is the worked example: all base land,
forest (rendered as their base terrain until per-tile tree overlays exist), and
water/arctic/hills/mountains tile types are mapped there.

## Original soundtrack (music)

The Steam release ships the original soundtrack as 26 MP3s under
`Bonus Content\Soundtrack\`. FreeCol can only play OGG Vorbis (bundled
jorbis) and what `javax.sound` reads natively (PCM WAV/AIFF/AU) — there is no
MP3 decoder in `jars/`, and none is added. `convert-soundtrack.ps1` therefore
uses the MP3 decoder that is already part of Windows (Media Foundation, via the
WinRT `MediaTranscoder` API) to write plain PCM WAV. No download, no install.

```
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\classic_assets\convert-soundtrack.ps1
```

- Run it with **Windows PowerShell 5.1** (`powershell.exe`), not `pwsh` 7, which
  cannot load WinRT types.
- `-Source` defaults to
  `C:\Program Files (x86)\Steam\steamapps\common\Sid Meier's Colonization\Bonus Content\Soundtrack`;
  `-Target` defaults to `data\mods\classic_music\resources\music\` in this
  repository. `-Force` re-converts everything.
- Output: `track01.wav` … `track26.wav` (`trackNN` = Steam "Track N"), PCM
  signed 16-bit little-endian, 44100 Hz, stereo, ~328 MB / 32:30 in total.
  Durations match the MP3s to the second.
- Idempotent: tracks whose WAV is newer than the MP3 are skipped; each file is
  written as `*.wav.part` and renamed only after success, so an aborted run
  never leaves a truncated WAV behind. The script ends with a format/duration
  summary and exits non-zero if any track failed.
- `data/mods/classic_music/` is **git-ignored** (copyrighted music). It is a
  separate folder from `classic_original/` because `ant classic-assets`
  regenerates that one from scratch.
- The script also writes the pack descriptor two levels above `-Target`
  (the pack root, so `-Target` must end in `resources\music`): `mod.xml`
  (`<mod id="classic_music"/>`) and `resources.properties`, **each only if
  missing**. The latter holds the one line that picks the title piece,
  `sound.classic.music.title=resources/music/track01.wav`, and
  `sound.classic.music.tracks=resources/music`. Re-running the converter
  therefore keeps a hand-edited title line; `-TitleTrack N` rewrites just that
  line on purpose (e.g. `-TitleTrack 5` → `track05.wav`). The script ends by
  printing the current title line. How the game plays the pack:
  `src/net/sf/freecol/client/gui/classic/README.md`, "Music (original
  soundtrack)".

## Runtime (in-game) extraction — future

Because the decoder is plain Java with no external dependencies, the same
classes can run in-process. The planned end state (plan item A5) is an in-game
install picker that decodes on demand instead of at build time; this build-time
target remains as the simple, scriptable path.
