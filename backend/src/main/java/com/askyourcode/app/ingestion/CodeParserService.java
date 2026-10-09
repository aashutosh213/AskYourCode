package com.askyourcode.app.ingestion;

import com.askyourcode.app.ingestion.model.FileEntity;
import com.askyourcode.app.ingestion.model.RepositoryEntity;
import com.askyourcode.app.ingestion.repo.FileEntityRepository;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CodeParserService {

    private final FileEntityRepository fileRepo;
    public CodeParserService(FileEntityRepository fileRepo) {
        this.fileRepo = fileRepo;
    }

    public List<ParsedCodeSymbol> parseRepository(Path root, RepositoryEntity repository) {
        return parseFiles(root, fileRepo.findByRepository(repository));
    }

    public List<ParsedCodeSymbol> parseFiles(Path root, List<FileEntity> files) {
        List<ParsedCodeSymbol> symbols = new java.util.ArrayList<>();
        List<String> failures = new java.util.ArrayList<>();
        for (FileEntity f : files) {
            if (!SUPPORTED_LANGUAGES.contains(f.getLanguage().toLowerCase())) continue;
            Path filePath = root.resolve(f.getRelativePath());
            try {
                String content = Files.readString(filePath);
                if ("java".equalsIgnoreCase(f.getLanguage())) {
                    parseJava(f, content, symbols);
                } else if ("python".equalsIgnoreCase(f.getLanguage())) {
                    parsePython(f, content, symbols);
                } else {
                    parseBraceLanguage(f, content, symbols);
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
        return List.copyOf(symbols);
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    /** Largest chunk body kept whole. Dense code is about 2-3 characters per token. */
    static final int MAX_CHUNK_CHARS = 3000;

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
    private static final Pattern PYTHON_CLASS = Pattern.compile("^\\s*class\\s+([A-Za-z_]\\w*)\\b.*");

    private void parseJava(FileEntity file, String content, List<ParsedCodeSymbol> symbols) {
        // Indexed repositories may use any recent Java (records, pattern switches, sealed
        // types). BLEEDING_EDGE accepts every syntax this JavaParser release knows. A
        // parser per call keeps concurrent indexing jobs from sharing state.
        var parsed = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE))
                .parse(content);
        // Partial trees are returned even on error; using them would silently drop chunks.
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            String problems = parsed.getProblems().stream()
                    .limit(3).map(problem -> problem.getMessage()).collect(java.util.stream.Collectors.joining("; "));
            throw new IllegalStateException("Java syntax error: " + problems);
        }
        CompilationUnit cu = parsed.getResult().get();
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(t -> handleType(file, content, t, symbols));
        cu.findAll(EnumDeclaration.class).forEach(t -> handleType(file, content, t, symbols));
        cu.findAll(RecordDeclaration.class).forEach(t -> handleType(file, content, t, symbols));
        cu.findAll(MethodDeclaration.class).forEach(m -> handleCallable(file, content, m, symbols));
        cu.findAll(ConstructorDeclaration.class).forEach(c -> handleCallable(file, content, c, symbols));
    }

    /**
     * Emits a type chunk that covers only the declaration header (annotations,
     * modifiers, name, extends/implements) up to its first member. Member bodies
     * are already covered by method/constructor chunks, so repeating them here
     * would duplicate code in the index and the LLM context.
     */
    private void handleType(FileEntity file, String content, TypeDeclaration<?> decl,
                            List<ParsedCodeSymbol> symbols) {
        if (decl.getRange().isEmpty()) return;
        int start = decl.getRange().get().begin.line;
        int end = decl.getRange().get().end.line;
        int firstMember = firstMemberLine(decl);
        if (firstMember != Integer.MAX_VALUE) {
            end = Math.max(start, firstMember - 1);
        }
        String type = switch (decl) {
            case ClassOrInterfaceDeclaration c when c.isInterface() -> "interface";
            case EnumDeclaration ignored -> "enum";
            case RecordDeclaration ignored -> "record";
            default -> "class";
        };
        addSymbol(file, decl.getNameAsString(), type, enclosingTypeName(decl), start, end, lines(content), symbols);
    }

    private static int firstMemberLine(TypeDeclaration<?> decl) {
        int first = Integer.MAX_VALUE;
        for (var member : decl.getMembers()) {
            if (member.getRange().isPresent()) first = Math.min(first, member.getRange().get().begin.line);
        }
        if (decl instanceof EnumDeclaration enumDecl) {
            for (var entry : enumDecl.getEntries()) {
                if (entry.getRange().isPresent()) first = Math.min(first, entry.getRange().get().begin.line);
            }
        }
        return first;
    }

    /** Dotted names of the enclosing Java types, outermost first, or null at top level. */
    private static String enclosingTypeName(Node node) {
        Deque<String> names = new ArrayDeque<>();
        Optional<TypeDeclaration> ancestor = node.findAncestor(TypeDeclaration.class);
        while (ancestor.isPresent()) {
            names.addFirst(ancestor.get().getNameAsString());
            ancestor = ancestor.get().findAncestor(TypeDeclaration.class);
        }
        return names.isEmpty() ? null : String.join(".", names);
    }

    /**
     * Extracts useful declaration-sized chunks from JS/TS without executing or
     * transpiling repository code. This intentionally favors safe, readable
     * chunks over attempting to be a complete language grammar.
     */
    private void parseBraceLanguage(FileEntity file, String content, List<ParsedCodeSymbol> symbols) {
        String[] lines = lines(content);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            Matcher declaration = BRACE_DECLARATION.matcher(line);
            if (declaration.matches()) {
                String type = declaration.group(1);
                String symbol = firstNonBlank(declaration.group(2), declaration.group(3), declaration.group(4));
                saveDeclaration(file, lines, index, symbol, type == null ? "function" : type, symbols);
                continue;
            }

            Matcher method = BRACE_METHOD.matcher(line);
            if (method.matches() && !NON_METHOD_KEYWORDS.contains(method.group(1))) {
                saveDeclaration(file, lines, index, method.group(1), "method", symbols);
            }
        }
    }

    private void parsePython(FileEntity file, String content, List<ParsedCodeSymbol> symbols) {
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
            addSymbol(file, match.group(3), match.group(2), enclosingPythonClasses(lines, index, indentation),
                    index + 1, end + 1, lines, symbols);
        }
    }

    /** Dotted names of the Python classes enclosing the declaration at {@code index}, or null. */
    private static String enclosingPythonClasses(String[] lines, int index, int indentation) {
        Deque<String> names = new ArrayDeque<>();
        int level = indentation;
        for (int previous = index - 1; previous >= 0 && level > 0; previous--) {
            String line = lines[previous];
            if (line.trim().isEmpty()) continue;
            int lineIndentation = leadingSpaces(line);
            if (lineIndentation >= level) continue;
            level = lineIndentation;
            Matcher owner = PYTHON_CLASS.matcher(line);
            if (owner.matches()) names.addFirst(owner.group(1));
        }
        return names.isEmpty() ? null : String.join(".", names);
    }

    private void saveDeclaration(FileEntity file, String[] lines, int startIndex, String symbol, String type,
                                 List<ParsedCodeSymbol> symbols) {
        int endIndex = isExpressionBodiedArrow(lines[startIndex])
                ? expressionBodyEnd(lines, startIndex)
                : findBraceEnd(lines, startIndex);
        addSymbol(file, symbol, type, null, startIndex + 1, endIndex + 1, lines, symbols);
    }

    /** {@code const f = () => <div/>} has no block; its body is the more-indented lines below it. */
    private static boolean isExpressionBodiedArrow(String line) {
        String stripped = stripLineComment(line);
        int arrow = stripped.lastIndexOf("=>");
        return arrow >= 0 && !stripped.substring(arrow + 2).trim().startsWith("{");
    }

    private static int expressionBodyEnd(String[] lines, int startIndex) {
        int indentation = leadingSpaces(lines[startIndex]);
        int end = startIndex;
        for (int next = startIndex + 1; next < lines.length; next++) {
            String candidate = lines[next];
            if (candidate.isBlank() || leadingSpaces(candidate) <= indentation) break;
            end = next;
        }
        return end;
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

    /**
     * Adds a symbol, splitting it into consecutive line windows when it is larger than
     * {@link #MAX_CHUNK_CHARS}. Every window keeps the symbol name, parent, and its own
     * exact line range, so citations still point at the source that was retrieved. The
     * limit keeps each chunk inside the embedding model's context window.
     */
    private void addSymbol(FileEntity file, String symbol, String type, String parent, int start, int end,
                           String[] lines, List<ParsedCodeSymbol> symbols) {
        int safeStart = Math.max(1, start);
        int safeEnd = Math.min(lines.length, Math.max(safeStart, end));
        int windowStart = safeStart;
        StringBuilder window = new StringBuilder();
        for (int line = safeStart; line <= safeEnd; line++) {
            String text = lines[line - 1] + "\n";
            if (window.length() > 0 && window.length() + text.length() > MAX_CHUNK_CHARS) {
                symbols.add(new ParsedCodeSymbol(file, symbol, type, parent, windowStart, line - 1, window.toString()));
                window.setLength(0);
                windowStart = line;
            }
            window.append(text);
        }
        symbols.add(new ParsedCodeSymbol(file, symbol, type, parent, windowStart, safeEnd, window.toString()));
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

    private void handleCallable(FileEntity f, String fullContent, CallableDeclaration<?> decl,
                                List<ParsedCodeSymbol> symbols) {
        if (!decl.getRange().isPresent()) return;
        int start = decl.getRange().get().begin.line;
        int end = decl.getRange().get().end.line;
        String symbol = decl.getNameAsString();
        String type = decl instanceof MethodDeclaration ? "method" : "constructor";

        addSymbol(f, symbol, type, enclosingTypeName(decl), start, end, lines(fullContent), symbols);
    }
}
