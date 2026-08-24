@echo off
cd /d "%~dp0"
if not exist "target\radio-streaming-api-1.0.0.jar" (
  echo Building project...
  call mvn -q -DskipTests package
)
echo Starting Radio Streaming API on http://localhost:8080
java -jar target\radio-streaming-api-1.0.0.jar
