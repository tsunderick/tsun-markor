/*#######################################################
 *
 *   Maintained 2018-2025 by Gregor Santner <gsantner AT mailbox DOT org>
 *   License of this file: Apache 2.0
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
#########################################################*/
package net.gsantner.markor.format;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.text.format.DateFormat;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import net.gsantner.markor.R;
import net.gsantner.markor.model.AppSettings;
import net.gsantner.markor.model.Document;
import net.gsantner.opoc.format.GsTextUtils;
import net.gsantner.opoc.util.GsContextUtils;
import net.gsantner.opoc.util.GsFileUtils;

import java.io.File;
import java.util.Date;
import java.util.Locale;

import other.de.stanetz.jpencconverter.JavaPasswordbasedCryption;

@SuppressWarnings("WeakerAccess")
public abstract class TextConverterBase {
    //########################
    //## HTML
    //########################
    protected static final String UTF_CHARSET = "utf-8";
    protected static final String CONTENT_TYPE_HTML = "text/html";
    // protected static final String CONTENT_TYPE_PLAIN = "text/plain";

    protected static final String CSS_S = "<style type='text/css'>";
    protected static final String CSS_E = "</style>";
    protected static final String JS_S = "<script>";
    protected static final String JS_E = "</script>";

    protected static final String TOKEN_TEXT_DIRECTION = "{{ app.text_direction }}"; // this is either 'right' or 'left'
    protected static final String TOKEN_FONT = "{{ app.text_font }}";
    protected static final String TOKEN_BW_INVERSE_OF_THEME = "{{ app.token_bw_inverse_of_theme }}";
    protected static final String TOKEN_BW_INVERSE_OF_THEME_HEADER_UNDERLINE = "{{ app.token_headline_underline_inverse_of_theme }}";
    protected static final String TOKEN_COLOR_GREY_OF_THEME = "{{ app.token_color_grey_inverse_of_theme }}";
    protected static final String TOKEN_LINK_COLOR = "{{ app.token_link_color }}";
    protected static final String TOKEN_ACCENT_COLOR = "{{ app.token_accent_color }}";
    protected static final String TOKEN_TEXT_CONVERTER_CSS_CLASS = "{{ post.text_converter_name }}";
    protected static final String TOKEN_TEXT_CONVERTER_MAX_ZOOM_OUT_BY_DEFAULT = "{{ app.webview_max_zoom_out_by_default }}";
    protected static final String TOKEN_POST_TODAY_DATE = "{{ post.date_today }}";
    protected static final String TOKEN_POST_LANG = "{{ post.lang }}";
    protected static final String TOKEN_FILEURI_VIEWED_FILE = "{{ app.fileuri_viewed_file }}";

    protected static final String HTML_DOCTYPE = "<!DOCTYPE html>";
    // tsunderick: never let the page itself scroll horizontally (a wide table used to
    // widen the whole document); wide tables scroll within their own box instead
    protected static final String HTML001_HEAD_WITH_BASESTYLE = "<html lang='" + TOKEN_POST_LANG + "'><head><meta charset='UTF-8'>" + CSS_S + "html,body{padding:4px 8px 4px 8px;font-family:'" + TOKEN_FONT + "';}h1,h2,h3,h4,h5,h6{font-family:'sans-serif-condensed';}a{color: " + TOKEN_LINK_COLOR + ";text-decoration:underline;}img{height:auto;max-width:100%;max-height: 90vh;margin:auto;}html,body{overflow-x:hidden;}table{display:block;overflow-x:auto;}" + CSS_E;
    protected static final String HTML002_HEAD_WITH_STYLE_LIGHT = CSS_S + "html,body{color:#303030;}blockquote{color:#73747d;}" + CSS_E;
    // tsunderick: OLED dark style - pure black, warm white text, sakura accents
    protected static final String HTML002_HEAD_WITH_STYLE_DARK = CSS_S + "html,body{color:#f0eaed;background-color:#000000;}a:link,a:visited{color:#ff8fb1;}blockquote{color:#da9fdc;border-left:3px solid #ff8fb1;padding-left:8px;margin-left:0;margin-right:0;}code,pre{background-color:#111111;}" + CSS_E;
    protected static final String HTML003_RIGHT_TO_LEFT = CSS_S + "body{text-align:" + TOKEN_TEXT_DIRECTION + ";direction:rtl;}" + CSS_E;
    protected static final String HTML004_HEAD_META_VIEWPORT_MOBILE = "<style>video, img { max-width: 100%; } pre { max-width: 100%; overflow: auto; } </style>";//"<meta name='viewport' content='width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no'>";
    protected static final String HTML100_PERCENT_IN_FILEPATH = "<base>" + JS_S + "var newbase = document.baseURI.split('%').join('%25'); document.querySelector('base').setAttribute('href', newbase);" + JS_E;
    protected static final String CSS_TABLE_STYLE = CSS_S + "table, th, td {  border: 1px solid " + TOKEN_BW_INVERSE_OF_THEME + "; border-collapse: collapse; border-spacing: 0; padding: 6px; }" + CSS_E;
    protected static final String CSS_BUTTON_STYLE_MATERIAL = CSS_S + "button:hover,button:active {filter: invert(1);} button { display: inline-block; box-sizing: border-box; border: none; border-radius: 4px; padding: 0 16px; min-width: 64px; height: 36px; font-family: 'Roboto'; font-size: 14px; font-weight: 500;  line-height: 36px; overflow: hidden; outline: none; vertical-align: middle; text-align: center; text-overflow: ellipsis; text-transform: uppercase; box-shadow: 0 3px 1px -2px rgba(0, 0, 0, 0.2), 0 2px 2px 0 rgba(0, 0, 0, 0.14), 0 1px 5px 0 rgba(0, 0, 0, 0.12); margin: 4px 4px 8px 0px;}   " + CSS_E;
    protected static final String CSS_BUTTON_STYLE_EMOJIBTN = CSS_S + " .emojibtn,.fa {font-size:250%; background: transparent; padding: 0px; min-width:0px;}   " + CSS_E;
    protected static final String CSS_CLASS_STICKY = CSS_S + " .sticky {position: sticky; display: inline-block; border: 0px solid " + TOKEN_BW_INVERSE_OF_THEME + ";} " + CSS_E;
    protected static final String CSS_CLASS_FLOAT = CSS_S + " .floatl {float: left;} .clear {clear:both;} " + CSS_E;

