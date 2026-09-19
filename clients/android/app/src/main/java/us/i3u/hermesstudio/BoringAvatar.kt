package us.i3u.hermesstudio

import kotlin.math.abs

/**
 * The `beam` variant of boring-avatars, ported from the library Core Hub runs
 * in the browser (`node_modules/boring-avatars-vanilla/dist/index.js`, MIT) so
 * that a seed draws the very same avatar on the phone as it does on the web.
 *
 * `packages/client/src/components/hermes/profiles/ProfileAvatar.vue` calls the
 * library with only `name`, `variant: 'beam'` and `size`, so the palette, the
 * `title` flag and the `square` flag all stay at their defaults here too.
 *
 * Everything below mirrors the JavaScript literally, including the whitespace
 * of the emitted markup: `BoringAvatarTest` compares this output against
 * fixtures captured from the library itself.
 */
object BoringAvatar {

    /** The library default palette, which the web never overrides. */
    val DEFAULT_COLORS: List<String> =
        listOf("#92A1C6", "#146A7C", "#F0AB3D", "#C271B4", "#C20D90")

    /** The beam variant draws on a 36×36 canvas. */
    private const val SIZE = 36

    /**
     * The values the variant derives from the seed. Kept as a type of its own
     * because the tests assert on them directly, and because the arithmetic is
     * the part that has to match the web exactly.
     */
    data class Beam(
        val wrapperColor: String,
        val faceColor: String,
        val backgroundColor: String,
        val wrapperTranslateX: Int,
        val wrapperTranslateY: Int,
        val wrapperRotate: Int,
        /** 0, 1 or 2 — the tenths added to a scale of 1. */
        val wrapperScaleTenths: Int,
        val isMouthOpen: Boolean,
        val isCircle: Boolean,
        val eyeSpread: Int,
        val mouthSpread: Int,
        val faceRotate: Int,
        /**
         * Halved in the library by a plain `/ 2`, which in JavaScript keeps the
         * half: an odd wrapper offset gives the face a `.5` of its own.
         */
        val faceTranslateX: Double,
        val faceTranslateY: Double,
    )

    /**
     * `hashCode` in the library: a 32-bit `h * 31 + c` walk over UTF-16 code
     * units, then an absolute value. The absolute value is taken as a Long
     * because JavaScript's `Math.abs` on -2^31 yields 2147483648, which does
     * not fit back into a 32-bit integer.
     */
    fun hash(name: String): Long {
        var value = 0
        for (index in name.indices) {
            val code = name[index].code
            value = (value shl 5) - value + code
        }
        return abs(value.toLong())
    }

    /** `getDigit`: the nth decimal digit, counting from the units. */
    private fun digit(number: Long, position: Int): Int {
        var divisor = 1L
        repeat(position) { divisor *= 10 }
        return ((number / divisor) % 10).toInt()
    }

    /** `getBoolean`: true when that digit is even. */
    private fun bool(number: Long, position: Int): Boolean = digit(number, position) % 2 == 0

    /**
     * `getUnit`: `number % range`, made negative when the digit at `position`
     * is even. `position` 0 means "always positive", matching the library's
     * falsy-index check.
     */
    private fun unit(number: Long, range: Int, position: Int = 0): Int {
        val value = (number % range).toInt()
        return if (position != 0 && digit(number, position) % 2 == 0) -value else value
    }

    /** `getRandomColor`. */
    private fun colorAt(number: Long, colors: List<String>): String =
        colors[(number % colors.size).toInt()]

    /**
     * `getContrast`: black on a light wrapper, white on a dark one, using the
     * same luminance weights the library uses.
     */
    fun contrast(hexColor: String): String {
        val hex = hexColor.removePrefix("#")
        val red = hex.substring(0, 2).toInt(16)
        val green = hex.substring(2, 4).toInt(16)
        val blue = hex.substring(4, 6).toInt(16)
        return if ((red * 299 + green * 587 + blue * 114) / 1000.0 >= 128) "#000000" else "#FFFFFF"
    }

