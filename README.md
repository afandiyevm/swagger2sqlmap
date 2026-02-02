# Swagger2Sqlmap
<img width="979" height="675" alt="swagger2sqlmap 19 39 23" src="https://github.com/user-attachments/assets/74724903-559c-4b43-be49-7ab27c30df35" />

**Swagger2Sqlmap** is a Burp Suite extension designed specifically to **generate sqlmap-ready requests from Swagger / OpenAPI specifications**.

Its main goal is to make **SQL injection testing of APIs fast, structured, and scalable** by automatically converting Swagger definitions into **real HTTP requests and sqlmap commands/scripts**.

---

# What Problem Does This Solve?

When testing APIs for SQL injection, penetration testers often face these issues:

- Swagger/OpenAPI lists endpoints but **does not produce executable requests**
- Manually recreating POST/PUT bodies is slow and error-prone
- sqlmap requires **precise, real HTTP requests**
- Testing **dozens or hundreds of endpoints** manually is impractical

**Swagger2Sqlmap exists to solve exactly this.**

> It turns Swagger files into **sqlmap-ready HTTP requests**, inside Burp Suite.

---

# Who is this for?

- API penetration testers
- Red teamers
- Bug bounty hunters
- Anyone doing SQL injection testing on APIs

---
# Key features
- Import and parse Swagger / OpenAPI (v2 & v3) specifications
- Automatic request body templating based on schema types
- Build ready-to-use sqlmap commands per endpoint
- Full control over sqlmap options (Level, risk, threads, batch, random User-Agent, Force SSL, tamper)
- Export automation scripts for sqlmap execution (options: `.sh`, `.py`, `.ps1`)
- Bulk sqlmap execution across all imported endpoints

# Usage instructions
### 1. Import Swagger / OpenAPI
1. Open the **Targets** tab
2. Click **Import**
3. Select a Swagger / OpenAPI file
4. Click **Load** to populate endpoints
The base URL is detected automatically and can be edited.
---

### 2. Authorization
1. Open the **Authorization** tab
2. Provide a Bearer token via:
   * Clipboard
   * Burp Proxy history
   * Manual input

**The token is applied automatically to all requests and sqlmap commands.**

---

### 3. SQLmap Command Builder
1. Select an endpoint in **Targets**
2. Open **Command Builder**
3. Configure options:
   * Level
   * Risk
   * Threads
   * Batch mode
   * Random User-Agent
   * Force SSL
   * Header inclusion mode
   * Extra sqlmap arguments
Click **Build command** to generate a ready-to-run sqlmap command.
---
### 4. Tamper Scripts
* Select **multiple tampers** simultaneously
* Supported built-in tampers:
  * `apostrophemask`
  * `between`
  * `space2comment`
  * `randomcase`
  * `charunicodeencode`
* Use **Add custom** to define additional tampers

The extension automatically builds:
```bash
--tamper=tamper1,tamper2,tamper3
```

---
### 5. Export Automation Scripts

1. Click **Export**
2. Choose format:

   * `.sh`
   * `.py`
   * `.ps1`

The generated script runs sqlmap against **all loaded endpoints** with consistent options.

---
### 6. Run the generated script:
```bash
bash swagger2sqlmap.sh
```
or
```bash
python3 swagger2sqlmap.py
```
or
```powershell
.\swagger2sqlmap.ps1
```

---

# Typical Workflow 

1. Load Swagger file
2. Review generated endpoints
3. Verify / adjust request bodies
4. Add authorization
5. Generate sqlmap commands or scripts
6. Run sqlmap externally  

---
# Example Use Case
- API has 120 endpoints
- Swagger file is provided
- You need **systematic SQL injection coverage**

With Swagger2Sqlmap:
- No manual request recreation
- No guessing parameter placement
- No copy-paste chaos
- Clean sqlmap execution per endpoint

---
# Authors
- Farkhad Askarov
- Murad Afandiyev

---
# Disclaimer
This tool is intended only for authorized security testing.
The authors take no responsibility for misuse.
