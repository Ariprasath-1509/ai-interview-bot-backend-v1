package com.benchreadiness.screening.service;

import com.benchreadiness.screening.entity.ScreeningBatch;
import com.benchreadiness.screening.entity.ScreeningQuestion;
import com.benchreadiness.screening.entity.enums.QuestionType;
import com.benchreadiness.screening.llm.ScreeningLlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generates the one shared question pool for a batch — a single LLM call, reused (shuffled) across every candidate in it. */
@Service
public class QuestionGenerationService {

    public static final String PROFICIENCY_PROMPT =
            "Why do we need to hire you? Answer in your own words, in a few sentences.";

    private static final Map<String, String> LANGUAGE_BASICS_SCOPE = new LinkedHashMap<>();
    static {
        LANGUAGE_BASICS_SCOPE.put("Java + Spring Boot",
                "Core Java fundamentals through exception handling: variables and data types, operators, " +
                "control flow (if/else, loops, switch), arrays, strings, OOP (classes, objects, constructors, " +
                "inheritance, polymorphism, encapsulation, abstraction, interfaces, access modifiers), and exception " +
                "handling (try/catch/finally, checked vs unchecked exceptions, custom exceptions, throw/throws) — " +
                "plus absolute Spring Boot basics only: what Spring/Spring Boot is, dependency injection, common " +
                "annotations (@RestController, @Service, @Autowired, @RequestMapping/@GetMapping/@PostMapping), and " +
                "the basic shape of a REST controller. " +
                "Do NOT include collections, generics, streams, multithreading, lambdas, Spring Data JPA, Spring " +
                "Security, or any other advanced/framework-internal topics.");
        LANGUAGE_BASICS_SCOPE.put("Python",
                "Core Python fundamentals through exception handling only: variables and data types, operators, " +
                "control flow, functions, lists/tuples/dicts basics, OOP (classes, objects, inheritance, " +
                "polymorphism, encapsulation), and exception handling (try/except/finally/raise, custom exceptions). " +
                "Do NOT include decorators, generators, async/await, comprehensions beyond basics, or third-party libraries.");
        LANGUAGE_BASICS_SCOPE.put("JavaScript",
                "Core JavaScript fundamentals through exception handling only: variables (var/let/const), data types, " +
                "operators, control flow, functions, OOP (prototypes, classes, inheritance, encapsulation), and " +
                "exception handling (try/catch/finally, throw, custom errors). " +
                "Do NOT include async/await, promises, closures beyond basics, or framework-specific APIs (React/Node).");
        LANGUAGE_BASICS_SCOPE.put("C++",
                "Core C++ fundamentals through exception handling only: variables and data types, operators, control " +
                "flow, functions, arrays, OOP (classes, objects, constructors/destructors, inheritance, polymorphism, " +
                "encapsulation), and exception handling (try/catch/throw, custom exceptions). " +
                "Do NOT include templates, STL containers, smart pointers, or multithreading.");
        LANGUAGE_BASICS_SCOPE.put(".NET / C#",
                "Core C# fundamentals through exception handling only: variables and data types, operators, control " +
                "flow, arrays, strings, OOP (classes, objects, constructors, inheritance, polymorphism, encapsulation, " +
                "interfaces), and exception handling (try/catch/finally, custom exceptions). " +
                "Do NOT include LINQ, async/await, generics, ASP.NET internals, or Entity Framework.");
        LANGUAGE_BASICS_SCOPE.put("Full Stack (MERN)",
                "Fundamentals of the MERN stack at a beginner level: HTML/CSS basics, core JavaScript (variables, " +
                "functions, control flow, basic OOP), the basic idea of React (components, props, state) and Node/" +
                "Express (routes, request/response basics), and basic MongoDB concepts (documents, collections, " +
                "simple CRUD). Snippet/logical questions should use plain JavaScript or simple React/Express code. " +
                "Do NOT include hooks beyond useState/useEffect, Redux, middleware internals, or aggregation pipelines.");
        LANGUAGE_BASICS_SCOPE.put("Manual Testing",
                "QA/manual testing fundamentals: the software testing life cycle (STLC), levels of testing (unit, " +
                "integration, system, UAT), types of testing (functional, regression, smoke, sanity, black-box vs " +
                "white-box), writing test cases and bug reports, severity vs priority, and basic SDLC/Agile " +
                "terminology. Snippet questions should show a short sample test case, bug report, or requirement and " +
                "ask the candidate to evaluate/complete it; logical questions should ask the candidate to design " +
                "simple test cases or a bug report for a given short scenario, in plain text (no code editor). " +
                "Do NOT include test automation tools, scripting, or programming-language-specific content.");
        LANGUAGE_BASICS_SCOPE.put("Automation Testing (Selenium + Java)",
                "Automation testing fundamentals built on core Java basics (variables, control flow, OOP through " +
                "exception handling, same scope as the Java track) plus foundational Selenium WebDriver concepts: " +
                "locators (id, name, xpath, css selector), basic WebDriver commands (findElement, click, sendKeys, " +
                "getText), waits (implicit vs explicit, basic idea only), and the basic structure of a test script " +
                "using a framework like JUnit/TestNG. Snippet questions should show a short Selenium/Java code " +
                "snippet; logical questions should ask the candidate to write pseudocode/code for a simple automation " +
                "scenario (e.g. locate and click a button, assert page text) as plain text. " +
                "Do NOT include Page Object Model, TestNG/JUnit advanced features, CI integration, or grid/parallel execution.");
        LANGUAGE_BASICS_SCOPE.put("DevOps",
                "DevOps fundamentals at a beginner level: basic Linux commands and file permissions, version control " +
                "basics with Git (commit, push, pull, branch, merge), the basic idea of CI/CD pipelines, Docker " +
                "fundamentals (image vs container, Dockerfile basics, common commands), and the general purpose of " +
                "cloud/infra concepts (what is a server, what is deployment). Snippet questions should show a short " +
                "shell command, Dockerfile line, or Git command sequence; logical questions should ask the candidate " +
                "to write out the steps/commands for a simple scenario (e.g. initialize a repo and push a first " +
                "commit, write a minimal Dockerfile) as plain text. " +
                "Do NOT include Kubernetes, advanced pipeline scripting, cloud-provider-specific services, or " +
                "infrastructure-as-code tools.");
        LANGUAGE_BASICS_SCOPE.put("SQL / Database",
                "Database fundamentals: what a relational database is, tables/rows/columns, primary and foreign " +
                "keys, basic SQL (SELECT, WHERE, ORDER BY, basic JOIN, GROUP BY, INSERT/UPDATE/DELETE), and basic " +
                "normalization concepts (1NF/2NF/3NF at a conceptual level). Snippet questions should show a short " +
                "SQL query or table and ask what it does/returns; logical questions should ask the candidate to " +
                "write a SQL query for a simple requirement, as plain text. " +
                "Do NOT include stored procedures, triggers, transactions/isolation levels, indexing internals, or " +
                "database-specific advanced features.");
    }

