/*#######################################################
 *
 *   tsun-markor fork: Obsidian wikilink resolver tests
 *   License of this file: Apache 2.0
 *
#########################################################*/
package net.gsantner.markor.format.markdown;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.options.MutableDataSet;

import net.gsantner.opoc.util.GsFileUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ObsidianWikiLinkResolverTests {
    private Path tempFolder;
    private Path notebookRoot;

    /**
     * Creates the following notebook structure:
     * <pre>
     * notebookRoot ___ Unique.md
     * |              |_ Note B.md
     * |              |_ media ___ daily-note-waifu ___ download.jpg
     * |___ Alpha ___ Current.md
     * |           |_ Dup.md
     * |___ Beta ___ Dup.md
     * |___ Deep ___ Nested ___ Only_One.md
     *          |_ A ___ B ___ Note.md
     * </pre>
     */
    @Before
    public void before() {
        try {
            tempFolder = Files.createTempDirectory("markorTemp");
            notebookRoot = Files.createDirectory(tempFolder.resolve("notebookRoot"));
            Files.createFile(notebookRoot.resolve("Unique.md"));
            Files.createFile(notebookRoot.resolve("Note B.md"));
            Files.createDirectories(notebookRoot.resolve("media/daily-note-waifu"));
            Files.createFile(notebookRoot.resolve("media/daily-note-waifu/download.jpg"));
            Files.createDirectories(notebookRoot.resolve("Alpha"));
            Files.createFile(notebookRoot.resolve("Alpha/Current.md"));
            Files.createFile(notebookRoot.resolve("Alpha/Dup.md"));
            Files.createDirectories(notebookRoot.resolve("Beta"));
            Files.createFile(notebookRoot.resolve("Beta/Dup.md"));
            Files.createDirectories(notebookRoot.resolve("Deep/Nested"));
            Files.createFile(notebookRoot.resolve("Deep/Nested/Only_One.md"));
            Files.createDirectories(notebookRoot.resolve("Deep/A/B"));
            Files.createFile(notebookRoot.resolve("Deep/A/B/Note.md"));
            System.out.println("Created test notebook in: " + tempFolder);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Could not create the test directory", e);
        }
    }

    @After
    public void after() {
        GsFileUtils.deleteRecursive(tempFolder.toFile());
        System.out.println("Deleted: " + tempFolder);
    }

    private File file(final String relPath) {
        return notebookRoot.resolve(relPath).toFile();
    }

    //#############################################################################################
    //## resolve()
    //#############################################################################################

    @Test
    public void resolvesBareTargetVaultWide() {
        final File resolved = ObsidianWikiLinkResolver.resolve(
                notebookRoot.toFile(), file("Alpha/Current.md"), "Unique");
        assertEquals(file("Unique.md").getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    public void resolvesTargetInCurrentFolder() {
        final File resolved = ObsidianWikiLinkResolver.resolve(
                notebookRoot.toFile(), file("Alpha/Current.md"), "Dup");
        assertEquals(file("Alpha/Dup.md").getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    public void resolvesVaultWideShortestPathWhenNotLocal() {
        final File resolved = ObsidianWikiLinkResolver.resolve(
                notebookRoot.toFile(), file("Unique.md"), "Dup");
        assertEquals(file("Alpha/Dup.md").getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    public void resolvesVaultWideByFewestSegmentsNotByStringLength() {
        // Regression: "s" (4 chars) is shorter than "AVeryLongFolder" — but the match closest
        // to the vault root (fewest segments) must win, regardless of folder name length
        try {
            Files.createDirectories(notebookRoot.resolve("AVeryLongFolder"));
            Files.createFile(notebookRoot.resolve("AVeryLongFolder/Second.md"));
            Files.createDirectories(notebookRoot.resolve("s"));
            Files.createFile(notebookRoot.resolve("s/Second.md"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final File resolved = ObsidianWikiLinkResolver.resolve(
                notebookRoot.toFile(), file("Unique.md"), "Second");
        assertEquals(file("AVeryLongFolder/Second.md").getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    public void resolvesTargetWithExtension() {
        final File resolved = ObsidianWikiLinkResolver.resolve(
                notebookRoot.toFile(), file("Alpha/Current.md"), "Unique.md");
        assertEquals(file("Unique.md").getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    public void resolvesNestedTargetByRootRelativePath() {
        final File resolved = ObsidianWikiLinkResolver.resolve(
                notebookRoot.toFile(), file("Alpha/Current.md"), "Deep/Nested/Only_One");
        assertEquals(file("Deep/Nested/Only_One.md").getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    public void resolvesDotSlashRelativeTarget() {
        final File resolved = ObsidianWikiLinkResolver.resolve(
                notebookRoot.toFile(), file("Alpha/Current.md"), "./Dup");
        assertEquals(file("Alpha/Dup.md").getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    public void resolvesParentRelativeTarget() {
        final File resolved = ObsidianWikiLinkResolver.resolve(
                notebookRoot.toFile(), file("Alpha/Current.md"), "../Note B");
        assertEquals(file("Note B.md").getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    public void fallsBackToNotebookRootForMissingTarget() {
        final File resolved = ObsidianWikiLinkResolver.resolve(
                notebookRoot.toFile(), file("Alpha/Current.md"), "Missing");
        assertEquals(file("Missing.md").getAbsolutePath(), resolved.getAbsolutePath());
        assertTrue("Fallback target need not exist yet", !resolved.exists());
    }

    @Test
    public void returnsNullForEmptyTargetOrMissingNotebook() {
        assertNull(ObsidianWikiLinkResolver.resolve(notebookRoot.toFile(), null, ""));
        assertNull(ObsidianWikiLinkResolver.resolve(null, null, "Unique"));
    }

    //#############################################################################################
    //## extractTarget() / extractAlias()
    //#############################################################################################

    @Test
    public void extractTargetStripsAliasAndFragmentAndEmbedMarker() {
        assertEquals("Note B", ObsidianWikiLinkResolver.extractTarget("[[Note B|Alias]]"));
        assertEquals("Note", ObsidianWikiLinkResolver.extractTarget("[[Note#Section]]"));
        assertEquals("Note", ObsidianWikiLinkResolver.extractTarget("[[Note#Section|Alias]]"));
        assertEquals("Note", ObsidianWikiLinkResolver.extractTarget("![[Note]]"));
        assertEquals("./sub/Note", ObsidianWikiLinkResolver.extractTarget("[[./sub/Note]]"));
        assertEquals("", ObsidianWikiLinkResolver.extractTarget("[[|Alias]]"));
    }

    @Test
    public void extractAliasFallsBackToTarget() {
        assertEquals("The Alias", ObsidianWikiLinkResolver.extractAlias("[[Note B|The Alias]]", "Note B"));
        assertEquals("Note B", ObsidianWikiLinkResolver.extractAlias("[[Note B]]", "Note B"));
        assertEquals("Note B", ObsidianWikiLinkResolver.extractAlias("[[Note B|]]", "Note B"));
    }

    //#############################################################################################
    //## rewriteWikiLinks()
    //#############################################################################################

    @Test
    public void rewriteProducesAliasLink() {
        final String out = ObsidianWikiLinkResolver.rewriteWikiLinks(
                "see [[Unique|The Alias]] here", notebookRoot.toFile(), file("Alpha/Current.md"));
        assertEquals("see [The Alias](file://" + file("Unique.md").getAbsolutePath() + ") here", out);
    }

    @Test
    public void rewriteProducesPlainLinkWithTargetAsText() {
        final String out = ObsidianWikiLinkResolver.rewriteWikiLinks(
                "see [[Unique]]", notebookRoot.toFile(), file("Alpha/Current.md"));
        assertEquals("see [Unique](file://" + file("Unique.md").getAbsolutePath() + ")", out);
    }

    @Test
    public void rewriteEscapesSpacesInUrl() {
        final String out = ObsidianWikiLinkResolver.rewriteWikiLinks(
                "see [[Note B]]", notebookRoot.toFile(), file("Alpha/Current.md"));
        assertEquals("see [Note B](file://" + file("Note B.md").getAbsolutePath().replace(" ", "%20") + ")", out);
    }

    @Test
    public void rewriteLeavesEmbedsUntouched() {
        final String markup = "embed ![[Unique]] stays";
        assertEquals(markup, ObsidianWikiLinkResolver.rewriteWikiLinks(markup, notebookRoot.toFile(), null));
    }

    @Test
    public void rewriteLeavesFencedCodeUntouched() {
        final String markup = "```\n[[Unique]]\n```\n";
        assertEquals(markup, ObsidianWikiLinkResolver.rewriteWikiLinks(markup, notebookRoot.toFile(), null));
    }

    @Test
    public void rewriteLeavesEmptyTargetUntouched() {
        final String markup = "weird [[|Alias]] stays";
        assertEquals(markup, ObsidianWikiLinkResolver.rewriteWikiLinks(markup, notebookRoot.toFile(), null));
    }

    @Test
    public void rewriteWithoutWikiLinksIsIdentity() {
        final String markup = "# Title\n\nSome [regular](link.md) markdown.\n";
        assertEquals(markup, ObsidianWikiLinkResolver.rewriteWikiLinks(markup, notebookRoot.toFile(), null));
    }

    @Test
    public void rewriteRewritesMultipleLinksOnOneLine() {
        final String out = ObsidianWikiLinkResolver.rewriteWikiLinks(
                "[[Unique]] and [[Alpha/Dup|the dup]]",
                notebookRoot.toFile(), file("Alpha/Current.md"));
        assertEquals("[Unique](file://" + file("Unique.md").getAbsolutePath()
                        + ") and [the dup](file://" + file("Alpha/Dup.md").getAbsolutePath() + ")",
                out);
    }

    //#############################################################################################
    //## heading anchors ([[Note#Heading]], [[#Heading]])
    //#############################################################################################

    @Test
    public void extractTargetAndAnchorSplitsAnchor() {
        assertArrayEquals(new String[]{"Note", "Some Heading", "Alias"},
                ObsidianWikiLinkResolver.extractTargetAndAnchor("[[Note#Some Heading|Alias]]"));
        assertArrayEquals(new String[]{"Note", "", "Alias"},
                ObsidianWikiLinkResolver.extractTargetAndAnchor("[[Note|Alias]]"));
        assertArrayEquals(new String[]{"Note", "", null},
                ObsidianWikiLinkResolver.extractTargetAndAnchor("[[Note]]"));
        assertArrayEquals(new String[]{"", "Heading", null},
                ObsidianWikiLinkResolver.extractTargetAndAnchor("[[#Heading]]"));
        // extractTarget keeps stripping the anchor
        assertEquals("Note", ObsidianWikiLinkResolver.extractTarget("[[Note#Section]]"));
    }

    /**
     * The equivalence contract of the whole anchor feature: slugifyHeading must
     * produce byte-identical ids to what the Markdown preview renderer emits
     * (same flexmark generator, same options). Rendered here with the exact
     * options MarkdownTextConverter sets.
     */
    @Test
    public void slugifyHeadingMatchesFlexmarkRenderedIds() {
        final String[] headings = {
                "2026-09-17 Thursday", "Hello World", "Some Heading!", "Mixed_Case-dash",
                "Trimmed", "1. Numeric start", "C++ & C#", "note 2026-W38",
                "emoji heading", "日本語 heading", "Über Änderung"
        };
        final StringBuilder md = new StringBuilder();
        for (final String h : headings) {
            md.append("# ").append(h).append("\n\n");
        }

        final MutableDataSet options = new MutableDataSet();
        options.set(HtmlRenderer.GENERATE_HEADER_ID, true)
                .set(HtmlRenderer.RENDER_HEADER_ID, true)
                .set(HtmlRenderer.HEADER_ID_GENERATOR_RESOLVE_DUPES, true);
        final Parser parser = Parser.builder().build();
        final HtmlRenderer renderer = HtmlRenderer.builder().build();
        final String html = renderer.withOptions(options).render(parser.parse(md.toString()));

        final Pattern idPattern = Pattern.compile("<h1 id=\"([^\"]*)\">");
        final Matcher idMatcher = idPattern.matcher(html);
        for (final String heading : headings) {
            assertTrue("Renderer emitted fewer ids than headings", idMatcher.find());
            assertEquals("slug must equal rendered id for: " + heading,
                    ObsidianWikiLinkResolver.slugifyHeading(heading), idMatcher.group(1));
        }
        assertFalse(idMatcher.find());
    }

    @Test
    public void rewriteAppendsSlugToResolvedLink() {
        final String out = ObsidianWikiLinkResolver.rewriteWikiLinks(
                "see [[Unique#Some Heading!|jump]] here", notebookRoot.toFile(), file("Alpha/Current.md"));
        assertEquals("see [jump](file://" + file("Unique.md").getAbsolutePath() + "#some-heading) here", out);
    }

    @Test
    public void rewriteAppendsNothingWhenSlugIsEmpty() {
        final String out = ObsidianWikiLinkResolver.rewriteWikiLinks(
                "see [[Unique#!!!]]", notebookRoot.toFile(), file("Alpha/Current.md"));
        assertEquals("see [Unique](file://" + file("Unique.md").getAbsolutePath() + ")", out);
    }

    @Test
    public void rewriteSamePageAnchorToFragmentHref() {
        final String out = ObsidianWikiLinkResolver.rewriteWikiLinks(
                "jump [[#2026-09-17 Thursday]]", notebookRoot.toFile(), null);
        assertEquals("jump [2026-09-17 Thursday](#2026-09-17-thursday)", out);
    }

    @Test
    public void rewriteLeavesAnchorlessUnresolvableUntouched() {
        final String markup = "[[|Alias]] stays";
        assertEquals(markup, ObsidianWikiLinkResolver.rewriteWikiLinks(markup, notebookRoot.toFile(), null));
    }

    //#############################################################################################
    //## wikiTextToHtmlLinks() — clickable links in frontmatter values
    //#############################################################################################

    /** The legacy value pipeline (pre-linkify): htmlEncode + em/en dash + trim. */
    private static String legacyEscape(final String text) {
        return text
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&#39;").replace("\"", "&quot;")
                .replaceAll("(?<!-)---(?!-)", "&mdash;")
                .replaceAll("(?<!-)--(?!-)", "&ndash;")
                .trim();
    }

    @Test
    public void linkifyProducesAnchorForResolvableTarget() {
        assertEquals("<a href=\"file://" + file("Unique.md").getAbsolutePath() + "\">Back</a>",
                ObsidianWikiLinkResolver.wikiTextToHtmlLinks("[[Unique|Back]]", notebookRoot.toFile(), file("Alpha/Current.md")));
    }

    @Test
    public void linkifyResolvesMissingTargetToFallbackLink() {
        // Consistent with body wikilinks: missing notes link to the fallback path,
        // so tapping offers the usual document-creation flow
        assertEquals("<a href=\"file://" + file("Missing.md").getAbsolutePath() + "\">Missing</a>",
                ObsidianWikiLinkResolver.wikiTextToHtmlLinks("[[Missing]]", notebookRoot.toFile(), null));
    }

    @Test
    public void linkifyWithoutNotebookKeepsLiteral() {
        assertEquals("[[Missing]]",
                ObsidianWikiLinkResolver.wikiTextToHtmlLinks("[[Missing]]", null, null));
        // HTML chars in the literal stay escaped
        assertEquals("[[a&lt;b]]",
                ObsidianWikiLinkResolver.wikiTextToHtmlLinks("[[a<b]]", null, null));
    }

    @Test
    public void linkifyEscapesSurroundingTextAndAlias() {
        assertEquals("a &lt;b&gt; <a href=\"file://" + file("Unique.md").getAbsolutePath() + "\">&quot;x&#39;y&quot; &amp;</a> &amp; c",
                ObsidianWikiLinkResolver.wikiTextToHtmlLinks("a <b> [[Unique|\"x'y\" &]] & c", notebookRoot.toFile(), null));
    }

    @Test
    public void linkifySamePageAnchorToFragment() {
        assertEquals("<a href=\"#some-heading\">Some Heading</a>",
                ObsidianWikiLinkResolver.wikiTextToHtmlLinks("[[#Some Heading]]", notebookRoot.toFile(), null));
    }

    @Test
    public void linkifyAppendsHeadingSlug() {
        assertEquals("<a href=\"file://" + file("Unique.md").getAbsolutePath() + "#some-heading\">jump</a>",
                ObsidianWikiLinkResolver.wikiTextToHtmlLinks("[[Unique#Some Heading!|jump]]", notebookRoot.toFile(), null));
    }

    @Test
    public void linkifyMultipleLinksInOneValue() {
        assertEquals("<a href=\"file://" + file("Unique.md").getAbsolutePath() + "\">a</a> and <a href=\"file://" + file("Alpha/Dup.md").getAbsolutePath() + "\">b</a>",
                ObsidianWikiLinkResolver.wikiTextToHtmlLinks("[[Unique|a]] and [[Alpha/Dup|b]]", notebookRoot.toFile(), null));
    }

    @Test
    public void linklessValueIsByteIdenticalToLegacyPipeline() {
        final String[] values = {
                "plain value",
                "with <html> & \"quotes\" and 'apostrophes'",
                "em --- dash and en -- dash",
                "  padded  ",
                "2026-09-17 Thursday"
        };
        for (final String v : values) {
            assertEquals("parity broken for: " + v, legacyEscape(v),
                    ObsidianWikiLinkResolver.wikiTextToHtmlLinks(v, notebookRoot.toFile(), null));
        }
    }

    //#############################################################################################
    //## rewriteObsidianImages() — ![alt|300](vault-relative) image handling
    //#############################################################################################

    /** Deep/A/B/Note.md — three levels below the vault root, like the user's daily notes. */
    private File deepNote() {
        return file("Deep/A/B/Note.md");
    }

    @Test
    public void imageWithSizeRewrittenToImgTag() {
        assertEquals("<img src=\"file://" + file("media/daily-note-waifu/download.jpg").getAbsolutePath()
                        + "\" alt=\"Photo of the Day\" width=\"300\" />",
                ObsidianWikiLinkResolver.rewriteObsidianImages(
                        "![Photo of the Day|300](media/daily-note-waifu/download.jpg)",
                        notebookRoot.toFile(), deepNote()));
    }

    @Test
    public void imageWithWidthAndHeight() {
        assertEquals("<img src=\"file://" + file("media/daily-note-waifu/download.jpg").getAbsolutePath()
                        + "\" alt=\"a\" width=\"300\" height=\"200\" />",
                ObsidianWikiLinkResolver.rewriteObsidianImages(
                        "![a|300x200](media/daily-note-waifu/download.jpg)",
                        notebookRoot.toFile(), deepNote()));
    }

    @Test
    public void workingRelativeImageUntouched() {
        // The "../../../" form already works — must not be touched
        final String md = "![download](../../../media/daily-note-waifu/download.jpg)";
        assertEquals(md, ObsidianWikiLinkResolver.rewriteObsidianImages(md, notebookRoot.toFile(), deepNote()));
    }

    @Test
    public void blockquoteWrappedImageRewritten() {
        final String out = ObsidianWikiLinkResolver.rewriteObsidianImages(
                "> ![Photo of the Day|300](media/daily-note-waifu/download.jpg)",
                notebookRoot.toFile(), deepNote());
        assertTrue("blockquote prefix preserved", out.startsWith("> <img "));
        assertTrue(out.contains("width=\"300\""));
    }

    @Test
    public void vaultResolvedImageWithoutSizeRewrittenToFileUrl() {
        assertEquals("![](file://" + file("media/daily-note-waifu/download.jpg").getAbsolutePath() + ")",
                ObsidianWikiLinkResolver.rewriteObsidianImages(
                        "![](media/daily-note-waifu/download.jpg)",
                        notebookRoot.toFile(), deepNote()));
    }

    @Test
    public void noteRelativeExistingImageWithSizeGetsImgTag() throws IOException {
        Files.createFile(notebookRoot.resolve("Deep/A/B/local.jpg"));
        assertEquals("<img src=\"file://" + file("Deep/A/B/local.jpg").getAbsolutePath()
                        + "\" alt=\"x\" width=\"100\" />",
                ObsidianWikiLinkResolver.rewriteObsidianImages(
                        "![x|100](local.jpg)", notebookRoot.toFile(), deepNote()));
    }

    @Test
    public void webUrlAndMissingImagesUntouched() {
        assertEquals("![x](https://example.com/a.jpg)",
                ObsidianWikiLinkResolver.rewriteObsidianImages("![x](https://example.com/a.jpg)", notebookRoot.toFile(), deepNote()));
        assertEquals("![x](media/nope.jpg)",
                ObsidianWikiLinkResolver.rewriteObsidianImages("![x](media/nope.jpg)", notebookRoot.toFile(), deepNote()));
    }

    @Test
    public void embedFormStillUntouched() {
        // ![[...]] embeds remain out of scope
        final String md = "> ![[media/daily-note-waifu/download.jpg]]";
        assertEquals(md, ObsidianWikiLinkResolver.rewriteObsidianImages(md, notebookRoot.toFile(), deepNote()));
    }
}
