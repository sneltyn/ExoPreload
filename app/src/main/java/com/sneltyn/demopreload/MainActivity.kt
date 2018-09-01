package com.sneltyn.demopreload

import android.os.Bundle
import android.support.v7.app.AppCompatActivity


class MainActivity : AppCompatActivity(){

    private lateinit var mPlayer: DefaultPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mPlayer = findViewById(R.id.player)
    }

    override fun onStart() {
        super.onStart()
        mPlayer.playVideo(R.raw.test)
    }

    override fun onDestroy() {
        super.onDestroy()
        mPlayer.release()
    }
}
