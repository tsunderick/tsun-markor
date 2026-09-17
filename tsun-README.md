# tsun-markor 🎐

Personal fork of [Markor](https://github.com/gsantner/markor) (markdown notes
for Android), tailored for the **`tsunderelkasten`** vault — an Obsidian-shaped
Zettelkasten synced to the phone — with an OLED sakura theme, a bundled
Operator Mono typeface, auto-hiding bars, and Obsidian-style wikilinks (links,
heading anchors, sized images, clickable frontmatter). Everything here is
documented from the terminal-only workflow perspective — no Android Studio
required.

---

## The one command you need

```bash
make dev
```

Edit any file → run `make dev` → the app rebuilds (5–30 s incremental),
reinstalls, and restarts on the emulator. That is the whole dev loop.

| Command | What it does |
|---|---|
| `make dev` | Boot emulator (if needed) → build → install → force-stop & relaunch |
| `make emulator` | Just boot the `tsunderelkasten` AVD and wait for full boot |
| `make install-dev` | Build + install + auto-grant storage permissions (see First-run below) |
| `make reset-app` | Uninstall (wipe app data); next `make dev` runs first-run flow |
| `make fonts` | Fetch/refresh Operator Mono OTFs from the private dotfiles repo |
| `make build` | Full clean-style build into `dist/` (same as CI runs) |
| `make test` | Unit tests |
| `make log` | Attach to app logcat (emulator log lives at `/tmp/tsun-emulator.log`, see bottom) |

Emulator window opens on your desktop — it is a full Android device (AVD
named `tsunderelkasten`). The app appears *inside* it after `make dev`.

## One-time setup (already done on this machine, notes for a new machine)

1. **JDK 21** — `pacman -S jdk21-openjdk`; Java 26 breaks AGP 8.13
   (`Unsupported class file major version 70`). Pinned for all Gradle builds
   via `~/.gradle/gradle.properties`:
   ```properties
   org.gradle.java.home=/usr/lib/jvm/java-21-openjdk
   ```
2. **Android SDK** — root-owned `/opt/android-sdk` is used only for the
   emulator binary. Builds use a user-writable SDK at `~/Android/Sdk`
   (`platforms;android-35`, `build-tools;35.0.0`), wired via git-ignored
   `local.properties`:
   ```properties
   sdk.dir=/home/tsunderick/Android/Sdk
   ```
3. **gh CLI auth** — `gh auth status` must show a token with `repo` scope
   for the private font fetch to work. Without it, builds still succeed —
   the fonts are simply skipped (also true in CI).

## Fork features

| Feature | Section |
|---|---|
| 🎐 Obsidian wikilinks, heading anchors, vault-wide images, clickable frontmatter | first section below |
| 🌸 OLED sakura theme (default) | below |
| 🖋 Operator Mono (default font) | below |
| 🎨 Preview theme + frontmatter keys | below |
| 📱 Auto-hiding bars | below |
| 🚀 First-run experience (fork defaults) | last section below |

### 🎐 Obsidian wikilinks in Markdown

`[[Note]]`, `[[Note|Alias]]` and `[[Note#Heading]]` work in Markdown notes —
display **and** routing, in preview and editor.

The daily-note nav ribbon this exists for (from a real `tsunderelkasten`
daily note):

```markdown
[[tsunderelkasten/1.fleeting/1.daily/2026-09-17 Thursday|⏮️昨日🌜]] ·
[[tsunderelkasten/1.fleeting/1.daily/2026-09-17 Thursday#Log|📜log]] ·
[[tsunderelkasten/1.fleeting/2.weekly/2026-W38|今週 W38]]

> ![Photo of the Day|300](media/daily-note-waifu/download.jpg)
```

Aliases display, taps open the note, `#Log` lands on the heading, and the
waifu renders at 300 px — paths resolved vault-wide. The machinery is
generic: nothing is hardcoded to `tsunderelkasten`; the vault root is simply
Markor's notebook directory (set once, below).

#### ⚠️ Set the notebook directory to the vault root (tsunderelkasten setup)

This app is tailored for the `tsunderelkasten` vault. Wikilinks resolve
**relative to Markor's notebook directory** — they are vault-absolute, so the
notebook directory must BE the vault root:

> **Settings → Notebooks → Notebook directory →**
> `/storage/emulated/0/Zettelkasten/ObsidianTsundere`

