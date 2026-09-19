package us.i3u.hermesstudio

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generated avatar has to be the same picture on the phone and in the
 * browser, so the expectations are not written by hand: they are the output of
 * `boring-avatars-vanilla` itself, captured from
 * `node_modules/boring-avatars-vanilla/dist/index.js` with
 * `boring({ name, variant: 'beam', size: 144 })` — the exact call
 * `ProfileAvatar.vue` makes — and stored in
 * `app/src/test/resources/boring-avatars-beam.json`.
 *
 * The one thing that cannot match is the mask id: the library counts up and
 * appends a timestamp, so both sides are normalised to the literal `AVATAR`
 * before they are compared. Everything else — colours, transforms, the shape
 * of the mouth, even the whitespace — is compared byte for byte.
 */
class BoringAvatarTest {

    private val fixtures: JSONObject by lazy {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream("boring-avatars-beam.json")) {
            "boring-avatars-beam.json is missing from the test resources"
        }.bufferedReader().use { it.readText() }
        JSONObject(text).getJSONObject("avatars")
    }

    private fun normalise(svg: String) = svg.replace(Regex("boring-avatar-\\d+"), "AVATAR")

    @Test
    fun `every fixture seed draws the avatar the web library draws`() {
        val seeds = fixtures.keys().asSequence().toList()
        assertTrue("the fixture file should carry a spread of seeds", seeds.size >= 12)
        for (seed in seeds) {
            assertEquals(
                "seed \"$seed\" does not match boring-avatars-vanilla",
                fixtures.getString(seed),
                normalise(BoringAvatar.beam(seed, size = 144)),
            )
        }
    }

    /**
     * The fixtures are only proof of a match if they actually exercise both
     * branches the variant can take; a set that happened to be all closed
     * mouths would pass while the open-mouth path stayed untested.
     */
    @Test
    fun `the fixtures cover both mouth shapes and both wrapper shapes`() {
        val drawn = fixtures.keys().asSequence().map { BoringAvatar.beam(it, size = 144) }.toList()
        assertTrue("no open mouth in the fixtures", drawn.any { it.contains("c2 1 4 1 6 0") })
        assertTrue("no closed mouth in the fixtures", drawn.any { it.contains("a1,0.75 0 0,0 10,0") })
        assertTrue("no circular wrapper in the fixtures", drawn.any { it.contains("rx=\"36\"") })
        assertTrue("no rounded-square wrapper in the fixtures", drawn.any { it.contains("rx=\"6\"") })
        assertTrue("no white face in the fixtures", drawn.any { it.contains("fill=\"#FFFFFF\"") })
        assertTrue("no black face in the fixtures", drawn.any { it.contains("fill=\"#000000\"") })
    }

    /** `hashCode` in the library: the 32-bit `h * 31 + c` walk, then an absolute value. */
    @Test
    fun `the seed hash matches the library`() {
        assertEquals(0L, BoringAvatar.hash(""))
        assertEquals(97L, BoringAvatar.hash("a"))
        assertEquals(2161834L, BoringAvatar.hash("Ekko"))
        // A seed long enough to wrap the 32-bit accumulator several times over.
        assertEquals(1501792902L, BoringAvatar.hash("a-fairly-long-profile-seed-value"))
    }

    /** Arabic seeds hash over UTF-16 code units, exactly as `charCodeAt` does. */
    @Test
    fun `an arabic seed is hashed over utf16 code units`() {
        assertEquals(fixtures.getString("أحمد"), normalise(BoringAvatar.beam("أحمد", size = 144)))
        assertNotEquals(BoringAvatar.hash("أحمد"), BoringAvatar.hash("احمد"))
    }

    @Test
    fun `the face colour is the contrast of the wrapper colour`() {
        assertEquals("#000000", BoringAvatar.contrast("#F0AB3D"))
        assertEquals("#FFFFFF", BoringAvatar.contrast("#146A7C"))
        assertEquals("#FFFFFF", BoringAvatar.contrast("C20D90"))
        for (color in BoringAvatar.DEFAULT_COLORS) {
            assertTrue(BoringAvatar.contrast(color) in setOf("#000000", "#FFFFFF"))
        }
    }

    @Test
    fun `the same seed always draws the same avatar`() {
        assertEquals(BoringAvatar.beam("twuijri"), BoringAvatar.beam("twuijri"))
        assertNotEquals(BoringAvatar.beam("twuijri"), BoringAvatar.beam("twuijri2"))
    }

    /** The size is the only thing a caller changes; it must not disturb the drawing. */
    @Test
    fun `size changes only the svg dimensions`() {
        val small = BoringAvatar.beam("Hermes", size = 24)
        val large = BoringAvatar.beam("Hermes", size = 144)
        assertTrue(small.contains("width=\"24px\""))
        assertTrue(large.contains("width=\"144px\""))
        assertEquals(
            large.replace("144px", "24px"),
            small,
        )
    }
}
