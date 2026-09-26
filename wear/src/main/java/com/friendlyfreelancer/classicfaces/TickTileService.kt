package com.friendlyfreelancer.classicfaces

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/** Tile with a single round on/off button. */
class TickTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val on = TickSettings.isEnabled(this)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(on)))
            .build()
        return CallbackToFutureAdapter.getFuture { it.set(tile); "tile" }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
        return CallbackToFutureAdapter.getFuture { it.set(resources); "resources" }
    }

    private fun layout(on: Boolean): LayoutElementBuilders.LayoutElement {
        // Tiles can only launch activities, so the button opens MainActivity
        // with a toggle extra; it flips the setting and closes immediately.
        val toggle = ModifiersBuilders.Clickable.Builder()
            .setId("toggle")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .addKeyToExtraMapping(
                                MainActivity.EXTRA_TOGGLE,
                                ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build(),
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val button = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(BUTTON_DP))
            .setHeight(dp(BUTTON_DP))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(toggle)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(if (on) COLOR_ON else COLOR_OFF))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(BUTTON_DP / 2)).build())
                            .build()
                    )
                    .setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription(
                                getString(if (on) R.string.tile_turn_off else R.string.tile_turn_on)
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(text(getString(if (on) R.string.on else R.string.off), 22f, bold = true))
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(getString(R.string.tile_label), 16f, bold = false))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(12f)).build())
            .addContent(button)
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(column)
            .build()
    }

    private fun text(value: String, size: Float, bold: Boolean) =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(COLOR_TEXT))
                    .setWeight(
                        if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD
                        else LayoutElementBuilders.FONT_WEIGHT_NORMAL
                    )
                    .build()
            )
            .build()

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val BUTTON_DP = 96f
        const val COLOR_ON = 0xFFD62838.toInt()
        const val COLOR_OFF = 0xFF3A3F4B.toInt()
        const val COLOR_TEXT = 0xFFFFFFFF.toInt()
    }
}