    // onPageLoaded_markor_private() invokes the user injected function onPageLoaded()
    protected static final String HTML500_BODY = "</head>\n<body class='" + TOKEN_TEXT_CONVERTER_CSS_CLASS + "' onload='onPageLoaded_markor_private();'>\n\n<!-- USER DOCUMENT CONTENT -->\n\n\n";
    /**
     * protected static final String HTML900_TO_TOP = "<a class='back_to_top'>&uarr;</a>"
     * + CSS_S + ".back_to_top { position: fixed; bottom: 80px; right: 40px; z-index: 9999; width: 30px; height: 30px; text-align: center; line-height: 30px; background: #f5f5f5; color: #444; cursor: pointer; border-radius: 2px; display: none; } .back_to_top:hover { background: #e9ebec; } .back_to_top-show { display: block; }" +CSS_E
     * + "<script>" + "(function() { 'use strict'; function trackScroll() { var scrolled = window.pageYOffset; var coords = document.documentElement.clientHeight; if (scrolled > coords) { goTopBtn.classList.add('back_to_top-show'); } if (scrolled < coords) { goTopBtn.classList.remove('back_to_top-show'); } } function backToTop() { if (window.pageYOffset > 0) { window.scrollBy(0, -80); setTimeout(backToTop, 0); } } var goTopBtn = document.querySelector('.back_to_top'); window.addEventListener('scroll', trackScroll); goTopBtn.addEventListener('click', backToTop); })();" + "</script>";
     */
    protected static final String HTML990_BODY_END = "\n\n<!-- USER DOCUMENT CONTENT END -->\n\n</body></html>";

    protected static final String HTML_ON_PAGE_LOAD_S = "<script> function onPageLoaded_markor_private() {\n";
    protected static final String HTML_ON_PAGE_LOAD_E = "\nonPageLoaded(); }\n</script>";

    // protected static final String HTML_JQUERY_INCLUDE = "<script src='file:///android_asset/jquery/jquery-3.3.1.min.js'></script>"; // currently not bundled

    //########################
    //## Methods
    //########################
    public TextConverterBase() {
    }

    /**
     * Convert markup to target format and show the result in a WebView
     *
     * @param document The document containing the contents
     * @param webView  The WebView content to be shown in
     */
    public void convertMarkupShowInWebView(
            final Document document,
            final String content,
            final Activity context,
            final WebView webView,
            final boolean lightMode,
            final boolean lineNum
    ) {
        convertMarkupShowInWebView(document, content, context, webView, lightMode, lineNum, null);
    }

