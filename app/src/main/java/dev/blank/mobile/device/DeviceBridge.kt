package dev.blank.mobile.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import dev.blank.mobile.agent.Selector
import dev.blank.mobile.agent.TreeSerializer
import dev.blank.mobile.agent.UiNode
import dev.blank.mobile.agent.UiRect
import dev.blank.mobile.provider.ImageAttachment
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

data class Observation(val tree: String, val image: ImageAttachment?)

object DeviceBridge {
    private fun service(): AccessibilityService =
        BlankAccessibilityService.instance ?: throw IllegalStateException("Accessibility service is not connected")

    suspend fun observe(screenshot: Boolean): Observation {
        val pairs = collect()
        val nodes = pairs.map { it.first }.filter { it.clickable || it.editable || it.text.isNotBlank() || it.desc.isNotBlank() }
        val tree = TreeSerializer.serialize(nodes)
        pairs.forEach { it.second.recycle() }
        val image = if (screenshot) screenshot() else null
        return Observation(tree, image)
    }

    suspend fun act(args: JsonObject): String {
        val s = service()
        return when (val action = args["action"]?.jsonPrimitive?.content) {
            "tap", "long_press" -> {
                val long = action == "long_press"
                val node = resolveNode(args)
                if (node != null) {
                    val ok = node.performAction(if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK)
                    val bounds = Rect().also { node.getBoundsInScreen(it) }
                    node.recycle()
                    if (ok) return if (long) "Long-pressed element" else "Tapped element"
                    return gestureTap(bounds.centerX().toFloat(), bounds.centerY().toFloat(), long)
                }
                val x = args["x"]?.jsonPrimitive?.intOrNull
                val y = args["y"]?.jsonPrimitive?.intOrNull
                if (x == null || y == null) "No matching element and no coordinates given"
                else gestureTap(x.toFloat(), y.toFloat(), long)
            }
            "swipe" -> {
                val metrics = s.resources.displayMetrics
                val w = metrics.widthPixels.toFloat()
                val h = metrics.heightPixels.toFloat()
                val direction = args["direction"]?.jsonPrimitive?.content
                val path = Path()
                if (direction != null) {
                    when (direction) {
                        "up" -> { path.moveTo(w / 2, h * 0.7f); path.lineTo(w / 2, h * 0.3f) }
                        "down" -> { path.moveTo(w / 2, h * 0.3f); path.lineTo(w / 2, h * 0.7f) }
                        "left" -> { path.moveTo(w * 0.8f, h / 2); path.lineTo(w * 0.2f, h / 2) }
                        else -> { path.moveTo(w * 0.2f, h / 2); path.lineTo(w * 0.8f, h / 2) }
                    }
                } else {
                    val x = args["x"]?.jsonPrimitive?.intOrNull ?: return "swipe missing coordinates"
                    val y = args["y"]?.jsonPrimitive?.intOrNull ?: return "swipe missing coordinates"
                    val x2 = args["x2"]?.jsonPrimitive?.intOrNull ?: return "swipe missing coordinates"
                    val y2 = args["y2"]?.jsonPrimitive?.intOrNull ?: return "swipe missing coordinates"
                    path.moveTo(x.toFloat(), y.toFloat())
                    path.lineTo(x2.toFloat(), y2.toFloat())
                }
                if (gesture(path, 300)) "Swiped ${direction ?: "along path"}" else "Swipe gesture was cancelled"
            }
            "type" -> {
                val text = args["text"]?.jsonPrimitive?.content ?: return "type missing text"
                var node = resolveNode(args)
                if (node == null) node = s.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (node == null) return "No focused input field. Tap a text field first."
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val bundle = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                node.recycle()
                if (ok) "Typed \"$text\"" else "Failed to set text on the field"
            }
            "key" -> {
                when (val key = args["key"]?.jsonPrimitive?.content) {
                    "back" -> { s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); "Pressed back" }
                    "home" -> { s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); "Pressed home" }
                    "recents" -> { s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS); "Opened recents" }
                    "enter" -> {
                        val node = s.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                        if (node == null) "No focused input field"
                        else {
                            val ok = node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                            node.recycle()
                            if (ok) "Pressed enter" else "Enter action not supported by this field"
                        }
                    }
                    "delete" -> {
                        val node = s.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                        if (node == null) "No focused input field"
                        else {
                            val current = node.text?.toString() ?: ""
                            val bundle = Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, current.dropLast(1))
                            }
                            val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                            node.recycle()
                            if (ok) "Deleted last character" else "Failed to edit the field"
                        }
                    }
                    else -> "Unknown key: $key"
                }
            }
            "open_url" -> {
                val url = args["url"]?.jsonPrimitive?.content ?: return "open_url missing url"
                s.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opened $url"
            }
            "wait" -> {
                val ms = (args["ms"]?.jsonPrimitive?.intOrNull ?: 1000).coerceIn(100, 10000)
                delay(ms.toLong())
                "Waited ${ms}ms"
            }
            else -> "Unknown action: $action"
        }
    }

    private suspend fun gestureTap(x: Float, y: Float, long: Boolean): String {
        val path = Path().apply { moveTo(x, y) }
        val ok = gesture(path, if (long) 600 else 50)
        return if (ok) (if (long) "Long-pressed ($x, $y)" else "Tapped ($x, $y)") else "Tap gesture was cancelled"
    }

    private suspend fun gesture(path: Path, duration: Long): Boolean {
        val s = service()
        return suspendCancellableCoroutine { cont ->
            val dispatched = s.dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                    .build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gesture: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gesture: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null,
            )
            if (!dispatched && cont.isActive) cont.resume(false)
        }
    }

    private fun resolveNode(args: JsonObject): AccessibilityNodeInfo? {
        val selector = args["selector"] as? JsonObject ?: return null
        val pairs = collect()
        val nodes = pairs.map { it.first }
            .filter { it.clickable || it.editable || it.text.isNotBlank() || it.desc.isNotBlank() }
        val filteredPairs = pairs.filter { it.first.clickable || it.first.editable || it.first.text.isNotBlank() || it.first.desc.isNotBlank() }
        val match = Selector.match(nodes, selector)
        if (match == null) {
            pairs.forEach { it.second.recycle() }
            return null
        }
        val index = nodes.indexOf(match)
        val live = filteredPairs[index].second
        pairs.forEach { if (it.second !== live) it.second.recycle() }
        return live
    }

    private fun collect(): List<Pair<UiNode, AccessibilityNodeInfo>> {
        val s = BlankAccessibilityService.instance ?: return emptyList()
        val root = s.rootInActiveWindow ?: return emptyList()
        val out = mutableListOf<Pair<UiNode, AccessibilityNodeInfo>>()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40 || out.size >= 400) return
            val rect = Rect().also { node.getBoundsInScreen(it) }
            val ui = UiNode(
                text = node.text?.toString() ?: "",
                desc = node.contentDescription?.toString() ?: "",
                resourceId = node.viewIdResourceName ?: "",
                className = node.className?.toString() ?: "",
                clickable = node.isClickable,
                editable = node.isEditable,
                bounds = UiRect(rect.left, rect.top, rect.right, rect.bottom),
            )
            out += ui to node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { walk(it, depth + 1) }
            }
        }
        walk(root, 0)
        return out
    }

    private suspend fun screenshot(): ImageAttachment? {
        val s = BlankAccessibilityService.instance ?: return null
        val bitmap = suspendCancellableCoroutine<Bitmap?> { cont ->
            s.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                s.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        result.hardwareBuffer.close()
                        val soft = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        hardware?.recycle()
                        if (cont.isActive) cont.resume(soft)
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        } ?: return null
        val scale = 720f / bitmap.width.coerceAtLeast(1)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return ImageAttachment("image/jpeg", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
    }
}