/**
 * Dự án: Volunteer Manager
 * File: CommentsDialogHelper.kt
 * Chức năng: Bộ tiện ích quản lý và đồng bộ hệ thống bình luận (Comments) và phản hồi con (Replies) thời gian thực.
 * Các chức năng chính:
 * - showComments(): Hiển thị bảng trượt BottomSheetDialog bình luận cấp mạng xã hội (Facebook/iMessage).
 * - showEditCommentDialog(): Mở hộp thoại nhanh cho phép người sở hữu chỉnh sửa nội dung bình luận / phản hồi của mình.
 * - showDeleteConfirmDialog(): Xác nhận và thực hiện xóa vĩnh viễn bình luận trên Firebase Database (chỉ hỗ trợ chủ sở hữu hoặc Admin).
 * - incrementCampaignCommentsCount() / decrementCampaignCommentsCount(): Sử dụng giao dịch Firebase Transaction an toàn tuyệt đối để đồng bộ số lượng `commentsCount` của chiến dịch tương ứng trên bảng tin dòng thời gian.
 */
package com.volunteer.manager.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.database.*
import com.volunteer.manager.R
import com.volunteer.manager.adapters.CommentAdapter
import com.volunteer.manager.databinding.DialogCommentsBinding
import com.volunteer.manager.models.Campaign
import com.volunteer.manager.models.Comment
import com.volunteer.manager.models.CommentReply
import com.volunteer.manager.models.User

object CommentsDialogHelper {

