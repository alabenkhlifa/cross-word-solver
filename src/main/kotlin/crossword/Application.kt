package crossword

import kotlinx.coroutines.*
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

var matrix: MutableList<List<Char>> = mutableListOf()
var allResults: MutableMap<Int, MutableList<String?>> = mutableMapOf()
const val minimumWordLength = 3
val ioScope = CoroutineScope(Dispatchers.IO + Job())
val finished = AtomicInteger()

fun main(args: Array<String>) = runBlocking<Unit> {
    val measureTimeMillis = measureTimeMillis {
        readCrossWords("wordpuzzel.png")
        isMatrixValidOrThrow()
//        printMatrixElements()
        println("longestWordRight : ${longestWordByDirection(Directions.RIGHT)}")
    }
    println("(The operation took $measureTimeMillis ms)")
    exitProcess(0)
}

private fun readCrossWords(imageName: String) {
    val tesseract = Tesseract()

    try {

        tesseract.setDatapath("C:\\Users\\AlaK\\Documents\\crossword\\tesseract-4.1.1")
        tesseract.setTessVariable("user_defined_dpi", "300")
        val text = tesseract.doOCR(File(imageName)).trim()
        val split = text.toUpperCase().split('\n').toList()
        matrix.addAll(split.map { it.toList() })

    } catch (e: TesseractException) {
        e.printStackTrace()
    }
}

private fun printMatrixElements() {
    matrix.forEach {
        println("$it : ${it.size}")
    }
}

private suspend fun wordExists(word: String): Boolean {
    return withContext(Dispatchers.IO) {
        val httpClient = OkHttpClient()
        val response = httpClient.newCall(
            Request
                .Builder()
                .url("https://api.dictionaryapi.dev/api/v2/entries/en/$word")
                .build()
        )
            .execute()

        response.close()
        if (response.code() == 403) {
            error("Too many request on the dictionnary API, please try again later")
        }
        response.isSuccessful
    }
}


private suspend fun longestWordByDirection(direction: Directions): MutableMap<Int, String?>? =
    when (direction) {
        Directions.RIGHT -> longestWordRight()
        else -> longestWordRight()
    }

private suspend fun longestWordRight(): MutableMap<Int, String?> {
    val resultMap: MutableMap<Int, String?> = mutableMapOf()
    (0 until getLineCount()).forEach {
        longestWordRightByLineV2(it)
    }
    println("[RIGHT] all words found : $allResults")
    allResults.forEach { (key, value) ->
        value.sortByDescending { it?.length }
        resultMap[key] = value[0]
    }
    return resultMap
}

private suspend fun longestWordRightByLineV2(lineNumber: Int) { // line : 0
    val longestPotentialWordPerLine = getColumnCount() + 1 - minimumWordLength
    val totalCombinisationInPerLine = IntRange(1, longestPotentialWordPerLine).sum()
    val totalNumberOfCombination = (getLineCount() * totalCombinisationInPerLine).toFloat() //60
    val line = matrix[lineNumber]
    val fullLine = line
        .map { it.toString() }
        .reduce { acc, char -> "$acc$char" }
    for (letterIndex in fullLine.indices) {
        val word = fullLine.substring(letterIndex)
        if (word.length >= 3) {
            dropLastAndCheck(totalNumberOfCombination, word, lineNumber)
        }
    }
}

private suspend fun dropLastAndCheck(totalNumberOfCombination: Float, word: String, lineNumber: Int) {
    val jobs = ArrayList<Job>()
    for (index in word.length downTo minimumWordLength) {
        printPercentage(totalNumberOfCombination)
        jobs.add(ioScope.launch {
            val dropRight = word.dropLast(word.length - index)
            if (allResults[lineNumber].isNullOrEmpty().not() &&
                allResults[lineNumber]!!.contains(dropRight).not() &&
                wordExists(dropRight)
            ) {
                println("word found : $dropRight")
                allResults.computeIfPresent(lineNumber)
                { _, oldValue ->
                    if (oldValue.contains(dropRight).not()) {
                        oldValue.add(dropRight)
                    }
                    oldValue
                }
            } else if (allResults[lineNumber].isNullOrEmpty() && wordExists(dropRight)) {
                println("word found : $dropRight")
                allResults[lineNumber] = mutableListOf(dropRight)
            }
        })
    }
    jobs.joinAll()
}

private fun printPercentage(totalNumberOfCombination: Float) {
    val counter = finished.incrementAndGet().toFloat()
    val percentage = counter.div(totalNumberOfCombination) * 100.toFloat()
    println("${String.format("%.2f", percentage)}% Finished")
}

private fun getDiffBetweenColumnsAndLines(): Int {
    return getColumnCount() - getLongestDiagonalSize()
}

private fun getLongestDiagonalSize(): Int {
    return Integer.min(getColumnCount(), getLineCount())
}

private fun isMatrixValidOrThrow() {
    require(matrix.isNullOrEmpty().not()) { "Couldn't read letters, please try with another crossword image" }
    require(matrix.all { it.size == matrix[0].size }) { "Invalid Crossword image" }
}

private fun getColumnCount(): Int {
    return matrix[0].size
}

private fun getSmallestDimension(): Dimensions {
    return if (getColumnCount() == getLongestDiagonalSize()) Dimensions.COLUMN else Dimensions.LINE
}

private fun getLineCount(): Int {
    return matrix.size
}

enum class Directions {
    RIGHT, LEFT, UP, DOWN, DOWN_RIGHT, DOWN_LEFT, UP_RIGHT, UP_LEFT
}

enum class Dimensions {
    LINE, COLUMN
}