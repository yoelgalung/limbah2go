package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import java.io.ByteArrayInputStream
import java.io.IOException

private const val LOCAL_HOST = "localhost"
private const val ENTRY_URL = "https://localhost/index.html"

private sealed interface JsDialogState {
  data class Alert(val message: String, val result: JsResult) : JsDialogState
  data class Confirm(val message: String, val result: JsResult) : JsDialogState
  data class Prompt(
    val message: String,
    val defaultValue: String,
    val result: JsPromptResult,
  ) : JsDialogState
}

private data class PendingGeoPermission(
  val origin: String,
  val callback: GeolocationPermissions.Callback,
)

@Composable
fun LimbahWebViewScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val backgroundColor = MaterialTheme.colorScheme.background
  val backgroundArgb = backgroundColor.toArgb()

  var isLoading by remember { mutableStateOf(true) }
  var hasMainFrameError by remember { mutableStateOf(false) }
  var canGoBack by remember { mutableStateOf(false) }
  var jsDialog by remember { mutableStateOf<JsDialogState?>(null) }
  var authPopupWebView by remember { mutableStateOf<WebView?>(null) }
  var pendingGeo by remember { mutableStateOf<PendingGeoPermission?>(null) }
  var mainWebView by remember { mutableStateOf<WebView?>(null) }

  val locationPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
      val granted =
        grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
          grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
      pendingGeo?.let { request ->
        request.callback.invoke(request.origin, granted, false)
        pendingGeo = null
      }
    }

  val closeAuthPopup: () -> Unit = {
    authPopupWebView?.let { popup ->
      popup.stopLoading()
      popup.destroy()
    }
    authPopupWebView = null
  }

  BackHandler(enabled = authPopupWebView != null || canGoBack) {
    if (authPopupWebView != null) {
      val popup = authPopupWebView
      if (popup != null && popup.canGoBack()) {
        popup.goBack()
      } else {
        closeAuthPopup()
      }
    } else {
      mainWebView?.goBack()
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      authPopupWebView?.destroy()
      authPopupWebView = null
      mainWebView?.destroy()
      mainWebView = null
    }
  }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(backgroundColor)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .imePadding()
  ) {
    AndroidView(
      modifier = Modifier.fillMaxSize().testTag("limbah_webview"),
      factory = { ctx ->
        createMainWebView(
            context = ctx,
            backgroundArgb = backgroundArgb,
            onLoadingChanged = { loading -> isLoading = loading },
            onMainFrameError = { failed -> hasMainFrameError = failed },
            onCanGoBackChanged = { back -> canGoBack = back },
            onJsDialog = { state -> jsDialog = state },
            onRequestGeolocation = { origin, callback ->
              if (hasLocationPermission(ctx)) {
                callback.invoke(origin, true, false)
              } else {
                pendingGeo?.callback?.invoke(pendingGeo!!.origin, false, false)
                pendingGeo = PendingGeoPermission(origin, callback)
                locationPermissionLauncher.launch(
                  arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                  )
                )
              }
            },
            onOpenAuthPopup = { popup ->
              closeAuthPopup()
              authPopupWebView = popup
            },
            onCloseAuthPopup = closeAuthPopup,
          )
          .also { wv ->
            mainWebView = wv
            wv.loadUrl(ENTRY_URL)
          }
      },
      update = { webView -> webView.setBackgroundColor(backgroundArgb) },
    )

    if (isLoading && !hasMainFrameError) {
      LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).testTag("loading_indicator"),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
      )
    }

    if (hasMainFrameError) {
      OfflineFallbackOverlay(
        onRetry = {
          hasMainFrameError = false
          isLoading = true
          mainWebView?.loadUrl(ENTRY_URL)
        }
      )
    }
  }

  jsDialog?.let { activeDialog ->
    JsBridgeDialog(
      dialogState = activeDialog,
      onDismiss = { jsDialog = null },
    )
  }

  authPopupWebView?.let { popupView ->
    AuthPopupDialog(
      popupWebView = popupView,
      onDismiss = closeAuthPopup,
    )
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createMainWebView(
  context: Context,
  backgroundArgb: Int,
  onLoadingChanged: (Boolean) -> Unit,
  onMainFrameError: (Boolean) -> Unit,
  onCanGoBackChanged: (Boolean) -> Unit,
  onJsDialog: (JsDialogState) -> Unit,
  onRequestGeolocation: (String, GeolocationPermissions.Callback) -> Unit,
  onOpenAuthPopup: (WebView) -> Unit,
  onCloseAuthPopup: () -> Unit,
): WebView {
  return WebView(context).apply {
    layoutParams =
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    configureWebViewSettings(this, backgroundArgb)

    webViewClient =
      object : WebViewClient() {
        override fun shouldInterceptRequest(
          view: WebView,
          request: WebResourceRequest,
        ): WebResourceResponse? {
          if (request.url.host != LOCAL_HOST) return null
          return serveLocalAsset(context, request.url)
        }

        override fun shouldOverrideUrlLoading(
          view: WebView,
          request: WebResourceRequest,
        ): Boolean {
          return handleUrlNavigation(context, request.url, request.isForMainFrame)
        }

        override fun onPageFinished(view: WebView, url: String?) {
          super.onPageFinished(view, url)
          onLoadingChanged(false)
          onCanGoBackChanged(view.canGoBack())
        }

        override fun onReceivedError(
          view: WebView,
          request: WebResourceRequest,
          error: WebResourceError,
        ) {
          super.onReceivedError(view, request, error)
          if (request.isForMainFrame) {
            onLoadingChanged(false)
            onMainFrameError(true)
          }
        }
      }

    webChromeClient =
      object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
          if (newProgress >= 95) {
            onLoadingChanged(false)
          }
        }

        override fun onGeolocationPermissionsShowPrompt(
          origin: String,
          callback: GeolocationPermissions.Callback,
        ) {
          onRequestGeolocation(origin, callback)
        }

        override fun onJsAlert(
          view: WebView?,
          url: String?,
          message: String?,
          result: JsResult,
        ): Boolean {
          onJsDialog(JsDialogState.Alert(message.orEmpty(), result))
          return true
        }

        override fun onJsConfirm(
          view: WebView?,
          url: String?,
          message: String?,
          result: JsResult,
        ): Boolean {
          onJsDialog(JsDialogState.Confirm(message.orEmpty(), result))
          return true
        }

        override fun onJsPrompt(
          view: WebView?,
          url: String?,
          message: String?,
          defaultValue: String?,
          result: JsPromptResult,
        ): Boolean {
          onJsDialog(
            JsDialogState.Prompt(
              message = message.orEmpty(),
              defaultValue = defaultValue.orEmpty(),
              result = result,
            )
          )
          return true
        }

        override fun onCreateWindow(
          view: WebView?,
          isDialog: Boolean,
          isUserGesture: Boolean,
          resultMsg: Message?,
        ): Boolean {
          val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
          var shownAsPopup = false

          val childWebView =
            WebView(context).apply {
              layoutParams =
                ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT,
                )
              configureWebViewSettings(this, backgroundArgb)

              webViewClient =
                object : WebViewClient() {
                  override fun shouldOverrideUrlLoading(
                    child: WebView,
                    request: WebResourceRequest,
                  ): Boolean {
                    val uri = request.url
                    if (isExternalIntentUri(uri)) {
                      launchExternalUri(context, uri)
                      if (shownAsPopup) {
                        onCloseAuthPopup()
                      } else {
                        child.destroy()
                      }
                      return true
                    }
                    if (!shownAsPopup) {
                      shownAsPopup = true
                      onOpenAuthPopup(child)
                    }
                    return false
                  }

                  override fun onPageStarted(
                    child: WebView,
                    url: String?,
                    favicon: android.graphics.Bitmap?,
                  ) {
                    super.onPageStarted(child, url, favicon)
                    val uri = url?.let { Uri.parse(it) }
                    if (uri != null && isExternalIntentUri(uri)) {
                      child.stopLoading()
                      launchExternalUri(context, uri)
                      if (shownAsPopup) {
                        onCloseAuthPopup()
                      } else {
                        child.destroy()
                      }
                    } else if (!shownAsPopup) {
                      shownAsPopup = true
                      onOpenAuthPopup(child)
                    }
                  }
                }

              webChromeClient =
                object : WebChromeClient() {
                  override fun onCloseWindow(window: WebView?) {
                    onCloseAuthPopup()
                  }
                }
            }

          transport.webView = childWebView
          resultMsg.sendToTarget()
          return true
        }
      }
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureWebViewSettings(webView: WebView, backgroundArgb: Int) {
  webView.setBackgroundColor(backgroundArgb)
  webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    setGeolocationEnabled(true)
    javaScriptCanOpenWindowsAutomatically = true
    setSupportMultipleWindows(true)
    loadWithOverviewMode = true
    useWideViewPort = true
    builtInZoomControls = false
    displayZoomControls = false
    allowFileAccess = false
    allowContentAccess = false
    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    userAgentString = userAgentString.replace("; wv", "")
  }
  val cookieManager = CookieManager.getInstance()
  cookieManager.setAcceptCookie(true)
  cookieManager.setAcceptThirdPartyCookies(webView, true)
}

