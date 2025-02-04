pipeline {
    agent {
        label 'jenkins-agent'
    }
    
    environment {
        SONAR_PROJECT_KEY = "test-project"
        IMAGE_TAG = "latest"
        K8S_NAMESPACE = "k8s-deployments"
        K8S_MANIFEST_PATH = "manifests"
        K8S_DEPLOYMENTS_PATH = "deployments"
        K8S_SERVICES_PATH = "services"
    }

    parameters {
        string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'Docker Image Tag')
        string(name: 'K8S_NAMESPACE', defaultValue: 'k8s-deployments', description: 'Kubernetes Namespace')
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

                        # Run Sonar Scanner
                        sonar-scanner -Dsonar.projectKey=${SONAR_PROJECT_KEY} -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_TOKEN}

                        # Ensure SonarQube report-task.txt is generated
                        if [ ! -f ".scannerwork/report-task.txt" ]; then
                            echo "Sonar Scanner did not generate report-task.txt!"
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
                        trivy image nginx:${IMAGE_TAG} --no-progress --severity HIGH,CRITICAL > trivy_report.txt

                        # Check for critical vulnerabilities
                        if grep -q "CRITICAL" trivy_report.txt; then
                            echo "CRITICAL vulnerabilities found! Failing the pipeline."
                            exit 1
                        fi
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
                        metadata:
                          name: jenkins-agent
                          namespace: k8s-deployments
                        spec:
                          serviceAccountName: jenkins-sa
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
                        kubectl apply -f ${K8S_MANIFEST_PATH}/namespace.yaml || { echo "Namespace apply failed"; exit 1; }
                        
                        echo "Applying Kubernetes deployments..."
                        kubectl apply -f ${K8S_DEPLOYMENTS_PATH} || { echo "Deployment apply failed"; exit 1; }

                        echo "Applying Kubernetes services..."
                        kubectl apply -f ${K8S_SERVICES_PATH} || { echo "Service apply failed"; exit 1; }
                        
                        echo "Waiting for deployments to become ready..."
                        kubectl rollout status deployment/nginx-deployment -n ${K8S_NAMESPACE} || { echo "Deployment rollout failed"; exit 1; }
                        
                        echo "Deployment successful!"
                    '''
                }
            }
        }
        
        stage('Report') {
            agent {
                kubernetes {
                    yaml '''
                        apiVersion: v1
                        kind: Pod
                        metadata:
                          name: jenkins-agent
                          namespace: k8s-deployments
                        spec:
                          serviceAccountName: jenkins-sa
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
                        echo "Deployment Report:"
                        kubectl get all -n ${K8S_NAMESPACE}
                    '''
                }
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
            sh '''
                curl -X POST -H 'Content-type: application/json' --data '{"text":"✅ Pipeline Success: Kubernetes deployment completed successfully!"}' YOUR_SLACK_WEBHOOK_URL
            '''
        }
        failure {
            echo 'Pipeline failed! Check logs for errors.'
            sh '''
                curl -X POST -H 'Content-type: application/json' --data '{"text":"❌ Pipeline Failed! Check logs for details."}' YOUR_SLACK_WEBHOOK_URL
            '''
        }
    }
}