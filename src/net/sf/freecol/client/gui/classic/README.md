# Classic UI (`net.sf.freecol.client.gui.classic`)

A primitive, original-1994-*Colonization*-style view for FreeCol, built as an
alternative `GUI` implementation on top of the unchanged engine. This document
is the **implementation reference** for the code in this package — the durable
"how it works / why" that outlives the phase-by-phase tracker in the repo-root
[`CLASSIC_UI_PLAN.md`](../../../../../../../CLASSIC_UI_PLAN.md) (which tracks
*remaining* work and the broader asset/rules tracks).

## What this is & how it is selected

FreeCol is cleanly layered: `common/model` (UI-agnostic game state), the
`client/control/*` controllers (reach the view only via `getGUI().<method>`),
and the view facade `client/gui/GUI.java` — a **concrete base class with no
abstract methods and no-op stubs** (used as-is in headless mode). A new UI is
therefore just another `GUI` subclass: unoverridden methods no-op, so the app
runs from day one and screens light up incrementally.

Selection point, `client/FreeColClient.java`:
```java
gui = headless ? new GUI(this) : classic ? new ClassicGUI(this) : new SwingGUI(this);
```
The `--classic` command-line flag picks `ClassicGUI`. The change to upstream is
purely additive: this package + a one-line selector change + the flag, so the
fork stays mergeable with `master`.

Run it (from the repo root, so `data/` is found):
```powershell
ant compile
java -Xmx2G -cp "build;jars/*" net.sf.freecol.FreeCol --classic --fast --no-intro
```
`--fast --no-intro` auto-starts a single-player game with no GUI clicks — the
fastest way to the in-game view. It starts at sea: the ship on an ocean patch.

## `ClassicGUI` — the view facade

`ClassicGUI extends GUI` and overrides only the methods it implements:

