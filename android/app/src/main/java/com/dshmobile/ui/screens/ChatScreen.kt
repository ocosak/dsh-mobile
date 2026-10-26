package com.dshmobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dshmobile.data.ChatMessage
import com.dshmobile.data.ToolCall
import com.dshmobile.ui.ChatViewModel
import com.dshmobile.ui.theme.errorButtonColor

/** Tool-call status in Chinese (the host only sends the enum name). */
private val UserBubbleShape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)

private const val COPIED_RESET_MS = 2000L
// 同会话重载弱提示的延迟：局域网重载通常 <300ms，直接给提示会闪一下；
// 超过 800ms 才算慢重载、值得提示，800ms 在两者之间。
private const val RELOAD_INDICATOR_DELAY_MS = 800L
private const val BUBBLE_MAX_WIDTH_FRACTION = 0.85f
private const val COLLAPSE_THRESHOLD = 8
private const val COLLAPSE_VISIBLE = 6
private const val INPUT_MAX_LINES = 5
// 底部跟随与回到底部按钮共用的边界（项数）：末可见项离末尾 < 该值算在底部
// （跟随）、>= 该值显示按钮；两处必须同值，否则跟随与按钮之间会出现都没
// 覆盖的缺口（跟随已停、按钮未出）或双覆盖——抽成常量，改一处即两处。
// 与 TopBarSideSize 同款“两侧共用一值防改漏”口径。
private const val NEAR_BOTTOM_THRESHOLD = 2
// 顶栏两侧占位：返回钮与右侧空白同宽，标题才能真正居中；两侧都用它，
// 改一边尺寸时另一边不会忘改、标题不再偏。
private val TopBarSideSize = 48.dp
private val SEND_BUTTON_SIZE = 36.dp

private fun toolStatusText(status: ToolCall.Status): String = when (status) {
    ToolCall.Status.PENDING -> "待处理"
    ToolCall.Status.APPROVED -> "已批准"
    ToolCall.Status.DENIED -> "已拒绝"
    ToolCall.Status.RUNNING -> "进行中"
    ToolCall.Status.DONE -> "已完成"
    ToolCall.Status.FAILED -> "失败"
    ToolCall.Status.UNKNOWN -> "未知"
}

private data class MdBlock(val code: Boolean, val text: String)

/** Split assistant text into code/non-code blocks on ``` fences (leading spaces
 * allowed); an unclosed fence at the end is treated as code; trailing newlines
 * are trimmed from every block. */
private fun splitMdBlocks(text: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val current = StringBuilder()
    var inCode = false
    for (line in text.lines()) {
        if (line.trimStart().startsWith("```")) {
            if (current.isNotEmpty()) {
                out += MdBlock(inCode, current.toString().trimEnd('\n'))
                current.clear()
            }
            inCode = !inCode
        } else {
            current.append(line).append('\n')
        }
    }
    if (current.isNotEmpty()) out += MdBlock(inCode, current.toString().trimEnd('\n'))
    return out
}

/** **bold**, *italic* and `code` are interpreted; unmatched markers stay literal, never dropped. */
// scheme 不区分大小写：主机消息里出现 "HTTP://..." 时旧正则认不出来、网址点不开；
 // 链接打开走系统浏览器意图，scheme 大小写不影响解析。
private val UrlRegex = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)

// markdown 链接 [text](url)：仅 http(s)，括号内允许单层配对括号（维基式
// "PC_Tools_(Central_Point_Software)"，多对同层亦可）；嵌套括号、非 http
// scheme 不匹配，回退裸网址处理。链接文本至少 1 字（空文本回退，见下）。
private val MdLinkRegex = Regex("""\[([^\]]+)\]\((https?://[^()\s]*(?:\([^()\s]*\)[^()\s]*)*)\)""")

// 网址末尾常跟的标点不是网址的一部分（中文句号、逗号、括号等）：
// 不去掉，点链接会带着标点打不开；ASCII 右括号/方括号只在网址里没有
// 左配对时才去（维基式网址里的括号是正文，不能误伤）。
private fun stripUrlTail(url: String): String {
    var u = url
    while (u.isNotEmpty()) {
        val last = u.last()
        // 补 ASCII 冒号（句末 "链接：https://x.com/foo:" 的尾冒号不是端口，
        // 端口后必有数字）与中文全角括号/书名号（"）》" 是常见包裹结尾，
        // "（《" 在尾部同样不可能是网址正文）；与已有中文标点同一逻辑。
        if (last in ".,;:!?。、，；：】」』）》（《") u = u.dropLast(1)
        else if (last == ')' && '(' !in u) u = u.dropLast(1)
        else if (last == ']' && '[' !in u) u = u.dropLast(1)
        // 花括号仿圆/方括号的配对逻辑：无左配对时才是包裹符；引号与尖括号
        // 在合法网址里必须百分号编码，字面出现在尾部必是包裹符，直接去掉。
        // 否则“https://x.com/foo”这类被引号包住的网址会把引号也链进链接、点不开。
        else if (last == '}' && '{' !in u) u = u.dropLast(1)
        else if (last == '"' || last == '\'' || last == '>') u = u.dropLast(1)
        else break
    }
    return u
}

// 助手正文分段处理：markdown 链接优先（链接里含网址，若先按网址切分，
// "[点击这里](" 与 ")" 会残成字面括号、网址本身成链接文本）；链接间的
// 空隙走裸网址切分（见 appendUrlSplit）；无 scheme 的 www. 不认，避免把
// 版本号之类误当链接。用户气泡与审批描述不走这里（纯文本 + 裸网址即可）。
// 链接注解的 tag：经典字符串注解写法（项目 Compose 1.7 里没有 LinkAnnotation），
// 渲染处用 ClickableText 按偏移取注解、走系统浏览器打开。
private const val LINK_URL_TAG = "URL"

