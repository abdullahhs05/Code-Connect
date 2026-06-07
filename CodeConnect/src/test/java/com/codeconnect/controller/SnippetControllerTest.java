package com.codeconnect.controller;

import com.codeconnect.db.DatabaseHelper;
import com.codeconnect.model.CodeSnippet;
import com.codeconnect.model.Session;
import com.codeconnect.model.User;
import com.codeconnect.dao.UserDAO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SnippetControllerTest {

    private static User testUser;
    private static User adminUser;

    @BeforeAll
    static void setup() {
        DatabaseHelper.initializeDatabase();
        UserDAO userDAO = new UserDAO();
        testUser = userDAO.findByUsername("dev_test_user");
        if (testUser == null) {
            userDAO.register("dev_test_user", "hashed_pw", "Developer", "dev@test.local");
            testUser = userDAO.findByUsername("dev_test_user");
        }
        adminUser = userDAO.findByUsername("admin");
        if (adminUser == null) {
            userDAO.register("admin", "admin_pw", "Admin", "admin@test.local");
            adminUser = userDAO.findByUsername("admin");
        }
    }

    @BeforeEach
    void clearSession() {
        Session.setCurrentUser(null);
    }

    @Test
    void testValidateData() {
        SnippetController controller = new SnippetController();
        SnippetController.OpResult res = controller.validateData("My Snippet", "Java", "public class X {}");
        assertTrue(res.success);

        res = controller.validateData("", "Java", "public class X {}");
        assertFalse(res.success);
        assertEquals("Title is required.", res.message);

        res = controller.validateData("My Snippet", null, "public class X {}");
        assertFalse(res.success);
        assertEquals("Language is required.", res.message);

        res = controller.validateData("My Snippet", "Java", "");
        assertFalse(res.success);
        assertEquals("Code body is empty.", res.message);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 256 * 1024 + 10; i++) {
            sb.append("a");
        }
        res = controller.validateData("My Snippet", "Java", sb.toString());
        assertFalse(res.success);
        assertEquals("Code is too large (>256 KB).", res.message);
    }

    @Test
    void testUploadRequiresLogin() {
        SnippetController controller = new SnippetController();
        SnippetController.OpResult res = controller.handleUpload("Title", "Java", "code", "desc", "tags");
        assertFalse(res.success);
        assertEquals("You must be logged in to upload.", res.message);
    }

    @Test
    void testUploadAndRetrieveSnippet() {
        Session.setCurrentUser(testUser);
        SnippetController controller = new SnippetController();
        String title = "Upload Test Snippet " + System.currentTimeMillis();
        SnippetController.OpResult res = controller.handleUpload(title, "Java", "public class A {}", "some description", "java,test");
        assertTrue(res.success);
        assertNotNull(res.snippet);
        assertTrue(res.snippet.getId() > 0);

        CodeSnippet retrieved = controller.requestSnippetData(res.snippet.getId());
        assertNotNull(retrieved);
        assertEquals(title, retrieved.getTitle());
        assertEquals("Java", retrieved.getLanguage());
        assertEquals("public class A {}", retrieved.getCode());
    }

    @Test
    void testExecuteSearch() {
        Session.setCurrentUser(testUser);
        SnippetController controller = new SnippetController();
        String uniqueTag = "uniqueTag_" + System.currentTimeMillis();
        SnippetController.OpResult res = controller.handleUpload("Search Snippet", "Python", "print('hello')", "desc", uniqueTag);
        assertTrue(res.success);

        List<CodeSnippet> list = controller.executeSearch(uniqueTag, null);
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(s -> s.getId() == res.snippet.getId()));

        List<CodeSnippet> listPy = controller.executeSearch(null, "Python");
        assertFalse(listPy.isEmpty());
        assertTrue(listPy.stream().anyMatch(s -> s.getId() == res.snippet.getId()));

        List<CodeSnippet> listCpp = controller.executeSearch(uniqueTag, "C++");
        assertTrue(listCpp.isEmpty());
    }

    @Test
    void testApplyModeration() {
        Session.setCurrentUser(testUser);
        SnippetController controller = new SnippetController();
        SnippetController.OpResult uploadRes = controller.handleUpload("Mod Test", "Java", "code", "desc", "tags");
        assertTrue(uploadRes.success);
        int snippetId = uploadRes.snippet.getId();

        SnippetController.OpResult modRes = controller.applyModeration(snippetId, "HIDE");
        assertFalse(modRes.success);
        assertEquals("Only administrators can moderate content.", modRes.message);

        Session.setCurrentUser(adminUser);
        modRes = controller.applyModeration(snippetId, "HIDE");
        assertTrue(modRes.success);

        Session.setCurrentUser(testUser);
        List<CodeSnippet> searchList = controller.executeSearch("Mod Test", null);
        assertFalse(searchList.stream().anyMatch(s -> s.getId() == snippetId));

        Session.setCurrentUser(adminUser);
        modRes = controller.applyModeration(snippetId, "UNHIDE");
        assertTrue(modRes.success);

        modRes = controller.applyModeration(snippetId, "DELETE");
        assertTrue(modRes.success);
        assertNull(controller.requestSnippetData(snippetId));
    }
}
