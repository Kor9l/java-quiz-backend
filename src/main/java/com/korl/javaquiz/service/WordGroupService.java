package com.korl.javaquiz.service;

import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.domain.Role;
import com.korl.javaquiz.domain.WordGroup;
import com.korl.javaquiz.domain.WordGroupRepository;
import com.korl.javaquiz.domain.WordGroupType;
import com.korl.javaquiz.security.UserPrincipal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response.Status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Word groups and, with them, the whole access story of the English module. Reading is wide —
 * the shared vocabulary plus your own — and writing is narrow: your own groups, or the shared
 * ones if you are an admin.
 *
 * <p>A group nobody may see answers 404 rather than 403: telling an outsider that someone
 * else's group exists is already more than they should learn.
 */
@ApplicationScoped
public class WordGroupService {

    private final WordGroupRepository groups;

    public WordGroupService(WordGroupRepository groups) {
        this.groups = groups;
    }

    @Transactional
    public List<WordGroup> listAccessible(UUID userId) {
        return groups.findAccessible(userId);
    }

    @Transactional
    public WordGroup accessible(UUID userId, UUID groupId) {
        return groups.findAccessibleById(userId, groupId)
                .orElseThrow(() -> new ApiException(Status.NOT_FOUND, "Word group not found"));
    }

    /** The same lookup, but refusing to hand back a group the caller may only read. */
    @Transactional
    public WordGroup editable(UserPrincipal user, UUID groupId) {
        WordGroup group = accessible(user.getId(), groupId);
        if (!canEdit(user, group)) {
            throw new ApiException(Status.FORBIDDEN, "Editing this group is not allowed");
        }
        return group;
    }

    public boolean canEdit(UserPrincipal user, WordGroup group) {
        if (group.getGroupType() == WordGroupType.PERSONAL) {
            return user.getId().equals(group.getOwnerId());
        }
        return user.getRole() == Role.ADMIN;
    }

    @Transactional
    public WordGroup create(UUID ownerId, String title) {
        String trimmed = requireTitle(title);
        return groups.save(WordGroup.personal(ownerId, trimmed, groups.nextSortOrder(), Instant.now()));
    }

    @Transactional
    public WordGroup rename(UserPrincipal user, UUID groupId, String title) {
        WordGroup group = editable(user, groupId);
        group.setTitle(requireTitle(title));
        return groups.save(group);
    }

    /** Cascades to the group's words and to anyone's favourites among them. */
    @Transactional
    public void delete(UserPrincipal user, UUID groupId) {
        groups.delete(editable(user, groupId));
    }

    private static String requireTitle(String title) {
        String trimmed = title == null ? "" : title.strip();
        if (trimmed.isEmpty()) {
            throw new ApiException(Status.BAD_REQUEST, "Group title must not be empty");
        }
        return trimmed;
    }
}
