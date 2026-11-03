# HeronQueue

<p align="center">
  <img src="src/main/resources/static/assets/logos/umak-logo-black.png" alt="University of Makati logo" width="220">
</p>

<p align="center">
  <strong>A Priority Queue-based ticket queueing system for University of Makati student services.</strong>
</p>

## About the Project

HeronQueue is a web-based queue management system designed to make student-service
transactions more organized and efficient. Students can get a Regular or Priority
ticket from the kiosk, staff can call and manage the next ticket, and a public monitor
can display the current queue status.

This project was developed as a **DATASTRU - Data Structures and Algorithms final
case study**. Its core algorithm demonstrates how a **Priority Queue implemented as
a binary heap** can serve priority tickets before regular tickets while preserving
fair ordering within each category.

## Objectives

- Reduce manual queue management for University of Makati student services.
- Provide students with a clear ticket number and queue position.
- Serve Priority tickets before Regular tickets.
- Maintain first-in-order service within the same ticket category.
- Provide live views for kiosk, staff, and monitor users.
- Demonstrate practical use of data structures, algorithms, OOP, and concurrency.

## Key Features

- **Student kiosk** - Join the Regular queue or the Priority queue for PWDs, senior citizens, and other approved priority service, then receive a display code.
- **Priority-based serving** - Priority tickets are called before Regular tickets.
- **Fair tie-breaking** - Tickets with the same type are ordered by queue number,
  then UUID as a final deterministic tie-breaker.
- **Staff dashboard** - Call next, recall, or mark the current ticket as no-show.
- **Public monitor** - Display the current ticket and the ordered waiting list.
- **Daily numbering** - Regular and Priority queue numbers start at 1 each day.
- **Queue recovery** - Waiting tickets are restored from the database after restart.
- **History** - View completed and no-show tickets served during the current day.
- **Text-to-speech announcement** - The browser announces the ticket currently being served.

## Data Structures and Algorithms

### Priority Queue / Binary Heap

The backend uses Java's `PriorityQueue`, which is implemented as a min-oriented
binary heap using a custom comparator:

1. Priority tickets come before Regular tickets.
2. Lower queue numbers come first within the same ticket type.
3. UUID provides a deterministic final tie-breaker.

| Operation | Complexity |
|---|---:|
| Insert a ticket | O(log n) |
| Remove the next ticket | O(log n) |
| Read queue size | O(1) |
| Get the next ticket | O(1) |
| Sort a copy for display | O(n log n) |

The queue manager uses a `ReentrantLock` to protect operations that update the
current ticket, database state, and in-memory heap together. This prevents race
conditions when multiple staff requests arrive at the same time.

## Technology Stack

- **Backend:** Java 17, Spring Boot 4, Spring Web MVC
- **Persistence:** Spring Data JPA, PostgreSQL
- **Local development database:** H2
- **Frontend:** HTML, CSS, Vanilla JavaScript
- **Build tool:** Maven Wrapper
- **Concurrency:** Java `ReentrantLock`

## Project Structure

```text
src/
├── main/
│   ├── java/com/umak/heronqueue/
│   │   ├── queue/
│   │   │   ├── QueueController.java
│   │   │   ├── QueueManager.java
│   │   │   └── TicketPriorityQueue.java
│   │   └── ticket/
│   │       ├── Ticket.java
│   │       ├── TicketRepository.java
│   │       ├── TicketStatus.java
│   │       └── TicketType.java
│   └── resources/
│       ├── static/
│       │   ├── kiosk.html
│       │   ├── staff.html
│       │   ├── monitor.html
│       │   └── app.js
│       ├── application.properties
│       ├── application-local.properties
│       └── application-prod.properties
└── test/
    └── java/com/umak/heronqueue/
```

## Running Locally

### Prerequisites

- Java 17 or later
- Git
- Internet access on the first Maven build so dependencies can be downloaded

### Start the application

```bash
git clone https://github.com/luigibalingit14/HERONQUEUE.git
cd HERONQUEUE
./mvnw spring-boot:run
```

On Windows, use:

```powershell
.\mvnw.cmd spring-boot:run
```

The local profile uses an in-memory H2 database. Open:

- Kiosk: `http://localhost:8080/kiosk.html`
- Staff dashboard: `http://localhost:8080/staff.html`
- Public monitor: `http://localhost:8080/monitor.html`

The app listens on port `8080` by default and uses the `PORT` environment
variable when provided by a hosting platform. The frontend and queue API are
served from the same origin, so the browser uses the deployed host automatically.

### Run tests

```bash
./mvnw test
```

On Windows:

```powershell
.\mvnw.cmd test
```

## Production Database Configuration

The production profile reads database settings from environment variables. Copy
`.env.example` as a reference and provide the real values through your deployment
environment. Do not commit passwords, tokens, or private credentials.

```text
SPRING_PROFILES_ACTIVE=prod
SUPABASE_JDBC_URL=your-jdbc-url
SUPABASE_DB_USER=your-database-user
SUPABASE_DB_PASSWORD=your-database-password
```

## Deploy to Render

This repository includes a Render Blueprint (`render.yaml`) and a Dockerfile.
To deploy:

1. Create a Supabase project, run `src/main/resources/supabase_schema.sql` in
   the Supabase SQL Editor, and prepare its PostgreSQL connection URL, database
   username, and password. Use the connection details recommended by Supabase
   for your deployment region.
2. In Render, choose **New → Blueprint** and connect this GitHub repository.
3. Before creating the service, enter `SUPABASE_JDBC_URL`, `SUPABASE_DB_USER`,
   and `SUPABASE_DB_PASSWORD` as secret environment values when prompted.
   The production profile requires these values; they are not stored in Git.
4. Deploy the `heronqueue` web service. Render builds the Docker image and
   redeploys when new commits are pushed to `main`.
5. Once deployment is healthy, open `/kiosk.html`, `/staff.html`, or
   `/monitor.html` on the Render service URL.

The free Render plan may spin down an idle service, so its first request after
inactivity can take longer. Persistent queue records are stored in Supabase,
not in the service container.

## API Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/queue/join` | Add a Regular or Priority ticket |
| `POST` | `/api/queue/next` | Call the next ticket |
| `GET` | `/api/queue/status` | Get the current ticket and waiting queue |
| `POST` | `/api/queue/skip` | Mark the current ticket as no-show |
| `POST` | `/api/queue/recall` | Recall the current ticket |
| `GET` | `/api/queue/history` | Get today's completed and no-show tickets |

## Team Members

- **BALINGIT, LUIGI**
- **DE LEON, EDRIAN JOSH**
- **EVANGELIO, FRANCHESKA GAILE**
- **FANIA, ZAIREN**
- **JIMENEZ, CARL KIAN**
- **PABILANI, KIZZIAH AHERICA**
- **PAGSISIHAN, JASON ZAKI**
- **RIÑON, JETT LOUEGE**
- **STAGEN, STANLEY FOX**
- **TENEDERO, HANNAH NIOBE**

## Academic Use and Attribution

HeronQueue is an academic case study project for the University of Makati.
The UMak logo and other University of Makati brand assets are included for
authorized academic project use. Please see
[`src/main/resources/static/assets/COPYRIGHT.md`](src/main/resources/static/assets/COPYRIGHT.md)
for the applicable copyright and trademark notice.

---

**HeronQueue** - Queueing student services with data structures and algorithms.
