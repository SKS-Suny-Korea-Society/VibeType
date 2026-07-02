# VibeType ⌨️✨

> **An AI-powered custom keyboard that transforms your writing in real time.**
>
> Developed for **SKS Hackathon The X**

---

## 📖 Overview

**VibeType** is an Android-based AI keyboard that helps users communicate more effectively using generative AI. Instead of switching between apps or AI assistants, users can rewrite, summarize, refine, or expand text directly from their keyboard.


<img width="1080" height="995" alt="image" src="https://github.com/user-attachments/assets/122ae308-dcc5-49d1-ba83-ef54b7c49071" />

<img width="1080" height="988" alt="image" src="https://github.com/user-attachments/assets/ce4915fc-7aed-4ea0-b426-6ad661680ef0" />


Powered by **Google Gemini API**, VibeType provides real-time writing assistance while users type, making communication faster, clearer, and more natural.

---

## ✨ Features

### 🎭 Tone & Style Conversion

Rewrite text to match different situations and audiences.

* Casual conversations
* Professional business emails
* Polite requests
* Formal writing

---

### 📝 Text Summarization

Summarize lengthy text into concise, easy-to-read messages while preserving the key information.

---

### ✨ Grammar Correction & Refinement

Improve sentence quality by:

* Correcting grammatical mistakes
* Refining awkward expressions
* Removing redundancy
* Increasing readability

---

### 💡 Creative Expansion

Expand simple ideas into richer, more expressive writing by adding appropriate context and emotion.

---

## 🛠 Tech Stack

| Category        | Technology         |
| --------------- | ------------------ |
| Platform        | Android            |
| Language        | Kotlin             |
| Framework       | InputMethodService |
| AI              | Google Gemini API  |
| Version Control | Git & GitHub       |

---

## 🏗 Architecture

```text
        User Input
             │
             ▼
    VibeType Keyboard
             │
             ▼
     Google Gemini API
             │
             ▼
    AI-generated Response
             │
             ▼
   Updated Text in Keyboard
```

---

## 📂 Project Structure

```text
VibeType/
├── app/
│   ├── keyboard/
│   ├── service/
│   ├── network/
│   └── ui/
├── gradle/
├── local.properties
├── build.gradle
└── README.md
```
---

## 🔐 Security

To protect sensitive credentials:

* API keys are stored in `local.properties`
* Sensitive files are excluded using `.gitignore`
* No secret information is committed to the public repository

---

## ⚡ Technical Highlights

### Optimized API Requests

Since keyboard input generates continuous events, sending every keystroke to the AI would quickly exceed API rate limits.

To improve efficiency, VibeType incorporates:

* Debouncing
* Optimized request timing
* Exception handling
* Reduced unnecessary API calls

---

## 👥 Team

| Member            | Responsibilities                                                                            |
| ----------------- | ------------------------------------------------------------------------------------------- |
| **Taemin Kim**    | Android keyboard development, Google Gemini API integration for VibeType Web and app        |
| **Jaeyoung Choi** | VibeType web development (Claude) , ppt making                                              |
| **Jihoon Choi**   | Android keyboard support, application logo design, iOS version development                  |
| **Hyenna Joo**    | Desktop launcher for automatically running VibeType Web alongside PC messenger applications |

---

## 💻 Development Process

The project was developed simultaneously across multiple platforms.

### 🌐 Web

* Built using HTML
* Connected with Google Gemini API
* Provides AI-powered writing assistance through the browser

### 📱 Android

* Custom keyboard built with Kotlin
* Real-time AI-assisted typing experience
* Integrated directly with Gemini API

### 🖥 Desktop Integration

Implemented a launcher that automatically opens VibeType Web whenever supported desktop messenger applications are executed.

---

## 🔮 Future Work

* 🍎 Release an iOS version (currently in development)
* 🌍 Support additional languages
* ⚡ Improve response speed and latency
* 🤖 Introduce more AI writing modes
* ☁ Synchronize writing history across devices

---

## 🎯 Hackathon

**Project:** VibeType

**Event:** SKS Hackathon The X

VibeType aims to bring generative AI directly into everyday communication through a seamless keyboard experience. By reducing the need to switch between applications, users can write faster, better, and more naturally.

---

## 📄 License

This project was developed for **SKS Hackathon The X**.