// ClickableText 的 onClick 只给偏移：按偏移取 URL 注解，点中链接才打开，
// 点中普通文字时注解为空、什么都不做（不吞点击、不误跳）。
private fun openAnnotatedUrl(text: AnnotatedString, offset: Int, uriHandler: UriHandler) {
    text.getStringAnnotations(LINK_URL_TAG, offset, offset).firstOrNull()?.let { uriHandler.openUri(it.item) }
}

private fun inlineAnnotated(text: String, codeBg: Color, linkColor: Color): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var cursor = 0
    for (match in MdLinkRegex.findAll(text)) {
        if (match.range.first > cursor) appendUrlSplit(builder, text.substring(cursor, match.range.first), codeBg, linkColor)
        val linkText = match.groupValues[1]
        val linkUrl = match.groupValues[2]
        if (linkText.isNotBlank()) {
            // 链接文本走 appendMarkdown：粗体/斜体/代码在链接文本里同样生效；
            // 目标网址用括号界定的原文（不 strip——界定已精确，句末标点若在括号
            // 内就是作者写的地址本身，不像裸网址那样有歧义）。
            val linkStart = builder.length
            appendMarkdown(builder, linkText, codeBg)
            if (builder.length > linkStart) {
                builder.addStringAnnotation(LINK_URL_TAG, linkUrl, linkStart, builder.length)
                builder.addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), linkStart, builder.length)
            }
        } else {
            // 空链接文本回退裸网址处理：网址仍可点、括号字面，与此前行为一致。
            appendUrlSplit(builder, match.value, codeBg, linkColor)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) appendUrlSplit(builder, text.substring(cursor), codeBg, linkColor)
    return builder.toAnnotatedString()
}

// 裸网址切分（原 inlineAnnotated 的主体）：先按网址切分，非网址段走
// markdown 解析，网址段挂字符串注解（渲染处 ClickableText 点开浏览器）。
private fun appendUrlSplit(builder: AnnotatedString.Builder, text: String, codeBg: Color, linkColor: Color) {
    var cursor = 0
    for (match in UrlRegex.findAll(text)) {
        if (match.range.first > cursor) appendMarkdown(builder, text.substring(cursor, match.range.first), codeBg)
        val url = stripUrlTail(match.value)
        val tail = match.value.substring(url.length)
        if (url.isNotBlank()) {
            val linkStart = builder.length
            builder.append(url)
            builder.addStringAnnotation(LINK_URL_TAG, url, linkStart, builder.length)
            builder.addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), linkStart, builder.length)
        }
        if (tail.isNotEmpty()) builder.append(tail)
        cursor = match.range.last + 1
    }
    if (cursor < text.length) appendMarkdown(builder, text.substring(cursor), codeBg)
}

// 用户气泡不解析 markdown（用户打的星号是原文），但网址同样可点。
// 链接色调用处显式给：审批描述在浅灰底上用主色蓝；用户气泡是系统蓝底，
// 蓝链接在蓝底上基本看不见，所以用户气泡调用处必须传 onPrimary（白字 +
// 下划线），链接在蓝底上才看得见、点得了。
private fun linkified(text: String, linkColor: Color): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var cursor = 0
    for (match in UrlRegex.findAll(text)) {
        if (match.range.first > cursor) builder.append(text.substring(cursor, match.range.first))
        val url = stripUrlTail(match.value)
        val tail = match.value.substring(url.length)
        if (url.isNotBlank()) {
            val linkStart = builder.length
            builder.append(url)
            builder.addStringAnnotation(LINK_URL_TAG, url, linkStart, builder.length)
            builder.addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), linkStart, builder.length)
        }
        if (tail.isNotEmpty()) builder.append(tail)
        cursor = match.range.last + 1
    }
    if (cursor < text.length) builder.append(text.substring(cursor))
    return builder.toAnnotatedString()
}

private fun appendMarkdown(builder: AnnotatedString.Builder, text: String, codeBg: Color) {
    var i = 0
    while (i < text.length) {
        when {
            // 删除线（GFM 行内标记，与粗体/斜体同模式）：未闭合时按字面
            // 保留 "~~"，不丢字符；用户气泡不走这里（纯文本，~~ 字面）。
            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end > i + 2) {
                    // 内容递归解析：以前原样 append，~~ 内再有 **/* 会露字面标记；
                    // 递归后外层样式（删除线）与内层样式叠加，行内代码分支不递归
                    // （代码内容本就要求字面）。内容恒短于输入，递归必终止。
                    builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendMarkdown(builder, text.substring(i + 2, end), codeBg) }
                    i = end + 2
                } else {
                    builder.append("~~")
                    i += 2
                }
            }
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    // 同删除线分支：内容递归，粗体内的斜体/删除线叠加生效。
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendMarkdown(builder, text.substring(i + 2, end), codeBg) }
                    i = end + 2
                } else {
                    builder.append("**")
                    i += 2
                }
            }
            text[i] == '*' && (i + 1 >= text.length || text[i + 1] != '*') -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1) {
                    // 同上：内容递归，斜体内的粗体/删除线叠加生效。
                    builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendMarkdown(builder, text.substring(i + 1, end), codeBg) }
                    i = end + 1
                } else {
                    builder.append('*')
                    i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    builder.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    builder.append('`')
                    i++
                }
            }
            else -> {
                builder.append(text[i])
                i++
            }
        }
    }
}

/** Minimal markdown for assistant replies: fences, headers (H1-H4, H4 is bold body),
 * bullets (-, *, +), quotes (> ), horizontal rules (---, ***, ___), bold, italic,
 * strikethrough (~~), inline code. H2/H3 share titleMedium: the type
 * scale has no 20pt slot, so no separate H3 size. */
private sealed interface AssistRow {
    data class Code(val text: String) : AssistRow
    data class Line(val kind: Kind, val text: AnnotatedString) : AssistRow
    enum class Kind { TITLE_LARGE, TITLE_MEDIUM, BOLD_BODY, BULLET, QUOTE, HR, BODY, BLANK }
}

