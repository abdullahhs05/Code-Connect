# Running CodeConnect across Two Desktops

CodeConnect now supports two backends — **SQLite** (default, single machine) and
**MySQL** (LAN-shared, multi-machine) — and a **LAN-aware event bus** so that
mentions, new messages, and room updates flow in real time between users on
different desktops.

## 1. Pick the "hub" machine

One machine on the LAN runs MySQL Server *and* the CodeConnect app first.
The other machine runs only the CodeConnect app and connects back.

> A typical home/office LAN over Wi-Fi is fine. Both machines need to see each
> other on TCP — the easiest test is `ping <hub-ip>` from the second machine.

Find the hub's LAN IPv4 (e.g. `192.168.1.10`):
```powershell
ipconfig
```

## 2. Install MySQL on the hub

The simplest free option is **MySQL Community Server 8.x**:
1. Download from <https://dev.mysql.com/downloads/installer/> (Windows MSI Installer).
2. During setup pick *"Server only"*, set a root password, allow port `3306`,
   and start the service.
3. Open MySQL Workbench (or `mysql -u root -p`) and run:

```sql
CREATE DATABASE codeconnect CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'cc_user'@'%' IDENTIFIED BY 'cc_pw';
GRANT ALL PRIVILEGES ON codeconnect.* TO 'cc_user'@'%';
FLUSH PRIVILEGES;
```

Allow MySQL through Windows Firewall (TCP 3306) and verify it binds to all
interfaces — open `C:\ProgramData\MySQL\MySQL Server 8.0\my.ini` and ensure
`bind-address = 0.0.0.0` (or comment the line out). Restart the MySQL service
afterwards.

> [!IMPORTANT]
> The credentials `cc_user` and `cc_pw` are examples for documentation. In a production or shared LAN environment, please replace `cc_pw` with a strong, custom password and update your environment variables accordingly.

## 3. Configure CodeConnect on **both** machines

CodeConnect reads config from environment variables (or `-D` system properties).
Two variables matter:

| Var | Value (both machines) |
|---|---|
| `DB_KIND` | `mysql` |
| `DB_URL` | `jdbc:mysql://192.168.1.10:3306/codeconnect?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` |
| `DB_USER` | `cc_user` |
| `DB_PASSWORD` | `cc_pw` |
| `BUS_HOST` | `192.168.1.10` (the hub IP) |
| `BUS_PORT` | `39817` (default) |

Replace `192.168.1.10` with **your** hub's IP.

### PowerShell (per terminal session)
```powershell
$env:DB_KIND     = "mysql"
$env:DB_URL      = "jdbc:mysql://192.168.1.10:3306/codeconnect?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USER     = "cc_user"
$env:DB_PASSWORD = "cc_pw"
$env:BUS_HOST    = "192.168.1.10"
.\run.ps1
```

### Or pass as JVM properties at launch
```powershell
mvn javafx:run "-Dcc.db.kind=mysql" `
               "-Dcc.db.url=jdbc:mysql://192.168.1.10:3306/codeconnect?useSSL=false&serverTimezone=UTC" `
               "-Dcc.db.user=cc_user" `
               "-Dcc.db.password=cc_pw" `
               "-Dcc.bus.host=192.168.1.10"
```

## 4. Allow port 39817 on the hub

The first CodeConnect instance to start on the hub binds `0.0.0.0:39817` and
becomes the **event bus server**. Other clients on the LAN connect to it and
exchange topic/payload lines (e.g. new chat messages).

```powershell
# On the hub, run as Administrator:
New-NetFirewallRule -DisplayName "CodeConnect Bus" -Direction Inbound `
    -Protocol TCP -LocalPort 39817 -Action Allow
```

## 5. Verify

1. Start CodeConnect on the **hub** first. Console should print:
   ```
   [DB] Initialized backend=MYSQL url=jdbc:mysql://192.168.1.10:3306/...
   [Bus] Server listening on 0.0.0.0:39817
   ```
2. Start CodeConnect on the **second machine**. Console should print:
   ```
   [DB] Initialized backend=MYSQL url=jdbc:mysql://192.168.1.10:3306/...
   [Bus] Connected as client to 192.168.1.10:39817
   ```
3. Log in as `dev` (password `dev`) on machine A and `admin` (password `admin`) on machine B.
4. Open the same code snippet's discussion room on both. Type a message on A —
   it should appear on B within a second.

## 6. Going back to single-machine mode

Just unset the env vars (or close the terminal). Default behavior is
`DB_KIND=sqlite` writing to `./codeconnect.db`, with the event bus on
loopback only — no setup required.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Communications link failure` | Hub firewall blocking 3306, or `bind-address` is `127.0.0.1`. Open the port and restart MySQL. |
| `Access denied for user 'cc_user'@'192.168.1.x'` | The MySQL user wasn't granted `'%'` host. Re-run the `CREATE USER ... '@'%'` step. |
| `[Bus] Standalone mode` | The hub app isn't running yet, or 39817 is firewalled. Start the hub first; allow the port. |
| Messages don't appear in real-time but show on next refresh | Bus is in standalone mode; the 5 s polling fallback is doing the work. Fix the bus connectivity. |
| `SQLITE_BUSY`/corruption | You tried to share `codeconnect.db` over a network drive. Don't. Use MySQL instead — that's exactly what this setup is for. |
