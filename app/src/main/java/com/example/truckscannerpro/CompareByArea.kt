package com.example.truckscannerpro

import android.util.Size

class CompareByArea : Comparator<Size> {
    override fun compare(p0: Size?, p1: Size?): Int {
        return p0!!.height*p0.width - p1!!.height*p1.width
    }

}