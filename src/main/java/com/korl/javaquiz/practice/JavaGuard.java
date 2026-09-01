package com.korl.javaquiz.practice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cheap static policy applied to submitted source before it reaches the compiler.
 *
 * <p>This is the outermost of three rings, and the only one that reads Java as text. It exists
 * for the message rather than for the protection: {@code import java.io.File} is far better
 * answered with "that package is not available here" than with a verifier error thrown three
 * stages later. {@link ClassFileGuard} and {@link SandboxClassLoader} are what actually hold
 * the line, and they work on compiled bytecode, where a comment cannot hide anything and a
 * fully-qualified name cannot dodge an import check.
 */
public final class JavaGuard {

    private static final Pattern PACKAGE_DECLARATION =
            Pattern.compile("(?m)^\\s*package\\s+[\\w.]+\\s*;");

    private static final Pattern IMPORT_DECLARATION =
            Pattern.compile("(?m)^\\s*import\\s+(static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;");

    private JavaGuard() {
    }

    /**
     * @param source    what the learner submitted
     * @param className the top-level type the task expects them to write
     */
    public static void check(String source, String className, int maxLength) {
        if (source == null || source.isBlank()) {
            throw new PracticeSubmissionException(SubmissionStatus.POLICY_ERROR, "practice.error.empty", null);
        }
        if (source.length() > maxLength) {
            throw new PracticeSubmissionException(
                    SubmissionStatus.POLICY_ERROR, "practice.error.tooLong", "limit=" + maxLength);
        }
        if (PACKAGE_DECLARATION.matcher(source).find()) {
            // The task's class is compiled into the default package and looked up there by
            // name, so a package declaration would hide it rather than fail to compile.
            throw new PracticeSubmissionException(
                    SubmissionStatus.POLICY_ERROR, "practice.error.packageDeclaration", null);
        }
        checkImports(source);
        checkDeclaresRequiredType(source, className);
    }

    private static void checkImports(String source) {
        Matcher matcher = IMPORT_DECLARATION.matcher(source);
        while (matcher.find()) {
            if (!SandboxPolicy.allowsClass(importedType(matcher.group(1) != null, matcher.group(2)))) {
                throw new PracticeSubmissionException(
                        SubmissionStatus.POLICY_ERROR, "practice.error.forbiddenImport", matcher.group(2));
            }
        }
    }

    /**
     * The type an import form comes down to, so all four forms can be checked as one.
     *
     * <p>What the last segment means depends on the form: a member for a static import, a
     * package for a wildcard one, a type for a static wildcard. Only a plain single-type
     * import already names the type. A package is turned into a type by hanging an arbitrary
     * name off it, since the policy is expressed in classes.
     */
    private static String importedType(boolean isStatic, String imported) {
        if (imported.endsWith(".*")) {
            String prefix = imported.substring(0, imported.length() - 2);
            return isStatic ? prefix : prefix + ".Any";
        }
        return isStatic ? dropLastSegment(imported) : imported;
    }

    /**
     * A submission that compiles but declares something else entirely would fail later on with
     * a missing class, and the learner would have no idea why.
     */
    private static void checkDeclaresRequiredType(String source, String className) {
        Pattern declaration = Pattern.compile(
                "\\b(class|interface|enum|record)\\s+" + Pattern.quote(className) + "\\b");
        if (!declaration.matcher(source).find()) {
            throw new PracticeSubmissionException(
                    SubmissionStatus.POLICY_ERROR, "practice.error.missingClass", className);
        }
    }

    private static String dropLastSegment(String name) {
        int lastDot = name.lastIndexOf('.');
        return lastDot < 0 ? name : name.substring(0, lastDot);
    }
}
