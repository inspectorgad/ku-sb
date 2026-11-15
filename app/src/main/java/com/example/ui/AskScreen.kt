package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.ASK_MODELS
import com.example.data.AskEngine
import com.example.data.AskKeyStore
import com.example.data.AskModel
import com.example.data.AskSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val EXPLAIN_ASK =
    "Type a question about the team and Claude, Anthropic's AI, answers it from the season data: " +
        "every game, the score in every inning, every batting, pitching and fielding line, how each " +
        "run scored, the schedule, the Big 12 standings, the national poll, the weekly RPI and the " +
        "roster — and, for other teams, every Division I game this season, each Big 12 team's " +
        "scoring and record, and every Division I team's RPI row. Claude works each answer out by " +
        "running Python on the data rather than from memory, and \"Show the code\" under an answer " +
        "lists exactly what it ran. On its own it knows nothing about injuries, practice or " +
        "line-ups; turn on web search below to let it look those up. Follow-up questions build on " +
        "the earlier ones until you tap New conversation."

const val EXPLAIN_ASK_WEB =
    "Web search lets Claude also search the internet for this question — previews, recaps, injury " +
        "and line-up news, coaches' comments — and it lists the pages it used under the answer, " +
        "numbered to match the [1], [2] marks in the text. Statistics still come from the box " +
        "scores. Each search costs 1 cent on your Anthropic account plus the cost of reading the " +
        "pages, so a question can cost several cents more; it uses at most 5 searches. Web pages " +
        "can be wrong or out of date, so check the dates on the sources. Turning it on partway " +
        "through a conversation starts a new one with your next question, because Claude can't " +
        "take on web search partway through; turning it off keeps the conversation going."

const val EXPLAIN_ASK_KEY =
    "This uses your own Anthropic API key, so questions are billed to your Anthropic account — " +
        "usually a few cents each; the cost is shown under every answer. The key is stored on this " +
        "phone only, encrypted, and is sent only to api.anthropic.com. Get one at console.anthropic.com."

// The same five the dashboard offers, picked to show what the tables answer
// and the other tabs do not. The last one needs the web search switch, so
// there is an obvious thing to try it on.
private val EXAMPLES = listOf(
    "How did we hit against ranked opponents compared with unranked ones?",
    "Which inning do we score most of our runs in, and which do we give up most?",
    "Compare our pitchers' workload across the Big 12 weekends — pitches and batters faced.",
    "In our one-run losses, how did the winning run score?",
    "With web search on: any injury or lineup news for our next opponent?",
)

/**
 * The Preview button's question; the dashboard's ask.js asks the same, word
 * for word. The injury and line-up news is looked up only when web search is
 * on: every question tells Claude whether it is, so with search off Claude
 * answers the rest from the tables and says it cannot get the news.
 *
 * [versus] is "vs" or "at", as [opponentLabel] prints it.
 */
fun previewQuestion(date: String, versus: String, opponent: String) =
    "Give me a short scouting report for the $date game $versus $opponent: their season so far " +
        "and where they sit in the RPI, our last meeting, any opponents we have both played and " +
        "how each of us did against them, the hitters and pitchers to watch on each side, how we " +
        "match up, and, if web search is on, any injury or lineup news for either team."

/** The Recap button's question; the dashboard asks the same. */
fun recapQuestion(date: String, versus: String, opponent: String) =
    "Recap the $date game $versus $opponent: the result and how it unfolded inning by inning, " +
        "our standout batters and pitchers and theirs, how the day compared with our season " +
        "averages, and what decided it."

/** One question and whatever came back for it. */
class AskTurn(val question: String) {
    var answer by mutableStateOf("")
    var status by mutableStateOf("")
    var code by mutableStateOf<List<String>>(emptyList())
    var notes by mutableStateOf<List<String>>(emptyList())
    var error by mutableStateOf<String?>(null)
    var sources by mutableStateOf<List<AskSource>>(emptyList())
    var searches by mutableStateOf<List<String>>(emptyList())
}

/**
 * The thread lives for as long as the app does, so closing the screen and
 * coming back finds it where it was, and an answer being worked on carries
 * on while the reader looks at something else.
 */
object AskSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val engine = AskEngine()
    val turns = mutableStateListOf<AskTurn>()
    var running by mutableStateOf<Job?>(null)
        private set
    /** Holds the key for this run only when the reader chose not to keep it. */
    var sessionKey: String? = null
    private var resetting: Job? = null

    fun ask(question: String, key: String, model: AskModel, webSearch: Boolean = false) {
        if (running != null) return
        // A conversation begun without web search can't take it on: the engine
        // starts a new one, so the earlier turns leave the screen too. (After
        // New conversation the list is already empty, so this changes nothing.)
        if (engine.startsNewConversation(webSearch)) turns.clear()
        val turn = AskTurn(question).also { turns += it }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        // Off the main thread: the requests carry the season's tables, and
        // building them is too slow for the UI thread. Snapshot state may be
        // written from here; the screen redraws with each change.
        // Started only once it is recorded as running, so a question that
        // fails at once cannot finish before that and leave the screen busy.
        val job = scope.launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            try {
                resetting?.join()
                val result = engine.ask(question, key, model, today, webSearch) { status, partial, sources ->
                    turn.status = status
                    if (partial.isNotEmpty()) {
                        turn.answer = partial
                        turn.sources = sources
                    }
                }
                turn.answer = result.text
                turn.code = result.code
                turn.notes = result.notes
                turn.error = result.error
                turn.sources = result.sources
                turn.searches = result.searches
            } catch (e: CancellationException) {
                turn.notes = listOf("Stopped.")
            } catch (e: Exception) {
                turn.error = "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                turn.status = ""
                running = null
            }
        }
        running = job
        job.start()
    }

    fun stop() {
        running?.cancel()
    }

    fun newConversation() {
        val job = running
        turns.clear()
        // Reset only once a question in flight has finished rolling itself back.
        resetting = scope.launch(Dispatchers.Default) {
            job?.cancelAndJoin()
            engine.reset()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(onBack: () -> Unit, prefill: String? = null) {
    val context = LocalContext.current.applicationContext
    var savedKey by remember { mutableStateOf(AskSession.sessionKey ?: AskKeyStore.load(context)) }
    var model by remember { mutableStateOf(AskKeyStore.model(context)) }
    var webSearch by remember { mutableStateOf(AskKeyStore.webSearch(context)) }
    var question by rememberSaveable { mutableStateOf("") }
    val busy = AskSession.running != null
    val listState = rememberLazyListState()
    val turns = AskSession.turns

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size + 1)
    }

    // Arrived from a Preview or Recap button: ask it straight away when the
    // key is already saved, and otherwise put it in the box so the reader can
    // send it once they have entered one. Guarded against asking twice if the
    // screen is recomposed, and against cutting across an answer in flight.
    LaunchedEffect(prefill) {
        val q = prefill ?: return@LaunchedEffect
        if (turns.any { it.question == q }) return@LaunchedEffect
        val key = savedKey
        if (key != null && AskSession.running == null) {
            AskSession.ask(q, key, model, webSearch)
        } else {
            question = q
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask about the team") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner).imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Questions about the season, answered by Claude",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Explanation(EXPLAIN_ASK)
                        }
                    }
                }
                item {
                    KeyCard(
                        savedKey = savedKey,
                        model = model,
                        onKey = { key, keep ->
                            if (keep && AskKeyStore.save(context, key)) {
                                AskSession.sessionKey = null
                            } else {
                                AskSession.sessionKey = key
                            }
                            savedKey = key
                        },
                        onForget = {
                            AskKeyStore.forget(context)
                            AskSession.sessionKey = null
                            savedKey = null
                        },
                        onModel = {
                            model = it
                            AskKeyStore.saveModel(context, it)
                        }
                    )
                }
                items(turns) { TurnCard(it) }
                if (turns.isEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Try one of these:", style = MaterialTheme.typography.labelMedium)
                            EXAMPLES.forEach { ex ->
                                AssistChip(onClick = { question = ex }, label = { Text(ex) })
                            }
                        }
                    }
                }
            }
            AskInput(
                question = question,
                onQuestion = { question = it },
                busy = busy,
                hasKey = savedKey != null,
                showNew = turns.isNotEmpty(),
                webSearch = webSearch,
                onWebSearch = {
                    webSearch = it
                    AskKeyStore.saveWebSearch(context, it)
                },
                onAsk = {
                    val q = question.trim()
                    val key = savedKey
                    if (q.isNotEmpty() && key != null) {
                        question = ""
                        AskSession.ask(q, key, model, webSearch)
                    }
                },
                onStop = AskSession::stop,
                onNew = AskSession::newConversation
            )
        }
    }
}

