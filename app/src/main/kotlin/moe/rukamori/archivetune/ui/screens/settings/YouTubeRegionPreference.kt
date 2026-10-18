package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.utils.AUTOMATIC_YOUTUBE_CONTENT_REGION
import moe.rukamori.archivetune.utils.YouTubeContentRegions
import moe.rukamori.archivetune.utils.resolveYouTubeContentRegion
import java.util.Locale

@Composable
internal fun YouTubeRegionPreference(
    selectedValue: String,
    onValueSelected: (String) -> Unit,
) {
    ListPreference(
        title = { Text(stringResource(R.string.youtube_music_region)) },
        description = stringResource(R.string.youtube_music_region_description),
        icon = { Icon(painterResource(R.drawable.location_on), null) },
        selectedValue = resolveYouTubeContentRegion(selectedValue),
        values = listOf(AUTOMATIC_YOUTUBE_CONTENT_REGION) + YouTubeContentRegions.keys.toList(),
        valueText = { value ->
            if (value == AUTOMATIC_YOUTUBE_CONTENT_REGION) {
                stringResource(R.string.youtube_music_region_auto)
            } else {
                Locale.Builder().setRegion(value).build().getDisplayCountry(Locale.getDefault())
            }
        },
        onValueSelected = onValueSelected,
    )
}
