# Local Development Workflow

This document outlines the local development setup for the AI Interview Bot backend using IntelliJ IDEA and your local PostgreSQL instance.

## 1. Running the Services via IntelliJ

Instead of running scripts or Docker Compose, we leverage IntelliJ's "Run Dashboard" to orchestrate the microservices locally using the **`local`** Spring Profile.

### Prerequisites
- Make sure you have downloaded **JDK 21** via IntelliJ (File > Project Structure > SDKs > Add > Download JDK > Version 21).
- Your local PostgreSQL database must be running (the `aibot-postgres` container on port 5434).

### IntelliJ Run Configurations Setup
For each of the 8 microservices, verify or create a Spring Boot Run Configuration. 

For the 5 services that connect to the database, you must **activate the `local` profile**:
1. `auth-service`
2. `compliance-service`
3. `interview-service`
4. `observer-service`
5. `review-service`

**How to activate the profile:**
1. Open **Run/Debug Configurations** in IntelliJ.
2. Select the Spring Boot application (e.g., `AuthServiceApplication`).
3. Find the **Active Profiles** field and enter: `local`
4. Click Apply.
5. Repeat for the 5 services listed above.

*Note: `ai-service`, `api-gateway`, and `eureka-server` do not connect to the database. They can be run with no active profiles specified (they will default to their standard config which already targets localhost).*

### Startup Order
When starting the applications in the Services panel, start them in this sequence:
1. `eureka-server` (Wait for it to say 'Started' on port 6009)
2. `api-gateway`
3. `auth-service` and `compliance-service`
4. `ai-service`, `interview-service`, `observer-service`, `review-service`

## 2. Source Control Exclusions (.gitignore)

To ensure that your local development configurations are not accidentally pushed to your remote repository, add the following lines to your project's `.gitignore` file:

```gitignore
# Local Development Profiles
*/*application-local.yml
LOCAL_DEV_README.md
```

## 3. Database Management (pgAdmin)

You can manage your local database using pgAdmin, which is accessible via your browser.

- **URL**: [http://localhost:5050](http://localhost:5050)
- **Login Credentials**:
  - **Email**: `admin@benchreadiness.com`
  - **Password**: `admin`

### Connecting to the Database in pgAdmin
1. Log in to pgAdmin.
2. Right-click on **Servers** > **Register** > **Server...**.
3. Under **General**, enter a name (e.g., `Local Postgres`).
4. Under **Connection**:
   - **Host name/address**: `aibot-postgres` (since they are on the same Docker network)
   - **Port**: `5432`
   - **Maintenance database**: `aibot`
   - **Username**: `aibot`
   - **Password**: `aibot!@#$%` (Check your `application.yml` for confirmation)
5. Click **Save**.

