package us.i3u.hermesstudio

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AvatarTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Matching the web generator byte for byte is `BoringAvatarTest`, a plain
     * JVM test against fixtures taken from the library itself. What a device
     * adds is the half that cannot run on the JVM: that androidsvg can parse
     * the markup and that the result is a real picture.
     */
    @Test
    fun theGeneratorProducesMarkupAndroidSvgCanParse() {
        listOf("manager", "default", "barq", "فهد").forEach { seed ->
            val markup = BoringAvatar.beam(seed, size = 144)
            assertTrue(seed, markup.startsWith("<svg"))
            assertNotNull(seed, com.caverock.androidsvg.SVG.getFromString(markup))
        }
    }

    @Test
    fun avatarRendersToAPicture() = runBlocking {
        val profile = "render-check"
        Avatars.ensure(context, profile, null)

        val drawn = Avatars.of(profile)
        assertNotNull("no bitmap was produced", drawn)

        // A blank canvas would mean the SVG parsed to nothing. The beam design
        // is deliberately flat — a ground, a wrapper and a face — so three
        // distinct colours is the honest floor, not a dozen.
        val bitmap = drawn!!.asAndroidBitmap()
        val colours = buildSet {
            for (x in 0 until bitmap.width step 8) {
                for (y in 0 until bitmap.height step 8) add(bitmap.getPixel(x, y))
            }
        }
        assertTrue("the avatar came out flat: ${colours.size} colour(s)", colours.size >= 3)
    }

    @Test
    fun avatarIsCachedOnDisk() = runBlocking {
        val profile = "cache-check"
        Avatars.ensure(context, profile, null)

        val cached = File(context.filesDir, "avatars").listFiles().orEmpty()
        assertTrue("nothing was written to files/avatars", cached.any { it.name.endsWith(".png") })
    }
}
