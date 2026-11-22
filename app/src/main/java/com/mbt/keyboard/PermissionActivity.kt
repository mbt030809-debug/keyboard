package com.mbt.keyboard

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** 키보드에서 마이크 키를 처음 누를 때 마이크 권한을 요청하는 투명 화면. */
class PermissionActivity : AppCompatActivity() {

    private val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(
            this,
            if (granted) R.string.voice_permission_ok else R.string.voice_permission_denied,
            Toast.LENGTH_LONG,
        ).show()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