If it is left at the default `Documents/markor`, vault-absolute links like
`[[tsunderelkasten/1.fleeting/1.daily/2026-09-17 Thursday|…]]` cannot resolve
(there is no `tsunderelkasten/` below `Documents/markor`), and tapping one
falls into the "missing note" path: a brand-new empty note opens under
`Documents/markor/tsunderelkasten/…`. That is the symptom — the fix is the
setting above, nothing else. (The folder picker hides dot-folders; that is
cosmetic only — the app reads through them fine.)

#### How it works

- **Render**: `MarkdownTextConverter` rewrites wikilinks to
  `[alias](file:///abs/path)` *before* flexmark parsing (same trick as the
  pre-existing `@attachment` rewrite), so the preview shows the alias as a
  normal link and the WebView client's existing `file://` handling routes the
  tap to `DocumentActivity`. Upstream's flexmark wikilink extension has no
  `|` alias support — that's why `[[a|b]]` rendered literally before.
  Frontmatter values containing wikilinks are linkified the same way
  (`ObsidianWikiLinkResolver.wikiTextToHtmlLinks` — pure-java, escaping is
  byte-parity-tested against the old values-only pipeline).
- **Images**: Obsidian-style `![alt|300](vault/relative.jpg)` works too —
  the size suffix becomes a real `width`/`height` (`|WxH` supported), and
  paths resolve like wikilinks (note-relative first, then vault-wide).
  Working standard images (`../../../relative.jpg`, web URLs) and
  unresolvable ones are left untouched; `![[embeds]]` remain out of scope.
- **Heading anchors**: `[[Note#Heading|alias]]` rewrites to
  `file:///…/Note.md#slug`, `[[#Heading]]` to a plain `#slug` fragment
  (native same-page jump). The slug is produced by flexmark's own
  `HeaderIdGenerator` — byte-identical to the ids the preview emits (there is
  a unit test rendering real headings and asserting the ids match). The
  anchor rides the tapped `file://…#slug` Uri through `DocumentActivity`
  (`EXTRA_FRAGMENT_ID`) into the view fragment, which injects a
  `scrollIntoView` on load. This exposed an upstream-shipped bug: flexmark
  0.42 needs `RENDER_HEADER_ID` in addition to `GENERATE_HEADER_ID`, without
  which header ids are never emitted at all — headings had no ids before.
- **Resolve** (`ObsidianWikiLinkResolver` — file resolution is pure
  java.io, JVM-testable): `./x`/`../x` anchor at the current file; other
  targets try notebook-root relative, then current folder, then a vault-wide
  breadth-first search by note name with **fewest-path-segments wins**
  (Obsidian's "shortest path" rule — *not* shortest string; `Alpha` vs `Beta`
  bit me during testing). Unresolved targets fall back to
  `notebookRoot/Target.md` so opening offers document creation. Extensions
  tried: as-given, `.md`, `.markdown`, `.md.txt`.
- **Editor**: wikilinks highlight like links
  (`MarkdownSyntaxHighlighter.WIKI_LINK`) and the open-link action resolves
  them vault-wide; cross-page anchors open the note (and jump in preview),
  same-page `[[#Heading]]` jumps the visible preview directly.
- **Known limits**: `![[embeds]]` untouched; duplicate headings resolve to
  the first occurrence; case-sensitive matching; `obsidian://` URIs not
  intercepted.

Tests: `ObsidianWikiLinkResolverTests` (42 cases: resolution, anchors with a
slug≡flexmark-rendered-id equivalence test, frontmatter linkification with a
legacy-escaping-parity guard, and image rewriting).

### 🌸 OLED sakura theme (default)

Pure `#000000` background with the palette from
`omarchy-tsunderick-theme/colors.toml`:

| Token | Hex |
|---|---|
| background | `#000000` |
| foreground | `#f0eaed` |
| accent (cursor, highlights) | `#ff8fb1` |
| secondary text | `#da9fdc` |

Files: `app/src/main/res/values/colors.xml` (accent, global),
`app/src/main/res/values-night/colors.xml` (dark-mode overrides).
Theme default is `dark-black` ("Black" in the picker) via
`string-not_translatable.xml` → `app_theme_black`, referenced in
`AppSettings.getAppThemeName()` and `preferences_master.xml`.

### 🖋 Operator Mono (default font)

One font directory, three consumers. The single source of truth is
`app/thirdparty/assets/fonts/` (upstream's own asset dir — files merge into
the APK automatically). The 4 Operator Mono faces there are **gitignored**
(`/app/thirdparty/assets/fonts/Operator*.otf` — commercial font, never
committed); they are fetched by `make fonts` or the `downloadFonts` Gradle
task from the **private** `tsunderick/dotfiles` repo via `gh api`. Builds
without gh access still succeed — the fetch is skipped with a warning and
the bundled *Source Pro Code* font stands in everywhere.

