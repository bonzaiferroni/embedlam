package ponder.embedlam

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform