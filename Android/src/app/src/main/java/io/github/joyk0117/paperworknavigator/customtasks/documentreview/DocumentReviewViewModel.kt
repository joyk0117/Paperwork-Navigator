package io.github.joyk0117.paperworknavigator.customtasks.documentreview

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.joyk0117.paperworknavigator.R
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ChatMessage
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ChatRole
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.DocumentInput
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.DocumentReviewUiState
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ExtractionError
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.FieldExtractionError
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.InquiryRecipient
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.issuerEmail
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.issuerPhone
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.PiiSpan
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ProcessingStep
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.ChatInferenceError
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.ChatLimitReachedException
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.EntityAnnotator
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.EntityExtractor
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.DocumentChatSession
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.EscalationPackageGenerator
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.FieldExtractor
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.InquiryContextBuilder
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.LanguageIdentifier
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.OcrCorrector
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.PiiMasker
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.TextExtractor
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.processing.Translator
import io.github.joyk0117.paperworknavigator.data.Model
import io.github.joyk0117.paperworknavigator.runtime.LlmModelHelper
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Keys used by MF-02 line format (9 fields)
private val ALL_MF02_KEYS = setOf(
    "DOC_NAME", "ISSUER_NAME", "APPLICANT_NAME", "OTHER_NAME",
    "IMPORTANCE", "SUMMARY", "ACTION_ITEMS", "REQUIRED_ITEMS", "WARNING",
)
// Keys shown in the S-01 partial-field preview (high information value, appear early).
private val DISPLAY_MF02_KEYS = setOf(
    "DOC_NAME", "IMPORTANCE", "SUMMARY", "ACTION_ITEMS", "WARNING",
)

