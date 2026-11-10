#!/bin/bash
# Opens the Social on the TV, reads the room code from its QR and prints the key (empty when the room
# already has people: the QR is gone, reuse the last key).
# Needs OpenCV in a scratch venv: python3 -m venv /tmp/qrvenv && /tmp/qrvenv/bin/pip install --only-binary=:all: opencv-python-headless
T=192.168.1.3:5555; k(){ adb -s $T shell input keyevent $1; sleep 0.5; }
adb -s $T shell input keyevent KEYCODE_WAKEUP
adb -s $T shell am start -n dev.jean.atvhome/.HomeActivity -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; sleep 4
k DPAD_UP; k DPAD_UP; for i in 1 2 3 4 5 6; do k DPAD_LEFT; done; for i in 1 2 3 4; do k DPAD_RIGHT; done; k DPAD_CENTER; sleep 4
adb -s $T exec-out screencap -p > /tmp/tv/qr.png
/tmp/qrvenv/bin/python -I -c "import cv2; im=cv2.imread('/tmp/tv/qr.png'); print(cv2.QRCodeDetector().detectAndDecode(im)[0].split('#k=')[-1])"
