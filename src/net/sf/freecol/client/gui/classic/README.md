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

## The rules (`data/rules/levi`; master plan R1a, R1c, R2)

The Classic UI's new games play the ruleset **"levi"**: the `classic` rules
(the original's) with Roger's choices from `Regeln-Auswahl.md` ("Rogers Wahl",
2026-10-06) and his house rules. Both new-game paths take it,
`ClassicGUI.startNewWorldGame` (NEUE WELT) and `--classic --fast`/`--debug`
(`FreeCol.getRulesSpecification`), through `FreeCol.getNewGameRules`:
`--rules` if given, else `FreeCol.CLASSIC_RULES` under `--classic`, else
`freecol`. It is **not** the default rules (`FreeCol.getRules`, still
`freecol`), because those also name the user's options folder
(`FreeColDirectories.getOptionsDirectory`, `<config>/freecol`), which must not
move. A save keeps its own rules, so only new games change; an old `freecol`
save plays on as it was (R1b re-applies the rules on load, later).

- `mod.xml` names the parent (`parent="classic"`), the specification
  `extends="classic"`; `ant validate` checks it against the schema.
- **Rows that play as `freecol`** (each is a copy of the freecol rules'
  data): 1 no stockade lock (the three `minimumColonySize` deletes, stockade,
  fort and fortress: the fort and the fortress copy the stockade's modifier),
  5 `enhancedMissionaries`, 8 `captureUnitsUnderRepair`, 11
  `foundColonyDuringRebellion`, 12 `saveProductionOverflow` off, 13
  `allowStudentSelection`, 17 Pocahontas also lifts the mission bans
  (`model.event.resetBannedMissions`). Every other row, and every other
  option and difficulty value, is classic's (`LeviRulesTest`), except:
- **The original's sailing time** (W13): `model.option.turnsToSail` 2, not
  classic's and freecol's 3. Measured in the clips: landfall off the map in
  1503, in Amsterdam at the start of 1505; clip008 off the map in 1513, in
  Amsterdam in 1515, and from Amsterdam in 1508, back on the map in 1510 (both
  ways 2 turns; Roger's levi game took 3, 1503 -> 1506). Magellan's -1 still
  applies (1 turn). New games only: a save keeps its option
  (`LeviRulesTest.testSailingTime`). Every clip's ship left from the east
  edge; the original's manual gives 1-4 turns, longer from the west (pp. 14,
  60; H REVIEW2 L3). Roger (2026-10-08): 2 turns both ways as the standard;
  the west edge's longer voyage and Magellan's west-only bonus (PEDIA
  @FATHER5, L4) are not built.
- **The original's fortification** (J2): `model.option.fortifyKeepsMoves` on:
  a unit whose fortification completes at the turn start keeps that turn's
  moves (opening_014 #2177: "Züge: 1" in its block), so a click frees it and
  it moves at once; classic and freecol take them (`ServerUnit.csNewTurn`).
  Rules and saves without the option take the moves
  (`Specification.fixGameOptions`); a running game keeps what its save has.
  (`ServerUnitTest.testFortifyKeepsMoves`, `LeviRulesTest.testFortifyKeepsMoves`.)
- **The original's treaty breach** (B3, part M2; clip opening_018,
  `war-french-analysis/10-spec.md`): `model.option.fortifyDeclaresWar` on.
  F with an offensive land unit of a human player (soldier, dragoon,
  artillery, and the scout, FreeCol's set, until Roger answers) on a land
  tile next to a colony of a European he is at peace or cease fire with, or
  on land such a colony owns (`Unit.getFortifyWarColony`), asks first
  (`GUI.confirmFortifyWar`; the Classic UI's @HAVETREATY, below "Advisor
  boxes"); "Friedensvertrag brechen." fortifies, and the server
  (`InGameController.changeState`) declares war both ways before the
  fortification's occupation: the colony's worker on that tile is evicted
  and the tile becomes the occupier's land (V: «Neuholland» next to
  Montreal afterwards; FreeCol clears its owner; either way the colony may
  claim it again when he has gone, `Player.getLandPrice`). The declarer gets
  no notice that the other "hat uns den Krieg erklärt"
  (`ServerPlayer.csChangeStance(..., tellThis false, ...)`; the original has
  none). The AI never declares war this way; its occupation at war takes the
  tile too (the reverse case, a French soldier fortified next to our colony).
  At war F asks nothing (V: the scout #53979); S never asks; native land and
  alliances keep FreeCol's rule. Classic and freecol: off (FreeCol: no war
  at peace). A levi save from before the rule gets it on
  (`Specification.fortifyDeclaresWarDefault`: the game Roger is playing).
  (`UnitTest.testFortifyWarColony`, `InGameControllerTest.testFortifyBreaksTheTreaty`,
  `MoveTest.testFortifyNextToAForeignColony`, `LeviRulesTest.testFortifyDeclaresWar`.)
- **House rules** (game options; rules and saves without them get FreeCol's
  behaviour from `Specification.fixGameOptions`):
  - `model.option.cancelKeepsMove` on (D1, moved here from the classic
    rules; elsewhere off now).
  - `model.option.revengeMode` off: a defeat ends the game. The client's
    `setDead` asks no revenge question and shows `defeatedGameOver.text`
    (`GUI.showGameOverPanel`, in the Classic UI a notice box), then logs out
    to the title. It comes only on the server's verdict (`setDeadHandler`):
    disbanding the last unit no longer calls it, as before 1600 the server
    may keep a player without units alive. The server refuses
    `enterRevengeMode` too.
  - `model.option.lastColonyDefeat` on: a European is defeated when his last
    colony is lost, not when a rebel loses his last coastal colony
    (`ServerPlayer.checkForDeath`): rebels and independents need any
    colony; a colonial player needs one from `mandatoryColonyYear` (1600) on,
    whatever units he still has, and before it may be without one as in the
    original. The debug-run observer is spared as before.
  - Open end after independence: `victoryDefeatREF` and
    `victoryDefeatEuropeans` off, so no victory and no per-turn high-score
    loop. No founding fathers after the declaration
    (`continueFoundingFatherRecruitment` stays off, FreeCol's default).
- **Names** (`NameCache`): colony names fall back to the parent rules'
  (`getParentRules`: the mod descriptor's `parent`, found by the rules id,
  so it works for a save too), so the levi Dutch found "Neu-Amsterdam", the
  original's names, not freecol's. A name a ruleset may change has a key
  `<key>.<rules>` (`NameCache.getRulesKey`, nearest rules first): the AI
  Europeans are the original's leaders (`model.nation.*.leader.levi`, NAMES.TXT
  `@LEADERNAME`: Walter Raleigh, Jacques Cartier, Christoph Columbus,
  Michiel De Ruyter; `NameCache.getLeaderName`, `ServerPlayer.initialize`)
  while their kings keep the ruler names, and Spain's Europe is Sevilla
  (`model.nation.spanish.europe.levi`, `Player.getEuropeNameKey`).
  `ClassicGUI.isFreePlayerName` refuses the name another nation's player
  gets; the player's own leader, the name screen's default, is free.

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
  (e.g. which unit(s) to disembark from a laden ship; now the original's
  landing box, see "The landing") and `getNewColonyName`
  (now the original's @COLONY box and @NOPORT, see "Founding a colony"; the
  base `modalInputDialog` still no-ops). Without these, founding a colony —
  and hence the colony screen — would be unreachable. All run their dialog on the
  event thread via `onEventThread` (controllers call from arbitrary threads).
  Phase 3 reskins them.

## `ClassicMapViewer` — the map

A `JPanel` that renders the map on a **plain rectangular grid** (where
`SwingGUI`'s `MapViewer` paints isometric diamonds) from a stored view origin,
mirroring the original game. It owns the view state
(`viewMode`/view origin/`selectedTile`/`activeUnit`) and holds a back-reference to
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
cleanly (no diamond gaps). On the HUD grid the terrain is composed from the
original's palette indices ("The dark map, the fog fringe and the blends"
below). The RGBA fallback (a pack without index sheets, the adaptive layout)
fetches the tiles at native 16×16 via
`ImageLibrary.getTerrainImage(type, x, y, SRC_SIZE)` and up-scales them
nearest-neighbour (`VALUE_INTERPOLATION_NEAREST_NEIGHBOR`) so the chunky classic
pixels stay crisp, unexplored tiles flat dark blue. The key→frame mapping lives
in `tools/classic_assets/aliases.properties`. When the asset pack is absent the
same keys fall back to FreeCol's own (isometric) art.

**Palette indices (M1c design 10 §4, W6e).** The pack also keeps every SS file
as the original's palette indices, `ssidx/<NAME>.idx` (`ClassicIndexSheet`,
0xFD = transparent), with the game palette `palette/VICEROY.rgb` and the
original's colour cycle `data/CYCLE.DAT` (8 entries from 120, a step every 35
ticks = 575.05 ms). `ClassicGamePalette` holds VICEROY.PAL and, per cycling
phase p, `P_p[120+i] = B[120 + ((i-p) mod 8)]` and an `IndexColorModel` to draw
indices through; the clips' fog-start #19 is phase 0, its #0 phase 7, landfall
#0 phase 1. Why: the PNGs are coloured with each SS file's own palette, and
`TERRAIN.SS`'s differs from the game's in 121-126 (the sea lane's PNG shows 4
colours the game never shows). The map is composed from these indices (W6a);
the water cycling (W6c, below) swaps the colour model.
`ClassicPackFiles` reads them (`indexSheet`, `gamePalette`, `cycleSpec`, and
`terrainSpriteFor` from the alias lines). A pack converted before W6e has none:
`indexStatus` says `fallback no ssidx/TERRAIN.SS.idx`, logs one warning, and the
recorder notes it (`terrain` event and `summary.txt` line). Re-run
`ant classic-assets` to convert such a pack again.

**True terrain of the fog ring (M1c design 10 §5, W6d).** The original draws
the edge of the dark area from the real map: a dark tile next to explored water
shows its own land in its 3-px fringe, explored water carries coast quarters
toward land that is still dark, and explored tiles blend with the true terrain
of a dark neighbour (landfall #1407, fog-start #876/#1042). FreeCol's client has
no type for an unexplored tile, so `ClassicTerrainOracle.trueType(x, y)` gives:
the client's type for an explored tile; for an unexplored tile **of the ring**
(Chebyshev distance 1 from an explored one) in **single player**, the type of
the in-process server's map (read only, as the client specification's object of
the same id); else null, "unknown". Unknown is every tile beyond the ring (least
knowledge: refused, counted, logged once), every tile in multiplayer (also for
the host, whose client has a server too) and every tile while the server's game
is not the client's (another UUID or map size, e.g. during a load); the
composer's fallback is F-W6d-MP. `ClassicGUI` creates it with the map viewer
and disposes of it in `teardownInGame`; only the terrain composer (W6a/W6b) may
use it, the minimap and everything else keep `isExplored`. The recorder notes
the start ring (`oracle: server ring=11 known=11 refused=0 highSeas=.. ocean=..`).
Tests: `ClassicTerrainOracleTest` (client views made the way the login makes
them) and `ClassicTerrainGoldenTest`, which reads the fixtures in
`test/expected-data/classic-terrain/` (§12.1) and, with
`-Dclassic.clips=<video/recordings>` and a converted pack, compares the
fringe, blend and quarter sprites that follow from the true terrain with the
clip frames' indices (all 0 px off; the multiplayer fallback fails each).

**The dark map, the fog fringe and the blends (M1c design 10 §6, W6a).** On the
HUD grid the terrain is the original's palette indices: `ClassicTerrainLayer`
holds the 15x12 view as one 240x192 index buffer, wrapped in a raster that 8
images share, one per cycling phase (`ClassicGamePalette.colorModel`); a paint
composes only the cells its clip touches, rounded out to whole cells, and
blits them scaled by the HUD scale (dst = src x S, nearest neighbour). The
rules are `ClassicTerrainComposer`, pure code over the index sheets:

- **Unexplored:** `PHYS0.SS.148` (TERRAIN.SS.010 + 2, indices 60/61/62), nothing
  else on it (no overlay, coast, unit). For each side whose neighbour is
  **explored** (N, E, S, W; never a diagonal) its **fringe**: the 15 px of that
  side's mask `PHYS0.SS.104-107` (3 px deep, 7 + 4 + 4, inside the dark tile
  only), the sides a union (15 / 29 adjacent / 30 opposite / 43 / 56 px). The
  fringe shows the neighbour's sprite where it bleeds in; water's own sprite
  never bleeds into land, so dark land next to explored water shows the
  water's **land face** (W6b, below), which is **its own true terrain** (the
  oracle above) when it is the water's last land side: the first island shows
  a move before it is explored. An unknown own type (multiplayer) takes the
  neighbour's.
- **Explored:** its TERRAIN.SS sprite, then for each side whose neighbour's
  type is known (explored, or the true type of the dark ring) and bleeds in,
  that sprite through the side's mask; an explored neighbour with the own
  sprite is skipped, a dark one is copied all the same (`DARK_SIDES_ALWAYS`:
  it shows only at the 4 px where two masks overlap, pinned by fog-start #1042
  (42,46)). Then the overlays as `PHYS0.SS` indices (0xFD transparent, 0
  black), the area features connected through the true terrain of the ring.
  An explored tile is never darkened.
- **T008 (sand with cacti) draws T001 into its neighbours** (`BLEND_BASE`,
  fog-start §3.3); no FreeCol type uses T008 yet. Side order N, E, S, W
  (`SIDE_ORDER`; the clips need W after N and S, E is free).
- **Least knowledge:** the composer asks the oracle only within Chebyshev 1 of
  an explored tile; the minimap keeps `isExplored`.
