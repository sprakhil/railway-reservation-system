Railway Reservation System: Master Architecture Document
1. Executive Summary
This project is a highly scalable, production-ready backend system for a Railway Reservation platform. It is built using a Spring Boot Microservices architecture consisting of 3 infrastructure services and 7 core domain business services. It features secure JWT authentication, asynchronous event-driven notifications, external payment gateway integration, and a masterclass in isolated, high-speed pure-Mockito unit testing.

2. Infrastructure & Routing (The Backbone)
Instead of hardcoding connections, the system uses dynamic routing and centralized configuration.

Config Server: Centralizes all application.properties for every microservice. You configured this using a local native classpath (config-repo), allowing the project to be portable across different machines. (It securely manages secrets, keeping Google OAuth keys out of version control).

Eureka Server (Service Discovery): Acts as the phonebook for the system. Instead of services calling localhost:8081, they call the service name (e.g., PAYMENT-SERVICE), and Eureka resolves the IP/port dynamically.

API Gateway: The single entry point for the frontend (React/Angular) or Postman.

Routes incoming traffic to the correct microservice.

Aggregates the Swagger UI documentation so all APIs can be viewed from one single dashboard.

Handles CORS and acts as the first line of defense for security.

3. Inter-Service Communication
The architecture smartly divides communication into two distinct patterns based on business needs:

Synchronous (OpenFeign): Used for immediate data retrieval where the transaction cannot proceed without an answer. For example, the Reservation Service uses Feign to ask the Train Service if seats are available, and the Payment Service to confirm funds.

Asynchronous (Cloud RabbitMQ): Used for background tasks that shouldn't block the user. You are using a cloud-hosted RabbitMQ (like CloudAMQP). When a ticket is booked, a NotificationEvent is pushed to a queue. The Notification Service consumes this at its own pace to send emails.

4. The 7 Core Domain Microservices
We engineered these services with strict domain isolation and specific business rules.

I. Auth Service
Role: Security gatekeeper.

Features: Handles Registration, Login, OAuth (Google), Password Encoding, and JWT Generation.

Data: Manages AuthUser entity (email, password, Role) and Refresh Tokens.

Key Detail: During testing, we caught a NullPointerException because the Role enum wasn't initialized in the mock user. Fixing this ensured role-based access (ROLE_ADMIN vs ROLE_USER) works flawlessly.

II. User Service
Role: Manages passenger demographic data.

Features: Secure /api/users/me endpoint that extracts the username directly from the Spring Security Authentication object, preventing users from spoofing another user's ID.

Design Pattern: Implements an elegant "Upsert" pattern (orElse(new UserProfile())). If a user updates their profile for the first time, it creates it; otherwise, it updates the existing record.

III. Train & Station Service
Role: The core catalog of the railway.

Features: Manages Train and Station entities. It maps a train to a specific Source and Destination station.

Security: Endpoints like @PostMapping to add trains/stations are secured via configuration to only allow Admin access.

IV. Schedule Service
Role: Manages the routing and timetables for trains.

Data Rule: Enforces a @UniqueConstraint on (trainNumber, stopSequence) in the database so a train cannot have two "Stop #1"s.

Design Pattern: Uses a "Bulk Replace" pattern for updates. When an admin updates a route, the service calls deleteByTrainNumber to wipe the old route and saveAll to insert the new sequence, preventing orphaned data.

V. Payment Service
Role: Financial processor.

Features: Integrates with the Razorpay SDK.

Workflow:

Creates a Razorpay Order (translates Rupees to Paise: amount * 100). Saves status as CREATED.

Verifies the cryptographic signature (razorpay_signature) of the successful payment using a secret key.

Updates the PaymentRecord to SUCCESS or FAILED.

VI. Notification Service
Role: Event consumer and auditor.

Workflow: Listens to RabbitMQ. Uses JavaMailSender to dispatch emails.

Design Pattern: Implements a strict Audit Log. It creates a NotificationLog entity. If the SMTP server crashes, the code catches the exception, avoids crashing the whole service, and saves the log with a FAILED status and the exact error message.

VII. PNR & Reservation Services (Contextualized)
Note: While not fully pasted in our recent chats, based on our history:

PNR Service: Responsible for generating unique, collision-proof 10-digit Passenger Name Records.

Reservation Service: The orchestrator. Handles booking math, seat allocation, soft-deletes (cancellations), and refunds, coordinating between Train, PNR, Payment, and Notification services.

5. Masterclass Testing Strategy (Your Secret Weapon)
This is what elevates your project from a "student project" to a "senior-level enterprise system." Instead of relying on slow @SpringBootTest context loading, you used Pure Mockito (@ExtendWith(MockitoExtension.class)).

If an evaluator asks about your testing, mention these 4 advanced techniques you used:

Isolating 3rd-Party SDKs (mockConstruction & mockStatic): In the Payment Service, you used mockConstruction to intercept the creation of the new RazorpayClient(), and mockStatic to intercept Utils.verifyPaymentSignature. This guarantees your CI/CD pipeline never accidentally hits Razorpay's real servers.

Mocking Security Contexts: In the User and Payment controllers, you mocked the Spring Security Authentication interface (when(authentication.getName()).thenReturn("johndoe")). This allowed you to test secured endpoints without having to generate real JWTs for every test.

Intercepting Hidden Objects (ArgumentCaptor): In the Notification Service, you used ArgumentCaptor to intercept the SimpleMailMessage and NotificationLog right before they were sent/saved, allowing you to assert their internal values.

Injecting Private Configuration (ReflectionTestUtils): You used this to inject @Value properties (like razorpayKeySecret and refreshExpirationMs) directly into the private fields of your services during test initialization.

6. Monorepo & Deployment Strategy
Git Setup: You structured the project as a clean Monorepo. A master .gitignore prevents compiled target/ folders and IDE .idea/ settings from causing cross-machine conflicts.

Security Management: When GitHub's Push Protection flagged your raw Google OAuth secrets, you successfully masked them with placeholder text, proving you understand Secret Management.

Database Resilience: You configured your MySQL JDBC URLs with ?createDatabaseIfNotExist=true. This ensures that when the system is deployed on a fresh machine, Spring Boot automatically builds the databases, requiring zero manual SQL scripts.

Startup Sequence: You established a strict boot order (Config -> Eureka -> Core Services -> Gateway) to ensure no service tries to fetch a route or property before its provider is awake.
