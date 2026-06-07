# Traceability Matrix — Deliverables 3, 4, 5 → Source

This document is the line-by-line mapping a grader can use to verify that
every artifact from Deliverables 3-5 is implemented in this codebase. All
file paths are relative to the project root.

---

## 1. Design Class Diagram (Deliverable 5) → Source

| DCD Class | Type | File | Notes |
|---|---|---|---|
| `Account` (abstract) | Abstract base | `src/main/java/com/codeconnect/model/Account.java` | id, username, password; `login()`, `logout()`, `verifyPassword(String) : boolean`. Information Expert. |
| `Developer` | Concrete subclass | `src/main/java/com/codeconnect/model/Developer.java` | extends `User extends Account`. Adds `bio`. |
| `Admin` | Concrete subclass | `src/main/java/com/codeconnect/model/Admin.java` | extends `User extends Account`. Adds `adminLevel`. |
| `User` | Bridge concrete | `src/main/java/com/codeconnect/model/User.java` | extends `Account`. Holds `role`, `email`, `disabled` for legacy column-based persistence. |
| `CodeSnippet` | Domain entity | `src/main/java/com/codeconnect/model/CodeSnippet.java` | Owns `formatSyntax() : String` (Information Expert) at line ~70. |
| `DiscussionRoom` | Domain entity | `src/main/java/com/codeconnect/model/DiscussionRoom.java` | Owns `createMessage(content, userID) : Message` (Creator) and `saveRoom() : void`, `leaveRoom() : void` (Information Expert). |
| `Message` | Domain entity | `src/main/java/com/codeconnect/model/Message.java` | id, content, roomId, senderId, timestamp. |
| `AuthController` | Pure Fabrication | `src/main/java/com/codeconnect/controller/AuthController.java` | `handleRegister`, `authenticateUser`, `createSession`, `requestLogout`. |
| `SnippetController` | Pure Fabrication | `src/main/java/com/codeconnect/controller/SnippetController.java` | `handleUpload`, `validateData`, `requestSnippetData`, `executeSearch`, `applyModeration`. |
| `DiscussionController` | Pure Fabrication | `src/main/java/com/codeconnect/controller/DiscussionController.java` | `createRoom`, `requestJoinRoom`, `handleNewMessage`, `requestHistory`. |
| `DatabaseManager` | Pure Fabrication | `src/main/java/com/codeconnect/db/DatabaseManager.java` | Facade — delegates to `DatabaseHelper` for backend selection (SQLite/MySQL), DDL, and migrations. |
| `SocketServer` | Pure Fabrication | `src/main/java/com/codeconnect/net/SocketServer.java` | Facade — delegates to `LocalEventBus` (Java `ServerSocket`/`Socket`, LAN-aware). Provides `verifyPermissions`, `connectUser`, `broadcastMessage`. |

### Relationships

| Relation | Multiplicity | Where enforced |
|---|---|---|
| `DiscussionRoom` *composition* `Message` | 1 — 1..* | FK `messages.room_id` with `ON DELETE CASCADE` (DDL in `DatabaseHelper.initializeDatabase`). |
| `DiscussionRoom` *aggregation* `CodeSnippet` | 0..1 — 1 | FK `discussion_rooms.snippet_id`. Snippet survives if room is deleted. |
| `Developer` *uploads* `CodeSnippet` | 1 — 0..* | FK `code_snippets.uploader_id`. Query in `CodeSnippetDAO.findAllWithDetails`. |
| `User` *joins* `DiscussionRoom` | 0..* — 0..* | Junction table `room_members` (DDL in `DatabaseHelper`). |
| `Admin` *manages* `User` | 1 — 0..* | `AdminView` UI calls `UserDAO.setRole`, `UserDAO.setDisabled`. |
| `Admin` *moderates* `CodeSnippet` | 1 — 0..* | `AdminView` calls `SnippetController.applyModeration`. |

---

## 2. Use Cases (Deliverable 3) → Source

### UC-1 Register Account

| Step | DCD/UC element | Source |
|---|---|---|
| User clicks Register | `RegisterUI.clickRegister()` | `AuthView.buildRegisterPane` |
| Submit | `AuthController.handleRegister(...)` | `AuthController.java:62` |
| Duplicate check | `userDAO.findByUsername` | `UserDAO.java:71` |
| Hash + persist | `BCrypt.hashpw` + `userDAO.register` | `AuthController.java:76-78` |
| Extension 5a (duplicate) | Returns `success=false, message="Username is already taken."` | `AuthController.java:69` |

### UC-2 Login

