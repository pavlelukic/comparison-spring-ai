package com.corpusai.comparison_spring_ai.springai.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RagPipelineFactory {

    private static final int TOP_K = 4;

    private final VectorStore vectorStore;
    private final ChatClient.Builder chatClientBuilder;
    private final Map<String, RetrievalAugmentationAdvisor> cache = new ConcurrentHashMap<>();

    public RagPipelineFactory(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClientBuilder = chatClientBuilder;
    }

    public RetrievalAugmentationAdvisor forSubject(String subjectId) {
        return cache.computeIfAbsent(subjectId, this::build);
    }

    private RetrievalAugmentationAdvisor build(String subjectId) {
        var retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(TOP_K)
                .filterExpression(new FilterExpressionBuilder().eq("subject_id", subjectId).build())
                .build();

        var compressionTransformer = CompressionQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryTransformers(compressionTransformer)
                .build();
    }
}