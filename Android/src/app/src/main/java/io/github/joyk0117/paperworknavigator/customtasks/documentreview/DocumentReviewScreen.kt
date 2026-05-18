package io.github.joyk0117.paperworknavigator.customtasks.documentreview

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.ViewModelProvider
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.joyk0117.paperworknavigator.R
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ChatMessage
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ChatRole
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.DocumentInput
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.DocumentReviewUiState
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.EscalationPackage
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ExtractionError
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.InquiryRecipient
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.MaskResult
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.PiiSpan
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ProcessingStep
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.categoryLabel
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.issuerEmail
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.issuerPhone
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ReviewResult
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.SupportedLanguage
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.Translation
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.ImageTextExtractor
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.TextExtractor
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.util.DocumentIntentBuilder
import io.github.joyk0117.paperworknavigator.data.Model
import io.github.joyk0117.paperworknavigator.data.ModelDownloadStatusType
import io.github.joyk0117.paperworknavigator.data.Task
import io.github.joyk0117.paperworknavigator.ui.common.modelitem.ModelItem
import io.github.joyk0117.paperworknavigator.ui.modelmanager.ModelManagerViewModel

@Composable
fun DocumentReviewScreen(
    modelManagerViewModel: ModelManagerViewModel,
    task: Task? = null,
    availableModels: List<Model> = emptyList(),
    bottomPadding: Dp = 0.dp,
    setAppBarControlsDisabled: (Boolean) -> Unit = {},
    setTopBarVisible: (Boolean) -> Unit = {},
    setCustomNavigateUpCallback: ((() -> Unit)?) -> Unit = {},
) {
    val viewModel: DocumentReviewViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val shouldScrollChatToBottom by viewModel.shouldScrollChatToBottom.collectAsState()
    val isCorrectingOcr by viewModel.isCorrectingOcr.collectAsState()
    val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
    val selectedModel = modelManagerUiState.selectedModel
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Input state lifted to parent so Processing state can mirror the selection.
    // rememberSaveable ensures state survives process death (OOM kill during document scanner).
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var pastedText by rememberSaveable { mutableStateOf("") }
    var isExtractingText by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var pendingOcrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCropUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var croppedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pendingOcrBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pendingOcrUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showOcrLanguageDialog by remember { mutableStateOf(false) }
    var extractionJob by remember { mutableStateOf<Job?>(null) }
    // Bitmaps used for the last OCR pass — retained for user-triggered OCR correction (MF-01c).
    var ocrSourceBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    val modelNotReadyMsg = stringResource(R.string.doc_review_error_model_not_ready)
    val ocrFailedMsg = stringResource(R.string.doc_review_error_ocr_failed)
    val imageReadErrorMsg = stringResource(R.string.doc_review_error_image_read)
    val noPdfTextLayerMsg = stringResource(R.string.doc_review_error_no_text_layer)
    val extractionFailedMsg = stringResource(R.string.doc_review_error_file_read)
    val translationChatResetMsg = stringResource(R.string.doc_review_translation_chat_reset)

    // Observe incoming documents shared/opened from other apps (Activity-scoped ViewModel).
    val activity = LocalActivity.current
    val incomingDocViewModel: IncomingDocumentViewModel? = remember(activity) {
        (activity as? ComponentActivity)?.let {
            ViewModelProvider(it)[IncomingDocumentViewModel::class.java]
        }
    }
    // MF-01c: OCR correction result → update text area
    LaunchedEffect(Unit) {
        viewModel.correctedOcrText.collect { corrected ->
            pastedText = corrected
            ocrSourceBitmaps = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        incomingDocViewModel?.events?.collect { event ->
            // Reset any active session/analysis so the new document starts fresh.
            val currentState = viewModel.uiState.value
            if (currentState !is DocumentReviewUiState.Idle &&
                currentState !is DocumentReviewUiState.Error
            ) {
                viewModel.reset()
            }
            extractionJob?.cancel()
            extractionJob = null
            isExtractingText = false
            pendingCropUri = null

            when (event) {
                is IncomingDocumentEvent.PdfUri -> {
                    val uri = event.uri
                    pastedText = ""
                    // Prefer filename hint from the sender (e.g. Chrome's EXTRA_SUBJECT).
                    // Fall back to ContentResolver query; wrap in try-catch because some
                    // senders grant only stream access and the query may throw SecurityException.
                    selectedFileName = event.fileName
                        ?: try {
                            context.contentResolver.query(
                                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(0) else null
                            }
                        } catch (_: Exception) {
                            null
                        }
                    selectedUri = uri
                    extractionJob = coroutineScope.launch {
                        isExtractingText = true
                        try {
                            pastedText = TextExtractor.extract(context, uri)
                            selectedUri = null
                        } catch (e: ExtractionError.NoPdfTextLayer) {
                            selectedUri = null
                            selectedFileName = null
                            snackbarHostState.showSnackbar(noPdfTextLayerMsg)
                        } catch (e: ExtractionError) {
                            selectedUri = null
                            selectedFileName = null
                            snackbarHostState.showSnackbar(extractionFailedMsg)
                        } finally {
                            isExtractingText = false
                        }
                    }
                }
                is IncomingDocumentEvent.RawText -> {
                    selectedUri = null
                    selectedFileName = null
                    pastedText = event.text
                }
                is IncomingDocumentEvent.ImageUri -> {
                    selectedUri = null
                    selectedFileName = null
                    pastedText = ""
                    extractionJob?.cancel()
                    extractionJob = null
                    pendingCropUri = event.uri
                }
            }
        }
    }

    // One-shot: model not initialized — show snackbar (full navigation requires navController)
    LaunchedEffect(Unit) {
        viewModel.navigateToModelManager.collect {
            snackbarHostState.showSnackbar(modelNotReadyMsg)
        }
    }

    // One-shot: translation completed → show snackbar
    LaunchedEffect(Unit) {
        viewModel.translationCompleted.collect {
            snackbarHostState.showSnackbar(translationChatResetMsg)
        }
    }

    // Disable app bar during processing
    LaunchedEffect(uiState) {
        setAppBarControlsDisabled(
            uiState is DocumentReviewUiState.Processing ||
                uiState is DocumentReviewUiState.GeneratingEscalation
        )
    }

    // Wire custom back navigation for S-02 / S-03 / S-04
    LaunchedEffect(uiState) {
        when (uiState) {
            is DocumentReviewUiState.Review ->
                setCustomNavigateUpCallback { viewModel.reset() }
            is DocumentReviewUiState.GeneratingEscalation ->
                setCustomNavigateUpCallback { /* no-op: back disabled during generation */ }
            is DocumentReviewUiState.OutputPreview ->
                setCustomNavigateUpCallback { viewModel.backFromOutput() }
            is DocumentReviewUiState.InquiryWizard ->
                setCustomNavigateUpCallback { viewModel.backFromInquiryWizard() }
            is DocumentReviewUiState.InquiryPreview ->
                setCustomNavigateUpCallback { viewModel.backFromInquiryPreview() }
            else -> setCustomNavigateUpCallback(null)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = bottomPadding)
                .consumeWindowInsets(PaddingValues(bottom = bottomPadding)),
        ) {
            // OCR 言語選択ダイアログ（カメラ・ギャラリー・Intent 経由の画像すべてに対応）
            if (showOcrLanguageDialog) {
                OcrLanguagePickerDialog(
                    onSelected = { lang ->
                        showOcrLanguageDialog = false
                        val bitmap = pendingOcrBitmap
                        val bitmaps = pendingOcrBitmaps
                        val uris = pendingOcrUris
                        pendingOcrBitmap = null
                        pendingOcrBitmaps = emptyList()
                        pendingOcrUris = emptyList()
                        extractionJob?.cancel()
                        extractionJob = coroutineScope.launch {
                            isExtractingText = true
                            try {
                                when {
                                    bitmap != null -> {
                                        pastedText = ImageTextExtractor.extractFromBitmap(bitmap, lang)
                                        ocrSourceBitmaps = listOf(bitmap)
                                    }
                                    bitmaps.isNotEmpty() -> {
                                        pastedText = bitmaps.map { bmp ->
                                            ImageTextExtractor.extractFromBitmap(bmp, lang)
                                        }.joinToString("\n\n")
                                        ocrSourceBitmaps = bitmaps
                                    }
                                    uris.isNotEmpty() -> {
                                        // Load bitmaps from camera-scanner URIs so they can be
                                        // reused by OcrCorrector (MF-01c) without a second I/O pass.
                                        val loadedBitmaps = withContext(Dispatchers.IO) {
                                            uris.mapNotNull { u ->
                                                runCatching {
                                                    context.contentResolver.openInputStream(u)
                                                        ?.use { BitmapFactory.decodeStream(it) }
                                                }.getOrNull()
                                            }
                                        }
                                        pastedText = loadedBitmaps.map { bmp ->
                                            ImageTextExtractor.extractFromBitmap(bmp, lang)
                                        }.joinToString("\n\n")
                                        ocrSourceBitmaps = loadedBitmaps
                                    }
                                    else -> return@launch
                                }
                            } catch (e: ExtractionError.OcrFailed) {
                                ocrSourceBitmaps = emptyList()
                                snackbarHostState.showSnackbar(ocrFailedMsg)
                            } catch (e: ExtractionError) {
                                ocrSourceBitmaps = emptyList()
                                snackbarHostState.showSnackbar(imageReadErrorMsg)
                            } finally {
                                isExtractingText = false
                            }
                        }
                    },
                    onDismiss = {
                        showOcrLanguageDialog = false
                        pendingOcrBitmap = null
                        pendingOcrBitmaps = emptyList()
                        pendingOcrUris = emptyList()
                        isExtractingText = false
                    },
                )
            }

            // S-04: Inquiry Wizard
            val wizardState = uiState as? DocumentReviewUiState.InquiryWizard
            if (wizardState != null) {
                S04InquiryWizardContent(
                    state = wizardState,
                    onUpdatePurpose = { viewModel.updateWizardPurpose(it) },
                    onUpdateRecipient = { viewModel.updateWizardRecipient(it) },
                    onTogglePiiSpan = { viewModel.toggleWizardPiiSpan(it) },
                    onBuildContext = { viewModel.buildInquiryContext() },
                )
                return@Box
            }

            // S-03: Inquiry Preview
            val previewState = uiState as? DocumentReviewUiState.InquiryPreview
            if (previewState != null) {
                S03InquiryPreviewContent(
                    contextText = previewState.contextText,
                )
                return@Box
            }

            // Single composable call site for S-02: keeps rememberSaveable state
            // alive across the Review ↔ GeneratingEscalation transition.
            val s02State: DocumentReviewUiState.Review? = when (val s = uiState) {
                is DocumentReviewUiState.Review -> s
                is DocumentReviewUiState.GeneratingEscalation -> DocumentReviewUiState.Review(
                    reviewResult = s.reviewResult,
                    piiSpans = s.piiSpans,
                    sourceText = s.sourceText,
                    chatMessages = s.chatMessages,
                    selectedLanguage = s.reviewResult.translation?.language
                        ?: if (s.reviewResult.sourceLanguage == "en") "ja" else "en",
                )
                else -> null
            }
            if (s02State != null) {
                S02ReviewContent(
                    state = s02State,
                    isGeneratingEscalation = uiState is DocumentReviewUiState.GeneratingEscalation,
                    shouldScrollToBottom = shouldScrollChatToBottom,
                    onResetScrollFlag = { viewModel.resetChatScrollFlag() },
                    onTranslate = { language -> viewModel.translate(language) },
                    onSendChatMessage = { message -> viewModel.sendChatMessage(message) },
                    onStartInquiryWizard = { language -> viewModel.startInquiryWizard(language) },
                    onGenerateEscalation = { userNotes ->
                        viewModel.generateEscalation(userNotes)
                    },
                )
            } else {
                when (val state = uiState) {
                    is DocumentReviewUiState.Idle -> {
                        if (pendingCropUri != null) {
                            S01CropContent(
                                imageUri = pendingCropUri!!,
                                onCropApplied = { correctedBitmap ->
                                    pendingCropUri = null
                                    selectedUri = null
                                    selectedFileName = null
                                    pendingOcrBitmap = correctedBitmap
                                    showOcrLanguageDialog = true
                                },
                                onCancel = { pendingCropUri = null },
                            )
                        } else if (pendingCropUris.isNotEmpty()) {
                            key(pendingCropUris.first()) {
                                S01CropContent(
                                    imageUri = pendingCropUris.first(),
                                    onCropApplied = { correctedBitmap ->
                                        val accumulated = croppedBitmaps + correctedBitmap
                                        val remaining = pendingCropUris.drop(1)
                                        if (remaining.isEmpty()) {
                                            pendingCropUris = emptyList()
                                            croppedBitmaps = emptyList()
                                            pendingOcrBitmaps = accumulated
                                            showOcrLanguageDialog = true
                                        } else {
                                            pendingCropUris = remaining
                                            croppedBitmaps = accumulated
                                        }
                                    },
                                    onCancel = {
                                        pendingCropUris = emptyList()
                                        croppedBitmaps = emptyList()
                                    },
                                )
                            }
                        } else {
                            val curDownloadStatus =
                                modelManagerUiState.modelDownloadStatus[selectedModel.name]?.status
                            val isModelReady = curDownloadStatus == ModelDownloadStatusType.SUCCEEDED &&
                                modelManagerUiState.isModelInitialized(selectedModel)
                            S01IdleContent(
                                selectedUri = selectedUri,
                                selectedFileName = selectedFileName,
                                pastedText = pastedText,
                                isModelReady = isModelReady,
                                isExtractingText = isExtractingText,
                                task = task,
                                availableModels = availableModels,
                                modelManagerViewModel = modelManagerViewModel,
                                onScannerError = { message ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(message)
                                    }
                                },
                                onUriSelected = { uri ->
                                    selectedUri = uri
                                    selectedFileName = uri?.let { u ->
                                        context.contentResolver.query(
                                            u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
                                        )?.use { cursor ->
                                            if (cursor.moveToFirst()) cursor.getString(0) else null
                                        }
                                    }
                                    if (uri != null) {
                                        pastedText = ""
                                        ocrSourceBitmaps = emptyList()
                                        viewModel.cancelCorrection()
                                        extractionJob?.cancel()
                                        extractionJob = coroutineScope.launch {
                                            isExtractingText = true
                                            try {
                                                pastedText = TextExtractor.extract(context, uri)
                                                selectedUri = null
                                            } catch (e: ExtractionError.NoPdfTextLayer) {
                                                selectedUri = null
                                                selectedFileName = null
                                                snackbarHostState.showSnackbar(noPdfTextLayerMsg)
                                            } catch (e: ExtractionError) {
                                                selectedUri = null
                                                selectedFileName = null
                                                snackbarHostState.showSnackbar(extractionFailedMsg)
                                            } finally {
                                                isExtractingText = false
                                            }
                                        }
                                    }
                                },
                                onCameraImagesSelected = { uris ->
                                    viewModel.cancelCorrection()
                                    selectedUri = null
                                    selectedFileName = null
                                    pastedText = ""
                                    ocrSourceBitmaps = emptyList()
                                    pendingOcrUris = uris
                                    showOcrLanguageDialog = true
                                },
                                onImageSelectedForCrop = { uri ->
                                    viewModel.cancelCorrection()
                                    selectedUri = null
                                    selectedFileName = null
                                    pastedText = ""
                                    ocrSourceBitmaps = emptyList()
                                    pendingCropUri = uri
                                },
                                onImagesSelected = { uris ->
                                    viewModel.cancelCorrection()
                                    selectedUri = null
                                    selectedFileName = null
                                    pastedText = ""
                                    ocrSourceBitmaps = emptyList()
                                    pendingCropUris = uris
                                    croppedBitmaps = emptyList()
                                },
                                onTextChanged = { text ->
                                    pastedText = text
                                    if (text.isNotBlank()) {
                                        viewModel.cancelCorrection()
                                        selectedUri = null
                                        selectedFileName = null
                                        ocrSourceBitmaps = emptyList()
                                    }
                                },
                                ocrSourceBitmaps = ocrSourceBitmaps,
                                isCorrectingOcr = isCorrectingOcr,
                                onCorrectOcr = { text, bitmaps ->
                                    viewModel.correctOcr(selectedModel, text, bitmaps)
                                },
                                onAnalyze = { input ->
                                    ocrSourceBitmaps = emptyList()
                                    viewModel.analyzeDocument(input, selectedModel)
                                },
                                onReset = {
                                    viewModel.cancelCorrection()
                                    extractionJob?.cancel()
                                    extractionJob = null
                                    selectedUri = null
                                    selectedFileName = null
                                    pastedText = ""
                                    ocrSourceBitmaps = emptyList()
                                    isExtractingText = false
                                },
                            )
                        } // end else (pendingCropUri == null)
                    }
                    is DocumentReviewUiState.Processing -> S01ProcessingContent(
                        state = state,
                        selectedFileName = selectedFileName,
                        pastedText = pastedText,
                        modelDisplayName = selectedModel.displayName.ifBlank { selectedModel.name },
                    )
                    is DocumentReviewUiState.Error -> S01ErrorContent(
                        messageRes = state.messageRes,
                        onRetry = { viewModel.retry() },
                    )
                    is DocumentReviewUiState.OutputPreview -> S03OutputPreviewContent(
                        pkg = state.pkg,
                    )
                    else -> {} // Review and GeneratingEscalation handled above
                }
            }
        }
    }
}

