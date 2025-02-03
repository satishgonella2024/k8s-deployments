pipeline {
    agent {
        label 'jenkins-agent'
    }
    
    environment {
        SONAR_PROJECT_KEY = "test-project"
        K8S_NAMESPACE = "devsecops"
        K8S_MANIFEST_PATH = "manifests"
        K8S_DEPLOYMENTS_PATH = "deployments"
        K8S_SERVICES_PATH = "services"
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
        
        stage('Apply Kubernetes Manifests') {
            agent {
                kubernetes {
                    yaml '''
                        apiVersion: v1
                        kind: Pod
                        spec:
                          containers:
                          - name: kubectl
                            image: lachlanevenson/k8s-kubectl:latest
                            command:
                            - cat
                            tty: true
                          resources:
                            requests:
                              cpu: "500m"
                              memory: "512Mi"
                            limits:
                              cpu: "1000m"
                              memory: "1Gi"
                    '''
                }
            }
            steps {
                container('kubectl') {
                    sh '''
                        echo "Checking kubectl version..."
                        kubectl version --client
                        
                        echo "Applying Kubernetes namespace..."
                        kubectl apply -f ${K8S_MANIFEST_PATH}/namespace.yaml
                        
                        echo "Applying Kubernetes deployments..."
                        kubectl apply -f ${K8S_DEPLOYMENTS_PATH}

                        echo "Applying Kubernetes services..."
                        kubectl apply -f ${K8S_SERVICES_PATH}
                        
                        echo "Waiting for deployments to become ready..."
                        kubectl rollout status deployment/nginx-deployment -n ${K8S_NAMESPACE}
                        
                        echo "Deployment successful!"
                    '''
                }
            }
        }
        
        stage('Report') {
            steps {
                sh '''
                    echo "Deployment Report:"
                    kubectl get all -n ${K8S_NAMESPACE}
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
            echo 'Pipeline succeeded! Kubernetes deployment completed successfully.'
        }
        failure {
            echo 'Pipeline failed! Check logs for errors.'
        }
    }
}