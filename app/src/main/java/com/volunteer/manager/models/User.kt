/**
 * Dự án: Volunteer Manager
 * File: User.kt
 * Chức năng: Model lớp dữ liệu đại diện cho Người dùng (User) và Nhật ký hoàn thành (CompletionRecord).
 * Chi tiết các trường lớp User:
 * - uid: Mã định danh duy nhất của người dùng trên Firebase Auth.
 * - name: Tên đầy đủ hiển thị.
 * - email: Địa chỉ email của người dùng.
 * - role: Vai trò (Student đại diện cho Tình nguyện viên, ORG cho Nhà tổ chức, Admin cho Quản trị viên).
 * - avatarUrl: Chuỗi nén Base64 của ảnh đại diện người dùng.
 * - trainingPoints: Tổng điểm rèn luyện (ĐRL) tích lũy thời gian thực.
 * - completions: Danh sách lịch sử các sự kiện đã được xác nhận hoàn thành (campaignId -> CompletionRecord).
 *
 * Chi tiết các trường lớp CompletionRecord:
 * - campaignId: Mã chiến dịch đã tham gia thành công.
 * - campaignTitle: Tiêu đề chiến dịch đã tham gia.
 * - timestamp: Mốc thời gian được duyệt hoàn thành (Long).
 * - points: Số điểm rèn luyện nhận được từ chiến dịch này.
 */
package com.volunteer.manager.models

import java.io.Serializable

data class User(
    var uid: String = "",
    var name: String = "",
    var email: String = "",
    var role: String = "Student",
    var avatarUrl: String = "",
    var trainingPoints: Int = 0,
    var completions: HashMap<String, CompletionRecord> = HashMap()
) : Serializable

data class CompletionRecord(
    var campaignId: String = "",
    var campaignTitle: String = "",
    var timestamp: Long = 0L,
    var points: Int = 10
) : Serializable
