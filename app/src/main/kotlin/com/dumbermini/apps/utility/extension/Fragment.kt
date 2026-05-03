package com.dumbermini.apps.utility.extension

import androidx.fragment.app.Fragment
import com.dumbermini.apps.MainActivity

inline val Fragment.mainActivity: MainActivity
    get() = requireActivity() as MainActivity
