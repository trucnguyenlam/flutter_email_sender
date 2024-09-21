package com.sidlatau.flutteremailsender

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.io.File

private const val SUBJECT = "subject"
private const val BODY = "body"
private const val RECIPIENTS = "recipients"
private const val CC = "cc"
private const val BCC = "bcc"
private const val ATTACHMENT_PATHS = "attachment_paths"
private const val CONTENT_URI_PATHS = "content_uri_paths"
private const val USB_MASS_STORAGE = "usb_mass_storage"
private const val IS_HTML = "is_html"
private const val REQUEST_CODE_SEND = 607

class FlutterEmailSenderPlugin
    : FlutterPlugin, ActivityAware, MethodCallHandler, PluginRegistry.ActivityResultListener {
    companion object {
        private const val METHOD_CHANNEL_NAME = "flutter_email_sender"
    }
    private var activity: Activity? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val channel = MethodChannel(binding.binaryMessenger, METHOD_CHANNEL_NAME)
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    }

    override fun onAttachedToActivity(activityPluginBinding: ActivityPluginBinding) {
        activity = activityPluginBinding.activity
        activityPluginBinding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(activityPluginBinding: ActivityPluginBinding) {
        activity = activityPluginBinding.activity
        activityPluginBinding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    private var channelResult: Result? = null

    override fun onMethodCall(call: MethodCall, result: Result) {
        if (call.method == "send") {
            // If the call threw an exception, Flutter already sent a result to the channel,
            // so we don't need to send a result from onActivityResult anymore.
            this.channelResult = result
            sendEmail(call, result)
        } else {
            result.notImplemented()
        }
    }

    private fun sendEmail(options: MethodCall, callback: Result) {
        if (activity == null) {
            callback.error("error", "Activity == null!", null)
            return
        }

        val body = options.argument<String>(BODY)
        val isHtml = options.argument<Boolean>(IS_HTML) ?: false
        val attachmentPaths = options.argument<ArrayList<String>>(ATTACHMENT_PATHS) ?: ArrayList()
        val contentUriPaths = options.argument<ArrayList<String>>(CONTENT_URI_PATHS) ?: ArrayList()
        val usbMassStorage = options.argument<Boolean>(USB_MASS_STORAGE) ?: false
        val subject = options.argument<String>(SUBJECT)
        val recipients = options.argument<ArrayList<String>>(RECIPIENTS)
        val cc = options.argument<ArrayList<String>>(CC)
        val bcc = options.argument<ArrayList<String>>(BCC)

        var text: CharSequence? = null
        var html: String? = null
        if (body != null) {
            if (isHtml) {
                text = HtmlCompat.fromHtml(body, HtmlCompat.FROM_HTML_MODE_LEGACY)
                html = body
            } else {
                text = body
            }
        }
        // Special branch for Android sdk 30+
        val attachmentUris = if (Build.VERSION.SDK_INT >= 30 || usbMassStorage) {
            contentUriPaths.map { Uri.parse(it) }
        } else {
            attachmentPaths.map {
                FileProvider.getUriForFile(activity!!, activity!!.packageName + ".file_provider", File(it))
            }
        }

        // We need a different intent action depending on the number of attachments.
        val intent = if (attachmentUris.isEmpty()) {
            val localIntent = Intent(Intent.ACTION_SENDTO)
            localIntent.data = Uri.parse("mailto:")
            localIntent
        } else {
            val localIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
            localIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            localIntent.type = "text/plain"
            localIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachmentUris))
            localIntent
        }

        if (text != null) {
            intent.putExtra(Intent.EXTRA_TEXT, text)

            val textList = java.util.ArrayList<CharSequence>()
            textList.add(text)
            intent.putCharSequenceArrayListExtra(Intent.EXTRA_TEXT, textList)
        }

        if (html != null) {
            intent.putExtra(Intent.EXTRA_HTML_TEXT, html)
        }

        if (subject != null) {
            intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        }

        if (recipients != null) {
            intent.putExtra(Intent.EXTRA_EMAIL, listArrayToArray(recipients))
        }

        if (cc != null) {
            intent.putExtra(Intent.EXTRA_CC, listArrayToArray(cc))
        }

        if (bcc != null) {
            intent.putExtra(Intent.EXTRA_BCC, listArrayToArray(bcc))
        }

        val packageManager = activity?.packageManager

        val chooserIntent = Intent.createChooser(intent, "Select Email app")
        if (packageManager?.resolveActivity(chooserIntent, 0) != null) {
            activity?.startActivityForResult(chooserIntent, REQUEST_CODE_SEND)
        } else {
            callback.error("not_available", "No email clients found!", null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return when (requestCode) {
            REQUEST_CODE_SEND -> {
                channelResult?.success(null)
                channelResult = null
                return true
            }
            else -> {
                false
            }
        }
    }

    private fun listArrayToArray(r: ArrayList<String>): Array<String> {
        return r.toArray(arrayOfNulls<String>(r.size))
    }
}
