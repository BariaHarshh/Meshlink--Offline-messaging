package com.meshlink.app.ui.medical

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlink.app.domain.model.EmergencyContact
import com.meshlink.app.ui.theme.BloodGroupSelected
import com.meshlink.app.ui.theme.BloodGroupUnselected
import com.meshlink.app.ui.theme.ContactAvatarSalmon
import com.meshlink.app.ui.theme.EmergencyGreen
import com.meshlink.app.ui.theme.EmergencyRed
import com.meshlink.app.ui.theme.LightBackground
import com.meshlink.app.ui.theme.LightBorder
import com.meshlink.app.ui.theme.LightSurface
import com.meshlink.app.ui.theme.LightSurfaceVariant
import com.meshlink.app.ui.theme.MeshLinkLightTheme
import com.meshlink.app.ui.theme.MeshReadyGreen
import com.meshlink.app.ui.theme.TextMuted
import com.meshlink.app.ui.theme.TextPrimary
import com.meshlink.app.ui.theme.TextSecondary

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun MedicalProfileScreen(
    onBackClick: () -> Unit,
    viewModel:   MedicalProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Navigate back after successful save
    LaunchedEffect(Unit) {
        viewModel.profileSaved.collect { onBackClick() }
    }

    // Medical Profile uses a light theme
    MeshLinkLightTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightBackground)
        ) {
            // ── Top bar ────────────────────────────────────────────────────────
            MedicalTopBar(onBackClick = onBackClick)

            LazyColumn(
                modifier      = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── Full Identity ──────────────────────────────────────────────
                item {
                    SectionLabel("FULL IDENTITY")
                    IdentityCard(
                        name     = state.fullName,
                        onChange = viewModel::onNameChanged
                    )
                }

                // ── Blood Group ────────────────────────────────────────────────
                item {
                    SectionLabel("BLOOD GROUP")
                    BloodGroupGrid(
                        selected   = state.bloodGroup,
                        onSelected = viewModel::onBloodGroupSelected
                    )
                }

                // ── Mesh Ready card ────────────────────────────────────────────
                item {
                    MeshReadyCard()
                }

                // ── Critical Allergies ─────────────────────────────────────────
                item {
                    SectionLabel("CRITICAL ALLERGIES")
                    ProfileInputField(
                        value       = state.allergies,
                        onChange    = viewModel::onAllergiesChanged,
                        placeholder = "e.g. Penicillin, Peanuts, Latex…",
                        minLines    = 2
                    )
                }

                // ── Current Medications ────────────────────────────────────────
                item {
                    SectionLabel("CURRENT MEDICATIONS")
                    ProfileInputField(
                        value       = state.medications,
                        onChange    = viewModel::onMedicationsChanged,
                        placeholder = "List dosage if known…",
                        minLines    = 2
                    )
                }

                // ── Emergency Contacts ─────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 24.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text  = "EMERGENCY CONTACTS",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmergencyRed,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable { /* Add contact dialog */ }
                        ) {
                            Icon(
                                Icons.Default.AddCircleOutline,
                                contentDescription = "Add contact",
                                tint     = EmergencyRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text  = "Add Contact",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmergencyRed
                            )
                        }
                    }
                }

                items(state.emergencyContacts, key = { it.id }) { contact ->
                    ContactRow(contact = contact)
                }

                item { Spacer(Modifier.height(100.dp)) }  // bottom padding for Save button
            }

            // ── Save Profile button ────────────────────────────────────────────
            SaveProfileButton(
                isSaved = state.isSaved,
                onClick = viewModel::saveProfile
            )
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun MedicalTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LightSurface)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = EmergencyRed
            )
        }
        Text(
            text       = "Medical Profile",
            style      = MaterialTheme.typography.titleLarge,
            color      = EmergencyRed,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = { }) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextSecondary
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(LightBorder))
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelLarge,
        color    = EmergencyRed,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 10.dp)
    )
}

// ── Identity card ─────────────────────────────────────────────────────────────

@Composable
private fun IdentityCard(name: String, onChange: (String) -> Unit) {
    Surface(
        modifier      = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape         = RoundedCornerShape(16.dp),
        color         = LightSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(LightSurfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (name.isEmpty()) {
                    Text(
                        text  = "Your full name",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextMuted
                    )
                }
                BasicTextField(
                    value         = name,
                    onValueChange = onChange,
                    textStyle     = MaterialTheme.typography.titleLarge.copy(
                        color      = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 22.sp
                    ),
                    cursorBrush   = SolidColor(EmergencyRed),
                    modifier      = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint     = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text  = "Ensure this matches your official identification for responders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

// ── Blood group grid ──────────────────────────────────────────────────────────

@Composable
private fun BloodGroupGrid(selected: String, onSelected: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape    = RoundedCornerShape(16.dp),
        color    = LightSurface
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val rows = bloodGroups.chunked(4)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { group ->
                        val isSelected = group == selected
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) BloodGroupSelected else BloodGroupUnselected)
                                .border(
                                    width = if (isSelected) 0.dp else 1.dp,
                                    color = if (isSelected) Color.Transparent else LightBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelected(group) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text       = group,
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color      = if (isSelected) Color.White else TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Mesh Ready card ───────────────────────────────────────────────────────────

@Composable
private fun MeshReadyCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MeshReadyGreen
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment     = Alignment.Top
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint     = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text       = "Mesh Ready",
                    style      = MaterialTheme.typography.titleMedium,
                    color      = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "Your medical data is encrypted and stored locally. It will only be shared during active SOS signals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

// ── Profile input field ───────────────────────────────────────────────────────

@Composable
private fun ProfileInputField(
    value:       String,
    onChange:    (String) -> Unit,
    placeholder: String,
    minLines:    Int = 1
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = LightSurface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text      = placeholder,
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = TextMuted,
                    textAlign = TextAlign.Start
                )
            }
            BasicTextField(
                value         = value,
                onValueChange = onChange,
                textStyle     = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                cursorBrush   = SolidColor(EmergencyRed),
                minLines      = minLines,
                modifier      = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Emergency contact row ─────────────────────────────────────────────────────

@Composable
private fun ContactRow(contact: EmergencyContact) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .shadow(1.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = LightSurface
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Initials avatar
            val initials = contact.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }
                .take(2).joinToString("")
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ContactAvatarSalmon),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = initials,
                    color      = Color.White,
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Name + relation + phone
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = contact.name,
                    style      = MaterialTheme.typography.titleSmall,
                    color      = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text  = "${contact.relation} • ${contact.phone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Drag handle
            Icon(
                Icons.Default.Menu,
                contentDescription = "Reorder",
                tint     = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Save profile button ───────────────────────────────────────────────────────

@Composable
private fun SaveProfileButton(isSaved: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LightSurface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            color = if (isSaved) EmergencyGreen else EmergencyRed
        ) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = null,
                    tint     = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text       = if (isSaved) "Profile Saved ✓" else "Save Profile",
                    style      = MaterialTheme.typography.titleSmall,
                    color      = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