// 渲染行预计算：流式生成时每来一个增量都会重组，整段 markdown 解析
// （分块 + 逐字符号）若每次重组都重跑，长回复就是 O(n²)；remember 到
// 文本与代码底色上，只有正文变了才重算，复制按钮等无关状态变化不再重跑。
private fun parseAssistRows(text: String, codeBg: Color, linkColor: Color): List<AssistRow> {
    val rows = mutableListOf<AssistRow>()
    for (block in splitMdBlocks(text)) {
        if (block.code) {
            rows += AssistRow.Code(block.text)
            continue
        }
        for (line in block.text.lines()) {
            // 前导空格不影响标题/项目符号判定：助手常发带缩进的嵌套列表
            // （"  - 子项"）与缩进标题，以前按原行起首判、前缀对不上就按
            // 正文原样显示，嵌套列表会挂着 "- " 字面标记；先去前导空格再判，
            // 余文仍用去空格后的（嵌套层级不保留——最小 markdown 不分层级，
            // 平铺圆点比挂字面标记干净）。未匹配时仍用原行（保留缩进，如
            // 四空格代码行），不丢空白。
            val trimmed = line.trimStart()
            when {
                line.isBlank() -> rows += AssistRow.Line(AssistRow.Kind.BLANK, AnnotatedString(""))
                // 标题行余文为空时（如单独一行 "# "）不按标题渲染：否则画一个
                // 空的 28dp 标题行、正文里多一段空白 gap；落回正文分支按字面
                // 显示 "# "，与“未匹配标记保持字面、不丢弃”的既定原则一致。
                // 项目符号分支早已有同样的 isNotBlank 守卫，这里补齐标题分支。
                trimmed.startsWith("# ") && trimmed.removePrefix("# ").isNotBlank() -> rows += AssistRow.Line(AssistRow.Kind.TITLE_LARGE, inlineAnnotated(trimmed.removePrefix("# "), codeBg, linkColor))
                trimmed.startsWith("## ") && trimmed.removePrefix("## ").isNotBlank() -> rows += AssistRow.Line(AssistRow.Kind.TITLE_MEDIUM, inlineAnnotated(trimmed.removePrefix("## "), codeBg, linkColor))
                trimmed.startsWith("### ") && trimmed.removePrefix("### ").isNotBlank() -> rows += AssistRow.Line(AssistRow.Kind.TITLE_MEDIUM, inlineAnnotated(trimmed.removePrefix("### "), codeBg, linkColor))
                trimmed.startsWith("#### ") && trimmed.removePrefix("#### ").isNotBlank() -> rows += AssistRow.Line(AssistRow.Kind.BOLD_BODY, inlineAnnotated(trimmed.removePrefix("#### "), codeBg, linkColor))
                (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) && trimmed.substring(2).isNotBlank() ->
                    rows += AssistRow.Line(AssistRow.Kind.BULLET, inlineAnnotated(trimmed.substring(2), codeBg, linkColor))
                // 引用行（单层 "> "，与项目符号同款单层、不分层级）：
                // 余文为空（如单独一行 "> "）落回正文按字面显示，与标题/项目
                // 符号的空余文守卫同款；">text"（无空格）与 ">>" 嵌套不认、
                // 按正文原样显示，不丢内容。
                trimmed.startsWith("> ") && trimmed.removePrefix("> ").isNotBlank() ->
                    rows += AssistRow.Line(AssistRow.Kind.QUOTE, inlineAnnotated(trimmed.removePrefix("> "), codeBg, linkColor))
                // 分隔线（整行 3 个以上同种字符 -/*/_，CommonMark 的 HR）：
                // 不是项目符号（无空格）也不是正文，单独成行时几乎必是分隔线；
                // 行内出现（如 "a---b"）或不足 3 个（如 "--"）不认，仍走正文。
                (trimmed.length >= 3 && (trimmed.all { it == '-' } || trimmed.all { it == '*' } || trimmed.all { it == '_' })) ->
                    rows += AssistRow.Line(AssistRow.Kind.HR, AnnotatedString(""))
                else -> rows += AssistRow.Line(AssistRow.Kind.BODY, inlineAnnotated(line, codeBg, linkColor))
            }
        }
    }
    return rows
}

@Composable
private fun AssistantMessageText(text: String) {
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    val uriHandler = LocalUriHandler.current
    val rows = remember(text, codeBg, linkColor) { parseAssistRows(text, codeBg, linkColor) }
    Column {
        for (row in rows) {
            when (row) {
                is AssistRow.Code -> {
                    Surface(
                        // 代码块占满内容宽：以前没给 fillMaxWidth，短代码块只包文字宽、
                        // 看着像行内标签，与“代码块占满内容宽”的设计对不上。
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = codeBg,
                    ) {
                        // 代码文字显式指定 onSurface：Surface 的默认内容色是灰，代码是正文不能是灰。
                        Text(
                            text = row.text,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                is AssistRow.Line -> when (row.kind) {
                    // 空行不是零高度：以前空行走 else 分支画空 Text（高度 0），
                    // 段落之间的空行直接塌掉、正文挤成一团；给 8dp 间距断开段落。
                    AssistRow.Kind.BLANK -> Spacer(modifier = Modifier.height(8.dp))
                    // 消息内的 markdown 标题同样给 heading 语义：页标题与分组头
                    // 已有 heading（R228），消息正文里的标题不给、TalkBack 就没法
                    // 按标题在长回复里跳转。
                    AssistRow.Kind.TITLE_LARGE -> ClickableText(row.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }, onClick = { offset -> openAnnotatedUrl(row.text, offset, uriHandler) })
                    AssistRow.Kind.TITLE_MEDIUM -> ClickableText(row.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }, onClick = { offset -> openAnnotatedUrl(row.text, offset, uriHandler) })
                    AssistRow.Kind.BOLD_BODY -> ClickableText(row.text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), onClick = { offset -> openAnnotatedUrl(row.text, offset, uriHandler) })
                    AssistRow.Kind.BULLET -> Row(verticalAlignment = Alignment.Top) {
                        Text("•", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.width(8.dp))
                        ClickableText(row.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), onClick = { offset -> openAnnotatedUrl(row.text, offset, uriHandler) })
                    }
                    // 引用：灰色 3dp 竖线 + 8dp 间隙，与项目符号的“• + 8dp”
                    // 同宽节奏；Row 定高 IntrinsicSize.Max 使竖线（fillMaxHeight）
                    // 撑满多行引用文本高（无内容的 Box 在不定高 Row 里会塌成 0）。
                    // 竖线是形状信号（不只靠颜色），灰字在两主题下均过 WCAG AA。
                    AssistRow.Kind.QUOTE -> Row(modifier = Modifier.height(IntrinsicSize.Max), verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier.width(3.dp).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(1.5.dp)),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ClickableText(row.text, style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(1f), onClick = { offset -> openAnnotatedUrl(row.text, offset, uriHandler) })
                    }
                    // 分隔线：1dp 细线 + 上下 8dp 间隙，与代码块的 vertical 8dp
                    // 同节奏；细线用 outline（与标签栏分隔线同款语义色，1dp 在
                    // 正文里比 0.5dp 更可见）。修饰符顺序：padding 在外给间隙、
                    // height 定线高、background 在内只画线（不染间隙）。
                    AssistRow.Kind.HR -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp)
                            .background(MaterialTheme.colorScheme.outline),
                    )
                    AssistRow.Kind.BODY -> ClickableText(row.text, style = MaterialTheme.typography.bodyLarge, onClick = { offset -> openAnnotatedUrl(row.text, offset, uriHandler) })
                }
            }
        }
    }
}

