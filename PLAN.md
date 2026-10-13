# Wil's Canvas fork: the plan

This fork of [j-raghavan/sn-canvas](https://github.com/j-raghavan/sn-canvas) is a personal build of the
Canvas plugin, tuned for mind maps on a **Supernote Manta** (firmware 3.29.45 beta). It is not meant to
go back upstream, but the developer's updates are still merged in, so changes stay in their own files
where possible and edits to the developer's files stay small.

## The one rule

**Snappiness comes first.** After every feature, re-run the speed test (below) and compare with the last
run. If anything got slower, fix it before moving on.

## How to work on it

- **Who builds:** Wil, working with Claude Code. Explain every change in depth: what it does, why it is
  written that way, and what would break without it. Prefer letting Wil try, break and fix things over
  handing over finished pieces.
- **Building:** GitHub Actions does the build (the cloud Claude Code environment cannot download the
  Android SDK). Actions → Release → Run workflow, with a version **above the developer's** (they are on
  1.0.x; this fork uses 1.1.0 and up). The `.snplg` lands on the fork's Releases page.
- **Installing:** download the `.snplg` on the phone, upload it into `MyStyle` with Supernote's
  **Browse & Access** (swipe down on the Manta), then Settings → Apps → Plugins → Choose Installation
  Package. Losing saved canvases is fine.
- **CI:** every push runs lint, the JS tests, the Kotlin unit tests (which also prove the native code
  compiles) and a full build. The build now fails loudly if the native part does not compile, instead of
  quietly packaging a plugin without it.

## Measuring speed

1. Open Canvas on a **new, empty** canvas.
2. **Hold the zoom pill** (bottom left) for 1.5 seconds. The speed test map appears: 100 nodes, 99 bound
   arrows and 200 scribbled words, 399 elements, always identical (`PerfTestCanvas.kt`). On a canvas with
   anything on it, the hold does nothing.
3. Do the same things each time: pan around, zoom, move a node, erase a few words, drag out a shape.
4. Close Canvas, then open `MyStyle/SnCanvas/perf-log.csv` with Browse & Access.

Each line of the log is one frame (`PerfLog.kt`):

| column | meaning |
|---|---|
| `time_ms` | when it was drawn |
| `frame` | `full` (everything redrawn), `pan` (the pan snapshot slid along), `ink` (a live pencil stroke the canvas drew itself), or `mark:...` lines between runs |
| `gesture` | what was going on: `Move`, `Erase`, `Pan`, `DrawShape`, `none`... |
| `elements` | how many elements the canvas held |
| `draw_ms` | how long `onDraw` took |
| `gap_ms` | time since the previous frame; blank after a pause |

`gap_ms` matters as much as `draw_ms`: the view is hardware accelerated, so `onDraw` only records what to
draw and the screen is painted afterwards on another thread. Slow painting shows up as long gaps. The
firmware inks the pencil itself, so normal writing does not appear in the log at all.

## Build order

1. **Speed.** Measure first (done: the log and the test map). Then: cache the finished drawing as a bitmap
   and draw only what is changing on top (the developer's own TODO in `CanvasView.kt` says the same, and
   panning already works this way via `PanPreview`); skip elements that are off screen; keep each shape's
   hand-drawn outline instead of rebuilding it every frame. Done when moving and erasing feel instant on
   the test map.
2. **Clean refresh after panning.** One full refresh when a pan ends, to clear the e-ink ghosting.
3. **Fine/Bold pens.** A two-state button next to the pencil in the toolbar. Fixed width, no pressure.
   The live ink (`FirmwarePen.CANVAS_SIZE`) and the saved stroke (`StrokeElements.PEN_WIDTH_PX`) must
   match, so the firmware sizes are measured on the Manta.
4. **Tap-to-place nodes.** With the rectangle or ellipse tool, a tap places a node centred on the tap,
   wide 2:1, sized by the style panel's S/M/L/XL. Sizes are fixed on the canvas, not on screen. A tap on
   an existing node selects it. Dragging still draws a custom size. (Today a short tap with a shape tool
   does nothing: see `GestureCommit.kt`.)
5. **Writing sticks to nodes.** A stroke written mostly inside a node attaches to it and moves with it,
   without scaling when the node is resized. Deleting a node deletes its writing. Must not clash with the
   manual groups (`groupId`).
6. **Collapse and expand branches.** Hide everything branching off a node; hidden nodes are not drawn.
7. **Gesture eraser.** A copy of Supernote's own, in left-handed mode: two fingers held still in the
   top-right or bottom-right corner, landing within 200 ms of each other and before the pen, 18-55 mm
   apart, turn the pen into the eraser; lifting them goes back to the previous tool. Moving fingers still
   pinch-zoom.
8. **Tidy a branch.** One button that spaces a node's children evenly around it.
9. **Export one branch** to PDF.
10. **Mind-map starter.** A new canvas opens with a centre node placed.
11. **Later:** an arrow drawn automatically from the selected node to a newly placed one.

## Decisions already made

- Infinite canvas stays: elements remain objects; speed comes from how they are drawn.
- Arrows already bind to shapes and follow them (`startElementId` / `endElementId`).
- Handwriting inside Canvas cannot be searched or recognised: Supernote's recognition only reads the
  note's own ink.
- Version numbers for this fork stay above the developer's.
