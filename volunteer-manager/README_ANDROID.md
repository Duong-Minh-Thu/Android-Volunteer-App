# Volunteer Management Android App

This is a complete Android application built with Kotlin, following the requested 5-step development process.

## Features
1. **Authentication**: Email/Password login and registration with Student/ORG roles.
2. **Campaign List**: Facebook-style main feed with `RecyclerView`, `CardView`, and `SwipeRefreshLayout`.
3. **Double Tap to Favorite**: Custom `GestureDetector` in the adapter.
4. **Detail Screen**:
   - **Google Maps**: Visual marker for the event location.
   - **Location Services**: Real-time distance calculation from the user.
   - **Weather**: Integrated with OpenWeatherMap using Retrofit.
   - **Participation**: Join events with a single click.
5. **Background Notifications**: A background service that listens for new campaigns in the database and notifies the user.

## Setup Requirements
1. **Firebase**:
   - Add `google-services.json` to the `app/` directory.
   - Enable **Authentication** (Email/Password).
   - Enable **Realtime Database** and import `sample_database_data.json`.
   - Apply `database.rules.json` in the Firebase Console Rules tab.
2. **API Keys**:
   - **Google Maps**: Set your key in `AndroidManifest.xml` (or use the `${GOOGLE_MAPS_API_KEY}` injection).
   - **OpenWeatherMap**: Replace `YOUR_OPENWEATHER_KEY` in `DetailActivity.kt` with your actual API key.

## Tech Stack
- **Language**: Kotlin
- **Architecture**: ViewBinding, Model-View-Controller (Activity-centric)
- **Networking**: Retrofit 2 + Gson, Glide for images
- **Firebase**: Auth, Realtime Database, Messaging
- **Services**: Google Play Services (Maps, Location)
- **Background**: Service + ChildEventListener for real-time updates.

## How to Build
Run `./gradlew assembleDebug` or open the project in Android Studio.
