# Paperwork Navigator: Private On-Device AI for Official Paperwork

## Subtitle

Privacy-first on-device document navigator for migrants who need to understand official paperwork without exposing personal data

## Project Report

Every day, migrants, foreign residents, international students, refugees, and cross-border workers receive documents they must act on but cannot fully read. These papers often contain deadlines, required attachments, penalties, addresses, phone numbers, and identity information. In practice, many people paste them into cloud translation tools or AI assistants because they need help immediately. That creates a painful tradeoff: understand the document, or protect your privacy.

Paperwork Navigator is a privacy-first Android document navigator that reads, analyzes, translates, and explains official paperwork entirely on-device using Gemma 4 with LiteRT-LM. Instead of sending sensitive documents to a cloud service, the app processes them locally, highlights what matters, and generates a masked inquiry context that users can share with a trusted expert or external AI only after personal data has been removed.

Our current focus is Japanese administrative, medical, and everyday paperwork for foreigners living in Japan, such as child allowance forms, residency notices, and other official documents. But the broader product direction is simple: anyone who receives important paperwork in a non-native language should be able to understand it safely, even with limited connectivity and without relying on a remote server.

## Why this matters

The people who most need language support are often the same people who have the least margin for error. Missing a deadline on a benefits form, misunderstanding a required attachment, or failing to respond to an official notice can have real consequences. At the same time, these documents contain names, addresses, birth dates, account details, and other sensitive data that should not be casually uploaded to an online service.

This is exactly the kind of real-world constraint where local AI matters. Privacy is not a secondary feature here. It is the reason the product exists.

## What we built

Paperwork Navigator is a working Android proof of concept built on Google AI Edge Gallery, extended with a dedicated Document Review workflow. A user can open a PDF, scan a paper document with the camera, import an image from the gallery, or receive a document from another app through Android share intents. The app then performs a fully local processing pipeline:

1. Text extraction from PDF or OCR from camera and gallery images
2. Optional multimodal OCR correction with Gemma 4 for scanned or photographed documents
3. Local entity extraction for dates, addresses, phone numbers, emails, and monetary values
4. Gemma 4 field extraction for deadlines, actions, warnings, and document summary
5. Entity annotation to turn raw entities into useful meanings such as submission deadline or issuer address
6. Translation into one of 15 supported languages
7. On-device PII masking without using an LLM
8. A document-specific question-answering chat
9. Generation of a masked inquiry context that can be copied or shared safely

The output is not just a translation. It is a structured review experience. The app identifies the document title, summarizes the document, extracts deadlines, lists required actions, surfaces warnings, and turns useful fields into quick actions such as adding a deadline to the calendar or opening the issuing office in maps.

This matters because translation alone is often not enough. Many users do not just need to know what a document says. They need to know what to do next.

## Why Gemma 4

Gemma 4 is central to the solution for four reasons.

First, it enables high-quality on-device reasoning. We use it not only to translate text, but to turn raw document content into structured, actionable information. That includes summaries, deadlines, action items, warnings, OCR correction, and document-aware chat responses.

Second, Gemma 4 is well suited to multimodal and tool-supported workflows. In our architecture, Gemma 4 works alongside OCR and entity extraction rather than replacing them. We let deterministic tools handle extraction, then use Gemma 4 for context and meaning. This is more reliable and practical than a single-model approach.

Third, Gemma 4's stronger control features are important for product behavior. We need consistent, structured outputs and predictable handling of sensitive documents. That makes the model useful not only for a demo, but for a real mobile workflow.

Fourth, Gemma 4 supports the core story of this project: useful AI under privacy, edge, and low-connectivity constraints. Users should not have to choose between comprehension and confidentiality.

## Why LiteRT

Paperwork Navigator is also a strong fit for the LiteRT track because the entire user value depends on efficient local inference. We use LiteRT-LM on Android to keep the critical analysis path on-device. That means no server round trip, no cloud dependency during inference, and no need to transmit document contents off the phone after the initial model download.

This is important both technically and socially. Technically, it makes the app usable in unstable-network settings. Socially, it makes the product credible for sensitive workflows involving government documents, benefits, identity information, and family data.

Our system is not "mobile" as a presentation choice. It is mobile because that is where the privacy and accessibility benefits become real.

## How the system works

Our pipeline combines lightweight extraction tools with Gemma 4 reasoning.

For input, we support PDF text extraction, OCR from scanned or photographed documents, gallery import, and content shared from other apps. For deterministic signal extraction, we use local mobile tooling to detect entities such as dates, addresses, phone numbers, and amounts. For camera and image inputs, Gemma 4 can also perform an optional OCR correction pass by comparing the image with the OCR text. Then Gemma 4 interprets the document at a higher level: what the document is, what the user must do, what is urgent, and which extracted items deserve labels such as deadline, issuer contact, or warning.

We also implement tiered privacy handling. Highly sensitive information such as name, address, date of birth, My Number, and bank account details remains strictly on-device. Before anything is prepared for sharing, the app masks personally identifying spans and replaces them with readable labels so that the resulting context is still useful. For example, a user can ask an external assistant to help draft a message to a city office without exposing raw personal data.

This creates a practical bridge between private local understanding and optional external escalation.

## Impact

We believe this project fits Digital Equity & Inclusivity because it lowers a real barrier to civic participation. Bureaucratic language is already hard. Bureaucratic language in a foreign language, under deadline pressure, is much worse. Paperwork Navigator helps users understand essential documents with more confidence and less risk.

It also fits Safety & Trust because the design starts from data minimization. The sensitive original document stays local, the masking step is non-LLM and on-device, and only a masked representation is prepared for optional sharing.

For the user, the benefit is simple: understand the form, know the deadline, know the next action, and ask for help without leaking your identity.

## Current status and next steps

Our current prototype demonstrates the full product direction on Android and focuses on Japanese administrative documents as the first high-need use case. The app already supports the core flow described in our README: local document intake, structured review, multilingual translation, document chat, and masked inquiry generation. Next steps are broader multilingual evaluation, more document types, stronger benchmark coverage, and usability testing with real users who regularly handle official paperwork in a second language.

We are also interested in expanding the same approach to other settings where privacy, comprehension, and access intersect: healthcare forms, school paperwork, immigration documents, and social support applications.

Paperwork Navigator is our attempt to make AI genuinely useful in one of the most stressful everyday situations people face: receiving an important document they cannot easily understand, and needing help now without giving their private life away.

## Links

- Demo: [Add live demo URL]
- Video: [Add YouTube URL]
- Code: [Add GitHub repository URL]