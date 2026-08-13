package com.corpusai.comparison_spring_ai.ingestion;

import com.corpusai.comparison_spring_ai.springai.ingestion.IngestionPipeline;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionPipeline ingestionPipeline;

    public IngestionController(IngestionPipeline ingestionPipeline) {
        this.ingestionPipeline = ingestionPipeline;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IngestResponse ingest(@RequestParam("file") MultipartFile file,
                                  @RequestParam("subjectId") String subjectId) {
        int chunkCount = ingestionPipeline.ingest(file.getResource(), subjectId);
        return new IngestResponse(subjectId, file.getOriginalFilename(), chunkCount);
    }
}