| Step | UC element | Source |
|---|---|---|
| `LoginUI.enterCredentials/clickLogin` | `AuthView.buildLoginPane` `loginBtn.setOnAction` | `AuthView.java:98-110` |
| `AuthController.authenticateUser` | | `AuthController.java:83` |
| `DatabaseManager.fetchUserRecord` | `userDAO.findByUsername` | `UserDAO.java:71` |
| `Account.verifyPassword(password)` | Information Expert on `Account` | `Account.java:54` |
| `AuthController.createSession` | Updates `Session.setCurrentUser` | `AuthController.java:114` |
| `loadMainDashboard` | `MainWindow` constructed via `App.onLoginSuccess` | `App.java`, `MainWindow.java` |
| Extension 4b (disabled) | Returns "Account is disabled…" | `AuthController.java:91` |

### UC-3 Upload Code Snippet

| Step | UC element | Source |
|---|---|---|
| `UploadUI.selectUploadSnippet/showUploadForm` | `UploadView` constructor | `UploadView.java` |
| `SnippetController.handleUpload` | | `SnippetController.java:55` |
| `SnippetController.validateData` | | `SnippetController.java:79` |
| `new CodeSnippet(...)` | Information Expert ctor | `SnippetController.java:67` |
| `DatabaseManager.saveSnippet` | `snippetDAO.addSnippet` | `CodeSnippetDAO.java:17` |
| Extension 4a (too large) | 256 KB cap | `SnippetController.java:96` |

### UC-4 View Code Snippet with Discussion

| Step | UC element | Source |
|---|---|---|
| `ViewUI.selectSnippet` | DashboardView snippet click | `DashboardView.java` |
| `SnippetController.requestSnippetData` | | `SnippetController.java:111` |
| `DatabaseManager.getSnippet` | `snippetDAO.findById` | `CodeSnippetDAO.java:90` |
| `DatabaseManager.getAssociatedMessages` | `messageDAO.getMessagesForRoom` | `MessageDAO.java:39` |
| `CodeSnippet.formatSyntax()` | Information Expert | `CodeSnippet.java:70` |
| `displayCodeAndChat` | `DiscussionWindow` | `DiscussionWindow.java` |

### UC-5 Create Discussion Room

| Step | UC element | Source |
|---|---|---|
| `RoomUI.clickCreateRoom/promptRoomName/submitRoomName` | `MainWindow.openDiscussion` | `MainWindow.java` |
| `DiscussionController.createRoom` | | `DiscussionController.java:62` |
| `DatabaseManager.checkRoomExists` + create | `roomDAO.getOrCreateRoom` | `DiscussionRoomDAO.java:20` |
| `new DiscussionRoom(...)` | Mapped from DB row inside `getOrCreateRoom` | same |
| `roomDAO.createRoom` (raw insert) | available for direct creation | `DiscussionRoomDAO.java:69` |
| `openDiscussionView` | New stage opens `DiscussionWindow` | `DiscussionWindow.java` |

### UC-6 Join Existing Discussion

| Step | UC element | Source |
|---|---|---|
| `RoomUI.selectRoom` | Bell + sidebar + RightInsight join buttons | `MainWindow.handleNav("MY_ROOMS")` etc. |
| `DiscussionController.requestJoinRoom` | | `DiscussionController.java:76` |
| `SocketServer.verifyPermissions` | | `SocketServer.java:32` |
| `SocketServer.connectUser` | Subscribes UI handler | `SocketServer.java:38` |
| `SocketServer.fetchRecentHistory` | `DiscussionController.requestHistory` | `DiscussionController.java:120` |
| `displayLiveChatWindow` | `DiscussionWindow` | same |

### UC-7 Send Real-Time Message

| Step | UC element | Source |
|---|---|---|
| `ChatUI.typeAndSendMessage` | `messageField.onAction` | `DiscussionWindow.java:255` |
| `DiscussionController.handleNewMessage` | | `DiscussionController.java:97` |
| `DiscussionRoom.createMessage` (Creator) | | `DiscussionRoom.java:51` |
| `DatabaseManager.saveMessage` | `messageDAO.sendMessage` | `MessageDAO.java:17` |
| `DiscussionController.broadcastMessage` | | `DiscussionController.java:119` |
| `SocketServer.pushToClients` | Bus fan-out | `LocalEventBus.publish` |
| `appendMessageToView` | `DiscussionWindow.loadMessages` | `DiscussionWindow.java:308` |
| Extension 3a (empty/too long) | | `DiscussionController.java:108-114` |

### UC-8 View Message History

| Step | UC element | Source |
|---|---|---|
| `ChatUI.openDiscussionHistory` | `DiscussionWindow.loadInitialMessages` | `DiscussionWindow.java:117` |
| `DiscussionController.requestHistory` | | `DiscussionController.java:120` |
| `DatabaseManager.queryMessagesByRoom` | `messageDAO.getMessagesForRoom` | `MessageDAO.java:39` |
| `orderChronologically` | `Comparator.comparing(Message::getTimestamp)` | `DiscussionController.java:124` |

### UC-9 Search Code Discussions