    /**
     * tsun-markor fork: same as above, with an optional element id to scroll to
     * once the page has loaded (used by Obsidian wikilink heading anchors).
     *
     * @param jumpToAnchorId element id to jump to after load, or null
     */
    public void convertMarkupShowInWebView(
            final Document document,
            final String content,
            final Activity context,
            final WebView webView,
            final boolean lightMode,
            final boolean lineNum,
            final String jumpToAnchorId
    ) {
        final AppSettings as = AppSettings.get(context);

        String html;
        try {
            html = convertMarkup(content, context, lightMode, lineNum, document.file);
        } catch (Exception e) {
            html = "Please report at project issue tracker: " + e;
        }

        if (jumpToAnchorId != null && !jumpToAnchorId.trim().isEmpty()) {
            html = injectJumpToAnchor(html, jumpToAnchorId.trim());
        }

        String parent = document.file.getParent();
        if (parent == null) {
            parent = as.getNotebookDirectory().getAbsolutePath();
        }
        final String baseFolder = "file://" + parent + "/";

        webView.loadDataWithBaseURL(baseFolder, html, getContentType(), UTF_CHARSET, null);

        // When TOKEN_TEXT_CONVERTER_MAX_ZOOM_OUT_BY_DEFAULT is contained in text zoom out as far possible
        // Notice: overViewMode / useWideViewPort work differently
        for (int i = (html.contains(TOKEN_TEXT_CONVERTER_MAX_ZOOM_OUT_BY_DEFAULT) ? 0 : 99); i < 30; i++) {
            webView.postDelayed(webView::zoomOut, 210 * (i < 5 ? 1 : (i < 10 ? 2 : (i < 15 ? 3 : (i < 20 ? 5 : 9)))));
        }
    }

    /**
     * tsun-markor fork: append a script that scrolls the given element id into
     * view on window load. The id is tried as-is and URL-decoded (fragments may
     * arrive percent-encoded from the WebView).
     */
    private static String injectJumpToAnchor(final String html, final String anchorId) {
        final String idJson = anchorId.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
        final String script = "<script>window.addEventListener('load',function(){try{"
                + "var t='" + idJson + "';"
                + "var e=document.getElementById(t)||document.getElementById(decodeURIComponent(t));"
                + "if(e){e.scrollIntoView();}"
                + "}catch(err){}});</script>";
        final int bodyEnd = html.lastIndexOf("</body>");
        if (bodyEnd >= 0) {
            return html.substring(0, bodyEnd) + script + html.substring(bodyEnd);
        }
        return html + script;
    }

    /**
     * Convert markup text to target format
     *
     * @param markup    Markup text
     * @param context   Android Context
     * @param lightMode light/dark mode
     * @param lineNum   line number
     * @return html as String
     */
    public abstract String convertMarkup(String markup, Context context, boolean lightMode, boolean lineNum, File file);

