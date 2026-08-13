package com.corpusai.comparison_spring_ai.springai.ingestion;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IngestionPipeline {
    
    private static final int CHUNK_SIZE_TOKENS = 300;

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;

    public IngestionPipeline(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.splitter = TokenTextSplitter.builder().withChunkSize(CHUNK_SIZE_TOKENS).build();
    }

    public int ingest(Resource file, String subjectId) {
        List<Document> parsed = new TikaDocumentReader(file).get();
        List<Document> tagged = parsed.stream()
                .map(document -> document.mutate().metadata("subject_id", subjectId).build())
                .toList();
        List<Document> chunks = splitter.split(tagged);
        vectorStore.add(chunks);
        return chunks.size();
    }
}