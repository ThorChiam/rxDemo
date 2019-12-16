package com.example.myapplication

import android.util.Log
import rx.Observable
import rx.Subscriber
import java.util.*

object ShareModule {

    const val TAG = "RxDemo:ShareModule"
    const val END_TIME = 10
    private var timer1: Timer? = null
    private var timerTask1: TimerTask? = null
    private var timerProgress1: Int = -2
    private var shareDownloadSubscriber: Subscriber<in Int>? = null

    var isInstalled: Boolean = false


    fun reset() {
        EndTimer()
        shareDownloadSubscriber?.unsubscribe()
    }

    fun prepareToDownload(result: String) {
        Log.d(TAG, "share prepareToDownload()")
        if (result.contains("s")) {
            StartTimer()
        } else {
            shareDownloadCallBack.handleInstallFail(NoClassDefFoundError())
        }
    }

    object shareDownloadCallBack {
        fun handleDownloading(progress: Int) {
            Log.i(TAG, "share progress: $progress")
            if (shareDownloadSubscriber != null && shareDownloadSubscriber!!.isUnsubscribed) {
                return
            }
            if (progress <= 0) {
                shareDownloadSubscriber?.onNext(0)
            } else {
                shareDownloadSubscriber?.onNext(progress)
            }
        }

        fun handleInstallSuccess() {
            Log.i(TAG, "share Module: install success")
            EndTimer()
            isInstalled = true
            shareDownloadSubscriber?.onCompleted()
        }

        fun handleInstallFail(error: Throwable) {
            Log.e(TAG, "share Module: install error($error)")
            EndTimer()
            shareDownloadSubscriber?.onError(error)
        }

    }

    fun downloadAndInstall(result: String): Observable<Int> = Observable.create { subscriber ->
        Log.e(TAG, "share downloadAndInstall")
        shareDownloadSubscriber = subscriber
        if (isInstalled) {
            subscriber.onCompleted()
            return@create
        }
        prepareToDownload(result)
        if (isInstalled) {
            subscriber.onCompleted()
            return@create
        }
    }

    private fun StartTimer() {
        if (timer1 == null && timerTask1 == null) {
            timer1 = Timer()
            timerTask1 = object : TimerTask() {
                override fun run() {
                    timerProgress1++
                    if (timerProgress1 >= END_TIME) {
                        shareDownloadCallBack.handleInstallSuccess()
                    } else {
                        shareDownloadCallBack.handleDownloading(timerProgress1)
                    }

                }
            }
            timer1?.schedule(timerTask1, 5000, 1000)
        }
    }

    private fun EndTimer() {
        timer1?.cancel()
        timerTask1?.cancel()
        timer1 = null
        timerTask1 = null
    }
}