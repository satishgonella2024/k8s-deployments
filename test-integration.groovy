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
        
        stage('Apply Kubernetes Manifests') {
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
        }
        failure {
            echo 'Pipeline failed! Check logs for errors.'
        }
    }
}