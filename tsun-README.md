# tsun-markor 🎐

Personal fork of [Markor](https://github.com/gsantner/markor) (markdown notes
for Android) with an OLED sakura theme, a bundled Operator Mono typeface, and
auto-hiding bars. Everything here is documented from the terminal-only
workflow perspective — no Android Studio required.

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
| `make reset-app` | Uninstall (wipe app data); next `make dev` runs first-run flow |
| `make fonts` | Fetch/refresh Operator Mono OTFs from the private dotfiles repo |
| `make build` | Full clean-style build into `dist/` (same as CI runs) |
| `make test` | Unit tests |
| `make log` | *(see Logging section below)* attach to app logcat |

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

## The three features

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

- **Editor**: font preference default `/android_asset/fonts/Operator Mono - Regular.otf`
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
to match.

### 📱 Auto-hiding bars

`app/src/main/java/net/gsantner/markor/frontend/BarAutoHideHelper.java`:

- Scroll down → top app bar + bottom action bar hide; scroll up / near the
  top → both return (edit **and** preview mode)
- Two scroll sources feed one code path (`onScrollTick`): the window-level
  `ViewTreeObserver` listener (edit-mode ScrollView) and an
  `onScrollChanged` override in `DraggableScrollbarWebView` (preview-mode
  WebView — its scrolls do **not** dispatch window-wide scroll-changed
  events, which is why the preview initially ignored the bars)
- Keyboard open while editing → top bar hides, action bar stays
- Honors the user's action-bar visibility setting; idempotent, detached in

#### Why the top bar is not scroll-toggled in edit mode

Hiding the top bar via `GONE`/`VISIBLE` reflows the layout, and a focused
editor then scrolls its cursor back into view (`bringPointIntoView` — the
editor's own comment: reflow *"will bring focus back to the cursor and reset
scroll position"*). With the cursor at the document end that yanks the scroll
position all the way back down on every toggle, and the toggle/reflow cycle
oscillates (the "vibrating screen"). So in edit mode the top bar is
IME-driven only (hide while typing, show otherwise), while the bottom bar —
whose reflow grows the viewport downward and cannot push the cursor out of
view — stays fully scroll-driven in both modes.

#### Stability (the "vibrating screen" fix)

Bar toggles are `GONE`/`VISIBLE` reflows, which shift the content scroll
position (viewport resize + clamping). Naive scroll-direction tracking then
reads its own reflow as user scrolling and oscillates — bars flapping,
layout churn cancelling the keyboard show request. `BarAutoHideHelper`
therefore: (a) ignores scroll ticks for 250 ms after any toggle and
re-syncs its reference position once the layout settles, (b) ignores
deltas below 12 px (clamp jitter), and (c) ignores scroll ticks entirely
while the keyboard is open in edit mode.
  `onDestroyView`

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

## Upstream sync

This fork tracks `gsantner/markor` via the `upstream` remote. The changes
are deliberately surgical (isolated new files + small diffs) so merges stay
painless:

```bash
git fetch upstream
git merge upstream/master
# resolve, build: make dev
```

| Area touched by fork | Merge risk |
|---|---|
| `values-night/colors.xml`, string defaults | 🟢 low |
| `Makefile`, `.gitignore`, `app/build.gradle` | 🟢 low (additive) |
| `GsFontPreferenceCompat`, `TextConverterBase` | 🟡 small additive edits |
| `DocumentEditAndViewFragment.java` | 🟡 ~20 hook lines |
| New files (`BarAutoHideHelper`, `SeedNotesInstaller`, samples) | 🟢 none |

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
