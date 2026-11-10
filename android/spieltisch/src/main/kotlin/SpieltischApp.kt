package com.mediagram.spieltisch

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the one [NightSession] every screen of this process shares. */
class SpieltischApp : Application() {
    val night: NightSession by lazy {
        NightSession(NativeSpieltischCore, NsdGameNightAnnouncer(this), CoroutineScope(SupervisorJob() + Dispatchers.Default), filesDir.path)
    }
}
