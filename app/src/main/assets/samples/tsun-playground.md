# Tsun Playground 🎐

> A deliberately long markdown document for exercising everything:
> scrolling, syntax highlighting, tables, lists, and the auto-hiding bars.
> If you can reach the bottom, the bars have done their job.

---

## 1. Introduction

This document exists so that every feature of this fork can be verified with a
single file. It is intentionally long — long enough that scrolling it in either
the editor or the rendered preview will make the top bar and the bottom action
bar slide away, and bring them back when you scroll up.

Scroll down. Watch the bars vanish. Scroll up. Watch them return.
That is the contract.

### 1.1 What this fork changes

- **OLED black theme** with a sakura-pink accent (`#ff8fb1`) on pure black
- **Operator Mono** as the default typeface for the editor and the rendered view
- **Auto-hiding bars** — top bar and bottom action bar follow scroll direction
- **Bundled sample notes** — this file and the Markdown reference, seeded on first run

### 1.2 How to read this file

1. Open it in the editor — the text should render in Operator Mono.
2. Scroll — the bars should hide and reappear.
3. Toggle the preview (eye icon) — bold and italics should render as *true*
   Operator Mono Bold and Operator Mono Italic, not synthesized substitutes.
4. Open the keyboard in edit mode — the top bar should hide while typing.

---

## 2. Emphasis playground

Plain text, *italic text*, **bold text**, ***bold italic text***,
~~strikethrough text~~, `inline code text`, and a [link to the Markor
project](https://github.com/gsantner/markor).

> Blockquote level one.
>> Blockquote level two — nested.
>>> Blockquote level three — still readable on pure black, we hope.

Footnote references work too[^1], and here is a second one[^2].

[^1]: Footnotes render at the bottom in most markdown converters.
[^2]: This is the second footnote.

---

## 3. Lists of every flavor

### 3.1 Task list (checkboxes)

- [x] OLED theme applied
- [x] Operator Mono bundled
- [x] Auto-hiding bars implemented
- [ ] Verify scrolling in editor mode
- [ ] Verify scrolling in preview mode
- [ ] Rotate device and check again
- [ ] Be excellent to each other

### 3.2 Nested unordered list

- Fonts
  - Operator Mono
    - Regular (default)
    - Italic
    - Bold
    - Bold Italic
  - Fallbacks
    - sans-serif
    - monospace
- Themes
  - Black (OLED)
    - `#000000` background
    - `#f0eaed` foreground
    - `#ff8fb1` accent
  - Dark
  - Light

### 3.3 Ordered list with nesting

1. First, open this file
2. Then, scroll
   1. Down — bars hide
   2. Up — bars return
      1. Even in nested lists
      2. Even when the list is long
3. Finally, admire the palette

---

## 4. Code blocks

### 4.1 Kotlin

```kotlin
data class Note(
    val title: String,
    val body: String,
    val isOled: Boolean = true,
)

fun decorate(note: Note): String = buildString {
    append("║ ${note.title} ║\n")
    note.body.lines().forEach { append("║ $it\n") }
    if (note.isOled) append("║ black as the void ║")
}
```

### 4.2 Bash

```bash
#!/usr/bin/env bash
# The entire dev loop of this fork, in spirit
make dev          # build + install + relaunch
make reset-app    # wipe app data, first-run again
make fonts        # refetch Operator Mono from the private repo
```

### 4.3 Python

```python
def palette():
    return {
        "background": "#000000",
        "foreground": "#f0eaed",
        "accent":     "#ff8fb1",
        "muted":      "#da9fdc",
        "selection":  "#626583",
    }

SAKURA = palette()
print(f"accent = {SAKURA['accent']}")
```

### 4.4 JSON

```json
{
  "fork": "tsun-markor",
  "upstream": "gsantner/markor",
  "features": ["oled-theme", "operator-mono", "auto-hiding-bars"],
  "sync": "git fetch upstream && git merge upstream/master",
  "pixel_count": 0
}
```

### 4.5 Diff

```diff
+ accent = #ff8fb1
+ toolbar = #000000
- accent = #F04B4B
- toolbar = #1E2135
```

---

## 5. Tables

### 5.1 The palette

| Token | Hex | Role |
|---|---|---|
| background | `#000000` | OLED black, everywhere |
| foreground | `#f0eaed` | primary text, slightly warm |
| accent | `#ff8fb1` | cursor, highlights, checkboxes |
| secondary | `#da9fdc` | orchid, secondary text |
| selection | `#626583` | selection background |
| bright sakura | `#ffb3d9` | terminal color13 |

### 5.2 Features and status

| Feature | Editor | Preview | Notes |
|---|---|---|---|
| Operator Mono | ✅ | ✅ true bold/italic | via @font-face injection |
| Auto-hide bars | ✅ | ✅ | scroll direction based |
| IME top-bar hide | ✅ | n/a | keyboard open = bar gone |
| Black background | ✅ | ✅ | forced when Black theme |

### 5.3 A wide table for scrolling torture

| Column A | Column B | Column C | Column D | Column E | Column F |
|---|---|---|---|---|---|
| cell | cell | cell | cell | cell | cell |
| 1 | 2 | 3 | 4 | 5 | 6 |
| alpha | beta | gamma | delta | epsilon | zeta |
| 左 | 中 | 右 | 上 | 下 | 終 |

---

## 6. The scroll endurance section

The section below repeats enough prose to give your thumb a workout.
Each paragraph is deliberately unremarkable; their quantity is the point.

Scroll down. The top bar and the bottom action bar should slide out of view.
Keep going. The scrollbar thumb may be dragged — that works too, and the bars
should stay hidden while you travel downward.

Scroll up even a little. Both bars should come back immediately. This is the
"standard" behavior you asked for, and it applies in the editor as well as in
the rendered preview.

While editing with the keyboard open, the top bar hides itself so that the
cursor line sits as high on the screen as possible, and the bottom action bar
stays available for formatting buttons. Close the keyboard and the top bar
returns. None of this should fight you — if it does, that is a bug.

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor
incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis
nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.

Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu
fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in
culpa qui officia deserunt mollit anim id est laborum.

Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium
doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore
veritatis et quasi architecto beatae vitae dicta sunt explicabo.

Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed
quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt.

Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur,
adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et
dolore magnam aliquam quaerat voluptatem.

Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit
laboriosam, nisi ut aliquid ex ea commodi consequatur. Quis autem vel eum iure
reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur.

---

## 7. Horizontal rules and spacing

---

Above and below this line: rules. Between sections: silence.

### 7.1 Entities and escaping

Escaped markdown: \*not italic\*, \`not code\`, \[not a link\].
HTML entities: &lt;tag&gt; &amp; &copy; — should render literally.

### 7.2 Autolinks and raw URLs

https://example.com should autolink in most converters, as should
bare www.example.com paths in some.

---

## 8. Conclusion

If you have scrolled this far:

- [x] the bars hid and returned at least a dozen times
- [x] Operator Mono rendered every glyph above
- [x] the black was pure and the pink was pink

Then the fork is behaving. Go write something real.

> 終わり — the end. 🎐

---

*Generated for the tsun-markor fork. Safe to edit, delete, or duplicate — it is
re-seeded on first run only if missing.*
