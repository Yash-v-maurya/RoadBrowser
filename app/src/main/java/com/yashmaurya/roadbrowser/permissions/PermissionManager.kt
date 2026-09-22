package com.yashmaurya.roadbrowser.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yashmaurya.roadbrowser.AppConstants
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.data.BrowserPreferences

class PermissionManager(private val activity: AppCompatActivity) {

    companion object {
        private const val DIALOG_TYPE_CLEARTEXT = 0
        private const val DIALOG_TYPE_MICROPHONE = 1
        private const val DIALOG_TYPE_LOCATION = 2
    }

    var pendingPermissionRequest: PermissionRequest? = null
    var pendingSpeechBridgeTabId: Long? = null
    var pendingGeolocationOrigin: String? = null
    var pendingGeolocationCallback: android.webkit.GeolocationPermissions.Callback? = null
    var isShowingCleartextDialog: Boolean = false
    var isShowingMicrophoneDialog: Boolean = false
    var isShowingLocationDialog: Boolean = false

    fun ensureNotificationPermissionIfNeeded(requestCode: Int) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode)
    }

    private fun grantableWebPermissionResources(request: PermissionRequest): Array<String> {
        val allowed = setOf(
            PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
            PermissionRequest.RESOURCE_AUDIO_CAPTURE
        )
        return request.resources.filter { it in allowed }.toTypedArray()
    }

    private fun protectedMediaResources(request: PermissionRequest): Array<String> {
        return request.resources
            .filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
            .toTypedArray()
    }

    private fun denyAudioButAllowProtectedMediaIfPresent(request: PermissionRequest) {
        val protectedMedia = protectedMediaResources(request)
        if (protectedMedia.isNotEmpty()) {
            request.grant(protectedMedia)
        } else {
            request.deny()
        }
    }

    private fun continueWebPermissionRequest(request: PermissionRequest, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val grantable = grantableWebPermissionResources(request)
            if (grantable.isNotEmpty()) request.grant(grantable) else request.deny()
        } else {
            pendingPermissionRequest?.let { oldRequest ->
                denyAudioButAllowProtectedMediaIfPresent(oldRequest)
            }
            pendingPermissionRequest = request
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), requestCode)
        }
    }

    fun handleWebPermissionRequest(request: PermissionRequest, requestCode: Int) {
        val grantable = grantableWebPermissionResources(request)
        if (grantable.isEmpty()) {
            request.deny()
            return
        }

        val origin = runCatching { request.origin }.getOrNull()
        val host = origin?.host?.lowercase()
        val scheme = origin?.scheme?.lowercase()
        val isSecure = scheme == "https" || host == "localhost" || host == "127.0.0.1" || scheme == "file"

        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE !in grantable || (isSecure && BrowserPreferences.isHostAllowedMicrophone(activity, host))) {
            continueWebPermissionRequest(request, requestCode)
            return
        }

        if (activity.isFinishing || activity.isDestroyed || isShowingMicrophoneDialog) {
            denyAudioButAllowProtectedMediaIfPresent(request)
            return
        }

        showMicrophoneAccessDialog(
            origin = origin,
            isSecure = isSecure,
            onAllowOnce = { continueWebPermissionRequest(request, requestCode) },
            onAllowHost = {
                host?.let { BrowserPreferences.addAllowedMicrophoneHost(activity, it) }
                continueWebPermissionRequest(request, requestCode)
            },
            onCancel = { denyAudioButAllowProtectedMediaIfPresent(request) }
        )
    }

    fun requestSpeechRecognitionMicrophoneAccess(tabId: Long, pageUrl: String?, onPermissionResult: (Boolean) -> Unit) {
        val pageUri = runCatching { pageUrl?.let(Uri::parse) }.getOrNull()
        val host = pageUri?.host?.lowercase()
        val scheme = pageUri?.scheme?.lowercase()
        val isSecure = scheme == "https" || host == "localhost" || host == "127.0.0.1" || scheme == "file"
        pendingSpeechBridgeTabId = tabId

        if (isSecure && BrowserPreferences.isHostAllowedMicrophone(activity, host)) {
            continueSpeechRecognitionMicrophoneAccess(onPermissionResult)
            return
        }

        if (activity.isFinishing || activity.isDestroyed || isShowingMicrophoneDialog) {
            onPermissionResult(false)
            return
        }

        showMicrophoneAccessDialog(
            origin = pageUri,
            isSecure = isSecure,
            onAllowOnce = { continueSpeechRecognitionMicrophoneAccess(onPermissionResult) },
            onAllowHost = {
                host?.let { BrowserPreferences.addAllowedMicrophoneHost(activity, it) }
                continueSpeechRecognitionMicrophoneAccess(onPermissionResult)
            },
            onCancel = {
                onPermissionResult(false)
                pendingSpeechBridgeTabId = null
            }
        )
    }

    private fun continueSpeechRecognitionMicrophoneAccess(onPermissionResult: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onPermissionResult(true)
            pendingSpeechBridgeTabId = null
        } else {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), AppConstants.REQUEST_CODE_RECORD_AUDIO)
        }
    }

    fun handleGeolocationPermissionRequest(origin: String?, callback: android.webkit.GeolocationPermissions.Callback?) {
        if (callback == null) return
        val uri = runCatching { origin?.let(Uri::parse) }.getOrNull()
        val host = uri?.host?.lowercase()
        val scheme = uri?.scheme?.lowercase()
        val isSecure = scheme == "https" || host == "localhost" || host == "127.0.0.1" || scheme == "file"

        if (!isSecure) {
            callback.invoke(origin, false, false)
            return
        }

        val hasFine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (BrowserPreferences.isHostAllowedLocation(activity, host) && hasFine) {
            callback.invoke(origin, true, true)
            return
        }

        if (activity.isFinishing || activity.isDestroyed || isShowingLocationDialog) {
            callback.invoke(origin, false, false)
            return
        }

        if (hasCoarse && !hasFine) {
            showLocationUpgradeDialog(
                origin = uri,
                onUpgrade = {
                    pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, false, false)
                    pendingGeolocationOrigin = origin
                    pendingGeolocationCallback = callback
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        AppConstants.REQUEST_CODE_ACCESS_LOCATION
                    )
                },
                onKeepApproximate = {
                    callback.invoke(origin, true, true)
                },
                onCancel = {
                    callback.invoke(origin, false, false)
                }
            )
            return
        }

        showLocationAccessDialog(
            origin = uri,
            isSecure = isSecure,
            onAllowOnce = { continueGeolocationPermissionRequest(origin, callback) },
            onAllowHost = {
                host?.let { BrowserPreferences.addAllowedLocationHost(activity, it) }
                continueGeolocationPermissionRequest(origin, callback)
            },
            onCancel = { callback.invoke(origin, false, false) }
        )
    }

    private fun continueGeolocationPermissionRequest(origin: String?, callback: android.webkit.GeolocationPermissions.Callback) {
        val hasFine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            callback.invoke(origin, true, true)
        } else {
            pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, false, false)

            pendingGeolocationOrigin = origin
            pendingGeolocationCallback = callback
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                AppConstants.REQUEST_CODE_ACCESS_LOCATION
            )
        }
    }

    fun showLocationUpgradeDialog(
        origin: Uri?,
        onUpgrade: () -> Unit,
        onKeepApproximate: () -> Unit,
        onCancel: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onCancel()
            return
        }
        if (isShowingLocationDialog) return
        isShowingLocationDialog = true

        val view = activity.layoutInflater.inflate(R.layout.dialog_cleartext_confirmation, null)
        val titleView = view.findViewById<TextView>(R.id.cleartext_title)
        val messageView = view.findViewById<TextView>(R.id.cleartext_message)
        val hostContainer = view.findViewById<View>(R.id.cleartext_host_container)
        val hostLabelView = view.findViewById<TextView>(R.id.cleartext_host_label)
        val hostValueView = view.findViewById<TextView>(R.id.cleartext_host_value)
        val detailView = view.findViewById<TextView>(R.id.cleartext_detail)
        val cancelButton = view.findViewById<MaterialButton>(R.id.btn_cancel_dialog)
        val allowOnceButton = view.findViewById<MaterialButton>(R.id.btn_allow_once)
        val allowHostButton = view.findViewById<MaterialButton>(R.id.btn_allow_host)
        val dialog = MaterialAlertDialogBuilder(
            activity,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        ).setView(view).create()

        titleView.text = activity.getString(R.string.location_upgrade_title)
        messageView.text = activity.getString(R.string.location_upgrade_message)

        val originLabel = origin?.host ?: origin?.toString() ?: activity.getString(R.string.location_access_unknown_origin)
        hostContainer.visibility = View.VISIBLE
        hostLabelView.text = activity.getString(R.string.location_access_host_label)
        hostValueView.text = originLabel

        detailView.visibility = View.GONE

        allowHostButton.visibility = View.VISIBLE
        allowHostButton.text = activity.getString(R.string.location_upgrade_btn_precise)
        allowOnceButton.text = activity.getString(R.string.location_upgrade_btn_approximate)
        cancelButton.text = activity.getString(android.R.string.cancel)

        cancelButton.setOnClickListener {
            try { dialog.dismiss() } catch (_: Exception) {}
            onCancel()
        }
        allowOnceButton.setOnClickListener {
            try { dialog.dismiss() } catch (_: Exception) {}
            onKeepApproximate()
        }
        allowHostButton.setOnClickListener {
            try { dialog.dismiss() } catch (_: Exception) {}
            onUpgrade()
        }

        dialog.setOnDismissListener { isShowingLocationDialog = false }

        try {
            dialog.show()
            val width = (activity.resources.displayMetrics.widthPixels * 0.9).toInt()
            dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        } catch (_: Exception) {
            isShowingLocationDialog = false
            onCancel()
        }
    }

    fun showLocationAccessDialog(
        origin: Uri?,
        isSecure: Boolean,
        onAllowOnce: () -> Unit,
        onAllowHost: () -> Unit,
        onCancel: () -> Unit
    ) {
        val originLabel = origin?.host ?: origin?.toString() ?: activity.getString(R.string.location_access_unknown_origin)
        showSitePermissionDialog(
            title = activity.getString(R.string.location_access_title),
            message = activity.getString(R.string.location_access_message),
            dialogType = DIALOG_TYPE_LOCATION,
            hostLabel = activity.getString(R.string.location_access_host_label),
            hostValue = originLabel,
            detailMessage = activity.getString(R.string.location_access_detail),
            onAllowOnce = onAllowOnce,
            onAllowHost = if (origin?.host.isNullOrBlank() || !isSecure) null else onAllowHost,
            onCancel = onCancel
        )
    }

    fun showMicrophoneAccessDialog(
        origin: Uri?,
        isSecure: Boolean,
        onAllowOnce: () -> Unit,
        onAllowHost: () -> Unit,
        onCancel: () -> Unit
    ) {
        val originLabel = origin?.host ?: origin?.toString() ?: activity.getString(R.string.microphone_access_unknown_origin)
        showSitePermissionDialog(
            title = activity.getString(R.string.microphone_access_title),
            message = activity.getString(R.string.microphone_access_message),
            dialogType = DIALOG_TYPE_MICROPHONE,
            hostLabel = activity.getString(R.string.microphone_access_host_label),
            hostValue = originLabel,
            detailMessage = activity.getString(R.string.microphone_access_detail),
            onAllowOnce = onAllowOnce,
            onAllowHost = if (origin?.host.isNullOrBlank() || !isSecure) null else onAllowHost,
            onCancel = onCancel
        )
    }

    fun showCleartextNavigationDialog(
        uri: Uri,
        onAllowOnce: () -> Unit,
        onAllowHost: () -> Unit,
        onCancel: () -> Unit
    ) {
        val host = uri.host ?: uri.toString()
        showSitePermissionDialog(
            title = activity.getString(R.string.cleartext_connection_title),
            message = activity.getString(R.string.cleartext_connection_message, host),
            dialogType = DIALOG_TYPE_CLEARTEXT,
            onAllowOnce = onAllowOnce,
            onAllowHost = onAllowHost,
            onCancel = onCancel
        )
    }

    private fun showSitePermissionDialog(
        title: String,
        message: String,
        dialogType: Int,
        hostLabel: String? = null,
        hostValue: String? = null,
        detailMessage: String? = null,
        onAllowOnce: () -> Unit,
        onAllowHost: (() -> Unit)?,
        onCancel: () -> Unit
    ) {
        val flagAccessor: () -> Boolean = {
            when (dialogType) {
                DIALOG_TYPE_MICROPHONE -> isShowingMicrophoneDialog
                DIALOG_TYPE_LOCATION -> isShowingLocationDialog
                else -> isShowingCleartextDialog
            }
        }
        val flagSetter: (Boolean) -> Unit = { showing ->
            when (dialogType) {
                DIALOG_TYPE_MICROPHONE -> isShowingMicrophoneDialog = showing
                DIALOG_TYPE_LOCATION -> isShowingLocationDialog = showing
                else -> isShowingCleartextDialog = showing
            }
        }

        if (activity.isFinishing || activity.isDestroyed) {
            onCancel()
            return
        }
        if (flagAccessor()) return
        flagSetter(true)

        val view = activity.layoutInflater.inflate(R.layout.dialog_cleartext_confirmation, null)
        val titleView = view.findViewById<TextView>(R.id.cleartext_title)
        val messageView = view.findViewById<TextView>(R.id.cleartext_message)
        val hostContainer = view.findViewById<View>(R.id.cleartext_host_container)
        val hostLabelView = view.findViewById<TextView>(R.id.cleartext_host_label)
        val hostValueView = view.findViewById<TextView>(R.id.cleartext_host_value)
        val detailView = view.findViewById<TextView>(R.id.cleartext_detail)
        val cancelButton = view.findViewById<MaterialButton>(R.id.btn_cancel_dialog)
        val allowOnceButton = view.findViewById<MaterialButton>(R.id.btn_allow_once)
        val allowHostButton = view.findViewById<MaterialButton>(R.id.btn_allow_host)
        val dialog = MaterialAlertDialogBuilder(
            activity,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        ).setView(view).create()

        titleView.text = title
        messageView.text = message
        if (!hostLabel.isNullOrBlank() && !hostValue.isNullOrBlank()) {
            hostContainer.visibility = View.VISIBLE
            hostLabelView.text = hostLabel
            hostValueView.text = hostValue
        } else {
            hostContainer.visibility = View.GONE
        }
        if (!detailMessage.isNullOrBlank()) {
            detailView.visibility = View.VISIBLE
            detailView.text = detailMessage
        } else {
            detailView.visibility = View.GONE
        }

        cancelButton.setOnClickListener {
            try { dialog.dismiss() } catch (_: Exception) {}
            onCancel()
        }
        allowOnceButton.setOnClickListener {
            try { dialog.dismiss() } catch (_: Exception) {}
            onAllowOnce()
        }
        if (onAllowHost != null) {
            allowHostButton.visibility = View.VISIBLE
            allowHostButton.setOnClickListener {
                try { dialog.dismiss() } catch (_: Exception) {}
                onAllowHost()
            }
        } else {
            allowHostButton.visibility = View.GONE
        }

        dialog.setOnDismissListener { flagSetter(false) }

        try {
            dialog.show()
            val width = (activity.resources.displayMetrics.widthPixels * 0.9).toInt()
            dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        } catch (_: Exception) {
            flagSetter(false)
            onCancel()
        }
    }

    fun handleRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray,
        onRecordAudioGranted: (Boolean) -> Unit
    ) {
        if (requestCode == AppConstants.REQUEST_CODE_RECORD_AUDIO) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

            val request = pendingPermissionRequest
            pendingPermissionRequest = null
            if (request != null) {
                if (granted) {
                    val grantable = grantableWebPermissionResources(request)
                    if (grantable.isNotEmpty()) request.grant(grantable) else request.deny()
                } else {
                    denyAudioButAllowProtectedMediaIfPresent(request)
                }
            }
            onRecordAudioGranted(granted)
            pendingSpeechBridgeTabId = null
        } else if (requestCode == AppConstants.REQUEST_CODE_ACCESS_LOCATION) {
            val hasFine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val granted = hasFine || hasCoarse
            val callback = pendingGeolocationCallback
            val origin = pendingGeolocationOrigin
            pendingGeolocationCallback = null
            pendingGeolocationOrigin = null
            if (callback != null) {
                callback.invoke(origin, granted, true)
            }
        }
    }
}
