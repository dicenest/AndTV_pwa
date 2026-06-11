package io.autodarts.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        // Change this if you want to land directly on your board page,
        // e.g. "https://play.autodarts.io/boards/<your-board-id>/follow"
        const val START_URL = "https://play.autodarts.io"
    }

    private lateinit var cursorLayout: CursorLayout
    private lateinit var webView: WebView
    private lateinit var spatialNavJs: String

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        spatialNavJs = assets.open("spatialnav.js").bufferedReader().use { it.readText() }

        webView = WebView(this)
        cursorLayout = CursorLayout(this)
        cursorLayout.addView(webView)
        setContentView(cursorLayout)

        hideSystemUi()

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true          // REQUIRED: Keycloak tokens live in localStorage
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false  // caller sounds without extra click
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
        }

        // Keep the login session across app restarts
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // Inject spatial navigation (idempotent thanks to window.__adnav guard)
                view.evaluateJavascript(spatialNavJs, null)
            }
        }
        webView.webChromeClient = WebChromeClient()

        // Default mode: spatial navigation -> WebView must receive D-pad key events
        setCursorMode(false)

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    /** Toggle between focus-jump navigation (default) and the free cursor fallback. */
    private fun setCursorMode(enabled: Boolean) {
        cursorLayout.cursorEnabled = enabled
        webView.isFocusable = !enabled
        webView.isFocusableInTouchMode = !enabled
        if (enabled) cursorLayout.requestFocus() else webView.requestFocus()
    }

    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            // BACK navigates the WebView history instead of closing the app
            KeyEvent.KEYCODE_BACK -> if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
            // MENU (or the "settings"/hamburger button on many remotes) toggles
            // the free-cursor fallback for elements spatial nav can't reach
            KeyEvent.KEYCODE_MENU -> {
                val enable = !cursorLayout.cursorEnabled
                setCursorMode(enable)
                Toast.makeText(
                    this,
                    if (enable) "Cursor-Modus" else "Fokus-Navigation",
                    Toast.LENGTH_SHORT
                ).show()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()  // persist session to disk
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }
}
