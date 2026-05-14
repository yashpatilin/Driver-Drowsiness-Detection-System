# 🚗 Driver Drowsiness Detection System

A real-time **Driver Drowsiness Detection System** that monitors a driver's eyes and alerts them when signs of fatigue are detected. This project helps in reducing road accidents caused by drowsy driving using computer vision and machine learning techniques.

---

## 📌 Features

* 👁️ Real-time eye detection using webcam
* 😴 Drowsiness detection based on eye closure (EAR / blink rate)
* 🔊 Alarm system to alert the driver
* ⚡ Fast and lightweight processing
* 💻 Works on local machine (no internet required)

---

## 🛠️ Tech Stack

* **Programming Language:** Python
* **Libraries Used:**

  * OpenCV
  * Dlib / Mediapipe
  * NumPy
  * Scipy
* **Tools:** Webcam, VS Code / PyCharm

---

## 📂 Project Structure

```
Driver-Drowsiness-Detection-System/
│
├── models/              # Pre-trained models
├── src/                 # Main source code
├── utils/               # Helper functions
├── alarm/               # Alarm sound files
├── README.md
└── requirements.txt
```

---

## ⚙️ Installation & Setup

1. Clone the repository:

```bash
git clone https://github.com/yashpatilin/Driver-Drowsiness-Detection-System.git
cd Driver-Drowsiness-Detection-System
```

2. Install dependencies:

```bash
pip install -r requirements.txt
```

3. Run the project:

```bash
python main.py
```

---

## 🧠 How It Works

* The system captures video using a webcam.
* It detects facial landmarks (especially eyes).
* Calculates **Eye Aspect Ratio (EAR)**.
* If EAR falls below a threshold for a certain time:

  * 🚨 Alarm is triggered
  * Driver is alerted

---

## 📸 Screenshots (Optional)

*Add screenshots or demo images here*

---

## 🚀 Future Improvements

* Mobile app integration
* Cloud-based monitoring
* Driver behavior analytics
* Integration with vehicle systems

---

## 🤝 Contributing

Contributions are welcome!
Feel free to fork this repository and submit a pull request.

---

## 📄 License

This project is open-source and available under the MIT License.

---

## 👨‍💻 Author

**Yash Patil**

* GitHub: https://github.com/yashpatilin

---

## ⭐ Support

If you found this project helpful, please give it a ⭐ on GitHub!

---