    fun showComments(context: Context, campaign: Campaign, currentUserId: String) {
        if (currentUserId.isEmpty()) {
            Toast.makeText(context, "Bạn cần đăng nhập để xem và bình luận!", Toast.LENGTH_SHORT).show()
            return
        }

        val campaignId = campaign.id ?: return

        // 1. Fetch user first to determine their role (isAdmin status)
        FirebaseDatabase.getInstance().getReference("users").child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(userSnapshot: DataSnapshot) {
                    val currentUserModel = userSnapshot.getValue(User::class.java)
                    val isAdmin = currentUserModel?.role == "Admin"
                    val currentUserName = currentUserModel?.name ?: "Người dùng"
                    val currentUserAvatar = currentUserModel?.avatarUrl ?: ""
                    val currentUserRole = currentUserModel?.role ?: "Student"

                    // Now proceed with showing the dialog and adapter setup
                    val dialog = BottomSheetDialog(context)
                    val binding = DialogCommentsBinding.inflate(LayoutInflater.from(context))
                    dialog.setContentView(binding.root)

                    var activeReplyCommentId: String? = null
                    var activeReplyUser: String = ""

                    // Setup RecyclerView
                    val commentsList = mutableListOf<Comment>()
                    
                    // Setup database reference
                    val commentsRef = FirebaseDatabase.getInstance().getReference("comments").child(campaignId)

                    // Options Dialog for parent comments
                    val onCommentLongClick = { comment: Comment ->
                        val options = arrayOf<CharSequence>("Chỉnh sửa bình luận", "Xóa bình luận")
                        val builder = AlertDialog.Builder(context)
                        builder.setTitle("Tùy chọn bình luận")
                        builder.setItems(options) { _, which ->
                            if (which == 0) {
                                showEditCommentDialog(context, commentsRef.child(comment.id!!), comment.text)
                            } else {
                                showDeleteConfirmDialog(context, commentsRef.child(comment.id!!))
                            }
                        }
                        builder.show()
                        Unit
                    }

                    // Options Dialog for nested replies
                    val onReplyLongClick = { comment: Comment, reply: CommentReply ->
                        val options = arrayOf<CharSequence>("Chỉnh sửa phản hồi", "Xóa phản hồi")
                        val builder = AlertDialog.Builder(context)
                        builder.setTitle("Tùy chọn phản hồi")
                        builder.setItems(options) { _, which ->
                            if (which == 0) {
                                showEditCommentDialog(
                                    context, 
                                    commentsRef.child(comment.id!!).child("replies").child(reply.id!!), 
                                    reply.text
                                )
                            } else {
                                showDeleteConfirmDialog(
                                    context, 
                                    commentsRef.child(comment.id!!).child("replies").child(reply.id!!)
                                )
                            }
                        }
                        builder.show()
                        Unit
                    }
                    
                    var activeReplyToUserName: String? = null

                    // Setup adapter with reply button click & long click options callbacks
                    val adapter = CommentAdapter(
                        commentsList,
                        currentUserId,
                        isAdmin,
                        onReplyClick = { comment ->
                            activeReplyCommentId = comment.id
                            activeReplyUser = comment.userName
                            activeReplyToUserName = null
                            binding.tvReplyHeaderTitle.text = "Đang trả lời ${comment.userName}..."
                            binding.layoutReplyHeader.visibility = View.VISIBLE
                            binding.etCommentInput.requestFocus()
                            
                            // Open keyboard
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showSoftInput(binding.etCommentInput, InputMethodManager.SHOW_IMPLICIT)
                        },
                        onNestedReplyClick = { comment, reply ->
                            activeReplyCommentId = comment.id
                            activeReplyUser = comment.userName
                            activeReplyToUserName = reply.userName
                            binding.tvReplyHeaderTitle.text = "Đang trả lời ${reply.userName}..."
                            binding.layoutReplyHeader.visibility = View.VISIBLE
                            binding.etCommentInput.requestFocus()
                            
                            // Open keyboard
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showSoftInput(binding.etCommentInput, InputMethodManager.SHOW_IMPLICIT)
                        },
                        onCommentLongClick = onCommentLongClick,
                        onReplyLongClick = onReplyLongClick
                    )
                    
                    binding.rvComments.layoutManager = LinearLayoutManager(context)
                    binding.rvComments.adapter = adapter

                    // Setup real-time database listener
                    val listener = object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            commentsList.clear()
                            for (child in snapshot.children) {
                                val comment = child.getValue(Comment::class.java)
                                comment?.let {
                                    it.id = child.key
                                    
                                    // Parse replies map safely
                                    val repliesNode = child.child("replies")
                                    val repliesMap = HashMap<String, CommentReply>()
                                    for (replyChild in repliesNode.children) {
                                        val reply = replyChild.getValue(CommentReply::class.java)
                                        reply?.let { r ->
                                            r.id = replyChild.key
                                            repliesMap[replyChild.key!!] = r
                                        }
                                    }
                                    it.replies = repliesMap
                                    
                                    commentsList.add(it)
                                }
                            }
                            
                            // Sort by oldest first (flowing down like a chat thread)
                            commentsList.sortBy { it.timestamp }
                            adapter.updateData(commentsList)

                            // Update count in header (Sum of normal comments + nested replies)
                            var totalCount = commentsList.size
                            for (c in commentsList) {
                                totalCount += c.replies.size
                            }
                            binding.tvCommentsTitle.text = "Bình luận ($totalCount)"

                            // Force self-healing count sync back to campaign node to keep cards synchronized
                            FirebaseDatabase.getInstance().getReference("campaigns").child(campaignId).child("commentsCount")
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(campSnapshot: DataSnapshot) {
                                        val currentCount = campSnapshot.getValue(Int::class.java) ?: 0
                                        if (currentCount != totalCount) {
                                            campSnapshot.ref.setValue(totalCount)
                                        }
                                    }
                                    override fun onCancelled(err: DatabaseError) {}
                                })

