package com.korl.javaquiz.practice;

import java.util.List;

/**
 * The shape of a dataset, read back from the sandbox rather than written by hand, so what a
 * learner sees is always what the tables actually are.
 */
public record SchemaInfo(List<Table> tables) {

    public record Table(String name, List<Column> columns) {
    }

    public record Column(String name, String type, boolean nullable) {
    }
}
