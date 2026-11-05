package com.runwear.app.makeitme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.RunWearColors

/**
 * GEN-6 WAVE C (RW-61): the designed "Your looks" library
 * (final-design frame 21; design-spec §17). It did not exist on
 * Android — a gate-failed cell's explanation lived nowhere, so a
 * user who met a stock photo had no surface that could tell them
 * why. This file is that surface: the policy that derives the
 * cell list from the bank data the app already reads, and the
 * screen that renders it.
 *
 * The combination id is self-describing
 * ({GENDER}_{WEATHER}_{BRACKET}_{TIME_OF_DAY}_{HASH} — see
 * OutfitCombination), so every cell's settled label
 * ("Snow · Freezing", daypart "Night") derives from the id alone.
 * A cell's state comes from the two owner-read tables the
 * coverage read already uses: banked in personal_heroes → ready;
 * a personal_generation_jobs row at FAILED with no banked hero →
 * the serve policy's gate-failure state (STOCK_QUIET), labelled
 * "…stock photo for now"; anything else with a job → being made.
 */
object LooksLibraryPolicy {

    enum class CellState { READY, MAKING, FAILED }

    data class LookCell(
        val combinationId: String,
        /** "Snow · Freezing" — weather · bracket, title case. */
        val title: String,
        /** "Night" — the cell's daypart, title case. */
        val daypartLabel: String,
        val state: CellState
    ) {
        /** The cell's sub-label; a failed cell carries the settled
         * "stock photo for now" label (frame 21). */
        val subLabel: String
            get() = when (state) {
                CellState.FAILED -> "$daypartLabel · stock photo for now"
                CellState.MAKING -> "$daypartLabel · being made"
                CellState.READY -> daypartLabel
            }
    }

    private val WEATHER_LABELS = mapOf(
        "CLEAR" to "Clear", "CLOUDY" to "Cloudy",
        "RAIN" to "Rain", "SNOW" to "Snow"
    )
    private val BRACKET_LABELS = mapOf(
        "FREEZING" to "Freezing", "COLD" to "Cold", "COOL" to "Cool",
        "MILD" to "Mild", "WARM" to "Warm", "HOT" to "Hot"
    )
    private val DAYPART_LABELS = mapOf(
        "DAWN" to "Dawn", "MIDDAY" to "Midday",
        "DUSK" to "Dusk", "NIGHT" to "Night"
    )
    private val BRACKET_ORDER = listOf(
        "FREEZING", "COLD", "COOL", "MILD", "WARM", "HOT"
    )

    /** Parse one combination id into its labels; null when the id
     * does not carry the five-part self-describing form. */
    fun parse(combinationId: String): Triple<String, String, String>? {
        val parts = combinationId.split("_")
        if (parts.size < 5) return null
        val weather = WEATHER_LABELS[parts[1]] ?: return null
        val bracket = BRACKET_LABELS[parts[2]] ?: return null
        val daypart = DAYPART_LABELS[parts[3]] ?: return null
        return Triple(weather, bracket, daypart)
    }

    /**
     * Build the library's cells from the bank read. [jobStatuses]
     * maps combination id → the job's status (the worker writes
     * QUEUED / PROCESSING / COMPLETED / FAILED). Failed cells
     * sort first — they are the ones carrying the explanation —
     * then making, then ready; within a state, bracket order.
     */
    fun build(
        heroCells: Set<String>,
        jobStatuses: Map<String, String>
    ): List<LookCell> {
        val ids = heroCells union jobStatuses.keys
        return ids.mapNotNull { id ->
            val (weather, bracket, daypart) = parse(id) ?: return@mapNotNull null
            val state = when {
                id in heroCells -> CellState.READY
                jobStatuses[id]?.equals("FAILED", ignoreCase = true) == true ->
                    CellState.FAILED
                else -> CellState.MAKING
            }
            LookCell(
                combinationId = id,
                title = "$weather · $bracket",
                daypartLabel = daypart,
                state = state
            ) to BRACKET_ORDER.indexOf(
                id.split("_").getOrNull(2) ?: ""
            )
        }.sortedWith(
            compareBy<Pair<LookCell, Int>> {
                when (it.first.state) {
                    CellState.FAILED -> 0
                    CellState.MAKING -> 1
                    CellState.READY -> 2
                }
            }
                .thenBy { it.second }
                .thenBy { it.first.title }
        ).map { it.first }
    }
}

