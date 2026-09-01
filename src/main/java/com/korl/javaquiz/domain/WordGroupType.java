package com.korl.javaquiz.domain;

public enum WordGroupType {

    /** Belongs to one learner, who is the only one who sees it. */
    PERSONAL,

    /** Shared vocabulary: everybody reads it, admins curate it. */
    PUBLIC
}