// ─── S-03: Output Preview ─────────────────────────────────────────────────────

@Composable
private fun S03OutputPreviewContent(pkg: EscalationPackage) {
    val context = LocalContext.current
    val plainText = remember(pkg) { pkg.toPlainText() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Masked PII banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (pkg.maskedFields.isEmpty()) {
                    stringResource(R.string.doc_review_pii_masked_header)
                } else {
                    stringResource(R.string.doc_review_pii_masked_header_with_fields, pkg.maskedFields.joinToString(", "))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        // Scrollable preview area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = plainText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        HorizontalDivider()

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { copyToClipboard(context, plainText) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.copy))
            }
            Button(
                onClick = { shareText(context, plainText) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.share))
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String, label: String = "escalation_package") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

// ─── S-01: Idle ───────────────────────────────────────────────────────────────

@Composable
private fun S01IdleContent(
    selectedUri: Uri?,
    selectedFileName: String?,
    pastedText: String,
    isModelReady: Boolean,
    isExtractingText: Boolean,
    ocrSourceBitmaps: List<Bitmap>,
    isCorrectingOcr: Boolean,
    task: Task?,
    availableModels: List<Model>,
    modelManagerViewModel: ModelManagerViewModel,
    onScannerError: (String) -> Unit,
    onUriSelected: (Uri?) -> Unit,
    onCameraImagesSelected: (List<Uri>) -> Unit,
    onImageSelectedForCrop: (Uri) -> Unit,
    onImagesSelected: (List<Uri>) -> Unit,
    onTextChanged: (String) -> Unit,
    onCorrectOcr: (String, List<Bitmap>) -> Unit,
    onAnalyze: (DocumentInput) -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val scannerNoResultMsg = stringResource(R.string.doc_review_error_scanner_no_result)
    val scannerNoImageMsg = stringResource(R.string.doc_review_error_scanner_no_image)
    val scannerStartErrorMsg = stringResource(R.string.doc_review_error_scanner_start)

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> onUriSelected(uri) }

    val documentScanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data == null) {
                onScannerError(scannerNoResultMsg)
                return@rememberLauncherForActivityResult
            }
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(data)
            val pages = scanResult?.pages
            if (pages.isNullOrEmpty()) {
                onScannerError(scannerNoImageMsg)
            } else {
                onCameraImagesSelected(pages.map { it.imageUri })
            }
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        when {
            uris.isEmpty() -> { /* nothing selected */ }
            uris.size == 1 -> onImageSelectedForCrop(uris.first())
            else -> onImagesSelected(uris)
        }
    }

    val anyUriSelected = selectedUri != null
    val hasInput = anyUriSelected || pastedText.isNotBlank()
    var isTextExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Model selector section
        if (availableModels.isNotEmpty() && task != null) {
            val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
            val selectedModelName = modelManagerUiState.selectedModel.name
            Text(
                text = stringResource(R.string.doc_review_model_section_title),
                style = MaterialTheme.typography.titleMedium,
            )
            availableModels.forEach { model ->
                val isSelected = model.name == selectedModelName
                Box(
                    modifier = if (isSelected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp),
                        )
                    } else {
                        Modifier
                    }
                ) {
                    ModelItem(
                        model = model,
                        task = task,
                        modelManagerViewModel = modelManagerViewModel,
                        onModelClicked = { if (!isSelected) modelManagerViewModel.selectModel(it) },
                        onBenchmarkClicked = {},
                        showDeleteButton = false,
                        canExpand = false,
                        expanded = true,
                        showBenchmarkButton = false,
                        showInfo = false,
                        tryItLabel = if (isSelected) {
                            stringResource(R.string.doc_review_model_in_use)
                        } else {
                            stringResource(R.string.doc_review_select_model)
                        },
                    )
                }
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.doc_review_select_document),
            style = MaterialTheme.typography.titleMedium,
        )

        // PDF file picker
        OutlinedButton(
            onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = pastedText.isBlank() && !isExtractingText,
        ) {
            if (isExtractingText && selectedFileName != null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.doc_review_step_extracting_text), maxLines = 1)
            } else {
                Icon(Icons.Outlined.Description, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = selectedFileName ?: stringResource(R.string.doc_review_open_pdf),
                    maxLines = 1,
                )
            }
        }

        // Camera capture button (ML Kit Document Scanner)
        OutlinedButton(
            onClick = {
                activity?.let { act ->
                    val options = GmsDocumentScannerOptions.Builder()
                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                        .build()
                    GmsDocumentScanning.getClient(options)
                        .getStartScanIntent(act)
                        .addOnSuccessListener { intentSender ->
                            documentScanLauncher.launch(
                                IntentSenderRequest.Builder(intentSender).build()
                            )
                        }
                        .addOnFailureListener { onScannerError(scannerStartErrorMsg) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = pastedText.isBlank() && selectedUri == null && activity != null && !isExtractingText,
        ) {
            if (isExtractingText) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.doc_review_step_extracting_text), maxLines = 1)
            } else {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.doc_review_camera_capture), maxLines = 1)
            }
        }

        // Gallery image picker → crop/correction screen → OCR
        OutlinedButton(
            onClick = { imageLauncher.launch(arrayOf("image/jpeg", "image/png")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = pastedText.isBlank() && selectedUri == null && !isExtractingText,
        ) {
            Icon(Icons.Outlined.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.doc_review_open_image), maxLines = 1)
        }

        // Separator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = " ${stringResource(R.string.doc_review_or)} ",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        // Collapsible text paste area
        val showExpandButton = pastedText.length >= 200
        val textAreaModifier = when {
            !showExpandButton -> Modifier.fillMaxWidth()
            isTextExpanded -> Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 350.dp)
            else -> Modifier.fillMaxWidth().height(100.dp)
        }
        val textMaxLines = if (!showExpandButton || isTextExpanded) Int.MAX_VALUE else 4

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.animateContentSize(),
        ) {
            OutlinedTextField(
                value = pastedText,
                onValueChange = onTextChanged,
                label = { Text(stringResource(R.string.doc_review_paste_text)) },
                modifier = textAreaModifier,
                minLines = 3,
                maxLines = textMaxLines,
                enabled = !anyUriSelected && !isExtractingText,
            )
            if (showExpandButton) {
                TextButton(
                    onClick = { isTextExpanded = !isTextExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Spacer(Modifier.weight(1f))
                    if (isTextExpanded) {
                        Icon(
                            Icons.Outlined.ExpandLess,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.doc_review_text_collapse))
                    } else {
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.doc_review_text_expand, pastedText.length))
                    }
                }
            }
        }

        // OCR Correct + Reset + Analyze buttons
        if (ocrSourceBitmaps.isNotEmpty()) {
            OutlinedButton(
                onClick = { onCorrectOcr(pastedText, ocrSourceBitmaps) },
                modifier = Modifier.fillMaxWidth(),
                enabled = isModelReady && !isCorrectingOcr && !isExtractingText,
            ) {
                if (isCorrectingOcr) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.doc_review_correct_ocr))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasInput) {
                OutlinedButton(
                    onClick = onReset,
                    enabled = !isCorrectingOcr,
                ) {
                    Text(stringResource(R.string.reset))
                }
            }
            Button(
                onClick = {
                    val input = when {
                        selectedUri != null -> DocumentInput.PdfUri(selectedUri!!)
                        else -> DocumentInput.RawText(pastedText.trim())
                    }
                    onAnalyze(input)
                },
                modifier = if (hasInput) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                enabled = hasInput && isModelReady && !isExtractingText && !isCorrectingOcr,
            ) {
                Text(stringResource(R.string.doc_review_analyze))
            }
        }

        if (!isModelReady) {
            Text(
                text = stringResource(R.string.doc_review_model_initializing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── OCR Language Picker Dialog ──────────────────────────────────────────────

@Composable
private fun OcrLanguagePickerDialog(
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ocrLanguages = SupportedLanguage.entries
        .filter { it.supportsOcr }
        .sortedWith(compareByDescending { it == SupportedLanguage.JA })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.doc_review_ocr_language_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ocrLanguages.forEach { lang ->
                    TextButton(
                        onClick = { onSelected(lang.code) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = lang.displayName,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.doc_review_ocr_language_cancel))
            }
        },
    )
}

// ─── S-01: Crop / Perspective Correction ─────────────────────────────────────

@Composable
private fun S01CropContent(
    imageUri: Uri,
    onCropApplied: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isApplying by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            val decoded = context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext
            val maxDim = 2000
            val scale = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height, 1f)
            bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt(),
                    (decoded.height * scale).toInt(),
                    true,
                ).also { decoded.recycle() }
            } else {
                decoded
            }
        }
    }

    val bm = bitmap
    if (bm == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Fractional corner positions [0..1]: order = topLeft, topRight, bottomLeft, bottomRight
    var corners by remember {
        mutableStateOf(
            listOf(
                Offset(0.05f, 0.05f),
                Offset(0.95f, 0.05f),
                Offset(0.05f, 0.95f),
                Offset(0.95f, 0.95f),
            )
        )
    }
    var activeCornerIndex by remember { mutableStateOf<Int?>(null) }

    var imgLeft by remember { mutableStateOf(0f) }
    var imgTop by remember { mutableStateOf(0f) }
    var imgDispW by remember { mutableStateOf(1f) }
    var imgDispH by remember { mutableStateOf(1f) }

    val handleRadiusPx = with(density) { 18.dp.toPx() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.doc_review_crop_instructions),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            activeCornerIndex = corners.indices
                                .minByOrNull { i ->
                                    val cx = imgLeft + corners[i].x * imgDispW
                                    val cy = imgTop + corners[i].y * imgDispH
                                    (Offset(cx, cy) - offset).getDistance()
                                }
                                ?.takeIf { i ->
                                    val cx = imgLeft + corners[i].x * imgDispW
                                    val cy = imgTop + corners[i].y * imgDispH
                                    (Offset(cx, cy) - offset).getDistance() <= handleRadiusPx * 3
                                }
                        },
                        onDrag = { change, _ ->
                            activeCornerIndex?.let { idx ->
                                corners = corners.mapIndexed { i, frac ->
                                    if (i == idx) {
                                        Offset(
                                            ((change.position.x - imgLeft) / imgDispW.coerceAtLeast(1f))
                                                .coerceIn(0f, 1f),
                                            ((change.position.y - imgTop) / imgDispH.coerceAtLeast(1f))
                                                .coerceIn(0f, 1f),
                                        )
                                    } else frac
                                }
                            }
                        },
                        onDragEnd = { activeCornerIndex = null },
                        onDragCancel = { activeCornerIndex = null },
                    )
                }
        ) {
            val imageBitmap = remember(bm) { bm.asImageBitmap() }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        val cw = coords.size.width.toFloat()
                        val ch = coords.size.height.toFloat()
                        val scale = minOf(cw / bm.width, ch / bm.height)
                        imgDispW = bm.width * scale
                        imgDispH = bm.height * scale
                        imgLeft = (cw - imgDispW) / 2
                        imgTop = (ch - imgDispH) / 2
                    }
            ) {
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
                    dstSize = IntSize(imgDispW.toInt().coerceAtLeast(1), imgDispH.toInt().coerceAtLeast(1)),
                )

                val sc = corners.map { f ->
                    Offset(imgLeft + f.x * imgDispW, imgTop + f.y * imgDispH)
                }

                val path = Path().apply {
                    moveTo(sc[0].x, sc[0].y)
                    lineTo(sc[1].x, sc[1].y)
                    lineTo(sc[3].x, sc[3].y)
                    lineTo(sc[2].x, sc[2].y)
                    close()
                }
                drawPath(path, color = Color.Yellow.copy(alpha = 0.7f), style = Stroke(width = 3f))

                sc.forEachIndexed { i, pos ->
                    drawCircle(color = Color.White, radius = handleRadiusPx, center = pos)
                    drawCircle(
                        color = if (i == activeCornerIndex) Color.Yellow else Color(0xFF1976D2),
                        radius = handleRadiusPx,
                        center = pos,
                        style = Stroke(width = 3f),
                    )
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = {
                    isApplying = true
                    val bitmapCopy = bm
                    val cornersCopy = corners
                    coroutineScope.launch {
                        val corrected = withContext(Dispatchers.Default) {
                            applyPerspectiveCorrection(bitmapCopy, cornersCopy)
                        }
                        onCropApplied(corrected)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isApplying,
            ) {
                if (isApplying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.doc_review_crop_processing))
                } else {
                    Text(stringResource(R.string.doc_review_crop_apply))
                }
            }
        }
    }
}

