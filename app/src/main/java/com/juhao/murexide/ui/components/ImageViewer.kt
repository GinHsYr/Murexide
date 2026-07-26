package com.juhao.murexide.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.doOnPreDraw
import com.flyjingfish.openimagelib.OpenImage
import com.flyjingfish.openimagelib.beans.ClickViewParam
import com.flyjingfish.openimagelib.beans.DownloadParams
import com.flyjingfish.openimagelib.beans.OpenImageUrl
import com.flyjingfish.openimagelib.enums.MediaType
import com.juhao.murexide.R
import com.juhao.murexide.utils.imageThumbnailUrl

data class OpenImageItem(
    val originalUrl: String,
    val thumbnailUrl: String = originalUrl,
    val messageId: String? = null,
    val imageId: Long? = null,
    val mediaType: MediaType = MediaType.IMAGE,
    val playbackUrl: String? = null
) : OpenImageUrl {
    override fun getImageUrl(): String = originalUrl

    override fun getVideoUrl(): String = playbackUrl.orEmpty()

    override fun getCoverImageUrl(): String = thumbnailUrl

    override fun getType(): MediaType = mediaType
}

fun imageMessagePreviewItem(
    url: String,
    messageId: String? = null,
    imageId: Long? = null
): OpenImageItem = OpenImageItem(
    originalUrl = url,
    thumbnailUrl = imageThumbnailUrl(url),
    messageId = messageId,
    imageId = imageId
)

fun fullImagePreviewItem(url: String): OpenImageItem = OpenImageItem(originalUrl = url)

fun videoMessagePreviewItem(
    url: String,
    messageId: String? = null,
    sequence: Long? = null
): OpenImageItem = OpenImageItem(
    originalUrl = url,
    thumbnailUrl = url,
    messageId = messageId,
    imageId = sequence,
    mediaType = MediaType.VIDEO,
    playbackUrl = url
)

data class MediaViewerPagination(
    val chatId: String,
    val chatType: Int
)

data class ImageViewerSourceBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val isCropped: Boolean
) {
    internal val isValid: Boolean
        get() = width > 0 && height > 0
}

fun showImageViewer(
    context: Context,
    images: List<OpenImageItem>,
    initialIndex: Int = 0,
    pagination: MediaViewerPagination? = null,
    sourceBounds: ImageViewerSourceBounds? = null
): Boolean {
    val activity = context.findActivity() ?: return false
    if (images.isEmpty()) return false

    val selectedIndex = initialIndex.coerceIn(images.indices)
    val viewerOptions = Bundle().apply {
        putStringArrayList(
            MurexideOpenImageActivity.EXTRA_MEDIA_URLS,
            ArrayList(images.map(OpenImageItem::originalUrl))
        )
        putStringArrayList(
            MurexideOpenImageActivity.EXTRA_MEDIA_MESSAGE_IDS,
            ArrayList(images.map { it.messageId.orEmpty() })
        )
        pagination?.let { options ->
            putString(MurexideOpenImageActivity.EXTRA_CHAT_ID, options.chatId)
            putInt(MurexideOpenImageActivity.EXTRA_CHAT_TYPE, options.chatType)
        }
    }
    val viewer = OpenImage.with(activity)
        .setImageUrlList(images)
        // Keep the first frame as a thumbnail, then let OpenImage load the
        // original image.  Keep one page on either side warm while swiping.
        .setBothLoadCover(true)
        .setPreloadCount(false, 1)
        .setOpenPageAnimTimeMs(WECHAT_TRANSITION_DURATION_MS)
        .setWechatExitFillInEffect(true)
        .setOpenImageStyle(R.style.Theme_Murexide_OpenImage)
        .setOpenImageActivityCls(
            MurexideOpenImageActivity::class.java,
            MurexideOpenImageActivity.EXTRA_VIEWER_OPTIONS,
            viewerOptions
        )
        .setShowDownload(viewerDownloadParams(activity))
        .setOnItemLongClickListener { _, image, _ ->
            val mediaUrl = if (image.type == MediaType.VIDEO) image.videoUrl else image.imageUrl
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Media URL", mediaUrl))
            Toast.makeText(activity, "链接已复制", Toast.LENGTH_SHORT).show()
        }

    val origin = sourceBounds?.takeIf { it.isValid }
    val decorView = activity.window.decorView
    if (origin != null) {
        captureTransitionSource(activity, decorView, origin) { transitionSource ->
            if (activity.isFinishing || activity.isDestroyed) {
                transitionSource?.let(::removeTransitionSource)
                return@captureTransitionSource
            }

            if (transitionSource == null) {
                showWithBoundsFallback(viewer, decorView, origin, selectedIndex)
                return@captureTransitionSource
            }

            viewer
                // OpenImage names an ImageView source from its position in this list.
                // Align that position with the selected media so both transition names match.
                .setClickImageViews(List(images.size) { transitionSource })
                .setClickPosition(selectedIndex)
                .setSrcImageViewScaleType(sourceScaleType(origin), true)
                .setOnExitListener { removeTransitionSource(transitionSource) }

            // A newly attached shared element can be laid out before Android has committed
            // its first frame. Launch on the following frame so the transition is reliable.
            transitionSource.doOnPreDraw {
                transitionSource.postOnAnimation {
                    if (
                        transitionSource.isAttachedToWindow &&
                        !activity.isFinishing &&
                        !activity.isDestroyed
                    ) {
                        viewer.show()
                    } else {
                        removeTransitionSource(transitionSource)
                    }
                }
            }
        }
        return true
    }

    viewer
        .setNoneClickView()
        .setClickPosition(selectedIndex)
        .show()
    return true
}

