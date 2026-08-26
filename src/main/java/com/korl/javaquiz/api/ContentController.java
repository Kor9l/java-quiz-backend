package com.korl.javaquiz.api;

import com.korl.javaquiz.security.UserPrincipal;
import com.korl.javaquiz.service.ContentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping("/topics")
    public List<Map<String, Object>> topics(@AuthenticationPrincipal UserPrincipal principal) {
        return contentService.listTopics(principal.getId());
    }

    @GetMapping("/materials/{topicId}/{sectionId}")
    public Map<String, Object> material(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String topicId,
            @PathVariable String sectionId) {
        return contentService.material(principal.getId(), topicId, sectionId);
    }
}
