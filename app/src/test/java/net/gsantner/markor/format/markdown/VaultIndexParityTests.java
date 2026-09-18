/*#######################################################
 *
 *   tsun-markor fork: VaultIndex parity tests
 *   License of this file: Apache 2.0
 *
#########################################################*/
package net.gsantner.markor.format.markdown;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import net.gsantner.opoc.util.GsFileUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pins {@link VaultIndex}'s vault-wide search to identical results with the
 * legacy per-link full-vault walk: for a battery of targets × current files,
 * {@code resolve(dir, current, target, index)} must return exactly what the
 * legacy {@code resolve(dir, current, target)} (index == null, full walks)
 * returns.
 */
public class VaultIndexParityTests {
    private Path tempFolder;
    private Path notebookRoot;

    /**
     * Test notebook:
     * <pre>
     * notebookRoot ___ Unique.md, Note B.md, Plain, Dup.md.txt
     *              |_ media/daily-note-waifu/download.jpg
     *              |_ Alpha ___ Current.md, Dup.md, Dup.markdown
     *              |_ Beta ____ Dup.md
     *              |_ Deep _____ Note.md, Nested/Only_One.md, A/B/Note.md
     *              |_ .hiddendir/Hidden.md          (skipped by both paths)
     * </pre>
     */
    @Before
    public void before() {
        try {
            tempFolder = Files.createTempDirectory("markorTemp");
            notebookRoot = Files.createDirectory(tempFolder.resolve("notebookRoot"));
            Files.createFile(notebookRoot.resolve("Unique.md"));
            Files.createFile(notebookRoot.resolve("Note B.md"));
            Files.createFile(notebookRoot.resolve("Plain")); // no known extension
            Files.createFile(notebookRoot.resolve("Dup.md.txt")); // double extension
            Files.createDirectories(notebookRoot.resolve("media/daily-note-waifu"));
            Files.createFile(notebookRoot.resolve("media/daily-note-waifu/download.jpg"));
            Files.createDirectories(notebookRoot.resolve("Alpha"));
            Files.createFile(notebookRoot.resolve("Alpha/Current.md"));
            Files.createFile(notebookRoot.resolve("Alpha/Dup.md"));
            Files.createFile(notebookRoot.resolve("Alpha/Dup.markdown"));
            Files.createDirectories(notebookRoot.resolve("Beta"));
            Files.createFile(notebookRoot.resolve("Beta/Dup.md"));
            Files.createDirectories(notebookRoot.resolve("Deep/Nested"));
            Files.createFile(notebookRoot.resolve("Deep/Nested/Only_One.md"));
            Files.createDirectories(notebookRoot.resolve("Deep/A/B"));
            Files.createFile(notebookRoot.resolve("Deep/A/B/Note.md"));
            Files.createFile(notebookRoot.resolve("Deep/Note.md")); // fewest segments wins
            Files.createDirectories(notebookRoot.resolve(".hiddendir"));
            Files.createFile(notebookRoot.resolve(".hiddendir/Hidden.md"));
        } catch (IOException e) {
            throw new RuntimeException("Could not create the test directory", e);
        }
    }

    @After
    public void after() {
        GsFileUtils.deleteRecursive(tempFolder.toFile());
    }

    private File file(final String relPath) {
        return notebookRoot.resolve(relPath).toFile();
    }

    private static final String[] CURRENT_FILES = {
            "Unique.md", "Alpha/Current.md", "Deep/A/B/Note.md", "media/daily-note-waifu/download.jpg",
    };

    private static final String[] TARGETS = {
            "Unique", "Unique.md", "Unique.markdown",
            "Note B", "Note B.md",
            "Dup", "Dup.md", "Dup.markdown", "Dup.md.txt",
            "Plain", "Plain.md",
            "download.jpg",
            "Note", "Note.md", // fewest-segments: Deep/Note.md beats Deep/A/B/Note.md
            "Deep/A/B/Note", "Deep/A/B/Note.md",
            "Deep/Note.md",
            "media/daily-note-waifu/download.jpg",
            "Alpha/Dup",
            "Only_One", "Only_One.md", "Deep/Nested/Only_One.markdown",
            "Hidden", // only inside a dot-dir — must stay unresolved for both
            "Missing", "Missing.md",
            "./Dup", "../Beta/Dup", "./nope/../../Beta/Dup.md",
            "Note#Heading", "Deep/A/B/Note#Some Heading", "#JustAnAnchor",
            "", "   ",
    };

