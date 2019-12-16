package com.example.myapplication

import android.util.Log
import rx.Observable

object Downloader {
    const val TAG = "RxDemo:Downloader"
    const val END_TIME = 10

    fun download(result: String): Observable<Int> {
        Log.i(TAG, "++++++++ download(result = ${result})")
        Log.i(TAG, "++++++++ EngineModule.isInstalled():${EngineModule.isInstalled()}")
        return if (!EngineModule.isInstalled()) {
            // 没有安装先安装
            Log.i(TAG, "++++++++ add AAB")

            EngineModule.downloadAndInstall(result)
                .doOnNext {
                    Log.i(TAG, "++++++++ adding AAB:${it}")
                }
                .doOnCompleted {
                    Log.i(TAG, "++++++++ add AAB completed")
                }
                .doOnError {
                    Log.e(TAG, "++++++++ downloadAndInstall() failed", it)
                }
        } else {
            Log.i(TAG, "++++++++ Observable.just(100)")
            Observable.just(100)
        }.doOnSubscribe {
            Log.i(TAG, "++++++++ doOnSubscribe")
        }
    }

}