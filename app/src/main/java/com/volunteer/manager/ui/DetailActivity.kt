/**
 * ============================================================================
 * TÊN FILE: DetailActivity.kt
 * MỤC ĐÍCH: Màn hình chi tiết của chiến dịch tình nguyện trong ứng dụng Volunteer Manager.
 * CHỨC NĂNG CHÍNH:
 *   1. Hiển thị thông tin chiến dịch: Tiêu đề, Thời gian, Mô tả chi tiết rút gọn (đặt nổi bật trong thẻ thông tin), Ảnh banner lớn.
 *   2. Tích hợp Bản đồ Google Maps:
 *      - Hiển thị vị trí tọa độ GPS của sự kiện với biểu tượng Marker.
 *      - Xử lý bộ chặn chạm thông minh (requestDisallowInterceptTouchEvent) giúp vuốt/zoom bản đồ mượt mà không bị xung đột cuộn với NestedScrollView.
 *   3. Tích hợp API Thời tiết (Retrofit): Truy vấn thời gian thực nhiệt độ và mô tả khí hậu tại tọa độ sự kiện (tự động fallback nếu lỗi).
 *   4. Tính toán khoảng cách (FusedLocationProvider): Đo khoảng cách thực tế giữa vị trí GPS của người dùng tới vị trí chiến dịch.
 *   5. Hệ thống 3 nút tương tác cho Tình nguyện viên:
 *      - Thích: Tăng/giảm và hiển thị lượt thích trực tiếp.
 *      - Tham gia: Đăng ký/Hủy tham gia hoạt động.
 *      - Bình luận: Mở BottomSheet bình luận và phản hồi phân tầng thời gian thực.
 *   6. Góc phải trên ảnh Banner: Nút Chia sẻ (Share) Glassmorphic cao cấp.
 *   7. Dành cho ORG / Admin:
 *      - Nút Chỉnh sửa (Edit) để sửa thông tin chiến dịch.
 *      - Xem danh sách thành viên đăng ký, xác nhận hoàn thành hoạt động để cộng Điểm rèn luyện động cho SV thông qua transaction.
 * ============================================================================
 */
package com.volunteer.manager.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.volunteer.manager.R
import com.volunteer.manager.adapters.ParticipantAdapter
import com.volunteer.manager.api.WeatherResponse
import com.volunteer.manager.api.WeatherService
import com.volunteer.manager.databinding.ActivityDetailBinding
import com.volunteer.manager.databinding.DialogParticipantsBinding
import com.volunteer.manager.models.Campaign
import com.volunteer.manager.models.CompletionRecord
import com.volunteer.manager.models.User
import com.volunteer.manager.utils.CommentsDialogHelper
import com.volunteer.manager.utils.ImageLoader
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale

class DetailActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityDetailBinding
    private lateinit var campaign: Campaign
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private lateinit var participantAdapter: ParticipantAdapter
    private var participantList = mutableListOf<User>()
    private var isOrgOrAdmin: Boolean = false
    
    private val WEATHER_API_KEY = "ee462d8db8119a5b1bc859d6b7560033"
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        campaign = intent.getSerializableExtra("campaign") as Campaign
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupToolbar()
        setupUI()
        setupParticipantRecyclerView()

        // Theo dõi cập nhật dữ liệu của chiến dịch này thời gian thực
        listenToCampaignUpdates()

        // Lấy thông tin thời tiết
        fetchWeather()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Đăng ký sự kiện click nút Tham gia
        binding.btnJoin.setOnClickListener {
            toggleJoinStatus()
        }

        // Đăng ký sự kiện click nút Thích
        binding.btnLike.setOnClickListener {
            toggleLikeStatus()
        }

        // Đăng ký sự kiện click nút Chia sẻ (Nút Glassmorphic trên ảnh banner)
        binding.btnShare.setOnClickListener {
            shareCampaign()
        }

        // Đăng ký sự kiện click nút Bình luận
        binding.btnDetailComments.setOnClickListener {
            CommentsDialogHelper.showComments(this, campaign, currentUserId)
        }

        // Đăng ký sự kiện click nút Chỉnh sửa chiến dịch (chỉ hiển thị cho ORG / Admin)
        binding.btnEditCampaign.setOnClickListener {
            val intent = Intent(this, EditCampaignActivity::class.java)
            intent.putExtra("campaign", campaign)
            startActivity(intent)
        }

        // Đăng ký sự kiện click nút Xem danh sách Tình nguyện viên đăng ký
        binding.btnViewParticipants.setOnClickListener {
            showParticipantsDialog()
        }
    }

    // Thiết lập Toolbar hỗ trợ quay lại màn hình chính
    private fun setupToolbar() {
        setSupportActionBar(binding.root.findViewById(androidx.appcompat.R.id.action_bar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Chi tiết Hoạt động"
        
        binding.root.post {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // Nạp dữ liệu chiến dịch lên giao diện
    private fun setupUI() {
        binding.tvDetailTitle.text = campaign.title
        binding.tvDetailTime.text = campaign.time
        binding.tvDetailDesc.text = campaign.description
        binding.tvDetailRewardPoints.text = "${campaign.trainingPoints} ĐRL"
        
        // Gán số lượng lượt thích, tham gia, bình luận trên các nút tương tác
        binding.tvLikeBtnText.text = "${campaign.favoriteBy.size}"
        binding.tvJoinBtnText.text = "${campaign.participants.size}"
        binding.tvCommentsBtnText.text = "${campaign.commentsCount}"
        binding.tvParticipantsBtnText.text = "${campaign.participants.size}"
        
        // Tải ảnh banner chiến dịch sử dụng ImageLoader Base64
        if (campaign.imageUrl.isNotEmpty()) {
            ImageLoader.loadImage(this, campaign.imageUrl, binding.ivDetail)
        }
    }

    // Khởi tạo Adapter hiển thị danh sách người tham gia
    private fun setupParticipantRecyclerView() {
        participantAdapter = ParticipantAdapter(
            participantList,
            isOrgOrAdmin = this.isOrgOrAdmin,
            confirmedMap = campaign.confirmedParticipants,
            onConfirmClick = { volunteer ->
                confirmVolunteer(volunteer)
            }
        )
    }

    // Lắng nghe thay đổi dữ liệu của chiến dịch thời gian thực từ Firebase
    private fun listenToCampaignUpdates() {
        val campaignId = if (!campaign.id.isNullOrEmpty()) campaign.id else campaign.campaignId
        if (campaignId.isNullOrEmpty()) return
        FirebaseDatabase.getInstance().getReference("campaigns").child(campaignId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val updatedCampaign = snapshot.getValue(Campaign::class.java)
                    if (updatedCampaign != null) {
                        updatedCampaign.id = snapshot.key
                        campaign = updatedCampaign
                        
                        // Cập nhật số liệu hiển thị thời gian thực
                        binding.tvLikeBtnText.text = "${campaign.favoriteBy.size}"
                        binding.tvJoinBtnText.text = "${campaign.participants.size}"
                        binding.tvCommentsBtnText.text = "${campaign.commentsCount}"
                        binding.tvParticipantsBtnText.text = "${campaign.participants.size}"
                        
                        // Cập nhật trạng thái màu sắc của nút Thích & Tham gia
                        updateButtonStates()
 
                        // Kiểm tra quyền vai trò của người dùng để tải danh sách SV đăng ký
                        checkRoleAndLoadParticipants()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
 
    // Cập nhật trạng thái hiển thị (Màu sắc và Biểu tượng) của nút Thích và nút Tham gia dựa trên dữ liệu Firebase
    private fun updateButtonStates() {
        val isLiked = campaign.favoriteBy.containsKey(currentUserId)
        val isJoined = campaign.participants.containsKey(currentUserId)
 
        // Nút Thích
        if (isLiked) {
            binding.ivLikeIcon.setImageResource(R.drawable.ic_like_filled)
            binding.ivLikeIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.primary)
            binding.tvLikeBtnText.setTextColor(ContextCompat.getColor(this, R.color.primary))
        } else {
            binding.ivLikeIcon.setImageResource(R.drawable.ic_like_outline)
            binding.ivLikeIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.slate_500)
            binding.tvLikeBtnText.setTextColor(ContextCompat.getColor(this, R.color.slate_500))
        }
 
        // Nút Tham gia
        if (isJoined) {
            binding.ivJoinIcon.setImageResource(R.drawable.ic_checkmark)
            binding.ivJoinIcon.imageTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
            binding.tvJoinBtnText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            binding.ivJoinIcon.setImageResource(android.R.drawable.ic_menu_add)
            binding.ivJoinIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.slate_500)
            binding.tvJoinBtnText.setTextColor(ContextCompat.getColor(this, R.color.slate_500))
        }
    }

    // Kiểm tra quyền vai trò: Hiển thị nút Edit cho người tạo chiến dịch hoặc Admin; tải danh sách TNV nếu là ORG/Admin
    private fun checkRoleAndLoadParticipants() {
        FirebaseDatabase.getInstance().getReference("users").child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        // Hiển thị nút Chỉnh sửa nếu là Admin hoặc là người tạo ra chiến dịch này
                        val isCreatorOrAdmin = user.role == "Admin" || 
                                              campaign.orgId == currentUserId || 
                                              campaign.creatorId == currentUserId
                        
                        binding.btnEditCampaign.visibility = if (isCreatorOrAdmin) View.VISIBLE else View.GONE
                        
                        this@DetailActivity.isOrgOrAdmin = user.role == "ORG" || user.role == "Admin"
                        participantAdapter.updateValidationStates(this@DetailActivity.isOrgOrAdmin, campaign.confirmedParticipants)

                        // Hiển thị nút xem danh sách TNV đối với vai trò ORG/Admin
                        if (this@DetailActivity.isOrgOrAdmin) {
                            binding.btnViewParticipants.visibility = View.VISIBLE
                            loadRegisteredVolunteers()
                        } else {
                            binding.btnViewParticipants.visibility = View.GONE
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // Tải thông tin của toàn bộ các Tình nguyện viên đã nhấn nút đăng ký tham gia chiến dịch
    private fun loadRegisteredVolunteers() {
        val participantIds = campaign.participants.keys

        if (participantIds.isEmpty()) {
            participantList.clear()
            participantAdapter.updateData(participantList)
            participantAdapter.updateValidationStates(this.isOrgOrAdmin, campaign.confirmedParticipants)
            return
        }

        val usersRef = FirebaseDatabase.getInstance().getReference("users")
        val loadedVolunteers = mutableListOf<User>()
        var count = 0

        for (id in participantIds) {
            usersRef.child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val volunteer = snapshot.getValue(User::class.java)
                    if (volunteer != null) {
                        volunteer.uid = snapshot.key ?: ""
                        loadedVolunteers.add(volunteer)
                    }
                    count++
                    if (count == participantIds.size) {
                        participantList.clear()
                        participantList.addAll(loadedVolunteers)
                        participantAdapter.updateData(participantList)
                        participantAdapter.updateValidationStates(this@DetailActivity.isOrgOrAdmin, campaign.confirmedParticipants)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    count++
                }
            })
        }
    }

    // ORG / Admin phê duyệt hoàn thành chiến dịch cho tình nguyện viên: Cộng ĐRL động và ghi lại lịch sử hoàn thành
    private fun confirmVolunteer(volunteer: User) {
        val campaignId = if (!campaign.id.isNullOrEmpty()) campaign.id else campaign.campaignId
        if (campaignId.isNullOrEmpty()) return
        
        val databaseRef = FirebaseDatabase.getInstance().reference
        // Đánh dấu hoàn thành trên nhánh confirmedParticipants của chiến dịch
        databaseRef.child("campaigns").child(campaignId).child("confirmedParticipants").child(volunteer.uid).setValue(true)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Đã xác nhận hoàn thành cho ${volunteer.name}!", Toast.LENGTH_SHORT).show()
                    
                    val rewardPoints = campaign.trainingPoints // Sử dụng điểm rèn luyện động của chiến dịch tương ứng
                    val completionRef = databaseRef.child("users").child(volunteer.uid).child("completions").child(campaignId)
                    val record = CompletionRecord(
                        campaignId = campaignId,
                        campaignTitle = campaign.title,
                        timestamp = System.currentTimeMillis(),
                        points = rewardPoints
                    )
                    
                    // Ghi lịch sử hoàn thành vào thông tin người dùng
                    completionRef.setValue(record).addOnCompleteListener { completionTask ->
                        if (completionTask.isSuccessful) {
                            val pointsRef = databaseRef.child("users").child(volunteer.uid).child("trainingPoints")
                            // Thực hiện Transaction để cộng điểm rèn luyện an toàn, tránh xung đột
                            pointsRef.runTransaction(object : Transaction.Handler {
                                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                                    val current = mutableData.getValue(Int::class.java) ?: 0
                                    mutableData.value = current + rewardPoints
                                    return Transaction.success(mutableData)
                                }
                                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
                            })
                        }
                    }
                } else {
                    Toast.makeText(this, "Lỗi xác nhận: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Đảo ngược trạng thái đăng ký tham gia (Đăng ký / Hủy đăng ký) của người dùng hiện tại
    private fun toggleJoinStatus() {
        val campaignId = if (!campaign.id.isNullOrEmpty()) campaign.id else campaign.campaignId
        if (campaignId.isNullOrEmpty()) return
        val ref = FirebaseDatabase.getInstance().getReference("campaigns")
            .child(campaignId).child("participants").child(currentUserId)

        val isJoined = campaign.participants.containsKey(currentUserId)
        if (isJoined) {
            ref.removeValue().addOnCompleteListener {
                if (it.isSuccessful) {
                    Toast.makeText(this, "Đã hủy đăng ký tham gia!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Thao tác thất bại: ${it.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            ref.setValue(true).addOnCompleteListener {
                if (it.isSuccessful) {
                    Toast.makeText(this, "Đăng ký tham gia thành công!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Thao tác thất bại: ${it.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Đảo ngược trạng thái thích (Thích / Bỏ thích) chiến dịch của người dùng hiện tại
    private fun toggleLikeStatus() {
        val campaignId = if (!campaign.id.isNullOrEmpty()) campaign.id else campaign.campaignId
        if (campaignId.isNullOrEmpty()) return
        val ref = FirebaseDatabase.getInstance().getReference("campaigns")
            .child(campaignId).child("favoriteBy").child(currentUserId)

        val isLiked = campaign.favoriteBy.containsKey(currentUserId)
        if (isLiked) {
            ref.removeValue().addOnCompleteListener {
                if (it.isSuccessful) {
                    Toast.makeText(this, "Đã bỏ thích chiến dịch!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Thao tác thất bại: ${it.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            ref.setValue(true).addOnCompleteListener {
                if (it.isSuccessful) {
                    Toast.makeText(this, "Đã thích chiến dịch!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Thao tác thất bại: ${it.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Thực hiện chia sẻ thông tin chiến dịch nhanh dưới dạng văn bản
    private fun shareCampaign() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, campaign.title)
            putExtra(Intent.EXTRA_TEXT, "Tham gia cùng tôi trong chiến dịch ý nghĩa này:\n\n${campaign.title}\nThời gian: ${campaign.time}\nMô tả: ${campaign.description}\n\nTải ngay ứng dụng Volunteer Manager để đăng ký tham gia nhé!")
        }
        startActivity(Intent.createChooser(intent, "Chia sẻ chiến dịch"))
    }

    // Khi bản đồ Google Maps đã sẵn sàng để hiển thị
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val location = LatLng(campaign.latitude, campaign.longitude)
        mMap.addMarker(MarkerOptions().position(location).title(campaign.title))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 14f))

        // UX thông minh: Ngăn chặn NestedScrollView cướp sự kiện vuốt bản đồ
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.view?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Yêu cầu bố cục cha không chặn sự kiện chạm để người dùng zoom/kéo bản đồ mượt mà
                    binding.root.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.root.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        checkLocationPermission()
    }

    // Kiểm tra quyền GPS để hiển thị vị trí của tôi trên bản đồ
    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            mMap.isMyLocationEnabled = true
            calculateDistance()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mMap.isMyLocationEnabled = true
                    calculateDistance()
                }
            }
        }
    }

    // Tính toán khoảng cách thực tế từ vị trí GPS hiện tại của người dùng tới địa điểm chiến dịch
    private fun calculateDistance() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val results = FloatArray(1)
                    Location.distanceBetween(location.latitude, location.longitude, campaign.latitude, campaign.longitude, results)
                    val distance = results[0] / 1000 // Chuyển đổi sang đơn vị Kilomet (KM)
                    binding.tvDistance.text = "Khoảng cách từ bạn: %.2f km".format(Locale.US, distance)
                }
            }
        }
    }

    // Tải và hiển thị thời tiết hiện tại tại khu vực diễn ra chiến dịch thông qua API OpenWeather
    private fun fetchWeather() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(WeatherService::class.java)
        service.getCurrentWeather(campaign.latitude, campaign.longitude, WEATHER_API_KEY)
            .enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        val tempCelsius = body?.main?.temp ?: 25.0
                        val rawDesc = body?.weather?.get(0)?.description ?: "trời quang"
                        val desc = rawDesc.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                        binding.tvWeather.text = "Thời tiết: %.1f°C, %s".format(Locale.US, tempCelsius, desc)
                    } else {
                        binding.tvWeather.text = "Thời tiết: 26°C, Trời mát"
                    }
                }
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    binding.tvWeather.text = "Thời tiết: 26°C, Trời mát"
                }
            })
    }

    // Hiển thị BottomSheet chứa danh sách chi tiết các Tình nguyện viên đã đăng ký tham gia chiến dịch
    private fun showParticipantsDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogParticipantsBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Đặt tiêu đề số lượng SV đăng ký
        dialogBinding.tvParticipantsTitle.text = "Danh Sách Đăng Ký (${participantList.size})"

        // Nút đóng hộp thoại
        dialogBinding.ivCloseParticipants.setOnClickListener {
            dialog.dismiss()
        }

        // Khởi tạo hiển thị danh sách
        dialogBinding.rvParticipants.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvParticipants.adapter = participantAdapter

        // Xử lý hiển thị màn hình trống
        if (participantList.isEmpty()) {
            dialogBinding.tvParticipantsEmptyState.visibility = View.VISIBLE
            dialogBinding.rvParticipants.visibility = View.GONE
        } else {
            dialogBinding.tvParticipantsEmptyState.visibility = View.GONE
            dialogBinding.rvParticipants.visibility = View.VISIBLE
        }

        dialog.show()
    }
}

