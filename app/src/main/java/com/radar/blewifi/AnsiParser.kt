package com.radar.blewifi

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface

class AnsiParser(var cols: Int, var rows: Int) {
    private var grid = Array(rows) { CharArray(cols) { ' ' } }
    private var fgGrid = Array(rows) { IntArray(cols) { Color.WHITE } }
    private var bgGrid = Array(rows) { IntArray(cols) { Color.TRANSPARENT } }
    private var boldGrid = Array(rows) { BooleanArray(cols) { false } }

    private var altGrid = Array(rows) { CharArray(cols) { ' ' } }
    private var altFgGrid = Array(rows) { IntArray(cols) { Color.WHITE } }
    private var altBgGrid = Array(rows) { IntArray(cols) { Color.TRANSPARENT } }
    private var altBoldGrid = Array(rows) { BooleanArray(cols) { false } }
    
    private var useAltBuffer = false
    private var g0Charset = "B" // B = US ASCII, 0 = DEC Line Drawing
    private var g1Charset = "0"
    private var selectedCharset = 0 // 0 for G0, 1 for G1
    
    private var pendingInput = ""
    private var dirtyRows = BooleanArray(rows) { true }
    private var rowCache = Array<Spannable?>(rows) { null }
    private var lastCursorX = -1
    private var lastCursorY = -1

    private var cursorX = 0
    private var cursorY = 0
    private var savedX = 0
    private var savedY = 0
    private var scrollTop = 0
    private var scrollBottom = rows - 1
    
    private var currentFg = Color.WHITE
    private var currentBg = Color.TRANSPARENT
    private var isBold = false
    private var isCursorVisible = true
    private var isReverseVideo = false

    fun resize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return

        val newGrid = Array(newRows) { CharArray(newCols) { ' ' } }
        val newFgGrid = Array(newRows) { IntArray(newCols) { Color.WHITE } }
        val newBgGrid = Array(newRows) { IntArray(newCols) { Color.TRANSPARENT } }
        val newBoldGrid = Array(newRows) { BooleanArray(newCols) { false } }

        val newAltGrid = Array(newRows) { CharArray(newCols) { ' ' } }
        val newAltFgGrid = Array(newRows) { IntArray(newCols) { Color.WHITE } }
        val newAltBgGrid = Array(newRows) { IntArray(newCols) { Color.TRANSPARENT } }
        val newAltBoldGrid = Array(newRows) { BooleanArray(newCols) { false } }

        val minRows = Math.min(rows, newRows)
        val minCols = Math.min(cols, newCols)

        for (y in 0 until minRows) {
            System.arraycopy(grid[y], 0, newGrid[y], 0, minCols)
            System.arraycopy(fgGrid[y], 0, newFgGrid[y], 0, minCols)
            System.arraycopy(bgGrid[y], 0, newBgGrid[y], 0, minCols)
            System.arraycopy(boldGrid[y], 0, newBoldGrid[y], 0, minCols)

            System.arraycopy(altGrid[y], 0, newAltGrid[y], 0, minCols)
            System.arraycopy(altFgGrid[y], 0, newAltFgGrid[y], 0, minCols)
            System.arraycopy(altBgGrid[y], 0, newAltBgGrid[y], 0, minCols)
            System.arraycopy(altBoldGrid[y], 0, newAltBoldGrid[y], 0, minCols)
        }

        grid = newGrid; fgGrid = newFgGrid; bgGrid = newBgGrid; boldGrid = newBoldGrid
        altGrid = newAltGrid; altFgGrid = newAltFgGrid; altBgGrid = newAltBgGrid; altBoldGrid = newAltBoldGrid

