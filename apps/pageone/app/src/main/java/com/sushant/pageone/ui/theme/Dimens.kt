package com.sushant.pageone.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val xxs: Dp = 2.dp, val xs: Dp = 4.dp, val s: Dp = 8.dp, val m: Dp = 12.dp,
    val l: Dp = 16.dp, val xl: Dp = 24.dp, val xxl: Dp = 32.dp, val xxxl: Dp = 48.dp,
    val screen: Dp = 16.dp,          // 24dp on medium/expanded
    val groupGap: Dp = 3.dp,         // gap between rows in a grouped list
    val listSideInset: Dp = 12.dp,   // grouped lists sit 12dp from screen edges
)

object Sizes {
    val rowMin = 60.dp
    val pdfRowMin = 72.dp
    val folderShape = 48.dp
    val pdfThumbW = 40.dp
    val pdfThumbH = 52.dp
    val progressBar = 4.dp
    val searchPill = 56.dp
    val homeFab = 80.dp
    val penFab = 64.dp
    val floatingToolbar = 64.dp
    val readerCircle = 52.dp
    val readerTopFade = 130.dp
    val navBar = 80.dp
    val navPill = 64.dp to 32.dp
    val connectedButtonH = 34.dp
    val continueCover = 84.dp to 112.dp
    val emptyIllustration = 160.dp
    val maxListWidth = 840.dp
    val tabletPageGap = 12.dp
}
