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
  compiles) and a full build. Version 1.2.0 measured; 1.3.0 has everything below. The build now fails loudly if the native part does not compile, instead of
  quietly packaging a plugin without it.

## Measuring speed

1. Open Canvas on a **new** canvas (⋮ → New canvas; it holds only the starter node).
2. **Hold the zoom pill** (bottom left) for 1.5 seconds. The speed test map appears: 100 nodes, 99 bound
   arrows and 200 scribbled words, 399 elements, always identical (`PerfTestCanvas.kt`). On a canvas with
   anything else on it, the hold does nothing.
3. Do the same things each time: pan around, zoom, move a node, erase a few words, drag out a shape.
4. Close Canvas, then open `MyStyle/SnCanvas/perf-log.csv` with Browse & Access.

Each line of the log is one frame (`PerfLog.kt`):

| column | meaning |
|---|---|
| `time_ms` | when it was drawn |
| `frame` | `full` (the kept drawing redrawn), `cached` (the kept drawing copied, only the moving parts drawn), `pan` (the kept drawing slid while the view moves), `flash` (the clean refresh's black frame), `ink` (a live pencil stroke the canvas drew itself), or `mark:...` lines between runs |
| `gesture` | what was going on: `Move`, `Erase`, `Pan`, `DrawShape`, `none`... |
| `elements` | how many elements the canvas held |
| `draw_ms` | how long `onDraw` took |
| `gap_ms` | time since the previous frame; blank after a pause |

`gap_ms` matters as much as `draw_ms`: the view is hardware accelerated, so `onDraw` only records what to
draw and the screen is painted afterwards on another thread. Slow painting shows up as long gaps. The
firmware inks the pencil itself, so normal writing does not appear in the log at all.

## What is built (1.3.0)

Every step below is in the code and covered by unit tests; none of it has been tried on a device yet. "Check on the
Manta" says what only the real screen and pen can confirm.

1. **Speed.** `SceneCache.kt` draws the canvas once into a bitmap and keeps it. A frame copies the bitmap and draws only
   what a gesture is changing on top (`SceneCull.changedIds`); the bitmap is drawn again only when the canvas, the view,
   or what a gesture leaves out changes. Pans, pinches and minimap drags slide and scale the kept bitmap
   (`SceneCache.drawMoved`), so they cost the same on any canvas. Off-screen elements are skipped (`SceneCull`), and
   each shape's hand-drawn outline is worked out once and kept (`ElementPainter.outlinePath`). The speed log now tells
   `full` (bitmap redrawn) from `cached` (bitmap copied) frames.
   Also fixed: a pen landing during a pinch left Android's pinch detector stuck, and every pen move of that stroke was
   ignored. *Check on the Manta:* run the speed test before and after; moving a node on the test map should log one
   `full` frame then `cached` ones.
2. **Clean refresh.** After a pan, a pinch or a minimap drag, one black frame shows for 200 ms (`FLASH_MS` in
   `CanvasView.kt`), then the canvas. A tap that only selects does not flash. *Check:* whether 200 ms is long enough
   for the panel to clear, and whether it is too much.
3. **Fine/Bold pen.** A button right after the pencil in the toolbar shows a thin or thick line; a tap switches weight
   and takes up the pencil. `PenWeight.kt` holds both numbers for each weight. FINE is the developer's measured pair;
   BOLD (firmware 2000, 18 px) is a guess. *Check:* write a bold word and wait two seconds. If its thickness jumps when
   the canvas redraws it, change `BOLD`'s `widthPx` until it doesn't.
4. **Tap-to-place nodes.** With the rectangle or ellipse tool, a tap on empty canvas places a node centred there, 2:1,
   sized by the style panel's S/M/L/XL (`MindMap.nodeSize`, in canvas units). Dragging still draws a custom shape.
   A tap on a node selects it; a second tap lets it go.
5. **Writing sticks to nodes.** A stroke with at least 60% of it inside a node is attached to it
   (`Element.attachedTo`). It moves with the node by the node's centre (so a resize never stretches it), is copied
   with it, and is deleted with it; erasing a node fades its writing too. Writing dragged elsewhere attaches to
   whatever node it lands in. One rule does it all, for every edit: `MindMap.follow`, run by `CanvasController.commit`.
6. **Collapse and expand.** A node with children, selected on its own, shows a "−" badge beside its right edge; a tap
   folds its whole branch (nodes, arrows, writing) into the node (`Element.collapsed`). A collapsed node shows "+N"
   (how many nodes it hides); a tap brings the branch back, moved by however far the node moved meanwhile. Also in
   the ⋮ menu as Collapse branch / Expand branch. Hidden branches are left out of thumbnails and PDFs.
7. **Gesture eraser.** `GestureEraser.kt`: two fingers landing within 200 ms, 18-55 mm apart, held still in the
   top-right or bottom-right corner (35% of the screen in from the right, 35% down from the top or up from the
   bottom), before the pen. While they are down the pen erases with any tool, the firmware stops inking, and an
   "Eraser" label shows at the top. Moving them more than 4 mm lets them pinch instead. *Check:* Android may cancel
   the fingers' touch stream when the pen lands; if so it lasts for one pen stroke only. The log tag `SnCanvas`
   says "gesture eraser on/off".
8. **Tidy branch.** ⋮ → Tidy branch, with a node selected: its children are spaced evenly round it (all the way round
   for a root, a fan pointing away from its parent otherwise), far enough out not to overlap, each with its branch and
   writing. One undo step.
9. **Export branch.** ⋮ → Export branch to PDF: the selected node and everything below it, to `EXPORT/`.
10. **Mind-map starter.** An empty canvas opens with one large ellipse in the middle at 100%, selected, so the first
    node tapped beside it joins it. The speed test hold still works on a canvas holding only that node.
11. **Auto-arrow.** Placing a node while another node is selected joins them with an arrow and selects the new one,
    so tap, tap, tap builds a branch. Tap the selected node to let it go, and the next node starts on its own.

### Still to decide after trying it

- Whether the auto-arrow chain should be on by default, or behind a toggle.
- Whether badges should show on every parent node, not only the selected one.
- The flash length, the BOLD width, and the gesture eraser's corner size.

## Decisions already made

- Infinite canvas stays: elements remain objects; speed comes from how they are drawn.
- Arrows already bind to shapes and follow them (`startElementId` / `endElementId`).
- Handwriting inside Canvas cannot be searched or recognised: Supernote's recognition only reads the
  note's own ink.
- Version numbers for this fork stay above the developer's.
