package com.simon.harmonichackernews

import android.content.res.Resources

object StoryTypeAndroid {

    fun buildStartingPageLabels(
        resources: Resources,
        enabledFrontpages: Set<String>?,
    ): ArrayList<CharSequence> {
        val options = resources.getStringArray(R.array.starting_page_options)
        val labels = options.mapTo(ArrayList<CharSequence>(options.size)) { it }
        StoryType.additionalFrontpages.forEach { type ->
            if (type.label in enabledFrontpages.orEmpty()) labels.add(type.label)
        }
        return labels
    }
}
