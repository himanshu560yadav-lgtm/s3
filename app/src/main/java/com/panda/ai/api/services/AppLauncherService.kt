package com.panda.ai.api.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object AppLauncherService {

    fun openApp(context: Context, appName: String): String {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(main, 0)
        val match = apps.firstOrNull { it.loadLabel(pm).toString().equals(appName, true) }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().contains(appName, true) }
        return if (match != null) {
            val launch = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                `package` = match.activityInfo.packageName
                setClassName(match.activityInfo.packageName, match.activityInfo.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launch)
            "Opened $appName"
        } else {
            "Could not find app: $appName"
        }
    }

    fun openPackage(context: Context, packageName: String): String {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opened $packageName"
        } else "Could not open package: $packageName"
    }

    fun openUrl(context: Context, url: String): String {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Opened $url"
    }
}
