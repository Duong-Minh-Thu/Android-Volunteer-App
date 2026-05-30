/**
 * ============================================================================
 * TÊN FILE: RegisterActivity.kt
 * MỤC ĐÍCH: Màn hình đăng ký tài khoản mới cho ứng dụng Volunteer Manager.
 * CHỨC NĂNG CHÍNH:
 *   1. Lựa chọn vai trò người dùng: Học sinh/Tình nguyện viên (Student) hoặc Nhà tổ chức (ORG).
 *   2. Kiểm định dữ liệu đăng ký (Validation): Họ tên không được để trống, định dạng email hợp lệ, độ dài mật khẩu >= 6 ký tự.
 *   3. Đăng ký tài khoản qua Firebase Auth: Sử dụng createUserWithEmailAndPassword.
 *   4. Lưu thông tin người dùng bổ sung: Ghi thông tin UID, họ tên, email, vai trò (role) và điểm rèn luyện mặc định (0) vào Realtime Database tại nhánh `users/$uid`.
 *   5. Quản lý hiển thị trạng thái tải (Loading) thông qua ProgressBar.
 *   6. UX thân thiện: Tự động ẩn bàn phím khi bấm ra ngoài vùng nhập liệu.
 * ============================================================================
 */
package com.volunteer.manager.ui

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Patterns
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.volunteer.manager.databinding.ActivityRegisterBinding
import com.volunteer.manager.models.User

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    // Định nghĩa hai vai trò cơ bản của hệ thống: Tình nguyện viên (Student) và Nhà tổ chức (ORG)
    private val roles = arrayOf("Student", "ORG")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Thiết lập Adapter hiển thị danh sách vai trò cho Spinner lựa chọn
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRole.adapter = adapter

        // Sự kiện click nút Đăng ký tài khoản
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()
            val role = binding.spinnerRole.selectedItem.toString()

            // Kiểm định tên không trống
            if (name.isEmpty()) {
                binding.etName.error = "Vui lòng nhập Họ và Tên"
                binding.etName.requestFocus()
                return@setOnClickListener
            }

            // Kiểm định email không trống
            if (email.isEmpty()) {
                binding.etEmail.error = "Vui lòng nhập Email"
                binding.etEmail.requestFocus()
                return@setOnClickListener
            }

            // Kiểm định định dạng email hợp lệ
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmail.error = "Định dạng Email không hợp lệ"
                binding.etEmail.requestFocus()
                return@setOnClickListener
            }

            // Kiểm định mật khẩu không trống
            if (pass.isEmpty()) {
                binding.etPassword.error = "Vui lòng nhập Mật khẩu"
                binding.etPassword.requestFocus()
                return@setOnClickListener
            }

            // Mật khẩu bắt buộc phải từ 6 ký tự trở lên theo quy định của Firebase Auth
            if (pass.length < 6) {
                binding.etPassword.error = "Mật khẩu phải chứa ít nhất 6 ký tự"
                binding.etPassword.requestFocus()
                return@setOnClickListener
            }

            // Hiển thị vòng xoay tải và vô hiệu hóa các nút tương tác
            setLoading(true)

            // Bắt đầu đăng ký tài khoản trên hệ thống Firebase Authentication
            auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: ""
                    // Tạo đối tượng dữ liệu User mới
                    val user = User(uid, name, email, role)
                    
                    // Lưu trữ thông tin chi tiết của người dùng vào cơ sở dữ liệu Firebase Realtime Database
                    FirebaseDatabase.getInstance().getReference("users").child(uid).setValue(user)
                        .addOnCompleteListener { dbTask ->
                            setLoading(false)
                            if (dbTask.isSuccessful) {
                                Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                                finish() // Đăng ký thành công, quay về màn hình đăng nhập
                            } else {
                                Toast.makeText(this, "Lưu dữ liệu người dùng thất bại: ${dbTask.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    setLoading(false)
                    Toast.makeText(this, "Đăng ký thất bại: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Quay lại màn hình Đăng nhập
        binding.tvToLogin.setOnClickListener {
            finish()
        }
    }

    // Điều khiển trạng thái tải, bật/tắt ProgressBar và vô hiệu hóa/kích hoạt các trường tương tác tương ứng
    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !isLoading
        binding.etName.isEnabled = !isLoading
        binding.etEmail.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
        binding.spinnerRole.isEnabled = !isLoading
        binding.tvToLogin.isEnabled = !isLoading
    }

    // Hỗ trợ UX tự động ẩn bàn phím khi chạm ra bên ngoài khu vực nhập liệu
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

