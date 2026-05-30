/**
 * Dự án: Volunteer Manager
 * File: CompletionRecordAdapter.kt
 * Chức năng: Bộ tiếp hợp hiển thị Nhật ký nhận Điểm rèn luyện của Tình nguyện viên (RecyclerView Adapter).
 * Các đặc tính chính:
 * - Hiển thị tên chiến dịch đã hoàn thành, mốc thời gian hoàn thành cụ thể.
 * - Hiển thị số lượng Điểm rèn luyện nhận được dạng màu xanh lá đặc trưng (+10 ĐRL, +20 ĐRL,...).
 */
package com.volunteer.manager.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.volunteer.manager.databinding.ItemCompletionRecordBinding
import com.volunteer.manager.models.CompletionRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CompletionRecordAdapter(
    private var records: List<CompletionRecord>
) : RecyclerView.Adapter<CompletionRecordAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemCompletionRecordBinding) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        fun bind(record: CompletionRecord) {
            binding.tvRecordTitle.text = record.campaignTitle
            val formattedDate = dateFormat.format(Date(record.timestamp))
            binding.tvRecordDate.text = "Đã hoàn thành: $formattedDate"
            binding.tvRecordPoints.text = "+${record.points} ĐRL"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCompletionRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount() = records.size

    fun updateData(newList: List<CompletionRecord>) {
        records = newList
        notifyDataSetChanged()
    }
}
