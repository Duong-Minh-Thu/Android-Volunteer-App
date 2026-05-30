package com.volunteer.manager.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.volunteer.manager.R
import com.volunteer.manager.api.WeatherResponse
import com.volunteer.manager.api.WeatherService
import com.volunteer.manager.databinding.ActivityDetailBinding
import com.volunteer.manager.models.Campaign
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DetailActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityDetailBinding
    private lateinit var campaign: Campaign
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val WEATHER_API_KEY = "YOUR_OPENWEATHER_KEY" // User should replace this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        campaign = intent.getSerializableExtra("campaign") as Campaign

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupUI()
        fetchWeather()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnJoin.setOnClickListener {
            joinCampaign()
        }
    }

    private fun setupUI() {
        binding.tvDetailTitle.text = campaign.title
        binding.tvDetailTime.text = campaign.time
        binding.tvDetailDesc.text = campaign.description
        Glide.with(this).load(campaign.imageUrl).into(binding.ivDetail)
    }

    private fun joinCampaign() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val campaignId = campaign.id ?: return
        FirebaseDatabase.getInstance().getReference("campaigns")
            .child(campaignId).child("participants").child(uid).setValue(true)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    Toast.makeText(this, "Joined successfully!", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val location = LatLng(campaign.latitude, campaign.longitude)
        mMap.addMarker(MarkerOptions().position(location).title(campaign.title))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))

        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            mMap.isMyLocationEnabled = true
            calculateDistance()
        }
    }

    private fun calculateDistance() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val results = FloatArray(1)
                    Location.distanceBetween(location.latitude, location.longitude, campaign.latitude, campaign.longitude, results)
                    val distance = results[0] / 1000 // KM
                    binding.tvDistance.text = "Distance: %.2f km".format(distance)
                }
            }
        }
    }

    private fun fetchWeather() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(WeatherService::class.java)
        service.getCurrentWeather(campaign.latitude, campaign.longitude, WEATHER_API_KEY)
            .enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        binding.tvWeather.text = "Weather: ${body?.main?.temp}°C, ${body?.weather?.get(0)?.description}"
                    }
                }
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {}
            })
    }
}