| Step | UC element | Source |
|---|---|---|
| `SearchUI.enterSearchFilters/clickSearch` | DashboardView filter row | `DashboardView.java` |
| `SnippetController.executeSearch` | | `SnippetController.java:124` |
| `SnippetController.validateFilters` | (inline; trim + lowercase + null-check) | `SnippetController.java:127-128` |
| `DatabaseManager.executeQuery` | `snippetDAO.findAllWithDetails` then in-memory filter | `SnippetController.java:126` |
| `displayResults` | `DashboardView.rebuildCards` | `DashboardView.java` |

### UC-10 Logout

| Step | UC element | Source |
|---|---|---|
| `MainUI.clickLogout` | sidebar logout button | `SidebarView` |
| `AuthController.requestLogout` | | `AuthController.java:124` |
| `Account.logout()` | abstract base method | `Account.java:46` |
| `AuthController.clearSessionData` | `Session.setCurrentUser(null)` via `Account.logout()` | same |
| `showLoginScreen` | `App.showAuthView` | `App.java` |

### UC-11 Manage User Accounts (Admin)

| Step | UC element | Source |
|---|---|---|
| `AdminUI.openUserManagement` | `AdminView.buildUsersCard` | `AdminView.java` |
| `AuthController.requestUserList` (effectively) | `userDAO.getAllUsers` | `UserDAO.java:115` (legacy lower line offset) |
| `AdminUI.displayUsers` | ListView in `buildUsersCard` | `AdminView.java` |
| `AuthController.modifyUser(status)` | `userDAO.setRole`, `userDAO.setDisabled` | `UserDAO.java:148`, `:160` |
| Extension 5b (cannot self-disable) | Guard in `AdminView.buildUsersCard` | `AdminView.java` |

### UC-12 Manage Code Snippets (Admin)

| Step | UC element | Source |
|---|---|---|
| `AdminUI.openContentManagement` | `AdminView.buildSnippetsCard` | `AdminView.java` |
| `SnippetController.requestFlaggedContent` | `snippetDAO.findAllWithDetails(viewer, includeHidden=true)` | `CodeSnippetDAO.java:56` |
| `SnippetController.applyModeration("HIDE"/"DELETE")` | | `SnippetController.java:147` |
| `DatabaseManager.updateItemStatus` | `snippetDAO.setHidden`, `snippetDAO.deleteSnippet` | `CodeSnippetDAO.java:79`, `:109` |
| Extension 5a (audit-only) | UI offers Hide/Unhide; Delete is also exposed but admin can choose | `SnippetController.java:147` |

---

## 3. Domain Model (Deliverable 4) → Source

| Domain class | Attributes | Source |
|---|---|---|
| User / Developer | id, username, email, password, role | `User.java`, `Developer.java` |
| Admin | + adminLevel | `Admin.java` |
| CodeSnippet | snippetID, title, language, description, codeContent, tags, createdAt, hidden | `CodeSnippet.java` |
| DiscussionRoom | roomID, title (`roomName`), timestamp (`createdAt`), linked snippetID, isPrivate | `DiscussionRoom.java` |
| Message | messageID, content, timestamp, senderID, roomID | `Message.java` |

---

## 4. System Sequence Diagrams (Deliverable 4) — System operations

Each SSD's "system event" maps to one method on the controller layer. The
table below lists the entry-point method that an SSD-level test would call.

| SSD | Actor system event | Public method |
|---|---|---|
| 1  | `register(username,email,password)`           | `AuthController.handleRegister` |
| 2  | `login(username,password)`                    | `AuthController.authenticateUser` |
| 3  | `uploadSnippet(title,lang,code,desc,tags)`    | `SnippetController.handleUpload` |
| 4  | `viewSnippet(snippetId)`                      | `SnippetController.requestSnippetData` |
| 5  | `createRoom(snippetId,name)`                  | `DiscussionController.createRoom` |
| 6  | `joinRoom(roomId)`                            | `DiscussionController.requestJoinRoom` |
| 7  | `sendMessage(roomId,content)`                 | `DiscussionController.handleNewMessage` |
| 8  | `viewHistory(roomId)`                         | `DiscussionController.requestHistory` |
| 9  | `search(keywords,language)`                   | `SnippetController.executeSearch` |
| 10 | `logout()`                                     | `AuthController.requestLogout` |
| 11 | `setRole(userId,role)` / `setDisabled(userId,flag)` | `UserDAO.setRole`/`setDisabled` (admin-only via `AdminView`) |
| 12 | `applyModeration(itemId,action)`              | `SnippetController.applyModeration` |

---

## 5. Out-of-scope confirmation (Deliverable 1)

| Out-of-scope item | Confirmation |
|---|---|
| Video / voice calls | No media/audio dependency in `pom.xml`. |
| Mobile application | Pure JavaFX desktop runtime; no Android/iOS targets. |
| Cloud hosting | DB defaults to local SQLite file. MySQL is LAN-only. |
| AI features | No ML libraries in `pom.xml`. |
| Large-scale deployment | No Docker/Kubernetes/cloud config files. |
