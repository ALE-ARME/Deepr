package com.yogeshpaliyal.deepr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yogeshpaliyal.deepr.Profile
import com.yogeshpaliyal.deepr.R
import com.yogeshpaliyal.deepr.util.GLOBAL_TAG_PROFILE_ID
import compose.icons.TablerIcons
import compose.icons.tablericons.Tag
import compose.icons.tablericons.World

/**
 * Returns the human readable scope of a tag.
 *
 * @param profileId The `Tags.profileId` of the tag, [GLOBAL_TAG_PROFILE_ID] for global tags.
 * @param profiles The known profiles, used to resolve the profile name.
 */
@Composable
fun tagScopeLabel(
    profileId: Long,
    profiles: List<Profile>,
): String {
    val name = profiles.firstOrNull { it.id == profileId }?.name
    return if (profileId == GLOBAL_TAG_PROFILE_ID || name == null) {
        stringResource(R.string.tag_scope_global)
    } else {
        stringResource(R.string.tag_scope_profile, name)
    }
}

/**
 * Dropdown used to pick whether a tag is global or bound to a single profile.
 *
 * @param selectedProfileId The currently selected scope, [GLOBAL_TAG_PROFILE_ID] means global.
 * @param onProfileSelected Invoked with [GLOBAL_TAG_PROFILE_ID] for the global option, or with a profile id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagScopeDropdown(
    selectedProfileId: Long,
    profiles: List<Profile>,
    onProfileSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val scopeLabel = tagScopeLabel(selectedProfileId, profiles)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = scopeLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.tag_scope)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = {
                Icon(
                    TablerIcons.Tag,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tag_scope_global)) },
                onClick = {
                    onProfileSelected(GLOBAL_TAG_PROFILE_ID)
                    expanded = false
                },
            )
            if (profiles.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.name) },
                        onClick = {
                            onProfileSelected(profile.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Small badge describing whether a tag is global or bound to a single profile.
 */
@Composable
fun TagScopeBadge(
    profileId: Long,
    profiles: List<Profile>,
    modifier: Modifier = Modifier,
) {
    val isGlobal = profileId == GLOBAL_TAG_PROFILE_ID
    val name = profiles.firstOrNull { it.id == profileId }?.name
    val contentColor =
        if (isGlobal) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }

    Surface(
        color =
            if (isGlobal) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (isGlobal) TablerIcons.World else TablerIcons.Tag,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = contentColor,
            )
            Text(
                text =
                    if (name != null) {
                        stringResource(R.string.tag_badge_profile, name)
                    } else {
                        stringResource(R.string.tag_badge_global)
                    },
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}
