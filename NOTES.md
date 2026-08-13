# Spring AI 1.1.8 vs LangChain4j 1.10.0 — implementation notes

Reimplementation of CorpusAI's RAG chat feature on Spring AI 1.1.8, holding Spring
Boot 3.5.14, Postgres/pgvector, and the OpenAI models constant. Every class/method
below was verified against the actual 1.1.8 jars (via `javap`, and in one case
decompiled bytecode) and/or the 1.1-tagged reference docs
(`https://docs.spring.io/spring-ai/reference/1.1/...`), not the default (2.0) docs
and not memory. One obligation's docs page pointed at a wrong/abbreviated package
for two classes - caught by checking the jar directly (see #7/#8). The full
pipeline was also run live end-to-end against a real Postgres/pgvector
container and real OpenAI calls (ingestion, English and Serbian sessions,
grounded answers, grounded refusal, and a 34-message conversation to exercise
the memory window) - this caught a real runtime bug in the streaming/usage
mapping that no amount of signature-checking would have (see #12).

Source files referenced below live under `src/main/java/com/corpusai/comparison_spring_ai/`.
Every file under the `springai/` subpackage imports `org.springframework.ai.*`;
nothing outside it does - that split is the countable line between
"library-touching" and boilerplate code for the LOC comparison.

---

## 1. Ingest PDF/TXT for one subject → parse, split, embed, write to pgvector

**Spring AI**: `TikaDocumentReader(Resource)` → `Document.mutate().metadata("subject_id", id)`
→ `TokenTextSplitter.builder().withChunkSize(300).build()` → `VectorStore.add(List<Document>)`.
File: `springai/ingestion/IngestionPipeline.java`.

- **Parsing — direct, arguably better.** One reader auto-detects PDF vs TXT via
  Tika, replacing CorpusAI's manual `parserFor()` switch between
  `ApachePdfBoxDocumentParser` and `TextDocumentParser`.
- **Splitting — partial.** `TokenTextSplitter` has **no chunk-overlap parameter**.
  CorpusAI's `DocumentSplitters.recursive(300, 30)` carries a 30-token overlap
  between chunks; there is no Spring AI equivalent, and it was simply dropped
  (`chunkSize=300` only). This is a real behavioral gap, not cosmetic - retrieval
  quality near chunk boundaries will differ.
- **Pipeline shape - partial.** LangChain4j's `EmbeddingStoreIngestor` is one
  object that owns split+embed+store. Spring AI has no equivalent single class;
  `IngestionPipeline` hand-composes reader → splitter → `vectorStore.add(...)`.
  Similar LOC either way, but Spring AI doesn't hand you the convenience object.

## 2. pgvector vector store, 1536 dimensions

**Spring AI**: `spring-ai-starter-vector-store-pgvector` autoconfigures `PgVectorStore`
as the `VectorStore` bean, entirely from `application.yml`
(`table-name`, `dimensions`, `index-type`, `distance-type`). Zero Java.

- **Direct, less code.** CorpusAI hand-builds the `PgVectorEmbeddingStore` bean in
  `VectorStoreConfig.java`. Here there is no equivalent file at all.

## 3. Embedding model: text-embedding-3-small, 1536 dims

**Spring AI**: `spring-ai-starter-model-openai` autoconfigures `OpenAiEmbeddingModel`
from `spring.ai.openai.embedding.options.model`. Zero Java.

- **Direct, less code.** CorpusAI builds this bean explicitly in `ModelFactory`.

## 4. Chat model: gpt-5.4-mini, temperature 0.7, streaming

**Spring AI**: autoconfigured `OpenAiChatModel` from
`spring.ai.openai.chat.options.{model,temperature}`. Streaming via
`ChatClient...stream().chatResponse()` → `Flux<ChatResponse>`.
`OpenAiChatOptions.builder().streamUsage(true)` is set **in code**
(`springai/chat/ChatAssistant.java`) rather than in `application.yml`, since the
yml file is frozen - runtime options merge over the yml defaults per the 1.1 docs,
so model/temperature are unaffected.

- **Direct, less code.** CorpusAI hand-builds and caches a provider-keyed
  `OpenAiStreamingChatModel` in `ModelFactory`; here there is no equivalent file.

## 5. Declare the assistant, bind the system prompt

**Spring AI**: the autoconfigured `ChatClient.Builder` bean is built into one
`ChatClient` in `ChatAssistant`'s constructor; the system prompt is bound
per-request via `.system(text)`.

- **Direct, but differently shaped.** LangChain4j requires declaring a
  `TutorAssistant` interface and rebuilding a whole new `AiServices`-backed
  assistant **on every chat call**, purely to bind that request's memory/system
  prompt/RAG augmentor. `ChatClient` is one long-lived instance; everything
  request-specific (system text, advisors, conversation id) is threaded through
  the fluent `.prompt()` call. No interface-declaration step exists in Spring AI
  at all — there's nothing analogous to `TutorAssistant.java`.

## 6. Retrieval filtered to subject, top 4 chunks

**Spring AI**: `VectorStoreDocumentRetriever.builder().vectorStore(vs).topK(4)
.filterExpression(new FilterExpressionBuilder().eq("subject_id", subjectId).build())`.
File: `springai/rag/RagPipelineFactory.java`.

- **Direct.** Equivalent of `EmbeddingStoreContentRetriever` + `IsEqualTo`.

## 7. Query compression over conversation history

**Spring AI**: `CompressionQueryTransformer.builder().chatClientBuilder(builder).build()`.

- **Direct.** Equivalent of `CompressingQueryTransformer`. History reaches it for
  free: `RetrievalAugmentationAdvisor.before()` builds
  `Query.builder().history(prompt.getInstructions())`, and
  `MessageChatMemoryAdvisor` (order = `HIGHEST_PRECEDENCE + 1000`, confirmed via
  the `Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER` constant) runs before the RAG
  advisor (default order `0`) - so restored conversation history is already present
  by the time compression runs, with no extra wiring.
- **Correction while researching this obligation:** the fetched 1.1 docs page gave
  an abbreviated package (`org.springframework.ai.rag.query.transformation`) for
  this class. The real package, confirmed via `javap` on the actual jar, is
  `org.springframework.ai.rag.preretrieval.query.transformation`. Caught before
  writing any code by checking the jar directly rather than trusting the doc
  fetch.
- **One asymmetry**: CorpusAI wraps its compression `ChatModel` in a hand-written
  `RecordingChatModel` decorator solely to meter that call's token usage. Spring
  AI needs no such wrapper - the actuator's automatic observations (#12) meter
  every model call, including this one, for free. One whole class CorpusAI has
  that this project doesn't need.

## 8. Assemble the RAG pipeline

**Spring AI**: `RetrievalAugmentationAdvisor.builder().documentRetriever(retriever)
.queryTransformers(compressionTransformer).build()`, cached per `subjectId` in a
`ConcurrentHashMap` - same caching shape as CorpusAI's factory.

- **Direct.** Equivalent of `DefaultRetrievalAugmentor`.
- **Behavioral overlap worth flagging**: the default `QueryAugmenter`
  (`ContextualQueryAugmenter`, `allowEmptyContext=false`) injects its **own**
  grounding/refuse-if-empty instructions into the prompt, on top of the retrieved
  documents. LangChain4j's default content injector does *not* do this - which is
  exactly why CorpusAI had to write its own grounding rule into the system
  prompt as a workaround (see #10). In this project the grounding rule now
  effectively exists twice: once from our own system-prompt text (kept for
  parity and explicit control) and once automatically from the advisor. Not a
  bug, but asymmetric - deleting our own grounding text would still leave Spring
  AI enforcing it, whereas deleting CorpusAI's would leave LangChain4j
  unguarded.

## 9. Memory: 20 turns + system prompt, persisted via JdbcTemplate

**Spring AI**: `PersistentChatMemoryRepository implements ChatMemoryRepository`
(`springai/chat/`), backed by the plain `ChatMessageRepository`/`ChatSessionRepository`
(`chat/`, pure JdbcTemplate). Wired as `MessageWindowChatMemory(maxMessages=20)` +
`MessageChatMemoryAdvisor` in `springai/chat/ChatMemoryConfig.java`.

- **Partial.** The persistence contract needs the *same* append-only workaround
  CorpusAI uses: `MessageWindowChatMemory.add()` always passes `saveAll()` the
  **full re-windowed history**, not a delta. Verified directly against the 1.1.8
  bytecode of `MessageWindowChatMemory.process()` - eviction only ever trims from
  the front - so the list's last element is always exactly the message just
  added. `saveAll()` persists only that last element, mirroring
  `ChatMemoryStoreImpl.updateMessages()`.
- **A workaround CorpusAI needed that this project does not.** CorpusAI's
  `ChatMemoryStoreImpl` carries an entire Caffeine-cached `systemPrompts` map
  purely to shuttle the `SystemMessage` back into LangChain4j's memory on every
  read, because `AiServices` rebuilds the whole outbound request from
  `chatMemory.messages()`. I disassembled `MessageChatMemoryAdvisor.before()`/
  `after()` and confirmed neither ever calls `chatMemory.add()` with a
  `SystemMessage` — only the last user message and the assistant's reply. The
  system prompt never round-trips through Spring AI's memory at all; `.system()`
  is fully independent of it. CorpusAI's workaround has no equivalent here
  because the problem it solves doesn't exist.
- **A whole class CorpusAI needs that this project does not.** LangChain4j's
  `MessageWindowChatMemory` is stateful/in-memory, which is why CorpusAI needs
  `ChatMemoryRegistry` — a Caffeine cache of per-session `ChatMemory` objects, so
  the window isn't rebuilt from the DB on every message. Spring AI's
  `MessageWindowChatMemory` is stateless - verified via bytecode: its only field
  besides `maxMessages` is the `chatMemoryRepository` reference, no internal
  deque - so it hits the repository on every `add()`/`get()` call regardless.
  One shared bean covers every session; no registry-equivalent exists or is
  needed.
- **A deliberate choice going the other way.** Because Spring AI hits the
  repository every turn (not just on cache miss, unlike CorpusAI's registry),
  `PersistentChatMemoryRepository.findByConversationId` bounds its query to the
  last 20 rows for efficiency - not correctness; `MessageWindowChatMemory`
  would re-trim any size input. CorpusAI's store returns the full transcript
  unbounded on every read.
- **Confirmed live, not just via bytecode**: pushed one session to 34 persisted
  messages. `comparison_chat_messages` kept the full transcript (row count
  verified via `psql`), while the model's `inputTokens` per call rose to a
  peak around the point the conversation first exceeded 20 messages, then
  *fell back down* and plateaued (~930 tokens, steady) as older turns aged out
  of the window on each subsequent call - the visible signature of a working
  sliding window, not just a static guarantee from the `LIMIT 20` in the SQL.
- **Not used, but exists**: Spring AI ships a batteries-included
  `JdbcChatMemoryRepository` (separate starter
  `spring-ai-starter-model-chat-memory-repository-jdbc`, its own
  `SPRING_AI_CHAT_MEMORY` table, replace-on-save semantics) — something
  LangChain4j has no equivalent of. It's incompatible with this project's fixed
  table names and full-transcript requirement, so it could not be used here even
  though it exists - worth noting since a project without the frozen-schema
  constraint could have used it and skipped writing `PersistentChatMemoryRepository`
  entirely.

## 10. System prompt = persona + grounding rule + language lock

**Spring AI**: no library API. `chat/SystemPromptBuilder.java` (plain Java, no
Spring AI import) reads `prompts/tutor-persona.txt` and `prompts/grounding-rule.txt`
- same wording as CorpusAI's `default-tutor-template.txt`/`invariant-rules.txt` -
and appends the identical language-lock instruction text CorpusAI uses. Bound via
`ChatClient`'s `.system(text)` in `ChatAssistant`.

- **Direct.** The assembly was never going to touch either library; the only
  library-touching step is the single `.system(text)` call.
- **The template text itself is byte-for-byte identical** to CorpusAI's two
  `.txt` files - confirmed with a binary diff, not just a visual comparison.
  The language-lock wording in `SystemPromptBuilder` is also copied verbatim
  from `ChatService.buildSystemPrompt()`, and the assembly order (persona →
  grounding rule → language lock) matches `SubjectService.systemPromptFor()` +
  `ChatService`'s append exactly.
- **Two real differences, found only by actually reading `SubjectService.java`**
  (I had initially asserted parity here without checking it - worth flagging
  since guessing here is exactly what I was told not to do):
  1. CorpusAI substitutes `{{subjectName}}` with `subject.getDisplayName()` - a
     curated, human-readable name (e.g. "Introduction to Machine Learning").
     This project substitutes the raw `subjectId` instead, since there is no
     `Subject` entity in a single-subject scope. If a caller passes a slug-like
     `subjectId` (e.g. `zylophant-101`) rather than a human-readable string, the
     rendered persona line reads noticeably worse than CorpusAI's
     equivalent - this is a real, visible difference in the rendered prompt,
     not just an implementation detail.
  2. CorpusAI's `persona()` method lets an admin fully override the persona
     template via `subject.getSystemPrompt()`, bypassing `{{subjectName}}`
     substitution entirely when set. That override path has no equivalent
     here at all, since admin-editable subjects are out of scope - this
     project always renders the default template.
- See #8 for the grounding-rule overlap with the RAG advisor's default augmenter.

## 11. Stream the answer token-by-token over SSE

**Spring AI**: `ChatClient...stream().chatResponse()` → `Flux<ChatResponse>`,
mapped inside `ChatAssistant` to a plain sealed `ChatStreamEvent` (`Token`/`Done`,
in `chat/ChatStreamEvent.java`, no Spring AI import), subscribed in
`ChatController` via `Flux.subscribe(onNext, onError, onComplete)`.

- **Direct.** LangChain4j's `TokenStream` callback registration
  (`onPartialResponse`/`onCompleteResponse`/`onError`/`.start()`) vs Reactor's
  `Flux.subscribe(...)` — same shape, same SSE wire contract (`token`/`done`
  named events), so the two controllers are close to line-for-line comparable.
- **Design note, not a library difference**: `ChatAssistant` fully maps
  `Flux<ChatResponse>` to the plain `Flux<ChatStreamEvent>` before it reaches
  `ChatController`, so the controller needs zero `org.springframework.ai`
  imports — the same boundary `IngestionController`/`IngestionPipeline` already
  draw. (CorpusAI's own `ChatController` also ends up import-free of
  langchain4j, but only incidentally, via `var`/lambda type inference on
  `TokenStream`'s callbacks — not a deliberate boundary there.)

## 12. Per-call token usage (input/output) and latency

**Spring AI, automatic (zero code)**: `spring-boot-starter-actuator` on the
classpath is sufficient for Spring AI's autoconfigured observations to record
`gen_ai.client.token.usage` (input/output/total) and `gen_ai.client.operation`
timings for **every** model call — including the compression call from #7.
Micrometer records these into the registry regardless of HTTP exposure. This
entirely replaces CorpusAI's hand-rolled `UsageRecorder` +
`RecordingChatModel` decorator.

**Spring AI, per-call (for the SSE payload)**: `streamUsage(true)` (#4) makes the
final streamed `ChatResponse` carry `Usage.getPromptTokens()`/`getCompletionTokens()`;
`ChatAssistant.toStreamEvent()` turns that into `ChatStreamEvent.Done`.
`ChatController` measures latency with a plain `Instant`/`Duration` stopwatch —
identical technique to CorpusAI - and emits both in the SSE `done` event plus one
log line.

- **No usage table.** Unlike CorpusAI's `llm_usage` table, this project's frozen
  schema has no equivalent, and none was added — out of scope per the original
  constraint that only `comparison_vector_store`/`comparison_chat_sessions`/
  `comparison_chat_messages` exist. The obligation asks for *recording*, not
  *persistence*, and the automatic Micrometer layer satisfies "whatever Spring
  AI/Micrometer gives you out of the box" without writing anything manual.
- **Deferred, not done**: `/actuator/metrics/gen_ai.client.token.usage` is not
  HTTP-reachable without adding `management.endpoints.web.exposure.include` to
  the frozen `application.yml`. Confirmed live: `/actuator/health` returns 200,
  `/actuator/metrics` and the specific meter both return 404. Metrics are being
  recorded either way (Micrometer doesn't need HTTP exposure to record); only
  the inspection endpoint needs that (not-yet-approved) yml edit.
- **A real bug the 1.1 docs did not warn about, found only via a live call**:
  the docs state that non-final streamed chunks carry a usage field "with a
  null value." Empirically, against a real `gpt-5.4-mini` stream with
  `streamUsage(true)`, `ChatResponseMetadata.getUsage()` is **never null** -
  every chunk carries a `Usage` object, populated with zeros until the real
  final numbers arrive. Checking `usage.getPromptTokens() != null` therefore
  misclassified every content-bearing chunk as the final one, and the SSE
  stream sent zero actual token text - a totally silent failure that only a
  live call surfaces; nothing about it is visible in a type signature or in
  the docs. Fixed in `ChatAssistant.toStreamEvent()` by checking output text
  first (empty only on the genuinely final chunk) and requiring the usage
  numbers to be actually nonzero before treating a chunk as the `done` event.
  A second, related quirk: the real usage numbers arrived on **two** separate
  trailing chunks, not one - `ChatAssistant.stream()` now suppresses every
  `done`-shaped chunk after the first via a per-subscription `AtomicBoolean`,
  regardless of which of these two behaviors is a `gpt-5.4-mini`-specific
  quirk versus a general OpenAI streaming quirk.

---

## Summary table

| # | Obligation | Equivalence | Workaround needed |
|---|---|---|---|
| 1 | Ingestion | Partial | No chunk-overlap param in `TokenTextSplitter`; no single ingestor class |
| 2 | Vector store | Direct, less code | None |
| 3 | Embedding model | Direct, less code | None |
| 4 | Chat model + streaming | Direct, less code | None |
| 5 | Assistant + system prompt | Direct, different shape | None |
| 6 | Filtered retrieval | Direct | None |
| 7 | Query compression | Direct | None |
| 8 | RAG pipeline assembly | Direct | Default augmenter double-applies grounding |
| 9 | Memory + persistence | Partial | Append-only `saveAll` (same as CorpusAI); system-prompt workaround and `ChatMemoryRegistry`-equivalent both eliminated |
| 10 | System prompt | Direct (text identical) | subjectId substitutes for displayName; admin override not replicated (both by scope) |
| 11 | SSE streaming | Direct | None |
| 12 | Usage + latency | Direct (read) / automatic (recording) | Usage-chunk detection needed text-first logic + dedup, found live; metrics HTTP endpoint deferred pending yml approval |