private fun serveLocalAsset(context: Context, uri: Uri): WebResourceResponse {
  val rawPath = uri.path?.trimStart('/').orEmpty()
  val assetPath = if (rawPath.isEmpty()) "index.html" else rawPath
  return try {
    val stream = context.assets.open(assetPath)
    val mimeType =
      when {
        assetPath.endsWith(".html", ignoreCase = true) -> "text/html"
        assetPath.endsWith(".js", ignoreCase = true) -> "application/javascript"
        assetPath.endsWith(".css", ignoreCase = true) -> "text/css"
        assetPath.endsWith(".json", ignoreCase = true) ||
          assetPath.endsWith(".webmanifest", ignoreCase = true) -> "application/json"
        assetPath.endsWith(".png", ignoreCase = true) -> "image/png"
        else -> "application/octet-stream"
      }
    WebResourceResponse(
      mimeType,
      "UTF-8",
      200,
      "OK",
      mapOf("Cache-Control" to "no-store"),
      stream,
    )
  } catch (_: IOException) {
    WebResourceResponse(
      "text/plain",
      "UTF-8",
      404,
      "Not Found",
      emptyMap(),
      ByteArrayInputStream(ByteArray(0)),
    )
  }
}

private fun handleUrlNavigation(
  context: Context,
  uri: Uri,
  isForMainFrame: Boolean,
): Boolean {
  if (uri.host == LOCAL_HOST) return false
  if (isExternalIntentUri(uri)) {
    launchExternalUri(context, uri)
    return true
  }
  if (!isForMainFrame) {
    return false
  }
  if (isInAppAuthOrEmbedUri(uri)) {
    return false
  }
  launchExternalUri(context, uri)
  return true
}

