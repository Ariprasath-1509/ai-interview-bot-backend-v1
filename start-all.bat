@echo off
echo Starting all 8 microservices in dependency order...
echo.

echo [1/8] Starting Eureka Server (6009) - Service Registry
start "Eureka Server (6009)" cmd /k "cd eureka-server && mvn spring-boot:run"
echo Waiting for Eureka Server to be ready...
:wait_eureka
timeout /t 3 /nobreak >nul
curl -s http://localhost:6009/actuator/health >nul 2>&1
if errorlevel 1 goto wait_eureka
echo Eureka Server is ready!
echo.

echo [2/8] Starting Compliance Service (6005) - no dependencies
start "Compliance Service (6005)" cmd /k "cd compliance-service && mvn spring-boot:run"
echo Waiting for Compliance Service to register with Eureka...
:wait_compliance
timeout /t 3 /nobreak >nul
curl -s http://localhost:6005/actuator/health >nul 2>&1
if errorlevel 1 goto wait_compliance
echo Compliance Service is ready!
echo.

echo [3/8] Starting Auth Service (6004) - no dependencies
start "Auth Service (6004)" cmd /k "cd auth-service && mvn spring-boot:run"
echo Waiting for Auth Service to register with Eureka...
:wait_auth
timeout /t 3 /nobreak >nul
curl -s http://localhost:6004/actuator/health >nul 2>&1
if errorlevel 1 goto wait_auth
echo Auth Service is ready!
echo.

echo [4/8] Starting Interview Service (6006) - depends on auth-service
start "Interview Service (6006)" cmd /k "cd interview-service && mvn spring-boot:run"
echo Waiting for Interview Service to register with Eureka...
:wait_interview
timeout /t 3 /nobreak >nul
curl -s http://localhost:6006/actuator/health >nul 2>&1
if errorlevel 1 goto wait_interview
echo Interview Service is ready!
echo.

echo [5/8] Starting AI Service (6003) - depends on compliance-service
start "AI Service (6003)" cmd /k "cd ai-service && mvn spring-boot:run"
echo Waiting for AI Service to register with Eureka...
:wait_ai
timeout /t 3 /nobreak >nul
curl -s http://localhost:6003/actuator/health >nul 2>&1
if errorlevel 1 goto wait_ai
echo AI Service is ready!
echo.

echo [6/8] Starting Observer Service (6007) - depends on auth-service
start "Observer Service (6007)" cmd /k "cd observer-service && mvn spring-boot:run"
echo Waiting for Observer Service to register with Eureka...
:wait_observer
timeout /t 3 /nobreak >nul
curl -s http://localhost:6007/actuator/health >nul 2>&1
if errorlevel 1 goto wait_observer
echo Observer Service is ready!
echo.

echo [7/8] Starting Review Service (6008) - depends on interview-service
start "Review Service (6008)" cmd /k "cd review-service && mvn spring-boot:run"
echo Waiting for Review Service to register with Eureka...
:wait_review
timeout /t 3 /nobreak >nul
curl -s http://localhost:6008/actuator/health >nul 2>&1
if errorlevel 1 goto wait_review
echo Review Service is ready!
echo.

echo [8/8] Starting API Gateway (6002) - depends on all services
start "API Gateway (6002)" cmd /k "cd api-gateway && mvn spring-boot:run"
echo Waiting for API Gateway to be ready...
:wait_gateway
timeout /t 3 /nobreak >nul
curl -s http://localhost:6002/actuator/health >nul 2>&1
if errorlevel 1 goto wait_gateway
echo API Gateway is ready!
echo.

echo ========================================
echo All services are now running!
echo ========================================
echo.
echo Eureka Dashboard: http://localhost:6009
echo API Gateway:      http://localhost:6002
echo.
echo Services will continue running in separate windows.
echo Close this window or press any key to continue...
pause
