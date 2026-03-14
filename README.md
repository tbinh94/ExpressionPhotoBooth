# AI Expression Photo Booth 📸😄

An Android application that detects **facial expressions in real-time** using the device camera and displays the predicted emotion on screen.

This project demonstrates the integration of **Computer Vision and Mobile Development** by combining camera processing, face detection, and emotion classification.

---

## 🚀 Features

* Real-time camera preview
* Face detection using ML-based vision tools
* Emotion recognition (Happy, Sad, Angry, Surprise, Neutral)
* Display detected emotion on screen
* Clean and simple UI

Future improvements:

* Auto capture when smiling
* Emotion statistics dashboard
* Emoji overlay on detected faces
* Emotion history tracking

---

## 🛠 Tech Stack

* **Language:** Java
* **IDE:** Android Studio
* **Camera:** CameraX
* **Face Detection:** ML Kit
* **AI Model:** TensorFlow Lite

---

## 📱 Application Workflow

Camera → Face Detection → Crop Face → Emotion Model → Display Result

---

## 📂 Project Structure

```
app/
 ├── java/com/.../
 │   ├── MainActivity.java
 │   ├── camera/
 │   │    └── CameraManager.java
 │   ├── vision/
 │   │    └── FaceDetectorHelper.java
 │   └── ml/
 │        └── EmotionClassifier.java
 │
 └── res/
      ├── layout/
      ├── drawable/
      └── values/
```

---

## ⚙️ Setup Instructions

1. Clone the repository

```
git clone https://github.com/your-username/expression-photobooth.git
```

2. Open the project in **Android Studio**

3. Sync Gradle dependencies

4. Connect an Android device or emulator

5. Run the application

---

## 📸 Demo

Coming soon...

---

## 🧠 AI Model

The emotion recognition model is a **TensorFlow Lite model trained on facial expression datasets such as FER2013**.

Predicted classes:

* Happy
* Sad
* Angry
* Surprise
* Neutral

---

## 📌 Future Work

* Improve emotion classification accuracy
* Add emotion history tracking
* Implement automatic photo capture on smile detection
* Add graphical emotion analytics

---

## 👨‍💻 Author

Developed by **Binh Ca**

---

## 📄 License

This project is for educational and research purposes.
