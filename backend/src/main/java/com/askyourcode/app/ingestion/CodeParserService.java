package com.askyourcode.app.ingestion;

import com.askyourcode.app.ingestion.model.CodeChunkEntity;
import com.askyourcode.app.ingestion.model.FileEntity;
import com.askyourcode.app.ingestion.model.RepositoryEntity;
import com.askyourcode.app.ingestion.repo.CodeChunkRepository;
import com.askyourcode.app.ingestion.repo.FileEntityRepository;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CodeParserService {

    private final FileEntityRepository fileRepo;
    private final CodeChunkRepository chunkRepo;

    public CodeParserService(FileEntityRepository fileRepo, CodeChunkRepository chunkRepo) {
        this.fileRepo = fileRepo;
        this.chunkRepo = chunkRepo;
    }

    public void parseRepository(Path root, RepositoryEntity repository) {
        List<FileEntity> files = fileRepo.findByRepository(repository);
        List<String> failures = new java.util.ArrayList<>();
        for (FileEntity f : files) {
            if (!SUPPORTED_LANGUAGES.contains(f.getLanguage().toLowerCase())) continue;
            Path filePath = root.resolve(f.getRelativePath());
            try {
                String content = Files.readString(filePath);
                if ("java".equalsIgnoreCase(f.getLanguage())) {
                    parseJava(f, content);
                } else if ("python".equalsIgnoreCase(f.getLanguage())) {
                    parsePython(f, content);
                } else {
                    parseBraceLanguage(f, content);
                }

            } catch (IOException | RuntimeException ex) {
                failures.add(f.getRelativePath() + ": " + safeMessage(ex));
            }
        }
        if (!failures.isEmpty()) {
            String details = failures.stream().limit(10).collect(java.util.stream.Collectors.joining("; "));
            String suffix = failures.size() > 10 ? "; and " + (failures.size() - 10) + " more" : "";
            throw new IllegalStateException("Failed to parse " + failures.size() + " source file(s): " + details + suffix);
        }
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("java", "javascript", "typescript", "python");

    private static final Pattern BRACE_DECLARATION = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:default\\s+)?(?:abstract\\s+)?"
                    + "(?:(class|interface|enum|type)\\s+([A-Za-z_$][\\w$]*)"
                    + "|(?:async\\s+)?function\\s+([A-Za-z_$][\\w$]*)"
                    + "|(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[A-Za-z_$][\\w$]*)\\s*=>)"
                    + ".*$");
    private static final Pattern BRACE_METHOD = Pattern.compile(
            "^\\s*(?:public|private|protected|static|async|readonly|get|set|abstract|override|final|synchronized|native|default\\s+)*"
                    + "([A-Za-z_$][\\w$]*)\\s*\\([^;]*\\)\\s*(?::\\s*[^={]+)?\\s*\\{.*$");
    private static final Set<String> NON_METHOD_KEYWORDS = Set.of("if", "for", "while", "switch", "catch", "with");

    private void parseJava(FileEntity file, String content) {
        CompilationUnit cu = StaticJavaParser.parse(content);
        cu.findAll(MethodDeclaration.class).forEach(m -> handleCallable(file, content, m));
        cu.findAll(ConstructorDeclaration.class).forEach(c -> handleCallable(file, content, c));
    }

    /**
     * Extracts useful declaration-sized chunks from JS/TS without executing or
     * transpiling repository code. This intentionally favors safe, readable
     * chunks over attempting to be a complete language grammar.
     */
    private void parseBraceLanguage(FileEntity file, String content) {
        String[] lines = lines(content);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            Matcher declaration = BRACE_DECLARATION.matcher(line);
            if (declaration.matches()) {
                String type = declaration.group(1);
                String symbol = firstNonBlank(declaration.group(2), declaration.group(3), declaration.group(4));
                saveDeclaration(file, lines, index, symbol, type == null ? "function" : type);
                continue;
            }

            Matcher method = BRACE_METHOD.matcher(line);
            if (method.matches() && !NON_METHOD_KEYWORDS.contains(method.group(1))) {
                saveDeclaration(file, lines, index, method.group(1), "method");
            }
        }
    }

    private void parsePython(FileEntity file, String content) {
        String[] lines = lines(content);
        Pattern declaration = Pattern.compile("^(\\s*)(?:async\\s+)?(def|class)\\s+([A-Za-z_][\\w]*)\\s*.*:");
        for (int index = 0; index < lines.length; index++) {
            Matcher match = declaration.matcher(lines[index]);
            if (!match.matches()) continue;

            int indentation = match.group(1).length();
            int end = index;
            for (int next = index + 1; next < lines.length; next++) {
                String candidate = lines[next];
                if (!candidate.trim().isEmpty() && leadingSpaces(candidate) <= indentation) break;
                end = next;
            }
            saveChunk(file, match.group(3), match.group(2), index + 1, end + 1, lines);
        }
    }

    private void saveDeclaration(FileEntity file, String[] lines, int startIndex, String symbol, String type) {
        int endIndex = findBraceEnd(lines, startIndex);
        saveChunk(file, symbol, type, startIndex + 1, endIndex + 1, lines);
    }

    private int findBraceEnd(String[] lines, int startIndex) {
        int depth = 0;
        boolean opened = false;
        for (int index = startIndex; index < lines.length; index++) {
            String stripped = stripLineComment(lines[index]);
            for (int character = 0; character < stripped.length(); character++) {
                char value = stripped.charAt(character);
                if (value == '{') {
                    depth++;
                    opened = true;
                } else if (value == '}' && opened && --depth == 0) {
                    return index;
                }
            }
            if (opened && depth == 0) return index;
        }
        return lines.length - 1;
    }

    private void saveChunk(FileEntity file, String symbol, String type, int start, int end, String[] lines) {
        int safeStart = Math.max(1, start);
        int safeEnd = Math.min(lines.length, Math.max(safeStart, end));
        StringBuilder content = new StringBuilder();
        for (int line = safeStart; line <= safeEnd; line++) content.append(lines[line - 1]).append("\n");
        chunkRepo.save(new CodeChunkEntity(file, symbol, type, safeStart, safeEnd, content.toString()));
    }

    private static String[] lines(String content) {
        return content.split("\\r?\\n", -1);
    }

    private static int leadingSpaces(String line) {
        return line.length() - line.stripLeading().length();
    }

    private static String stripLineComment(String line) {
        int slash = line.indexOf("//");
        return slash >= 0 ? line.substring(0, slash) : line;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "anonymous";
    }

    private void handleCallable(FileEntity f, String fullContent, CallableDeclaration<?> decl) {
        if (!decl.getRange().isPresent()) return;
        int start = decl.getRange().get().begin.line;
        int end = decl.getRange().get().end.line;
        String[] lines = fullContent.split("\r?\n");
        int s = Math.max(1, start);
        int e = Math.min(lines.length, end);
        StringBuilder sb = new StringBuilder();
        for (int i = s; i <= e; i++) {
            sb.append(lines[i-1]).append("\n");
        }

        String symbol = decl.getNameAsString();
        String type = decl instanceof MethodDeclaration ? "method" : "constructor";

        // find file entity from repo
        FileEntity fileEntity = f;

        CodeChunkEntity chunk = new CodeChunkEntity(fileEntity, symbol, type, start, end, sb.toString());
        chunkRepo.save(chunk);
    }
}
