# Troubleshooting & FAQ Guide

**Version:** 1.6.9  
**Author:** NSENGIYUMVA Christophe  
*Copyright © 2026 NSENGIYUMVA Christophe. All rights reserved.*

---

## 1. Windows Issues

### Issue 1.1: Windows 11 Smart App Control Error 4551
#### Symptom
When attempting to launch `SchemaSync.exe`, Windows displays:
```text
Unable to execute file:
C:\Users\<user>\AppData\Local\Programs\SchemaSync\SchemaSync.exe
CreateProcess failed; code 4551.
An Application Control policy has blocked this file.
```

#### Root Cause
Your Windows 11 system has **Smart App Control (SAC)** actively enforced. Unsigned `.exe` wrappers produced by tools like Launch4j are blocked at the Windows kernel level if they lack a Microsoft-trusted Authenticode digital certificate.

#### Permanent Solution
1. **Launch via the Official Shortcut:**  
   SchemaSync's Start Menu and Desktop shortcuts are specifically configured to launch via the signed Java runtime (`javaw.exe`), which is **100% trusted and approved by Smart App Control**.
2. **If You Prefer Running `.exe` Directly:**  
   Add a folder exclusion in Windows Security:
   - Open **Windows Security** ➔ **Virus & threat protection**.
   - Under *Virus & threat protection settings*, click **Manage settings**.
   - Scroll to **Exclusions** ➔ **Add or remove exclusions**.
   - Add a folder exclusion for `%LOCALAPPDATA%\Programs\SchemaSync`.

---

### Issue 1.2: "Windows cannot find 'javaw.exe'"
#### Symptom
When clicking the desktop icon, Windows displays:
```text
Windows cannot find 'javaw.exe'. Make sure you typed the name correctly, and then try again.
```

#### Root Cause
This occurs if Java was not installed when the installer ran, or if the shortcut was created before Java was added to the system `PATH`.

#### Solution
1. Run the latest [`SchemaSync-Setup.exe`](file:///d:/projects/DBTOOL/release/SchemaSync-Setup.exe).
2. The setup wizard will automatically detect the missing Java environment and offer to download and install **Eclipse Temurin JRE 21**.
3. Setup automatically resolves the exact absolute path to the newly installed `javaw.exe` and updates your desktop shortcuts permanently.

---

### Issue 1.3: `UnsupportedClassVersionError` (Java Version Too Old)
#### Symptom
When launching SchemaSync, an error dialog or console output mentions:
```text
java.lang.UnsupportedClassVersionError: com/dbtool/Main has been compiled by a more recent version of the Java Runtime (class file version 55.0 / 65.0), this version of the Java Runtime only recognizes class file versions up to 52.0
```

#### Root Cause
Your machine has an outdated Java 8 (1.8) runtime set as the default Java. SchemaSync is built with modern Java features requiring **Java 11 or higher** (Java 21 recommended).

#### Solution
1. Download and install **Java 21 (LTS)** from [Adoptium Temurin](https://adoptium.net).
2. Or rerun `SchemaSync-Setup.exe`, which automatically upgrades your Java runtime to Temurin JRE 21.

---

## 2. Linux Issues (Ubuntu & Fedora)

### Issue 2.1: Dock Shows Generic Grey Gear or Coffee Cup Icon in Fedora/GNOME
#### Symptom
SchemaSync launches and operates normally, but the dock/taskbar displays a generic grey icon instead of the SchemaSync 3D crystal cube.

#### Solution
1. Ensure the package was installed via `sudo dnf install ./schemasync-1.6.9-1.fc44.noarch.rpm`.
2. Verify that `/usr/share/applications/schemasync.desktop` contains:
   ```ini
   StartupWMClass=schemasync
   ```
3. Update your desktop database and icon cache:
   ```bash
   sudo update-desktop-database
   sudo gtk-update-icon-cache /usr/share/icons/hicolor
   ```
4. Restart GNOME Shell (on X11: `Alt+F2`, type `r`, press Enter; on Wayland: log out and back in).

---

### Issue 2.2: Permission Denied When Updating on Linux
#### Symptom
Clicking **Update & Restart** fails to replace files in `/usr/lib/schemasync/`.

#### Solution
System directories under `/usr/lib/` are owned by `root`. SchemaSync v1.6.9 automatically manages updates in user-space (`~/.local/share/schemasync/`) so that updates succeed without prompting for `sudo` passwords. If running from the system directory, install new releases via:
```bash
sudo apt install ./schemasync_1.6.9_all.deb     # Ubuntu
sudo dnf install ./schemasync-1.6.9-1.fc44.noarch.rpm # Fedora
```

---

## 3. Database Connection Issues

### Issue 3.1: Microsoft Access `.laccdb` File Lock
#### Symptom
After querying an Access `.accdb` file, you cannot rename or delete the file because Windows reports it is locked by another process.

#### Solution
* Click **Disconnect** on the top toolbar when you are finished querying.
* SchemaSync passes `immediatelyReleaseResources=true` to UCanAccess to ensure all file locks are released upon connection close.

---

### Issue 3.2: PostgreSQL "FATAL: password authentication failed"
#### Symptom
Connection to PostgreSQL server fails with authentication error.

#### Checklist
1. Ensure the PostgreSQL service is running: `sudo systemctl status postgresql` (Linux) or Services app (Windows).
2. Check `pg_hba.conf` to verify your client IP or localhost is permitted with `md5` or `scram-sha-256`.
3. If connecting to a cloud database (AWS RDS, Supabase, Neon), ensure SSL is set to **Require** or **Prefer** and port `5432` is open in security groups.

---

### Issue 3.3: Java Heap OutOfMemoryError on Massive Queries
#### Symptom
Exporting or querying tables with millions of rows causes a JVM `OutOfMemoryError`.

#### Solution
Launch SchemaSync with an expanded heap limit:
```bash
java -Xmx2g -Duser.timezone=UTC -jar SchemaSync.jar
```
Or use the **Table Browser's LIMIT field** (e.g. `LIMIT 5000`) to paginate queries efficiently.