- **Editor**: font preference default `/android_asset/fonts/Operator Mono - Regular.otf`.
  Emphasis uses the **true faces, not the synthetic skew**: `*italic*` renders the
  cursive Operator Mono Italic, `**bold**` the true Bold, and `***both***` the true
  Bold Italic. `SyntaxHighlighterBase` derives sibling variant paths from the chosen
  font (`X - Italic.ttf` …, same convention as the preview), loads them from assets
  (cached, with negative caching), swaps them in via a metric-affecting
  `StyledTypefaceSpan`, and merges the co-located bold+italic spans that
  triple-marker emphasis produces (typeface spans can't compose — last one would
  win). This covers every format (Markdown, Orgmode, Wikitext, TodoTxt) and the
  italic blue-link spans; fonts without variant siblings fall back to the old
  skew/fake-bold behavior. Known limit: *nested* emphasis (`**b *c* d**`) keeps
  the inner italic face only in the overlap region (preview renders it correctly).
- **Whole UI theme**: the task copies the faces to git-ignored
  `app/src/main/res/font/operator_mono_{regular,italic,bold,bold_italic}.otf`,
  wired through the committed `operator_mono.xml` font family +
  `fontFamily` in `AppTheme.Unified` and the dialog theme
- **Preview (WebView)**: served over `https://appassets.androidplatform.net/assets/…`
  by `androidx.webkit`'s **WebViewAssetLoader** (hooked into
  `MarkorWebViewClient.shouldInterceptRequest`, which also injects the
  `Access-Control-Allow-Origin: *` header — fonts are CORS-checked and the
  preview page's origin is `null` due to `file://`). True bold/italic faces
  are injected, not synthesized. When a custom font is active, headings
  (upstream hardcodes `sans-serif-condensed`) and code blocks (hardcoded
  `monospace`) are swapped to it too — Operator Mono everywhere in the
  rendered view.

> History lesson (why this took three attempts): plain `file:///android_asset/…`
> font URLs are **blocked** by WebView (`setAllowFileAccessFromFileURLs=false`),
> inlining 11 MB of base64 into the HTML was too heavy, and the loader alone
> still failed on the CORS header. `adb logcat`'s chromium console messages
> pinpointed each failure — check there first when fonts misbehave.

> Also note: `minSdk` was bumped 18 → 19 for androidx.webkit (Android 4.4+, 2013).

### 🎨 Preview theme

`TextConverterBase` dark style + token colors restyled to the OLED palette:
pure black body, `#f0eaed` text, pink links/headings/underlines, orchid
blockquotes with pink border, `#111` code blocks, `#444` table borders.
Markdown converter's frontmatter chips and inline-code backgrounds darkened
to match. Frontmatter items render as `key: value` — the key is a visible
muted-orchid label (`#da9fdc`) instead of upstream's values-only display, and
values containing `[[wikilinks]]` render as clickable anchors (vault-resolved,
heading anchors scroll — same routing as body links).

### 📱 Auto-hiding bars

`app/src/main/java/net/gsantner/markor/frontend/BarAutoHideHelper.java`:

- Scroll down → top app bar + bottom action bar hide; scroll up → both return
  — in **both** edit and preview mode. Near the top of the content the bars
  always show.
- **Asymmetric hysteresis**: hiding needs 64 px of sustained down-travel past
  the last flip; showing needs only 24 px of up-travel, with a shorter guard.
  Bars are eager to return and reluctant to leave — showing is harmless,
  hiding is disruptive. (An earlier plain direction-vote variant had no
  hysteresis: reflow deltas re-flipped the state instantly and the bars
  flapped — that variant is what made the feature feel broken.)
- **Programmatic jumps re-anchor the baseline**: scroll restore, wikilink
  heading anchors and TOC jumps call `resyncScrollBaseline()`, so a jump's
  own delta is never mistaken for user scrolling (no phantom anchors, no
  spurious flips after landing).
- Two scroll sources feed one code path (`onScrollTick`): the window-level
  `ViewTreeObserver` listener (edit-mode ScrollView) and an
  `onScrollChanged` override in `DraggableScrollbarWebView` (preview-mode
  WebView — its scrolls do **not** dispatch window-wide scroll-changed
  events, which is why the preview initially ignored the bars)
- Keyboard open while editing → top bar stays hidden regardless of scroll
  (typing space first); bottom bar remains scroll-driven
