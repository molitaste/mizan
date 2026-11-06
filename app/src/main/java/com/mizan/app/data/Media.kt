package com.mizan.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/** Photo and attachment files. Everything is copied into the app's own storage so it survives restarts. */
object Media {
    private const val MAX_DIM = 2000

    fun newMediaFile(ext: String = "jpg"): File =
        Store.media("IMG_${System.currentTimeMillis()}_${(1000..9999).random()}.$ext")

    fun newSourceFile(ext: String): File =
        Store.source("SRC_${System.currentTimeMillis()}_${(1000..9999).random()}.$ext")

    fun uriFor(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)

    fun displayName(ctx: Context, uri: Uri): String = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: ""
    } catch (_: Exception) {
        ""
    }

    /** Copy a gallery image into the media folder (resized, rotated upright). Returns the file name. */
    fun importImage(ctx: Context, uri: Uri, intoSources: Boolean = false): String? {
        val out = if (intoSources) newSourceFile("jpg") else newMediaFile()
        val ok = try {
            process({ ctx.contentResolver.openInputStream(uri) }, out)
        } catch (_: Exception) {
            false
        }
        if (!ok) out.delete()
        return if (ok) out.name else null
    }

    /** Copy any file byte-for-byte into the sources folder (PDF originals). */
    fun importRaw(ctx: Context, uri: Uri, ext: String): String? {
        val out = newSourceFile(ext)
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { i -> FileOutputStream(out).use { i.copyTo(it) } } ?: return null
            if (out.length() > 0) out.name else { out.delete(); null }
        } catch (_: Exception) {
            out.delete()
            null
        }
    }

    /** Shrink and rotate a camera photo in place. */
    fun normalize(f: File): Boolean {
        if (!f.exists() || f.length() == 0L) return false
        return try {
            process({ FileInputStream(f) }, f)
        } catch (_: Exception) {
            f.exists() && f.length() > 0
        }
    }

    private fun process(open: () -> InputStream?, out: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = open() ?: return false
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_DIM || bounds.outHeight / (sample * 2) >= MAX_DIM) sample *= 2
        val bmp = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return false
        val rotation = try {
            open()?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (_: Exception) {
            0
        }
        val m = Matrix()
        val scale = MAX_DIM.toFloat() / maxOf(bmp.width, bmp.height)
        if (scale < 1f) m.postScale(scale, scale)
        if (rotation != 0) m.postRotate(rotation.toFloat())
        val result = if (m.isIdentity) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (result !== bmp) bmp.recycle()
        val tmp = File(out.parentFile, out.name + ".tmp")
        FileOutputStream(tmp).use { result.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        result.recycle()
        if (out.exists()) out.delete()
        return tmp.renameTo(out)
    }

    fun saveBitmap(src: Bitmap, intoSources: Boolean): String? {
        val out = if (intoSources) newSourceFile("jpg") else newMediaFile()
        return try {
            FileOutputStream(out).use { src.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            out.name
        } catch (_: Exception) {
            out.delete()
            null
        }
    }

    fun decodeSmall(f: File, targetW: Int, targetH: Int): Bitmap? {
        if (!f.exists()) return null
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, b)
        if (b.outWidth <= 0) return null
        var sample = 1
        while (b.outWidth / (sample * 2) >= targetW && b.outHeight / (sample * 2) >= targetH) sample *= 2
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Ordered-dither to pure black & white (E-Ink previews and B/W print). Original file is untouched. */
    fun twoTone(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]
            val lum = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            val threshold = (bayer[(y and 3) * 4 + (x and 3)] * 16) + 8
            px[y * w + x] = if (lum > threshold) -0x1 else -0x1000000
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun grayscale(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val p = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
        }
        c.drawBitmap(src, 0f, 0f, p)
        return out
    }
}
