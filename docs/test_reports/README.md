# Test Reports Data Documentation

## Directory Structure

```
docs/test_reports/
├── blood_tests/           # Lab work and blood panel results (11 PDFs)
├── dexa_scans/            # DEXA body composition scans (6 PDFs)
├── workout_logs/          # Exercise tracking data (CSV)
├── design_mockups/        # UI reference designs (WebP)
└── README.md              # This documentation
```

---

## Data Types

### 1. Blood Tests (`blood_tests/`)

**Format:** PDF (33-110 KB each, 2-5 pages)
**Naming:** UUID-based identifiers (e.g., `015753ce-f615-48ef-bad4-dc845e9214b9.pdf`)
**Count:** 11 reports

**Description:**
Laboratory blood test results containing standard biomarker panels. Reports are image-based PDFs that will require OCR or manual data entry for structured extraction.

**Expected Data Fields:**
| Category | Biomarkers |
|----------|------------|
| Complete Blood Count | WBC, RBC, hemoglobin, hematocrit, platelets, MCV, MCH, MCHC |
| Metabolic Panel | Glucose, BUN, creatinine, eGFR, sodium, potassium, chloride, CO2 |
| Lipid Panel | Total cholesterol, LDL, HDL, triglycerides, VLDL |
| Liver Function | AST, ALT, ALP, bilirubin, albumin, total protein |
| Thyroid | TSH, T3, T4, free T4 |
| Hormones | Testosterone, estradiol, cortisol, DHEA-S |
| Vitamins/Minerals | Vitamin D, B12, folate, iron, ferritin, magnesium |
| Inflammation | CRP, ESR, homocysteine |

---

### 2. DEXA Scans (`dexa_scans/`)

**Format:** PDF (1.4-2.0 MB each), image-based
**Naming:** Sequential (`DXAReport (0).pdf` through `DXAReport (5).pdf`)
**Count:** 6 reports

**Description:**
Dual-Energy X-ray Absorptiometry scans measuring body composition and bone density. Large image-based PDFs containing scan visualizations and measurement tables.

**Expected Data Fields:**
| Category | Metrics |
|----------|---------|
| Body Composition | Total body fat %, lean mass (lbs/kg), fat mass (lbs/kg), bone mineral content |
| Regional Fat | Arms, legs, trunk, android (abdominal), gynoid (hip) |
| Regional Lean | Arms, legs, trunk percentages and mass |
| Bone Density | T-score, Z-score, BMD (g/cm²) |
| Visceral Fat | VAT mass, VAT volume, VAT area |
| Ratios | Android/Gynoid ratio, Fat Mass Index (FMI), Lean Mass Index (LMI) |

---

### 3. Workout Logs (`workout_logs/`)

**Format:** CSV (477 KB)
**File:** `future_workout_history.csv`
**Rows:** 5,589 exercise records
**Date Range:** March 2023 - November 2025

**Description:**
Structured exercise tracking data from a workout program. Each row represents a single exercise set within a workout session.

**CSV Schema:**
| Column | Type | Description | Example |
|--------|------|-------------|---------|
| (index) | integer | Row number | 1 |
| Full Name | string | User name | "Evan Ruff" |
| Workout Completed (Local) Time | datetime | ISO 8601 timestamp | "2025-11-24 15:48:37" |
| Actual Workout Duration (sec) | integer | Session duration in seconds | 1931 |
| Is Future Workout (Yes / No) | string | Future workout flag | "Yes" |
| Workout Name | string | Program day identifier | "Day 1.1" |
| Exercise Name | string | Exercise performed | "Barbell Bench Press" |
| Actual Weight of Set (lbs) | integer | Weight used (0 for bodyweight) | 205 |

**Workout Program Structure:**
- Day 1.x: Upper body focus (Bench Press, Lat Pull-Down, etc.)
- Day 2.x: Lower body focus (Squats, Deadlifts, etc.)
- Day 3.x: Push/accessories
- Day 4.x: Pull/accessories

