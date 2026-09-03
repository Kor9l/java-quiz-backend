package com.korl.javaquiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.korl.javaquiz.domain.LearningModule;
import com.korl.javaquiz.domain.Level;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The start body carries the module in the spelling the rest of the API uses — {@code english},
 * the same word that goes into {@code ?module=english} and comes back as a module id. Jackson
 * matches enum constants exactly unless told otherwise, so without a creator on the enum the
 * grammar round died on a bodyless 400 before any of this class's code ran.
 */
class QuizStartRequestJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void theModuleIsReadInTheSpellingTheUiSends() throws Exception {
        QuizService.QuizStartRequest request = mapper.readValue(
                """
                {"module":"english","topicIds":["word-order-basics"],"sectionId":"basic-order",
                 "targetCount":10,"infinite":false}
                """,
                QuizService.QuizStartRequest.class);

        assertThat(request.module).isEqualTo(LearningModule.ENGLISH);
        assertThat(request.topicIds).isEqualTo(List.of("word-order-basics"));
        assertThat(request.sectionId).isEqualTo("basic-order");
        assertThat(request.targetCount).isEqualTo(10);
    }

    /** The column spelling has to keep working: it is what {@code /api/quiz/setup} hands back. */
    @Test
    void theColumnSpellingStillWorks() throws Exception {
        assertThat(mapper.readValue("{\"module\":\"ENGLISH\"}", QuizService.QuizStartRequest.class).module)
                .isEqualTo(LearningModule.ENGLISH);
        assertThat(mapper.readValue("{\"module\":\"BACKEND\"}", QuizService.QuizStartRequest.class).module)
                .isEqualTo(LearningModule.BACKEND);
    }

    /** Left out means backend, which is what every body written before grammar existed meant. */
    @Test
    void anAbsentModuleStaysNull() throws Exception {
        assertThat(mapper.readValue("{}", QuizService.QuizStartRequest.class).module).isNull();
    }

    /** Levels travel in the column spelling, and both ladders have to come through. */
    @Test
    void levelsAreReadTheSameWay() throws Exception {
        assertThat(mapper.readValue("{\"level\":\"BASE\"}", QuizService.QuizStartRequest.class).level)
                .isEqualTo(Level.BASE);
        assertThat(mapper.readValue("{\"level\":\"MIDDLE\"}", QuizService.QuizStartRequest.class).level)
                .isEqualTo(Level.MIDDLE);
    }
}
