// In CsvImportScreen.kt, fix the LinearProgressIndicator and uploadGlucoseSeries issues:

// Change this:
LinearProgressIndicator(
progress = { uploadProgress },
modifier = Modifier.fillMaxWidth()
)

// To this:
LinearProgressIndicator(
progress = uploadProgress,
modifier = Modifier.fillMaxWidth()
)

// For the uploadGlucoseSeries function call, ensure the progressCallback lambda has typed parameters:
healthConnectUploader.uploadGlucoseSeries(
values,
startTime,
endTime,
mealTypes,
progressCallback = { current: Int, total: Int ->
    uploadProgress = current.toFloat() / total.toFloat()
    uploadStatus = "Uploaded $current of $total readings"
}
)