**Exercise Categories:**
- Compound lifts: Bench Press, Squat, Deadlift, Overhead Press
- Isolation: Curls, Extensions, Raises
- Mobility/Recovery: Stretches, Bird Dogs, Prayer Stretch, Recover

---

### 4. Design Mockups (`design_mockups/`)

**Format:** WebP (113 KB, 2038x1358px)
**File:** `db36c524-ba7b-4861-9f33-44c9095899e6.webp`

**Description:**
UI/UX reference design for the health and fitness mobile application, showing dashboard layouts and key metric displays.

---

## Proposed Firestore Schema

### Subcollections vs Top-Level Collections

**Subcollection**: A collection nested within a document (e.g., `users/{userId}/biomarkers/{id}`).
- Pros: Automatic scoping to parent, simpler security rules
- Cons: Can't query across all users, harder to share data

**Top-Level Collection with References**: Flat collections with foreign keys (e.g., `biomarkers` collection with `userId` and `bloodTestId` fields).
- Pros: Easier cross-user queries, more flexible relationships
- Cons: More complex security rules, manual denormalization

For this schema, we use a **hybrid approach**: user-scoped data uses subcollections, shared reference data (exercises) is top-level.

---

### Collection Hierarchy

```
# Top-Level Collections (shared/reference data)
exercises/{exerciseId}                    # Master exercise library

# User-Scoped Collections
users/{userId}
├── profile (document fields)
├── bloodTests/{testId}
├── biomarkers/{biomarkerId}              # Flat, references bloodTestId
├── dexaScans/{scanId}
├── dexaRegionalData/{regionId}           # Flat, references scanId
├── workoutRoutines/{routineId}           # Template: defines exercise sequence
├── workoutSessions/{sessionId}           # Actual workout, references routineId
├── exerciseSets/{setId}                  # Individual sets, references sessionId + exerciseId
└── documents/{documentId}
```

---

### Document Structures

#### `exercises/{exerciseId}` - Master Exercise Library (Top-Level)
```typescript
{
  id: string;                     // Auto-generated or slug: "barbell-bench-press"
  name: string;                   // "Barbell Bench Press"
  category: 'compound' | 'isolation' | 'mobility' | 'recovery' | 'cardio';
  muscleGroups: string[];         // ["chest", "triceps", "shoulders"]
  primaryMuscle: string;          // "chest"
  equipment: string[];            // ["barbell", "bench"]
  isBodyweight: boolean;
  instructions: string;
  videoUrl: string | null;
  createdAt: Timestamp;
}
```

**Sample Exercises from Data:**
| Exercise | Category | Primary Muscle | Equipment |
|----------|----------|----------------|-----------|
| Barbell Bench Press | compound | chest | barbell, bench |
| Dumbbell Goblet Squat | compound | quadriceps | dumbbell |
| Supinated Cable Lat Pull-Down | compound | back | cable machine |
| Prayer Stretch | mobility | back | bodyweight |
| Quad Rockers | mobility | quadriceps | bodyweight |
| Recover | recovery | - | - |

---

#### `users/{userId}` - User Profile
```typescript
{
  uid: string;                    // Firebase Auth UID
  email: string;
  displayName: string;
  createdAt: Timestamp;
  updatedAt: Timestamp;
  settings: {
    units: 'imperial' | 'metric';
    timezone: string;
  };
}
```

---

