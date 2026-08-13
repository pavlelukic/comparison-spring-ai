package com.corpusai.comparison_spring_ai.ingestion;

public record IngestResponse(String subjectId, String fileName, int chunkCount) {
}