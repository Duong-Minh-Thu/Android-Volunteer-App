/**
 * Dự án: Volunteer Manager
 * File: Campaign.kt
 * Chức năng: Model lớp dữ liệu đại diện cho một Chiến dịch tình nguyện (Campaign).
 * Chi tiết các trường:
 * - id / campaignId: Mã định danh duy nhất của chiến dịch.
 * - creatorId / orgId: Mã định danh của nhà tổ chức hoặc người tạo chiến dịch.
 * - date: Ngày tạo / đăng bài chiến dịch.
 * - location: Địa chỉ tổ chức chiến dịch.
 * - status: Trạng thái chiến dịch (Ví dụ: ACTIVE, COMPLETED).
 * - title: Tiêu đề chiến dịch.
 * - time: Thời gian tổ chức chiến dịch.
 * - description: Nội dung mô tả chi tiết chiến dịch.
 * - imageUrl: Ảnh minh họa chiến dịch dưới dạng chuỗi nén Base64 hoặc liên kết mạng.
 * - latitude / longitude: Tọa độ bản đồ (vĩ độ / kinh độ) của địa điểm tổ chức.
 * - participants: Danh sách những tình nguyện viên đã đăng ký tham gia (userId -> True).
 * - confirmedParticipants: Danh sách những tình nguyện viên đã được xác nhận hoàn thành (userId -> True).
 * - favoriteBy: Danh sách những người dùng đã thích/yêu thích chiến dịch này (userId -> True).
 * - orgName: Tên thực tế của tổ chức/người tạo chiến dịch để hiển thị trên bảng tin.
 * - commentsCount: Tổng số bình luận và phản hồi con của chiến dịch này.
 * - trainingPoints: Số điểm rèn luyện nhận được khi hoàn thành (mặc định là 10 ĐRL).
 */
package com.volunteer.manager.models

import java.io.Serializable

data class Campaign(
    var id: String? = null,
    var campaignId: String = "",
    var creatorId: String = "",
    var date: String = "",
    var location: String = "",
    var status: String = "",
    var title: String = "",
    var time: String = "",
    var description: String = "",
    var imageUrl: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var participants: HashMap<String, Boolean> = HashMap(),
    var confirmedParticipants: HashMap<String, Boolean> = HashMap(),
    var favoriteBy: HashMap<String, Boolean> = HashMap(),
    var orgId: String = "",
    var orgName: String = "",
    var commentsCount: Int = 0,
    var trainingPoints: Int = 10
) : Serializable
