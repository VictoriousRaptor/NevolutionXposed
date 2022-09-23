package com.notxx.xposed

import de.robv.android.xposed.callbacks.XC_LoadPackage

interface Hook {
    fun hook(loadPackageParam: XC_LoadPackage.LoadPackageParam)
}