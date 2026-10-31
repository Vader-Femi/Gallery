cat << 'INNER_EOF' >> app/src/main/java/com/femi/gallery/presentation/settings/ThemePreferences.kt

    private const val KEY_HIDDEN_ALBUMS = "hidden_albums"

    fun getHiddenAlbums(context: Context): Set<Long> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_HIDDEN_ALBUMS, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    fun setHiddenAlbums(context: Context, hiddenIds: Set<Long>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putStringSet(KEY_HIDDEN_ALBUMS, hiddenIds.map { it.toString() }.toSet()) }
    }
INNER_EOF
