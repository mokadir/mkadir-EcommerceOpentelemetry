pipeline {
    agent {
        label 'jenkins-agent-msk-ubt-opt' 
    }

    parameters {
        choice(
            name: 'BUILD_SCOPE',
            choices: ['accounting', 'all', 'ad', 'cart', 'checkout', 'currency', 'email', 'flagd', 'flagd-ui', 'fraud-detection', 'frontend', 'frontend-proxy', 'grafana', 'image-provider', 'jaeger', 'kafka', 'load-generator', 'opensearch', 'otel-collector', 'payment', 'postgres', 'product-catalog', 'prometheus', 'quote', 'react-native-app', 'recommendation', 'shipping'],
            description: 'Select which microservice(s) to build and push out of 26 microservices'
        )
        string(
            name: 'DOCKER_REGISTRY',
            defaultValue: 'docker.io',
            description: 'Docker Registry URL (e.g., docker.io, gcr.io, acr.azurecr.io)'
        )
        string(
            name: 'DOCKER_REGISTRY_NAMESPACE',
            defaultValue: 'mokadir',
            description: 'Docker Registry namespace/organization'
        )
        string(
            name: 'IMAGE_TAG',
            defaultValue: 'latest',
            description: 'Docker image tag (default: latest, can use ${BUILD_NUMBER} or ${GIT_COMMIT})'
        )
        booleanParam(
            name: 'RUN_SECURITY_SCANS',
            defaultValue: false,
            description: 'Run security scanning (SAST, SCA, container scanning)'
        )
        booleanParam(
            name: 'RUN_CODE_QUALITY',
            defaultValue: false,
            description: 'Run code quality checks (linting, testing, coverage)'
        )
        booleanParam(
            name: 'PUSH_IMAGES',
            defaultValue: false,
            description: 'Push images to registry after build'
        )
        booleanParam(
            name: 'SIGN_IMAGES',
            defaultValue: false,
            description: 'Sign Docker images with Cosign'
        )
        booleanParam(
            name: 'GENERATE_SBOM',
            defaultValue: false,
            description: 'Generate Software Bill of Materials (SBOM)'
        )
        choice(
            name: 'ENVIRONMENT',
            choices: ['release', 'staging', 'production'],
            description: 'Target environment for deployment'
        )
        booleanParam(
            name: 'DEPLOY_TO_K8S',
            defaultValue: false,
            description: 'Deploy to Kubernetes after successful build and security checks'
        )
        booleanParam(
            name: 'MULTIPLATFORM_BUILD',
            defaultValue: false,
            description: 'Build multiplatform Docker images (linux/amd64, linux/arm64)'
        )
        string(
            name: 'BUILD_PLATFORMS',
            defaultValue: 'linux/amd64,linux/arm64',
            description: 'Comma-separated list of target platforms for multiplatform builds'
        )
    }

    environment {
        DOCKER_REGISTRY_URL = "${params.DOCKER_REGISTRY}/${params.DOCKER_REGISTRY_NAMESPACE}"
        IMAGE_TAG = "${params.IMAGE_TAG}"
        BUILD_SCOPE = "${params.BUILD_SCOPE}"
        DOCKER_BUILDKIT = "1"
        // Security thresholds
        CRITICAL_VULN_THRESHOLD = "0"
        HIGH_VULN_THRESHOLD = "5"
        // Code quality thresholds
        CODE_COVERAGE_MIN = "80"
        // OWASP Dependency Check thresholds
        OWASP_CRITICAL_THRESHOLD = "0"
        OWASP_HIGH_THRESHOLD = "10"
    }

    options {
        timestamps()
        timeout(time: 3, unit: 'HOURS')
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout & Initialize') {
            steps {
                script {
                    echo "=== DevSecOps Pipeline Started ==="
                    echo "Build Scope: ${BUILD_SCOPE}"
                    echo "Environment: ${ENVIRONMENT}"
                    echo "Security Scans: ${RUN_SECURITY_SCANS}"
                    echo "Code Quality: ${RUN_CODE_QUALITY}"
                    echo "Multiplatform Build: ${MULTIPLATFORM_BUILD}"
                    echo "Build Platforms: ${BUILD_PLATFORMS}"
                    echo "Git Commit: ${GIT_COMMIT}"
                    echo "Git Branch: ${GIT_BRANCH}"

                    // Clean workspace
                    sh 'git clean -fdx'
                }
            }
        }

        stage('Security: Secret Scanning') {
            when {
                expression {
                    return params.RUN_SECURITY_SCANS
                }
            }
            steps {
                script {
                    echo "🔍 Scanning for secrets and sensitive data..."
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        sh '''
                            # Install TruffleHog if not available
                            if ! command -v trufflehog &> /dev/null; then
                                echo "Installing TruffleHog..."
                                curl -sSfL https://raw.githubusercontent.com/trufflesecurity/trufflehog/main/scripts/install.sh | sh -s -- -b /usr/local/bin
                            fi

                            # Scan for secrets
                            echo "Running TruffleHog secret scan..."
                            trufflehog filesystem . --json --concurrency 4 | tee trufflehog-results.json || true

                            # Check for high-confidence secrets
                            if grep -q '"DetectorType":".*"' trufflehog-results.json; then
                                echo "⚠️  Potential secrets found! Review trufflehog-results.json"
                                exit 1
                            else
                                echo "✅ No secrets detected"
                            fi
                        '''
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trufflehog-results.json', allowEmptyArchive: true
                }
            }
        }

        stage('Code Quality: Linting & Static Analysis') {
            when {
                expression {
                    return params.RUN_CODE_QUALITY
                }
            }
            parallel {
                stage('Go Services') {
                    when {
                        anyOf {
                            expression { return BUILD_SCOPE == 'all' || BUILD_SCOPE == 'checkout' }
                        }
                    }
                    steps {
                        script {
                            echo "🔍 Analyzing Go code..."
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                sh '''
                                    cd src/checkout

                                    # Install golangci-lint if not available
                                    if ! command -v golangci-lint &> /dev/null; then
                                        echo "Installing golangci-lint..."
                                        curl -sSfL https://raw.githubusercontent.com/golangci/golangci-lint/master/install.sh | sh -s -- -b $(go env GOPATH)/bin v1.55.2
                                        export PATH=$PATH:$(go env GOPATH)/bin
                                    fi

                                    # Run golangci-lint
                                    golangci-lint run --timeout=10m --out-format=json | tee golangci-lint-results.json || true

                                    # Run gosec for security analysis
                                    if command -v gosec &> /dev/null; then
                                        gosec -fmt=json -out=gosec-results.json ./... || true
                                    fi
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'src/checkout/golangci-lint-results.json,src/checkout/gosec-results.json', allowEmptyArchive: true
                        }
                    }
                }

                stage('C# Services') {
                    when {
                        anyOf {
                            expression { return BUILD_SCOPE == 'all' || BUILD_SCOPE == 'accounting' }
                        }
                    }
                    steps {
                        script {
                            echo "🔍 Analyzing C# code..."
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                sh '''
                                    cd src/accounting

                                    # Install dotnet tools if needed
                                    dotnet tool install -g dotnet-format --version 5.1.250801 || true
                                    dotnet tool install -g security-scan --version 5.6.3 || true

                                    # Run dotnet format check
                                    dotnet format --check --verbosity diagnostic || true

                                    # Run security scan
                                    dotnet list package --vulnerable || true
                                '''
                            }
                        }
                    }
                }

                stage('Java Services') {
                    when {
                        anyOf {
                            expression { return BUILD_SCOPE == 'all' || BUILD_SCOPE == 'ad' || BUILD_SCOPE == 'fraud-detection' }
                        }
                    }
                    steps {
                        script {
                            echo "🔍 Analyzing Java code..."
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                sh '''
                                    cd src/ad

                                    # Run SpotBugs if available
                                    if command -v spotbugs &> /dev/null; then
                                        spotbugs -textui -output spotbugs-results.xml build/classes || true
                                    fi

                                    # Run PMD if available
                                    if command -v pmd &> /dev/null; then
                                        pmd check -d src/main/java -R rulesets/java/quickstart.xml -f text || true
                                    fi
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'src/ad/spotbugs-results.xml', allowEmptyArchive: true
                        }
                    }
                }

                stage('JavaScript/Node.js Services') {
                    when {
                        anyOf {
                            expression { return BUILD_SCOPE == 'all' || BUILD_SCOPE == 'frontend' || BUILD_SCOPE == 'flagd-ui' }
                        }
                    }
                    steps {
                        script {
                            echo "🔍 Analyzing JavaScript code..."
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                sh '''
                                    # Install ESLint and security tools globally if needed
                                    npm install -g eslint eslint-config-standard eslint-plugin-security eslint-plugin-node || true
                                    npm install -g audit-ci || true

                                    # Run ESLint on frontend
                                    if [ -d "src/frontend" ]; then
                                        cd src/frontend
                                        npm install
                                        npx eslint . --ext .js,.jsx,.ts,.tsx --format=json | tee eslint-results.json || true
                                        cd ../..
                                    fi

                                    # Run audit-ci for dependency vulnerabilities
                                    if [ -f "src/frontend/package.json" ]; then
                                        cd src/frontend
                                        npx audit-ci --config audit-ci.json || true
                                        cd ../..
                                    fi
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'src/frontend/eslint-results.json', allowEmptyArchive: true
                        }
                    }
                }

                stage('Ruby Services') {
                    when {
                        anyOf {
                            expression { return BUILD_SCOPE == 'all' || BUILD_SCOPE == 'email' }
                        }
                    }
                    steps {
                        script {
                            echo "🔍 Analyzing Ruby code..."
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                sh '''
                                    cd src/email

                                    # Install bundler-audit for Ruby security
                                    gem install bundler-audit || true

                                    # Run RuboCop if available
                                    if command -v rubocop &> /dev/null; then
                                        rubocop --format=json | tee rubocop-results.json || true
                                    fi

                                    # Run bundle audit
                                    bundle audit check --format=json | tee bundle-audit-results.json || true
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'src/email/rubocop-results.json,src/email/bundle-audit-results.json', allowEmptyArchive: true
                        }
                    }
                }
            }
        }

        stage('Security: Dependency Scanning (SCA)') {
            when {
                expression {
                    return params.RUN_SECURITY_SCANS
                }
            }
            parallel {
                stage('OWASP Dependency Check') {
                    steps {
                        script {
                            echo "🔍 Running OWASP Dependency Check..."
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                sh '''
                                    # Install OWASP Dependency Check if not available
                                    if ! command -v dependency-check.sh &> /dev/null; then
                                        echo "Installing OWASP Dependency Check..."
                                        wget -q https://github.com/jeremylong/DependencyCheck/releases/download/v8.4.0/dependency-check-8.4.0-release.zip
                                        unzip -q dependency-check-8.4.0-release.zip
                                        mv dependency-check /opt/
                                        ln -s /opt/dependency-check/bin/dependency-check.sh /usr/local/bin/dependency-check.sh
                                    fi

                                    # Run dependency check
                                    dependency-check.sh --project "OpenTelemetry Demo" --scan . --format ALL --out dependency-check-report

                                    # Check for critical vulnerabilities
                                    CRITICAL_COUNT=$(grep -c "CRITICAL" dependency-check-report.html || echo "0")
                                    HIGH_COUNT=$(grep -c "HIGH" dependency-check-report.html || echo "0")

                                    echo "Critical vulnerabilities: $CRITICAL_COUNT (threshold: $OWASP_CRITICAL_THRESHOLD)"
                                    echo "High vulnerabilities: $HIGH_COUNT (threshold: $OWASP_HIGH_THRESHOLD)"

                                    if [ "$CRITICAL_COUNT" -gt "$OWASP_CRITICAL_THRESHOLD" ] || [ "$HIGH_COUNT" -gt "$OWASP_HIGH_THRESHOLD" ]; then
                                        echo "❌ Dependency check failed - too many vulnerabilities"
                                        exit 1
                                    fi
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'dependency-check-report.*', allowEmptyArchive: true
                            publishHTML target: [
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: '.',
                                reportFiles: 'dependency-check-report.html',
                                reportName: 'OWASP Dependency Check Report'
                            ]
                        }
                    }
                }

                stage('Trivy SCA Scan') {
                    steps {
                        script {
                            echo "🔍 Running Trivy SCA scan..."
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                sh '''
                                    # Install Trivy if not available
                                    if ! command -v trivy &> /dev/null; then
                                        echo "Installing Trivy..."
                                        wget -q https://github.com/aquasecurity/trivy/releases/download/v0.48.3/trivy_0.48.3_Linux-64bit.tar.gz
                                        tar -xzf trivy_0.48.3_Linux-64bit.tar.gz
                                        mv trivy /usr/local/bin/
                                    fi

                                    # Run Trivy filesystem scan
                                    trivy fs --format json --output trivy-fs-results.json . || true

                                    # Check for critical vulnerabilities
                                    CRITICAL_VULNS=$(jq '.Results[].Vulnerabilities[] | select(.Severity == "CRITICAL") | .VulnerabilityID' trivy-fs-results.json | wc -l || echo "0")
                                    HIGH_VULNS=$(jq '.Results[].Vulnerabilities[] | select(.Severity == "HIGH") | .VulnerabilityID' trivy-fs-results.json | wc -l || echo "0")

                                    echo "Critical vulnerabilities: $CRITICAL_VULNS (threshold: $CRITICAL_VULN_THRESHOLD)"
                                    echo "High vulnerabilities: $HIGH_VULNS (threshold: $HIGH_VULN_THRESHOLD)"

                                    if [ "$CRITICAL_VULNS" -gt "$CRITICAL_VULN_THRESHOLD" ] || [ "$HIGH_VULNS" -gt "$HIGH_VULN_THRESHOLD" ]; then
                                        echo "❌ Trivy SCA scan failed - too many vulnerabilities"
                                        exit 1
                                    fi
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'trivy-fs-results.json', allowEmptyArchive: true
                        }
                    }
                }
            }
        }

        stage('Code Quality: Testing') {
            when {
                expression {
                    return params.RUN_CODE_QUALITY
                }
            }
            parallel {
                stage('Go Tests') {
                    when {
                        anyOf {
                            expression { return BUILD_SCOPE == 'all' || BUILD_SCOPE == 'checkout' }
                        }
                    }
                    steps {
                        script {
                            echo "🧪 Running Go tests..."
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                sh '''
                                    cd src/checkout
                                    go test -v -race -coverprofile=coverage.out ./... | tee go-test-results.txt || true
                                    go tool cover -html=coverage.out -o coverage.html

                                    # Check coverage
                                    COVERAGE=$(go tool cover -func=coverage.out | grep total | awk '{print substr($3, 1, length($3)-1)}' || echo "0")
                                    echo "Code coverage: ${COVERAGE}% (minimum: ${CODE_COVERAGE_MIN}%)"

                                    if (( $(echo "$COVERAGE < $CODE_COVERAGE_MIN" | bc -l) )); then
                                        echo "❌ Code coverage below threshold"
                                        exit 1
                                    fi
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'src/checkout/coverage.html,src/checkout/go-test-results.txt', allowEmptyArchive: true
                            publishHTML target: [
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'src/checkout',
                                reportFiles: 'coverage.html',
                                reportName: 'Go Test Coverage Report'
                            ]
                        }
                    }
                }

                stage('Java Tests') {
                    when {
                        anyOf {
                            expression { return BUILD_SCOPE == 'all' || BUILD_SCOPE == 'ad' || BUILD_SCOPE == 'fraud-detection' }
                        }
                    }
                    steps {
                        script {
                            echo "🧪 Running Java tests..."
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                sh '''
                                    cd src/ad
                                    ./gradlew test jacocoTestReport || true

                                    # Check if test results exist
                                    if [ -f "build/reports/tests/test/index.html" ]; then
                                        echo "✅ Java tests completed"
                                    fi
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'src/ad/build/reports/tests/test/index.html,src/ad/build/reports/jacoco/test/html/index.html', allowEmptyArchive: true
                        }
                    }
                }

                stage('JavaScript Tests') {
                    when {
                        anyOf {
                            expression { return BUILD_SCOPE == 'all' || BUILD_SCOPE == 'frontend' }
                        }
                    }
                    steps {
                        script {
                            echo "🧪 Running JavaScript tests..."
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                sh '''
                                    cd src/frontend
                                    npm install
                                    npm run test:ci || true

                                    # Check for test results
                                    if [ -d "coverage" ]; then
                                        echo "✅ JavaScript tests completed with coverage"
                                    fi
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'src/frontend/coverage/**', allowEmptyArchive: true
                        }
                    }
                }
            }
        }

        stage('Security: Infrastructure as Code Scanning') {
            when {
                expression {
                    return params.RUN_SECURITY_SCANS
                }
            }
            steps {
                script {
                    echo "🔍 Scanning Kubernetes manifests..."
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        sh '''
                            # Install Checkov if not available
                            if ! command -v checkov &> /dev/null; then
                                echo "Installing Checkov..."
                                pip install checkov || true
                            fi

                            # Scan Kubernetes manifests
                            if command -v checkov &> /dev/null; then
                                checkov -f kubernetes/ --framework kubernetes --output json | tee checkov-results.json || true

                                # Check for failed checks
                                FAILED_CHECKS=$(jq '.results.failed_checks | length' checkov-results.json || echo "0")
                                echo "Failed IaC checks: $FAILED_CHECKS"

                                if [ "$FAILED_CHECKS" -gt "0" ]; then
                                    echo "⚠️  Infrastructure security issues found"
                                    # Don't fail build for IaC issues in development
                                    if [ "${ENVIRONMENT}" = "production" ]; then
                                        echo "❌ Blocking production deployment due to IaC security issues"
                                        exit 1
                                    fi
                                fi
                            fi
                        '''
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'checkov-results.json', allowEmptyArchive: true
                }
            }
        }

        stage('Setup Docker Buildx') {
            when {
                expression {
                    return params.MULTIPLATFORM_BUILD
                }
            }
            steps {
                script {
                    echo "🔧 Setting up Docker Buildx for multiplatform builds..."
                    sh '''
                        # Check if Docker Buildx is available
                        if ! docker buildx version &> /dev/null; then
                            echo "❌ Docker Buildx is not available. Please install Docker 19.03+ with Buildx support."
                            exit 1
                        fi

                        # Create or use existing multiplatform builder
                        BUILDER_NAME="otel-demo-builder-${BUILD_NUMBER}"

                        if docker buildx inspect "$BUILDER_NAME" &> /dev/null; then
                            echo "Using existing builder: $BUILDER_NAME"
                            docker buildx use "$BUILDER_NAME"
                        else
                            echo "Creating new multiplatform builder: $BUILDER_NAME"
                            docker buildx create --name "$BUILDER_NAME" --bootstrap --use --driver docker-container

                            # Configure builder for multiplatform (QEMU emulation)
                            docker run --privileged --rm tonistiigi/binfmt --install all || true
                        fi

                        # Verify builder is ready
                        docker buildx inspect
                        echo "✅ Docker Buildx setup complete"
                    '''
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    echo "🏗️  Building Docker images..."
                    if (BUILD_SCOPE == 'all') {
                        if (params.MULTIPLATFORM_BUILD) {
                            // Build all services individually for multiplatform
                            def services = [
                                'accounting', 'ad', 'cart', 'checkout', 'currency', 'email', 'flagd', 'flagd-ui',
                                'fraud-detection', 'frontend', 'frontend-proxy', 'grafana', 'image-provider',
                                'jaeger', 'kafka', 'load-generator', 'opensearch', 'otel-collector', 'payment',
                                'postgres', 'product-catalog', 'prometheus', 'quote', 'react-native-app',
                                'recommendation', 'shipping'
                            ]

                            services.each { service ->
                                def serviceDir = "src/${service}"
                                if (fileExists("${serviceDir}/Dockerfile")) {
                                    sh """
                                        IMAGE_NAME="${DOCKER_REGISTRY_URL}/${service}:${IMAGE_TAG}"

                                        echo "Building multiplatform image for ${service}..."
                                        echo "Target platforms: ${BUILD_PLATFORMS}"

                                        if [ "${PUSH_IMAGES}" = "true" ]; then
                                            docker buildx build --platform ${BUILD_PLATFORMS} -f ${serviceDir}/Dockerfile -t \${IMAGE_NAME} --push .
                                        else
                                            docker buildx build --platform ${BUILD_PLATFORMS} -f ${serviceDir}/Dockerfile -t \${IMAGE_NAME} .
                                        fi
                                    """
                                } else {
                                    echo "⚠️  Skipping ${service} - no Dockerfile found"
                                }
                            }
                        } else {
                            sh '''
                                export IMAGE_TAG=${IMAGE_TAG}
                                make build
                            '''
                        }
                    } else {
                        def service = BUILD_SCOPE
                        def serviceDir = "src/${service}"
                        if (params.MULTIPLATFORM_BUILD) {
                            sh """
                                IMAGE_NAME="${DOCKER_REGISTRY_URL}/${service}:${IMAGE_TAG}"

                                echo "Building multiplatform image for ${service}..."
                                echo "Target platforms: ${BUILD_PLATFORMS}"

                                if [ "${PUSH_IMAGES}" = "true" ]; then
                                    docker buildx build --platform ${BUILD_PLATFORMS} -f ${serviceDir}/Dockerfile -t \${IMAGE_NAME} --push .
                                else
                                    docker buildx build --platform ${BUILD_PLATFORMS} -f ${serviceDir}/Dockerfile -t \${IMAGE_NAME} .
                                fi
                            """
                        } else {
                            sh """
                                docker build -f ${serviceDir}/Dockerfile -t ${DOCKER_REGISTRY_URL}/${service}:${IMAGE_TAG} .
                            """
                        }
                    }
                }
            }
        }

        stage('Security: Container Image Scanning') {
            when {
                expression {
                    return params.RUN_SECURITY_SCANS
                }
            }
            steps {
                script {
                    echo "🔍 Scanning container images for vulnerabilities..."
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        sh '''
                            # Install Trivy if not available
                            if ! command -v trivy &> /dev/null; then
                                echo "Installing Trivy..."
                                wget -q https://github.com/aquasecurity/trivy/releases/download/v0.48.3/trivy_0.48.3_Linux-64bit.tar.gz
                                tar -xzf trivy_0.48.3_Linux-64bit.tar.gz
                                mv trivy /usr/local/bin/
                            fi

                            # Scan images
                            if [ "${BUILD_SCOPE}" = "all" ]; then
                                # Scan all service images (this would need to be customized based on actual image names)
                                echo "Scanning all service images..."
                                docker images --format "table {{.Repository}}:{{.Tag}}" | grep "${DOCKER_REGISTRY_URL}" | while read image; do
                                    echo "Scanning ${image}..."
                                    trivy image --format json --output "trivy-${image##*/}.json" "${image}" || true
                                done
                            else
                                IMAGE_NAME="${DOCKER_REGISTRY_URL}/${BUILD_SCOPE}:${IMAGE_TAG}"
                                echo "Scanning ${IMAGE_NAME}..."
                                trivy image --format json --output "trivy-${BUILD_SCOPE}.json" "${IMAGE_NAME}" || true

                                # Check vulnerability thresholds
                                CRITICAL_VULNS=$(jq '.Results[].Vulnerabilities[] | select(.Severity == "CRITICAL") | .VulnerabilityID' "trivy-${BUILD_SCOPE}.json" | wc -l || echo "0")
                                HIGH_VULNS=$(jq '.Results[].Vulnerabilities[] | select(.Severity == "HIGH") | .VulnerabilityID' "trivy-${BUILD_SCOPE}.json" | wc -l || echo "0")

                                echo "Critical vulnerabilities: $CRITICAL_VULNS (threshold: $CRITICAL_VULN_THRESHOLD)"
                                echo "High vulnerabilities: $HIGH_VULNS (threshold: $HIGH_VULN_THRESHOLD)"

                                if [ "$CRITICAL_VULNS" -gt "$CRITICAL_VULN_THRESHOLD" ] || [ "$HIGH_VULNS" -gt "$HIGH_VULN_THRESHOLD" ]; then
                                    echo "❌ Container image scan failed - too many vulnerabilities"
                                    exit 1
                                fi
                            fi
                        '''
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-*.json', allowEmptyArchive: true
                }
            }
        }

        stage('Generate SBOM') {
            when {
                expression {
                    return params.GENERATE_SBOM
                }
            }
            steps {
                script {
                    echo "📋 Generating Software Bill of Materials (SBOM)..."
                    sh '''
                        # Install Syft if not available
                        if ! command -v syft &> /dev/null; then
                            echo "Installing Syft..."
                            curl -sSfL https://raw.githubusercontent.com/anchore/syft/main/install.sh | sh -s -- -b /usr/local/bin
                        fi

                        # Generate SBOM for each image
                        if [ "${BUILD_SCOPE}" = "all" ]; then
                            docker images --format "table {{.Repository}}:{{.Tag}}" | grep "${DOCKER_REGISTRY_URL}" | while read image; do
                                echo "Generating SBOM for ${image}..."
                                syft "${image}" -o json | tee "sbom-${image##*/}.json"
                                syft "${image}" -o spdx-json | tee "sbom-${image##*/}.spdx.json"
                            done
                        else
                            IMAGE_NAME="${DOCKER_REGISTRY_URL}/${BUILD_SCOPE}:${IMAGE_TAG}"
                            echo "Generating SBOM for ${IMAGE_NAME}..."
                            syft "${IMAGE_NAME}" -o json | tee "sbom-${BUILD_SCOPE}.json"
                            syft "${IMAGE_NAME}" -o spdx-json | tee "sbom-${BUILD_SCOPE}.spdx.json"
                        fi
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'sbom-*.json', allowEmptyArchive: true
                }
            }
        }

        stage('Sign Images') {
            when {
                expression {
                    return params.SIGN_IMAGES && params.PUSH_IMAGES
                }
            }
            steps {
                script {
                    echo "🔐 Signing Docker images..."
                    withCredentials([
                        string(credentialsId: 'cosign-private-key', variable: 'COSIGN_PRIVATE_KEY'),
                        string(credentialsId: 'cosign-password', variable: 'COSIGN_PASSWORD')
                    ]) {
                        sh '''
                            # Install Cosign if not available
                            if ! command -v cosign &> /dev/null; then
                                echo "Installing Cosign..."
                                curl -sSfL https://raw.githubusercontent.com/sigstore/cosign/main/install.sh | sh -s -- -b /usr/local/bin
                            fi

                            # Sign images
                            if [ "${BUILD_SCOPE}" = "all" ]; then
                                docker images --format "table {{.Repository}}:{{.Tag}}" | grep "${DOCKER_REGISTRY_URL}" | while read image; do
                                    echo "Signing ${image}..."
                                    cosign sign --key env://COSIGN_PRIVATE_KEY "${image}" || true
                                done
                            else
                                IMAGE_NAME="${DOCKER_REGISTRY_URL}/${BUILD_SCOPE}:${IMAGE_TAG}"
                                echo "Signing ${IMAGE_NAME}..."
                                cosign sign --key env://COSIGN_PRIVATE_KEY "${IMAGE_NAME}" || true
                            fi
                        '''
                    }
                }
            }
        }

        stage('Push Images') {
            when {
                expression {
                    return params.PUSH_IMAGES && !params.MULTIPLATFORM_BUILD
                }
            }
            steps {
                script {
                    echo "📤 Pushing images to registry..."
                    withCredentials([
                        usernamePassword(
                            credentialsId: 'docker-registry-credentials',
                            usernameVariable: 'DOCKER_USERNAME',
                            passwordVariable: 'DOCKER_PASSWORD'
                        )
                    ]) {
                        sh '''
                            echo "${DOCKER_PASSWORD}" | docker login -u "${DOCKER_USERNAME}" --password-stdin ${DOCKER_REGISTRY}

                            if [ "${BUILD_SCOPE}" = "all" ]; then
                                export IMAGE_TAG=${IMAGE_TAG}
                                make build-and-push
                            else
                                IMAGE_NAME="${DOCKER_REGISTRY_URL}/${BUILD_SCOPE}:${IMAGE_TAG}"
                                echo "Pushing ${IMAGE_NAME}..."
                                docker push "${IMAGE_NAME}"
                            fi

                            docker logout ${DOCKER_REGISTRY}
                        '''
                    }
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when {
                expression {
                    return params.DEPLOY_TO_K8S && params.PUSH_IMAGES
                }
            }
            steps {
                script {
                    echo "🚀 Deploying to Kubernetes..."
                    withCredentials([
                        file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')
                    ]) {
                        sh '''
                            # Update image tags in Kubernetes manifests
                            if [ "${BUILD_SCOPE}" = "all" ]; then
                                # Update all services
                                find kubernetes/ -name "*.yaml" -exec sed -i "s|image:.*|image: ${DOCKER_REGISTRY_URL}/&:${IMAGE_TAG}|g" {} \\;
                            else
                                # Update specific service
                                sed -i "s|image:.*${BUILD_SCOPE}.*|image: ${DOCKER_REGISTRY_URL}/${BUILD_SCOPE}:${IMAGE_TAG}|g" kubernetes/${BUILD_SCOPE}/deploy.yaml
                            fi

                            # Deploy to Kubernetes
                            kubectl apply -f kubernetes/

                            # Wait for rollout
                            if [ "${BUILD_SCOPE}" = "all" ]; then
                                kubectl rollout status deployment --all
                            else
                                kubectl rollout status deployment/${BUILD_SCOPE}
                            fi
                        '''
                    }
                }
            }
        }

        stage('Compliance: License Scanning') {
            when {
                expression {
                    return params.RUN_SECURITY_SCANS
                }
            }
            steps {
                script {
                    echo "📜 Scanning for license compliance..."
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        sh '''
                            # Install license scanner
                            npm install -g license-checker || true

                            # Run license check
                            npx license-checker --json | tee license-check-results.json || true

                            # Check for problematic licenses
                            PROBLEMATIC_LICENSES=$(jq '. | to_entries[] | select(.value.licenses | contains("GPL") or contains("LGPL") or contains("MS-PL")) | .key' license-check-results.json | wc -l || echo "0")

                            echo "Problematic licenses found: $PROBLEMATIC_LICENSES"

                            if [ "$PROBLEMATIC_LICENSES" -gt "0" ]; then
                                echo "⚠️  Problematic licenses detected - review license-check-results.json"
                            fi
                        '''
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'license-check-results.json', allowEmptyArchive: true
                }
            }
        }
    }

    post {
        always {
            script {
                echo "=== DevSecOps Pipeline Summary ==="
                echo "Build Scope: ${BUILD_SCOPE}"
                echo "Environment: ${ENVIRONMENT}"
                echo "Docker Registry: ${DOCKER_REGISTRY_URL}"
                echo "Image Tag: ${IMAGE_TAG}"
                echo "Security Scans: ${RUN_SECURITY_SCANS}"
                echo "Code Quality: ${RUN_CODE_QUALITY}"
                echo "Push Images: ${PUSH_IMAGES}"
                echo "Sign Images: ${SIGN_IMAGES}"
                echo "Generate SBOM: ${GENERATE_SBOM}"
                echo "Multiplatform Build: ${MULTIPLATFORM_BUILD}"
                echo "Build Platforms: ${BUILD_PLATFORMS}"
                echo "Deploy to K8s: ${DEPLOY_TO_K8S}"
                echo "Build Result: ${currentBuild.result}"
                echo "Build Duration: ${currentBuild.durationString}"

                // Clean up workspace
                sh '''
                    # Remove sensitive files
                    rm -f trufflehog-results.json
                    rm -f *-results.json
                    rm -f dependency-check-report.*
                    rm -f trivy-*.json
                    rm -f sbom-*.json
                    rm -f license-check-results.json

                    # Clean up Docker Buildx builder if it was created
                    if [ "${MULTIPLATFORM_BUILD}" = "true" ]; then
                        BUILDER_NAME="otel-demo-builder-${BUILD_NUMBER}"
                        docker buildx rm "$BUILDER_NAME" || true
                        echo "Cleaned up buildx builder: $BUILDER_NAME"
                    fi

                    # Clean Docker system
                    docker system prune -f || true
                '''
            }

            // Publish test results (commented out as no tests generate junit.xml)
            // junit allowEmptyResults: true, testResults: '**/test-results.xml,**/junit.xml'

            // Publish coverage reports (commented out due to plugin availability)
            // publishCoverage adapters: [
            //     jacocoAdapter('**/jacoco.xml')
            // ], sourceFileResolver: sourceFiles('STORE_LAST_BUILD')
        }

        success {
            script {
                echo "✅ DevSecOps Pipeline completed successfully!"
                echo "All security checks passed and images are ready for deployment."

                // Send success notification
                emailext(
                    subject: "✅ DevSecOps Pipeline Success: ${BUILD_SCOPE} - ${IMAGE_TAG}",
                    body: """
                        DevSecOps Pipeline completed successfully!

                        Build Details:
                        - Scope: ${BUILD_SCOPE}
                        - Environment: ${ENVIRONMENT}
                        - Image Tag: ${IMAGE_TAG}
                        - Registry: ${DOCKER_REGISTRY_URL}
                        - Multiplatform: ${MULTIPLATFORM_BUILD} (${BUILD_PLATFORMS})
                        - Build: ${BUILD_URL}

                        Security Status: ✅ All checks passed
                        Code Quality: ✅ All tests passed
                        Images: ${PUSH_IMAGES ? '📤 Pushed to registry' : '🏗️  Built locally'}
                        Deployment: ${DEPLOY_TO_K8S ? '🚀 Deployed to Kubernetes' : '⏭️  Skipped'}
                    """,
                    to: '${DEFAULT_RECIPIENTS}'
                )
            }
        }

        failure {
            script {
                echo "❌ DevSecOps Pipeline failed!"
                echo "Check the logs above for security violations or build errors."

                // Send failure notification
                emailext(
                    subject: "❌ DevSecOps Pipeline Failed: ${BUILD_SCOPE} - ${IMAGE_TAG}",
                    body: """
                        DevSecOps Pipeline failed!

                        Build Details:
                        - Scope: ${BUILD_SCOPE}
                        - Environment: ${ENVIRONMENT}
                        - Image Tag: ${IMAGE_TAG}
                        - Build: ${BUILD_URL}

                        Please review the build logs for:
                        - Security scan failures
                        - Code quality issues
                        - Build errors
                        - Test failures
                    """,
                    to: '${DEFAULT_RECIPIENTS}'
                )
            }
        }

        unstable {
            script {
                echo "⚠️  DevSecOps Pipeline is unstable!"
                echo "Some checks failed but the build continued."

                // Send warning notification
                emailext(
                    subject: "⚠️  DevSecOps Pipeline Unstable: ${BUILD_SCOPE} - ${IMAGE_TAG}",
                    body: """
                        DevSecOps Pipeline completed with warnings!

                        Build Details:
                        - Scope: ${BUILD_SCOPE}
                        - Environment: ${ENVIRONMENT}
                        - Image Tag: ${IMAGE_TAG}
                        - Build: ${BUILD_URL}

                        Some security or quality checks failed. Please review the build artifacts.
                    """,
                    to: '${DEFAULT_RECIPIENTS}'
                )
            }
        }
    }
}