#### `users/{userId}/workoutRoutines/{routineId}` - Workout Templates
```typescript
{
  id: string;
  name: string;                   // "Day 1.1" or "Arm Farm 🐄"
  description: string;
  programName: string | null;     // "Phase 1", "Hypertrophy Block"
  routineType: 'structured' | 'themed' | 'deload';

  // Ordered list of exercises in this routine
  exerciseSequence: [
    {
      order: number;              // 1, 2, 3...
      exerciseId: string;         // Reference to exercises collection
      exerciseName: string;       // Denormalized for quick display
      targetSets: number;         // e.g., 3
      targetReps: string;         // e.g., "8-12" or "AMRAP"
      targetWeight: number | null;// Suggested weight (null for bodyweight)
      restSeconds: number;        // Rest between sets
      notes: string;              // "Warm-up set first"
    }
  ];

  estimatedDuration: number;      // minutes
  muscleGroupsFocus: string[];    // ["chest", "triceps"]
  isActive: boolean;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}
```

**Routine Patterns Found in Data:**
| Routine Name | Type | Focus |
|--------------|------|-------|
| Day 1.1, Day 1.2, ... | structured | Upper body (bench, lat pull-down) |
| Day 2.1, Day 2.2, ... | structured | Lower body (squat, deadlift) |
| Day X.5 DE-LOAD | deload | Recovery week |
| "Arm Farm 🐄" | themed | Arms isolation |
| "KB on the ⏰" | themed | Kettlebell circuit |
| "Back Focus 💪" | themed | Back emphasis |

---

#### `users/{userId}/workoutSessions/{sessionId}` - Logged Workouts
```typescript
{
  id: string;

  // Links to routine template (nullable for ad-hoc workouts)
  routineId: string | null;       // Reference to workoutRoutines
  routineName: string;            // Denormalized: "Day 1.1"

  // Session metadata
  completedAt: Timestamp;
  duration: number;               // seconds (from CSV: 1931, 2752, etc.)
  isFuture: boolean;              // Planning vs completed

  // Denormalized summary for dashboards
  summary: {
    exerciseCount: number;
    totalSets: number;
    totalVolume: number;          // sum(weight × reps) across all sets
    topExercise: string;
    topWeight: number;
  };

  notes: string;
  createdAt: Timestamp;
}
```

---

#### `users/{userId}/exerciseSets/{setId}` - Individual Sets Performed
```typescript
{
  id: string;

  // Foreign keys
  sessionId: string;              // Reference to workoutSessions
  exerciseId: string;             // Reference to exercises (top-level)

  // Denormalized for query efficiency
  exerciseName: string;           // "Barbell Bench Press"
  sessionDate: Timestamp;         // Copy of session.completedAt for range queries
  routineId: string | null;       // Copy for filtering by routine

  // Set data
  setNumber: number;              // 1, 2, 3 within this exercise in this session
  orderInSession: number;         // Global order across all exercises (1-20+)
  weight: number;                 // lbs (0 for bodyweight)
  reps: number | null;            // null if not tracked (mobility/recovery)
  rpe: number | null;             // Rate of Perceived Exertion (1-10)
  isWarmup: boolean;

  createdAt: Timestamp;
}
```

**Example from CSV Data:**
One session (2025-11-24 15:48:37) becomes:
```
workoutSession: { id: "abc", routineName: "Day 1.1", duration: 1931 }

exerciseSets:
  { sessionId: "abc", exerciseName: "Dumbbell Goblet Squat", setNumber: 1, weight: 75 }
  { sessionId: "abc", exerciseName: "Barbell Bench Press", setNumber: 1, weight: 150 }
  { sessionId: "abc", exerciseName: "Barbell Bench Press", setNumber: 2, weight: 170 }
  { sessionId: "abc", exerciseName: "Barbell Bench Press", setNumber: 3, weight: 205 }
  { sessionId: "abc", exerciseName: "Supinated Cable Lat Pull-Down", setNumber: 1, weight: 0 }
  { sessionId: "abc", exerciseName: "Recover", setNumber: 1, weight: 0 }
```

---

#### `users/{userId}/bloodTests/{testId}` - Blood Test Results
```typescript
{
  id: string;
  testDate: Timestamp;
  labName: string;
  documentRef: string;            // Cloud Storage path
  uploadedAt: Timestamp;
  status: 'pending' | 'processed' | 'verified';
  notes: string;

  // Summary metrics (denormalized for dashboard)
  summary: {
    totalCholesterol: number | null;
    ldl: number | null;
    hdl: number | null;
    glucose: number | null;
    testosterone: number | null;
    vitaminD: number | null;
  };
}
```

