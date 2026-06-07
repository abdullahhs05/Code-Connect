package com.codeconnect.dao;

import com.codeconnect.db.DatabaseHelper;
import com.codeconnect.model.CodeSnippet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeSnippetDAOTest {

    private static final CodeSnippetDAO snippetDAO = new CodeSnippetDAO();
    private static final UserDAO userDAO = new UserDAO();

    @BeforeAll
    static void init() {
        DatabaseHelper.initializeDatabase();
    }

    @Test
    void addFindHideAndDelete() {
        Integer adminId = userDAO.findIdByUsername("admin");
        assertNotNull(adminId, "admin user must be seeded");

        String uniqueTitle = "JUnit snippet " + System.currentTimeMillis();
        CodeSnippet s = new CodeSnippet(0, uniqueTitle, "Java", "class X {}", "test desc", adminId);
        assertTrue(snippetDAO.addSnippet(s));
        assertTrue(s.getId() > 0);

        List<CodeSnippet> visible = snippetDAO.findAllWithDetails(adminId);
        assertTrue(visible.stream().anyMatch(x -> x.getId() == s.getId()),
                "new snippet should appear in default listing");

        // Hide it and ensure it disappears from default view but remains when includeHidden=true
        assertTrue(snippetDAO.setHidden(s.getId(), true));
        List<CodeSnippet> afterHide = snippetDAO.findAllWithDetails(adminId);
        assertFalse(afterHide.stream().anyMatch(x -> x.getId() == s.getId()),
                "hidden snippet should not be returned by default");
        List<CodeSnippet> withHidden = snippetDAO.findAllWithDetails(adminId, true);
        assertTrue(withHidden.stream().anyMatch(x -> x.getId() == s.getId() && x.isHidden()));

        // Clean up
        assertTrue(snippetDAO.deleteSnippet(s.getId()));
    }
}
