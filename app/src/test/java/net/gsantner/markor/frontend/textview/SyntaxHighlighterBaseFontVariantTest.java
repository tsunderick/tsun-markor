/*#######################################################
 *
 *   tsun-markor fork: true bold/italic face path derivation tests
 *   License of this file: Apache 2.0
 *
#########################################################*/
package net.gsantner.markor.frontend.textview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SyntaxHighlighterBaseFontVariantTest {

    private static final String OP_REGULAR_TTF = "/android_asset/fonts/Operator Mono - Regular.ttf";
    private static final String OP_REGULAR_OTF = "/android_asset/fonts/Operator Mono - Regular.otf";

    @Test
    public void italicVariantDerived() {
        assertEquals("/android_asset/fonts/Operator Mono - Italic.ttf",
                SyntaxHighlighterBase.getVariantFontPath(OP_REGULAR_TTF, false, true));
    }

    @Test
    public void boldVariantDerived() {
        assertEquals("/android_asset/fonts/Operator Mono - Bold.ttf",
                SyntaxHighlighterBase.getVariantFontPath(OP_REGULAR_TTF, true, false));
    }

    @Test
    public void boldItalicVariantDerived() {
        assertEquals("/android_asset/fonts/Operator Mono - Bold Italic.ttf",
                SyntaxHighlighterBase.getVariantFontPath(OP_REGULAR_TTF, true, true));
    }

    @Test
    public void otfExtensionPreserved() {
        assertEquals("/android_asset/fonts/Operator Mono - Bold Italic.otf",
                SyntaxHighlighterBase.getVariantFontPath(OP_REGULAR_OTF, true, true));
    }

    @Test
    public void derivationIndependentOfChosenFace() {
        // Even if the user picked the italic file as their editor font, variant
        // derivation resolves through the family part
        assertEquals("/android_asset/fonts/Operator Mono - Bold Italic.ttf",
                SyntaxHighlighterBase.getVariantFontPath("/android_asset/fonts/Operator Mono - Bold Italic.ttf", true, true));
        assertEquals("/android_asset/fonts/Operator Mono - Italic.ttf",
                SyntaxHighlighterBase.getVariantFontPath("/android_asset/fonts/Operator Mono - Bold.ttf", false, true));
    }

    @Test
    public void fontWithoutVariantSeparatorReturnsNull() {
        assertNull(SyntaxHighlighterBase.getVariantFontPath("/android_asset/fonts/Lato.ttf", false, true));
        assertNull(SyntaxHighlighterBase.getVariantFontPath("/storage/emulated/0/Fonts/MyFont.otf", true, false));
    }

    @Test
    public void pathWithoutExtensionReturnsNull() {
        assertNull(SyntaxHighlighterBase.getVariantFontPath("/android_asset/fonts/Operator Mono - Regular", true, false));
    }

    @Test
    public void degenerateInputsReturnNull() {
        assertNull(SyntaxHighlighterBase.getVariantFontPath(null, true, true));
        assertNull(SyntaxHighlighterBase.getVariantFontPath("", true, true));
        assertNull(SyntaxHighlighterBase.getVariantFontPath(OP_REGULAR_TTF, false, false));
    }

    @Test
    public void lastSeparatorWins() {
        // Family names may themselves contain " - "; the last separator splits face from family
        assertEquals("/android_asset/fonts/Some - Family - Italic.ttf",
                SyntaxHighlighterBase.getVariantFontPath("/android_asset/fonts/Some - Family - Regular.ttf", false, true));
    }
}
