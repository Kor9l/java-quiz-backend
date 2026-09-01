package com.korl.javaquiz.api;

import com.korl.javaquiz.api.dto.WordGroupRequest;
import com.korl.javaquiz.api.dto.WordImportRequest;
import com.korl.javaquiz.api.dto.WordRequest;
import com.korl.javaquiz.security.CurrentUser;
import com.korl.javaquiz.service.WordGroupService;
import com.korl.javaquiz.service.WordService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The English module: the vocabulary, and everything needed to add to it and tidy it up. */
@Path("/api/english")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class EnglishResource {

    private final WordService wordService;
    private final WordGroupService wordGroupService;
    private final CurrentUser currentUser;

    public EnglishResource(WordService wordService, WordGroupService wordGroupService, CurrentUser currentUser) {
        this.wordService = wordService;
        this.wordGroupService = wordGroupService;
        this.currentUser = currentUser;
    }

    /** Group headers only, for the screen that lists them. */
    @GET
    @Path("/groups")
    public List<Map<String, Object>> groups() {
        return wordService.listGroups(currentUser.principal());
    }

    /** The whole vocabulary, already grouped: one call behind the word list. */
    @GET
    @Path("/words")
    public List<Map<String, Object>> words() {
        return wordService.listWordsByGroup(currentUser.principal());
    }

    @GET
    @Path("/groups/{groupId}")
    public Map<String, Object> group(@PathParam("groupId") UUID groupId) {
        return wordService.group(currentUser.principal(), groupId);
    }

    @POST
    @Path("/groups")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> createGroup(@Valid WordGroupRequest request) {
        UUID id = wordGroupService.create(currentUser.id(), request.title).getId();
        return wordService.group(currentUser.principal(), id);
    }

    @PATCH
    @Path("/groups/{groupId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> renameGroup(@PathParam("groupId") UUID groupId, @Valid WordGroupRequest request) {
        wordGroupService.rename(currentUser.principal(), groupId, request.title);
        return wordService.group(currentUser.principal(), groupId);
    }

    /** Takes the group's words with it. */
    @DELETE
    @Path("/groups/{groupId}")
    public void deleteGroup(@PathParam("groupId") UUID groupId) {
        wordGroupService.delete(currentUser.principal(), groupId);
    }

    @POST
    @Path("/groups/{groupId}/words")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> createWord(@PathParam("groupId") UUID groupId, @Valid WordRequest request) {
        return wordService.create(currentUser.principal(), groupId, request);
    }

    @PUT
    @Path("/words/{wordId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> updateWord(@PathParam("wordId") UUID wordId, @Valid WordRequest request) {
        return wordService.update(currentUser.principal(), wordId, request);
    }

    @DELETE
    @Path("/words/{wordId}")
    public void deleteWord(@PathParam("wordId") UUID wordId) {
        wordService.delete(currentUser.principal(), wordId);
    }

    /** A pasted list or a typed grid, into an existing group or a new one. */
    @POST
    @Path("/words/import")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> importWords(WordImportRequest request) {
        return wordService.importWords(currentUser.principal(), request);
    }

    /** Starred per learner, so it works on shared groups too. */
    @POST
    @Path("/words/{wordId}/favorite")
    public Map<String, Object> toggleFavorite(@PathParam("wordId") UUID wordId) {
        return wordService.toggleFavorite(currentUser.id(), wordId);
    }
}
