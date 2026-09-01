package com.korl.javaquiz.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * A bulk add. Two shapes reach the same place: {@code TEXT} is a vocabulary list pasted whole,
 * {@code TABLE} is rows typed into a grid. Either goes into an existing group ({@link #groupId})
 * or into a new personal one ({@link #newGroupTitle}).
 */
public class WordImportRequest {

    public String mode;

    public UUID groupId;

    public String newGroupTitle;

    /** TEXT mode: the pasted list, one word per line. */
    public String text;

    /** TABLE mode: one entry per row. */
    public List<Row> rows;

    public static class Row {

        public String text;

        public String translation;

        public String example;
    }
}
