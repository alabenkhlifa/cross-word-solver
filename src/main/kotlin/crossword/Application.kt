package crossword

import kotlinx.coroutines.*
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

var matrix: MutableList<List<Char>> = mutableListOf()
var minimaumWordLength = 3
private val ioScope = CoroutineScope(Dispatchers.IO + Job())
val finished = AtomicInteger()
val string = AtomicReference("")
val bool = AtomicReference(false)

@ObsoleteCoroutinesApi
val counterContext = newSingleThreadContext("CounterContext")

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
        response.isSuccessful
    }
}


private suspend fun longestWordByDirection(direction: Directions): MutableMap<Int, String?>? =
    when (direction) {
        Directions.RIGHT -> longestWordRight()
        else -> longestWordRight()
    }


private suspend fun longestWordRight(): MutableMap<Int, String?>? {
    val resultMap: MutableMap<Int, String?> = mutableMapOf()
    (0 until getLineCount()).forEach {
        resultMap[it.plus(1)] = longestWordRightByLineV2(it)
    }
    return resultMap
}

private suspend fun longestWordRightByLineV2(lineNumber: Int): String? { // line : 0
    val longestPotentialWordPerLine = getColumnCount() + 1 - minimaumWordLength
    val totalCombinisationInPerLine = IntRange(1, longestPotentialWordPerLine).sum()
    val totalNumberOfCombination = (getLineCount() * totalCombinisationInPerLine).toFloat() //60
    val line = matrix[lineNumber]
    val fullLine = line
        .map { it.toString() }
        .reduce { acc, char -> "$acc$char" }
    for (letterIndex in fullLine.indices) {
        val word = fullLine.substring(letterIndex)
        if (word.length >= 3) {
            dropLastAndCheck(totalNumberOfCombination, word)
        }
    }
    return string.acquire
}

private suspend fun dropLastAndCheck(totalNumberOfCombination: Float, word: String) {
    val jobs = ArrayList<Job>()
    for (index in word.length downTo minimaumWordLength) {
        string.set("")
        printPercentage(totalNumberOfCombination)
        jobs.add(ioScope.launch {
            if (bool.acquire.not()) {
                val dropRight = word.dropLast(word.length - index)
                bool.set(withContext(Dispatchers.IO) {
                    wordExists(dropRight).or(bool.acquire)
                })
                if (bool.acquire) {
                    string.set(dropRight)
                    bool.set(false)
                }
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

//private fun recursive(word: String, wordFound: Boolean = false): String? {
//    if(wordFound)
//        return null
//    else {
//        return recursive()
//    }
//}


// FIXME: replace this algorithm is wrong
private suspend fun longestWordRightByLine(lineNumber: Int): String? {
    var theFoundWord: String? = null
    val line = matrix[lineNumber]
    val word = line
        .map { it.toString() }
        .reduce { acc, char -> "$acc$char" }
    println("$word : ${wordExists(word)}")
    var wordFound = withContext(Dispatchers.IO) { wordExists(word) }
    if (wordFound)
        theFoundWord = word
    for (numberOfDroppedLetters in 1 until (getColumnCount() - 3)) {
        if (wordFound.not()) {
            val dropLeft = word.drop(numberOfDroppedLetters)
            wordFound = withContext(Dispatchers.IO) { wordExists(dropLeft).or(wordFound) }
            println("$dropLeft : ${wordExists(dropLeft)}")
            if (wordFound) {
                theFoundWord = dropLeft
            }
        }
        if (wordFound.not()) {
            val dropRight = word.dropLast(numberOfDroppedLetters)
            wordFound = withContext(Dispatchers.IO) { wordExists(dropRight).or(wordFound) }
            println("$dropRight : ${wordExists(dropRight)}")
            if (wordFound) {
                theFoundWord = dropRight
            }
        }
        if (wordFound.not() && numberOfDroppedLetters > 1) {
            val dropBoth = word.dropLast(numberOfDroppedLetters - 1).drop(numberOfDroppedLetters - 1)
            wordFound = withContext(Dispatchers.IO) { wordExists(dropBoth).or(wordFound) }
            println("$dropBoth : ${wordExists(dropBoth)}")
            if (wordFound) {
                theFoundWord = dropBoth
            }
        }
    }
    return theFoundWord
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