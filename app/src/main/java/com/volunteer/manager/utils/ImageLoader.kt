/**
 * Dự án: Volunteer Manager
 * File: ImageLoader.kt
 * Chức năng: Tiện ích xử lý và nạp ảnh thông minh cho ứng dụng.
 * Các chức năng chính:
 * - loadImage(): Nạp ảnh bất đồng bộ từ liên kết mạng (sử dụng thư viện Glide) hoặc tự động phát hiện, giải mã chuỗi Base64 trên luồng nền (background thread) để tránh gây nghẽn luồng giao diện (UI thread).
 * - compressBitmapToBase64(): Thực hiện nén tỷ lệ ảnh về kích thước tối đa 600px và giảm chất lượng còn 70% định dạng JPEG, giúp chuyển đổi sang chuỗi Base64 cực kỳ nhỏ gọn nhằm tối ưu băng thông truyền tải và dung lượng lưu trữ trên Firebase Realtime Database.
 */
package com.volunteer.manager.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView
import com.bumptech.glide.Glide
import java.io.ByteArrayOutputStream

object ImageLoader {
    // Nạp ảnh bất đồng bộ an toàn từ URL hoặc chuỗi Base64
    fun loadImage(context: Context, imageUrl: String?, imageView: ImageView) {
        if (imageUrl.isNullOrEmpty()) {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }

        // Nếu là dữ liệu ảnh nén dạng Base64
        if (imageUrl.startsWith("data:image")) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            Thread {
                try {
                    val base64Str = imageUrl.substringAfter(",")
                    val bytes = Base64.decode(base64Str, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    handler.post {
                        imageView.setImageBitmap(bitmap)
                    }
                } catch (e: Exception) {
                    handler.post {
                        imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
            }.start()
        } else {
            // Nạp từ URL web thông thường sử dụng thư viện Glide
            Glide.with(context)
                .load(imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(imageView)
        }
    }

    // Nén ảnh về 600px chất lượng 70% JPEG và mã hóa sang chuỗi Base64
    fun compressBitmapToBase64(bitmap: Bitmap): String {
        val maxDimension = 600
        val width = bitmap.width
        val height = bitmap.height
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (height * (maxDimension.toFloat() / width)).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (width * (maxDimension.toFloat() / height)).toInt()
        }
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val bytes = outputStream.toByteArray()
        val base64Str = Base64.encodeToString(bytes, Base64.DEFAULT).trim().replace("\n", "").replace("\r", "")
        return "data:image/jpeg;base64,$base64Str"
    }
}
