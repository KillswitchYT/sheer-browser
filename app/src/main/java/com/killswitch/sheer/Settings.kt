package com.killswitch.sheer

import android.content.Context

/** Everything the person can change, in one private preferences file on the phone. Nothing here is ever sent anywhere. */
object Settings {
    private fun p(c: Context) = c.getSharedPreferences("sheer", Context.MODE_PRIVATE)

    fun int(c: Context, k: String, def: Int): Int = p(c).getInt(k, def)
    fun setInt(c: Context, k: String, v: Int) { p(c).edit().putInt(k, v).apply() }
    fun str(c: Context, k: String, def: String = ""): String = p(c).getString(k, def) ?: def
    fun setStr(c: Context, k: String, v: String) { p(c).edit().putString(k, v).apply() }

    /** 0 follows the phone, 1 dark, 2 light. */
    fun theme(c: Context) = int(c, "th", 0).coerceIn(0, 2)

    /** Index into [UrlInput.ENGINES]. The key is "se2" because the list changed: an older saved choice must not point at a different engine. */
    fun engine(c: Context) = int(c, "se2", 0).coerceIn(0, UrlInput.ENGINES.lastIndex)
    fun setEngine(c: Context, i: Int) = setInt(c, "se2", i)

    /** Pages that only offer plain http are refused. On until the person turns it off. */
    fun httpsOnly(c: Context) = int(c, "https", 1) == 1

    /** Strict tracking protection. On until the person turns it off. */
    fun strictTracking(c: Context) = int(c, "etp", 1) == 1

    /** Google's list of dangerous sites, checked on the phone. On until the person turns it off. */
    fun safeBrowsing(c: Context) = int(c, "sb", 1) == 1

    /** The address bar at the top instead of the bottom. Test builds can flip the default with -PtestTop=true. */
    fun addressTop(c: Context) = int(c, "top", if (c.resources.getBoolean(R.bool.default_address_top)) 1 else 0) == 1

    fun flag(c: Context, k: String, on: Boolean) = setInt(c, k, if (on) 1 else 0)
}
