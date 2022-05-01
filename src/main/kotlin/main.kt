import it.skrape.core.document
import it.skrape.fetcher.HttpFetcher
import it.skrape.fetcher.response
import it.skrape.fetcher.skrape
import it.skrape.selects.eachHref
import it.skrape.selects.eachText
import it.skrape.selects.html5.a
import kotlinx.coroutines.*
import java.io.File
import java.util.regex.Pattern

fun main(args: Array<String>) {
    val profiles = runBlocking { getProfiles() }
    val writer = File("output.csv").bufferedWriter()
    writer.write("Name,Life Range,Occupation,Main Article Link")
    writer.newLine()
    profiles.forEach {
        writer.write("${it.name.csv},${it.lifeRange.csv},${it.occupation.csv},${it.mainArticleLink.csv}")
        writer.newLine()
    }
}

val String?.csv: String
    get() = if (this?.contains(',') == true) "\"$this\"" else this ?: ""

fun getUrl(pageNumber: Int): String {
    return "https://www.oxforddnb.com/browse?date-era-0-le_0_0=CE&date-era-0-le_0_1=CE&date-era-1-le_0_0=CE&date-era-1-le_0_1=CE&date-from-0-le_0=1066&date-to-1-le_0=1700&date-type-0-le_0=firstDate&date-type-1-le_0_0=lastDate&date-type-1-le_0_1=lastDate&date-year-0-le_0=1066&date-year-1-le_0_0=1700&date-year-1-le_0_1=1700&page=$pageNumber&pageSize=50&sort=titlesort&t=OccupationsAndRealmsOfRenown%3A938&type-le_0=any"
}

data class Profile(val name: String, val lifeRange: String, val occupation: String?, val mainArticleLink: String)

data class Title(val name: String, val lifeRange: String, val occupation: String?)

suspend fun getProfiles(): List<Profile> = coroutineScope {
    (1..89).map { pageNum ->
        val target = getUrl(pageNum)
        delay(2000)
        async {
            skrape(HttpFetcher) {
                request {
                    timeout = 120000
                    url = target
                }

                response {
                    document.a {
                        val titles = mutableListOf<Title>()
                        val profileLinks = mutableListOf<String>()
                        withClass = "c-Button--link"
                        val titleTexts = findAll { eachText }
                        val titleTriplets = titleTexts.map {
                            val matchResult = "([^\\(]+)\\(([^\\)]+)\\),? ?([^$]*)".toRegex().find(it)
                            if (matchResult == null) {
                                println("Couldn't parse: $it")
                                null
                            } else matchResult.groupValues.drop(1)
                        }
                        titles.addAll(titleTriplets.mapNotNull {
                            Title(
                                it!!.first().trim(),
                                it[1].trim(),
                                it.getOrNull(2)
                            )
                        })
                        profileLinks.addAll(findAll { eachHref }.map { "https://www.oxforddnb.com/$it" })
                        val profileLinksAndTitles = titles.zip(profileLinks)
                        profileLinksAndTitles.map { Profile(it.first.name, it.first.lifeRange, it.first.occupation, it.second) }
                    }
                }
            }
        }
    }.awaitAll().flatten()
}
