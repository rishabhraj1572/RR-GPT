<p align="center">
  <img src="https://github.com/rishabhraj1572/RR-GPT/blob/master/app/src/main/ic_launcher-playstore.png?raw=true" alt="App Icon" width="120"/>
</p>

<h1 align="center">🤖 RR GPT</h1>
<p align="center">
  <b>Welcome to RR GPT – GPT API Client</b><br>
  A modern Android app to chat with GPT models, attach images, and capture with camera.<br>
</p>

---

## ✨ Features
- 🧠 **GPT-Powered Chat** – Connect with GPT APIs and get instant, smart responses.  
- 📸 **Camera & Gallery Support** – Capture images or attach files directly in the chat.  
- 💬 **Clean & Modern Chat UI** – Minimal, WhatsApp-style design with recycler view.  
- ⚡ **Streaming Responses** – Watch AI reply in real-time as it types.  
- 🎨 **Customizable Settings** – Easily configure API endpoint, key, and version.  
- 📂 **Attachments Preview** – View selected images before sending.  
- 🌙 **Auto Empty State UI** – Shows a welcome screen when chat is empty.  

---

## 🖼️ Screenshots  

<p align="center">
  <img src="https://github.com/rishabhraj1572/RR-GPT/blob/master/Screenshots/1.png?raw=true" alt="Chat Screen" width="250"/>
  <img src="https://github.com/rishabhraj1572/RR-GPT/blob/master/Screenshots/2.png?raw=true" alt="Multiple Images" width="250"/>
  <img src="https://github.com/rishabhraj1572/RR-GPT/blob/master/Screenshots/3.png?raw=true" alt="Chat with Image" width="250"/>
  <img src="https://github.com/rishabhraj1572/RR-GPT/blob/master/Screenshots/4.png?raw=true" alt="Code" width="250"/>
  <img src="https://github.com/rishabhraj1572/RR-GPT/blob/master/Screenshots/5.png?raw=true" alt="Single Message" width="250"/>
  <img src="https://github.com/rishabhraj1572/RR-GPT/blob/master/Screenshots/6.png?raw=true" alt="Config Dialog" width="250"/>
</p>  

---

## ☁️ Image Upload Setup (Cloudinary)  

This app supports **Cloudinary unsigned upload** to handle images captured or selected before sending to GPT API.  

### 1️⃣ Create Free Cloudinary Account  
👉 [Sign up here](https://cloudinary.com/users/register/free)  

### 2️⃣ Create an Unsigned Upload Preset  
- Go to your **Cloudinary Dashboard** → **Settings** → **Upload**.  
- Scroll down to **Upload Presets**.  
- Click **Add Upload Preset**.  
- Set:  
  - **Signing Mode** → `Unsigned`  
  - **Upload Folder** → `ml_default`

### 3️⃣ Configure App  
In `Settings Dialog` inside the app:  
- Enter your **Cloudinary Cloud Name** (In the Dashboard)  
- Now, when you capture or attach an image, it will upload to Cloudinary before being sent to GPT API.  

---

## 🎯 Use Cases
- Personal AI Assistant  
- Quick Note-Taking with GPT  
- Experimenting with GPT APIs  
- AI-powered Q&A for learning  

---

## 🚀 Tech Stack
- **Kotlin, AndroidX, RecyclerView**  
- **CameraX for Camera Preview**  
- **Material Design Components**  
- **REST API (OpenAI / Azure / Custom GPT Endpoints)**  
