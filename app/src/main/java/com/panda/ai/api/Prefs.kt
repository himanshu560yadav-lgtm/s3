package com.panda.ai.api

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

fun Context.appPrefs(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
