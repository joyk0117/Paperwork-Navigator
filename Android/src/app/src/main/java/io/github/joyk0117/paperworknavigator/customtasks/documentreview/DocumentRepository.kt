package io.github.joyk0117.paperworknavigator.customtasks.documentreview

import android.util.Log
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.DocumentBundle
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.DocumentMeta
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.EscalationPackage
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.InquiryContext
import io.github.joyk0117.paperworknavigator.customtasks.documentreview.model.ReviewResult
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val TAG = "DocumentRepository"
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

class DocumentRepository(private val filesDir: File) {

    private val documentsDir = File(filesDir, "documents")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(
        docId: String,
        reviewResult: ReviewResult,
        sourceText: String,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dir = docDir(docId)
            dir.mkdirs()
            File(dir, "meta.json").writeText(
                json.encodeToString(ReviewResult.serializer(), reviewResult)
            )
            File(dir, "source.txt").writeText(sourceText)
            true
        }.getOrElse { e ->
            Log.w(TAG, "save failed for $docId", e)
            false
        }
    }

    suspend fun saveInquiry(docId: String, context: InquiryContext): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = docDir(docId)
                if (!dir.exists()) {
                    Log.w(TAG, "saveInquiry: doc dir not found for $docId")
                    return@runCatching false
                }
                // Serialize as a simple JSON-like text since InquiryContext is not @Serializable
                val serializable = InquiryContextJson(
                    language = context.language,
                    purpose = context.purpose,
                    organizationName = context.recipient.organizationName,
                    contactEmail = context.recipient.email,
                    documentSummary = context.documentSummary,
                    contextText = context.toContextText(),
                )
                File(dir, "inquiry.json").writeText(
                    json.encodeToString(InquiryContextJson.serializer(), serializable)
                )
                true
            }.getOrElse { e ->
                Log.w(TAG, "saveInquiry failed for $docId", e)
                false
            }
        }

    suspend fun saveEscalation(docId: String, pkg: EscalationPackage): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = docDir(docId)
                // save() が先に呼ばれていない場合は孤立した escalation.json を作らない
                if (!dir.exists()) {
                    Log.w(TAG, "saveEscalation: doc dir not found for $docId")
                    return@runCatching false
                }
                File(dir, "escalation.json").writeText(
                    json.encodeToString(EscalationPackage.serializer(), pkg)
                )
                true
            }.getOrElse { e ->
                Log.w(TAG, "saveEscalation failed for $docId", e)
                false
            }
        }

    suspend fun list(): List<DocumentMeta> = withContext(Dispatchers.IO) {
        val dirs = documentsDir.listFiles { f -> f.isDirectory } ?: return@withContext emptyList()
        dirs.mapNotNull { dir ->
            runCatching {
                val metaFile = File(dir, "meta.json")
                if (!metaFile.exists()) return@runCatching null
                val reviewResult = json.decodeFromString<ReviewResult>(metaFile.readText())
                DocumentMeta(
                    docId = dir.name,
                    docName = reviewResult.docName,
                    importanceLevel = reviewResult.importance,
                    createdAt = parseCreatedAt(dir.name),
                    hasEscalation = File(dir, "escalation.json").exists(),
                    hasInquiry = File(dir, "inquiry.json").exists(),
                )
            }.getOrElse { e ->
                Log.w(TAG, "list: skipping corrupt entry ${dir.name}", e)
                null
            }
        }.sortedByDescending { it.createdAt }
    }

    suspend fun load(docId: String): DocumentBundle? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = docDir(docId)
            val metaFile = File(dir, "meta.json")
            val sourceFile = File(dir, "source.txt")
            if (!metaFile.exists() || !sourceFile.exists()) return@runCatching null
            val reviewResult = json.decodeFromString<ReviewResult>(metaFile.readText())
            val sourceText = sourceFile.readText()
            val escalationFile = File(dir, "escalation.json")
            val escalationPackage = if (escalationFile.exists()) {
                json.decodeFromString<EscalationPackage>(escalationFile.readText())
            } else null
            DocumentBundle(reviewResult, sourceText, escalationPackage)
        }.getOrElse { e ->
            Log.w(TAG, "load failed for $docId", e)
            null
        }
    }

    suspend fun delete(docId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { docDir(docId).deleteRecursively() }.getOrElse { e ->
            Log.w(TAG, "delete failed for $docId", e)
            false
        }
    }

    private fun docDir(docId: String) = File(documentsDir, docId)

    // Directory name format: doc_yyyyMMdd_HHmmss_shortUuid
    private fun parseCreatedAt(dirName: String): Long = runCatching {
        val parts = dirName.split("_")
        if (parts.size >= 3) {
            LocalDateTime.parse("${parts[1]}_${parts[2]}", DATE_FORMATTER)
                .toInstant(ZoneOffset.UTC).toEpochMilli()
        } else 0L
    }.getOrDefault(0L)
}

// ─── Serializable DTO for InquiryContext ─────────────────────────────────────

@kotlinx.serialization.Serializable
private data class InquiryContextJson(
    val language: String,
    val purpose: String,
    val organizationName: String,
    val contactEmail: String? = null,
    val documentSummary: String,
    val contextText: String,
)
