package com.korl.javaquiz.practice;

import java.util.List;

/**
 * Everything the engine needs to grade one Java task, free of persistence concerns so that the
 * grading logic can be exercised without a database behind it.
 *
 * <p>The Java counterpart of {@link TaskSpec}: where a SQL task is a dataset plus a reference
 * query, a Java task is a class the learner writes plus the calls that are made against it.
 * Both are graded the same way — run the reference, run the submission, compare.
 *
 * @param id           identifies the task, used as the expected-result cache key
 * @param className    the top-level class the submission has to declare
 * @param solutionCode reference solution; what it returns for each case is the right answer
 * @param cases        the calls made against the class, in the order they are reported
 */
public record JavaTaskSpec(String id, String className, String solutionCode, List<Case> cases) {

    /**
     * One call against the learner's class.
     *
     * @param label      what the learner sees this call named as
     * @param expression Java expression compiled into the harness; must produce a value
     */
    public record Case(String label, String expression) {
    }
}
