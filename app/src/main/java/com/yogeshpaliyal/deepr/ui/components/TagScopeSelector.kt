package com.yogeshpaliyal.deepr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
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
import compose.icons.tablericons.Check
import compose.icons.tablericons.Tag
import compose.icons.tablericons.World

/**
 * Returns the human readable scope of a tag for multiple profile selection.
 */
@Composable
fun tagScopeLabel(
    isGlobal: Boolean,
    selectedProfileIds: Set<Long>,
    profiles: List<Profile>,
): String {
    if (isGlobal || selectedProfileIds.isEmpty()) {
        return stringResource(R.string.tag_scope_global)
    }
    val matchingProfiles = profiles.filter { selectedProfileIds.contains(it.id) }
    return when {
        matchingProfiles.isEmpty() -> stringResource(R.string.tag_scope_global)
        matchingProfiles.size == 1 -> stringResource(R.string.tag_scope_profile, matchingProfiles.first().name)
        else -> {
            val names = matchingProfiles.joinToString(", ") { it.name }
            stringResource(R.string.tag_scope_multiple_profiles, matchingProfiles.size, names)
        }
    }
}

/**
 * Backward compatible overload returning the human readable scope of a tag.
 */
@Composable
fun tagScopeLabel(
    profileId: Long,
    profiles: List<Profile>,
): String =
    tagScopeLabel(
        isGlobal = profileId == GLOBAL_TAG_PROFILE_ID,
        selectedProfileIds = if (profileId != GLOBAL_TAG_PROFILE_ID) setOf(profileId) else emptySet(),
        profiles = profiles,
    )

/**
 * Dropdown used to pick whether a tag is global or bound to one or more profiles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagScopeDropdown(
    isGlobal: Boolean,
    selectedProfileIds: Set<Long>,
    profiles: List<Profile>,
    onScopeChanged: (isGlobal: Boolean, selectedProfileIds: Set<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val scopeLabel = tagScopeLabel(isGlobal, selectedProfileIds, profiles)

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
                    if (isGlobal || selectedProfileIds.isEmpty()) TablerIcons.World else TablerIcons.Tag,
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
                leadingIcon = {
                    Icon(
                        TablerIcons.World,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    Checkbox(
                        checked = isGlobal || selectedProfileIds.isEmpty(),
                        onCheckedChange = null,
                    )
                },
                onClick = {
                    onScopeChanged(true, emptySet())
                },
            )
            if (profiles.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                profiles.forEach { profile ->
                    val isChecked = !isGlobal && selectedProfileIds.contains(profile.id)
                    DropdownMenuItem(
                        text = { Text(profile.name) },
                        leadingIcon = {
                            Icon(
                                TablerIcons.Tag,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null,
                            )
                        },
                        onClick = {
                            if (isGlobal) {
                                onScopeChanged(false, setOf(profile.id))
                            } else {
                                val next =
                                    if (selectedProfileIds.contains(profile.id)) {
                                        selectedProfileIds - profile.id
                                    } else {
                                        selectedProfileIds + profile.id
                                    }
                                if (next.isEmpty()) {
                                    onScopeChanged(true, emptySet())
                                } else {
                                    onScopeChanged(false, next)
                                }
                            }
                        },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.done),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            TablerIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = { expanded = false },
                )
            }
        }
    }
}

/**
 * Backward compatible overload accepting a single profile id.
 */
@Composable
fun TagScopeDropdown(
    selectedProfileId: Long,
    profiles: List<Profile>,
    onProfileSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    TagScopeDropdown(
        isGlobal = selectedProfileId == GLOBAL_TAG_PROFILE_ID,
        selectedProfileIds = if (selectedProfileId != GLOBAL_TAG_PROFILE_ID) setOf(selectedProfileId) else emptySet(),
        profiles = profiles,
        onScopeChanged = { isGlobal, ids ->
            if (isGlobal || ids.isEmpty()) {
                onProfileSelected(GLOBAL_TAG_PROFILE_ID)
            } else {
                onProfileSelected(ids.first())
            }
        },
        modifier = modifier,
    )
}

/**
 * Small badge describing whether a tag is global or bound to one or more profiles.
 */
@Composable
fun TagScopeBadge(
    isGlobal: Boolean,
    profileIds: Set<Long>,
    profiles: List<Profile>,
    modifier: Modifier = Modifier,
) {
    val effectiveGlobal = isGlobal || profileIds.isEmpty()
    val selectedProfiles = profiles.filter { profileIds.contains(it.id) }
    val contentColor =
        if (effectiveGlobal) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }

    Surface(
        color =
            if (effectiveGlobal) {
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
                imageVector = if (effectiveGlobal) TablerIcons.World else TablerIcons.Tag,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = contentColor,
            )
            val badgeText =
                if (effectiveGlobal || selectedProfiles.isEmpty()) {
                    stringResource(R.string.tag_badge_global)
                } else if (selectedProfiles.size == 1) {
                    stringResource(R.string.tag_badge_profile, selectedProfiles.first().name)
                } else if (selectedProfiles.size <= 2) {
                    selectedProfiles.joinToString(", ") { it.name }
                } else {
                    stringResource(R.string.tag_scope_profiles_count, selectedProfiles.size)
                }
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

/**
 * Backward compatible overload accepting a single profile id.
 */
@Composable
fun TagScopeBadge(
    profileId: Long,
    profiles: List<Profile>,
    modifier: Modifier = Modifier,
) {
    TagScopeBadge(
        isGlobal = profileId == GLOBAL_TAG_PROFILE_ID,
        profileIds = if (profileId != GLOBAL_TAG_PROFILE_ID) setOf(profileId) else emptySet(),
        profiles = profiles,
        modifier = modifier,
    )
}