/** 工具调用摘要行：常驻一行“已用 N 个工具 · 首个工具名”，点开看各工具名与实发状态；
 * 不是常驻大卡片，也不伪造主机没发的数据。
 */
@Composable
private fun ToolCallsSummary(toolCalls: List<ToolCall>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    if (toolCalls.isEmpty()) return
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Row(
            // 展开态报读：TalkBack 只读“已用 N 个工具，按钮”听不出是展开还是收起，
            // 点开后详情卡出现、收起后消失，但状态本身没报出来；stateDescription
            // 让它读成“已用 N 个工具，按钮，已展开/已收起”。
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 15.dp).semantics { stateDescription = if (expanded) "已展开" else "已收起" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                // 首个“有名字”的工具：以前只看第一个，第一个名字为空时即使后面
                // 的工具有名字也不显示，只剩“已用 N 个工具”；往后找第一个非空名。
                text = "已用 ${toolCalls.size} 个工具" + toolCalls.firstOrNull { it.name.isNotBlank() }?.name?.let { " · $it" }.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
            toolCalls.forEach { tc ->
                // 工具详情卡不是气泡，不适用气泡的 85% 规则；它走卡片模式
                // （与 IosCard、代码块一样占满内容宽）。以前固定 300dp，
                // 宽屏上卡片比正文窄一截，与正文宽度不一致，改成满宽。
                Surface(
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("工具：${tc.name.ifBlank { "未知" }}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(toolStatusText(tc.status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
/** 对话页（标准 iOS + 新视频的聊天语言）：顶部单行栏（返回 + 居中会话标题）、
 * 用户消息系统蓝气泡、AI 回复纯文本不套气泡；生成中先显示“思考中…”轮换状态，
 * 工具调用收成一行摘要（点开看详情，名字和状态都来自主机实发数据）；历史多时
 * 先只看最近几条，点“查看更早的消息”再展开；底部药丸输入框里带模型药丸。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    sessionTitle: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onModelChange: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    // 未发送草稿用 rememberSaveable：转屏/进程重建不丢已打的字（只丢已发送的
    // 不会丢，发送失败的草稿另有 failedDraft 兜底）；切会话仍以 sessionId 为键清空。
    var input by rememberSaveable(sessionId) { mutableStateOf("") }
    // 发送失败时把原文本还回输入框，免得重打（传输中断、主机报错、未配对三路
    // 都会记草稿）；只认本会话的，别会话的失败不串到这里（切回原会话时再恢复）。
    // 只在输入框空白时恢复：失败到达前用户若已打了新字，无条件恢复会把新字
    // 覆盖掉（旧文本已在列表里以用户气泡留着，丢的只是预填便利；新字是当前
    // 意图，优先保新字）；跳过恢复时仍清掉草稿，不留过期状态。
    // 输入框焦点：快捷药丸填入与失败草稿恢复时直接聚焦，用户不用再点一次输入框。
    // 声明必须在首次使用（失败草稿恢复的 LaunchedEffect）之前：同一函数内后声明的
    // 局部变量在前面用不到，编译报找不到。
    val inputFocus = remember { FocusRequester() }
    val failedDraft = state.failedDraft
    LaunchedEffect(failedDraft) {
        if (failedDraft != null && failedDraft.first == sessionId) {
            // 空白草稿不恢复、但也要清掉：以前 isNotBlank 在外层门里，空白草稿
            // 既不恢复也不清、会一直挂着（目前写草稿处都用 cleanText、到不了
            // 空白，是状态机缺口而非用户可见问题；本会话匹配的分支一律清掉）。
            if (failedDraft.second.isNotBlank() && input.isBlank()) {
                input = failedDraft.second
                // 失败还回输入框后直接聚焦：用户不用再点一次输入框就能改完重发。
                inputFocus.requestFocus()
            }
            viewModel.clearFailedDraft()
        }
    }
    // 按会话区分：历史消息 id 是 h-序号，各会话会重复，直接 remember 会串。
    var copiedId by remember(sessionId) { mutableStateOf<String?>(null) }
    // 复制序号：同条 2 秒内重复点“复制”时 copiedId 不变、效果键不变、旧计时
    // 不重启——第二次复制的反馈只剩零头（第一次计时的尾巴），用户会以为
    // 第二下没点上；序号每次复制都 +1、效果键一定变、计时一定重启，2 秒
    // 反馈从最近一次复制算起。计时到后只清 copiedId、不动序号，不循环。
    var copiedSeq by remember(sessionId) { mutableStateOf(0) }
    // “已复制”只是瞬时反馈：2 秒后自动恢复成“复制”，免得十分钟后还显示已复制。
    LaunchedEffect(copiedSeq) {
        if (copiedId != null) {
            delay(COPIED_RESET_MS)
            copiedId = null
        }
    }
    // 弹层开关与草稿都用 rememberSaveable：转屏时弹层不闪退、已打的模型名不丢；
    // 先前只有输入框草稿是 Saveable，换模型弹层转屏会直接关掉。
    var showModelDialog by rememberSaveable { mutableStateOf(false) }
    var modelDraft by rememberSaveable { mutableStateOf("") }
    // 展开状态也 survive 转屏：顶层状态、不在 LazyColumn item 里，Saveable 安全；
    // 以前转屏会把已展开的长历史重新折起来。
    var showAll by rememberSaveable(sessionId) { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 打开会话即加载历史（桌面端行为）；切会话时重新加载。
    LaunchedEffect(sessionId) { viewModel.loadHistory(sessionId) }
    // 离开聊天页停掉流：只断客户端接收，不叫停主机任务；
    // 回来时 loadHistory 会重新拉历史，loadHistory 开头也会先停掉旧流。
    DisposableEffect(Unit) { onDispose { viewModel.stopGenerating() } }

    // 历史折叠：超过 8 条时先只看最近 6 条，和 Codex 的“更早消息”一致。
    val visibleMessages = remember(state.messages, showAll) {
        if (showAll || state.messages.size <= COLLAPSE_THRESHOLD) state.messages
        else state.messages.takeLast(COLLAPSE_VISIBLE)
    }
    val collapsedCount = state.messages.size - visibleMessages.size

    // 首次加载历史后定位到底部（桌面端打开会话的行为）；之后有新消息
    // 且用户本来就在底部时，跟随到底部；用户往上翻时不抢滚动。
    // 转屏后仍回到已读位置：以前是普通 remember，转屏重组后变回 false、
    // 效果重跑会把用户从正在读的旧消息一把拉回底部；Saveable 在转屏时恢复
    // true（走“已在底部才跟随”分支）、重进会话时是新组合（false，走首次定位到底部），
    // 两者正好分开。LazyListState 本身就是 Saveable，位置恢复 + 本标志配合才完整。
    var initialScrollDone by rememberSaveable(sessionId) { mutableStateOf(false) }
    // 注意：键不能只用 visibleMessages.size。流式生成时消息条数不变、
    // 只是最后一条的正文在变长，用 size 作键就不会跟随滚动，用户得
    // 自己往下翻才能看到新生成的文字；用整个列表作键，文本一变就
    // 重新判断（已在底部才跟随，往上翻了就不抢滚动）。
    // 折叠行是否出现与 LazyColumn 里的条件同款：collapsedCount > 0，或已展开
    // 且消息超阈值（此时 collapsedCount 已归零、行仍在）；只按 collapsedCount
    // 算会在展开态漏掉这一行，初始定位滚到倒数第二条（跟随分支滚 total-1 不
    // 受影响，但等待下限也应与实际项数一致，两处同改）。
    val hasFoldRow = collapsedCount > 0 || (showAll && state.messages.size > COLLAPSE_THRESHOLD)
    LaunchedEffect(visibleMessages, showAll) {
        if (!initialScrollDone && visibleMessages.isNotEmpty()) {
            val expected = visibleMessages.size + if (hasFoldRow) 1 else 0
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= expected }
            listState.scrollToItem(expected - 1)
            initialScrollDone = true
        } else {
            // 跟随前先等布局追上新内容：布局信息滞后于组合（初始分支已用
            // snapshotFlow 等过），跟随分支直接读可能拿到旧总数——新消息刚进来
            // 时滚到倒数第二条就停，且“已在底部”判定也跟着错；等总数 >= 期望
            // 项数再读、再判、再滚。增量（无新项）时总数早已达标，等待立即返回、
            // 不拖慢流式跟随；效果键一变（新消息/展开）旧等待即取消，不残留。
            val expected = visibleMessages.size + if (hasFoldRow) 1 else 0
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= expected }
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0 && visibleMessages.isNotEmpty()) {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                // 跟随条件放宽 2 项：最后一条半可见时也算在底部，否则刚生成时用户得手动翻。
                if (lastVisible >= total - NEAR_BOTTOM_THRESHOLD) listState.scrollToItem(total - 1)
            }
        }
    }

    // 换模型是底部弹层：Material 3 的 ModalBottomSheet 默认自带拖拽小横条，
    // 这里不再自己再画一个，否则会出现上下两个横条。右上角 X + 保存/取消保留。
    if (showModelDialog) {
        ModalBottomSheet(
            // 圆角与重命名弹层、删除对话框同理：默认 28dp 太圆，给 medium（12dp）。
            shape = MaterialTheme.shapes.medium,
            onDismissRequest = { showModelDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = SheetContentModifier) {
                SheetHeader(title = "切换模型", onClose = { showModelDialog = false })
                AppTextField(
                    value = modelDraft,
                    onValueChange = { modelDraft = it },
                    placeholder = "模型名（留空用主机默认）",
                    modifier = Modifier.padding(top = 12.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                PrimaryButton(
                    text = "保存",
                    onClick = { onModelChange(modelDraft.trim()); showModelDialog = false },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showModelDialog = false },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("取消") }
            }
        }
    }

    // 标签栏在聊天详情页按 iOS 规范隐藏，这里不再给它留 96dp 空隙。
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // 新视频的顶部单行栏：左返回、中间居中会话标题、右 48dp 占位保居中；
        // 模型选择改到下面的输入框里（新视频里模型药丸就在输入区）。
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(TopBarSideSize)) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "返回")
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    // 导航参数缺省时是“会话”，但空串标题不会触发缺省；
                    // 空标题会让顶栏空白一片，这里兜底回“会话”。
                    sessionTitle.ifBlank { "会话" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    // 页标题同样给 heading 语义：会话页大标题已有，聊天页顶栏标题
                    // 是同级的页面标题，TalkBack 按标题导航时不应跳过这一页。
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(modifier = Modifier.size(TopBarSideSize))
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
        // 同会话重载弱提示：R251 的 SWR 之后，同会话重载时旧消息留着、
        // historyLoading 为 true，但没有任何弱提示——慢主机（隧道、冷启动）
        // 要 1-3 秒，用户会对着旧消息干等、不知道新消息正在路上。
        // 快重载（局域网通常 <300ms）直接给提示会闪一下，所以延迟 800ms：
        // 800ms 内加载完，效果被键变化取消、提示全程不出现；超过 800ms
        // 还在加载才显示。提示是 Box 顶部的覆盖药丸（与回到底部同款），
        // 不占列表位置、不推内容；转屏后效果重跑，仍在加载就再等 800ms 显示。
        var showReloading by remember { mutableStateOf(false) }
        LaunchedEffect(state.historyLoading, state.messages.isNotEmpty()) {
            if (state.historyLoading && state.messages.isNotEmpty()) {
                delay(RELOAD_INDICATOR_DELAY_MS)
                showReloading = true
            } else {
                showReloading = false
            }
        }
        // 消息列包一层 Box，好在右下角叠一个“回到底部”按钮（见列尾）。
        Box(modifier = Modifier.weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // 展开后仍保留这一行切回“收起”：以前展开后 collapsedCount 归零、行直接消失，
            // 用户再也折不回去，只能退出重进；现在行常驻、可来回切换。
            // 但历史重载后消息可能已少于折叠阈值（showAll 还残留 true）：那时没有
            // 可折内容，行不应出现，否则会挂一行无意义的“收起”。
            if (collapsedCount > 0 || (showAll && state.messages.size > COLLAPSE_THRESHOLD)) {
                item {
                    // 行高只由 Text 的内边距给：以前 Box 也补了一份 vertical 15dp，
                    // 两份叠加行高达 78dp，注释本意是 18 + 15 + 15 = 48dp；删 Box
                    // 那份，触控区仍是 Text 的 48dp（clickable 在外、含内边距）。
                    Box(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { showAll = !showAll }, contentAlignment = Alignment.Center) {
                        Text(
                            text = if (showAll) "收起更早消息 ›" else "查看更早的 $collapsedCount 条消息 ›",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // 整行都可点（以前只有文字本身可点，行左右空白点不动）；
                            // 小字链接也要 48dp 触控高度（行高 18dp + 上下 15dp）。
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 15.dp),
                        )
                    }
                }
            }
            itemsIndexed(visibleMessages, key = { _, m -> m.id }, contentType = { _, m -> m.role }) { index, msg ->
                val isUser = msg.role == ChatMessage.Role.USER
                val groupedWithPrevious = index > 0 && visibleMessages[index - 1].role == msg.role
                // 同组消息间距 8dp（上 4 + 下 4）、换组 16dp（上 12 + 下 4），
                // 与标准间距 8/16 一致，别把 4 改成 8，否则同组变 12。
                val topPadding = if (groupedWithPrevious) 4.dp else 12.dp
                // 主机历史里偶尔会有空的用户消息：以前会画一个只有内边距的
                // 空蓝气泡，看着像没发出去；空文本直接不画气泡。
                if (isUser && msg.text.isNotBlank()) {
                    // 聊天规范：气泡最大宽度按屏幕比例（70-85%），不要固定 dp。
                    // 以前固定 300dp：360dp 屏上占内容宽 320dp 的 94%（过大），
                    // 平板上又只有 300dp（过窄）。这里用内容宽的 85%。
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth().padding(top = topPadding, bottom = 4.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Surface(
                            modifier = Modifier.widthIn(max = maxWidth * BUBBLE_MAX_WIDTH_FRACTION),
                            shape = UserBubbleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            // 用户气泡也可框选：以前只有助手正文包 SelectionContainer，
                            // 自己发的话想复制一段只能整条靠记忆重打。
                            val userAnnotated = linkified(msg.text, MaterialTheme.colorScheme.onPrimary)
                            SelectionContainer {
                                // 用户气泡不解析 markdown，但网址同样可点（与助手一致）；
                                // 链接色显式给 onPrimary（见 linkified 注释，蓝链接在蓝底上看不见）。
                                ClickableText(
                                    text = userAnnotated,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    onClick = { offset -> openAnnotatedUrl(userAnnotated, offset, uriHandler) },
                                )
                            }
                        }
                    }
                } else {
                    // 无内容消息直接不画：以前空消息（无正文、无工具、非流式占位）
                    // 仍会留一个上下 4dp 的空 Column，看着像丢了一条消息；
                    // 旧注释写着“仍不画”，实际却留了 8dp 空白，名实不符。
                    // 流式占位只认最后一条：以前流式进行中时，列表里任何一条
                    // 空白历史消息都会跟着显示“思考中…”，出现两行思考状态。
                    val isStreamingPlaceholder = state.streaming && index == visibleMessages.lastIndex
                    if (msg.text.isNotBlank() || msg.toolCalls.isNotEmpty() || isStreamingPlaceholder) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = topPadding, bottom = 4.dp)) {
                        if (msg.text.isBlank()) {
                            // 有工具痕迹时优先画工具摘要：以前流式进行中一律画思考状态，
                            // 工具到了也看不见“已用 N 个工具”，要等 Done 才出现；
                            // 工具摘要本身就是进行中的活进度（视频里“搜索网页…”那行），
                            // 思考轮换只留给还没任何内容的等待态。输入框占位在流式时
                            // 仍是“正在回复…”，生成指示不会丢。
                            if (msg.toolCalls.isNotEmpty()) ToolCallsSummary(msg.toolCalls) else ThinkingStatus()
                        } else {
                            // 工具痕迹放正文上方，像视频里“搜索网页 …”那行，点开看详情。
                            ToolCallsSummary(msg.toolCalls)
                            // 助手正文可框选：代码块里的代码以前只能整条复制，
                            // 现在可以直接框选其中一段复制，复制钮仍在下面。
                            SelectionContainer { AssistantMessageText(msg.text) }
                            Text(
                                text = if (copiedId == msg.id) "已复制" else "复制",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                // 小字按钮也要 48dp 触控高度：labelMedium 行高 18dp，
                                // 上下各 15dp 内边距正好 48dp，之前只有 4dp、很难点中。
                                modifier = Modifier.clickable(role = Role.Button) {
                                    clipboardManager.setText(AnnotatedString(msg.text))
                                    copiedId = msg.id
                                    copiedSeq++
                                }.then(TextButtonTouchModifier),
                            )
                        }
                    }
                    }
                }
            }
            // 历史加载失败时 messages 为空但 message 有值：以前仍会显示
            // “今天想做什么？”空状态，和报错行一起出现会误导人以为是新会话。
            // 历史正在加载时不显示空状态：否则加载的 2 秒内先闪“今天想做什么？”，
            // 历史一到又弹出消息，像是空会话突然变出内容。
            if (state.historyLoading && state.messages.isEmpty()) {
                item { LoadingState(label = "加载历史中…") }
            } else if (state.messages.isEmpty() && state.message == null) {
                item {
                    EmptyState(
                        title = "今天想做什么？",
                        body = "在下方输入消息，主机智能体会在这里回复，工具调用会以摘要行展示，点开看详情。",
                    )
                }
            }
        }
        // 回到底部：用户往上翻时新消息在底下看不见——跟随逻辑只在已在底部时
        // 才跟随（往上翻了就不抢滚动），以前没有任何提示、得自己手动翻下去。
        // 不在底部时（与跟随逻辑同阈值：最后可见项离末尾超过 2 项）右下角给
        // 一个药丸按钮，点它平滑滚到底部；到了底部按钮自然消失（跟随逻辑
        // 接管后续增量）。derivedStateOf 读 layoutInfo：滚动时 layoutInfo 一直
        // 变，直接读会让整页每次滚动都重组，derivedStateOf 只在“是否在底部”
        // 翻转时才通知。按钮高 49dp（14 + 21 + 14，与各类药丸一致），触控达标。
        val showScrollToBottom by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: -1) < info.totalItemsCount - NEAR_BOTTOM_THRESHOLD
            }
        }
        if (showScrollToBottom) {
            Surface(
                onClick = {
                    // 点按时再取一次总数并判空：按钮显示与点按之间列表可能被
                    // 清空（如历史重载失败后无内容），总数为 0 时滚到 -1 会抛错。
                    val total = listState.layoutInfo.totalItemsCount
                    if (total > 0) scope.launch { listState.animateScrollToItem(total - 1) }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 12.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("回到底部", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        // 重载弱提示药丸（延迟效果见 Box 前）：顶部居中覆盖，不占列表位置、
        // 不推内容；无 onClick、点按透传给下面的消息列；瞬时覆盖层不进
        // 无障碍树，免得 TalkBack 焦点落在会自己消失的节点上。
        if (showReloading) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).clearAndSetSemantics { },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text("正在刷新…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        }
        // 审批：底部卡片，不是系统弹窗。
        // 卡只取本会话的：别会话的审批在 map 里另有格子，不会串到这页。
        state.pendingApprovals[sessionId]?.let { approval ->
            Column {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                // 故意用 background 而不用 Surface：Surface 会把内容色改成 onSurfaceVariant（灰），
                // 审批标题和工具名需要黑色；改成 Surface 会让标题变灰、层级消失。
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 20.dp, vertical = 16.dp)) {
                    // 审批卡是固定在输入框上方的卡片，不能拖拽，所以不画拖拽小横条
                    // （小横条只属于可拖拽的底部弹层，画在这里会误导人去拖它）。
                    // 审批卡出现时报读：审批是等用户决策的打断，TalkBack 用户
                    // 若不知道卡片出现、就没法及时允许/拒绝。
                    Text("需要审批", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Text("工具：${approval.toolName.ifBlank { "未知" }}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    if (approval.description.isNotBlank()) {
                        // 审批描述里的网址也可点：描述常带主机给出的文档/操作链接，
                        // 点开核对再决定允不允许，比纯文本更利于知情决策。
                        val approvalAnnotated = linkified(approval.description, MaterialTheme.colorScheme.primary)
                        ClickableText(approvalAnnotated, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.padding(top = 8.dp), onClick = { offset -> openAnnotatedUrl(approvalAnnotated, offset, uriHandler) })
                    }
                    Row(modifier = Modifier.padding(top = 16.dp)) {
                        // 允许是破坏性动作（主机会执行命令/读写文件）：按 HIG 破坏性动作用红，
                        // 不再披主色蓝外衣，与删除/清除凭据的红同一套语言。
                        PrimaryButton(text = "允许", onClick = { viewModel.answerApproval(sessionId, true) }, enabled = !state.answeringApproval, modifier = Modifier.weight(1f), containerColor = errorButtonColor())
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.answerApproval(sessionId, false) },
                            enabled = !state.answeringApproval,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) { Text("拒绝") }
                    }
                    Text(
                        "允许后主机会执行这项操作（可能读写文件或运行命令）。不认识就点拒绝。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    // 回答进行中给一行状态：回答是网络请求，慢主机上要等几秒，
                    // 以前只有按钮变灰、没有文字反馈，用户会以为没点上而重复点
                    // （重复点虽被 answeringApproval 挡住，但视觉上没反馈）。
                    if (state.answeringApproval) {
                        Text(
                            "处理中…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        // 报错时给重试：历史加载失败、发送失败都只是一行字、没地方重试，
        // 用户之前只能退出重进；重试钮按 historyFailed 显式标志给（不是消息
        // 为空才给——SWR 之后刷新失败时旧消息在，消息非空也应给重试）。
        state.message?.let {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 报错出现时报读：生成/加载失败是用户需要立刻知道的事，
                // liveRegion 让 TalkBack 在报错出现时主动读出来，不用用户自己翻找。
                // 报错正文包 SelectionContainer（与会话页 ErrorState 同款）：用户要
                // 复制报错去反馈/搜索时可框选；weight 必须挂在 SelectionContainer
                // 上（它是 Row 的直接子项），挂内层 Text 上 weight 失效、行会缩。
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                // 重试只给历史加载失败：发送失败的重试是重新发送（走输入框），
                // 点“重试”去拉历史帮不上忙；以前按消息为空给，SWR 之后刷新失败
                // 时旧消息在、重试钮反而没了，改成 historyFailed 显式标志。
                // 文案写“重试加载”不写“重试”： historyFailed 叠加发送失败时，
                // 行里是发送报错、钮却是拉历史，单写“重试”会让人以为是重发；
                // 写明是加载，发送重发仍走输入框（失败草稿已回填）。
                if (state.historyFailed) {
                    TextButton(onClick = { viewModel.loadHistory(sessionId) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("重试加载") }
                }
            }
        }
        // 空会话时的快捷药丸：视频里输入框上方那排，点一下只填入输入框，不假装已执行。
        // 用户已经开始打字时就不再显示，避免药丸和输入框抢空间。
        // 加载中不显示快捷药丸：空状态本身加载时就不显示（改显示加载圈），
        // 药丸是空状态的一部分，加载的 2 秒内先闪一排药丸、历史一到又消失，
        // 和空状态闪烁是同一个毛病；加载完、确认是空会话再显示。
        // 药丸是空状态的一部分：历史加载失败时空状态本身不显示（见上），药丸
        // 也要跟着藏——否则加载失败页会挂着“解释代码/写个脚本”这类空会话发现
        // 入口，暗示这是一个空会话，可实际内容未知（历史根本没拉到）。
        // 门用 historyFailed 而不用 message：上传提示这类信息性消息不代表会话
        // 非空，不应连累药丸；历史失败才是“内容未知”的精确信号。
        if (!state.historyLoading && !state.historyFailed && state.messages.isEmpty() && input.isBlank()) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                QuickActionPill(icon = Icons.Filled.Code, text = "解释代码", onClick = { input = "帮我解释这段代码"; inputFocus.requestFocus() })
                Spacer(modifier = Modifier.width(8.dp))
                QuickActionPill(icon = Icons.Filled.Edit, text = "写个脚本", onClick = { input = "帮我写一个脚本"; inputFocus.requestFocus() })
                Spacer(modifier = Modifier.width(8.dp))
                QuickActionPill(icon = Icons.Filled.Description, text = "总结内容", onClick = { input = "帮我总结这段内容"; inputFocus.requestFocus() })
            }
        }
        // 输入区横向 16dp、消息列 20dp：药丸故意比消息列略宽，不是没对齐，
        // 别“修正”成 20dp；快捷药丸行同样是 16dp，与输入框对齐。
        // Kimi 式药丸输入框：白底 + 细边线，+ 附件、多行输入、系统蓝圆形发送键。
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                // 多行输入时 + 号和发送键贴底（对齐最后一行），不居中：
                // 居中会让 5 行输入时的发送键悬在输入框中部，拇指够不着。
                // 单行时三者同高，贴底与居中看起来一样。
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(onClick = { viewModel.attachNote(sessionId, "attachment") }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "添加附件", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 输入框两侧 4dp 原本在 Box 上（字段之外）：点那 4dp 窄带既不聚焦
                // 输入框、也不触发邻钮，是死区；挪进输入框自身的修饰符里，视觉
                // 位置不变、那 4dp 变成输入框触控区的一部分，点到就聚焦。
                Box(modifier = Modifier.weight(1f)) {
                    // 注意：这里刻意不给键盘设 ImeAction.Send。ImeAction.Send 虽然是
                    // 给聊天应用用的，但它会把回车键变成发送键；本输入框是多行
                    // （maxLines = 5），用户还要靠回车换行写多行提示，所以回车
                    // 保持换行，发送只走右边的蓝色发送钮，和 ChatGPT 类应用一致。
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        // 焦点请求挂在输入框本体上：以前挂在外层 Box 上，焦点能否
                        // 落到输入框取决于父子焦点委托（不确定）；挂本体是标准写法，
                        // 快捷药丸与失败草稿的 requestFocus 一定能让键盘弹出来。
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp).focusRequester(inputFocus).semantics { contentDescription = "消息输入框" },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        // 光标显式给系统蓝：foundation 默认光标色在深色下可能不可见，
                        // 输入框文字已是 onSurface，光标不能只靠默认。
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = INPUT_MAX_LINES,
                        decorationBox = { innerTextField ->
                            Box {
                                if (input.isBlank()) {
                                    // 生成中时占位换成“正在回复…”，和视频里输入区的状态一致。
                                    Text(
                                        if (state.streaming) "正在回复…" else "尽管问，做个任务…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
                // 新视频的输入区模型药丸：点开换模型，之前在顶部的药丸改到这里。
                CompactModelPill(
                    text = if (state.model.isBlank()) "默认模型" else state.model,
                    // 存储层写模型名时已去空格；打开弹层时仍先去空格，保存时也去空格（双重保险）。
                    onClick = { modelDraft = state.model.trim(); showModelDialog = true },
                )
                // 药丸与发送钮之间补 8dp：以前两者触控区直接相邻（0 间隔），
                // 瞄准药丸换模型时手滑一点就会误触发送、把没打完的话提前发出去；
                // 行内其余可点元素间隔都是 8dp（搜索行、审批按钮同款规则）。
                // 代价是输入框再窄 8dp（多行输入、只多换行，仍可用）。
                Spacer(modifier = Modifier.width(8.dp))
                val canSend = input.isNotBlank() && !state.streaming
                val showStop = state.streaming
                IconButton(
                    onClick = {
                        if (showStop) viewModel.stopGenerating()
                        else if (input.isNotBlank()) { viewModel.send(sessionId, input); input = "" }
                    },
                    enabled = canSend || showStop,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(
                        modifier = Modifier.size(SEND_BUTTON_SIZE).clip(CircleShape)
                            .background(if (showStop) errorButtonColor() else if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (showStop) Icons.Filled.Stop else Icons.Filled.Send,
                            contentDescription = if (showStop) "停止生成" else "发送",
                            tint = if (canSend || showStop) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
