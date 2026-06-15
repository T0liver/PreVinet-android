package hu.toliver.previnet.ui.submit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import hu.toliver.previnet.R
import hu.toliver.previnet.data.Disease
import hu.toliver.previnet.ui.theme.SheetShape
import hu.toliver.previnet.ui.theme.Spacing

@Composable
fun diseaseDisplayName(disease: Disease): String =
    disease.nameRes?.let { stringResource(it) } ?: disease.fallbackName

@Composable
fun diseaseDescription(disease: Disease): String? =
    disease.descriptionRes?.let { stringResource(it) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiseasePickerSheet(
    diseases: List<Disease>,
    selected: Disease?,
    onSelect: (Disease) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
    ) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Text(
                text = stringResource(R.string.select_disease_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            LazyColumn {
                itemsIndexed(diseases, key = { _, disease -> disease.slug }) { index, disease ->
                    DiseaseRow(
                        disease = disease,
                        isSelected = disease.slug == selected?.slug,
                        onClick = { onSelect(disease) },
                    )
                    if (index < diseases.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.lg))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiseaseRow(
    disease: Disease,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Column(modifier = Modifier.padding(start = Spacing.sm)) {
            Text(
                text = diseaseDisplayName(disease),
                style = MaterialTheme.typography.titleMedium,
                fontStyle = if (disease.isUnknownOption) FontStyle.Italic else FontStyle.Normal,
                color = if (disease.isUnknownOption) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            diseaseDescription(disease)?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
