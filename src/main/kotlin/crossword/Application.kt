package crossword

import kotlinx.coroutines.*
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.IllegalArgumentException
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

const val minimumWordLength = 3

var matrix: MutableList<List<Char>> = mutableListOf()
var allResults: MutableMap<Directions, MutableMap<Int, MutableList<String?>>> = mutableMapOf()

lateinit var inputArgs: List<Directions>

val ioScope = CoroutineScope(Dispatchers.IO + Job())

var log: Logger = LoggerFactory.getLogger("")

fun main(args: Array<String>) = runBlocking<Unit> {
    val imageName = args.first()
    val measureTimeMillis = measureTimeMillis {
        try { inputArgs = args.drop(1).map { Directions.valueOf(it) }.toList() }
        catch (exception: IllegalArgumentException) {
            log.error("Invalid input directions ! Please check your inputs")
            log.error("args should be one of theses directions : ${Directions.values().map { it.direction }.toList()}")
            exitProcess(1)
        }
        readCrossWords(imageName)
        isMatrixValidOrThrow()
        inputArgs.forEach { findWordsInLineByDirection(it) }
        log.info("result : $allResults")
    }
    log.info("The operation took ${measureTimeMillis / 1000} s in total")
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
        log.info("$it : ${it.size}")
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
            log.error("Too many request on the dictionnary API, please try again later")
            exitProcess(-1)
        }
        response.isSuccessful
    }
}


private suspend fun findWordsInLineByDirection(direction: Directions) =
    when (direction) {
        Directions.RIGHT -> findWordsByRightOrLeft(Directions.RIGHT)
        Directions.LEFT -> findWordsByRightOrLeft(Directions.LEFT)
        else -> findWordsByRightOrLeft(Directions.RIGHT)
    }

private suspend fun findWordsByRightOrLeft(direction: Directions) {
    var resultMap: MutableMap<Int, MutableList<String?>> = mutableMapOf()
    initResultMap(resultMap)
    (0 until getLineCount()).forEach {
        searchByLineAndDirection(it, direction, resultMap)
    }
    allResults[direction] = resultMap
}

private suspend fun searchByLineAndDirection(
    lineNumber: Int,
    direction: Directions,
    resultMap: MutableMap<Int, MutableList<String?>>
) {
    var fullLine = matrix[lineNumber]
        .map { it.toString() }
        .reduce { acc, char -> "$acc$char" }
    if (direction == Directions.LEFT) {
        fullLine = fullLine.reversed()
    }
    for (letterIndex in fullLine.indices) {
        val word = fullLine.substring(letterIndex)
        if (word.length >= minimumWordLength) {
            dropLastAndCheck(word, lineNumber, resultMap)
        }
    }
    resultMap.forEach { (_, value) ->
        value.sortByDescending { it?.length }
    }
}

private suspend fun dropLastAndCheck(word: String, lineNumber: Int, result: MutableMap<Int, MutableList<String?>>) {
    val jobs = ArrayList<Job>()
    for (index in word.length downTo minimumWordLength) {
        jobs.add(ioScope.launch {
            val dropRight = word.dropLast(word.length - index)
            if (result[lineNumber]!!.contains(dropRight).not() &&
                wordExists(dropRight)
            ) {
                log.debug("word found : $dropRight")
                result[lineNumber]!!.add(dropRight)
            }
        })
    }
    jobs.joinAll()
}

private fun initResultMap(resultMap: MutableMap<Int, MutableList<String?>>) {
    for (lineNumber in 0 until getLineCount()) {
        resultMap[lineNumber] = mutableListOf()
    }
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

enum class Directions(val direction: String) {
    RIGHT("RIGHT"), LEFT("LEFT"),
    UP("UP"), DOWN("DOWN"),
    DOWN_RIGHT("DOWN RIGHT"), DOWN_LEFT("DOWN LEFT"),
    UP_RIGHT("UP RIGHT"), UP_LEFT("UP LEFT")
}

enum class Dimensions {
    LINE, COLUMN
}