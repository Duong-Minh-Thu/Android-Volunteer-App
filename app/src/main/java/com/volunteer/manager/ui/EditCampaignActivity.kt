/**
 * ============================================================================
 * TÊN FILE: EditCampaignActivity.kt
 * MỤC ĐÍCH: Màn hình chỉnh sửa thông tin chiến dịch tình nguyện đã có (dành cho ORG / Admin).
 * CHỨC NĂNG CHÍNH:
 *   1. Điền sẵn dữ liệu cũ (Prefill Form): Tự động nạp thông tin hiện tại của chiến dịch (Tiêu đề, Thời gian, Mô tả, ĐRL, Ảnh đại diện, Vĩ độ, Kinh độ) lên các trường nhập liệu tương ứng.
 *   2. Thay đổi ảnh banner: Chọn ảnh từ Gallery và nén sang dạng Base64 qua tiện ích ImageLoader.
 *   3. Chỉnh sửa tọa độ GPS: Cho phép nhập thủ công hoặc click nút định vị tự động qua GPS (ACCESS_FINE_LOCATION).
 *   4. Cập nhật cơ sở dữ liệu an toàn (updateChildren):
 *      - Chỉ cập nhật các trường được chỉnh sửa (Tiêu đề, Thời gian, Mô tả, Ảnh, Tọa độ, ĐRL).
 *      - Đảm bảo tuyệt đối không xóa sạch hoặc làm ảnh hưởng tới nhánh danh sách người đăng ký (participants) hay danh sách lượt thích (favoriteBy) hiện có của chiến dịch!
 *   5. Hỗ trợ UX: Hiển thị trạng thái tải (Loading), tự động ẩn bàn phím khi bấm ra ngoài.
 * ============================================================================
 */
package com.volunteer.manager.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.FirebaseDatabase
import com.volunteer.manager.R
import com.volunteer.manager.databinding.ActivityEditCampaignBinding
import com.volunteer.manager.models.Campaign
import com.volunteer.manager.utils.ImageLoader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.location.Geocoder
import android.view.inputmethod.EditorInfo
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

class EditCampaignActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityEditCampaignBinding
    private lateinit var campaign: Campaign
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val calendar = Calendar.getInstance()
    private var selectedBase64Image: String? = null
    private var mMap: GoogleMap? = null
    private var mMarker: Marker? = null

    // Launcher xử lý chọn ảnh mới từ Gallery để thay thế ảnh banner cũ
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    // Nén ảnh sang định dạng Base64
                    val base64 = ImageLoader.compressBitmapToBase64(bitmap)
                    selectedBase64Image = base64
                    
                    // Hiển thị ảnh xem trước mới chọn
                    binding.ivEditImagePreview.setImageBitmap(bitmap)
                    binding.cardEditImagePreview.visibility = View.VISIBLE
                    
                    Toast.makeText(this, "Chọn và nén ảnh thành công!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Không thể đọc định dạng ảnh này!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi đọc ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditCampaignBinding.inflate(layoutInflater)
        setContentView(binding.root)

        campaign = intent.getSerializableExtra("campaign") as Campaign
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupToolbar()
        prefillForm()

        // Bấm chọn thời gian hiển thị hộp thoại Date/Time Picker
        binding.etEditTime.setOnClickListener {
            showDateTimePicker()
        }

        // Bấm chọn ảnh mới
        binding.btnEditSelectImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Bấm lấy tọa độ GPS tự động cập nhật
        binding.btnEditLocateGPS.setOnClickListener {
            checkAndFetchGPS()
        }

        // Khởi tạo bản đồ picker cho chỉnh sửa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_picker_edit) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Tìm vị trí theo địa chỉ
        binding.btnEditSearchAddress.setOnClickListener {
            searchAddress()
        }
        binding.etEditAddressSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchAddress()
                true
            } else {
                false
            }
        }

        // Bấm lưu lại các thay đổi của chiến dịch
        binding.btnSaveEditCampaign.setOnClickListener {
            saveEditCampaign()
        }
    }

    // Thiết lập Toolbar có hỗ trợ quay lại
    private fun setupToolbar() {
        setSupportActionBar(binding.root.findViewById(androidx.appcompat.R.id.action_bar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Chỉnh sửa Chiến dịch"
        
        binding.root.post {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // Tự động điền dữ liệu cũ của chiến dịch lên giao diện biểu mẫu chỉnh sửa
    private fun prefillForm() {
        binding.etEditTitle.setText(campaign.title)
        binding.etEditTime.setText(campaign.time)
        binding.etEditDesc.setText(campaign.description)
        binding.etEditPoints.setText(campaign.trainingPoints.toString())
        
        // Tải ảnh banner cũ nếu có sẵn
        if (!campaign.imageUrl.isNullOrEmpty()) {
            selectedBase64Image = campaign.imageUrl
            ImageLoader.loadImage(this, campaign.imageUrl, binding.ivEditImagePreview)
            binding.cardEditImagePreview.visibility = View.VISIBLE
        }
        
        binding.etEditLatitude.setText(campaign.latitude.toString())
        binding.etEditLongitude.setText(campaign.longitude.toString())
    }

    // Hiển thị hộp thoại kết hợp chọn Ngày và Giờ
    private fun showDateTimePicker() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)

                val myFormat = "dd/MM/yyyy - hh:mm a"
                val sdf = SimpleDateFormat(myFormat, Locale.US)
                binding.etEditTime.setText(sdf.format(calendar.time))
            }

            TimePickerDialog(
                this, timeSetListener,
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE), false
            ).show()
        }

        DatePickerDialog(
            this, dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // Kiểm tra cấp quyền GPS trước khi định vị
    private fun checkAndFetchGPS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1002
            )
        } else {
            fetchLocation()
        }
    }

    // Thực hiện định vị GPS để lấy tọa độ kinh độ & vĩ độ thực tế hiện tại
    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            binding.btnEditLocateGPS.isEnabled = false
            binding.btnEditLocateGPS.text = "Đang lấy tọa độ GPS..."

            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                binding.btnEditLocateGPS.isEnabled = true
                binding.btnEditLocateGPS.text = "Lấy tọa độ vị trí hiện tại qua GPS"

                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    updateSelectedLocation(latLng)
                    Toast.makeText(this, "Đã tự động cập nhật tọa độ GPS mới!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Không tìm thấy GPS. Vui lòng điền tọa độ thủ công!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocation()
            } else {
                Toast.makeText(this, "Quyền vị trí bị từ chối. Vui lòng nhập tọa độ thủ công!", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Lưu các thông tin đã chỉnh sửa vào Firebase Realtime Database
    private fun saveEditCampaign() {
        val title = binding.etEditTitle.text.toString().trim()
        val time = binding.etEditTime.text.toString().trim()
        val desc = binding.etEditDesc.text.toString().trim()
        val latStr = binding.etEditLatitude.text.toString().trim()
        val lngStr = binding.etEditLongitude.text.toString().trim()

        // Kiểm định tiêu đề
        if (title.isEmpty()) {
            binding.etEditTitle.error = "Tiêu đề không được để trống"
            binding.etEditTitle.requestFocus()
            return
        }

        // Kiểm định thời gian
        if (time.isEmpty()) {
            binding.etEditTime.error = "Thời gian không được để trống"
            binding.etEditTime.requestFocus()
            return
        }

        // Kiểm định mô tả
        if (desc.isEmpty()) {
            binding.etEditDesc.error = "Mô tả không được để trống"
            binding.etEditDesc.requestFocus()
            return
        }

        // Kiểm định hình ảnh
        if (selectedBase64Image == null) {
            Toast.makeText(this, "Vui lòng chọn hình ảnh cho chiến dịch!", Toast.LENGTH_LONG).show()
            return
        }

        // Kiểm định tọa độ địa lý
        if (latStr.isEmpty() || lngStr.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ Vĩ độ và Kinh độ!", Toast.LENGTH_SHORT).show()
            return
        }

        val latitude: Double
        val longitude: Double
        try {
            latitude = latStr.toDouble()
            longitude = lngStr.toDouble()
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Tọa độ không hợp lệ. Vui lòng nhập số thập phân!", Toast.LENGTH_SHORT).show()
            return
        }

        // Kiểm định điểm rèn luyện động
        val pointsStr = binding.etEditPoints.text.toString().trim()
        val points = if (pointsStr.isNotEmpty()) {
            try {
                pointsStr.toInt()
            } catch (e: NumberFormatException) {
                10
            }
        } else {
            10
        }

        // Khóa giao diện và hiển thị trạng thái tải
        setLoading(true)

        val campaignId = if (!campaign.id.isNullOrEmpty()) campaign.id else campaign.campaignId
        if (campaignId.isNullOrEmpty()) return

        // Khởi tạo HashMap để cập nhật (chỉ ghi đè các trường thay đổi, bảo toàn hoàn toàn danh sách TNV đăng ký!)
        val updates = HashMap<String, Any>()
        updates["title"] = title
        updates["time"] = time
        updates["description"] = desc
        updates["imageUrl"] = selectedBase64Image ?: ""
        updates["latitude"] = latitude
        updates["longitude"] = longitude
        updates["trainingPoints"] = points

        // Thực hiện cập nhật trên Firebase
        FirebaseDatabase.getInstance().getReference("campaigns").child(campaignId)
            .updateChildren(updates).addOnCompleteListener { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    Toast.makeText(this, "Cập nhật chiến dịch thành công!", Toast.LENGTH_LONG).show()
                    finish() // Hoàn tất, đóng màn hình quay về trang chi tiết chiến dịch
                } else {
                    Toast.makeText(this, "Lỗi cập nhật: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    // Điều khiển trạng thái bật/tắt của vòng xoay tải và vô hiệu hóa tương tác
    private fun setLoading(isLoading: Boolean) {
        binding.editProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSaveEditCampaign.visibility = if (isLoading) View.GONE else View.VISIBLE
        binding.etEditTitle.isEnabled = !isLoading
        binding.etEditTime.isEnabled = !isLoading
        binding.etEditDesc.isEnabled = !isLoading
        binding.etEditPoints.isEnabled = !isLoading
        binding.etEditLatitude.isEnabled = !isLoading
        binding.etEditLongitude.isEnabled = !isLoading
        binding.btnEditLocateGPS.isEnabled = !isLoading
        binding.btnEditSelectImage.isEnabled = !isLoading
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        
        // UX thông minh: Ngăn chặn ScrollView cướp sự kiện vuốt bản đồ picker
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_picker_edit) as SupportMapFragment
        mapFragment.view?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.root.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.root.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        // Lắng nghe sự kiện click trên bản đồ
        mMap?.setOnMapClickListener { latLng ->
            updateSelectedLocation(latLng)
        }

        // Cắm marker ban đầu tại vị trí hiện tại của chiến dịch đang chỉnh sửa
        val lat = campaign.latitude
        val lng = campaign.longitude
        val location = LatLng(lat, lng)
        updateSelectedLocation(location)
        mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 14f))
    }

    private fun updateSelectedLocation(latLng: LatLng) {
        mMarker?.remove()
        mMarker = mMap?.addMarker(MarkerOptions().position(latLng).title("Vị trí đã chọn"))
        
        binding.etEditLatitude.setText("%.6f".format(Locale.US, latLng.latitude))
        binding.etEditLongitude.setText("%.6f".format(Locale.US, latLng.longitude))
    }

    private fun searchAddress() {
        val addressStr = binding.etEditAddressSearch.text.toString().trim()
        if (addressStr.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập địa chỉ cần tìm!", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Đang tìm kiếm vị trí...", Toast.LENGTH_SHORT).show()

        Thread {
            var latLng: LatLng? = null
            var displayName: String? = null

            // 1. Thử dùng Geocoder mặc định của hệ thống Android
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocationName(addressStr, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    latLng = LatLng(address.latitude, address.longitude)
                    displayName = address.getAddressLine(0)
                }
            } catch (e: Exception) {
                // Geocoder hệ thống lỗi
            }

            // 2. Thử dùng Google Geocoding API bằng API Key của ứng dụng
            if (latLng == null) {
                try {
                    val apiKey = getString(R.string.google_maps_key)
                    if (apiKey.isNotEmpty() && apiKey != "YOUR_API_KEY_HERE") {
                        val urlString = "https://maps.googleapis.com/maps/api/geocode/json?address=" +
                                java.net.URLEncoder.encode(addressStr, "UTF-8") + "&key=" + apiKey
                        val url = java.net.URL(urlString)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000

                        if (connection.responseCode == 200) {
                            val response = connection.inputStream.bufferedReader().use { it.readText() }
                            val jsonObj = org.json.JSONObject(response)
                            val status = jsonObj.optString("status", "")
                            if (status == "OK") {
                                val resultsArray = jsonObj.getJSONArray("results")
                                if (resultsArray.length() > 0) {
                                    val result = resultsArray.getJSONObject(0)
                                    val geometry = result.getJSONObject("geometry")
                                    val locationObj = geometry.getJSONObject("location")
                                    val lat = locationObj.getDouble("lat")
                                    val lng = locationObj.getDouble("lng")
                                    latLng = LatLng(lat, lng)
                                    displayName = result.optString("formatted_address", addressStr)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Google Geocoding API lỗi hoặc chưa kích hoạt
                }
            }

            // 3. Nếu các phương án trên đều thất bại, gọi Nominatim (OpenStreetMap API) dự phòng
            if (latLng == null) {
                try {
                    val urlString = "https://nominatim.openstreetmap.org/search?q=" +
                            java.net.URLEncoder.encode(addressStr, "UTF-8") + "&format=json&limit=1"
                    val url = java.net.URL(urlString)
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "VolunteerManagerAndroidApp/1.0")
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val jsonArray = org.json.JSONArray(response)
                        if (jsonArray.length() > 0) {
                            val jsonObj = jsonArray.getJSONObject(0)
                            val lat = jsonObj.getDouble("lat")
                            val lon = jsonObj.getDouble("lon")
                            latLng = LatLng(lat, lon)
                            displayName = jsonObj.optString("display_name", addressStr)
                        }
                    }
                } catch (e: Exception) {
                    // Lỗi kết nối HTTP Nominatim
                }
            }

            // 4. Cập nhật kết quả lên giao diện trên Main Thread
            runOnUiThread {
                if (latLng != null) {
                    mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    updateSelectedLocation(latLng)
                    Toast.makeText(this, "Đã tìm thấy: ${displayName ?: addressStr}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Không tìm thấy địa chỉ hoặc kết nối mạng bị lỗi!", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // UX: Tự động ẩn bàn phím khi bấm ra ngoài các trường nhập liệu
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

