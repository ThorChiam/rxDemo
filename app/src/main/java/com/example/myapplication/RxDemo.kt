package com.example.myapplication

import android.util.Log
import rx.Observable
import rx.Subscriber
import java.util.*
import kotlin.collections.ArrayList

object RxDemo {

    const val TAG = "RxDemo"
    const val END_TIME = 10
    var currentIsInstalled: Boolean = false
    var shareIsInstalled: Boolean = false
    var needDownload: Boolean = false
    var loaded: Boolean = false
    private var downloadSubscriber: Subscriber<in Int>? = null
    private var shareDownloadSubscriber: Subscriber<in Int>? = null
    private var packSubscriber: Subscriber<in Int>? = null
    private var combineSubscriber: Subscriber<in Int>? = null
    private var timer1: Timer? = null
    private var timer2: Timer? = null
    private var timer3: Timer? = null
    private var timerTask1: TimerTask? = null
    private var timerTask2: TimerTask? = null
    private var timerTask3: TimerTask? = null
    private var timerProgress1: Int = -2
    private var timerProgress2: Int = -1
    private var timerProgress3: Int = -1

    fun init() {
        loadDynamicModule()
        Log.i(TAG, "init()")
    }

    fun isInstalled(): Boolean {
        val areAllInstalled = shareIsInstalled && currentIsInstalled
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
            shareIsInstalled = true
            shareDownloadSubscriber?.onCompleted()
        }

        fun handleInstallFail(error: Throwable) {
            Log.e(TAG, "share Module: install error($error)")
            shareDownloadSubscriber?.onError(error)
        }

    }

    private fun StartTimer1() {
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

    private fun EndTimer1() {
        timer1?.cancel()
        timerTask1?.cancel()
        timer1 = null
        timerTask1 = null
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

    fun sharePrepareToDownload(result: String) {
        Log.d(TAG, "share prepareToDownload()")
        if (result.contains("s")) {
            StartTimer1()
        } else {
            shareDownloadCallBack.handleInstallFail(NoClassDefFoundError())
        }
    }

    fun shareDownloadAndInstall(result: String): Observable<Int> = Observable.create { subscriber ->
        Log.e(TAG, "share downloadAndInstall")
        shareDownloadSubscriber = subscriber
        if (shareIsInstalled) {
            subscriber.onCompleted()
            return@create
        }
        sharePrepareToDownload(result)
        if (shareIsInstalled) {
            subscriber.onCompleted()
            return@create
        }
    }

    fun downloadAndInstall(result: String): Observable<Int> = Observable.create { subscriber ->
        Observable.concat(downloadAndInstallModules(result), downloadFlutterPackage(result))
            .subscribe(
                {
                    Log.d(TAG, "concat:${it}")
                    if (shareIsInstalled && isInstalled() && needDownload) {
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
            if (!shareIsInstalled) {
                val shareObservable = shareDownloadAndInstall(result)
                shareObservable
                    .doOnNext { progress ->
                        Log.e(TAG, "download FlutterShared in progress:${progress}")
                        sharedProgress = progress
                        val allProgress = (flutterProgress + sharedProgress) / 2
                        combineSubscriber?.onNext(allProgress)
                    }
                    .doOnCompleted {
                        shareIsInstalled = true
                        sharedProgress = END_TIME
                        Log.e(TAG, "download FlutterShared completed")
                        EndTimer1()
                        if (tasks.count() < 2) {
                            combineSubscriber?.onCompleted()
                        }
                        tasks.remove(shareObservable)
                    }
                    .doOnError { error ->
                        Log.e(TAG, "download FlutterShared module failed:${error.localizedMessage}")
                        EndTimer1()
                        topSubscriber.onError(error)
                    }
                    .subscribe()
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
                        Log.e(TAG, "download Flutter module failed:${error.localizedMessage}")
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
                    .subscribe()
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
                }.subscribe(
                    {
                        Log.i(TAG, "Flutter in progress:${flutterProgress}")
                        Log.i(TAG, "Shared in progress:${sharedProgress}")
                        Log.e(TAG, "download Flutter Modules in progress (ALL):${it}")
                        topSubscriber.onNext(it / 2)
                    },
                    {
                        topSubscriber.onError(it)
                    },
                    {
                        topSubscriber.onCompleted()
                    }
                )
            }

            return@create
//        if (sharedModuleInstalled && isInstalled) {
//            subscriber.onCompleted()
//            return@create
//        }
        }

    fun downloadFlutterPackage(result: String): Observable<Int> {
        return Observable.create { subscriber ->
            packSubscriber = subscriber
            needDownload = result.contains("p")
            Log.e(TAG, "downloadFlutterPackage()")
            if (isInstalled()) {
                if (needDownload) {
                    StartTimer3()
                } else {
                    subscriber.onCompleted()
                }
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
        EndTimer1()
        EndTimer2()
        EndTimer3()
        downloadSubscriber?.unsubscribe()
        shareDownloadSubscriber?.unsubscribe()
        packSubscriber?.unsubscribe()
    }

    private fun loadDynamicModule() {
        if (!loaded) {
            loaded = true
            Log.e(TAG, "loadDynamicModule()")
        }
    }
}