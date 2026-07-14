package net.rwhps.plugin.namefilter

class NameFilterState(
    val config: NameFilterConfig,
    @Volatile var pattern: Regex,
)
