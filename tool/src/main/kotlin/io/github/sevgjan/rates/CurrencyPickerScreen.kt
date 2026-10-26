package io.github.sevgjan.rates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/** Choose which currencies are listed on the home screen. Returns the selection in list order. */
class CurrencyPickerScreen(
    sealedActivity: SealedLightActivity,
    private val store: RatesStore,
    private val initial: List<String>,
) : SimpleLightScreen<List<String>>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val snapshot by store.snapshot.collectAsState(initial = null)
        val selected = remember { mutableStateListOf(*initial.toTypedArray()) }
        val names = store.currencyNames

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Currencies"),
                    rightButton = LightBarButton.Text(text = "DONE", onClick = { goBack(selected.toList()) }),
                    modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                )
                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    snapshot?.codes?.forEach { code ->
                        val isSelected = code in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .lightClickable { if (isSelected) selected.remove(code) else selected.add(code) }
                                .padding(vertical = 0.4f.gridUnitsAsDp()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LightIcon(
                                icon = if (isSelected) LightIcons.SELECT_ON else LightIcons.SELECT_OFF,
                                size = 1.2f,
                            )
                            LightText(
                                text = code,
                                variant = LightTextVariant.Copy,
                                modifier = Modifier.padding(start = 0.6f.gridUnitsAsDp()).width(3.2f.gridUnitsAsDp()),
                            )
                            LightText(
                                text = names[code] ?: "",
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
