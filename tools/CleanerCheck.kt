import com.yann.dictate.TextCleaner

private var failures = 0
private var total = 0

private fun check(name: String, actual: String, expected: String) {
    total++
    if (actual == expected) {
        println("PASS  $name")
    } else {
        failures++
        println("FAIL  $name\n      expected: ${expected.replace("\n", "\\n")}\n      actual:   ${actual.replace("\n", "\\n")}")
    }
}

fun main() {
    val none = emptyList<Pair<String, String>>()
    val words = listOf(Pair("meta trader", "MetaTrader"), Pair("xau usd", "XAUUSD"))

    check("filler + capital", TextCleaner.clean("um hello world", "", none), "Hello world")
    check("uh and erm gone", TextCleaner.clean("so uh we erm go", "", none), "So we go")
    check("comma and question mark", TextCleaner.clean("hello comma how are you question mark", "", none), "Hello, how are you?")
    check("full stop mid-phrase", TextCleaner.clean("hello full stop how are you", "", none), "Hello. How are you")
    check("exclamation", TextCleaner.clean("well done exclamation mark", "", none), "Well done!")
    check("5 mm and ER untouched", TextCleaner.clean("it is 5 mm and ER", "", none), "It is 5 mm and ER")
    check("umbrella untouched", TextCleaner.clean("number umbrella", "", none), "Number umbrella")
    check("uh-huh untouched", TextCleaner.clean("uh-huh", "", none), "Uh-huh")
    check("period and colon untouched", TextCleaner.clean("a period of time and colon surgery", "", none), "A period of time and colon surgery")
    check("i becomes I", TextCleaner.clean("i think i'm fine", "", none), "I think I'm fine")
    check("continues after sentence end", TextCleaner.clean("it works", "ld.", none), " It works")
    check("continues mid-sentence", TextCleaner.clean("and then", "llo", none), " and then")
    check("no extra space after space", TextCleaner.clean("and then", "o ", none), "and then")
    check("new line", TextCleaner.clean("new line next", "llo", none), "\nNext")
    check("new paragraph", TextCleaner.clean("one new paragraph two", "", none), "One\n\nTwo")
    check("after newline capital", TextCleaner.clean("hello", "x\n", none), "Hello")
    check("custom words", TextCleaner.clean("open meta trader today", "", words), "Open MetaTrader today")
    check("custom words 2", TextCleaner.clean("xau usd looks bullish", "", words), "XAUUSD looks bullish")
    check("custom whole word only", TextCleaner.clean("metatraders", "", words), "Metatraders")
    check("spaces collapsed", TextCleaner.clean("hello    world", "", none), "Hello world")
    check("blank in", TextCleaner.clean("   ", "", none), "")
    check("only filler", TextCleaner.clean("um", "", none), "")
    check("punctuation attaches", TextCleaner.clean("comma and so on", "llo", none), ", and so on")

    val parsed = TextCleaner.parseCustomWords("# note\n a b = C D \n\nbadline\n=x\ny=\n")
    check("parse words", parsed.joinToString("|") { it.first + ">" + it.second }, "a b>C D")
    check("parse null", TextCleaner.parseCustomWords(null).size.toString(), "0")

    println("\n${total - failures}/$total passed")
    if (failures > 0) System.exit(1)
}
