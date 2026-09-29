package com.sidequest.app

data class SearchPlace(val name:String,val subtitle:String,val lat:Double,val lon:Double)

data class MapPlace(
    val id:String,
    val title:String,
    val subtitle:String,
    val lat:Double,
    val lon:Double,
    val image:String?=null,
    val source:String,
    val kind:String
)

data class NewsItem(
    val title:String,
    val url:String?,
    val image:String?,
    val domain:String?,
    val seenDate:String?,
    val kind:String
)

data class Signal(
    val level:String="none",
    val mentions:Int=0,
    val window:String="24h",
    val label:String="Unusual-media mention signal",
    val disclaimer:String="Keyword matches in recent coverage; not evidence of paranormal activity."
)

data class DiscoverFeed(
    val places:List<MapPlace> = emptyList(),
    val wikiPlaces:List<MapPlace> = emptyList(),
    val articles:List<NewsItem> = emptyList(),
    val incidents:List<NewsItem> = emptyList(),
    val unusual:List<NewsItem> = emptyList(),
    val signal:Signal = Signal(),
    val generatedAt:String = ""
)

data class Account(
    val uid:String,
    val email:String
)

data class CommunityReport(
    val id:String="",
    val uid:String="",
    val category:String="other",
    val title:String="",
    val details:String="",
    val lat:Double=0.0,
    val lon:Double=0.0,
    val createdAtMillis:Long=0L
)
