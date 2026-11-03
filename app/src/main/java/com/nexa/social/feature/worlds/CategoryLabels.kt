package com.nexa.social.feature.worlds

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nexa.social.R
import com.nexa.social.domain.model.CommunityCategory

@Composable
fun categoryLabel(category: CommunityCategory): String = when (category) {
    CommunityCategory.GAMING -> stringResource(R.string.category_gaming)
    CommunityCategory.TECH -> stringResource(R.string.category_tech)
    CommunityCategory.EDUCATION -> stringResource(R.string.category_education)
    CommunityCategory.CREATIVE -> stringResource(R.string.category_creative)
    CommunityCategory.BUSINESS -> stringResource(R.string.category_business)
    CommunityCategory.LOCAL -> stringResource(R.string.category_local)
    CommunityCategory.OTHER -> stringResource(R.string.category_other)
}
