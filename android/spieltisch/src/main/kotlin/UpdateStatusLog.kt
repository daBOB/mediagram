package com.mediagram.spieltisch

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import update.AppUpdater
import update.updateLine

/** One log line per change of the updater's status (checked, downloading, ready, refused or failed with its reason), for the box run to read in logcat. */
fun AppUpdater.logStatus(scope: CoroutineScope) {
    scope.launch { status.collect { status -> updateLine(status, System.currentTimeMillis())?.let { Log.i("update", it) } } }
}
