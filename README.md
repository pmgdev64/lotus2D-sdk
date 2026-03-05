<div align = "center">
  <img src = "icons.png" draggable = "false">
</div>

# 🌸 Lotus2D-Platform (modcoderpack-redevelop)

Project: lotus2d-sdk
Source: github.com/pmgdev64/lotus2d-sdk
Status: Stable Release

---

## 🛠 CORE SPECIFICATIONS
- Architecture: Modular-based Engine
- Base Language: Java 17+
- Build System: Gradle / Maven
- Feature: Redevelop-ready (MCP Spirit)

---

## ✨ KEY FEATURES (INTERNAL LOGIC)

1. MODULAR SYSTEM:
   Tách biệt hoàn toàn các thành phần xử lý Core, Render và API.
   
2. AUTO-SAVE MECHANISM (Requirement 2026-01-02):
   - Logic: Khi một module gọi hàm toggle(), hệ thống sẽ thực hiện Trigger.
   - Action: Tự động thực thi phương thức autoSave() để lưu Config.
   - Ưu điểm: Không bao giờ mất dữ liệu trạng thái khi Crash hoặc Exit đột ngột.

---

## 📂 SOURCE CODE STRUCTURE (/src)

| Package                | Responsibility                           |
|------------------------|------------------------------------------|
| vn.pmgteam.lotus2d.core       | Engine Lifecycle & Event Bus             |
| vn.pmgteam.lotus2d.editor     | Java Swing Editor & rigging tool       |
| vn.pmgteam.lotus2d.viewer     | allFunction-in-one model viewer       |
| vn.pmgteam.lotus2d.framework     | Easy2Use runtime-rendering Framework      |
---

## 🚀 QUICK INSTALLATION

$ git clone https://github.com/pmgdev64/lotus2d-sdk.git
$ cd lotus2d-sdk
$ ./gradlew build

---

## 📄 LICENSE
This project is licensed under the MIT License.