        cols = newCols; rows = newRows
        dirtyRows = BooleanArray(rows) { true }
        rowCache = Array(rows) { null }
        scrollTop = 0; scrollBottom = rows - 1
        cursorX = cursorX.coerceIn(0, cols - 1)
        cursorY = cursorY.coerceIn(0, rows - 1)
    }

    fun parse(input: String) {
        val currentInput = pendingInput + input
        pendingInput = ""
        var i = 0
        while (i < currentInput.length) {
            val char = currentInput[i]
            when (char) {
                '\u001B' -> {
                    val end = findEndSequence(currentInput, i + 1)
                    if (end == -1) {
                        pendingInput = currentInput.substring(i)
                        return
                    }
                    val sequence = currentInput.substring(i + 1, end + 1)
                    handleEscSequence(sequence)
                    i = end
                }
                '\n' -> {
                    cursorY++
                    if (cursorY > scrollBottom) {
                        scrollUp(scrollTop, scrollBottom)
                        cursorY = scrollBottom
                    }
                }
                '\r' -> cursorX = 0
                '\b' -> if (cursorX > 0) cursorX--
                '\u0007' -> { /* Bell */ }
                '\t' -> {
                    val nextTab = (cursorX / 8 + 1) * 8
                    while (cursorX < nextTab && cursorX < cols) putChar(' ')
                }
                '\u000E' -> selectedCharset = 1 // SO
                '\u000F' -> selectedCharset = 0 // SI
                else -> {
                    if (cursorX >= cols) {
                        cursorX = 0; cursorY++
                        if (cursorY > scrollBottom) { 
                            scrollUp(scrollTop, scrollBottom); cursorY = scrollBottom 
                        }
                    }
                    val charset = if (selectedCharset == 0) g0Charset else g1Charset
                    putChar(if (charset == "0") mapLineDrawing(char) else char)
                }
            }
            i++
        }
    }

    private fun findEndSequence(input: String, start: Int): Int {
        if (start >= input.length) return -1
        val next = input[start]
        return when (next) {
            '[' -> { // CSI
                for (j in start + 1 until input.length) {
                    val c = input[j]
                    if (c in '\u0040'..'\u007E') return j
                }
                -1
            }
            '(', ')' -> if (start + 1 < input.length) start + 1 else -1 // Charset designation
            else -> start
        }
    }

    private fun handleEscSequence(seq: String) {
        when {
            seq.startsWith("[") -> {
                val cmd = seq.last()
                val content = seq.substring(1, seq.length - 1)
                handleCsiSequence(content, cmd)
            }
            seq.startsWith("(") -> if (seq.length > 1) g0Charset = seq[1].toString()
            seq.startsWith(")") -> if (seq.length > 1) g1Charset = seq[1].toString()
            else -> when (seq) {
                "7" -> { savedX = cursorX; savedY = cursorY }
                "8" -> { cursorX = savedX; cursorY = savedY }
                "M" -> {
                    cursorY--
                    if (cursorY < scrollTop) {
                        scrollDown(scrollTop, scrollBottom)
                        cursorY = scrollTop
                    }
                }
                "D" -> { // Index
                    cursorY++
                    if (cursorY > scrollBottom) {
                        scrollUp(scrollTop, scrollBottom)
                        cursorY = scrollBottom
                    }
                }
                "c" -> reset() // Full reset
            }
        }
    }

    private fun handleCsiSequence(sequence: String, cmd: Char) {
        val parts = sequence.removePrefix("?").split(';')
        when (cmd) {
            'm' -> handleSgr(sequence)
            'H', 'f' -> {
                cursorY = (parts.getOrNull(0)?.toIntOrNull() ?: 1) - 1
                cursorX = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
                cursorX = cursorX.coerceIn(0, cols - 1)
                cursorY = cursorY.coerceIn(0, rows - 1)
            }
            'A' -> cursorY = (cursorY - (parts[0].toIntOrNull() ?: 1)).coerceAtLeast(0)
            'B' -> cursorY = (cursorY + (parts[0].toIntOrNull() ?: 1)).coerceAtMost(rows - 1)
            'C' -> cursorX = (cursorX + (parts[0].toIntOrNull() ?: 1)).coerceAtMost(cols - 1)
            'D' -> cursorX = (cursorX - (parts[0].toIntOrNull() ?: 1)).coerceAtLeast(0)
            'G' -> cursorX = (parts[0].toIntOrNull() ?: 1) - 1
            'd' -> cursorY = (parts[0].toIntOrNull() ?: 1) - 1
            'E' -> { cursorY += parts[0].toIntOrNull() ?: 1; cursorX = 0 }
            'F' -> { cursorY -= parts[0].toIntOrNull() ?: 1; cursorX = 0 }
            'J' -> {
                val mode = parts[0].toIntOrNull() ?: 0
                when (mode) {
                    0 -> { clearLine(cursorY, cursorX, cols); for (y in cursorY + 1 until rows) clearLine(y, 0, cols) }
                    1 -> { for (y in 0 until cursorY) clearLine(y, 0, cols); clearLine(cursorY, 0, cursorX + 1) }
                    2, 3 -> { for (y in 0 until rows) clearLine(y, 0, cols); cursorX = 0; cursorY = 0 }
                }
            }
            'K' -> {
                val mode = parts[0].toIntOrNull() ?: 0
                when (mode) {
                    0 -> clearLine(cursorY, cursorX, cols)
                    1 -> clearLine(cursorY, 0, cursorX + 1)
                    2 -> clearLine(cursorY, 0, cols)
                }
            }
            'L' -> repeat(parts[0].toIntOrNull() ?: 1) { scrollDown(cursorY, scrollBottom) }
            'M' -> repeat(parts[0].toIntOrNull() ?: 1) { scrollUp(cursorY, scrollBottom) }
            'P' -> shiftLineLeft(cursorY, cursorX, (parts[0].toIntOrNull() ?: 1))
            '@' -> shiftLineRight(cursorY, cursorX, (parts[0].toIntOrNull() ?: 1))
            'X' -> clearLine(cursorY, cursorX, cursorX + (parts[0].toIntOrNull() ?: 1))
            'S' -> repeat(parts[0].toIntOrNull() ?: 1) { scrollUp(scrollTop, scrollBottom) }
            'T' -> repeat(parts[0].toIntOrNull() ?: 1) { scrollDown(scrollTop, scrollBottom) }
            'r' -> {
                scrollTop = (parts.getOrNull(0)?.toIntOrNull() ?: 1) - 1
                scrollBottom = (parts.getOrNull(1)?.toIntOrNull() ?: rows) - 1
                scrollTop = scrollTop.coerceIn(0, rows - 1)
                scrollBottom = scrollBottom.coerceIn(0, rows - 1)
            }
            'h' -> {
                if (sequence.contains("1049")) { useAltBuffer = true; dirtyRows.fill(true) }
                if (sequence.contains("25")) isCursorVisible = true
            }
            'l' -> {
                if (sequence.contains("1049")) { useAltBuffer = false; dirtyRows.fill(true) }
                if (sequence.contains("25")) isCursorVisible = false
            }
            's' -> { savedX = cursorX; savedY = cursorY }
            'u' -> { cursorX = savedX; cursorY = savedY }
        }
    }

    private fun handleSgr(sequence: String) {
        val codes = sequence.split(';')
        var i = 0
        while (i < codes.size) {
            val code = codes[i].toIntOrNull() ?: 0
            when (code) {
                0 -> { currentFg = Color.WHITE; currentBg = Color.TRANSPARENT; isBold = false; isReverseVideo = false }
                1 -> isBold = true
                7 -> isReverseVideo = true
                22 -> isBold = false
                27 -> isReverseVideo = false
                in 30..37 -> currentFg = getAnsiColor(code - 30, false)
                in 90..97 -> currentFg = getAnsiColor(code - 90, true)
                38 -> {
                    if (i + 2 < codes.size && codes[i + 1] == "5") {
                        currentFg = getAnsiColor256(codes[i + 2].toIntOrNull() ?: 0); i += 2
                    } else if (i + 4 < codes.size && codes[i + 1] == "2") {
                        currentFg = Color.rgb(codes[i+2].toIntOrNull()?:0, codes[i+3].toIntOrNull()?:0, codes[i+4].toIntOrNull()?:0); i += 4
                    }
                }
                39 -> currentFg = Color.WHITE
                in 40..47 -> currentBg = getAnsiColor(code - 40, false)
                in 100..107 -> currentBg = getAnsiColor(code - 100, true)
                48 -> {
                    if (i + 2 < codes.size && codes[i + 1] == "5") {
                        currentBg = getAnsiColor256(codes[i + 2].toIntOrNull() ?: 0); i += 2
                    } else if (i + 4 < codes.size && codes[i + 1] == "2") {
                        currentBg = Color.rgb(codes[i+2].toIntOrNull()?:0, codes[i+3].toIntOrNull()?:0, codes[i+4].toIntOrNull()?:0); i += 4
                    }
                }
                49 -> currentBg = Color.TRANSPARENT
            }
            i++
        }
    }

    private fun mapLineDrawing(c: Char): Char {
        return when (c) {
            'j' -> '┘'; 'k' -> '┐'; 'l' -> '┌'; 'm' -> '└'; 'n' -> '┼'; 'q' -> '─'; 't' -> '├'; 'u' -> '┤'; 'v' -> '┴'; 'w' -> '┬'; 'x' -> '│'; 'a' -> '▒'; 'f' -> '°'; 'g' -> '±'; '~' -> '·'; else -> c
        }
    }

    private fun putChar(c: Char) {
        if (cursorY in 0 until rows && cursorX in 0 until cols) {
            val g = if (useAltBuffer) altGrid else grid
            val fg = if (useAltBuffer) altFgGrid else fgGrid
            val bg = if (useAltBuffer) altBgGrid else bgGrid
            val bld = if (useAltBuffer) altBoldGrid else boldGrid
            
            g[cursorY][cursorX] = c
            fg[cursorY][cursorX] = if (isReverseVideo) currentBg else currentFg
            bg[cursorY][cursorX] = if (isReverseVideo) currentFg else currentBg
            bld[cursorY][cursorX] = isBold
            dirtyRows[cursorY] = true
            cursorX++
        }
    }

    private fun shiftLineLeft(y: Int, x: Int, n: Int) {
        val count = n.coerceAtMost(cols - x)
        val g = if (useAltBuffer) altGrid else grid
        val fg = if (useAltBuffer) altFgGrid else fgGrid
        val bg = if (useAltBuffer) altBgGrid else bgGrid
        val bld = if (useAltBuffer) altBoldGrid else boldGrid
        System.arraycopy(g[y], x + count, g[y], x, cols - x - count)
        System.arraycopy(fg[y], x + count, fg[y], x, cols - x - count)
        System.arraycopy(bg[y], x + count, bg[y], x, cols - x - count)
        System.arraycopy(bld[y], x + count, bld[y], x, cols - x - count)
        g[y].fill(' ', cols - count, cols)
        fg[y].fill(Color.WHITE, cols - count, cols)
        bg[y].fill(Color.TRANSPARENT, cols - count, cols)
        bld[y].fill(false, cols - count, cols)
        dirtyRows[y] = true
    }

    private fun shiftLineRight(y: Int, x: Int, n: Int) {
        val count = n.coerceAtMost(cols - x)
        val g = if (useAltBuffer) altGrid else grid
        val fg = if (useAltBuffer) altFgGrid else fgGrid
        val bg = if (useAltBuffer) altBgGrid else bgGrid
        val bld = if (useAltBuffer) altBoldGrid else boldGrid
        System.arraycopy(g[y], x, g[y], x + count, cols - x - count)
        System.arraycopy(fg[y], x, fg[y], x + count, cols - x - count)
        System.arraycopy(bg[y], x, bg[y], x + count, cols - x - count)
        System.arraycopy(bld[y], x, bld[y], x + count, cols - x - count)
        g[y].fill(' ', x, x + count)
        fg[y].fill(currentFg, x, x + count)
        bg[y].fill(currentBg, x, x + count)
        bld[y].fill(isBold, x, x + count)
        dirtyRows[y] = true
    }

    private fun clearLine(y: Int, startX: Int, endX: Int) {
        val yIdx = y.coerceIn(0, rows - 1)
        val s = startX.coerceIn(0, cols); val e = endX.coerceIn(0, cols)
        val g = if (useAltBuffer) altGrid else grid
        val fg = if (useAltBuffer) altFgGrid else fgGrid
        val bg = if (useAltBuffer) altBgGrid else bgGrid
        val bld = if (useAltBuffer) altBoldGrid else boldGrid
        for (x in s until e) {
            g[yIdx][x] = ' '; fg[yIdx][x] = Color.WHITE; bg[yIdx][x] = Color.TRANSPARENT; bld[yIdx][x] = false
        }
        dirtyRows[yIdx] = true
    }

    private fun scrollUp(top: Int, bottom: Int) {
        val t = top.coerceIn(0, rows - 1); val b = bottom.coerceIn(0, rows - 1)
        if (t >= b) return
        val g = if (useAltBuffer) altGrid else grid
        val fg = if (useAltBuffer) altFgGrid else fgGrid
        val bg = if (useAltBuffer) altBgGrid else bgGrid
        val bld = if (useAltBuffer) altBoldGrid else boldGrid
        for (y in t until b) {
            System.arraycopy(g[y + 1], 0, g[y], 0, cols); System.arraycopy(fg[y + 1], 0, fg[y], 0, cols)
            System.arraycopy(bg[y + 1], 0, bg[y], 0, cols); System.arraycopy(bld[y + 1], 0, bld[y], 0, cols)
            dirtyRows[y] = true
        }
        g[b].fill(' '); fg[b].fill(Color.WHITE); bg[b].fill(Color.TRANSPARENT); bld[b].fill(false); dirtyRows[b] = true
    }
    
    private fun scrollDown(top: Int, bottom: Int) {
        val t = top.coerceIn(0, rows - 1); val b = bottom.coerceIn(0, rows - 1)
        if (t >= b) return
        val g = if (useAltBuffer) altGrid else grid
        val fg = if (useAltBuffer) altFgGrid else fgGrid
        val bg = if (useAltBuffer) altBgGrid else bgGrid
        val bld = if (useAltBuffer) altBoldGrid else boldGrid
        for (y in b downTo t + 1) {
            System.arraycopy(g[y - 1], 0, g[y], 0, cols); System.arraycopy(fg[y - 1], 0, fg[y], 0, cols)
            System.arraycopy(bg[y - 1], 0, bg[y], 0, cols); System.arraycopy(bld[y - 1], 0, bld[y], 0, cols)
            dirtyRows[y] = true
        }
        g[t].fill(' '); fg[t].fill(Color.WHITE); bg[t].fill(Color.TRANSPARENT); bld[t].fill(false); dirtyRows[t] = true
    }

    private fun reset() {
        grid.forEach { it.fill(' ') }; fgGrid.forEach { it.fill(Color.WHITE) }; bgGrid.forEach { it.fill(Color.TRANSPARENT) }; boldGrid.forEach { it.fill(false) }
        altGrid.forEach { it.fill(' ') }; altFgGrid.forEach { it.fill(Color.WHITE) }; altBgGrid.forEach { it.fill(Color.TRANSPARENT) }; altBoldGrid.forEach { it.fill(false) }
        cursorX = 0; cursorY = 0; useAltBuffer = false; g0Charset = "B"; g1Charset = "0"; selectedCharset = 0
        currentFg = Color.WHITE; currentBg = Color.TRANSPARENT; isBold = false; isReverseVideo = false; dirtyRows.fill(true)
    }

    fun render(): Spannable {
        if (isCursorVisible && (cursorX != lastCursorX || cursorY != lastCursorY)) {
            if (lastCursorY in 0 until rows) dirtyRows[lastCursorY] = true
            if (cursorY in 0 until rows) dirtyRows[cursorY] = true
            lastCursorX = cursorX; lastCursorY = cursorY
        }
        
        val builder = SpannableStringBuilder()
        for (y in 0 until rows) {
            if (dirtyRows[y] || y >= rowCache.size || rowCache[y] == null) {
                rowCache[y] = renderRow(y)
                dirtyRows[y] = false
            }
            builder.append(rowCache[y])
            if (y < rows - 1) builder.append('\n')
        }
        return builder
    }

    private fun renderRow(y: Int): Spannable {
        val builder = SpannableStringBuilder()
        var x = 0
        val g = if (useAltBuffer) altGrid else grid
        val fgG = if (useAltBuffer) altFgGrid else fgGrid
        val bgG = if (useAltBuffer) altBgGrid else bgGrid
        val bldG = if (useAltBuffer) altBoldGrid else boldGrid
        while (x < cols) {
            if (isCursorVisible && y == cursorY && x == cursorX) {
                val char = g[y][x]
                val spanStart = builder.length
                builder.append(if (char == '\u0000' || char == ' ') ' ' else char)
                builder.setSpan(BackgroundColorSpan(Color.GREEN), spanStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(ForegroundColorSpan(Color.BLACK), spanStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                x++; continue
            }
            val start = x; val fg = fgG[y][x]; val bg = bgG[y][x]; val bold = bldG[y][x]
            while (x < cols && fgG[y][x] == fg && bgG[y][x] == bg && bldG[y][x] == bold && !(isCursorVisible && y == cursorY && x == cursorX)) x++
            val spanStart = builder.length
            for (i in start until x) {
                val c = g[y][i]
                builder.append(if (c == '\u0000') ' ' else c)
            }
            if (fg != Color.WHITE) builder.setSpan(ForegroundColorSpan(fg), spanStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (bg != Color.TRANSPARENT && bg != Color.BLACK) builder.setSpan(BackgroundColorSpan(bg), spanStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (bold) builder.setSpan(StyleSpan(Typeface.BOLD), spanStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return builder
    }

    private fun getAnsiColor256(index: Int): Int {
        if (index in 0..15) return getAnsiColor(index % 8, index >= 8)
        if (index in 16..231) {
            val i = index - 16; val r = (i / 36) * 51; val g = ((i % 36) / 6) * 51; val b = (i % 6) * 51
            return Color.rgb(r, g, b)
        }
        if (index in 232..255) { val g = (index - 232) * 10 + 8; return Color.rgb(g, g, g) }
        return Color.WHITE
    }

    private fun getAnsiColor(index: Int, bright: Boolean): Int {
        return when (index) {
            0 -> if (bright) Color.parseColor("#7F7F7F") else Color.BLACK
            1 -> if (bright) Color.parseColor("#FF0000") else Color.parseColor("#CD0000")
            2 -> if (bright) Color.parseColor("#00FF00") else Color.parseColor("#00CD00")
            3 -> if (bright) Color.parseColor("#FFFF00") else Color.parseColor("#CDCD00")
            4 -> if (bright) Color.parseColor("#5C5CFF") else Color.parseColor("#0000EE")
            5 -> if (bright) Color.parseColor("#FF00FF") else Color.parseColor("#CD00CD")
            6 -> if (bright) Color.parseColor("#00FFFF") else Color.parseColor("#00CDCD")
            7 -> if (bright) Color.WHITE else Color.parseColor("#E5E5E5")
            else -> Color.WHITE
        }
    }
}
