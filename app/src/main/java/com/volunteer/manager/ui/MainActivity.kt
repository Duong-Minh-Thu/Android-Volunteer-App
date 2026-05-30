/**
 * ============================================================================
 * TÊN FILE: MainActivity.kt
 * MỤC ĐÍCH: Màn hình chính điều hướng trung tâm của ứng dụng Volunteer Manager.
 * CHỨC NĂNG CHÍNH:
 *   1. Quản lý Điều hướng Tab (Bottom Navigation):
 *      - Trang chủ (Home): Hiển thị tất cả các chiến dịch tình nguyện dạng Facebook feed.
 *      - Đăng ký/Quản lý: Tình nguyện viên xem các chiến dịch đã tham gia, ORG/Admin quản lý các chiến dịch của mình.
 *      - Yêu thích (Favorites): Hiển thị các chiến dịch người dùng đã ấn thích.
 *      - Bảng xếp hạng (Leaderboard): Bảng xếp hạng điểm rèn luyện (ĐRL) của Tình nguyện viên theo Tuần, Tháng, Năm, hoặc Tất cả.
 *      - Trang cá nhân (Profile): Quản lý thông tin cá nhân, cập nhật avatar Base64, xem lịch sử cộng Điểm rèn luyện.
 *   2. Phân quyền Người dùng (Role Handling):
 *      - Student (Tình nguyện viên): Tham gia chiến dịch, tích lũy ĐRL, xem bảng xếp hạng.
 *      - ORG (Nhà tổ chức) & Admin: Tạo chiến dịch mới (qua FAB), quản lý, phê duyệt hoàn thành để cộng điểm cho SV.
 *   3. Tiện ích và Tương tác:
 *      - Tải và nén ảnh đại diện Base64, lưu trữ động trên Firebase Database.
 *      - Đồng bộ lượt bình luận chạy ngầm tự động sửa lỗi lệch số đếm (self-healing).
 *      - Lọc và tìm kiếm chiến dịch thời gian thực theo tiêu đề và mô tả.
 *      - Chia sẻ chiến dịch dạng văn bản nhanh lên mạng xã hội khác.
 * ============================================================================
 */
