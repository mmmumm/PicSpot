package com.example.picspot3.utils

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.example.picspot3.R

class MainActivity : AppCompatActivity() {

    lateinit var start :Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        start = findViewById(R.id.start_btn)

        start.setOnClickListener {
            val intent = Intent(applicationContext, InputActivity::class.java)
            startActivity(intent)
        }
    }
}