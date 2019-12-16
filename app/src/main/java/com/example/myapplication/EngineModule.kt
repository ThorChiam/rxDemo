package com.example.myapplication

import android.util.Log
import com.example.myapplication.Downloader.END_TIME
import rx.Observable
import rx.Subscriber
import rx.Subscription
import java.util.*
import kotlin.collections.ArrayList

object EngineModule {

    const val TAG = "RxDemo:EngineModule"
    var currentIsInstalled: Boolean = false
    var needDownload: Boolean = false
    var loaded: Boolean = false
    private var downloadSubscriber: Subscriber<in Int>? = null
    private var combineSubscriber: Subscriber<in Int>? = null
    private var timer: Timer? = null
    private var timerTask: TimerTask? = null
    private var timerProgress: Int = -1
    private var packDownloader: PackDownloader? = null

    fun init() {
        loadDynamicModule()
        packDownloader = PackDownloader
        Log.i(TAG, "init()")
    }

    fun isInstalled(): Boolean {
        Log.i(TAG, "ShareModule.isInstalled:${ShareModule.isInstalled}")
        Log.i(TAG, "currentIsInstalled:${currentIsInstalled}")
        val areAllInstalled = ShareModule.isInstalled && currentIsInstalled
        if (areAllInstalled) {
            loadDynamicModule()
        }
        return areAllInstalled
    }

    object downloadCallBack {
        fun handleDownloading(progress: Int) {
            Log.i(TAG, "flutter progress: $progress")
            if (downloadSubscriber != null && downloadSubscriber!!.isUnsubscribed) {
                return
            }
            if (progress <= 0) {
                downloadSubscriber?.onNext(0)
            } else {
                downloadSubscriber?.onNext(progress)
            }
        }

        fun handleInstallSuccess() {
            Log.i(TAG, "flutter Module: install success")
            currentIsInstalled = true
            init()
            downloadSubscriber?.onCompleted()
            EndTimer2()
        }

        fun handleInstallFail(error: Throwable) {
            Log.e(TAG, "flutter Module: install error($error)")
            downloadSubscriber?.onError(error)
            EndTimer2()
        }

    }

    private fun StartTimer2() {
        if (timer == null && timerTask == null) {
            timer = Timer()
            timerTask = object : TimerTask() {
                override fun run() {
                    timerProgress++
                    if (timerProgress >= END_TIME) {
                        downloadCallBack.handleInstallSuccess()
                    } else {
                        downloadCallBack.handleDownloading(timerProgress)
                    }
                }
            }
            timer?.schedule(timerTask, 5000, 5000)
        }
    }

    private fun EndTimer2() {
        timer?.cancel()
        timerTask?.cancel()
        timer = null
        timerTask = null
    }

    fun prepareToDownload(result: String) {
        Log.d(TAG, "flutter prepareToDownload()")
        if (result.contains("f")) {
            StartTimer2()
        } else {
            downloadCallBack.handleInstallFail(IndexOutOfBoundsException())
        }
    }

