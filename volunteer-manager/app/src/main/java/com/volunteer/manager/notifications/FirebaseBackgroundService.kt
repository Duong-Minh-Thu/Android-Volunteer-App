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
                // Ignore the initial load to only notify for NEW campaigns
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

        // Simple trick to set isFirstLoad to false after a short delay
        // In a real app, you might use a more robust way to track last seen ID
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isFirstLoad = false
        }, 5000)

        return START_STICKY
    }

    private fun showNotification(title: String) {
        val channelId = "new_campaign_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "New Campaigns", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New Volunteer Activity")
            .setContentText("Check out: $title")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
