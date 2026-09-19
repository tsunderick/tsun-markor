# Operator Mono weight strategies (tsun-markor)

> Status: **Strategy A implemented** (2026-09-18) — awaiting try-out verdict.
> **A.5 code-face amendment shipped** (2026-09-18): preview code pinned to
> Regular (`customfont-code`), cursive Prism code comments, editor inline-code
> consistency fix — see Appendix.
> B is the planned end state if Light reads well; C is the documented alternative.
> Baseline = all sections Regular/Italic/Bold/Bold Italic (the four bundled faces).

The fork bundles four of the ten Operator Mono faces from the private dotfiles
repo (Extra Light, Light, Regular, Medium, Bold — each with an Italic). This
document records why the *unused* faces might be put to work, per section, with
readability as the deciding factor.

Sections that consume fonts (see `tsun-README.md` → “Operator Mono”):

- **Editor** — plain-text editing; emphasis faces resolved by the
  `<family> - <Face>.ttf` sibling-name convention (`SyntaxHighlighterBase`)
- **Preview** — rendered WebView; 4 `@font-face` rules injected
  (`TextConverterBase`); headings and code blocks are forced onto the same
  family
- **App UI** — menus, settings, dialogs; `res/font/operator_mono.xml` family
  (400/700 slots) applied theme-wide

## The three readability forces

1. **Halation on OLED black.** Bright strokes on a pure black display optically
   *bloom* — the eye's lens scatters light, so heavy strokes smear and glare
   slightly. The heavier the weight, the stronger the glow. This is why Bold
   feels “loud” in the preview at night, and why lighter weights feel calmer on
   OLED specifically (on white paper it is the opposite — thin strokes
   disappear).
2. **The thin-stroke floor.** Below a certain stroke thickness, thin bright
   lines on black shimmer and lose their shape at editor font sizes
   (~14–16sp). This excludes **Extra Light** from every strategy — **Light is
   roughly the lowest weight that stays crisp on a phone**.
3. **Emphasis needs contrast, not force.** Operator Mono's italic is
   *cursive* — emphasis is carried by letterform **shape**, not weight, so bold
   does not need a 400→700 jump to read as “different”. A one-class step
   (300→500) plus the shape change of italics is plenty. Notes are full of
   `**bold**` frontmatter labels; heavy Bold sprinkled densely becomes visual
   noise.

## Baseline — what every element uses today

| Element | Font used (baseline) |
|---|---|
| Editor · body text | Operator Mono **Regular** (400) |
| Editor · `*italic*` emphasis, wikilinks | Operator Mono **Italic** |
| Editor · `**bold**` emphasis | Operator Mono **Bold** (700) |
| Editor · `***both***` | Operator Mono **Bold Italic** |
| Preview · body text | **Regular** |
| Preview · headings | **Bold** (family swapped to customfont; headings render bold-weight) |
| Preview · `*em*` / `**strong***` / `***both***` | **Italic** / **Bold** / **Bold Italic** |
| Preview · code blocks & inline code | **Regular** (hardcoded monospace swapped to customfont) |
| App UI · normal text (menus, lists, settings) | **Regular** (weight-400 slot) |
| App UI · bold text (titles, selected items, dialogs) | **Bold** (weight-700 slot) |

## Strategy A — “Test the Water” 🌡 *(implemented)*

**Hypothesis: body weight is the single highest-leverage readability
variable.** Only the body drops one class (Regular → Light); everything else
stays fixed. One variable, so whatever an evening of reading feels like is
attributable to that variable alone.

| Element | Font under A |
|---|---|
| Editor · body text | **Light** ⬅ the only editor change |
| Editor · `*italic*`, wikilinks | Italic (Regular-weight — *one step heavier than body, the known quirk*) |
| Editor · `**bold***` / `***both***` | Bold / Bold Italic (unchanged) |
| Preview · body text | **Light** |
| Preview · headings | Bold (unchanged) |
| Preview · code blocks & inline code | **Regular** — pinned via `customfont-code`, not inherited (A.5) |
| Preview · code comments (Prism) | cursive **Italic** — `.token.comment` italic rule (A.5) |
| App UI · everything | Regular / Italic / Bold / Bold Italic — **fully unchanged** |

- **Readability argument**: less emitted light per glyph → less halation →
  calmer long-form reading in a dark room.