- **The explored state as shown** (Critic 5): the viewer takes it from the
  model on every full paint outside a slide and keeps it through a slide, so a
  reveal that a server-pushed move already put in the model shows with the
  slide's final draw, not in its 3-px margins; units are drawn only on tiles
  shown explored. Every path that ends a slide paints the final draw with the
  panel's minimap in the same pass (`finalDraw`; a key move through
  `panelAfterFinalDraw`). The recorder logs `explored n= tiles=` for a new map
  (the start's 3x3) and `reveal n= tiles=` at each final draw (the staircase).
- **The start** needs no server change: on the square grid FreeCol explores
  exactly the 3x3 around each European start ship (line of sight 1,
  Chebyshev distance), passengers add nothing, and every move adds the 3x3 of
  its target (`ClassicViewRuleTest.testStartExploresTheThreeByThree`, classic
  and freecol rules). At the edge start 6 cells are explored, 7 carry a fringe
  and 167 are plain dark (landfall #341).
- **Fallback:** a pack without `ssidx/` (and the adaptive, non-HUD layout, which
  only shows before the first layout and in tests) draws the RGBA tiles and
  their overlays without blends, unexplored tiles flat in `DARK_SEA` #181C7D
  (VICEROY's 61).

Tests: `ClassicTerrainComposerTest` and `ClassicTerrainLayerTest` (synthetic
sheets, `ClassicTerrainSheets`), `ClassicMapViewerTest` (the reveal with the
final draw, the fallback), and `ClassicTerrainGoldenTest`, whose `compare` lines
compose whole clip frames from the pack's index sheets and compare them index
for index: fog-start #19 (46,080/46,080 px), #718, #876, #1042 and landfall
#341, #345, #1407 all 0 px off (the mouse arrow and the unit cells excused; the
coast cells included since W6b).

**The coast (M1c design 10 §8, W6b).** Explored water with land among its 8
neighbours (by `type()`, so land still dark counts and the coastline shows a
move before the land is explored; off the map and unknown count as water) is
composed twice: the **water layer** (its base and blends, as above) and the
**land layer**, the cell drawn as land of the `blendSpr` of its **last land side
in N, E, S, W order** (W before S before E before N) with the side-mask blends
of its land neighbours (no water bleeds into it). Then:

- **Beach corner** when exactly two adjacent sides are land, the other two
  water and the diagonal opposite them water too (`BEACH_RULE`; the corner's
  own diagonal does not matter): the land layer with `PHYS0.SS.150-153` (land
  N+W, N+E, S+W, S+E) over it, 0xFD keeping the land, and no quarters. Its
  base is therefore the W side for 150 and 152, the E side for 151 and the S
  side for 153 (`BEACH_BASE`).
- Else the **coast quarters** `PHYS0.SS.108 + 4c + q` (`ClassicTileArt.
  coastQuarter`; q = 0 NW, 1 NE, 2 SE, 3 SW at (0,0), (8,0), (8,8), (0,8); c =
  b0 + 2 b1 + 4 b2 from the land bits of the corner's three neighbours
  clockwise: NW (W, NW, N), NE (N, NE, E), SE (E, SE, S), SW (S, SW, W)) over
  the water layer: index 0 keeps the water layer, 0xFD shows the **land layer**
  (`LAND_FILL`), any other index is drawn; c = 0 draws nothing. So the quarters
  sit on top of the land blends (fog-start #876), and every quarter's
  transparent pixels show the same land, also where its own orthogonal land
  side is another (fog-start #3162 (41,45): land N T003, W T007, S T005; its NE
  quarter shows T007).
- The overlays (a fish, a rumour) come last.
- **Land face** (`LAND_FACE`): explored coast water bleeds the sprite of its
  land layer into its land neighbours, explored or dark, where that neighbour
  is not itself the source: "water never bleeds into land" holds for the
  water's own sprite only. Fog-start #3162: the prairie (41,44) shows T007 in
  its S mask; landfall #14247: the dark (49,41) and (50,42) show T004 and T007
  in their fringes from the beach corners N and W of them, not their own T007
  and T001. Dark water stays out (least knowledge, unverified).

How the rules were found (freecol-spike-results/d/w6b, scans of the clips'
indexed frames): over nine clips, two adjacent land sides with the other two
and the opposite diagonal water carry a beach corner in 2,787 cells (1 against,
a blank screen); with the opposite diagonal land they carry quarters (9 cells
at 4 places, e.g. clip005 #1046); a third land side gives quarters (landfall:
234 cells with land N, E, W, no beach). Of the coast cells whose land sides
show different sprites, all 2,207 of four clips fill their quarters' and beach
corners' transparent pixels with the last land side's land layer, none with
the first side's or each corner's own side (the design's O1 candidates); 345
land cells next to coast water show its land face where it differs from their
own, none their own; and 700 coast cells show no water neighbour's land face
in their land layer. Tests: `ClassicTerrainComposerTest` (the frames, the
layers, the beach rule and base, the land face, least knowledge), and
`ClassicTerrainGoldenTest`: the whole frames above with their 10 coast cells,
the `cells` lines (fog-start #3162, #4027 and #4876 and clip005 #1046 whole
coast cells; landfall #14247 16 coast cells and 3 dark fringe cells), the
`quarter`/`beach` lines (59 checks in all) -- all 0 px off -- and
`testCoastRuleCandidates`, which composes every combination of the candidates
(`LandFill`, `BeachRule`, `BeachBase`, `LAND_FACE`: 128) on all 328,750
compared pixels: the committed rules 0 px off, every single alternative worse
(33 to 1,777 px). Open: river mouths `PHYS0.SS.140-147` (O9) and the land
face of dark water (not measured; left out).

**The water cycling (M1c design 10 §7, W6c).** Palette entries 120-127 rotate
one step every 35 game ticks, 575.05 ms (`ClassicGamePalette.PERIOD_MS`; the
clips measure 575.0-575.1 ms): each colour moves one entry up, phase p shows
`B[120 + ((i - p) mod 8)]` at entry 120 + i. Every pixel that holds such an
index cycles: sea lanes (62 px), **rivers** (5-21 px each), swamps, beach
corners, and the fringes and blends next to them. `ClassicWaterCycle` (created
with the map viewer in `ClassicGUI.reconnectGUI`, started there at phase 0 =
VICEROY's order, closed in `teardownInGame`) keeps the absolute schedule
`t0 + k * P` on a daemon thread (`Thread.sleep` plus spin) and posts each step to
the EDT; `ClassicMapViewer.paletteStep` sets the layer's phase and repaints the
box of the cells whose last composition holds a cycling index, with the units
over them, only while the map is on screen. It keeps running under boxes, menus
and the Europe screen. Details:

- **On time inside blocking waits.** The slide, its hold, the gap before a
  chained slide, the native cue and the panel tick after a key move block the
  EDT; they wait on `ClassicWaterCycle.servicing` (the viewer's `slideClock`,
  `ClassicGUI.waitClock`), which fires every step due before the wait's own
  deadline at that step's deadline, between two slide steps; the slide's own
  schedule is unchanged. A post that comes after is dropped as stale.
- **Silent paints** (Critic 3): a palette step's paint takes no explored state,
  uses up no final draw and tells the turn flow nothing (`palettePaint`); while
  a slide's final draw is due the step paints that final draw instead. With no
  cycling cell in the view nothing is painted (`ClassicTerrainLayer.markShown`).
- **Frozen:** `hold(reason)` / `release(reason)` for the woodcuts (W9, wired:
  them: no step while one is up, then one step at the next frame, 14.27 ms, and
  the schedule runs on from it, landfall #2638 -> #2639). The pref
  `waterCycling` OFF freezes the phase, read at every due step; ON resumes one
  period later (the original's OFF is not observed, O12). `setEnabled` is the
  same switch for the options box (W14).
- **Not emulated:** the original's step delayed by 2-3 frames when a portrait
  box opens (O10). A step posted while the EDT is busy otherwise (the turn
  start, a long paint) comes that much late.

Tests: `ClassicWaterCycleTest` (schedule, no drift over 1,000 steps, stale posts,
hold/release, the pref, the servicing clock, the thread), `ClassicSlideTest`
(the slide's deadlines with a step inside), `ClassicMapViewerTest` (a step in a
slide at its deadline, the final-draw case, the silent paint),
`ClassicTerrainLayerTest`, `ClassicFrameRecorderTest` (below) and the golden
lines of `ClassicTerrainGoldenTest`: the clips' palettes are phases of VICEROY
(fog-start #0 = 7, #19 = 0, landfall #0 = 1); landfall #5420-#5702, 8 phases
in a row, equal our composed sea lane (56,42) and ocean (55,42) index for index
and colour for colour; the river `PHYS0.SS.024` of fog-start #6543 cell (6,1)
holds 86/86 px with its 5 cycling ones at three phases in a row.

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

**`drawCentered` — cell-fit for both art styles (adaptive layout only; the
HUD grid draws icons 1:1, see "Unit icons and the slide").** The map draws the
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

**No edge scrolling (removed in part K1).** The pointer's motion never moves
the view; only presses are listened to. Phase 1b had FreeCol's edge scrolling:
a cell-wide hot zone along the map's edge and a 110-ms `Timer` that panned the
view one cell per tick while the pointer rested there. It undid every jump to
the unit up within 110 ms (the turn start, the hand-over, a minimap click), and
Europe's exit button and the word "Spielzugende" both sit next to that zone, so
in Roger's test of `abc27b588` the view ended on open sea and his units were out
of reach (`freecol-spike-results\k\REPRO.md` section 1). The original's view
moves only by its jumps (landfall 02 sections 1 and 5; that the pointer never
moves it is I: in the landfall clip the pointer never moved, so edge
scrolling was not observable, landfall 02 l.228). Ways to move the view by
hand: a minimap click (navigation, to the place clicked), the centre command
(C, to the unit up), a click on an unexplored or a foreign tile. The unit up
comes back into the view by the view rule (its next move, the next unit, the
next turn) and when Europe closes (`ClassicGUI.europeGone`,
`ClassicMapViewer.showActiveUnit`).

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
  → nothing (the original's arrows never moved the landscape with no unit up,
  W5d; while the player waits every map key is blocked, see "End of turn and
  hand-over"). The view follows the
  original's jump rule (see "The view rule"), never the arrival of a move.
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
    begins) and the map/minimap refresh. Through `ClassicGUI.requestEndTurn` ->
    `ClassicTurnFlow.endTurnNow`, which lights the turn indicator first (W5c).
    Without a key the turn ends by itself 485 ms after the last change once
    nothing can move, if a unit came up in the turn (W5a; else the
    Spielzugende mode waits, J2). Since W17 only in the Spielzugende mode (or with no
    unit up and none left that can move, `ClassicGUI.mayEndTurnByKey`; a unit
    off the map, a ship that has sailed for Europe, counts as none): the
    original has no other end of turn, with units left it ends by itself once
    none can move (spec delta W17 item 7); otherwise the key is logged
    `key-ignored end-turn`.
  - **Space** → "no orders": skip the active unit for this turn, mirroring
    `SkipUnitAction` (`changeState(unit, SKIPPED)` then `nextActiveUnit()`). With no
    active unit (or one off the map), Space ends the turn instead, under Enter's
    condition (as in the original, where Space advances the turn once every unit
    is done). A unit up with orders FreeCol cannot skip (only an ACTIVE unit can
    be SKIPPED: a click brought up a pioneer at work, or a fortified or sentried
    unit with no moves left) keeps its orders and the next unit comes, or the
    turn ends by itself 485 ms later (`ClassicMapViewer.keepsOrdersOnSkip`,
    `ClassicGUI.nextUnitAfterSkip`; the fixer of part J: before, Space did
    nothing there, and with nothing else to move Enter was refused too).
  - **W** → wait: the next unit of the original's unit cycle comes at once
    (`ClassicGUI.waitUnit`, `ClassicTurnFlow.waited`, W5f), and this one again
    after the wrap (I: never recorded); without the turn flow
    `InGameController.waitUnit()`.
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
  delegates to `changeToMoveUnits` (so the view jumps to the next active unit
  when it is in the margin or off the view, `player.hasNextActiveUnit()`).
  Movement was verified live post-end-turn; note that moving the view away
  (a minimap click is navigation, Roger 2026-10-08) leaves the active unit
  off-screen until its next move starts, whose source-tile test brings the
  view back, or until Europe closes (the same test for the unit up, K1).

**Minimap raster.** A whole-map overview: a plain rectangular
`map.getWidth() × map.getHeight()` raster — **no** isometric projection (unlike
`client/gui/panel/MiniMap`, which is the colour-source reference only). The map
viewer *builds and caches* it (it is map data); it is **drawn by
`ClassicInfoPanel`**, which hosts the minimap at the top of the right column as
the original does. (It began as a bottom-left overlay on the map itself; the
"HUD minimap" slice moved it into the info panel — see "In-game HUD" below; it is now the original's 56x39 window at 1 px per tile, built by `ClassicHud.minimapOf`, and this raster is unused by the panel.)

- Colours: unexplored → `ImageLibrary.getMinimapBackgroundColor()`; explored →
  `getMinimapPoliticsColor(tile.getType())`; a tile with a settlement/unit →
  the owner's `ClassicHud.indicatorRgb` (H4: the same colours as the panel's
  minimap, the tribes' NAMES.TXT ones). Terrain guarded with fallbacks (`orElse`).
- Sizing: integer pixels-per-tile `max(1, MINIMAP_MAX/max(w,h))` fits the raster
  into a ~200px box, so a tall/narrow map renders as a vertical strip.
- **Caching / performance:** the raster is cached in a `BufferedImage` and
  rebuilt only when `invalidateMinimap()` marks it dirty — wired to
  `ClassicGUI.refresh`/`refreshTile`, the model-change hooks (exploration, new
  settlements, unit moves). `getMinimapImage()` returns the cache, rebuilding if
  stale; the panel blits it every repaint, so no per-repaint tile iteration.
- **Accessors for the panel:** `getMinimapImage()`, `getMinimapPixelsPerTile()`,
  `viewOrigin()` (the panel's viewport ring) and `recenterOnTile(x,y)` (a
  minimap click → `setFocus`). The old
  in-viewer `paintMinimap`/`minimapClick`/`minimapBounds` and the edge-scroll
  suppression over the overlay box were removed with the move.

**Feature overlays (item (e)).** On top of the base terrain the map draws the
per-tile *physical features* — forest trees, hills, mountains, rivers, roads,
plowed fields, resource markers and the lost-city rumour — before the
settlement/unit sprite: `ClassicTileArt.overlayFrames` picks the `PHYS0.SS`
frames, the composer copies them as indices (W6a; the area features connect
through the true terrain of the fog ring), the RGBA fallback draws the PNGs
(`paintOverlays`). This is the last
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
  plowed field, `89..102` the resource markers, `89 + NAMES.TXT @RESOURCE row`
  (L3/Q4: V for 7 of them in playthrough-1, oasis 90 and sugar 94 among them;
  grain 91, cotton 92, tobacco 93, game 98 and ore 102 by the rule, I).
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

**Coastline and tile borders: the original's own sprites (M1c W6a, W6b).**
The map used to draw its borders procedurally (2026-08-05/06): a dithered
land-land band (`blendLandBorders`/`ditherEdge`, Q7), a contiguous land-water
incursion (`blendCoastEdge`) and a grey foam line on the water side
(`blendWaterBorders`/`foamEdge`), after the coast quarter-tiles had been removed
from `ClassicTileArt` for a "green fleck" along the wave crest. All of that is
gone with W6a: the original has no procedural border, and the fleck is its own
rim (indices 67-71, landfall 04 §5.2). The original's borders are sprites:

- **Side-mask blends** (W6a): a neighbour's terrain bleeds 3 px into the tile
  through the masks `PHYS0.SS.104-107` (`ClassicTerrainComposer`, above). That
  is the land-land border and the land side of the coast.
- **Coast quarters** `PHYS0.SS.108-139` (8x8, quarter `q` = `108 + 4c + q`, `c`
  from the three land bits around the corner; index 0 = keep the water, 0xFD =
  the cell's land layer) and the **beach corners** `150-153`, on explored
  water, drawn after the blends (W6b, "The coast" above; fog-start #876,
  #3162, #4027, #4876 and landfall #1407/#14247 pin them).

The history of the procedural attempts is in git and
[land-tile-borders.md](../../../../../../../classic_ui_plan/land-tile-borders.md)
([Q7](../../../../../../../classic_ui_plan/ui-phases.md#open-questions-for-the-expert)).

### Unit icons and the slide (`ClassicSlide`; build spec W2, spec delta W2)

On the HUD grid (`setFixedScale` > 0) the map draws what the original draws,
in native pixels times the HUD scale s; the adaptive layout keeps the old
cell-fitted sprites (`drawCentered`, `UNIT_CELL_FRACTION`).

- **Icons 1:1, the panel's own** (build spec W21a). A unit is
  `ClassicHud.paintIcon` -- the panel's proved painter, so a map unit looks
  exactly as its block -- at the cell origin: the sprite's black silhouette
  2 px left, the 7x9 flag (black ring, nation fill), the sprite, then the
  `@ORDERS` letter in FONTTINY at ring + (2,2), over the sprite where they
  meet (clip006 #4471: the galleon's pixel (9,4) under the '-' is black).
  - The flag's side is per sprite (`flagRing(x, y, w, unitRow)`): the galleon
    (ICONS.SS.007) and the frigate (015) at cell + (9,0), the merchantman
    (006), the other ships and mounted units at the top-left, a land unit at
    the sprite's lower right. The frigate's sprite sits at +2
    (`spriteOffset(w, unitRow)`), the rest at their measured offsets.
  - The letter's ink (`letterInk`): the darker nation shade only for
    Befestigt (F, fortified) and Wache (S); black for '-', G, R, P and for F
    while still fortifying (clip006 #4555 -> #4556).
  - Checked pixel class by pixel class (`ClassicUnitIconTest`: landfall #343,
    clip007 #2374/#3697; the flag side, the frigate's offset, the letter over
    the sprite and its ink), and at 0 px against the clips by the C3 harness
    (`freecol-spike-results\c\tools\C3Icons.java`: the map galleon c5 #20440
    and c6 #4471/#4516 on their blink-OFF frames, the panel's galleon, the
    pioneer's R, the dragoon's F black and dark, Europe #8120's frigate,
    merchantman and galleon, the laden and the empty ship of clip007).
- **Second flag.** A ship with a passenger draws the cargo marker, a second
  flag 2 px down-right behind the flag (spec delta W2.3, V: the 7-px edge at
  x 2-6, y 8-10). A land unit standing over others, or a passenger drawn over
  its ship, draws the stack marker 2 px up-left (base spec W2.4, **I**).
- **Settlements 1:1**, centred, the 21-px frames 2 px over the left edge
  (landfall #13302). The map paints in two passes, terrain then
  settlements/units, so a shadow or an overhang lies on the neighbour's
  terrain.
- **Which unit.** The active unit when it is on the tile (a woken passenger
  offered aboard is drawn instead of its ship, W18, clip007 #3107; one that has
  just boarded and is asleep is not, the ship is, #4272), else the first unit,
  never the one mid-slide.  An activation that puts a passenger in its ship's
  place, or the ship back, paints the cell at once (`paintCellNow`), in step
  with the block.
- **No cursor box** in MOVE_UNITS (the original has none); TERRAIN keeps the
  selected-tile box, but only while the player can use it
  (`ClassicMapViewer.isCursorShown`): not while the player waits
  (`turnInputBlocked`: the automatic end, a hand-over, the AI phase) and not
  in the Spielzugende mode, which has its own square. In a turn without a unit
  the controller selects its fallback tile (the first colony, else the entry
  tile; since the fixer of part K without moving the view, see "The view" in
  the Spielzugende section); the box flashed white there for about 0.5 s
  every turn (C acceptance A3), which the original does not show
  (`ClassicMapViewerTest.testCursorOnlyWhileThePlayerCanUseIt`).
- **Blink.** `setBlinkOff` draws the active unit's tile bare (no unit,
  carrier or stack); the blink clock drives it (next section), a slide clears it.
- **A unit up in a settlement** (part K2, Roger's soldier that "vanished" in
  Base Silver; `unitOverSettlement`; corrected by the fixer of part K). A
  settlement's tile always shows the settlement; while a unit is *up* there
  -- the active unit that takes orders (ACTIVE, moves left, in no building),
  is not sliding and has no hand-over to another unit pending -- it is drawn
  **over the settlement** while its blink is ON, the settlement alone while
  OFF. The original's ON frame changes only the unit's sprite and flag; the
  colony's buildings and pennant stay around it (clip006 #4625 the trapper up
  in Fur City, #5169 the soldier U25 up in Base after his goto, #5299 U26;
  clip008 #35590 the merchantman up in Base, #35613 Base, 23 frames each;
  K2 had drawn the unit in the colony's place, the review of part K measured
  152-180 of the colony area's 336 px unchanged between ON and OFF). Over the
  settlement the unit keeps its marker (`markerOf`): the stack marker when
  other units stand on the tile, as on open land (#4625 with a farmer and an
  artillery on Fur City's tile; #5127, #5169, #5299 in Base, whose tile list
  goes on with "+ Weiter +", #5135: the second flag's top edge and fill in
  rows 29-30, 2 px above the flag's, measured by the fixer of part K), a
  laden ship its cargo marker. Our map
  draws no colony name and no size digit at all (the original's digit is
  hidden under the unit up; the name is an open item). As soon as the unit
  is done the settlement alone comes back: its last move (the final draw,
  #50603 -> #50610), Space, or the cycle moving past it (W, F, S). A visit's
  unit (W5f) is not drawn there: it is done with the completion, and drawn it
  flashed for one frame before the end (live K2 L2); the original's visit in
  a colony is not recorded (I). Those changes have no blink change, so
  `refreshCover` repaints the cell (from `rearmBlink`, `holdBlink`,
  `enterPrompt` and every hand-over's start,
  `ClassicTurnFlow.Host.handOverStarted`), not after a view move (the whole
  map is painted then, one cut). A colony's workers and every unit there that
  is not up stay hidden under it (#32928). A slide out of a settlement draws
  the sprite over it from offset 0 on: offset 0 is the ON frame again
  (#35751 OFF -> #35756 = #35590 -> #35757), so there is no rule of its own
  (K2's offset-0 rule, which hid the settlement for that step, also hid
  foreign villages for one frame; it is gone). No colony screen opens when a
  unit comes up in a colony at a turn start (#35590); in the original a ship
  that docks in our colony gets it (clip008 #30021). Recorder events:
  `cover on|off unit=.. at=x,y`. Tests: `ClassicMapViewerTest.testAUnitUpOnItsColonyTile`
  (the rule, the markers), `testTheColonyCellShowsTheUnitUp` (the pixels, with
  `setTestArt`), `ClassicTurnFlowTest.testHandOverStartTellsTheHost`.

**The slide** (`animateMove` on `ClassicSlide`): offsets 1..15 one native
pixel per step on an absolute schedule `t_k = t1 + (k-1)S` with S = 16.43 ms,
13.25 ms while the classic pref `moveAccelerator` is on (read at each slide
start); a 72-ms hold at 15; offset 16 is the final draw, the first ordinary
paint after the slide, so it comes together with the tiles the move revealed
(`handleMoveKey` paints it as soon as `moveUnit` returns). The panel's
refresh follows one tick later (`ClassicGUI.panelAfterFinalDraw`, M1
acceptance F2): its minimap with the final draw (the reveal, landfall #1167),
the block 16.43 ms after it (#1168: +1 frame in 36 of 47 slides, +2 in 9);
it used to land in the final draw's own frame in 21 of 53 slides. Steps repaint only
the cells the sprite crosses (`slideBounds`); every step restores the source
tile; diagonals step (+-1,+-1); nothing is mirrored. Offset 0 is painted first,
one step before offset 1, when the view jumps for the move or the player's own
unit is not on screen at its source (blink OFF, a passenger leaving its ship:
its offset 0 shows it instead of the ship, landfall #11577, clip007 #3048);
offset 1 is due one step after offset 0's map paint has *returned* (a jump's
full repaint took 8-27 ms out of offset 0's tick before, M1 acceptance F3;
the minimap and the recorder's event after it do not count, `ClassicSlide.run`'s
`zeroShown`).
The jump frame shows a foreign unit at its source too, a native on an
unexplored tile included (landing-slow #6482); a foreign unit not on screen
without a jump appears at offset 1. A slide without a key (goto
steps, AI moves) first paints the previous slide's final draw if the queue has
not, then starts 100 ms (own goto) or 60 ms (foreign) after it. Waits are
`Thread.sleep` plus a spin, never `parkNanos` (W1). `ClassicSlideTest` checks
the schedule on a fake clock, including what a 70.0863 Hz capture shows: OFF
16 (sometimes 17) frames from offset 1 to 15 with repeats, ON 13 frames with
exactly one 2-px advance.

**Session option.** While the in-game map is up, `ClassicGUI` sets FreeCol's
`unitLastMoveDelay` to false and restores it in `teardownInGame`
(`SESSION_OPTIONS`): FreeCol's 300-ms EDT sleep after a unit's last move held
the slide at offset 15 for 300 ms more. The pause after the last move belongs
to the Classic UI's end of turn and hand-over (W5).

**Foreign moves** obey the classic prefs `showNativeMoves` (native units) and
`showEuropeanMoves` (other foreign units), read in `ClassicGUI.animateUnitMove`
(`movesPref`); FreeCol's `enemyMoveAnimationSpeed` is not consulted.

**AI phases: the model runs ahead** (M1 acceptance F5). FreeCol queues AI
animations on the EDT with `invokeLater` while the network thread applies the
moves at once, so a unit could show at its new tile before its slide (one
frame of a brave at its destination, iso #1938). The server sends the
animation before the update; `InGameController.animateMoveHandler` now tells
the GUI first (`GUI.animateUnitMoveQueued`, a no-op in the base GUI), and the
map keeps such a foreign unit at the source of its oldest queued move until
its slide starts (`moveQueued`, `displayUnit`); the slide, or a skip, takes the
move off (`moveDequeued`). Own moves are not queued (they slide inside the
move).

**Native-phase cues** (build spec W19). Before a native's move whose source
tile fails the view rule (the view will jump for it), 2 frames after the
previous native's final draw (`CUE_GAP_MS`; the original 2-4 frames), the
source's minimap pixel turns white, and if the source cell is on the screen
(in the margin) the Spielzugende mode's white 16x16 square is drawn on it with
the sprite hidden (clip004 #9060, landing-slow #6464); the jump follows
`CUE_MS` = 250 ms later (the original 199-330 ms) and paints both away, and
offset 1 one step after the jump frame. No cue for a move inside the view,
none for a European. The whole slide is gated by "Indianer zeigen"
(`showNativeMoves`). Recorder event `native-cue minimap|square unit=.. at=..
cell=..`. Measured from a save with Dutch lookouts by villages
(`freecol-spike-results\c\saves\c3-natives.fsg`, `C3Natives.java`).

### The blink and the minimap dot (`ClassicBlink`; build spec W3)

The active unit blinks as in the original: one clock with a half-period of
**328.5 ms** (20 ticks of 16.43 ms), 50 % duty, **ON first**. `ClassicBlink`
keeps the phase: toggle n is due at `t0 + n * 328.5 ms` on the absolute clock
(odd n OFF, even n ON), waited for by a daemon thread of its own (the
recorder's `Thread.sleep` plus spin, never `parkNanos`) and run on the EDT
(`ClassicMapViewer.blinkToggle`).

- **(Re)arm, reset not pause.** `rearmBlink` sets ON and starts a new phase on
  every activation (`changeToMoveUnits`, which also covers the turn start), at
  the end of every slide (`animateMove`), and when a box closes (every
  advisor box, `ClassicAdvisorLayer`, and the stopgaps `ClassicDialog` and
  the `chooseFromList` list, through `ClassicDialog.Watcher`,
  the first scene, a menu of the strip through `ClassicMenuStrip.Host`). The
  original measures its first OFF from the **panel refresh** that follows
  (57 episodes); so every panel paint that changes a pixel within 100 ms of
  an arm re-bases the phase (`ClassicInfoPanel.paintComponent` ->
  `blinkPanelPainted`), and without one it stays at the arm. An identical
  repaint is no refresh: the one the controller queues 10-15 ms behind a
  move's block put the first OFF a frame late (C3). Each arm is a new generation; a toggle of an older
  one (queued behind a slide or a box) is dropped.
- **Held ON** while a slide runs, a menu, a modal box, the first scene or a
  classic screen is up (in front: see W5a), or it is not our turn
  (`ClassicGUI.blinkHoldReason`).
  A box that opens while OFF redraws the unit ON first (`holdBlink`, landfall
  #11146 -> #11151 -> #11154). Goto steps never blink: each step's slide ends
  with a re-arm and the next one starts 100 ms later. A hold without a close
  hook (a screen closed by its window's close box) ends at the next toggle,
  which re-arms instead of toggling.
- **Stopped** without an active unit in MOVE_UNITS (TERRAIN, END_TURN), and
  once the active unit has no moves left (W5: after its last move it stays on
  screen, unblinking, through the pause).
- **OFF paint** = the bare tile: terrain and overlays, no unit at all, carrier
  and stack included (`paintOccupant`); a settlement stays, and is what OFF
  shows of a unit up in it (K2, clip008 #35613). A toggle paints
  only the unit's cell plus the icon's reach (`paintBlinkCell`).
- **Minimap dot.** The active unit's pixel is its nation colour while ON and
  white (`BLINK_DOT_RGB`, index 15) while OFF (`ClassicInfoPanel.blinkDot`,
  `MinimapModel.with`); the panel's minimap is painted right after the cell
  (`paintMinimapNow`). The viewport ring is still drawn last and hides the dot
  in view column 14, as in the original.
- **A key while OFF** starts the slide at offset 0 (W2's `isShownAt`).
- **The background preload waits** while a timed paint is due
  (`ResourceManager.setPreloadHold`, `ClassicMapViewer.holdsPreload`; M1
  acceptance F6): a slide or a cue runs, or a toggle of the blink or of the
  Spielzugende square is due within 40 ms or has just been posted. The
  preload overlaps the game's first 13 s; its decoding made those paints 2-5
  times as slow and the game's first OFF came 24-26 frames after the panel.
  It is held at most 1 s per resource, so it always finishes.
- Recorder events: `blink` with `arm <reason>`, `rebase panel`, `off n=..`,
  `on n=..` (with `late=`, and `cell=` / `dot=`: what the toggle's two paints
  took), `hold <reason>`, `stop <reason>`; `menu-open`, `menu-close`.

`ClassicBlinkTest` checks the schedule on a fake clock (no drift over 1000
toggles, ON first, reset on re-arm, the panel re-base and its window, stale
toggles dropped), the thread on the real clock, and the minimap dot;
`ClassicMapViewerTest.testBlink` the viewer's states (bare tile, hold, resume,
stop, the dot).

### The view rule (`ClassicMapViewer.jumpIfNeeded`; build spec W4)

The view is the original's fixed 15x12 window. It never scrolls: it moves
only by a hard jump, which redraws the whole map at once (landfall 02).

- **Stored origin.** The viewer keeps the view origin `(vx, vy)` (the tile in
  the top-left cell) itself; `viewOrigin()` returns it, `screenX/screenY`
  and `tileAt` project with it (HUD: `(x - vx) * 16s`), and `getFocus()` is
  just the tile in cell (7,6). The origin is clamped to
  `vx in [1, W-16]`, `vy in [1, H-13]` (`ClassicHud.clampView`; 42 and 59
  on 58x72), so the map's outer ring and anything beyond the edge never show:
  in HUD mode no open sea is painted past the edge any more. One exception,
  for a case the original never has: a unit standing **on** the outer ring
  is off the clamped view, so it always makes the view jump, and `viewFor`
  moves the origin one step past the clamp just to show it (vx 43; the
  minimap ring then lies on the frame's last column). On the square maps the
  Classic UI makes no unit can stand there any more (next section); before,
  FreeCol's start and entry location could be column 57 (live run
  `m1-w4-west`, F1).
- **Recentre** = `ClassicHud.viewFor`: the unit in cell (7,6), clamped. It
  reproduces all 14 measured origins V1-V14, including both east clamps.
- **Jump test** = `ClassicHud.needsRecentre`: the unit's cell is in column 0-1
  or 13-14 or row 0-1 or 10-11, on a side where the origin is not yet at its
  clamp, or the unit is off the view; any violation recentres **both** axes.
- **Where:** (a) on activation (`changeToMoveUnits`: turn start, next unit;
  not when the controller selects the moving unit again after each of its
  moves, `InGameController.moveDirection`'s redisplay -- that is its arrival;
  a jump paints the map and the minimap at once);
  (b) when a move is accepted (`animateMove`), on the **source** tile before
  the first step -- the jump is the slide's offset-0 frame, with the minimap
  ring painted at once, and offset 1 follows one step later; (c) the same for
  every animated foreign move, natives included (spec delta W4; the marker and
  the 250-ms pause before such a jump are W19). Never on arrival, never at the
  end of the turn.
- **Other moves of the view:** `setFocus` (the start and a reconnect, the
  centre command, a minimap click, a click on an unexplored or foreign tile)
  centres clamped: the start ship at (56,42) sits in cell (14,6) with the
  ring at y 22-33 (landfall #340). The TERRAIN cursor follows the same jumps
  as a unit (**I**: the original's view mode was not recorded). The free pan
  (the mouse at the window edge) moves the origin by one cell, clamped; the
  arrow keys with nothing selected no longer pan at all (W5d).
- Recorder event: `view-jump <reason> <old> -> <new> tile=.. cell=.. now=..`.

`ClassicViewRuleTest` replays the landfall clip -- its 86 slide starts and 27
selections, in order, on 58x72 -- and gets exactly the 13 jumps of 02 section
4.1 and V1-V14, once on the rule alone and once through the viewer's stored
origin; plus the named cases (#10269 row 10, #15010 column 1, #19506 at (14,1),
none at #10077 row 9, #14876 column 2 or the five east-clamp starts), the
margins and clamps, the outer ring, the start view with its ring, and that
the controller's re-selection of the moving unit after each move (its
arrival) never tests the view while a newly active unit always does.

### The outer ring and the start (master plan N16, N18)

The view clamp never shows columns 0 and W-1 or rows 0 and H-1 (EUQ section
3). So a square map from the map generator has the **outer ring**
(`Map.hasOuterRing`; saved as the map's `outerRing` attribute, and a square
save without it, made before, has it), and no unit of any player may stand
there. The map editor's new square maps come from the same generator and
have it too. Isometric maps, and maps built otherwise (the tests'
`MapBuilder` maps), have no ring and behave as before.

- **Moves.** A move onto the ring is illegal, as a move off the map is
  (`Unit.getSimpleMoveType`: `MOVE_ILLEGAL`). So paths, the AI's missions and
  the server's move check keep off it. At the east and west edges Roger's
  Europe question (W8a and E1, below) comes first; any other key toward the
  ring does nothing. A unit already on the ring could move off it, but not
  along it.
- **The way to Europe.** The ring leads nowhere: `Map.resetHighSeasCount`
  leaves it out, and FreeCol's rule that water on the map's vertical edges
  leads to Europe (`moveToEurope`) applies to the columns just inside it
  (1 and W-2). So every water tile of the last drawn column leads to Europe,
  as the original's light-water edge does, and a sea that used to reach Europe
  over the ring still does. The high seas goal deciders (the way to Europe,
  the entry tile of a ship coming back, the REF and the intervention force)
  never pick a ring tile, and `Tile.getSafeTile` (a ship arriving at a taken
  entry tile) never returns one.
- **Placement.** No European start, REF entry tile, native settlement or
  rumour is put on the ring. Loading an older square save moves a unit found
  on the ring inside it, moves an entry location on it to the nearest tile
  inside of the same kind (water for a ship), and makes the high seas counts
  again, so the column inside the ring leads to Europe there too
  (`Map.checkIntegrity`; the C1 saves had an entry tile at (57,60)).
  `Unit.setLocation` logs a warning should anything still put a unit there.
- **The start** (part L; Roger 2026-10-08: "Startplatz: Ja, ändert das aufs
  Original (am Anfang zählt jeder Zug)"): on such a map
  `Map.collectStartingTiles` takes, on every row whose tile in the column
  inside the ring (x = W-2 = 56) leads to Europe, the innermost tile of the
  high seas band that reaches that column (`innermostHighSeas`), never the
  ring: FreeCol's own rule inside the ring. Playthrough-1 #208/#412: the
  original's ship at (53,28) "(Seeweg)", (52,28) "(Ozean)"; the Seeweg band
  there starts at x 50-53 (06-map-graphics §8, O1). The start view
  (activation clamped, `viewFor`, W4) puts the ship in cell (7,6), clamped
  at the east edge: (53,28) gives the origin (42,22), cell (11,6), as
  #208; (56,42) still gives (42,36), cell (14,6), as the landfall clip.
  Our high seas may reach 16 columns in from the edge where the land is far
  (`MapGeneratorOptions.applyTopologyDefaults`), so such a row starts its
  ship up to x 42 (cell (7,6)). Levi, 4 nations, square, seeds 1-40 (the L
  review's probe): the Dutch start at x 42-56, at x 42 in 9 of 40 games
  and at x 46 or less in 16; land is at least 3 tiles west of every start,
  but on an AI row there can be 12-21 tiles of open ocean (or no land at
  all) before it. Before part L (C2, N16) the ship always started in the
  column inside the ring.
- **The start order (N6).** With the classic starting positions (the
  default), the nations take the start tiles of such a map from north to
  south in a fixed order: England, France, the Netherlands, Spain
  (`EuropeanStartingPositionsGenerator.START_ORDER`, Roger's "Probelauf" in
  the original). Nations the list does not name (the freecol rules' other
  four) follow in the specification's order; fewer nations keep the order.
  FreeCol's sampling of the column's rows is unchanged: with four nations on
  our maps it gives the rows 9, 26, 43 and 60 (20 of 20 generated games), so
  England starts on row 9, France on 26, the Netherlands on 43 and Spain on
  60 (each on the row's innermost high seas tile since part L; before, at
  x 56). Before N6, the same four tiles were dealt out in FreeCol's
  shuffled order. A map without the ring (isometric) keeps the shuffle.
- **Repeatable starts (N18).** `--seed N` now also seeds a new game's server
  random numbers (`FreeColServer`; before, only a loaded game's), and that one
  `Random` feeds the map generator, the start positions and the AI: the same
  seed gives the same map and the same start tile. The acceptance launcher
  (`run-scripted.ps1 -Save`) can also start from a copy of a fixed save.

Tests: `OuterRingTest` (the ring, moves, paths to and from Europe, the edge
columns, start tiles, the safe tile, the attribute, the load fixes),
`MapGeneratorTest.testStartsKeepOffTheOuterRing` (both topologies),
`testSeedFixesTheNewGame`, `testStartOrderList` and
`testFixedStartOrderNorthToSouth` (N6),
`ClassicViewRuleTest.testNewGamesStartInCell14x6`,
`InGameControllerTest.testMoveOntoTheOuterRingIsRefused` (the server).

### End of turn and hand-over (`ClassicTurnFlow`, `ClassicOneShot`; build spec W5)

What happens between the player's last screen change and what comes next,
as measured in the landfall clip (13 turn ends, 6 hand-overs) and the
landing-slow clip. `ClassicGUI` creates the flow with the HUD and routes the
controller's `changeView` calls through it; its deadlines run on a
`ClassicOneShot` (one task on an absolute deadline, a daemon thread with the
recorder's sleep plus spin, posted to the EDT with generations -- not a
`javax.swing.Timer`, whose queue waits with `parkNanos`).

- **The last change.** The map reports the paints that show something new
  (final draw, view jump, blink toggle, cursor; `changeToShow`), the panel
  every paint that changed a pixel outside the turn indicator (it keeps what
  the screen shows and compares within the clip, `noteShown`), a box or a
  menu its close (`ClassicDialog.Watcher`, `ClassicMenuStrip.Host`). A pause
  that is pending and has not started a stage yet is re-based on every such
  change; the many repaints that change nothing do not count.
- **Automatic end (W5a).** `changeView()` and `changeView(Tile)` (the
  controller's "no unit left") arm it if it is our turn and no unit can move
  or go to its destination: **485 ms** after the last change, **756 ms**
  after a cancelled village box (`villageChoice`; FreeCol keeps the unit and
  its move, Roger's house rule, so today this only matters once W8 skips it;
  the learn question keeps it too, see "Escape, the high seas and the Europe
  question").
  When it fires it ends the turn (`endTurn(false)`) only if it is still our
  turn, no box, menu, first scene or classic screen is up, and no unit can
  move; a box keeps it from firing and its close arms it again (a screen
  without a close hook: the 50-ms poll). A classic screen counts only while
  the player can be looking at it (`ClassicGUI.classicScreenUp`): not
  minimized, and not behind the map -- windowed, a click on the map puts it in
  front of an open screen, and with the main frame active the screens behind
  it hold nothing (the blink, the hand-over, the turn start). A high-score
  window an ended game left over the title closes with the next game view
  (`installInGameHud`). FreeCol's `autoEndTurn` is forced off
  for the session (`SESSION_OPTIONS`, restored at teardown): it ends at once.
  With the classic pref `endTurnPrompt` on -- read at this idle decision, not
  at the turn start -- the Spielzugende mode follows at **500 ms** (813 after a
  village cancel) instead; see "The Spielzugende mode" (W17). With the pref
  off the automatic end comes only in a turn in which a unit of ours came up
  for orders; in a turn with none (all fortified or sentried, the ship at sea)
  the Spielzugende mode comes and waits (J2, below).
- **Stale panel (W5b).** `ClassicInfoPanel` remembers the block it last built
  for a unit and paints it while no unit is active or the remembered unit has
  no moves left (`showsLive`): after the last move the panel keeps "Züge" and
  "Ort" from before that move, through the pause, the hand-over and the AI
  phase. Another unit's block replaces it; the turn-start wipe forgets it. The
  season line is frozen from our end to the wipe, so the year changes in the
  wipe's paint. The minimap stays live. Our boxes are separate windows and
  never cover the panel, so a close leaves it exactly as before the box.
- **Turn indicator (W5c).** `ClassicHud.INDICATOR` (315..319, 197..199) is
  filled with the current player's colour while it is not our turn
  (`ClassicHud.indicatorRgb`: the eight tribes, England, France, Spain from
  NAMES.TXT; else the nation colour), and with the **next** player's in the
  paint just before our end request goes out (the request blocks the EDT; the
  server's answer and the player change come in either order, so the
  prediction holds until the player or the turn number changes, or 1.5 s
  after the server's answer).
  - **Ours** (#FF7100) lights while our new turn's boxes are up, while our
    ships' arrival chain holds the turn start (from the hold on, painted at
    once; the chain's first step 2 frames later, `CHAIN_AFTER_COLOUR_MS`:
    landfall #27876 -> #27878, H REVIEW2 L5), and
    otherwise only for one tick, in a paint of its own, just before the wipe
    (`flashOwnColour`, `OWN_FLASH_MS` = 16.43 ms: 1-2 frames, as every end in
    the landfall clip; M1 acceptance F4). Between the player change and the
    controller's first view change FreeCol autosaves and prepares its turn
    report (0.16-0.41 s); the last colour stays there now, where ours used to
    light for 13-29 frames. A 50-ms Swing poll (`tick`) repaints the box when the colour
  changes. Our end request is settled (`settleEnding`, first in `tick`,
  `unitChosen` and `noUnitLeft`) when the current player **or the turn
  number** has changed: native slides are queued on the EDT while the model
  runs ahead, so the whole AI phase can pass between two polls and the next
  `changeView(unit)` is already our new turn's first unit, which then gets its
  wipe and turn start. An end the controller did not send at all
  (`InGameController.endTurn` now returns whether the request went to the
  server: false for goto or trade-route units to look at, a panel up) is
  refused at once; a sent one counts as refused 1.5 s after the server's
  *answer* with neither change (counted from the request, a slow server's
  answer used to count as a refusal and let Enter send a second end; FINAL
  "Open" item 9). Its turn is kept, so a later turn change still ends the old
  turn, and a task posted behind the queue asks the controller for the next
  unit unless one or a pause came meanwhile (`recoverRefused`): the unit it
  re-selected during the end, or its end view arming the idle end again. The order is
  FreeCol's (dutch, iroquois, tupi, sioux, french, arawak, english, apache,
  inca, aztec, spanish in the runs), not the original's natives first; the
  European dark sub-phase is left out (spec: optional, its trigger is I).
- **Input (W5d).** `ClassicMapViewer.inputBlocked` also holds while it is not
  our turn, our new turn is not shown yet, a pause is pending, our end request
  is out, a goto unit runs or a visit is pending (`ClassicTurnFlow.isInputBlocked`):
  arrows (logged `key-blocked`), Enter, Space, W, B and map clicks do nothing.
  The arrows with nothing selected never pan (`key-ignored`); the TERRAIN
  cursor still steps.
- **Hand-over (W5e).** When the previous unit ran out of moves (or is gone),
  the next unit of the unit cycle comes (W5f, below; the controller's choice
  is put back): its block
  comes **500 ms** after the last change, and a view jump it needs at
  **280 ms** (`wouldJump`, `jumpTo`: the map and the minimap ring in one cut,
  the old block still up). The controller's re-selection of the shown unit
  meanwhile keeps the hand-over. A unit with no moves left does not blink
  (`rearmBlink`), so nothing changes on the map during the pause.
  - **Space** ("Keine Befehle") makes the unit done too (`ranOut`, I: clip007
    #1397 -> #1449, #1528): it is redrawn ON, stops blinking, and the next
    unit comes 500 ms after that redraw. The key asks the controller once:
    its state change already brings the next unit, and the second request
    used to take two units from the cycle, passing the first over.
  - **After a boarding** (spec delta W18) the carrier comes next, if it can
    still move, **128 ms** after the boarding's final draw (clip007 #4272 ->
    #4281, #6179 -> #6188, #6980 -> #6989: 9 frames), not the unit the
    controller chose, which is put back to come after it
    (`ClassicGUI.carrierAfterBoarding`, `Player.putBackActiveUnit`).
  - **After a landing** see "The landing" below.
- **Turn start (W5e, W5g).** The controller's first view change of our new
  turn wipes the panel (no block, new season line, no indicator, one paint),
  unless a box is up -- FreeCol's turn-start report ("Rundenende") comes
  before it, with the old year, the stale block and the orange indicator,
  and the wipe comes with its close. The first unit's block follows **300 ms**
  after the wipe, or with a jump the jump at **500 ms** and the block 21 ms
  later.
- **The unit cycle (W5f, `ClassicUnitCycle`).** One order for the whole turn,
  the original's unit list, used at the turn start, at every hand-over
  (after a last move, a skip, a landing, a goto run, a visit) and for W:
  the units in the order they came into the game, which FreeCol's ids keep,
  **the start ship first** (the original's unit 0; FreeCol makes it after
  the land units of the start, c1-edge.fsg: pioneer 5885, soldier 5886,
  merchantman 5887). The start ship is a naval carrier whose id is within
  the nation type's start count of the owner's lowest unit id (I: a ship
  made right after the lowest unit once all start units before it are gone
  would count too). Each turn starts at the head; after that the first due
  unit after the one that has just finished, wrapping, the finished unit
  itself last. Fits every clip sample: landfall's turn starts (ship,
  pioneer, soldier) and landing (#13118, #13649), clip007 #1928, #3107,
  **#3697** (the empty ship after the last passenger landed, though the
  pioneer and the soldier could move), #4579, #5804, #6643, clip008 #32963,
  #35590, #40835; clip006 rules out "the nearest unit first" and FreeCol's
  tile order (U2 -> U3 at (23,16), U3 -> U4). The flow takes the cycle's
  unit instead of the controller's at the turn start and at a hand-over and
  puts the controller's back (`Player.putBackActiveUnit`); a click and the
  boarding's carrier are not replaced. After a unit that got orders and
  kept its moves (a goto order that stopped early, F, S) the cycle's next
  unit comes at once, as the controller's always did (`bringNow`). While
  any hand-over is pending the active unit's blink is held ON
  (`ClassicTurnFlow.holdsBlink`, `ClassicGUI.blinkHoldReason` "handover";
  a visit that comes at once draws the unit ON at once): the unit up is
  the one the cycle moved past, and its toggles, screen changes, re-based
  the pause for good (G review: after W, F, S, V or a stopped goto with a
  visit next the turn froze). Due units (`kind`): ORDERS
  (`isCandidateForNextActiveUnit`, W18's woken passengers too), GOTO (a goto
  or trade-route unit on the map that has not run this turn), VISIT (below).
  A goto unit that ran and is still active with moves is ORDERS: never run
  twice (FreeCol's end-of-turn goto pass must not find it, C FINAL "Open"
  item 9).
- **The cycle's cursor (I2, `ClassicUnitCycle.cursor`, I-prep cycle.md
  3A/3B).** A place in the unit list, kept on the player
  (`Player.classicCycleCursor`, written to the save only when set; old
  saves read -1, the head): twice a unit's rank when the unit became
  active (the cycle's choice, a click, the boarding's carrier, a goto run,
  a visit; a unit not due, as the re-selection after a last move, does
  not move it), plus one once it finished (its last move, Space, its goto
  ran, its visit shown, gone; W is no finish). Every choice with no unit
  that has just finished takes the first due unit at or after the cursor,
  wrapping (a unit gone or not due there is passed over, a new unit comes
  before the wrap): back from a colony or Europe, after a box or the
  terrain view, a load (the controller's own choice no longer stands there:
  before, the ship came again), and the turn start. At our end request
  the cursor is settled (`turnEnds`): past a unit that is done, on a unit
  still due (the turn ended while it was up); with the classic pref
  **`turnStartFromCursor`** (in `classic-options.properties`, in no box,
  default on = Roger's rule) off it goes to the head instead, as every
  turn start of the clips. A refused end puts it back. The view mirrors it
  onto the server's copy of our player (as `markWoodcut`), so every save
  holds it: a mid-turn save goes on at the unit up, a turn-start autosave
  where the new turn starts.
- **Goto units (W5f).** FreeCol's goto batch is off in the Classic UI
  (`InGameController.setGotoBatch(false)` while the game view is up, on
  again when it goes; default on, the standard GUI and the AI unchanged),
  and so is its stop at a region still to discover (`setRegionStops`,
  BR#2707; the classic UI names regions at once, "The silent seams").
  A goto unit moves when the cycle reaches it (c6 U22, U25, U26): its block
  500 ms after the last change (a jump at 280 ms), its first step **28 ms**
  after the block (0-71 ms measured; at the turn start 100 ms after the
  block, LF 1502 #24166 -> #24174), through the controller's single-unit
  `moveToDestination(Unit)`. The controller's view changes during the run
  are dropped until a marker posted behind them (`gotoDone`). Arrived (or
  stopped) with moves left the unit stays the active unit: its block at
  once ("Keine Befehle", the letter G gone), the blink timed from it (c6
  #5123, first OFF #5146), and FreeCol's colony screen for a land unit
  arriving at its destination colony is dropped (`dropsColonyScreen`; a
  ship's docking screen stays). With no moves left the next unit of the
  cycle comes 500 ms after its last change (c6 U22 #4803 -> #4839); next
  turn it moves again at its place (#9413). With no candidate left the
  controller's end view brings the cycle's due unit instead
  (`ClassicTurnFlow.dueInstead`), at the turn start as its head. A unit with
  a destination in Europe is not due; FreeCol's end-of-turn goto pass sends
  it (I).
- **Visits (W5f).** A unit whose road, plowing or fortification FreeCol
  completed at our turn start gets a silent visit at its place in the
  cycle: the jump (if needed) at the hand-over's 500 ms, the completion
  **15 ms** later (c6 #3447 -> #3450, #4555 -> #4556, #4589 -> #4590: 471-542
  ms, 1-3 frames), no block, no blink, the next unit 500 ms after the
  completion; also after a visit that came at once after W, F or S (I:
  the first visit then at once, the next ones 500 ms apart as in c6
  #4556, #4589, #4619). FreeCol completes the work before our turn is shown, so the
  cycle keeps a snapshot of our end of turn (`snapshot`, `turnStarted`):
  until the visit the map and the panel show the old letter (R, P, the black
  F; `ordersRowShown`) and the new road is neither drawn nor listed
  (`roadShown`). A road under construction is never drawn or listed
  (clip008 #45293, as FreeCol's own map). Plowing and clearing change only
  the letter at the visit; the field shows from the turn start (I). Not
  saved: after a load (an autosave at the turn start) that turn has no
  visits.
- **A unit gone, and no choice (D acceptance D1).** When a ship left the map
  (sailing for Europe) and no unit is active, FreeCol's controller may change
  nothing on `nextActiveUnit` (left in a mode after a refused end). Where the
  flow asks it -- a block or a goto run whose unit is gone (`usable`: off the
  map, disposed, not ours) and the cycle has nothing after it -- and nothing
  came (`nextUnitOrIdle`: no `unitChosen`, no `noUnitLeft`), the flow brings
  the cycle's next due unit, or arms the idle decision itself if nothing can
  move (the automatic end, or the Spielzugende mode in a turn in which no
  unit came up). A unit off the map that the controller re-selects
  (`moveDirection`'s redisplay of the ship after its last move) is no unit
  to show when nothing else can move or is due
  (`ClassicGUI.goneWithNothingLeft`): the end view and `noUnitLeft`, as
  `changeView()`; with a unit left it is chosen as before, and the next unit
  comes as a hand-over from the ship.
- **Robust stages.** A stage's host call that throws no longer leaves the
  pause pending with no timer and the input blocked: the next stage is
  scheduled in a `finally` (`fire`, FINAL item 6). Once the game view goes
  (`dispose`) every late call -- the goto marker, a queued village cancel, a
  menu close in the teardown -- does nothing (item 11); the teardown closes
  the menu before it disposes the map.
- **Not our turn:** activations the controller sends then (doEndTurn's goto
  pass) are ignored; no unit comes up until our next turn.
- Recorder events: `endturn-timer-start <why> <kind> <stages> due=.. base=..`,
  `endturn-timer-fire <kind> <stage> late=..` (also `held` / `kept`),
  `endturn-timer-cancel`, `end-turn <auto|key|refused|settled late|refused:
  next unit>`, `indicator <rgb|off> cur=.. [predicted]`, `turn-wait`,
  `turn-wipe`, `handover ...`, `endturn-prompt`, `endturn-village`,
  `came-up <unit> turn=.. (<how>)`, `endturn-idle Spielzugende: no unit came
  up in turn ..`, `click-wake <unit> <state>`,
  `key-blocked`, `key-ignored`, `click-blocked`; the `state` probe has
  `flow=<kind>/<stage> [waiting] [prompt] [goto]`, and `waitIdle` waits while
  the flow is busy.

`ClassicTurnFlowTest` runs the flow on a fake clock and host: the pauses and
stages, the 485-ms end and its re-basing, the conditions, a box holding it,
the village pause, the Spielzugende hand-off, the hand-over with and without
its jump, the turn start (with a box, with a jump, without units: the mode,
after an AI phase no poll saw, after a refusal), the mode when no unit came
up and a click in it (J2), our colour's tick before the wipe, with a box up
and during the arrival hold, goto units first and a goto unit with no path, a goto ship
gone off the map with no choice from the controller, a refusal not
sent and a slow answer, a stage that throws, a disposed flow, a screen behind
the map, the indicator's colours and its poll, and `ClassicOneShot` (replace,
cancel, stale posts, the thread on the real clock: never early, the best of
five tries within 8 ms).
`ClassicMapViewerTest.testAfterTheLastMove` covers the blink stop with no
moves, the stale-block rule and the jump question; `ClassicHudTest.
testTurnIndicator` the 15 pixels; `ClassicUnitIconTest.testSessionOptions`
the forced options.

### The Spielzugende mode (build spec W17, spec delta D3)

With the classic pref `endTurnPrompt` on (the original's "Spielzug~ende",
default **off**) the turn does not end by itself: at the idle decision
(**500 ms** after the last change, **813 ms** after a cancelled village box;
the pref read there, so switching it mid-turn counts for that turn and
switching it off while the mode shows keeps the mode) `ClassicTurnFlow` enters
the mode and waits. Measured in landing-slow (1505-1507) and clip004
(1509/1510); the panel re-rendered with our painters is identical to the
original's #2306, #2328, #2352 and clip004 #1974, #1995 (0 px; the m1 W17
harness).

**With the pref off (J2; opening_014 1510, 1511, 1512, i-prep
spielzugende-fortified.md; H REVIEW2 M2; the original's manual p. 10: "only
displayed at the end of a turn in which you haven't yet had a chance to move a
unit")** the same mode, look and wait come in a turn in which no unit of ours
came up for orders (`ClassicTurnFlow.cameUpTurn`: the turn start's unit, a
hand-over's, the controller's choice made active at once, a click, a goto unit
that arrived with moves left, the view's first unit after a load; not a
visit, a goto unit's block before its run, the Europe screen and its
sailings, a box). Typical: every unit fortified or sentried, the ship at sea.
A turn in which a unit came up -- moved, fortified (F), skipped (Space) --
still ends by itself 485 ms after the last change (#691, #1834, #4680).

- **When.** At a turn start where nothing came at all, **328 ms** after the
  wipe, in the place of the first unit's block (`PROMPT_START_MS`; #4288 ->
  #4310/#4311, after Europe closed #3696 -> #3719/#3720: 314-342 ms; also the
  pref's own mode at a turn start); after a later change (a visit's
  completion, a box's close) at the automatic end's **485 ms** (#2178 ->
  #2211/#2212, I: the same timer as the end; the pref's mode keeps its 500).
  Nothing changed since the wipe (`changedSinceWipe`) decides "turn start".
- **A click on an own unit that can move** ends the mode without an end, in
  the pref's mode too (#4475 -> #4477): a fortified, fortifying or sentried
  unit is freed first (`ClassicGUI.unitClicked` -> `wake`, the server's
  `changeState(ACTIVE)`, before the activation, so the cycle's cursor goes on
  it, I2), the square and the minimap pixel go and the map's repaint shows the
  freed unit's "-" (`promptClicked`); the panel keeps the tile mode with the
  word as it was (it stops blinking; white in the clip, the panel icon's F
  too) until the unit's block **2 frames** later (`PROMPT_CLICK_MS`, a
  hand-over of its own, so FreeCol's own choice of the unit after the state
  change is kept). The unit then plays as any other; after its last move the
  turn ends by itself (#4646 -> #4680) and the mode does not come back. A
  unit with no moves left takes no click in the mode (it keeps its orders),
  nor does a unit with orders a click does not free, such as a pioneer at
  work (`ClassicGUI.takesPromptClick`: freed by the click, or one that takes
  orders; the fixer of part J: before, the working pioneer came up with his
  orders, Space could not skip him, Enter was refused and W found nothing).
  The same freeing on a click outside the mode (`ClassicMapViewer.clickOn`,
  both branches); there FreeCol's choice of another unit from inside the
  freeing's state change (its cycle head, with units left to move) goes back
  to its cycle instead of coming up for a moment before the clicked unit
  (`ClassicGUI.changeView(Unit, boolean)`, the fixer of part J).
- **A click on an own goto unit** (part L, Roger 2026-10-08: "Wenn ich eine
  Entität mit G auf einen Weg geschickt habe, kann ich seinen Weg später
  unterbrechen, indem ich mit der Maus auf die Entität klicke") cancels its
  goto order first (`ClassicGUI.cancelsGotoOnClick`: a unit on the map, not
  aboard, with a destination and no trade route; `cancelGoto` ->
  `InGameController.cancelGotoOrders`, the server's destination change with
  no choice of the next unit); the unit stays where it is, keeps its moves
  and comes up as any clicked unit, so the cycle no longer runs it. In the
  Spielzugende mode the same with moves left (`takesPromptClick`); without
  them the order goes and the mode stays (`gotoCancelledInPrompt`; I), so a
  unit on its way can be stopped before the next turn's cycle runs it.
  A unit on a trade route (T) keeps its route and its next stop: Roger's
  rule names G only, and the click is the one before part L (the fixer of
  part L; a click just to look at a wagon train took its route off).
  Recorder event `click-goto-cancel`. Tests:
  `ClassicMapViewerTest.testAClickCancelsTheGotoOrder`.
- **A load with nothing to move** (J3; the review of part I): the view opens
  with no unit when FreeCol's saved active unit (`restoreActiveUnit`, any
  state) is sentried, fortified or out of moves (`ClassicGUI.firstUnit`:
  then the cycle's first due unit if it takes orders, else none; before,
  the saved sentried ship blinked, Enter was ignored and Space could not
  skip it), and `reconnectGUI` hands over to the turn flow after the
  view's focus (`openedWithNoUnit`, as the controller's end view: the
  flow's `dueInstead`, else `noUnitLeft`): a due goto unit or visit comes,
  and after it the units that take orders, else the mode **485 ms** after
  the last change (no wipe at a load, I) and waits. Before, a load with no
  unit waited for a key with no mode (G acceptance A5); until the fixer of
  part J a goto unit first in the cycle and another unit that could move
  left the view with nothing up and nothing coming (Enter and Space
  refused).
- **The levi rules keep the moves of a completed fortification**
  (`model.option.fortifyKeepsMoves`, "The rules"): FreeCol takes the turn's
  moves when FORTIFYING becomes FORTIFIED at the turn start; the original's
  visit block shows "Züge: 1" (#2177), so a unit whose fortification
  completed this turn can be freed and moved at once.

- **The cursor tile.** The last active unit's tile (`ClassicMapViewer.
  promptTileFor`: the target of its last move), or the village whose box was
  just cancelled (`villageBoxCancelled(Tile)`, #4193), else the view's focus.
  After a load with no unit up it is our own unit's tile
  (`ClassicGUI.loadedViewTile`: FreeCol's saved active unit of ours, else
  the cycle's first unit on the map outside a colony's buildings; I), and the
  view opens centred on it, never on FreeCol's fallback on the open sea
  (fixer of part K).
- **The view** (fixer of part K, Roger's item 3: "die Ansicht ist irgendwo
  random auf dem Meer"). FreeCol's fallback view at a turn start with nothing
  to move (`Player.getFallbackTile`: the first colony, else the Europe entry
  tile on the open sea) no longer moves the view (`ClassicGUI.changeView(Tile)`
  -> `changeToTerrain(tile, false)`, recorder `view-kept fallback`); the
  original keeps it (opening_014 1510-1512: #2212, #3696 after Europe, #4311;
  no full redraw #4287-#4311). The mode's entry then shows the square
  (`ClassicMapViewer.enterPrompt(Tile, boolean)`, `ClassicGUI.showPrompt`):
  in a turn in which no unit of ours came up, by W4's test on the square's
  tile (kept in the safe zone, as in opening_014; centred in cell (7,6) in
  the margin or off the view, after the AI phase's W19 jumps or the player's
  own minimap click; I); after a unit's last move (the pref's mode) the view
  is not tested (never on arrival, W4) and moves only for a square off the
  view (I). A jump paints the map, the square and the panel at once
  (recorder `prompt on x,y jumped`).
- **The panel's tile mode** (`ClassicHud.paintTileMode`, `TileFacts`) instead
  of the unit block: "Ort: (x, y) 1" at (242,68) (the trailing number is
  unexplained, always 1 for now), then 7 px apart the land name on land
  (the player's @LANDHO name, else @COLONYNAME "Neuholland"; "<tribe> Land" on
  a native settlement's tile), the terrain, and one line each for a river, a
  road, plowing and a resource; then the list 10 below: a native settlement
  (its sprite, the tribe and "ein Dorf" green at x 262) and the units as in the
  unit list (a ship now with its type name over the orders, clip007 #3107); no
  "Züge" line. Below it the word **"Spielzugende"** (LABELS @MISC 2) at x 242,
  7 below where the next entry would go (117 under one unit at 92), at most
  192, white while ON and black while OFF. The facts are taken when the mode
  begins.
- **The map's square** (`ClassicHud.paintPromptSquare`): a 1-px white outline
  of the cursor cell, 60 native pixels, over terrain and sprite. No unit
  blinks in the mode.
- **The minimap pixel** of the cursor tile is white while the square is
  drawn, its own colour otherwise (`ClassicInfoPanel.promptDot`), the opposite
  phase to a unit's dot.
- **Blink.** Square, word and pixel change together on the map viewer's second
  `ClassicBlink` (328.5 ms, ON first). A menu or a box freezes the phase (no
  forced ON); its close restarts it ON one frame later (`armDelayed`, #5320 ->
  #5321). A FreeCol box without the watcher is noticed by the next toggle.
- **The end.** Enter, Space or a press on the word (`promptWordBounds`): the
  square, the word and the pixel go ON at once and freeze; the indicator and
  the end request follow 15 ms later (`PROMPT_END_MS`, #5500 -> #5501). The
  word stays white until the turn-start wipe; the square and its pixel stay
  until a paint covers them -- a slide over the cell or a slide's final draw, a
  jump, the next activation (clip004 #2766, landing-slow #2902/#5525/#6460) --
  or the next mode replaces them (also through FreeCol's fallback terrain view
  at our next turn's start, which no longer clears a frozen square; opening_014
  #4309 -> #4311, J2).
- **Other input** in the mode: the arrows are inert; a map click does
  something only on an own unit that a click frees or that takes orders,
  which comes up as above (opening_014 1512; W17 item 6), and on our own
  colony, whose screen opens as outside the mode (the original's manual,
  p. 10: the player "may continue to perform management functions" while it
  flashes; I: no clip); the mode stays and goes on when the screen closes,
  unless «Befehle aufheben.» freed a unit there: that unit comes up once the
  screen is gone and the mode ends (I; "The units standing in the colony").
  A unit on a colony's tile (its guard, a ship in its port), which the map
  does not draw there, is never freed or brought up by the click (the fixer
  of part J: before, the click freed the fortified guard, who lost his
  orders, and the colony did not open). E opens Europe; the menus and the
  minimap work.
- **Not built / I:** the colony block and "+ Weiter +" of a colony tile (W21),
  the meaning of the number after the position, Enter vs. click (both work),
  what arrows do; the turn-start visit's block ("Befestigen", black F, #2177:
  ours shows no block) and its jump right after an F order at 300-314 ms
  (ours 500); the original's one-frame black word while the mode is drawn
  (#2211).
- Recorder events: `endturn-prompt on|end <why>|left|click <unit>`,
  `endturn-idle Spielzugende: no unit came up in turn ..`, `came-up`,
  `click-wake`, `prompt on|off n=..|hold|restart|freeze|clear <why>`,
  `click-ignored prompt`.

Tests: `ClassicTurnFlowTest.testPromptMode` / `testPromptModeKeepsAndLeaves` /
`testVillageCancel` (the stages, the forced-ON end one frame before the
request, the pref off in the mode, leaving by a unit, the village tile),
`testTurnStartWithoutUnits`, `testPromptWhenNoUnitCameUp`, `testPromptClick`
(J2: 328 ms after the wipe, 485 after a visit, what counts as come up, the
click's 2 frames, the automatic end after the clicked unit's move),
`ClassicMapViewerTest.testPromptSquare` (phases, hold and restart, freeze,
what clears it, the minimap pixel), `testAClickFreesTheUnit`,
`testPromptClickKeepsTheWordUntilTheBlock`, `ClassicHudTest.testTileMode*`
(lines, layout, word and square pixels, live tile facts), `ClassicBlinkTest.
testArmDelayed`, `ServerUnitTest.testFortifyKeepsMoves`,
`LeviRulesTest.testFortifyKeepsMoves`; the fixer of part J:
`ClassicTurnFlowTest.testWhatAClickTakesInTheMode` (a pioneer at work
ignored, our colony opens, its guard kept, Enter still ends),
`testSpaceOnAUnitThatKeepsItsOrders`, `testATerrainViewClickBringsOnlyTheClickedUnit`,
`ClassicGUISeamTest.testLoadWithAGotoUnitFirstAndAUnitThatCanMove`.

### Escape, the high seas and the Europe question (build spec W0e, W0f, W8a; house rule D1)

- **Escape answers "no" (W0e).** In every yes/no box (`modalConfirmDialog`,
  so every `GUI.confirm*` and every controller confirm) Escape, the close
  button and a box that could not open answer **no**, whatever the
  controller's `defaultOk`; `defaultOk` only picks the option Enter takes.
  Before, Escape answered `defaultOk`: "learn" at a village, "found" at the
  site warnings, "sail to Europe" on the high seas. The event boxes
  (`askEvent`) and the choice lists (`modalChoiceDialog`: the village boxes,
  which unit lands; `chooseFromList`) already answered Escape with "no" /
  null, the controllers' cancel. All questions and lists go through
  `ClassicGUI.Prompter` (`BOXES` in the game), so the tests answer them
  headless.
- **Enter in the king's and the natives' boxes (C trap 1).** The original's
  boxes open with the bar on row 1 unless GAME.TXT gives an `@default`. The
  king's tax rise (`@KINGSTAMPACT` and the other tax texts, rows
  `@TAXOPTIONS`) opens on "Den königlichen Ring küssen" (clip005 #17489,
  clip006 #7380), so Enter there now accepts the tax
  (`ClassicGUI.monarchEnterAccepts`: `RAISE_TAX_ACT`, `RAISE_TAX_WAR`);
  FreeCol's box, and ours before, took the party. "Nein" holds the party.
  Escape does nothing in the King's decision boxes (G1, Roger: "man muss
  sich entscheiden"; before G1 Escape answered no, the party): the box and
  its bar stay until a row is taken, in the canvas and in the stopgap
  window (no Escape binding, the close button inert). A box that closed
  without a row (the game view went, the box failed) answers its first row,
  the ring or "Nein danke", never the party (`ClassicGUI.eventAnswer`). The
  King's notices (no "yes": a lower tax, war declared ...) still close on
  any key. The mercenary offers
  keep Enter on "no" (`@MERCENARIES` lists "Nein danke." first; I), and the
  advisor box (W7) lists their "no" first, with the bar on it. The
  natives' demands (`showNativeDemandDialog`) keep Enter and Escape on the
  refusal: FreeCol's default, and the original's first row (`@INDIANGOLD`,
  `@WANTSTUFF`, `@INDIANBEGFOOD` list the refusal first; I, no clip shows
  one); the advisor box lists the refusal first too, with the bar on it
  (D acceptance D5), so Down then Enter pays. The first contact with a native nation (`showFirstContactDialog`)
  takes "Ja", the peace, on Enter: `@INDIANWELCOME` lists "Ja" first and
  has no `@default`, and FreeCol's `FirstContactDialog` defaults to "yes"
  (D acceptance review). A refusal costs dearly -- the server adds major
  tension and bans missions for that nation (`nativeFirstContact`), the
  land on offer is lost, and the original answers with `@INDIANSHUN` ("Das
  bedeutet KRIEG!") -- and several such boxes can come at one turn start.
  Escape still refuses (W0e, Roger's rule; an open question for him), as
  at a native demand. `askEvent` takes the row Enter picks, and whether
  Escape does nothing (`mustChoose`), from its caller.
- **Back to Europe from anywhere (C trap 2).** BEFEHLE row 16 "Zurück nach
  Europa" (ships only) and its gold letter R fire FreeCol's new
  `ReturnToEuropeAction` (`returnToEuropeAction`), which calls
  `InGameController.goToEurope`: the destination dialog's "Europe" without
  the dialog (`selectDestination` and it share `goToDestination`). A ship
  on the high seas sails at once; any other one (in a west-coast colony,
  say) gets Europe as its destination, sets out at once and sails on
  entering the high seas, as a goto order does (with no way there, FreeCol
  skips the unit; its notice is not shown yet, acceptance A2). It is not put on
  the high seas where it stands (`InGameController.moveTo(unit, europe)`
  would do that: the server does not check the tile). The action is enabled
  for a ship on the map that can cross the high seas, while its player has
  Europe; otherwise the row is grey. R stays the road order for land units
  (the road action is disabled on water, the Europe action for land units).
  No question is asked (I: the original's behaviour on this order was not
  recorded). Once the ship has left the map with other units of ours still
  on it (sentried, fortified), the turn ends by itself as after any last
  move; before, it hung (D acceptance D1, see "A unit gone, and no choice").
- **Go to (G; R2, master plan W8e, N11; `ClassicDestinations`).** G and
  BEFEHLE 13 "Zum Hafen gehen" (ships) / 14 "Zum Ort gehen" (land units)
  fire FreeCol's `gotoAction` (no longer a no-op seam), whose
  `InGameController.selectDestination` asks
  `ClassicGUI.showSelectDestinationDialog`: the original's box with no
  portrait, GAME.TXT @SAILPORT "Wählen Sie einen Zielhafen:" for a ship,
  @TRAVELPLACE "Wählen Sie eine Kolonie als Reiseziel:" for a land unit
  (@width 230, the bar on @default=1; the rows at box + 15,
  `ClassicMenuBox.PORT_INDENT`; V landfall #23247/#23300, box
  (42,84,236,32), Steam capture opening_051; the golden crop
  24_SAILPORT is 0 px). The rows (the manual: the destinations the unit
  "could reach"): a ship gets our colonies with a port on water that
  leads to the high seas (`isConnectedPort`), not the one it lies in,
  that it can reach, then the home port "Amsterdam (Holland)"
  (@HOMEPORT + " (" + @COUNTRY + ")") while its player has Europe and the
  ship can get there (one path search, as FreeCol's own dialog); a land
  unit on the map gets our colonies on its landmass it can reach, no home
  port; a passenger gets nothing. Order (I, not recorded): the colonies in
  founding order, the home port last (`HOME_PORT_LAST`). With nowhere to
  go no box comes and the key does nothing (I). The arrows move the bar,
  Enter takes the row; Escape and a click outside choose nothing
  (`noCancelRow`: the builder's default would send the unit to the last
  row): the unit keeps its orders and moves and stays active. A chosen
  row goes back to the controller, whose `goToDestination` (the path
  "Zurück nach Europa" takes) sets it and moves the unit at once: its
  orders line "Ziel Amsterdam" (@ORDERS 3 + @HOMEPORT; a colony's name
  alone, W21b) is painted **42 ms** after the close and its first slide
  starts **128 ms** after it (landfall #23372 -> #23375 -> #23381;
  `ClassicMapViewer.holdFirstSlide`, the landing's hold with a panel
  moment; a hold over a second stale is dropped). A ship on water leading
  to Europe sails at once with no slide (as "Jawohl"). The unit counts as
  run in the unit cycle this turn; in later turns the cycle runs it at its
  place (W5f) until it arrives or leaves the map. A land unit arriving at
  its colony during that run gets no colony screen (as the cycle's run,
  `landGotoRunning`). FreeCol's Swing Europe panel is the seam's other
  caller (`EuropePanel.java:192`); the Classic UI never reaches it (its
  Europe screen sails with `moveTo`). Recorder events: `goto-list unit=
  rows=a|b chosen= dest= ms=` (`none` with no rows; `ms` the path
  searches), `hold-panel unit=` (the 42-ms panel), and the box's own
  `box-open SAILPORT` / `box-close`.
- **FreeCol's high-seas question is never shown (W0f).**
  `InGameController.moveHighSeas` asks `highseas.text` when a ship sails from
  coastal water onto the high seas; `modalConfirmDialog` answers it "no" at
  once (`silentNo`, recorder event `dialog-silent`), so the controller makes
  the plain move. A ship with Europe as its destination (a goto order, a
  trade route) still leaves the map on entering the high seas, as the
  original's "Ziel Amsterdam" ship does (landfall #25713).
- **The Europe question, by Roger's rule (W8a, in the advisor box W7).**
  A ship on the high seas in the last column the view
  shows (`ClassicHud.lastViewColumn`: x = W-2 = 56 of 58) ordered E, NE or
  SE (6, 9, 3) past it, onto the never-drawn ring or off the map
  (`ClassicHud.eastPastView`, `ClassicGUI.asksSailHome`), gets the original's
  @SAILHOME question instead of a move: `ClassicMapViewer.handleMoveKey`
  asks `ClassicGUI.sailHomeKey` before the controller. Its words are GAME.TXT
  `@SAILHOME` read from the pack ("{hoher See}" in gold; without the pack
  FreeCol's `highseas.*` strings), in the original's advisor box with the
  admiral over it (see "Advisor boxes" below): 0 px against the landfall
  crops 05a and 05b, live as well. The bar starts on the first row
  ("Jawohl, ...", `@default=1`); Down and Up move it one row, Enter or a
  click takes a row. "Jawohl" sails the ship to Europe (`InGameController.moveTo(unit, europe)`): it leaves its
  tile with no slide, as in the original (c8 #44179), and the controller
  brings the next unit. The second row and Escape do nothing: the ship keeps
  its moves and stays the active unit; the box's close restarts its blink ON
  and the turn flow's clock. Every other move is a plain one: entering the
  light water, leaving it, and moving along it. The original also asks one
  column earlier (EUQ, C5 #21226); we do not (master plan section 10).
  Recorder event: `sail-home unit=.. at=x,y <dir> chosen=<0|1|-1>`, and the
  key's `move-done ... question`.
- **The same question at the west edge (E1, Roger's rule mirrored).** A ship
  on the high seas in the first column the view shows
  (`ClassicHud.firstViewColumn`: x = 1, the west clamp; column 0 is the
  never-drawn ring) ordered W, NW or SW (4, 7, 1) past it, onto the ring or
  off the map (`ClassicHud.westPastView`), gets the same @SAILHOME box with
  the same answers: "Jawohl" sails it to Europe, "Nein" and Escape do
  nothing and keep the moves. `ClassicGUI.asksSailHome` asks
  `ClassicHud.sidePastView`, both edges together. As at the east edge,
  entering the light water, leaving it and moving along it are plain moves,
  and in isometric mode a NW or SW step that stays in column 1 is a plain
  move too. No clip shows the original's west edge (EUQ section 7, open
  point 4); the rule is Roger's. A ship that sails home from the west edge
  comes back there: FreeCol keeps the tile it left as its entry location.
- **"Handlung abbrechen" keeps the move (house rule D1).** The game option
  `model.option.cancelKeepsMove` (levi rules, `gameOptions.map`, **on**; the
  other rules and saves without it get it off, `Specification.fixGameOptions`,
  see "The rules" above): the server's
  `askLearnSkill` no longer spends a human player's moves before the learn
  question. Accepting spends them as before (`learnFromIndianSettlement`);
  declining (or Escape) and a settlement with nothing (more) to teach
  ("info.noMoreSkill") cost nothing, so the unit stays active and keeps
  blinking, and no automatic end starts while it can move
  (`ClassicTurnFlow.armIdle` asks `hasNextActiveUnit`). The AI, and the rule
  off, spend the moves at the question as before. The scout, the armed unit
  and the missionary boxes never cost a move on a cancel (FreeCol asks the
  server only after the choice). An expert never gets the learn question
  (`MOVE_NO_ACCESS_SKILL`, no server call).

Tests: `ClassicGUISeamTest` (`testEscapeAnswersNo`, `testEscapeCancelsAChoice`,
`testHighSeasQuestionIsSilent`, `testEastPastView`,
`testEuropeQuestionAtTheEastEdge`, `testWestPastView`,
`testEuropeQuestionAtTheWestEdge`, `testSailHomeText`,
`testKingsBoxEnterKissesTheRing`, `testNativeDemandEnterRefuses`,
`testReturnToEuropeOrder`, `testShipAtSeaGivesTheEndView`,
`testChoiceBoxKeys`, `testConfirmBoxKeys`, `testSailHomeBoxKeys`,
`testNoticesOneBoxEach`),
`ClassicMenuBarTest.testActionMap`,
`ClassicTurnFlowTest.testCancelKeepsTheUnitUp`,
`InGameControllerTest.testLearnSkillQuestionKeepsTheMove` (server).

### Voyages (`ClassicVoyages`, `ClassicBands`; master plan W13, spec R4, Part H3)

Roger (2026-10-07): his ship arrived in Amsterdam, nothing showed it, and with
no unit left on the map the automatic end ran 42 empty turns (1504-1545).
FreeCol's controller never opens Europe (its turn report has a line to click),
and units at sea or in Europe never count as movable. Now as the original:

- **Bands on the top strip** (`ClassicGUI.showBand`, the first scene's
  `ClassicMenuStrip.setBand`; the menu takes no input meanwhile, a new band
  replaces the one up). Texts (`ClassicBands`): NAMES `@NATIONALITY` + the
  ship's `@UNIT` (its type, never its name) + LABELS `@MISC` 5/7/6 + `@HOMEPORT`;
  FreeCol's words for missing pieces, never an empty band.
  - **Departure** "Holl. Handelsschiff Ziel: Amsterdam", 137 frames (1.955 s,
    landfall #25735, clip008 #44180), from the new no-op seam
    `GUI.unitSailedForEurope(Unit, Tile)` that `InGameController.moveTowardEurope`
    calls after the server accepted the move (every path: "Jawohl", R, G, a goto,
    the high-seas move); only for our ship that had a tile (not one that turns
    around at sea). Play goes on under it. The band's paint is the last change:
    the next unit comes 500 ms after it, and when the departure was the turn's
    last action the automatic end comes 485 ms after the band, under it
    (opening_013 #1536 -> #1570; no band holds the end any more). Its 137 frames
    run out only while the player has the turn
    (`ClassicTurnFlow.playerHasTurn`, `ClassicVoyages.BandEnd`): after our end
    it stays through the AI phase and goes 2 frames after our next turn's first
    unit (#1854 -> #1856), or, with no unit coming up, once our turn is shown.
    Guarded (a throw inside FreeCol's `moveTowardEurope` would skip its
    `fireChanges`/`updateGUI`).
  - **Arrival in Europe** "... Trifft jetzt ein in Amsterdam" and **back in the
    New World** "... Ankunft aus Amsterdam" (below).
- **Arrivals** (`ClassicVoyages`): our ships at sea are noted while our turn is
  shown (the 50-ms poll) and when our end goes out; at the next turn start a noted
  ship now in Europe, or one of a FreeCol "model.unit.arriveInEurope" message
  (taken out of the notices: no FreeCol box any more; a first laden ship still
  brings woodcut 9 there, before the band), has arrived there; a noted ship now on
  the map has come back. Taken once per turn (the turn's notes and messages are
  forgotten, so the chain cannot start again). A ship on a trade route: nothing
  (FreeCol sends no message). A load shows no arrival (Part H's load fallback
  is gone with its guard): Europe opens by itself only at an arrival at a turn
  start, never as a reminder of ships left in port (Roger, 2026-10-08).
- **The chain** (`ClassicVoyages.Chain`), inside the turn flow's hold before the
  year flips (`ClassicGUI.holdTurnStart`: the father offer first, then the
  arrivals; the hold is asked only when no box is up, so the band comes after
  the last turn-start box; our colour lights at the hold, the chain's first
  step 2 frames later, L5): one band per ship in Europe, 542 ms
  apart; Europe opens 542 ms after the last (38 frames, landfall #27878 ->
  #27916); a box up then (an emigration question): Europe after its close; open
  already (windowed, behind the map or minimized): refreshed, restored and
  brought to the front; the band goes when Europe is open; Europe not open
  after 1 s: counts as closed (logged). While the hold waits for Europe's close
  every game key is dead, so a Europe that goes behind the map or is minimized
  while the map's window is active comes up again at the next 50-ms step (not
  over a box or an open menu, not while the game's window itself is minimized;
  `ClassicGUI.raisesEurope`), and E brings it up too (`ClassicKeyMap
  .WHILE_EUROPE_HOLDS`; E raises an open Europe instead of opening a second
  one) (H REVIEW2 M1). A host call that throws ends the chain and the turn
  start goes on (L1). When
  Europe closes: per ship back in the New World the view jumps to it, its band
  (137 frames) one frame later, the hold ends 128 ms after the band (the wipe,
  clip008 #26208/#26209/#26218); the turn start's jump is decided at the wipe,
  so the ship's block comes 300 ms after it with no second jump. Then the wipe.
- **Europe holds the end** (`ClassicTurnFlow.Host.europeOpen` =
  `ClassicGUI.europeHoldsTheEnd`): while the Europe window exists (in front,
  behind the map or minimized) or was asked for less than 1 s ago, the automatic
  end waits; the player's close is a box's close (`europeClosed`: its "Abb" or
  Escape, the window's X and Alt+F4 through `WINDOW_CLOSING`; Alt+Enter's
  re-framing is no close): the end comes 485 ms after it.
- **Europe closes by itself** 357 ms after the last ship in its port sailed
  (`ClassicEuropePanel` Set Sail -> `ClassicGUI.europeShipSailed`,
  `ClassicVoyages.closesAfterSailing`, `EUROPE_CLOSE_MS`; opening_013 #3958 ->
  #3983, opening_014 #3669 -> #3695, opening_015 #1682 -> #1707; Roger,
  2026-10-08), also with colonists waiting on the dock (those marked "S" have
  boarded the ship before it sailed, opening_015 #1686; the fixer of part J);
  not if a ship is in port again by then; a modal list or
  a box up: after it. It is the player's close for the turn flow: the arrival
  hold ends (the wipe) and the unit whose turn it is comes through the unit
  cycle, or, with nothing to move, the Spielzugende mode 328 ms after the wipe
  (opening_014 1511 #3696 -> #3719/#3720; J2).
- **Turns with nothing to move** (the ship at sea, every unit fortified or
  sentried, nothing in the New World) no longer run on by themselves: with no
  unit up in the turn the Spielzugende mode comes and waits for Enter, Space
  or a press on the word (J2, "The Spielzugende mode"; H REVIEW2 M2). Before
  J2 such turns ended 485 ms after the wipe, which was not the original's
  behaviour (Roger's 1504-1545).
- **No reminder** (Roger, 2026-10-08): Part H's guard (Europe opened instead
  of the automatic end when nothing was in the New World) and its Europe after
  a load are gone. Ships left in Europe get no reminder.
- **`@TUTORIAL17`** 557 ms after the game's first Europe screen is drawn,
  whatever opened it, with Tutortips on (landfall #27956, box (7,10,306,90),
  `%STRING0/1/2` = home port, country, the New World's name). Once per game,
  kept in the save: `Player.classicTips` (bit k for `@TUTORIALk`, as
  `classicWoodcuts`; marked on the server's copy in single player). Over the
  Europe window it is the stopgap's box until W22.
- **Sailing time**: levi's `turnsToSail` 2 (the original's, "The rules" above).
- Not yet: the Europe status line and the band carried into Europe's top bar
  (W22), the ship's slide onto the Seeweg before it leaves (R2 section 8),
  woodcut 9 after the band (it comes before it), the colony notices after Europe
  (W23).
- Tests: `ClassicVoyagesTest` (arrivals, the message, port order, Europe only
  at an arrival, `closesAfterSailing`, the band's end, a voyage through the
  server's turns both ways, the chain on a test clock, a hidden Europe at an
  arrival, a failing host), `ClassicBandsTest` (stand-in and the pack's texts,
  `bandX` 94/71/83/83), `ClassicTurnFlowTest.testDepartureBandDoesNotHoldTheEnd`,
  `testEuropeHoldsTheEnd`, `testNoEuropeInsteadOfTheEnd`,
  `testArrivalHoldsTheWipe`, `testOwnColourDuringTheArrivalHold`,
  `ClassicGUISeamTest.testVoyageSeams`,
  `testEuropeOpensOnlyAtAnArrival`, `testRaisesEurope`,
  `testWoodcutsOfTheEventsAndNotices`, `ClassicHudTest.testKeysWhileWaiting`,
  `ClassicScriptTest.testScreenClick`, `PlayerTest.testClassicTips`,
  `LeviRulesTest.testSailingTime`.
- Scripted runs (`ClassicTestHarness` only; `ClassicGUI.childWindowHook` is
  null in a game): a sub-window opened while the game's window is minimized
  opens minimized and without focus, and the script's keys go to an open
  classic screen when no window has the focus, so a minimized run can open
  and close Europe without a window on the desktop.

### Advisor boxes (`ClassicAdvisorBox`, `ClassicAdvisorLayer`; build spec W7)

Every question, choice and notice of the game is now the original's
advisor box, drawn into the 320x200 canvas over the map and the panel, in
place of the `ClassicDialog` windows (which stay as the stopgap where the
canvas is not what the player looks at, below). Measured on the landfall
clip's 24 GAME.TXT boxes (`landfall 05-dialogs-and-events.md` sections 2-3),
clip004 (the Sioux chief), clips 005/006 (the King, the Tea Party) and the
dago-colony clips (the bar); V where verified on the pixels, I inferred.

- **Look (V).** `ClassicMenuBox.paintDialogFrame` in the GAME theme,
  WOODTILE from the box's corner; no title bar, no buttons, no page
  counter, no "Okay". FONTTINY, ink 68 with `{..}` gold 149, rows marked
  too; the prompt at box + (5, 9), 6 px apart; rows at x + 9, glyph top
  `y + 13 + 6P + 8i`; the bar a flat index-138 strip
  `(x + 4, rowTop - 1, w - 8, 7)` under its row's text, which keeps its
  colour. A greyed row (a choice FreeCol disables) is ink `0x555555`.
- **Size (V).** `w = @width + 6`, `h = 6P + 8R + 18`. GAME.TXT's own line
  breaks are ignored: the text is reflowed by `ClassicTextLayout.BOX`, the
  plain advance width of a line, spaces collapsed, braces taking no room, at
  most `@width - 6` (161 line ends of the clip: one exception,
  @TUTORIAL13's first line, kept at 216 of 214 px). The page rule of the
  nation pages (`ClassicTextLayout.PAGE`) misses four of them. FreeCol's
  own texts are laid out at width 230, one paragraph per line, their `{ } ~
  ^ _` made literal; a text too tall for the screen gets width 300, then is
  cut with "...".
- **Place (V).** Without a portrait the box is centred, `x = (321 - w) div
  2`, `y = (201 - h) div 2`, or `y = @y` when the message has one
  (@TUTORIAL17). An advisor stands OVER the box at a fixed offset and the
  union of both is centred (`ClassicFirstScene.place`): admiral MSS0 at
  (-4, -71), soldier MSS1 at (w - 55, -77), trade advisor MSS2, frontiersman
  MSS3, priest MSS4 and colonist MSS5 centred, `(w - pw + 1) div 2`, at -78,
  -87, -52 and -62. A chief stands UNDER the box, on his tribe's side
  (`Portrait.chiefLeft`, L3/Q11), at `chief.y = (198 - ph) div 2` (it fits
  all five portraits measured; an even height is 1 px lower than with 197,
  the Apache at 28). At the right: `chief.x = min(246, 317 - pw)`, the box's
  right edge 3 px left of him, at least x 0 (Arawak (246,8) and box x 7,
  Sioux (210,10) and box x 0; the 3 px are I). At the left, as the King
  ((0,18), clip005/006 (84,68,236,64)): x 0, the box flush right,
  `x = 320 - w` (Iroquois (0,11) and Apache (0,28) with box x 84,
  playthrough-1). The side: V for Iroquois and Apache (left), Arawak and
  Sioux (right); Aztec and Tupi left, Inca and Cherokee right by the index
  parity that fits those four (I). The chiefs' sprites are `IND<n>A0` in
  NAMES.TXT @TRIBES order (Inca 0 ... Tupi 7, `ClassicGUI.TRIBES`).
- **The bar (V, Roger).** It starts on GAME.TXT's `@default=n` (1-based),
  else on row 1 (@SAILHOME, @LANDFALL, @ABANDON's 2, @BUYME1, every box
  without one); in a FreeCol box on the row FreeCol makes the default.
  Up and Down (also the keypad's 8/2, held keys repeating) move it one row,
  never past the ends, in one paint; Enter takes its row, Escape the box's
  cancel row (Roger's rule: the "no" row), except in the boxes where one
  must choose (the King's decisions, the father and recruit boxes:
  `noEscape`), where Escape does nothing. The mouse never moves it by
  hovering: a press on a row puts it there and the release on that row
  takes it; a press outside the box removes it and the release outside
  closes the box as Escape (clip004, the options box). The portrait's own
  rectangle counts as the box (I): a click on the admiral, a chief or the
  King answers nothing. In the King's boxes, the first contact and the
  natives' demands (`askEvent`, `Request.outsideCancels` off; I) a click
  outside does nothing at all and the bar stays: their "no" cannot be
  undone (the Tea Party, a refused peace, a refused demand), and the
  notices that often come just before them close on a click anywhere (E
  acceptance A5). Confirms, choices and @SAILHOME keep the measured rule.
  A notice (no rows) goes on any key or click. Enter, Escape and the other
  keys count only as fresh presses made while the box is on screen (an
  auto-repeat of a key held from before, or a press older than the box,
  does nothing).
- **Timing (V).** A box comes, moves its bar and goes in one paint
  (`paintImmediately`, as the slide and the blink paint, so also in a
  minimized window, whose `repaint()`s Swing holds back). A box after
  another comes no earlier than 200 ms after its close (the clip restores
  the screen for 0.13-0.27 s between chained boxes). A box whose portrait
  is not the last one shown loads its palette first, 3 frames ahead (the
  clip: 2-8, only when the advisor changes): the recorder's frames then
  carry the portrait's entries 16-119 and 144-255 (W22p: the Sioux chief
  loads 16, 24, 32 and 40 too, clip004 #5089, the King 103 and up to
  251, clip005 #17484; 5, 12, 13, 120-127 and 139 are never loaded)
  (`ClassicFrameRecorder.portraitPalette`, from the pack's index sheet and
  sprite). The same portrait again loads nothing. The original's load also
  recolours the frozen screen behind the box at those entries (6 map px
  under the Sioux chief in clip004 #5095); ours keeps the game colours
  there.
- **Frozen screen, exact close (V).** While a box is up or due the blink
  holds ON and the turn flow waits (`blinkHoldReason`, `turnBlocked`, the
  `ClassicDialog.Watcher` hooks), the map, the key map and the strip take
  no input (`isDialogShowing`, `boxBusy`), Alt+Enter is refused. Only the
  water cycles under it. The layer paints nothing but the box and its
  portrait, so its close shows exactly what was there: 0 px in every live
  check.
- **Modal like a dialog.** `ClassicAdvisorLayer.show` returns the answer
  the seam needs (`modalConfirmDialog` returns a boolean): it waits in a
  secondary loop of the event queue, which keeps pumping, as a modal
  `JDialog` does. A box asked for while another is up or due waits its
  turn, in the order they came; the earlier caller resumes after the later
  box is answered, as with stacked dialogs. The game view's teardown closes
  every box as dismissed ("no", "cancel").
- **The seams (`ClassicGUI.Prompter`, `putBox`).** `modalConfirmDialog`
  (every confirm: FreeCol's words, its "yes" row first, the bar on
  `defaultOk`'s row, Escape "no"); the event boxes through `askEvent`: the
  King's (the King at the left; the tax rise opens on "kiss the ring", the
  mercenaries on their "no", listed first), the first contact (the tribe's
  chief on its side; "Ja" first and barred; FreeCol's words until W8c),
  the natives' demands (the refusal first and barred); `sailHomeKey`
  (GAME.TXT @SAILHOME with the admiral); `askLandfall` (GAME.TXT @LANDFALL
  with the frontiersman, in both landing seams); `confirmFortifyWar`
  (GAME.TXT @HAVETREATY with the soldier, part M2: «"Wir haben einen
  Friedensvertrag mit den {Frz.} unterzeichnet, Eure Exzellenz."», %STRING0
  NAMES @NATIONALITY, rows «Handlung abbrechen.» (the bar's, Escape's:
  nothing, nothing spent) / «Friedensvertrag brechen.»; box (34,116,236,46),
  the soldier at (215,39), 0 px against clip opening_018 #640,
  `ClassicWar`, `ClassicWarTest`); the colony screen's unit box @COLONYUNIT
  in the colony screen's own layer ("The units standing in the colony"
  below; a *unit box*: no portrait, the unit's icon at box + (5,6) and a
  22-px icon column, `ClassicAdvisorBox.UnitIcon`); `modalChoiceDialog` (one row per
  choice, the cancel row last, greyed choices); and the notices: each model
  message is a box of its own, one after the other (the original has no
  paged report), the error and "not yet" notices too. The answers are
  FreeCol's as before. `putBox` shows the box in the canvas while the main
  window is the one the player looks at; on the title screens, over a
  colony, Europe or report screen (`dialogOwner` is not the main frame),
  without the pack's FONTTINY, or when the rows do not fit on the screen,
  the stopgap stays (`ClassicDialog`, the selection list for a choice).
- **Not yet.** FreeCol's words in the FreeCol boxes (the GAME.TXT texts of
  the villages' menus, the rumours' questions, the first contact chain, the
  King's texts are W8c-W8f, W24; the landing's is below; the notices that
  have a GAME.TXT text are below); the
  @LANDHO input field; the King's KING2 gesture (W24). The list boxes of
  the colony and Europe screens (D6, D7, D9c) are built on the list-box
  extensions below.
- **Recorder events:** `box-palette` and `palette-portrait` (the portrait's
  palette), `box-open <id> box=x,y,w,h portrait=<sprite>@x,y rows= bar=
  text=`, `box-bar <id> row=`, `box-close <id> chosen=` (`-2` for F1, D2);
  the harness's state line shows ` box=<id>:<bar row>` (or `due`), its
  `isIdle` is false and its keys and clicks go to the box while one is up
  or due.

Tests: `ClassicAdvisorBoxTest` (the geometry of all 26 measured boxes, the
bar's keys and mouse, the text helpers, `@default`, long texts, the
portraits and their palette; and `testGoldenAgainstTheLandfallClip`: 26
GAME.TXT boxes drawn from the pack against the clip's pixel-exact crops
`landfall/analysis/img/*_1x.png`, box and portrait pixels, 0 px off apart
from the original's mouse arrow and @TUTORIAL13's first four lines; it
needs the pack and `-Dclassic.clips`; the clicks on a portrait and outside:
`testAClickOnTheKingsPortraitDoesNotAnswer`,
`testAClickOutsideAMonarchBoxDoesNotAnswer`,
`testAClickOutsideSailHomeStillAnswersNein`), `ClassicAdvisorLayerTest`
(modal show, keys, held keys, the mouse, a click outside the King's box,
chained boxes 200 ms apart, the palette's lead, notices, teardown),
`ClassicGUISeamTest` (every seam's Enter, Escape, arrows and a click
outside on the real bar: `testConfirmBoxKeys`, `testChoiceBoxKeys`,
`testSailHomeBoxKeys`, `testKingsBoxEnterKissesTheRing`,
`testNativeDemandEnterRefuses`, `testNoticesOneBoxEach`).

### List boxes (`ClassicAdvisorBox`; build spec D2)

The original's list boxes (the father choice, the build menu, the job
menus, @RECRUIT) are advisor boxes with a few more parts, each a
`Builder` call; measured on clip008 (`clip008-analysis/01-fathers.md`
§2.2, `01-colony.md` §3.5-3.6), V unless marked:

- **Row indent** (`rowIndent`): the rows at box + 9 (`ROW_INDENT`, the
  village menus, the build menu) or + 13 (`LIST_INDENT`: the father,
  recruit and job lists; I: two leading 2-px blanks).
- **Footer** (`footer`): LABELS @MISC 188 "(F1 für Hilfe)" in gold,
  flush right at `x + W - 2 - w`, glyph top `y + H - 9`; it adds 6 px,
  `h = 6P + 8R + 18 + 6` (82 the fathers, 126 the build menu, 174 and 78
  the job menus).
- **Right column** (`right`): per row a cell, right-aligned as far from
  the box's right edge as the rows are from its left (the build menu's
  costs end 9 px from it, the job menus' goods 13 px).
- **Row colours:** the current entry yellow, the whole row with its cell
  (`current`: the current build, the unit's own role in the job sub-list);
  an unavailable row grey 8 (`0x555555`), with its cell (`disabled`: Späher
  and Dragoner without horses).
- **Start row, mouse:** the caller's start row (`defaultRow`); a press
  puts the bar on the row under the pointer, the release on that row
  takes it (as every advisor box).
- **F1 hook** (`help`, `Help`): F1 tells the hook the barred row and the
  box closes with `Bar.HELP` (-2); the caller shows the help and may ask
  the box again. Without a hook F1 is any other key.
- **No Escape** (`noEscape`): Escape does nothing (the King's decisions,
  G1; the Fountain of Youth's recruit box). The stopgap window of such a
  box has no Escape binding and an inert close button
  (`ClassicDialog.ask(..., cancellable)`). The father box and Brewster's
  recruit box take Escape as "Nein" since the G fixer (Roger's rule: Esc
  is "Nein" everywhere but at the King's decisions): `noCancelRow`, the
  box closes with no row and the choice comes again next turn.
- **A page** (`picture`): a full-screen 320x200 picture instead of a box,
  a notice of the whole screen (any key or click closes it): the
  Colonopedia page below.
- **Chain time** (`chain`): the least time after the previous box's close,
  instead of the usual 200 ms (the F1 page 142 ms, the father box again
  264 ms).

Tests: `ClassicListBoxTest` (the geometry, the colours, F1, no Escape, the
page; and `testGoldenAgainstTheClip`: the build menu #11142 and the job
menus #42043 and #42539 drawn with the extensions over clip008's frames,
0 px off on the whole box; the rows' words are the clip's, the boxes that
will make them are D6's and D7's). Two findings for D7: NAMES' upper case
leaves the "ä" of "STäLLE" small (as `ClassicText.upperAscii`), and a
two-good cost has no blank between its parts: "(52 Hämmer)(20 Werkzeuge)".

### Notices (`ClassicNotices`; master plan N1)

FreeCol's information messages (`GUI.showInformationPanel`, which the base
GUI drops: every result of speaking to a chief, every village's answer,
"not enough gold", a colony that cannot shrink) and its model messages are
each one notice box (W7), in the original's words where GAME.TXT has the
same notice (`ClassicNotices.RULES`), else in FreeCol's:

| FreeCol | GAME.TXT | Portrait |
|---|---|---|
| `scoutSettlement.speakBeads` | @CHIEFGIFT (the tribe, @NATIONABBREV "Holl.", the worth and the coin; V clip008 #51431) | the tribe's chief |
| `scoutSettlement.expertScout` | @CHIEFGUIDES (V #46396) | chief |
| `scoutSettlement.speakTales`, `speakNothing`, `speakDie` | @CHIEFAREA, @CHIEFBORED, @CHIEFKILL (I) | chief |
| `info.noMoreSkill` | @LEARNALREADY (V #37156) | chief |
| `learnSkill.leave`, `learnSkill.die` | @LEARNMAD, @CHIEFKILL (I) | chief |
| `buildColony.badUnit` | @ONLYCOL (I); the GUI first takes the unit's own cause (Refusals below) | none |
| the rumours: mounds empty / gift / treasure | @BURIAL1 (V landfall #20177), @BURIAL2, @BURIAL3 | frontiersman |
| the rumours: nothing, vanished, chief's gift, ruins, Cibola, fountain, survivors | @LOSTCITY6, 5, 7, 3, 2, 1, 9 | frontiersman |
| `model.lostCityRumour.burialGround` | @SCREWED (the tribe) | chief (I) |

The tribe is the owner of the notice's object (the village), else the
nation of its `%nation%`. A notice whose value the original does not have
(a tribe or nation outside its eight and four) keeps FreeCol's words.
Silent: a key while it is not our turn (`info.notYourTurn`), and FreeCol's
illegal-move messages (`move.noAccess*`, `move.noAttackWater`,
`move.noTile`), which only a goto's failed last step still posts: a move
key gets the original's refusal before the controller (next section). The
recorder logs `notice-silent`. `buildColony.badUnit` and FreeCol's site
refusals (`model.noClaimReason.*`) come as the refusals below. On the event
thread the box is asked at once (the controller posts these with
`invokeLater`); from another thread it is posted, so no server message
waits for the player.

Dropped (part K3, Roger 2026-10-08: "den gibt es auch gar nicht im
Original - bitte löschen"; GAME.TXT has neither): two of FreeCol's notices
at a first contact never come, the recorder logs `notice-dropped <id>`.
- `model.unit.nativeSettlementContact` ("Ihr trefft auf einen Späher der
  Tupi aus Paraná-mirim."). FreeCol's server sends it once per village, not
  per tribe: when our unit comes next to a village or one of its people,
  and in the natives' turn when one of them comes next to ours
  (`ServerUnit.csNewContactCheck`). The meeting stays woodcut 3 and the
  chief's box.
- `diplomacy.offerAccepted` / `diplomacy.offerRejected` ("Spanien hat Euer
  großzügiges Angebot angenommen" / "abgelehnt") when they only echo our own
  answer to the other nation's proposal: the server sends the session's
  result to both sides and the client turns it into this notice for whoever
  gets it (`InGameController.diplomacyHandler`). `showNegotiationDialog`
  notes the other nation before it answers (the contact peace without a
  box, G1, and the box's "Annehmen" / "Abbrechen"); a notice about a nation
  so noted is dropped (`ClassicNotices.isAnswerEcho`, by the label of its
  `%nation%`), as a model message and as an information message. The note
  is kept (a notice held for the first scene passes the filter twice) and
  cleared at the game's teardown. Our own proposals are "not yet"; when
  they are built, sending one must take its nation out of the note so that
  the real answer is shown.

Tests: `ClassicNoticesTest` (the silent ones, the rules, the tribe, the
words with the pack, the GUI's two seams; the dropped notices,
`testTheMeetingNoticeNeverComes`, `testTheEchoOfOurAnswerNeverComes`,
`testTheOriginalHasNoneOfThem` and, on the server,
`testTheServerSendsTheDroppedMeetingNotice`; and `testGoldenAgainstTheClips`:
@BURIAL1, @CHIEFGUIDES, @LEARNALREADY and @CHIEFGIFT as the GUI builds
them from FreeCol's messages, 0 px off on box and portrait).

### Refusals (`ClassicIllegalMoves`; R3, Part H1)

An order the game refuses gets GAME.TXT's box for it, where the original
has one, before FreeCol's controller sees it: no FreeCol sound, no server
call, no move cost (Roger's house rule; the original's @LEARNMASTER ended
the unit's turn), the unit stays the active unit and blinks again when the
box closes; nothing ends the turn. FreeCol's own rules decide what is
refused (`Unit.getMoveType`, `Unit.canBuildColony`,
`Player.canClaimToFoundSettlementReason`, the actions' `shouldBeEnabled`);
no rule is added.

| Order | Refused because (FreeCol) | Box | Portrait (I) |
|---|---|---|---|
| move | merchantman, caravel, galleon into a foreign ship (`MOVE_NO_ATTACK_CIVILIAN`) | @SHIPCOMBAT | admiral |
| move | civilian into a foreign unit or colony (`NO_ATTACK_CIVILIAN`, `NO_ACCESS_SETTLEMENT`) | @CANNOTATTACK | soldier |
| move | from aboard onto an enemy square (`NO_ATTACK_MARINE`, `NO_ACCESS_WATER`, a civilian aboard); a loaded ship onto land a foreign unit holds | @LANDFIRST | frontiersman |
| move | ship or wagon train into a foreign colony at war, or not contacted (`NO_ACCESS_WAR`, `NO_ACCESS_TRADE`) | @TRADEATWAR | none |
| move | the same, contacted and at peace, without de Witt | @TRADEMERCANTILISM in FreeCol's words (`%STRING0` unknown) | none |
| move | ship at an uncontacted village (`NO_ACCESS_CONTACT`) | @DONTKNOWSHIPS | admiral |
| move | empty ship or wagon train at a village (`NO_ACCESS_GOODS`) | @TRADENOCARGO | the chief |
| move | the rebels' ship past the edge (no Europe) | @EUROPENOTLEAVE | admiral |
| move | into the sea without our ship, onto a full ship, off the map, an empty ship onto land | nothing | |
| B | a ship | @SEACOLONY | frontiersman |
| B | a type that cannot found colonies (wagon train, artillery, treasure) | @ONLYCOL | none |
| B | the war of independence with `foundColonyDuringRebellion` off (never in levi) | @NOCOLONIESEITHER | none |
| B, menu | a colonist without moves | nothing | |
| B, menu | FreeCol's site refusal: at sea / mountains / next to a colony (`terrain`, `settlement`, `worked`, `europeans`) | @SEACOLONY / @TOOMOUNTAIN / @TOONEAR (the colony's name) | frontiersman |
| P, R | a land unit that is no pioneer | @ONLYPIO | none |
| P | the land is ploughed | @NOPLOW | none |
| R | a road is there | @NOROAD | none |
| P, R | anything else (a ship, no moves, foreign land) | nothing | |

Legal moves, a move without moves left (FreeCol's SKIPPED), a landing
(W8b) and the village's skill and mission answers (D11) go to the
controller as before. FreeCol's illegal-move sound is dropped on every
path that still reaches it (a goto's failed step, those village answers;
`ClassicSoundController.isDropped`, recorder `sound-dropped`): the
original has no such sound (landfall 06-audio section 5). The move box comes the landing box's time after the
key (`landfallShowAt`, I); B, P and R at once. Without the pack the words
are `classic.refusal.<SECTION>` (FreeColMessages). The recorder logs
`illegal-move` (with the move type and the section), `move-done ...
refused` and `refused`. No clip shows any of these boxes: the words are
GAME.TXT's, the triggers and portraits are inferred.

Tests: `ClassicIllegalMovesTest` (every verdict, classic and levi, both
topologies by the suite), `ClassicGUISeamTest.testRefusalBoxes` (the
boxes, the moves kept), `ClassicMapViewerTest.testRefusedMoveKey`,
`ClassicHudTest` (the P/R hook).

### The silent seams (`ClassicSeams`; master plan N15, G1)

Seams the base GUI leaves silent, and what the classic UI answers there.
Two of them froze the game: the server's end of turn waits for every open
`Session` (`InGameController.endTurn`: `while (Session.waitingForSession())
sleep`).

- **Loot** (`showCaptureGoodsDialog`; froze). A naval win against a loaded
  ship opens a `LootSession` without a timer. It is answered at once, no
  question: what fits, the most valuable first at our market's bid price,
  each one only if the server's own check (`Unit.canAdd`, one after the
  other) takes it (`ClassicSeams.lootTaken`); never `null` and never more
  than fits (either leaves the session open). Then one GAME.TXT
  `@CARGOCAPTURE` notice per goods type ("Engl. Ware (150 Felle) durch
  Holl. Kaperschiff erobert!"), the loser being the one our ship fought last
  (`animateUnitAttack` only remembers the fight; the animation is W12);
  without the loser or the texts, FreeCol's words in one notice. I: no clip
  shows a loot; GAME.TXT has no capture question.
- **First contact with Europeans** (`showNegotiationDialog`; froze). Our
  land unit meeting a European unit or colony gets the peace treaty in a
  `DiplomacySession` of 1000 hours (single player). The peace is accepted
  at once, no box (`ClassicSeams.isContactPeace`; I). Any other proposal
  sent to us: a box in FreeCol's words, "Annehmen" / "Abbrechen", the bar
  and Escape on "Abbrechen". Our own proposals (a scout's "Verhandeln", a
  ship's trade at a foreign colony): the "not yet" notice, nothing sent.
  The "accepted" / "rejected" notice that the server's result brings back
  after our answer never comes (K3, Notices above). Not built yet: the
  original's own chain, woodcut 10 (once) -> @HELLOFIRST -> [@SIEGES] ->
  @WORTHY ("Seid Ihr damit einverstanden?" Ja/Nein) -> @PEACEMEEK
  (playthrough-1 `02-europeans.md`, France #36546-#39181, England
  #88666-#89536 without the woodcut, V; "Nein" -> @WARMANLY, I); it is S5
  of the playthrough-1 gap list, noted at W8c in the master plan (whose W8c
  row lists the natives' chain). When it comes, the @WORTHY answer must
  keep FreeCol's echo dropped.
- **The recruits** (`showEmigrationDialog`: William Brewster, the Fountain
  of Youth). A list box of the three recruits (D2's rows), bar on row 1,
  a click beside it doing nothing: GAME.TXT `@RECRUITCHOOSE` (our
  `@COUNTRY`, `@HOMEPORT`) over the priest, `@LOSTCITY0` over the
  frontiersman (I, in no clip); FreeCol's words without the texts. Before,
  after Brewster no recruit ever came. Escape at Brewster's box is "Nein"
  (Roger's rule, G fixer): no recruit, and the controller asks again at
  the next turn start (`checkEmigrate` still true, the points stay); at
  the Fountain of Youth it does nothing, as a cancel would throw the free
  recruits away (I).
- **A region's name** (`showNamingDialog` with `nameRegion.text`; G
  fixer). The server counts a region discovered only once the client
  answers its naming (`setNewRegionName` -> `csDiscover`): unanswered,
  every land region stayed to discover for the whole game, and FreeCol's
  goto stopped after every land step that left moves (BR#2707's break in
  `InGameController.moveTile`). The default name is answered at once, no
  box (`ClassicSeams.namesRegion`; the original names no region), and the
  goto's stop at a new region is off while the classic game view is up
  (`InGameController.setRegionStops`, switched with the goto batch): the
  original's goto runs on (clip006 U22: three road steps in one run).
  FreeCol's history then records the discoveries (and, with the game
  option `explorationPoints`, their score), as with FreeCol's own dialog.
  The server asks again for the same unit until it has the answer and
  refuses a second answer ("No discoverable region", shown as an error
  notice), so the client answers once (J3): a move's reply comes on the
  event thread, where `newRegionNameHandler` runs at once
  (`invokeNowOrLater`), so the Pacific's woodcut 6 and the answer come
  inside the step that brought the request and a goto run's next step
  finds the Pacific discovered; a request handled after the answer sends
  nothing, also the first one after its woodcut 6 when a repeat ran while
  the woodcut was up (`ClassicPacificTest.testOneAnswerInAGotoRun`,
  `testARepeatDuringTheWoodcutIsNotAnsweredTwice`,
  `testInvokeNowOrLaterOnTheEventThread`).
- **A village, a tile** (`showIndianSettlementPanel`, `showTilePanel`):
  notices in FreeCol's words. A click on a native village centres the view
  and then shows its notice (`ClassicMapViewer.clickOn`; an invention, the
  original's reaction is in no clip; one call to drop). Nothing calls the
  tile notice.

Listed, not changed (they do not block): `showVictoryDialog` (the win's
question is never answered, the game goes on), `showNamingDialog` for the
first landing's name (`newLand.text`, W10: the server's default stays),
`showSelectTributeAmountDialog` (a tribute demand at a European colony is
dropped, the move kept), and the seams the classic UI never reaches (the
menu rows of `ClassicMenuModel.NOOP_SEAMS`, the pre-game, editor and
multiplayer panels).

Tests: `ClassicGUISeamTest` (`testLootIsAnsweredAtOnce`,
`testLootSessionCompletes` on a server, `testLootNotice`,
`testEmigrationBox`, `testNegotiation`, `testRegionNamedAtOnce`,
`testVillageAndTileNotices`), `ClassicMapViewerTest.testClickOnAVillage`,
`MoveTest.testRegionStopsFlag`.

### The founding father choice (`ClassicFathers`, `ClassicPedia`; build spec D8a, D8b's page)

GAME.TXT @WHICHFREEDOM as a list box, no portrait, centred: (42,59)
236x82 for its three prompt lines and five rows, one per offered father in
FreeCol's order (one per type, the original's), each "Peter Minuit
(Handels- Berater)" (NAMES @FATHERS, NAMES @FOUNDING with its hyphen and
blank, LABELS @MISC 102), rows at + 13, the gold footer, the bar on row 1.
FreeCol's fathers map to NAMES @FATHERS, PEDIA @FATHERn and the congress
figures CC-nn through an explicit table (`ClassicFathers.IDS`).

- **Keys.** The arrows move the bar, Enter takes the father under it (no
  message follows). **F1** opens the Colonopedia page of the father under
  the bar, 142 ms after the box goes (clip008: 0.13-0.16 s); any key or
  click closes it, the screen under it comes back in one paint, and the
  box comes again 264 ms later with the bar on row 1 (0.257-0.271 s; 3 of
  3 times). **Escape** closes the box with no father (G fixer, Roger's
  rule "Esc = Nein" but at the King's; I): the server offers the same
  choice again at the next turn start, the bells stay. A click beside the
  box does nothing.
- **The page** (`ClassicPedia.fatherPage`): WOODPANL.PIK, the gold header
  LABELS @MISC 107 "COLONIZATION-ENZYKLOPÄDIE" and "(Name: Gründerväter)"
  (PEDIA @PEDIA 5) at glyph tops 5 and 13, `x = (322 - w) div 2`; the
  entry's title at y 36, its text reflowed at `320 - 2 x0`
  (`x0 = (321 - @width) div 2`), 7 px apart, `^` a paragraph with one
  blank line, `{..}` gold across line ends, `%%` a percent, `$` the coin.
  PEDIA.TXT is read by `ClassicPedia` (its `@SMALLFONT` is a directive, `@;`
  ends a section). Without the pack's page the box simply comes back.
- **When.** FreeCol posts the offer before the turn start's notices; the
  original shows it after their price messages. So it waits
  (`pendingFathers`): the turn report asks the price messages, then the
  father box, then the other notices (`showMessagePopup`); a turn start
  without notices asks it just before the year flips: the turn flow's wipe
  asks `Host.holdTurnStart` first, which posts the box and holds the wipe
  until it is answered. Withheld (no box, no handler) after independence
  (Roger) and in the turn a father joined (the player's FOUNDING_FATHER
  history event of this turn: the original offers the next father a turn
  later; FreeCol offers it again then, while none is chosen).
- **Not yet.** The music (M10). A father chosen before independence still
  joins (a server rule, N10).
- **Recorder events:** `fathers-offer <ids>`, `fathers-due` (the hold),
  `fathers-withheld <why>`, `fathers-chosen <id>`.

**The join (`ClassicCongress`; build spec D8c, I5; clip008 #39296 -
#40109).** The trigger is our player's father set growing (the ids seen at
the view's build, `noteFathers`; `takeJoins`), not FreeCol's notice
`model.player.foundingFatherJoinedCongress`, which is never shown (its
message option could drop it). The sequence is the first thing of the turn
start, before the price messages and the emigration notice (F A3): the
turn report asks it first (`showMessagePopup`, any notice batch), a turn
start without notices in the turn flow's hold before the year flips
(`holdTurnStart`, posted once, `fathers-join-due`).
- **@FREEDOM** (GAME.TXT, `%STRING1` NAMES @NATIONALITY "Holl.",
  `{%STRING0}` the NAMES name in gold): (42,85) 236x30, no portrait,
  342 ms after it is asked (V: 24 frames after the indicator). Any key.
- **The congress hall**: a `ClassicWoodcut.Screen` of its own times on the
  woodcuts' queue (`putCongress` -> `ClassicAdvisorLayer.showWoodcut`):
  black 2 frames after the box went, CCBKGD.PIK 1 frame later with the
  fathers before him at their CC-nn anchors, back to front by the anchor's
  y (I), his figure dissolving in 3 frames after the hall over 55 frames
  (the woodcuts' `revealed` and fixed order), the arrow hidden from the
  hall to the dissolve's end and not dimmed (V: index 7 stays #AAAAAA),
  the water held, nothing marked; held until a fresh key or click.
- **Black 0.21 s** (15 frames), then **his page** (`ClassicPedia.fatherPage`)
  in the same entry (no map in between); a fresh key or click: the map in
  one paint. Without the hall's art or over another screen: his page alone
  (the prompter's), 0.21 s after @FREEDOM.
- The father offer of that turn stays withheld (`ClassicFathers.withheld`).
- Recorder: `father-joined <id>`, `hall-ask <id> before=[..]`,
  `hall-black/-frame/-done/-close/-palette/-page/-end congress_n`,
  `hall-skipped`; probe `congress_n:<black|frame|dissolve|held|closing|page>`.

**COLONIPÄDIE → Gründerväter (`ClassicGUI.showColopediaPanel`,
`ClassicPedia.typeList`; build spec D8b's menu part, I: no recording).** Two
list boxes of the father box's form (width 230, rows at + 13, no footer):
"Gründerväter" (PEDIA @PEDIA 5) with the five types "Handels- Berater" ...
(NAMES @FOUNDING + LABELS @MISC 102); Enter: "Gründerväter (Handels-
Berater)" with the type's five fathers by their NAMES names; Enter: his
page (142 ms later, as F1); a key: his list again, the bar on him (264 ms);
Escape (or a click beside) goes back a list, from the first one to the map.
A father's id (`showColopediaPanel("model.foundingFather.x")`) opens his
page alone. The menu row is live now (`ClassicMenuModel.NOOP_SEAMS` lost
`colopediaAction.fathers`); the other Colonopedia rows are still inert.

Tests: `ClassicFathersTest` (the table, the rows, the box, PEDIA.TXT, when
it is withheld, the turn start's order with Escape, a click beside, F1 and
the page, the hold before the year flips; the join sequence in its order and
at the hold; the Colonopedia menu; and the golden checks: the five
father boxes #12765, #13304, #48314, #49787, #50129 and the four pages
#40109 Minuit, #48645 Stuyvesant, #49256 Hudson, #49900 Drake, 0 px off),
`ClassicCongressTest` (the trigger, @FREEDOM, the hall back to front, its
screen, the lists; golden: @FREEDOM #39296, the empty hall #39719, the hall
with Minuit #39775, 0 px off apart from the arrow's 82 px),
`ClassicTurnFlowTest.testHeldTurnStart`, `ClassicAdvisorLayerTest.testF1AndThePage`,
`ClassicAdvisorLayerTest.testCongressHallTimeline`.

### The landing (`ClassicGUI.askLandfall`; build spec W8b, master plan W18)

The original's landing, as measured in the landfall clip and clip007
(landing-slow `02-landing.md`, clips-004-007 `clip007-01-deep.md` §5-§7) and
as Roger describes it: the unit that boarded first goes ashore; the others
are woken and offered one by one; Space keeps a unit aboard.

- **The question.** A ship ordered onto land asks FreeCol's landing question
  in one of two seams: `modalConfirmDialog` when one passenger can go ashore,
  `modalChoiceDialog` (one row per passenger and "Alle") when several can.
  Both put the original's box instead (`isLandfall`, `landfallRequest`):
  GAME.TXT `@LANDFALL` with the frontiersman, "Sollen wir an Land gehen, Eure
  Exzellenz, und die Schiffe zurücklassen?", the bar on row 1 "Bei den
  Schiffen bleiben" (`@default=1`), Escape and a click beside the box staying.
  Golden: the box and the portrait equal landfall #11157 and clip007 #5400,
  #6446 at 0 px, the bar on row 2 #11399 and #5518
  (`ClassicAdvisorBoxTest.testGoldenAgainstTheLandfallClip` builds those
  crops with `landfallRequest`). Without the pack: FreeCol's question with
  "Abbrechen" / "OK" in the same order.
- **When.** The box is on screen 6 frames after the move key when the
  frontiersman's palette goes in first (landfall #11151 -> #11157), 7
  without (clip007 #5393 -> #5400): `landfallShowAt` from the key's time
  (`ClassicMapViewer.moveKeyNanos`), and the layer loads the palette the
  3-frame lead before it, or what is left of it
  (`ClassicAdvisorBox.Request.showAtNanos`). FreeCol asks only after waking
  the passengers on the server, 10-50 ms after the key.
- **"An Land gehen"** sends exactly one passenger: FreeCol's first choice (or
  its one unit), the one aboard longest, whatever its kind (`firstLander`;
  the carrier's list grows at the end on boarding and keeps its order
  through a save). "Alle" is never the answer. Every other passenger asleep
  aboard is woken ("Wache" -> "Keine Befehle"), also one without moves
  (clip007 landing 3; FreeCol woke only those that can go). FreeCol's move
  sends the passenger in the ship's direction; its slide starts 110 ms after
  the box closed (`ClassicMapViewer.LANDING_SLIDE_MS`; the clips 86-128 ms,
  6-9 frames), and its offset 0 shows it instead of the ship. The ship keeps
  its moves; the landed unit has none (FreeCol's disembark).
- **"Bei den Schiffen bleiben"** (and Escape, and S, the sentry key: L3/Q1,
  Roger) lands nothing; the ship stays
  the active unit with its moves. The passengers FreeCol woke before the
  question stay awake (**I**: never chosen in a clip, Roger's question F2).
- **The panel** keeps the block it showed when the ship was ordered (the
  passengers still "Wache") from the box until the next unit's block
  (`ClassicInfoPanel.holdBlock`; clip007 #3040 -> #3107: only the minimap
  changes); a "stay" shows the new states at once. The cargo list is newest
  first, so the bottom entry is the one that lands (#5093, #6188, #6989).
- **The next unit.** When the controller re-selects the ship after the move
  (`landingDone`), the landed unit becomes the unit that has just made its
  last move (`ClassicMapViewer.finishedUnit`: no blink, no paint), and the
  next unit comes as a hand-over from it, 500 ms after its final draw: the
  next one of the unit cycle after the landed unit (W5f, above; clip007
  §3.6: ship, pioneer, soldier, farmer, scout fit all six hand-overs). So a
  woken passenger made after the landed one comes next, aboard (clip007
  #3107, landfall #13118), a land unit made after it before an older
  passenger aboard (clip007 landing 2: the farmer, #5804), and after the
  last passenger the empty start ship, before the older pioneer and soldier
  on land (#3697; F's rank "a carrier before what it carries" put the
  pioneer first with FreeCol's real start ids).
- **Passengers as active units.** `Unit.isActivePassenger` (common model):
  a passenger on a carrier on the map, active, with moves, no orders, is a
  candidate for the next active unit (`isCandidateForNextActiveUnit`), so
  the turn waits for it (W5a). `readyAndAble`, which goto and trade routes
  use, is unchanged. It is drawn instead of its ship with the stack marker,
  and its blink OFF shows the bare water (landfall #13140, clip007 #3130).
  A direction key onto land is FreeCol's ordinary move from a carrier: no
  box, all moves spent. Space keeps it aboard, skipped ("Keine Befehle");
  at the next turn FreeCol makes it active again, so it is offered again
  while the ship stays (I). A ship's move puts its passengers to sleep
  again (FreeCol, until Roger's F2).
- **Not built:** `@LANDFALL2` (a river) and `@LANDFIRST` (a foreign unit on
  the target: FreeCol plays the illegal-move sound only); a click on a
  passenger aboard (a map click selects the ship).
- Recorder events: `landfall carrier= unit= aboard= chosen= ashore|stay`,
  `handover after landing ...`, `handover after boarding ...`, `skip unit=`.

Live check (F2, `freecol-spike-results\f\f2`): three scripted scenarios built
from E1's west-edge save, in both topologies, reproduce the clips' sequences
(landfall L, clip007 landings 1-3 with both boardings) to the unit and the
frame window. Tests: `ClassicGUISeamTest` (`testLandfallBox`,
`testLandfallSendsTheLongestAboard`, `testLandingCycleAndBoarding`),
`ClassicAdvisorLayerTest.testABoxDueAtATime`,
`ClassicTurnFlowTest.testHandOverAfterBoardingAndSkip`,
`ClassicMapViewerTest` (the passenger asleep and awake, `finishedUnit`, the
skipped unit's blink, the held block, the cargo order),
`UnitTest.testActivePassenger`, `UnitTest.testActiveUnitCycleRestart`.

### Option boxes (`ClassicOptionBoxes`; build spec W14)

SPIEL row 0 opens "Spieloptionen festlegen" (GAME.TXT `@GAMEOPTIONS`, 8
rows), row 1 "Koloniebericht-Optionen festlegen" (`@COLONYOPTIONS`, 10
rows): checkbox advisor boxes (`Request.checks`, the `@checkbox` rule of
`ClassicAdvisorBox`). Measured on the landing-slow clip
(`01-options.md`, V) and confirmed by clips 006/007; no clip shows the
colony box, which follows the same rules (I).

- **Look (V).** @width 220: the box (47,56,226,88), the colony box
  (47,48,226,104). The title, then per row FONTTINY `]` (on) or `[` (off)
  in gold 149 at box + 9, a 2-px space, the text at box + 17 with its `~`
  letter or `{..}` words in gold. The bar on row 1 at every open. No OK row,
  no button.
- **Input.** A press on a row (bullet or text) puts the bar there, the
  release flips it: only the bullet's 3x3 inside changes (9 px, #1784 →
  #1785). The box stays. Escape, or a press outside (it removes the bar)
  and the release outside, closes it; the screen under it comes back exactly.
  Keys (I, never seen): Up/Down move the bar, Enter and Space flip the
  barred row, a row's gold letter (I F S E A C Y T; the colony rows mark
  none) flips that row.
- **Live (V).** Each flip acts at once, mid-turn: the classic prefs are read
  where they are used (the next slide, the next Spielzugende decision), the
  water cycle is switched in the same step (`ClassicWaterCycle.setEnabled`),
  the three FreeCol options (Autom. Sichern = `autosavePeriod` 0/1, or the
  period already set; Kampfanalyse = `guiShowPreCombat`; Tutortips =
  `guiShowTutorial`) are set in the client's options.
- **Kept (V for the next opening).** In `classic-options.properties`
  (`ClassicPrefs`): the classic prefs, and what the box set for the three
  FreeCol rows (`remember`), put back into the client's options at the next
  game view (`applyRemembered`), since FreeCol writes its own option file
  only from its own dialog.
- **The colony rows.** "Beschriftung an Gebäuden" (`buildingLabels`) shows
  the colony screen's production numbers on the buildings, "... an Waren
  und Terrain" (`goodsTerrainLabels`) those on the work tiles and under the
  net production (Roger: the original switches its numbers with them; ours
  are FreeCol's, which are right). The warehouse's amounts always show (I).
  The eight report rows hold back FreeCol's matching notices
  (`ClassicPrefs.REPORTS`, read off the server's messages and the
  original's texts of the same matter, I): trained (`unitEducated`,
  `noStudent`), food (`famineFeared`; a colonist who starves is always
  reported), raw materials (`noInput`, `notEnoughInput`), tools
  (`buildableNeedsGoods`), government (`badGovernment`,
  `veryBadGovernment`, `governmentImproved1/2`), new goods
  (`warehouseFull`, `warehouseOverfull`), Sons of Liberty (the colony's
  `soLIncrease`/`soLDecrease`), rebel majorities (`goodGovernment`,
  `veryGoodGovernment`, `lostGoodGovernment`, `lostVeryGoodGovernment`).
  Every other notice comes as FreeCol's own options say, the founding
  fathers among them. The filter is in `ClassicGUI.noticesShown`, the
  funnel of both notice seams.
- **Defaults.** The game box: Roger's state A (V). The colony box: all on
  (I).
- **Without the pack's GAME.TXT** the rows cannot be named: the row shows
  the "follows later" notice. Over a colony, Europe or report screen the
  stopgap popup lists the rows as `[x]`/`[ ]` and comes back after each
  flip.
- **Recorder events:** `box-open ... checks=XXooXXXX`, `box-toggle <id>
  row= on= bar= checks=`, `pref <key>=<value>`, `report-off <id>
  <key>=false`.

Tests: `ClassicOptionBoxesTest` (both boxes from a synthetic GAME.TXT: rows,
states, places, a flip setting its own key; the store, the file and the next
session; the FreeCol rows; the reports; with the pack: the gold letters,
the 9-px flip, and `testGoldenAgainstTheClips`, the box drawn from the
pack in each frame's states against 11 frames of landing-slow, clip006 and
clip007, 0 px off on the whole box apart from the original's arrow),
`ClassicAdvisorBoxTest.testCheckboxBar`, `testCheckboxRowText`,
`ClassicAdvisorLayerTest.testACheckboxBoxStaysUp`,
`ClassicGUISeamTest.testTheOptionRowsOpenTheBoxes`,
`testColonyReportsHeldBack`, `ClassicPrefsTest`, `ClassicMenuBarTest`.

### Woodcuts (`ClassicWoodcut`; master plan W9, N17)

The original's full-screen pictures at the great events, once per game:
the discovery of the New World (k = 1), the first colony (2), the first
meeting with the natives (3; the Aztecs 4, the Incas 5), the Pacific (6), the
first village entered (7), the Fountain of Youth (8), the first laden ship in
Europe (9), the first other Europeans (10), a burning colony (11), a
destroyed colony (12), a raid on a colony (13). k is the WOODCUT.TXT entry
and the WDCUT number (the landfall analysis numbers its woodcuts by
appearance: its "woodcut 2" is k = 3, its "woodcut 3" k = 7). Spec:
`g\prep\G5-spec.md`; report `g\G5.md`.

- **The picture (V, 0 px):** black over the whole screen; `WOODFRAM.SS.000`
  at (23,15); its opening filled with index 10 (#5D3824) over (63,40) 192x112
  (row 152 stays black); the ribbon at y 162, `NAMEPLAT.SS.000`, n x `.001`,
  `.002`, n = ceil(advance / 16), centred; the title (WOODCUT.TXT line k,
  `ClassicText.woodcuts`) in FONT-NP at y 165, x = (320 - advance + 1) / 2,
  values 1/2/3 = #755924/#61411C/#2C200C; `WDCUT<k>.SS.000` at (63,40) last
  (a 192x115 picture covers rows 153-154, a transparent pixel shows the fill).
  `ClassicWoodcutTest.testGoldenAgainstTheClips`: landfall #2118/#2175,
  #11611/#11670, #15431/#15489, clip008 #3381/#3442, #52688/#52745, fog-start
  #1053/#1110, the whole screen but the arrow's box, 0 px; the three analysis
  crops `02_`, `07_`, `13_*_1x` 0 px. The titles come from the pack only.
- **The timeline (V, six recordings):** black 57 ms after the trigger's paint
  (and never sooner than 57 ms after the map's last final draw); the frame,
  ribbon, title and fill 86 ms after the black, in one paint; the dissolve 43
  ms later, 55 frames from the first changed one to the last, evenly (about
  400 px a frame), on the timer's deadlines; held until a key; black at once,
  the game palette back a frame later (the water's step a frame after that),
  the map 300 ms after the black, painted from the current state. The water
  is frozen from the black to the palette's return (`ClassicWaterCycle.hold`).
  The arrow is hidden during the dissolve and drawn in the woodcut palette's
  grey (#797979 for #AAAAAA) from the black to the palette's return.
- **The dissolve:** the departure's fixed seeded order
  (`ClassicDeparture.dissolveOrder`): the original's is fixed too (the same in
  all six recordings, within a frame) but unknown, so the frames during the
  dissolve differ from it, the settled ones do not.
- **Keys:** any fresh key or click made after the picture is complete ends
  it, Escape too (a woodcut has no "Nein"); keys during the black or the
  dissolve and the auto-repeat of a held key do nothing; no timeout (I).
- **One queue with the boxes:** `ClassicAdvisorLayer.showWoodcut`, so every
  gate of the boxes holds (busy, focus, the blink's hold, the turn flow, the
  actions); probe `woodcut_<k>:<black|frame|dissolve|held|closing>` (scripts:
  `waitBox woodcut_1:held 15000`). The next box comes no earlier than the
  woodcut's follow-up after the map is back (W10's @LANDHO 71 ms, the chief
  171 ms, the village box 128 ms, the colony screen 328 ms, k = 9 57 ms;
  `ClassicAdvisorLayer.holdUntil`). A box asked while another box or woodcut
  is up waits for it, and its caller resumes only when its own box is done
  (`enter` re-enters its loop: the JDK ends a nested event loop about a second
  after the one below it exits).
- **The triggers** (`ClassicGUI`, "The woodcuts"): 1 the first final draw that
  shows land as explored (`ClassicMapViewer.noteReveal`, posted, due at once;
  then the W10 seam `discoveryShown`); 2 between `getNewColonyName` (the
  founding, `noteFounding`) and that colony's screen; 3/4/5 in
  `showFirstContactDialog` before the box; 6 `showEventPanel` with FreeCol's
  Pacific picture (on the square maps the server asks for the Pacific's
  naming for the first unit of any kind, ship, scout or colonist, whose move
  brings a Pacific tile into its own sight or enters one, whether the tile
  was explored or seen before or not: `ServerUnit.csCheckSightedPacific` with
  `broughtIntoSight`, part L, Roger 2026-10-08, opening_017 #46351-#46373: a
  seasoned scout's step on a map the cheat had revealed; before, only tiles
  newly explored or out of sight counted. Once per nation, M1, Roger
  2026-10-09: "Dieser Holzschnitt erscheint nur bei MEINER ersten
  Pazifikentdeckung", whether others were there before, which the player
  cannot know: a human nation whose first sighting comes after another
  nation discovered the Pacific gets the same request for the discovered
  region, once (`csSightedDiscoveredPacific`, `Player.pacificSighted` in the
  save), and its client shows the picture and names nothing
  (`InGameController.newRegionNameHandler`); FreeCol's isometric maps keep
  FreeCol's rule, the first discoverer only); 7 the key into a village (`villageEntryKey`, before the move), else
  before the village seams' boxes and the learn question; 8/9/11/12/13 the
  notice funnel (`showMessagePopup`, by message id, also for a dropped
  notice; 8 also before the Fountain of Youth's recruit box); 10 a CONTACT
  negotiation or FreeCol's meeting sound of a European nation. A woodcut is
  shown only when the map is what the player sees (not over a colony, Europe
  or report screen, the first scene, without the pack); then it is not marked
  and comes at the next trigger of its kind.
- **Once per game, across save/load:** `Player.classicWoodcuts` (bit k; in
  the save and for its owner only, only when not 0; an update never removes a
  bit). Marked on our player and, in single player, on the server's copy, so
  every later save has it (autosaves included). A save without it (an older
  build, the standard GUI) counts the woodcuts whose event left a trace as
  shown (`ClassicWoodcut.derived`: explored land, a colony or FOUND_COLONY,
  a met native nation, the Aztecs/Incas met, a Pacific we discovered or
  sighted (`pacificSighted`; not one another nation discovered), a visited
  village, goods sold in Europe, a met European, COLONY_DESTROYED; none for 8,
  11, 13). Multiplayer: this session and the derived bits only.
- **The recorder** puts the woodcut's palette (the index sheets of WOODFRAM,
  NAMEPLAT and WDCUT<k>, plus 94) into the frames' PLTE from the black and the
  entries of before back at the palette's return (`palette-woodcut`,
  `palette-restore`), and the map's index hint is off while a woodcut covers
  it. Live (fog-start replay, F3's scout, C4's founding, C3's natives):
  frame-only and finished screens 0 px, 0 palette misses during the woodcuts.
- Tests: `ClassicWoodcutTest`, `ClassicAdvisorLayerTest.testWoodcut*`,
  `ClassicGUISeamTest.testWoodcut*`, `testDerivedWoodcuts`,
  `ClassicMapViewerTest.testTheLandSightingAtTheFinalDraw`,
  `ClassicFrameRecorderTest.testTheWoodcutPaletteAndItsReturn`,
  `PlayerTest.testClassicWoodcuts`.

### The New World's name (master plan W10; I3)

- **At the first sighting** (`ClassicGUI.discoveryShown`, after woodcut 1's
  map is back): the @LANDHO box (`landHoRequest`), the admiral, GAME.TXT's
  text, its option row "Name:" as the label of a **name field**
  (`ClassicAdvisorBox.Field`) holding the nation's default from NAMES.TXT
  @COLONYNAME ("Neuholland"; GAME.TXT's `@default=America` is not used),
  selected. The box layer holds it to the woodcut's follow-up (71 ms, V
  landfall #2658 -> #2663), the admiral's palette in the lead. Nothing when
  the land has a name already (FreeCol's or a kept one).
- **The field** (V landfall #2664, clip008 #2789): 144 x 10, frame in the ink
  at box + (29, 12 + 6P), text at field + (3, 3), the selected default on the
  index-138 block from text - 1 over its advance + 1, 6 rows; the box is
  `6P + 31` high (43); no caret. Typed characters (`KEY_TYPED`,
  `ClassicAdvisorLayer.onTyped`, `Bar.type`): the first replaces the default
  (V clip008 #3134), the others are added; Backspace (I); letters with
  umlauts and ß, digits, space, `- . '` (I); at most 23 characters and never
  wider than the field (I). Enter takes the text (empty: the default),
  **Escape keeps the default** (Roger), a click does nothing (I). The answer
  is in `Request.field.answer()` (the bar that closed the box wrote it). The
  stopgap off the map shows the text and takes the default. D4's @COLONY
  prompt uses the same field.
  (Escape there founds nothing, see "Founding a colony".)
- **Kept** in `Player.classicLandName` (save and owner scope only, written
  only when set; an update never removes it; on the server's copy in single
  player, as the woodcuts). The panel's land line and the tips' `%STRING2`
  use it before the landing (`ClassicBands.landName`).
- **At the first landing** the server asks (`newLand.text` through
  `showNamingDialog`, `ClassicSeams.namesNewLand`): the kept name is sent at
  once, no box; the server's answer sets our player's `newLandName`
  (`declareIndependence` needs it; recorder `land-name-client` 1.5 s later).
  Without a kept name (a save from before I3 between sighting and landing)
  the @LANDHO box comes then. FreeCol's `buildColony.tutorial` is dropped
  (`withoutStartMessage`). Regions stay silent with their default names.
- **@TUTORIAL2** comes 0.457 s after the box's close with Tutortips on
  (V #3185 -> #3217): `landNamed` (W11, below).
- Recorder: `box-open LANDHO ... field=Neuholland`, `box-field`, `land-named`,
  `land-name-sent`, `land-name-client`.
- Tests: `ClassicAdvisorBoxTest.testNameField*` and its golden crop
  `03_LANDHO_naming` (0 px), `ClassicAdvisorLayerTest.testANameField`,
  `ClassicGUISeamTest.testLandHoAtTheSighting`, `testTheLandingSendsTheName`,
  `testRegionNamedAtOnce`, `PlayerTest.testClassicLandName`.

### Founding a colony (master plan D4; I6)

`ClassicFounding` (headless: the site warning, the name box, the times) and
`ClassicGUI.modalConfirmDialog` / `getNewColonyName` / `foundingWoodcut` /
`showColonyPanel`. V: clip008 `01-colony.md` section 1 (#1002, #2789-#4068).

- **Site warning.** FreeCol's `Tile.getBuildColonyWarnings` (a label of
  `warning.*` keys, asked only with its client option "colony warnings", on by
  default): a land-locked site is GAME.TXT's **@NOPORT** with the frontiersman,
  box (62,112,196,64), the bar on row 1 "Oh, daran hatte ich nicht gedacht."
  (V #1002), which founds nothing, as Escape and a click beside it do; only
  row 2 "Und das ist genau das, was ich vorhatte." founds. FreeCol's other
  warnings (little food, lumber or ore, land of ours, of Europeans, of natives)
  have no words in the original: answered "found" without a box (I; recorder
  `dialog-silent site warning ...`). Without GAME.TXT FreeCol's words as before.
- **Name.** GAME.TXT's **@COLONY** with the colonist (MSS5) and the W10 name
  field, box (71,113,178,37) (the section has no `@width`: the box is the
  field's, 29 + 144 + 5). The default is COLONY.TXT's (`ClassicText.colonyNames`)
  first name of our nation that no settlement in the game has, "New Amsterdam"
  for the Dutch (FreeCol's suggestion only without the file), selected; the first
  key replaces it. Enter founds with the text (empty: the default); **Escape
  founds nothing** (null to the controller: no move used, the unit stays up); a
  name some settlement has is FreeCol's notice `nameColony.notUnique` and founds
  nothing. Recorder `colony-name`.
- **Nothing used on a refusal:** `InGameController.buildColony` asks the warning
  and the name before it tells the server (tested from the source).
- **Timeline.** The box closes (#3340); the map without it until the colony,
  413.8 ms (29 frames) after the close, `getNewColonyName` holding (#3369); woodcut
  2 (first colony only) black 485.1 ms (34 frames) after the close and never sooner
  than one frame after the colony's paint (`blackAfterFounding`; live the
  server's round trip after the hold put the colony 48 ms late, recorder
  `founding-map`; without a box 72 ms after the paint); the woodcut as in
  "Woodcuts"; the colony screen built while the map shows and made visible
  328 ms after the map came back (#4009 -> #4032;
  before I6 it was built after the wait, ~130 ms late); @TUTORIAL4 514 ms later
  (I4). A later colony: the screen as soon as the colony is on the map (I).
- Tests: `ClassicFoundingTest` (warnings, COLONY.TXT, timeline, boxes; golden
  #1002, #2789, #3218 0 px, the arrow and the field's one stray pixel of #3218
  apart), `ClassicGUISeamTest.testTheFoundingAsTheOriginal`,
  `ClassicAdvisorLayerTest.testADeclinedSiteWarningHoldsNothing` (Roger's freeze
  report), `testWoodcutsOfTheColonyAndTheVillage`.

### The tutorial tips (master plan W11; I4)

`ClassicTips` (which tip, its values, its box) and `ClassicGUI`'s
"tutorial tips" section (`scheduleTip`, `runTips`, `showTip`, the timer
`adviceTimer`). Each tip once per game (`Player.classicTips`, bit k, the
classicWoodcuts pattern, as @TUTORIAL17), only with Tutortips on (the
options box's row = FreeCol's `model.option.guiShowTutorial`). FreeCol's own
`TUTORIAL` messages are dropped (`withoutStartMessage`).

| Tip | Advisor | Trigger | When |
|---|---|---|---|
| 2 | admiral | the @LANDHO box closed (`landNamed`) | +457 ms (V LF #3185 -> #3217) |
| 5 | admiral | the notice of FreeCol's `model.player.emigrate` / `autoRecruit` closed (`unrestClosed`; W8e's @UNREST later) | +457 ms (V @UNREST #18247 -> #18279) |
| 11 | admiral | an empty ship (no units, no goods) on the map | at the switch (below) |
| 13 | frontiersman | a pioneer on land | at the switch |
| 14 | soldier | a soldier on land | at the switch |
| 3 | frontiersman | before the first colony, a unit that can found one where it may (natives' land too), a resource on one of the 8 tiles around (not on its own: LF turn 8); `%STRING0` the goods ("Felle" for game and beaver) | at the switch |
| 4 | colonist, `@x=10` | the screen of a colony just founded opened (`colonyScreenShown`); `%STRING0` the colonist's goods, `%STRING1` the goods his tile gives him most of (I) | +514 ms (V c8 #4032 -> #4068) |
| 12 | colonist | **seam** `shipDocked` for D10 (FreeCol opens no screen on a docking) | +542 ms (V c8 #30022 -> #30059) |
| 17 | none | the first Europe screen (H3, `europeTip`, its own timer) | +557 ms |

- **At the switch:** the unit tips come 457 ms after the last change of the
  unit before (the turn flow's hand-over base, `Host.unitComing`), while the
  hand-over is pending; the turn flow holds its activation while the box is
  up and brings the unit with the close (V landfall 03 section 6: the block
  1 frame after the tip's close). A unit made active without a hand-over
  (a click, the view's first unit, the turn start, `bringNow`) gets its tip
  457 ms after it came up; a unit whose tip came at its switch gets none at
  its activation. Order when several fit: 11, 13, 14, 3; one per switch.
- **On time with another advisor:** a tip is asked `TIP_LEAD_NANOS` (the
  palette lead, 3 frames, plus one) before its moment with the box's
  `showAt`, so the layer loads the advisor's palette in the lead and the box
  appears at the moment (the first live run had @TUTORIAL13's box 75 ms late:
  the lead came after the moment).
- **Dropped, not marked** (it comes at its next trigger): a unit tip when
  another unit is up or coming by then, or the map is not what the player
  sees; a colony tip when its screen closed. A box up holds a tip (polled);
  a box that closed after the trigger puts it 457 ms after that close.
- `ClassicAdvisorBox.Builder.x`: GAME.TXT's `@x` moves the box only, the
  advisor stays over the centred box (V c8 #4068: box x 10, colonist x 130);
  only the tips set it (@TUTORIAL1's `@x` is not used).
- Over the colony screen @TUTORIAL4 is the stopgap popup (as @TUTORIAL17
  over Europe).
- Recorder: `tip-asked TUTORIALk in N ms`, `tip TUTORIALk`, `tip-dropped`.
- Tests: `ClassicTipsTest` (7), `ClassicGUISeamTest.testTheTipsAtTheirMoments`,
  `ClassicTurnFlowTest.testUnitComingAtTheSwitch`, the landfall golden crops
  04, 11, 12, 17, 19, 22 drawn from `ClassicTips.request` (0 px) and
  `ClassicAdvisorBoxTest.testColonyTipsAgainstClip008` (#4068, #30059: 0 px
  apart from the mouse arrow).

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
same grid as strip and panel, with the view origin in the top-left cell
instead of the half-tile-centred adaptive layout; `tileAt` follows. A
recentred unit sits in view column 7, row 6 (`ClassicHud.UNIT_COL/UNIT_ROW`),
clamped at the map's edges (see "The view rule").

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
| SPIEL | 0 `classic.gameOptions`ᶜ, 1 `classic.colonyOptions`ᶜ, 2 —, 3 —, 4 `saveAction`ⁿ, 5 `openAction`ⁿ, 6 `declareIndependenceAction`ⁿ, 7 `retireAction`, 8 `quitAction` |
| ANSICHT | 0 `toggleViewModeAction` (fires only in TERRAIN mode, key M), 1 `toggleViewModeAction` (fires only outside it, key V), 2 `europeAction`, 3 `findSettlementAction`ⁿ, 4 `zoomInAction`, 5 `zoomOutAction` (both disable themselves: `GUI.canZoomInMap` is false), 6-9 —, 10 —, 11 `centerAction` |
| BEFEHLE | 0 `clearOrdersAction`, 1 `waitAction`, 2 `fortifyAction`, 3 (second fortify line: hidden, context unknown), 4 `sentryAction`, 5/6 `buildColonyAction` (no colony / colony on the tile), 7 `clearForestAction` (forest), 8 `plowAction` (no forest), 9 `roadAction`, 10 `loadAction` (carriers), 11 `unloadAction` (carrier in a colony), 12 — (armed land units), 13/14 `gotoAction` (ship / land, the destination list), 15 `assignTradeRouteAction`ⁿ (carriers), 16 `returnToEuropeAction` (ships, key R), 17 `skipUnitAction`, 18 `unloadAction` (ship at sea: dumps cargo), 19 `disbandUnitAction` |
| BERICHTE | 0 —, 1 `reportReligionAction`, 2 `reportCongressAction`, 3 `reportLabourAction`, 4 `reportTradeAction`, 5 `reportColonyAction`, 6 `reportNavalAction`, 7 `reportForeignAction`, 8 `reportIndianAction`, 9 `reportHighScoresAction` (the hall of fame, not the live score: earlier README decision) |
| HANDEL | 0-2 `tradeRouteAction`ⁿ (FreeCol's one panel does all three) |
| COLONIPÄDIE | 0 `colopediaAction.goods`ⁿ, 1 `.units`ⁿ, 2 `.terrain`ⁿ, 3 —, 4 `.buildings`ⁿ, 5 `.fathers` (D8b, I5), 6 `.concepts`ⁿ, 7 — |

ⁿ = in `NOOP_SEAMS`: drawn in normal ink but **inert** until a classic screen
exists (`showSaveDialog`, `showLoadSaveFileDialog`,
`showDeclarationPanel`, `showFindSettlementPanel`,
`showTradeRoutePanel` are still the base `GUI` no-ops; `showColopediaPanel` has only the fathers, I5).
— = **no engine equivalent**, drawn like the original (normal ink) and always
inert: sound options, choose music, the four
zoom-level presets, show hidden terrain, F1 terrain information, pillage,
colonist skills, complete Colonipädie.  Zoom in/out are wired
but disable themselves; outside BEFEHLE that only makes them inert, not grey.
ᶜ = the Classic UI's own row (`ClassicMenuModel.GAME_OPTIONS`,
`COLONY_OPTIONS`): no FreeCol action; the strip's host gives it
(`ClassicGUI.classicAction`), and it opens the original's option box
("Option boxes" under "Advisor boxes"). FreeCol's `preferencesAction`, the
row's engine action before, opens a dialog the Classic UI has no seam for.
The preview harness, which has no such host, shows both rows inert.

**Visibility** (manual + 001): BEFEHLE lists only the orders that apply to
the active unit's kind and tile (`ClassicMenuModel.Context`: unit, naval,
carrier, armed, forest, colony on the tile, view mode); with no active unit
only 0, 1 and 17.  001 (a colonist without tools on a forest road, no
colony) gives exactly 0 1 2 4 | 5 7 9 | 14 | 17 | 19 with 7 and 9 greyed.
The other menus list every item (004 shows both view-mode items green).

### Interaction (`ClassicMenuStrip`)

- Mouse (001-005 were opened with the mouse: no bar; build spec W20): a
  press on a title opens its menu (or closes it if open); the bar never
  follows the pointer (landing-slow #335-#373 and clip006 #706-#791: no bar
  pixels while the pointer rests on or crosses the rows); a press on a
  normal-ink row bars it, a drag with the button held moves the bar (I),
  and the release over a row fires it (press-drag-release works too;
  landing-slow #384 -> #391, clip006 #833 -> #842); a press anywhere else
  removes the bar and is swallowed, and its release closes the menu
  (dago-colony #189 -> #194; a release back inside the box leaves it open
  without a bar, I). The drop layer covers the canvas while open. The
  recorder logs `menu-bar slot=n` when a press or a drag moves the bar.
  The stopgap `ClassicDialog` plates follow the same rule: no hover light,
  a left press marks a plate, the release on it takes it.
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
- A menu's open, close and bar moves are painted at once (`paintNow`:
  `paintImmediately` of the strip and the dropdown's area), as the boxes,
  the slide and the blink paint: a minimized window holds back
  `repaint()`, and the F1 recording showed the SPIEL menu 38 frames late
  and still beside the options box its row had opened.

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
| P | clearForest else plow |
| R | road (land units), else back to Europe (ships, `returnToEuropeAction`): the gold letter of both BEFEHLE rows, each action enabled only for its kind |
| M / V | toggleViewMode, only from TERRAIN / only from MOVE_UNITS; fired through `ClassicGUI.classicAction` (the strip and the keys): V is the player's own tile selection (`ClassicGUI.toggleView` -> `selectTile`, no unit brought), M is FreeCol's toggle (G fixer: FreeCol's V called the controller's fallback `changeView(Tile)`, whose cycle step activated the next unit) |
| E Z X C | europe, zoomIn, zoomOut, center |
| F2..F9 | Religion, Congress, Labour, Trade, Colony, Naval, Foreign, Indian |
| F10 | HighScores |
| Shift+F1 / F2 / F4 | Cargo / Exploration / Production (FreeCol's keys, no original entry) |
| Shift+F7 | **Military — moved off F7** (now Naval, as in the original): owner decision flagged |
| Ctrl+N | FreeCol's `newAction`: **back to the classic title** (asks first, `confirmStopGame`; then `showNewPanel` → `showMainPanel`). No original key: the original leaves a game only by retiring or quitting to DOS. Kept because dropping the `JMenuBar` had removed every in-game way back to the title or to a second game. |

**While the player waits** (the pauses before the end of turn, the next unit
or the turn's first unit, and the AI phase; `ClassicGUI.turnInputBlocked`)
every key of the table does nothing, as the map's own keys (build spec W5d;
logged `key-blocked`); only Ctrl+N still works there (`WHILE_WAITING`,
`waitAllows`; FINAL "Open" item 7).

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
- Minimap (build spec W16): 1 px per tile, unexplored black, explored ocean,
  high seas (and FreeCol's lake and great river) `0x202C8A`; a tile with a
  colony or unit in its owner's colour: the Europeans' fill (Holland
  `0xFF7100`), a tribe its NAMES.TXT `@TRIBES` colour (`indicatorRgb`:
  Araukaner villages 54 in every frame of landfall and clips 004-008, Sioux
  braves 118 in clip004); land by terrain, a forest in its base terrain's
  colour (`ClassicHud.MINIMAP_LAND_RGB`, measured over all matched frames of
  clips 004-008 and landfall): tundra/boreal 72 `0xBABA41`, desert/scrub 88
  `0xCFB28E`, plains/mixed 92 `0x867151`, prairie/broadleaf 75 `0x8A8E3C`,
  grassland/conifer 70 `0x1C6D10`, savannah/tropical 67 `0x75A64D`,
  marsh/wetland 58 `0x34499E`, swamp/rain forest 67, hills 89 `0xBAA27D`,
  mountains 108 `0xDBCFAE`; roads, rivers, plowing and resources do not
  change it. Inferred: boreal and swamp (by the pairing), savannah and marsh
  (map art only), arctic (in no clip: index 19 `0xE3E3E3`), six of the eight
  tribes (NAMES.TXT, same indices as the turn indicator). This also explains
  D6's four off-palette colours (FreeCol's one land green `0x24801F` and its
  Iroquois, Sioux and Apache colours).  Window: x origin 1 when the map is ≤ 58 wide (else
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
  shade: Holland FF7100/AA4900, England FF0000/AA0000, France 5555FF and
  Spain FFFF55 with 2/3 of it, unmeasured; a tribe its NAMES.TXT `@TRIBES`
  colour, W16: clip004 #5090 the Sioux brave's flag 920000, before
  FreeCol's 900000), then the sprite at cell + 3 (widths
  6/7/13) or + 2 (8/14), others centred (unmeasured).  Flag at the cell's
  top-left for sprites ≥ 13 wide (ships, mounted), else at
  `(sprite.x + w - 2, y + 7)`; galleon and frigate at cell + (9,0); the
  treasure, the artillery and the wagon train at cell + (6,0) (fill x
  249-253 for cell 242: clip005 #13993, #14364, #14481, #14594, clip006
  #9345; also on the map, which draws the same icon).
  Lines (build spec W21b): (260,70) `@INFO 0` + moves ("N k/3" for
  fractions: clip005 #14852 "2/3"); (260,77) `@INFO 1` + " (x, y)"
  (FreeCol tile coordinates: assumed mapping); (242,86) `@NATIONALITY` +
  `@UNIT` row (by role for colonists, by type otherwise); then 7 apart from
  y 93, gold: a treasure's "(" + `@CTITLE 1` + " " + amount + ")" (clip005
  #14594 "(Gold: 10000)"); the qualifier word (misc 4 "Experte", misc 64
  "Erfahren") when the unit has one and no tools (clip007 #3107, #6643,
  #1449), else the colonist's skill `@JOB` (also the hardy pioneer with
  tools: "Pionier", clip006 #5351); the tools "(" + n / tools word + ")"
  (007); the orders `@ORDERS`, or for a goto to a colony the colony's name
  (clip005 #14481 "Fur Town", the flag letter G); then green "(" + terrain
  + ")" and one line each for a river, a road, plowing and a resource, in
  the tile mode's order (clip005 #14481, clip006 #5351, clip007 #1449,
  clip008 #4009).
- A carrier with goods (W21b): "Mit:" (`@INFO 2`, green) 12 below the last
  line, one goods icon per hold from x 259 two rows above it, 1 px apart:
  ICONS.SS 022 + `@CARGO` row for a full hold, the grey 038 + row for a
  part one (clip006 #9345, clip008 #29463, #32928); the screen's edge cuts
  the last (clip005 #15391). FreeCol keeps no loading order: by goods
  type, full holds first (the original: the loading order, I).
- The colony on the tile (W21b, also in the tile mode): its name N green at
  x 262, 20 below the last line or 21 below a carrier's "Mit:"; its sprite
  at (242, N - 10) under the name, the flag recoloured (`colonyFlag`: the
  sprite's 11 `#4159A6` pixels the nation's fill, 4 `#34499E` its dark
  shade; France keeps the blue, V Quebec; the map's colonies the same);
  "Mit:" N + 15 with the warehouse's goods: storable goods, by `@CARGO`
  row, coloured from 100, at most five (I: which goods and in which order
  the original shows is in no rule the clips give); the list N + 26
  (clip005 #14364, #14852, #15391, clip006 #9345, clip008 #35590,
  dago-colony2 #4045).
- List (the passengers of a carrier, newest first, else the tile's other
  units -- also under a carrier with goods only, clip006 #9345): first
  sprite 10 below the last active line (or 11 below a "Mit:"), text at x
  260 from sprite.y + 4, all gold: a carrier with goods its icons from x
  260 on the cell's top row and only its orders 10 below the cell (clip006
  #9345 wagon, clip005 #15391 galleon); a treasure "Gold: 10000" (clip005
  #13993); a ship, the artillery or a wagon train its `@UNIT` name; else
  the veteran word (misc 64) for a veteran in a military role, the expert
  word (misc 4) + tool count for a unit in its own skill (hardy pioneer;
  scouts and missionaries assumed alike), else the bare tool count without
  the skill (clip007 #5093, #6884), else the skill of a colonist without a
  role or with a non-free-colonist skill; the tools word on its own line
  after a count; lines 7 apart; the orders (or a goto's colony, clip005
  #14827 "Schmied / Base") 6 below the last; the next sprite 8 below the
  orders (at least 18 below the sprite: assumed). An entry that would cross
  y 199 is left out and "+ Weiter +" (`@MISC 104`, green, x 242) stands
  where it would have gone, clipped at 199, only when an entry remains
  (clip005 #14852 185, clip006 #9345 191, clip005 #15391 196 under the
  Spielzugende word at 192). `ClassicHud.blockLayout` places all of it for
  the painters, the tile mode's word and the tests.
- Golden check (`ClassicHudTest.testPanelGoldenAgainstTheClips`, with
  `-Dclassic.clips` and the pack): 27 frames of clips 005-008 and
  dago-colony2 (the plan's twelve and fifteen more, two of them in the
  tile mode), drawn from hand-built facts with the pack's font, texts, wood
  and sprites, 0 px off in x 241-319, y 64-199 (the original's arrow
  excused).
- Removed: the order buttons, the Enter/Space/W footer, the Swing fonts, the
  `WOODPANL` chrome and the 240-px width.  `repaintInfo` hooks unchanged.

`ClassicHud.UnitFacts.of(Unit, sprite, icons)` turns a FreeCol unit into
plain facts (`@UNIT`/`@JOB` rows, qualifier, tools from the role's required
goods, orders from the unit state: sentry, fortify/fortified, improving →
plough/road, trade route, destination → goto, a goto's colony, a treasure's
gold, its tile's river, road, plowing and resource, the goods aboard one icon
per hold), `ColonyFacts.of(Colony, sprite, icons)` a colony; the painter never
touches the model, so the harness and the golden test build the captures'
facts by hand.

### Sprite aliases (corrected)

The panel draws `ICONS.SS` sprites 1:1, so the captures pin them down
(`tools/classic_assets/aliases.properties`, re-run `ant classic-assets`;
`ClassicPackAliasesTest.testThePackHasTheseAliases` fails when the pack is
older than the file): free colonist without a role 100 (was 058), free
colonist pioneer 073 (was 081), hardy pioneer as pioneer 101 (081), free
colonist soldier 074 (089), veteran soldier 102 (089), veteran dragoon 104
(076).  Build spec W21b (0 px against the clips): every colonist without a
role is 081 + its `@JOB` row (expert farmer 081, fur trapper 085, ore miner
087, blacksmith 095 seen; the others by the sheet's order), every pioneer but
the hardy pioneer 073 (was 081, the farmer's sprite), every soldier 074 and
missionary 077 (by the role row 073-077; I), the jesuit as missionary 105,
the artillery 009 (was 065). The units NAMES.TXT `@UNIT` gives an icon use
that icon minus one: the brave 109 (was 098, the statesman's sprite; clip004
#5090, 0 px), armed 110, mounted 111, armed and mounted 112, the king's
regulars 125 and cavalry 126 (were 115/116, native leaders' faces), the
continental army 128 and cavalry 129 (by the rule).  These also change the
map, colony and Europe screens — check them live.

H4 (R1, the verifier's rule): the four experts of a role are not 081 + row
without their equipment but 058 hardy pioneer (no tools), 059 veteran soldier
(no muskets; clip008 #4032/#42042 after he left his muskets in Base, clip005
#709 and dago-colony2 #3976 on the map, 0 px), 060 seasoned scout (no
horses), 061 jesuit (no cross) — the last three by their clothes; in their
role they keep 101-105.  The convert is 066 (the native in blue trousers, I;
the row's 108 is the totem pole).  A type's own picture (`getUnitTypeImage`,
the Europe and report lists) takes the type's default role, so it still shows
the equipped expert.  `ClassicPackAliasesTest.testEveryUnitAliasFollowsTheFrameTable`
checks every unit alias against the whole table, `testTheFramesAgainstTheClips`
the frames the clips show (skipped without `-Dclassic.clips` or the pack).
The tribes' flags, the minimap and the turn indicator all take the NAMES.TXT
`@TRIBES` colours (`ClassicHud.nationRgb`/`indicatorRgb`; FreeCol's swap
Sioux/Apache and Iroquois/Cherokee).

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
- Open / assumed: the minimap's arctic, swamp and boreal colours and horizontal
  scrolling; the position line's coordinate base; scrolling the list (only
  "+ Weiter +"); the colony's "Mit:" goods and order; the cargo's order;
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
- **The numbers** follow SPIEL "Koloniebericht-Optionen" (W14, read at each
  paint): "Beschriftung an Gebäuden" the production tags on the buildings,
  "Beschriftung an Waren und Terrain" those on the work tiles and the
  figures under the net production. The warehouse amounts always show.
  The figures are FreeCol's, a house improvement on the original's icon
  rows (Regeln-Auswahl).

**`BUILDING.SS` frame map (`BUILDING_FRAMES`, plan D0e).** The seven buildings
a new colony starts with are matched on the original's colony screen (clip008,
population 1): town hall 9, carpenter's 35, distiller's 27, weaver's 21,
tobacconist's 24, fur trader's 32, blacksmith's 39. Each house's two upgrades
are inferred as the next two frames (22/23, 25/26, 28/29, 33/34, 40/41; lumber
mill 36), church and cathedral as the other two 53×37 frames 37/38, walls and
harbour scenes by size and slot. The custom house and the armory chain draw no
sprite until a capture shows them (the old townHall 19 / customHouse 9 were
wrong: 9 is the town hall). Schools, chapel, press, storage and stables are
older guesses by look; the hover name keeps a mis-mapped sprite legible.

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

### The units standing in the colony (`ClassicColonyUnits`; clip 019, part M2)

How Roger wakes a soldier fortified in his colony (clip 019,
`wake-in-colony-analysis/10-spec.md`; playthrough-2 #45170 for a unit without
orders): the units standing on the colony tile (the original's "Vorhandene
Einheiten"; ours in the band's left panel until D5d builds that panel) are a
row of icons with their order flags, the letter black also for F (V #1290),
18 px apart (closer when more stand there than fit, so all stay clickable).
A **press** on one selects it (its frame; V: the white frame at #916), the
**release** on the same one opens its **@COLONYUNIT** box over the colony
screen (V: 0.6 s after the press, I: on the release), in the screen's own wood
box layer (`ClassicGUI.colonyBoxes`, the colony frame's glass pane, as the
Europe screen's; the map's keys and clicks are inert while it is up, and a
click on the map's window brings the colony screen back in front). The unit stays
selected after its box: a following click on a building or a work tile still
puts a colonist to work there (our click stand-in for the original's drag,
"Work assignment" above), now with the box in between.

- **The box (V, 0 px, `ClassicColonyUnitsTest.testGoldenAgainstTheClips`):**
  «Optionen für  {(Erfahrene Holzfäller)} (Dragoner):» -- `%STRING0` the
  NAMES @UNIT name of the role/type, `%STRING1` " (" + the @JOB expert name +
  ")", empty for a free colonist ("Optionen für  (Pioniere):") and a unit that
  is no colonist (I) -- no portrait; a *unit box*: the unit's icon (sprite,
  shadow, flag, black letter; V: the pioneer's shadow) at box + (5,6), the box 22 px wider, prompt,
  rows and the bar's left end 22 px right (box (31,76,258,48), prompt x 58,
  rows x 62, bar x 57-284; `ClassicAdvisorBox.UnitIcon`, `ICON_COLUMN`).
- **The rows** (@UNITOPTIONS without the ones that change nothing; V for the
  fortified dragoon and the pioneer, I for the rest): «Nach vorne bewegen.»
  not for the row's first unit, «Befehle aufheben.» not without orders,
  «Wache / An Bord gehen.» not on sentry, «Befestigen.» not when fortified
  or fortifying, «Keine Veränderungen.» always. The bar on the first row;
  Escape = «Keine Veränderungen.».
- **What they do:** «Befehle aufheben.» clears the orders
  (`InGameController.clearOrders`; a pioneer at work without FreeCol's
  question): the flag's F becomes "-" (V #1298); the unit stays where it is,
  selected, and does **not** come up on the map while the screen is open
  (V #1554: the unit that was up stays up; `ClassicGUI.colonyWaking` drops
  the controller's choice of it, it comes later in the cycle). With **no**
  unit up or coming (the end view after the turn's last unit, whose idle end
  the screen holds, or the Spielzugende mode) the freed unit comes as the
  controller's next unit once the screen is gone, at once, and the mode ends
  (I: the original is not seen; FreeCol's own colony panel asks for the next
  unit at its close too; `ClassicTurnFlow.unitFreed`; before, the turn stood
  still: no unit, no end, Enter refused, the review of part M). An idle end
  held by any screen while a unit became able to move asks the controller
  the same way («caught up»). «Wache» = sentry (S; boarding a ship in port is
  not built), «Befestigen.» = fortify (FreeCol: only with moves left),
  «Nach vorne bewegen.» = first in the row (I: the original's meaning is not
  seen; here only the screen's order, for the session).
- **The enemy on a work tile** (clip opening_018 #13962): an offensive unit of
  a player at war with us on one of the colony's tiles is drawn there at cell
  + (4,4) with his flag, fortified or not (`ClassicColonyPanel.occupier`).
- Tests: `ClassicColonyUnitsTest` (rows, names, box and place, the rows in
  the game with a fake controller, the row's order and step, the golden
  check), `ClassicWarTest.testTheFrenchSoldierAtOurColony`.
- Not yet (D5d/D6): the "Vorhandene Einheiten" panel and its buttons, the
  blinking white/green frame, the figures on the Festung picture, the
  original's 24 px cells.

## Europe screen (`ClassicEuropePanel`)

The original's home-port dock (design ref: the expert's `opening_009`–`013`).
Built exactly like the colony screen — painted into a virtual **320×200** canvas
and up-scaled by the largest integer factor that fits, nearest-neighbour, hosted
in its own `JFrame` (no `Canvas`). The original **`EUROPE.PIK`** harbour picture
(sky, sea, the wooden piers, the row of European town houses) is blitted as the
backdrop (loaded straight by its pack key `image.classic_original.pik.EUROPE.PIK`,
with a plain sea/sky fallback when the pack is absent); the live figures are drawn
over it. The original opens this screen without loading the picture's own
palette, so the converter decodes it under the game palette (W22p,
`PikDecoder.GAME_PALETTE_PIKS`): the sea under the piers and the market fill
show the game's blues 54-59, 0 px off the backdrop in clip005 #18653, clip006
#7980/#8120 and clip008 #17007, #52974, #53113 (`ClassicIndexGoldenTest`).
Layout:

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
reused `EuropeAction` (the **Europe** menu item, accelerator **E**), and by the
Classic UI itself only when a ship of ours arrives in Europe (W13,
"Voyages" in the map section; FreeCol's controller never calls
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
  build-queue picker, open for the expert. **Since the fixer of part J** (the review of part J; Roger's
  clip opening_015 #1683 -> #1686, V) the colonists on the dock marked "S" (FreeCol's SENTRY, which every
  land unit gets on the dock) board the ship by themselves first, as many as fit, in the dock's order
  (`ClassicEuropePanel.sail` / `boarders`, `InGameController.boardShip`; FreeCol's `moveAutoload` with
  `Unit.sentryPred`), and a colonist marked "-" stays; there is no question any more (before, FreeCol's
  "... und die Kolonisten zurücklassen?" box, `europePanel.leaveColonists`, came up with the bar on
  "Okay", and Enter left the colonist behind unseen). The original's @SAILAWAY box and the dock's
  option box ("Nicht aufs nächste Schiff gehen" = "-") are not built yet, so every colonist on our
  dock boards. Loading and selling keep the ship selected, so several goods types can be bought or sold in
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

> **Since W7 the stopgap only.** In the game every question, choice and
> notice is the original's advisor box in the canvas ("Advisor boxes",
> above); `ClassicDialog` and the selection list remain where the canvas is
> not what the player looks at: the title screens, a colony, Europe or
> report screen in front, a pack without FONTTINY, rows that do not fit.
> The seams below still describe what each answer means.

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
  remained, tied to the Q4 widgets: `showEmigrationDialog` (pick 1 of 3 recruits —
  a choice; since G1 a list box, "The silent seams" above) and
  `showNamingDialog` (name a colony/region — text input; still silent).

### Wired seams

- `showModelMessages(List<ModelMessage>)` — in-turn notices.
- `showReportTurnPanel(List<ModelMessage>)` — the end-of-turn batch.
  Each notice keeps FreeCol's own illustration for it
  (`ImageLibrary.getObjectImageIcon(game.getMessageDisplay(m))`).
- `modalConfirmDialog(Tile, StringTemplate, ImageIcon, …)` — replaces the plain
  `JOptionPane` stopgap. The `(…, Unit, …)` and `(…, FreeColObject, …)`
  overloads in `GUI` are `final` and delegate here, so every confirm in the game
  lands on this one override. A dismissed popup (Escape, close) answers no
  (W0e; until 2026-10-06 it fell back to `defaultOk`), and FreeCol's
  `highseas.text` is answered no without a box (W0f). FreeCol's question
  before every rumour, `exploreLostCityRumour.text`, is answered yes without a
  box (L3/Q2: the original has none, C32); its burial mounds question
  `exploreMoundsRumour.text` is the original's @LOSTCITY4 with the frontiersman
  (row 1 digs, row 2 and Escape leave them: nothing is touched, the mounds stay
  and the unit keeps its place and moves, Roger 2026-10-09 "Es ist, als hätte
  man nichts angetastet"; `InGameController.setDeclinedMoundsGo(false)` with
  the classic view switches off FreeCol's `declineMounds`, and a goto through
  them ends there; the original asks after the slide, V playthrough-2).
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
rather than leaving it dangling; since G1 a King's decision box resolves it
with its first row instead (the ring, "Nein danke"), never the party.

Of the remaining two event dialogs, `showEmigrationDialog` (choose 1 of 3
recruits) is a list box since G1 ("The silent seams" above); `showNamingDialog`
(name a colony/region) still needs the text field — the same Q4-blocked work as
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
    save). The thread stops as soon as the box closes or reopens. Enter, or a
    left press on a row and its release there, loads; Esc or a right press goes
    back. The wheel moves one row per notch (precise touchpad deltas are
    summed).
  - RUHMESHALLE → `showHighScoresPanel(null, HighScore.loadHighScores())`. It
    runs on the client side and works before any game.
  - NEUE WELT and loading catch any `RuntimeException`, show it and return to
    the title; otherwise the BUSY box (which ignores all input) would stay up
    forever.
  - Keys: Up/Down (and keypad), Home/End, Enter. PgUp/PgDn page in the load
    box and jump to the first/last item on the title. The pointer alone moves
    nothing (build spec W20, I: no clip shows the title with the mouse): a
    left press bars the item or row, its release over it fires; a release
    elsewhere fires nothing, and a press that skipped the intro, closed a
    notice or ended the chain cannot fire the item its release lands on.
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
    window (the next game view closes it, `installInGameHud`). It stops the
    map's edge-scroll timer (`ClassicMapViewer.dispose`), removes the menu bar
    and nulls `mapViewer`/`infoPanel`, so `reconnectGUI` rebuilds the HUD.
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
  - The in-game SPIEL items save and load are drawn as in the original but stay inert (they show the "follows later" notice): their classic seams (`showSaveDialog`, `showLoadSaveFileDialog`) are still no-ops (see "In-game HUD", `NOOP_SEAMS`). The two option rows open the original's boxes (W14, "Option boxes").
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
    for byte into the git-ignored `<pack>/text/` (they are copyrighted),
    and with them `MENU.TXT`, `OPENING.TXT`, `PATH.DAT`, `PEDIA.TXT` and
    `COLONY.TXT`.
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
- **Open items** (product questions, out of this step): the France/Spain
  banner positions come from the sprite headers, not from captures. Settled
  since (E3, "The rules" above): NEUE WELT plays the `levi` rules, the AI
  Europeans are the NAMES.TXT leaders, Spain's home port is Sevilla.

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
  HUD's grid (15×12 tiles of 16 px, the unit in column 7, row 6, clamped at
  the map's edges like the scene's own view, so 083's ship sits in column 11
  on both). Any part of the map area not over the live map viewer is
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
- **Open items:** the original's dismiss rule; the original's land
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
    first). Up/Down, Enter, a left press and release on a row; Esc again or a
    right press = Nein. Ja → `FreeColClient.quit()` — FreeCol's normal quit
    path (stop server, prune autosaves, `quitGUI`, `FreeCol.quit(0)`), the one
    FreeCol's own window listener uses when no game runs. The box is sized to its
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
  dismissed.` in the log. The script's `key` reaches the scene while it is
  up (`ClassicTestHarness.keyTarget`, `ClassicGUI.sceneOverlay`), also in a
  window minimized without activation, which has no focus owner; its
  `click` does not (it lands on the map viewer). Without a focus owner the
  title screens (the menu, the NEUE WELT chain) get the keys too
  (`ClassicGUI.currentTitlePanel`). Notices held meanwhile appear right after. A
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
  a ship east past the last drawn column or west past the first one (the
  Europe question; FreeCol's own `highseas.text` confirm is silent since W0f)
  do.
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
  indexed with the given palette (the launcher's default is the landfall clip's
  frame #0; design 10 §9.2 recommends fog-start #19, phase 0). RGB maps to
  indices exactly, a colour held twice (59/120, 56/121) takes the lowest index,
  a colour not in the palette its nearest entry (counted in `summary.txt`).
- **The water cycle (W6c).** With every HUD paint the recorder reads the map's
  terrain layer (`TerrainProbe`): the phase it is shown at and its index hint.
  The record palette's own phase of VICEROY's cycle is found once (`palette-file
  phase=`: fog-start #19 = 0, landfall #0 = 1); each frame's PLTE is the record
  palette with 120-127 rotated to the frame's phase, so the PNGs carry the
  original's palette at every step. A frame whose palette changed is a
  `paletteChanged=1` row with `paletteEntriesChanged=8` and gets a PNG even
  with no changed pixel, as ZmbvExtract wrote the clips (fog-start #7). The
  **index hint** (the layer's indices where they cycle) keeps the cycling
  pixels on their indices where their colour is that of a lower one (127 at
  phase 0 = 59's, 120 = 56's), so a palette step changes no pixel. A step with
  no cycling pixel on the screen still gets its palette frame.
- `timeline.csv`, one row per frame on an absolute 70.086303 Hz grid
  (the clips' strh rate), ZmbvExtract's columns and number format.
- `events.log`: `nanoTime,ms,frame,event,detail`. Events: `state` (the probe:
  turn, current player, view mode, active unit and moves, view origin, open
  dialogs; logged when it changes), `script*`, `key-post/press/release`,
  `click`, `move-key`, `move-done`, `pan`, `slide-start` (unit, tiles, view,
  cell, step and hold, `redraw=jump|hidden` when offset 0 comes first),
  `slide-step k/16` (offset k painted), `slide-end` (the hold is over),
  `slide-skip` (fog, or a classic pref), `final-draw`, `end-turn`,
  `dialog-open/close`, `menu-open/close`, `blink` (W3: `arm`, `rebase`,
  `off`/`on n=..`, `hold`, `stop`), `music-request`, `music-mode`, `pref`,
  `late`, `terrain` (W6e: `index ...` or `fallback ...`, whether the pack holds
  the palette indices), `oracle` (W6d: `server ...` or `unknown ...`, the fog
  ring at the game view's start), `palette-step` (W6c: `p= k= late= dur=
  via=post|wait` and `cells=c0,r0-c1,r1`, `final-draw`, `none` or `hidden`),
  `palette-start`, `palette-hold`, `palette-release`, `palette-pref`,
  `palette-file`. Reserved for the M1 items: `endturn-timer-start/fire` (W5),
  `music-fade`. Add a hook with
  `ClassicFrameRecorder.event(name, detail)`; guard a costly detail with
  `ClassicFrameRecorder.on()`. `ClassicFrameRecorder.note(key, value)` logs
  an event that is also a `key: value` line of `summary.txt`.
- `summary.txt`: frames, PNGs, late ticks, palette misses, paint cost, the
  water cycle (`paletteSteps:` count, lateness and the steps' own paint cost,
  Critic 7; `paletteFrames:` the frames whose palette changed, palette-only
  ones and the record palette's phase; `hintPixels:` the pixels the hint
  resolved), the notes (`terrain: ...`, `oracle: ...`).

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
the HUD painting untouched, to time the game itself: then the sampler only
reads the state probe every 50 ms (`EVENTS_PROBE_MS`), with sleeps that never
raise the Windows timer resolution, so nothing of the recorder's keeps the
game's timers on time.

Timing on Windows: `LockSupport.parkNanos` wakes on the 15.6 ms system tick
(measured up to 15 ms late); `Thread.sleep` keeps to ~1 ms -- but HotSpot
raises the timer resolution only for a sleep that is not a multiple of 10 ms
(`HighResolutionInterval`), and a sleep of 10, 20 ... ms wakes on the tick
unless another thread of the process holds the resolution up (in the M1 runs
the recorder's sampler always did). So `waitUntil` sleeps to ~1.5 ms before
the deadline, one millisecond less when that is a multiple of 10
(`sleepMillis`), and spins the rest (~11 % of a core for the sampler); 2-3
late ticks per 33 s run remain. Measured in one JVM with nothing else
(`freecol-spike-results\c\tools\C3Sleep.java`, 400 waits each): the old rule
woke late in 25 (> 5 ms in 17, at most 12.4 ms), the new one never (at most
0.44 ms). Schedule slides and blinks the same way, not with `parkNanos`.

**Input script** (`-Dfreecol.classic.script=<file>`, format in
`ClassicScript`'s class comment): `wait <ms>`, `key <KeyStroke>` (e.g. `LEFT`,
`NUMPAD7`, `ENTER`, `alt G`), `click <x> <y>` (canvas pixels), `sclick <x> <y> [shift]` (the open Europe screen's own 320x200 canvas, also minimized; `shift`: with Shift held, B1),
`tclick <x> <y>` (map tile (x, y) as the map shows it now: its cell's centre
on the canvas, an error out of the view; J2), `waitGame`,
`waitIdle`, `waitTurn [timeoutMs [key]]` (with a key, every box on screen
meanwhile gets it once: the turn start's notices; the Spielzugende mode counts
as having the controls), `markTurn` (the next `waitTurn` waits for a turn after
the marked one: before a key that ends the turn at once, Enter in the mode,
whose AI phase can be over before `waitTurn` starts; J2), `pref <name> on|off` (the classic
prefs below, or `autoSave`/`combatAnalysis`/`tutorTips`, which set FreeCol's
own options), `goto <x> <y>` (the active unit gets FreeCol's goto order to that
tile, `InGameController.goToTile`; W5f's goto units), `waitBox <prefix>
[timeoutMs [key]]` (until an advisor box whose probe id starts with the
prefix is on screen, e.g. `WHICHFREEDOM`, `pedia`, `CHIEF`; with a key, that
key answers every other box that comes first, as the turn start's notices
before the father box), `log <text>`, `quit`. It
runs on its own thread. A key is a
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
`guiShowTutorial`); what the options box set for them is remembered in the
same file. The ten colony report rows (`buildingLabels` ...
`reportRebelMajority`) default on. Read a pref where it is used, not once at
start. Read: `moveAccelerator` (each slide start), `showNativeMoves` and
`showEuropeanMoves` (each foreign move, W2), `waterCycling` (each due step of
the water cycle, W6c; the box also switches it at once), `endTurnPrompt` (the
idle decision, W5/W17), the two label rows (each colony screen paint), the
report rows (each notice). The SPIEL option boxes set them all (W14, "Option
boxes"); the script's `pref` command does too.

The sandbox launcher, the copied tools and today's baseline are outside the
repo, in `C:\Users\koch_\freecol-spike-results\m1` (`W1.md`).
