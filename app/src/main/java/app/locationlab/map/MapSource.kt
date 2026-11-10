package app.locationlab.map

import androidx.annotation.StringRes
import app.locationlab.R

enum class MapSource(@get:StringRes val label: Int, internal val styleUrl: String? = null) {
    OpenFreeMap(R.string.map_open_free, "https://tiles.openfreemap.org/styles/liberty"),
    VersaTiles(R.string.map_versa, "https://tiles.versatiles.org/assets/styles/colorful/style.json"),
    OpenStreetMap(R.string.map_osm),
    AMapPreview(R.string.map_amap),
    BaiduPreview(R.string.map_baidu),
    GooglePreview(R.string.map_google),
}

