# Job Portal — Microservice Architecture

A full-stack **Job Portal** web application built with **Spring Boot** and **Spring Cloud**, following a microservice architecture. The platform supports two user roles — **Recruiters** (post jobs, manage applicants) and **Job Seekers** (browse jobs, apply, save listings, manage profile).

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Services](#services)
- [Technology Stack](#technology-stack)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Database Setup](#database-setup)
  - [Running the Services](#running-the-services)
- [API Reference](#api-reference)
- [Security](#security)
- [Inter-Service Communication](#inter-service-communication)
- [Fault Tolerance](#fault-tolerance)

---

## Architecture Overview

```
                        ┌─────────────────────┐
                        │     Eureka Server    │
                        │      Port: 8761      │
                        │  (Service Registry)  │
                        └──────────┬──────────┘
                                   │  All services register here
                                   │
          ┌────────────────────────▼────────────────────────┐
          │                  API Gateway                     │
          │                  Port: 8080                      │
          │    JWT Validation · Routing · Header Injection   │
          └───┬──────┬──────┬──────┬──────┬──────┬──────┬───┘
              │      │      │      │      │      │      │
         ┌────▼─┐ ┌──▼──┐ ┌▼────┐ ┌▼───┐ ┌▼────┐ ┌▼───┐ ┌▼────┐
         │ User │ │ Job │ │Rec- │ │Job-│ │Appl-│ │Save│ │(more│
         │ Svc  │ │ Svc │ │rtr  │ │Seek│ │ica- │ │Jobs│ │later│
         │ 8081 │ │ 8083│ │8082 │ │8084│ │tion │ │8086│ │  )  │
         └──────┘ └─────┘ └─────┘ └────┘ │8085 │ └────┘ └─────┘
                                          └─────┘
```

Each backend service registers with **Eureka** and communicates with peer services via **OpenFeign** clients over load-balanced HTTP. The **API Gateway** is the single point of entry for all clients — it validates the JWT token, then injects `X-Auth-User` and `X-Auth-Roles` headers before forwarding the request.

---

## Services

| Service | Port | Database | Description |
|---|---|---|---|
| **eureka-server** | 8761 | — | Service registry (Netflix Eureka) |
| **api-gateway** | 8080 | — | JWT validation, routing, header injection |
| **user-service** | 8081 | `db_users` | Registration, login, JWT generation |
| **recruiter-service** | 8082 | `db_recruiter` | Recruiter profiles & photo uploads |
| **job-service** | 8083 | `db_jobs` | Job postings, search, dashboard |
| **jobseeker-service** | 8084 | `db_jobseeker` | Job seeker profiles, skills, resume |
| **application-service** | 8085 | `db_applications` | Job applications & cover letters |
| **saved-jobs-service** | 8086 | `db_savedjobs` | Bookmark/save jobs |

### Service Details

#### Eureka Server
Standalone service discovery server. No other service needs to know the IP of any peer — they look each other up by name via Eureka.

#### API Gateway
- Routes all `/api/**` and UI paths to the appropriate downstream service.
- Reads the `JWT_TOKEN` cookie, validates the JWT signature, and on success injects:
  - `X-Auth-User` — the authenticated user's email
  - `X-Auth-Roles` — comma-separated role list
- Unauthenticated requests to protected paths are redirected to `/login`.
- Multipart form data is forwarded as-is (not buffered) so file uploads reach their target service intact.

#### User Service
- The **only** service that issues JWT tokens.
- Handles user registration and form-based login.
- Exposes REST endpoints consumed by other services to resolve a user by email or ID.
- After a successful login, `CustomAuthenticationSuccessHandler` generates a JWT and stores it in an `HttpOnly` cookie (`JWT_TOKEN`, 24-hour expiry).

#### Recruiter Service
- Manages recruiter profile data (name, company, location) and profile photos.
- Photos are stored on disk under `photos/recruiter/{userId}/`.
- Exposes REST endpoints so other services can look up recruiter information.

#### Job Service
- Core service for creating and browsing job postings.
- Each posting includes job title, type, salary, remote flag, description, location, and company logo.
- The dashboard aggregates data from multiple services (application status, saved status, recruiter info) via Feign clients.

#### Jobseeker Service
- Manages job seeker profiles: personal info, work authorisation, employment type, skills list, resume, and profile photo.
- Photos and resumes are stored on disk under `photos/candidate/{userId}/`.
- Skills are stored as a one-to-many relationship with the profile.

#### Application Service
- Records job applications with the date applied and an optional cover letter.
- Enforces a unique constraint on `(userId, jobId)` to prevent duplicate applications.
- Exposes endpoints to list all jobs a user has applied to, and all applicants for a given job.

#### Saved Jobs Service
- Allows job seekers to bookmark jobs.
- Enforces a unique constraint on `(userId, jobId)`.
- Exposes endpoints to list saved jobs and check whether a specific job is already saved.

---

## Technology Stack

| Category | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.0.5 |
| Cloud | Spring Cloud 2025.1.1 |
| Service Discovery | Netflix Eureka |
| API Gateway | Spring Cloud Gateway (WebMvc) |
| Inter-service HTTP | OpenFeign |
| Fault Tolerance | Resilience4j (Circuit Breaker + Retry) |
| Authentication | Spring Security 6 + JWT (JJWT 0.12.6) |
| ORM | Spring Data JPA / Hibernate |
| Database | MySQL (production), H2 (development) |
| Templating | Thymeleaf |
| Frontend | Bootstrap 5.3.8, jQuery 3.7.1, Font Awesome |
| Build | Maven |

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8.x running locally (or update connection strings)
- (Optional) Docker for containerised MySQL

### Database Setup

Create a separate schema for each service in MySQL:

```sql
CREATE DATABASE db_users;
CREATE DATABASE db_recruiter;
CREATE DATABASE db_jobs;
CREATE DATABASE db_jobseeker;
CREATE DATABASE db_applications;
CREATE DATABASE db_savedjobs;
```

Each service uses `spring.jpa.hibernate.ddl-auto=update` so tables are created automatically on first startup.

Update `src/main/resources/application.properties` in each service with your MySQL credentials:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/<db_name>
spring.datasource.username=<your_username>
spring.datasource.password=<your_password>
```

### Running the Services

Start the services **in the following order** to ensure dependencies are available:

**1. Eureka Server**
```bash
cd eureka-server
./mvnw spring-boot:run
```
Visit the Eureka dashboard at [http://localhost:8761](http://localhost:8761).

**2. All Backend Services** (in any order, after Eureka is up)
```bash
cd user-service       && ./mvnw spring-boot:run &
cd recruiter-service  && ./mvnw spring-boot:run &
cd job-service        && ./mvnw spring-boot:run &
cd jobseeker-service  && ./mvnw spring-boot:run &
cd application-service && ./mvnw spring-boot:run &
cd saved-jobs-service && ./mvnw spring-boot:run &
```

**3. API Gateway** (last, after all services have registered with Eureka)
```bash
cd api-gateway
./mvnw spring-boot:run
```

The application is now accessible at **[http://localhost:8080](http://localhost:8080)**.

> **Windows users:** Replace `./mvnw` with `mvnw.cmd`.

---

## API Reference

All REST endpoints are accessed through the API Gateway (`http://localhost:8080`). Authentication is required via the `JWT_TOKEN` cookie.

### User Service — `/api/users`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/users/{id}` | Get user by ID |
| `GET` | `/api/users/by-email/{email}` | Get user by email |
| `GET` | `/api/users/credentials/{email}` | Get password hash and role (internal) |
| `GET` | `/register` | Show registration form |
| `POST` | `/register/new` | Create new user account |
| `GET` | `/login` | Show login form |

### Recruiter Service — `/api/recruiters`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/recruiters/{userId}` | Get recruiter profile |
| `POST` | `/api/recruiters/create/{userId}` | Create recruiter profile |
| `GET` | `/recruiter-profile/` | Show recruiter profile page |
| `POST` | `/recruiter-profile/addNew` | Update recruiter profile with photo |

### Job Service — `/api/jobs`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/jobs/{id}` | Get job details by ID |
| `GET` | `/dashboard` | Job listing dashboard |
| `POST` | `/dashboard/addNew` | Create a new job posting |
| `GET` | `/job-details/{id}` | View job details page |

### Jobseeker Service — `/api/jobseekers`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/jobseekers/{userId}` | Get job seeker profile |
| `POST` | `/api/jobseekers/create/{userId}` | Create job seeker profile |
| `GET` | `/job-seeker-profile/` | Show job seeker profile page |
| `POST` | `/job-seeker-profile/addNew` | Update profile with photo/resume |

### Application Service — `/api/applications`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/applications/by-jobseeker/{userId}` | Get all job IDs a user applied to |
| `GET` | `/api/applications/by-job/{jobId}` | Get all applicant user IDs for a job |
| `GET` | `/job-details-apply/{id}` | Show job details with applicants list |
| `POST` | `/job-details-apply/{id}` | Submit a job application |

### Saved Jobs Service — `/api/saved-jobs`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/saved-jobs/by-jobseeker/{userId}` | Get all saved job IDs for a user |
| `GET` | `/api/saved-jobs/check/{userId}/{jobId}` | Check if a job is saved (boolean) |
| `POST` | `/job-details/save/{id}` | Save / bookmark a job |
| `GET` | `/saved-jobs/` | Show all saved jobs page |

---

## Security

### Authentication Flow

```
1. User submits credentials at POST /register/new or form login
        ↓
2. USER-SERVICE validates credentials via Spring Security
        ↓
3. CustomAuthenticationSuccessHandler generates a signed JWT
        ↓
4. JWT is stored in an HttpOnly cookie: JWT_TOKEN (24-hour expiry)
        ↓
5. All subsequent requests pass through the API Gateway
        ↓
6. JwtAuthenticationFilter validates the JWT from the cookie
        ↓
7. Gateway injects X-Auth-User and X-Auth-Roles headers
        ↓
8. GatewayAuthFilter in each backend service reads these headers
   and populates the SecurityContext — no session required
```

### Key Points

- **Stateless** — no server-side sessions; all identity is carried in the JWT.
- **HttpOnly cookie** — the JWT is not accessible to JavaScript, mitigating XSS token theft.
- **Gateway-only JWT validation** — backend services trust the headers injected by the gateway and do not re-validate the JWT themselves (internal network assumption).
- **Role-based access** — `UsersType` maps to Recruiter or Job Seeker roles, enforced in the security configuration of each service.

---

## Inter-Service Communication

Services communicate synchronously over HTTP using **OpenFeign** clients with Eureka-based load balancing. Each service defines Feign interfaces for the peers it depends on, along with a `Fallback` implementation to handle unavailability.

```
API Gateway
  └─→ All services (routing)

User Service
  ├─→ Recruiter Service  (resolve recruiter profile on login)
  └─→ Jobseeker Service  (resolve job seeker profile on login)

Job Service
  ├─→ User Service         (resolve email → user)
  ├─→ Recruiter Service    (recruiter info for listings)
  ├─→ Jobseeker Service    (job seeker info)
  ├─→ Application Service  (check if user has applied)
  └─→ Saved Jobs Service   (check if job is saved)

Application Service
  ├─→ User Service
  ├─→ Job Service
  ├─→ Jobseeker Service
  ├─→ Recruiter Service
  └─→ Saved Jobs Service

Saved Jobs Service
  ├─→ User Service
  ├─→ Job Service
  └─→ Jobseeker Service

Recruiter Service   └─→ User Service
Jobseeker Service   └─→ User Service
```

---

## Fault Tolerance

All Feign clients are protected by **Resilience4j** circuit breakers with the following configuration:

| Setting | Value |
|---|---|
| Connect Timeout | 3 seconds |
| Read Timeout | 5 seconds |
| Sliding Window Size | 5 requests |
| Failure Rate Threshold | 50% |
| Open State Duration | 10 seconds |
| Half-Open Permitted Calls | 3 |
| Max Retry Attempts | 3 |
| Retry Wait Duration | 1 second |

When the circuit is open (a downstream service is down), the Feign client's `Fallback` class returns a safe default response so the calling service degrades gracefully rather than failing completely.

---

## File Upload Configuration

| Service | Upload Path | Max File Size | Accepted Formats |
|---|---|---|---|
| Recruiter Service | `photos/recruiter/{userId}/` | 5 MB / 10 MB per request | JPEG, PNG |
| Jobseeker Service | `photos/candidate/{userId}/` | 5 MB / 10 MB per request | JPEG, PNG, PDF (resume) |

---
