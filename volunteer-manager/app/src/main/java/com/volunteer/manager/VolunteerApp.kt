package com.volunteer.manager

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class VolunteerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Start background listener for new campaigns
        val serviceIntent = android.content.Intent(this, com.volunteer.manager.notifications.FirebaseBackgroundService::class.java)
        startService(serviceIntent)
    }
}
