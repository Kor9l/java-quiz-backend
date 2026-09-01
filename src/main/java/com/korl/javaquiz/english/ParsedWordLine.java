package com.korl.javaquiz.english;

/** One line of a pasted vocabulary list, split into its parts. */
public record ParsedWordLine(String text, String translation, boolean markedNew) {
}
