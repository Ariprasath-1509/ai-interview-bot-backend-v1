@echo off
echo Starting all 7 microservices...
echo.

start "API Gateway (8080)" cmd /k "cd api-gateway && mvn spring-boot:run"
timeout /t 5 /nobreak >nul

start "Auth Service (8081)" cmd /k "cd auth-service && mvn spring-boot:run"
timeout /t 3 /nobreak >nul

start "Interview Service (8082)" cmd /k "cd interview-service && mvn spring-boot:run"
timeout /t 3 /nobreak >nul

start "AI Service (8083)" cmd /k "cd ai-service && mvn spring-boot:run"
timeout /t 3 /nobreak >nul

start "Observer Service (8084)" cmd /k "cd observer-service && mvn spring-boot:run"
timeout /t 3 /nobreak >nul

start "Review Service (8085)" cmd /k "cd review-service && mvn spring-boot:run"
timeout /t 3 /nobreak >nul

start "Compliance Service (8086)" cmd /k "cd compliance-service && mvn spring-boot:run"

echo.
echo All services starting in separate windows...
echo Wait 30-60 seconds for all services to be ready.
echo.
echo Gateway: http://localhost:8080
echo.
pause
