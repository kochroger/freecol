# UI phases — the `ClassicGUI` build-out

Our own work: an original-style view on the unchanged engine. Tracks *remaining* work — how the
shipped code works lives in
[`src/net/sf/freecol/client/gui/classic/README.md`](../src/net/sf/freecol/client/gui/classic/README.md).

**Expert player drives:** screen priority, validating each rebuilt screen vs. the real game, sign-off
per slice. Reference screenshots live in git-excluded `screenshots/` (never commit; `git add` explicit
paths). Sub-states matter as much as idle layouts (open menus, in-progress drag, mid-dialog).

## Open questions for the expert

What is blocked on him rather than on more code: **reference material only he has**, a **guess we
shipped** that needs sign-off, or a **call we cannot make from the repo**. The detail lives in the
phase item linked; this list is just the queue to take to him.

| # | Question | Blocks | Cost of guessing wrong |
|---|---|---|---|
| **Q2** | **The per-view Colony Advisor paging key for >9 colonies** — the *between-views* cycle is now confirmed (F6 again; see Resolved below), but the shots' two-page military view (9 colonies, then a 10th on its own page) doesn't show what pages *within* one view. Left un-implemented (rows past the ninth are just clipped) pending this. | [Colony Advisor](../src/net/sf/freecol/client/gui/classic/README.md#colony-advisor-classicreportcolonypanel-f6--the-paged-report) | Shipped and wrong: a key nobody would press, for a case only a long game hits. |
| **Q3** | **The popup look** — `ClassicDialog`'s metrics and green-on-wood palette are read off screenshots by eye, not measured from the art. `WOODPAN2.PIK` is unused; should it be? | [Phase 3 §2](#remaining-1) | Every popup in the game inherits the error. |
| **Q4** | **Reference shots for the choice-list and text-input popups**, whose original look we have never seen. | [Phase 3 §1](#remaining-1) | Cannot start; they stay Swing stopgaps. |
| **Q5** | **The colony screen's fixed building ground-slots** — the original places buildings at set positions; we flow-layout them. | [Phase 2 §3](#remaining) | Rework of the biggest remaining slice. |
| **Q6** | **Four shipped reports — Military, Production, Exploration, Cargo — have no confirmed Col1 counterpart** at all (see Resolved below); "Military Garrison" may just be the Colony Advisor's own paging, not a standalone report. **Concretely blocking now:** remapping Naval to its confirmed key F7 collides with Military, which already sits there and which this plan does not touch unilaterally — both menu items show "F7" today but only one responds (verified live). Should any of the four be reworked, re-keyed, or removed? | Key-scheme remap (`ClassicGUI.remapClassicReportAccelerators`); README "Report screens" | Guessing wrong either reworks a report the original never had, or leaves a live, user-visible key collision unresolved. |

**All five go to him together, in one sitting** — see the
[milestone note](#milestone-ready-for-the-experts-first-playtest) below for why this is now a bundled
hand-off (build + every open question at once) rather than asking Q4 separately/early.

*Resolved* (detail in the README, not repeated here):
- Whether Education/History/Requirements/Labour belong in a faithful classic UI — no for the first
  three (deferred to [optional reports](optional-reports.md), 2026-07-17); **Labour reversed** —
  the original does have it, at F4 (2026-07-24).
- **Q1, Foreign Affairs** (2026-07-24) — key F8, backdrop `REPORT8.PIK` (also double-booked with
  Exploration → feeds Q6), full field/layout spec, all powers listed regardless of contact, own
  nation last, no sub-states. Built as `ClassicReportForeignAffairPanel`.
- **Q2, the *between-views* Colony Advisor paging key** (2026-07-24) — confirmed **F6 again**, not
  Left/Right/Space. The *within-a-view* key for >9 colonies remains open (table above).
- **The observed F-key scheme** (2026-07-24) — remapped in `ClassicGUI` at runtime, in-memory only;
  see README "Report screens".
- **Q7, land-land tile borders** (2026-08-05) — no border sprite sheet exists to source, so fixed with
  a procedural dithered edge-blend at render time (`ClassicMapViewer.blendLandBorders`), not more asset
  RE. Verified live against `screenshots/ui-square-tiles-bug.png`/`ui-square-tiles-fixed.png`; see
  [land-tile-borders.md](land-tile-borders.md) and the README's "`ClassicMapViewer`" section.

## Milestone: ready for the expert's first playtest

**Order of operations, decided 2026-07-25: finish everything *not* blocked on the expert first, then do
one bundled hand-off** — the finished build plus all five open questions, together, in a single sitting
— rather than trickling questions to him early or sharing an intermediate build. Nothing here is gated
on his answers, so there's no reason to wait on him to get it done, and it means his first look at the
build is the most finished one we can give him.

### Before sharing — the unblocked queue

None of these need an answer from the expert to build; they're just not done yet. Doing them first
means the questions we do ask are the real remaining unknowns, not things we simply hadn't gotten to.
**Two items are already done** — HUD caption localization and the wood-framed menu dropdowns (detail
in the README, not repeated here) — leaving:

1. ~~**F10 "Kolonisationspunkte" (score breakdown)**~~ — done 2026-07-26, see [Phase 2 §2](#remaining)
   and the package README's "Report screens" section (`ClassicReportHighScoresPanel`).
2. **Per-nation building/flag tints** — Phase 2/Europe follow-ups. Not one of Q2–Q6; just unbuilt.
3. **Phase 4 pre-game/setup screens** — nation select (`NATIONS.PIK`), difficulty (`DIFFICUL.PIK`),
   customise (`CUSTOMIZ.PIK`); all art already extracted, none of it blocked. Replacing the auto-launch
   stopgap here means his first playtest can start a *new* game through the classic UI instead of
   resuming a save — closer to how he'd actually experience the real thing.

### Then — one hand-off: the build, and all five questions together

Once the queue above is clear, give him the build and ask him to:

1. **Play a real session**, not a click-through — start a new game (now reachable, per item 3 above),
   found a colony, run it a few turns, take a ship to Europe and back with cargo, let a random event or
   two fire — noting first impressions as he goes, the same sub-states-matter bar this doc already holds
   work to (open menus, mid-drag, mid-dialog).
2. **Answer Q2–Q6 in the same sitting** — Q4 (the choice/text-input popup shots) no longer jumps the
   queue; by this point the build is as finished as it can get without his input, so every question goes
   over at once instead of in a separate earlier ask.

**What stays open no matter how much of the queue above gets done** — genuinely blocked on him, not
just unstarted: the recruit/purchase/train/set-sail choice dialogs stay plain `JOptionPane` stopgaps
until [Q4](#open-questions-for-the-expert) lands (Phase 3 §1); the colony's buildings stay flow-laid-out
rather than at the original's fixed ground-slots until [Q5](#open-questions-for-the-expert); the Colony
Advisor's >9-colony paging ([Q2](#open-questions-for-the-expert)) and the Military/Naval F7 key collision
([Q6](#open-questions-for-the-expert)) stay as they are. These are exactly what the bundled hand-off is
for — no point guessing at them again solo.

## Phase 0 — scaffold & launch ✅

`ClassicGUI extends GUI`, `--classic` flag, selector, auto-launch lobby stopgap.
(README "What this is".)

## Phase 1 — the map ✅

Rectangular 48px grid: `TERRAIN.SS` terrain, `PHYS0.SS` feature overlays + coast/beach feathering,
`ICONS.SS` unit/goods/settlement sprites, edge-pan, minimap, clicks/keys driving the real
`InGameController`, and **turn controls** (Enter/Space/W/B, the 1994 key scheme).
(README "`ClassicMapViewer`".) Frame maps + RE writeups live in `ClassicTileArt`,
`aliases.properties`, and the README. **Gap found 2026-07-27, fixed 2026-08-05:** coast
feathering blends ocean↔land, but two adjacent land tiles of different type never blended —
bug + fix + acceptance criteria in [land-tile-borders.md](land-tile-borders.md) (see also
[Q7, Resolved](#open-questions-for-the-expert) and the README's "`ClassicMapViewer`" section).

## Phase 2 — HUD & core screens 🔨

**Shipped:** map HUD (reused `InGameMenuBar` + `ClassicInfoPanel`); **colony screen**
(`ClassicColonyPanel`, `COLONY.PIK`/`BUILDING.SS`/`WOODTILE.SS`); **Europe screen**
(`ClassicEuropePanel`, `EUROPE.PIK`, recruit/purchase/train via the real controllers); **all twelve
advisor reports** — the original's ten plus Labour, a reversed decision (see "Remaining" below) — over
a shared `ClassicReportPanel` frame (unit rosters share a further `ClassicReportRosterPanel`): Colony
(`REPORT6.PIK`), Military (`REPORT6`), Naval (`REPORT7`), Trade (`REPORT5`), Religious (`REPORT2`),
Production (`REPORT4`), Congress (`REPORT3`), Exploration (`REPORT8`), Cargo (`REPORT7`), Indian
(`REPORT1`), **Labour** (`REPORT4`, new), **Foreign Affairs** (`REPORT8`, new — the last report the
original actually has, finishing the set). Keys now follow the *observed* original F-key scheme
(F2 Religious, F3 Congress, F4 Labour, F5 Trade, F6 Colony, F7 Naval, F8 Foreign Affairs, F9 Indian),
remapped at runtime in `ClassicGUI` rather than FreeCol's own arbitrary layout — see
["Report screens"](../src/net/sf/freecol/client/gui/classic/README.md#report-screens-classicreportpanel--concrete-reports)
in the README for the mechanism and the one confirmed, still-open key collision (F7: Naval vs.
Military — [Q6](#open-questions-for-the-expert)). Also: **order buttons** (reuse `FreeColAction`
`BUTTON_IMAGE` art), **in-panel minimap**, **unit portrait**, **seam-free `WOODPANL.PIK` wood chrome**,
Col1's light-on-dark menu bar, and localized report captions. (README "Phase 2 HUD" / "Colony screen" /
"Europe screen" / "Report screens".)

> **Key enabler:** `ClassicGUI.updateActions()` (called on `reconnectGUI` + every `changeView`)
> refreshes the reused actions' enabled state — the classic HUD has no `Canvas`, so without it the
> Europe/report menu items and the order buttons stay stuck disabled.

The report set (Foreign Affairs, Labour, and the key-scheme remap) is now **done** — full detail lives
in the README's "Report screens" section, not here. What's left:

### Remaining

1. **Colony Advisor: the within-a-view paging key for >9 colonies** — open, see
   [Q2](#open-questions-for-the-expert). (The between-views cycle, F6-again, is done.) Colony rows
   stay clickable (Colony/Production/Religious → jump to that colony's screen); the Congress and
   Indian row loops still haven't been exercised with real data — see the README's verification
   caveat.
2. ~~**F10 "Kolonisationspunkte" (score breakdown)**~~ ✅ — done 2026-07-26. FreeCol's matching seam,
   `showHighScoresPanel(String, List<HighScore>)`, differs in shape from every other report (takes its
   data as arguments) and has **no accelerator at all** in FreeCol's own scheme, so F10 was uncontested.
   Built as `ClassicReportHighScoresPanel`; see the package README's "Report screens" section for the
   layout and the open backdrop/field-set guesses.
3. **Screen-interaction leftovers.** The build queue, Europe boarding, colony drag-interaction (work
   assignment), and Europe cargo/set-sail are all done — see the README's "Colony screen" / "Europe
   screen" / "Work assignment (drag interaction)" / "Cargo & set sail" sections. Left: *Colony:*
   validate the provisional `BUILDING.SS` frame map, and place buildings at the original fixed
   ground-slots (vs. our flow layout — [Q5](#open-questions-for-the-expert)). *Both:* per-nation
   building/flag tints.

## Phase 3 — dialogs & polish 🔨

**Shipped:** the shared **`ClassicDialog`** component — the wood-framed popup (`ClassicWood` grain +
illustration + green-on-wood text + option plates) that the plan called for building *once*, with
every popup routing through it. Live-verified 2026-07-17 (README "Popups"). Over it:

- **The message channel** — `showModelMessages` + `showReportTurnPanel`. Until now the classic UI
  **silently discarded every in-game notice**, and the root cause was worse than the missing
  overrides: `invokeNowOrLater`/`invokeNowOrWait` are no-ops in the base `GUI` and were never
  overridden, so the controllers' display tasks never ran at all. Both seams now mirror `SwingGUI`.
  The original has no batched turn report, so notices page one at a time rather than list.
- **`modalConfirmDialog`** — off `JOptionPane` onto the shared frame. The `Unit`/`FreeColObject`
  overloads in `GUI` are `final` and delegate here, so every confirm in the game lands on it.
- **`showErrorPanel`** — the no-op seam audit's find (see "Done, not repeated here" below), same silent-loss class as the
  messages: all five overloads funnel into one non-final seam that no-op'd, so **every error
  vanished**, and errors carrying a `System.exit` callback left the app hung. Now routes through the
  shared popup and runs the callback on dismiss. Live-verified 2026-07-18 (README "Wired seams").
- **The three event confirm dialogs** — `showMonarchDialog` (king's demands), `showFirstContactDialog`
  (meeting natives), `showNativeDemandDialog` (native tribute). Also silent-loss bugs the audit
  surfaced, not just missing screens: each hands the player's yes/no back over the wire, so a no-op
  dropped the exchange (a tax hike accepted by omission). These seams are *async*
  (`DialogHandler<Boolean>`); a shared `askEvent` helper mirrors the matching FreeCol dialog's
  message/labels/icon over the popup and fires the handler. Monarch tax dialog live-verified
  2026-07-18 (README "Event confirm dialogs").

### Remaining

1. **The choice / input dialogs — blocked on [Q4](#open-questions-for-the-expert).** What is left of
   the dialog seams needs a **list widget** (`modalChoiceDialog`, and `showEmigrationDialog` — choose
   1 of 3 recruits) or a **text field** (`modalInputDialog`, and `showNamingDialog` — name a
   colony/region), both still plain Swing stopgaps whose original look we have never seen. The
   confirm-shaped popups these used to be grouped with (meeting natives, king's demands, native
   tribute) are **done** — see Shipped. Learn-skill and lost-city-rumour choosers ride on the list
   widget too. Negotiation (`DiplomacyPanel`) and the end-turn "units still active" prompt are the
   larger bespoke ones.
2. **The popup look is a guess — see [Q3](#open-questions-for-the-expert).** `ClassicDialog`'s
   metrics and green-on-wood palette are read off the original's screenshots by eye, not measured
   from the art (the menu dropdowns now share this same palette, see README "Menu dropdowns", so
   this sign-off now covers both). Every popup inherits whatever is wrong, so this wants sign-off
   before the seams above multiply it. `WOODPAN2.PIK` is not used yet.

Done, not repeated here: the menu dropdowns (README "Menu dropdowns"), and the no-op seam audit that
found `showErrorPanel` and the three confirm-shaped event dialogs (README "The no-op seam audit") —
its only leftover, the two non-confirm event dialogs, is tracked in item 1 above.

## Phase 4 — pre-game & setup screens ⬜

No classic lobby yet — `showStartGamePanel` auto-launches single-player as a stopgap (why `--fast`
drops straight into the map, no nation/difficulty choice). Each setup screen is a `showXPanel`-style
override + its `.PIK` background: **nation select** (`NATIONS.PIK`), **difficulty** (`DIFFICUL.PIK`),
**customise** (`CUSTOMIZ.PIK`); the **declare-independence** (`DECLARAT`/`DECOIND.PIK`) and **king**
(`KINGLSS1/2.PIK`) art sits adjacent (some overlaps Phase 3 event dialogs). Lower urgency than the
in-game screens — the auto-launch keeps the game reachable.

## Explicitly out of scope (deliberate)

The intro/new-game **cinematics** and closing sequence. `--no-intro` skips them; only the static
title picture `OPENMENU.PIK` renders, with the original main menu over it (`ClassicMainMenuPanel`,
see the classic README; `OPENING.PIK` is a 960px sea chart, not the title). The animated frames (`LEVN0001–0010`, `CLOS-BKG`/`CCBKGD`) are extracted
but nothing plays them — revisit only if the expert deems the original intro essential (a fresh scope
decision, akin to A6 audio).
