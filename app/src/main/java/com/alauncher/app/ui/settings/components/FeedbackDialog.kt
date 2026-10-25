package com.alauncher.app.ui.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alauncher.app.R
import com.alauncher.app.ui.settings.FeedbackCategory
import com.alauncher.app.ui.settings.FeedbackUtils

@Composable
fun FeedbackDialog(
    isDefaultLauncher: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var selectedCategory by rememberSaveable(
        stateSaver = Saver(
            save = { it.name },
            restore = { FeedbackCategory.valueOf(it) }
        )
    ) { mutableStateOf(FeedbackCategory.FEATURE_REQUEST) }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }
    var messageText by rememberSaveable { mutableStateOf("") }
    var includeDiagnostics by rememberSaveable { mutableStateOf(true) }

    val isMessageValid = remember(messageText) {
        FeedbackUtils.isMessageValid(messageText)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        title = {
            SettingsDialogHeader(
                icon = Icons.Default.Feedback,
                title = stringResource(R.string.feedback_dialog_title),
                subtitle = stringResource(R.string.feedback_dialog_subtitle)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category Dropdown Selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { isCategoryDropdownExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.feedback_category_label),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(selectedCategory.labelRes),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isCategoryDropdownExpanded,
                        onDismissRequest = { isCategoryDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.75f)
                    ) {
                        FeedbackCategory.entries.forEach { category ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(category.labelRes),
                                        fontSize = 13.sp,
                                        fontWeight = if (category == selectedCategory) FontWeight.Bold else FontWeight.Normal,
                                        color = if (category == selectedCategory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                trailingIcon = {
                                    if (category == selectedCategory) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    selectedCategory = category
                                    isCategoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Message Text Field
                OutlinedTextField(
                    value = messageText,
                    onValueChange = {
                        if (it.length <= FeedbackUtils.MAX_MESSAGE_LENGTH) {
                            messageText = it
                        }
                    },
                    label = { Text(stringResource(R.string.feedback_message_label), fontSize = 13.sp) },
                    placeholder = {
                        Text(
                            stringResource(R.string.feedback_message_placeholder),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    minLines = 4,
                    maxLines = 7,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (messageText.isNotBlank() && messageText.trim().length < FeedbackUtils.MIN_MESSAGE_LENGTH) {
                                Text(
                                    text = stringResource(R.string.feedback_min_chars, FeedbackUtils.MIN_MESSAGE_LENGTH),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }
                            Text(
                                text = "${messageText.length} / ${FeedbackUtils.MAX_MESSAGE_LENGTH}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Diagnostics Checkbox / Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.feedback_include_diagnostics),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.feedback_include_diagnostics_desc),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ThemedSwitch(
                        checked = includeDiagnostics,
                        onCheckedChange = { includeDiagnostics = it },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val subject = FeedbackUtils.buildSubject(selectedCategory, messageText)
                    val body = FeedbackUtils.buildBody(
                        category = selectedCategory,
                        message = messageText,
                        includeDiagnostics = includeDiagnostics,
                        isDefaultLauncher = isDefaultLauncher
                    )
                    FeedbackUtils.dispatchFeedbackEmail(context, subject, body)
                    onDismiss()
                },
                enabled = isMessageValid,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.feedback_submit), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.feedback_cancel), fontSize = 13.sp)
            }
        }
    )
}