private fun applyPerspectiveCorrection(bitmap: Bitmap, corners: List<Offset>): Bitmap {
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()

    // corners: [TL, TR, BL, BR] in fractional coords [0..1]
    fun pixelDist(a: Offset, b: Offset) =
        sqrt((a.x - b.x).pow(2) * w.pow(2) + (a.y - b.y).pow(2) * h.pow(2))

    val outW = max(pixelDist(corners[0], corners[1]), pixelDist(corners[2], corners[3]))
        .toInt().coerceIn(1, bitmap.width * 2)
    val outH = max(pixelDist(corners[0], corners[2]), pixelDist(corners[1], corners[3]))
        .toInt().coerceIn(1, bitmap.height * 2)

    val srcPoints = floatArrayOf(
        corners[0].x * w, corners[0].y * h,
        corners[1].x * w, corners[1].y * h,
        corners[2].x * w, corners[2].y * h,
        corners[3].x * w, corners[3].y * h,
    )
    val dstPoints = floatArrayOf(
        0f, 0f,
        outW.toFloat(), 0f,
        0f, outH.toFloat(),
        outW.toFloat(), outH.toFloat(),
    )

    val matrix = android.graphics.Matrix()
    matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

    val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    canvas.drawBitmap(bitmap, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    return output
}

// ─── S-01: Processing ─────────────────────────────────────────────────────────

@Composable
private fun S01ProcessingContent(
    state: DocumentReviewUiState.Processing,
    selectedFileName: String?,
    pastedText: String,
    modelDisplayName: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.doc_review_select_document),
            style = MaterialTheme.typography.bodyLarge,
        )

        // Disabled file / text display
        if (selectedFileName != null) {
            OutlinedButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
            ) {
                Icon(Icons.Outlined.Description, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text = selectedFileName, maxLines = 1)
            }
        } else if (pastedText.isNotBlank()) {
            OutlinedTextField(
                value = pastedText.take(120).let { if (pastedText.length > 120) "$it…" else it },
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                enabled = false,
                minLines = 4,
                maxLines = 10,
            )
        } else {
            // Camera-scanned image (OCR result placed in pastedText, no filename)
            OutlinedButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
            ) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.doc_review_scanned_document), maxLines = 1)
            }
        }

        // Current step label
        Text(
            text = "⟳ ${stringResource(state.step.labelRes)}",
            style = MaterialTheme.typography.bodyMedium,
        )

        // Progress bar
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Model name
        Text(
            text = stringResource(R.string.doc_review_model_on_device, modelDisplayName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Partial fields preview during EXTRACTING_FIELDS step
        if (state.step == ProcessingStep.EXTRACTING_FIELDS && state.partialFields.isNotEmpty()) {
            HorizontalDivider()
            Column(
                modifier = Modifier.animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Spacer(Modifier.height(0.dp))
                state.partialFields.forEach { (key, value) ->
                    val label = when (key) {
                        "DOC_NAME" -> stringResource(R.string.doc_review_partial_field_doc_name)
                        "IMPORTANCE" -> stringResource(R.string.doc_review_partial_field_importance)
                        "SUMMARY" -> stringResource(R.string.doc_review_partial_field_summary)
                        "DEADLINE_NOTE", "DEADLINE_DATE" -> stringResource(R.string.doc_review_partial_field_deadline)
                        "ACTION_ITEMS" -> stringResource(R.string.doc_review_partial_field_action_items)
                        "WARNING" -> stringResource(R.string.doc_review_partial_field_warnings)
                        else -> return@forEach
                    }
                    // Strip pipe-delimited sub-fields; show only the first item for multi-value keys
                    val displayValue = when (key) {
                        "ACTION_ITEMS" -> value.split("|||").first().trim()
                        "WARNING" -> value.split("|", limit = 2).getOrElse(1) { value }.trim()
                        else -> value
                    }.let { if (it.length > 60) it.take(60) + "…" else it }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(80.dp),
                        )
                        Text(
                            text = displayValue,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// ─── S-01: Error ──────────────────────────────────────────────────────────────

@Composable
private fun S01ErrorContent(
    messageRes: Int,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = stringResource(R.string.doc_review_analysis_failed_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onRetry) {
            Text(stringResource(R.string.doc_review_retry))
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

// ─── S-02: Review Screen ──────────────────────────────────────────────────────

@Composable
private fun S02ReviewContent(
    state: DocumentReviewUiState.Review,
    isGeneratingEscalation: Boolean,
    shouldScrollToBottom: Boolean,
    onResetScrollFlag: () -> Unit,
    onTranslate: (language: String) -> Unit,
    onSendChatMessage: (String) -> Unit,
    onStartInquiryWizard: (String) -> Unit,
    onGenerateEscalation: (userNotes: String) -> Unit,
) {
    var sourceTextExpanded by rememberSaveable { mutableStateOf(false) }
    var inputText by rememberSaveable { mutableStateOf("") }
    var userNotes by rememberSaveable { mutableStateOf("") }
    var showInquiryLanguageDialog by rememberSaveable { mutableStateOf(false) }

    val isChatDisabled = state.chatIsGenerating || isGeneratingEscalation || state.isTranslating

    val scrollState = rememberScrollState()

    // Scroll to bottom only when the user has sent a message (not on initial greeting or reinit).
    LaunchedEffect(shouldScrollToBottom) {
        if (shouldScrollToBottom) {
            scrollState.animateScrollTo(scrollState.maxValue)
            onResetScrollFlag()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState),
    ) {
        // Source text section (collapsible) — above translation bar
        if (state.sourceText.isNotBlank()) {
            SourceTextSection(
                sourceText = state.sourceText,
                expanded = sourceTextExpanded,
                onToggleExpand = { sourceTextExpanded = !sourceTextExpanded },
            )
            HorizontalDivider()
        }

        // Translation bar
        TranslationBar(
            selectedLanguage = state.selectedLanguage,
            sourceLanguage = state.reviewResult.sourceLanguage,
            isTranslated = state.reviewResult.translation != null,
            isTranslating = state.isTranslating,
            translationError = state.translationError,
            isDisabled = isChatDisabled,
            onTranslate = onTranslate,
        )

        HorizontalDivider()

        // Section 1: Bilingual review
        BilingualReviewSection(reviewResult = state.reviewResult)

        // Section 2: Chat (only if chatAvailable)
        if (state.chatAvailable) {
            ChatSection(
                messages = state.chatMessages,
                partialResponse = state.partialChatResponse,
                chatErrorRes = state.chatErrorRes,
                chatIsGenerating = state.chatIsGenerating,
                inputEnabled = !isChatDisabled,
                chatLimitReached = state.chatLimitReached,
                inputText = inputText,
                onInputChanged = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() && !isChatDisabled) {
                        onSendChatMessage(inputText.trim())
                        inputText = ""
                    }
                },
            )
            HorizontalDivider()
        }

        // Section 4: Inquiry wizard entry button
        InquiryWizardEntrySection(
            isDisabled = isChatDisabled,
            onStartWizard = {
                if (state.reviewResult.translation == null) {
                    onStartInquiryWizard(state.reviewResult.sourceLanguage)
                } else {
                    showInquiryLanguageDialog = true
                }
            },
        )
    }

    // Language selection dialog — only shown when a translation exists
    if (showInquiryLanguageDialog && state.reviewResult.translation != null) {
        val reviewResult = state.reviewResult
        val sourceLang = reviewResult.sourceLanguage
        val translatedLang = reviewResult.translation!!.language
        val sourceDisplayName = SupportedLanguage.fromCode(sourceLang)?.displayName ?: sourceLang
        val translatedDisplayName = SupportedLanguage.fromCode(translatedLang)?.displayName ?: translatedLang
        AlertDialog(
            onDismissRequest = { showInquiryLanguageDialog = false },
            title = { Text(stringResource(R.string.doc_review_inquiry_language_dialog_title)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showInquiryLanguageDialog = false
                            onStartInquiryWizard(sourceLang)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = sourceDisplayName, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            showInquiryLanguageDialog = false
                            onStartInquiryWizard(translatedLang)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = translatedDisplayName, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showInquiryLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

// ─── S-02: Source Text Section ───────────────────────────────────────────────

@Composable
private fun SourceTextSection(
    sourceText: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.doc_review_source_text_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onToggleExpand,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(
                        if (expanded) R.string.doc_review_source_text_collapse
                        else R.string.doc_review_source_text_expand
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        if (expanded) {
            SelectionContainer {
                Text(
                    text = sourceText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                )
            }
        }
    }
}

private val LANGUAGE_OPTIONS = SupportedLanguage.entries.map { it.code to it.displayName }

// ─── S-02: Translation Bar ────────────────────────────────────────────────────

@Composable
private fun TranslationBar(
    selectedLanguage: String,
    sourceLanguage: String,
    isTranslated: Boolean,
    isTranslating: Boolean,
    translationError: Boolean,
    isDisabled: Boolean,
    onTranslate: (String) -> Unit,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    var localLanguage by rememberSaveable { mutableStateOf(selectedLanguage) }

    val translationOptions = LANGUAGE_OPTIONS.filter { it.first != sourceLanguage }
    val langLabel = translationOptions.firstOrNull { it.first == localLanguage }?.second
        ?: LANGUAGE_OPTIONS.firstOrNull { it.first == localLanguage }?.second
        ?: localLanguage
    val isInteractionDisabled = isTranslating || isDisabled

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "🌐",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (isTranslating) {
                // Translating state
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.doc_review_translating, langLabel),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else if (isTranslated) {
                // Translated state
                Text(
                    text = stringResource(R.string.doc_review_translated, langLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                // Retranslate dropdown
                Box {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        enabled = !isInteractionDisabled,
                        contentPadding = PaddingValues(
                            horizontal = 10.dp, vertical = 4.dp,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.doc_review_retranslate),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    DropdownMenuLanguageSelector(
                        expanded = dropdownExpanded,
                        currentLanguage = localLanguage,
                        options = translationOptions,
                        onDismiss = { dropdownExpanded = false },
                        onLanguageSelected = { lang ->
                            localLanguage = lang
                            dropdownExpanded = false
                            onTranslate(lang)
                        },
                    )
                }
            } else {
                // Untranslated state: language picker + translate button
                Box {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        enabled = !isInteractionDisabled,
                        contentPadding = PaddingValues(
                            horizontal = 10.dp, vertical = 4.dp,
                        ),
                    ) {
                        Text(
                            text = "$langLabel ▼",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    DropdownMenuLanguageSelector(
                        expanded = dropdownExpanded,
                        currentLanguage = localLanguage,
                        options = translationOptions,
                        onDismiss = { dropdownExpanded = false },
                        onLanguageSelected = { lang ->
                            localLanguage = lang
                            dropdownExpanded = false
                        },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { onTranslate(localLanguage) },
                    enabled = !isInteractionDisabled,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.doc_review_translate_button),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        // Error message below the bar
        if (translationError) {
            Text(
                text = stringResource(R.string.doc_review_translation_error),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DropdownMenuLanguageSelector(
    expanded: Boolean,
    currentLanguage: String,
    options: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        options.forEach { (code, label) ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = label,
                        fontWeight = if (code == currentLanguage) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                onClick = { onLanguageSelected(code) },
            )
        }
    }
}

// ─── S-02: Bilingual Review Section ──────────────────────────────────────────

@Composable
private fun BilingualReviewSection(reviewResult: ReviewResult) {
    val translation = reviewResult.translation
    val langLabel = translationLanguageLabel(translation?.language)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Column headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                text = translationLanguageLabel(reviewResult.sourceLanguage),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (translation != null) {
                Text(
                    text = langLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            // Japanese column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                JaReviewContent(reviewResult = reviewResult)
            }

            if (translation != null) {
                VerticalDivider()
                // Translated column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TranslatedReviewContent(reviewResult = reviewResult, translation = translation)
                }
            }
        }
    }
}

private fun translationLanguageLabel(code: String?): String = when (code) {
    "en" -> "English"
    "ja" -> "日本語"
    "zh" -> "中文"
    "ko" -> "한국어"
    "es" -> "Español"
    "fr" -> "Français"
    "de" -> "Deutsch"
    "it" -> "Italiano"
    "pt" -> "Português"
    "ru" -> "Русский"
    "pl" -> "Polski"
    "nl" -> "Nederlands"
    "ar" -> "العربية"
    "th" -> "ภาษาไทย"
    "tr" -> "Türkçe"
    else -> code ?: ""
}

@Composable
private fun JaReviewContent(reviewResult: ReviewResult) {
    // Summary
    Text(
        text = reviewResult.summaryJa,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // ── アクション・緊急情報 ────────────────────────────────────────────
    // Importance
    if (reviewResult.importance.isNotBlank()) {
        ReviewBadgeItem(
            icon = "📊",
            label = stringResource(R.string.doc_review_label_importance),
            badgeColor = ReviewBadgeColor.GRAY,
            content = reviewResult.importance.replaceFirstChar { it.uppercase() },
        )
    }

    // Deadline (red badge when importance is high) + calendar button
    val deadline = reviewResult.deadline
    if (deadline.noteJa != null || deadline.date != null) {
        val isHighImportance = reviewResult.importance == "high"
        val calIntent = deadline.date?.let {
            DocumentIntentBuilder.calendarIntent(reviewResult.docName, it, deadline.noteJa)
        }
        ReviewBadgeItem(
            icon = if (isHighImportance) "⚠️" else "📅",
            label = stringResource(R.string.doc_review_label_deadline),
            badgeColor = if (isHighImportance) ReviewBadgeColor.RED else ReviewBadgeColor.GRAY,
            content = deadline.noteJa ?: deadline.date ?: "",
            trailingButton = calIntent?.let { intent -> { IntentIconButton("📅", intent) } },
        )
    }

    // Event dates + calendar buttons
    EventDatesSection(reviewResult = reviewResult)

    // Warning (single, red for high severity, gray for others)
    reviewResult.warning?.let { warning ->
        ReviewBadgeItem(
            icon = if (warning.severity == "high") "⚠️" else "ℹ️",
            label = stringResource(R.string.doc_review_label_warning),
            badgeColor = if (warning.severity == "high") ReviewBadgeColor.RED else ReviewBadgeColor.GRAY,
            content = warning.descriptionJa,
        )
    }

    // Action items (orange badge)
    if (reviewResult.actionItems.isNotEmpty()) {
        ReviewBadgeItem(
            icon = "📋",
            label = stringResource(R.string.doc_review_label_action),
            badgeColor = ReviewBadgeColor.ORANGE,
            content = reviewResult.actionItems.joinToString("\n") { "・${it.descriptionJa}" },
        )
    }

    // Required items (blue badge)
    if (reviewResult.requiredItems.isNotEmpty()) {
        ReviewBadgeItem(
            icon = "📎",
            label = stringResource(R.string.doc_review_label_required_docs),
            badgeColor = ReviewBadgeColor.BLUE,
            content = reviewResult.requiredItems.joinToString("\n") { item ->
                "・${item.nameJa}${item.noteJa?.let { " ($it)" } ?: ""}"
            },
        )
    }

    // ── 書類基本情報 ────────────────────────────────────────────────────
    reviewResult.docDate?.let {
        ReviewBadgeItem(
            icon = "🗓",
            label = stringResource(R.string.doc_review_label_doc_date),
            badgeColor = ReviewBadgeColor.GRAY,
            content = it,
        )
    }
    reviewResult.applicantName?.let {
        ReviewBadgeItem(
            icon = "👤",
            label = stringResource(R.string.doc_review_label_target_person),
            badgeColor = ReviewBadgeColor.GRAY,
            content = it,
        )
    }

    // ── 発行者・窓口連絡先 / 金融情報 / その他 / デジタル識別子 ────────
    EntityGroupedFields(reviewResult = reviewResult)
}

@Composable
private fun TranslatedReviewContent(reviewResult: ReviewResult, translation: Translation) {
    // Summary (translated)
    Text(
        text = translation.summary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // ── アクション・緊急情報 ────────────────────────────────────────────
    // Importance
    if (reviewResult.importance.isNotBlank()) {
        ReviewBadgeItem(
            icon = "📊",
            label = stringResource(R.string.doc_review_label_importance),
            badgeColor = ReviewBadgeColor.GRAY,
            content = reviewResult.importance.replaceFirstChar { it.uppercase() },
        )
    }

    // Deadline (red badge when importance is high) + calendar button
    val isHighImportance = reviewResult.importance == "high"
    translation.deadlineNote?.let { note ->
        val calIntent = reviewResult.deadline.date?.let {
            DocumentIntentBuilder.calendarIntent(reviewResult.docName, it, note)
        }
        ReviewBadgeItem(
            icon = if (isHighImportance) "⚠️" else "📅",
            label = stringResource(R.string.doc_review_label_deadline),
            badgeColor = if (isHighImportance) ReviewBadgeColor.RED else ReviewBadgeColor.GRAY,
            content = note,
            trailingButton = calIntent?.let { intent -> { IntentIconButton("📅", intent) } },
        )
    }

    // Event dates + calendar buttons (not translated — shared composable)
    EventDatesSection(reviewResult = reviewResult)

    // Warning (single, inherit severity from original)
    translation.warning?.let { tw ->
        val severity = reviewResult.warning?.severity ?: "medium"
        ReviewBadgeItem(
            icon = if (severity == "high") "⚠️" else "ℹ️",
            label = stringResource(R.string.doc_review_label_warning),
            badgeColor = if (severity == "high") ReviewBadgeColor.RED else ReviewBadgeColor.GRAY,
            content = tw.description,
        )
    }

    // Action items (orange badge)
    if (translation.actionItems.isNotEmpty()) {
        ReviewBadgeItem(
            icon = "📋",
            label = stringResource(R.string.doc_review_label_action),
            badgeColor = ReviewBadgeColor.ORANGE,
            content = translation.actionItems.joinToString("\n") { "・${it.description}" },
        )
    }

    // Required items (blue badge)
    if (translation.requiredItems.isNotEmpty()) {
        ReviewBadgeItem(
            icon = "📎",
            label = stringResource(R.string.doc_review_label_required_docs),
            badgeColor = ReviewBadgeColor.BLUE,
            content = translation.requiredItems.joinToString("\n") { item ->
                "・${item.name}${item.note?.let { " ($it)" } ?: ""}"
            },
        )
    }

    // ── 書類基本情報 ────────────────────────────────────────────────────
    // Document date (not translated — show original)
    reviewResult.docDate?.let {
        ReviewBadgeItem(
            icon = "🗓",
            label = stringResource(R.string.doc_review_label_doc_date),
            badgeColor = ReviewBadgeColor.GRAY,
            content = it,
        )
    }
    reviewResult.applicantName?.let {
        ReviewBadgeItem(
            icon = "👤",
            label = stringResource(R.string.doc_review_label_target_person),
            badgeColor = ReviewBadgeColor.GRAY,
            content = it,
        )
    }

    // ── 発行者・窓口連絡先 / 金融情報 / その他 / デジタル識別子 ────────
    EntityGroupedFields(reviewResult = reviewResult)
}

// ─── S-02: Entity-grouped fields (not translated) ────────────────────────────
// Display order mirrors 抽出アーキテクチャ仕様書.md §2 group order.

@Composable
private fun EntityGroupedFields(reviewResult: ReviewResult) {
    // ── 発行者・窓口連絡先 ──────────────────────────────────────────────
    reviewResult.issuerName?.let { name ->
        ReviewBadgeItem(
            icon = "🏢",
            label = stringResource(R.string.doc_review_label_issuer),
            badgeColor = ReviewBadgeColor.GRAY,
            content = name,
        )
    }
    reviewResult.issuerAddress?.takeIf { it.isNotBlank() }?.let { addr ->
        val mapsIntent = DocumentIntentBuilder.mapsIntent(addr, null)
        ReviewBadgeItem(
            icon = "📍",
            label = stringResource(R.string.doc_review_label_office),
            badgeColor = ReviewBadgeColor.GRAY,
            content = addr,
            trailingButton = mapsIntent?.let { intent -> { IntentIconButton("🗺", intent) } },
        )
    }
    reviewResult.issuerPhone()?.takeIf { it.isNotBlank() }?.let { phone ->
        ReviewBadgeItem(
            icon = "📞",
            label = stringResource(R.string.doc_review_label_contact_phone),
            badgeColor = ReviewBadgeColor.GRAY,
            content = phone,
            trailingButton = DocumentIntentBuilder.phoneIntent(phone)?.let { intent ->
                { IntentIconButton("📞", intent) }
            },
        )
    }
    reviewResult.issuerEmail()?.takeIf { it.isNotBlank() }?.let { email ->
        ReviewBadgeItem(
            icon = "✉️",
            label = stringResource(R.string.doc_review_label_contact_email),
            badgeColor = ReviewBadgeColor.GRAY,
            content = email,
            trailingButton = DocumentIntentBuilder.emailIntent(email)?.let { intent ->
                { IntentIconButton("✉️", intent) }
            },
        )
    }

    // ── 申請者連絡先 ────────────────────────────────────────────────────
    reviewResult.detectedEntities
        .filter { it.contextLabel == "applicant_address" && it.rawText.isNotBlank() }
        .forEach { entity ->
            ReviewBadgeItem(
                icon = "📍",
                label = stringResource(R.string.doc_review_label_applicant_address),
                badgeColor = ReviewBadgeColor.GRAY,
                content = entity.rawText,
                trailingButton = DocumentIntentBuilder.mapsIntent(entity.rawText, null)?.let { intent ->
                    { IntentIconButton("🗺", intent) }
                },
            )
        }
    reviewResult.detectedEntities
        .filter { it.contextLabel == "applicant_phone" && it.rawText.isNotBlank() }
        .forEach { entity ->
            ReviewBadgeItem(
                icon = "📞",
                label = stringResource(R.string.doc_review_label_applicant_phone),
                badgeColor = ReviewBadgeColor.GRAY,
                content = entity.rawText,
                trailingButton = DocumentIntentBuilder.phoneIntent(entity.rawText)?.let { intent ->
                    { IntentIconButton("📞", intent) }
                },
            )
        }
    reviewResult.detectedEntities
        .filter { it.contextLabel == "applicant_email" && it.rawText.isNotBlank() }
        .forEach { entity ->
            ReviewBadgeItem(
                icon = "✉️",
                label = stringResource(R.string.doc_review_label_applicant_email),
                badgeColor = ReviewBadgeColor.GRAY,
                content = entity.rawText,
                trailingButton = DocumentIntentBuilder.emailIntent(entity.rawText)?.let { intent ->
                    { IntentIconButton("✉️", intent) }
                },
            )
        }

    // ── 金融情報 ────────────────────────────────────────────────────────
    val moneyLabelMap = mapOf(
        "benefit_amount" to stringResource(R.string.doc_review_label_benefit),
        "fee"            to stringResource(R.string.doc_review_label_fee),
        "penalty"        to stringResource(R.string.doc_review_label_penalty),
        "other_amount"   to stringResource(R.string.doc_review_label_amount),
    )
    reviewResult.detectedEntities
        .filter { it.contextLabel in moneyLabelMap && it.rawText.isNotBlank() }
        .forEach { entity ->
            ReviewBadgeItem(
                icon = "💰",
                label = moneyLabelMap[entity.contextLabel] ?: stringResource(R.string.doc_review_label_amount),
                badgeColor = ReviewBadgeColor.GRAY,
                content = entity.rawText,
            )
        }
    reviewResult.detectedEntities
        .filter { it.contextLabel == "iban" && it.rawText.isNotBlank() }
        .forEach { entity ->
            ReviewBadgeItem(
                icon = "🏦",
                label = stringResource(R.string.doc_review_label_iban),
                badgeColor = ReviewBadgeColor.GRAY,
                content = entity.rawText,
            )
        }
    reviewResult.detectedEntities
        .filter { it.contextLabel == "payment_card" && it.rawText.isNotBlank() }
        .forEach { entity ->
            ReviewBadgeItem(
                icon = "💳",
                label = stringResource(R.string.doc_review_label_payment_card),
                badgeColor = ReviewBadgeColor.GRAY,
                content = entity.rawText,
            )
        }

    // ── その他の人物・場所 ──────────────────────────────────────────────
    reviewResult.otherName?.takeIf { it.isNotBlank() }?.let { name ->
        ReviewBadgeItem(
            icon = "👤",
            label = stringResource(R.string.doc_review_label_other_name),
            badgeColor = ReviewBadgeColor.GRAY,
            content = name,
        )
    }
    reviewResult.detectedEntities
        .filter { it.contextLabel == "other_address" && it.rawText.isNotBlank() }
        .forEach { entity ->
            ReviewBadgeItem(
                icon = "📍",
                label = stringResource(R.string.doc_review_label_other_address),
                badgeColor = ReviewBadgeColor.GRAY,
                content = entity.rawText,
                trailingButton = DocumentIntentBuilder.mapsIntent(entity.rawText, null)?.let { intent ->
                    { IntentIconButton("🗺", intent) }
                },
            )
        }
    reviewResult.detectedEntities
        .filter { it.contextLabel == "other_phone" && it.rawText.isNotBlank() }
        .forEach { entity ->
            ReviewBadgeItem(
                icon = "📞",
                label = stringResource(R.string.doc_review_label_other_phone),
                badgeColor = ReviewBadgeColor.GRAY,
                content = entity.rawText,
                trailingButton = DocumentIntentBuilder.phoneIntent(entity.rawText)?.let { intent ->
                    { IntentIconButton("📞", intent) }
                },
            )
        }
    reviewResult.detectedEntities
        .filter { it.contextLabel == "other_email" && it.rawText.isNotBlank() }
        .forEach { entity ->
            ReviewBadgeItem(
                icon = "✉️",
                label = stringResource(R.string.doc_review_label_other_email),
                badgeColor = ReviewBadgeColor.GRAY,
                content = entity.rawText,
                trailingButton = DocumentIntentBuilder.emailIntent(entity.rawText)?.let { intent ->
                    { IntentIconButton("✉️", intent) }
                },
            )
        }

    // ── デジタル・識別子 ─────────────────────────────────────────────────
    reviewResult.detectedEntities
        .filter { it.contextLabel == "url" && it.rawText.isNotBlank() }
        .forEach { entity ->
            ReviewBadgeItem(
                icon = "🔗",
                label = stringResource(R.string.doc_review_label_url),
                badgeColor = ReviewBadgeColor.GRAY,
                content = entity.rawText,
                trailingButton = DocumentIntentBuilder.urlIntent(entity.rawText)?.let { intent ->
                    { IntentIconButton("🔗", intent) }
                },
            )
        }
    reviewResult.detectedEntities
        .filter { it.contextLabel == "tracking_number" && it.rawText.isNotBlank() }
        .forEach { entity ->
            ReviewBadgeItem(
                icon = "📦",
                label = stringResource(R.string.doc_review_label_tracking),
                badgeColor = ReviewBadgeColor.GRAY,
                content = entity.rawText,
                trailingButton = DocumentIntentBuilder.trackingIntent(
                    entity.metadata?.carrier, entity.rawText
                )?.let { intent -> { IntentIconButton("📦", intent) } },
            )
        }
    reviewResult.detectedEntities
        .filter { it.contextLabel == "flight_number" && it.rawText.isNotBlank() }
        .forEach { entity ->
            ReviewBadgeItem(
                icon = "✈️",
                label = stringResource(R.string.doc_review_label_flight),
                badgeColor = ReviewBadgeColor.GRAY,
                content = entity.rawText,
                trailingButton = DocumentIntentBuilder.flightIntent(
                    entity.metadata?.airlineCode, entity.rawText
                )?.let { intent -> { IntentIconButton("✈️", intent) } },
            )
        }
    reviewResult.detectedEntities
        .filter { it.contextLabel == "isbn" && it.rawText.isNotBlank() }
        .forEach { entity ->
            ReviewBadgeItem(
                icon = "📚",
                label = stringResource(R.string.doc_review_label_isbn),
                badgeColor = ReviewBadgeColor.GRAY,
                content = entity.rawText,
            )
        }

    // ── フォールバック: contextLabel 未付与エンティティ ──────────────────────
    // EntityAnnotator が unknown を返した・タイムアウト・パース失敗した場合に
    // contextLabel = null のまま残ったエンティティを型ベースで表示する。
    // 既に contextLabel が付与されたエンティティは上のセクションで表示済みのためここには来ない。
    val unlabeled = reviewResult.detectedEntities
        .filter { it.contextLabel == null && it.rawText.isNotBlank() }
    unlabeled.filter { it.type == "ADDRESS" }.forEach { entity ->
        ReviewBadgeItem(
            icon = "📍",
            label = stringResource(R.string.doc_review_label_detected_address),
            badgeColor = ReviewBadgeColor.GRAY,
            content = entity.rawText,
            trailingButton = DocumentIntentBuilder.mapsIntent(entity.rawText, null)?.let { intent ->
                { IntentIconButton("🗺", intent) }
            },
        )
    }
    unlabeled.filter { it.type == "PHONE" }.forEach { entity ->
        ReviewBadgeItem(
            icon = "📞",
            label = stringResource(R.string.doc_review_label_detected_phone),
            badgeColor = ReviewBadgeColor.GRAY,
            content = entity.rawText,
            trailingButton = DocumentIntentBuilder.phoneIntent(entity.rawText)?.let { intent ->
                { IntentIconButton("📞", intent) }
            },
        )
    }
    unlabeled.filter { it.type == "EMAIL" }.forEach { entity ->
        ReviewBadgeItem(
            icon = "✉️",
            label = stringResource(R.string.doc_review_label_detected_email),
            badgeColor = ReviewBadgeColor.GRAY,
            content = entity.rawText,
            trailingButton = DocumentIntentBuilder.emailIntent(entity.rawText)?.let { intent ->
                { IntentIconButton("✉️", intent) }
            },
        )
    }
    unlabeled.filter { it.type == "MONEY" }.forEach { entity ->
        ReviewBadgeItem(
            icon = "💰",
            label = stringResource(R.string.doc_review_label_detected_amount),
            badgeColor = ReviewBadgeColor.GRAY,
            content = entity.rawText,
        )
    }
    unlabeled.filter { it.type == "DATE_TIME" }.forEach { entity ->
        ReviewBadgeItem(
            icon = "🗓",
            label = stringResource(R.string.doc_review_label_detected_date),
            badgeColor = ReviewBadgeColor.GRAY,
            content = entity.rawText,
        )
    }
}

// ─── S-02: Review Badge ───────────────────────────────────────────────────────

private enum class ReviewBadgeColor { RED, ORANGE, BLUE, GRAY }

@Composable
private fun ReviewBadgeItem(
    icon: String,
    label: String,
    badgeColor: ReviewBadgeColor,
    content: String,
    trailingButton: (@Composable () -> Unit)? = null,
) {
    val bgColor = when (badgeColor) {
        ReviewBadgeColor.RED -> MaterialTheme.colorScheme.errorContainer
        ReviewBadgeColor.ORANGE -> Color(0xFFFFE0B2)
        ReviewBadgeColor.BLUE -> MaterialTheme.colorScheme.secondaryContainer
        ReviewBadgeColor.GRAY -> MaterialTheme.colorScheme.surfaceVariant
    }
    val txtColor = when (badgeColor) {
        ReviewBadgeColor.RED -> MaterialTheme.colorScheme.onErrorContainer
        ReviewBadgeColor.ORANGE -> Color(0xFFBF360C)
        ReviewBadgeColor.BLUE -> MaterialTheme.colorScheme.onSecondaryContainer
        ReviewBadgeColor.GRAY -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = icon, style = MaterialTheme.typography.bodySmall)
            Surface(
                color = bgColor,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = txtColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (trailingButton != null) {
                Spacer(modifier = Modifier.weight(1f))
                trailingButton()
            }
        }
        if (content.isNotBlank()) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

// ─── S-02: Event Dates Section (shared between JaReviewContent / TranslatedReviewContent) ──

@Composable
private fun EventDatesSection(reviewResult: ReviewResult) {
    val label = stringResource(R.string.doc_review_label_event_date)
    reviewResult.eventDates.forEach { event ->
        val eventContent = buildString {
            append(event.descriptionJa)
            event.date?.let { append(" ($it)") }
        }
        val calIntent = event.date?.let {
            DocumentIntentBuilder.calendarIntentForEvent(
                title = "${reviewResult.docName} - ${event.descriptionJa}",
                date = it,
                descriptionJa = event.descriptionJa,
            )
        }
        ReviewBadgeItem(
            icon = "📅",
            label = label,
            badgeColor = ReviewBadgeColor.GRAY,
            content = eventContent,
            trailingButton = calIntent?.let { intent -> { IntentIconButton("📅", intent) } },
        )
    }
}

// ─── S-02: Intent Icon Button ─────────────────────────────────────────────────

@Composable
private fun IntentIconButton(emoji: String, intent: Intent) {
    val context = LocalContext.current
    val noAppMsg = stringResource(R.string.doc_review_quick_action_no_app)
    OutlinedButton(
        onClick = {
            try {
                context.startActivity(intent)
            } catch (_: android.content.ActivityNotFoundException) {
                android.widget.Toast.makeText(context, noAppMsg, android.widget.Toast.LENGTH_SHORT).show()
            }
        },
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        modifier = Modifier
            .widthIn(min = 32.dp)
            .heightIn(min = 48.dp),
    ) {
        Text(text = emoji, style = MaterialTheme.typography.labelSmall)
    }
}

// ─── S-02: PII Section ────────────────────────────────────────────────────────

@Composable
private fun PiiSection(
    maskResult: MaskResult,
    lang: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleMask: (spanId: String, userOverride: Boolean?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.doc_review_pii_section_title),
            style = MaterialTheme.typography.titleSmall,
        )

        // Unmatched spans warning
        if (maskResult.unmatchedSpans.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.shapes.small,
                    )
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.doc_review_pii_unmatched_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        // PII summary chips
        val maskedSpans = maskResult.appliedSpans
        if (maskedSpans.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.small,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                maskedSpans.take(4).forEach { span ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "[■■■]",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = span.categoryLabel(lang),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Toggle button for expand/collapse
        OutlinedButton(
            onClick = onToggleExpand,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (expanded) stringResource(R.string.doc_review_pii_collapse) else stringResource(R.string.doc_review_pii_expand))
        }

        // Expandable edit panel
        if (expanded) {
            PiiEditPanel(maskResult = maskResult, lang = lang, onToggleMask = onToggleMask)
        }
    }
}

@Composable
private fun PiiEditPanel(
    maskResult: MaskResult,
    lang: String,
    onToggleMask: (spanId: String, userOverride: Boolean?) -> Unit,
) {
    val allSpans = (maskResult.appliedSpans + maskResult.skippedSpans + maskResult.unmatchedSpans)
        .distinctBy { it.id }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        allSpans.forEach { span ->
            PiiEditRow(
                span = span,
                lang = lang,
                isMasked = maskResult.appliedSpans.any { it.id == span.id },
                isUnmatched = maskResult.unmatchedSpans.any { it.id == span.id },
                onToggleMask = onToggleMask,
            )
        }
    }
}

@Composable
private fun PiiEditRow(
    span: PiiSpan,
    lang: String,
    isMasked: Boolean,
    isUnmatched: Boolean,
    onToggleMask: (spanId: String, userOverride: Boolean?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(
            checked = isMasked,
            onCheckedChange = { checked ->
                if (!isUnmatched) {
                    onToggleMask(span.id, checked)
                }
            },
            enabled = !isUnmatched,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isMasked) {
                    Text(
                        text = "[■■■]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = span.spanText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = span.categoryLabel(lang),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                isUnmatched -> Text(
                    text = stringResource(R.string.doc_review_pii_not_masked),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                isMasked -> Text(
                    text = stringResource(R.string.doc_review_pii_masked),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ─── S-02: Chat Section ───────────────────────────────────────────────────────

@Composable
private fun ChatSection(
    messages: List<ChatMessage>,
    partialResponse: String?,
    chatErrorRes: Int?,
    chatIsGenerating: Boolean,
    inputEnabled: Boolean,
    chatLimitReached: Boolean,
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.doc_review_chat_title),
            style = MaterialTheme.typography.titleSmall,
        )

        // Chat messages
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            messages.forEach { message ->
                key(message.id) {
                    ChatBubble(
                        isUser = message.role == ChatRole.USER,
                        content = message.content,
                    )
                }
            }

            // Streaming partial response or generating indicator
            if (chatIsGenerating) {
                if (!partialResponse.isNullOrEmpty()) {
                    ChatBubble(
                        isUser = false,
                        content = partialResponse,
                        isStreaming = true,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = stringResource(R.string.doc_review_chat_generating),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Chat error message
        chatErrorRes?.let { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Chat limit reached message
        if (chatLimitReached) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(R.string.doc_review_chat_limit_reached),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        // Input area (hidden when limit reached)
        if (!chatLimitReached) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.doc_review_chat_placeholder)) },
                    minLines = 1,
                    maxLines = 4,
                    enabled = inputEnabled,
                )
                Button(
                    onClick = onSend,
                    enabled = inputText.isNotBlank() && inputEnabled,
                ) {
                    Text("▶")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(isUser: Boolean, content: String, isStreaming: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isUser) {
                    Text(
                        text = "🤖",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                Text(
                    text = if (isStreaming) "$content…" else content,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

// ─── S-02: Inquiry Wizard Entry Section ──────────────────────────────────────

@Composable
private fun InquiryWizardEntrySection(
    isDisabled: Boolean,
    onStartWizard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
    ) {
        Button(
            onClick = onStartWizard,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isDisabled,
        ) {
            Text(stringResource(R.string.doc_review_create_inquiry))
        }
    }
}

// ─── S-03: Inquiry Preview ────────────────────────────────────────────────────

@Composable
private fun S03InquiryPreviewContent(contextText: String) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Masked PII banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.doc_review_pii_masked_header),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        // Scrollable context text
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = contextText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { copyToClipboard(context, contextText, "inquiry_context") },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.copy))
            }
            Button(
                onClick = { shareText(context, contextText) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.share))
            }
        }
    }
}

// ─── S-04: Inquiry Wizard ─────────────────────────────────────────────────────

@Composable
private fun S04InquiryWizardContent(
    state: DocumentReviewUiState.InquiryWizard,
    onUpdatePurpose: (String) -> Unit,
    onUpdateRecipient: (InquiryRecipient) -> Unit,
    onTogglePiiSpan: (String) -> Unit,
    onBuildContext: () -> Unit,
) {
    S04Step1Content(
        state = state,
        onUpdatePurpose = onUpdatePurpose,
        onUpdateRecipient = onUpdateRecipient,
        onTogglePiiSpan = onTogglePiiSpan,
        onConfirm = onBuildContext,
    )
}

@Composable
private fun S04Step1Content(
    state: DocumentReviewUiState.InquiryWizard,
    onUpdatePurpose: (String) -> Unit,
    onUpdateRecipient: (InquiryRecipient) -> Unit,
    onTogglePiiSpan: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Purpose section
        Text(
            text = stringResource(R.string.doc_review_inquiry_purpose_label),
            style = MaterialTheme.typography.titleSmall,
        )

        // Purpose suggestions
        if (state.purposeSuggestionsLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.doc_review_inquiry_generating_purposes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (state.purposeSuggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.purposeSuggestions.forEach { suggestion ->
                    OutlinedButton(
                        onClick = { onUpdatePurpose(suggestion) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                        )
                    }
                }
            }
        }

        // Purpose free text input
        OutlinedTextField(
            value = state.userPurpose,
            onValueChange = onUpdatePurpose,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.doc_review_inquiry_purpose_placeholder)) },
            minLines = 2,
            maxLines = 4,
        )

        HorizontalDivider()

        // Recipient section
        Text(
            text = stringResource(R.string.doc_review_inquiry_recipient_label),
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
            value = state.recipient.organizationName,
            onValueChange = { onUpdateRecipient(state.recipient.copy(organizationName = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.doc_review_inquiry_recipient_org)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.recipient.email ?: "",
            onValueChange = { onUpdateRecipient(state.recipient.copy(email = it.ifBlank { null })) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.doc_review_inquiry_recipient_email)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.recipient.phone ?: "",
            onValueChange = { onUpdateRecipient(state.recipient.copy(phone = it.ifBlank { null })) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.doc_review_inquiry_recipient_phone)) },
            singleLine = true,
        )

        HorizontalDivider()

        // Items to mask
        Text(
            text = stringResource(R.string.doc_review_inquiry_pii_label),
            style = MaterialTheme.typography.titleSmall,
        )
        val sourceFieldLabelMap = mapOf(
            "issuer_name"       to stringResource(R.string.doc_review_label_issuer),
            "applicant_name"    to stringResource(R.string.doc_review_label_target_person),
            "other_name"        to stringResource(R.string.doc_review_label_other_name),
            "issuer_address"    to stringResource(R.string.doc_review_label_office),
            "applicant_address" to stringResource(R.string.doc_review_label_applicant_address),
            "other_address"     to stringResource(R.string.doc_review_label_other_address),
            "issuer_phone"      to stringResource(R.string.doc_review_label_contact_phone),
            "applicant_phone"   to stringResource(R.string.doc_review_label_applicant_phone),
            "other_phone"       to stringResource(R.string.doc_review_label_other_phone),
            "issuer_email"      to stringResource(R.string.doc_review_label_contact_email),
            "applicant_email"   to stringResource(R.string.doc_review_label_applicant_email),
            "other_email"       to stringResource(R.string.doc_review_label_other_email),
            "date_of_birth"     to stringResource(R.string.doc_review_label_dob),
            "benefit_amount"    to stringResource(R.string.doc_review_label_benefit),
            "fee"               to stringResource(R.string.doc_review_label_fee),
            "penalty"           to stringResource(R.string.doc_review_label_penalty),
            "other_amount"      to stringResource(R.string.doc_review_label_amount),
            "iban"              to stringResource(R.string.doc_review_label_iban),
            "payment_card"      to stringResource(R.string.doc_review_label_payment_card),
            "tracking_number"   to stringResource(R.string.doc_review_label_tracking),
        )
        val allPiiSpans = state.piiSpans
        if (allPiiSpans.isEmpty()) {
            Text(
                text = stringResource(R.string.doc_review_inquiry_pii_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            allPiiSpans.forEach { span ->
                val isMasked = state.maskedPiiSpans.any { it.id == span.id }
                val spanLabel = sourceFieldLabelMap[span.sourceField]
                    ?: span.categoryLabel(state.targetLanguage)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Checkbox(
                        checked = isMasked,
                        onCheckedChange = { onTogglePiiSpan(span.id) },
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = spanLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isMasked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (isMasked) "[$spanLabel]" else "[${span.spanText}]",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Confirm button
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.userPurpose.isNotBlank(),
        ) {
            Text(stringResource(R.string.doc_review_inquiry_confirm))
        }
    }
}

