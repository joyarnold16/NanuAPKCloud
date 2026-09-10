package com.example.llama

import android.view.LayoutInflater
import android.view.View
import androidx.core.widget.NestedScrollView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlusMenuLayoutTest {
    @Test fun lastOptionRemainsReachableInShortWindowWithTaskbar() {
        val menu = LayoutInflater.from(RuntimeEnvironment.getApplication())
            .inflate(R.layout.sheet_plus_menu, null) as NestedScrollView
        menu.setPadding(0, 24, 0, 64)
        menu.measure(
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY)
        )
        menu.layout(0, 0, 600, 280)
        assertTrue("Short menu must scroll", menu.canScrollVertically(1))
        // Use the measured content extent; Int.MAX_VALUE overflows Android's clamp arithmetic.
        menu.scrollTo(0, menu.getChildAt(0).height)
        val last = menu.findViewById<View>(R.id.plus_attach)
        val lastBottom = menu.getChildAt(0).top + last.bottom - menu.scrollY
        assertTrue("Attach File must clear the taskbar", lastBottom <= menu.height - menu.paddingBottom)
        assertTrue("Attach File must be visible", lastBottom > menu.paddingTop)
    }
}
