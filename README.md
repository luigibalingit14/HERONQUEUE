<p align="center">
  <img src="src/main/resources/static/assets/logos/umak-logo-black.png" alt="University of Makati Logo" width="160" style="vertical-align: middle; margin-right: 25px;">
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="src/main/resources/static/assets/logos/ccis-logo.png" alt="College of Computing and Information Sciences Logo" width="160" style="vertical-align: middle; margin-left: 25px;">
</p>

<h1 align="center">HERONQUEUE</h1>

<p align="center">
  <strong>An Intelligent Priority Queue Management System for University of Makati Student Services</strong><br>
  <em>University of Makati • College of Computing and Information Sciences (CCIS)</em><br>
  <strong>DATASTRU – Data Structures and Algorithms Final Case Study Project</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Database-Supabase_PostgreSQL-3ECF8E?style=for-the-badge&logo=supabase&logoColor=white" alt="Supabase PostgreSQL">
  <img src="https://img.shields.io/badge/Data_Structure-Binary_Min--Heap-111C4E?style=for-the-badge" alt="Binary Min-Heap">
  <img src="https://img.shields.io/badge/UMak-CCIS-177D44?style=for-the-badge" alt="UMak CCIS">
</p>

---

## 🏛️ Executive Summary

**HeronQueue** is a robust, web-based priority queuing system designed to streamline student transactions across campus service offices at the **University of Makati** (such as the Registrar, Accounting Office, OSAS, and Admissions). 

Developed as an academic case study for **DATASTRU (Data Structures and Algorithms)** under **Mr. Roel C. Traballo (Faculty, IT Department)**, HeronQueue addresses real-world congestion in campus hallways by replacing manual lines with an intelligent, priority-aware virtual queue powered by a custom **Binary Min-Heap Priority Queue**.

---

## 👥 Project Team (CCIS - IT Department)

| Role | Name | Contribution Focus |
| :--- | :--- | :--- |
| **Team Leader** | **BALINGIT, LUIGI** | Project Architecture, Full-Stack Integration, & Presentation Lead |
| **Member** | **DE LEON, EDRIAN JOSH** | Problem Analysis, System Objectives & Documentation |
| **Member** | **EVANGELIO, FRANCHESKA GAILE** | Data Structure Design: Priority Queue vs. Regular Queue |
| **Member** | **FANIA, ZAIREN** | Comparator Implementation & Priority Rules Engine |
| **Member** | **JIMENEZ, CARL KIAN** | Enqueue Algorithm & Binary Heapify-Up (Shift Up) |
| **Member** | **PABILANI, KIZZIAH AHERICA** | Dequeue Algorithm & Binary Heapify-Down (Shift Down) |
| **Member** | **PAGSISIHAN, JASON ZAKI** | Time & Space Complexity Mathematical Analysis |
| **Member** | **RIÑON, JETT LOUEGE** | Concurrency, `ReentrantLock` Thread-Safety & Heap Restoration |
| **Member** | **STAGEN, STANLEY FOX** | Live UI Demonstration (Kiosk, Monitor & Staff Console) |
| **Member** | **TENEDERO, HANNAH NIOBE** | Real-world UMak Impact, Case Study Synthesis & Q&A |

---

## 🎯 Case Study Objectives

1. **Eliminate Hallway Congestion:** Provide students with instant queue tickets and real-time live position feedback so they can wait comfortably around campus.
2. **Mandatory Priority Lane Compliance:** Ensure PWDs, senior citizens, and pregnant students are legally and promptly prioritized ahead of standard service lanes.
3. **Data Structure Rigor:** Demonstrate practical in-memory execution of a **Binary Min-Heap** with strict logarithmic complexity guarantees.
4. **Resilience & Zero Data Loss:** Guarantee that active queue numbers and tickets persist across power outages, system reboots, and network hiccups.
5. **Anti-Spam & Fair Service:** Protect the physical kiosk and staff consoles from accidental spamming, rage-tapping, or skipping.

---

## 🔬 Core Data Structures & Algorithm Design

### 1. Binary Min-Heap Priority Queue
Rather than relying on unsorted arrays or linked lists that require $O(n)$ linear searches or $O(n \log n)$ repetitive sorting upon every call, HeronQueue implements a custom binary min-heap structure (`TicketPriorityQueue.java`).

The heap is represented efficiently inside an array using standard binary tree arithmetic:
- **Parent Index:** $\text{parent}(i) = \lfloor (i - 1) / 2 \rfloor$
- **Left Child Index:** $\text{left}(i) = 2i + 1$
- **Right Child Index:** $\text{right}(i) = 2i + 2$

### 2. Multi-Tier Comparator Rules (`TICKET_ORDER`)
The ordering of items in the heap is determined by a deterministic three-stage comparator:
1. **Priority Rank Tier:** Priority tickets (`PWD / Senior / Pregnant`) receive Rank `0`, while Regular tickets receive Rank `1`. The min-heap always elevates Rank `0` to the root.
2. **FIFO Queue Number Tier:** Within the same ticket category, tickets are strictly ordered by ascending `queue_number` (`R-001` before `R-002`), preserving absolute fairness.
3. **Deterministic Tie-Breaker:** A unique UUID guarantees that identical timestamps or edge-case numbers never collide.

