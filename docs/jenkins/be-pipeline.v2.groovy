// aibot BE Jenkins pipeline v2 (FIX_PLAN step 4, BE part), drafted 2026-10-13. DRAFT, not applied.
// Base: be-pipeline.before.groovy (live script, pasted 2026-10-13). Only these changes:
//   1. Every app image is also tagged and pushed with the git short SHA (#21).
//   2. Deploy: no `docker compose down`. Running app images are tagged locally as <repo>:prev first,
//      images are pulled while the old containers keep running, then app services are updated
//      ONE AT A TIME (compose recreates a service only if its image or config changed), each waited on
//      until healthy. If one fails, it and every service already updated in this run go back to :prev (#19).
//   3. ELK / whisper / kokoro keep the existing order and wait loops; `up -d` only recreates them if changed.
//      The final `up -d --remove-orphans` keeps today's "start the rest" + orphan removal.
//   4. Health check covers all 9 app services and FAILS the build when one is down (#23).
//   5. Optional DEPLOY_REF parameter: build/deploy a given commit instead of main HEAD (blank = as before).
// Unchanged on purpose (not investigated yet): Kokoro CPU stage, -DskipTests, Backup stage (.env copy),
// COMPOSE_SOURCE logic, post blocks. Open "why" questions: see FIX_PLAN step 4.
pipeline {
    agent any

    environment {
        REGISTRY = 'registry.gitlab.com/tyssdev/ty_aibot'
        DEPLOY_DIR = '/home/aibot/BE'
        ENV_FILE = '/home/aibot/BE/.env'
        JAVA_HOME = '/usr/lib/jvm/java-21-openjdk-amd64'
        DOCKER_BUILDKIT = '1'
        COMPOSE_SOURCE = 'workspace'
        // v2: app services updated one at a time, in this order
        APP_SERVICES = 'eureka-server ops-service auth-service questionbank-service interview-service review-service screening-service ai-service api-gateway'
    }

    // v2: optional commit to deploy (blank = main HEAD, as before). Used for the first v2 test run and for rollback by version.
    parameters {
        string(name: 'DEPLOY_REF', defaultValue: '', description: 'Commit SHA to build and deploy. Leave blank for main HEAD.')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {

        stage('Clone') {
            steps {
                cleanWs()
                git branch: 'main',
                    credentialsId: '17f6dcbd-41f7-4145-bd20-d602d88cfbb5',
                    url: 'https://github.com/Ariprasath-1509/ai-interview-bot-backend-v1.git'
                sh '''
                if [ -n "${DEPLOY_REF}" ]; then
                  echo "DEPLOY_REF set: checking out ${DEPLOY_REF} instead of main HEAD"
                  git checkout --detach "${DEPLOY_REF}"
                fi
                '''
                script {
                    // v2: commit SHA used as an extra image tag
                    env.GIT_SHORT = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                }
                sh 'echo "Building commit: $(git log -1 --format="%H %ci %s")"'
                sh '''
                echo "=== Git workspace ==="
                ls -lash
                echo "=== docker-compose.yml from repo (whisper healthcheck) ==="
                grep -A6 "whisper-stt:" docker-compose.yml | head -20 || true
                sed -n '/whisper-stt:/,/kokoro-tts:/p' docker-compose.yml | grep -A8 healthcheck || true
                '''
            }
        }

        stage('Build (Java 21)') {
            steps {
                sh '''
                export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
                export PATH=$JAVA_HOME/bin:$PATH

                echo "=== Java Version ==="
                java -version
                javac -version

                echo "=== Starting Maven Build (root modules only) ==="
                mvn clean package -DskipTests -T 1C

                echo "=== Maven Build Completed ==="
                '''
            }
        }

        stage('Verify JARs') {
            steps {
                sh '''
                echo "=== Verifying JAR files ==="

                ls -lah eureka-server/target/*.jar
                ls -lah auth-service/target/*.jar
                ls -lah ai-service/target/*.jar
                ls -lah api-gateway/target/*.jar
                ls -lah ops-service/target/*.jar
                ls -lah interview-service/target/*.jar
                ls -lah review-service/target/*.jar
                ls -lah questionbank-service/target/*.jar
                ls -lah screening-service/target/*.jar

                echo "=== All JARs verified ==="
                echo "NOTE: compliance-service and observer-service are merged into ops-service"
                '''
            }
        }

        stage('GitLab Registry Login') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'gitlab-registry-creds',
                    usernameVariable: 'GITLAB_USER',
                    passwordVariable: 'GITLAB_TOKEN'
                )]) {
                    sh 'echo "$GITLAB_TOKEN" | docker login registry.gitlab.com -u "$GITLAB_USER" --password-stdin'
                }
            }
        }

        stage('Build & Push Docker Images') {
            parallel {

                stage('Eureka Server') {
                    steps {
                        sh '''
                        docker build --progress=plain \
                          -t ${REGISTRY}/ai_eurekha:latest \
                          -t ${REGISTRY}/ai_eurekha:${GIT_SHORT} \
                          -f eureka-server/Dockerfile .
                        docker push ${REGISTRY}/ai_eurekha:latest
                        docker push ${REGISTRY}/ai_eurekha:${GIT_SHORT}
                        '''
                    }
                }

                stage('Auth Service') {
                    steps {
                        sh '''
                        docker build --progress=plain \
                          -t ${REGISTRY}/ai_auth:latest \
                          -t ${REGISTRY}/ai_auth:${GIT_SHORT} \
                          -f auth-service/Dockerfile .
                        docker push ${REGISTRY}/ai_auth:latest
                        docker push ${REGISTRY}/ai_auth:${GIT_SHORT}
                        '''
                    }
                }

                stage('AI Service') {
                    steps {
                        sh '''
                        docker build --progress=plain \
                          -t ${REGISTRY}/ai_service:latest \
                          -t ${REGISTRY}/ai_service:${GIT_SHORT} \
                          -f ai-service/Dockerfile .
                        docker push ${REGISTRY}/ai_service:latest
                        docker push ${REGISTRY}/ai_service:${GIT_SHORT}
                        '''
                    }
                }

                stage('API Gateway') {
                    steps {
                        sh '''
                        docker build --progress=plain \
                          -t ${REGISTRY}/api_gateway:latest \
                          -t ${REGISTRY}/api_gateway:${GIT_SHORT} \
                          -f api-gateway/Dockerfile .
                        docker push ${REGISTRY}/api_gateway:latest
                        docker push ${REGISTRY}/api_gateway:${GIT_SHORT}
                        '''
                    }
                }

                stage('Ops Service') {
                    steps {
                        sh '''
                        docker build --progress=plain \
                          -t ${REGISTRY}/ops_service:latest \
                          -t ${REGISTRY}/ops_service:${GIT_SHORT} \
                          -f ops-service/Dockerfile .
                        docker push ${REGISTRY}/ops_service:latest
                        docker push ${REGISTRY}/ops_service:${GIT_SHORT}
                        '''
                    }
                }

                stage('Interview Service') {
                    steps {
                        sh '''
                        docker build --progress=plain \
                          -t ${REGISTRY}/interview_service:latest \
                          -t ${REGISTRY}/interview_service:${GIT_SHORT} \
                          -f interview-service/Dockerfile .
                        docker push ${REGISTRY}/interview_service:latest
                        docker push ${REGISTRY}/interview_service:${GIT_SHORT}
                        '''
                    }
                }

                stage('Review Service') {
                    steps {
                        sh '''
                        docker build --progress=plain \
                          -t ${REGISTRY}/review_service:latest \
                          -t ${REGISTRY}/review_service:${GIT_SHORT} \
                          -f review-service/Dockerfile .
                        docker push ${REGISTRY}/review_service:latest
                        docker push ${REGISTRY}/review_service:${GIT_SHORT}
                        '''
                    }
                }

                stage('Questionbank Service') {
                    steps {
                        sh '''
                        docker build --progress=plain \
                          -t ${REGISTRY}/questionbank_service:latest \
                          -t ${REGISTRY}/questionbank_service:${GIT_SHORT} \
                          -f questionbank-service/Dockerfile .
                        docker push ${REGISTRY}/questionbank_service:latest
                        docker push ${REGISTRY}/questionbank_service:${GIT_SHORT}
                        '''
                    }
                }

                stage('Screening Service') {
                    steps {
                        sh '''
                        docker build --progress=plain \
                          -t ${REGISTRY}/screening_service:latest \
                          -t ${REGISTRY}/screening_service:${GIT_SHORT} \
                          -f screening-service/Dockerfile .
                        docker push ${REGISTRY}/screening_service:latest
                        docker push ${REGISTRY}/screening_service:${GIT_SHORT}
                        '''
                    }
                }

                stage('Kokoro TTS') {
                    steps {
                        sh '''
                        docker pull ghcr.io/remsky/kokoro-fastapi-cpu:latest
                        docker tag ghcr.io/remsky/kokoro-fastapi-cpu:latest ${REGISTRY}/kokoro_tts:latest
                        docker push ${REGISTRY}/kokoro_tts:latest
                        '''
                    }
                }
            }
        }

        stage('GitLab Registry Logout') {
            steps {
                sh 'docker logout registry.gitlab.com'
            }
        }

        stage('Validate Docker Compose') {
            steps {
                sh '''
                echo "=== Validating docker-compose.yml (COMPOSE_SOURCE=${COMPOSE_SOURCE}) ==="
                if [ "${COMPOSE_SOURCE}" = "workspace" ]; then
                  COMPOSE_FILE="${WORKSPACE}/docker-compose.yml"
                else
                  COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.yml"
                fi
                echo "Using compose file: ${COMPOSE_FILE}"
                echo "Using env file: ${ENV_FILE}"
                if [ ! -f "${ENV_FILE}" ]; then
                  echo "ERROR: ${ENV_FILE} missing — create it on the server before deploy"
                  exit 1
                fi
                docker compose --project-directory "${DEPLOY_DIR}" -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" config > /dev/null
                echo "docker-compose.yml is valid"
                '''
            }
        }

        stage('Prepare Deployment Files') {
            steps {
                sh '''
                set -e
                echo "=== Preparing ${DEPLOY_DIR} (COMPOSE_SOURCE=${COMPOSE_SOURCE}) ==="
                sudo mkdir -p ${DEPLOY_DIR}
                sudo mkdir -p ${DEPLOY_DIR}/elk/logstash/pipeline

                if [ ! -f "${ENV_FILE}" ]; then
                  echo "ERROR: ${ENV_FILE} missing"
                  echo "Create it on the server (ssh) before running the pipeline:"
                  echo "  sudo nano ${ENV_FILE}"
                  echo "  sudo chmod 600 ${ENV_FILE}"
                  exit 1
                fi
                echo "Using server .env (not copied from git or Jenkins): ${ENV_FILE}"
                ls -la "${ENV_FILE}"

                if [ "${COMPOSE_SOURCE}" = "workspace" ]; then
                  echo "Publishing docker-compose.yml from GitHub workspace -> ${DEPLOY_DIR}"
                  sudo cp "${WORKSPACE}/docker-compose.yml" "${DEPLOY_DIR}/docker-compose.yml"
                  echo "=== whisper-stt healthcheck deployed ==="
                  sed -n '/whisper-stt:/,/kokoro-tts:/p' "${DEPLOY_DIR}/docker-compose.yml" | grep -A8 healthcheck || true
                else
                  echo "Using existing server compose: ${DEPLOY_DIR}/docker-compose.yml (not overwritten)"
                  if [ ! -f "${DEPLOY_DIR}/docker-compose.yml" ]; then
                    echo "ERROR: ${DEPLOY_DIR}/docker-compose.yml missing"
                    exit 1
                  fi
                fi

                sudo cp -r elk/logstash/pipeline/* ${DEPLOY_DIR}/elk/logstash/pipeline/
                echo "=== Deployment files ready ==="
                '''
            }
        }

        stage('Backup Current Deployment') {
            steps {
                sh '''
                echo "=== Creating backup ==="
                cd ${DEPLOY_DIR}
                BACKUP_DIR="${DEPLOY_DIR}/backups/$(date +%Y%m%d_%H%M%S)"
                sudo mkdir -p ${BACKUP_DIR}
                sudo docker compose -f docker-compose.yml --env-file "${ENV_FILE}" ps | sudo tee ${BACKUP_DIR}/running_services.txt > /dev/null || true
                sudo cp docker-compose.yml ${BACKUP_DIR}/ 2>/dev/null || true
                sudo cp "${ENV_FILE}" ${BACKUP_DIR}/.env.backup 2>/dev/null || true
                echo "Backup created at ${BACKUP_DIR}"
                '''
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'gitlab-registry-creds',
                    usernameVariable: 'GITLAB_USER',
                    passwordVariable: 'GITLAB_TOKEN'
                )]) {
                sh '''#!/bin/bash
                set -e
                echo "=== Starting Deployment (COMPOSE_SOURCE=${COMPOSE_SOURCE}) ==="
                cd ${DEPLOY_DIR}
                COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.yml"

                if [ ! -f "${ENV_FILE}" ]; then
                  echo "ERROR: ${ENV_FILE} missing — cannot deploy"
                  exit 1
                fi

                echo "Compose file in use:"
                ls -la "${COMPOSE_FILE}"
                echo "Env file in use:"
                ls -la "${ENV_FILE}"

                echo "Logging into GitLab registry for pull..."
                echo "$GITLAB_TOKEN" | sudo docker login registry.gitlab.com -u "$GITLAB_USER" --password-stdin

                # v2: no `down`. Helpers for the one-at-a-time update below.
                image_of() { sudo docker inspect -f '{{.Config.Image}}' "$1" | sed 's/:latest$//'; }
                wait_healthy() {   # $1 = container, $2 = timeout seconds
                  local s="$1" t="$2" waited=0 st=""
                  while [ "$waited" -lt "$t" ]; do
                    st=$(sudo docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$s" 2>/dev/null || echo missing)
                    if [ "$st" = "healthy" ]; then echo "  $s healthy after ${waited}s"; return 0; fi
                    sleep 10; waited=$((waited + 10))
                  done
                  echo "  $s NOT healthy after ${t}s (last: $st)"; return 1
                }

                echo "v2: rollback points (local :prev tag of each running app image, nothing pushed)..."
                for s in ${APP_SERVICES}; do
                  id=$(sudo docker inspect -f '{{.Image}}' "$s" 2>/dev/null || true)
                  if [ -n "$id" ]; then
                    sudo docker tag "$id" "$(image_of "$s"):prev"
                    echo "  $s -> $(image_of "$s"):prev ($id)"
                  else
                    echo "  WARNING: $s not running, no :prev for it"
                  fi
                done

                echo "Pulling latest images (old containers keep running)..."
                sudo docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" pull

                sudo docker logout registry.gitlab.com

                echo "Starting/updating ELK (recreated only if its image or config changed)..."
                sudo docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d elasticsearch logstash kibana

                echo "Starting media services (whisper + kokoro) before ai-service..."
                sudo docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d whisper-stt kokoro-tts

                echo "Waiting for whisper-stt to be ready (up to 12 min)..."
                echo "Note: Docker may show 'starting' during start_period even when Uvicorn is already up."
                for i in $(seq 1 72); do
                  STATUS=$(sudo docker inspect --format='{{.State.Health.Status}}' whisper-stt 2>/dev/null || echo "missing")
                  PORT_OK=0
                  PROBE_OK=0
                  if timeout 3 bash -c '</dev/tcp/127.0.0.1/6013' 2>/dev/null; then PORT_OK=1; fi
                  if sudo docker exec whisper-stt uv run python -c "import socket; socket.create_connection(('127.0.0.1',8000),3).close()" 2>/dev/null; then PROBE_OK=1; fi
                  echo "  attempt ${i}/72: docker_health=${STATUS} host_port_6013=${PORT_OK} in_container_probe=${PROBE_OK}"
                  if [ "${STATUS}" = "healthy" ]; then
                    echo "whisper-stt is healthy (Docker healthcheck)"
                    break
                  fi
                  if [ "${PORT_OK}" = "1" ] && [ "${PROBE_OK}" = "1" ]; then
                    echo "whisper-stt is ready (port open + in-container probe; proceeding even if Docker still says starting)"
                    break
                  fi
                  if [ "${i}" -eq 72 ]; then
                    echo "whisper-stt did not become ready in time"
                    sudo docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" logs --tail=80 whisper-stt || true
                    echo "=== Docker health log (last 5 probes) ==="
                    sudo docker inspect whisper-stt --format='{{json .State.Health}}' 2>/dev/null || true
                    exit 1
                  fi
                  sleep 10
                done

                echo "Waiting for kokoro-tts to be ready (up to 5 min)..."
                echo "Kokoro uses a pre-built image; this wait covers model warmup on first start."
                for i in $(seq 1 30); do
                  STATUS=$(sudo docker inspect --format='{{.State.Health.Status}}' kokoro-tts 2>/dev/null || echo "missing")
                  PORT_OK=0
                  if timeout 3 bash -c '</dev/tcp/127.0.0.1/6014' 2>/dev/null; then PORT_OK=1; fi
                  RESTARTS=$(sudo docker inspect --format='{{.RestartCount}}' kokoro-tts 2>/dev/null || echo "0")
                  echo "  attempt ${i}/30: docker_health=${STATUS} host_port_6014=${PORT_OK} restarts=${RESTARTS}"
                  if [ "${STATUS}" = "healthy" ]; then
                    echo "kokoro-tts is healthy (Docker healthcheck)"
                    break
                  fi
                  if [ "${PORT_OK}" = "1" ]; then
                    echo "kokoro-tts is ready (port 6014 open)"
                    break
                  fi
                  if [ "${i}" -eq 30 ]; then
                    echo "kokoro-tts did not become ready in time"
                    sudo docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" logs --tail=100 kokoro-tts || true
                    echo "=== kokoro-tts health (json) ==="
                    sudo docker inspect kokoro-tts --format='{{json .State.Health}}' 2>/dev/null || true
                    exit 1
                  fi
                  sleep 10
                done

                echo "v2: updating app services one at a time: ${APP_SERVICES}"
                UPDATED=""
                rollback_all() {
                  echo "ROLLING BACK to :prev:${UPDATED}"
                  for r in ${UPDATED}; do
                    repo=$(image_of "$r")
                    if sudo docker image inspect "$repo:prev" >/dev/null 2>&1; then
                      sudo docker tag "$repo:prev" "$repo:latest"
                      sudo docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d --no-deps "$r"
                      wait_healthy "$r" 300 || echo "  $r still unhealthy after rollback: manual action (:baseline-20261012)"
                    else
                      echo "  no $repo:prev, manual rollback needed for $r"
                    fi
                  done
                  echo "NOTE: registry :latest still points to the failed build; local :latest = :prev now."
                }
                for s in ${APP_SERVICES}; do
                  before=$(sudo docker inspect -f '{{.Id}}' "$s" 2>/dev/null || true)
                  sudo docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d --no-deps "$s"
                  after=$(sudo docker inspect -f '{{.Id}}' "$s" 2>/dev/null || true)
                  if [ -n "$before" ] && [ "$before" = "$after" ]; then
                    echo "  $s unchanged"
                    continue
                  fi
                  UPDATED="${UPDATED} $s"
                  echo "  $s recreated, waiting until healthy (max 5 min)..."
                  wait_healthy "$s" 300 || { rollback_all; exit 1; }
                done
                echo "Updated services:${UPDATED:- none}"

                echo "Starting anything else (e.g. media-warmup) and removing orphans, as before..."
                sudo docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d --remove-orphans

                echo "=== Deployment completed ==="
                sudo docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" ps
                '''
                }
            }
        }

        stage('Health Check') {
            steps {
                sh '''#!/bin/bash
                echo "=== Performing Health Checks ==="
                cd ${DEPLOY_DIR}
                COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.yml"
                FAILED=""

                check_service () {
                    SERVICE_NAME=$1
                    URL=$2
                    echo "Checking ${SERVICE_NAME}..."
                    for i in $(seq 1 12); do
                        if curl -f ${URL} > /dev/null 2>&1; then
                            echo "${SERVICE_NAME} is UP"
                            return 0
                        fi
                        echo "Waiting for ${SERVICE_NAME}..."
                        sleep 5
                    done
                    echo "${SERVICE_NAME} Health Check Failed"
                    return 1
                }

                check_service "Eureka Server" "http://localhost:6009/actuator/health" || FAILED="$FAILED eureka"
                check_service "API Gateway" "http://localhost:6002/actuator/health" || FAILED="$FAILED gateway"
                check_service "Ops Service (compliance+observer)" "http://localhost:6005/actuator/health" || \
                  { timeout 5 bash -c '</dev/tcp/localhost/6005' && echo "Ops Service TCP OK"; } || FAILED="$FAILED ops"
                # v2: services the old check skipped
                check_service "Auth Service" "http://localhost:6004/actuator/health" || FAILED="$FAILED auth"
                check_service "AI Service" "http://localhost:6003/actuator/health" || FAILED="$FAILED ai"
                check_service "Interview Service" "http://localhost:6006/actuator/health" || FAILED="$FAILED interview"
                check_service "Review Service" "http://localhost:6008/actuator/health" || FAILED="$FAILED review"
                check_service "Questionbank Service" "http://localhost:6016/actuator/health" || FAILED="$FAILED questionbank"
                check_service "Screening Service" "http://localhost:6017/actuator/health" || FAILED="$FAILED screening"

                echo "Whisper STT Docker health: $(sudo docker inspect --format='{{.State.Health.Status}}' whisper-stt 2>/dev/null || echo n/a)"
                if timeout 3 bash -c '</dev/tcp/localhost/6013' 2>/dev/null; then
                  echo "Whisper STT port 6013 is open"
                else
                  echo "WARNING: Whisper STT port 6013 not reachable from host"
                fi

                echo "Kokoro TTS Docker health: $(sudo docker inspect --format='{{.State.Health.Status}}' kokoro-tts 2>/dev/null || echo n/a)"
                if timeout 3 bash -c '</dev/tcp/localhost/6014' 2>/dev/null; then
                  echo "Kokoro TTS port 6014 is open"
                else
                  echo "WARNING: Kokoro TTS port 6014 not reachable from host"
                fi

                echo ""
                echo "=== Running Containers ==="
                sudo docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" ps

                echo ""
                echo "Deployed commit: ${GIT_SHORT}"
                if [ -n "$FAILED" ]; then
                  echo "=== Health checks FAILED:${FAILED} ==="
                  echo "Rollback: local :prev tags (see Deploy log) or :baseline-20261012"
                  exit 1
                fi
                echo "=== Health checks completed ==="
                '''
            }
        }
    }

    post {
        success {
            echo 'Pipeline completed successfully!'
            sh '''
            cd ${DEPLOY_DIR}
            echo ""
            echo "======================================"
            echo "       DEPLOYMENT SUCCESSFUL"
            echo "       COMPOSE_SOURCE=${COMPOSE_SOURCE}"
            echo "       ENV_FILE=${ENV_FILE}"
            echo "======================================"
            sudo docker compose -f docker-compose.yml --env-file "${ENV_FILE}" ps
            echo ""
            echo "Services are running at:"
            echo "  - Eureka:      http://103.182.211.219:6009"
            echo "  - API Gateway: http://103.182.211.219:6002"
            echo "  - Ops Service: http://103.182.211.219:6005 (compliance + observer)"
            echo "  - Whisper STT: http://103.182.211.219:6013"
            echo "  - Kokoro TTS:  http://103.182.211.219:6014"
            echo "  - Kibana:      http://103.182.211.219:6012"
            echo "======================================"

            # Ops Dashboard: record deployment success and refresh status.json immediately
            if [ -d "${DEPLOY_DIR}" ]; then
              mkdir -p /var/www/ops 2>/dev/null || true
              cat <<DEP_JSON > "${DEPLOY_DIR}/status-deployment.json.tmp"
{
  "active": {
    "service": "ai-service",
    "build": ${BUILD_NUMBER:-143},
    "version": "${GIT_SHORT:-latest}",
    "stage": "live",
    "status": "success",
    "elapsedSeconds": 0
  },
  "recent": [
    { "id": ${BUILD_NUMBER:-143}, "svc": "ai-service", "ver": "${GIT_SHORT:-v2.8.3}", "sha": "${GIT_SHORT:-HEAD}", "time": "just now", "status": "success" },
    { "id": 142, "svc": "api-gateway", "ver": "v2.4.1", "sha": "8a3d12c", "time": "1d ago", "status": "success" },
    { "id": 141, "svc": "auth-service", "ver": "v1.9.0", "sha": "e490b81", "time": "3d ago", "status": "success" }
  ]
}
DEP_JSON
              mv -f "${DEPLOY_DIR}/status-deployment.json.tmp" "${DEPLOY_DIR}/status-deployment.json" 2>/dev/null || true
              if [ -x "${DEPLOY_DIR}/scripts/emit-status.sh" ]; then
                "${DEPLOY_DIR}/scripts/emit-status.sh" >/dev/null 2>&1 || true
              fi
            fi
            '''
        }

        failure {
            echo 'Pipeline failed!'
            sh '''
            cd ${DEPLOY_DIR} || true
            echo ""
            echo "======================================"
            echo "       PIPELINE FAILED"
            echo "       COMPOSE_SOURCE=${COMPOSE_SOURCE}"
            echo "       ENV_FILE=${ENV_FILE}"
            echo "======================================"
            sudo docker compose -f docker-compose.yml --env-file "${ENV_FILE}" ps || true
            echo ""
            echo "=== whisper-stt health (json) ==="
            sudo docker inspect whisper-stt --format='{{json .State.Health}}' 2>/dev/null || true
            echo ""
            echo "=== Recent Error Logs ==="
            sudo docker compose -f docker-compose.yml --env-file "${ENV_FILE}" logs --tail=50 whisper-stt kokoro-tts ai-service ops-service || true
            echo ""
            echo "======================================"
            '''
        }

        always {
            echo 'Cleaning up workspace...'
            cleanWs()
        }
    }
}
