// In GlucoseChangesTrackingScreen.kt, add the missing else branch in the when statement:

// Change this:
healthConnectUploader.getGlucoseChanges(token).collect { message ->
    when (message) {
        is ChangesMessage.ChangeList -> {
            val changeDetails = healthConnectUploader.processGlucoseChanges(message.changes)
            changesList.addAll(changeDetails)
        }
        is ChangesMessage.NoMoreChanges -> {
            newToken = message.nextChangesToken
        }
    }
}

// To this:
healthConnectUploader.getGlucoseChanges(token).collect { message ->
    when (message) {
        is HealthConnectUploader.ChangesMessage.ChangeList -> {
            val changeDetails = healthConnectUploader.processGlucoseChanges(message.changes)
            changesList.addAll(changeDetails)
        }
        is HealthConnectUploader.ChangesMessage.NoMoreChanges -> {
            newToken = message.nextChangesToken
        }
        else -> {
            // Handle unexpected message type
            Log.d("ChangesTracking", "Unexpected message type: $message")
        }
    }
}