package com.volunteer.manager.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.volunteer.manager.R
import com.volunteer.manager.adapters.CampaignAdapter
import com.volunteer.manager.adapters.CompletionRecordAdapter
import com.volunteer.manager.adapters.LeaderboardAdapter
import com.volunteer.manager.adapters.LeaderboardEntry
import com.volunteer.manager.databinding.ActivityMainBinding
import com.volunteer.manager.databinding.DialogCompletionsHistoryBinding
import com.volunteer.manager.models.Campaign
import com.volunteer.manager.models.User
import com.volunteer.manager.utils.CommentsDialogHelper
import com.volunteer.manager.utils.ImageLoader
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var volunteerAdapter: CampaignAdapter
    private lateinit var orgAdapter: CampaignAdapter
    private lateinit var profileCampaignAdapter: CampaignAdapter
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    
    // Danh sách lưu trữ chiến dịch
    private var allCampaigns = mutableListOf<Campaign>()
    private var displayedCampaigns = mutableListOf<Campaign>()
    private var profileCampaignsList = mutableListOf<Campaign>()
    
    private var userRole = "Student" // Vai trò mặc định: Tình nguyện viên
    private var currentFilterMode = "ALL" // Chế độ lọc hiện tại: ALL, JOINED, FAVORITE, MY_CREATED
    private var currentSearchQuery = "" // Từ khóa tìm kiếm
    private var currentUserId = "" // UID của người dùng đang đăng nhập
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    private var leaderboardList = mutableListOf<LeaderboardEntry>()
    private var allUsersList = mutableListOf<User>()
    private var currentLeaderboardPeriod = "WEEK" // Chu kỳ bảng xếp hạng mặc định: WEEK, MONTH, YEAR, ALL_TIME
    private var currentUserObject: User? = null

    // Trình chọn ảnh từ thư viện để làm ảnh đại diện
    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && currentUserId.isNotEmpty()) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    // Nén ảnh sang Base64
                    val base64 = ImageLoader.compressBitmapToBase64(bitmap)
                    
                    // Cập nhật avatarUrl của người dùng trên Firebase
                    FirebaseDatabase.getInstance().getReference("users")
                        .child(currentUserId).child("avatarUrl").setValue(base64)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(this, "Cập nhật ảnh đại diện thành công!", Toast.LENGTH_SHORT).show()
                                loadUserProfile()
                            } else {
                                Toast.makeText(this, "Lỗi cập nhật: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "Không thể đọc định dạng ảnh!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi đọc ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""
        database = FirebaseDatabase.getInstance().getReference("campaigns")

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Volunteer Hub"

        // Cấu hình thanh điều hướng bên dưới (Bottom Navigation)
        setupBottomNavigation()

        // Khởi tạo các RecyclerView và Adapter tương ứng
        setupRecyclerViews()

        // Lấy thông tin người dùng và phân quyền vai trò
        checkUserRole()

        // Thiết lập bộ lắng nghe thanh tìm kiếm tìm kiếm chiến dịch theo thời gian thực
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString().trim()
                filterAndDisplayCampaigns()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Lắng nghe sự kiện kéo để làm mới (Swipe Refresh)
        binding.swipeRefresh.setOnRefreshListener {
            loadCampaigns()
        }

        // Sự kiện click FAB để thêm chiến dịch mới (dành cho ORG / Admin)
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddCampaignActivity::class.java))
        }

        // Sự kiện nhấp ảnh đại diện ở Trang cá nhân để cập nhật avatar mới
        binding.cardProfileAvatar.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }

        // Sự kiện click nút Đăng xuất ở Trang cá nhân
        binding.btnProfileLogout.setOnClickListener {
            showLogoutConfirmation()
        }

        // Sự kiện nhấp xem điểm rèn luyện ở Trang cá nhân để mở lịch sử hoàn thành chiến dịch
        binding.layoutProfileDetailPoints.setOnClickListener {
            showCompletionsHistoryDialog()
        }

        // Chạy quy trình đồng bộ hóa chạy ngầm kiểm tra số lượng bình luận để tự động sửa lỗi lệch số đếm
        syncCommentCountsBackground()
    }

    override fun onResume() {
        super.onResume()
        if (auth.currentUser != null) {
            checkUserRole()
        }
    }

    // Thiết lập Bottom Navigation và định tuyến chế độ xem tương ứng khi chuyển Tab
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    currentFilterMode = "ALL"
                    showHomeView()
                }
                R.id.nav_joined -> {
                    currentFilterMode = if (userRole == "ORG" || userRole == "Admin") "MY_CREATED" else "JOINED"
                    showHomeView()
                }
                R.id.nav_favorites -> {
                    currentFilterMode = "FAVORITE"
                    showHomeView()
                }
                R.id.nav_profile -> {
                    showProfileView()
                }
                R.id.nav_leaderboard -> {
                    showLeaderboardView()
                }
            }
            true
        }
    }

    // Hiển thị Tab Giao diện trang chủ (Feed chiến dịch & Dashboard ORG)
    private fun showHomeView() {
        binding.layoutProfile.visibility = View.GONE
        binding.layoutLeaderboard.visibility = View.GONE
        
        if (currentFilterMode == "MY_CREATED" && (userRole == "ORG" || userRole == "Admin")) {
            binding.layoutVolunteer.visibility = View.GONE
            binding.layoutDashboard.visibility = View.VISIBLE
            binding.fabAdd.visibility = View.VISIBLE
            supportActionBar?.title = "Quản lý Chiến dịch"
        } else {
            binding.layoutVolunteer.visibility = View.VISIBLE
            binding.layoutDashboard.visibility = View.GONE
            binding.fabAdd.visibility = if (userRole == "ORG" || userRole == "Admin") View.VISIBLE else View.GONE
            supportActionBar?.title = when (currentFilterMode) {
                "JOINED" -> "Sự kiện đã tham gia"
                "FAVORITE" -> "Đã yêu thích"
                else -> "Volunteer Hub"
            }
        }
        filterAndDisplayCampaigns()
    }

    // Hiển thị Tab Giao diện trang cá nhân
    private fun showProfileView() {
        binding.layoutVolunteer.visibility = View.GONE
        binding.layoutDashboard.visibility = View.GONE
        binding.fabAdd.visibility = View.GONE
        binding.layoutLeaderboard.visibility = View.GONE
        binding.layoutProfile.visibility = View.VISIBLE
        supportActionBar?.title = "Trang cá nhân"
        loadUserProfile()
        loadUserCampaigns()
    }

    // Hiển thị Tab Giao diện Bảng xếp hạng
    private fun showLeaderboardView() {
        binding.layoutVolunteer.visibility = View.GONE
        binding.layoutDashboard.visibility = View.GONE
        binding.fabAdd.visibility = View.GONE
        binding.layoutProfile.visibility = View.GONE
        binding.layoutLeaderboard.visibility = View.VISIBLE
        supportActionBar?.title = "Bảng xếp hạng"
        setupLeaderboardPills()
        loadLeaderboard()
    }

    // Khởi tạo các RecyclerView và Adapter liên quan
    private fun setupRecyclerViews() {
        // Sự kiện khi click xem chi tiết chiến dịch
        val onCampaignClick = { campaign: Campaign ->
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra("campaign", campaign)
            startActivity(intent)
        }

        // Sự kiện click thích chiến dịch
        val onLikeToggle = { campaign: Campaign ->
            toggleLike(campaign)
        }

        // Sự kiện click tham gia/hủy tham gia chiến dịch
        val onJoinToggle = { campaign: Campaign ->
            toggleJoin(campaign)
        }

        // Sự kiện click chia sẻ chiến dịch
        val onShare = { campaign: Campaign ->
            shareCampaign(campaign)
        }

        // Sự kiện click xem danh sách bình luận (BottomSheet)
        val onCommentClick = { campaign: Campaign ->
            CommentsDialogHelper.showComments(this, campaign, currentUserId)
        }

        // Adapter cho Volunteer Feed
        volunteerAdapter = CampaignAdapter(displayedCampaigns, onCampaignClick, onLikeToggle, onJoinToggle, onShare, onCommentClick)
        binding.rvCampaigns.layoutManager = LinearLayoutManager(this)
        binding.rvCampaigns.adapter = volunteerAdapter

        // Adapter cho ORG Dashboard
        orgAdapter = CampaignAdapter(displayedCampaigns, onCampaignClick, onLikeToggle, onJoinToggle, onShare, onCommentClick)
        binding.rvOrgCampaigns.layoutManager = LinearLayoutManager(this)
        binding.rvOrgCampaigns.adapter = orgAdapter

        // Adapter cho Danh sách ở Trang cá nhân
        profileCampaignAdapter = CampaignAdapter(profileCampaignsList, onCampaignClick, onLikeToggle, onJoinToggle, onShare, onCommentClick)
        binding.rvProfileCampaigns.layoutManager = LinearLayoutManager(this)
        binding.rvProfileCampaigns.adapter = profileCampaignAdapter

        // Adapter cho Bảng xếp hạng
        leaderboardAdapter = LeaderboardAdapter(leaderboardList)
        binding.rvLeaderboard.layoutManager = LinearLayoutManager(this)
        binding.rvLeaderboard.adapter = leaderboardAdapter
    }

    // Kiểm tra quyền vai trò của người dùng ngay khi đăng nhập thành công để định dạng giao diện tương thích
    private fun checkUserRole() {
        if (currentUserId.isEmpty()) return
        
        binding.pbMainLoading.visibility = View.VISIBLE

        FirebaseDatabase.getInstance().getReference("users").child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)
                    binding.pbMainLoading.visibility = View.GONE
                    
                    if (user != null) {
                        userRole = user.role
                        
                        // Cấu hình nhãn Bottom Navigation tùy biến theo vai trò
                        if (userRole == "ORG" || userRole == "Admin") {
                            binding.bottomNavigation.menu.findItem(R.id.nav_joined).title = "Quản lý"
                            binding.tvWelcomeOrg.text = "Chào mừng, ${user.name}!"
                        } else {
                            binding.bottomNavigation.menu.findItem(R.id.nav_joined).title = "Đăng ký"
                        }
                        
                        // Khởi tạo tab xem tương ứng
                        when (binding.bottomNavigation.selectedItemId) {
                            R.id.nav_profile -> showProfileView()
                            else -> showHomeView()
                        }
                        
                        loadCampaigns()
                    } else {
                        // Nếu tài khoản hợp lệ nhưng chưa tồn tại thông tin trong DB, khởi tạo thông tin mặc định
                        val email = auth.currentUser?.email ?: "volunteer@example.com"
                        val defaultUser = User(currentUserId, "Tình Nguyện Viên", email, "Student")
                        FirebaseDatabase.getInstance().getReference("users").child(currentUserId).setValue(defaultUser)
                        userRole = "Student"
                        
                        showHomeView()
                        loadCampaigns()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    binding.pbMainLoading.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Database error: ${error.message}", Toast.LENGTH_LONG).show()
                    loadCampaigns()
                }
            })
    }

    // Tải danh sách tất cả các chiến dịch từ Firebase Realtime Database
    private fun loadCampaigns() {
        binding.swipeRefresh.isRefreshing = true
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allCampaigns.clear()
                for (child in snapshot.children) {
                    val campaign = child.getValue(Campaign::class.java)
                    campaign?.let {
                        it.id = child.key
                        allCampaigns.add(it)
                    }
                }
                
                // Tính toán số liệu thống kê thời gian thực nếu là Nhà tổ chức (ORG)
                if (userRole == "ORG" || userRole == "Admin") {
                    calculateDashboardStats()
                }

                // Nếu đang mở trang cá nhân, tải lại danh sách sự kiện cá nhân
                if (binding.layoutProfile.visibility == View.VISIBLE) {
                    loadUserCampaigns()
                }

                filterAndDisplayCampaigns()
                binding.swipeRefresh.isRefreshing = false
            }

            override fun onCancelled(error: DatabaseError) {
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this@MainActivity, "Lỗi tải dữ liệu: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Tính toán số liệu thống kê thời gian thực cho ORG Dashboard (Tổng chiến dịch, Tổng TNV đăng ký)
    private fun calculateDashboardStats() {
        var myCampaignsCount = 0
        var totalVolunteersCount = 0

        for (campaign in allCampaigns) {
            if (campaign.orgId == currentUserId) {
                myCampaignsCount++
                totalVolunteersCount += campaign.participants.size
            }
        }

        binding.tvDashboardCampaignsCount.text = myCampaignsCount.toString()
        binding.tvDashboardVolunteersCount.text = totalVolunteersCount.toString()
    }

    // Lọc và hiển thị danh sách các chiến dịch dựa theo Tab lựa chọn và Từ khóa tìm kiếm
    private fun filterAndDisplayCampaigns() {
        displayedCampaigns.clear()

        // Lọc lớp 1: Theo Tab điều hướng hiện tại
        val baseFiltered = when (currentFilterMode) {
            "JOINED" -> allCampaigns.filter { it.participants.containsKey(currentUserId) }
            "FAVORITE" -> allCampaigns.filter { it.favoriteBy.containsKey(currentUserId) }
            "MY_CREATED" -> allCampaigns.filter { it.orgId == currentUserId }
            else -> allCampaigns
        }

        // Lọc lớp 2: Theo Từ khóa tìm kiếm (so khớp Tiêu đề hoặc Mô tả)
        val finalFiltered = if (currentSearchQuery.isNotEmpty()) {
            baseFiltered.filter { 
                it.title.contains(currentSearchQuery, ignoreCase = true) ||
                it.description.contains(currentSearchQuery, ignoreCase = true)
            }
        } else {
            baseFiltered
        }

        displayedCampaigns.addAll(finalFiltered)
        
        // Cập nhật lên adapter tương ứng
        if (binding.layoutDashboard.visibility == View.VISIBLE) {
            orgAdapter.updateData(displayedCampaigns)
            binding.rvOrgCampaigns.post {
                binding.rvOrgCampaigns.requestLayout()
            }
        } else {
            volunteerAdapter.updateData(displayedCampaigns)
            binding.rvCampaigns.post {
                binding.rvCampaigns.requestLayout()
            }
        }
    }

    // Tải thông tin chi tiết trang cá nhân của người dùng đang đăng nhập
    private fun loadUserProfile() {
        if (currentUserId.isEmpty()) return
        
        FirebaseDatabase.getInstance().getReference("users").child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        currentUserObject = user
                        binding.tvProfileName.text = user.name
                        binding.tvProfileEmail.text = user.email
                        binding.tvProfileRoleBadge.text = if (user.role == "ORG") "Nhà Tổ Chức (ORG)" else "Tình Nguyện Viên"
                        
                        // Đọc và tải Avatar Base64
                        if (!user.avatarUrl.isNullOrEmpty()) {
                            binding.tvProfileAvatarChar.visibility = View.GONE
                            binding.ivProfileAvatar.visibility = View.VISIBLE
                            ImageLoader.loadImage(this@MainActivity, user.avatarUrl, binding.ivProfileAvatar)
                        } else {
                            binding.tvProfileAvatarChar.visibility = View.VISIBLE
                            binding.ivProfileAvatar.visibility = View.GONE
                            val firstChar = if (user.name.isNotEmpty()) user.name.substring(0, 1).uppercase() else "U"
                            binding.tvProfileAvatarChar.text = firstChar
                        }

                        // Thông tin chi tiết trong Card
                        binding.tvProfileDetailName.text = user.name
                        binding.tvProfileDetailEmail.text = user.email
                        binding.tvProfileDetailRole.text = if (user.role == "ORG") "Nhà Tổ Chức (ORG)" else "Học sinh / Tình nguyện viên"

                        // Trình bày điểm rèn luyện nếu vai trò là Học sinh/Tình nguyện viên
                        if (user.role == "Student") {
                            binding.layoutProfileDetailPoints.visibility = View.VISIBLE
                            binding.viewProfileDetailPointsDivider.visibility = View.VISIBLE
                            binding.tvProfileDetailPoints.text = "${user.trainingPoints} ĐRL"
                        } else {
                            binding.layoutProfileDetailPoints.visibility = View.GONE
                            binding.viewProfileDetailPointsDivider.visibility = View.GONE
                        }

                        // Điều chỉnh tiêu đề phần chiến dịch tương thích với vai trò
                        if (user.role == "ORG" || user.role == "Admin") {
                            binding.tvProfileCampaignsSectionTitle.text = "Chiến dịch đã tạo của bạn"
                        } else {
                            binding.tvProfileCampaignsSectionTitle.text = "Chiến dịch bạn đã đăng ký tham gia"
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // Tải danh sách chiến dịch liên quan đến trang cá nhân của người dùng
    private fun loadUserCampaigns() {
        profileCampaignsList.clear()
        
        val filtered = if (userRole == "ORG" || userRole == "Admin") {
            allCampaigns.filter { it.orgId == currentUserId }
        } else {
            allCampaigns.filter { it.participants.containsKey(currentUserId) }
        }
        
        profileCampaignsList.addAll(filtered)
        profileCampaignAdapter.updateData(profileCampaignsList)
    }

    // Đảo ngược trạng thái thích chiến dịch (Thích / Bỏ thích) trên Realtime Database
    private fun toggleLike(campaign: Campaign) {
        val campaignId = campaign.id ?: return
        val ref = FirebaseDatabase.getInstance().getReference("campaigns").child(campaignId).child("favoriteBy").child(currentUserId)
        
        if (campaign.favoriteBy.containsKey(currentUserId)) {
            ref.removeValue().addOnFailureListener {
                Toast.makeText(this, "Không thể bỏ thích: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            ref.setValue(true).addOnFailureListener {
                Toast.makeText(this, "Không thể thích: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Đảo ngược trạng thái đăng ký tham gia chiến dịch
    private fun toggleJoin(campaign: Campaign) {
        val campaignId = campaign.id ?: return
        val ref = FirebaseDatabase.getInstance().getReference("campaigns").child(campaignId).child("participants").child(currentUserId)
        
        if (campaign.participants.containsKey(currentUserId)) {
            ref.removeValue().addOnCompleteListener {
                if (it.isSuccessful) {
                    Toast.makeText(this, "Đã hủy tham gia chiến dịch", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            ref.setValue(true).addOnCompleteListener {
                if (it.isSuccessful) {
                    Toast.makeText(this, "Tham gia chiến dịch thành công!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Chia sẻ chiến dịch dạng text nhanh thông qua Intent chooser
    private fun shareCampaign(campaign: Campaign) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, campaign.title)
            putExtra(Intent.EXTRA_TEXT, "Tham gia cùng tôi trong chiến dịch ý nghĩa này:\n\n${campaign.title}\nThời gian: ${campaign.time}\nMô tả: ${campaign.description}\n\nTải ngay ứng dụng Volunteer Manager để đăng ký tham gia nhé!")
        }
        startActivity(Intent.createChooser(intent, "Chia sẻ chiến dịch"))
    }

    // Hiển thị hộp thoại xác nhận đăng xuất tài khoản khỏi thiết bị
    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Đăng xuất")
            .setMessage("Bạn có chắc chắn muốn đăng xuất tài khoản này khỏi thiết bị?")
            .setPositiveButton("Đăng xuất") { _, _ ->
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Hủy bỏ", null)
            .show()
    }

    // Đồng bộ số đếm bình luận chạy ngầm tự động sửa lỗi lệch (Self-healing scanner)
    private fun syncCommentCountsBackground() {
        FirebaseDatabase.getInstance().getReference("comments")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(commentsSnapshot: DataSnapshot) {
                    val updates = HashMap<String, Any>()
                    for (campaignChild in commentsSnapshot.children) {
                        val campaignId = campaignChild.key ?: continue
                        var count = campaignChild.childrenCount.toInt()
                        for (commentChild in campaignChild.children) {
                            count += commentChild.child("replies").childrenCount.toInt()
                        }
                        
                        val matchingCampaign = allCampaigns.find { it.id == campaignId }
                        if (matchingCampaign == null || matchingCampaign.commentsCount != count) {
                            updates["$campaignId/commentsCount"] = count
                        }
                    }
                    if (updates.isNotEmpty()) {
                        FirebaseDatabase.getInstance().getReference("campaigns").updateChildren(updates)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // Hỗ trợ xử lý nút Back: Nếu không ở màn hình chính, đưa người dùng quay về Tab chính trước khi thoát app
    override fun onBackPressed() {
        if (binding.bottomNavigation.selectedItemId != R.id.nav_home) {
            binding.bottomNavigation.selectedItemId = R.id.nav_home
        } else {
            super.onBackPressed()
        }
    }

    // Quản lý trạng thái và giao diện các nút lọc thời gian trên Bảng xếp hạng (Weekly, Monthly, Yearly, All Time)
    private fun setupLeaderboardPills() {
        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_pill_active)
        val inactiveBg = ContextCompat.getDrawable(this, R.drawable.bg_pill_inactive)
        val whiteColor = Color.WHITE
        val slateColor = Color.parseColor("#334155")

        val resetPills = {
            binding.tvFilterWeekly.background = inactiveBg
            binding.tvFilterWeekly.setTextColor(slateColor)
            binding.tvFilterMonthly.background = inactiveBg
            binding.tvFilterMonthly.setTextColor(slateColor)
            binding.tvFilterYearly.background = inactiveBg
            binding.tvFilterYearly.setTextColor(slateColor)
            binding.tvFilterAllTime.background = inactiveBg
            binding.tvFilterAllTime.setTextColor(slateColor)
        }

        binding.tvFilterWeekly.setOnClickListener {
            resetPills()
            binding.tvFilterWeekly.background = activeBg
            binding.tvFilterWeekly.setTextColor(whiteColor)
            currentLeaderboardPeriod = "WEEK"
            processAndDisplayLeaderboard()
        }

        binding.tvFilterMonthly.setOnClickListener {
            resetPills()
            binding.tvFilterMonthly.background = activeBg
            binding.tvFilterMonthly.setTextColor(whiteColor)
            currentLeaderboardPeriod = "MONTH"
            processAndDisplayLeaderboard()
        }

        binding.tvFilterYearly.setOnClickListener {
            resetPills()
            binding.tvFilterYearly.background = activeBg
            binding.tvFilterYearly.setTextColor(whiteColor)
            currentLeaderboardPeriod = "YEAR"
            processAndDisplayLeaderboard()
        }

        binding.tvFilterAllTime.setOnClickListener {
            resetPills()
            binding.tvFilterAllTime.background = activeBg
            binding.tvFilterAllTime.setTextColor(whiteColor)
            currentLeaderboardPeriod = "ALL_TIME"
            processAndDisplayLeaderboard()
        }
    }

    // Tải thông tin người dùng từ DB để tính toán Bảng xếp hạng
    private fun loadLeaderboard() {
        FirebaseDatabase.getInstance().getReference("users")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    allUsersList.clear()
                    for (child in snapshot.children) {
                        val user = child.getValue(User::class.java)
                        if (user != null) {
                            user.uid = child.key ?: ""
                            allUsersList.add(user)
                        }
                    }
                    processAndDisplayLeaderboard()
                }
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@MainActivity, "Lỗi tải bảng xếp hạng: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // Quy trình xử lý và tính toán điểm cho bảng xếp hạng tương ứng theo thời gian đã chọn
    private fun processAndDisplayLeaderboard() {
        val startOfWeek = getStartOfWeekTimestamp()
        val startOfMonth = getStartOfMonthTimestamp()
        val startOfYear = getStartOfYearTimestamp()

        val entries = mutableListOf<LeaderboardEntry>()

        for (user in allUsersList) {
            // Lọc: Chỉ hiển thị các tài khoản là Học sinh / Tình nguyện viên
            if (user.role != "Student") continue

            val score = when (currentLeaderboardPeriod) {
                "ALL_TIME" -> user.trainingPoints
                "WEEK" -> {
                    var sum = 0
                    for (comp in user.completions.values) {
                        if (comp.timestamp >= startOfWeek) {
                            sum += comp.points
                        }
                    }
                    sum
                }
                "MONTH" -> {
                    var sum = 0
                    for (comp in user.completions.values) {
                        if (comp.timestamp >= startOfMonth) {
                            sum += comp.points
                        }
                    }
                    sum
                }
                "YEAR" -> {
                    var sum = 0
                    for (comp in user.completions.values) {
                        if (comp.timestamp >= startOfYear) {
                            sum += comp.points
                        }
                    }
                    sum
                }
                else -> user.trainingPoints
            }

            entries.add(LeaderboardEntry(user, score))
        }

        // Sắp xếp điểm giảm dần
        entries.sortByDescending { it.score }

        leaderboardList.clear()
        leaderboardList.addAll(entries)
        leaderboardAdapter.updateData(leaderboardList)

        if (leaderboardList.isEmpty()) {
            binding.tvLeaderboardEmptyState.visibility = View.VISIBLE
        } else {
            binding.tvLeaderboardEmptyState.visibility = View.GONE
        }
    }

    // Hàm lấy mốc thời gian bắt đầu của tuần hiện tại (Timestamp)
    private fun getStartOfWeekTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // Hàm lấy mốc thời gian bắt đầu của tháng hiện tại (Timestamp)
    private fun getStartOfMonthTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // Hàm lấy mốc thời gian bắt đầu của năm hiện tại (Timestamp)
    private fun getStartOfYearTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // Hiển thị BottomSheet chứa danh sách chi tiết các sự kiện người dùng đã hoàn thành (Lịch sử điểm rèn luyện)
    private fun showCompletionsHistoryDialog() {
        val user = currentUserObject ?: return
        if (user.role != "Student") return

        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogCompletionsHistoryBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Tổng điểm rèn luyện
        dialogBinding.tvCompletionsTotalPoints.text = "${user.trainingPoints} ĐRL"

        // Nút tắt BottomSheet
        dialogBinding.ivCloseCompletions.setOnClickListener {
            dialog.dismiss()
        }

        // Sắp xếp lịch sử theo thứ tự thời gian gần nhất
        val completionsList = user.completions.values.sortedByDescending { it.timestamp }

        if (completionsList.isEmpty()) {
            dialogBinding.tvCompletionsEmptyState.visibility = View.VISIBLE
            dialogBinding.rvCompletionsHistory.visibility = View.GONE
        } else {
            dialogBinding.tvCompletionsEmptyState.visibility = View.GONE
            dialogBinding.rvCompletionsHistory.visibility = View.VISIBLE

            val completionsAdapter = CompletionRecordAdapter(completionsList)
            dialogBinding.rvCompletionsHistory.layoutManager = LinearLayoutManager(this)
            dialogBinding.rvCompletionsHistory.adapter = completionsAdapter
        }

        dialog.show()
    }

    // Hỗ trợ UX tự động ẩn bàn phím khi bấm ra ngoài thanh tìm kiếm
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