@Composable
private fun KeyCard(
    savedKey: String?,
    model: AskModel,
    onKey: (String, Boolean) -> Unit,
    onForget: () -> Unit,
    onModel: (AskModel) -> Unit
) {
    var entry by rememberSaveable { mutableStateOf("") }
    var keep by rememberSaveable { mutableStateOf(true) }
    var problem by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (savedKey == null) {
                Text("Your Anthropic API key", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = entry,
                    onValueChange = { entry = it; problem = null },
                    placeholder = { Text("sk-ant-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keep, onCheckedChange = { keep = it })
                    Text("Keep it on this phone", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        val k = entry.trim()
                        if (!k.startsWith("sk-ant-")) {
                            problem = "That doesn't look like an Anthropic API key (they start with sk-ant-)."
                        } else {
                            onKey(k, keep)
                            entry = ""
                        }
                    }) { Text("Save key") }
                }
                problem?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "API key saved (…${savedKey.takeLast(4)})",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onForget) { Text("Forget key") }
                }
            }
            Text("Model", style = MaterialTheme.typography.labelMedium)
            Box {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(model.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("▾")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ASK_MODELS.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.label) },
                            onClick = { onModel(m); menuOpen = false }
                        )
                    }
                }
            }
            Explanation(EXPLAIN_ASK_KEY)
        }
    }
}

@Composable
private fun TurnCard(turn: AskTurn) {
    var showCode by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(turn.question, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (turn.answer.isNotEmpty()) MarkdownText(turn.answer)
                turn.error?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                if (turn.status.isNotEmpty()) {
                    Text(
                        turn.status,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (turn.code.isNotEmpty()) {
                    Text(
                        (if (showCode) "Hide the code" else "Show the code Claude ran") +
                            " (${turn.code.size} step${if (turn.code.size == 1) "" else "s"})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showCode = !showCode }.padding(vertical = 4.dp)
                    )
                    if (showCode) turn.code.forEach { CodeBox(it) }
                }
                if (turn.sources.isNotEmpty()) Sources(turn.sources)
                if (turn.searches.isNotEmpty()) {
                    Text(
                        "Searched the web for: " + turn.searches.joinToString("; ") { "\"$it\"" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                turn.notes.forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The web pages an answer cites, numbered as in its text; each opens in the browser. */
@Composable
private fun Sources(sources: List<AskSource>) {
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Sources from the web", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        sources.forEach { s ->
            Text(
                "[${s.number}] ${s.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { runCatching { uri.openUri(s.url) } }.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun AskInput(
    question: String,
    onQuestion: (String) -> Unit,
    busy: Boolean,
    hasKey: Boolean,
    showNew: Boolean,
    webSearch: Boolean,
    onWebSearch: (Boolean) -> Unit,
    onAsk: () -> Unit,
    onStop: () -> Unit,
    onNew: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = question,
            onValueChange = onQuestion,
            placeholder = { Text(if (hasKey) "Ask a question about the team" else "Add your API key above first") },
            enabled = hasKey,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        var explainWeb by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Switch(checked = webSearch, onCheckedChange = onWebSearch)
            Text(
                if (webSearch) "Web search on" else "Web search off",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp)
            )
            TextButton(onClick = { explainWeb = !explainWeb }) { Text(if (explainWeb) "Hide" else "What's this?") }
        }
        if (explainWeb) Explanation(EXPLAIN_ASK_WEB)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (busy) {
                OutlinedButton(onClick = onStop) { Text("Stop") }
            } else {
                Button(onClick = onAsk, enabled = hasKey && question.isNotBlank()) { Text("Ask") }
            }
            if (showNew) TextButton(onClick = onNew) { Text("New conversation") }
            Spacer(Modifier.width(4.dp))
        }
    }
}

