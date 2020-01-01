package org.droidplanner.android.fragments.widget.diagnostics

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import java.lang.Exception

/**
 * Created by Fredia Huya-Kouadio on 9/15/15.
 */
public class DiagnosticViewAdapter(val context: Context, fm: FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    private val viewTitles = arrayOf(
            context.getText(EkfStatusViewer.LABEL_ID),
            context.getText(EkfFlagsViewer.LABEL_ID),
            context.getText(VibrationViewer.LABEL_ID)
    )

    override fun getCount() = 3

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> EkfStatusViewer()
            1 -> EkfFlagsViewer()
            2 -> VibrationViewer()
            else -> throw Exception("Illegal argument exception! position is out of range")
        }
    }

    override fun getPageTitle(position: Int): CharSequence {
        return viewTitles.get(position)
    }

    public fun setViewTitles(position: Int, title: CharSequence){
        viewTitles.set(position, title)
    }
}