/** RW-61 settled copy (frame 21 / design-spec §17, verbatim). */
object LooksLibraryStrings {
    const val TITLE: String = "Your looks"
    const val WHY_DOOR: String = "Why am I seeing a stock photo?"
    const val EXPLANATION: String =
        "One scenario didn't pass the quality gate after every attempt, " +
            "so it shows the stock photo rather than a version of you " +
            "that isn't right. It retries on its own when generation improves."
    const val EMPTY_LINE: String = "No looks yet — your first look is on its way."
}

/**
 * The library screen. [cells] null = still loading; the ViewModel
 * overload below loads through [MakeItMeViewModel.loadLooksLibrary].
 */
@Composable
internal fun LooksLibraryScreen(
    cells: List<LooksLibraryPolicy.LookCell>?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showExplanation by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RunWearColors.Background)
            .padding(horizontal = 20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = RunWearColors.TextPrimary
                )
            }
        }
        Text(
            text = LooksLibraryStrings.TITLE,
            fontFamily = com.runwear.app.ui.theme.GreatVibesFontFamily,
            fontSize = 30.sp,
            color = RunWearColors.TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        when {
            cells == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator(color = RunWearColors.PineAccent)
            }
            cells.isEmpty() -> Text(
                text = LooksLibraryStrings.EMPTY_LINE,
                fontSize = 14.sp,
                color = RunWearColors.TextSecondary
            )
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(cells, key = { it.combinationId }) { cell ->
                        LookCellTile(cell)
                    }
                }
                // Frame 21: when a failed cell exists, the plain
                // explanation band + the "Why am I seeing a stock
                // photo?" door live here — one tap from the failed
                // cell, never a badge on home.
                if (cells.any { it.state == LooksLibraryPolicy.CellState.FAILED }) {
                    Text(
                        text = LooksLibraryStrings.EXPLANATION,
                        fontSize = 13.sp,
                        color = RunWearColors.TextSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = LooksLibraryStrings.WHY_DOOR,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RunWearColors.PineAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showExplanation = true }
                            .padding(vertical = 6.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text(LooksLibraryStrings.WHY_DOOR) },
            text = { Text(LooksLibraryStrings.EXPLANATION) },
            confirmButton = {
                TextButton(onClick = { showExplanation = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
private fun LookCellTile(cell: LooksLibraryPolicy.LookCell) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(RunWearColors.BgCard)
            .padding(12.dp)
    ) {
        // Scenario tile: the cell's weather glyph over a tinted
        // ground (per-cell photo URLs are served one cell at a
        // time by the serve function; the library's read carries
        // labels and states, so the tile is a labelled scenario
        // tile rather than a photo).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(RunWearColors.BgCardLight),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = when (cell.state) {
                    LooksLibraryPolicy.CellState.FAILED -> "Stock photo"
                    LooksLibraryPolicy.CellState.MAKING -> "Being made…"
                    LooksLibraryPolicy.CellState.READY -> "Your photo"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = RunWearColors.TextSecondary
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = cell.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = RunWearColors.TextPrimary
        )
        Text(
            text = cell.subLabel,
            fontSize = 12.sp,
            color = RunWearColors.TextSecondary
        )
    }
}

/** ViewModel-hosted overload: loads the bank cells, then renders. */
@Composable
fun LooksLibraryScreen(
    viewModel: MakeItMeViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var cells by remember {
        mutableStateOf<List<LooksLibraryPolicy.LookCell>?>(null)
    }
    LaunchedEffect(Unit) {
        viewModel.loadLooksLibrary { loaded -> cells = loaded ?: emptyList() }
    }
    LooksLibraryScreen(cells = cells, onDismiss = onDismiss, modifier = modifier)
}
