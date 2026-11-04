---
target: the UI design (now playing + library mockup)
total_score: 23
max_score: 40
na_heuristics: 
p0_count: 1
p1_count: 3
target_identity: "file:/home/andrew/projects/mpd-tablet-controller/mockups/chosen.html"
target_fingerprint: "sha256:2f235424cf19062f5d76349e0ede7a1c478c148b68826d334c023b6b5273d4c1"
target_path: /home/andrew/projects/mpd-tablet-controller/mockups/chosen.html
timestamp: 2026-11-04T14-26-55Z
slug: mockups-chosen-html
closed: true
---
# Critique — Plinth now playing + library (mockup)

Method: dual-agent (A: sa-0-0daab997 · B: sa-1-25af2b56)

Target: `mockups/chosen.html` — tablet now playing (1180×780), phone library (390×844), phone now-playing sheet (390×844).

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Position, format, level and next-up all present; no connection state, so "playing" and "frozen" look identical |
| 2 | Match System / Real World | 3 | "To DAC" / "Bit-perfect" is the right dialect, but the chain order puts a verdict where a stage belongs |
| 3 | User Control and Freedom | 2 | The phone sheet has no close affordance — a 40×4 px grab handle at 22 % opacity and the OS back gesture are the only exits |
| 4 | Consistency and Standards | 2 | Transport is left-aligned on tablet, centred on phone; meter has a dB readout on phone and none on tablet; play is 74 px vs 64 px |
| 5 | Error Prevention | 2 | No truncation rules: a 64 px title at `line-height:1` wraps into the telemetry strip |
| 6 | Recognition Rather Than Recall | 3 | Text-only tabs at 11 px carry all navigation; the queue is two rows with no count |
| 7 | Flexibility and Efficiency | 2 | No search field in the library header, no jump-to-current, no tap-through from now-playing to album |
| 8 | Aesthetic and Minimalist Design | 3 | Restrained, but restraint became under-composition: a 304 px dead gutter and cover art subordinate to type |
| 9 | Error Recovery | 1 | Zero failure states designed — unreachable, no level data, missing artwork, "No mixer" |
| 10 | Help and Documentation | 2 | "Bit-perfect" and "To DAC" are unglossed jargon |

**Total: 23/40 — Acceptable.** The visual craft is above the band; the score reflects a picture of a screen rather than a specification of a surface — no states, no overflow rules, no affordances.

## Design Specificity Verdict

**LLM assessment.** The information model is authored for this product; the visual language is borrowed. A Source / To DAC / Signal strip, a stereo meter fed by real loudness, a deliberate refusal to fake a volume control — nobody builds those for a generic player. But the surrounding grammar (blurred art wash, 2-up grid, pill chips, outlined circular play, mint on graphite) is the default premium-audio kit of 2020–2024. Strip the strip and the meter and this could be any streaming app's redesign. Proportion is the bigger tell: the cover is 260×260 on a 1180 canvas — 22 % of width — while a 64 px title, the wash and the telemetry carry more optical weight.

**Deterministic scan.** 34 findings, exit 2, all `warning`: `low-contrast` 19, `undersized-ui-text` 10, `cramped-padding` 3, `hero-eyebrow-chip` 1, `wide-tracking` 1. The detector and the design review agree on the two that matter — the `--faint` token at 2.97:1 carrying functional text, and 9–10 px labels — and the detector adds precision: `--faint` composites to `#5d5c5d` on `#0a0a0c`, and it is applied to the track counter, all strip keys, both timecodes, every album artist line, and all four tab labels. Three `cramped-padding` hits and the `hero-eyebrow-chip`/`wide-tracking` hits are mockup artifacts.

**Visual overlays.** Injection succeeded in the review tab: `detect.js` loaded, defined `window.impeccableDetect`, and drew 20 overlay boxes reporting 31 rule findings (plus 5 `text-occlusion` hits created by the overlay labels themselves). No console messages are emitted by the script, so the DOM and API are the signal. That overlay lives in the reviewer's tab, not in your browser.

## Overall Impression

The calm is real and the telemetry is the product's own idea. Two things hold it back: nothing has been designed for the moments when the network, the art or the level data is missing, and at stand distance the cover — the thing the whole hi-fi idiom is built around — is a thumbnail. Fix those and this stops being a tasteful player and starts being a piece of equipment.

## What's Working

- **The signal strip.** Naming three cells Source / To DAC / Signal and colouring only the verdict means the health of the chain is one fixation. It also earns the omitted volume slider by putting something honest in its place.
- **Chrome discipline.** Hairlines instead of cards, one shadow (the cover), one accent, one family. On an OLED tablet in a dim room that reads as engineered rather than styled.
- **Editorial care in small type.** A true minus sign in "−3:47", 0.18–0.24 em tracking on uppercase keys, aligned timecodes. Cheap to preserve in Compose, and it is most of the perceived quality.

## Priority Issues

