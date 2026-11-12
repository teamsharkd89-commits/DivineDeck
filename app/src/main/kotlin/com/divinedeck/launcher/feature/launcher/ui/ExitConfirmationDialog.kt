package com.divinedeck.launcher.feature.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import com.divinedeck.launcher.R
import com.divinedeck.launcher.core.designsystem.components.AutomotivePrimaryButton
import com.divinedeck.launcher.core.designsystem.components.AutomotiveSecondaryButton
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme

enum class ExitStep {
    STEP_1_FIRST_CONFIRMATION,
    STEP_2_FINAL_CONFIRMATION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExitConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirmExit: () -> Unit
) {
    val palette = DivineDeckTheme.palette
    var currentStep by remember { mutableStateOf(ExitStep.STEP_1_FIRST_CONFIRMATION) }

    BasicAlertDialog(
        onDismissRequest = onDismissRequest
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = palette.surface,
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.9f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                when (currentStep) {
                    ExitStep.STEP_1_FIRST_CONFIRMATION -> {
                        Text(
                            text = stringResource(R.string.exit_step1_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.exit_step1_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Paso 1 de 2: Esta verificación previene toques involuntarios en marcha.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            AutomotiveSecondaryButton(
                                onClick = onDismissRequest
                            ) {
                                Text(stringResource(R.string.exit_step1_cancel))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            AutomotivePrimaryButton(
                                onClick = { currentStep = ExitStep.STEP_2_FINAL_CONFIRMATION },
                                containerColor = palette.accent,
                                contentColor = palette.onAccent
                            ) {
                                Text(stringResource(R.string.exit_step1_confirm))
                            }
                        }
                    }

                    ExitStep.STEP_2_FINAL_CONFIRMATION -> {
                        Text(
                            text = stringResource(R.string.exit_step2_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = palette.statusAlert
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.exit_step2_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Recordatorio: Al presionar el botón HOME de la tablet, volverás de inmediato a DIVINE DECK.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            AutomotiveSecondaryButton(
                                onClick = onDismissRequest
                            ) {
                                Text(stringResource(R.string.exit_step2_cancel))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            AutomotivePrimaryButton(
                                onClick = onConfirmExit,
                                containerColor = palette.statusAlert,
                                contentColor = palette.textPrimary
                            ) {
                                Text(stringResource(R.string.exit_step2_confirm))
                            }
                        }
                    }
                }
            }
        }
    }
}
