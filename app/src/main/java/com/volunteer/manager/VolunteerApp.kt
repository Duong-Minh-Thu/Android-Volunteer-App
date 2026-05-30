/**
 * ============================================================================
 * TÊN FILE: VolunteerApp.kt
 * MỤC ĐÍCH: Lớp Application chính của ứng dụng Volunteer Manager. Khởi tạo các cấu
 *           hình toàn cục và dịch vụ chạy ngầm ngay khi ứng dụng bắt đầu khởi chạy.
 * CHỨC NĂNG CHÍNH:
 *   1. Khởi chạy ứng dụng và thừa kế lớp Application để thiết lập môi trường.
 *   2. Khởi chạy FirebaseBackgroundService để theo dõi các chiến dịch tình nguyện
 *      mới được thêm trên Realtime Database và gửi thông báo cục bộ cho người dùng.
 * ============================================================================
 */
package com.volunteer.manager

import android.app.Application
import android.content.Intent
import com.volunteer.manager.notifications.FirebaseBackgroundService

class VolunteerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Khởi động dịch vụ chạy ngầm để lắng nghe các chiến dịch mới được thêm từ Firebase
        val serviceIntent = Intent(this, FirebaseBackgroundService::class.java)
        startService(serviceIntent)
    }
}

