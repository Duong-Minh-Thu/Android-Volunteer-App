/**
 * Dự án: Volunteer Manager
 * File: Comment.kt
 * Chức năng: Model lớp dữ liệu đại diện cho một bình luận cấp cha (Comment).
 * Chi tiết các trường:
 * - id: Mã định danh của bình luận cha.
 * - userId: Mã định danh của người bình luận.
 * - userName: Tên hiển thị của người bình luận.
 * - userRole: Vai trò của người bình luận (Ví dụ: Student, ORG, Admin).
 * - userAvatarUrl: Ảnh đại diện Base64 của người bình luận.
 * - text: Nội dung bình luận.
 * - timestamp: Mốc thời gian thực tế đăng bình luận (Long).
 * - replies: Danh sách các phản hồi cấp con (CommentReply) nằm bên dưới bình luận này.
 */
package com.volunteer.manager.models

import java.io.Serializable

data class Comment(
    var id: String? = null,
    var userId: String = "",
    var userName: String = "",
    var userRole: String = "",
    var userAvatarUrl: String = "",
    var text: String = "",
    var timestamp: Long = 0L,
    var replies: HashMap<String, CommentReply> = HashMap()
) : Serializable
