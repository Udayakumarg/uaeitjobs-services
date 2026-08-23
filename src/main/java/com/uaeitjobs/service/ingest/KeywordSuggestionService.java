package com.uaeitjobs.service.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uaeitjobs.dto.KeywordSuggestionDTO;
import com.uaeitjobs.entity.KeywordSearchStrategy;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.repository.KeywordSearchStrategyRepository;
import com.uaeitjobs.service.ingest.pipeline.description.llm.LlmClient;
import com.uaeitjobs.service.ingest.pipeline.description.llm.LlmConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Asks the same LLM already configured for description formatting
 * ({@code app.llm.*}) to propose new JSearch search keywords, then inserts
 * them into {@link KeywordSearchStrategy} at the lowest rotation tier.
 *
 * <p>The LLM is a <em>candidate generator only</em> — it does not decide
 * whether a keyword is actually good. Every keyword it proposes enters the
 * same real-performance-tracked rotation every other keyword goes through
 * ({@code total_runs}/{@code total_returned}/{@code total_inserted} via
 * {@link KeywordSearchStrategyRepository#pickByTier}); a bad suggestion
 * simply never accumulates inserts and stays at the bottom of the rotation
 * instead of being trusted outright.
 */
@Slf4j
@Service
public class KeywordSuggestionService {

    private static final Set<String> VALID_CATEGORIES = Set.of("role", "technology", "experience", "location");
    /** New suggestions start untested — lowest rotation tier until they prove themselves. */
    private static final short NEW_KEYWORD_TIER = 4;
    private static final int MAX_SUGGESTIONS = 20;

    private static final Pattern CODE_FENCE =
            Pattern.compile("^\\s*```(?:json)?\\s*|\\s*```\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final String SYSTEM_PROMPT = """
            You are a UAE technology-recruitment market analyst helping tune a job board's search coverage.

            TASK: propose new search keywords to find IT/technology job postings in the UAE that are NOT
            already covered by the existing keyword list you're given.

            RULES (must follow exactly):
            1. Only propose keywords for IT/technology/software roles — nothing outside that domain.
            2. Do not repeat or trivially rephrase any keyword already in the existing list.
            3. Favour current 2025/2026-era UAE tech hiring trends: AI/LLM roles (e.g. prompt engineer,
               MLOps engineer, AI product manager), platform engineering, site reliability, and named
               technologies not yet covered.
            4. Each keyword should be realistic search text a recruiter or job board would actually use —
               2 to 5 words, no boolean operators, no punctuation beyond spaces and hyphens.
            5. Classify each keyword into exactly one category: "role", "technology", "experience", or "location".
            6. Return ONLY a JSON array, no markdown fences, no commentary, no explanation.
               Format: [{"keyword": "prompt engineer UAE", "category": "role"}, ...]
            """;

    private final LlmConfig config;
    private final Map<String, LlmClient> clientsByName;
    private final KeywordSearchStrategyRepository keywordRepository;
    private final ObjectMapper objectMapper;

    public KeywordSuggestionService(LlmConfig config,
                                     List<LlmClient> clients,
                                     KeywordSearchStrategyRepository keywordRepository,
                                     ObjectMapper objectMapper) {
        this.config = config;
        this.clientsByName = clients.stream()
                .collect(Collectors.toUnmodifiableMap(c -> c.name().toLowerCase(Locale.ROOT), c -> c));
        this.keywordRepository = keywordRepository;
        this.objectMapper = objectMapper;
    }

    public KeywordSuggestionDTO.Response suggestAndAddKeywords() {
        if (!config.enabled()) {
            throw new ValidationException("LLM is disabled (app.llm.enabled=false) — no provider to call");
        }
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new ValidationException("app.llm.api-key is blank — cannot call the LLM");
        }
        LlmClient client = clientsByName.get(config.provider().toLowerCase(Locale.ROOT));
        if (client == null) {
            throw new ValidationException("No LLM client registered for provider '" + config.provider() + "'");
        }

        List<KeywordSearchStrategy> existing = keywordRepository.findAll();
        Set<String> existingLower = existing.stream()
                .map(k -> k.getKeyword().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        String existingList = existing.stream().map(KeywordSearchStrategy::getKeyword)
                .collect(Collectors.joining(", "));

        String userPrompt = "Existing keywords (do not repeat these): " + existingList
                + "\n\nPropose up to " + MAX_SUGGESTIONS + " new keywords as a JSON array.";

        String response;
        try {
            response = client.complete(SYSTEM_PROMPT, userPrompt);
        } catch (Exception e) {
            log.error("Keyword suggestion LLM call failed", e);
            throw new ValidationException("LLM call failed: " + e.getMessage());
        }

        List<Suggestion> suggestions = parse(response);

        List<String> added = new ArrayList<>();
        List<String> skippedExisting = new ArrayList<>();
        List<String> skippedInvalid = new ArrayList<>();

        for (Suggestion s : suggestions) {
            String keyword = s.keyword() == null ? "" : s.keyword().trim();
            if (keyword.isBlank() || keyword.length() > 255) {
                skippedInvalid.add(String.valueOf(s.keyword()));
                continue;
            }
            if (existingLower.contains(keyword.toLowerCase(Locale.ROOT))) {
                skippedExisting.add(keyword);
                continue;
            }
            String category = VALID_CATEGORIES.contains(s.category()) ? s.category() : "role";

            KeywordSearchStrategy row = new KeywordSearchStrategy();
            row.setKeyword(keyword);
            row.setTier(NEW_KEYWORD_TIER);
            row.setCategory(category);
            row.setWeight(BigDecimal.ONE);
            row.setActive(true);
            keywordRepository.save(row);

            existingLower.add(keyword.toLowerCase(Locale.ROOT));
            added.add(keyword);
        }

        log.info("Keyword suggestion run: {} added, {} already existed, {} invalid",
                added.size(), skippedExisting.size(), skippedInvalid.size());
        return new KeywordSuggestionDTO.Response(added, skippedExisting, skippedInvalid);
    }

    private List<Suggestion> parse(String raw) {
        String cleaned = CODE_FENCE.matcher(raw == null ? "" : raw).replaceAll("").trim();
        try {
            Suggestion[] parsed = objectMapper.readValue(cleaned, Suggestion[].class);
            return List.of(parsed);
        } catch (Exception e) {
            log.warn("Could not parse LLM keyword response as JSON — treating as zero suggestions. Response: {}",
                    cleaned.length() <= 300 ? cleaned : cleaned.substring(0, 300) + "…");
            return List.of();
        }
    }

    private record Suggestion(String keyword, String category) {}
}