    protected String putContentIntoTemplate(Context context, String content, boolean isExportInLightMode, File file, String onLoadJs, String head) {
        final AppSettings as = AppSettings.get(context);
        final String contentLower = content.toLowerCase();
        boolean darkTheme = GsContextUtils.instance.isDarkModeEnabled(context) && !isExportInLightMode;
        String html = HTML_DOCTYPE + HTML001_HEAD_WITH_BASESTYLE.replace(TOKEN_POST_LANG, Locale.getDefault().getLanguage()) + (darkTheme ? HTML002_HEAD_WITH_STYLE_DARK : HTML002_HEAD_WITH_STYLE_LIGHT);
        if (isExportInLightMode) {
            html = html.replace("html,body{color:#303030;}", "html,body{color: black !important; background-color: white !important;}");
        }
        html += HTML004_HEAD_META_VIEWPORT_MOBILE + CSS_TABLE_STYLE + CSS_CLASS_FLOAT + CSS_BUTTON_STYLE_MATERIAL + CSS_BUTTON_STYLE_EMOJIBTN + CSS_CLASS_STICKY;
        if (as.isRenderRtl()) {
            html += HTML003_RIGHT_TO_LEFT;
        }

        html += head + as.getInjectedHeader();

        html += HTML_ON_PAGE_LOAD_S + onLoadJs + HTML_ON_PAGE_LOAD_E;

        // Add custom font css if font is a filepath, swap path with new font-family
        // tsunderick: bundled fonts are served to the WebView over
        // https://appassets.androidplatform.net (WebViewAssetLoader, hooked into
        // MarkorWebViewClient) - plain file:// subresources are blocked from file://
        // pages (setAllowFileAccessFromFileURLs=false), so they never loaded.
        String font = as.getFontFamily();
        if (font.startsWith("/")) {
            final StringBuilder fontCss = new StringBuilder();
            appendFontFace(fontCss, font, null, null);
            // For bundled font families, add matching bold/italic faces so they render true instead of synthesized
            if (font.startsWith("/android_asset/fonts/") && font.contains(" - ")) {
                final String base = font.substring(0, font.lastIndexOf(" - "));
                final String[][] variants = {{" - Bold.ttf", "bold", "normal"}, {" - Italic.ttf", "normal", "italic"}, {" - Bold Italic.ttf", "bold", "italic"}};
                for (String[] v : variants) {
                    appendFontFace(fontCss, base + v[0], v[1], v[2]);
                }
            }
            if (fontCss.length() > 0) {
                html += CSS_S + fontCss + CSS_E;
                font = "customfont";
            }
        }

        // Remove duplicate style blocks
        html = html.replace(CSS_E + CSS_S, "").replace(CSS_E + "\n" + CSS_S, "");

        // Options based on filepath
        if (file != null) {
            if (file.getAbsolutePath().contains("%") || ((contentLower.contains(".nextcloud") || contentLower.contains(".owncloud")) && (contentLower.contains("%2") || contentLower.contains("%4")))) {
                html += HTML100_PERCENT_IN_FILEPATH;
            }
        }

        // Load content
        html += HTML500_BODY;
        html += as.getInjectedBody();
        html += content;
        html += HTML990_BODY_END;

        // Replace tokens
        html = html
                .replace(TOKEN_BW_INVERSE_OF_THEME, darkTheme ? "#444444" : "black")
                .replace(TOKEN_BW_INVERSE_OF_THEME_HEADER_UNDERLINE, darkTheme ? "#ff8fb1" : "#696969")
                .replace(TOKEN_COLOR_GREY_OF_THEME, darkTheme ? "#1c1c1c" : GsTextUtils.colorToHexString(ContextCompat.getColor(context, R.color.lighter_grey)))
                .replace(TOKEN_LINK_COLOR, as.getViewModeLinkColor())
                .replace(TOKEN_ACCENT_COLOR, GsTextUtils.colorToHexString(ContextCompat.getColor(context, R.color.accent)))
                .replace(TOKEN_TEXT_DIRECTION, as.isRenderRtl() ? "right" : "left")
                .replace(TOKEN_FONT, font)
                .replace(TOKEN_TEXT_CONVERTER_CSS_CLASS, "format-" + getClass().getSimpleName().toLowerCase().replace("textconverter", "").replace("converter", "") + (file == null ? "" : " fileext-" + GsFileUtils.getFilenameExtension(file).replace(".", "")))
                .replace(TOKEN_POST_TODAY_DATE, DateFormat.getDateFormat(context).format(new Date()))
                .replace(TOKEN_FILEURI_VIEWED_FILE, (file == null ? "" : Uri.fromFile(file.getAbsoluteFile()).toString().replace("'", "\\'").replace("\"", "\\\"")));

        // tsunderick: when a custom font is active, headings and code must use it too -
        // upstream CSS hardcodes 'sans-serif-condensed' for headings and monospace for code
        if (font.equals("customfont")) {
            html = html.replace("font-family:'sans-serif-condensed'", "font-family:'customfont'")
                    .replace("font-family: 'sans-serif-condensed'", "font-family: 'customfont'")
                    .replace("font-family: monospace", "font-family: customfont")
                    .replace("font-family:monospace", "font-family:customfont");
        }

        return html;
    }

    protected String getContentType() {
        return CONTENT_TYPE_HTML;
    }

    //#################### tsunderick: custom font via WebViewAssetLoader ####################

    private static final String APPASSETS_BASE = "https://appassets.androidplatform.net/assets/";

    /**
     * Append an {@code @font-face} rule for the given font file path to {@code fontCss}.
     * Only asset paths ({@code /android_asset/...}) are supported - they are served by
     * the {@link androidx.webkit.WebViewAssetLoader} registered on the preview's client.
     * {@code weight}/{@code style} may be null for the default face.
     */
    private static void appendFontFace(final StringBuilder fontCss, final String fontPath, final String weight, final String style) {
        if (!fontPath.startsWith("/android_asset/")) {
            return;
        }
        final String url = APPASSETS_BASE + fontPath.substring("/android_asset/".length()).replace(" ", "%20");
        fontCss.append(" @font-face { font-family: customfont;");
        if (weight != null) {
            fontCss.append(" font-weight: ").append(weight).append(";");
        }
        if (style != null) {
            fontCss.append(" font-style: ").append(style).append(";");
        }
        fontCss.append(" src: url('").append(url).append("'); }");
    }

    public boolean isFileOutOfThisFormat(final @NonNull File file) {
        final String name = file.getName().toLowerCase().replace(JavaPasswordbasedCryption.DEFAULT_ENCRYPTION_EXTENSION, "").trim();
        final String extWithDot = GsFileUtils.getFilenameExtension(name);
        return isFileOutOfThisFormat(file, name, extWithDot);
    }

    protected abstract boolean isFileOutOfThisFormat(final File file, final String name, final String ext);
}
