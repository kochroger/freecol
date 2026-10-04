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

Output: `data/mods/classic_original/` — `mod.xml`, `resources.properties`, and
`resources/images/{pik,ss,ff}/*.png`.

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
3. Writes `resources.properties` exposing every frame under a stable
   `image.classic_original.*` key namespace, plus `mod.xml` and messages.

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

## Runtime (in-game) extraction — future

Because the decoder is plain Java with no external dependencies, the same
classes can run in-process. The planned end state (plan item A5) is an in-game
install picker that decodes on demand instead of at build time; this build-time
target remains as the simple, scriptable path.
