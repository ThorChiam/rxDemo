package com.example.myapplication

import android.util.Log
import rx.Observable

object Downloader {
    const val TAG = "RxDemo:Downloader"

    fun download(result: String): Observable<Int> {
        Log.i(TAG, "++++++++ download(result = ${result})")
        val tasks = ArrayList<Observable<Int>>()
        Log.i(TAG, "++++++++ EngineModule.isInstalled():${EngineModule.isInstalled()}")
        if (!EngineModule.isInstalled()) {
            // 没有安装先安装
            Log.i(TAG, "++++++++ add AAB")

            tasks.add(EngineModule.downloadAndInstall(result)
                .doOnCompleted {
                    Log.i(TAG, "++++++++ add AAB completed")
                    tasks.removeAt(0)
                }
                .doOnError {
                    Log.e(TAG, "++++++++ EngineModule.downloadAndInstall() failed", it)
                    if (EngineModule.isInstalled() && EngineModule.needDownload) {
                        // 如果FlutterShell里的资源下载失败了，尝试从业务层直接调用下载的方法
                        EngineModule.init()
                        tasks.add(EngineModule.downloadFlutterPackage(result)
                            .doOnCompleted {
                                tasks.removeAt(0)
                            })
                    }
                })
        }


        return if (tasks.isEmpty()) {
            Log.i(TAG, "++++++++ tasks.isEmpty()=${tasks.isEmpty()}")
            Observable.just(100)
        } else {
            Log.i(TAG, "++++++++ tasks.count()=${tasks.count()}")
            Observable.concat(tasks)
        }.doOnSubscribe {
            Log.i(TAG, "++++++++ doOnSubscribe")
        }
    }

}