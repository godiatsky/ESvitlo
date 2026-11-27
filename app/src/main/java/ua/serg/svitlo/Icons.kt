package ua.serg.svitlo

/** Монохромні іконки локацій (Phosphor, MIT): тонкі для великих місць, light для дрібних. */
object Icons {
    val KEYS = listOf("house", "briefcase", "flower_lotus", "smiley", "paw_print", "car_profile", "barbell", "graduation_cap", "heart", "tree", "shopping_cart", "wrench", "coffee")

    private val LEGACY = mapOf("🏠" to "house", "💼" to "briefcase", "💅" to "flower_lotus", "🛒" to "shopping_cart", "🔧" to "wrench", "🏋️" to "barbell", "🏫" to "graduation_cap", "☕" to "coffee", "❤️" to "heart", "🏡" to "tree")

    fun key(raw: String) = if (raw in KEYS) raw else LEGACY[raw] ?: "house"

    fun thin(raw: String) = when (key(raw)) {
        "briefcase" -> R.drawable.ic_t_briefcase
        "flower_lotus" -> R.drawable.ic_t_flower_lotus
        "smiley" -> R.drawable.ic_t_smiley
        "paw_print" -> R.drawable.ic_t_paw_print
        "car_profile" -> R.drawable.ic_t_car_profile
        "barbell" -> R.drawable.ic_t_barbell
        "graduation_cap" -> R.drawable.ic_t_graduation_cap
        "heart" -> R.drawable.ic_t_heart
        "tree" -> R.drawable.ic_t_tree
        "shopping_cart" -> R.drawable.ic_t_shopping_cart
        "wrench" -> R.drawable.ic_t_wrench
        "coffee" -> R.drawable.ic_t_coffee
        else -> R.drawable.ic_t_house
    }

    fun light(raw: String) = when (key(raw)) {
        "briefcase" -> R.drawable.ic_l_briefcase
        "flower_lotus" -> R.drawable.ic_l_flower_lotus
        "smiley" -> R.drawable.ic_l_smiley
        "paw_print" -> R.drawable.ic_l_paw_print
        "car_profile" -> R.drawable.ic_l_car_profile
        "barbell" -> R.drawable.ic_l_barbell
        "graduation_cap" -> R.drawable.ic_l_graduation_cap
        "heart" -> R.drawable.ic_l_heart
        "tree" -> R.drawable.ic_l_tree
        "shopping_cart" -> R.drawable.ic_l_shopping_cart
        "wrench" -> R.drawable.ic_l_wrench
        "coffee" -> R.drawable.ic_l_coffee
        else -> R.drawable.ic_l_house
    }
}