#### `users/{userId}/biomarkers/{biomarkerId}` - Individual Biomarker Results
```typescript
{
  id: string;
  bloodTestId: string;            // Reference to bloodTests
  testDate: Timestamp;            // Denormalized for time-series queries

  name: string;                   // "LDL Cholesterol"
  code: string;                   // "LDL" (standardized)
  category: string;               // "lipid_panel"
  value: number;
  unit: string;                   // "mg/dL"
  referenceRange: {
    low: number;
    high: number;
  };
  flag: 'normal' | 'high' | 'low' | 'critical';
}
```

---

#### `users/{userId}/dexaScans/{scanId}` - DEXA Results
```typescript
{
  id: string;
  scanDate: Timestamp;
  facility: string;
  documentRef: string;
  uploadedAt: Timestamp;
  status: 'pending' | 'processed' | 'verified';

  bodyComposition: {
    totalBodyFatPercent: number;
    totalFatMass: number;         // lbs
    totalLeanMass: number;
    boneMineralContent: number;
    totalMass: number;
  };

  boneDensity: {
    tScore: number;
    zScore: number;
    bmd: number;                  // g/cm²
  };

  visceralFat: {
    mass: number;                 // grams
    volume: number;               // cm³
    area: number;                 // cm²
  };

  ratios: {
    androidGynoid: number;
    fatMassIndex: number;
    leanMassIndex: number;
  };
}
```

#### `users/{userId}/dexaRegionalData/{regionId}` - DEXA Regional Measurements
```typescript
{
  id: string;
  scanId: string;                 // Reference to dexaScans
  scanDate: Timestamp;            // Denormalized for trends

  region: 'left_arm' | 'right_arm' | 'left_leg' | 'right_leg' | 'trunk' | 'android' | 'gynoid';
  fatPercent: number;
  fatMass: number;
  leanMass: number;
  boneMineralContent: number;
}
```

---

#### `users/{userId}/documents/{documentId}` - Document References
```typescript
{
  id: string;
  type: 'blood_test' | 'dexa_scan' | 'other';
  filename: string;
  storagePath: string;
  mimeType: string;
  sizeBytes: number;
  uploadedAt: Timestamp;
  linkedRecordId: string | null;  // Reference to bloodTests or dexaScans
  processingStatus: 'uploaded' | 'processing' | 'complete' | 'failed';
}
```

---

## Indexing Strategy

### Composite Indexes Required

```
Collection: exercises (top-level)
- category ASC, name ASC
- primaryMuscle ASC, name ASC

Collection: users/{userId}/workoutRoutines
- routineType ASC, name ASC
- isActive ASC, updatedAt DESC

Collection: users/{userId}/workoutSessions
- completedAt DESC
- routineId ASC, completedAt DESC

Collection: users/{userId}/exerciseSets
- sessionId ASC, orderInSession ASC       # All sets in a session
- exerciseId ASC, sessionDate DESC        # Exercise progression over time
- exerciseName ASC, sessionDate DESC      # Same, using denormalized name
- routineId ASC, exerciseId ASC, sessionDate DESC  # Exercise in specific routine

Collection: users/{userId}/bloodTests
- testDate DESC, status ASC

Collection: users/{userId}/biomarkers
- bloodTestId ASC, category ASC           # All markers for a test
- code ASC, testDate DESC                 # Single biomarker over time
- flag ASC, testDate DESC                 # Flagged results

Collection: users/{userId}/dexaScans
- scanDate DESC

Collection: users/{userId}/dexaRegionalData
- scanId ASC, region ASC
- region ASC, scanDate DESC               # Region trends over time
```

