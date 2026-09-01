package com.korl.javaquiz.practice;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * One compiler message, reduced to what an editor needs to underline it.
 *
 * <p>A submission is compiled as-is rather than wrapped in generated scaffolding, precisely so
 * that line 7 of a diagnostic is line 7 of the learner's editor. The generated harness is a
 * separate compilation unit, and an error in <em>it</em> means the submission does not have the
 * shape the task calls for — a different thing to be told, and not something to underline at a
 * line number the learner cannot see. {@link #inSubmission()} is which of the two this is, and
 * a diagnostic that is not in the submission carries no position at all.
 *
 * @param severity     {@code ERROR} or {@code WARNING}
 * @param line         one-based line, 0 when the message is not tied to a place the learner has
 * @param column       one-based column, 0 on the same terms
 * @param message      the compiler's own wording, which is the useful part for a learner
 * @param inSubmission whether it is about the submitted source rather than the harness
 */
public record CompileDiagnostic(String severity, long line, long column, String message, boolean inSubmission) {

    static CompileDiagnostic from(Diagnostic<? extends JavaFileObject> diagnostic, String submissionFile) {
        boolean inSubmission = diagnostic.getSource() != null
                && submissionFile.equals(diagnostic.getSource().getName());
        return new CompileDiagnostic(
                diagnostic.getKind() == Diagnostic.Kind.ERROR ? "ERROR" : "WARNING",
                inSubmission ? Math.max(diagnostic.getLineNumber(), 0) : 0,
                inSubmission ? Math.max(diagnostic.getColumnNumber(), 0) : 0,
                clean(diagnostic.getMessage(null)),
                inSubmission);
    }

    public boolean isError() {
        return "ERROR".equals(severity);
    }

    /**
     * Strips the leading file/line banner ECJ puts in front of the wording. The position is
     * already carried in its own fields, and repeating it inside the text reads as noise.
     */
    private static String clean(String message) {
        if (message == null) {
            return "";
        }
        return message.replaceAll("(?m)^.*\\.java:\\d+:\\s*", "").trim();
    }
}
