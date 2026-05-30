/**
 * Dự án: Volunteer Manager
 * File: LeaderboardAdapter.kt
 * Chức năng: Bộ tiếp hợp hiển thị danh sách Bảng xếp hạng Tình nguyện viên (RecyclerView Adapter).
 * Các đặc tính chính:
 * - Hiển thị thứ tự xếp hạng (Rank) cùng với màu nền nổi bật (Top 1 - Vàng Gold, Top 2 - Bạc Silver, Top 3 - Đồng Bronze).
 * - Hiển thị thông tin tên, vai trò, số điểm tích lũy và nạp ảnh đại diện Base64 thực tế của Tình nguyện viên.
 */
package com.volunteer.manager.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.volunteer.manager.databinding.ItemLeaderboardUserBinding
import com.volunteer.manager.models.User

data class LeaderboardEntry(val user: User, val score: Int)

class LeaderboardAdapter(
    private var entries: List<LeaderboardEntry>
) : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemLeaderboardUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: LeaderboardEntry, position: Int) {
            val rank = position + 1
            binding.tvRankNumber.text = rank.toString()
            
            // Premium background tints for top 3
            when (rank) {
                1 -> {
                    binding.viewRankBg.visibility = View.VISIBLE
                    binding.viewRankBg.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F59E0B")) // Gold
                    binding.tvRankNumber.setTextColor(Color.WHITE)
                }
                2 -> {
                    binding.viewRankBg.visibility = View.VISIBLE
                    binding.viewRankBg.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#94A3B8")) // Silver
                    binding.tvRankNumber.setTextColor(Color.WHITE)
                }
                3 -> {
                    binding.viewRankBg.visibility = View.VISIBLE
                    binding.viewRankBg.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B45309")) // Bronze
                    binding.tvRankNumber.setTextColor(Color.WHITE)
                }
                else -> {
                    binding.viewRankBg.visibility = View.GONE
                    binding.tvRankNumber.setTextColor(Color.parseColor("#475569")) // Slate_600
                }
            }

            binding.tvLeaderboardUserName.text = entry.user.name
            binding.tvLeaderboardUserRole.text = translateRole(entry.user.role)
            binding.tvLeaderboardPoints.text = entry.score.toString()

            // Profile character
            if (!entry.user.avatarUrl.isNullOrEmpty()) {
                binding.tvLeaderboardAvatarChar.visibility = View.GONE
                binding.ivLeaderboardAvatar.visibility = View.VISIBLE
                com.volunteer.manager.utils.ImageLoader.loadImage(binding.root.context, entry.user.avatarUrl, binding.ivLeaderboardAvatar)
            } else {
                binding.tvLeaderboardAvatarChar.visibility = View.VISIBLE
                binding.ivLeaderboardAvatar.visibility = View.GONE
                val firstChar = if (entry.user.name.isNotEmpty()) entry.user.name.substring(0, 1).uppercase() else "U"
                binding.tvLeaderboardAvatarChar.text = firstChar
            }
        }

        private fun translateRole(role: String): String {
            return when (role) {
                "ORG" -> "Nhà Tổ Chức"
                "Admin" -> "Quản Trị Viên (Admin)"
                else -> "Tình Nguyện Viên"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLeaderboardUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(entries[position], position)
    }

    override fun getItemCount() = entries.size

    fun updateData(newList: List<LeaderboardEntry>) {
        entries = newList
        notifyDataSetChanged()
    }
}
