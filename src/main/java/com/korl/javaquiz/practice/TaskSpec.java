package com.korl.javaquiz.practice;

import java.util.List;

/**
 * Everything the engine needs to grade one task, free of persistence concerns so that the
 * grading logic can be exercised without a database behind it.
 *
 * @param id               identifies the task, used as the expected-result cache key
 * @param setupStatements  DDL and seed data building the dataset, run in order
 * @param solutionSql      reference solution; its result set defines the right answer
 * @param orderMatters     whether the task asked for rows in a specific order
 */
public record TaskSpec(String id, List<String> setupStatements, String solutionSql, boolean orderMatters) {
}
