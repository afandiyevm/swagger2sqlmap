# Swagger2Sqlmap
<img width="979" height="675" alt="swagger2sqlmap 19 39 23" src="https://github.com/user-attachments/assets/74724903-559c-4b43-be49-7ab27c30df35" />

**Swagger2Sqlmap** is a Burp Suite extension designed specifically to **generate sqlmap-ready requests from Swagger / OpenAPI specifications**.

Its main goal is to make **SQL injection testing of APIs fast, structured, and scalable** by automatically converting Swagger definitions into **real HTTP requests and sqlmap commands/scripts**.

---

## What Problem Does This Solve?

When testing APIs for SQL injection, penetration testers often face these issues:

- Swagger/OpenAPI lists endpoints but **does not produce executable requests**
- Manually recreating POST/PUT bodies is slow and error-prone
- sqlmap requires **precise, real HTTP requests**
- Testing **dozens or hundreds of endpoints** manually is impractical

**Swagger2Sqlmap exists to solve exactly this.**

> It turns Swagger files into **sqlmap-ready HTTP requests**, inside Burp Suite.

---

## Main Features (sqlmap-focused)

### 🔹 Swagger → SQLi Pipeline
- Load Swagger / OpenAPI JSON
- Extract all endpoints relevant for injection testing
- Generate real HTTP requests suitable for sqlmap

### 🔹 Full HTTP Request Generation
For each endpoint:
- Method (GET / POST / PUT / DELETE)
- Query parameters
- Headers
- JSON body (auto-generated from schema when available)
- Correct data types (numbers, booleans, strings)

### 🔹 Burp-style Request Editor
- Dedicated **Request tab** (similar to Repeater)
- Views:
  - Pretty
  - Raw
  - Hex
- Fully editable
- Resizable request editor

### 🔹 Authorization Support
Dedicated **Authorization tab**:
- Insert token from clipboard
- Load authorization headers from Burp History
- Automatically applied to all generated requests

### 🔹 SQLmap Command Builder
Dedicated **Command Builder tab**:
- Generates sqlmap commands from selected endpoints
- Includes:
  - URL
  - Headers
  - Cookies
  - Request body
- Designed for direct sqlmap execution

### 🔹 Bulk sqlmap Script Export
Generate scripts to run sqlmap against **all endpoints**:
- Bash (`.sh`)
- PowerShell (`.ps1`)
- Python (`.py`)

Each script:
- Iterates over endpoints
- Executes sqlmap per request
- Preserves headers and bodies
---

### 🔹 Logs Tab
- Parsing status
- Request generation info
- Export results

---

## Typical Workflow (sqlmap-centric)

1️⃣ Load Swagger file  
2️⃣ Review generated endpoints  
3️⃣ Verify / adjust request bodies  
4️⃣ Add authorization  
5️⃣ Generate sqlmap commands or scripts  
6️⃣ Run sqlmap externally  

---

## Example Use Case

- API has 120 endpoints
- Swagger file is provided
- You need **systematic SQL injection coverage**

With Swagger2Sqlmap:
- No manual request recreation
- No guessing parameter placement
- No copy-paste chaos
- Clean sqlmap execution per endpoint

---

## How to Build & Load

```bash
./gradlew clean jar
```

Then load the JAR in Burp Suite:
Extensions → Installed → Add
Type: Java
Select the generated JAR

---

## Who is this for?

- API penetration testers
- Red teamers
- Bug bounty hunters
- Anyone doing SQL injection testing on APIs

---

## Authors
- Farkhad Askarov
- Murad Afandiyev

## Version
v0.1.0

## Disclaimer

This tool is intended only for authorized security testing.
The authors take no responsibility for misuse.