private const val TAG = "DocumentReviewViewModel"
private val DOC_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class DocumentReviewViewModel @Inject constructor(
    private val llmModelHelper: LlmModelHelper,
    private val repository: DocumentRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DocumentReviewUiState>(DocumentReviewUiState.Idle)
    val uiState = _uiState.asStateFlow()

    // One-shot event: navigate the caller to ModelManager
    private val _navigateToModelManager = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToModelManager = _navigateToModelManager.asSharedFlow()

    // One-shot event: translation completed → show snackbar with language code
    private val _translationCompleted = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val translationCompleted = _translationCompleted.asSharedFlow()

    // True only when the user has sent a message — triggers bottom scroll in S-02
    private val _shouldScrollChatToBottom = MutableStateFlow(false)
    val shouldScrollChatToBottom = _shouldScrollChatToBottom.asStateFlow()

    // MF-01c: OCR correction (user-triggered)
    private val _isCorrectingOcr = MutableStateFlow(false)
    val isCorrectingOcr = _isCorrectingOcr.asStateFlow()

    // One-shot event: OCR correction completed → update text in S-01
    private val _correctedOcrText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val correctedOcrText = _correctedOcrText.asSharedFlow()

    fun resetChatScrollFlag() {
        _shouldScrollChatToBottom.value = false
    }

    // LLM exclusive-execution guard (MF-02 / MF-03 / MF-06 / MF-07)
    private val mutex = Mutex()

    private val ocrCorrector = OcrCorrector(llmModelHelper)
    private val fieldExtractor = FieldExtractor(llmModelHelper)
    private val entityAnnotator = EntityAnnotator(llmModelHelper)
    private val translator = Translator(llmModelHelper)
    private val escalationGenerator = EscalationPackageGenerator(llmModelHelper)
    private val inquiryContextBuilder = InquiryContextBuilder(llmModelHelper)
    private val chatSession = DocumentChatSession(llmModelHelper)

    // Per-analysis state kept in the ViewModel between state transitions
    private var sourceText: String = ""          // original extracted text (needed for remask)
    private var currentDocId: String? = null
    private var currentTargetLanguage: String = "en"
    private var activeModel: Model? = null
    private var savedReviewState: DocumentReviewUiState.Review? = null   // for S-03 → S-02 back
    private var savedWizardState: DocumentReviewUiState.InquiryWizard? = null  // for S-03 → S-04 back

    // ─── analyzeDocument (MF-01 → MF-02 → MF-05 → MF-03 → save → MF-07) ────

    fun analyzeDocument(input: DocumentInput, model: Model) {
        if (uiState.value is DocumentReviewUiState.Processing) return

        if (model.instance == null) {
            _navigateToModelManager.tryEmit(Unit)
            return
        }

        activeModel = model
        currentTargetLanguage = "en"

        viewModelScope.launch {
            try {
                // MF-01: text extraction
                _uiState.value = DocumentReviewUiState.Processing(
                    step = ProcessingStep.EXTRACTING_TEXT,
                    progress = 0.1f,
                )
                val text = extractText(input) ?: return@launch
                sourceText = text

                // MF-01 後: ML Kit Language Identification で書類言語を判定（< 1 秒）
                val detectedLanguage = LanguageIdentifier.identify(text)
                Log.d(TAG, "Detected source language: $detectedLanguage")

                // MF-02: field extraction (Mutex)
                _uiState.value = DocumentReviewUiState.Processing(
                    step = ProcessingStep.EXTRACTING_FIELDS,
                    progress = 0.1f,
                )

                // EntityExtractor: ML Kit でパターンベースのエンティティを高速に先行取得 (<1秒)
                val mlKitEntities = EntityExtractor.extract(text, detectedLanguage)

                // ML Kit ネイティブメモリを解放してから Gemma 4 を起動する（#95, #102 参照）
                System.gc()

                // Accumulates raw LLM output across onProgress calls (inference thread only)
                val partialBuffer = StringBuilder()
                val rawReviewResult = mutex.withLock {
                    try {
                        fieldExtractor.extract(model = model, text = text, sourceLanguage = detectedLanguage, onProgress = { token ->
                            partialBuffer.append(token)
                            if ('\n' in token) {
                                val raw = partialBuffer.toString()
                                val fields = fieldExtractor.parsePartialLines(raw)
                                val seenCount = fields.count { (k, _) -> k in ALL_MF02_KEYS }
                                val newProgress = 0.10f + (seenCount / 9f) * 0.60f
                                val displayFields = fields.filter { (k, _) -> k in DISPLAY_MF02_KEYS }
                                viewModelScope.launch(Dispatchers.Main.immediate) {
                                    _uiState.update { current ->
                                        (current as? DocumentReviewUiState.Processing)?.copy(
                                            progress = newProgress,
                                            partialFields = displayFields,
                                        ) ?: current
                                    }
                                }
                            }
                        })
                    } catch (e: FieldExtractionError.ModelNotInitialized) {
                        _navigateToModelManager.tryEmit(Unit)
                        _uiState.value = DocumentReviewUiState.Idle
                        return@withLock null
                    } catch (e: FieldExtractionError) {
                        _uiState.value = DocumentReviewUiState.Error(
                            R.string.doc_review_error_analysis_failed
                        )
                        return@withLock null
                    }
                } ?: return@launch

                // EntityAnnotator: ML Kit エンティティに context_label を付与（Gemma 4 呼び出し #2）
                val annotatedEntities = mutex.withLock {
                    entityAnnotator.annotate(
                        model = model,
                        entities = mlKitEntities,
                        issuerName = rawReviewResult.issuerName,
                        applicantName = rawReviewResult.applicantName,
                        otherName = rawReviewResult.otherName,
                    )
                }

                // mergeEntities: アノテーション済みエンティティを ReviewResult にマージし PiiSpan を構築
                val (reviewResult, piiSpans) = EntityExtractor.mergeEntities(rawReviewResult, annotatedEntities)

                // Save to repository (Tier-1 data stays on device)
                val docId = generateDocId()
                currentDocId = docId
                repository.save(docId, reviewResult, text)

                // MF-07: initialize chat session in the document's source language
                val sourceLang = reviewResult.sourceLanguage
                val chatAvailable = mutex.withLock {
                    try {
                        chatSession.initialize(
                            model = model,
                            reviewResult = reviewResult,
                            targetLanguage = sourceLang,
                            sourceText = sourceText,
                        )
                        true
                    } catch (e: Exception) {
                        Log.w(TAG, "Chat session init failed: ${e.message}")
                        false
                    }
                }

                val initialMessages = if (chatAvailable) chatSession.getChatHistory() else emptyList()

                // Default translation target: pick "en" unless source is "en", then "ja"
                val defaultTargetLanguage = if (sourceLang == "en") "ja" else "en"
                currentTargetLanguage = defaultTargetLanguage

                val reviewState = DocumentReviewUiState.Review(
                    reviewResult = reviewResult,
                    piiSpans = piiSpans,
                    sourceText = text,
                    selectedLanguage = defaultTargetLanguage,
                    chatMessages = initialMessages,
                    chatAvailable = chatAvailable,
                )
                savedReviewState = reviewState
                _uiState.value = reviewState

            } catch (e: Exception) {
                Log.e(TAG, "analyzeDocument failed unexpectedly", e)
                _uiState.value = DocumentReviewUiState.Error(R.string.doc_review_error_analysis_failed)
            }
        }
    }

    // ─── translate (MF-03) ───────────────────────────────────────────────────

    fun translate(language: String) {
        val state = uiState.value as? DocumentReviewUiState.Review ?: return
        if (state.isTranslating) return
        val model = activeModel ?: return

        currentTargetLanguage = language

        _uiState.update { current ->
            (current as? DocumentReviewUiState.Review)?.copy(
                selectedLanguage = language,
                isTranslating = true,
                translationError = false,
            ) ?: current
        }

        viewModelScope.launch {
            val translatedResult = mutex.withLock {
                try {
                    translator.translate(
                        model = model,
                        reviewResult = state.reviewResult,
                        targetLanguage = language,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Translation failed: ${e.message}")
                    null
                }
            }

            if (translatedResult == null) {
                _uiState.update { current ->
                    (current as? DocumentReviewUiState.Review)?.copy(
                        isTranslating = false,
                        translationError = true,
                    ) ?: current
                }
                return@launch
            }

            // Reinitialize chat in translation language (spec §3.3)
            val chatAvailable = mutex.withLock {
                try {
                    chatSession.initialize(
                        model = model,
                        reviewResult = translatedResult,
                        targetLanguage = language,
                        sourceText = sourceText,
                    )
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "Chat reinit failed: ${e.message}")
                    false
                }
            }

            val initialMessages = if (chatAvailable) chatSession.getChatHistory() else emptyList()

            val newState = (uiState.value as? DocumentReviewUiState.Review)?.copy(
                reviewResult = translatedResult,
                isTranslating = false,
                translationError = false,
                chatMessages = initialMessages,
                chatAvailable = chatAvailable,
            )
            if (newState != null) {
                savedReviewState = newState
                _uiState.value = newState
                _translationCompleted.tryEmit(language)
            }
        }
    }

    private suspend fun extractText(input: DocumentInput): String? = when (input) {
        is DocumentInput.PdfUri -> {
            try {
                TextExtractor.extract(appContext, input.uri)
            } catch (e: ExtractionError.NoPdfTextLayer) {
                _uiState.value = DocumentReviewUiState.Error(R.string.doc_review_error_no_text_layer)
                null
            } catch (e: ExtractionError) {
                _uiState.value = DocumentReviewUiState.Error(R.string.doc_review_error_file_read)
                null
            }
        }
        is DocumentInput.RawText -> {
            if (input.text.length > TextExtractor.MAX_CHARS) {
                _uiState.value = DocumentReviewUiState.Error(R.string.doc_review_error_too_long)
                null
            } else {
                input.text
            }
        }
    }

    // ─── correctOcr (MF-01c, user-triggered) ─────────────────────────────────

    private var correctionJob: Job? = null

    fun correctOcr(model: Model, ocrText: String, images: List<android.graphics.Bitmap>) {
        if (_isCorrectingOcr.value) return
        correctionJob = viewModelScope.launch {
            _isCorrectingOcr.value = true
            try {
                val corrected = mutex.withLock {
                    ocrCorrector.correct(model = model, ocrText = ocrText, images = images)
                }
                _correctedOcrText.emit(corrected)
            } finally {
                _isCorrectingOcr.value = false
            }
        }
    }

    fun cancelCorrection() {
        correctionJob?.cancel()
        correctionJob = null
        _isCorrectingOcr.value = false
    }

    // ─── generateEscalation (MF-06) ──────────────────────────────────────────

    fun generateEscalation(userNotes: String) {
        val state = uiState.value as? DocumentReviewUiState.Review ?: return
        if (state.chatIsGenerating) return
        val model = activeModel ?: return

        val piiSpans = state.piiSpans
        val reviewResult = state.reviewResult
        val chatMessages = chatSession.getChatHistory()
        // Mask all piiSpans for the escalation package (no user customization in this flow)
        val maskResult = PiiMasker.mask(sourceText, piiSpans)

        _uiState.value = DocumentReviewUiState.GeneratingEscalation(
            piiSpans = piiSpans,
            reviewResult = reviewResult,
            sourceText = sourceText,
            userNotes = userNotes,
            chatMessages = chatMessages,
        )

        viewModelScope.launch {
            val pkg = mutex.withLock {
                try {
                    escalationGenerator.generate(
                        model = model,
                        maskResult = maskResult,
                        reviewResult = reviewResult,
                        userNotes = userNotes,
                        chatMessages = chatMessages,
                        targetLanguage = currentTargetLanguage,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "generateEscalation failed: ${e.message}")
                    // Restore from savedReviewState to preserve chatAvailable and chat history
                    _uiState.value = savedReviewState ?: DocumentReviewUiState.Review(
                        reviewResult = reviewResult,
                        piiSpans = piiSpans,
                        chatMessages = chatMessages,
                    )
                    return@launch
                }
            }

            currentDocId?.let { repository.saveEscalation(it, pkg) }
            _uiState.value = DocumentReviewUiState.OutputPreview(pkg)
        }
    }

    // ─── sendChatMessage (MF-07) ──────────────────────────────────────────────

    fun sendChatMessage(userMessage: String) {
        val state = uiState.value as? DocumentReviewUiState.Review ?: return
        if (state.chatIsGenerating || !state.chatAvailable || state.chatLimitReached) return
        val model = activeModel ?: return

        _shouldScrollChatToBottom.value = true

        // Optimistic UI: show user message immediately, then stream assistant response
        val userChatMessage = ChatMessage(role = ChatRole.USER, content = userMessage)
        _uiState.update { current ->
            (current as? DocumentReviewUiState.Review)?.copy(
                chatMessages = current.chatMessages + userChatMessage,
                chatIsGenerating = true,
                partialChatResponse = "",
                chatErrorRes = null,
            ) ?: current
        }

        viewModelScope.launch {
            mutex.withLock {
                try {
                    chatSession.sendMessage(
                        model = model,
                        userMessage = userMessage,
                        onToken = { token ->
                            _uiState.update { current ->
                                (current as? DocumentReviewUiState.Review)?.copy(
                                    partialChatResponse = (current.partialChatResponse ?: "") + token,
                                ) ?: current
                            }
                        },
                    )
                } catch (e: ChatLimitReachedException) {
                    // Remove the optimistically-added user message and mark limit reached
                    _uiState.update { current ->
                        (current as? DocumentReviewUiState.Review)?.copy(
                            chatMessages = current.chatMessages.dropLast(1),
                            chatIsGenerating = false,
                            partialChatResponse = null,
                            chatLimitReached = true,
                        ) ?: current
                    }
                    return@withLock
                } catch (e: ChatInferenceError) {
                    Log.w(TAG, "Chat inference failed: ${e.message}")
                    // Spec §4.3: failed assistant message must not persist in history
                    _uiState.update { current ->
                        (current as? DocumentReviewUiState.Review)?.copy(
                            chatMessages = current.chatMessages.dropLast(1),
                            chatIsGenerating = false,
                            partialChatResponse = null,
                            chatErrorRes = R.string.doc_review_error_chat_failed,
                        ) ?: current
                    }
                    return@withLock
                } catch (e: Exception) {
                    Log.e(TAG, "sendChatMessage unexpected error", e)
                    _uiState.update { current ->
                        (current as? DocumentReviewUiState.Review)?.copy(
                            chatMessages = current.chatMessages.dropLast(1),
                            chatIsGenerating = false,
                            partialChatResponse = null,
                            chatErrorRes = R.string.doc_review_error_chat_failed,
                        ) ?: current
                    }
                    return@withLock
                }

                // Success: sync UI with the canonical chat history from the session
                val updatedMessages = chatSession.getChatHistory()
                val newState = (uiState.value as? DocumentReviewUiState.Review)?.copy(
                    chatMessages = updatedMessages,
                    chatIsGenerating = false,
                    partialChatResponse = null,
                    chatErrorRes = null,
                )
                if (newState != null) {
                    savedReviewState = newState
                    _uiState.value = newState
                }
            }
        }
    }

    // ─── startInquiryWizard (S-02 → S-04) ────────────────────────────────────

    fun startInquiryWizard(targetLanguage: String) {
        val state = uiState.value as? DocumentReviewUiState.Review ?: return
        if (state.chatIsGenerating) return
        val model = activeModel ?: return

        val reviewResult = state.reviewResult
        val targetLang = targetLanguage

        val recipient = InquiryRecipient(
            organizationName = reviewResult.issuerName ?: reviewResult.locations.firstOrNull()?.nameJa ?: "",
            contactName = null,
            email = reviewResult.issuerEmail(),
            phone = reviewResult.issuerPhone(),
        )

        val allPiiSpans = state.piiSpans

        val wizardState = DocumentReviewUiState.InquiryWizard(
            reviewResult = reviewResult,
            piiSpans = allPiiSpans,
            recipient = recipient,
            targetLanguage = targetLang,
            maskedPiiSpans = allPiiSpans.filter { it.maskRecommended },  // Tier 1 only by default
            purposeSuggestionsLoading = true,
        )
        _uiState.value = wizardState

        // Launch purpose suggestions in background (MF-06a)
        val chatHistorySnapshot = chatSession.getChatHistory()
        viewModelScope.launch {
            val suggestions = mutex.withLock {
                inquiryContextBuilder.suggestPurposes(
                    model = model,
                    reviewResult = reviewResult,
                    targetLanguage = targetLang,
                    sourceText = sourceText,
                    chatHistory = chatHistorySnapshot,
                )
            }
            _uiState.update { current ->
                (current as? DocumentReviewUiState.InquiryWizard)?.copy(
                    purposeSuggestions = suggestions,
                    purposeSuggestionsLoading = false,
                ) ?: current
            }
        }
    }

    fun updateWizardPurpose(purpose: String) {
        _uiState.update { current ->
            (current as? DocumentReviewUiState.InquiryWizard)?.copy(userPurpose = purpose) ?: current
        }
    }

    fun updateWizardRecipient(recipient: InquiryRecipient) {
        _uiState.update { current ->
            (current as? DocumentReviewUiState.InquiryWizard)?.copy(recipient = recipient) ?: current
        }
    }

    fun toggleWizardPiiSpan(spanId: String) {
        _uiState.update { current ->
            val wizard = current as? DocumentReviewUiState.InquiryWizard ?: return@update current
            val span = wizard.piiSpans.find { it.id == spanId } ?: return@update current
            val isMasked = wizard.maskedPiiSpans.any { it.id == spanId }
            if (isMasked) {
                // Uncheck = unmask this span
                wizard.copy(maskedPiiSpans = wizard.maskedPiiSpans.filter { it.id != spanId })
            } else {
                // Check = mask this span; set userOverride if toggling against default
                val updated = span.copy(
                    userOverride = when {
                        !span.maskRecommended -> true  // Tier 2: force-mask since default is false
                        else -> null                   // Tier 1: default already masks, no override needed
                    }
                )
                wizard.copy(maskedPiiSpans = (wizard.maskedPiiSpans + updated).distinctBy { it.id })
            }
        }
    }

    fun buildInquiryContext() {
        val wizard = uiState.value as? DocumentReviewUiState.InquiryWizard ?: return

        val context = inquiryContextBuilder.buildContext(
            reviewResult = wizard.reviewResult,
            purpose = wizard.userPurpose,
            recipient = wizard.recipient,
            maskedPiiSpans = wizard.maskedPiiSpans,
            allPiiSpans = wizard.piiSpans,
            targetLanguage = wizard.targetLanguage,
            sourceText = sourceText,
        )

        currentDocId?.let { docId ->
            viewModelScope.launch {
                repository.saveInquiry(docId, context)
            }
        }

        savedWizardState = wizard
        _uiState.value = DocumentReviewUiState.InquiryPreview(
            contextText = context.toContextText(),
        )
    }

    fun backFromInquiryPreview() {
        _uiState.value = savedWizardState ?: savedReviewState ?: DocumentReviewUiState.Idle
    }

    fun backFromInquiryWizard() {
        savedWizardState = null
        _uiState.value = savedReviewState ?: DocumentReviewUiState.Idle
    }

    // ─── backFromOutput (S-03 → S-02) ────────────────────────────────────────

    fun backFromOutput() {
        _uiState.value = savedReviewState ?: return
    }

    // ─── retry ───────────────────────────────────────────────────────────────

    // Returns from Error state to Idle so the user can try again
    fun retry() {
        _uiState.value = DocumentReviewUiState.Idle
    }

    // ─── reset (S-02 → S-01) ─────────────────────────────────────────────────

    // Clears all data and returns to Idle (called when navigating back from S-02)
    fun reset() {
        chatSession.clear()
        sourceText = ""
        currentDocId = null
        savedReviewState = null
        savedWizardState = null
        _shouldScrollChatToBottom.value = false
        _uiState.value = DocumentReviewUiState.Idle
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun generateDocId(): String {
        val timestamp = LocalDateTime.now().format(DOC_ID_FORMATTER)
        val shortUuid = UUID.randomUUID().toString().take(8)
        return "doc_${timestamp}_$shortUuid"
    }
}
