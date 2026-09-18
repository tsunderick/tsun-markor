/*#######################################################
 *
 *   tsun-markor fork: per-conversion vault index for Obsidian wikilink resolution
 *   License of this file: Apache 2.0
 *
#########################################################*/
package net.gsantner.markor.format.markdown;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One-shot index of the notebook ("vault") directory, shared by every
 * wikilink/image/frontmatter rewrite of a single Markdown&rarr;HTML conversion.
 *
 * <p>Motivation: the legacy resolver performed a full breadth-first vault walk
 * for every target that could not be resolved by direct path — a document with
 * N such links cost N complete walks of the vault (every directory listed and
 * sorted, every entry stat'ed) on the UI thread. This class performs exactly
 * one walk and answers all further vault-wide lookups from in-memory maps.</p>
 *
 * <p>Lifecycle: instantiate per conversion (see
 * {@link MarkdownTextConverter#convertMarkup}), pass it through the rewrite
 * calls, discard afterwards. There is deliberately no caching beyond one
 * conversion — externally synced vaults change underneath the app, and a fresh
 * index cannot go stale.</p>
 *
 * <p>Semantics mirror the legacy walk exactly:</p>
 * <ul>
 *   <li>dot-directories are skipped, only files are candidates;</li>
 *   <li>a target matches a file by full name or full root-relative path, or by
 *       either with a known extension appended (see
 *       {@link ObsidianWikiLinkResolver#TARGET_EXTENSIONS});</li>
 *   <li>the winner is the candidate closest to the vault root (fewest path
 *       segments — Obsidian's "shortest path" rule), ties broken
 *       lexicographically by absolute path.</li>
 * </ul>
 *
 * <p>Pure java.io/java.util — unit-testable without Android. Parity with the
 * legacy walk is pinned by {@code VaultIndexParityTests}.</p>
 */
public final class VaultIndex {

    private final File root;
    private Map<String, List<File>> byName;
    private Map<String, List<File>> byRelPath;
    private boolean built;
    /** Duration of the single index build in milliseconds; &minus;1 before the first lookup. */
    public long buildMs = -1;

    public VaultIndex(final File root) {
        this.root = root;
    }

    /**
     * Vault-wide best match for the target, or null. Equivalent to the legacy
     * full-vault walk ({@code ObsidianWikiLinkResolver.searchVault}).
     */
    public File searchVault(final String target) {
        ensureBuilt();
        if (byName == null || target == null || target.isEmpty()) {
            return null;
        }
        final Set<File> candidates = new LinkedHashSet<>();
        addAll(candidates, byName.get(target));
        addAll(candidates, byRelPath.get(target));
        File best = null;
        String bestPath = null;
        int bestDepth = Integer.MAX_VALUE;
        for (final File candidate : candidates) {
            final String path = candidate.getAbsolutePath();
            // Same selection rule as the legacy walk: closest to the vault root
            // wins; equal depth is broken lexicographically for determinism
            final int depth = ObsidianWikiLinkResolver.countPathSegments(candidate, root);
            if (best == null || depth < bestDepth
                    || (depth == bestDepth && path.compareTo(bestPath) < 0)) {
                best = candidate;
                bestPath = path;
                bestDepth = depth;
            }
        }
        return best;
    }

    /** Build the index on first use (lazy — link-free conversions never pay for it). */
    private void ensureBuilt() {
        if (built) {
            return;
        }
        built = true;
        if (root == null) {
            return;
        }
        final long t0 = System.nanoTime();
        byName = new HashMap<>();
        byRelPath = new HashMap<>();
        final Deque<File> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            final File dir = queue.poll();
            final File[] children = dir.listFiles();
            if (children == null) {
                continue;
            }
            for (final File child : children) {
                if (child.isDirectory()) {
                    if (!child.getName().startsWith(".")) {
                        queue.add(child);
                    }
                } else {
                    indexFile(child);
                }
            }
        }
        buildMs = (System.nanoTime() - t0) / 1_000_000L;
    }

    /** Register one file under its name and root-relative path (plus extension-stripped variants). */
    private void indexFile(final File file) {
        final String name = file.getName();
        final String rel = ObsidianWikiLinkResolver.relativeToRoot(file, root);
        addEntry(byName, name, file);
        addEntry(byName, stripKnownExtension(name), file);
        addEntry(byRelPath, rel, file);
        addEntry(byRelPath, stripKnownExtension(rel), file);
    }

    /**
     * Strip one known target extension (".md", ".markdown", ".md.txt") from the
     * end of {@code s}, mirroring the legacy {@code baseName + ext} /
     * {@code target + ext} matching; null when nothing sensible was stripped.
     */
    private static String stripKnownExtension(final String s) {
        if (s == null) {
            return null;
        }
        for (final String ext : ObsidianWikiLinkResolver.TARGET_EXTENSIONS) {
            if (!ext.isEmpty() && s.endsWith(ext) && s.length() > ext.length()) {
                return s.substring(0, s.length() - ext.length());
            }
        }
        return null;
    }

    private static void addEntry(final Map<String, List<File>> map, final String key, final File file) {
        if (key == null || key.isEmpty()) {
            return;
        }
        map.computeIfAbsent(key, k -> new ArrayList<>(2)).add(file);
    }

    private static void addAll(final Set<File> out, final List<File> files) {
        if (files != null) {
            out.addAll(files);
        }
    }
}
