/**
 * Dự án: Volunteer Manager
 * File: CommentAdapter.kt
 * Chức năng: Bộ tiếp hợp hiển thị các Bình luận (Comments) và Phản hồi con (Replies) thời gian thực.
 * Các đặc tính chính:
 * - Render phân cấp bình luận cha và phản hồi con (thụt lề lùi vào trong).
 * - Hiển thị ảnh đại diện và vai trò của người bình luận một cách sinh động (Tình nguyện viên, Nhà tổ chức, Admin).
 * - Cung cấp tùy chọn chỉnh sửa/xóa bình luận khi nhấn giữ (Long click) đối với chủ sở hữu bình luận hoặc quản trị viên.
 * - Định dạng mốc thời gian đăng bình luận thân thiện (Ví dụ: "Vừa xong", "5 phút trước", "2 giờ trước").
 */
package com.volunteer.manager.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.volunteer.manager.databinding.ItemCommentBinding
import com.volunteer.manager.databinding.ItemCommentReplyBinding
import com.volunteer.manager.models.Comment
import com.volunteer.manager.models.CommentReply
import java.text.SimpleDateFormat
import java.util.*

class CommentAdapter(
    private var comments: List<Comment>,
    private val currentUserId: String,
    private val isAdmin: Boolean,
    private val onReplyClick: (Comment) -> Unit,
    private val onNestedReplyClick: (Comment, CommentReply) -> Unit,
    private val onCommentLongClick: (Comment) -> Unit,
    private val onReplyLongClick: (Comment, CommentReply) -> Unit
) : RecyclerView.Adapter<CommentAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comment: Comment) {
            val translatedRole = translateRole(comment.userRole)
            binding.tvCommentName.text = "${comment.userName} - $translatedRole"
            binding.tvCommentText.text = comment.text
            binding.tvCommentTime.text = formatTimestamp(comment.timestamp)

            // Render custom user avatar Base64 or initials
            if (!comment.userAvatarUrl.isNullOrEmpty()) {
                binding.tvCommentAvatarChar.visibility = View.GONE
                binding.ivCommentAvatar.visibility = View.VISIBLE
                com.volunteer.manager.utils.ImageLoader.loadImage(binding.root.context, comment.userAvatarUrl, binding.ivCommentAvatar)
            } else {
                binding.tvCommentAvatarChar.visibility = View.VISIBLE
                binding.ivCommentAvatar.visibility = View.GONE
                val firstChar = if (comment.userName.isNotEmpty()) comment.userName.substring(0, 1).uppercase() else "U"
                binding.tvCommentAvatarChar.text = firstChar
            }

            // Bind Reply Button
            binding.tvReplyBtn.setOnClickListener {
                onReplyClick(comment)
            }

            // Bind Long Click Options for parent comment
            binding.root.setOnLongClickListener {
                if (comment.userId == currentUserId || isAdmin) {
                    onCommentLongClick(comment)
                    true
                } else {
                    false
                }
            }

            // Render nested replies
            binding.layoutRepliesContainer.removeAllViews()
            if (comment.replies.isNotEmpty()) {
                binding.layoutRepliesContainer.visibility = View.VISIBLE
                
                // Sort replies by oldest first
                val sortedReplies = comment.replies.values.sortedBy { it.timestamp }
                
                for (reply in sortedReplies) {
                    val replyBinding = ItemCommentReplyBinding.inflate(
                        LayoutInflater.from(binding.root.context),
                        binding.layoutRepliesContainer,
                        false
                    )
                    
                    // Bind Reply Name with Format: <Tên> - <Role>
                    val translatedReplyRole = translateRole(reply.userRole)
                    replyBinding.tvReplyName.text = "${reply.userName} - $translatedReplyRole"
                    replyBinding.tvReplyText.text = reply.text
                    replyBinding.tvReplyTime.text = formatTimestamp(reply.timestamp)

                    // Show parent reference if this reply was directed to another reply specifically
                    if (!reply.parentUserName.isNullOrEmpty()) {
                        replyBinding.tvReplyToHeader.visibility = View.VISIBLE
                        replyBinding.tvReplyToHeader.text = "Đang phản hồi ${reply.parentUserName}"
                    } else {
                        replyBinding.tvReplyToHeader.visibility = View.GONE
                    }

                    // Setup click listener for Reply within a Reply
                    replyBinding.tvReplyActionBtn.setOnClickListener {
                        onNestedReplyClick(comment, reply)
                    }
                    
                    // Render avatar for reply
                    if (!reply.userAvatarUrl.isNullOrEmpty()) {
                        replyBinding.tvReplyAvatarChar.visibility = View.GONE
                        replyBinding.ivReplyAvatar.visibility = View.VISIBLE
                        com.volunteer.manager.utils.ImageLoader.loadImage(
                            replyBinding.root.context,
                            reply.userAvatarUrl,
                            replyBinding.ivReplyAvatar
                        )
                    } else {
                        replyBinding.tvReplyAvatarChar.visibility = View.VISIBLE
                        replyBinding.ivReplyAvatar.visibility = View.GONE
                        val firstChar = if (reply.userName.isNotEmpty()) reply.userName.substring(0, 1).uppercase() else "U"
                        replyBinding.tvReplyAvatarChar.text = firstChar
                    }

                    // Bind Long Click Options for reply
                    replyBinding.root.setOnLongClickListener {
                        if (reply.userId == currentUserId || isAdmin) {
                            onReplyLongClick(comment, reply)
                            true
                        } else {
                            false
                        }
                    }
                    
                    binding.layoutRepliesContainer.addView(replyBinding.root)
                }
            } else {
                binding.layoutRepliesContainer.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount() = comments.size

    fun updateData(newList: List<Comment>) {
        comments = newList
        notifyDataSetChanged()
    }

    private fun translateRole(role: String?): String {
        return when (role) {
            "ORG" -> "Nhà Tổ Chức (ORG)"
            "Admin" -> "Admin"
            else -> "Tình Nguyện Viên"
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        if (diff < 0) return "Vừa xong"
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            seconds < 60 -> "Vừa xong"
            minutes < 60 -> "$minutes phút trước"
            hours < 24 -> "$hours giờ trước"
            days < 7 -> "$days ngày trước"
            else -> {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}