    /** `generateData` for the beam variant. */
    fun data(name: String, colors: List<String> = DEFAULT_COLORS): Beam {
        val number = hash(name)
        val wrapperColor = colorAt(number, colors)

        val rawX = unit(number, 10, 1)
        val translateX = if (rawX < 5) rawX + SIZE / 9 else rawX
        val rawY = unit(number, 10, 2)
        val translateY = if (rawY < 5) rawY + SIZE / 9 else rawY

        return Beam(
            wrapperColor = wrapperColor,
            faceColor = contrast(wrapperColor),
            backgroundColor = colorAt(number + 13, colors),
            wrapperTranslateX = translateX,
            wrapperTranslateY = translateY,
            wrapperRotate = unit(number, 360),
            wrapperScaleTenths = unit(number, SIZE / 12),
            isMouthOpen = bool(number, 2),
            isCircle = bool(number, 1),
            eyeSpread = unit(number, 5),
            mouthSpread = unit(number, 3),
            faceRotate = unit(number, 10, 3),
            faceTranslateX = if (translateX > SIZE / 6) translateX / 2.0 else unit(number, 8, 1).toDouble(),
            faceTranslateY = if (translateY > SIZE / 6) translateY / 2.0 else unit(number, 7, 2).toDouble(),
        )
    }

    /**
     * JavaScript prints `1` for a whole number and `1.2` otherwise; the scale
     * is always 1 plus nought, one or two tenths, so this covers every case.
     */
    private fun scaleText(tenths: Int): String = if (tenths == 0) "1" else "1.$tenths"

    /**
     * JavaScript drops a trailing `.0` — and prints `-0` as `0` — when it turns
     * a number into text, which is what the transforms rely on.
     */
    private fun numberText(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString()
        else String.format(java.util.Locale.ROOT, "%s", value)

    /**
     * The mask needs an id that is unique inside one document. The library
     * counts up and appends a timestamp; a seed-derived id is stable instead,
     * which keeps a redrawn avatar byte-identical to the cached one.
     */
    fun idFor(name: String): String = "boring-avatar-${hash(name)}"

    /** The SVG the web renders for this seed, whitespace included. */
    fun beam(
        name: String,
        size: Int = 40,
        colors: List<String> = DEFAULT_COLORS,
        id: String = idFor(name),
    ): String {
        val data = data(name, colors)
        val half = SIZE / 2
        val mouth = if (data.isMouthOpen) {
            """<path d="M15 ${19 + data.mouthSpread}c2 1 4 1 6 0" stroke="${data.faceColor}" fill="none" stroke-linecap="round" />"""
        } else {
            """<path d="M13,${19 + data.mouthSpread} a1,0.75 0 0,0 10,0" fill="${data.faceColor}" />"""
        }
        val cornerRadius = if (data.isCircle) SIZE else SIZE / 6
        // The library emits `  ${title ? "<title>…</title>" : ""}` on its own
        // line; the web never asks for a title, so the line is two spaces.
        val title = ""

        return """<svg
  viewBox="0 0 $SIZE $SIZE"
  fill="none"
  role="img"
  xmlns="http://www.w3.org/2000/svg"
  width="${size}px"
  height="${size}px"
>
  $title
  <mask id="$id" maskUnits="userSpaceOnUse" x="0" y="0" width="$SIZE" height="$SIZE">
    <rect width="$SIZE" height="$SIZE" rx="${SIZE * 2}" fill="#FFFFFF" />
  </mask>
  <g mask="url(#$id)">
    <rect width="$SIZE" height="$SIZE" fill="${data.backgroundColor}" />
    <rect
      x="0"
      y="0"
      width="$SIZE"
      height="$SIZE"
      transform="translate(${data.wrapperTranslateX} ${data.wrapperTranslateY}) rotate(${data.wrapperRotate} $half $half) scale(${scaleText(data.wrapperScaleTenths)})"
      fill="${data.wrapperColor}"
      rx="$cornerRadius"
    />
    <g transform="translate(${numberText(data.faceTranslateX)} ${numberText(data.faceTranslateY)}) rotate(${data.faceRotate} $half $half)">
      $mouth
      <rect x="${14 - data.eyeSpread}" y="14" width="1.5" height="2" rx="1" stroke="none" fill="${data.faceColor}" />
      <rect x="${20 + data.eyeSpread}" y="14" width="1.5" height="2" rx="1" stroke="none" fill="${data.faceColor}" />
    </g>
  </g>
</svg>"""
    }
}