### 3. Complexity Matrix

| Operation | Method | Time Complexity | Space Complexity | Description |
| :--- | :--- | :---: | :---: | :--- |
| **Enqueue** | `add(Ticket)` | **$O(\log n)$** | $O(1)$ aux | Inserts node at leaf and executes *Heapify-Up* |
| **Dequeue** | `removeNext()` | **$O(\log n)$** | $O(1)$ aux | Removes root, places last leaf at root, executes *Heapify-Down* |
| **Peek / Size** | `size()` | **$O(1)$** | $O(1)$ | Immediate direct inspection of active queue size |
| **Sorted Snapshot**| `sortedTickets()` | **$O(n \log n)$** | $O(n)$ | Safe sorted snapshot stream for public monitor displays |
| **Heap Restore** | `restore(List)` | **$O(n \log n)$** | $O(n)$ | Re-inserts all active tickets upon application startup |

---

## 🛡️ Enterprise Engineering Features

- **Concurrency Control (`ReentrantLock`):** Atomic execution of queue read, numbering, and insertion ensures that simultaneous taps from multiple kiosks never create duplicate ticket numbers.
- **Anti-Spam Cooldown & Throttling:**
  - **Kiosk:** 6-second persistent countdown with `sessionStorage` tracking and `pointer-events: none` protection.
  - **Staff Console:** 3-second action cooldown preventing accidental double-clicking of *"Call next ticket"*.
  - **Backend Throttling:** REST endpoint rate limiting via `X-Client-Id` returning HTTP `429 Too Many Requests`.
- **Text-to-Speech Announcements:** Built-in Web Speech Synthesis automatically announces ticket numbers (e.g., *"Now serving P-001"*).
- **Daily Automatic Numbering Reset:** Scoped to Philippine Standard Time (`Asia/Manila`), queue numbers reset to `001` every midnight.

---

## 🖥️ System Architecture & Interfaces

```text
       ┌────────────────────────┐         ┌────────────────────────┐
       │   Student Kiosk UI     │         │   Staff Dashboard UI   │
       │   (kiosk.html)         │         │   (staff.html)         │
       └───────────┬────────────┘         └───────────┬────────────┘
                   │                                  │
                   │ POST /join                       │ POST /next
                   ▼                                  ▼
      ┌─────────────────────────────────────────────────────────────┐
      │            Spring Boot REST Controller (QueueController)     │
      │            • Client Rate Limiting (X-Client-Id)             │
      └──────────────────────────────┬──────────────────────────────┘
                                     │
                                     ▼
      ┌─────────────────────────────────────────────────────────────┐
      │                 QueueManager (Service Layer)                │
      │                 • ReentrantLock Concurrency Control         │
      └──────────────┬──────────────────────────────┬───────────────┘
                     │                              │
                     ▼ (In-Memory RAM)              ▼ (Persistent Storage)
      ┌─────────────────────────────┐ ┌─────────────────────────────┐
      │     TicketPriorityQueue     │ │      Supabase PostgreSQL    │
      │     (Binary Min-Heap)       │ │      (Spring Data JPA)      │
      └─────────────────────────────┘ └─────────────────────────────┘
```

1. **Student Kiosk (`/kiosk.html`):** Allows students to select between Regular and Priority categories, receives their printed/screen ticket, and displays people ahead.
2. **Staff Console (`/staff.html`):** Operations panel allowing staff to call next, recall, or mark no-shows with active queue breakdown metrics.
3. **Public TV Display Monitor (`/monitor.html`):** Hallway TV display showing the currently serving ticket and the upcoming priority queue.

---

## 🚀 Running Locally

### Prerequisites
- **JDK 17** or higher
- **Git**
- Modern web browser (Chrome, Edge, Firefox, Safari)

### Quick Start
```bash
# Clone the repository
git clone https://github.com/luigibalingit14/HERONQUEUE.git
cd HERONQUEUE

# Run with Maven Wrapper (Windows PowerShell)
.\mvnw.cmd spring-boot:run
```

Once started, open your browser:
- **Kiosk:** [http://localhost:8080/kiosk.html](http://localhost:8080/kiosk.html)
- **Staff Console:** [http://localhost:8080/staff.html](http://localhost:8080/staff.html)
- **Public Monitor:** [http://localhost:8080/monitor.html](http://localhost:8080/monitor.html)

---

## ☁️ Deployment on Render

This repository is pre-configured with a Render Docker Blueprint (`render.yaml`).
- **Runtime:** Docker Container (`eclipse-temurin:17-jre`)
- **Auto-Deploy:** Enabled upon push to `main` branch.
- **Environment Variables Required:**
  - `SPRING_PROFILES_ACTIVE=prod`
  - `SUPABASE_JDBC_URL`
  - `SUPABASE_DB_USER`
  - `SUPABASE_DB_PASSWORD`

---

## 📜 Academic Attribution & Copyright Notice

This software is an original academic project developed by students of the **College of Computing and Information Sciences (CCIS)** at the **University of Makati**. All university logos and brand trademarks belong to their respective holders and are utilized strictly under educational and fair academic use.
