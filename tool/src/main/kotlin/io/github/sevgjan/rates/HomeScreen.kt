package io.github.sevgjan.rates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.LightWork
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.scrollBarGutterUnits
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

data class Conversion(val code: String, val amount: Double?)

data class RatesUiState(
    val base: String = "EUR",
    val amountText: String = "",
    val conversions: List<Conversion> = emptyList(),
    val date: String = "",
)

class HomeViewModel(private val store: RatesStore) : LightViewModel<Unit>() {
    private val amountText = MutableStateFlow("100")
    val status = MutableStateFlow<String?>(null)

    val state: StateFlow<RatesUiState> = combine(
        store.snapshot, store.base, store.favorites, amountText,
    ) { snapshot, base, favorites, text ->
        val amount = AmountInput.value(text)
        RatesUiState(
            base = base,
            amountText = text,
            conversions = favorites.filter { it != base }.map { Conversion(it, snapshot.convert(amount, base, it)) },
            date = snapshot.date,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RatesUiState())

    fun press(key: Char) {
        amountText.value = AmountInput.press(amountText.value, key)
    }

    fun clear() {
        amountText.value = ""
    }

    /** Make [code] the base, carrying the converted amount over so the values stay equivalent. */
    fun selectBase(conversion: Conversion) {
        viewModelScope.launch {
            val oldBase = store.base.first()
            store.setBase(conversion.code)
            conversion.amount?.let { amountText.value = AmountInput.fromValue(it) }
            val favorites = store.favorites.first()
            if (oldBase !in favorites) store.setFavorites(favorites + oldBase)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            status.value = "Updating…"
            status.value = runCatching { store.save(RatesApi.fetchLatest()) }
                .fold(onSuccess = { null }, onFailure = { "Offline. Showing saved rates." })
        }
    }

    fun setFavorites(codes: List<String>) {
        viewModelScope.launch { store.setFavorites(codes) }
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeViewModel>(sealedActivity) {

    private val store = RatesStore(lightContext)

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel() = HomeViewModel(store)

    override fun willShow() {
        LightWork.enqueuePeriodic(lightContext, REFRESH_JOB, repeatInterval = 12.hours)
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        val status by viewModel.status.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.Text(text = "LIST", onClick = ::openPicker),
                    center = LightTopBarCenter.Text("Rates"),
                    rightButton = LightBarButton.Text(text = "SYNC", onClick = viewModel::refresh),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable(onClick = viewModel::clear)
                        // the list below keeps a gutter free for its scrollbar on the right: match it
                        // there, and use the same inset on the left so both sides look even
                        .padding(horizontal = SIDE_INSET.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    LightText(
                        text = state.amountText.ifEmpty { "0" },
                        variant = LightTextVariant.Subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    LightText(text = state.base, variant = LightTextVariant.Heading)
                }

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = SIDE_INSET.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
                ) {
                    state.conversions.forEach { conversion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .lightClickable { viewModel.selectBase(conversion) }
                                .padding(vertical = 0.4f.gridUnitsAsDp()),
                        ) {
                            LightText(
                                text = conversion.amount?.let(::formatAmount) ?: "-",
                                variant = LightTextVariant.Paragraph,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            LightText(text = conversion.code, variant = LightTextVariant.Paragraph, lighten = true)
                        }
                    }
                }

                LightText(
                    text = status ?: "ECB rates of ${state.date}",
                    variant = LightTextVariant.Fine,
                    lighten = true,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 0.25f.gridUnitsAsDp()),
                )

                Keypad(onKey = viewModel::press)
            }
        }
    }

    private fun openPicker() {
        val current = viewModel.state.value
        navigateTo(
            screenFactory = {
                CurrencyPickerScreen(it, store, current.conversions.map(Conversion::code))
            },
            resultCallback = viewModel::setFavorites,
        )
    }
}

/** Side inset of the amount and the list: one grid unit plus the scroll view's scrollbar gutter. */
private val SIDE_INSET = 1f + scrollBarGutterUnits(LightScrollBarPosition.Outside)

private val KEYS =listOf("123", "456", "789", ".0${AmountInput.BACKSPACE}")

@Composable
private fun Keypad(onKey: (Char) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 0.5f.gridUnitsAsDp())) {
        KEYS.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.8f.gridUnitsAsDp())
                            .lightClickable { onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        LightText(
                            text = if (key == AmountInput.BACKSPACE) "⌫" else key.toString(),
                            variant = LightTextVariant.Subtitle,
                        )
                    }
                }
            }
        }
    }
}
