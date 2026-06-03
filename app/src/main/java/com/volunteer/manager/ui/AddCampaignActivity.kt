/**
 * ============================================================================
 * TÊN FILE: AddCampaignActivity.kt
 * MỤC ĐÍCH: Màn hình tạo và đăng ký chiến dịch tình nguyện mới (chỉ dành cho ORG / Admin).
 * CHỨC NĂNG CHÍNH:
 *   1. Nhập thông tin sự kiện: Tiêu đề, Thời gian diễn ra (DatePicker + TimePicker), Điểm rèn luyện động (mặc định 10), Mô tả chi tiết.
 *   2. Chọn và nén ảnh đại diện:
 *      - Chọn ảnh từ thư viện thiết bị (pickImageLauncher).
 *      - Gọi ImageLoader để nén ảnh Base64 an toàn trên luồng phụ và lưu trữ trực tiếp dưới dạng chuỗi Base64 trên Realtime Database.
 *   3. Điền nhanh tọa độ GPS (FusedLocationProvider):
 *      - Xin quyền GPS (ACCESS_FINE_LOCATION).
 *      - Lấy tọa độ GPS thực tế hiện tại và điền nhanh vào ô Vĩ độ (Latitude) & Kinh độ (Longitude) (tự động fallback về TP.HCM nếu là giả lập hoặc không có GPS).
 *   4. Đồng bộ hóa Firebase Realtime Database:
 *      - Tự động lấy họ tên thật (orgName) của Nhà tổ chức từ nhánh `/users/$orgId`.
 *      - Tạo mã ID chiến dịch duy nhất bằng `.push().key` và lưu vào nhánh `/campaigns/$campaignId`.
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.volunteer.manager.R
import com.volunteer.manager.databinding.ActivityAddCampaignBinding
import com.volunteer.manager.models.Campaign
import com.volunteer.manager.models.User
import com.volunteer.manager.utils.ImageLoader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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

class AddCampaignActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityAddCampaignBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var auth: FirebaseAuth
    private val calendar = Calendar.getInstance()
    private var selectedBase64Image: String? = null
    private var mMap: GoogleMap? = null
    private var mMarker: Marker? = null

    // Đăng ký Launcher xử lý chọn ảnh từ Thư viện (Gallery)
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    // Nén ảnh sang định dạng Base64 để lưu vào Database
                    val base64 = ImageLoader.compressBitmapToBase64(bitmap)
                    selectedBase64Image = base64
                    
                    // Hiển thị ảnh xem trước
                    binding.ivAddImagePreview.setImageBitmap(bitmap)
                    binding.cardAddImagePreview.visibility = View.VISIBLE
                    
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
        binding = ActivityAddCampaignBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupToolbar()

        // Bấm chọn thời gian hiển thị hộp thoại Date/Time Picker
        binding.etAddTime.setOnClickListener {
            showDateTimePicker()
        }

        // Bấm chọn ảnh đại diện từ Gallery
        binding.btnSelectImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Bấm lấy tọa độ GPS tự động điền
        binding.btnLocateGPS.setOnClickListener {
            checkAndFetchGPS()
        }

        // Khởi tạo bản đồ picker
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_picker_add) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Tìm vị trí theo địa chỉ
        binding.btnSearchAddress.setOnClickListener {
            searchAddress()
        }
        binding.etAddAddressSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchAddress()
                true
            } else {
                false
            }
        }

        // Bấm lưu thông tin chiến dịch mới
        binding.btnSaveCampaign.setOnClickListener {
            saveCampaign()
        }
    }

    // Hiển thị hộp thoại kết hợp chọn Ngày (DatePicker) và Giờ (TimePicker) trực quan
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
                binding.etAddTime.setText(sdf.format(calendar.time))
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

    // Thiết lập Toolbar có hỗ trợ nút Back quay lại màn hình chính
    private fun setupToolbar() {
        setSupportActionBar(binding.root.findViewById(androidx.appcompat.R.id.action_bar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Đăng ký Chiến dịch"
        
        binding.root.post {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // Kiểm tra cấp quyền trước khi tiến hành định vị tọa độ GPS
    private fun checkAndFetchGPS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1001
            )
        } else {
            fetchLocation()
        }
    }

    // Lấy vị trí GPS hiện tại và điền tọa độ thập phân vào Vĩ độ & Kinh độ
    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            binding.btnLocateGPS.isEnabled = false
            binding.btnLocateGPS.text = "Đang xác vị trí GPS..."

            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                binding.btnLocateGPS.isEnabled = true
                binding.btnLocateGPS.text = "Lấy tọa độ vị trí hiện tại qua GPS"

                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    updateSelectedLocation(latLng)
                    Toast.makeText(this, "Đã tự động điền tọa độ GPS!", Toast.LENGTH_SHORT).show()
                } else {
                    // Trình dự phòng (fallback) nếu là giả lập không có dữ liệu GPS sẵn sàng
                    val defaultLatLng = LatLng(10.762622, 106.660172)
                    mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(defaultLatLng, 12f))
                    updateSelectedLocation(defaultLatLng)
                    Toast.makeText(this, "Không nhận được GPS. Đã điền tọa độ mặc định TP.HCM!", Toast.LENGTH_LONG).show()
                }
            }.addOnFailureListener {
                binding.btnLocateGPS.isEnabled = true
                binding.btnLocateGPS.text = "Lấy tọa độ vị trí hiện tại qua GPS"
                Toast.makeText(this, "Lỗi GPS: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocation()
            } else {
                Toast.makeText(this, "Quyền vị trí bị từ chối. Vui lòng nhập tọa độ thủ công!", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Xác nhận và đăng tải chiến dịch tình nguyện mới lên Firebase Realtime Database
    private fun saveCampaign() {
        val title = binding.etAddTitle.text.toString().trim()
        val time = binding.etAddTime.text.toString().trim()
        val desc = binding.etAddDesc.text.toString().trim()
        val latStr = binding.etAddLatitude.text.toString().trim()
        val lngStr = binding.etAddLongitude.text.toString().trim()

        // Kiểm định tiêu đề
        if (title.isEmpty()) {
            binding.etAddTitle.error = "Tiêu đề không được để trống"
            binding.etAddTitle.requestFocus()
            return
        }

        // Kiểm định thời gian
        if (time.isEmpty()) {
            binding.etAddTime.error = "Thời gian không được để trống"
            binding.etAddTime.requestFocus()
            return
        }

        // Kiểm định mô tả
        if (desc.isEmpty()) {
            binding.etAddDesc.error = "Mô tả không được để trống"
            binding.etAddDesc.requestFocus()
            return
        }

        // Kiểm định ảnh banner bắt buộc
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

        // Điểm rèn luyện động được cấu hình tự do (mặc định là 10 ĐRL)
        val pointsStr = binding.etAddPoints.text.toString().trim()
        val points = if (pointsStr.isNotEmpty()) {
            try {
                pointsStr.toInt()
            } catch (e: NumberFormatException) {
                10
            }
        } else {
            10
        }

        val uid = auth.currentUser?.uid ?: return

        // Hiển thị vòng xoay tải và ẩn nút Lưu để tránh đăng tải trùng lặp
        setLoading(true)

        // Thực hiện truy vấn tên hiển thị thực tế của ORG tạo chiến dịch
        FirebaseDatabase.getInstance().getReference("users").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)
                    val orgName = user?.name ?: "Nhà Tổ Chức"

                    val databaseRef = FirebaseDatabase.getInstance().getReference("campaigns")
                    val campaignId = databaseRef.push().key ?: ""

                    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val currentDateStr = "Đăng ngày: " + formatter.format(Date())

                    // Khởi tạo đối tượng Campaign mới
                    val campaign = Campaign(
                        id = campaignId,
                        campaignId = campaignId,
                        creatorId = uid,
                        date = currentDateStr,
                        title = title,
                        time = time,
                        description = desc,
                        imageUrl = selectedBase64Image ?: "",
                        latitude = latitude,
                        longitude = longitude,
                        orgId = uid,
                        orgName = orgName,
                        trainingPoints = points
                    )

                    // Lưu dữ liệu vào Firebase Realtime Database
                    databaseRef.child(campaignId).setValue(campaign).addOnCompleteListener { task ->
                        setLoading(false)
                        if (task.isSuccessful) {
                            Toast.makeText(this@AddCampaignActivity, "Tạo chiến dịch thành công!", Toast.LENGTH_LONG).show()
                            finish() // Đăng ký thành công, kết thúc activity quay lại trang chủ
                        } else {
                            Toast.makeText(this@AddCampaignActivity, "Lỗi đăng tải: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    setLoading(false)
                    Toast.makeText(this@AddCampaignActivity, "Lỗi kết nối cơ sở dữ liệu!", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // Điều khiển bật/tắt hiển thị trạng thái tải và tương tác của các trường
    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSaveCampaign.visibility = if (isLoading) View.GONE else View.VISIBLE
        binding.etAddTitle.isEnabled = !isLoading
        binding.etAddTime.isEnabled = !isLoading
        binding.etAddDesc.isEnabled = !isLoading
        binding.etAddLatitude.isEnabled = !isLoading
        binding.etAddLongitude.isEnabled = !isLoading
        binding.btnLocateGPS.isEnabled = !isLoading
        binding.btnSelectImage.isEnabled = !isLoading
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        
        // UX thông minh: Ngăn chặn ScrollView cướp sự kiện vuốt bản đồ picker
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_picker_add) as SupportMapFragment
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

        // Tọa độ mặc định: TP.HCM
        val defaultLoc = LatLng(10.762622, 106.660172)
        mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 12f))

        // Lắng nghe sự kiện click trên bản đồ
        mMap?.setOnMapClickListener { latLng ->
            updateSelectedLocation(latLng)
        }

        // Kiểm tra xem đã có sẵn tọa độ trong EditText chưa để cắm marker (ví dụ khi GPS chạy trước khi map ready)
        val latStr = binding.etAddLatitude.text.toString()
        val lngStr = binding.etAddLongitude.text.toString()
        if (latStr.isNotEmpty() && lngStr.isNotEmpty()) {
            try {
                val lat = latStr.toDouble()
                val lng = lngStr.toDouble()
                val loc = LatLng(lat, lng)
                updateSelectedLocation(loc)
                mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(loc, 15f))
            } catch (e: Exception) {}
        }
    }

    private fun updateSelectedLocation(latLng: LatLng) {
        mMarker?.remove()
        mMarker = mMap?.addMarker(MarkerOptions().position(latLng).title("Vị trí đã chọn"))
        
        binding.etAddLatitude.setText("%.6f".format(Locale.US, latLng.latitude))
        binding.etAddLongitude.setText("%.6f".format(Locale.US, latLng.longitude))
    }

    private fun searchAddress() {
        val addressStr = binding.etAddAddressSearch.text.toString().trim()
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
                // Geocoder hệ thống lỗi, sẽ tự động chuyển qua Nominatim dự phòng
            }

            // 2. Nếu Geocoder lỗi hoặc không tìm thấy, gọi Nominatim (OpenStreetMap API) dự phòng
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
                    // Lỗi kết nối HTTP
                }
            }

            // 3. Cập nhật kết quả lên giao diện trên Main Thread
            runOnUiThread {
                if (latLng != null) {
                    mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng!!, 15f))
                    updateSelectedLocation(latLng!!)
                    Toast.makeText(this, "Đã tìm thấy: ${displayName ?: addressStr}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Không tìm thấy địa chỉ hoặc kết nối mạng bị lỗi!", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // UX: Tự động ẩn bàn phím khi bấm ra ngoài khu vực nhập liệu
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

