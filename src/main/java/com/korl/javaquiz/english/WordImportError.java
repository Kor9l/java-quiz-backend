package com.korl.javaquiz.english;

/**
 * Why one line of a bulk import could not be used.
 *
 * <p>A code rather than a sentence, unlike the validation messages elsewhere in this API. Those
 * are read by whoever is calling the endpoint; these reach a learner mid-paste, on a screen the
 * UI has already translated around them. Only the UI knows which language that is, so the
 * wording belongs there and the reason travels as a name.
 */
public enum WordImportError {

    /** No em or en dash, so there is no telling where the English stops. */
    MISSING_SEPARATOR,

    /** A dash with nothing on one side of it. */
    EMPTY_SIDE,

    /** A typed row carrying neither a word nor its translation. */
    MISSING_FIELDS
}