    @Test
    public void indexResolvesIdenticalToLegacyWalk() {
        final VaultIndex index = new VaultIndex(notebookRoot.toFile());
        for (final String currentRel : CURRENT_FILES) {
            final File current = file(currentRel);
            for (final String target : TARGETS) {
                final File legacy = ObsidianWikiLinkResolver.resolve(notebookRoot.toFile(), current, target);
                final File indexed = ObsidianWikiLinkResolver.resolve(notebookRoot.toFile(), current, target, index);
                assertEquals("target=\"" + target + "\" from " + currentRel,
                        legacy == null ? null : legacy.getAbsolutePath(),
                        indexed == null ? null : indexed.getAbsolutePath());
            }
        }
    }

    @Test
    public void indexPicksFewestSegmentsForBareName() {
        final VaultIndex index = new VaultIndex(notebookRoot.toFile());
        final File indexed = index.searchVault("Note");
        assertEquals(file("Deep/Note.md").getAbsolutePath(), indexed.getAbsolutePath());
    }

    @Test
    public void indexSkipsDotDirectories() {
        final VaultIndex index = new VaultIndex(notebookRoot.toFile());
        assertNull(index.searchVault("Hidden"));
        assertNull(index.searchVault(""));
        assertNull(index.searchVault(null));
    }

    @Test
    public void indexResolvesByRootRelativePath() {
        final VaultIndex index = new VaultIndex(notebookRoot.toFile());
        assertEquals(file("Deep/A/B/Note.md").getAbsolutePath(),
                index.searchVault("Deep/A/B/Note").getAbsolutePath());
        assertEquals(file("media/daily-note-waifu/download.jpg").getAbsolutePath(),
                index.searchVault("media/daily-note-waifu/download.jpg").getAbsolutePath());
    }

    @Test
    public void rewriteWithIndexMatchesLegacyRewrite() {
        final VaultIndex index = new VaultIndex(notebookRoot.toFile());
        final File current = file("Alpha/Current.md");
        final String[] samples = {
                "[[Unique]] and [[Note|alias]] and [[Note#Heading|a2]]",
                "[[Missing Note]] stays literal",
                "![alt|300](media/daily-note-waifu/download.jpg) and ![x](../Beta/Dup.md)",
                "```\n[[Dup]] inside fence stays\n```",
                "[[#SamePage|top]]",
        };
        for (final String markup : samples) {
            assertEquals(markup,
                    ObsidianWikiLinkResolver.rewriteWikiLinks(markup, notebookRoot.toFile(), current),
                    ObsidianWikiLinkResolver.rewriteWikiLinks(markup, notebookRoot.toFile(), current, index));
            assertEquals(markup,
                    ObsidianWikiLinkResolver.rewriteObsidianImages(markup, notebookRoot.toFile(), current),
                    ObsidianWikiLinkResolver.rewriteObsidianImages(markup, notebookRoot.toFile(), current, index));
            assertEquals(markup,
                    ObsidianWikiLinkResolver.wikiTextToHtmlLinks(markup, notebookRoot.toFile(), current),
                    ObsidianWikiLinkResolver.wikiTextToHtmlLinks(markup, notebookRoot.toFile(), current, index));
        }
    }

    @Test
    public void nullNotebookDirBehavesLikeLegacy() {
        final VaultIndex index = new VaultIndex(null);
        assertNull(ObsidianWikiLinkResolver.resolve(null, file("Alpha/Current.md"), "Unique", index));
        assertNull(index.searchVault("Unique"));
        assertEquals("", ObsidianWikiLinkResolver.wikiTextToHtmlLinks(null, notebookRoot.toFile(), null, index));
    }
}