- Honors the user's action-bar visibility setting; idempotent; detached in
  `onDestroyView`
- **Debugging**: debug builds log every flip and throttled scroll ticks under
  the `BarAutoHide` tag — `make log | grep BarAutoHide` shows exactly why the
  bars flipped

#### How the top bar became scroll-toggled in edit mode (the cursor-yank containment)

Hiding the top bar via `GONE`/`VISIBLE` reflows the layout, and a focused
editor then scrolls its cursor back into view (`bringPointIntoView` — the
editor's own comment: reflow *"will bring focus back to the cursor and reset
scroll position"*). With the cursor at the document end that yanks the scroll
position all the way back down on every toggle, and the toggle/reflow cycle
oscillates (the "vibrating screen" — which is why the top bar was once
IME-driven only in edit mode; that variant meant it *never* hid, since the
emulator usually types via hardware keyboard). The oscillation is contained
by the same armor the bottom bar always had — direction-specific guards
(400 ms hide / 120 ms show), asymmetric hysteresis, and a post-reflow
baseline re-sync — so the top bar now toggles by scroll in edit mode too,
with the IME rule kept as an override while typing.

### 🚀 First-run experience (fork defaults)

- **Preview-first** — documents open in view mode by default
  (`pref_key__is_preview_first`; toggle in Settings → "Prefer view mode")
- **No intro** — `IntroActivity.isFirstStart` defaults to "already shown"
  (re-flashing the app no longer replays the intro slides)
- **No permission screens in the dev loop** — `make install-dev` auto-grants
  after install: `pm grant` READ/WRITE_EXTERNAL_STORAGE +
  `appops set MANAGE_EXTERNAL_STORAGE allow`
- **Seeded sample notes** — `assets/samples/tsun-playground.md` (long,
  multi-section scroll test) and `markor-markdown-reference.md` are copied
  into the notebook root by `util/SeedNotesInstaller` (idempotent — never
  overwrites, retries silently until storage is writable)
- Defaults: Black theme + Operator Mono editor font, out of the box

## Upstream: intentionally diverged

This fork does **not** track or merge `gsantner/markor` — it develops on its
own line for the tsunderelkasten workflow. The `upstream` remote stays
configured purely for opportunistic cherry-picks, on no cadence:

```bash
git fetch upstream                  # see what upstream is doing
git log upstream/master --oneline   # cherry-pick something, if ever useful
```

There is no merge cadence and no merge-risk budget. Where each feature lives,
for reading or rewriting:

| Feature | Key files |
|---|---|
| Wikilinks / heading anchors / images / frontmatter linkify | `format/markdown/ObsidianWikiLinkResolver.java` (+ `ObsidianWikiLinkResolverTests`); hooks in `MarkdownTextConverter`, `MarkdownActionButtons`, `MarkdownSyntaxHighlighter` |
| Anchor routing (tap → scroll) | `DocumentActivity` (`EXTRA_FRAGMENT_ID`), `DocumentEditAndViewFragment` (`START_JUMP_ANCHOR`, `jumpToAnchorPreview`), `TextConverterBase` (jump-on-load render overload) |
| Frontmatter keys as labels | `MarkdownTextConverter` (`HTML_FRONTMATTER_KEY_*`, `CSS_FRONTMATTER`) |
| Sakura palette (UI + editor + preview) | `values/colors.xml`, `values-night/colors.xml`, per-format syntax highlighters |
| Operator Mono everywhere | `app/build.gradle` (`downloadFonts` task), `res/font/operator_mono.xml`, `MarkorWebViewClient` (WebViewAssetLoader) |
| Auto-hiding bars | `frontend/BarAutoHideHelper.java`, `DraggableScrollbarWebView` (scroll source) |
| First-run / seeded samples | `AppSettings` (defaults), `IntroActivity` (skipped intro), `util/SeedNotesInstaller`, `assets/samples/` |
| Dev loop & CI | `Makefile` (`make dev`), `.github/workflows` (upstream CI, kept green) |

## CI

Upstream's GitHub Actions workflow runs on every push of this fork: clean
build + unit tests + lint (`make clean all` on JDK 21). The private font
fetch gracefully skips there — CI builds are font-less but green.

## Build environment quick reference

```bash
make dev            # THE loop
make reset-app      # wipe data
make build          # CI-style full build → dist/
adb devices         # emulator shows as emulator-5554
```

Emulator log: `/tmp/tsun-emulator.log` · Gradle log: `dist/log/gradle.log`
