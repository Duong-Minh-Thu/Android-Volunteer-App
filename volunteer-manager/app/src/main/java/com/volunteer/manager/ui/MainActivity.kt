package com.volunteer.manager.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.volunteer.manager.adapters.CampaignAdapter
import com.volunteer.manager.databinding.ActivityMainBinding
import com.volunteer.manager.models.Campaign
import com.volunteer.manager.models.User

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CampaignAdapter
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var campaigns = mutableListOf<Campaign>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("campaigns")

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Volunteer Campaigns"

        setupRecyclerView()
        checkUserRole()
        loadCampaigns()

        binding.swipeRefresh.setOnRefreshListener {
            loadCampaigns()
        }

        binding.fabAdd.setOnClickListener {
            // Placeholder: In a real app, this would open an AddCampaignActivity
            Toast.makeText(this, "ORG feature: Add Campaign", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = CampaignAdapter(campaigns, { campaign ->
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra("campaign", campaign)
            startActivity(intent)
        }, { campaign ->
            addToFavorites(campaign)
        })
        binding.rvCampaigns.layoutManager = LinearLayoutManager(this)
        binding.rvCampaigns.adapter = adapter
    }

    private fun checkUserRole() {
        val uid = auth.currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("users").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)
                    if (user?.role == "ORG" || user?.role == "Admin") {
                        binding.fabAdd.visibility = View.VISIBLE
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadCampaigns() {
        binding.swipeRefresh.isRefreshing = true
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                campaigns.clear()
                for (child in snapshot.children) {
                    val campaign = child.getValue(Campaign::class.java)
                    campaign?.let {
                        it.id = child.key
                        campaigns.add(it)
                    }
                }
                adapter.updateData(campaigns)
                binding.swipeRefresh.isRefreshing = false
            }

            override fun onCancelled(error: DatabaseError) {
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun addToFavorites(campaign: Campaign) {
        val uid = auth.currentUser?.uid ?: return
        val campaignId = campaign.id ?: return
        val ref = database.child(campaignId).child("favoriteBy").child(uid)
        ref.setValue(true).addOnCompleteListener {
            if (it.isSuccessful) {
                Toast.makeText(this, "Added to Favorites!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
