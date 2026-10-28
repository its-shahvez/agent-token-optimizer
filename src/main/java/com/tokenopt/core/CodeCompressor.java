package com.tokenopt.core;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.springframework.stereotype.Component;

@Component
public class CodeCompressor implements CompressionStrategy {

    private final TokenEstimator tokenEstimator;
    private final RawChunkStore chunkStore;

    public CodeCompressor(TokenEstimator tokenEstimator, RawChunkStore chunkStore) {
        this.tokenEstimator = tokenEstimator;
        this.chunkStore = chunkStore;
    }

    public CompressionResult compress(String rawCode, CompressionLevel level) {
        if (rawCode == null || rawCode.isBlank()) {
            return CompressionResult.of("none", "", 0, 0, "CodeCompressor");
        }

        String chunkId = chunkStore.store(rawCode);
        int origTokens = tokenEstimator.countTokens(rawCode);

        String compressed;
        String strategy;

        try {
            CompilationUnit cu = StaticJavaParser.parse(rawCode);
            compressed = buildAstSkeleton(cu, chunkId, level);
            strategy = "CodeCompressor-AST(" + level.name() + ")";
        } catch (Exception e) {
            compressed = buildFallbackSkeleton(rawCode, chunkId);
            strategy = "CodeCompressor-HeuristicFallback(" + level.name() + ")";
        }

        int compTokens = tokenEstimator.countTokens(compressed);
        return CompressionResult.of(chunkId, compressed, origTokens, compTokens, strategy);
    }

    private String buildAstSkeleton(CompilationUnit cu, String chunkId, CompressionLevel level) {
        StringBuilder sb = new StringBuilder();
        sb.append("// [AST Skeleton | chunkId=").append(chunkId)
                .append(" | Use expand_section(chunkId, startLine, endLine) to read method bodies]\n");

        cu.getPackageDeclaration().ifPresent(pkg -> sb.append(pkg.toString().trim()).append("\n\n"));

        if (level == CompressionLevel.AGGRESSIVE && cu.getImports().size() > 5) {
            sb.append("// [").append(cu.getImports().size()).append(" imports omitted]\n\n");
        } else {
            cu.getImports().forEach(imp -> sb.append(imp.toString().trim()).append("\n"));
            if (!cu.getImports().isEmpty()) sb.append("\n");
        }

        for (TypeDeclaration<?> type : cu.getTypes()) {
            appendTypeSkeleton(type, sb, level, "");
        }

        return sb.toString().trim();
    }

    private void appendTypeSkeleton(TypeDeclaration<?> type, StringBuilder sb, CompressionLevel level, String indent) {
        type.getAnnotations().forEach(a -> sb.append(indent).append(a.toString()).append("\n"));
        sb.append(indent).append(type.getAccessSpecifier().asString())
                .append(type.getAccessSpecifier().asString().isEmpty() ? "" : " ")
                .append(type.isClassOrInterfaceDeclaration() && type.asClassOrInterfaceDeclaration().isInterface() ? "interface " : "class ")
                .append(type.getNameAsString()).append(" {\n");

        String memberIndent = indent + "    ";

        for (FieldDeclaration field : type.getFields()) {
            if (level == CompressionLevel.AGGRESSIVE && field.isPrivate()) continue;
            sb.append(memberIndent).append(field.toString().trim()).append("\n");
        }

        for (ConstructorDeclaration ctor : type.getConstructors()) {
            appendCallableSignature(ctor, sb, memberIndent);
        }

        for (MethodDeclaration method : type.getMethods()) {
            if (level == CompressionLevel.AGGRESSIVE && method.isPrivate()) continue;
            appendCallableSignature(method, sb, memberIndent);
        }

        sb.append(indent).append("}\n");
    }

    private void appendCallableSignature(CallableDeclaration<?> callable, StringBuilder sb, String indent) {
        int startLine = callable.getBegin().map(p -> p.line).orElse(-1);
        int endLine = callable.getEnd().map(p -> p.line).orElse(-1);

        callable.getAnnotations().forEach(a -> sb.append(indent).append(a.toString()).append("\n"));
        sb.append(indent)
                .append(callable.getDeclarationAsString(true, true, true))
                .append(String.format(" { /* L%d-L%d */ }%n", startLine, endLine));
    }

    private String buildFallbackSkeleton(String rawCode, String chunkId) {
        String[] lines = rawCode.split("\\R");
        StringBuilder sb = new StringBuilder();
        sb.append("// [Heuristic Skeleton | chunkId=").append(chunkId).append("]\n");
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("public ") || trimmed.startsWith("private ") ||
                    trimmed.startsWith("protected ") || trimmed.startsWith("class ") ||
                    trimmed.startsWith("def ") || trimmed.startsWith("function ") ||
                    trimmed.startsWith("export ")) {
                sb.append(String.format("L%d: %s%n", i + 1, lines[i]));
            }
        }
        return sb.toString().trim();
    }
}