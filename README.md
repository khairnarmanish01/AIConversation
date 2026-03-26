# AI Conversation Interface

A sleek, premium Android application that simulates a real-time conversational AI. This application
features an animated futuristic AI avatar with dynamic lip-syncing (viseme mapping), live
speech-to-text (STT) transcription, text-to-speech (TTS) responses, camera preview integration, and
dynamic language localization.

## 🚀 Setup Steps

To run this project on your local machine:

1. **Prerequisites**: Ensure you have [Android Studio](https://developer.android.com/studio) (Koala
   or newer recommended) installed.
2. **Clone / Open**: Open the project folder `AIConversation` directly in Android Studio.
3. **Sync Gradle**: Allow Gradle to sync and download all necessary Jetpack compose and core
   dependencies.
4. **Device/Emulator Requirements**:
    - Minimum API: 29 (Android 10)
    - A physical device or an emulator equipped with a working **Microphone** and **Camera**.
5. **Run the App**: Click the `Run` button (Shift+F10) in Android Studio to deploy an
   `assembleDebug` APK to your device.
6. **Permissions**: On first launch, grant the requested Camera and Microphone permissions to fully
   experience the visual and auditory feedback.

## 🏗 Architecture Summary

The project is built on a clean **MVVM (Model-View-ViewModel)** architecture utilizing **Jetpack
Compose** for a purely declarative UI, **StateFlow** for reactive state management, and **Jetpack
Navigation** for spatial, fluid screen routing.

## ✨ What was Implemented

* **Dynamic Lip-Sync & Avatar Animation**: A custom `AvatarView` powered by Compose Canvas and
  Spring animations. A dedicated `VisemeGenerator` analyzes TTS outputs to morph the AI's mouth and
  express states dynamically based on current spoken syllables.
* **Audio Engines (STT / TTS)**: Complete wrapping of Android's native `SpeechRecognizer` for
  continuous listening and `TextToSpeech` engines, complete with live partial-transcription
  rendering mimicking real-time processing.
* **Dual-Screen Layouts**: Side-by-side Chat & Camera preview UI to prevent component overlapping,
  decorated with premium gradient borders.
* **On-the-fly Localization**: Integrated robust Spanish & English translations. Powered by
  `DataStore` and a custom `CompositionLocalProvider` override in `MainActivity`, the UI translates
  instantly without requiring an Activity restart.
* **Premium Dark UI**: Established a unified "Neon Cyan & Primary Purple" aesthetic. This includes
  asymmetric chat bubbles, pulsating microphone animations, glassmorphic Settings cards, and deeply
  customized dark-mode Material dialogs.
* **Lifecycle Robustness**: Implemented `LifecycleEventObserver` to gracefully release camera and
  audio resources when navigating to background, handling dynamic permission revocations
  effectively.

## ⚖️ Assumptions & Tradeoffs

* **Mocked AI Intelligence**: For demonstration purposes, the application does not hit a remote LLM
  API. Instead, the `ConversationViewModel` cycles through an array of hardcoded Mock AI strings to
  mimic intelligent text generation.
* **Viseme Approximation Algorithm**: True acoustic lip-syncing requires complex waveform phase
  analysis. To minimize processing overhead on the UI thread, the `VisemeGenerator` instead maps
  vowel characters from the text string into recognizable lip shapes synchronously.
* **Aesthetic Strictness (Dark Theme Forced)**: The application uses forced deep-space dark colors (
  `Color(0xFF1E1E2C)`) and bright white text natively overrides the system's light/dark mode
  preference. This tradeoff was made to guarantee the futuristic "Neon" aesthetic cannot be broken
  by default system theming.
* **Full Hardware Reconstruction**: The `CameraPreview` and STT engines aggressively disconnect and
  reconnect whenever they are toggled or minimized. While a slight overhead is incurred doing this,
  it aggressively prevents hardware lockups or memory leaks across different Android devices. 
