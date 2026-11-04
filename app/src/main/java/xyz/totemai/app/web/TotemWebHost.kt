package xyz.totemai.app.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import java.io.ByteArrayInputStream
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import xyz.totemai.app.Intents
import xyz.totemai.app.NetworkMonitor
import xyz.totemai.app.SessionStore
import xyz.totemai.app.TotemConfig
import xyz.totemai.app.ui.components.ErrorPanel
import xyz.totemai.app.ui.components.OfflineBanner
import xyz.totemai.app.ui.theme.Lime

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TotemWebHost(
    controller: WebController,
    store: SessionStore,
    monitor: NetworkMonitor,
    visible: Boolean,
    onSessionChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var webViewGeneration by remember { mutableIntStateOf(0) }
    val online by monitor.online.collectAsState()

    val chooser = remember { FileChooserHost() }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        chooser.deliver(result.resultCode, result.data)
    }
    SideEffect {
        chooser.launch = { intent -> launcher.launch(intent) }
    }

    val sessionChanged by rememberUpdatedState(onSessionChanged)
    val bridge = remember(store) { SessionBridge(store) { sessionChanged() } }

    val client = remember(controller) {
        object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val uri = request.url
                val scheme = uri.scheme?.lowercase()
                // WebView does not call shouldOverrideUrlLoading for POST navigations.
                // Block an untrusted main-frame HTTP(S) POST rather than letting it bypass the host allowlist.
                if (request.isForMainFrame && (scheme == "http" || scheme == "https") &&
                    !OriginPolicy.isTrustedHost(uri.toString()) &&
                    request.method.equals("POST", ignoreCase = true)
                ) {
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        403,
                        "Blocked by TOTEM host policy",
                        emptyMap(),
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                return null
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri: Uri = request.url
                val scheme = uri.scheme?.lowercase().orEmpty()

                if (scheme == "https" || scheme == "http") {
                    if (OriginPolicy.isTrustedHost(uri.toString())) return false
                    if (request.isForMainFrame) Intents.open(context, uri)
                    return true
                }

                if (OriginPolicy.isWallet(scheme)) {
                    if (request.isForMainFrame) Intents.open(context, uri)
                    return true
                }

                if (scheme == "javascript" || scheme == "blob" ||
                    scheme == "data" || scheme == "about"
                ) {
                    return false
                }

                if (request.isForMainFrame) Intents.open(context, uri)
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                controller.onUrl(url)
                loading = true
                failed = false
                progress = 5

                val token = store.token()
                if (!token.isNullOrEmpty() && url != null && OriginPolicy.isTrustedHost(url)) {
                    view.evaluateJavascript(SessionBridge.restoreJs(token, store.user()), null)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                controller.onUrl(url)
                loading = false
                progress = 100
                if (url != null && OriginPolicy.isTrustedHost(url)) {
                    view.evaluateJavascript(SessionBridge.CAPTURE_JS, null)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (!request.isForMainFrame) return
                loading = false
                failed = true
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 500) {
                    loading = false
                    failed = true
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                Log.w(TAG, "TLS certificate error; navigation cancelled")
                handler.cancel()
                loading = false
                failed = true
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                Log.e(TAG, "WebView renderer exited; recreating WebView")
                (view.parent as? ViewGroup)?.removeView(view)
                runCatching { view.destroy() }
                controller.detach()
                loading = false
                failed = true
                webViewGeneration += 1
                return true
            }
        }
    }

    val chrome = remember(chooser) {
        object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress = newProgress
            }

            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                chooser.begin(filePathCallback, fileChooserParams)
                return true
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        key(webViewGeneration) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        WebViewConfig.apply(this)
                        CookiePolicy.configure(this)
                        webViewClient = client
                        webChromeClient = chrome

                        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                            WebViewCompat.addWebMessageListener(
                                this,
                                TotemConfig.BRIDGE_NAME,
                                setOf(TotemConfig.ORIGIN),
                                bridge
                            )
                        } else {
                            Log.w(TAG, "Web message listener unsupported; native session mirroring disabled")
                        }

                        controller.attach(this)
                        val pendingUrl = controller.takePending()
                        val state = controller.consumeRestoredState()
                        if (pendingUrl != null) {
                            loadUrl(pendingUrl)
                        } else {
                            val restoredList = state?.let { saved ->
                                runCatching { restoreState(saved) }.getOrNull()
                            }
                            val restoredUrl = restoredList?.currentItem?.url ?: url
                            if (restoredList != null && restoredUrl != null &&
                                OriginPolicy.isTrustedHost(restoredUrl)
                            ) {
                                controller.onUrl(restoredUrl)
                            } else {
                                loadUrl(controller.currentUrl)
                            }
                        }
                    }
                },
                update = { view ->
                    view.visibility = if (visible) View.VISIBLE else View.GONE
                }
            )
        }

        if (visible && loading && progress in 1..99) {
            LinearProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter),
                color = Lime
            )
        }

        if (visible && !online) {
            OfflineBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )
        }

        if (visible && failed) {
            ErrorPanel(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                onRetry = {
                    failed = false
                    controller.reload()
                }
            )
        }
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    controller.webView?.onResume()
                    controller.webView?.resumeTimers()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    controller.webView?.onPause()
                    controller.webView?.pauseTimers()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            CookiePolicy.flush()
            val view = controller.webView
            if (view != null) {
                (view.parent as? ViewGroup)?.removeView(view)
                runCatching {
                    view.stopLoading()
                    view.destroy()
                }
            }
            controller.detach()
        }
    }
}

private const val TAG = "TotemWebHost"
