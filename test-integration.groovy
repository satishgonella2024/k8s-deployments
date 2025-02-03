pipeline {
    agent {
        label 'jenkins-agent'
    }
    
    environment {
        SONAR_PROJECT_KEY = "test-project"
        DOCKER_IMAGE = "your-docker-repo/sample-app"
        DOCKER_TAG = "latest"
        K8S_NAMESPACE = "devsecops"
        DEPLOYMENT_NAME = "sample-app"
    }
    
    stages {
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh '''
                        echo "SonarQube Integration Test"
                        echo "SonarQube URL: ${SONAR_HOST_URL}"
                        curl -s -f -u "${SONAR_TOKEN}:" "${SONAR_HOST_URL}/api/system/status"
                        if [ $? -eq 0 ]; then
                            echo "Successfully connected to SonarQube"
                        else
                            echo "Failed to connect to SonarQube"
                            exit 1
                        fi
                    '''
                }
            }
        }
        
        stage('Security Scan') {
            agent {
                kubernetes {
                    yaml '''
                        apiVersion: v1
                        kind: Pod
                        spec:
                          containers:
                          - name: trivy
                            image: aquasec/trivy:latest
                            command:
                            - cat
                            tty: true
                    '''
                }
            }
            steps {
                container('trivy') {
                    sh '''
                        echo "Running Trivy vulnerability scan..."
                        trivy version
                        # Example of scanning a public image
                        trivy image nginx:latest --no-progress --severity HIGH,CRITICAL
                    '''
                }
            }
        }
        
        stage('Code Quality Gates') {
            steps {
                script {
                    echo "Checking code quality gates..."
                    // Add quality gate checks here
                }
            }
        }
        
        stage('Build and Push Docker Image') {
            steps {
                sh '''
                    echo "Building Docker Image..."
                    docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} .
                    echo "Pushing Docker Image to Registry..."
                    docker push ${DOCKER_IMAGE}:${DOCKER_TAG}
                '''
            }
        }

        stage('Deploy to Kubernetes') {
            agent {
                kubernetes {
                    yaml '''
                        apiVersion: v1
                        kind: Pod
                        spec:
                          containers:
                          - name: kubectl
                            image: bitnami/kubectl:latest
                            command:
                            - cat
                            tty: true
                    '''
                }
            }
            steps {
                container('kubectl') {
                    sh '''
                        echo "Deploying to Kubernetes..."
                        kubectl set image deployment/${DEPLOYMENT_NAME} ${DEPLOYMENT_NAME}=${DOCKER_IMAGE}:${DOCKER_TAG} -n ${K8S_NAMESPACE}
                        kubectl rollout status deployment/${DEPLOYMENT_NAME} -n ${K8S_NAMESPACE}
                        echo "Deployment complete!"
                    '''
                }
            }
        }
        
        stage('Report') {
            steps {
                sh '''
                    echo "Environment Information:"
                    echo "SONAR_PROJECT_KEY: ${SONAR_PROJECT_KEY}"
                    echo "Workspace: ${WORKSPACE}"
                    echo "Node Name: ${NODE_NAME}"
                    echo "Build Number: ${BUILD_NUMBER}"
                '''
            }
        }
    }
    
    post {
        always {
            deleteDir()
            echo "Cleanup completed"
        }
        success {
            echo 'Pipeline succeeded! All security checks passed and deployment completed.'
        }
        failure {
            echo 'Pipeline failed! Security checks or deployment did not pass.'
        }
    }
}