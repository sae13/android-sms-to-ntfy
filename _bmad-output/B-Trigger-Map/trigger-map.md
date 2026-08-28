# Trigger Map Poster: sms-ntfy-android-app

> Visual overview connecting business goals to user psychology

**Created:** 2026-08-28
**Author:** Saeb
**Methodology:** Based on Effect Mapping (Balic & Domingues), adapted for WDS framework

---

## Strategic Documents

This is the visual overview. For detailed documentation, see:

- **01-Business-Goals.md** - Full vision statements and SMART objectives
- **02-Target-Groups.md** - All personas with complete driving forces
- **03-Feature-Impact-Analysis.md** - Prioritized features with impact scores
- **04-08-\*.md** - Individual persona detail files

---

## Vision

Create a fully functional native Kotlin Android application that forwards incoming SMS and calls to a self-hosted ntfy server, enables remote SMS replies via SSE, and includes a professional web dashboard. KMP remains an experimental scaffold without production feature parity.

---

## Business Objectives

### Objective 1: Deliver Android app that forwards SMS and calls to ntfy server with remote reply capability

- **Metric:** Percentage of SMS/calls successfully forwarded and replied
- **Target:** 99.9%
- **Timeline:** 3 months

### Objective 2: Provide professional web dashboard with live simulator and logs

- **Metric:** User satisfaction score
- **Target:** 4.5/5
- **Timeline:** 4 months

### Objective 3: Ensure zero Google Play Services dependency and LAN-only operation

- **Metric:** Number of Google dependencies
- **Target:** 0
- **Timeline:** 3 months

---

## Target Groups (Prioritized)

### 1. Individual Users

**Priority Reasoning:** Primary users needing personal SMS/call forwarding to self-hosted server

> Tech-savvy individuals who value privacy and self-hosting, want reliable forwarding without Google services.

**Key Positive Drivers:**
- Privacy
- Reliability
- Offline capability
- Customizability

**Key Negative Drivers:**
- Complex setup
- Battery drain
- Unreliable delivery

### 2. Small Businesses

**Priority Reasoning:** Need to forward business communications to internal systems

> Small business owners seeking low-cost communication forwarding to internal ticketing or notification systems.

**Key Positive Drivers:**
- Cost-effectiveness
- Integration with internal systems
- Scalability

**Key Negative Drivers:**
- Security concerns
- Lack of support
- Downtime risk

---

## Trigger Map Visualization

```mermaid
%%{init: {'theme':'base', 'themeVariables': { 'fontFamily':'Inter, system-ui, sans-serif', 'fontSize':'14px'}}}%%
flowchart LR
    %% Business Goals (Left)
    BG0["<br/>📱 SMS & Call Forwarding<br/><br/>Receive all incoming SMS and calls<br/>Extract sender info and message content<br/>Send to ntfy server via POST<br/>"]
    BG1["<br/>🔁 Remote SMS Reply<br/><br/>Listen to ntfy reply topic via SSE<br/>Send SMS as reply to original sender<br/>"]
    BG2["<br/>📊 Web Dashboard<br/><br/>Display live events<br/>Simulate SMS/call/test<br/>Manage settings<br/>"]

    %% Central Platform
    PLATFORM["<br/>⚙️ SMS-to-Ntfy Bridge<br/><br/>Private communication bridge<br/><br/>From device notifications to server notifications and back<br/><br/>"]

    %% Target Groups
    TG0["<br/>👤 Individual Users<br/>Priority<br/><br/>Individual Users<br/>Tech-savvy individuals who value privacy and self-hosting, want reliable forwarding without Google services.<br/>"]
    TG1["<br/>🏢 Small Businesses<br/>Priority<br/><br/>Small Businesses<br/>Small business owners seeking low-cost communication forwarding to internal ticketing or notification systems.<br/>"]

    %% Driving Forces
    DF0["<br/>👤 Individual Users'S DRIVERS<br/><br/>WANTS<br/>✅ Privacy<br/>✅ Reliability<br/>✅ Offline capability<br/>✅ Customizability<br/>FEARS<br/>❌ Complex setup<br/>❌ Battery drain<br/>❌ Unreliable delivery<br/>"]
    DF1["<br/>🏢 Small Businesses'S DRIVERS<br/><br/>WANTS<br/>✅ Cost-effectiveness<br/>✅ Integration with internal systems<br/>✅ Scalability<br/>FEARS<br/>❌ Security concerns<br/>❌ Lack of support<br/>❌ Downtime risk<br/>"]

    %% Connections
    BG0 --> PLATFORM
    BG1 --> PLATFORM
    BG2 --> PLATFORM
    PLATFORM --> TG0
    TG0 --> DF0
    PLATFORM --> TG1
    TG1 --> DF1

    %% Light Gray Styling with Dark Text
    classDef businessGoal fill:#f3f4f6,color:#1f2937,stroke:#d1d5db,stroke-width:2px
    classDef platform fill:#e5e7eb,color:#111827,stroke:#9ca3af,stroke-width:3px
    classDef targetGroup fill:#f9fafb,color:#1f2937,stroke:#d1d5db,stroke-width:2px
    classDef drivingForces fill:#f3f4f6,color:#1f2937,stroke:#d1d5db,stroke-width:2px
    class BG0 businessGoal
    class BG1 businessGoal
    class BG2 businessGoal
    class PLATFORM platform
    class TG0 targetGroup
    class DF0 drivingForces
    class TG1 targetGroup
    class DF1 drivingForces

```

---

## Design Focus Statement
Ensure the system addresses core drivers of privacy, reliability, and offline capability while minimizing complexity and battery impact.

**Primary Design Target:** Individual Users

**Must Address:**
- Privacy
- Reliability
- Offline capability

**Should Address:**
- Customizability
- Integration with internal systems

---

## Cross-Group Patterns

### Shared Drivers
Privacy, Reliability, Offline capability

### Unique Drivers
Individual Users: Customizability
Small Businesses: Integration with internal systems, Cost-effectiveness

---

## Next Steps
- [ ] **Review detailed docs** - See 01-Business-Goals.md, 02-Target-Groups.md, 03-Feature-Impact-Analysis.md
- [ ] **Use for Feature Prioritization** - Reference feature impact scores
- [ ] **Guide UX Design** - Ensure designs address priority drivers
- [ ] **Validate with Users** - Test assumptions with real target group members
- [ ] **Update as Learnings Emerge** - This is a living document

---

_Generated with Whiteport Design Studio framework_
_Trigger Mapping methodology credits: Effect Mapping by Mijo Balic & Ingrid Domingues (inUse), adapted with negative driving forces_
