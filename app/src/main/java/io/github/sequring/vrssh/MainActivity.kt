package io.github.sequring.vrssh

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var bridge: SshBridge

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Remote inspection only in debug builds: it exposes terminal contents to anyone with adb.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        WebView.setWebContentsDebuggingEnabled(debuggable)

        webView = WebView(this).apply {
            setBackgroundColor(getPreferences(MODE_PRIVATE).getInt("background", Color.parseColor("#1E1E2E")))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }
        bridge = SshBridge(this, webView)
        webView.addJavascriptInterface(bridge, "Android")
        webView.addJavascriptInterface(KeyStore { getExternalFilesDir("ssh") }, "Keys")
        webView.loadUrl("file:///android_asset/index.html")
        setContentView(webView)
    }

    override fun onResume() {
        super.onResume()
        bridge.checkAlive()
    }

    override fun onDestroy() {
        bridge.disconnectAll()
        webView.destroy()
        super.onDestroy()
    }
}
