FROM python:3.12-slim
WORKDIR /srv
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 libgl1 curl && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py .
# Model weights (YuNet MIT, SFace Apache 2.0 — opencv_zoo), pinned by
# sha256. The same two files the licensing measurements ran on.
RUN mkdir -p models \
 && curl -fsSL -o models/face_detection_yunet_2023mar.onnx \
      https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx \
 && echo "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4  models/face_detection_yunet_2023mar.onnx" | sha256sum -c - \
 && curl -fsSL -o models/face_recognition_sface_2021dec.onnx \
      https://github.com/opencv/opencv_zoo/raw/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx \
 && echo "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79  models/face_recognition_sface_2021dec.onnx" | sha256sum -c -
ENV PORT=8080
EXPOSE 8080
CMD ["python", "app.py"]
