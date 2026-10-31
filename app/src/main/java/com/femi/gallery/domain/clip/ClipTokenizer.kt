package com.femi.gallery.domain.clip

import java.text.Normalizer

/**
 * The byte-pair tokenizer that CLIP models were trained with (a port of OpenAI's `simple_tokenizer.py`,
 * MIT licence). A description is split into words, each word into the pieces of a fixed vocabulary, and the
 * pieces become numbers; a text model turns those numbers into the vector that is compared with the photos'.
 *
 * [mergesText] is the content of `bpe_simple_vocab_16e6.txt` (the list of pairs to merge, best first).
 * The only difference from the original is that the text is not passed through the `ftfy` library (which
 * repairs broken encodings); it is normalised to NFC instead, which is the same for ordinary text.
 */
class ClipTokenizer(mergesText: String) {

    /** The stand-in character of each byte (by byte value), and the same characters in the order the vocabulary lists them. */
    private val byteToChar: Array<String>
    private val symbolsInVocabOrder: List<String>
    private val encoder = HashMap<String, Int>(VOCAB_SIZE * 2)
    private val ranks = HashMap<Pair<String, String>, Int>(MERGE_COUNT * 2)
    private val cache = HashMap<String, List<String>>()
    private val pattern = Regex(
        "<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+",
        RegexOption.IGNORE_CASE,
    )

    init {
        val (byValue, inVocabOrder) = byteSymbols()
        byteToChar = byValue
        symbolsInVocabOrder = inVocabOrder

        val lines = mergesText.split('\n')
        val merges = lines.subList(1, 1 + MERGE_COUNT).map { line ->
            val parts = line.split(' ')
            parts[0] to parts[1]
        }
        // The vocabulary: the 256 byte symbols, the same with an end-of-word mark, every merge, the two special tokens.
        val vocab = ArrayList<String>(VOCAB_SIZE)
        vocab.addAll(symbolsInVocabOrder)
        symbolsInVocabOrder.forEach { vocab.add("$it</w>") }
        merges.forEach { vocab.add(it.first + it.second) }
        vocab.add(START_TEXT)
        vocab.add(END_TEXT)
        vocab.forEachIndexed { index, token -> encoder[token] = index }
        merges.forEachIndexed { rank, pair -> ranks[pair] = rank }
        cache[START_TEXT] = listOf(START_TEXT)
        cache[END_TEXT] = listOf(END_TEXT)
    }

    /** The token numbers of [text], without the start and end markers. */
    fun encode(text: String): IntArray {
        val tokens = ArrayList<Int>()
        for (match in pattern.findAll(clean(text))) {
            val word = match.value.toByteArray(Charsets.UTF_8).joinToString("") { byteToChar[it.toInt() and 0xFF] }
            for (piece in bpe(word)) tokens.add(encoder.getValue(piece))
        }
        return tokens.toIntArray()
    }

    /**
     * What a CLIP text model is fed: the start marker, the tokens of [text], the end marker, then zeros up to
     * [contextLength] numbers. Text that is too long is cut, keeping the end marker last.
     */
    fun tokenize(text: String, contextLength: Int = CONTEXT_LENGTH): IntArray {
        val words = encode(text)
        val result = IntArray(contextLength)
        val room = contextLength - 2
        val kept = minOf(words.size, room)
        result[0] = START_ID
        for (i in 0 until kept) result[1 + i] = words[i]
        result[1 + kept] = END_ID
        return result
    }

    private fun clean(text: String): String {
        var t = Normalizer.normalize(text, Normalizer.Form.NFC)
        t = repairLikeFtfy(t)
        repeat(2) { t = unescapeHtml(t) }
        // (?U): like Python, any Unicode whitespace (a non-breaking space, for instance) counts, not only ASCII ones.
        return t.trim().replace(Regex("(?U)\\s+"), " ").trim().lowercase()
    }

    /** The few repairs of the `ftfy` library that matter for typed text: curly quotes, ligatures and full-width letters. */
    private fun repairLikeFtfy(text: String): String {
        val out = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch == '\u2018' || ch == '\u2019' || ch == '\u201A' || ch == '\u201B' -> out.append('\'')
                ch == '\u201C' || ch == '\u201D' || ch == '\u201E' || ch == '\u201F' -> out.append('"')
                ch == '\u3000' -> out.append(' ')
                ch in '\uFF01'..'\uFF5E' -> out.append((ch.code - 0xFEE0).toChar()) // full-width ASCII
                else -> LIGATURES[ch]?.let { out.append(it) } ?: out.append(ch)
            }
        }
        return out.toString()
    }

    private fun unescapeHtml(text: String): String =
        text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")

    private fun bpe(word: String): List<String> {
        cache[word]?.let { return it }
        var parts: List<String> = word.dropLast(1).map { it.toString() } + (word.last().toString() + "</w>")
        if (parts.size == 1) return listOf(word + "</w>").also { cache[word] = it }

        while (parts.size > 1) {
            // The pair with the best (lowest) rank among the neighbouring pairs.
            var best: Pair<String, String>? = null
            var bestRank = Int.MAX_VALUE
            for (i in 0 until parts.size - 1) {
                val pair = parts[i] to parts[i + 1]
                val rank = ranks[pair] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    best = pair
                }
            }
            val (first, second) = best ?: break
            val merged = ArrayList<String>(parts.size)
            var i = 0
            while (i < parts.size) {
                if (i < parts.size - 1 && parts[i] == first && parts[i + 1] == second) {
                    merged.add(first + second)
                    i += 2
                } else {
                    merged.add(parts[i])
                    i += 1
                }
            }
            parts = merged
        }
        cache[word] = parts
        return parts
    }

    /**
     * Every byte has a printable stand-in character so that words made of raw bytes can be handled as text.
     * Returns the stand-ins by byte value and in the order the original lists them: the bytes that are already
     * printable first, then the others, each given a character after 255.
     */
    private fun byteSymbols(): Pair<Array<String>, List<String>> {
        val printable = ArrayList<Int>()
        printable.addAll('!'.code..'~'.code)
        printable.addAll('¡'.code..'¬'.code)
        printable.addAll('®'.code..'ÿ'.code)
        val bytes = ArrayList(printable)
        val chars = ArrayList(printable)
        var extra = 0
        for (b in 0 until 256) {
            if (b !in printable) {
                bytes.add(b)
                chars.add(256 + extra)
                extra++
            }
        }
        val byValue = arrayOfNulls<String>(256)
        val inOrder = ArrayList<String>(256)
        bytes.forEachIndexed { index, b ->
            val symbol = String(Character.toChars(chars[index]))
            byValue[b] = symbol
            inOrder.add(symbol)
        }
        return Array(256) { byValue[it]!! } to inOrder
    }

    companion object {
        const val CONTEXT_LENGTH = 77
        const val START_ID = 49406
        const val END_ID = 49407
        private val LIGATURES = mapOf(
            '\uFB00' to "ff", '\uFB01' to "fi", '\uFB02' to "fl", '\uFB03' to "ffi", '\uFB04' to "ffl", '\uFB05' to "st", '\uFB06' to "st",
        )
        private const val START_TEXT = "<|startoftext|>"
        private const val END_TEXT = "<|endoftext|>"
        private const val MERGE_COUNT = 49152 - 256 - 2
        private const val VOCAB_SIZE = 49408
    }
}
