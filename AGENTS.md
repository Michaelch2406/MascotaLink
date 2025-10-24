# Repository Guidelines

## Project Structure & Module Organization

- Root Gradle settings and build:
  - build.gradle (top-level plugins)
  - settings.gradle (project name MascotaLink and includes :app)
  - gradle.properties (AndroidX, JVM args)
  - gradlew / gradlew.bat, gradle/ (wrapper)
- Android application module:
  - app/
    - build.gradle (Android config, dependencies)
    - proguard-rules.pro
    - src/
      - main/
        - AndroidManifest.xml
        - java/
          - com/mjc/mascotalink/*.java (activities, adapters, services, app class)
          - com/mjc/mascotalink/modelo/*.java
          - com/mjc/mascota/modelo/*.java
          - com/mjc/mascota/ui/{busqueda,perfil}/*.java
          - com/mjc/mascota/utils/*.java
        - res/ (layouts, drawables, values, xml, mipmaps)
      - test/ (unit tests placeholder)
      - androidTest/ (instrumented tests placeholder)
- Documentation:
  - BUSQUEDA_PASEADORES_README.md

## Build, Test, and Development Commands

```bash
# Build (assemble all variants)
./gradlew build

# Clean build outputs
./gradlew clean

# Run unit tests
./gradlew test

# Run instrumented Android tests (requires device/emulator)
./gradlew connectedAndroidTest

# Install debug APK to a connected device
./gradlew :app:installDebug
```

## Coding Style & Naming Conventions

- Indentation: 4 spaces (Java/Kotlin default in Android Studio). Keep consistent with existing files.
- File naming:
  - Activities: PascalCase ending with Activity (e.g., LoginActivity.java)
  - Adapters/Repositories/ViewModels: PascalCase with clear suffix (e.g., FavoritosAdapter.java, PaseadorRepository.java)
  - Models: PascalCase nouns (e.g., PaseadorResultado.java)
- Function/variable naming: camelCase for methods and variables; CONSTANTS_UPPER_SNAKE for constants.
- Linting/formatting: Use Android Studio’s Reformat Code and Optimize Imports. Lint baseline: app/lint-baseline.xml. Respect warnings and do not introduce new ones.

## Testing Guidelines

- Frameworks: JUnit 4, Mockito, AndroidX test (as declared in app/build.gradle).
- Test files: Place unit tests under app/src/test/java and instrumented tests under app/src/androidTest/java. Mirror package structure of production code.
- Running tests: `./gradlew test` for unit tests, `./gradlew connectedAndroidTest` for instrumented tests.
- Coverage: No explicit coverage thresholds found in repo.

## Commit & Pull Request Guidelines

- Commit format: Prefer concise, imperative messages (e.g., "Add BusquedaPaseadoresActivity and adapters"). No conventional commit enforcement found.
- PR process: Not specified in repo. Recommended: include description, screenshots for UI changes, and test notes.
- Branch naming: Not specified. Suggested: feature/<short-desc>, fix/<short-desc>, chore/<short-desc>.

---

# Repository Tour

## 🎯 What This Repository Does

MascotaLink is an Android application that connects pet owners with dog walkers, featuring profile management, search, maps, media capture, and Firebase integration.

Key responsibilities:
- User onboarding, authentication flows, and role-based registration for owners and walkers
- Search and discovery of walkers with filters, maps, and pagination
- Profile management, gallery, favorites, and reviews

---

## 🏗️ Architecture Overview

### System Context
```
[User on Android device] → [MascotaLink Android app] → [Firebase (Auth, Firestore, Storage, Messaging), Google APIs (Maps/Places)]
                                     ↓
                               [Local Services (Foreground uploads)]
```

### Key Components
- Activities (com.mjc.mascotalink.*): Screens for login, registration, profiles, media capture, availability, zones, quizzes, etc.
- Search module (com.mjc.mascota.ui.busqueda): ViewModel, repository, adapters for walker discovery; integrates with Firestore and Google Maps.
- Models (com.mjc.mascota.modelo, com.mjc.mascotalink.modelo): Data classes like PaseadorResultado, Resena, PaseadorFavorito.
- Services/Helpers: FileUploadService, FileStorageHelper for background uploads and storage operations.
- Application class: MyApplication for app-wide initialization (declared in AndroidManifest.xml).

### Data Flow
1. User interacts with Activities (e.g., BusquedaPaseadoresActivity, LoginActivity).
2. ViewModels/Repositories fetch and combine data, primarily from Firebase Firestore and Auth.
3. UI Adapters render lists (popular/results) and maps; Glide loads images; Google Maps displays markers.
4. Storage and background operations use FileUploadService and Firebase Storage; navigation returns UI updates.

---

## 📁 Project Structure [Partial Directory Tree]

```
MascotaLink/
├── build.gradle                  # Top-level plugins
├── settings.gradle               # Name and module includes
├── gradle.properties             # Gradle/AndroidX config
├── gradlew, gradlew.bat, gradle/ # Gradle wrapper
├── BUSQUEDA_PASEADORES_README.md # Search module documentation
├── app/
│   ├── build.gradle              # Android, deps, product config
│   ├── proguard-rules.pro        # ProGuard/R8 rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/
│       │   │   ├── com/mjc/mascotalink/*.java
│       │   │   ├── com/mjc/mascotalink/modelo/*.java
│       │   │   ├── com/mjc/mascota/modelo/*.java
│       │   │   ├── com/mjc/mascota/ui/{busqueda,perfil}/*.java
│       │   │   └── com/mjc/mascota/utils/*.java
│       │   └── res/               # layouts, drawables, values, xml
│       ├── test/                  # unit tests
│       └── androidTest/           # instrumented tests
```

### Key Files to Know

| File | Purpose | When You'd Touch It |
|------|---------|---------------------|
| app/src/main/AndroidManifest.xml | Declares activities, services, permissions, metadata (Maps API key) | Add screens, services, or permissions |
| app/build.gradle | Module config, SDK versions, dependencies (Firebase, Maps, CameraX, ML Kit) | Bump SDKs, add libraries |
| build.gradle (root) | Top-level plugin declarations | Change global plugin versions |
| settings.gradle | Module includes and repository setup | Add/remove modules |
| app/src/main/java/com/mjc/mascota/ui/busqueda/BusquedaPaseadoresActivity.java | Walker search UI and logic | Extend filters, pagination, map UI |
| app/src/main/java/com/mjc/mascota/ui/busqueda/PaseadorRepository.java | Data access for search | Modify queries to Firestore |
| app/src/main/java/com/mjc/mascotalink/FileUploadService.java | Foreground file upload service | Adjust upload behavior |
| app/src/main/java/com/mjc/mascotalink/MyApplication.java | Application initialization | Global setup, SDK init |
| BUSQUEDA_PASEADORES_README.md | Detailed search module docs | Understand/search feature details |

---

## 🔧 Technology Stack

### Core Technologies
- Language: Java (Android app code in .java files)
- Framework/SDK: Android SDK (compileSdk 36, targetSdk 34, minSdk 32)
- Backend-as-a-Service: Firebase (Analytics, Auth, Firestore, Storage, Messaging, Functions)
- Maps & Location: Google Maps SDK, Places API, Location Services

### Key Libraries
- Glide and CircleImageView for image loading and circular avatars
- CameraX for camera and video
- FirebaseUI Auth for simplified auth flows
- Android Maps Utils for map utilities
- AndroidX core libraries: appcompat, material, activity, constraintlayout, swiperefreshlayout

### Development Tools
- Gradle (Android Gradle Plugin via version catalog `libs.plugins.android.application`)
- Android Lint with baseline (app/lint-baseline.xml)
- Testing: JUnit 4, Mockito, AndroidX test libraries

---

## 🌐 External Dependencies

- Firebase: Firestore (data), Auth (authentication), Storage (media), Messaging (push), Analytics; configured via google-services plugin and google-services.json (present in repo).
- Google APIs: Maps SDK, Places API, Location Services; requires MAPS_API_KEY via manifestPlaceholders sourced from local.properties (mapsApiKey).

### Environment Variables

Configured via Gradle manifestPlaceholders reading from local.properties:

```bash
# local.properties
mapsApiKey=YOUR_KEY
```

The app reads MAPS_API_KEY from this property. Do not commit secrets; keep local.properties out of VCS.

---

## 🔄 Common Workflows

### Add a new screen (Activity)
1. Create Activity class under com.mjc.mascotalink.
2. Add layout in res/layout.
3. Declare Activity in AndroidManifest.xml.
4. Wire navigation from existing Activity.

### Extend walker search filters
1. Adjust UI chips in activity_busqueda_paseadores.xml.
2. Update BusquedaPaseadoresActivity and PaseadorRepository queries.
3. Verify indexes in Firestore as documented in BUSQUEDA_PASEADORES_README.md.

---

## 📈 Performance & Scale

- Firebase queries should use indexes as documented to avoid client-side filtering overhead.
- Use pagination (already implemented in search) to limit reads.
- Image loading handled by Glide; prefer placeholders and caching.

---

## 🚨 Things to Be Careful About

### Security Considerations
- Do not hardcode API keys. MAPS_API_KEY is injected via manifest placeholders.
- Follow Firebase security rules for Firestore/Storage to protect user data.
- Permissions: App requests camera, location, notifications, external storage, and foreground service. Request runtime permissions where needed and handle denial flows.


Update to last commit: [Unavailable]
Updated at: 2025-10-23 (UTC)
