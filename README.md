# Railwize — railway reservation platform

One repository containing the Spring Boot microservices backend and Angular frontend. Google sign-in, Razorpay **test-mode** payments, CNF/WL booking, ticket PDFs, email/in-app notifications, journey alternatives, Gemini assistant and administrator reporting are included. No Docker, WSL, Redis or SonarQube installation is required.

```text
backend/      Java microservices, configuration, database setup and launch scripts
frontend/     Angular application, locked npm dependencies and Jasmine tests
*.png         Existing architecture and design diagrams
*.pptx        Existing project presentation
```

Detailed feature/API documentation: [backend README](backend/README.md). UI/testing documentation: [frontend README](frontend/README.md).

## Download and prepare another Windows laptop

1. On GitHub select **Code → Download ZIP**, then extract the ZIP completely. Do not run scripts inside the ZIP. GitHub's [source archive instructions](https://docs.github.com/en/repositories/working-with-files/using-files/downloading-source-code-archives) explain this workflow. The extracted folder normally ends in `-main`; it can be renamed or moved to Desktop.
2. Install a JDK compatible with Java 21 source (this project was tested with JDK 22), Maven 3.9.x, Node.js 22.13 or newer in the 22.x line, MySQL 8.x and MySQL Workbench. Install native RabbitMQ with its compatible Erlang runtime, or use your existing accessible broker. Use [PowerShell 7](https://learn.microsoft.com/en-us/powershell/scripting/install/install-powershell-on-windows), not Command Prompt, for the `.ps1` scripts. Git is optional when using ZIP.
3. Open a new PowerShell terminal and confirm these commands work:

   ```powershell
   java -version
   mvn.cmd -version
   node --version
   npm.cmd --version
   $PSVersionTable.PSVersion
   ```

4. Start MySQL and RabbitMQ. Open `backend/Create-Databases.sql` in MySQL Workbench and execute it. It creates the eight missing databases without deleting existing data. Services create tables in fresh databases with `DDL_AUTO=update`; do not run old manual migration scripts against a fresh installation.
5. Choose the data setup below, then configure `backend/.env`.

### Keep your current data, or start fresh

**GitHub contains source code, not your running MySQL data.** To retain the current accounts, train catalogue, timetables, saved passengers and bookings, use MySQL Workbench **Server → Data Export** on the original laptop. Stop application services first so the eight databases form a consistent snapshot. Select all eight `railway_*_db` schemas listed in `backend/Create-Databases.sql`, select structure and data, and export to a private folder outside the repository. Import those schemas into the new laptop's MySQL using **Server → Data Import** before starting services. Keep the export private: it includes account and passenger data. Do not overwrite an existing database without backing it up first.

Alternatively, start with the empty databases and add catalogue data through the admin UI. `backend/Seed-DemoCatalogue.ps1` can create two small DSTA → DSTB → DSTC demonstration routes after admin login. It requires an admin access token supplied privately in your terminal. It does not recreate your existing trains or test all nearby/interchange scenarios.

**Privately transfer `backend/.env` from your original backend**, then update `DB_USERNAME`, `DB_PASSWORD` and any machine-specific addresses for the new laptop. Never commit this file. When starting entirely fresh, generate it instead:

```powershell
cd backend
.\New-DevEnvironment.ps1
notepad .env
```

Fill database, SMTP, Google OAuth, Razorpay TEST and Gemini values from your own accounts. The generator creates local service secrets and refuses to overwrite an existing `.env`. All services launched with `Run-Service.ps1` read this one backend-root file. Keep `PDF_FONT_PATH=` empty to use the bundled ticket font unless you intentionally supply a valid font path on the new laptop.

Keep the local addresses consistent:

- Frontend: `http://localhost:4200`.
- Gateway: `http://localhost:8080`.
- Google authorized redirect URI and `GOOGLE_REDIRECT_URI`: `http://localhost:8080/api/auth/google/callback`.
- `FRONTEND_CALLBACK_URI`: `http://localhost:4200/auth/callback`.
- `FRONTEND_ORIGINS` and `FRONTEND_BASE_URL`: `http://localhost:4200`.
- Use `localhost` consistently; mixing `127.0.0.1`, a LAN address and `localhost` changes cookie/origin behavior.

If you use a remote RabbitMQ/SMTP endpoint, the new laptop must be able to reach it. Gemini free quotas, OAuth configuration and SMTP delivery remain dependent on the provider; a source ZIP cannot guarantee them. Razorpay keys must begin with `rzp_test_` and match the supplied test secret. No real payments are supported.

### Start the backend

Open a separate PowerShell terminal for each service, with its working directory set to the extracted `backend` folder. If scripts are blocked, use `Set-ExecutionPolicy -Scope Process Bypass` only in that terminal. The following are **separate long-running commands**, not one sequential script:

```powershell
.\Run-Service.ps1 config-server
.\Run-Service.ps1 eureka-server
.\Run-Service.ps1 auth-service
.\Run-Service.ps1 user-service
.\Run-Service.ps1 train-service
.\Run-Service.ps1 schedule-service
.\Run-Service.ps1 pnr-service
.\Run-Service.ps1 reservation-service
.\Run-Service.ps1 payment-service
.\Run-Service.ps1 notification-service
.\Run-Service.ps1 chatbot-service
.\Run-Service.ps1 api-gateway
```

Start Config Server and Eureka first, then the other services, and wait for registration before using the UI. The first Maven run downloads dependencies. A laptop running all twelve JVMs needs enough free RAM; avoid running duplicate instances from IntelliJ and PowerShell at the same time. Use Ctrl+C in each terminal to stop it.

Config Server/Eureka browser pages require their credentials from `.env`; a 401 response without credentials is expected. They use infrastructure credentials, not a passenger login. Gateway Swagger is at `http://localhost:8080/swagger-ui/index.html`.

For a **fresh database only**, configure `BOOTSTRAP_ADMIN_ENABLED=true` and the bootstrap admin username/email/password in `.env` before the first auth-service start. After creation, set bootstrap to false and restart auth-service. An imported database already has your accounts. Use the normal frontend login for the admin workspace; public registration never grants ADMIN.

### Start the frontend

In another terminal, open the extracted `frontend` folder:

```powershell
npm.cmd ci
npm.cmd test
npm.cmd start
```

Open `http://localhost:4200`. The Angular dev proxy forwards `/api` calls to port 8080; do not open `index.html` directly. `npm ci` restores exactly the dependencies in `package-lock.json`. There is no frontend secret file. The PowerShell alternative is `.\Start-Frontend.ps1 -Install`.

### Verify the second laptop

1. Confirm all services register in Eureka and the gateway Swagger page loads.
2. Register, verify OTP, log in, view your profile and log out. Verify Google login separately.
3. Sign in as admin; load users, stations, trains, timetables and reports.
4. Search a future date within two months on a train operating that day. Fresh databases have no train results until a catalogue/timetable is added.
5. Select a saved passenger; create a booking; complete Razorpay test checkout within two minutes.
6. Check the ticket, seat/berth labels, PDF, confirmation email and notification. Verify cancellation preview, cancellation and test-refund progress.
7. Test WL/promotion on a small-capacity train, nearby-station/one-interchange suggestions with suitable catalogue data, and the assistant's account queries and Gemini policy replies.
8. Check admin CSV exports and logout; a second user's private data must not appear.

Automated commands:

```powershell
# In backend: isolated JUnit/Mockito and integration suites
.\Verify-Backend.ps1
# In frontend: Jasmine, production build and optional browser scenarios
npm.cmd test
npm.cmd run build
npm.cmd run test:e2e
```

Browser scenarios use Microsoft Edge by default. Offline backend tests do not require your MySQL data or real provider credentials. A live Gemini smoke test is opt-in and is not part of the normal test command.

## What is intentionally excluded

`.env`, runtime logs, MySQL exports, `node_modules`, Maven `target`, Angular build/cache folders, browser reports and local verification output stay off GitHub. They do **not** need to be deleted from your original laptop. Keep `.env` and your database backup in private storage. Fonts, logos, templates, SQL schema/migration source, test source and npm lockfile are included.

Previously committed logs are omitted from this latest snapshot but remain in Git history. This update does not rewrite history or rotate provider credentials. If any old credential was previously committed, revoke it with its provider separately. The original diagrams/presentation are retained and may describe earlier architecture details.

SonarQube is intentionally deferred.

## Preparation checks (2 October 2026)

The combined repository was assembled from the current application folders, retaining existing GitHub diagrams and the presentation. All 12 backend services packaged successfully using a newly populated Maven dependency cache (`package -DskipTests`); this checks fresh dependency resolution and compilation, not a new full backend test run. The copied frontend passed 34 Jasmine specs and strict Angular template/AOT compilation. Standard npm lifecycle installation and `ng build` were blocked by this execution environment's Windows `spawn EPERM`; dependencies were restored with lifecycle scripts disabled for the isolated unit/AOT checks. On the new laptop run ordinary `npm ci`, `npm run build` and the smoke checklist above. Live MySQL/provider behavior on that laptop has not yet been verified. The source snapshot excludes current environment credentials and runtime artifacts.
