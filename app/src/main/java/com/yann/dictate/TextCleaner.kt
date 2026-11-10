package com.yann.dictate

/**
 * Pure text clean-up for dictation. No Android classes here, so it can be tested on its own.
 *
 * What it does to each phrase the speech recogniser returns:
 *  - removes "um", "uh", "erm" (only those exact words; never "ER", "mm" or "umbrella")
 *  - turns spoken commands into punctuation: full stop, comma, question mark,
 *    exclamation mark/point, new line, new paragraph
 *  - applies the user's own word list ("what it hears = what to type")
 *  - fixes "i" -> "I", spacing, and capital letters at the start of sentences
 *  - adds a leading space when it continues text that is already in the box
 *
 * Deliberately NOT turned into punctuation: "period" and "colon" (real words in normal and medical text).
 */
object TextCleaner {

    const val DEFAULT_WORDS: String =
        "# One per line:  what it hears = what to type\n" +
        "meta trader = MetaTrader\n" +
        "xau usd = XAUUSD\n"

    private val filler = Regex("(?<![\\w-])(?:um+|uh+|erm+)(?![\\w-]),?", RegexOption.IGNORE_CASE)

    private fun cmd(phrase: String): Regex =
        Regex("\\s*\\b" + phrase + "\\b\\s*", RegexOption.IGNORE_CASE)

    private val commands: List<Pair<Regex, String>> = listOf(
        Pair(cmd("full stop"), ". "),
        Pair(cmd("comma"), ", "),
        Pair(cmd("question mark"), "? "),
        Pair(cmd("exclamation mark"), "! "),
        Pair(cmd("exclamation point"), "! "),
        Pair(cmd("new paragraph"), "\n\n"),
        Pair(cmd("new line"), "\n")
    )

    private val soloI = Regex("\\bi\\b")
    private val manySpaces = Regex("[ \\t]+")
    private val spaceAroundNewline = Regex(" ?\\n ?")
    private val afterSentenceEnd = Regex("([.!?]\\s+|\\n)(\\p{Ll})")

    fun parseCustomWords(text: String?): List<Pair<String, String>> {
        if (text.isNullOrBlank()) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for (line in text.lines()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            val i = t.indexOf('=')
            if (i <= 0) continue
            val heard = t.substring(0, i).trim()
            val correct = t.substring(i + 1).trim()
            if (heard.isEmpty() || correct.isEmpty()) continue
            out.add(Pair(heard, correct))
        }
        return out
    }

    private fun atSentenceStart(preceding: String): Boolean {
        if (preceding.isEmpty()) return true
        if (preceding.last() == '\n') return true
        val t = preceding.trimEnd()
        if (t.isEmpty()) return true
        return t.last() in ".!?"
    }

    /**
     * @param raw        what the recogniser heard
     * @param preceding  the last few characters already in the text box (may be empty)
     * @param custom     the user's word list
     * @return the exact text to insert (may start with a space), or "" if nothing is left
     */
    fun clean(raw: String, preceding: String, custom: List<Pair<String, String>>): String {
        var s = raw.trim()
        if (s.isEmpty()) return ""

        s = filler.replace(s, "")

        for ((re, rep) in commands) {
            s = re.replace(s, rep)
        }

        for ((heard, correct) in custom) {
            val re = Regex(
                "(?<![\\p{L}\\p{N}])" + Regex.escape(heard) + "(?![\\p{L}\\p{N}])",
                RegexOption.IGNORE_CASE
            )
            s = re.replace(s, Regex.escapeReplacement(correct))
        }

        s = soloI.replace(s, "I")
        s = manySpaces.replace(s, " ")
        s = spaceAroundNewline.replace(s, "\n")
        s = s.trim(' ', '\t')
        if (s.isEmpty()) return ""

        s = afterSentenceEnd.replace(s) { m -> m.groupValues[1] + m.groupValues[2].uppercase() }

        if (atSentenceStart(preceding) && s[0].isLowerCase()) {
            s = s[0].uppercaseChar().toString() + s.substring(1)
        }

        if (preceding.isNotEmpty() && !preceding.last().isWhitespace() && s[0] !in ".,!?;:\n") {
            s = " " + s
        }
        return s
    }
}
