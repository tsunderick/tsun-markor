/*#######################################################
 *
 *   tsun-markor fork: Obsidian-style wikilinks for Markdown
 *   License of this file: Apache 2.0
 *
#########################################################*/
package net.gsantner.markor.format.markdown;

import com.vladsch.flexmark.html.renderer.HeaderIdGenerator;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves and rewrites Obsidian-style wikilinks (<code>[[Note]]</code>,
 * <code>[[Note|Alias]]</code>, <code>[[Note#Heading]]</code>) found in Markdown
 * documents.
 *
 * <p>Resolution itself is pure <code>java.io</code>/<code>java.util</code>
 * (JVM-testable without Android). The rewrite path additionally uses flexmark's
 * own {@link HeaderIdGenerator} to turn <code>#Heading</code> anchors into
 * element ids — byte-identical to the ids the preview renderer emits, which is
 * what makes cross-page anchor jumps land on the right heading.</p>
 *
 * <p>Resolution semantics (mirroring Obsidian's defaults, simplified):</p>
 * <ol>
 *   <li>Targets starting with {@code ./} or {@code ../} are resolved relative
 *       to the current file's folder.</li>
 *   <li>Other targets are tried as a notebook-root-relative path, then relative
 *       to the current file's folder.</li>
 *   <li>Finally the whole notebook is searched breadth-first for a file whose
 *       name (or root-relative path) matches the target; the shortest matching
 *       path wins, mirroring Obsidian's "shortest path when needed" behaviour.</li>
 *   <li>Unresolved targets fall back to {@code notebookDir/target.md} — opening
 *       it lets Markor offer its usual "create document" flow.</li>
 * </ol>
 *
 * <p>Limitations (deliberate): embeds ({@code ![[...]]}) are left untouched,
 * duplicate headings resolve to their first occurrence, and matching is
 * case-sensitive.</p>
 */
public final class ObsidianWikiLinkResolver {

    /** Matches [[target]], [[target|alias]], [[target#Heading|alias]] and the ![[...]] embed form. */
    public static final Pattern WIKI_LINK = Pattern.compile("!?\\[\\[[^\\[\\]\\n]+?\\]\\]");

    /** Matches ![alt](path) markdown images, including Obsidian's ![alt|300] / ![alt|300x200] size syntax. */
    public static final Pattern MD_IMAGE = Pattern.compile("!\\[([^\\]\\[]*)\\]\\(([^()]+)\\)");

    /** Extensions tried in order when looking a target up on disk. (package-private: also used by {@link VaultIndex}) */
    static final String[] TARGET_EXTENSIONS = {"", ".md", ".markdown", ".md.txt"};

    private ObsidianWikiLinkResolver() {
    }

    /**
     * Turn a heading anchor ({@code Some Heading!}) into the element id the
     * preview renderer emits for it. Delegates to flexmark's own generator
     * with the same options the Markdown converter renders with
     * ({@code " -_"} dash chars, dupes allowed without collapsing,
     * non-ASCII lowercased) so href and target id always match.
     */
    public static String slugifyHeading(final String anchor) {
        if (anchor == null) {
            return "";
        }
        return HeaderIdGenerator.generateId(anchor.trim(), " -_", null, false, true);
    }

    /**
     * Extract target and heading anchor from raw wiki-link text:
     * {@code [[target#Heading|alias]]} &rarr; {@code {"target", "Heading"}}.
     * Strips the embed marker ({@code !}) and alias part; the anchor is kept
     * verbatim (an empty string when absent).
     */
    public static String[] extractTargetAndAnchor(final String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("!")) {
            s = s.substring(1);
        }
        if (s.startsWith("[[")) {
            s = s.substring(2);
        }
        if (s.endsWith("]]")) {
            s = s.substring(0, s.length() - 2);
        }
        final int pipe = s.indexOf('|');
        String aliasPart = null;
        if (pipe >= 0) {
            aliasPart = s.substring(pipe + 1);
            s = s.substring(0, pipe);
        }
        final int hash = s.indexOf('#');
        String anchor = "";
        if (hash >= 0) {
            anchor = s.substring(hash + 1).trim();
            s = s.substring(0, hash);
        }
        return new String[]{s.trim(), anchor, aliasPart == null ? null : aliasPart.trim()};
    }

    /**
     * Extract the link target from raw wiki-link text:
     * {@code [[target|alias]]} &rarr; {@code target}. Also strips the embed
     * marker ({@code !}), alias part and heading anchor ({@code #...}).
     *
     * @return the bare target, possibly empty
     */
    public static String extractTarget(final String raw) {
        return extractTargetAndAnchor(raw)[0];
    }

    /**
     * Extract the alias/display text of a raw wiki-link, falling back to the
     * target when no {@code |alias} is given.
     */
    public static String extractAlias(final String raw, final String target) {
        final String alias = extractTargetAndAnchor(raw)[2];
        return alias != null && !alias.isEmpty() ? alias : target;
    }

    /**
     * Resolve a bare (alias-stripped) target to a concrete file.
     *
     * @param notebookDir notebook/vault root, may be null (null is returned)
     * @param currentFile the document being rendered/edited, may be null
     * @return the resolved file (which may not exist yet — the caller decides
     *         whether opening it offers document creation), or null when the
     *         input is empty or no notebook directory is known
     */
    public static File resolve(final File notebookDir, final File currentFile, final String target) {
        return resolve(notebookDir, currentFile, target, null);
    }

    /**
     * Same resolution, sharing a per-conversion {@link VaultIndex} for the
     * vault-wide search. {@code index == null} falls back to the legacy
     * per-link full-vault walk (used by one-off callers and the unit tests).
     */
    static File resolve(final File notebookDir, final File currentFile, final String target, final VaultIndex index) {
        String t = target == null ? "" : target.trim();
        if (t.isEmpty() || notebookDir == null) {
            return null;
        }

        // Degraded v1 behaviour: [[Note#Heading]] opens Note
        final int hash = t.indexOf('#');
        if (hash >= 0) {
            t = t.substring(0, hash).trim();
            if (t.isEmpty()) {
                return null;
            }
        }

        final File currentDir = currentFile != null && currentFile.getParentFile() != null
                ? currentFile.getParentFile() : null;

        // 1) Explicitly relative targets (./x, ../x) anchor at the current file
        if (t.startsWith("./") || t.startsWith("../")) {
            final File base = currentDir != null ? currentDir : notebookDir;
            return tryCandidates(normalize(new File(base, t)));
        }

        // 2) Path-style targets, first against the notebook root ...
        File candidate = tryCandidates(normalize(new File(notebookDir, t)));
        if (candidate != null) {
            return candidate;
        }

        // ... then against the current file's folder
        if (currentDir != null) {
            candidate = tryCandidates(normalize(new File(currentDir, t)));
            if (candidate != null) {
                return candidate;
            }
        }

        // 3) Vault-wide search: filename (or root-relative path) match,
        //    shortest absolute path wins
        candidate = index != null ? index.searchVault(t) : searchVault(notebookDir, t);
        if (candidate != null) {
            return candidate;
        }

        // 4) Unresolved: fall back to notebook-root/target.md so opening the
        //    link can offer Markor's document-creation flow
        return normalize(new File(notebookDir, t.endsWith(".md") ? t : t + ".md"));
    }

    /**
     * Rewrite all wikilinks in a Markdown source to standard
     * {@code [alias](file://...)} links, so that flexmark renders them as
     * regular anchors (alias shown) and Markor's WebView client routes the
     * {@code file://} hrefs to {@code DocumentActivity}.
     *
     * <p>Lines inside fenced code blocks ({@code ```}) and embeds
     * ({@code ![[...]]}) are left untouched. Links that cannot be resolved
     * are also left untouched.</p>
     */
    public static String rewriteWikiLinks(final String markup, final File notebookDir, final File currentFile) {
        return rewriteWikiLinks(markup, notebookDir, currentFile, null);
    }

    /**
     * Same rewrite, sharing a per-conversion {@link VaultIndex}; null index
     * falls back to the legacy per-link vault walk.
     */
    public static String rewriteWikiLinks(final String markup, final File notebookDir, final File currentFile, final VaultIndex index) {
        if (markup == null || !markup.contains("[[")) {
            return markup;
        }

        final StringBuilder out = new StringBuilder(markup.length() + 64);
        boolean inFence = false;
        final String[] lines = markup.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            if (line.trim().startsWith("```")) {
                inFence = !inFence;
                out.append(line);
            } else if (inFence) {
                out.append(line);
            } else {
                rewriteLine(line, notebookDir, currentFile, out, index);
            }
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    /**
     * Convert Obsidian wikilinks inside a text value (e.g. a YAML frontmatter
     * value like {@code "[[Note|Alias]]"}) to HTML anchors; everything else is
     * HTML-escaped exactly like the previous values-only display
     * (htmlEncode + em-dash/en-dash + trim), so linkless values render
     * byte-identical to before.
     *
     * <p>Resolvable links become {@code <a href="file://…">}; same-page
     * {@code [[#Heading]]} becomes a plain {@code #slug} fragment link;
     * unresolvable targets stay as literal (escaped) text.</p>
     */
    public static String wikiTextToHtmlLinks(final String value, final File notebookDir, final File currentFile) {
        return wikiTextToHtmlLinks(value, notebookDir, currentFile, null);
    }

    /** Same conversion, sharing a per-conversion {@link VaultIndex}; null index falls back to the legacy walk. */
    public static String wikiTextToHtmlLinks(final String value, final File notebookDir, final File currentFile, final VaultIndex index) {
        if (value == null) {
            return "";
        }
        if (!value.contains("[[")) {
            return escapeDisplayText(value).trim();
        }

        final Matcher m = WIKI_LINK.matcher(value);
        final StringBuilder out = new StringBuilder(value.length() + 32);
        int last = 0;
        while (m.find()) {
            out.append(escapeDisplayText(value.substring(last, m.start())))
                    .append(wikiLinkToHtml(m.group(), notebookDir, currentFile, index));
            last = m.end();
        }
        out.append(escapeDisplayText(value.substring(last)));
        // Trim the whole result like the legacy pipeline did — NOT per segment,
        // or spaces between text and anchors would be eaten
        return out.toString().trim();
    }

    /** Render one raw wikilink as an anchor, or the escaped literal when unresolvable. */
    private static String wikiLinkToHtml(final String raw, final File notebookDir, final File currentFile, final VaultIndex index) {
        final String[] parts = extractTargetAndAnchor(raw);
        final String target = parts[0];
        final String anchor = parts[1];
        final String slug = slugifyHeading(anchor);
        final String alias = extractAlias(raw, target.isEmpty() && !anchor.isEmpty() ? anchor : target);

        final String href;
        if (target.isEmpty() && !slug.isEmpty()) {
            href = "#" + slug; // same-page anchor
        } else {
            final File resolved = resolve(notebookDir, currentFile, target, index);
            if (resolved == null) {
                return escapeDisplayText(raw);
            }
            // Raw HTML (unlike the body path, flexmark does not escape this attribute for us)
            href = ("file://" + resolved.getAbsolutePath().replace(" ", "%20")
                    + (slug.isEmpty() ? "" : "#" + slug))
                    .replace("\"", "%22").replace("<", "%3C").replace(">", "%3E");
        }
        return "<a href=\"" + href + "\">" + escapeDisplayText(alias) + "</a>";
    }

    /**
     * Escape display text for HTML — mirrors the previous value pipeline
     * (TextUtils.htmlEncode, then ---/-- to em/en dash). Callers trim the
     * overall result, so this intentionally does not trim.
     */
    private static String escapeDisplayText(final String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&#39;")
                .replace("\"", "&quot;")
                .replaceAll("(?<!-)---(?!-)", "&mdash;")
                .replaceAll("(?<!-)--(?!-)", "&ndash;");
    }

    /**
     * Rewrite Obsidian-style image references in a Markdown source:
     * <ul>
     *   <li>{@code ![alt|300](media/pic.jpg)} — Obsidian's size syntax — becomes
     *       an {@code <img>} tag with {@code width} (and optional {@code height})
     *       and the path resolved like wikilinks: note-relative, then
     *       notebook-root, then vault-wide.</li>
     *   <li>Plain images whose note-relative path does not exist but which
     *       resolve elsewhere in the vault are rewritten to the resolved
     *       {@code file://} URL.</li>
     *   <li>Working standard images (note-relative file exists, web/file/data
     *       URLs) and unresolvable ones are left untouched.</li>
     * </ul>
     */
    public static String rewriteObsidianImages(final String markup, final File notebookDir, final File currentFile) {
        return rewriteObsidianImages(markup, notebookDir, currentFile, null);
    }

    /** Same rewrite, sharing a per-conversion {@link VaultIndex}; null index falls back to the legacy walk. */
    public static String rewriteObsidianImages(final String markup, final File notebookDir, final File currentFile, final VaultIndex index) {
        if (markup == null || !markup.contains("](")) {
            return markup;
        }

        final Matcher m = MD_IMAGE.matcher(markup);
        final StringBuilder out = new StringBuilder(markup.length() + 64);
        while (m.find()) {
            final String original = m.group();
            final String alt = m.group(1);
            final String path = m.group(2).trim();
            final String replacement = rewriteImage(original, alt, path, notebookDir, currentFile, index);
            m.appendReplacement(out, Matcher.quoteReplacement(replacement != null ? replacement : original));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Returns the replacement for one image match, or null to keep the original. */
    private static String rewriteImage(final String original, final String alt, final String path, final File notebookDir, final File currentFile, final VaultIndex index) {
        // Web / already-absolute URLs are not ours to fix
        if (path.matches("(?i)^(https?|file|data|content):.*") || path.startsWith("//")) {
            return null;
        }

        // Obsidian size syntax: ![alt|300] / ![alt|300x200]
        String width = null;
        String height = null;
        String displayAlt = alt;
        final int pipe = alt.lastIndexOf('|');
        if (pipe >= 0) {
            final String size = alt.substring(pipe + 1).trim();
            if (size.matches("\\d+(x\\d+)?")) {
                width = size.contains("x") ? size.substring(0, size.indexOf('x')) : size;
                height = size.contains("x") ? size.substring(size.indexOf('x') + 1) : null;
                displayAlt = alt.substring(0, pipe).trim();
            }
        }

        final File parent = currentFile != null ? currentFile.getParentFile() : null;
        final boolean noteRelativeExists = parent != null && new File(parent, path).isFile();
        if (noteRelativeExists && width == null) {
            return null; // working standard markdown — leave alone
        }

        final File resolved = resolve(notebookDir, currentFile, path, index);
        if (resolved == null || !resolved.isFile()) {
            return null; // unresolvable: keep original (still renders via relative base)
        }

        final String href = "file://" + resolved.getAbsolutePath().replace(" ", "%20");
        if (width == null) {
            return "![" + displayAlt + "](" + href + ")";
        }
        final StringBuilder img = new StringBuilder("<img src=\"").append(href)
                .append("\" alt=\"").append(displayAlt.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;"))
                .append("\" width=\"").append(width).append("\"");
        if (height != null) {
            img.append(" height=\"").append(height).append("\"");
        }
        return img.append(" />").toString();
    }

    private static void rewriteLine(final String line, final File notebookDir, final File currentFile, final StringBuilder out, final VaultIndex index) {        final Matcher m = WIKI_LINK.matcher(line);
        int last = 0;
        while (m.find()) {
            final String raw = m.group();
            if (raw.startsWith("!")) {
                continue; // embeds are out of scope
            }
            final String[] parts = extractTargetAndAnchor(raw);
            final String target = parts[0];
            final String anchor = parts[1];
            final String slug = slugifyHeading(anchor);
            // Same-page links have no target; display the anchor text in that case
            final String alias = extractAlias(raw, target.isEmpty() && !anchor.isEmpty() ? anchor : target);

            final String href;
            if (target.isEmpty() && !slug.isEmpty()) {
                // Same-page anchor [[#Heading]] -> native fragment jump
                href = "#" + slug;
            } else {
                final File resolved = resolve(notebookDir, currentFile, target, index);
                if (resolved == null) {
                    continue; // unresolvable: keep the raw text
                }
                href = "file://" + resolved.getAbsolutePath().replace(" ", "%20")
                        + (slug.isEmpty() ? "" : "#" + slug);
            }
            out.append(line, last, m.start())
                    .append('[').append(alias).append("](").append(href).append(')');
            last = m.end();
        }
        out.append(line, last, line.length());
    }

    // ---------------------------------------------------------------------------------
    // tsun-markor fork: debug performance counters (phase-0 instrumentation).
    // Only accumulated while DBG_STATS is armed — MarkdownTextConverter arms it around
    // one conversion in debug builds and logs the numbers under the "tsun-perf" tag.
    // Cost when disarmed: one volatile read per vault walk.
    // ---------------------------------------------------------------------------------
    static volatile boolean DBG_STATS = false;
    static volatile long DBG_WALKS = 0;
    static volatile long DBG_WALK_MS = 0;

    static void dbgResetStats() {
        DBG_WALKS = 0;
        DBG_WALK_MS = 0;
    }

    /** Try the target (with each candidate extension appended) below base dir; first existing file wins. */
    private static File tryCandidates(final File targetDir) {
        // targetDir's path already contains the un-extended target: <base>/<target>
        if (targetDir == null) {
            return null;
        }
        final File parent = targetDir.getParentFile();
        if (parent == null) {
            return null;
        }
        final String base = targetDir.getName();
        for (final String ext : TARGET_EXTENSIONS) {
            if (!ext.isEmpty() && base.endsWith(ext)) {
                continue;
            }
            final File f = new File(parent, base + ext);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    /** Breadth-first, sorted walk of the vault; returns the best match (fewest path segments, then lexicographic) or null. */
    private static File searchVault(final File root, final String target) {
        final long t0 = DBG_STATS ? System.nanoTime() : 0L;
        final String baseName = target.substring(target.lastIndexOf('/') + 1);
        final Deque<File> queue = new ArrayDeque<>();
        queue.add(root);
        File best = null;
        String bestPath = null;
        int bestDepth = Integer.MAX_VALUE;
        while (!queue.isEmpty()) {
            final File dir = queue.poll();
            final File[] children = dir.listFiles();
            if (children == null) {
                continue;
            }
            Arrays.sort(children, (a, b) -> a.getName().compareTo(b.getName()));
            for (final File child : children) {
                if (child.isDirectory()) {
                    if (!child.getName().startsWith(".")) {
                        queue.add(child);
                    }
                } else if (matchesVaultEntry(child, root, target, baseName)) {
                    final String path = child.getAbsolutePath();
                    // Closest to the vault root wins ("shortest path" in Obsidian terms);
                    // equal depth is broken lexicographically for determinism
                    final int depth = countPathSegments(child, root);
                    if (best == null || depth < bestDepth
                            || (depth == bestDepth && path.compareTo(bestPath) < 0)) {
                        best = child;
                        bestPath = path;
                        bestDepth = depth;
                    }
                }
            }
        }
        if (DBG_STATS) {
            DBG_WALKS++;
            DBG_WALK_MS += (System.nanoTime() - t0) / 1_000_000L;
        }
        return best;
    }

    /** Number of path segments of {@code file} below {@code root} (file itself included). (package-private: also used by {@link VaultIndex}) */
    static int countPathSegments(final File file, final File root) {
        final String rel = relativeToRoot(file, root);
        if (rel == null || rel.isEmpty()) {
            return rel == null ? Integer.MAX_VALUE : 0;
        }
        int segments = 1;
        for (int i = 0; i < rel.length(); i++) {
            if (rel.charAt(i) == '/') {
                segments++;
            }
        }
        return segments;
    }

    private static boolean matchesVaultEntry(final File file, final File root, final String target, final String baseName) {
        final String name = file.getName();
        for (final String ext : TARGET_EXTENSIONS) {
            if (name.equals(baseName + ext)) {
                return true;
            }
        }
        // Slash targets may also match by their root-relative path
        final String rel = relativeToRoot(file, root);
        if (rel != null) {
            for (final String ext : TARGET_EXTENSIONS) {
                if (!ext.isEmpty() && rel.equals(target + ext)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** (package-private: also used by {@link VaultIndex}) */
    static String relativeToRoot(final File file, final File root) {
        final String rootPath = root.getAbsolutePath();
        final String filePath = file.getAbsolutePath();
        if (filePath.equals(rootPath)) {
            return "";
        }
        final String prefix = rootPath.endsWith("/") ? rootPath : rootPath + "/";
        return filePath.startsWith(prefix) ? filePath.substring(prefix.length()) : null;
    }

    /**
     * Lexically normalize a path (resolve {@code .} and {@code ..}) without
     * touching the filesystem. Always uses {@code /} as separator — valid on
     * Android and on the unit-test JVM.
     */
    private static File normalize(final File file) {
        final String path = file.getPath();
        final boolean absolute = path.startsWith("/");
        final List<String> out = new ArrayList<>();
        for (final String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!out.isEmpty() && !out.get(out.size() - 1).equals("..")) {
                    out.remove(out.size() - 1);
                } else if (!absolute) {
                    out.add("..");
                }
            } else {
                out.add(part);
            }
        }
        final StringBuilder sb = new StringBuilder(absolute ? "/" : "");
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(out.get(i));
        }
        return new File(sb.length() == 0 ? (absolute ? "/" : ".") : sb.toString());
    }
}
