/**
 * Dự án: Volunteer Manager
 * File: CampaignAdapter.kt
 * Chức năng: Bộ tiếp hợp hiển thị danh sách Chiến dịch tình nguyện (RecyclerView Adapter).
 * Các đặc tính chính:
 * - Thiết kế giao diện Facebook-style với ảnh bìa rộng, tên nhà tổ chức (ORG), ngày đăng, giờ tổ chức sự kiện.
 * - Hỗ trợ nạp ảnh đại diện của ORG động từ cơ sở dữ liệu Firebase.
 * - Hỗ trợ cử chỉ nhấn đúp (Double tap) lên ảnh bìa để thả tim/thích nhanh chiến dịch.
 * - Thanh tương tác (Action bar) phong cách tối giản với số lượt thích, số lượt tham gia và số lượng bình luận được đồng bộ real-time.
 */
package com.volunteer.manager.adapters

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.volunteer.manager.R
import com.volunteer.manager.databinding.ItemCampaignBinding
import com.volunteer.manager.models.Campaign

class CampaignAdapter(
    private var campaigns: List<Campaign>,
    private val onClick: (Campaign) -> Unit,
    private val onLikeToggle: (Campaign) -> Unit,
    private val onJoinToggle: (Campaign) -> Unit,
    private val onShare: (Campaign) -> Unit,
    private val onCommentClick: (Campaign) -> Unit
) : RecyclerView.Adapter<CampaignAdapter.ViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    inner class ViewHolder(private val binding: ItemCampaignBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(campaign: Campaign) {
            // Title & Description
            binding.tvTitle.text = campaign.title
            binding.tvDescShort.text = campaign.description
            binding.tvEventTimeBadge.text = campaign.time

            // Load Org Avatar
            val orgId = if (campaign.orgId.isNotEmpty()) campaign.orgId else campaign.creatorId
            if (orgId.isNotEmpty()) {
                val userRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(orgId)
                userRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        val avatarUrl = snapshot.child("avatarUrl").getValue(String::class.java) ?: ""
                        if (avatarUrl.isNotEmpty()) {
                            binding.tvAvatarChar.visibility = View.GONE
                            binding.ivOrgAvatar.visibility = View.VISIBLE
                            com.volunteer.manager.utils.ImageLoader.loadImage(binding.root.context, avatarUrl, binding.ivOrgAvatar)
                        } else {
                            binding.tvAvatarChar.visibility = View.VISIBLE
                            binding.ivOrgAvatar.visibility = View.GONE
                            val firstChar = if (campaign.orgName.isNotEmpty()) {
                                campaign.orgName.substring(0, 1).uppercase()
                            } else {
                                if (campaign.title.isNotEmpty()) campaign.title.substring(0, 1).uppercase() else "V"
                            }
                            binding.tvAvatarChar.text = firstChar
                        }
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        binding.tvAvatarChar.visibility = View.VISIBLE
                        binding.ivOrgAvatar.visibility = View.GONE
                        val firstChar = if (campaign.orgName.isNotEmpty()) {
                            campaign.orgName.substring(0, 1).uppercase()
                        } else {
                            if (campaign.title.isNotEmpty()) campaign.title.substring(0, 1).uppercase() else "V"
                        }
                        binding.tvAvatarChar.text = firstChar
                    }
                })
            } else {
                binding.tvAvatarChar.visibility = View.VISIBLE
                binding.ivOrgAvatar.visibility = View.GONE
                val firstChar = if (campaign.title.isNotEmpty()) campaign.title.substring(0, 1).uppercase() else "V"
                binding.tvAvatarChar.text = firstChar
            }

            // Org Name (or Org ID as fallback)
            binding.tvOrgName.text = if (campaign.orgName.isNotEmpty()) {
                campaign.orgName
            } else if (campaign.orgId.isNotEmpty()) {
                "Ban Tổ Chức (ORG-${campaign.orgId.take(5)})"
            } else {
                "Volunteer Group"
            }
            binding.tvPostedTime.text = if (campaign.date.isNotEmpty()) campaign.date else "Hoạt động sắp diễn ra"

            // Image Load
            if (campaign.imageUrl.isNotEmpty()) {
                binding.ivCampaign.visibility = View.VISIBLE
                com.volunteer.manager.utils.ImageLoader.loadImage(binding.root.context, campaign.imageUrl, binding.ivCampaign)
            } else {
                binding.ivCampaign.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            // Stats Count
            val likesCount = campaign.favoriteBy.size
            val participantsCount = campaign.participants.size

            // Check if current user liked / joined
            val isLiked = campaign.favoriteBy.containsKey(currentUserId)
            val isJoined = campaign.participants.containsKey(currentUserId)

            // Setup Like Button Style with Inline Counts (Icon + Number only)
            if (isLiked) {
                binding.ivLikeIcon.setImageResource(R.drawable.ic_nav_favorites)
                binding.ivLikeIcon.setColorFilter(ContextCompat.getColor(binding.root.context, R.color.primary))
                binding.tvLikeBtnText.setTextColor(ContextCompat.getColor(binding.root.context, R.color.primary))
            } else {
                binding.ivLikeIcon.setImageResource(R.drawable.ic_like_outline)
                binding.ivLikeIcon.setColorFilter(ContextCompat.getColor(binding.root.context, R.color.slate_500))
                binding.tvLikeBtnText.setTextColor(ContextCompat.getColor(binding.root.context, R.color.slate_500))
            }
            binding.tvLikeBtnText.text = likesCount.toString()

            // Setup Join Button Style with Inline Counts (Icon + Number only)
            if (isJoined) {
                binding.ivJoinIcon.setImageResource(R.drawable.ic_checkmark)
                binding.ivJoinIcon.setColorFilter(ContextCompat.getColor(binding.root.context, android.R.color.holo_green_dark))
                binding.tvJoinBtnText.setTextColor(ContextCompat.getColor(binding.root.context, android.R.color.holo_green_dark))
            } else {
                binding.ivJoinIcon.setImageResource(android.R.drawable.ic_menu_add)
                binding.ivJoinIcon.setColorFilter(ContextCompat.getColor(binding.root.context, R.color.slate_500))
                binding.tvJoinBtnText.setTextColor(ContextCompat.getColor(binding.root.context, R.color.slate_500))
            }
            binding.tvJoinBtnText.text = participantsCount.toString()

            // Setup Comment Button with Inline Count (Icon + Number only)
            binding.tvCommentBtnText.text = campaign.commentsCount.toString()

            // Button listeners
            binding.btnLikeLayout.setOnClickListener {
                onLikeToggle(campaign)
            }

            binding.btnJoinLayout.setOnClickListener {
                onJoinToggle(campaign)
            }

            binding.btnCommentLayout.setOnClickListener {
                onCommentClick(campaign)
            }

            binding.btnShareLayout.setOnClickListener {
                onShare(campaign)
            }

            // Double tap to like gesture detector
            val detector = GestureDetector(binding.root.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onClick(campaign)
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    onLikeToggle(campaign)
                    return true
                }
            })

            // Set custom touch listener to image and body to allow gesture detection
            binding.ivCampaign.setOnTouchListener { _, event ->
                detector.onTouchEvent(event)
                true
            }
            
            binding.root.setOnClickListener {
                onClick(campaign)
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
