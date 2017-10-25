package com.example.meeera.cutsong.Fragment

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.meeera.cutsong.R

/**
 * Created by meeera on 24/10/17.
 */
class Recorder() : Fragment() {
    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var view = inflater?.inflate(R.layout.record_fragment, container, false)
        return view
    }
}