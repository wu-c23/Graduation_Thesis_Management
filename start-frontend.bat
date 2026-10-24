@echo off
setlocal
cd /d "%~dp0frontend"

if not exist node_modules (
  echo Installing frontend dependencies...
  call npm install
  if errorlevel 1 (
    echo Frontend dependency installation failed.
    exit /b 1
  )
)

echo Starting frontend at http://localhost:5173
call npm run dev