- **Why it is only a probe**: the ladder becomes *asymmetric* — body 300 next
  to strong 700 makes `**bold**` look extra heavy. If notes are emphasis-dense,
  that contrast starts feeling brutal — which is precisely the itch Strategy B
  scratches.
- **Cost**: +2.6 MB APK, two-line change, no tests touched, trivially
  reversible.

## Strategy B — “OLED Reader” 🌸 *(planned end state)*

**Hypothesis: readability comes from a consistent, compressed ladder (300/500)
tuned for black-background reading.** Every relationship stays recognizable
but softer.

| Element | Font under B |
|---|---|
| Editor · body text | **Light** (300) |
| Editor · `*italic*`, wikilinks | **Light Italic** — emphasis by pure cursive form, same optical weight |
| Editor · `**bold**` | **Medium** (500) |
| Editor · `***both***` | **Medium Italic** |
| Preview · body text | **Light** |
| Preview · headings | **Medium** — hierarchy by *size*, not heaviness |
| Preview · code blocks & inline code | **Regular** (pinned — A.5 carries over) |
| App UI · normal text | **Regular** (unchanged — chrome text is too small for Light) |
| App UI · bold text (titles, selected, dialogs) | **Medium** (takes over the 700 slots) |

- **Why**: Bold stops shouting — on OLED, Medium reads the way Bold reads on
  paper. The emphasis gap halves (4 weight classes → 2), and the cursive
  italic carries form-emphasis at zero weight cost. Preview headings become
  *size*-driven rather than *weight*-driven — how well-set books do it.
- **Cost**: extends the sibling-face map honestly (`getVariantFontPath` +
  preview variant list + `operator_mono.xml` weights + the pinned
  `SyntaxHighlighterBaseFontVariantTest`). **+0 MB** if the new ladder
  *replaces* the Regular set (Regular vanishes from the picker), +10.4 MB to
  keep both ladders pickable.
- **Rejected shortcut**: renaming Medium bytes to `… - Bold.ttf` at fetch time
  (zero code, but lying filenames + wrong picker labels).

## Strategy C — “Mode-Tuned Hierarchy” 🎛 *(documented alternative)*

**Hypothesis: readability is not one setting — reading and writing have
different optima.**

| Element | Font under C |
|---|---|
| Editor · body text | **Medium** (500) — “grippy” for saccade-heavy writing |
| Editor · `*italic*`, wikilinks | Italic (Regular-weight) |
| Editor · `**bold***` / `***both***` | Bold / Bold Italic |
| Preview · body text | **Light** — airy for linear reading |
| Preview · headings | **Medium** |
| Preview · `*em*` / `**strong***` / `***both***` | **Light Italic** / **Medium** / **Medium Italic** |
| Preview · code blocks & inline code | **Regular** (pinned — A.5 carries over) |
| App UI · everything | Regular / Italic / Bold / Bold Italic — unchanged |

- **Why heavier while writing**: writing is not linear reading — eyes jump to
  fragments and hunt the cursor; heavier strokes survive glance-reading (the
  same reason code editors often default one notch heavier than readers).
- **Cost**: the editor side is zero-code (Medium's named siblings resolve
  as-is); the preview needs a one-file Light/Medium injection override.
  ~+5–10 MB.

## Verdict & chosen path

For a reading-first vault: **B > C > A**. B is the only strategy that softens
*every* reading surface coherently — body, emphasis, headings, even UI
selection states follow one principle. A is the cheap experiment and is
literally a subset of B's implementation, so nothing is wasted.

**Path**: ship A → live with it for a session or two (`make dev`) → if Light
feels right, graduate to B; if the em-as-Regular-Italic quirk or the heavy
Bold neighbors already annoy, that is B confirming itself early.

## Appendix — implementation notes

### A (as shipped)

- `app/build.gradle` (`downloadFonts`): new `assetsOnlyFaces = ['Light']` list
  written to `thirdparty/assets/fonts/Operator Mono - Light.ttf` only — no
  `res/font` slots, UI untouched. Fetch/convert logic extracted into an
  `ensureFace` closure shared with the 4 UI slots (same cache, same
  Source Pro Code stand-in on fetch failure — keeps CI green and the default
  path resolvable).
- `app/src/main/res/values/string-not_translatable.xml`
  (`default_font_family`): `/android_asset/fonts/Operator Mono - Light.ttf`.
