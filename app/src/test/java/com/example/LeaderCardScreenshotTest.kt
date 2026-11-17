package com.example

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.LeaderCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * LeaderCard, drawn and compared against a committed picture of it.
 *
 * This test used to prove less than it looked. captureRoboImage only writes or
 * compares anything when Roborazzi is told to, and nothing told it to: no
 * baseline was committed and no workflow passed the flag, so every CI run
 * rendered the card, threw the pixels away and reported success. A card that
 * had gone blank, lost its numbers or turned black-on-black would have passed.
 *
 * Now src/test/screenshots/leader_card.png is committed and CI runs the tests
 * with -Proborazzi.test.verify=true, so a change in what this card looks like
 * fails the build. That matters here because LeaderCard is shared code drawn by
 * both phones, and because Compose and Material version bumps have already
 * quietly restyled things in this app once.
 *
 * When a change to the card is intended, regenerate the baseline with
 * -Proborazzi.test.record=true and commit the new picture, which puts the
 * before and after in the diff where they can be looked at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class LeaderCardScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun leader_card_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Surface {
                    LeaderCard(
                        title = "Batting Average",
                        entries = listOf(
                            "Presley Limbaugh" to ".382",
                            "Anna Soles" to ".354",
                            "Hailey Cripe" to ".339"
                        )
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/leader_card.png")
    }
}
