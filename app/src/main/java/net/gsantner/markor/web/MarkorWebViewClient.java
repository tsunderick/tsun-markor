/*#######################################################
 *
 *   Maintained 2017-2025 by Gregor Santner <gsantner AT mailbox DOT org>
 *   License of this file: Apache 2.0
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
#########################################################*/
package net.gsantner.markor.web;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.webkit.WebViewAssetLoader;

import net.gsantner.markor.activity.DocumentActivity;
import net.gsantner.markor.model.AppSettings;
import net.gsantner.markor.util.MarkorContextUtils;
import net.gsantner.opoc.web.GsWebViewClient;

public class MarkorWebViewClient extends GsWebViewClient {
    protected final Activity _activity;
    // tsun-markor fork: serves bundled assets (fonts) over appassets.androidplatform.net
    private WebViewAssetLoader _assetLoader;
    // tsun-markor fork: invoked after a page finished loading; consumers use it to
    // re-anchor state that must not mistake the load's scroll-restore for user scrolling.
    private Runnable _onPageSettled;

    public MarkorWebViewClient(final WebView webView, final Activity activity) {
        super(webView);
        _activity = activity;
    }

    /** tsun-markor fork: optional loader so the preview can load bundled fonts. */
    public void setAssetLoader(final WebViewAssetLoader loader) {
        _assetLoader = loader;
    }

    /**
     * tsun-markor fork: callback fired from {@link #onPageFinished}, i.e. after
     * {@code GsWebViewClient} has queued its delayed scroll-restore retries
     * (50–300ms). Used by the document fragment to re-anchor the auto-hide bar
     * baseline across that window so the programmatic restore never reads as
     * a downward user scroll (which would instantly hide the bars).
     */
    public void setOnPageSettled(final Runnable callback) {
        _onPageSettled = callback;
    }

    @Override
    public void onPageFinished(final WebView webView, final String url) {
        super.onPageFinished(webView, url);
        if (_onPageSettled != null) {
            _onPageSettled.run();
        }
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(final WebView view, final WebResourceRequest request) {
        if (_assetLoader != null) {
            final WebResourceResponse response = _assetLoader.shouldInterceptRequest(request.getUrl());
            if (response != null) {
                // tsun-markor fork: the preview page is a file:// URL (Origin: null), and
                // fonts are CORS-checked. The loader does not set the header on all
                // versions, so ensure it is present or Chromium blocks the font.
                final java.util.Map<String, String> headers = response.getResponseHeaders() == null
                        ? new java.util.HashMap<>() : new java.util.HashMap<>(response.getResponseHeaders());
                headers.put("Access-Control-Allow-Origin", "*");
                response.setResponseHeaders(headers);
                return response;
            }
        }
        return super.shouldInterceptRequest(view, request);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        try {
            Context context = view.getContext();

            if (url.equals("about:blank")) {
                view.reload();
                return true;
            }
            if (url.startsWith("file:///android_asset/")) {
                return false;
            } else if (url.startsWith("file://")) {
                DocumentActivity.launch(_activity, Uri.parse(url));
            } else {
                MarkorContextUtils su = new MarkorContextUtils(_activity);
                AppSettings settings = AppSettings.get(_activity);
                if (!settings.isOpenLinksWithChromeCustomTabs() || (settings.isOpenLinksWithChromeCustomTabs() && !su.openWebpageInChromeCustomTab(context, url))) {
                    su.openWebpageInExternalBrowser(context, url);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return true;
    }
}
