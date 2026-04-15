# Our Memories Photo Booth 📸✨

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?logo=firebase&logoColor=white)](https://firebase.google.com/)
[![Gemini](https://img.shields.io/badge/AI-Gemini%202.0%20Flash-4285F4?logo=google-gemini&logoColor=white)](https://deepmind.google/technologies/gemini/)

An advanced, AI-powered photo booth application for Android that redefines the photography experience. **Our Memories** combines real-time computer vision, generative AI, and professional post-processing to create stunning, interactive memories.

---

## ✨ Key Features

### 🧠 Sparkle AI™ Intelligent Triggers
Forget manual timers. Our Memories uses low-latency AI analysis to capture the perfect moment automatically:
*   **Face Expression Detection**: Trigger shots by smiling, winking, or tilting your head.
*   **Hand Gesture Recognition**: Supports "Hi" (V-sign), Finger Heart, Thumbs Up, and more.
*   **Voice Commands**: Just shout "Cheese!" to capture.
*   **Smart Shuffling**: Configure your own trigger sequence for each photo session.

### 🎭 Professional Creative Suite
*   **Portrait Mode**: Advanced image segmentation to apply beautiful, professional-grade background blur.
*   **Batch Editing**: Apply filters, frames, and stickers across your entire session at once.
*   **Custom Stickers & Frames**: Choose from a curated library of Cute, Y2K, K-pop, and Classic styles.
*   **Video Timelapse**: Automatically generates a timelapse video of your photo session.

### 📊 Smart Admin Ecosystem
A comprehensive dashboard powered by **Gemini 2.0 Flash**:
*   **AI Data Insights**: Automatically analyzes user reviews and download trends to provide actionable business recommendations.
*   **Analysis Chatbot**: An interactive AI assistant to query app statistics and performance data.
*   **RBAC Management**: Role-based access control to manage regular, premium, and admin users.
*   **Sticker Management**: Upload and manage custom sticker assets directly from the admin panel.

---

## 🛠 Tech Stack

| Category | Technologies |
| :--- | :--- |
| **Mobile** | Java, Android SDK, CameraX |
| **Backend** | Firebase Auth (Google Sign-In), Firestore, Cloud Storage |
| **Core AI/ML** | ML Kit (Face/Gesture), MediaPipe, Portrait Processor |
| **Generative AI** | Google Gemini 2.0 Flash (via Cloud Functions) |
| **Image Loading** | Glide |
| **Architecture** | Clean Architecture (Data, Domain, UI layers) |

---

## 🔒 Security Architecture: Backend-First
Our Memories prioritizes security by adopting a **Backend-First** architecture:
*   **API Key Protection**: Sensitive keys (like Gemini API) are never stored on the client. All AI requests are routed through **Firebase Cloud Functions**.
*   **Secure Implementation**: This prevents exposure and ensures that expensive AI calls are authenticated and rate-limited.

---

## 📂 Project Structure

```bash
app/src/main/java/com/example/expressionphotobooth/
├── data/               # Data Layer: Firebase implementations and Local Storage
├── domain/             # Domain Layer: Use Cases (Analyzers, Processors) and Models
├── ui/                 # UI Layer: Custom Views and Adapters
├── MainActivity.java    # Core Camera Experience
├── EditPhotoActivity.java # Advanced Image Editing
├── AdminDashboardActivity.java # Admin Panel & Stats
└── LoginActivity.java   # Auth with Google Integration
```

---

## 🚀 Getting Started

1.  **Clone the Repo**:
    ```bash
    git clone https://github.com/tbinh94/ExpressionPhotoBooth.git
    ```
2.  **Firebase Setup**:
    *   Create a Firebase project.
    *   Add your `google-services.json` to the `app/` directory.
    *   Enable Auth (Google, Email), Firestore, and Storage.
3.  **Deploy Functions**:
    *   Go to `functions/` and run `npm install`.
    *   Deploy using `firebase deploy --only functions`.
    *   Set `GEMINI_API_KEY` in Firebase environment variables.
4.  **Build & Run**:
    *   Open in Android Studio.
    *   Sync Gradle and run on a physical device for the best AI tracking experience.

---

## 👨‍💻 Development Team
Developed with ❤️ by **Binh Ca, Duc Trong**.

---

## 📄 License
This project is for educational and research purposes. All rights reserved.
