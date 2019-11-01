package com.example.meeera.cutsong.Utils

import android.content.Context
import android.util.DisplayMetrics

class UtilDpToPixel {
    companion object {
        @JvmStatic fun convertDpToPixel(dp: Float, context: Context): Float {
            val resources = context.resources
            val metrics = resources.displayMetrics
            return dp * (metrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
        }
    }
}