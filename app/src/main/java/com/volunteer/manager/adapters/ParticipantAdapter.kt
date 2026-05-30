/**
 * Dự án: Volunteer Manager
 * File: ParticipantAdapter.kt
 * Chức năng: Bộ tiếp hợp hiển thị danh sách thành viên Tình nguyện viên tham gia (RecyclerView Adapter).
 * Các đặc tính chính:
 * - Hỗ trợ nhà tổ chức (ORG) hoặc quản trị viên (Admin) xem và bấm xác nhận hoàn thành hoạt động cho từng Tình nguyện viên.
 * - Hiển thị trạng thái "Đã hoàn thành" hoặc nút hành động xác nhận thông qua callback click.
 */
package com.volunteer.manager.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.volunteer.manager.databinding.ItemParticipantBinding
import com.volunteer.manager.models.User

class ParticipantAdapter(
    private var participants: List<User>,
    private var isOrgOrAdmin: Boolean = false,
    private var confirmedMap: Map<String, Boolean> = HashMap(),
    private val onConfirmClick: (User) -> Unit = {}
) : RecyclerView.Adapter<ParticipantAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemParticipantBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.tvPartName.text = user.name
            binding.tvPartEmail.text = user.email

            val firstChar = if (user.name.isNotEmpty()) user.name.substring(0, 1).uppercase() else "U"
            binding.tvPartAvatar.text = firstChar

            if (isOrgOrAdmin) {
                val isConfirmed = confirmedMap[user.uid] == true
                if (isConfirmed) {
                    binding.btnConfirmParticipant.visibility = View.GONE
                    binding.tvConfirmedStatus.visibility = View.VISIBLE
                } else {
                    binding.btnConfirmParticipant.visibility = View.VISIBLE
                    binding.tvConfirmedStatus.visibility = View.GONE
                    binding.btnConfirmParticipant.setOnClickListener {
                        onConfirmClick(user)
                    }
                }
            } else {
                binding.btnConfirmParticipant.visibility = View.GONE
                binding.tvConfirmedStatus.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemParticipantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(participants[position])
    }

    override fun getItemCount() = participants.size

    fun updateData(newList: List<User>) {
        this.participants = newList
        notifyDataSetChanged()
    }

    fun updateValidationStates(isOrgOrAdmin: Boolean, confirmedMap: Map<String, Boolean>) {
        this.isOrgOrAdmin = isOrgOrAdmin
        this.confirmedMap = confirmedMap
        notifyDataSetChanged()
    }
}