- Editor/preview need **zero code**: sibling resolution finds the true
  `… - Bold.ttf`, `… - Italic.ttf`, `… - Bold Italic.ttf` in assets by name.
- **Note for the try-out**: the font pref is read-through — if a font was ever
  picked manually in Settings on the emulator, that persisted choice wins over
  the new default. Fix by picking “Operator Mono - Light” in Settings → Editor
  → Font, or `make reset-app` for the full first-run flow.
- **Quirk to judge**: `*em*` renders one weight step heavier than body
  (cursive + slight bump). If it reads as deliberate emphasis, fine; if it
  grates, that is B's Light Italic row fixing it.

### A.5 — code-face amendment (shipped 2026-09-18)

Three fixes, prompted by “shouldn't code be Regular?” — yes, it should:

1. **Preview code pinned to Regular** — `TextConverterBase` now declares a
   second `@font-face` family, `customfont-code`, whose default face is the
   configured family's Regular sibling (full Bold/Italic/Bold-Italic variant
   set included), plus an explicit `pre, code { font-family:
   'customfont-code' }` rule. Explicit rule required: normal-mode code never
   declares `monospace` (it just inherits the body font), so the old swap
   never reached it — and would have pointed it at the body face anyway.
   Non-convention fonts (no `" - "` in the name) keep the previous behavior.
2. **Cursive code comments** — `prism-markor.css` gained
   `.token.comment, … { font-style: italic }`; combined with the injected
   italic `@font-face`, comments render in true cursive instead of
   `prism-tomorrow`'s color-only grey (Chromium would otherwise synthesize an
   oblique). This is the Editor Convention corollary: code at Regular,
   differentiation by *color* — and the cursive is the payoff.
3. **Editor inline-code consistency** — `SyntaxHighlighterBase
   .createMonospaceSpanForMatches` now skips the switch to *system*
   monospace when the configured editor font is itself monospace (every
   bundled mono font carries “mono”/“code” in its name: Operator Mono,
   Liberation Mono, Source Pro Code). Covers Markdown, AsciiDoc, Wikitext,
   Orgmode inline/preformatted code in one place; background-color span
   still differentiates.
4. **Specificity fix (A.5.1)** — the first `pre, code` pinning rule was
   outranked by `prism-tomorrow.min.css`'s
   `code[class*="language-"] { font-family: Consolas, … }` — (0,1,1) beats
   (0,0,1) — so code blocks and their comments rendered in *synthetic-obliqued
   system monospace* (Roboto Mono), not Operator at all. DevTools-verified
   computed styles exposed it. Worse, the check revealed a **pre-existing
   fork bug**: the old `font-family: monospace → customfont` string swap never
   matched the Prism stack either, so preview code blocks had been
   system-monospace since the original font work — “Operator Mono everywhere
   in the rendered view” never actually covered Prism blocks. The injected
   rule now ties the theme at (0,1,1)
   (`pre[class*='language-'], code[class*='language-'], :not(pre) > code`)
   and wins on document order (the injected `<style>` comes after the Prism
   `<link>`s). Only emitted when a custom font is active, so upstream default
   behavior is untouched.

Convention reference: editors (VS Code, IntelliJ, terminals) render code at
**Regular**; syntax differentiation is color's job, not weight's. Medium for
code is rare (tiny sizes only); Light makes dense glyphs (`{ ; =`) wispy.

### B (sketch of the honest path)

1. `SyntaxHighlighterBase.getVariantFontPath`: replace the hardcoded
   Bold/Italic/Bold-Italic name synthesis with a per-family face map, e.g.
   `Light → {strong: Medium, em: Light Italic, both: Medium Italic}`,
   `Regular → {…current…}`, `Medium → {strong: Bold, em: Medium Italic,
   both: Bold Italic}`.
2. `TextConverterBase` preview injection: drive the `@font-face` variant list
   from the same map instead of the hardcoded `{" - Bold.ttf", …}` array; the
   `customfont-code` block derives from the same map too (code default stays
   the family Regular — A.5 carries over, comments keep the true Italic).
3. `res/font/operator_mono.xml`: 700 slots point at Medium/Medium Italic
   (slot contents already come from `slotToFile` in `downloadFonts`).
4. Extend `SyntaxHighlighterBaseFontVariantTest` with the new families.
5. Decide: replace the Regular set in `slotToFile` (+0 MB, Regular leaves the
   picker) or ship all 8 faces (+10.4 MB, both ladders pickable).
