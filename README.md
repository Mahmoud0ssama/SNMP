# SNMP Network Management Platform

> A telecom-grade SNMP trap monitoring platform for simulated network elements.

## Table of Contents

- [1. Project Overview](#1-project-overview)
- [2. System Architecture](#2-system-architecture)
- [3. Technology Stack](#3-technology-stack)
- [4. Quick Start](#4-quick-start)
- [5. Enterprise OID Tree](#5-enterprise-oid-tree)
- [6. Database Schema](#6-database-schema)
- [7. Emulator Component](#7-emulator-component)
- [8. Server Component](#8-server-component)
- [9. REST API Reference](#9-rest-api-reference)
- [10. Automated Actions](#10-automated-actions)
- [11. Docker Telecom Nodes](#11-docker-telecom-nodes)
- [12. Project Structure](#12-project-structure)
- [13. Testing](#13-testing)

---

## 1. Project Overview

The SNMP Network Management Platform is a full-stack monitoring system consisting of two independent Java applications that communicate over the SNMP protocol:

### Components

| Component | Package | Description |
|-----------|---------|-------------|
| **Telecom Node Emulator** | `com.snmp.emulator` | Simulates telecom network elements and generates SNMPv2c trap messages. Offers both a **JavaFX GUI** and a **CLI** interface. |
| **SNMP Manager Server** | `com.snmp.manager` | Listens for incoming SNMP traps on UDP port 162, parses the structured payload, identifies or auto-registers the source node, and persists alarm history into **PostgreSQL**. Includes a REST API and web dashboard. |

---

## 2. System Architecture

```
┌─────────────────┐     SNMPv2c Trap (UDP)      ┌──────────────────────┐
│  Telecom Node   │ ────────────────────────────▶│  SNMP Manager Server │
│    Emulator     │                              │  (Port 8080 + 162)   │
│                 │◀────────────────────────────│                      │
│  • GUI Mode     │      REST API (JWT)          │  • TrapReceiver      │
│  • CLI Mode     │                              │  • TrapParser        │
│  • SnmpService  │                              │  • TrapService       │
└─────────────────┘                              │  • NodeService       │
       ▲                                          │  • SnmpPoller        │
       │ Docker Containers                        │  • HeartbeatService  │
       │                                          │  • DiscoveryService  │
       │                                          │  • AiAnalysisService │
       │                                          └──────────┬───────────┘
       │                                                     │ JDBC
       │                                                     ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         PostgreSQL                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐ │
│  │    nodes    │  │ trap_actions│  │ trap_history│  │   users    │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java (JDK) | 21 | Core runtime & language |
| SNMP4J | 3.9.3 / 3.12.2 | SNMP protocol library |
| JavaFX | 21.0.3 | GUI framework for emulator |
| PostgreSQL | Latest | Relational database |
| PostgreSQL JDBC | 42.7.7 | Database connectivity driver |
| HikariCP | 5.1.0 | Connection pooling |
| Javalin | 6.1.3 | Web server & REST API |
| Jackson | 2.17.2 | JSON serialization |
| JWT (auth0) | 4.4.0 | Authentication tokens |
| BCrypt | 0.4 | Password hashing |
| Twilio SDK | 8.31.1 | SMS notifications |
| Angus Mail | 2.0.3 | Email notifications |
| Maven | - | Build & dependency management |
| Docker | - | Telecom node simulation |

---

## 4. Quick Start

### Prerequisites

- JDK 21+
- Maven 3.8+
- PostgreSQL 12+
- Docker & Docker Compose (for telecom nodes)

### Build

```bash
# Build both modules
mvn clean package

# Or build individually
cd emulator && mvn clean package
cd snmp-server && mvn clean package
```

### Run the Server

```bash
# Configure database connection in snmp-server/src/main/resources/database.properties

# Run the server (starts REST API on 8080, SNMP trap receiver on 162)
java -jar snmp-server/target/snmp-manager-1.0-SNAPSHOT.jar
```

### Run the Emulator

```bash
# GUI Mode
java -jar emulator/target/emulator-1.0-SNAPSHOT.jar --gui

# CLI Mode
java -jar emulator/target/emulator-1.0-SNAPSHOT.jar --cli <NodeName> <NodeType> <NodeIP> <AlarmType> [Details] <TargetIP> <TargetPort>
```

### Run Telecom Nodes (Docker)

```bash
cd docker
docker compose up -d
```

---

## 5. Enterprise OID Tree

Custom MIB structure under Private Enterprise `1.3.6.1.4.1.99999`. Convention: `.0.x` for notifications, `.1.x` for data objects.

```
1.3.6.1.4.1.99999                    ← Enterprise Root
│
├── .0.x  Notifications              (Trap OIDs)
│   ├── .0.1  Disk Full
│   ├── .0.2  Power Failure
│   ├── .0.3  Link Down
│   ├── .0.4  Congestion
│   ├── .0.5  High Temperature
│   ├── .0.6  Memory Exhaustion
│   └── .0.7  Configuration Error
│
└── .1.x  Objects                    (Variable Bindings)
    ├── .1.1  nodeName               e.g., "Cairo_BTS_01"
    ├── .1.2  nodeType               e.g., "BTS"
    ├── .1.3  details                e.g., "/dev/sda1 at 98%"
    └── .1.4  nodeIp                 e.g., "10.0.0.5"
```

### Alarm Types & Severity Mapping

| Trap OID | Alarm Name | Severity | Action | Node Status → |
|----------|-----------|----------|--------|---------------|
| `...99999.0.1` | Disk Full | **CRITICAL** | NOTIFY_ADMIN | DOWN |
| `...99999.0.2` | Power Failure | **CRITICAL** | NOTIFY_ADMIN | DOWN |
| `...99999.0.3` | Link Down | **MAJOR** | CHECK_LINK | WARNING |
| `...99999.0.4` | Congestion | **MINOR** | LOG_ONLY | WARNING |
| `...99999.0.5` | High Temperature | **MAJOR** | NOTIFY_ADMIN | WARNING |
| `...99999.0.6` | Memory Exhaustion | **CRITICAL** | RESTART_SERVICE | DOWN |
| `...99999.0.7` | Configuration Error | **MINOR** | LOG_ONLY | WARNING |

---

## 6. Database Schema

### ERD

```
USERS ||--o{ TRAP_HISTORY : "resolves"
NODES ||--o{ TRAP_HISTORY : "generates"
TRAP_ACTIONS ||--o{ TRAP_HISTORY : "defines"
```

### ENUM Types

| Enum | Values |
|------|--------|
| `node_status` | UP, DOWN, WARNING, UNKNOWN |
| `trap_severity` | INFO, MINOR, MAJOR, CRITICAL |
| `trap_status` | OPEN, ACKNOWLEDGED, RESOLVED |

### Tables

#### `nodes`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PK | Unique identifier |
| `name` | VARCHAR(100) | NOT NULL | Node alias |
| `node_type` | VARCHAR(50) | NULLABLE | Equipment type |
| `ip_address` | VARCHAR(45) | UNIQUE, NOT NULL | IPv4/IPv6 address |
| `port` | INTEGER | DEFAULT 162 | SNMP port |
| `location` | VARCHAR(255) | NULLABLE | Physical location |
| `description` | TEXT | NULLABLE | Free-text description |
| `status` | `node_status` | DEFAULT 'UP' | Current operational status |
| `created_at` | TIMESTAMP | DEFAULT NOW | Registration timestamp |

#### `trap_actions`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PK | Unique identifier |
| `trap_oid` | VARCHAR(150) | UNIQUE, NOT NULL | SNMP Trap OID |
| `trap_name` | VARCHAR(100) | NOT NULL | Human-readable alarm name |
| `severity` | `trap_severity` | NOT NULL | Alarm severity level |
| `action_name` | VARCHAR(100) | NOT NULL | Action identifier |
| `description` | TEXT | NULLABLE | Action description |
| `auto_resolve` | BOOLEAN | DEFAULT FALSE | Auto-resolve flag |
| `action_type` | VARCHAR(20) | NOT NULL | EMAIL, SMS, or SCRIPT |
| `target_payload` | VARCHAR(255) | NULLABLE | Target (email, phone, script path) |
| `created_at` | TIMESTAMP | DEFAULT NOW | Creation timestamp |
| `updated_at` | TIMESTAMP | DEFAULT NOW | Last update timestamp |

#### `trap_history`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PK | Unique identifier |
| `node_id` | BIGINT | FK → nodes.id | Source node |
| `trap_action_id` | BIGINT | FK → trap_actions.id | Matched action definition |
| `trap_oid` | VARCHAR(150) | NOT NULL | Received trap OID |
| `source_ip` | VARCHAR(45) | NOT NULL | Source IP address |
| `message` | TEXT | NULLABLE | Alarm name + details |
| `status` | `trap_status` | DEFAULT 'OPEN' | Lifecycle status |
| `received_at` | TIMESTAMP | DEFAULT NOW | When trap was received |
| `resolved_at` | TIMESTAMP | NULLABLE | When issue was resolved |
| `resolved_by` | BIGINT | FK → users.id | Who resolved it |

#### `users`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PK | Unique identifier |
| `username` | VARCHAR(50) | UNIQUE, NOT NULL | Login username |
| `password_hash` | VARCHAR(255) | NOT NULL | BCrypt hashed password |
| `role` | VARCHAR(20) | DEFAULT 'SUPPORT' | ADMIN or SUPPORT |
| `created_at` | TIMESTAMP | DEFAULT NOW | Registration timestamp |

---

## 7. Emulator Component

### Class Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                            Main                                  │
│                   java -jar emulator.jar                         │
│   ├── --gui  ──▶ EmulatorGUI (JavaFX)                           │
│   └── --cli  ──▶ EmulatorCLI                                    │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────┐     ┌──────────────────────────────────────┐
│  EmulatorGUI     │────▶│      EmulatorController              │
│  (JavaFX)        │     │  • nodeNameField : TextField          │
└──────────────────┘     │  • nodeTypeDropdown : ComboBox        │
                         │  • alarmTypeDropdown : ComboBox       │
┌──────────────────┐     │  • detailsField : TextField           │
│  EmulatorCLI     │────▶│  • ipField : TextField                │
│  (Command Line)  │     │  • portField : TextField              │
└──────────────────┘     │  + initialize()                       │
                         │  + onSendButtonClicked()              │
                         └──────────────────┬───────────────────┘
                                            │ sendTrap()
                         ┌──────────────────▼───────────────────┐
                         │              SnmpService               │
                         │  • sendTrap(nodeName, nodeType,       │
                         │    alarmType, details, targetIp,       │
                         │    targetPort)                         │
                         └──────────────────┬───────────────────┘
                                            │ uses
                         ┌──────────────────▼───────────────────┐
                         │              AlarmType (Enum)          │
                         │  DISK_FULL, POWER_FAILURE, LINK_DOWN, │
                         │  CONGESTION, HIGH_TEMPERATURE,        │
                         │  MEMORY_EXHAUSTION, CONFIG_ERROR      │
                         └───────────────────────────────────────┘
```

### Supported Node Types

| Type | Full Name |
|------|-----------|
| `BTS` | Base Transceiver Station |
| `BSC` | Base Station Controller |
| `MSC` | Mobile Switching Center |
| `HLR` | Home Location Register |
| `VLR` | Visitor Location Register |
| `SGSN` | Serving GPRS Support Node |
| `GGSN` | Gateway GPRS Support Node |

### Usage Examples

```bash
# GUI Mode
java -jar emulator.jar --gui

# CLI Mode (without details)
java -jar emulator.jar --cli Cairo_BTS_01 BTS 10.0.0.5 DISK_FULL 127.0.0.1 162

# CLI Mode (with details)
java -jar emulator.jar --cli Cairo_BTS_01 BTS 10.0.0.5 DISK_FULL "/dev/sda1 at 98%" 127.0.0.1 162

# Valid AlarmTypes:
DISK_FULL  POWER_FAILURE  LINK_DOWN  CONGESTION  HIGH_TEMPERATURE  MEMORY_EXHAUSTION  CONFIG_ERROR
```

---

## 8. Server Component

### Class Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        TrapReceiver (UDP :162)                          │
│  • start() / stop()                                                     │
│  • addTrapListener(TrapListener)                                        │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ parse
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           TrapParser                                    │
│  • parse(event) → TrapEvent                                             │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ creates
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            TrapEvent                                    │
│  • sourceIp, trapOid, nodeName, nodeType, details, community, version   │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ via TrapListener
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           TrapService                                   │
│  • process(TrapEvent)                                                   │
│  • buildHistory() / resolveStatus() / extractIp()                       │
│  • clearUnreachableTraps(nodeId)                                        │
│  • recalculateNodeStatus(nodeId)                                        │
└──────┬──────────────────────┬──────────────────────┬────────────────────┘
       │                      │                      │
       ▼                      ▼                      ▼
┌──────────────┐   ┌──────────────────┐   ┌──────────────────┐
│  NodeService │   │  TrapActionDAO   │   │  TrapHistoryDAO  │
└──────┬───────┘   └────────┬─────────┘   └────────┬─────────┘
       │                    │                       │
       ▼                    ▼                       ▼
┌──────────────┐   ┌──────────────────┐   ┌──────────────────┐
│   NodeDAO    │   │  trap_actions    │   │  trap_history    │
└──────────────┘   └──────────────────┘   └──────────────────┘
```

### Key Subsystems

| Subsystem | Class | Description |
|-----------|-------|-------------|
| **Trap Receiver** | `TrapReceiver` | Opens UDP socket on port 162, listens for SNMP traps |
| **Trap Parser** | `TrapParser` | Extracts structured fields from raw PDU |
| **Trap Service** | `TrapService` | Core business logic: node lookup, auto-registration, severity resolution, persistence |
| **Node Service** | `NodeService` | Node CRUD operations, status updates |
| **SNMP Poller** | `SnmpPoller` | Active SNMP GET polling for node metrics |
| **Heartbeat** | `HeartbeatService`, `SnmpHealthMonitor` | Periodic SNMP-based health monitoring, auto-recovery |
| **Discovery** | `DiscoveryService` | Auto-discovers nodes via SNMP GET in IP range |
| **AI Analysis** | `AiAnalysisService` | AI-powered insights, chat, and script safety evaluation |
| **Security** | `JwtUtil` | JWT token generation and verification |

### Processing Logic

When a trap is received:

1. **Extract IP** — Strip port from peer address
2. **Node Lookup** — Query `nodes` table by IP
3. **Auto-Register** — If not found, create node from trap payload
4. **Action Lookup** — Match trap OID against `trap_actions`
5. **Persist History** — INSERT into `trap_history`
6. **Update Node Status** — CRITICAL → DOWN, MAJOR/MINOR → WARNING, INFO → UP

---

## 9. REST API Reference

Base URL: `http://localhost:8080`

All endpoints except `/api/login` require JWT authentication via `Authorization: Bearer <token>` header.

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/login` | Login with username/password, returns JWT |

Request body:
```json
{
  "username": "admin",
  "password": "password"
}
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "role": "ADMIN"
}
```

### Dashboard

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/nodes` | Get all registered nodes |
| `GET` | `/api/traps` | Get all trap history records |
| `GET` | `/api/nodes/probe?ip=<ip>` | Probe a node via SNMP GET |
| `PUT` | `/api/traps/{id}/resolve` | Resolve a trap by ID |
| `GET` | `/api/ai/insights` | Get AI-generated NOC insights |
| `POST` | `/api/ai/chat` | Chat with AI NOC assistant |

### User Management (Admin only)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/users` | List all users |
| `POST` | `/api/users` | Create new user |
| `PUT` | `/api/users/{id}` | Update user |
| `DELETE` | `/api/users/{id}` | Delete user |

### Trap Configuration

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/trap-templates` | Get distinct trap templates |
| `GET` | `/api/nodes/{id}/trapActions` | Get trap actions for a node |

### Node Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/node-types` | Get distinct node types |
| `POST` | `/api/nodes` | Register new node (Admin only) |
| `PUT` | `/api/nodes/{id}` | Update node (Admin only) |
| `POST` | `/api/discovery` | Discover and register nodes via SNMP (Admin only) |
| `GET` | `/api/nodes/{id}/metrics` | Get SNMP metrics for a node |

### Script Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/upload-script` | Upload remediation script (AI safety check) |

### Force Execute

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/traps/{id}/force-execute` | Force execute script action for a trap |

### Static Files

The server serves a web dashboard from `/public` (classpath). Access at `http://localhost:8080` after starting the server.

---

## 10. Automated Actions

When a trap is received, the server matches it against `trap_actions` and executes the configured action type:

| Action Type | Description | Implementation |
|-------------|-------------|----------------|
| `SCRIPT` | Execute a shell script on the target node | `ScriptExecutor.executeRemote()` |
| `EMAIL` | Send email notification | `EmailNotifier.send()` via Jakarta Mail |
| `SMS` | Send SMS notification | `SmsNotifier.send()` via Twilio |

### Script Execution Flow

1. Trap received → matched to `trap_actions` entry with `action_type = 'SCRIPT'`
2. Server resolves container name from node name
3. Script is executed remotely via Docker exec
4. On success: trap is resolved, node status recalculated
5. On failure: error logged, trap remains OPEN

### AI Script Safety

Before scripts are executed or uploaded, they pass through an AI safety evaluation (`AiAnalysisService.evaluateScriptSafety()`). Unsafe scripts are blocked with a clear violation reason.

---

## 11. Docker Telecom Nodes

### Network Topology

The Docker Compose setup creates a `telecom_net` bridge network (`172.25.0.0/24`) with simulated telecom nodes:

| Region | Subnet | Nodes |
|--------|--------|-------|
| Core | `172.25.0.5-6` | Core_HLR, Core_SGSN |
| Cairo | `172.25.0.10-13` | Cairo_BTS_01, Cairo_BSC_01, Cairo_MSC_01, Cairo_BTS_02 |
| Giza | `172.25.0.20-23` | Giza_BSC_01, Giza_BTS_01, Giza_BTS_02, Giza_MSC_01 |
| Alex | `172.25.0.30-33` | Alex_MSC, Alex_BSC_01, Alex_BTS_01, Alex_BTS_02 |
| Tanta | `172.25.0.40-43` | Tanta_BSC_01, Tanta_BTS_01, Tanta_BTS_02, Tanta_MSC_01 |

### Node Configuration

Each node container uses `hardware_specs.cfg` for configuration:

```properties
NODE_NAME=Cairo_BTS_01
NODE_TYPE=BTS
VENDOR=Ericsson
MAX_FREQ=900
SECTOR_COUNT=3
```

### Base Image

Nodes run on Alpine Linux with:
- `net-snmp` — SNMP agent
- `stress-ng` — CPU/memory stress simulation
- `lm-sensors` — Temperature monitoring
- Custom monitoring scripts in `/usr/local/bin/`

### Starting the Network

```bash
cd docker
docker compose up -d
```

---

## 12. Project Structure

```
ITI_GP/
├── README.md                    # This file
├── documentation.html           # Interactive HTML documentation
├── NeonSchema.txt               # PostgreSQL schema
├── ERD.html                     # Entity relationship diagram
├── docker/
│   ├── docker-compose.yml       # Telecom node definitions
│   ├── Dockerfile.telecom-node  # Alpine-based node image
│   ├── config/snmpd.conf        # SNMP agent config
│   ├── scripts/                 # Node monitoring scripts
│   │   ├── entrypoint.sh
│   │   ├── fault_monitor_daemon.sh
│   │   ├── check_disk.sh
│   │   ├── check_temp.sh
│   │   ├── check_congestion.sh
│   │   ├── send_trap.sh
│   │   └── get_node_info.sh
│   └── nodes/                   # Per-node hardware configs
│       ├── cairo-bts-01/hardware_specs.cfg
│       ├── cairo-bts-02/hardware_specs.cfg
│       └── ...
│
├── emulator/
│   └── pom.xml
│   └── src/main/
│       ├── java/com/snmp/emulator/
│       │   ├── Main.java              # Entry point (GUI/CLI selection)
│       │   ├── EmulatorGUI.java       # JavaFX GUI launcher
│       │   ├── EmulatorController.java # GUI logic & FXML controller
│       │   ├── EmulatorCLI.java       # Command-line interface
│       │   ├── SnmpService.java       # SNMP trap construction & sending
│       │   └── AlarmType.java         # Alarm type enumeration
│       └── resources/
│           ├── dashboard.fxml         # JavaFX layout
│           ├── dark-theme.css         # Dark mode styles
│           └── light-theme.css        # Light mode styles
│
├── snmp-server/
│   └── pom.xml
│   └── src/main/
│       ├── java/com/snmp/manager/
│       │   ├── Main.java              # Server entry point
│       │   ├── config/
│       │   │   └── DatabaseConnection.java # HikariCP connection pool
│       │   ├── model/
│       │   │   ├── Node.java
│       │   │   ├── NodeStatus.java    # UP, DOWN, WARNING, UNKNOWN
│       │   │   ├── TrapAction.java
│       │   │   ├── TrapHistory.java
│       │   │   ├── TrapSeverity.java  # INFO, MINOR, MAJOR, CRITICAL
│       │   │   ├── TrapStatus.java    # OPEN, ACKNOWLEDGED, RESOLVED
│       │   │   └── User.java
│       │   ├── dao/
│       │   │   ├── NodeDAO.java
│       │   │   ├── TrapActionDAO.java
│       │   │   ├── TrapHistoryDAO.java
│       │   │   └── UserDAO.java
│       │   ├── service/
│       │   │   ├── TrapService.java   # Core trap processing
│       │   │   ├── NodeService.java   # Node business logic
│       │   │   ├── DiscoveryService.java # SNMP-based discovery
│       │   │   └── AiAnalysisService.java # AI insights & chat
│       │   ├── heartbeat/
│       │   │   └── service/
│       │   │       └── HeartbeatService.java # Node health tracking
│       │   ├── security/
│       │   │   └── JwtUtil.java       # JWT token management
│       │   ├── util/
│       │   │   ├── ScriptExecutor.java  # Remote script execution
│       │   │   ├── EmailNotifier.java   # Email notifications
│       │   │   └── SmsNotifier.java     # SMS notifications
│       │   └── snmp/
│       │       ├── listener/
│       │       │   └── TrapListener.java
│       │       ├── model/
│       │       │   └── TrapEvent.java
│       │       ├── parser/
│       │       │   └── TrapParser.java
│       │       ├── receiver/
│       │       │   └── TrapReceiver.java
│       │       ├── poller/
│       │       │   ├── SnmpPoller.java
│       │       │   └── SnmpGetResult.java
│       │       ├── monitor/
│       │       │   └── SnmpHealthMonitor.java
│       │       └── util/
│       │           └── OidResolver.java
│       └── resources/
│           └── public/
│               └── index.html         # Web dashboard
│
└── scripts/                           # Server-side remediation scripts
    ├── remediate_disk_full.sh
    ├── remediate_high_temp.sh
    ├── remediate_congestion.sh
    └── ...
```

---

## 13. Testing

### Run Tests

```bash
# Run all tests
mvn test

# Run tests for specific module
cd snmp-server && mvn test
cd emulator && mvn test
```

### Test Dependencies

- JUnit 5.10.0
- Mockito 5.7.0

---

## PDU Structure

When the emulator sends a trap, the PDU contains:

| Binding | OID | Example Value |
|---------|-----|---------------|
| `sysUpTime` | `1.3.6.1.2.1.1.3.0` | 5000 (timeticks) |
| `snmpTrapOID` | `1.3.6.1.6.3.1.1.4.1.0` | `1.3.6.1.4.1.99999.0.1` (Disk Full) |
| `nodeName` | `1.3.6.1.4.1.99999.1.1` | Cairo_BTS_01 |
| `nodeType` | `1.3.6.1.4.1.99999.1.2` | BTS |
| `details` | `1.3.6.1.4.1.99999.1.3` | /dev/sda1 at 98% |
| `nodeIp` | `1.3.6.1.4.1.99999.1.4` | 10.0.0.5 |

---

## License

ITI Graduation Project · 2026