    private final ScreeningLlmClient llmClient;
    private final ObjectMapper objectMapper;

    public QuestionGenerationService(ScreeningLlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public static List<String> supportedLanguages() {
        return new ArrayList<>(LANGUAGE_BASICS_SCOPE.keySet());
    }

    public static String scopeFor(String language) {
        return LANGUAGE_BASICS_SCOPE.getOrDefault(language,
                "Core " + language + " fundamentals through exception handling — basic syntax, control flow, " +
                "object-oriented concepts, and error/exception handling only. No advanced or framework-specific topics.");
    }

    /** Builds the 24 LLM-generated questions (10 MCQ + 10 snippet + 4 logical) plus the 1 fixed proficiency question. */
    public List<ScreeningQuestion> generate(ScreeningBatch batch) throws Exception {
        String scope = scopeFor(batch.getLanguage());
        String systemPrompt = "You are a technical assessment author for an entry-level " + batch.getLanguage() +
                " screening test. Output strictly valid JSON only, no markdown, no commentary, matching exactly the " +
                "schema described by the user. Keep every question strictly within the given concept scope — do not " +
                "test anything outside it.";
        String userPrompt = "Concept scope: " + scope +
                "\n\nAll content must fit the stated concept scope exactly, at an easy/entry-level difficulty.\n\n" +
                SCHEMA_INSTRUCTIONS;

        String raw = llmClient.generateQuestions(systemPrompt, userPrompt);
        return parseQuestionsJson(batch, raw);
    }

    private static final String SCHEMA_INSTRUCTIONS = """
            Generate a JSON object with exactly this shape:
            {
              "mcq": [ { "prompt": string, "options": [string,string,string,string], "correctAnswer": string } ] (exactly 10 items),
              "snippets": [ { "prompt": string, "referenceAnswer": string } ] (exactly 10 items),
              "logical": [ { "prompt": string, "referenceAnswer": string } ] (exactly 4 items)
            }

            Rules:
            - "mcq": each prompt is a short conceptual question with exactly 4 options; correctAnswer must be the exact text of one of the options.
            - "snippets": each prompt must embed a short code snippet (3-15 lines) plus a question about it (e.g. "what does this print", "what is wrong with this code and why"). referenceAnswer is the correct explanation/output, used only for grading — never shown to the candidate.
            - "logical": each prompt is an easy logical/algorithmic problem statement asking the candidate to write pseudocode or code (no code editor is provided — plain text). referenceAnswer is a brief correct approach/solution outline used only for grading.
            - Return ONLY the JSON object, nothing else.
            """;

    /** Generates a fresh question set scoped to a job description instead of the fixed language preset. */
    public List<ScreeningQuestion> generateFromJd(ScreeningBatch batch, String jdText) throws Exception {
        String systemPrompt = "You are a technical assessment author. Given a job description, first identify the " +
                "CORE/FOUNDATIONAL skills it calls for — suitable for an entry-level written screening test, not " +
                "senior-level or 'good-to-have' extras — then author a question set testing only those foundations. " +
                "Output strictly valid JSON only, no markdown, no commentary.";
        String userPrompt = "Job description:\n" + jdText + "\n\n" +
                "Scope every question to the core/must-have technical skills in this JD, at a basics/fundamentals " +
                "level only — skip advanced, senior, or 'good-to-have' items. " + SCHEMA_INSTRUCTIONS;

        String raw = llmClient.generateQuestions(systemPrompt, userPrompt);
        return parseQuestionsJson(batch, raw);
    }

    /**
     * Extracts an already-authored question paper verbatim instead of inventing new questions — the source
     * document typically has no answer key, so the model also has to work out the correct/reference answer
     * for each question it extracts. Needs a larger token budget than fresh generation since the source
     * document's own wording (including code snippets) has to be echoed back in full.
     */
    public List<ScreeningQuestion> generateFromQuestionPaper(ScreeningBatch batch, String paperText) throws Exception {
        String systemPrompt = "You transcribe an existing question paper into a structured format — you do NOT " +
                "invent new questions. Preserve each question's original wording (and any code snippets/options) " +
                "verbatim. The source document has no answer key, so for each question you also work out the " +
                "correct answer (MCQs) or a reference/expected answer (everything else) yourself, for grading " +
                "purposes only — never shown to the candidate. Output strictly valid JSON only, no markdown, no commentary.";
        String userPrompt = "Question paper:\n" + paperText + "\n\n" +
                "Map the paper's multiple-choice questions to \"mcq\", its output-based/short-answer questions to " +
                "\"snippets\", and its logical/programming questions (where the candidate writes code, typically " +
                "\"answer any N of M\") to \"logical\". Ignore any instructions section, marks/duration notes, and " +
                "any \"why should we hire you\" style question — those are handled separately. If the paper has " +
                "more or fewer than 10 MCQs / 10 output-based / 4 logical questions, include exactly what's there " +
                "(don't pad or invent extras to hit a count). " + SCHEMA_INSTRUCTIONS;

        String raw = llmClient.generateQuestions(systemPrompt, userPrompt, 8000);
        return parseQuestionsJson(batch, raw);
    }

    private List<ScreeningQuestion> parseQuestionsJson(ScreeningBatch batch, String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);

        List<ScreeningQuestion> questions = new ArrayList<>();
        int index = 0;

        for (JsonNode node : root.path("mcq")) {
            ScreeningQuestion q = new ScreeningQuestion();
            q.setBatch(batch);
            q.setQuestionType(QuestionType.MCQ);
            q.setPrompt(node.path("prompt").asText());
            q.setOptionsJson(objectMapper.writeValueAsString(node.path("options")));
            q.setReferenceAnswer(node.path("correctAnswer").asText());
            q.setMarks(1);
            q.setDisplayIndex(index++);
            questions.add(q);
        }
        for (JsonNode node : root.path("snippets")) {
            ScreeningQuestion q = new ScreeningQuestion();
            q.setBatch(batch);
            q.setQuestionType(QuestionType.SNIPPET);
            q.setPrompt(node.path("prompt").asText());
            q.setReferenceAnswer(node.path("referenceAnswer").asText());
            q.setMarks(1);
            q.setDisplayIndex(index++);
            questions.add(q);
        }
        for (JsonNode node : root.path("logical")) {
            ScreeningQuestion q = new ScreeningQuestion();
            q.setBatch(batch);
            q.setQuestionType(QuestionType.LOGICAL);
            q.setPrompt(node.path("prompt").asText());
            q.setReferenceAnswer(node.path("referenceAnswer").asText());
            q.setMarks(5);
            q.setDisplayIndex(index++);
            questions.add(q);
        }

        ScreeningQuestion proficiency = new ScreeningQuestion();
        proficiency.setBatch(batch);
        proficiency.setQuestionType(QuestionType.PROFICIENCY);
        proficiency.setPrompt(PROFICIENCY_PROMPT);
        proficiency.setMarks(5);
        proficiency.setDisplayIndex(index);
        questions.add(proficiency);

        return questions;
    }
}
