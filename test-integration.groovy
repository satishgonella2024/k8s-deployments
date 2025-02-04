pipeline {
    agent {
        label 'jenkins-agent'
    }
    
    environment {
        SONAR_PROJECT_KEY = "k8s-deployments"
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
        stage('Apply Namespace & ServiceAccount') {
            agent {
                kubernetes {
                    yaml '''
                        apiVersion: v1
                        kind: Pod
                        metadata:
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
                        echo "Applying Kubernetes namespace..."
                        kubectl apply -f ${K8S_MANIFEST_PATH}/namespace.yaml

                        echo "Applying Jenkins service account..."
                        kubectl apply -f ${K8S_MANIFEST_PATH}/jenkins-sa-binding.yaml
                    '''
                }
            }
        }

        stage('Deploy Blue-Green') {
            steps {
                container('kubectl') {
                    sh '''
                        echo "Deploying Blue-Green Strategy..."
                        kubectl apply -f ${K8S_DEPLOYMENTS_PATH}/nginx-blue-green.yaml
                        kubectl rollout status deployment/nginx-blue -n ${K8S_NAMESPACE}
                        kubectl rollout status deployment/nginx-green -n ${K8S_NAMESPACE}
                    '''
                }
            }
        }

        stage('Deploy Canary') {
            steps {
                container('kubectl') {
                    sh '''
                        echo "Deploying Canary Strategy..."
                        kubectl apply -f ${K8S_DEPLOYMENTS_PATH}/nginx-canary.yaml
                        kubectl rollout status deployment/nginx-canary -n ${K8S_NAMESPACE}
                    '''
                }
            }
        }

        stage('Deploy Stable Release') {
            steps {
                container('kubectl') {
                    sh '''
                        echo "Deploying Stable Release..."
                        kubectl apply -f ${K8S_DEPLOYMENTS_PATH}/nginx-deployment.yaml
                        kubectl rollout status deployment/nginx-deployment -n ${K8S_NAMESPACE}
                    '''
                }
            }
        }

        stage('Apply Kubernetes Services') {
            steps {
                container('kubectl') {
                    sh '''
                        echo "Applying Kubernetes services..."
                        kubectl apply -f ${K8S_SERVICES_PATH}
                    '''
                }
            }
        }

        stage('Deployment Report') {
            steps {
                container('kubectl') {
                    sh '''
                        echo "Fetching Deployment Report..."
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
            echo '✅ Pipeline succeeded! Kubernetes deployment completed successfully.'
        }
        failure {
            echo '❌ Pipeline failed! Check logs for errors.'
        }
    }
}