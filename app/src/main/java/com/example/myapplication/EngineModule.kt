package com.example.myapplication

import android.util.Log
import rx.Observable
import rx.Subscriber
import rx.Subscription
import java.util.*
import kotlin.collections.ArrayList

object EngineModule {

    const val TAG = "RxDemo:EngineModule"
    const val END_TIME = 10
    var currentIsInstalled: Boolean = false
    var needDownload: Boolean = false
    var loaded: Boolean = false
    private var downloadSubscriber: Subscriber<in Int>? = null
    private var packSubscriber: Subscriber<in Int>? = null
    private var combineSubscriber: Subscriber<in Int>? = null
    private var timer2: Timer? = null
    private var timer3: Timer? = null
    private var timerTask2: TimerTask? = null
    private var timerTask3: TimerTask? = null
    private var timerProgress2: Int = -1
    private var timerProgress3: Int = -1

    fun init() {
        loadDynamicModule()
        Log.i(TAG, "init()")
    }

    fun isInstalled(): Boolean {
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
        }

        fun handleInstallFail(error: Throwable) {
            Log.e(TAG, "flutter Module: install error($error)")
            downloadSubscriber?.onError(error)
        }

    }

    private fun StartTimer2() {
        if (timer2 == null && timerTask2 == null) {
            timer2 = Timer()
            timerTask2 = object : TimerTask() {
                override fun run() {
                    timerProgress2++
                    if (timerProgress2 >= END_TIME) {
                        downloadCallBack.handleInstallSuccess()
                    } else {
                        downloadCallBack.handleDownloading(timerProgress2)
                    }
                }
            }
            timer2?.schedule(timerTask2, 5000, 5000)
        }
    }

    private fun StartTimer3() {
        if (timer3 == null && timerTask3 == null) {
            timer3 = Timer()
            timerTask3 = object : TimerTask() {
                override fun run() {
                    timerProgress3++
                    if (timerProgress3 >= END_TIME) {
                        packSubscriber?.onCompleted()
                    } else {
                        packSubscriber?.onNext(timerProgress3)
                    }
                }
            }
            timer3?.schedule(timerTask3, 500, 2000)
        }
    }


    private fun EndTimer2() {
        timer2?.cancel()
        timerTask2?.cancel()
        timer2 = null
        timerTask2 = null
    }

    private fun EndTimer3() {
        timer3?.cancel()
        timerTask3?.cancel()
        timer3 = null
        timerTask3 = null
    }

    fun prepareToDownload(result: String) {
        Log.d(TAG, "flutter prepareToDownload()")
        if (result.contains("f")) {
            StartTimer2()
        } else {
            downloadCallBack.handleInstallFail(IndexOutOfBoundsException())
        }
    }

    fun downloadAndInstall(result: String): Observable<Int> = Observable.create { subscriber ->
        Observable.concat(downloadAndInstallModules(result), downloadFlutterPackage(result))
            .subscribe(
                {
                    Log.d(TAG, "concat:${it}")
                    if (ShareModule.isInstalled && isInstalled() && needDownload) {
                        subscriber.onNext((END_TIME + it) / 2)
                    } else {
                        subscriber.onNext(it)
                    }
                },
                {
                    Log.d(TAG, "concat error:${it}")
                    subscriber.onError(it)
                },
                {
                    Log.d(TAG, "concat completed")
                    subscriber.onCompleted()
                }
            )

        return@create
    }

    fun downloadAndInstallModules(result: String): Observable<Int> =
        Observable.create { topSubscriber ->
            var sharedProgress = 0
            var flutterProgress = 0
            val tasks = ArrayList<Observable<Int>>()

            Log.e(TAG, "flutter downloadAndInstall")
            if (!ShareModule.isInstalled) {
                val shareObservable = ShareModule.downloadAndInstall(result)
                shareObservable
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
                tasks.add(shareObservable)
            } else {
                sharedProgress = END_TIME
            }

            if (!isInstalled()) {
                val flutterObservable: Observable<Int> = Observable.create { subscriber ->
                    downloadSubscriber = subscriber
                    prepareToDownload(result)
                }
                flutterObservable
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
                        Log.e(TAG, "download FlutterShared completed")
                        EndTimer2()
                        if (tasks.count() < 2) {
                            combineSubscriber?.onCompleted()
                        }
                        tasks.remove(flutterObservable)
                    }
                tasks.add(flutterObservable)
            } else {
                flutterProgress = END_TIME
            }

            val taskSubscriptions = ArrayList<Subscription>()
            if (tasks.isEmpty()) {
                loadDynamicModule()
                topSubscriber.onCompleted()
            } else {
                for (task in tasks) {
                    taskSubscriptions.add(task.subscribe())
                }
                Observable.create<Int> { subscriber ->
                    combineSubscriber = subscriber
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
                        topSubscriber.onCompleted()
                        unsubscribeTasks(taskSubscriptions)
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
        Log.e(TAG, "downloadFlutterPackage()")
        needDownload = result.contains("p")
        return if (isInstalled()) {
            return if (needDownload) {
                Observable.create { subscriber ->
                    packSubscriber = subscriber
                    Log.e(TAG, "downloadFlutterPackage() start")
                    StartTimer3()
                }
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


    /**
     * result: s - shared | f - flutter | p - pack
     */
    fun startWithResult(result: String): Observable<Int> {
        return downloadAndInstall(result)
            .doOnCompleted {
                Log.i(TAG, "downloadAndInstall - doOnCompleted")
                reset()
            }
    }

    fun reset() {
        ShareModule.reset()
        EndTimer2()
        EndTimer3()
        downloadSubscriber?.unsubscribe()
        packSubscriber?.unsubscribe()
    }

    private fun loadDynamicModule() {
        if (!loaded) {
            loaded = true
            Log.e(TAG, "loadDynamicModule()")
        }
    }
}