package com.electricdreams.numo.ui.animation

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.electricdreams.numo.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NfcPaymentAnimationViewTest {

    private lateinit var context: Context
    private lateinit var activity: Activity
    private lateinit var view: NfcPaymentAnimationView
    private val results = mutableListOf<Boolean>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        view = NfcPaymentAnimationView(activity)
        activity.setContentView(view)
        view.setOnResultDisplayedListener { results += it }
    }

    @After
    fun tearDown() {
        setAnimatorDurationScale(1f)
    }

    private fun setAnimatorDurationScale(scale: Float) {
        // Hidden framework API; Robolectric runs the real ValueAnimator.
        ValueAnimator::class.java
            .getMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    private fun runAnimations() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
    }

    private fun layOut() {
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(view.width > 0 && view.height > 0)
    }

    /** Draws the view and returns the colour at [dy] badge radii above the badge centre. */
    private fun colorAboveCentre(dy: Float): Int {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val radius = context.resources.getDimension(R.dimen.nfc_indicator_size) / 2f
        return bitmap.getPixel(view.width / 2, (view.height / 2f - dy * radius).toInt())
    }

    private fun color(res: Int) = ContextCompat.getColor(context, res)

    private val cornerOfView = { RectF(0f, 0f, 1f, 1f) }

    @Test
    fun `success is reported once the reveal finishes`() {
        view.showSuccess()
        assertTrue(results.isEmpty())

        runAnimations()

        assertEquals(listOf(true), results)
    }

    @Test
    fun `error is reported as not successful`() {
        view.showError()
        runAnimations()

        assertEquals(listOf(false), results)
    }

    @Test
    fun `a second result while one is showing is ignored`() {
        view.showSuccess()
        view.showError()
        runAnimations()

        assertEquals(listOf(true), results)
    }

    @Test
    fun `reset before the result settles does not report it`() {
        view.showSuccess()
        view.reset()
        runAnimations()

        assertTrue(results.isEmpty())
    }

    @Test
    fun `after reset a new result can be shown`() {
        view.showError()
        runAnimations()
        view.reset()

        view.showSuccess()
        runAnimations()

        assertEquals(listOf(false, true), results)
    }

    @Test
    fun `with animations removed the result is reported without animating`() {
        setAnimatorDurationScale(0f)
        assertTrue(ReducedMotion.isEnabled(context))

        view.showSuccess()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(true), results)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `success badge is white with the green reveal around it`() {
        layOut()
        view.showSuccess()
        runAnimations()

        // Inside the badge, clear of the check stroke
        assertEquals(Color.WHITE, colorAboveCentre(0.6f))
        assertEquals(color(R.color.color_nfc_success), colorAboveCentre(1.5f))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `error badge stays red over the app background`() {
        layOut()
        view.showError()
        runAnimations()

        assertEquals(color(R.color.color_error), colorAboveCentre(0.6f))
        assertEquals(Color.TRANSPARENT, colorAboveCentre(1.5f))
    }

    @Test
    fun `reveal cover actions wait for the reveal to reach them`() {
        layOut()
        var runs = 0
        view.doOnRevealCovering(cornerOfView) { runs++ }

        view.showSuccess()
        assertEquals(0, runs)

        runAnimations()
        assertEquals(1, runs)
    }

    @Test
    fun `text counts as covered once its glyphs are, not its whole row`() {
        val text = TextView(activity).apply {
            text = "1 sat"
            gravity = Gravity.CENTER
        }
        val root = FrameLayout(activity)
        (view.parent as ViewGroup).removeView(view)
        root.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(
            text,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        activity.setContentView(root)
        layOut()
        val order = mutableListOf<String>()
        val row = {
            RectF(text.left.toFloat(), text.top.toFloat(), text.right.toFloat(), text.bottom.toFloat())
        }
        view.doOnRevealCovering(row) { order += "row" }
        view.doOnRevealCovering(text) { order += "glyphs" }

        view.showSuccess()
        runAnimations()

        assertEquals(listOf("glyphs", "row"), order)
    }

    @Test
    fun `with animations removed cover actions run straight away`() {
        setAnimatorDurationScale(0f)
        layOut()
        var runs = 0
        view.doOnRevealCovering(cornerOfView) { runs++ }

        view.showSuccess()

        assertEquals(1, runs)
    }

    @Test
    fun `reset drops cover actions that have not run`() {
        layOut()
        var runs = 0
        view.doOnRevealCovering(cornerOfView) { runs++ }
        view.showSuccess()

        view.reset()
        runAnimations()

        assertEquals(0, runs)
    }

    @Test
    fun `cover actions never run for an error`() {
        layOut()
        var runs = 0
        view.doOnRevealCovering(cornerOfView) { runs++ }

        view.showError()
        runAnimations()

        assertEquals(0, runs)
    }

    @Test
    fun `reduced motion is off by default`() {
        assertFalse(ReducedMotion.isEnabled(context))
    }
}
