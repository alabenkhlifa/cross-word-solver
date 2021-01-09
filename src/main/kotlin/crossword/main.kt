package crossword

import kotlinx.coroutines.*
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

const val minimumWordLength = 3

var matrix: MutableList<List<Char>> = mutableListOf()
var allResults: MutableMap<Directions, MutableMap<Int, MutableList<String?>>> = mutableMapOf()

lateinit var inputArgs: List<Directions>

lateinit var words: List<String>

val ioScope = CoroutineScope(Dispatchers.IO + Job())

var log: Logger = LoggerFactory.getLogger("")

fun main(args: Array<String>) = runBlocking<Unit> {
    val imageName = args.first()
//    val measureTimeMillis = measureTimeMillis {
        try {
            inputArgs = args.drop(1).map { Directions.valueOf(it) }.toList()
        } catch (exception: IllegalArgumentException) {
            log.error("Invalid input directions ! Please check your inputs")
            log.error("args should be one of theses directions : ${Directions.values().map { it.direction }.toList()}")
            exitProcess(1)
        }
        loadDictionnaryWords()
        readCrossWords(imageName)
        isMatrixValidOrThrow()
        printMatrixElements()
        inputArgs.forEach { findWordsByDirection(it) }
        log.info("result : $allResults")
//    }
//    log.info("The operation took $measureTimeMillis ms in total")
    exitProcess(0)
}

private fun loadDictionnaryWords() {
    words = readFileAsLinesUsingBufferedReader("words_alpha.txt").map { it.toUpperCase() }
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
        log.info("$it")
    }
}

private suspend fun wordExistsFromAPI(word: String): Boolean {
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

private fun wordExists(word: String) = words.binarySearch(word) > 0



private suspend fun findWordsByDirection(direction: Directions) =
    when (direction) {
        Directions.RIGHT -> findWords(Directions.RIGHT)
        Directions.LEFT -> findWords(Directions.LEFT)
        Directions.UP -> findWords(Directions.UP)
        Directions.DOWN -> findWords(Directions.DOWN)
        else -> findWords(Directions.RIGHT)
    }

private suspend fun findWords(direction: Directions) {
    var resultMap: MutableMap<Int, MutableList<String?>> = mutableMapOf()
    initResultMap(resultMap, direction)
    var loopCounterByDirection = 0
    if (direction == Directions.RIGHT || direction == Directions.LEFT)
        loopCounterByDirection = getLineCount()
    else if (direction == Directions.UP || direction == Directions.DOWN)
        loopCounterByDirection = getColumnCount()
    (0 until loopCounterByDirection).forEach {
        searchByLineAndDirection(it, direction, resultMap)
    }
    allResults[direction] = resultMap
}

private suspend fun searchByLineAndDirection(
    lineOrColumnNumber: Int,
    direction: Directions,
    resultMap: MutableMap<Int, MutableList<String?>>
) {
    val fullWord: String
    when (direction) {
        Directions.RIGHT -> fullWord = matrix[lineOrColumnNumber]
            .map { it.toString() }
            .reduce { acc, char -> "$acc$char" }
        Directions.LEFT -> {
            fullWord = matrix[lineOrColumnNumber]
                .map { it.toString() }
                .reduce { acc, char -> "$acc$char" }
                .reversed()
        }
        Directions.DOWN -> fullWord = matrix
            .map { it.drop(lineOrColumnNumber).first().toString() }
            .reduce { acc, char -> "$acc$char" }
        Directions.UP -> {
            fullWord = matrix
                .map { it.drop(lineOrColumnNumber).first().toString() }
                .reduce { acc, char -> "$acc$char" }
                .reversed()
        }
        else -> fullWord = ""
    }
    for (letterIndex in fullWord.indices) {
        val word = fullWord.substring(letterIndex)
        if (word.length >= minimumWordLength) {
            dropLastAndCheck(word, lineOrColumnNumber, resultMap)
        }
    }
    resultMap.forEach { (_, value) ->
        value.sortByDescending { it?.length }
    }
}

private suspend fun dropLastAndCheck(word: String, lineOrColumnNumber: Int, result: MutableMap<Int, MutableList<String?>>) {
    val jobs = ArrayList<Job>()
    for (index in word.length downTo minimumWordLength) {
        jobs.add(ioScope.launch {
            val dropRight = word.dropLast(word.length - index)
            if (result[lineOrColumnNumber]!!.contains(dropRight).not() &&
                wordExists(dropRight)
            ) {
                log.debug("word found : $dropRight")
                result[lineOrColumnNumber]!!.add(dropRight)
            }
        })
    }
    jobs.joinAll()
}

private fun initResultMap(resultMap: MutableMap<Int, MutableList<String?>>, direction: Directions) {
    if (direction == Directions.RIGHT || direction == Directions.LEFT) {
        for (lineNumber in 0 until getLineCount()) {
            resultMap[lineNumber] = mutableListOf()
        }
    } else if (direction == Directions.UP || direction == Directions.DOWN) {
        for (lineNumber in 0 until getColumnCount()) {
            resultMap[lineNumber] = mutableListOf()
        }
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
fun readFileAsLinesUsingBufferedReader(fileName: String): List<String>
        = File(fileName).bufferedReader().readLines()