### [P0] No state design exists
**What:** Three happy-path frames: playing, art present, level data present, network up. No paused state, no streamer-unreachable state, no missing-artwork state, no "no level data" state, no long-title behaviour.
**Why it matters:** An operate surface fails at its edges. A remote whose "playing" and "connection dropped" screens are identical sends its owner to the router.
**Fix:** Draw six frames before code: paused (play glyph, fill at 45 %, meter flat with no accent); unreachable (a 6 px #E0574F dot beside the device name, text "Streamer unreachable", transport at 30 % and disabled, strip values "—"); no level data (hairline box, "NO LEVEL DATA" at 10 px/0.2 em); no artwork (a 1:1 plate at #17171B with a glyph); long title (clamp 2 lines, step 64→48 px); paused for minutes (sheet collapses to the mini player).
**Suggested command:** `/impeccable harden`

### [P1] The cover is not the hero on the tablet
**What:** 260×260 art (22 % of width) beside a 64 px/200 title, with a 304 px dead gutter between the transport and the rail.
**Why it matters:** Every reference the owner named makes the cover the largest object. At two metres, 260 px is a thumbnail.
**Fix:** Art to 384×384 (fits the 486 px content height), art→meta gap 64 px, meta column 520, rail 200. Title 64→56 px at weight 300 so it stops out-shouting the cover. End the meta column at the rail's padding edge.
**Suggested command:** `/impeccable layout`

### [P1] The phone sheet is a trap with no exit and no queue
**What:** 805 of 844 px tall, wearing a 40×4 px grab handle, with no close control, no chevron, no queue and no tap-through to the album.
**Why it matters:** A surface that looks like a half-sheet has an affordance contract it does not honour, and "what's next" is unreachable from the phone entirely.
**Fix:** Inset the sheet 12 px from the top with an 8 px radius so the mini player's 66 px band peeks behind at 40 % brightness; add a 44×44 chevron-down at (20, 20); add a bottom row "Up next · 9 more" at 13 px, full-width tappable; let the sheet scroll.
**Suggested command:** `/impeccable adapt`

### [P1] Functional text is rendered at 2.97:1 and 9–10 px
**What:** `--faint` at rgba(246,245,243,.35) carries the track counter, strip keys, timecodes, album artists and all four tab labels; the sheet's strip keys are 9 px.
**Why it matters:** It fails AA for normal text and the 3:1 floor for large text. The artist line is how you tell four Massive Attack albums apart.
**Fix:** `--faint` to rgba(246,245,243,.52) ≈ 5.2:1 for all text; keep .35 for hairlines and the meter's empty track only. Sheet strip keys 9→11 px, library artist line 12→13 px.
**Suggested command:** `/impeccable typeset`

### [P2] The meter is decoration and the telemetry repeats itself
**What:** Two 12 px bars in a 171 px column with no scale, no L/R markers, no numeric; the peak-hold line floats 6 px above the fill and reads as a glitch. Sample rate appears three times (top bar, Source, To DAC).
**Why it matters:** The meter is the only live physical element and the least informative, and a strip whose middle cell repeats the first teaches the eye to skip the strip — which destroys the best idea here.
**Fix:** 40 px scale rail with ticks at −48/−24/−12/−6/0 dBFS, L/R under the bars, live peak as a 13 px numeric. Peak-hold 3 px, full opacity, touching the fill. Replace "To DAC 44.1 kHz" with the link and device ("USB · DX5 II"), drop the top-bar format, put connection state there instead.
**Suggested command:** `/impeccable distill`

## Persona Red Flags

**Sam (accessibility-dependent).** The 2.97:1 micro-text at 9–12 px is the headline failure. Chips are 31 px tall (below 48 dp), the tab bar is 11 px text with no icons and clips at 200 % font scale, the transport glyphs are bare spans with no role or accessible name except play, and the meter exposes an `aria-label` but no value.

**Alex (power user).** No search field in the library header — he must switch tabs to type. No sort, no letter-jump, no shuffle-this-artist. From now-playing he cannot tap the cover to reach the album, cannot see more than two upcoming tracks or a count, and the 2 px progress bar offers no drag target.

**Casey (distracted, mobile).** The mini player signals nothing about the row opening the sheet, so she taps pause while trying to see what is playing. The phone never names the output device anywhere — at a party she cannot tell whether she is controlling the stereo or a speaker in the next room.

## Minor Observations

- Roboto has no 200 weight: at 64 px this renders as 300 or a synthesised thin on Android, so the hairline stroke doing most of the "premium" work will not survive the port. Specify 300 and re-cut the hierarchy, or bundle a variable display face.
- `line-height: 1` with no clamp on a 64 px title is a latent break — any title past ~30 characters wraps into the strip.
- "In the Court of the Crimson King" wraps and drops its artist line 12 px below its neighbour; clamp to 2 lines with a fixed metadata block.
- Two of the four covers are Mezzanine hue-rotated — replace with distinct art before the build review, and check a red cover against the scrim.
- Two stacked 78 px bars (mini player + tabs) take 18 % of the phone screen; a floating 64 px inset pill would return 22 px.
- Mockup harness bug: the second `.stage` nests inside the first, so the page overflows 336 px at 1720 wide and the sheet device clips. Not a design fault, but it hid the third screen during review.

## Questions to Consider

1. If the tablet is read from two metres, why is the cover smaller than the type? What would it look like at 594×594 with all telemetry collapsed into one 56 px footer band?
2. The meter is the only thing on these screens that moves — does it belong on the phone at all, or is that 54 px better spent on the queue?
3. If the DAC reported "resampling to 48 kHz" instead of "bit-perfect", how much of the surface would change? If the answer is one word's colour, the strip is ornament.