- **Lifecycle.** `startGUI` shows the main `JFrame` (or, with the pack, adopts
  the one `ClassicStartupScreen` opened a second after launch — see "Fast
  start") with the title screen
  (`ClassicMainMenuPanel`) in **passive** mode (the early window already
  shows the live menu, except for `--fast` or a save argument): the original title picture
  `OPENMENU.PIK`, no menu, no input — so `--fast` loads over a clean backdrop
  and its code path is unchanged. `showMainPanel` makes the menu live (see
  "Title screen & main menu" below); a private `teardownInGame()` lets the HUD
  and the title replace each other in both directions. `reconnectGUI(active, tile)` — the game-start hook fired
  by `FreeColClient.restoreGUI` — builds the in-game HUD
  (`installInGameHud`): one integer-scaled 320x200 canvas (`ClassicHudPane`)
  with the painted menu strip, the `ClassicMapViewer` and the `ClassicInfoPanel`,
  no `JMenuBar`, plus the explicit key map; then seeds the initial view
  state/focus. `quitGUI` disposes the frame. See "In-game HUD" below.
- **`installLookAndFeel(fontName)`.** The base `GUI` no-ops this, leaving
  `FontLibrary`'s main font null — which NPEs in FreeCol panels the classic UI
  still reuses (and did in the old reused `InGameMenuBar`).
  So the classic GUI overrides it to create the main font (and set the
  image-border scale factor; and installs the stock `JPopupMenu` `UIManager`
  defaults, `installClassicMenuDropdownDefaults`). It
  deliberately does **not** install `FreeColLookAndFeel`: that L&F swaps in a
  `PanelUI` that paints the parchment texture behind every `JPanel`, which would
  override the classic map's black fog.
- **Pre-game lobby = the new-game chain's last step.** The original has no
  lobby: the choices are made on the new-game screens before the server
  exists (see "New-game chain" below). `showStartGamePanel` therefore
  auto-launches single-player games (`player.setReady(true)` +
  `requestLaunch`; the base `GUI` no-ops the lobby, which would stall a new
  game at login). Before that it applies the nation the chain picked
  (`applyNewWorldNation`: switch, line-up, server-side verification), or
  aborts back to the title if the server did not assign it. Multiplayer
  no-ops.
- **View-state delegation.** `changeView(Tile|Unit|)`, `getViewMode`,
  `getActiveUnit`, `getSelectedTile`, `getFocus`/`setFocus`,
  `refresh`/`refreshTile` all delegate to the `ClassicMapViewer`, which *owns*
  the classic view state. `refresh`/`refreshTile` also call
  `invalidateMinimap()` (they are the model-change hooks).
- **Image libraries.** `getFixedImageLibrary`/`getScaledImageLibrary` must be
  non-null even at scaffold stage: `ActionManager` builds every `FreeColAction`
  regardless of the active view and several look up order-button icons from
  their constructors. One unscaled `ImageLibrary` serves both today.
- **`showColonyPanel` — the classic colony screen.** A click on an owned colony
  (and the automatic open when one is founded) shows the `ClassicColonyPanel` in a
  window of its own (the classic UI has no `Canvas` to host panels in). Only one
  colony screen is open at a time — opening another disposes the previous — and
  the whole thing is guarded so a failure degrades to a log line. See "Colony
  screen" below.
- **Dialog seams (`modalConfirmDialog` / `modalChoiceDialog` / `getNewColonyName`).**
  The classic dialogs are Phase 3, but three controller flows can't proceed
  without an answer, so these are wired now with plain (unstyled) Swing dialogs:
  `modalConfirmDialog` (e.g. the build-colony site warnings), `modalChoiceDialog`
  (e.g. which unit(s) to disembark from a laden ship) and `getNewColonyName`
  (which returns FreeCol's suggested name, made unique, rather than prompting —
  the base `modalInputDialog` still no-ops). Without these, founding a colony —
  and hence the colony screen — would be unreachable. All run their dialog on the
  event thread via `onEventThread` (controllers call from arbitrary threads).
  Phase 3 reskins them.

## `ClassicMapViewer` — the map

A `JPanel` that renders the map on a **plain rectangular grid** (where
`SwingGUI`'s `MapViewer` paints isometric diamonds) centred on a focus tile,
mirroring the original game. It owns the view state
(`viewMode`/`focus`/`selectedTile`/`activeUnit`) and holds a back-reference to
`ClassicGUI` so clicks/keys route through the `GUI`/controller path exactly as
`SwingGUI.clickAt`/`MoveAction` do.

**Map topology: square by default.** New games started from the Classic UI (the
title-screen chain, or `--fast` without a save) are played on the original's
square map, 58×72 (`Topology.SQUARE`, with the map options of
`MapGeneratorOptions.applyTopologyDefaults`). The map stores its topology and the
save carries it as the map's `topology` attribute, so a square game reloads
square without any switch. A map without the attribute (every older save, the
bundled `.fsm` maps) loads as isometric, as before; the isometric caveat below
applies to those. `-Dfreecol.topology=isometric|square` overrides the topology
of new games, for developers. FreeCol's standard GUI keeps starting isometric
games and logs a warning when it loads a square one, which it cannot draw.

**Projection** (square 48px cells = native 16px × `CLASSIC_SCALE` 3):
```
screenX = width/2  + (tileX - focusX) * TILE_W - TILE_W/2
screenY = height/2 + (tileY - focusY) * TILE_H - TILE_H/2
```
`tileAt(px,py)` is the inverse (via `Math.floorDiv`).

**Terrain rendering.** The original rectangular `TERRAIN.SS` tiles fill the grid
cleanly (no diamond gaps). Tiles are fetched at native 16×16 via
`ImageLibrary.getTerrainImage(type, x, y, SRC_SIZE)` and up-scaled
nearest-neighbour (`VALUE_INTERPOLATION_NEAREST_NEIGHBOR`) so the chunky classic
pixels stay crisp. Unexplored tiles are left black (classic fog). The
key→frame mapping lives in `tools/classic_assets/aliases.properties`. When the
asset pack is absent the same keys fall back to FreeCol's own (isometric) art.

**Unit & goods sprites.** The original *Colonization* unit map-sprites and goods
icons come from `ICONS.SS`, aliased onto FreeCol's own resource keys in the same
`aliases.properties` (all 194 `image.unit.model.unit.*` base+role keys and 22
`image.icon.model.goods.*` keys). Because Col1 draws every civilian colonist with
one generic map sprite and one sprite per *role*, the aliases collapse to a small
set of frames: a base colonist for all civilian types, shared soldier / dragoon /
scout / pioneer / missionary role sprites, one frame per ship, and wagon /
artillery / treasure / brave / regular. The full frame table is documented in the
"Units & goods" block of `aliases.properties`. Goods icons only surface on the
Phase-2 colony/Europe screens; the unit sprites render on the map immediately.

**`drawCentered` — cell-fit for both art styles.** `paintTile` draws the
settlement/unit sprite through `drawCentered`, which sizes it to the cell
(preserving aspect) by branching on source size — which doubles as pack
detection. The original `ICONS.SS` sprites are ~16px, so they fit *inside* the
48px cell and are **up-scaled** to `UNIT_CELL_FRACTION` (0.9) of it,
nearest-neighbour like the terrain (else a 16px unit renders tiny). FreeCol's own
pack-absent art is sized for its 128×64 tiles, larger than the cell, and is
**shrunk** to fit as before. `paintTile` draws the settlement sprite the same way,
through `getScaledSettlementImage` (native ~16px, so the up-scale branch fires),
mirroring `getScaledUnitImage` — *not* `getSettlementImage(…, TILE_SIZE)`, which
would pre-size to 128×64 and bypass the crisp branch. Per-nation unit/colony
tinting and a fortress-distinct colony frame are documented follow-ups in
`CLASSIC_UI_PLAN.md`.

**Settlement sprites.** Colony and native-settlement map-sprites are aliased onto
FreeCol's `image.tileitem.model.settlement.*` keys in the same `aliases.properties`
(the "Settlements" block). Native settlements key off the settlement-type id
(`camp`/`village`/`inca`/`aztec` = `ICONS.SS` `010`/`011`/`012`/`013`). Colonies are
subtler: `ImageLibrary.getSettlementKey` builds `…colony.<size>[.stockade|.fort|
.fortress]` and then *prefers a per-nation key when one exists* — and the base pack
defines one for every European nation — so a neutral `…colony.small` alias would
never be reached. The block therefore overrides all 8 nation suffixes directly,
folding Col1's real signal (fortification) onto FreeCol's size×stockade grid:
unfortified `003`(small)→`000`(medium/large), stockade `001`, fort/fortress the
stone `002`. See the "Settlements" block of `aliases.properties` for the full table.

**Edge scrolling.** A mouse-motion listener sets an edge direction when the
cursor enters a ~1-tile hot zone at any window edge/corner; a repeating `Timer`
pans the focus by a **raw** rectangular-grid step while the mouse stays there
(stopped on `mouseExited`). Raw `(x,y)` steps, *not* `Direction.step` — the
isometric N/S step jumps two raw rows. Suppressed while the mouse is over the
minimap box.

**Controller wiring (clicks & keys drive the real `InGameController`).**

- **Click** ports `SwingGUI.clickAt`: unexplored → `setFocus`; owned colony →
  `showColonyPanel`; owned unit → `changeView(unit,false)` (make active);
  foreign unit/settlement → `setFocus`; empty explored tile → `changeView(tile)`
  (TERRAIN-select). A single click already terrain-selects (the rectangular grid
  has no drag-vs-click ambiguity), which also arms the TERRAIN cursor keys.
  Minimap-region clicks are intercepted *before* this (see below).
- **Keys** mirror `MoveAction`: arrow / numpad 8-2-4-6 orthogonal, numpad
  7-9-1-3 & Home/PageUp/End/PageDown diagonal, bound `WHEN_IN_FOCUSED_WINDOW`.
  MOVE_UNITS → `InGameController.moveUnit(activeUnit, dir)`; TERRAIN → step the
  selected-tile cursor to `getNeighbourOrNull(dir)`; nothing selected (END_TURN)
  → raw-grid free pan so the map stays navigable. After a unit move the focus
  follows the unit.
- **⚠️ Isometric-vs-rectangular caveat (resolved; isometric maps only).** On an
  isometric map, model `Direction` is isometric (`Direction.N` steps two raw
  rows), but this viewer draws a raw grid.
  The four orthogonal keys therefore resolve — parity-aware, via
  `Map.getDirection` — to the `Direction` whose *raw* step lands on the visually
  adjacent cell (e.g. straight-up is `NE` on even rows, `NW` on odd), so
  on-screen movement matches the key. The four diagonal keys map to the
  isometric corner directions, whose raw offset shifts with row parity — an
  inherent flattening artefact, documented on `intentToDirection`. On a square
  map the same `Map.getDirection` lookup gives `N`/`E`/`S`/`W` and the true
  diagonals, so there is no artefact.
- **Turn controls (classic-*Colonization* key scheme).** Because there is no menu
  bar / `Canvas` to install FreeCol's own accelerators, the viewer binds the
  turn-control keys directly (`WHEN_IN_FOCUSED_WINDOW`, like the movement keys),
  driving the real `InGameController`. Keys follow the original 1994 game (from its
  manual — see `bindTurnControls`):
  - **Enter** → `endTurn(false)` — end the turn. `false` because the classic `GUI`
    no-ops modal dialogs, so `endTurn(true)`'s "units still active" confirm would
    misbehave. Verified live: the turn advances (AI players process, a new turn
    begins) and the map/minimap refresh.
  - **Space** → "no orders": skip the active unit for this turn, mirroring
    `SkipUnitAction` (`changeState(unit, SKIPPED)` then `nextActiveUnit()`). With no
    active unit, Space ends the turn instead (as in the original, where Space
    advances the turn once every unit is done).
  - **W** → wait: `InGameController.waitUnit()` — cycle to the other units needing
    orders and return to this one.
  - **B** → build colony: `InGameController.buildColony(activeUnit)`, mirroring
    `BuildColonyAction` (the original manual: "To build a colony, press the build
    key (B)"). Guarded by the same precondition as the action's `shouldBeEnabled`
    (`hasTile() && canBuildColony()`), so pressing B with a ship or a spent unit
    selected quietly does nothing. The controller does the rest — it confirms any
    site warnings (`modalConfirmDialog`), names the colony (`getNewColonyName`) and
    on success opens the colony screen. This is the only way to reach the colony
    screen live, since `--fast` starts at sea; the flow is **sail to land →
    disembark a colonist → press B**.
- **Next-active-unit at turn start (caveat).** After `endTurn`, whether a fresh
  active unit is auto-selected is up to the controller's `setCurrentPlayer` →
  `updateActiveUnit` → `changeView(unit)` path, which the classic `GUI` already
  delegates to `changeToMoveUnits` (so it *does* re-centre on the next active unit
  when `player.hasNextActiveUnit()`). Movement was verified live post-end-turn
  (the active ship moves and the focus follows it); note that panning the focus
  away (e.g. a minimap click) leaves the active unit's cursor off-screen until a
  movement key re-centres on it.

**Minimap raster.** A whole-map overview: a plain rectangular
`map.getWidth() × map.getHeight()` raster — **no** isometric projection (unlike
`client/gui/panel/MiniMap`, which is the colour-source reference only). The map
viewer *builds and caches* it (it is map data); it is **drawn by
`ClassicInfoPanel`**, which hosts the minimap at the top of the right column as
the original does. (It began as a bottom-left overlay on the map itself; the
"HUD minimap" slice moved it into the info panel — see "In-game HUD" below; it is now the original's 56x39 window at 1 px per tile, built by `ClassicHud.minimapOf`, and this raster is unused by the panel.)

- Colours: unexplored → `ImageLibrary.getMinimapBackgroundColor()`; explored →
  `getMinimapPoliticsColor(tile.getType())`; a tile with a settlement/unit →
  the owner's `getNationColor()`. All guarded with fallbacks (`orElse`).
- Sizing: integer pixels-per-tile `max(1, MINIMAP_MAX/max(w,h))` fits the raster
  into a ~200px box, so a tall/narrow map renders as a vertical strip.
- **Caching / performance:** the raster is cached in a `BufferedImage` and
  rebuilt only when `invalidateMinimap()` marks it dirty — wired to
  `ClassicGUI.refresh`/`refreshTile`, the model-change hooks (exploration, new
  settlements, unit moves). `getMinimapImage()` returns the cache, rebuilding if
  stale; the panel blits it every repaint, so no per-repaint tile iteration.
- **Accessors for the panel:** `getMinimapImage()`, `getMinimapPixelsPerTile()`,
  `getViewHalfCols()`/`getViewHalfRows()` (the visible tile span, for the panel's
  viewport box) and `recenterOnTile(x,y)` (a minimap click → `setFocus`). The old
  in-viewer `paintMinimap`/`minimapClick`/`minimapBounds` and the edge-scroll
  suppression over the overlay box were removed with the move.

**Feature overlays (item (e)).** On top of the base terrain, `paintTile`
composites the per-tile *physical features* — forest trees, hills, mountains,
rivers, roads, plowed fields, resource markers and the lost-city rumour — via
`ClassicTileArt`, before the settlement/unit sprite. This is the last
map-fidelity slice, and its crux was **asset shape, not code**:

- **The overlays live in `PHYS0.SS`, not `TERRAIN.SS`.** `TERRAIN.SS` holds
  only the 12 base terrains; the original game drew a cell as a base tile plus
  square 16×16 *overlay* sprites, and those are a separate 154-frame set,
  `PHYS0.SS`. Being square (like the base tiles), they composite onto the
  classic rectangular cells with **no skew** — which is exactly why FreeCol's
  own isometric-diamond overlay art could not be reused here.
- **Frame map** (verified by pixel edge-analysis of the extracted frames, see
  the `ClassicTileArt` class comment for the table): all the *directional*
  feature sets share one 4-bit connectivity encoding — the frame within a set is
  `(E?1) | (W?2) | (S?4) | (N?8)` over the neighbours that also carry the
  feature — with bases minor-river `0`, major-river `16`, mountains `32`,
  hills `48`, forest `64`. Roads are composited instead: frame `80` is the
  centre hub and `81..88` the eight directional spokes (N…NW clockwise), one
  drawn per neighbour with a road. `103` is the lost-city rumour, `149` the
  plowed field, `89..102` the resource markers (a provisional read pending the
  expert's validation).
- **Connectivity is over *raw-grid* neighbours** (the cells drawn directly
  up/down/left/right and at the corners), not FreeCol's isometric
  `Direction`s, so a feature blends with whatever is *visually* adjacent on the
  square grid. Area features (forest / hills / mountains) use the four cardinal
  neighbours. Rivers are linear and FreeCol lays them along the isometric
  long-sides — which flatten to raw *diagonals* — so each diagonal neighbour
  with a river folds into its two adjacent cardinal bits, keeping a
  diagonally-running river visually connected. Roads draw a spoke toward each of
  the eight raw neighbours that has a road.
- **Loading & fallback.** The frames are exposed by the `classic_original` pack
  under the keys `image.classic_original.ss.PHYS0.SS.NNN` and loaded straight by
  key (cached), so no new alias entries are needed. When the pack is absent
  (`packPresent` false) `ClassicTileArt` falls back to FreeCol's own
  `getForestImage` / `getSizedOverlayImage` / `getRiverImage` — imperfect
  (isometric-shaped) on the square grid but enough to keep the build running.
- **Live-verification note.** Forest compositing was confirmed live by sailing
  the start ship to a coast (`--fast` starts at sea, so land must be reached to
  see overlays). Rivers/hills/mountains/roads share the *identical* draw path
  and 16×16 square sprites, so they render the same way; the classic UI's
  reconnect stopgap makes FreeCol's debug "reveal map" ineffective (it resyncs
  via a reconnect the classic UI does not fully reload), so inland features are
  reached by sailing rather than revealed.

**Coastline (beach feathering) — replaced with a procedural foam blend
(2026-08-06), no longer sprite-based.** The water side of the coastline was
originally fed by the original game's own `PHYS0.SS` coast quarter-tiles (32
8×8 sprites, `4 corners × 8 configs`, one drawn per cell quadrant based on
which raw-grid neighbours were land — see git history prior to this change for
the full decode: frame layout, the black colour-key transparency quirk, the
`COAST_CORNERS` per-corner offset table). That reverse-engineering was real,
but a live side-by-side comparison against the actual reference screenshots
(`screenshots/initial/opening_007.png`) found the *extracted* frames render a
scattered green fleck along the wave crest that the original never shows — it
draws a clean, fairly desaturated grey/white foam fringe hugging the coast
instead. That's a likely palette/extraction fidelity issue in the asset pack
rather than a bug in the compositing code (confirmed live: short-circuiting
the land-side blend entirely left the green fleck pixel-for-pixel unchanged,
proving it wasn't coming from this package's own blending logic).

Rather than ship a fringe that doesn't match the source material, `paintCoast`
and its supporting `COAST_CORNERS`/`COAST_BASE`/`COAST_LAST`/`keyOutBlack`
were removed from `ClassicTileArt`, and the water side of the coastline is now
painted procedurally by `ClassicMapViewer.blendWaterBorders`/`foamEdge` — the
same call Q7 already made for the land side once it turned out there was no
faithful land-land border sprite to source either (see below). The
estuary/river-mouth pieces (`140..147` ocean corner-hints, `150..153` diagonal
sand strips) were never wired regardless of this change.

**Land/land and land/water tile borders — dithered edge-blend (Q7, fixed
2026-08-05, retuned through 2026-08-06).** `paintCoast` only ever ran for water
cells; two adjacent **land** tiles of different `TileType` got no feathering at
all, so a bare (non-forest/hill) tile like Prairie rendered as a perfectly flat,
hard-edged 48px rectangle against its neighbours — see
`screenshots/ui-square-tiles-bug.png` for the original live capture (four adjacent
tiles, each a flat unblended square) versus any original reference shot (e.g.
`screenshots/initial/opening_007.png`), which never showed this. Live-clicking through
`--classic` confirmed the root cause: a "Prärie" tile (zero overlay) sat next to a
"Mischwald" tile whose substituted base texture
(`image.tile.model.tile.mixedForest.center` → `TERRAIN.SS.002`, plains) is visually
near-identical to Prairie's own (`TERRAIN.SS.003`) — so what read as one large
hard-edged block was actually two different `TileType`s with no blending between
them, not a forest-connectivity issue.

No dedicated land-land border/transition sprite sheet exists to source (every
extracted `.SS` archive under `tools/classic_assets` output was checked — `TERRAIN.SS`
is 12 base frames only, `PHYS0.SS` covers forest/hills/mountains/rivers/roads/
resources/coast and nothing else), so the fix is procedural rather than a sprite
lookup: `ClassicMapViewer.blendLandBorders`, called from `paintTile` right after the
base terrain is fetched and before `ClassicTileArt.paintOverlays` composites the
feature layer, checks each raw-grid cardinal neighbour for land of a *different*
`TileType` (or water) and, where true, replaces up to a `BORDER_BAND`-pixel-wide band
along that edge (in native 16×16 sprite space, before the ×3 `CLASSIC_SCALE`
up-scale) with the mirrored pixel from the neighbour's own base texture. Only a land
tile's own cached terrain image copy is touched (`copyImage`); the overlay
compositing is untouched (the water-side coastline mechanism this paragraph
originally cross-referenced, `paintCoast`, no longer exists — see "Coastline
(beach feathering)" above).

**Two blend mechanisms, not one — split by how forgiving the colour contrast is.**
`blendLandBorders` branches on `neighbour.isLand()`:

- **`ditherEdge` (land-land).** Each candidate pixel is gated independently on
  `hashNoise` (a cheap integer hash of its *world* pixel coordinate, so the scatter
  is stable across repaints — no flicker — without repeating tile-to-tile) against
  `BORDER_DENSITY` (`0.45`, tapering to 0 over `BORDER_BAND` rows). The first cut
  used a 2×2 Bayer matrix, but side-by-side comparison against the expert's
  reference shots showed the original's land borders as sparse, uneven speckling —
  a small repeating matrix instead read as a visibly regular checkerboard band,
  denser and more uniform than the reference — hence the switch to per-pixel noise.
  Independent per-pixel scatter reads as organic texture noise here because
  neighbouring *land* textures are close enough in colour value that an isolated
  swapped pixel still looks like part of the texture.
- **`blendCoastEdge` (land-water).** The same independent-scatter approach, tried
  first for water neighbours too, read badly: water is such a high-contrast colour
  swap from any land tone that isolated swapped pixels showed up as "flooded"
  potholes — scattered water-coloured pixels sitting alone inside solid land,
  disconnected from the actual coastline, flagged by the expert from a
  side-by-side screenshot against the original. So the land-water case gates *per
  lateral position along the edge* instead of per pixel: each position gets one
  noise-derived incursion depth in `[1, BORDER_BAND]`, filled solid from the edge
  inward, so the result is a wavy but *contiguous* line — strictly water past it,
  strictly land before it — never an isolated pixel. `COAST_GAP_PROBABILITY`
  (`0.5`) controls how often a position gets only the minimal 1-pixel depth versus
  reaching further inland; row 0 (the pixel right at the shared edge) is **never**
  skipped, so the coastline can vary how far it reaches but never gaps back to a
  hard land/water step — an early version gated row 0 on this probability too,
  which left roughly half of all edge positions with a literal hard 1px step right
  at the shore (confirmed both by a standalone reproduction of the algorithm
  against synthetic tiles, and by a before/after pixel diff of a live coastline
  screenshot showing the fix landing exactly on the edge row, nowhere else) before
  being corrected to always touch row 0.
- Both share `edgeCoords`, the row/lateral-position → own-pixel/neighbour-pixel
  coordinate math, deduplicated out of the original single-mechanism `ditherEdge`.
  It does **not** assume the neighbour's returned sprite shares the tile's own
  width/height: `ImageLibrary.getTerrainImage` only honours the requested size when
  the source sprite's aspect ratio already matches it — `ImageUtils.wildcardDimension`
  otherwise preserves the *source's* own aspect ratio to avoid distortion, true for
  every square `TERRAIN.SS` land frame but not guaranteed for water's source art.
  Reading a differently-shaped neighbour with the tile's own indices threw
  `ArrayIndexOutOfBoundsException` on every repaint of a coastal tile in an earlier
  round — caught by `FreeColClient`'s uncaught-exception handler (so the process
  didn't crash outright, but the map view never advanced past its loading
  placeholder) and found live via `FreeCol.log`, not by inspection. `edgeCoords`
  now scales the along-edge axis proportionally into the neighbour's own span and
  clamps the depth axis into it, so an odd-shaped neighbour degrades to a coarser
  sample instead of an out-of-bounds read.

**Water side — `blendWaterBorders`/`foamEdge` (2026-08-06).** Mirrors the land
side's `blendLandBorders`, called from the same `paintTile` step for water
tiles instead: for each raw-grid cardinal neighbour that is land,
`foamEdge` blends the sampled foam colour (`FOAM_R`/`FOAM_G`/`FOAM_B`, `(150,
155, 160)`, sampled directly from the coastline in
`screenshots/initial/opening_007.png`) into the water tile's own edge pixels,
peaking at `FOAM_MAX_ALPHA` (`0.6`) right at the shared edge and tapering to 0
over `FOAM_BAND` (`2`) native pixels — narrower than the land side's
`BORDER_BAND`, matching how thin the original's own fringe reads at native
resolution. Unlike the land-side blends, this **alpha-blends over** the
existing water pixel rather than replacing it outright: there is no neighbour
art to stay faithful to (a water tile has no land-coloured pixels worth
sampling), so lightening the existing water colour reads as a foam highlight
sitting on top of it, the same way the original's fringe looks like
whitecaps over water rather than a distinct layer. A per-pixel `hashNoise`
factor varies the alpha (within `0.7×`–`1.0×` of the row's peak) so the line
reads as an uneven natural highlight instead of a ruler-straight stripe;
unlike `blendCoastEdge`'s land-side incursion, there's no "flooded" failure
mode to guard against here — a lighter-than-usual water pixel never reads as
an isolated hole — so `foamEdge` has no gap/depth machinery, just a
continuous line of varying intensity.

Verified live against `screenshots/ui-square-tiles-fixed.png`/`-crop.png`/`-coast.png`
at the same map location as the original bug capture: coastline feathering,
forest/hill overlays and the composited tree canopy all render unchanged on top of
the blended base, land-land dithering still reads as the same sparse, approved
speckling, the land/water boundary is a continuous line with no isolated pixels, the
water-side foam line reads clean with no green fleck, and
0 uncaught exceptions in `FreeCol.log` for the session. See
[land-tile-borders.md](../../../../../../../classic_ui_plan/land-tile-borders.md) for
the full history (including the round-by-round expert feedback that produced the
split-mechanism design and the coast-frame removal) and
[Q7, Resolved](../../../../../../../classic_ui_plan/ui-phases.md#open-questions-for-the-expert).

## In-game HUD (menu strip, dropdowns, right panel)

(`ClassicHudPane`, `ClassicMenuStrip`, `ClassicMenuBar`, `ClassicMenuModel`,
`ClassicKeyMap`, `ClassicHud`, `ClassicInfoPanel`.)  This replaces the old
"Phase 2 HUD": FreeCol's reused `InGameMenuBar` (restyled Swing menus with a
gold/tax/score/year line) and a 240-px Swing info strip with order buttons.
The in-game screen now *is* the original's: every pixel of the strip, the
open dropdowns and the right panel is reproduced from the pack and diffed
against native captures with **0 differing pixels** (Steam
`dosbox_windows\capture\opening_000..005`, `032`; start-sequence `052`,
`053`).

### One 320x200 canvas (`ClassicHudPane`)

`ClassicGUI.reconnectGUI` → `installInGameHud` makes a `JLayeredPane` the
frame's content pane, with **no `JMenuBar`**.  Like the title screen it is one
integer-scaled canvas: `S = max(1, min(W/320, H/200))`, centred, the rest
black (`ClassicHudOverlay.scale/canvas`, shared with the first scene's glass
pane, so the scene lies exactly on it).  In 320x200 pixels:

| part | rect | component |
| --- | --- | --- |
| menu strip | (0,0,320,8) | `ClassicMenuStrip` (palette layer) |
| map | (0,8,240,192) | `ClassicMapViewer`, `setFixedScale(S)` |
| right panel | (240,8,80,192) | `ClassicInfoPanel` |
| dropdown | whole canvas | `ClassicMenuStrip.dropLayer()` (popup layer, visible only while a menu is open) |

`ClassicMapViewer.setFixedScale` makes `scale()` return S (bypassing
`MIN_SCALE`), so the map shows the original's **15x12 tiles of 16 px** on the
same grid as strip and panel, and anchors the focus tile in view column 7,
row 6 (`ClassicHud.UNIT_COL/UNIT_ROW`; the original keeps the unit in row 6,
083/032) instead of the half-tile-centred adaptive layout; `tileAt` follows.
The original's edge clamping of the view and its unit shadow/flags on the
map are still a follow-up (see "Open" below).

Strip, dropdown and panel each paint their 320x200 pixels into an off-screen
picture with the static painters and blit it up nearest-neighbour
(`ClassicMenuStrip.blit`), so the screen shows exactly what the headless
harness diffs; the harness's check `M4` paints the live Swing strip at scale 3
and compares it with the painters (0 px).

### The strip (`ClassicMenuBar`)

- Chrome: `WOODTILE.SS.000` (the GAME box fill) tiled from (0,0) over rows
  0..6, a black row 7 (032/000/052, also the first scene's band 083/049).
- Titles: MENU.TXT's six title lines (`@GAME @VIEW @ORDERS @REPORTS @TRADE
  @PEDIA`; `@CUP` is the cheat menu and never shown), FONTTINY, glyph top 1,
  green `0x559634`, the '~' letter gold `0xC7A220`, no shadow.  x: the first
  at 13, each next 14 px after the previous one's end, the last right-aligned
  13 px before the edge: widths 26/34/34/32/30/44 → x 13, 53, 101, 149, 195,
  263 (measured, 0 px on 032/000/052).
- Open title: a flat `0x3C2018` rectangle `(x-1, 0, w+4, 7)` (001-005, 053).
- Without MENU.TXT (an old pack) the strip shows wood only, one INFO line asks
  for `ant classic-assets`; the keys still work.

### Dropdowns

- Box: `y = 9`, `x = titleX - 1`, outer width = the widest item of the
  **whole** MENU.TXT section + 10 (every capture agrees, also when context
  hides items), height `8·slots + 5`; pushed left to stay on screen (assumed
  for COLONIPÄDIE, no capture: x 243, width 77).  Measured: SPIEL
  (12,9,100x109), ANSICHT (52,9,112x133), BEFEHLE (100,9,105x117), BERICHTE
  (148,9,110x109), HANDEL (194,9,92x29).
- Painting is `ClassicMenuBox.paintDropdown` (wood from the box corner, black
  ring, separator = green line at slot top + 3, bar `0x3C2018` at
  `(x+2, top, w-4, 7)`, text at x+5) plus the new **greyed rows**: every glyph
  of a grey row, its '~' letter included, in `DISABLED_INK = 0x555555`
  (001: the forest-clearing and the road order; 141 grey pixels, nothing green).
  Only a BEFEHLE order whose wired action is disabled is grey (see below);
  every other row keeps the normal ink, as in every capture.
- Separators: MENU.TXT has none; `ClassicMenuModel` holds the groups and draws
  a separator between two groups that both list something (an empty group
  adds none).  SPIEL [0,1][2,3][4,5][6][7,8]; ANSICHT [0-2][3][4,5][6-9][10,11];
  BEFEHLE [0-4][5-9][10-12][13-16][17][18,19] (hidden groups inferred);
  BERICHTE [0][1-4][5-8][9]; HANDEL and COLONIPÄDIE one group (the latter
  unknown).

### Items → engine actions (`ClassicMenuModel`)

Tracked code holds only section names and item indices (0 = first item after
the title); the words come from the pack.  How a row LOOKS and what it DOES
are separate (`ClassicMenuModel.Slot.greyed` / `.enabled`):

- **Usable** (`isLive`) = the item has an action, the action is enabled
  (`FreeColAction.isEnabled`, which already follows the active unit) and its
  classic seam is not a no-op (`NOOP_SEAMS`).
- **Grey** (`isGreyed`) = only a BEFEHLE order whose action is wired and
  disabled right now — the original's "does not fit the unit's situation"
  (001: clearing without tools, a road where one exists).
- **Inert** = normal ink but not usable: no engine equivalent, or a no-op
  seam.  The bar may sit on it (Alt+G bars SPIEL row 0, options, as in 053);
  firing it closes the menu and shows the one-page classic notice
  `classic.mainMenu.notYet` ("Diese Funktion folgt in einer späteren
  Version.").

Why: the captures draw every SPIEL/ANSICHT/BERICHTE/HANDEL row in normal
ink, also rows that cannot be used — 053 (1492) shows "declare
independence" in normal ink.  The first version greyed every inert row
(7 of 9 SPIEL rows grey in 1492); an independent review diffed that against
053/003/004 at 837/862/788 px.  The harness now renders the live rule (M1,
M4) and gets 0 px.

| menu | item → action |
| --- | --- |
| SPIEL | 0 `preferencesAction`ⁿ, 1 —, 2 —, 3 —, 4 `saveAction`ⁿ, 5 `openAction`ⁿ, 6 `declareIndependenceAction`ⁿ, 7 `retireAction`, 8 `quitAction` |
| ANSICHT | 0 `toggleViewModeAction` (fires only in TERRAIN mode, key M), 1 `toggleViewModeAction` (fires only outside it, key V), 2 `europeAction`, 3 `findSettlementAction`ⁿ, 4 `zoomInAction`, 5 `zoomOutAction` (both disable themselves: `GUI.canZoomInMap` is false), 6-9 —, 10 —, 11 `centerAction` |
| BEFEHLE | 0 `clearOrdersAction`, 1 `waitAction`, 2 `fortifyAction`, 3 (second fortify line: hidden, context unknown), 4 `sentryAction`, 5/6 `buildColonyAction` (no colony / colony on the tile), 7 `clearForestAction` (forest), 8 `plowAction` (no forest), 9 `roadAction`, 10 `loadAction` (carriers), 11 `unloadAction` (carrier in a colony), 12 — (armed land units), 13/14 `gotoAction`ⁿ (ship / land), 15 `assignTradeRouteAction`ⁿ (carriers), 16 — (ships), 17 `skipUnitAction`, 18 `unloadAction` (ship at sea: dumps cargo), 19 `disbandUnitAction` |
| BERICHTE | 0 —, 1 `reportReligionAction`, 2 `reportCongressAction`, 3 `reportLabourAction`, 4 `reportTradeAction`, 5 `reportColonyAction`, 6 `reportNavalAction`, 7 `reportForeignAction`, 8 `reportIndianAction`, 9 `reportHighScoresAction` (the hall of fame, not the live score: earlier README decision) |
| HANDEL | 0-2 `tradeRouteAction`ⁿ (FreeCol's one panel does all three) |
| COLONIPÄDIE | 0 `colopediaAction.goods`ⁿ, 1 `.units`ⁿ, 2 `.terrain`ⁿ, 3 —, 4 `.buildings`ⁿ, 5 `.fathers`ⁿ, 6 `.concepts`ⁿ, 7 — |

ⁿ = in `NOOP_SEAMS`: drawn in normal ink but **inert** until a classic screen
exists (`showClientOptionsDialog`, `showSaveDialog`, `showLoadSaveFileDialog`,
`showDeclarationPanel`, `showFindSettlementPanel`, `showSelectDestinationDialog`,
`showTradeRoutePanel`, `showColopediaPanel` are still the base `GUI` no-ops).
— = **no engine equivalent**, drawn like the original (normal ink) and always
inert: colony-report options, sound options, choose music, the four
zoom-level presets, show hidden terrain, F1 terrain information, pillage,
back to Europe, colonist skills, complete Colonipädie.  Zoom in/out are wired
but disable themselves; outside BEFEHLE that only makes them inert, not grey.

**Visibility** (manual + 001): BEFEHLE lists only the orders that apply to
the active unit's kind and tile (`ClassicMenuModel.Context`: unit, naval,
carrier, armed, forest, colony on the tile, view mode); with no active unit
only 0, 1 and 17.  001 (a colonist without tools on a forest road, no
colony) gives exactly 0 1 2 4 | 5 7 9 | 14 | 17 | 19 with 7 and 9 greyed.
The other menus list every item (004 shows both view-mode items green).

### Interaction (`ClassicMenuStrip`)

- Mouse (001-005 were opened with the mouse: no bar): a press on a title
  opens its menu (or closes it if open); moving over a normal-ink row bars
  it; releasing over one fires it (press-drag-release works too); a press
  anywhere else closes the menu and is swallowed (the drop layer covers the
  canvas while open).
- Keys (053 was opened with Alt+G: row 0 barred): Alt + a title's gold
  letter (G V O R T C) opens it with the first normal-ink row barred (SPIEL:
  row 0, as in 053).  While open a `KeyEventDispatcher` takes every key:
  Up/Down move over the normal-ink rows (skipping grey ones: assumed),
  Left/Right switch menus (assumed), Enter fires, Escape or a bare Alt
  (press and release with nothing between) closes, a row's gold letter or
  its printed key (F2, Shift-D, ...) fires it, Alt + another title letter
  switches; everything else is swallowed.  Only VK_A..VK_Z count as letters
  (`ClassicMenuStrip.letterOf`): the numpad and F-key codes equal letters'
  codes (VK_NUMPAD2 = 'b', VK_F1 = 'p'), and reading them as letters fired
  BEFEHLE orders from the movement keys while a menu was open (fixed; tested
  in `ClassicMenuBarTest.testKeyLetters`).  Alt+Enter / Alt+F4 stay the
  frame's (`FrameKeys` runs first).  A key in another window closes the menu.
- Firing closes the menu, then on a later event `action.actionPerformed(new
  ActionEvent(strip, ACTION_PERFORMED, id))` if still enabled -- the old order
  buttons' pattern; an inert row shows the "follows later" notice instead
  (`Host.unavailable`).  `ClassicGUI.closeMenus` (a base no-op) closes it;
  `updateActions`/`updateMenuBar` repaint strip, dropdown and panel.
- While the first scene is up the strip ignores input (`Host.inputBlocked`).

### Keys (`ClassicKeyMap`) — why a table is needed

FreeCol's accelerators only fired because its `JMenuBar` was installed (Swing
dispatches menu-item accelerators through the window's menu bar).  Without it
every key but the map viewer's own would have died, so the original's keys
are bound explicitly, `WHEN_IN_FOCUSED_WINDOW` on the HUD pane, **skipping
every keystroke the map viewer binds** (`ClassicMapViewer.BOUND_KEYS` and its
live input map: arrows, numpad, Enter, Space, W, B) — with two bindings for
one key Swing's `KeyboardManager` would decide by registration order, and the
map must keep winning as it did over the old accelerators.

| key | action |
| --- | --- |
| A F S L | clearOrders, fortify, sentry, load |
| U, O | unload (both `unloadAction`; each only where its BEFEHLE row is listed: U in a colony, O for a ship outside one — `Binding.when`, `contextAllows`; FreeCol's action would otherwise unload a whole ship on O in a colony, or dump cargo at sea on U without asking) |
| T, G | assignTradeRoute, goto (no-op seams: ignored for now) |
| Shift+D | disbandUnit |
| P, R | clearForest else plow, road |
| M / V | toggleViewMode, only from TERRAIN / only from MOVE_UNITS |
| E Z X C | europe, zoomIn, zoomOut, center |
| F2..F9 | Religion, Congress, Labour, Trade, Colony, Naval, Foreign, Indian |
| F10 | HighScores |
| Shift+F1 / F2 / F4 | Cargo / Exploration / Production (FreeCol's keys, no original entry) |
| Shift+F7 | **Military — moved off F7** (now Naval, as in the original): owner decision flagged |
| Ctrl+N | FreeCol's `newAction`: **back to the classic title** (asks first, `confirmStopGame`; then `showNewPanel` → `showMainPanel`). No original key: the original leaves a game only by retiring or quitting to DOS. Kept because dropping the `JMenuBar` had removed every in-game way back to the title or to a second game. |

This ends the old F7 Naval/Military clash and replaces
`remapClassicReportAccelerators` (deleted, with `styleClassicMenuBar`,
`styleClassicDropdownSeparators` and `MENU_BAR_BG/FG`).
`installClassicMenuDropdownDefaults` is kept: harmless, it still styles any
stock `JPopupMenu`.  Dropped with the `JMenuBar`: every FreeCol accelerator not
in the table (e.g. Ctrl+S, whose save dialog is a no-op anyway; Ctrl+Q, F11
turn report, F12 requirements, Shift+F3 history, Shift+F5 education, Ctrl+C
center).  Ctrl+N is the one brought back (above).

### The mouse arrow (`ClassicPointer`)

083/049 (first scene), 032/052 (play) and 001-005/053 (open dropdowns) all
show the original arrow `CURSOR.SS.000`; until now only the title canvas drew
it and the game showed the Windows pointer.  `ClassicPointer` is a
transparent layer as large as its host that draws the arrow on the
letterboxed 320×200 grid at the canvas scale (drawn, not a custom system
cursor: Windows caps those at 32×32, see "Why the arrow is drawn into the
canvas" above), clipped to the canvas, hot spot = sprite origin.

- HUD: the top layer of `ClassicHudPane` (DRAG_LAYER, above the dropdown).
  It never takes a mouse event (`contains` is false) and follows the mouse
  through an `AWTEventListener` for its own window.  Over the canvas it
  draws the arrow and gives the HUD pane (whose children inherit it) a blank
  cursor; in the letterbox, outside the window, while another window has the
  mouse (a classic dialog, the colony/report windows) and after the window is
  deactivated it draws nothing and the system arrow returns — never no
  pointer, never two.  It starts from `MouseInfo` and is held back while the
  first scene is up.
- First scene: the overlay has its own `ClassicPointer` child, placed at
  (160,100) when the scene appears (the original parks the mouse there:
  083/049) until the mouse first moves; only real motion moves it.  The real
  pointer is not moved (no `Robot`), so after the scene the HUD arrow is
  wherever the mouse really is.
- Limits: the drawn arrow moves on the EDT, so it pauses while the EDT is
  blocked (the short synchronous server calls).  Each move repaints two
  arrow-sized rectangles; the map viewer repaints its tiles under them.
- Harness: S1 now paints the scene through `ClassicHudOverlay.paintOverlay`
  with the overlay's arrow at `SCENE_X/Y`; H1/H2/M1 draw the arrow through
  `ClassicPointer.paintArrow` at the mouse position found in each capture;
  M4 draws it on the x3 live strip.  All 0 px.

### The right panel (`ClassicHud`, `ClassicInfoPanel`)

All in 320x200 pixels, 0 px on 032 (Dutch merchantman with a veteran soldier
and a pioneer aboard), 052 (English caravel, same cargo) and 000 (late game:
settler on a forest road, hardy pioneer, fortified veteran dragoon; minimap
terrain copied from the capture).  Green `0x559634`, gold `0xC7A220`.

- Chrome: black column x 240 (y 8..199), `WOODTILE.SS.000` over 241..319
  from phase (0,0) (not `WOODPANL`); minimap frame: 1-px `0xAA5500` ring
  (251,8,58,41), interior (252,9,56,39) black where unexplored; black row
  y 49.
- Minimap: 1 px per tile, explored ocean `0x202C8A`, own units/colonies in the
  nation colour, land in FreeCol's minimap colours (the 000 land colours are
  not mapped yet).  Window: x origin 1 when the map is ≤ 58 wide (else
  scrolls, assumed); y so the white 15x12 viewport ring sits at rows 13..24,
  clamped (032/052/083: ring (293,22); 000: y 26).  The ring shows the map
  viewer's actual view (`ClassicMapViewer.viewOrigin`).  A click in the
  interior recentres the map.
- Season (242,51): NAMES `@SEASONS` + year; gold (242,58):
  `label(CTITLE,1) + gold + "$  " + label(CTITLE,9) + " " + tax`, clipped at
  x 319 only.
- Active unit (hidden in scene mode): icon cell (242,68) — black silhouette
  shifted (-2,0), then the flag (7x9 black ring, 5x7 nation fill, the
  `@ORDERS` letter in FONTTINY at ring+(2,2), black for '-', else the dark
  shade: Holland FF7100/AA4900, England FF0000/AA0000, France/Spain FreeCol's
  colour and 2/3 of it, unmeasured), then the sprite at cell + 3 (widths
  6/7/13) or + 2 (8/14), others centred (unmeasured).  Flag at the cell's
  top-left for sprites ≥ 13 wide (ships, mounted), else at
  `(sprite.x + w - 2, y + 7)`.
  Lines: (260,70) `@INFO 0` + moves ("N k/3" for fractions: assumed);
  (260,77) `@INFO 1` + " (x, y)" (FreeCol tile coordinates: assumed mapping);
  (242,86) `@NATIONALITY` + `@UNIT` row (by role for colonists, by type
  otherwise); then 7 apart from y 93: the colonist's skill `@JOB` (gold), the
  tools "(" + n / tools word + ")" (gold, 007), the orders `@ORDERS` (gold),
  "(" + terrain + ")" and "(" + road + ")" (green).
- List (cargo of a carrier, else the tile's other units): first sprite 10
  below the last active line, text at x 260 from sprite.y + 4, all gold:
  the veteran word (misc 64) for a veteran in a military role, the expert word (misc 4)
  + tool count for a unit in its own skill (hardy pioneer; scouts and
  missionaries assumed alike), else the bare tool count, else the skill of a
  colonist without a role or with a non-free-colonist skill; the tools word
  on its own line after a count; lines 7 apart; the orders 6 below the last;
  the next sprite 8 below the orders (at least 18 below the sprite:
  assumed); entries that would cross y 199 are left out (overflow unknown).
- Removed: the order buttons, the Enter/Space/W footer, the Swing fonts, the
  `WOODPANL` chrome and the 240-px width.  `repaintInfo` hooks unchanged.

`ClassicHud.UnitFacts.of(Unit, sprite)` turns a FreeCol unit into plain facts
(`@UNIT`/`@JOB` rows, qualifier, tools from the role's required goods, orders
from the unit state: sentry, fortify/fortified, improving → plough/road,
trade route, destination → goto); the painter never touches the model, so the
harness builds the captures' facts by hand.

### Sprite aliases (corrected)

The panel draws `ICONS.SS` sprites 1:1, so the captures pin them down
(`tools/classic_assets/aliases.properties`, re-run `ant classic-assets`):
free colonist without a role 100 (was 058), free colonist pioneer 073 (was
081), hardy pioneer as pioneer 101 (081), free colonist soldier 074 (089),
veteran soldier 102 (089), veteran dragoon 104 (076).  These also change the
map, colony and Europe screens — check them live.  Other colonist types keep
the older guesses.

### Harness, tests, open

- Scratch harness `...\scratchpad\departure\preview\run.ps1 -only hud|menus`
  (never committed: it renders original words): H1 strip 032/000/052, H2
  panel 032/052/000, M1 dropdowns 003/053/004/001/002/005 through the LIVE
  rule (`ClassicMenuModel.slots` over action states: all enabled, 053
  without independence, 001 without clearing and road; the bar from
  `ClassicMenuStrip.nextSelectable`), plus 003/053/004/002/005 again with
  every action disabled (ink must not change outside BEFEHLE) — 0 px each,
  the arrow drawn by `ClassicPointer.paintArrow` where the capture has it;
  M2 `colonipaedie_review.png` (no capture); M3 reports the 001 rule
  ('+' usable, '~' inert, '-' grey); M4 the live Swing strip opened with
  Alt+G on a 1492 host, at scale 3, plus the arrow = capture 053, 0 px.
- JUnit: `ClassicMenuBarTest` (title x, highlight, boxes incl. the clamp, slot
  counts, the 001 and other BEFEHLE contexts, the item → action table, the
  live rule, the grey rule, the bar on row 0, key letters vs numpad/F-keys,
  the view-mode rows, hotkeys, greyed rows), `ClassicHudTest` (unit lines
  and list positions of 032/000/007 on synthetic texts, flag rings, sprite
  offsets, unit tables, the HUD layout, the key map: no duplicate, no map
  key, P/G/M/V rules, U/O context, Ctrl+N), `ClassicTextTest` (`menu`,
  `label`).
- Open / assumed: the map viewer's original framing (edge clamping), unit
  shadow and order flags on the map; minimap land colours and horizontal
  scrolling; fractional moves; the position line's coordinate base; list overflow;
  COLONIPÄDIE position and groups; the second fortify line; whether the
  keyboard bar skips greyed rows and Left/Right switch menus; the four
  Military/Production/Exploration/Cargo keys; the original's starting soldier
  is a veteran only on FreeCol's two easiest levels (`expertStartingUnits`).

## Colony screen (`ClassicColonyPanel`)

The signature original screen (design ref: the expert's `opening_016/017`,
"Northern Sugar"). Everything is painted into a virtual **320×200** canvas (the
original's VGA size) then up-scaled by the largest integer factor that fits the
window, nearest-neighbour — so the layout constants read straight off the
screenshots and the classic pixels stay crisp. Reached by clicking an owned
colony on the map (`ClassicMapViewer.onClick → gui.showColonyPanel`) or founding
one with **B**; hosted in its own `JFrame` (no `Canvas`). Layout, top to bottom:

- **Title bar** — colony name, turn and gold, gold-on-black.
- **Buildings pane** (left) — the colony's buildings on the sandy ground
  (`TERRAIN.SS.001`, tiled), each drawn from the original **`BUILDING.SS`** sprite
  set with the colonists working inside it and a black production tag
  (`amount` + goods icon). Names show **on hover** only (as in the original),
  which also keeps them from overlapping; the hover targets are the building
  bounds recorded during paint. *This slice flows the buildings left-to-right in
  rows rather than at the original's fixed ground positions — a documented
  deviation pending the expert's slot map.*
- **Tile pane** (right) — the **3×3 work-tile grid** on the wood panel
  (`WOODTILE.SS`), the colony in the centre cell and its eight neighbours around
  it. Each cell is placed by the work tile's compass `Direction` from the colony
  (`cellForDirection`), **not** its raw `(x,y)` offset — FreeCol's map is
  isometric, so the eight neighbours' raw offsets do not fill a −1..+1 square
  (the same isometric-vs-rectangular gotcha as the map viewer; placing by raw
  offset left cells empty). Each cell draws the same terrain art as the map, plus
  the colonist working it (green-boxed) and its production tag.
- **Bottom band** — the original **`COLONY.PIK`** chrome (320×72) blitted as-is,
  with the live figures over it: the SoL/tory split and the units standing in the
  colony (left), the ships in port or the empty-dock caption (middle), the net
  production (right), and the 16-slot **warehouse** row of goods icons + amounts
  along the very bottom. The red **"E"** at the bottom-right (part of the
  `COLONY.PIK` art) and **Escape** both close the screen.

**Provisional `BUILDING.SS` frame map (`BUILDING_FRAMES`).** Which of the 48
frames is which building was read off a labelled montage by eye. The clearly
distinct sets are certain (fortification walls; dock/drydock/shipyard; the sooty
blacksmith chain; the churches; the banner town hall), but several
interchangeable house/shop/factory chains are best-effort — hence the hover name,
which lets the expert spot a mis-mapped sprite and correct the table from a shot.

**Verified live (2026-07-14):** sailed the start ship to land, disembarked a
pioneer (the multi-unit disembark choice dialog now works), founded a colony with
**B** (the site-warnings confirm dialog now works), clicked it — the colony
screen renders (buildings, the filled 3×3 grid with the colony centred, the
`COLONY.PIK` band with SoL/port/production/warehouse), hover shows building names,
Escape closes it. 0 SEVERE.

### The build queue (`ClassicBuildQueuePanel`) — the first hard blocker, closed

Until this slice, `GUI.showBuildQueuePanel(Colony)` returned `null` unconditionally (an unoverridden
no-op): a colony could accumulate hammers/tools but the player had no way to ever tell it what to
build — not "rough," a genuine dead end for actually playing a game to a finish.

**The seam.** `ClassicGUI.showBuildQueuePanel` opens `ClassicBuildQueuePanel` in a window of its own
(the same one-window-at-a-time, guarded-`SwingUtilities.invokeLater` convention as every other classic
screen), positioned relative to the colony screen when one is open. **Reached by clicking the
construction indicator** — a new dark, gold-bordered band at the top of the buildings pane (above the
first building row, which shifted down `CONSTR_H` to make room) showing the colony's current build
target's icon, name and the goods still needed, or a "nothing being built" caption — mirroring
FreeCol's own `ConstructionPanel`, whose click likewise opens `showBuildQueuePanel` (same seam, same
click target, just reused rather than invented). ⚠️ The construction band **must paint after**
`paintBuildings`, not before: `paintBuildings` unconditionally re-fills the *entire* ground rectangle
(`fillTiled` over `AREA_Y..BAND_Y`) as its first step, which silently erases anything drawn earlier in
that region — this bit the first cut of this slice (the band was invisible) before the paint order was
swapped in `paintComponent`.

**The picker itself is deliberately not FreeCol's `BuildQueuePanel`.** That panel is a
drag-reorderable multi-item queue (MigLayout lists, `TransferHandler` drag-and-drop) — a modern
convenience the 1994 original never had. The original offered a simple list of what is *currently*
buildable and you picked one thing at a time, so `ClassicBuildQueuePanel` mirrors that instead: every
`BuildableType` (building or buildable unit — wagons/artillery/ships are buildable too) the colony can
legally build **right now** — `Colony.canBuild(BuildableType)` alone is sufficient filtering, since it
already excludes buildings already built/mid-upgrade, population/ability/limit shortfalls and
non-coastal mismatches, the same checks FreeCol's own panel runs by hand — listed with its icon
(`ImageLibrary.getSmallBuildableTypeImageWithWithSize`, the same lookup FreeCol's own row renderer
uses), name and remaining required goods (`Colony.getRequiredGoods`, icon+amount tags, right-aligned).
The colony's current pick is highlighted green; **clicking a row calls
`InGameController.setBuildQueue(colony, List.of(picked))` — replacing the queue with that one item —
and closes the screen**, exactly like choosing from the original's build menu. No reference screenshot
of the original's actual build-selection screen has surfaced (checked `screenshots/`), so the plain
list-over-a-dark-plate look is a considered placeholder in the same gold/green palette as the other
screens, not a faithfulness claim — an open item for the expert, like the popup metrics.

**The refresh gap this surfaced.** `InGameController.setBuildQueue`'s `updateGUI` only refreshes the
map controls and menu bar (`gui.updateMapControls()` / `gui.updateMenuBar()`) — it never calls
`GUI.refresh()`. Left alone, the colony screen behind the build-queue window would keep showing the
*old* construction indicator after a pick until some unrelated event (ending the turn, reopening the
screen) forced a repaint. So `showBuildQueuePanel`'s close callback also calls the same `repaintInfo()`
every other model-change hook uses — and, since the colony screen was never wired into that hook at
all before now (only the info panel and Europe screen were), this slice adds a `colonyPanel` field
and a `refresh()` method to `ClassicColonyPanel`, mirroring the Europe screen's existing
`europePanel.refresh()`, so the colony screen now also repaints on the general `refresh()`/
`refreshTile()`/`changeView*` path, not just after a build pick.

**Verified live (2026-07-24):** founded a colony, opened the build queue from the construction band —
the list rendered real hammers/tools costs (`Schmiede 64🔨/20🔧`, `Lagerhaus 80🔨`, …) with **Anlegestelle**
(Docks, the colony's real default pick) highlighted; picked **Lagerhaus** — the band updated to
"Lagerhaus" immediately on close (confirming the refresh fix, not just the underlying `setBuildQueue`
call); reopened and picked **Schmiede** — updated live again. 0 SEVERE throughout.

**Follow-ups (later slices, need the expert's sign-off):** validate/correct the
`BUILDING.SS` frame map; the original's fixed building ground-slots (vs. our
flow layout); per-nation building/flag tints; a reference shot for the
build-selection screen's actual look (Q-worthy, see above). Loading cargo and
caption localization are both closed — see "Cargo & set sail" and "Caption
localization" below.

### Work assignment (drag interaction) — the colony-screen blocker, closed

Until this slice, a colonist could be *founded into* a colony (via `B` on the map) but never told
*where* to work once inside: the colony screen only rendered the buildings/work-tile grid and let you
pick a build target, so a newly arrived colonist had no path to a job without leaving the screen and
finding some other, non-existent seam.

**The seam.** `InGameController.work(Unit, WorkLocation)` — `Building` and `ColonyTile` both implement
`WorkLocation`, so the one call handles moving a colonist into either. It already claims an unowned
tile and confirms abandoning education when needed, so `ClassicColonyPanel` calls it directly with no
extra guarding, the same way `boardShip` needed none for Europe boarding.

**Click-to-select, click-to-target — not drag-and-drop**, continuing the pattern of every other
classic screen (order buttons, report rows, the build queue, Europe boarding). `ClassicColonyPanel`
gained a `selectedUnit` field: clicking any colonist sprite on the screen — standing idle in the
colony (the population panel's own unit row, the very case that was previously a dead end), already
working a building, or already working a tile — selects it (a second click on the same unit
deselects); while one is selected, every building and every non-centre work-tile cell gets a gold hint
border, mirroring Europe's `BOARD_HINT` treatment of ships during boarding. Clicking a building or
tile then calls `InGameController.work(selectedUnit, target)` and clears the selection.
`paintWorkers` (used by both the buildings pane and the work-tile grid) and `paintPopulation` now
record each drawn colonist's virtual-space bounds + unit into shared `unitBounds`/`unitTargets` lists,
rebuilt once per paint (unlike `buildingBounds`, which only one method populates, these three
populating methods all run within a single `paintComponent` pass, so the lists are cleared once at
its top rather than per-method). `paintBuildings`/`paintWorkTiles` separately record `buildingTargets`/
`tileTargets` parallel to their existing bounds lists as the click-to-move targets.

**The refresh gap, again.** `InGameController.work`'s `updateGUI` has the same shape as `setBuildQueue`
and `boardShip` — it only refreshes map controls and the menu bar, never calls `GUI.refresh()` — so
`assignWork` calls the panel's own `refresh()` after the controller call, unconditionally (matching
`ClassicEuropePanel.boardSelected`), rather than leaving the screen showing the colonist in its old
spot until an unrelated repaint.

**Verified live (2026-07-25):** resumed the save with **Nieuw Amsterdam** (1 colonist, working the
Town Hall, producing 4 bells); clicked the Town Hall's colonist — it gained a selection box and every
building/work-tile gained a gold hint border; clicked the chapel — the real server call fired and was
correctly *rejected* (`CAPACITY_EXCEEDED`, shown via the existing `showErrorPanel` popup — see Phase 3
— not a bug, a legitimate validation failure); re-selected the same colonist and clicked the NW work
tile instead — the colonist moved there live, the Town Hall's production tag dropped to 1 (base, no
worker) and the tile gained a "3" grain production tag plus a matching net-production entry in the
band, all without leaving or reopening the screen. 0 SEVERE throughout (only the pre-existing benign
first-launch `options.xml` warning and the expected `CAPACITY_EXCEEDED` client warning).

**Follow-ups:** the original's fixed building ground-slots remain a flow layout (Q5, unchanged by this
slice); loading cargo and per-nation tints are still open, tracked above.

## Europe screen (`ClassicEuropePanel`)

The original's home-port dock (design ref: the expert's `opening_009`–`013`).
Built exactly like the colony screen — painted into a virtual **320×200** canvas
and up-scaled by the largest integer factor that fits, nearest-neighbour, hosted
in its own `JFrame` (no `Canvas`). The original **`EUROPE.PIK`** harbour picture
(sky, sea, the wooden piers, the row of European town houses) is blitted as the
backdrop (loaded straight by its pack key `image.classic_original.pik.EUROPE.PIK`,
with a plain sea/sky fallback when the pack is absent); the live figures are drawn
over it. Layout:

- **Title bar** — port name, turn, tax and treasury, gold on black.
- **Action buttons** (top right) — the three golden buttons **Anwerben / Kaufen /
  Ausbilden** (recruit / purchase / train). Their virtual-space bounds + actions
  are recorded during paint so `onClick`/`onHover` can drive them (hover
  highlights). Each opens a **plain Swing choice dialog** listing the priced
  options and calls the **real controller** — recruit via
  `InGameController.recruitUnitInEurope(index)` over `europe.getExpandedRecruitables`,
  train/purchase via `trainUnitInEurope(unitType)` over the spec's
  `getUnitTypes{Trained,Purchased}InEurope` (cheapest first). This mirrors the
  standard `RecruitPanel` / `NewUnitPanel` exactly (both of those also route
  purchase through `trainUnitInEurope`). The dialogs are the same stopgap as the
  colony-founding seams; Phase 3 reskins them to the wood-framed look with the
  colonist portrait (`opening_011`–`013`).
- **Ships in port** — the naval units in Europe, floating on the water by the piers.
- **Units on the docks** — the land units in Europe, standing on the quay (wrapping
  onto a second rank).
- **Sailing rows** — the high-seas units split by heading (to-America vs to-Europe,
  keyed on `unit.getDestination() instanceof Europe`), each a caption + sprites.
- **Market row** — every storable good with its current sale price
  (`market.getPaidForSale`) along the bottom, on a dark plate.
- **Exit** — the red "E" at the bottom-right (part of the `EUROPE.PIK` art) and
  **Escape** both close the screen.

**Reaching it — the `updateActions()` fix.** The Europe screen is opened by the
reused `EuropeAction` (the **Europe** menu item, accelerator **E**) or
automatically when a ship arrives in Europe (the controller calls
`showEuropePanel`). The menu item is the intended trigger, but the reused
`FreeColAction`s were **stuck disabled** in the classic HUD: `SwingGUI` refreshes
their enabled state through the `Canvas` on every view change / panel open, and
the classic UI has no `Canvas`, so `EuropeAction` (and the map/turn menu items)
never re-evaluated `shouldBeEnabled` after construction. `ClassicGUI.updateActions`
(→ `FreeColClient.updateActions` → `ActionManager.update`) is now called on
`reconnectGUI` and every `changeView`, so the menu items enable correctly. Only
one Europe screen is open at a time; `updateEuropeanSubpanels` (called by the
controllers after a recruit/train) and the `refresh` hooks repaint it.

**Verified live (2026-07-14):** **E** opens the Amsterdam port (backdrop, title,
the three localized buttons, a colonist on the dock, the market row); Anwerben
lists the recruitable (`Schuldknecht (200)`), Ausbilden the cheapest trainable
(`Erfahrener Erzschürfer (600)`), Kaufen the cheapest purchasable
(`Artillerie (500)`) — each calling the real controller; Escape closes. 0 SEVERE.

### Boarding — the second hard blocker, closed

Until this slice, `ClassicEuropePanel` had click handling for its action buttons and the exit only:
nothing put a recruited/trained/purchased colonist standing on the dock onto a waiting ship. Once a
unit is already at sea or ashore, the *map's* ordinary movement already triggers real embark/disembark
and the high-seas "sail?" confirm (`InGameController.moveEmbark`/`moveTowardEurope`, reached through
`ClassicMapViewer`'s existing movement-key wiring) — it was specifically the Europe screen's own
dock↔ship interaction that was unwired, and without it no new colonist could ever reach the New World.

**Click-to-select, click-to-target — not drag-and-drop.** `InGameController.boardShip(Unit, Unit
carrier)` already does exactly what is needed (validates the unit/carrier share a location, asks the
server to embark, updates the GUI) and is directly callable — its Javadoc says "Called from
CargoPanel, TilePopup" (standard-UI seams), but nothing about it is standard-UI-specific. So
`ClassicEuropePanel` gained a `selectedUnit` field: clicking a unit on the dock selects it (a green
box, a second click on the same unit deselects), and while one is selected every ship in port gets a
gold hint border; clicking a ship then calls `boardShip(selectedUnit, ship)` and clears the selection.
This fits the click-driven style every other classic screen already uses (order buttons, report rows,
the build queue above) rather than introducing drag-and-drop as a new interaction paradigm this
codebase doesn't otherwise have. `paintPort`/`paintDocks` now record each sprite's virtual-space bounds
+ unit (parallel `Rectangle`/`Unit` lists, rebuilt every paint) the same way the action buttons already
did, rather than inventing a new hit-testing mechanism.

**Verified live (2026-07-24):** advanced turns until a ship arrived in port (`Auf dem Weg nach Europa`
→ docked) alongside a colonist already standing on the dock; clicked the colonist — a green selection
box appeared and the ship gained a gold hint border; clicked the ship — the colonist vanished from the
dock (boarded), the hint cleared. 0 SEVERE.

**Follow-ups:** the wood-framed dialog reskin (shared Phase-3 component); refining the dock/pier sprite
positions against the original. Cargo and set-sail, the two items this note used to flag as open, are
closed — see below. Caption localization is also closed — see below.

### Cargo & set sail — the Europe screen's last blocker, closed

Until this slice, `ClassicEuropePanel` had no way to move *goods* (as opposed to colonists) on or off a
ship, or to send a docked ship back to the New World — a ship could arrive in Europe and sit there
forever, since nothing in the screen itself could load it with cargo or start its return trip.

**Selection now does double duty.** `selectedUnit` (the same field boarding already used) now holds
either a dock colonist *or* a ship, disambiguated by `Unit.isNaval()`: clicking a ship in port calls the
new `selectPortUnit`, which boards a selected colonist onto it if one is selected (the existing
behaviour, unchanged) or otherwise selects/deselects the ship itself as the target for the actions
below — switching selection between a colonist and a ship is just overwriting the one field, no extra
state needed. While a ship is selected, every market-row good gets the same gold `BOARD_HINT` border
the boarding ships got while a colonist was selected, and the ship's own cargo hold renders as a strip
of goods icons (`paintCargo`, `Unit.getCompactGoodsList()`) in the gap above the piers — empty, and
undrawn, unless a ship is currently selected.

**Three click targets, three controller calls, all reusing established real seams:**
- **A market-row good** → `loadMarketGood` → `InGameController.buyGoods(type, amount, ship)`, capped at
  one `GoodsContainer.CARGO_SIZE` (100) per click — the same amount and the same call `MarketLabel`
  makes when a market icon is dragged onto the standard UI's `CargoPanel`.
- **A cargo icon on the selected ship** → `sellCargo` → `InGameController.unloadCargo(goods, false)`,
  which (since the carrier is in Europe) routes to `sellGoods` internally — the same call `GoodsLabel`
  makes when a cargo icon is dragged off a carrier.
- **The new fourth action button, "Segel setzen" (Set Sail)** → `setSail` → `InGameController.moveTo(
  ship, game.getMap())` — the literal "set sail" seam (Javadoc: "Called from
  EuropePanel.DestinationPanel"). Mirrors the standard (non-classic) Europe screen's own Set Sail
  button (`EuropePanel#sailAction`, key `S`) down to reusing its `setSail` i18n key, since no screenshot
  of the original's own set-sail affordance has surfaced — a placeholder in the same vein as the
  build-queue picker, open for the expert. It also mirrors that button's one safety check: if
  auto-load-emigrants is off and a colonist is still waiting on the dock, it confirms first (the classic
  UI's own wired `modalConfirmDialog`, same `europePanel.leaveColonists` template) before leaving them
  behind. Loading and selling keep the ship selected, so several goods types can be bought or sold in
  one visit — a deliberate difference from boarding/work-assignment's clear-after-one-click convention,
  since cargo is inherently a multi-item action even in the standard UI's own drag interface.

**A real bug caught live, not by inspection.** The first cut built the goods-to-load as
`new Goods(game, europe, type, amount)` and called `InGameController.loadCargo`, mirroring that
method's own doc comment ("branches on `goods.getLocation() instanceof Europe` → calls `buyGoods`
internally"). Live testing threw immediately: `Goods`'s constructor rejects any location whose
`getGoodsContainer()` is null, and `Europe` has no goods container — so a `Goods` located `Europe` can
never legally exist, and `loadCargo`'s Europe branch is (at least via this path) unreachable in
practice. Every real caller that buys goods in Europe (`MarketLabel`, `QuickActionMenu`) in fact calls
`buyGoods` directly rather than going through `loadCargo` — `loadMarketGood` now does the same, and the
crash is gone. Left as a loose thread for whoever next touches `InGameController`: `loadCargo`'s Europe
branch may be genuinely dead code.

**Verified live (2026-07-25):** opened Amsterdam with a ship in port and a colonist on the dock; clicked
the ship — green selection box, every market good gained a gold hint border; clicked a market good
(before the `buyGoods` fix, this threw the `Goods`-construction `RuntimeException` above — confirmed
gone after the fix, buyGoods correctly rejected the purchase for insufficient gold with no crash and no
stray hint left behind); clicked **Segel setzen** — the wood-framed confirm fired for real ("Sollen wir
die Segel nach Neuholland setzen und die Kolonisten zurücklassen?", ship portrait, `europePanel.
leaveColonists`, the first live trigger of this specific event-confirm dialog); confirmed — the ship
left port and reappeared correctly in the "Auf dem Weg nach Amerika" sailing row, the market hints
cleared, the colonist stayed behind on the dock as warned. Re-selected the dock colonist afterward (no
ship left in port) to confirm the dual-purpose selection still boards/selects correctly with an empty
port list. 0 SEVERE throughout except the pre-existing benign first-launch `options.xml` warning.

**Follow-ups:** selling/loading were only exercised on the reject path (the test save had 0 gold) — the
success path is un-exercised beyond code review, though it is a one-line delegation to the same
`unloadCargo`/`buyGoods` calls already proven elsewhere. The `loadCargo`-is-Europe-dead-code loose
thread above; the wood-framed dialog reskin (shared Phase-3 component, same as boarding); per-nation
tints.

## Report screens (`ClassicReportPanel` + concrete reports)

The original 1994 game's full-screen **advisor reports** (design ref: the
expert's `opening_014`/`opening_015` shots, plus the dedicated report-shot batch
in `screenshots/Berichte_fuer_Pascal/`). All **twelve** are built — the
original's ten, plus Labour and Foreign Affairs (both reversed-in / newly built
2026-07-24, see their own sections below) — sharing one frame.

**Key scheme.** The report keys follow the *observed* original F-key layout
(`00_BERICHTE-Menu_Tastenbelegung`, and the BERICHTE dropdown, Steam 002: F2
Religious, F3 Congress, F4 Labour, F5 Trade, F6 Colony, F7 Naval, F8 Foreign
Affairs, F9 Indian, F10 score) rather than FreeCol's own. Since the painted
menu strip replaced FreeCol's `JMenuBar` (see "In-game HUD"), the keys are
bound by `ClassicKeyMap`, not by remapping the actions' accelerators (the old
`remapClassicReportAccelerators` is gone; `FreeColMessages.properties` was
never touched). The four FreeCol reports without a Col1 counterpart keep a key:
Cargo Shift+F1, Exploration Shift+F2, Production Shift+F4 (their FreeCol keys)
and **Military Shift+F7** — moved off F7, which ends the old collision where F7
opened Military instead of Naval (Q6 in `classic_ui_plan/ui-phases.md`; the
Shift+F7 choice is flagged for the owner).

**The subsection headings below now show each report's *new*, post-remap key.**
The dated "Verified live" notes further down were captured *before* the remap
and describe the keys actually pressed at the time — read those as historical
record, not current bindings.

### Which reports are actually Col1's

Cross-referencing the ten reports built before this session against the
observed F-key menu: **confirmed matches** (right concept, previously the
wrong key) — Colony↔Kolonieberater(F6), Naval↔Flotteninspektor(F7),
Trade↔Wirtschaftsberater(F5), Religious↔Religionsberater(F2),
Congress↔Kontinentalkongress(F3), Indian↔Indianerberater(F9). **No confirmed
match in the expert's evidence:** Military, Production, Exploration, Cargo.
Worse, "Military Garrison" is *not* a separate top-level report in the observed
original — it is one of the two paged views *inside* the Colony Advisor (F6),
which our Colony Advisor already, independently, correctly cycles through (see
"Colony Advisor" below). So the standalone Military Advisor screen may be
duplicating something the original folds into Colony's paging, rather than
being its own report. **Not unilaterally reworked or removed** — recorded as
Q6 in the plan for the user/expert to decide.

### The shared frame (`ClassicReportPanel`)

`ClassicReportPanel` is the abstract base every report extends; it owns the
framing so a concrete report only supplies its backdrop, title and body. Like the
colony/Europe screens, everything is painted into a virtual **320×200** canvas
up-scaled by the largest integer factor that fits, nearest-neighbour, hosted in
its own `JFrame`. The base paints: the dimmed sepia `REPORTn.PIK` backdrop (loaded
by pack key, with a flat-sepia fallback when the pack is absent), the gold-on-black
**title bar** (localized report name), and the red **Okay** plate at the
bottom-right; **Escape** and clicking Okay both close it. Subclasses implement
`backgroundKey()`, `titleKey()` and `paintBody(Graphics2D)` (drawing the header +
rows between `ROW_Y0` and `BODY_BOTTOM`), and may override `onBodyClick(vx,vy)` for
extra click behaviour (default no-op). Shared drawing helpers (`drawFitted`,
`clip`, `font`, and `cap(key)` for captions) and the palette/layout constants live
on the base.

**Clickable colony rows.** A report that lists colonies calls
`addColonyRow(yBaseline, colony)` per row during paint (the base clears the hit
list each paint, before `paintBody`); the base then handles the rest — a click in a
row band closes the report and opens that colony's screen via
`getGUI().showColonyPanel(colony, null)` (a jump-to, as the original advisor does),
and the cursor turns to a hand over a clickable row. Wired in the Colony Advisor,
Production and Religious reports (the three that list colonies). The Okay-plate and
Escape close paths take priority over a row hit.

**Caption localization.** The reports' short captions — column heads, the Colony
Advisor's page subtitles, the Religious/Congress summary labels, the empty-state
lines — have no equivalent in the standard FreeCol UI, so they get their own keys
in an isolated `classic.report.*` block appended to
`FreeColMessages[_de].properties`, fetched through the base's `cap(tail)` helper
(`Messages.message("classic.report." + tail)`). The German block deliberately
gives the Colony Advisor its **original captions** — `classic.report.colony.sol`
= "Söhne der Freiheit", `classic.report.colony.military` = "Militärgarnision" —
matching `opening_015` / `opening_014`. The info panel's key-hint captions reuse
the existing `endTurnAction`/`skipUnitAction`/`waitAction` `.name` keys (the key
tokens Enter/Space/W stay literal, being our actual bindings).

**Verified live (2026-07-16, German locale, 0 SEVERE):** Trade heads render
"Waren / Netto / $"; Exploration "Region / Typ / Runde / Punkte"; Congress
"Rekrutierung / Glocken", "Gründervater / Kategorie", "Noch keine Gründerväter.";
the info-panel hints "Enter: Zug beenden / Space: Überspringen / W: Warten/Nächste
Einheit"; and the Colony Advisor pages between the two original captions —
**"Söhne der Freiheit"** and **"Militärgarnision"** — over "Noch keine Kolonien.".
Umlauts render correctly throughout.

**The colony/Europe screens turned out to already be fully localized** — an exhaustive literal-string
sweep of `ClassicColonyPanel`, `ClassicEuropePanel`, `ClassicBuildQueuePanel`, `ClassicReportPanel` and
every concrete report subclass (2026-07-26) found every paint-time string already routing through
`Messages.message(...)`, `Messages.getName(...)`, or `cap(tail)`. The one genuine hard-coded literal
outside those screens was `ClassicMapViewer.paintWaiting`'s pre-map-ready fallback string, drawn only in
the narrow `map == null || f == null` startup window before the map/focus are available. Fixed by adding
`classic.mapViewer.waitingForMap` next to the `classic.buildQueue.*` block. Two literals were deliberately
left alone: `ClassicInfoPanel`'s key-cap tokens (Enter/Space/W — our actual bindings, not translatable
concepts, per the comment above that code) and `ClassicGUI`'s `JOptionPane` title fallback to the
brand name "FreeCol" (a proper noun). Because `paintWaiting` fires only in a transient startup window,
this fix was verified at the code level (key resolves, `ant compile` clean, 0 SEVERE and no missing-key
warning in `FreeCol.log` at startup) rather than by live screenshot — the same standard already applied
elsewhere in this doc when a state can't reliably be forced (e.g. the Colony Advisor's >9-colony paging).

**Row geometry (the invariant to keep).** A row's cell spans `[y-ROW_H+3, y+3)`
about its baseline `y`, and sprites are drawn from `y-ROW_H+4` — i.e. a row
occupies space *above* its own baseline. So the first row's baseline must sit a
full row-pitch below the column heads: `ROW_Y0 = HEAD_Y + ROW_H`. (An earlier
`ROW_Y0 = TITLE_H + 12` put the first row's sprites *above* the head baseline, so
every report's heads collided with its first row — most visibly the Cargo report's
sprites.) The two reports with a summary block above their table
(Religious, Congress) repeat the same relation locally with their own
`TABLE_HEAD_Y` / `TABLE_Y0 = TABLE_HEAD_Y + ROW_H`.

> ⚠️ **These layout constants are `static final int`, so javac *inlines* them into
> every report class.** `ant compile` only recompiles changed sources, so changing a
> constant on the base silently leaves untouched subclasses running the **old**
> value (this bit us: after fixing `ROW_Y0`, `ClassicReportTradePanel` — the one
> file not otherwise edited — still painted with the stale `21`). **Run
> `ant clean compile` after touching any shared constant**, not just `ant compile`.

**Wiring.** Every `showReport*Panel` override routes through one private
`ClassicGUI.showReport(titleKey, factory)` helper: it disposes any open report
(`closeReportPanel` — **one report window at a time**), builds the panel via the
factory (passing the close callback), frames it, and is guarded so a failure
degrades to a log line. Reached by the reused report menu items, enabled by the
same `updateActions()` wiring the Europe menu item needed. The remaining
`showReport*Panel` seams still no-op.

### Colony Advisor (`ClassicReportColonyPanel`, F6) — the paged report

The "KOLONIEBERATER-BERICHT" over the sepia fort illustration (**`REPORT6.PIK`**).
This is the one report that, as in the original, **pages through several column
sets** — the behaviour the expert's two shots document (`opening_014`,
`opening_015` are the *same* report on different pages, not different reports).

Every page keeps the same **left column** — the colony's flag sprite
(`getScaledSettlementImage`), a **population badge** (the boxed `getUnitCount()`
number, as in the original) and its name — and swaps what is drawn to the right,
with a **subtitle** naming the current page. The pages (the `Page` enum):

- **Sons of Liberty** (`opening_015`, the original's "Söhne der Freiheit") —
  `getSonsOfLiberty()` %, the building producing the colony's bells
  (`getWorkLocationForProducing(liberty)`, the original's "Druckerei" column; a new
  colony shows its *Rathaus*/Town Hall), the bells per turn
  (`getNetProductionOf(liberty)`) as icon+amount, and one colonist figure per SoL
  member.
- **Military Garrison** (`opening_014`, "Militärgarnison") — the offensive land
  units standing in the colony (`tile.getUnitList()` filtered by
  `isOffensiveUnit() && !isNaval()`) as sprites; none → "—".

> **The original shows no column heads** — the subtitle names the page and the
> columns are self-evident — so this panel deliberately paints none (and starts its
> rows a row-pitch below the *subtitle* via its own `PAGE_ROW_Y0`, keeping the same
> "a row's cell is drawn above its baseline" invariant).

**Paging interaction — confirmed live (2026-07-24):** the expert's menu capture
showed the original cycles pages by **pressing F6 again**, not arrow keys/Space
(the earlier guess, now replaced). This is a *local* binding on the report's own
`JFrame` — it never contends with the main frame's global F6 accelerator, since
the report window is a separate top-level window. Okay/Escape close as
everywhere else. Rows that overflow are clipped (no scroll yet — the *within-a-
view* paging key for >9 colonies is still open, see Q2 in the plan); empty →
"No colonies yet."

### Unit rosters — Military & Naval (`ClassicReportRosterPanel`)

`ClassicReportRosterPanel` is a second small base (over `ClassicReportPanel`) for
the reports that tally units by type×role, mirroring FreeCol's own
`ReportUnitPanel`. A subclass supplies backdrop, title, an `isReportable(Unit)`
predicate and an empty caption; the base groups the player's units
(`player.getUnits()`), keeps a sample for the sprite, **sorts by descending count
then label** (the unit set is unordered, so this keeps the roster stable), and
paints one row per group: sprite, the localized `Messages.getUnitLabel(...)`
type/role label, and the count.

- **Military Advisor** (`ClassicReportMilitaryPanel`, **F7** — unchanged by the
  remap; see "Which reports are actually Col1's" above) — the standing army
  over the fort illustration (**`REPORT6.PIK`** — the fortification is the garrison
  image; shared with the Colony Advisor, a framing the expert may re-assign once
  REPORT9 has a home). Reportable = FreeCol's `ReportMilitaryPanel` set:
  `!isNaval() && (hasAbility(EXPERT_SOLDIER) || isOffensiveUnit())`. Empty → "No
  military units."
- **Naval Advisor** (`ClassicReportNavalPanel`, confirmed key **F7** — currently
  collides with Military above, see Q6) — the fleet over the ship
  illustration (**`REPORT7.PIK`**). Reportable = `unit.isNaval()`. Empty → "No
  naval units."

### Trade Advisor (`ClassicReportTradePanel`, F5)

The goods ledger over the scales/candle/hourglass illustration (**`REPORT5.PIK`**).
Every storable good (`spec.getStorableGoodsTypeList()`) as **icon | name | Net |
$**: `Net` is the empire-wide net production summed over all colonies
(`Σ colony.getNetProductionOf(gt)`), `$` the market sale price
(`market.getPaidForSale(gt)`). Two goods per row (each occupies one 160px half —
the column origins are *within* a half, added to `col*HALF`) so the 21-good ledger
fits the canvas.

### Religious Advisor (`ClassicReportReligiousPanel`, F2)

Crosses/immigration over the preacher-and-congregation illustration
(**`REPORT2.PIK`**). A summary block at the top — accumulated immigration
(`player.getImmigration()` / `getImmigrationRequired()`) and empire-wide cross
output (`getTotalImmigrationProduction()`) with the cross goods icon — then one row
per colony with the crosses it produces (`Σ colony.getNetProductionOf(gt)` over the
`spec.getImmigrationGoodsTypeList()`). Empty → "No colonies yet."

### Production Report (`ClassicReportProductionPanel`, shift F4)

The per-colony production breakdown over the colony-under-construction
illustration (**`REPORT4.PIK`**). Where the Colony Advisor shows each colony's
*two* largest outputs and the Trade Advisor sums production empire-wide, this
lists **every** good a colony nets positively, as icon+amount along the row
(`colony.getNetProductionOf(gt)` over the storable goods, clipped when the row
fills). Empty → "No colonies yet."; a producing-nothing colony → "—". Its
per-colony row logic is shared with the (data-verified) Colony Advisor.

### Continental Congress (`ClassicReportCongressPanel`, F3)

The founding-father standing over the two-men-at-a-desk illustration
(**`REPORT3.PIK`**). A summary block — who is currently being recruited
(`player.getCurrentFather()`) and the liberty-bell progress toward them
(`getLiberty()` / `getTotalFoundingFatherCost()`, `+getLibertyProductionNextTurn()`
/turn) — then the roster of fathers already in Congress
(`player.getFoundingFathers()`): portrait (`lib.getFoundingFatherImage`), name and
category (`father.getTypeKey()`), grouped by type. Empty → "No founding fathers
yet."

### Exploration Report (`ClassicReportExplorationPanel`, shift F2)

The discovered regions over the map-and-wax-seal illustration (**`REPORT8.PIK`**) —
the same data as FreeCol's own `ReportExplorationPanel`: every `map.getRegions()`
with a non-null `getDiscoveredIn()`, as name | type | turn | score, newest first
(by discovered turn then score). Unnamed regions fall back to their localized type
name. Empty → "Nothing discovered yet."

### Cargo Report (`ClassicReportCargoPanel`, shift F1)

Each carrier's load, over the ship illustration (**`REPORT7.PIK`**, shared with the
Naval Advisor). Where the Naval Advisor tallies the fleet *by type*, this is one row
per carrier — the reportable set from FreeCol's own `ReportCargoPanel`
(`isCarrier() || canCarryTreasure()`) — showing its sprite and name, then the goods
it holds (`getCompactGoodsList()`, icon+amount) and the units aboard
(`getUnitList()`, sprites), clipped when the row fills. Nothing aboard → "(empty)";
no carriers → "No carriers."

### Indian Advisor (`ClassicReportIndianPanel`, F9)

The contacted native nations, over the native-scout illustration
(**`REPORT1.PIK`**). One row per native nation the player has **contacted**
(`player.hasContacted`, the same filter FreeCol's own `ReportIndianPanel` applies):
the tribe's capital settlement sprite
(`lib.getScaledSettlementTypeImage(nationType.getCapitalType())`), its name, the
number of its settlements the player knows of, and its tension toward the player
(`tribe.getTension(player)`, via `model.tension.*.name`). Empty → "No tribes
contacted yet."

> Unlike FreeCol's panel this deliberately omits the tribe's *true* settlement
> total: that comes from `igc().nationSummary()`, which a static paint must not
> drive — see "The `nationSummary` trap" below (the same trap the Foreign
> Affairs report below had to solve). The locally-known count is shown instead.

**Verified live (2026-07-15, pre-key-remap — see "Key scheme" above for the keys
these reports answer to today):** at the `--fast` start (at sea, no colonies) all ten
render framed over their correct backdrops with 0 SEVERE — **F3** "No colonies
yet."; **F7** the starting `Soldat (Freier Kolonist) ×1`; **F8** the starting
`Handelsschiff ×1`; **F9** the full 21-good two-column ledger with live sale prices;
**F1** "Immigration: 0 / 19", "Crosses per turn: +0"; **shift F4** "No colonies
yet."; **F6** "Recruiting: (none)", "Bells: 0 / 40 (+0/turn)", "No founding fathers
yet."; **shift F2** the three regions already discovered at the start (*Acadie* T2
sc66, *Newfoundland* T2 sc38, *Chile* T1 sc74), newest first; **shift F1** the
starting ship *Salm (Handelsschiff)* with the colonist aboard (the other starting
unit is the pioneer already ashore, so it is correctly absent from the hold);
**F5** "No tribes contacted yet." All localize (German) via the reused message keys
and `Messages.getName`/`getUnitLabel`. Escape/Okay close each; opening another
report replaces the previous window.

**Verified with a live colony (2026-07-15).** The `--fast` start put the active
unit ashore as a **Pionier** (the documented variant), so **B** → confirm the site
warnings founded *Nieuw Amsterdam* directly, no sailing needed. With it standing:

- **F3** both pages, with the row data matching `opening_015`'s shape — *Sons of
  Liberty*: flag, pop badge `1`, `Nieuw Amst…`, `0%`, **`Rathaus`**, bells `4`; then
  **Right** → *Military Garrison*: same left column, `—` (the colonist works inside,
  so it is correctly not a garrison unit); **Right** again wraps back.
- **shift F4** *Nieuw Amster…* with its furs `+2`; **F1** *Nieuw Amsterdam* `+1`
  crosses — closing those two row loops' verification gap. 0 SEVERE throughout.

> **Verification caveat — the row loops still unproven with data.** **Congress**
> (needs a founding father) and **Indian** (needs native contact) have so far only
> run through their empty-state branch. Populate them when next testing near that
> code. Note founding a colony also puts a road on its tile and turns the founding
> pioneer into a plain colonist — that is the engine's own behaviour, not a bug.

### The `nationSummary` trap

`InGameController.nationSummary(player)` is **a blocking server round-trip, not an
async callback** — an earlier note here and in the plan said otherwise. It reads
`myPlayer.getNationSummary(other)` and, **on a cache miss, calls
`askServer().nationSummary(…)` and waits** for the reply before returning.

So it must never be called from `paintComponent`: that is network I/O on the EDT,
on every repaint. Any report needing it has to fetch **once, off the EDT, when the
screen opens**, stash the results, and paint from the stash — a different shape
from every report shipped so far, all of which paint straight off the model. This
is why the Indian Advisor shows the locally-known settlement count, and it was
the main structural work in building the Foreign Affairs report below.

### Foreign Affairs Report (`ClassicReportForeignAffairPanel`, F8)

The "AUSSENPOLITIK-BERICHT" — the last report the original actually has, and now
built, closing the report set. Over the map-and-wax-seal illustration
(**`REPORT8.PIK`**): confirmed pixel-for-pixel against the expert's capture, and
in the process found to be **already double-booked** with the Exploration Report
above, which had only guessed at that backdrop — a real correction the expert's
shots surfaced (recorded as part of Q6, since it also means Exploration's own
backdrop claim is now less certain, on top of Exploration having no confirmed
Col1 counterpart at all).

**Layout — one fixed-height block per European power**, met or not, alive or
withdrawn, in game order, with the **viewer's own nation always last** (verified
against the capture's own save). Read directly off pixel scans of the raw
320×200 captures (not the point-scaled human-readable copies, which stretch to a
4:3 CRT aspect and would give wrong constants): a `RULE`-coloured horizontal line
opens each 45px block, then five 7px-pitch lines — name, colonies/avg size/
population, military/naval/merchant strength, stance, rebels/loyalists. A
withdrawn power's block instead shows just its name and a centred notice
(`classic.report.foreignAffairs.withdrawn`).

**Fields — deliberately not FreeCol's nine.** Matches the capture's own set:
colonies, average colony size, population, military strength, naval strength,
merchant-marine strength, stance (peace **yellow**, war **red** — colour-coded,
confirmed from the capture's side-by-side peace/war comparison shot), and
rebel/loyalist head counts. **Omits** gold, tax rate, Continental Congress
membership and Sons-of-Liberty % — all of which FreeCol's own
`ReportForeignAffairPanel` shows and this does not, the same
information-availability call already made for the other reports. The viewer's
own block instead lists its **stance toward every met rival**, one pair per
slot (an unmet or withdrawn power gets no entry there, per the capture).

**The two fields `NationSummary` didn't have — a small, additive model
extension, not a rules change.** Merchant-marine strength and rebel/loyalist
counts have no equivalent on `NationSummary` (the DTO `nationSummary()`'s server
round trip returns), and a rival's true figures are only ever knowable through
that DTO — the client's local copy of a rival `Player` is deliberately
incomplete. So `NationSummary` gained two small additions:
`mercantileMarine` (`NationSummary.computeMercantileMarine`, sum of
`Unit.getCargoCapacity()` over a player's naval units) and `rebels`/`loyalists`
(derived from population and `Player.getSoL()`). Both are **always computed**,
unlike `soL`/`foundingFathers`/`tax` on the same class, which stay gated behind
`Ability.BETTER_FOREIGN_AFFAIRS_REPORT` exactly as before — untouched, because
the capture shows Col1 exposes the *new* fields to every rival unconditionally,
but says nothing about the *existing* gated ones, so there was no evidence to
justify loosening them. Purely additive: new fields with sensible defaults, no
existing field, gate, or XML schema changed; `SwingGUI`'s own
`ReportForeignAffairPanel` doesn't read them, so it is unaffected.

**Fetched off the EDT, per the `nationSummary` trap above.**
`ClassicGUI.showReportForeignAffairPanel` spawns a background thread that
builds the full list of European powers (`game.getPlayers`, filtered to
European/non-REF/not-self, **including dead ones** — the capture's withdrawn
England stays on the list) and calls `nationSummary()` for each live one *before*
building the panel; the finished stash is handed to
`ClassicReportForeignAffairPanel` on the EDT via `SwingUtilities.invokeLater`,
which only ever paints from it. The viewing player's own block is read directly
off the local, always-authoritative `Player` instead — no round trip needed for
your own data.

**Verified live (2026-07-24, populated 4-nation save, 0 SEVERE):** all three
rivals (France, England — withdrawn, Spain) plus the viewer's own nation
rendered; colonies/population/military/naval/merchant figures and rebel/loyalist
splits all matched the underlying model; England's withdrawn block showed the
centred notice with no stats; the own-block stance line correctly showed no
entries in an unmet-everyone save. War/peace colour-coding is coded per the
capture but not yet live-tested against an actual war (the test save had none).

### Labour Advisor (`ClassicReportLabourPanel`, F4)

The "ARBEITSBERATER-BERICHT" — **a reversed decision**: previously filed as one
of the four FreeCol-only reports (`optional-reports.md`), until the expert's
capture (`F4_Arbeitsberater_Labor`) showed the original has this exact screen —
a three-column census of every "person" unit type the player owns, portrait +
localized name + count, **grouped exactly as the capture groups them**, not spec
order and not an arbitrary three-way split of it: primary-good producers
(farm/plantation/mine/trap/lumber) on the left, building/processing experts plus
the fisherman and preacher in the middle, and the "special" civil/military/other
roles on the right (`COLUMN_UNIT_IDS`, three hardcoded id lists). Over
**`REPORT4.PIK`** (a new double-booking, shared with the Production Report — the
dockside/warehouse scene matches both). A type with zero units still gets a row
(the capture shows `Jesuitenmissionare 0`) — only types unavailable to the
player's nation/ruleset are skipped.

**Row geometry read directly off pixel scans of the raw capture** (not the
point-scaled copy, same caveat as Foreign Affairs above): each entry is two
stacked lines (name, then count) in an 18px pitch — noticeably tighter than the
15px `ROW_H` every single-line report shares, since two lines have to fit where
one normally does, so this panel defines its own `ENTRY_H`/`FIRST_Y`/`COUNT_DY`
rather than reusing the base constants.

> ⚠️ **Clipping-margin bug found and fixed during live verification.** The first
> cut tested a row's fit against `y + ENTRY_H > BODY_BOTTOM` — the same shape as
> every other report's clipping check — but that demands the *next* row's full
> slot also fit, not just the current row's own content (which only reaches
> `y + COUNT_DY`). It silently dropped the ninth row of both 9-entry columns
> (`Mitreißender Prediger`, `Freie Siedler`) even though they visually fit with
> room to spare. Fixed by checking against the row's own content extent instead.
> The Foreign Affairs report above had the identical bug for the same reason (its
> own block-height check demanded a full next-block's headroom) — it silently
> dropped the **viewer's own nation** in a fully-populated 4-nation save, until
> fixed the same way (`BLOCK_CONTENT_H`, not `BLOCK_H`). Both were only caught by
> testing with data that actually filled every row/block — an idle/empty save
> would never have shown either.

**Deliberately not built: the capture's own "click to zoom" drill-down.** The
subtitle "(Zum Zoomen Objekt anklicken)" promises a per-type detail view
(FreeCol's `ReportLabourDetailPanel` equivalent) this slice does not build, so
the subtitle itself is **not painted** — showing it would advertise an
interaction that silently does nothing on click. The row geometry above already
reserves the caption's vertical space, so adding the drill-down later is a
self-contained follow-up, not a relayout. Education, History and Requirements
are **unaffected** by any of this — the capture says nothing about them, and
they remain deferred in `optional-reports.md`.

**Verified live (2026-07-24, a save with a colony elsewhere on the map, 0
SEVERE):** all three columns render over the correct backdrop; the visible unit
(a Free Colonist standing alone) and units in an off-screen colony (a Master
Carpenter) both counted correctly, confirming the census aggregates
`player.getUnitSet()` empire-wide rather than just what's on screen.

### High Scores (`ClassicReportHighScoresPanel`, F10 — no accelerator in either scheme)

The score breakdown — "Kolonisationspunkte" in the German menu — over the
records-at-a-desk illustration reused from the Continental Congress
(**`REPORT3.PIK`**, a guess; see below). Reached by the already-live **Spiel →
Punktzahlrekorde** menu item (`ReportHighScoresAction` → `InGameController.
highScore(null)` → a server round trip → `highScoresHandler(key, scores)` →
`getGUI().showHighScoresPanel(key, scores)`, already dispatched via
`invokeLater`), not by any of the F-key report shortcuts — FreeCol's own scheme
has no accelerator for this seam at all, so unlike every other report there was
no key-remap question to resolve.

**Differs in shape from every other report.** `GUI.showHighScoresPanel(String
messageId, List<HighScore> scores)` takes its data as arguments rather than
reading the live model from `paintBody` — the caller has already made the
server round trip before the classic override is ever invoked, so (unlike the
Foreign Affairs report's `nationSummary` trap) there is nothing to fetch off
the EDT here; `ClassicGUI.showHighScoresPanel` builds
`ClassicReportHighScoresPanel` synchronously and routes it through the same
`showReport` helper every other report uses.

**One row per `HighScore`, in the order given** (already best-first, per
`HighScore.tidyScores`): rank, score, the same localized governor/president-
of-nation headline the standard `ReportHighScoresPanel` shows (`report.
highScores.governor`/`.president`, reusing its exact `%name%`/`%nation%`
template), the retirement turn, and colony/unit counts. The standard panel's
other fields — difficulty, independence turn, the original/final nation name
and type, the retirement date — are read directly off `HighScore` too (see its
full accessor set), but are **not shown**: a single 320×200 screen has no room
for that panel's full nine-field-per-entry stacked layout, the same
information-availability call already made for the other reports (e.g.
Foreign Affairs' own deliberate omissions, see above). `messageId` (the
"highscores.yes"/"highscores.no" result of a just-finished game, when present)
prints as a line above the table; the space for it is always reserved so the
table's position does not shift between the two cases. An empty list (e.g. a
fresh profile's just-created, empty `HighScores.xml`) shows the same "no
scores yet" empty-state treatment every other report uses.

**Open, unconfirmed guesses (flagged, not settled):** no `REPORTn.PIK` backdrop
is confirmed for this screen at all — `REPORT3.PIK` (otherwise single-booked by
the Continental Congress) was picked as the closest thematic fit among the free
single-use backdrops (`REPORT1`/`2`/`3`/`5`), not because any capture confirms
it; the extracted-but-unused closing-sequence art (`CLOS-BKG`/`CCBKGD`) was
considered and passed over as riskier to reuse sight-unseen. The five-column
row layout and the choice of which `HighScore` fields to show at all are
likewise placeholders in the same vein as the build-queue picker's look —
open for the expert.

**Verified live (2026-07-26, `--fast` start, 0 SEVERE):** Spiel → Punktzahlrekorde
opens the panel over the sepia `REPORT3.PIK` backdrop with the localized title
"Punktzahlrekorde"; a fresh profile's empty `HighScores.xml` (created on first
read, logged at INFO) renders "Noch keine Punktzahlrekorde." per the empty-state
path; Okay closes it cleanly back to the map. **The populated-row path (the
table header and a real row) was only verified at the code level, not live** —
forcing an actual finished/retired game wasn't practical in this session, the
same documented fallback this doc already uses for other hard-to-force states
(e.g. the Colony Advisor's >9-colony paging, "The colony/Europe screens turned
out to already be fully localized" above).

### Follow-ups (later slices)

The expert's sign-off on the Colony Advisor's **within-a-view** paging key for
more than 9 colonies (the *between-views* key is now confirmed — see "Colony
Advisor" above); row scrolling for many entities generally. (Captions are
localized and colony rows are clickable now — see "Caption localization" and
"Clickable colony rows" above.) Education, History and Requirements remain
deferred to
[`classic_ui_plan/optional-reports.md`](../../../../../../../classic_ui_plan/optional-reports.md)
as possible later additions of our own, not missing work. The High Scores
screen's backdrop and row/field choices are open guesses — see "High Scores"
above.

## Popups (`ClassicDialog`) — and the dispatch seams that stranded them

The shared wood-framed popup every classic dialog routes through — the plan's
"build it once" for Phase 3. Like the reports it paints a virtual pixel canvas
(240 wide, height computed from the content) up-scaled ×3 nearest-neighbour, over
`ClassicWood` grain: an optional illustration at the left, wrapped green-on-wood
text, a centred row of raised option plates. Hosted in a modal `JDialog` (the
classic UI has no `Canvas`). Two entry points share the painting:

- `showMessages(owner, title, pages)` — pages through *n* notices one at a time
  with a single **Okay** plate and an `i/n` counter. The original has no batched
  turn-report screen, so both message seams page rather than list.
- `ask(owner, title, page, options, defaultIndex)` — a question with *n* plates,
  returning the index chosen (`-1` if dismissed). `Escape` dismisses, `Enter`
  takes the default.

Multi-page + multi-option is not meaningful (a page step consumes the plate), and
neither entry point builds one.

**`ClassicWood`** holds the seam-free grain tiling — the mirror-fold described
under `ClassicInfoPanel` — shared by the info strip and these popups.

### The dead dispatch seams (the actual bug)

`invokeNowOrLater` / `invokeNowOrWait` are **no-ops in the base `GUI`** (headless
has no EDT to reach), and `ClassicGUI` never overrode them. Every task routed
through them was therefore dropped on the floor, so the classic UI **silently
discarded every in-game notice**: `InGameController.displayModelMessages` posts
its display task through `invokeNowOrWait`, and `Message.clientGeneric` posts the
server-driven message flush (and sound) through `invokeNowOrLater`. Overriding
`showModelMessages`/`showReportTurnPanel` alone would have changed nothing —
they were never called. Both seams now mirror `SwingGUI`: run inline on the EDT,
else hand off.

This is the general hazard of building on a no-op base class: a seam you never
override fails *silently and invisibly*, and the two dispatch seams are
especially costly because they strand other seams rather than losing one screen.

### The no-op seam audit (2026-07-18)

Following that hazard to its conclusion: cross-referencing every `getGUI().X`
call in `client/control/*` against what `ClassicGUI` overrides turned up one
more silent-loss bug of the same class — **`showErrorPanel`** (below) — and
otherwise a reassuring picture:

- **Most confirms already work.** `confirmHostileAction` / `confirmLeaveColony`
  / `confirmStopGame` / `confirmClearTradeRoute` all delegate to
  `modalConfirmDialog`, which we override — so e.g. attacking an ally now
  prompts. `confirmPreCombat` was the exception. It is gated behind the client
  option `guiShowPreCombat`, which is **on** by default
  (`client-options.xml`), so it reached the base `showPreCombatDialog`, whose
  `false` cancelled every attack before it reached the server. (An earlier
  version of this note said the option was off by default; that was wrong.)
  `ClassicGUI.showPreCombatDialog` now answers `true` (attack at once) until the
  original KAMPFANALYSE box replaces it (build spec W12).
- **`showEventPanel` must not return null.** `InGameController.newLandName`
  adds a closing callback to the panel it gets back, so the base `null` would
  throw once new-land naming is wired. The original shows no event pictures,
  so `ClassicGUI.showEventPanel` returns a stand-in that is never shown and
  runs its closing callbacks at once.
- **Event dialogs are a genuine open sub-audit, deferred to Phase 3.**
  the event dialogs (monarch, emigration, naming, first-contact, native-demand)
  no-op'd today, and some *return a value that gates flow*. **Chasing this down
  found three real bugs, now fixed** — see "Event confirm dialogs" below. Two
  remain, tied to the Q4 widgets: `showEmigrationDialog` (pick 1 of 3 recruits —
  a choice) and `showNamingDialog` (name a colony/region — text input).

### Wired seams

- `showModelMessages(List<ModelMessage>)` — in-turn notices.
- `showReportTurnPanel(List<ModelMessage>)` — the end-of-turn batch.
  Each notice keeps FreeCol's own illustration for it
  (`ImageLibrary.getObjectImageIcon(game.getMessageDisplay(m))`).
- `modalConfirmDialog(Tile, StringTemplate, ImageIcon, …)` — replaces the plain
  `JOptionPane` stopgap. The `(…, Unit, …)` and `(…, FreeColObject, …)`
  overloads in `GUI` are `final` and delegate here, so every confirm in the game
  lands on this one override. A dismissed popup falls back to `defaultOk`.
  Window title is `colony(tile)` — the tile's colony, else **"FreeCol"**.
- `showErrorPanel(String, Runnable)` — the audit's find. All five `showErrorPanel`
  overloads are `final` and funnel into this one non-final seam, so a no-op meant
  **every error in the classic UI vanished**. Worse, some errors carry a
  `callback` due to run on close — the uncaught-exception handler in
  `FreeColClient` shows a *serious* error with a `System.exit` callback, so the
  no-op left the app hung, neither warning nor exiting. Now routes the message
  through the shared popup and runs the callback in a `finally` (so the exit path
  fires even if the popup throws). Error text currently uses the same green as a
  message — whether the original styled errors distinctly is an open expert
  question.

`modalChoiceDialog` / `modalInputDialog` remain plain Swing stopgaps: they need a
list widget and a text field, whose original look wants the expert's reference
shots first.

### Event confirm dialogs (async `DialogHandler<Boolean>`)

`showMonarchDialog` (the king's demands), `showFirstContactDialog` (meeting a
native nation) and `showNativeDemandDialog` (native tribute demand) — the three
event dialogs whose response is a yes/no. **Each was a real flow bug while it
no-op'd, not a missing screen:** the handler carries the player's answer back
over the wire (`answerMonarch` / `firstContact` / `indianDemand`), so with no
dialog the exchange silently dropped — a tax hike accepted by omission, the
player never offered a Tea Party.

Unlike `modalConfirmDialog` these seams are *asynchronous* (a `DialogHandler`
callback, not a return). They all share one private helper, `askEvent`: build the
message + icon + a Yes/No plate pair (or a lone acknowledge plate when the action
has no yes-key) exactly as the matching FreeCol dialog does — `MonarchDialog`,
`FirstContactDialog`, `NativeDemandDialog` — show it on the shared popup, and hand
the choice to the handler. `ClassicDialog.ask` is modal-blocking, which is right
for a demand that *must* be answered; the controllers already post these via
`invokeLater`, so blocking the EDT (which pumps events) is fine. The handler runs
in a `finally`, so a popup failure still resolves the exchange (as a reject)
rather than leaving it dangling.

The remaining two event dialogs, `showEmigrationDialog` (choose 1 of 3 recruits)
and `showNamingDialog` (name a colony/region), need the choice-list and
text-field widgets — the same Q4-blocked work as `modalChoiceDialog` /
`modalInputDialog`.

**Verified live** (2026-07-17 / -18): an end-of-turn notice (*Sons of Liberty at
10%*, title "Rundenende") and the **high-seas confirm** (title "FreeCol", ship
portrait, "Jawohl, setzt alle Segel!" / "Nein, verweilt in diesen Gewässern.")
render in the wood frame; the **error popup** (title "Fehler") renders and its
callback fires on dismiss; the **monarch tax dialog** (title "Eine Nachricht von
der Krone", per-action labels "Wir akzeptieren" / "Gebt mir Freiheit oder den
Tod!") renders and its handler fires with the mapped boolean. The error and
monarch checks were driven via a temporary key hook (reverted). 0 SEVERE
throughout. Reaching the notices needs a *populated* save — an idle unit
generates none, which is why the first attempt saw nothing.

> **Awaiting expert sign-off.** The popup metrics and the green-on-wood palette
> are read off the original's screenshots by eye, not measured from the art —
> a considered guess, like the Colony Advisor's paging keys.

## Opening (Vorspann) (`ClassicEmblem`, `ClassicIntro`, `ClassicIntroTimeline`, `ClassicIntroPlayer`, `ClassicOpeningScript`)

The owner asked for a Vorspann before the first page, "Levi's Colonization
2026, animated like MPS Labs", with the music starting together with it.
(2026-10-05)

**What a normal launch shows** (`ClassicIntroTimeline`; ms after the first
picture; ≈2:08 in all):

| ms | Phase | What | Source |
|---|---|---|---|
| 0-300 | BLACK | lead-in; covers the audio start (≤ ~150 ms), so first note and first movement coincide | own |
| 300-10,300 | EMBLEM | own emblem "Levi's / COLONIZATION" spinning, subtitle "Levi's Colonization 2026" grows out from under it | own art in the MPS-LABS style |
| 10,300-10,800 | FADE | 8-step palette fade | own (the original's exit is not captured) |
| 10,800-11,100 | BLACK | | own |
| 11,100-~128,300 | CHART | the original's sea-chart credits and title build-up, chart frames 0..890 at 7.603 fps, frozen from frame 769 | faithful, 0 px vs captures |
| from ~128,300 | DONE | cut to the live title menu | faithful (165 → 166) |

Any fresh key or a left/right click skips straight to the title menu; the
skipping key is used up (its auto-repeat and typed character are swallowed
until release, so a held Enter cannot also open NEUE WELT, and the rest of a
skipping double click is inert). Alt/Ctrl/Meta chords and bare modifiers do
not skip (Alt+Enter, Alt+F4 keep their meaning); closing the window opens the
quit box ("Nein" → the title). No arrow is shown (capture 084 has none).
Only on a normal launch: not with `--fast`, a save argument, a debug start,
`--no-splash` or headless. **`--no-intro` is deliberately not honoured**: it
means "skip FreeCol's intro video", which the Classic UI never shows, and
the owner's desktop shortcut passes it. Developers switch the Vorspann off
with `--fast` or `-Dfreecol.classic.intro=false` (`ClassicIntro.enabled`).

- **Emblem (`ClassicEmblem`, own art, tracked source).** No MicroProse
  sprite, name or palette is used; only the style measured on the native
  captures `screenshots/intro-original/opening_084..103` (the original logo
  is the pre-rendered MPSLOGO.SS, 16 frames per quarter turn, at (86,22);
  its subtitle MPSNAME.SS, 29 stages) is reproduced:
  - a square prism, the same face on all 4 sides, turning 5.625° per frame
    at 10.12 frames/s (98.8 ms; fit over 084..103, ±0.07 s), front face
    away to the LEFT; the picture repeats after 16 frames
    (`PHASES`), so the 16 prism pictures are built once (≈30 ms);
  - see-through letters, so the back faces show MIRRORED, lower and
    narrower — the automatic result of the perspective: camera distance
    7.7 × half face width (the original back band is 0.77× as wide), eye
    line 91 px below the band (it sits ~20 px lower), face-on texture row 0
    at y = 25, axis x = 159.5; per screen column the face's texture column
    is found by inverting the plane's perspective, nearest neighbour;
  - face texture 120×88: "Levi's" in Serif bold italic stretched to a 64-px
    'L' and 116 px width, rasterised at 4× without anti-aliasing and reduced
    by 4×4 majority (crisp, deterministic per JDK); a 16-step cyan → royal
    blue ramp (own values echoing capture 091's rows) with a seeded brushed
    streak, a 1-px #000848 outline; then gold #FBFB45 / #BEBE3C lines and
    "COLONIZATION" in own 7×9 block capitals (white #FBFBFB);
  - brightness 0.22 + 0.78·|cos φ|, the back faces' script ×0.45 more (the
    back band stays bright, as in the original); a fixed 60-colour palette,
    no anti-aliasing on the prism, an opaque black box (82..237, 22..140);
  - the subtitle (Serif bold, cap ≈16 px, rendered once at 300×25 at 4× with
    AA, quantised to 4 greys) is drawn BEFORE the box and stretched
    horizontally only, with the two laws measured on MPSNAME: width
    24 + 276·S(k/28), bottom 115 + 55·S(min(k,18)/18), S = smoothstep,
    k = 0..28 from emblem frame 11 (as the original's started 11 logo frames
    in); stages 0..8 lie wholly under the box, so it grows out from under
    the emblem; final at x 10..309, y 146..170.
  - Readability was checked at 1× in all 16 phases (preview harness A). The
    glyph pixels depend on the JDK's Serif font; the tests check invariants
    only.
- **Chart credits and title build-up (`ClassicIntro.paintChart`, faithful).**
  Every rule is verified to **0 differing pixels** against
  `intro-original/opening_104..165` (59 single frames; 123, 134, 141 are
  DOSBox mid-redraw captures and equal two consecutive frames row for row,
  also 0 px):
  1. OPENBORD.PIK (frame with the black window at rows 24..155);
  2. OPENING.PIK (960×132) in rows 24..155 at x offset `max(0, 640 - f)`
     (pan 1 px per frame from Europe to America; still from f = 640);
  3. the ship OPENSHIP.SS.(f mod 8) at `(PATH[f-1].x - 11 - offset,
     PATH[f-1].y - 12)` while f < 701 (the 701 PATH.DAT points);
  4. OPENING.TXT `@OPENING` entries in table order: index `f - start`;
     repeats 0 = play once and hold the last frame; R > 0 = R+1 plays, then
     gone; a later started entry of the same series supersedes (wind 1 at
     78 and 97); top-left = the SS anchor top-left + (baseX − offset, 0);
     the SUN's index is FITTED, `floorMod(floorDiv(f − 110, 21), 7)` (exact
     on f 143..201, unverified outside, ≤ 12 px);
  5. ship and animations clipped to rows 24..155;
  6. credits: the first `@CREDITS` row with start ≤ f ≤ end (inclusive) draws
     OPENCRD{series+1}.SS.{sprite−1} centred at (160 − w/2, 183 − h/2),
     cuts in and out, one at a time (first the MicroProse banner, f 25..50,
     then role banners and name scrolls every 16 frames to f 625).
  The credits are SPRITES with the names baked in; nothing is transcribed.
  OPENING.TXT and PATH.DAT are copied byte for byte into the git-ignored
  pack by `ant classic-assets` (`ClassicAssetConverter.TEXT_FILES`) and
  parsed by `ClassicOpeningScript` (series 0..9 = OPENWND1, OPENSUN,
  OPENMON1, OPENWND2, OPENMON2, OPENMON3, OPENFISH, OPENGUY, OPENLOGO,
  OPENBONK; −1 = END 891; `0,0,0,0` ends the table).
  Clock: 131.52 ms per chart frame (7.603 fps, fit over 52 captures, ±0.1 s);
  the beached ship from 701, the man with the flag from 720, the
  "Sid Meier's COLONIZATION" logo (OPENLOGO) at 767 as a cut, and the picture
  FROZEN at 769 (captures 156..165 are identical and need the freeze:
  OPENGUY.049 = 769 − 720). At END (891) it cuts to OPENMENU + menu, which is
  a separate picture (165 → 166: the man and flag vanish, the ship moves,
  ~2.3k sea pixels change), so the cut is faithful; the DONE frame equals
  166 at 0 px with the original version line and arrow (the production line
  reads "Version 2026", see the title section).
- **Why a render thread (`ClassicIntroPlayer`) and not a Swing Timer.**
  The intro starts in the early window, and `FreeCol.startClient` queues the
  ~3 s `FreeColClient` constructor on the EDT right behind it. A timer would
  freeze the first 3 s — exactly when the emblem should start with the
  music. A daemon thread "Classic intro" samples the timeline by
  `System.nanoTime()` (stalls drop frames, never stretch the show), renders
  each NEW picture into a fresh image (published through a volatile field;
  never written again, so no tearing), blits it with `getGraphics` at the
  panel's whole-number scale and letterbox
  (`ClassicMainMenuPanel.canvasPlacement`) under a lock that `stop()` takes
  (so nothing lands after a stop), skips the blit while the panel is not
  showing (Alt+Enter re-creates the window), redraws every 250 ms to repair
  exposes and also asks for a normal repaint (`paintComponent` in INTRO
  draws only that latest picture). The chart's ~250 sprites load on a
  second daemon thread during the emblem; missing or late (not ready at the
  fade) → the fade leads straight to the title. At DONE it posts
  `finishIntro(false)` and keeps the title picture up until the EDT runs it.
  Three render failures in a row end the intro on the title.
- **Glue (`ClassicMainMenuPanel` mode INTRO).** The intro is a mode of the
  same panel the early window builds and `ClassicGUI` adopts, so adoption,
  focus, `DeferringActions`, typed-ahead input, `--fast`, save arguments and
  `--windowsize` work unchanged. INTRO counts as `isLive()`, so the
  start-up's `showMainPanel` (~4 s) keeps it; the menu key bindings do
  nothing in INTRO; the pointer is hidden unconditionally (the EDT is
  blocked, so no mouse event could tell where it is); `offerQuit` stops it
  and opens the quit box; any mode change away from INTRO stops the thread.
  Typed-ahead input spends its first fresh key or click on the skip.
- **Log lines:** `Classic start-up: intro shown after N ms`,
  `Classic intro: started after N ms since launch`,
  `Classic intro: chart assets ready after N ms` (or `... opening material
  missing (...); emblem then title` / `... not ready after N ms`),
  `Classic intro: finished after N ms` / `skipped after N ms`.
- **Harness** (scratch, never tracked: `IntroPreview`): A emblem sheet,
  palette and geometry invariants, a side-by-side with captures 084..091
  (style only); B chart 104..165 (`CHART: 59/59 exact, 3/3 splices exact;
  clock 62/62`); C DONE vs 166 (0 px with the original line and arrow) and
  the frozen composite vs 165 (0 px); D contact sheets of the whole show
  every 2 s and of each credit.
- **Tests:** `ClassicOpeningScriptTest` (synthetic files: sections,
  comments, END, terminator, 1-based sprites, malformed input),
  `ClassicIntroTimelineTest` (lead-in, emblem frames at 98.8 ms, subtitle
  from frame 11, fade levels, f = 640 at ≈95.3 s, logo 767, freeze 769, END
  at ≈128.3 s, monotonic, skip, no chart), `ClassicEmblemTest` (period 16,
  symmetric silhouette at 45°, mirrored back face, palette, nothing outside
  box and subtitle, subtitle laws, early stages hidden, band face-on at rows
  96..110), `ClassicIntroTest` (synthetic chart and sprites: pan, ship path,
  hold / vanish / supersede, clipping, inclusive centred credits, sun).
- **Unverified, implemented as documented choices:** the emblem's length
  and exit (the original logo ran ≥ 9 s, its end and any fade fell in a
  capture gap), the cut from black to chart frame 0, the title logo's cut at
  767, the sun outside f 143..201, the exact END after the freeze (±0.4 s).
  `OPENING.EXE -f` (frame numbers) in DOSBox, run by the owner, would settle
  them. Whether to keep the first credit, the MicroProse banner, is the
  owner's call (faithful default: kept; dropping it is a one-line filter of
  `ClassicOpeningScript.credits`).

## Title screen & main menu (`ClassicMainMenuPanel`, `ClassicMenuBox`, `ClassicFont`)

Reference: `screenshots/start-sequence/opening_033.png` (native 320×200). The
headless preview renders it with **0 differing pixels** over the whole frame,
the original mouse arrow at (160,100) included, and the 055 load box with 0
differing pixels.

- **Canvas.** `ClassicMainMenuPanel` paints a 320×200 virtual canvas at the
  largest whole scale, nearest-neighbour, black letterbox (as the colony and
  Europe screens do). The background is `OPENMENU.PIK` drawn 1:1. It is *not*
  `OPENING.PIK`, which is a 960×132 sea chart. Only the menu box is drawn on
  top. Modes: `PASSIVE` (picture only), `TITLE`, `LOAD`, `NOTICE`, `BUSY`,
  `QUIT`, `NEW_WORLD` (the new-game chain, see below), `DEPARTURE` (the
  ten-picture departure after the audience, see "Departure" below) and
  `STARTING` (the departure's last picture — or, without the departure, the
  audience — frozen while the engine starts). In PASSIVE, BUSY and
  STARTING all input is ignored, which guards against starting twice.
  All painting is in package-private **static** methods (`paintTitleScreen`,
  `paintLoadBox`, `paintNotice`) taking explicit `MenuAssets`. A scratch
  harness calls them from a jar classpath without a `FreeColClient` and diffs
  the result against the captures.
- **Mouse arrow.** The original's own arrow `CURSOR.SS.000` (hot spot = sprite
  origin; the game puts it at (160,100) at start) is drawn *into the canvas*
  (`paintCursor`) at the canvas scale, on the 320×200 grid, and the system
  cursor is hidden meanwhile. A custom system cursor cannot do this: Windows
  caps it at `Toolkit.getBestCursorSize` = 32×32 and Java shrinks bigger
  images, so it could not exceed ×2, while the canvas runs at ×4/×5. The
  system arrow comes back in the letterbox and in PASSIVE/BUSY (the EDT may
  block then, which would freeze a drawn arrow). The two CURSOR frames — and
  no other SS frame — carry an opaque `#5555FF` key pixel in their top-right
  and bottom-left corners; `cursorSprite` clears them.
- **Notices** are drawn plain (no `{}`/`~` markup), with characters FONTTINY
  lacks mapped to near equivalents (`\` → `/`, `_` → `-`, …; a literal `\`
  would draw `Ü`), and cut to 20 lines (a cut text ends in "..." and is logged
  in full) so the box stays on the 200-px canvas.
- **Box rules** (`ClassicMenuBox`, from GAME.TXT `@BEGINMENU @width=160
  @y=91` and `@LOADGAME @width=190`, all measured):
  - Outer width = `@width + 6`, centred horizontally. Top = `@y`, or centred
    vertically when there is none.
  - Height = `6·promptLines + 8·rows + 18` (measured: prompt lines are 6 px
    apart, option rows 8). Every captured box fits: title 64, save 88, load
    104, SAILPORT 32 (one prompt line), the first scene's advisor box 48
    (five lines, no rows, 083/049), the Europe advisor boxes 68 and 86 (3+4
    and 2+7, each plus a 6-px "(F1 …)" footer line, 011/013). The earlier
    inference `8·(promptLines + rows) + 16` agreed only for one prompt line,
    so the title screen's boxes are unchanged (the title/load previews still
    give the same pixels).
  - Fill: the 32×24 `OPENTILE.SS.000` (title) or `WOODTILE.SS.000` (in game),
    tiled from the **outer** corner. Not `WOODPANL`, so `ClassicWood` is not
    reused.
  - Frame: a black ring, a flat ring, then a bevel (light on top and right,
    dark on left and bottom).
  - Prompt text at `(X+5, Y+9+6k)`. Rows at `(X+9, Y+13+6P+8i)`. Selection bar
    `(X+4, rowTop-1, W-8, 7)` in the dark colour; text on it stays green.
  - The title box is `(77,91,166,64)`. The load box with 10 rows is
    `(62,48,196,104)`.
  - Two themes: `TITLE` and `GAME` (the colours measured from 054/055).
    `paintDropdown` draws the in-game menus (053, 001-005; see "In-game HUD"), with greyed rows in `DISABLED_INK`.
- **Title line:** `{COLONIZATION} Version 2026` -- the owner's wording
  (2026-10-04), deliberately not the original's. The original reads
  `{COLONIZATION} Version 2.26 -- 19-Sept-94` (GAME.TXT:42, filled in by
  VICEROY.EXE, which holds only 2.26; the capture's pixels beside the cursor
  fit '2'). Same font, colours and place: `{..}` marks the gold word, the
  rest is the line's green.
- **Fonts** (`ClassicFont`):
  - The original `.FF` bitmap fonts are converted by `ant classic-assets`
    (`FfDecoder`). Each becomes a 2-bit palette atlas `ff/NAME.FF.png` plus a
    quoted metrics string `image.classic_original.ff.NAME.FF.properties`.
  - Roles: FONTTINY = menus, dialogs, info panel and menu bar (the menu
    matches with 0 differing pixels). FONTINTR = nation texts and captions.
    FONTKING = the king's scroll.
  - The advance is the glyph width, with no extra spacing. `y` is the glyph
    top.
  - Colours are a per-call table (`colours(rgb)`); recolouring swaps the
    `IndexColorModel` on the shared raster (no copy).
  - Markup: `{`/`}` toggle the highlight colour, and `~` highlights the next
    letter.
  - German letters use the game's own codes (ä 96, ö 28, ü 127, Ä 30, Ö 31,
    Ü 92, ß 29).
  - Without the pack, a 7 px Swing font is used and one INFO line is logged.
    Misses are cached, so the pack is not probed on every paint.
- **The pack palette** is expanded the way DOSBox does it, `(v<<2)|(v>>4)`, so
  pack PNGs, the colour constants and the captures compare bit for bit.
- **Menu actions** (`ClassicGUI.menuActions`):
  - NEUE WELT → the original's new-game chain on the same canvas
    (difficulty, power, name, the nation's two pages, the audience; see
    "New-game chain" below). Dismissing the audience calls
    `Actions.newWorld(setup)` → `beginNewWorldSetup(NewWorldSetup)` →
    `startNewWorldGame`, which loads a fresh spec with the chosen difficulty,
    sets the name and calls `startSinglePlayerGame`; it never resumes a save.
    A pack converted before the chain's material existed (no `text/`) skips
    the chain: NEUE WELT then starts at once with `NewWorldSetup.defaults()`
    and logs one INFO line asking for `ant classic-assets`.
  - AMERIKA and INDIVIDUALISIEREN show the notice "Diese Funktion folgt in
    einer späteren Version." FreeCol's America maps would look about 3.6×
    too tall in the rectangular view; the faithful route is converting
    AMER2.MP.
  - SPIEL LADEN opens the load box (`ClassicSaveGames`). It lists manual saves,
    then autosaves, each newest first, with no "(EMPTY)" slots. Each row first
    shows the file name and date. A daemon thread then replaces it with the
    original-style label "Entdecker Dago der Holl., Herbst 1729", read by
    streaming `savegame.xml` up to the owner's `<player>` (about 30 ms per
    save). The thread stops as soon as the box closes or reopens. Enter or a
    click loads; Esc or a right click goes back. The wheel moves one row per
    notch (precise touchpad deltas are summed).
  - RUHMESHALLE → `showHighScoresPanel(null, HighScore.loadHighScores())`. It
    runs on the client side and works before any game.
  - NEUE WELT and loading catch any `RuntimeException`, show it and return to
    the title; otherwise the BUSY box (which ignores all input) would stay up
    forever.
  - Keys: Up/Down (and keypad), Home/End, Enter. PgUp/PgDn page in the load
    box and jump to the first/last item on the title. Hover moves the bar.
    Esc on the title opens the quit box (see "Full screen, Alt+Enter and
    exit" below); the original ignores it there.
- **Lifecycle overrides:**
  - `showMainPanel` (`teardownInGame` + live title + switch to the original
    title piece; see "Music (original soundtrack)" below).
  - `showMainTitle` (→ `showMainPanel`; no FreeCol intro music).
  - `closeMainPanel` (→ PASSIVE).
  - `removeInGameComponents` and `prepareShowingMainMenu` (in-game new and
    load).
  - `showOpeningVideo` runs its callback. The base no-op hung any start
    without `--no-intro`. The Classic intro itself runs earlier, in the
    early window (mode INTRO, see "Opening (Vorspann)" above); it ends on
    this title with the first item barred, by a cut, and the key or click
    that skips it is consumed (never also acts on the title).
  - `showNewPanel` → title.
  - `showLoadingSavegameDialog` returns a single-player info. A null return
    silently aborted some loads.
  - `teardownInGame` closes the sub-windows, but keeps an open high-score
    window. It stops the map's edge-scroll timer (`ClassicMapViewer.dispose`),
    removes the menu bar and nulls `mapViewer`/`infoPanel`, so `reconnectGUI`
    rebuilds the HUD.
- **Side fix:** `FreeColServer` writes the configuration (user paths) as an XML
  comment. A path containing `--` (every sandbox scratchpad path does) made the
  save unreadable, so a space now follows every dash that another dash
  follows (`--` becomes `- -`, `---` becomes `- - -`), and a trailing `-` gets
  a space too (a comment must not end in `-`). Older such saves stay
  unreadable: the load box shows their file-name label, and loading them shows
  the engine error.
- **Open / not yet:**
  - The in-game "Öffnen" still gets `null` from the un-overridden
    `showLoadDialog`.
  - The in-game SPIEL items save, load and options are drawn as in the original but stay inert (they show the "follows later" notice): their classic seams (`showSaveDialog`, `showLoadSaveFileDialog`, `showClientOptionsDialog`) are still no-ops (see "In-game HUD", `NOOP_SEAMS`).
  - Notice height for multi-line prompts now follows the measured
    `6P+8R+18` rule (with R = 0: `6P+18`); no multi-line title-screen notice
    was captured itself.
  - The title-screen load box uses the TITLE theme by inference; no capture of
    it exists.
  - RUHMESHALLE still opens the generic report window: a separate, decorated
    OS window (`showReport` → `new JFrame`) whose `ClassicReportHighScoresPanel`
    draws with Swing fonts. The original shows the Hall of Fame full-screen in
    its bitmap font; the follow-up is another mode of the title canvas using
    `ClassicFont.TINY` and `ClassicMenuBox`.
  - The window is still titled "FreeCol — Classic UI (experimental)" (the
    live-test harness finds it by that title); it only shows in the taskbar
    and Alt+Tab now that the window is borderless full screen.
  - The colony, Europe and report windows still show the system cursor (the
    title canvas, the in-game HUD and the first scene draw the original
    arrow: see "The mouse arrow").

## New-game chain (`ClassicNewWorldChain`, `ClassicNewWorldScreens`, `ClassicText`, `ClassicTextLayout`, `ClassicPackFiles`)

What follows NEUE WELT in the original, rebuilt screen by screen against the
native DOSBox captures. Dutch run: `screenshots/start-sequence-dutch`; English
run: `screenshots/start-sequence`.

| # | Screen | Captures | Picture |
|---|--------|----------|---------|
| 1 | Difficulty (five portraits) | Dutch 056-060 (each level highlighted), English 034 | `DIFFICUL.PIK` |
| 2 | European power (four cards) | Dutch 061-064 (England, France, Spain, Holland), English 035 | `NATIONS.PIK` |
| 3 | Leader name | Dutch 065 (Holland's default leader), English 036 | `WOODPANL.PIK` |
| 4 | Nation page A (history) | Dutch 066, English 037 | `WOODPANL.PIK` |
| 5 | Nation page B (bonus) | Dutch 067, English 038 | `WOODPANL.PIK` |
| 6 | Audience | Dutch 068 (Holland: a stadtholder, `@VICEROY2`), English 039 (a king, `@VICEROY`) | `KINGLSS1.PIK` + banner + `KING1.SS` |

The headless preview harness renders **all 19 captures with 0 differing
pixels** (arrow included), through the production code only. After the
audience comes the departure (069-082, see "Departure" below), then the
game opens on the original's first scene with the admiral (083/049, see
"First scene" below) instead of the engine's own start message.

- **Where it runs.** On the title canvas itself (`ClassicMainMenuPanel` mode
  `NEW_WORLD`): one panel, one letterbox, one arrow, no panel swap. The chain
  holds no engine state, so Escape at any point needs no teardown. Its art
  and texts are read from pack files (`ClassicPackFiles`, not
  `ResourceManager`) on a daemon thread when the title first goes live and
  joined when NEUE WELT is chosen.
- **Painters.** `ClassicNewWorldScreens` has static painters per screen
  (`paintDifficulty`, `paintNation`, `paintName`, `paintNationPage`,
  `paintAudience`, dispatcher `paint`) taking explicit `Assets`, `Texts` and
  the chain's `View`. Each screen is the untouched PIK 1:1 plus overlays;
  unselected cards are pure PIK pixels.
- **Constants** (all measured; the Javadoc names the capture of each):
  - Difficulty cards: five 68×90 rects in reading order (top row of two,
    bottom row of three); nation cards: four 88×82 rects. The selection
    frame is a 1-px outline exactly on the card bounds, which are also the
    click rects.
  - Frame/label colours are palette entries of each PIK: difficulty
    green, blue, yellow, orange, red; nations red, blue, yellow, orange —
    the colour column of NAMES.TXT `@COUNTRY`. Palette index 12 differs
    between the two pictures (`0xEF0404` vs `0xF70000`), so the screens do
    not share a red.
  - Headings: two FONTINTR lines, pitch 13, centred in a span of 116
    (difficulty) / 114 (nations), shadow black. Footer: FONTTINY in the UI
    ink, same spans, the parentheses added by code.
  - Card labels: FONTTINY, `x = card.x + 1 + (card.w - w)/2`, black shadow
    one pixel to the right, then the card colour. Difficulty at card top
    +38/+46; nations at card top +2 and card bottom −8.
  - Name screen: prompt at y 88 centred in `@width=300`, box (79,98,167,14)
    in the ink, the untouched default on a fill in the menu's selection
    colour `0x3C2018`, name at (82,101), a static FONTINTR `_` caret after
    it. FONTINTR's shadow is `0x0C0C0C` on WOODPANL but black on the pickers.
  - Nation pages: FONTINTR, normal ink/shade/shadow, highlighted words in
    gold `0xC7A220`.
  - Audience: banner `<ENGLND|FRANCE|SPAIN|DUTCH>1.SS.000` and `KING1.SS.000`
    (the same seated figure for king and stadtholder) at their sprite-header
    anchors; scroll text FONTKING `0x715545`/black, `@x`, `@y + 3`, pitch 8.
- **Text strategy.** Every string comes from the original's own files at
  runtime; tracked code holds only section names, line/index numbers,
  geometry and colours.
  - `ant classic-assets` copies `GAME.TXT`, `NAMES.TXT`, `LABELS.TXT` byte
    for byte into the git-ignored `<pack>/text/` (they are copyrighted).
  - `ClassicText` decodes them with a 7-entry table (0x1C ö, 0x1D ß, 0x1E Ä,
    0x1F Ö, 0x5C Ü, 0x60 ä, 0x7F ü — the codes `ClassicFont.toCode` draws),
    never a charset, because ä, Ü and ü sit on printable ASCII. CR stripped,
    stop at 0x1A or `@END`, ';' lines are comments.
  - Sources: difficulty labels from NAMES.TXT `@DIFFICULTY` (not GAME.TXT
    `@DIFFICULTY`: the hardest level's label in 060 has a lowercase ö that
    only NAMES.TXT has), upper-cased a–z only (`upperAscii`, never
    `toUpperCase`) plus ':'; nation names `@COUNTRY` column 0; default
    leaders `@LEADERNAME` column 0; headings, subtitles, bonus words and
    footer from LABELS.TXT `@MISC`; the prompt GAME.TXT `@LEADERNAME` (line 1
    without `^^`, field length = its 22-underscore option); pages
    `@NATION0A..@NATION3B`; audience `@VICEROY` (England, France, Spain) and
    `@VICEROY2` (Holland), `%COUNTRY` = the nation name.
  - **LABELS index convention:** `@MISC` with blank lines SKIPPED, 0-based.
    Checked: 160 = LABELS.TXT:176, 161 = :177, 162 = :178, 164..168 =
    :180-184, 169 = :185, 170 = :186, 172..175 = :188-191 (the blank line
    :51 is skipped).
  - **Markup:** `{`/`}` highlight; `^^` at the start = centred hard line
    (`x = L + ceil((W − width(rtrim))/2)`, leading spaces count); `^` at the
    start = left hard line (bare `^` = empty line); `^` elsewhere removed;
    `_` is an invisible placeholder, removed before measuring (FONTINTR has
    a 7-px `_`); `%%` = `%`, any other `%` stays.
  - **Wrap rule** (`ClassicTextLayout`): other lines are joined with one
    space — also after a trailing '-', a quirk the Holland page really shows
    — spaces collapse, greedy wrap at spaces, never at hyphens. Measure =
    drawn width + 1 per `{`, `}` and `ß`; a line fits if the measure is at
    most W − 2. An open highlight is closed/re-opened across a break for
    drawing. Left = `@x` or `(320 − W)/2`; top = `@y + 3`, else the block is
    centred, `(200 − pitch·lines)/2`. `ClassicFont.wrap` is not used (plain
    width breaks the 066 page differently). Unknown: whether ö/Ä/Ö/ü/Ü count
    extra (ä does not) — France and Spain pages have no capture, so their
    breaks are reviewed by eye only.
- **Input model** (`ClassicNewWorldChain`, a Swing-free state machine; the
  panel maps events onto it). The captures prove only reading-order stepping
  and the initial index 0; the rest are documented assumptions:
  - Pickers: Left/Up/keypad 4/8 previous, Right/Down/keypad 6/2 next,
    clamped without wrap (like the title menu); Home/End first/last; Enter
    confirms. A click on a card selects it, a click on the selected card or
    on the footer confirms. Hover does nothing. Forward entry starts at 0
    (England on the power screen); a Back return keeps the choice.
  - Name: on every forward entry the nation's default leader, highlighted.
    The first typed character replaces it; markup characters and characters
    outside the game's set are ignored; at most 22 characters and text +
    caret within the box (163 px). Backspace clears the highlighted default,
    else deletes one character. Enter confirms; an empty name becomes the
    default leader. A click in the box only drops the highlight. Arrow keys
    do nothing; keypad digits type digits.
  - Pages and audience: any key (except Escape and bare modifiers) or a left
    click advances; dismissing the audience starts the game. The trigger
    (key, click or timer) is not visible in stills.
  - Everywhere: Escape or a right click = one screen back; from the
    difficulty screen back to the title with NEUE WELT barred. Chords with
    Alt/Ctrl/Meta are left alone, so Alt+Enter toggles full screen on every
    chain screen without advancing, and Alt+F4 opens the quit box ("Nein"
    returns to the title). The chain's key listener consumes what it handles
    and the menu's bound actions do nothing in `NEW_WORLD`, so no key is
    handled twice.
  - One physical press, at most one screen. Every screen is left by a single
    press, and the difficulty card under the pointer lies inside the
    England card of the nation screen (which opens on England), so a double
    click on the pre-highlighted easiest card used to confirm England as well,
    a held Enter ran through to the name screen with England, and a double
    click on the bonus page skipped the audience. Now (`ClassicMainMenuPanel`
    `onPress` / `onChainKey`): the later presses of a click series whose
    press changed the screen are ignored (`chainPressChangedScreen`; a double
    click on a card that is not yet selected still selects and confirms it),
    and a key's auto-repeat (`heldKeys`, cleared on focus loss) only steps
    through a picker or repeats Backspace on the name — Enter, Escape and the
    pages' "any key" act once per press.
  - After the audience: mode `DEPARTURE` (the original's departure, see the
    next section), then mode `STARTING` (the departure's last picture
    frozen, no arrow, input ignored — the EDT blocks while the server
    starts), then the in-game view replaces it through `reconnectGUI`. When
    the pack lacks the departure's pictures or captions, one INFO line says
    so and `STARTING` follows the audience directly (the frozen audience),
    exactly as before the departure existed.
- **Engine order** (`ClassicGUI.startNewWorldGame`, `showStartGamePanel`):
  1. Spec + difficulty: `FreeCol.loadSpecification(rules, advantages,
     difficultyId)`; if `spec.getDifficultyLevel()` differs (an unknown id
     silently applies nothing, Specification.java:645-669), a WARNING and a
     reload with `FreeCol.getDifficulty()`. The server plays on this spec
     object (FreeColServer.java:318-329); the save records the level, which
     the load list shows.
  2. Name validation: trimmed; refused if empty, `mapEditor`, or equal to
     any ruler name of the rules (those are the AI players' names,
     ServerPlayer.java:239, and duplicates break `Game.getPlayerByName` and
     loading) — then a notice in our own wording
     (`classic.newWorld.nameTaken`) and back to the title.
  3. `FreeCol.setName(name)`: the login name (ConnectController.java:351),
     the player's name (LoginMessage.java:233) and the save owner
     (FreeColServer.java:908-909) — hence the load-list label with the
     typed name needs no further work.
  4. `startSinglePlayerGame` → login. Pre-game login **ignores the requested
     nation** and takes the first AVAILABLE one in HashMap order
     (LoginMessage.java:214); that is Holland only because Portugal and
     Sweden are NOT_AVAILABLE with four Europeans.
  5. `showStartGamePanel` → `applyNewWorldNation`: if the target is not the
     player's nation, `setAvailable(AVAILABLE)` when needed (covers
     `--europeans` < 4), then `setNation` and THEN `setNationType` (FIXED
     advantages check the type against the current nation,
     SetNationTypeMessage.java:100-118). Line-up: the other originals that
     are NOT_AVAILABLE become AVAILABLE; every other European without a
     player becomes NOT_AVAILABLE — so `buildGame` (FreeColServer.java:
     1199-1208) makes exactly the other three originals AI.
  6. Verify on the in-process server (`getPlayerByNationId(nid)` must be the
     human, by name): a server rejection only shows an error dialog while the
     ask returns true. On failure `abortNewWorldStart`: notice
     `classic.newWorld.nationFailed`, logout, `stopServer` (deferred until the
     login call stack has unwound), never a silent launch as the wrong power.
  7. `player.setReady(true)`, `requestLaunch()`.
  `--fast` (`startSinglePlayerGame` directly, `pendingNationId` null) and
  loading saves (`startSavedGame` → `requestLaunch` without
  `showStartGamePanel`) never pass through the chain or the new checks;
  `loadSavedGame` also clears `pendingNationId`. `FreeCol.setDifficulty`,
  `setAdvantages` and `setEuropeanCount` are never touched.
- **Preview harness** (scratch only — it prints original words, so it never
  goes into the repo): `NewWorldPreview` in the classic package, compiled
  against `FreeCol.jar` with `javac --release 11`, run headless with
  `-pack data/mods/classic_original -shots screenshots -out <dir>`. It drives
  a `ClassicNewWorldChain` with the player's own operations, paints with
  `ClassicNewWorldScreens.paint` and `ClassicMainMenuPanel.paintCursor` at
  (160,100), diffs against the 19 captures, writes renders and ×4 residual
  maps, renders France/Spain pages and audience for review, asserts the
  sprite anchors, and exits 1 on any difference.
- **Open items** (product questions, out of this step): FreeCol names the AI
  Europeans after their monarchs, the original perhaps after the NAMES.TXT
  leaders; NEUE WELT uses the `freecol` rules (FreeCol.java:172), not
  `classic`; Spain's home port is Cádiz in FreeCol, Sevilla in the original.
  The France/Spain banner positions come from the sprite headers, not from
  captures.

## Departure (LEVN0001-0010, @BUILD1-10) (`ClassicDeparture`, `ClassicDepartureTimeline`)

After the audience the original shows the expedition leaving the home port:
night, dawn, day, the ship leaves the pier and sails to the horizon, one
caption at a time. References: Dutch `start-sequence-dutch/opening_069..082`
(from Amsterdam), English `start-sequence/opening_040..048` (from London);
`INDEX.md` in both folders lists each frame. Timing reference: the timed
Dutch run `departure-dutch-timed/opening_167..340` (Enter on the audience
immediately before 167, one capture every ~0.53 s, stamps in its
`INDEX.md`; two gaps of ~24 s).

- **What it is (measured).** A slideshow of ten full-screen pictures
  `LEVN0001.PIK`..`LEVN0010.PIK` (already in the pack:
  `image.classic_original.pik.LEVN000n.PIK`, no converter change), each
  drawn 1:1 with one GAME.TXT caption `@BUILD1`..`@BUILD10`. Below the
  caption band every settled capture equals its picture exactly (Dutch
  069 = 070 pixel for pixel, 4 s apart): nothing moves inside a step, no
  sprites, no palette cycling, **no mouse arrow**. The pictures are the same
  for every nation. Capture → step: Dutch 069/070→1, 071/072→2, 073→3,
  074/075→4, 076→5, 078→6, 079→7, 080→8, 081→9, 082→10; English 040→1,
  041→2, 043→4, 044→5, 045→6, 046→7, 047→9, 048→10.
- **Night to day is baked into the art.** All ten palettes are
  byte-identical; pictures 1→5 move the sky and sea one index at a time down
  the blue ramp at palette indices 48..63. So there is no palette fade to
  imitate. The pictures derive from the Europe harbour, but `EUROPE.PIK`'s
  palette differs in 190 entries — only the LEVN pictures are used.
- **Captions (measured, 0 px).** FONTINTR in ink `0xFFFF9A`, shade
  `0x698AC3`, shadow `0x0C0C0C` (the same on night and day), laid out by
  `ClassicNewWorldScreens.paintMessage` like the nation pages: `@width=310`
  → left x=5, `@y=10` → top 13, pitch 10; `^^` lines centred with leading
  spaces counted, plain lines flowed left. Placeholders
  (`ClassicDeparture.Captions`): `@BUILD2` `%STRING0` = the difficulty's
  title (NAMES.TXT `@DIFFICULTY` column 0 as written — **inferred**: both
  runs played the easiest level), `%STRING1` = the leader's name; `@BUILD3`
  `%STRING0` = NAMES.TXT `@HOMEPORT`; `@BUILD4`/`@BUILD7` `%STRING0` = the
  nation (NAMES.TXT `@COUNTRY` column 0).
- **Holland quirk (measured, source unknown).** The Dutch captures 074, 075
  and 079 show two spaces before the nation's name, the English ones one.
  With the plain name those frames differ by 912/1410 px, with one extra
  leading space by 0 (`ClassicDeparture.DUTCH_COUNTRY_PREFIX`). NAMES.TXT,
  GAME.TXT and the audience (which matched with the plain name) have no such
  space. France and Spain have no captures and get no prefix (unverified).
- **Transitions: random pixel dissolve, fixed order (measured), generator
  unknown.** All twelve captures caught mid-way — English 042 (2→3, 78.0 %
  new), Dutch 077 (5→6, 72.6 %) and ten of the timed run (168/169 black→1,
  189/190 1→2, 210 2→3, 221 5→6, 239 6→7, 256/257 7→8, 274 8→9) — contain
  only old-frame and new-frame pixels; the dissolve covers the whole
  screen, caption included, so old and new captions overlap and a caption
  changes exactly with its picture. **The first dissolve starts from
  black, not from the audience:** timed capture 167, taken right after
  Enter, is all black, and 168/169 hold only black and LEVN0001 pixels.
  The order is one fixed order over all 64,000 positions: the twelve
  captures (seven transitions, three runs on two days) nest — every pixel
  new in a capture with a smaller fraction is new in every capture with a
  larger one, 0 exceptions in 66 pairs — and it shows no spatial
  structure, but Galois LFSRs and the MS C `rand()` were rejected as its
  generator.
  `ClassicDeparture.dissolveOrder` therefore reveals exactly the differing
  pixels in the order of one fixed Fisher-Yates permutation
  (`Random(0x1492)`), the same for every transition. Mid-dissolve frames
  never match the original pixel for pixel; settled frames always do.
- **Timing (MEASURED on the timed run, `ClassicDepartureTimeline`).**
  Every capture 167..340 was classified against the production frames
  (black, LEVN 1..10 with captions): settled on one frame (0 px), or
  mid-dissolve with the fraction of changed pixels already new; times are
  the file stamps (t = 0 at 167, good to ~0.05 s).
  - **Black lead-in.** The audience is cut to black on the key; the
    dissolve into picture 1 starts ≈0.56 s later.
  - **Every dissolve takes the same 0.90 s (`DISSOLVE_MS`), whatever
    changes.** Proof: the ship step 7→8 changes only 3,994 px, yet 256
    and 257, 0.51 s apart, show 22 % and 88 % of them — at the old
    estimate of 39,000 px/s it would have lasted 0.1 s. One duration for
    all seven caught transitions fits every mid and bracketing capture
    within 0.05 s (a size-dependent 0.70 s + 0.32 s × changed/64,000
    would fit within 0.02 s, below what the stamps resolve; DOSBox itself
    lagged on the full-screen steps — the captures taken during them
    were stored ~0.17 s late — so it is not modelled). Pixels are
    revealed evenly over the 0.90 s (`ClassicDepartureTimeline.revealed`).
  - **Per-picture onsets, not one hold** (`ONSET_MS`, ms after the key;
    a picture stays from its onset to the next):

    | k | dissolve into | onset ms | on screen | from |
    |---|---|---|---|---|
    | 0 | LEVN0001 + @BUILD1 | 560 | 11.14 s | 168, 169 mid; 170 settled |
    | 1 | LEVN0002 + @BUILD2 | 11,700 | 10.83 s | 188 old; 189, 190 mid; 191 new |
    | 2 | LEVN0003 + @BUILD3 | 22,530 | 9.66 s | 209 old; 210 mid; 211 new |
    | 3 | LEVN0004 + @BUILD4 | 32,190 | 9.66 s | **interpolated** (gap 25.0-48.6 s) |
    | 4 | LEVN0005 + @BUILD5 | 41,850 | 9.66 s | **interpolated** |
    | 5 | LEVN0006 + @BUILD6 | 51,510 | 9.40 s | 220 old; 221 mid; 222 new |
    | 6 | LEVN0007 + @BUILD7 | 60,910 | 9.33 s | 238 old; 239 mid; 240 new |
    | 7 | LEVN0008 + @BUILD8 | 70,240 | 9.45 s | 255 old; 256, 257 mid; 258 new |
    | 8 | LEVN0009 + @BUILD9 | 79,690 | 9.40 s | 273 old; 274 mid; 275 new |
    | 9 | LEVN0010 + @BUILD10 | 89,090 | 9.39 s | **extrapolated** (gap 80.6-105.5 s) |
    | – | end (`END_MS`) | 98,480 | – | **extrapolated**: one more 9.4 s period |

    Pictures 1 and 2 stay ~1.7 s and ~1.4 s longer than the ship pictures;
    why is unknown, the table keeps it. The first game scene was up at the
    next capture after the gap (276, +105.5 s) and stays to 340; with the
    engine's 1-2 s start after `END_MS` ours appears at ≈100 s.
  - **Replay check (scratch `Verify`):** the proposed clock, replayed at
    all 174 stamps, agrees with every capture within ±50 ms (exactly at
    the stamp with 173; at 222 it finishes 5→6 37 ms after the stamp), and
    98 of the 99 settled captures up to 275 render with 0 px difference
    (the 99th is that 222). The old model (39,000 px/s, 8.9 s hold, start
    from the audience) disagreed with 16 captures.
  - **Older hand-stamped runs:** no contradiction when their stamps are
    read as upper bounds (English key 1.2-5.5 s after the audience
    stamp); the Dutch 069..083 run then needs its key ≥0.5 s before its
    audience stamp, i.e. that run reached picture 3 at least 0.5 s
    sooner — run-to-run variation of the early pictures, not a model
    error. Time comes from `System.nanoTime()` differences, never tick
    counts, so a stalled EDT only skips ahead.
- **How it runs (`ClassicMainMenuPanel`, mode `DEPARTURE`).** `handleChain`
  DONE → `startDeparture`: builds the captions from the chain's setup,
  uses a black frame 0 (the original cuts the audience to black) and renders frames 1..10
  offscreen, computes the ten dissolve orders (a few tens of ms in all), and
  starts a 15 ms `javax.swing.Timer`. Each tick reads the timeline and copies
  the newly revealed pixels into the shown 320×200 image (repaint only when
  something changed); earlier transitions are completed first. The pictures
  and captions are loaded with the chain's art on its daemon prefetch
  (`ClassicNewWorldScreens.Assets.levn`, `Texts.build/diffTitles/homePorts`).
  If anything is missing, `ClassicDeparture.available` logs one INFO line
  and the old `STARTING` path runs. Log lines: `Classic departure: start
  (prepared in N ms, about 98 s)` and `Classic departure: finished after N
  ms (skipped=true|false)`.
- **Why the engine starts AFTER the departure, not in parallel — a
  deviation from this step's specification, for the owner to confirm.** The
  specification asked for the engine to start in the background during the
  show with the EDT never blocked. It does not: `finishDeparture` →
  `actions.newWorld` → `ClassicGUI.startNewWorldGame` runs
  `loadSpecification` and `startSinglePlayerGame` on the EDT (an
  `invokeLater`), after the show, so the EDT is still blocked for 1-2 s
  (repaint, Alt+Enter and Alt+F4 wait) and the player waits ≈98.5 s plus
  that start. Moving it off the EDT is not a local change:
  `startSinglePlayerGame` runs the login whose reply re-enters
  `showStartGamePanel` with nested synchronous asks on the EDT
  (`applyNewWorldNation`, Connection.java:436-441), FreeCol's own
  `NewPanel` makes the same calls on the EDT, and the game view, the
  `closeMainPanel` call and every failure path would need to be held until
  the show ends — too much engine threading to change without live tests.
  Instead the show ends on a still picture (LEVN0010 + `@BUILD10`, held
  ≈8.5 s after its 0.9 s dissolve), so the engine's 1-2 s start falls on that same
  frozen frame — exactly what `STARTING` already did with the audience. Nothing of the engine exists during the show: skip, Alt+F4 and
  failures need no special care, no autosave or turn report can arrive
  mid-show, `closeMainPanel`/`reconnectGUI` need no gating, and the in-game
  music (`PreGameController.startGameInternal`, `sound.intro.<nation>`)
  starts when the ship has left, not two seconds into the night picture.
  The title piece plays on during the show. Cost: the engine's start time
  on the last picture. `finishDeparture` freezes frame 10 (`frozenFrame`),
  switches to `STARTING`, paints it at once and calls `actions.newWorld` —
  the unchanged path `ClassicGUI.beginNewWorldSetup` →
  `startNewWorldGame`. `closeMainPanel` then calls `showPassive` from
  `startGameInternal`; coming from `STARTING`, PASSIVE keeps painting the
  frozen picture, so the title picture never flashes between the ship and
  the game. Any other `showPassive`, a live mode, or the panel being
  replaced by the game view drops the frozen picture.
- **Input (the original's behaviour is unknown — a PROPOSAL).** Any new key
  press skips the rest of the show (Escape too; there is no way back), as
  does a left or right click. Not skipping: chords with Alt/Ctrl/Meta
  (Alt+Enter toggles full screen via `FrameKeys`; Alt+F4 quits directly, as
  `offerQuit` is false in this non-live mode and no server exists yet),
  bare modifiers, the auto-repeat of a key still held from the audience
  (`heldKeys`), and the rest of the click series that dismissed the audience
  (`clickCount > 1 && chainPressChangedScreen`) — one physical press, at most
  one screen. The system cursor stays blank over the departure and the
  frozen picture after it (`hidesPointer`). `isLive()` stays false, so
  `ClassicGUI.showMainPanel`'s start-up call leaves a running show alone.
- **Preview harness** (scratch only, never in the repo — it renders original
  words): `StartPreview` in the classic package plus `run.ps1 [-only anim]`
  under the session's `scratchpad\departure\preview`, compiled against
  `FreeCol.jar` with `javac --release 11`, run headless with `-pack -dutch
  -english -steam -out`. It calls only production code
  (`ClassicDeparture`, `ClassicDepartureTimeline`, `ClassicNewWorldScreens`,
  `ClassicMainMenuPanel.paintCursor`). Section `anim`: A0 frame 0 + arrow at
  (160,100) vs 068/039 — 0 px; A1 all 21 settled captures, full frame — **0
  px each**; A2 042/077 consist only of old/new pixels — 0 "neither"; A3
  (report only) the production order's prefix disagrees with 042/077 in
  21,132/3,558 px, as expected from an unknown order; A4 (report only) the
  timing fit above plus contact sheets `timeline_dutch.png` /
  `timeline_english.png`. Exits 1 if an EXACT check fails. (A4 predates
  the timed run; the timing is now checked by the scratch `TimingAnalysis`
  / `OrderCheck` / `Verify` under the session's `scratchpad\intro\timing`:
  classification of 167..340, prefix nesting of the twelve mid captures,
  replay of the clock at every stamp with a contact sheet
  `out\timed_sheet.png`.)
- **Tests:** `ClassicDepartureTimelineTest` (constant-time reveal whatever
  the size, the onset table's order and bounds, the black lead-in, the
  clock against the 26 bracketing and mid-dissolve stamps of the timed run
  within ±50 ms, schedule boundaries, monotonic capped reveal, done at the
  end and not before, skip, bad input, the dissolve order is a fixed
  non-sequential permutation of exactly the differing pixels) and
  `ClassicDepartureCaptionsTest` (synthetic text files: the `@BUILD2/3/4/7`
  substitutions, Holland's prefix, missing entries, `available`).
- **Open items:** how the original reacts to input during the show; whether
  picture 10 → first scene is a cut or a dissolve and how long picture 10
  stays (both fell into the timed run's second capture gap; we cut, after
  an extrapolated hold); the onsets of pictures 4, 5 and 10 (gaps; a second
  timed run with the gaps elsewhere would settle them) and why pictures 1-2
  stay longer; the exact dissolve order; the source of Holland's extra
  space and whether France/Spain have one; `@BUILD2`'s `%STRING0` at other
  difficulty levels; the music during the departure.

## First scene (MSS0 + @TUTORIAL1) (`ClassicFirstScene`, `ClassicMenuBar`, `ClassicHud`, `ClassicHudOverlay`)

After the departure the original opens the game on a scene of its own: the
admiral tells the player where the ship is. References: Dutch
`start-sequence-dutch/opening_083` (Dutch merchantman), English
`start-sequence/opening_049` (English caravel). FreeCol's equivalent is its
start message "Nach Monaten auf See …" (`model.player.startGame`,
Player.java:2725-2733) in a popup with the coat of arms; the classic UI now
shows the original scene instead.

- **What it shows (measured, 0 px).** The preview harness repaints the band,
  the right panel, the advisor box and the portrait's opaque pixels —
  30,469 pixels per capture — with **0 differing pixels in both 083 and
  049** when the original's arrow is painted at (160,100); without the arrow
  exactly its 4 pixels at (165..166, 112..113) differ. The map area is not
  compared (see "Open items").
  - **Title band** instead of the menu bar (`ClassicMenuBar.paintBand`):
    `WOODTILE.SS.000` tiled from screen (0,0) over rows 0..6, a black row 7,
    then the whole line in gold `0xC7A220`, FONTTINY, glyph top y = 1,
    centred `x = (320 − w)/2` (083: x = 83, w = 154; 049: x = 98, w = 124).
    Text = NAMES.TXT `@NATIONALITY[n]` + " " + `@UNIT[ship]` + " " + LABELS.TXT
    `@MISC` 6 ("arriving from") + " " + `@HOMEPORT[n]`. Ship rows of
    `@UNIT`: caravel 13, merchantman 14, galleon 15, privateer 16, frigate
    17, man-of-war 18 (NAMES.TXT:332-337). The menu bar of the next stage
    uses the same chrome.
  - **Right panel in scene mode** (`ClassicHud`): a black column x = 240,
    `WOODTILE.SS.000` over x 241..319 from phase (0,0) (not `WOODPANL`), the
    minimap frame (1-px `0xAA5500` ring at (251,8,58,41), interior 56×39 at
    (252,9), black where unexplored, one pixel per tile, explored ocean
    `0x202C8A`, a tile with a unit or colony in its owner's colour — Holland
    `0xFF7100`, England `0xFF0000`), the white 15×12 viewport ring drawn last
    at (293,22), a black row y = 49, then FONTTINY green lines at (242,51)
    season and year (NAMES.TXT `@SEASONS`; FreeCol has no season before 1600,
    which reads as spring) and (242,58) gold label + gold + `$` + two spaces
    + tax label + " " + tax (LABELS.TXT `@CTITLE` 1 and 9 via the new
    `ClassicText.label`; no space after the colon, two before the tax label,
    FONTTINY's `$` is the coin glyph). No unit block — that is the panel's
    normal mode, the next stage.
  - **Minimap window** (rules shared with the next stage): horizontally
    `px = 251 + mapX` on maps up to 58 columns (the original's; a wider
    FreeCol map scrolls around the ring — assumed); vertically the ring sits
    at minimap row 13 (y = 22) unless clamped at the map's ends. The view
    for the scene is the original's framing: the ship in view column 7, row
    6, clamped to columns/rows 1 .. size−2 (`ClassicHud.viewFor`: in 083 the
    ship at column 53 of 58 gives c0 = 42, ring x = 293, the ship in view
    column 11).
  - **The advisor** (`ClassicFirstScene.paintAdvisor`): the in-game dialog
    frame (`ClassicMenuBox.paintDialogFrame`, GAME theme, wood from the box
    corner), the text, and the admiral `MSS0.SS.000` (75×91) drawn LAST — his
    hands overlap the box's top and left edges. Text = GAME.TXT `@TUTORIAL1`
    with `%STRING0` = the ship's `@UNIT` name (the original does not inflect
    the fixed possessive before it, also for the feminine caravel), laid out
    by `ClassicTextLayout` in FONTTINY with the message's `@width` 230 (228..232 give the captured breaks, 226 does not),
    at box + (5, 9), pitch 6, green with `{…}` in gold.
- **Geometry rule (observed; fits 083, 049 and the Europe boxes 011/013).**
  Box = `@width + 6` wide, `ClassicMenuBox.dialogHeight(P, 0)` tall (P laid-out
  lines; 5 → 48). The portrait sits at a fixed offset from the box that
  depends on the advisor — admiral `MSS0` at box + (−4, −71), trade advisor
  `MSS2` (122×84) at box + (57, −78) — and the UNION of portrait and box is
  centred on the screen: `x = (320 − w)/2`, `y = (200 − h + 1)/2`. For the
  admiral: union 240×119 at (40,41) → portrait (40,41), box (44,112,236,48);
  for 011: box (42,102), portrait (99,24); for 013: box (42,93), portrait
  (99,15). The portraits' SS anchors are degenerate (1,1), so the anchor
  cannot place them. TUTORIAL1's `@x=10` / `@y=40` are not used: they are
  neither the text (49,121), the box nor the portrait position; their
  meaning is unknown.
- **Dialog formula fix (part of this step).** The advisor box exposed that
  prompt lines are 6 px apart, not 8: `dialogHeight = 6P + 8R + 18`,
  `promptTop = y + 9 + 6k`, `rowTop = y + 13 + 6P + 8i`
  (`ClassicMenuBox.PROMPT_PITCH`). One-line boxes (all title-screen boxes)
  are unchanged; multi-line notices get the measured height.
- **When it shows: a fresh-game flag, never the message.**
  `ClassicGUI.firstScenePending` is set in `showStartGamePanel` right before
  `setReady`/`requestLaunch` — the one engine path every fresh single-player
  game takes and no load does — but only when `newWorldStartRequested` says
  the title screen asked for the game (`startNewWorldGame`: NEUE WELT or the
  chain-less defaults). FreeCol's `--fast` start calls
  `startSinglePlayerGame` itself (FreeCol.java:1619 → `FreeColClient`), so
  it never shows the scene: it is the scripted smoke path, and a modal
  scene would stop it. The flag is cleared on every way to a load or back to
  the title (`loadSavedGame`, `prepareShowingMainMenu`, `teardownInGame`,
  hence `showMainPanel`). The HUD build in `reconnectGUI` consumes it and
  calls `showFirstScene`, which shows the scene if the turn is 1, the
  player is one of the four original nations, a ship of theirs is on the
  map (the active unit, else the first ship), and the pack has the texts,
  FONTTINY and `MSS0`; otherwise one INFO line says why and the game starts
  as before. Why not trigger on FreeCol's message: it is filtered by the
  client option `guiShowTutorial`, and FreeCol re-adds it for every LOADED
  save of turn 1 (PreGameController.startGameInternal :322-324), where the
  original shows nothing.
- **The start message and other notices (`showMessagePopup`, the shared
  funnel of `showModelMessages` and `showReportTurnPanel`).** FreeCol's start
  message is ALWAYS dropped (`withoutStartMessage`). Anything else that
  arrives while the scene is pending or up is held (`heldMessages`) and
  shown, in arrival order, right after it is dismissed. Ordering:
  `startGameInternal` runs on the EDT and only queues the HUD build
  (`reconnectGUI` → `invokeLater`), then adds the start message and calls
  `nextModelMessage`, which reaches the funnel before the HUD exists; the
  flag is already set, so nothing modal blocks and the queued build runs
  next.
- **Where it is drawn.** The scene lives in `ClassicHudOverlay`, installed
  as the frame's glass pane: it paints its own integer-scaled, letterboxed
  320×200 canvas (the title screen's formula), with band, panel and advisor
  opaque, the letterbox black and the map area transparent so the live map
  shows through. Since the in-game HUD became the same canvas
  (`ClassicHudPane`, see "In-game HUD"; same scale and letterbox, no
  `JMenuBar`), the band lies exactly on the strip, the scene's panel on the
  live panel and the map area exactly over the map viewer, which is on the
  HUD's grid (15×12 tiles of 16 px, the unit in column 7, row 6; the
  original's edge clamping that puts 083's ship in column 11 is still a
  follow-up). Any part of the map area not over the live map viewer is
  still painted black, as a guard. The strip ignores menu input while the
  scene is up, and `showFirstScene` closes an open dropdown first. Rendered
  once per show (`ClassicFirstScene.render`, a 320×200 ARGB picture), so
  painting is one scaled blit.
- **Input while it is up** (the original's rule is not visible in stills —
  a proposal, like the departure's skip). The layer takes the focus,
  consumes every key (so the map's Enter/Space/W/B/arrow bindings and the
  menu bar's accelerators cannot fire) and every mouse event. It is
  dismissed by a key press that is not auto-repeat, not a bare modifier and
  without Alt/Ctrl/Meta, or by a left/right press. Input made before the
  scene was on screen never dismisses it: events time-stamped before the
  show are ignored, and the auto-repeat of a key held since before (for
  example one that skipped the departure and stayed down through the
  engine start) is recognised by `FrameKeys`, which now records every key's
  press and release frame-wide (`isAutoRepeat`; a press more than 1.1 s
  after the previous one of the same key counts as new). Alt+Enter toggles
  full screen and Alt+F4 asks to quit as usual (`FrameKeys` runs first).
  Backstops: `ClassicGUI.isDialogShowing()` is true while the scene is up,
  so every `FreeColAction` disables itself on `updateActions` (the menu bar
  greys out), and the map viewer's own key actions and edge scroll do
  nothing while it is true. On dismissal: layer hidden, actions re-enabled,
  focus back to the map, then the held notices. Log lines: `Classic first
  scene shown (…)`, `Classic first scene dismissed.`, `… notice(s) held
  until the first scene is dismissed.`
- **Preview harness** (scratch, never in the repo): section `scene` of
  `StartPreview` (`run.ps1 -only scene`). S1 renders band + panel + advisor
  through `ClassicFirstScene.paintScene` over the capture's map area, with
  the minimap state measured in the capture (083: explored 3×3 at
  303..305 × 27..29, ship pixel (304,28); 049: column x = 306, the ship
  under the ring) on a synthetic 58×72 map framed by `ClassicHud.viewFor`:
  **0 px** with the arrow, exactly the 4 arrow pixels without it, in both
  captures; H3 the panel alone: 0 px; S2 the dialog formula for every
  captured box. It also writes `overlay_1920x1080_review.png` /
  `overlay_800x600_review.png` (the live layer over a stand-in game view).
  `regress.ps1` re-runs the title (`MainMenuPreview`) and new-game chain
  (`NewWorldPreview`) previews after the formula change: the chain stays
  at 0 px on all 19 captures, the load box at 0 px, the title box at the
  same 78 px (86 with the arrow) as before — the owner's "Version 2026" line.
- **Tests:** `ClassicMenuBoxTest` (the formula against every captured box,
  P = 1 unchanged), `ClassicFirstSceneTest` (placement for MSS0/MSS2, ship
  rows, band and advisor texts with the placeholder on synthetic text
  files, the start-message filter), `ClassicHudTest` (view framing, ring and
  minimap scrolling incl. clamps, minimap painting, status lines and
  `ClassicText.label`, the strip chrome, the layer's canvas and its black
  uncovered map area).
- **Open items:** the map under the scene is on the original's grid (15×12
  tiles of 16 px, unit in row 6) but not yet its framing (ship in column 11,
  clamped at the map edge) nor its unit drawing (the (−2,0) shadow and the
  order flag) — the map viewer follow-up; the original's dismiss rule; the original's land
  colours on the minimap (FreeCol's minimap terrain colours stand in);
  France/Spain unit colours on the minimap are FreeCol's (not measured); the
  meaning of TUTORIAL1's `@x`/`@y`; the original's starting soldier is a
  veteran, FreeCol's only on the two easiest levels (the scene's text names
  "a soldier" either way).

## Music (original soundtrack) (`ClassicSoundController`)

The owner wants the original 1994 music and no FreeCol music at all; the
title piece must carry on into the game instead of being cut off at game
start. Sound *effects* stay FreeCol's for now. (2026-10-04)

- **The pack.** `data/mods/classic_music/` (mod id `classic_music`),
  git-ignored (`.gitignore:15`; copyrighted):
  ```
  mod.xml                     <mod id="classic_music"/>
  resources.properties        the title line + the tracks directory
  resources/music/track01.wav … track26.wav
  ```
  `trackNN.wav` = Steam "Sid Meier's Colonization Soundtrack - Track N.mp3",
  converted to plain 16-bit PCM WAV, 44.1 kHz stereo (≈328 MB, 32:30). MP3
  cannot be used directly: neither the JDK nor `jars/` has an MP3 decoder
  (`SoundPlayer.getAudioInputStream` knows OGG via jorbis and what
  `javax.sound` reads). The names must be lowercase `.wav`, because a
  directory resource only accepts `.ogg`/`.wav` (case-sensitive,
  AudioResource.java:61), and **one** undecodable file there drops the whole
  directory resource (AudioResource.java:62-66). It is a separate pack, not
  part of `classic_original/`, because `ant classic-assets` deletes and
  regenerates that one (ClassicAssetConverter.java:94).
- **Creating / re-creating it** (Windows PowerShell 5.1, nothing downloaded):
  ```
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\classic_assets\convert-soundtrack.ps1
  ```
  The script converts the MP3s with Windows' own Media Foundation decoder
  (skipping up-to-date WAVs) and writes `mod.xml` and `resources.properties`
  only if they are missing, so a re-run never undoes the owner's title choice.
  Details: `tools/classic_assets/README.md`.
- **Where the tracks are.** The owner's original MP3s:
  `C:\Program Files (x86)\Steam\steamapps\common\Sid Meier's Colonization\Bonus Content\Soundtrack\`
  ("Sid Meier's Colonization Soundtrack - Track 1..26.mp3"). The copies the
  game plays: `data\mods\classic_music\resources\music\track01.wav ..
  track26.wav` in the repository folder (trackNN = Steam "Track N").
- **Choosing the title piece — the ONE line.** In
  `data/mods/classic_music/resources.properties` (line 10):
  ```
  sound.classic.music.title=resources/music/track01.wav
  ```
  Change the number (two digits, Steam "Track N"), save, restart the game.
  Or let the script write it:
  `convert-soundtrack.ps1 -TitleTrack 5`. Default: Track 1 (the original's
  title theme is not identified yet; the owner picks it by ear). The other
  key, `sound.classic.music.tracks=resources/music`, names the directory of
  all tracks; the in-game list is "all tracks minus the title piece". A title
  line naming a missing file falls back to the first track.
- **Loading.** `FreeColClient.withClassicPacks` (FreeColClient.java:398)
  overlays every present pack of `CLASSIC_PACK_IDS` (:371, `classic_original`
  then `classic_music`) last in the mod list under `--classic`, before the
  sound controller is created (:268-271). Absent, it logs "no 'classic_music'
  pack found; classic music silent." Mapping the pack opens the 27 WAV headers
  once at start-up (≈30 ms); playback streams from disk in 16 KB chunks, so the
  size costs no heap.
- **Why a `SoundController` subclass, not a `GUI.playSound` override.** The GUI
  is not a complete choke point: `PreGameController.startGameInternal`
  (PreGameController.java:294-300) and `MapEditorController` (:179-183) set
  FreeCol's default playlist directly on the `SoundController`, and
  `GUI.playSound` (GUI.java:1019-1025) sends a key to the music player only if
  its resource says `.type=music` — the 19thCenturyNations intros have no type
  and would play as "effects". `ClassicSoundController` (instantiated at
  FreeColClient.java:268-271 under `--classic`) overrides the only two music
  entry points, `playMusic` and `setDefaultPlaylist`, and never calls their
  `super`; `playSound` diverts the music family by key prefix (`sound.intro.`,
  `sound.anthem.`, `sound.event.meet.`, `sound.music.`,
  `sound.event.fountainOfYouth`). FreeCol music is unreachable by
  construction; effects (and `playSound(null)` = stop the effect) are
  untouched.
- **Behaviour** (a small state machine, `Jukebox`: SILENT / TITLE / GAME,
  driven purely through the player's default playlist, which
  `SoundPlayer.run` loops shuffled whenever its queue is empty):
  - *First picture* (2026-10-05) — on a normal launch the title piece starts
    with the first picture of the early window, together with the intro;
    see "Music from the first second" below. It is then already playing
    when the title appears, and nothing below restarts it.
  - *Title screen* — `ClassicGUI.showMainPanel` → `playTitleMusic()`: the list
    becomes the title piece alone, then `stop()`; the piece starts at once and
    loops while the menu is up (a no-op when the early piece was adopted). Every way to the title ends in `showMainPanel`
    (start-up, back to the title, in-game "Neues Spiel", defeat/quit, failed
    starts and loads), and re-showing the title does not restart the piece.
  - *Game start* (new game, load from the title, in-game load) — FreeCol's
    nation intro `sound.intro.<nation>` (PreGameController.java:295) is read
    as "a game has started": the list becomes the 25 other tracks **without**
    a stop, so the title piece plays to its natural end and then the in-game
    tracks cycle (shuffled, refilled forever). An in-game load keeps the
    current track. FreeCol's `setDefaultPlaylist` right after is ignored.
  - *Back to the title* — the in-game track is cut, the title piece starts.
  - *`--fast` / a save on the command line* — the title screen never shows
    (FreeCol's `sound.intro.general` is ignored), so the in-game list starts
    directly.
  - *Anthems, first contact, fountain of youth, map editor, the options
    dialog's test sound, `playMusic(null)`* — ignored; the soundtrack runs on.
  - *No pack, broken pack, or sound disabled* — silence, never FreeCol music.
- **Core changes (minimal, all behaviour-preserving for FreeCol):**
  `SoundController.getMusicPlayer()` (protected, SoundController.java:97);
  `SoundPlayer.defaultPlayList` is now `volatile` (SoundPlayer.java:243;
  written on the EDT, read by the player thread); and a stop generation
  counter (`stopCount`, SoundPlayer.java:93/115/140/174) closes a lost-stop
  race: `playDone` was cleared only after a dequeued file's line was open, so
  a `stop()` landing in between was lost and that file played to its end —
  for "back to the title" that would be a whole in-game track.
- **Music from the first second (`ClassicEarlyMusic`, 2026-10-05).** The
  owner wants the music "zeitgleich mit dem Zeigen des Vorspanns"; before,
  it started at client attach (~4.4 s). Now:
  - `FreeCol.createClassicSplashScreen` calls `ClassicEarlyMusic.prepare()`
    on a normal launch with sound (not `--fast`, a save, a debug start,
    `--no-sound`, `--no-splash`, headless) BEFORE the window is built. A
    daemon thread "Classic early music" (~0.25 s, overlapping the window's
    ~0.45 s) finds `classic_music` (`ClassicPackFiles.modDirectory`), picks
    the title by `Soundtrack.of` (the same rule as the controller, incl. the
    first-track fallback), peeks `model.option.musicVolume` and
    `model.option.audioMixer` from the options file the way
    `ClientOptions.getSpecialOptions` does (failure = 100 %, automatic;
    volume 0 = no early start), validates silently (audio header,
    `isLineSupported`, test open/close of a line), and only then — under the
    hand-over lock, only if nobody cancelled — builds a `SoundPlayer` with
    stand-in `AudioMixerOption`/`PercentageOption` (null specification).
  - `ClassicStartupScreen.show` calls `ClassicEarlyMusic.go()` right after
    the first picture is painted (the black fallback window calls it after
    `setVisible`): the title becomes the player's default playlist; the
    intro's 300 ms black lead-in covers the line start.
  - Hand-over without a restart: `SoundController` has a protected
    constructor taking a running music player (SoundController.java; the
    public one passes null, plain FreeCol unchanged).
    `ClassicSoundController`'s public constructor delegates with
    `ClassicEarlyMusic.take()`. If the base class did not use that player
    (`--no-sound`, unreadable mixer option) or `!canPlaySound()`, it is
    silenced for good (empty list + stop: its thread never ends). Otherwise
    the real `MUSIC_VOLUME`/`AUDIO_MIXER` are forwarded into the stand-ins
    (the volume applies to the playing line at once) and kept forwarded by
    listeners; if the piece plays and the resources resolve the same file
    (canonical path) or nothing, `Jukebox.adoptTitle()` records TITLE with no
    output call, so `playTitleMusic` is a no-op and game start swaps the list
    without a stop as before; a different resolved title switches once.
  - `take()` while the preparation still runs cancels it; its late result
    builds nothing, and the piece starts at attach as before. Every failure
    ends in today's behaviour (start at attach, or silence) — never FreeCol
    music, never two pieces.
  - Core hardening: `SoundPlayer.run` waits `WAIT_TIMEOUT` (100 ms) after a
    failed `playSound`, instead of refilling and failing at full speed
    (which flooded the log with WARNINGs when a line failed mid-session).
  - Log lines: `Classic start-up: title piece started after N ms
    (trackNN.wav, prepared in N ms)`, `Classic start-up: early music not
    started: <reason>`, `Classic music: title piece adopted from the
    start-up (trackNN.wav)`, `Classic music: early title piece silenced`.
- **Tests.** `ClassicSoundControllerTest` (no audio device needed): music-key
  routing, title/in-game selection, and the title → game → title transitions
  against a recording player; `adoptTitle` makes no output call, `title()`
  afterwards none either, `game()` swaps without a stop, a different
  resolved title switches once. `ClassicEarlyMusicTest`: the hand-over state
  machine with a fake player in every order (go before ready, take before
  ready = cancel with nothing built, take while ready or playing, double
  take, go without prepare, failure, null player) and the volume rules.
- **Open / not yet:**
  - Which Steam track is the original's title theme (default Track 1).
    **To confirm with the owner by ear:** before this change the piece
    heard "ganz zu Beginn" (the one he asked to keep running) was FreeCol's
    own `data/base/resources/sound/intro.ogg` (`sound.intro.general`,
    data/base/resources.properties:267-268) — the classic UI had no music of
    its own then. If he means that piece, it is a FreeCol composition, which
    conflicts with "keine FreeCol-Musik"; he decides. The Steam files are
    album tracks 2..27 of 27 (ID3), so album track 1 is not among them.
  - The in-game order is shuffled on every refill (SoundPlayer.java:211), so a
    track can repeat across a cycle boundary; whether the original plays
    specific pieces per situation (Europe, war, …) is not modelled.
  - The title piece loops on the title screen; if the original plays it
    once, `Jukebox.title` should queue it once instead.
  - The classic UI has no options dialog, so the music volume stays
    FreeCol's `MUSIC_VOLUME` (default 100); use the Windows volume mixer.

## Full screen, Alt+Enter and exit (`ClassicGUI`)

The owner wants the game to look like the original running full screen in
DOSBox: no window chrome anywhere. (2026-10-04)

- **Default = borderless full screen.** Without an explicit `--windowsize`,
  the main `JFrame` is shown undecorated with bounds = the *whole*
  monitor (`GraphicsConfiguration.getBounds()`, taskbar included) —
  `ClassicFrame.applyFrameMode(true)`. The frame, its mode and the remembered
  windowed bounds live in `ClassicFrame` (moved out of `ClassicGUI`
  unchanged), because two parties create the window: the early start-up
  window (see "Fast start" below) and, without it, `ClassicGUI.startGUI`.
  On the owner's 1920×1200 (16:10, 100 % scaling)
  monitor the 320×200 title canvas fills the screen at exactly ×6, no black
  bars: the panels take their whole scale from the content pane's size, and
  undecorated that is the full monitor (no insets, no menu bar on the
  title). In game the menu bar keeps its strip at the top as before.
- **Not exclusive full screen** (`GraphicsDevice.setFullScreenWindow`): the
  classic screens are separate top-level windows plus modal popups, and on
  Windows an exclusive window minimises or flickers as soon as another window
  takes focus, Alt+Tab misbehaves and a modal dialog can hide behind it. A
  borderless window is an ordinary window to the OS. Black stays the
  letterbox colour (the frame and its root pane are black too, so a switch
  never flashes grey).
- **With `--windowsize WxH`** the window opens decorated at that size, as
  before.
- **Start-up screen = the title itself, not FreeCol's splash.** Under
  `--classic` without an explicit `--splash`, FreeCol's `splash.jpg` (FreeCol
  logo, "an open source Colonization game", in a box on the desktop) is never
  shown. With the `classic_original` pack the main window opens about a
  second after the double click with the title already painted (see "Fast
  start" below); it is the first window the process shows, so `toFront` +
  `requestFocus` get the foreground, which is what makes Windows hide the
  taskbar behind it. **Fallback** (no pack, or the early window failed): the
  old black borderless window over the whole default monitor
  (`FreeCol.createClassicSplashScreen`, `SplashScreen.blackFullScreen`), which
  `FreeColClient` disposes only *after* `startGUI` has shown the main window
  (a dispose queued behind `startGUI`'s own `invokeLater`), so the desktop
  never shows in between and the process keeps the foreground. `--no-splash`
  switches both off (no early window, no black screen: the window opens when
  the client is ready — a developer escape hatch); a positive `--windowsize`
  without the pack: no start-up screen.
- **Alt+Enter** toggles borderless full screen ⇄ a decorated window, as in
  DOSBox — on the title, in game and in every classic sub-window. It is a
  `KeyEventDispatcher` (`FrameKeys`), so it wins over the panels' own Enter
  keys and over FreeCol's "alt ENTER" accelerator of the reused menu item
  (`changeWindowedModeAction`); that menu item, when clicked, toggles too
  (`changeWindowedMode`/`isWindowed` are overridden). One toggle per press:
  auto-repeat and the matching typed/released Enter are swallowed (a press
  more than 1.1 s after the last one counts as new, because the release can
  be lost while the window is re-created).
  - The window: the bounds (and maximised state) the player last left it
    with; the first time the largest whole multiple of 320×200 that fits the
    work area (×5 = 1600×1000 content here), centred.
  - How: `setUndecorated` only works on a non-displayable frame, so the frame
    is `dispose()`d and shown again. The component tree (content pane, menu
    bar, key bindings — re-registered by `addNotify`) and the frame object
    survive; focus goes back to the component that had it.
  - Open sub-windows (colony, build queue, Europe, report) are re-framed for
    the new mode; ones the player closed via their own close box are left
    alone (`isDisplayable()`).
  - Ignored (logged) while a modal popup is up: disposing a window disposes
    its owned windows, which would silently dismiss the question.
- **No chrome anywhere** — one helper, `ClassicGUI.prepareChildWindow(window,
  ref, fullScreenSized)`, frames every window the classic UI opens; the mode
  is read off `ref`'s decoration (`isBorderless`), so `ClassicDialog` needs no
  GUI reference.
  - Full screen: the 320×200 screens (colony, build queue, Europe, all
    reports incl. high scores) are undecorated and cover `ref`'s bounds
    exactly (×6 here). Popups are undecorated and centred: `ClassicDialog`,
    and the `modalChoiceDialog` list, now built by hand (with a wood line
    border) because `JOptionPane.showInputDialog` returns an
    already-displayable dialog whose decoration can no longer change. The
    Europe screen's recruit/train/buy lists use the same list
    (`ClassicGUI.chooseFromList`, called from `ClassicEuropePanel.choose`).
  - Windowed: today's behaviour (decorated, packed, centred).
  - Popups are owned by the classic screen in front (`dialogOwner`), not
    always the main frame: on Windows raising an owned window raises its
    owner beneath it, which would bury a full-screen colony screen under
    the map. The active window is only a hint (searched with its owner
    chain — a just-disposed popup still names its screen); activation is
    asynchronous and null while another app is in front, so the GUI's own
    state decides otherwise: the open screens in the fixed order build
    queue, colony, Europe, report; the main frame only when none is open
    (or, windowed, when the map itself is the active window).
- **Exits without a title bar:**
  - **Alt+F4** (and the windowed X, and any `WM_CLOSE`) → `WINDOW_CLOSING`
    → `ClassicGUI.closeRequested`; the main frame is `DO_NOTHING_ON_CLOSE`
    (it was `EXIT_ON_CLOSE`, which in borderless full screen made one Alt+F4
    slip end the game unsaved and skip `FreeColClient.quit`). Like FreeCol's
    own `WindowedFrameListener`, it asks first: in game (or map editor)
    `askToQuit` — the same classic popup as Spiel → Beenden; on the live
    title menu the "Colonization beenden?" box (`offerQuit`, Nein barred),
    as Esc opens it; on the passive backdrop or the busy box (nothing at
    stake yet) `FreeColClient.quit()` directly. `FrameKeys` posts the
    `WINDOW_CLOSING` itself, since AWT hands system keys to Java first; a
    second, native close while the question is open is ignored. In a
    sub-window or popup Alt+F4 closes just that one.
  - **In game:** Spiel → Beenden (FreeCol's `QuitAction` → `askToQuit` →
    classic confirm popup → `FreeColClient.quit`).
  - **On the title: Esc** opens the quit box "Colonization beenden?" / Ja /
    Nein (`Mode.QUIT`, `paintQuitBox`), drawn with `ClassicMenuBox` in the
    title style and modelled on the original's own exit question (GAME.TXT
    `@DOS`: "Abbrechen zu DOS?" Ja/Nein, `@default=2`, so **Nein** is barred
    first). Up/Down, Enter, hover and click; Esc again or a right click =
    Nein. Ja → `FreeColClient.quit()` — FreeCol's normal quit path (stop
    server, prune autosaves, `quitGUI`, `FreeCol.quit(0)`), the one FreeCol's
    own window listener uses when no game runs. The box is sized to its
    prompt (interior at least 80 px; `@DOS` has no `@width`), centred, over
    the bare title picture like the load box.
- **Open / not yet:**
  - HiDPI: the canvases pick their whole scale in *logical* pixels, so at a
    Windows scaling other than 100 % (e.g. 125 % on 1920×1200 → 1536×960
    logical → ×4 = 1280×800) black bars return. The owner's monitor runs at
    100 %; scaling in device pixels would fix it for others.
  - Each full-screen sub-window is its own top-level window with its own
    taskbar button (a `JFrame` cannot have an owner); Alt+Tab lists them.
  - The reused menu item's check mark does not follow the mode
    (`SelectableOptionAction` reads a client option this action does not
    have).

## Fast start (`ClassicStartupScreen`, `ClassicFrame`, `ClassicPackFiles`)

The owner waited ~30 s (warm disk cache; up to a minute as he perceived it)
in front of a black screen before the title appeared. The FreeCol.log of the
2026-10-04 live test splits that into two blocks of ~12-15 s each; both are
gone, and the title is painted before the client even exists. (2026-10-05)

| Stage | What | Where | Measured (headless harness, warm cache) |
|---|---|---|---|
| 1 | Directory-listing cache for the image size/variation search | `FreeColDataFile.getResourceMapping` / `sortedListing` | `classic_original` mapping 14.7 s → 0.56-0.64 s; tc 1.6 s → 0.55-0.57 s; whole headless `FreeColClient` constructor with the classic packs 17.7 s → 2.6 s |
| 2 | The Classic UI no longer waits for the full resource preload | `FreeColClient.startClassicGui`, `ResourceManager` | the 12-15 s preload (2954 resources) now runs in the background at `MIN_PRIORITY` |
| 3 | The main window opens before the client is built, title painted from pack files | `ClassicStartupScreen`, `FreeCol.createClassicSplashScreen` | JVM start → title painted 0.45-0.46 s headless (+ AWT/window ≈ 0.45 s), 0 px vs `opening_033` |

- **Stage 1 — why the pack mapping took 13 s.** For every image key
  `FreeColDataFile` looks for size alternatives (`x.size64.png`) and
  variations (`x2.png`): it listed, sorted and regex-matched the image's whole
  directory twice per image, recompiling the regex for every entry. The pack
  keeps its 1517 SS frames in one directory: ~3000 listings × 1517 entries.
  Now each directory is listed once per `getResourceMapping` call (a local
  map passed down the call chain, so concurrent calls never share it and a
  later call sees new files), the pattern is compiled once per lookup and a
  `startsWith`/`endsWith` pre-filter skips almost every entry. The result is
  unchanged: dumps of all 3071 image resources (770 with size alternatives,
  55 with variations) were identical before and after, and
  `FreeColDataFileListingTest` passes against both the old and the new class.
  Zip data files (saves) open a new `FileSystem` per lookup and so keep the
  old behaviour.
- **Stage 2 — why the window waited for every image.** `FreeColClient`
  started the GUI only from the completion callback of
  `ResourceManager.startPreloading`, i.e. after all 2954 resources were read
  (FreeCol's own 782 images alone 9 s). That is an optimisation, not a
  precondition: `ImageResource.getImage` loads a missing image on demand
  (`preload` is synchronized, so an EDT request waits for at most one image).
  Under `--classic` the constructor now calls `startGUI`, `updateActions` and
  the first task directly (it runs on the EDT already) and then starts the
  preload in the background (log line "Classic UI: background preload done.").
  `ResourceManager.preloadThread` is `volatile` now: a `prepare()` (every
  `--fast` start, every load from the title) regularly overlaps the running
  preload and polls that field. The SwingGUI path is unchanged.
- **Stage 3 — the early window.** The constructor still takes ~3 s and must
  stay on the EDT (`FreeColAction.addImageIcons` posts EDT tasks that use
  the GUI, created only near its end — off the EDT they NPE). So
  `FreeCol.createClassicSplashScreen`, which runs on the EDT just before the
  constructor is queued, calls `ClassicStartupScreen.show(windowSize, menu)`:
  - The window is built by `ClassicFrame` — the same borderless full screen
    or decorated `--windowsize` window as ever — with a real
    `ClassicMainMenuPanel` whose images and font come straight from the pack
    files (`MenuAssets.fromPackFiles` via `ClassicPackFiles`, no
    `ResourceManager`) and whose menu strings come from `Messages` (loaded at
    `FreeCol.java:323`). `menu` is false for `--fast`, a debug start or a save
    argument: then the passive picture, no menu, no music (as before).
  - It is painted with `paintImmediately` right after it is shown, so the
    picture is on screen before the constructor blocks the EDT (Swing's back
    buffer then also serves the OS's expose requests).
  - `ClassicGUI.startGUI` (called from the constructor, on the EDT) adopts
    it **synchronously** via `ClassicStartupScreen.take()`: frame, mode,
    panel, the real menu actions, the close listener and Alt+Enter/Alt+F4.
    Keys and clicks that AWT queued while the constructor blocked are
    dispatched after that, so they reach a live, attached menu.
  - **Typing ahead.** `show` runs in the same EDT task that queues the
    constructor (`FreeCol.startClient`), so practically all "early" input is
    dispatched only after the constructor, by the real actions — but still
    *before* the start-up task that calls `showMainPanel`. Enter mashed
    through the chain, or Down×3 + Enter + Enter for a save, has therefore
    already started the game or the load (panel STARTING / BUSY) when that
    call comes. `ClassicGUI.startupTitlePending` (set on adopting a live
    early window, cleared by the next `showMainPanel`) makes that one call
    change nothing — no title reset, no teardown, the title piece only if no
    music was chosen yet (`playTitleMusicIfSilent`). Without it the frozen
    audience was replaced by a live title for the seconds the server needs,
    and NEUE WELT clicked there started a second game while logged in.
  - Input that does reach the early window before the constructor (rare) is
    handled by the panel itself (the bar, the load box and the whole
    new-game chain need no client). A choice that needs the client (the end
    of the chain, loading a save, the hall of fame) is kept by
    `DeferringActions` (the first one only) and replayed by
    `ClassicGUI.showMainPanel` after the start-up's own call to it. "Ja" in
    the quit box ends at once (`FreeCol.quit(0)`).
  - `showMainPanel` no longer resets a live menu (`ClassicMainMenuPanel.isLive`:
    INTRO, TITLE, LOAD, NOTICE, QUIT, NEW_WORLD) when it has no notice to show —
    otherwise the start-up's call would undo the bar or a half-done chain.
    PASSIVE, BUSY and STARTING (a failed start or load, an ended game) are
    still reset to the title by every call except the start-up's own one
    described above.
  - Without the pack, `--no-splash`, or on any failure the old path runs
    (black screen; the window when the client is ready).
- **What the player sees now** (expected, warm cache; the live numbers are
  the two log lines below): the title and menu ~1-1.5 s after the double
  click; the menu reacts from ~4 s (the constructor's ~3 s, during which the
  Windows arrow shows and input is queued); the title music starts when the
  client is attached (~3-4 s, `showMainPanel` → `playTitleMusic`).
  **Since 2026-10-05** a normal launch opens on the intro instead (black,
  then the emblem; see "Opening (Vorspann)"), which keeps animating through
  the blocked ~3 s (own render thread, no arrow), and the title piece starts
  with that first picture (`ClassicEarlyMusic`, see "Music"); the title and
  menu follow after the intro (≈2:08) or at once on a key or click. The
  new-game chain needs only pack files, so it works fully while the
  background preload still runs; its art is prefetched on a daemon thread as
  soon as the title is live.
- **Log lines for live checks:** `Classic start-up: title shown after N ms`
  (`intro shown` on a normal launch; JVM start → painted) and
  `Classic start-up: client attached after N ms`;
  "ClassicGUI selected" now follows "overlaying 'classic_music'" within
  about a second, and "Classic UI: background preload done." comes after the
  window, not before.
- **Open:** cold-cache timings are unmeasured (the harness cannot flush the
  OS cache and may not launch the GUI); the background preload still reads
  FreeCol's own art too (9 of its 15 s of CPU), which a key filter could
  skip. DONE: the title music before client attach (`ClassicEarlyMusic`,
  ~0.25 s preparation overlapped with the window build).

## Seam facts (for the remaining/next work)

**`GUI` methods** (all no-ops in the base class; each Javadoc names its callers):
- View state: `changeView(Tile)` (TERRAIN), `changeView(Unit,boolean)`
  (MOVE_UNITS), `changeView()` (END_TURN), `changeView(MapTransform)`
  (map-editor only — ignore). `refresh()`/`refreshTile(Tile)`.
  `setFocus(Tile)`/`getFocus()`/`getFocusMapPoint()`/`setFocusMapPoint(Point)`.
- Model access: `getFreeColClient().getGame().getMap()`, `getMyPlayer()`; route
  clicks/keys through `getFreeColClient().getInGameController()`.

**Image lookups** (`ImageLibrary`): `getTerrainImage(TileType,x,y,size)` is the
base terrain the classic viewer draws; the pack-absent overlay fallbacks are
`getForestImage` / `getSizedOverlayImage` / `getRiverImage` (see
`ClassicTileArt`). With the pack present the feature overlays are the square
`PHYS0.SS` frames loaded by key (item (e), done — see "Feature overlays" above),
not FreeCol's isometric overlay art.

## Testing live (non-interactive harness)

The `--fast --no-intro` start-at-sea view (ship on an ocean patch, 0 SEVERE) is
the smoke test. Because this harness can't see the screen, drive/verify the
window from PowerShell: launch detached (`Start-Process -PassThru` with
redirected stdout/err — a `Start-Job` dies when the tool call ends); poll
`Get-Process -Id <pid>` for a non-zero `MainWindowHandle`; bring the window
frontmost with a minimize(6)→restore(9)→`SetForegroundWindow` bounce (a plain
`SetForegroundWindow` is refused — check `GetForegroundWindow`); drive input with
`SendKeys`/`SetCursorPos`/`mouse_event` only while frontmost; screenshot via
`CopyFromScreen` over `GetWindowRect`. Note `$pid` is a read-only automatic
variable — use another name. **Kill the game process as soon as verification is
done** (the window stealing foreground interrupts parallel work). A `WM_CLOSE`
no longer exits the classic UI: it asks first (see "Exits without a title
bar"). Under `--classic` with the pack, the first top-level window is
already the titled main window ("FreeCol — Classic UI (experimental)",
see "Fast start"), shown about a second after launch but unresponsive for the
next ~3 s while the client is built — wait for the log line
`Classic start-up: client attached` before driving it. Without the pack (or
with `--no-splash`) the first window is the old untitled black start-up
screen, so `MainWindowHandle` can be that window; wait for the handle whose
title is the main window's before bouncing it.

Hard-won details, each of which silently wastes a run:

- **`--fast` resumes the last save**, so the start state is whatever you left —
  and your test turns get autosaved back. The "starts at sea" start only happens
  on a profile with no saves.
- **A normal launch opens on the ≈2:08 intro (Vorspann).** Press one fresh
  key (not a modifier, not an Alt/Ctrl chord) or click once to skip to the
  title menu; the skipping key is consumed, so send it separately from the
  menu keys, and wait for `Classic intro: skipped after N ms` (or
  `finished`) before driving the menu. Keys typed ahead while the client is
  still being built spend their first key on the skip. `--fast` and
  `-Dfreecol.classic.intro=false` (a JVM option, before `-jar`/`-cp`'s main
  class) never show it; `--no-intro` does NOT switch it off. The title
  piece is audible from the first picture (`title piece started after N ms`
  then `title piece adopted from the start-up`).
- **The window takes ~1-2 s, the menu ~4 s** since the fast start (it took
  ~30–55 s before). Without the pack it still takes a few seconds more, and a
  cold disk cache is unmeasured: keep polling `MainWindowHandle` for a minute
  rather than giving up after a few seconds.
- **NEUE WELT now ends in the ~98 s departure.** After the audience the
  ten-picture departure runs before the engine starts. Press one fresh key
  (not one still held from the audience — auto-repeat never skips) or click
  once to skip it, then wait for `Classic departure: finished after N ms`
  in the log; the game view follows ~1-2 s later. `--fast` never shows it.
- **Every fresh game from the title opens on the admiral's first scene** —
  after NEUE WELT (or its chain-less defaults). `--fast` never shows it, also
  not on a profile without saves. It holds the focus and swallows every key
  and click until it is dismissed: press one fresh key (not one held since
  before it appeared) or click once, then wait for `Classic first scene
  dismissed.` in the log. Notices held meanwhile appear right after. A
  loaded save never shows the scene.
- **The original arrow is drawn in game** (first scene at (160,100) until
  the mouse moves, then HUD and dropdowns); the system cursor is hidden over
  the canvas and visible in the letterbox and over other windows.
- **Ctrl+N goes back to the title** (asks first). Menu rows whose feature
  does not exist yet (save, load, options, Colonipädie, ...) look like the
  original's and show "Diese Funktion folgt in einer späteren Version."
- **The in-game menus are painted, not Swing.** There is no `JMenuBar` any
  more: open a menu with Alt + its gold letter (G V O R T C) or a click on
  its title, move with the arrows, Enter fires, Escape closes. FreeCol's own
  accelerators are gone; the original keys are bound instead (A F S L U O P
  R G Shift+D, M/V, E Z X C, F2-F10, Ctrl+N; Military moved to Shift+F7). Old smoke
  steps that used FreeCol menu keys (e.g. Ctrl+S) need the new keys or menus.
  The log shows `Classic key map: N keys bound.` once per game view.
- **Drive states that actually produce output.** An idle unit generates no
  notices at all. Populated saves, ending turns, `B` (found colony) and sailing
  a ship east into the high seas (fires the `highseas.text` confirm) do.
- **Screenshot a popup by the handle you enumerated**, not by re-finding it by
  title: `GetWindowText` raced against dialog creation returns a truncated title
  (a "FreeCol" dialog read as "F"), and the re-find then misses.
- **Log lives in `%USERPROFILE%\OneDrive\Dokumente\freecol\FreeCol.log`**
  (`getUserCacheDirectory()`, OneDrive-redirected Documents) — *not* the repo root.
- In PowerShell, `Write-Output` inside a function becomes part of its **return
  value**; a logging line will silently corrupt an `if (Check ...)` boolean. Use
  `Write-Host`.

## Acceptance harness: frame recorder and input script (`ClassicFrameRecorder`, `ClassicScript`, `ClassicScriptDriver`, `ClassicTestHarness`, `ClassicPrefs`)

Build spec W1. Two JVM properties turn a game into a measurable, unattended
run; without them nothing changes (each hook is one volatile read, the HUD
paints as before). `ClassicTestHarness.install` (from `startGUI`) binds both.

**Recorder** (`-Dfreecol.classic.recordDir=<dir>`, palette
`-Dfreecol.classic.recordPalette=<png|768-byte file>`). Writes the HUD the way
`ZmbvExtract` decoded the original's DOSBox clips, so the clip-analysis tools
(BlinkScan, MoveScan2, Activation, MarginCheck, Ring, EndTurn) run on it:
- `frame_NNNNNN.png` for frame 0 and every changed frame, 320x200, 8-bit
  indexed with the given palette (the landfall clip's frame #0). RGB maps to
  indices exactly, a colour held twice (59/120, 56/121) takes the lowest index,
  a colour not in the palette its nearest entry (counted in `summary.txt`).
- `timeline.csv`, one row per frame on an absolute 70.086303 Hz grid
  (the clips' strh rate), ZmbvExtract's columns and number format.
- `events.log`: `nanoTime,ms,frame,event,detail`. Events: `state` (the probe:
  turn, current player, view mode, active unit and moves, view origin, open
  dialogs; logged when it changes), `script*`, `key-post/press/release`,
  `click`, `move-key`, `move-done`, `pan`, `slide-start` (unit, tiles, view,
  cell), `slide-step`, `slide-end`, `slide-skip`, `final-draw`, `end-turn`,
  `dialog-open/close`, `music-request`, `music-mode`, `pref`, `late`. Reserved
  for the M1 items: `blink` (W3), `endturn-timer-start/fire` (W5),
  `palette-step` (W6c), `music-fade`. Add a hook with
  `ClassicFrameRecorder.event(name, detail)`; guard a costly detail with
  `ClassicFrameRecorder.on()`.
- `summary.txt`: frames, PNGs, late ticks, palette misses, paint cost.

How the pixels are taken: while recording, `ClassicHudPane` is the painting
origin of all its children (`isPaintingOrigin`), paints itself as a print into
the recorder's image and shows that image; each finished paint is copied under
a lock for the sampler thread, which reads the centre pixel of every s x s
block. So the samples are exactly what the screen shows, all painting stays on
the EDT, and the blocking slide (`paintImmediately`) is captured frame by
frame. Not captured: the first scene (glass pane) and the JDialog popups (only
their open/close events). Cost at s = 3: about 5.4 ms per HUD paint + 0.4 ms
copy + 1.5-2 ms blit, i.e. a step of today's slide takes ~30 ms instead of
~25 ms; `-Dfreecol.classic.recordFrames=false` records the events alone with
the HUD painting untouched, to time the game itself.

Timing on Windows: `LockSupport.parkNanos` wakes on the 15.6 ms system tick
(measured up to 15 ms late); `Thread.sleep` keeps to ~1 ms. The sampler sleeps
to ~1.5 ms before each tick and spins the rest (`waitUntil`, ~11 % of a core);
2-3 late ticks per 33 s run remain. Schedule slides and blinks the same way,
not with `parkNanos`.

**Input script** (`-Dfreecol.classic.script=<file>`, format in
`ClassicScript`'s class comment): `wait <ms>`, `key <KeyStroke>` (e.g. `LEFT`,
`NUMPAD7`, `ENTER`, `alt G`), `click <x> <y>` (canvas pixels), `waitGame`,
`waitIdle`, `waitTurn` (optional timeout ms), `pref <name> on|off` (the classic
prefs below, or `autoSave`/`combatAnalysis`/`tutorTips`, which set FreeCol's
own options), `log <text>`, `quit`. It runs on its own thread. A key is a
press, 80 ms, a release (plus `KEY_TYPED` for a character), dispatched on the
EDT with `Component.dispatchEvent` to the component a real key would reach:
the focus owner, else the open modal dialog's or the frame's most recent focus
owner, else the map. That is the real `KeyboardFocusManager` path (frame
dispatchers, then bindings incl. the map's `WHEN_IN_FOCUSED_WINDOW` ones) and
works while the window has no focus, which an unattended start cannot
guarantee (posting to the event queue would drop the key then). A timeout or
error logs `script-error`, stops the recorder and quits; the outcome is in
`<script>.result` (`ok`, `ended`, `error ...`). `quit` flushes the recorder
and leaves through `FreeColClient.quit`.

**Classic prefs** (`ClassicPrefs`, `classic-options.properties` in the user
config directory, build spec section 2): `showNativeMoves` on,
`showEuropeanMoves` on, `moveAccelerator` off, `endTurnPrompt` off,
`waterCycling` on (the original's state A). Autom. Sichern, Kampfanalyse and
Tutortips are FreeCol options (`autosavePeriod`, `guiShowPreCombat`,
`guiShowTutorial`). Read a pref where it is used, not once at start. Nothing
reads the prefs yet; W2/W5/W14/W17 will.

The sandbox launcher, the copied tools and today's baseline are outside the
repo, in `C:\Users\koch_\freecol-spike-results\m1` (`W1.md`).