private fun isInAppAuthOrEmbedUri(uri: Uri): Boolean {
  val host = uri.host?.lowercase().orEmpty()
  val urlText = uri.toString().lowercase()
  if (urlText.contains("output=embed")) return true
  return host.endsWith("firebaseapp.com") ||
    host == "accounts.google.com" ||
    host == "accounts.youtube.com" ||
    host == "apis.google.com" ||
    host.endsWith("googleusercontent.com") ||
    host.endsWith("gstatic.com")
}

private fun isExternalIntentUri(uri: Uri): Boolean {
  val scheme = uri.scheme?.lowercase().orEmpty()
  if (scheme in setOf("tel", "mailto", "geo", "whatsapp", "intent")) return true
  val host = uri.host?.lowercase().orEmpty()
  val urlText = uri.toString().lowercase()
  if (host == "wa.me" || host == "api.whatsapp.com" || host == "maps.app.goo.gl") return true
  if ((host == "maps.google.com" || host == "www.google.com") &&
    urlText.contains("/maps") &&
    !urlText.contains("output=embed")
  ) {
    return true
  }
  if (host == "maps.google.com" && !urlText.contains("output=embed")) {
    return true
  }
  return false
}

private fun launchExternalUri(context: Context, uri: Uri) {
  val action =
    if (uri.scheme?.equals("tel", ignoreCase = true) == true) {
      Intent.ACTION_DIAL
    } else {
      Intent.ACTION_VIEW
    }
  val intent = Intent(action, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
  try {
    context.startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    Toast.makeText(
        context,
        context.getString(R.string.external_app_unavailable),
        Toast.LENGTH_SHORT,
      )
      .show()
  }
}

private fun hasLocationPermission(context: Context): Boolean {
  val fine =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED
  val coarse =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED
  return fine || coarse
}

@Composable
private fun JsBridgeDialog(
  dialogState: JsDialogState,
  onDismiss: () -> Unit,
) {
  when (dialogState) {
    is JsDialogState.Alert -> {
      AlertDialog(
        onDismissRequest = {
          dialogState.result.cancel()
          onDismiss()
        },
        text = { Text(dialogState.message) },
        confirmButton = {
          TextButton(
            onClick = {
              dialogState.result.confirm()
              onDismiss()
            },
            modifier = Modifier.testTag("js_dialog_confirm"),
          ) {
            Text(stringResource(R.string.dialog_ok))
          }
        },
      )
    }

    is JsDialogState.Confirm -> {
      AlertDialog(
        onDismissRequest = {
          dialogState.result.cancel()
          onDismiss()
        },
        text = { Text(dialogState.message) },
        confirmButton = {
          TextButton(
            onClick = {
              dialogState.result.confirm()
              onDismiss()
            },
            modifier = Modifier.testTag("js_dialog_confirm"),
          ) {
            Text(stringResource(R.string.dialog_yes))
          }
        },
        dismissButton = {
          TextButton(
            onClick = {
              dialogState.result.cancel()
              onDismiss()
            },
            modifier = Modifier.testTag("js_dialog_cancel"),
          ) {
            Text(stringResource(R.string.dialog_cancel))
          }
        },
      )
    }

    is JsDialogState.Prompt -> {
      var inputText by remember(dialogState) { mutableStateOf(dialogState.defaultValue) }
      AlertDialog(
        onDismissRequest = {
          dialogState.result.cancel()
          onDismiss()
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(dialogState.message)
            OutlinedTextField(
              value = inputText,
              onValueChange = { inputText = it },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("js_prompt_input"),
            )
          }
        },
        confirmButton = {
          TextButton(
            onClick = {
              dialogState.result.confirm(inputText)
              onDismiss()
            },
            modifier = Modifier.testTag("js_dialog_confirm"),
          ) {
            Text(stringResource(R.string.dialog_ok))
          }
        },
        dismissButton = {
          TextButton(
            onClick = {
              dialogState.result.cancel()
              onDismiss()
            },
            modifier = Modifier.testTag("js_dialog_cancel"),
          ) {
            Text(stringResource(R.string.dialog_cancel))
          }
        },
      )
    }
  }
}

@Composable
private fun AuthPopupDialog(
  popupWebView: WebView,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize().padding(12.dp),
      shape = MaterialTheme.shapes.large,
      tonalElevation = 4.dp,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(R.string.auth_popup_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_auth_popup"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.cd_close_auth),
            )
          }
        }
        AndroidView(
          modifier = Modifier.fillMaxWidth().weight(1f),
          factory = { popupWebView },
        )
      }
    }
  }
}

@Composable
private fun OfflineFallbackOverlay(onRetry: () -> Unit) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Icon(
          imageVector = Icons.Rounded.WifiOff,
          contentDescription = stringResource(R.string.cd_offline_icon),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = stringResource(R.string.offline_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(R.string.offline_message),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
          onClick = onRetry,
          modifier = Modifier.fillMaxWidth().testTag("retry_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.size(8.dp))
          Text(stringResource(R.string.retry_button))
        }
      }
    }
  }
}
