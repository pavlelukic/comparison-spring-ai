package com.corpusai.comparison_spring_ai.chat;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Component
public class SystemPromptBuilder {

    private static final String SERBIAN_INSTRUCTION =
            "Always respond in Serbian (Latin script, not Cyrillic), regardless of the language the User writes in.";
    private static final String ENGLISH_INSTRUCTION =
            "Always respond in English, regardless of the language the User writes in.";

    private final String personaTemplate;
    private final String groundingRule;

    public SystemPromptBuilder() {
        this.personaTemplate = readResource("prompts/tutor-persona.txt");
        this.groundingRule = readResource("prompts/grounding-rule.txt");
    }

    // Single subject, no Subject entity in scope - subjectId stands in for the subject name
    // CorpusAI resolves via SubjectService.
    public String build(String subjectId, String lang) {
        String persona = personaTemplate.replace("{{subjectName}}", subjectId);
        String langInstruction = "sr".equals(lang) ? SERBIAN_INSTRUCTION : ENGLISH_INSTRUCTION;
        return persona + "\n\n" + groundingRule + "\n\n" + langInstruction;
    }

    private static String readResource(String path) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}