private fun showWithBoundsFallback(
    viewer: OpenImage,
    decorView: View,
    bounds: ImageViewerSourceBounds,
    selectedIndex: Int
) {
    if (decorView.width > 0) {
        viewer
            .setClickWebView(
                decorView,
                ClickViewParam(
                    bounds.width,
                    bounds.height,
                    bounds.top,
                    bounds.left,
                    decorView.width
                )
            )
            .setClickPosition(selectedIndex, 0)
            .setSrcImageViewScaleType(sourceScaleType(bounds), true)
    } else {
        viewer
            .setNoneClickView()
            .setClickPosition(selectedIndex)
    }

    viewer.show()
}

@Suppress("DEPRECATION")
private fun captureTransitionSource(
    activity: Activity,
    decorView: View,
    bounds: ImageViewerSourceBounds,
    onReady: (ImageView?) -> Unit
) {
    val container = decorView as? ViewGroup ?: run {
        onReady(null)
        return
    }
    val decorLocation = IntArray(2)
    decorView.getLocationInWindow(decorLocation)
    val captureBounds = Rect(
        maxOf(bounds.left, decorLocation[0]),
        maxOf(bounds.top, decorLocation[1]),
        minOf(bounds.left + bounds.width, decorLocation[0] + decorView.width),
        minOf(bounds.top + bounds.height, decorLocation[1] + decorView.height)
    )
    if (captureBounds.width() <= 0 || captureBounds.height() <= 0) {
        onReady(null)
        return
    }

    val bitmap = runCatching {
        Bitmap.createBitmap(
            captureBounds.width(),
            captureBounds.height(),
            Bitmap.Config.ARGB_8888
        )
    }.getOrNull() ?: run {
        onReady(null)
        return
    }
    val left = captureBounds.left - decorLocation[0]
    val top = captureBounds.top - decorLocation[1]

    fun attachSource(): ImageView = ImageView(activity).apply {
        setImageBitmap(bitmap)
        scaleType = sourceScaleType(bounds)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        container.addView(
            this,
            FrameLayout.LayoutParams(captureBounds.width(), captureBounds.height()).apply {
                leftMargin = left
                topMargin = top
            }
        )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        PixelCopy.request(
            activity.window,
            captureBounds,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS && decorView.isAttachedToWindow) {
                    onReady(attachSource())
                } else {
                    Log.w(TRANSITION_TAG, "PixelCopy failed with result $result")
                    bitmap.recycle()
                    onReady(null)
                }
            },
            Handler(Looper.getMainLooper())
        )
        return
    }

    val captured = runCatching {
        Canvas(bitmap).apply {
            translate(-left.toFloat(), -top.toFloat())
            decorView.draw(this)
        }
    }.isSuccess
    if (captured) {
        onReady(attachSource())
    } else {
        bitmap.recycle()
        onReady(null)
    }
}

private fun sourceScaleType(bounds: ImageViewerSourceBounds): ImageView.ScaleType {
    return if (bounds.isCropped) {
        ImageView.ScaleType.CENTER_CROP
    } else {
        ImageView.ScaleType.FIT_CENTER
    }
}

private fun removeTransitionSource(source: ImageView) {
    (source.parent as? ViewGroup)?.removeView(source)
    source.setImageDrawable(null)
}

private fun viewerDownloadParams(context: Context): DownloadParams {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    val layoutParams = FrameLayout.LayoutParams(dp(28), dp(28)).apply {
        gravity = Gravity.BOTTOM or Gravity.END
        marginEnd = dp(14)
        bottomMargin = dp(72)
    }
    return DownloadParams()
        .setDownloadSrc(R.drawable.ic_media_viewer_download)
        .setDownloadLayoutParams(layoutParams)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val WECHAT_TRANSITION_DURATION_MS = 250L
private const val TRANSITION_TAG = "MurexideMediaTransition"
