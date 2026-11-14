package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.PartLibrary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelPerKindTest {

    @TempDir
    Path temp;
    private String previousHome;

    @BeforeEach
    void useAnEmptyHome() {
        previousHome = System.getProperty("user.home");
        System.setProperty("user.home", temp.resolve("home").toString());
    }

    @AfterEach
    void restoreHome() {
        System.setProperty("user.home", previousHome);
    }

    @Test
    void aKindCanHaveItsOwnModelAndGoBackToTheGeneralOne() throws Exception {
        assertNull(AiSetup.modelFor(PartLibrary.Kind.VISITOR));

        AiSetup.saveModelFor(PartLibrary.Kind.VISITOR, "openai/gpt-oss-120b");
        AiSetup.saveRequestOptions(4096, "low"); // other settings share the file
        assertEquals("openai/gpt-oss-120b", AiSetup.modelFor(PartLibrary.Kind.VISITOR));
        assertNull(AiSetup.modelFor(PartLibrary.Kind.BLOSSOM_TREE));
        assertEquals(4096, AiSetup.maxTokens());

        AiSetup.saveModelFor(PartLibrary.Kind.VISITOR, null);
        assertNull(AiSetup.modelFor(PartLibrary.Kind.VISITOR));
    }
}
