package com.femi.gallery.domain.label

/**
 * How object and scene labels are stored and looked up.
 *
 * ML Kit names things in English ("Dog", "Musical instrument", "Fast food"). A label is stored in
 * lower case with its words joined by underscores (`musical_instrument`) so that a list of labels can
 * be kept as one space-separated string. A search word matches a label when it equals one of the
 * label's words, or begins one (for words of four letters or more, so "musi" finds "musical" but "car" does not find "carnivore"), after
 * [expand] has added the everyday words people use for the same thing ("puppy" for "dog").
 */
object LabelMatcher {

    /** The stored form of a label from the labeller, or null when there is nothing to store. */
    fun normalize(label: String): String? {
        val words = label.lowercase().trim().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return if (words.isEmpty()) null else words.joinToString("_")
    }

    /** The words of one stored label: `musical_instrument` is "musical" and "instrument". */
    fun wordsOf(storedLabel: String): List<String> = storedLabel.split('_').filter { it.isNotEmpty() }

    /** What to look for when [term] is searched: the word itself, its singular, and its synonyms. */
    fun expand(term: String): Set<String> {
        val word = term.lowercase().trim()
        if (word.isEmpty()) return emptySet()
        val result = LinkedHashSet<String>()
        result.add(word)
        singular(word)?.let { result.add(it) }
        for (candidate in result.toList()) {
            SYNONYMS[candidate]?.let { result.addAll(it) }
        }
        return result
    }

    /** Whether a stored label answers any of the [wanted] words (see [expand]). */
    fun matches(storedLabel: String, wanted: Set<String>): Boolean {
        val words = wordsOf(storedLabel)
        return wanted.any { want ->
            want == storedLabel || words.any { it == want || (want.length >= MIN_PREFIX && it.startsWith(want)) }
        }
    }

    private fun singular(word: String): String? = when {
        word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
        word.length > 3 && word.endsWith("es") && word.dropLast(2).let { it.endsWith("s") || it.endsWith("x") || it.endsWith("ch") || it.endsWith("sh") } -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> null
    }

    private const val MIN_PREFIX = 4

    /** Everyday word -> the words ML Kit's labels use for it. Only words that are not labels themselves. */
    private val SYNONYMS: Map<String, Set<String>> = mapOf(
        "puppy" to setOf("dog"),
        "doggy" to setOf("dog"),
        "kitten" to setOf("cat"),
        "kitty" to setOf("cat"),
        "pet" to setOf("dog", "cat"),
        "animal" to setOf("dog", "cat", "bird", "horse", "fish", "mammal", "wildlife"),
        "meal" to setOf("food", "dish", "cuisine"),
        "dinner" to setOf("food", "dish", "cuisine"),
        "lunch" to setOf("food", "dish", "cuisine"),
        "breakfast" to setOf("food", "dish", "cuisine"),
        "snack" to setOf("food", "fast_food", "dessert"),
        "pizza" to setOf("pizza", "fast_food"),
        "burger" to setOf("hamburger", "fast_food"),
        "cake" to setOf("cake", "dessert", "birthday_cake"),
        "drink" to setOf("drink", "cocktail", "beverage"),
        "coffee" to setOf("coffee", "cup", "latte"),
        "sea" to setOf("sea", "ocean", "beach", "coast"),
        "ocean" to setOf("sea", "ocean", "beach", "coast"),
        "sand" to setOf("beach", "sand"),
        "sunset" to setOf("sunset", "dusk", "afterglow", "sunrise"),
        "sunrise" to setOf("sunrise", "sunset", "dawn"),
        "mountain" to setOf("mountain", "mountainous_landforms", "hill", "highland"),
        "hill" to setOf("hill", "mountain", "highland"),
        "tree" to setOf("tree", "plant", "woody_plant"),
        "flower" to setOf("flower", "petal", "plant"),
        "garden" to setOf("garden", "plant", "flower"),
        "car" to setOf("car", "vehicle", "sports_car"),
        "vehicle" to setOf("car", "vehicle", "truck", "motorcycle", "bus"),
        "bike" to setOf("bicycle", "motorcycle"),
        "plane" to setOf("airplane", "aircraft", "airliner"),
        "airplane" to setOf("airplane", "aircraft", "airliner"),
        "boat" to setOf("boat", "watercraft", "ship", "yacht"),
        "ship" to setOf("ship", "boat", "watercraft"),
        "building" to setOf("building", "skyscraper", "tower", "architecture"),
        "house" to setOf("house", "building", "home"),
        "city" to setOf("city", "skyline", "skyscraper", "urban_area", "metropolis"),
        "people" to setOf("person", "crowd", "people", "smile"),
        "person" to setOf("person", "people", "face"),
        "man" to setOf("man", "person", "beard"),
        "woman" to setOf("woman", "person", "dress"),
        "baby" to setOf("baby", "toddler", "infant"),
        "kid" to setOf("child", "toddler"),
        "kids" to setOf("child", "toddler"),
        "child" to setOf("child", "toddler"),
        "selfie" to setOf("selfie", "face", "smile"),
        "party" to setOf("party", "event", "crowd"),
        "wedding" to setOf("wedding", "bride", "wedding_dress", "bridal"),
        "concert" to setOf("concert", "stage", "performance", "crowd"),
        "phone" to setOf("mobile_phone", "gadget", "smartphone"),
        "computer" to setOf("computer", "laptop", "personal_computer", "gadget"),
        "laptop" to setOf("laptop", "computer", "netbook"),
        "tv" to setOf("television", "screen"),
        "book" to setOf("book", "publication", "paper"),
        "sport" to setOf("sport", "ball", "player", "team"),
        "football" to setOf("ball", "football", "soccer", "team_sport"),
        "soccer" to setOf("ball", "football", "soccer", "team_sport"),
        "snow" to setOf("snow", "winter", "freezing"),
        "winter" to setOf("snow", "winter", "freezing"),
        "night" to setOf("night", "midnight", "darkness"),
        "sky" to setOf("sky", "cloud", "atmosphere"),
        "water" to setOf("water", "lake", "river", "sea", "ocean", "waterfall"),
        "river" to setOf("river", "water", "stream"),
        "lake" to setOf("lake", "water", "pond"),
    )
}
