package com.example.glucoseuploader

import androidx.health.connect.client.changes.Change

/**
 * Represents different messages for changes
 */
sealed class ChangesMessage {
    data class ChangeList(val changes: List<Change>) : ChangesMessage()
    data class NoMoreChanges(val nextChangesToken: String) : ChangesMessage()
}