package io.github.joyk0117.paperworknavigator.customtasks.documentreview

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import io.github.joyk0117.paperworknavigator.customtasks.common.CustomTask
import io.github.joyk0117.paperworknavigator.customtasks.common.CustomTaskData
import io.github.joyk0117.paperworknavigator.data.BuiltInTaskId
import io.github.joyk0117.paperworknavigator.data.Category
import io.github.joyk0117.paperworknavigator.data.ConfigKeys
import io.github.joyk0117.paperworknavigator.data.Model
import io.github.joyk0117.paperworknavigator.data.Task
import io.github.joyk0117.paperworknavigator.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Contents
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

// Gemma 4 E2B/E4B supports up to 32,000 tokens, but allocating the full KV cache at 32K
// exhausts device RAM for E4B on Pixel 9 (12 GB):
//   model weights ~2.3 GB + KV cache at 32K ≈ 3.7 GB → OOM.
// Actual usage per inference: system prompt (~850) + 8K chars (~5,333) + output (~2,000) ≈ 8,200 tokens.
// 12,288 provides sufficient headroom while keeping KV cache around 1.4 GB for E4B.
// The remote allowlist defaults to 4,000 which causes "Input token ids are too long" errors;
// this value overrides it to a safe upper bound.
private const val DOCUMENT_REVIEW_MAX_TOKENS = 12_288

class DocumentReviewTask @Inject constructor() : CustomTask {

    override val task: Task = Task(
        id = BuiltInTaskId.DOCUMENT_REVIEW,
        label = "書類を読む",
        category = Category.LLM,
        icon = Icons.Outlined.Description,
        description = "日本語の行政書類を端末上で解析・翻訳し、PII をマスクしたうえで専門家へのエスカレーション用パッケージを生成します。すべての処理はオンデバイスで完結し、個人情報は端末外に送信されません。",
        shortDescription = "書類を解析・翻訳する",
        models = mutableListOf(),
        // Gemma 4 E2B (primary) and E4B (fallback) are fetched from the model allowlist by name.
        modelNames = listOf("Gemma-4-E2B-it", "Gemma-4-E4B-it"),
    )

    override fun initializeModelFn(
        context: Context,
        coroutineScope: CoroutineScope,
        model: Model,
        systemInstruction: Contents?,
        onDone: (String) -> Unit,
    ) {
        // Override maxNumTokens to 32K: the remote allowlist defaults to 4000 which causes
        // "Input token ids are too long" errors when processing Japanese documents.
        model.configValues = model.configValues + mapOf(ConfigKeys.MAX_TOKENS.label to DOCUMENT_REVIEW_MAX_TOKENS.toFloat())

        // systemInstruction is intentionally null here: each inference step (MF-02/03/07)
        // injects its own system prompt at call time via LlmModelHelper.runInference().
        // supportImage = true: OcrCorrector (MF-01c) passes camera/gallery bitmaps to the engine.
        // Without a vision backend the engine crashes (SIGSEGV) when images are attached,
        // even though most inferences (MF-02/03/06/07) are text-only.
        LlmChatModelHelper.initialize(
            context = context,
            model = model,
            taskId = task.id,
            supportImage = true,
            supportAudio = false,
            onDone = onDone,
            systemInstruction = null,
        )
    }

    override fun cleanUpModelFn(
        context: Context,
        coroutineScope: CoroutineScope,
        model: Model,
        onDone: () -> Unit,
    ) {
        LlmChatModelHelper.cleanUp(model = model, onDone = onDone)
    }

    @Composable
    override fun MainScreen(data: Any) {
        val customTaskData = data as CustomTaskData
        DocumentReviewScreen(
            modelManagerViewModel = customTaskData.modelManagerViewModel,
            task = task,
            availableModels = task.models.toList(),
            bottomPadding = customTaskData.bottomPadding,
            setAppBarControlsDisabled = customTaskData.setAppBarControlsDisabled,
            setTopBarVisible = customTaskData.setTopBarVisible,
            setCustomNavigateUpCallback = customTaskData.setCustomNavigateUpCallback,
        )
    }
}
