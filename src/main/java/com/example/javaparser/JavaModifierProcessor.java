package com.example.javaparser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

/**
 * Core processor that parses Java source trees and produces or applies modifier changes.
 * <p>
 * Behavior overview:
 * <ul>
 *   <li>Ensure top-level classes are {@code public}.</li>
 *   <li>Promote {@code private}/{@code protected} fields in top-level classes to {@code public}.</li>
 *   <li>Split files into multiple files when multiple top-level classes exist.</li>
 * </ul>
 */
public class JavaModifierProcessor {
    private static final Logger log = LoggerFactory.getLogger(JavaModifierProcessor.class);

    /**
     * Scan the input directory and return change plans for files that require modifications.
     */
    public List<FileChangePlan> analyze(Path inputRoot) throws IOException {
        if (!Files.isDirectory(inputRoot)) {
            throw new IOException("Input path is not a directory: " + inputRoot);
        }

        JavaParser parser = buildParser(inputRoot);
        List<FileChangePlan> plans = new ArrayList<>();

        try (java.util.stream.Stream<Path> paths = Files.walk(inputRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    FileChangePlan plan = analyzeFile(parser, inputRoot, path);
                    if (plan != null && plan.hasChanges()) {
                        plans.add(plan);
                    }
                });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }

        plans.sort(Comparator.comparing(plan -> plan.getRelativePath().toString()));
        return plans;
    }

    /**
     * Process all Java files and write outputs, overwriting existing files.
     */
    public void process(Path inputRoot, Path outputRoot) throws IOException {
        process(inputRoot, outputRoot, OutputConflictStrategy.OVERWRITE);
    }

    /**
     * Process all Java files and write outputs using the selected conflict strategy.
     */
    public void process(Path inputRoot, Path outputRoot, OutputConflictStrategy conflictStrategy) throws IOException {
        if (!Files.isDirectory(inputRoot)) {
            throw new IOException("Input path is not a directory: " + inputRoot);
        }
        Files.createDirectories(outputRoot);

        JavaParser parser = buildParser(inputRoot);

        try (java.util.stream.Stream<Path> paths = Files.walk(inputRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> processFile(parser, inputRoot, outputRoot, path, conflictStrategy));
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    /**
     * Apply changes for a single source file, overwriting existing outputs.
     */
    public void applySingle(Path inputRoot, Path outputRoot, Path sourceFile) throws IOException {
        applySingle(inputRoot, outputRoot, sourceFile, OutputConflictStrategy.OVERWRITE);
    }

    /**
     * Apply changes for a single source file using the chosen conflict strategy.
     */
    public void applySingle(Path inputRoot, Path outputRoot, Path sourceFile, OutputConflictStrategy conflictStrategy)
        throws IOException {
        if (!Files.isDirectory(inputRoot)) {
            throw new IOException("Input path is not a directory: " + inputRoot);
        }
        Files.createDirectories(outputRoot);

        JavaParser parser = buildParser(inputRoot);
        processFile(parser, inputRoot, outputRoot, sourceFile, conflictStrategy);
    }

    /**
     * Build output previews for a single file without writing to disk.
     */
    public List<OutputFilePreview> previewOutputs(Path inputRoot, Path sourceFile) throws IOException {
        if (!Files.isDirectory(inputRoot)) {
            throw new IOException("Input path is not a directory: " + inputRoot);
        }

        JavaParser parser = buildParser(inputRoot);
        return buildOutputs(parser, inputRoot, sourceFile);
    }

    /**
     * Build a JavaParser instance with symbol solving configured for the input tree.
     */
    private JavaParser buildParser(Path inputRoot) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(inputRoot.toFile()));

        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        configuration.setSymbolResolver(new JavaSymbolSolver(typeSolver));

        return new JavaParser(configuration);
    }

    /**
     * Parse a file and compute how it should be split and modified.
     */
    private FileChangePlan analyzeFile(JavaParser parser, Path inputRoot, Path sourceFile) {
        ParseResult<CompilationUnit> result;
        try {
            log.debug("Analyzing {}", sourceFile);
            result = parser.parse(sourceFile);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        if (result.getResult().isEmpty()) {
            log.warn("Parse failed: {}", sourceFile);
            result.getProblems().forEach(problem -> log.warn("  {}", problem.getMessage()));
            return null;
        }
        if (!result.getProblems().isEmpty()) {
            log.debug("Parse warnings for {}: {}", sourceFile, result.getProblems());
        }

        CompilationUnit cu = result.getResult().get();
        List<ClassOrInterfaceDeclaration> topLevelClasses = getTopLevelClasses(cu);
        boolean hasPublicClass = topLevelClasses.stream().anyMatch(ClassOrInterfaceDeclaration::isPublic);
        boolean onlyClasses = isOnlyTopLevelClasses(cu);

        // Decide whether to split the file based on top-level class visibility.
        SplitMode splitMode = SplitMode.NONE;
        ClassOrInterfaceDeclaration primary = null;
        if (!hasPublicClass && onlyClasses && !topLevelClasses.isEmpty()) {
            splitMode = SplitMode.SPLIT_ALL;
        } else if (hasPublicClass && topLevelClasses.size() > 1) {
            splitMode = SplitMode.SPLIT_OTHERS;
            primary = topLevelClasses.stream()
                .filter(ClassOrInterfaceDeclaration::isPublic)
                .findFirst()
                .orElse(topLevelClasses.get(0));
        }

        // Build a per-class plan for public modifier changes and splitting decisions.
        List<ClassChangePlan> classPlans = new ArrayList<>();
        for (ClassOrInterfaceDeclaration clazz : topLevelClasses) {
            boolean moveToNewFile = splitMode == SplitMode.SPLIT_ALL
                || (splitMode == SplitMode.SPLIT_OTHERS && clazz != primary);
            boolean addPublic = !clazz.isPublic();
            List<FieldChangePlan> fieldChanges = collectFieldChanges(clazz);
            classPlans.add(new ClassChangePlan(clazz.getNameAsString(), moveToNewFile, addPublic, fieldChanges));
        }

        Path relative = inputRoot.relativize(sourceFile);
        String primaryName = primary == null ? null : primary.getNameAsString();
        return new FileChangePlan(sourceFile, relative, splitMode, primaryName, classPlans);
    }

    /**
     * Build outputs for a single file and write them to the output directory.
     */
    private void processFile(
        JavaParser parser,
        Path inputRoot,
        Path outputRoot,
        Path sourceFile,
        OutputConflictStrategy conflictStrategy
    ) {
        try {
            List<OutputFilePreview> outputs = buildOutputs(parser, inputRoot, sourceFile);
            writeOutputs(outputs, outputRoot, conflictStrategy);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * @return true if the compilation unit only contains top-level (non-interface) classes.
     */
    private boolean isOnlyTopLevelClasses(CompilationUnit cu) {
        List<TypeDeclaration<?>> types = cu.getTypes();
        if (types.isEmpty()) {
            return false;
        }
        for (TypeDeclaration<?> type : types) {
            if (!(type instanceof ClassOrInterfaceDeclaration)) {
                return false;
            }
            ClassOrInterfaceDeclaration clazz = (ClassOrInterfaceDeclaration) type;
            if (clazz.isInterface()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return all top-level, non-interface classes in declaration order.
     */
    private List<ClassOrInterfaceDeclaration> getTopLevelClasses(CompilationUnit cu) {
        return cu.getTypes().stream()
            .filter(type -> type instanceof ClassOrInterfaceDeclaration)
            .map(type -> (ClassOrInterfaceDeclaration) type)
            .filter(type -> !type.isInterface())
            .toList();
    }

    /**
     * Ensure the class declaration is public.
     */
    private void ensurePublic(ClassOrInterfaceDeclaration clazz) {
        if (!clazz.isPublic()) {
            clazz.addModifier(Modifier.Keyword.PUBLIC);
        }
    }

    /**
     * Promote private/protected fields in a class to public.
     */
    private void updateFieldModifiers(ClassOrInterfaceDeclaration clazz) {
        for (FieldDeclaration field : clazz.getFields()) {
            if (field.isPrivate() || field.isProtected()) {
                field.removeModifier(Modifier.Keyword.PRIVATE);
                field.removeModifier(Modifier.Keyword.PROTECTED);
                field.addModifier(Modifier.Keyword.PUBLIC);
            }
        }
    }

    /**
     * Collect a summary of field modifier changes for reporting.
     */
    private List<FieldChangePlan> collectFieldChanges(ClassOrInterfaceDeclaration clazz) {
        List<FieldChangePlan> changes = new ArrayList<>();
        for (FieldDeclaration field : clazz.getFields()) {
            if (field.isPrivate() || field.isProtected()) {
                String from = field.isPrivate() ? "private" : "protected";
                List<String> names = field.getVariables().stream()
                    .map(variable -> variable.getNameAsString())
                    .toList();
                changes.add(new FieldChangePlan(from, names));
            }
        }
        return changes;
    }

    /**
     * Build the output file list and content for a single source file.
     */
    private List<OutputFilePreview> buildOutputs(JavaParser parser, Path inputRoot, Path sourceFile) {
        ParseResult<CompilationUnit> result;
        try {
            log.debug("Processing {}", sourceFile);
            result = parser.parse(sourceFile);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        if (result.getResult().isEmpty()) {
            log.warn("Parse failed: {}", sourceFile);
            result.getProblems().forEach(problem -> log.warn("  {}", problem.getMessage()));
            return List.of();
        }
        if (!result.getProblems().isEmpty()) {
            log.debug("Parse warnings for {}: {}", sourceFile, result.getProblems());
        }

        CompilationUnit cu = result.getResult().get();
        List<ClassOrInterfaceDeclaration> topLevelClasses = getTopLevelClasses(cu);
        boolean hasPublicClass = topLevelClasses.stream().anyMatch(ClassOrInterfaceDeclaration::isPublic);
        boolean onlyClasses = isOnlyTopLevelClasses(cu);

        // Apply field modifier changes in-place before any splitting logic.
        for (ClassOrInterfaceDeclaration clazz : topLevelClasses) {
            updateFieldModifiers(clazz);
        }

        Path relative = inputRoot.relativize(sourceFile);
        Path relativeParent = relative.getParent();
        List<OutputFilePreview> outputs = new ArrayList<>();

        // Case 1: no public class, only classes -> split all classes into separate files.
        if (!hasPublicClass && onlyClasses && !topLevelClasses.isEmpty()) {
            for (ClassOrInterfaceDeclaration clazz : topLevelClasses) {
                ensurePublic(clazz);
            }
            for (ClassOrInterfaceDeclaration clazz : topLevelClasses) {
                CompilationUnit newCu = buildSplitUnit(cu, clazz);
                Path outputRelative = relativeParent == null
                    ? Path.of(clazz.getNameAsString() + ".java")
                    : relativeParent.resolve(clazz.getNameAsString() + ".java");
                outputs.add(new OutputFilePreview(outputRelative, newCu.toString()));
            }
            return outputs;
        }

        // Case 2: has a public class and additional top-level classes -> move non-primary classes.
        if (hasPublicClass && topLevelClasses.size() > 1) {
            ClassOrInterfaceDeclaration primary = topLevelClasses.stream()
                .filter(ClassOrInterfaceDeclaration::isPublic)
                .findFirst()
                .orElse(topLevelClasses.get(0));
            ensurePublic(primary);

            List<ClassOrInterfaceDeclaration> toMove = topLevelClasses.stream()
                .filter(clazz -> clazz != primary)
                .toList();
            for (ClassOrInterfaceDeclaration clazz : toMove) {
                ensurePublic(clazz);
            }
            for (ClassOrInterfaceDeclaration clazz : toMove) {
                CompilationUnit newCu = buildSplitUnit(cu, clazz);
                Path outputRelative = relativeParent == null
                    ? Path.of(clazz.getNameAsString() + ".java")
                    : relativeParent.resolve(clazz.getNameAsString() + ".java");
                outputs.add(new OutputFilePreview(outputRelative, newCu.toString()));
            }
            for (ClassOrInterfaceDeclaration clazz : toMove) {
                clazz.remove();
            }
            outputs.add(new OutputFilePreview(relative, cu.toString()));
            return outputs;
        }

        // Case 3: single class or no split needed -> keep file, ensure class is public.
        for (ClassOrInterfaceDeclaration clazz : topLevelClasses) {
            ensurePublic(clazz);
        }
        outputs.add(new OutputFilePreview(relative, cu.toString()));
        return outputs;
    }

    /**
     * Create a new compilation unit containing only the given class and cloned imports/package.
     */
    private CompilationUnit buildSplitUnit(CompilationUnit originalCu, ClassOrInterfaceDeclaration clazz) {
        CompilationUnit newCu = new CompilationUnit();
        originalCu.getPackageDeclaration().ifPresent(pkg -> newCu.setPackageDeclaration(pkg.clone()));
        for (ImportDeclaration importDecl : originalCu.getImports()) {
            newCu.addImport(importDecl.clone());
        }
        newCu.addType(clazz.clone());
        return newCu;
    }

    /**
     * Write each output file to disk, optionally skipping existing files.
     */
    private void writeOutputs(
        List<OutputFilePreview> outputs,
        Path outputRoot,
        OutputConflictStrategy conflictStrategy
    ) throws IOException {
        for (OutputFilePreview output : outputs) {
            Path outputFile = outputRoot.resolve(output.getRelativePath());
            if (Files.exists(outputFile) && conflictStrategy == OutputConflictStrategy.SKIP_EXISTING) {
                log.info("Skipping existing output: {}", outputFile);
                continue;
            }
            Path parent = outputFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputFile, output.getContent(), StandardCharsets.UTF_8);
        }
    }
}
