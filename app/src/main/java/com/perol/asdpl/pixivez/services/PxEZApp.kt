/*
 * MIT License
 *
 * Copyright (c) 2020 ultranity
 * Copyright (c) 2019 Perol_Notsfsssf
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE
 */

package com.perol.asdpl.pixivez.services

// import com.google.android.play.core.missingsplits.MissingSplitsManagerFactory
import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.arialyy.annotations.Download
import com.arialyy.aria.core.Aria
import com.arialyy.aria.core.task.DownloadTask
import com.google.gson.Gson
import com.hjq.toast.ToastUtils
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.objects.*
import com.perol.asdpl.pixivez.repository.AppDataRepository
import com.tencent.mmkv.MMKV
import io.reactivex.plugins.RxJavaPlugins
import kotlinx.coroutines.*
import java.io.File
import java.util.*

class PxEZApp : Application() {
    lateinit var pre: SharedPreferences

    @Download.onTaskComplete
    fun taskComplete(task: DownloadTask?) {
        task?.let {
            val extendField = it.extendField
            val illustD = Gson().fromJson(extendField, IllustD::class.java)
            val title = illustD.title
            val sourceFile = File(it.filePath)
            if (sourceFile.isFile) {
                val needCreateFold = pre.getBoolean("needcreatefold", false)
                val name = illustD.userName?.toLegal()
                val targetFile = File(
                    "$storepath/" +
                        (if (R18Folder && sourceFile.name.startsWith("？")) R18FolderPath else "") +
                        if (needCreateFold) "${name}_${illustD.userId}" else "",
                    sourceFile.name.removePrefix("？")
                )
                sourceFile.copyTo(targetFile, overwrite = true)
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(targetFile.path),
                    arrayOf(
                        MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(targetFile.extension)
                    )
                ) { _, _ ->
                    FileUtil.ListLog.add(illustD.id.toInt())
                }
                sourceFile.delete()

                if (ShowDownloadToast) {
                    Toasty.success(
                        this,
                        "${title}${getString(R.string.savesuccess)}",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        // https://developer.android.com/guide/app-bundle/sideload-check#missing_splits
        /*if (BuildConfig.ISGOOGLEPLAY)
            if (MissingSplitsManagerFactory.create(this).disableAppIfMissingRequiredSplits()) {
                // Skip app initialization.
                return
            }*/
        super.onCreate()
        instance = this
        // LeakCanary.install(this);
        pre = PreferenceManager.getDefaultSharedPreferences(this)
        CoroutineScope(Dispatchers.IO).launch {
            AppDataRepository.getUser()
        }
        Aria.init(this)
        Aria.download(this).register()

        Aria.get(this).apply {
            downloadConfig.apply {
//                queueMod=QueueMod.NOW.tag
                maxTaskNum = pre.getString("max_task_num", "2")!!.toInt()
                threadNum = pre.getString("thread_num", "2")!!.toInt()
            }
            appConfig.apply {
                isNotNetRetry = true
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            // Aria.download(this).removeAllTask(true)
            Aria.download(this).allCompleteTask?.forEach {
                if ((System.currentTimeMillis() - it.completeTime) > 10 * 60 * 1000) {
                    Aria.download(this).load(it.id).cancel()
                }
            }
            delay(10000)
            if (pre.getBoolean("resume_unfinished_task", true)
                // && Aria.download(this).allNotCompleteTask?.isNotEmpty()
            ) {
                // Toasty.normal(this, getString(R.string.unfinished_task_title), Toast.LENGTH_SHORT).show()
                Aria.download(this).allNotCompleteTask?.forEach {
                    if (it.state == 0) {
                        Aria.download(this).load(it.id).cancel()
                        Thread.sleep(500)
                        // val illustD = Gson().fromJson(it.str, IllustD::class.java)
                        Aria.download(this).load(it.url)
                            .setFilePath(it.filePath) // 设置文件保存的完整路径
                            .ignoreFilePathOccupy()
                            .setExtendField(it.str)
                            .option(Works.option)
                            .create()
                        Thread.sleep(300)
                    }
                }
            }
        }

        initBugly(this)
        RxJavaPlugins.setErrorHandler {
            Log.e("onRxJavaErrorHandler", "${it.message}")
            it.printStackTrace()
        }
        if (pre.getBoolean("infoCache", true)) {
            MMKV.initialize(this)
        }
        ToastUtils.init(this)
        AppCompatDelegate.setDefaultNightMode(
            pre.getString(
                "dark_mode",
                "-1"
            )!!.toInt()
        )
        animationEnable = pre.getBoolean("animation", false)
        ShowDownloadToast = pre.getBoolean("ShowDownloadToast", true)
        CollectMode = pre.getString("CollectMode", "0")?.toInt() ?: 0
        R18Private = pre.getBoolean("R18Private", true)
        R18Folder = pre.getBoolean("R18Folder", false)
        R18FolderPath = pre.getString("R18FolderPath", "xRestrict/")!!
        TagSeparator = pre.getString("TagSeparator", "#")!!
        storepath = pre.getString(
            "storepath1",
            Environment.getExternalStorageDirectory().absolutePath + File.separator + "PxEz"
        )!!
        saveformat = pre.getString("filesaveformat", "{illustid}({userid})_{title}_{part}{type}")!!
        if (pre.getBoolean("crashreport", true)) {
            CrashHandler.getInstance().init(this)
        }
        locale = LanguageUtil.getLocale() // System locale
        language = pre.getString("language", "-1")?.toIntOrNull()
            ?: LanguageUtil.localeToLang(locale) // try to detect language from system locale if not configured

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                ActivityCollector.collect(activity)
            }

            override fun onActivityStarted(activity: Activity) {
            }

            override fun onActivityResumed(activity: Activity) {
            }

            override fun onActivityPaused(activity: Activity) {
            }

            override fun onActivityStopped(activity: Activity) {
            }

            override fun onActivityPostDestroyed(activity: Activity) {
                super.onActivityPostDestroyed(activity)
                InteractionUtil.onDestory()
            }

            override fun onActivityDestroyed(activity: Activity) {
                ActivityCollector.discard(activity)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                //
            }
        })
    }

    private val Activity.simpleName get() = javaClass.simpleName

    object ActivityCollector {
        @JvmStatic
        private val activityList = mutableListOf<Activity>()

        @JvmStatic
        fun collect(activity: Activity) {
            activityList.add(activity)
        }

        @JvmStatic
        fun discard(activity: Activity) {
            activityList.remove(activity)
        }

        @JvmStatic
        fun recreate() {
            for (i in activityList.size - 1 downTo 0) {
                activityList[i].recreate()
            }
        }
    }

    companion object {
        @JvmStatic
        var storepath = ""

        @JvmStatic
        var R18Folder: Boolean = false

        @JvmStatic
        var R18FolderPath = "xrestrict"

        @JvmStatic
        var saveformat = ""

        @JvmStatic
        var locale: Locale = Locale.SIMPLIFIED_CHINESE

        @JvmStatic
        var language: Int = 0

        @JvmStatic
        var animationEnable: Boolean = false

        @JvmStatic
        var R18Private: Boolean = true

        @JvmStatic
        var ShowDownloadToast: Boolean = true

        @JvmStatic
        var CollectMode: Int = 0

        lateinit var instance: PxEZApp

        @JvmStatic
        var TagSeparator: String = "#"

        private const val TAG = "PxEZApp"
    }
}
