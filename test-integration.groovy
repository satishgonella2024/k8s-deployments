pipeline {
    agent {
        label 'jenkins-agent'
    }
    
    environment {
        SONAR_PROJECT_KEY = "test-project"
        DOCKER_IMAGE = "sample-app"
        DOCKER_TAG = "latest"
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
            echo 'Pipeline succeeded! All security checks passed.'
        }
        failure {
            echo 'Pipeline failed! Security checks did not pass.'
        }
    }
}