    /**
     * result: s - shared | f - flutter | p - pack
     */
    fun downloadAndInstall(result: String): Observable<Int> = Observable.create { topSubscriber ->
        var sharedProgress = 0
        var flutterProgress = 0
        val tasks = ArrayList<Observable<Int>>()
        val taskSubscriptions = ArrayList<Subscription>()

        Log.e(TAG, "flutter downloadAndInstall")
        if (!ShareModule.isInstalled) {
            val shareObservable = ShareModule.downloadAndInstall(result)
            taskSubscriptions.add(shareObservable
                .doOnNext { progress ->
                    Log.e(TAG, "download FlutterShared in progress:${progress}")
                    sharedProgress = progress
                    val allProgress = (flutterProgress + sharedProgress) / 2
                    combineSubscriber?.onNext(allProgress)
                }
                .doOnCompleted {
                    sharedProgress = END_TIME
                    Log.e(TAG, "download FlutterShared completed")
                    if (tasks.count() < 2) {
                        combineSubscriber?.onCompleted()
                    }
                    tasks.remove(shareObservable)
                }
                .doOnError { error ->
                    Log.e(TAG, "download FlutterShared module failed", error)
                    topSubscriber.onError(error)
                }
                .subscribe())
            tasks.add(shareObservable)
        } else {
            sharedProgress = END_TIME
        }

        if (!isInstalled()) {
            val flutterObservable: Observable<Int> = Observable.create { subscriber ->
                downloadSubscriber = subscriber
                prepareToDownload(result)
            }
            taskSubscriptions.add(flutterObservable
                .doOnNext { progress ->
                    Log.e(TAG, "download Flutter module in progress:${progress}")
                    flutterProgress = progress
                    val allProgress = (flutterProgress + sharedProgress) / 2
                    combineSubscriber?.onNext(allProgress)
                }
                .doOnError { error ->
                    Log.e(TAG, "download Flutter module failed", error)
                    EndTimer2()
                    topSubscriber.onError(error)
                }
                .doOnCompleted {
                    flutterProgress = END_TIME
                    Log.e(TAG, "download Flutter module completed")
                    EndTimer2()
                    if (tasks.count() < 2) {
                        combineSubscriber?.onCompleted()
                    }
                    tasks.remove(flutterObservable)
                }
                .subscribe())
            tasks.add(flutterObservable)
        } else {
            flutterProgress = END_TIME
        }

        if (tasks.isEmpty()) {
            loadDynamicModule()
            topSubscriber.onCompleted()
        } else {
            Observable.create<Int> { subscriber ->
                combineSubscriber = subscriber
//                    for (task in tasks) {
//                        taskSubscriptions.add(task.subscribe())
//                    }
            }.subscribe(
                {
                    Log.i(TAG, "Flutter in progress:${flutterProgress}")
                    Log.i(TAG, "Shared in progress:${sharedProgress}")
                    Log.e(TAG, "download Flutter Modules in progress (ALL):${it}")
                    topSubscriber.onNext(it / 2)
                },
                {
                    topSubscriber.onError(it)
                    unsubscribeTasks(taskSubscriptions)
                },
                {
                    taskSubscriptions.add(downloadFlutterPackage(result)
                        .subscribe(
                            {
                                Log.d(TAG, "downloadFlutterPackage:${it}")
                                if (ShareModule.isInstalled && isInstalled() && needDownload) {
                                    topSubscriber.onNext((END_TIME + it) / 2)
                                } else {
                                    topSubscriber.onNext(it)
                                }
                            },
                            {
                                Log.d(TAG, "downloadFlutterPackage error:${it}")
                                topSubscriber.onError(it)
                                unsubscribeTasks(taskSubscriptions)
                            },
                            {
                                Log.d(TAG, "downloadFlutterPackage completed")
                                topSubscriber.onCompleted()
                                unsubscribeTasks(taskSubscriptions)
                            }
                        ))
                }
            )
        }

        return@create
    }

    private fun unsubscribeTasks(taskSubscriptions: ArrayList<Subscription>) {
        for (subscription in taskSubscriptions) {
            if (!subscription.isUnsubscribed) {
                subscription.unsubscribe()
            }
        }
    }

    fun downloadFlutterPackage(result: String): Observable<Int> {
        Log.e(TAG, "downloadFlutterPackage() - isInstalled():${isInstalled()}")
        Log.e(TAG, "downloadFlutterPackage() - packDownloader:${packDownloader}")
        needDownload = result.contains("p")
        return if (isInstalled()) {
            return if (needDownload && packDownloader != null) {
                packDownloader!!.downloadPacks()
            } else {
                Observable.just(100)
            }
        } else {
            Observable.create { subscriber ->
                Log.e(TAG, "DynamicFlutterModule not initialised.")
                subscriber.onError(NullPointerException())
            }
        }
    }

    fun reset() {
        ShareModule.reset()
        PackDownloader.reset()
        EndTimer2()
        downloadSubscriber?.unsubscribe()
    }

    private fun loadDynamicModule() {
        if (!loaded) {
            loaded = true
            Log.e(TAG, "loadDynamicModule()")
        }
    }
}