package com.femi.gallery.domain.clip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Checks the tokenizer against the original: the expected numbers below were produced by OpenAI's
 * `simple_tokenizer.py` (with the same vocabulary file the app ships), not by this code.
 */
class ClipTokenizerTest {

    private val tokenizer: ClipTokenizer by lazy {
        // The unit tests run with the app module as the working directory (or the repository root).
        val candidates = listOf("src/main/assets/clip/bpe_simple_vocab_16e6.txt.gz", "app/src/main/assets/clip/bpe_simple_vocab_16e6.txt.gz")
        val file = candidates.map { File(it) }.first { it.exists() }
        ClipTokenizer(GZIPInputStream(file.inputStream()).bufferedReader().readText())
    }

    private val golden: List<Pair<String, IntArray>> = listOf(
        "\u201cquoted\u201d text and \u2018single\u2019" to intArrayOf(49406, 257, 27706, 257, 4160, 537, 568, 17005, 262, 49407),
        "\ufb01sh and \ufb02ower" to intArrayOf(49406, 2759, 537, 4055, 49407),
        "\uff24\uff2f\uff27 \uff11\uff12\uff13" to intArrayOf(49406, 1929, 272, 273, 274, 49407),
        "a\u00a0non\u2011breaking\u2014dash" to intArrayOf(49406, 320, 3353, 728, 495, 2755, 2005, 10206, 49407),
        "caf\u00e9 vs cafe\u0301" to intArrayOf(49406, 15304, 1547, 15304, 49407),
        "a photo of a dog" to intArrayOf(49406, 320, 1125, 539, 320, 1929, 49407),
        "dog" to intArrayOf(49406, 1929, 49407),
        "Dog on the BEACH" to intArrayOf(49406, 1929, 525, 518, 2117, 49407),
        "sunset over the mountains" to intArrayOf(49406, 3424, 962, 518, 5873, 49407),
        "a red car parked near a tall building" to intArrayOf(49406, 320, 736, 1615, 16487, 2252, 320, 7771, 2307, 49407),
        "birthday cake with candles" to intArrayOf(49406, 1166, 2972, 593, 15472, 49407),
        "  extra   spaces   here  " to intArrayOf(49406, 4231, 9006, 763, 49407),
        "it's a dog's life, isn't it?" to intArrayOf(49406, 585, 568, 320, 1929, 568, 970, 267, 2923, 713, 585, 286, 49407),
        "I'll say they've gone; we'd see" to intArrayOf(49406, 328, 1342, 1451, 889, 1200, 3601, 282, 649, 1896, 862, 49407),
        "wedding 2019 in lagos" to intArrayOf(49406, 3101, 273, 271, 272, 280, 530, 12728, 49407),
        "café au lait" to intArrayOf(49406, 15304, 2566, 572, 585, 49407),
        "naïve façade" to intArrayOf(49406, 1097, 35689, 563, 778, 10067, 1928, 49407),
        "日本の桜" to intArrayOf(49406, 39121, 44353, 21575, 162, 94, 506, 49407),
        "photo of 3 kids playing football" to intArrayOf(49406, 1125, 539, 274, 1911, 1629, 1882, 49407),
        "a person's hand holding a coffee cup" to intArrayOf(49406, 320, 2533, 568, 2463, 5050, 320, 2453, 1937, 49407),
        "snow-covered pine trees & a frozen lake" to intArrayOf(49406, 2583, 268, 5603, 7374, 4682, 261, 320, 8507, 2553, 49407),
        "100% pure & simple" to intArrayOf(49406, 272, 271, 271, 260, 5979, 261, 4129, 49407),
        "Mountain... no, wait: a volcano!" to intArrayOf(49406, 3965, 678, 871, 267, 1885, 281, 320, 14581, 256, 49407),
        "a b c d e f g h i j k l m n o p q r s t u v w x y z" to intArrayOf(49406, 320, 321, 322, 323, 324, 325, 326, 327, 328, 329, 330, 331, 332, 333, 334, 335, 336, 337, 338, 339, 340, 341, 342, 343, 344, 345, 49407),
        "x" to intArrayOf(49406, 343, 49407),
        "" to intArrayOf(49406, 49407),
        "hello world" to intArrayOf(49406, 3306, 1002, 49407),
        "UPPER lower MiXeD" to intArrayOf(49406, 7067, 4909, 6780, 49407),
        "tab\tand\nnewline" to intArrayOf(49406, 14724, 537, 1218, 1148, 49407),
        "&amp; encoded &lt;html&gt;" to intArrayOf(49406, 261, 524, 33703, 283, 18231, 285, 49407),
        "it’s curly apostrophe" to intArrayOf(49406, 585, 568, 20795, 4089, 3121, 19853, 49407),
        "a very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very very long text that must be cut" to intArrayOf(49406, 320, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 1070, 49407),
        "😀 smiling face" to intArrayOf(49406, 7334, 9200, 1710, 49407),
        "school uniform in nigeria" to intArrayOf(49406, 1228, 11075, 530, 6106, 49407),
        "football match at the stadium at night" to intArrayOf(49406, 1882, 2439, 536, 518, 3390, 536, 930, 49407),
        "a close-up of a flower with a bee" to intArrayOf(49406, 320, 2660, 268, 705, 539, 320, 4055, 593, 320, 5028, 49407),
    )

    @Test
    fun matchesTheOriginalOnEveryCase() {
        for ((text, expected) in golden) {
            val actual = tokenizer.tokenize(text)
            assertEquals("length for: $text", ClipTokenizer.CONTEXT_LENGTH, actual.size)
            assertEquals("tokens for: $text", expected.toList(), actual.take(expected.size))
            assertTrue("padding after the end marker for: $text", actual.drop(expected.size).all { it == 0 })
        }
    }

    @Test
    fun aPhotoOfADogIsFiveWords() {
        // Known from the CLIP paper's examples: "a"=320, "photo"=1125, "of"=539, "dog"=1929.
        assertEquals(listOf(320, 1125, 539, 320, 1929), tokenizer.encode("a photo of a dog").toList())
    }

    @Test
    fun startAndEndMarkersFrameTheText() {
        val tokens = tokenizer.tokenize("hello")
        assertEquals(ClipTokenizer.START_ID, tokens.first())
        assertEquals(ClipTokenizer.END_ID, tokens[tokens.indexOfFirst { it == 0 } - 1])
    }

    @Test
    fun capitalisationDoesNotMatter() {
        assertEquals(tokenizer.encode("Golden Gate Bridge").toList(), tokenizer.encode("golden gate bridge").toList())
    }

    @Test
    fun aVeryLongTextIsCutWithTheEndMarkerLast() {
        val tokens = tokenizer.tokenize("word ".repeat(200))
        assertEquals(ClipTokenizer.CONTEXT_LENGTH, tokens.size)
        assertEquals(ClipTokenizer.END_ID, tokens.last())
    }

    @Test
    fun emptyTextIsJustTheMarkers() {
        val tokens = tokenizer.tokenize("")
        assertEquals(listOf(ClipTokenizer.START_ID, ClipTokenizer.END_ID, 0), tokens.take(3))
    }
}
