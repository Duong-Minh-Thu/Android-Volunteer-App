/**
 * ============================================================================
 * TÊN FILE: LoginActivity.kt
 * MỤC ĐÍCH: Màn hình đăng nhập tài khoản của ứng dụng Volunteer Manager.
 * CHỨC NĂNG CHÍNH:
 *   1. Kiểm tra trạng thái đăng nhập: Nếu người dùng đã đăng nhập trước đó, chuyển hướng thẳng vào MainActivity.
 *   2. Kiểm định dữ liệu đầu vào (Validation): Kiểm tra định dạng Email hợp lệ, các ô không được để trống.
 *   3. Kết nối Firebase Auth: Xác thực thông tin tài khoản qua signInWithEmailAndPassword.
 *   4. Quản lý trạng thái tải (Loading): Bật/tắt ProgressBar và vô hiệu hóa các ô nhập liệu trong lúc đang xử lý đăng nhập.
 *   5. Hỗ trợ UX ẩn bàn phím khi chạm ra ngoài khu vực EditText.
 * ============================================================================
 */
package com.volunteer.manager.ui

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Patterns
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.volunteer.manager.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Nếu đã đăng nhập từ trước, tự động chuyển vào màn hình chính
        if (auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Xử lý sự kiện khi nhấn nút Đăng nhập
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            // Kiểm tra email không được trống
            if (email.isEmpty()) {
                binding.etEmail.error = "Vui lòng nhập Email"
                binding.etEmail.requestFocus()
                return@setOnClickListener
            }

            // Kiểm tra định dạng email hợp lệ
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmail.error = "Định dạng Email không hợp lệ"
                binding.etEmail.requestFocus()
                return@setOnClickListener
            }

            // Kiểm tra mật khẩu không được trống
            if (pass.isEmpty()) {
                binding.etPassword.error = "Vui lòng nhập Mật khẩu"
                binding.etPassword.requestFocus()
                return@setOnClickListener
            }

            // Hiển thị vòng xoay tải và vô hiệu hóa tương tác để tránh bấm nút nhiều lần
            setLoading(true)

            // Tiến hành đăng nhập thông qua Firebase Authentication
            auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Đăng nhập thất bại: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Chuyển hướng sang màn hình Đăng ký tài khoản
        binding.tvToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    // Thiết lập trạng thái hiển thị của vòng xoay tải (ProgressBar)
    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
        binding.etEmail.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
        binding.tvToRegister.isEnabled = !isLoading
    }

    // Xử lý UX: Tự động ẩn bàn phím và xóa Focus của EditText khi người dùng chạm ra ngoài
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

