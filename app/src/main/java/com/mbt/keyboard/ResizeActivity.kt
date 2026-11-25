package com.mbt.keyboard

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

/**
 * 키보드 크기·위치 조정 단독 화면 (공용 [ResizePane] 래퍼).
 */
class ResizeActivity : AppCompatActivity() {
    private lateinit var pane: ResizePane

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resize)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        pane = ResizePane(this, root, layoutInflater)
        findViewById<MaterialButton>(R.id.doneButton).setOnClickListener { finish() }
    }

    override fun onDestroy() {
        if (::pane.isInitialized) pane.destroy()
        super.onDestroy()
    }
}
