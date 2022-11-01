package com.aureusapps.android.transformlayout.example

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

class UncheckableButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialButton(context, attrs, defStyleAttr) {

    override fun setCheckable(checkable: Boolean) {

    }

    override fun setChecked(checked: Boolean) {

    }

}