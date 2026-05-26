@echo off
cd /d D:\EnlinYue\Claw\Code\RealTimeVideo\backend
set SERVER_PORT=8080
mvn spring-boot:run > backend_run_ptz.log 2>&1