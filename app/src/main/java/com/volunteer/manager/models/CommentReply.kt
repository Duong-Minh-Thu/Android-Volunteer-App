/**
 * Dự án: Volunteer Manager
 * File: CommentReply.kt
 * Chức năng: Model lớp dữ liệu đại diện cho một bình luận phản hồi cấp con (CommentReply).
 * Chi tiết các trường:
 * - id: Mã định danh của phản hồi.
 * - userId: Mã định danh của người phản hồi.
 * - userName: Tên hiển thị của người phản hồi.
 * - userRole: Vai trò của người phản hồi (Ví dụ: Student, ORG, Admin).
 * - userAvatarUrl: Ảnh đại diện Base64 của người phản hồi.
 * - text: Nội dung phản hồi.
 * - timestamp: Mốc thời gian phản hồi (Long).
 * - parentUserName: Tên của người dùng cấp trên trực tiếp được trả lời (để hiển thị tag).
 */
package com.volunteer.manager.models

import java.io.Serializable

data class CommentReply(
    var id: String? = null,
    var userId: String = "",
    var userName: String = "",
    var userRole: String = "",
    var userAvatarUrl: String = "",
    var text: String = "",
    var timestamp: Long = 0L,
    var parentUserName: String? = null
) : Serializable
