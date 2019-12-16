package com.example.myapplication

import android.util.Log
import com.example.myapplication.Downloader.END_TIME
import rx.Observable
import rx.Subscriber
import java.util.*

object PackDownloader {

    const val TAG = "RxDemo:PackDownloader"
    private var timer: Timer? = null
    private var timerTask: TimerTask? = null
    private var timerProgress: Int = -1
    private var packSubscriber: Subscriber<in Int>? = null

    fun reset() {
        EndTimer()
        packSubscriber?.unsubscribe()

    }

    fun downloadPacks(): Observable<Int> = Observable.create { subscriber ->
        packSubscriber = subscriber
        Log.e(TAG, "downloadFlutterPackage() start")
        StartTimer()
    }

    private fun StartTimer() {
        if (timer == null && timerTask == null) {
            timer = Timer()
            timerTask = object : TimerTask() {
                override fun run() {
                    timerProgress++
                    if (timerProgress >= END_TIME) {
                        packSubscriber?.onCompleted()
                        EndTimer()
                    } else {
                        packSubscriber?.onNext(timerProgress)
                    }
                }
            }
            timer?.schedule(timerTask, 500, 2000)
        }
    }

    private fun EndTimer() {
        timer?.cancel()
        timerTask?.cancel()
        timer = null
        timerTask = null
    }
}