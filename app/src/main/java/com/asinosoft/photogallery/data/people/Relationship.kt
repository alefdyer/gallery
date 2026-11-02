package com.asinosoft.photogallery.data.people

import androidx.annotation.StringRes
import com.asinosoft.photogallery.R

enum class RelationshipKey(val key: String, @param:StringRes val labelRes: Int) {
    ME("me", R.string.people_rel_me),
    MOTHER("mother", R.string.people_rel_mother),
    FATHER("father", R.string.people_rel_father),
    PARENTS("parents", R.string.people_rel_parents),
    SISTER("sister", R.string.people_rel_sister),
    BROTHER("brother", R.string.people_rel_brother),
    WIFE("wife", R.string.people_rel_wife),
    HUSBAND("husband", R.string.people_rel_husband),
    SPOUSE("spouse", R.string.people_rel_spouse),
    DAUGHTER("daughter", R.string.people_rel_daughter),
    SON("son", R.string.people_rel_son),
    OTHER("other", R.string.people_rel_other),
    ;

    companion object {
        fun fromStored(value: String?): RelationshipKey? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull { it.key == value }
        }

        /** Подпись: ключ из списка или произвольный текст «Другое». */
        fun labelResOrNull(value: String?): Int? =
            fromStored(value)?.labelRes
    }
}
