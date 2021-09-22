package com.kuaishou.akdanmaku.sample

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    findViewById<View>(R.id.entryFullScreen).setOnClickListener {
      startActivity(Intent(this, SampleFullScreenActivity::class.java))
    }

    findViewById<View>(R.id.entryFullScreenStep).setOnClickListener {
      startActivity(Intent(this, SampleFullScreenActivity::class.java).putExtra("byStep", true))
    }
  }
}