### Single-Field Indexes (Auto-created)
- All document IDs
- `completedAt`, `testDate`, `scanDate`, `sessionDate` (for range queries)
- `status`, `routineName`, `exerciseId`, `category`, `flag` (for equality filters)

---

## Common Query Patterns

| Use Case | Query |
|----------|-------|
| **Workouts** |
| All routines | `workoutRoutines` where `isActive == true` |
| Sessions for routine | `workoutSessions` where `routineId == X` ordered by `completedAt DESC` |
| Sets in a session | `exerciseSets` where `sessionId == X` ordered by `orderInSession ASC` |
| Exercise progression | `exerciseSets` where `exerciseId == X` ordered by `sessionDate DESC` |
| Bench press history | `exerciseSets` where `exerciseName == "Barbell Bench Press"` ordered by `sessionDate DESC` |
| Max weight for exercise | `exerciseSets` where `exerciseId == X` ordered by `weight DESC`, limit 1 |
| **Blood Tests** |
| Latest blood test | `bloodTests` ordered by `testDate DESC`, limit 1 |
| Cholesterol trend | `biomarkers` where `code == "LDL"` ordered by `testDate ASC` |
| Flagged biomarkers | `biomarkers` where `flag != "normal"` ordered by `testDate DESC` |
| All markers for test | `biomarkers` where `bloodTestId == X` |
| **DEXA** |
| Body fat trend | `dexaScans` ordered by `scanDate ASC` (read `bodyComposition.totalBodyFatPercent`) |
| Regional comparison | `dexaRegionalData` where `scanId == X` |
| Arm lean mass trend | `dexaRegionalData` where `region in ["left_arm", "right_arm"]` ordered by `scanDate ASC` |
| **Documents** |
| Recent uploads | `documents` ordered by `uploadedAt DESC` |

---

## Cloud Storage Structure

```
gs://{bucket}/
└── users/
    └── {userId}/
        ├── blood_tests/
        │   └── {uuid}.pdf
        ├── dexa_scans/
        │   └── DXAReport_{date}.pdf
        └── profile/
            └── avatar.jpg
```

---

## Data Migration Notes

### 1. Blood Tests
PDFs require OCR processing or manual extraction. Consider using Document AI or Vision API for automated extraction.

### 2. DEXA Scans
Image-heavy PDFs. Data extraction may need specialized parsing or manual entry until OCR pipeline is built.

### 3. Workout CSV Import

**Step 1: Build Exercise Library**
Extract unique exercise names and categorize:
```sql
SELECT DISTINCT "Exercise Name" FROM csv
-- Creates ~100+ exercises for the exercises collection
```

**Step 2: Extract Routine Templates**
Group by workout name to identify routine patterns:
```sql
SELECT "Workout Name", array_agg(DISTINCT "Exercise Name")
FROM csv GROUP BY "Workout Name"
-- Creates workoutRoutines with exerciseSequence
```

**Step 3: Create Sessions**
Group rows by timestamp to create sessions:
```sql
SELECT "Workout Completed (Local) Time", "Workout Name", "Actual Workout Duration (sec)"
FROM csv GROUP BY 1, 2, 3
-- Each unique timestamp = one workoutSession
```

**Step 4: Import Sets**
Each CSV row becomes one exerciseSet:
```
CSV Row → exerciseSet {
  sessionId: lookup by timestamp,
  exerciseId: lookup by name,
  exerciseName: "Exercise Name",
  weight: "Actual Weight of Set (lbs)",
  sessionDate: "Workout Completed (Local) Time"
}
```

**Transform Requirements:**
- Parse comma-formatted numbers: `"1,931"` → `1931`
- Normalize exercise names (trim, consistent casing)
- Infer `setNumber` by counting occurrences per exercise per session
- Map `routineId` from workout name lookup

### 4. Document Upload
All PDFs should be uploaded to Cloud Storage with Firestore references maintaining the relationship.
