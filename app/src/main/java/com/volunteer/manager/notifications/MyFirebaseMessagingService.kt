/**
 * ============================================================================
 * TÊN FILE: MyFirebaseMessagingService.kt
 * MỤC ĐÍCH: Dịch vụ xử lý thông báo đẩy từ Firebase Cloud Messaging (FCM).
 * CHỨC NĂNG CHÍNH:
 *   1. Lắng nghe và tiếp nhận thông báo đẩy từ Firebase gửi tới thiết bị (onMessageReceived).
 *   2. Tạo kênh thông báo (Notification Channel) cho các thiết bị Android từ Oreo (API 26) trở lên.
 *   3. Xây dựng và hiển thị thông báo cục bộ (local notification) lên thanh trạng thái.
 *   4. Định tuyến người dùng quay trở lại MainActivity khi họ nhấn vào thông báo.
 * ============================================================================
 */
package com.volunteer.manager.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.volunteer.manager.R
import com.volunteer.manager.ui.MainActivity

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // Nhận thông điệp từ Firebase Cloud Messaging gửi về
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.notification?.let {
            sendNotification(it.title ?: "Chiến dịch mới", it.body ?: "Một hoạt động tình nguyện mới đang chờ bạn!")
        }
    }

    // Xây dựng và phát thông báo cục bộ lên thanh trạng thái điện thoại
    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

        val channelId = "campaign_notifications"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Đăng ký Kênh thông báo đối với các phiên bản Android Oreo (Android 8) trở lên
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Chiến dịch", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }
}

