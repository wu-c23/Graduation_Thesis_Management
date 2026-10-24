@echo off
start "thesis-backend" cmd /k "%~dp0start-backend.bat"
start "thesis-frontend" cmd /k "%~dp0start-frontend.bat"
echo Backend and frontend windows have been opened.
echo Backend:  http://localhost:8080/api/health
echo Frontend: http://localhost:5173
