/**
 * Dự án: Volunteer Manager
 * File: FirebaseBackgroundService.kt
 * Chức năng: Dịch vụ chạy ngầm (Background Service) lắng nghe sự kiện từ Firebase Database.
 * Các chức năng chính:
 * - addChildEventListener(): Theo dõi nhánh `campaigns` trên cơ sở dữ liệu thời gian thực.
 * - showNotification(): Khi có chiến dịch tình nguyện mới được thêm từ bất kỳ nhà tổ chức nào, dịch vụ sẽ tự động gửi thông báo đẩy cục bộ (Local Notification) lên thanh trạng thái của thiết bị người dùng.
 * - Sử dụng cờ START_STICKY để đảm bảo hệ thống tự động khởi chạy lại dịch vụ này khi bị thu hồi tài nguyên do chạy ngầm.
 */
package com.volunteer.manager.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.volunteer.manager.R
import com.volunteer.manager.models.Campaign
import com.volunteer.manager.ui.MainActivity

class FirebaseBackgroundService : Service() {

    private val database = FirebaseDatabase.getInstance().getReference("campaigns")
    private var isFirstLoad = true

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        database.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // Bỏ qua lượt tải dữ liệu ban đầu khi vừa bật app để tránh bắn thông báo hàng loạt cho tin cũ
                if (!isFirstLoad) {
                    val campaign = snapshot.getValue(Campaign::class.java)
                    campaign?.let {
                        showNotification(it.title)
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })

        // Sau 5 giây chạy ngầm ban đầu, đánh dấu đã nạp xong danh sách cũ và sẵn sàng bắt chiến dịch MỚI
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isFirstLoad = false
        }, 5000)

        return START_STICKY
    }

    // Hiển thị thông báo đẩy lên thiết bị người dùng
    private fun showNotification(title: String) {
        val channelId = "new_campaign_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Hoạt động Mới", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Hoạt động Tình nguyện Mới!")
            .setContentText("Khám phá ngay: $title")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
