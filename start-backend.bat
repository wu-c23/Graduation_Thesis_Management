@echo off
setlocal
cd /d "%~dp0backend"

echo [1/2] Packaging backend...
call mvn -q -DskipTests package
if errorlevel 1 (
  echo Backend build failed.
  exit /b 1
)

echo [2/2] Starting backend at http://localhost:8080
java -jar target\thesis-platform-0.1.0.jar
