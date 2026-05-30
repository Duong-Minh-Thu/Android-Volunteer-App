package com.volunteer.manager.adapters

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.volunteer.manager.databinding.ItemCampaignBinding
import com.volunteer.manager.models.Campaign

class CampaignAdapter(
    private var campaigns: List<Campaign>,
    private val onClick: (Campaign) -> Unit,
    private val onDoubleTap: (Campaign) -> Unit
) : RecyclerView.Adapter<CampaignAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemCampaignBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(campaign: Campaign) {
            binding.tvTitle.text = campaign.title
            binding.tvTime.text = campaign.time
            Glide.with(binding.root.context).load(campaign.imageUrl).into(binding.ivCampaign)

            val detector = GestureDetector(binding.root.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onClick(campaign)
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    onDoubleTap(campaign)
                    return true
                }
            })

            binding.root.setOnTouchListener { _, event ->
                detector.onTouchEvent(event)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCampaignBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(campaigns[position])
    }

    override fun getItemCount() = campaigns.size

    fun updateData(newList: List<Campaign>) {
        campaigns = newList
        notifyDataSetChanged()
    }
}