                            // Toggle empty state
                            if (commentsList.isEmpty()) {
                                binding.tvCommentsEmptyState.visibility = View.VISIBLE
                            } else {
                                binding.tvCommentsEmptyState.visibility = View.GONE
                                // Auto-scroll to latest comment on load
                                binding.rvComments.post {
                                    binding.rvComments.scrollToPosition(commentsList.size - 1)
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(context, "Lỗi tải bình luận: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    commentsRef.addValueEventListener(listener)

                    // Dismiss listener to prevent memory leaks
                    dialog.setOnDismissListener {
                        commentsRef.removeEventListener(listener)
                    }

                    // Cancel Active Reply State
                    binding.btnCancelReply.setOnClickListener {
                        activeReplyCommentId = null
                        activeReplyUser = ""
                        activeReplyToUserName = null
                        binding.layoutReplyHeader.visibility = View.GONE
                    }

                    // Close button click listener
                    binding.ivCloseComments.setOnClickListener {
                        dialog.dismiss()
                    }

                    // Send comment or reply action helper
                    fun sendComment() {
                        val text = binding.etCommentInput.text.toString().trim()
                        if (text.isNotEmpty()) {
                            if (activeReplyCommentId != null) {
                                // Write nested Reply
                                val replyRef = commentsRef.child(activeReplyCommentId!!).child("replies")
                                val replyId = replyRef.push().key ?: return
                                val newReply = CommentReply(
                                    id = replyId,
                                    userId = currentUserId,
                                    userName = currentUserName,
                                    userRole = currentUserRole,
                                    userAvatarUrl = currentUserAvatar,
                                    text = text,
                                    timestamp = System.currentTimeMillis(),
                                    parentUserName = activeReplyToUserName // Include the specific person being replied to
                                )

                                replyRef.child(replyId).setValue(newReply).addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        binding.etCommentInput.text.clear()
                                        activeReplyCommentId = null
                                        activeReplyUser = ""
                                        activeReplyToUserName = null
                                        binding.layoutReplyHeader.visibility = View.GONE
                                        incrementCampaignCommentsCount(campaignId)
                                    } else {
                                        Toast.makeText(context, "Lỗi gửi phản hồi: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                // Write normal parent Comment
                                val commentId = commentsRef.push().key ?: return
                                val newComment = Comment(
                                    id = commentId,
                                    userId = currentUserId,
                                    userName = currentUserName,
                                    userRole = currentUserRole,
                                    userAvatarUrl = currentUserAvatar,
                                    text = text,
                                    timestamp = System.currentTimeMillis()
                                )

                                commentsRef.child(commentId).setValue(newComment).addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        binding.etCommentInput.text.clear()
                                        incrementCampaignCommentsCount(campaignId)
                                    } else {
                                        Toast.makeText(context, "Lỗi gửi bình luận: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }

                    // Send button click listener
                    binding.btnSendComment.setOnClickListener {
                        sendComment()
                    }

                    // Handle keyboard IME Action Send
                    binding.etCommentInput.setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_SEND) {
                            sendComment()
                            true
                        } else {
                            false
                        }
                    }

                    dialog.show()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "Lỗi nạp dữ liệu người dùng!", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showEditCommentDialog(context: Context, databaseRef: DatabaseReference, currentText: String) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Chỉnh sửa bình luận")

        val input = EditText(context)
        input.setText(currentText)
        input.setSelection(currentText.length)
        input.setPadding(40, 30, 40, 30)
        
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        input.layoutParams = lp
        
        val container = LinearLayout(context)
        container.addView(input)
        container.setPadding(35, 10, 35, 10)
        builder.setView(container)

        builder.setPositiveButton("Lưu") { dialog, _ ->
            val newText = input.text.toString().trim()
            if (newText.isNotEmpty()) {
                databaseRef.child("text").setValue(newText).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(context, "Đã cập nhật bình luận!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Lỗi cập nhật: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            } else {
                Toast.makeText(context, "Nội dung không được để trống!", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Hủy") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun showDeleteConfirmDialog(context: Context, databaseRef: DatabaseReference) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Xóa bình luận")
        builder.setMessage("Bạn có chắc chắn muốn xóa bình luận này không?")
        builder.setPositiveButton("Xóa") { dialog, _ ->
            databaseRef.removeValue().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(context, "Đã xóa bình luận thành công!", Toast.LENGTH_SHORT).show()
                    // Extract campaignId from database path: /comments/{campaignId}/{commentId} or /comments/{campaignId}/{commentId}/replies/{replyId}
                    val pathSegments = databaseRef.path.toString().split("/")
                    if (pathSegments.size >= 3) {
                        val campaignId = pathSegments[2]
                        decrementCampaignCommentsCount(campaignId)
                    }
                } else {
                    Toast.makeText(context, "Lỗi xóa: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Hủy") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun incrementCampaignCommentsCount(campaignId: String) {
        FirebaseDatabase.getInstance().getReference("campaigns").child(campaignId).child("commentsCount")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val current = mutableData.getValue(Int::class.java) ?: 0
                    mutableData.value = current + 1
                    return Transaction.success(mutableData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
            })
    }

    private fun decrementCampaignCommentsCount(campaignId: String) {
        FirebaseDatabase.getInstance().getReference("campaigns").child(campaignId).child("commentsCount")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val current = mutableData.getValue(Int::class.java) ?: 0
                    mutableData.value = if (current > 0) current - 1 else 0
                    return Transaction.success(mutableData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
            })
    }
}
